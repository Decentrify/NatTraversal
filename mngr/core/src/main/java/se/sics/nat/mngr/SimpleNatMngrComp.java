/*
 * Copyright (C) 2009 Swedish Institute of Computer Science (SICS) Copyright (C)
 * 2009 Royal Institute of Technology (KTH)
 *
 * NatTraverser is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package se.sics.nat.mngr;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.Channel;
import se.sics.kompics.Component;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Kill;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.config.Config;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.CancelTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;
import se.sics.kompics.timer.Timer;
import se.sics.ktoolbox.netmngr.NetMngrBind;
import se.sics.ktoolbox.netmngr.NetworkKCWrapper;
import se.sics.ktoolbox.netmngr.chunk.ChunkMngrComp;
import se.sics.ktoolbox.netmngr.chunk.util.CMTrafficSelector;
import se.sics.ktoolbox.netmngr.event.NetMngrPort;
import se.sics.ktoolbox.netmngr.event.NetMngrReady;
import se.sics.ktoolbox.netmngr.event.NetMngrUnbind;
import se.sics.ktoolbox.netmngr.ipsolver.IpSolve;
import se.sics.ktoolbox.netmngr.ipsolver.IpSolverComp;
import se.sics.ktoolbox.netmngr.ipsolver.IpSolverPort;
import se.sics.ktoolbox.netmngr.nxnet.NxNetBind;
import se.sics.ktoolbox.netmngr.nxnet.NxNetComp;
import se.sics.ktoolbox.netmngr.nxnet.NxNetPort;
import se.sics.ktoolbox.netmngr.nxnet.NxNetUnbind;
import se.sics.ktoolbox.util.config.impl.SystemKCWrapper;
import se.sics.ktoolbox.util.identifiable.Identifier;
import se.sics.ktoolbox.util.network.basic.BasicAddress;
import se.sics.ktoolbox.util.network.nat.NatAwareAddress;
import se.sics.ktoolbox.util.network.nat.NatAwareAddressImpl;
import se.sics.ktoolbox.util.network.nat.NatType;
import se.sics.ktoolbox.util.network.ports.ShortCircuitChannel;
import se.sics.ktoolbox.util.status.Status;
import se.sics.ktoolbox.util.status.StatusPort;
import se.sics.nat.detection.NatDetectionComp;
import se.sics.nat.detection.NatDetectionPort;
import se.sics.nat.detection.event.NatDetected;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class SimpleNatMngrComp extends ComponentDefinition {

    private static final Logger LOG = LoggerFactory.getLogger(SimpleNatMngrComp.class);
    private String logPrefix = "";

    //*****************************CONNECTIONS**********************************
    private final ExtPort extPorts;
    //***************************EXTERNAL_CONNECT*******************************
    Negative<NetMngrPort> netMngrPort = provides(NetMngrPort.class);
    Negative<Network> networkPort = provides(Network.class);
    Negative<StatusPort> statusPort = provides(StatusPort.class);
    //*************************INTERNAL_NO_CONNECT******************************
    Positive<IpSolverPort> ipSolverPort = requires(IpSolverPort.class);
    Positive<NxNetPort> nxNetPort = requires(NxNetPort.class);
    Positive<NatDetectionPort> natDetectionPort = requires(NatDetectionPort.class);
    //****************************CONFIGURATION*********************************
    private final SystemKCWrapper systemConfig;
    private final NetworkKCWrapper netConfig;
    private final NetworkAuxKCWrapper netAuxConfig;
    //**************************INTERNAL_STATE**********************************
    private InetAddress privateIp;
    private InetAddress publicIp;
    private NatType natType;
    private NatAwareAddress selfAdr;
    //****************************COMPONENTS************************************
    private Component ipSolverComp;
    private Component nxNetComp;
    private Pair<Component, Channel[]> natDetection;
    private Component chunkMngrComp;
    //****************************AUX_STATE*************************************
    private UUID natDetectionRetryTid;
    //proxied
    private Map<Identifier, NetMngrBind.Request> proxiedPendingBind = new HashMap<>();
    private Map<Identifier, NetMngrUnbind.Request> proxiedPendingUnbind = new HashMap<>();

    public SimpleNatMngrComp(Init init) {
        systemConfig = new SystemKCWrapper(config());
        logPrefix = "<nid:" + systemConfig.id + "> ";
        LOG.info("{}initializing...", logPrefix);

        netConfig = new NetworkKCWrapper(config());
        netAuxConfig = new NetworkAuxKCWrapper(config());
        extPorts = init.extPorts;

        subscribe(handleStart, control);
        subscribe(handlePrivateIpDetected, ipSolverPort);
        subscribe(handleNatDetectionRetry, extPorts.timerPort);
        subscribe(handleNatDetected, natDetectionPort);
        subscribe(handleBindReq, netMngrPort);
        subscribe(handleBindResp, nxNetPort);
        subscribe(handleUnbindReq, netMngrPort);
        subscribe(handleUnbindResp, nxNetPort);
    }

    private Handler handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            LOG.info("{}starting...", logPrefix);
            setIpSolver();
            setNxNet();
            setChunkMngr();
            trigger(Start.event, ipSolverComp.control());
            trigger(Start.event, nxNetComp.control());
            trigger(Start.event, chunkMngrComp.control());
            trigger(new IpSolve.Request(netConfig.ipTypes), ipSolverPort);
        }
    };
    
    @Override
    public void tearDown() {
        cancelNatDetectionRetry();
    }

    private void setIpSolver() {
        ipSolverComp = create(IpSolverComp.class, new IpSolverComp.IpSolverInit());
        connect(ipSolverComp.getPositive(IpSolverPort.class), ipSolverPort.getPair(), Channel.TWO_WAY);
    }

    private void setNxNet() {
        nxNetComp = create(NxNetComp.class, new NxNetComp.Init());
        connect(nxNetComp.getPositive(NxNetPort.class), nxNetPort.getPair(), Channel.TWO_WAY);
    }

    private void setChunkMngr() {
        chunkMngrComp = create(ChunkMngrComp.class, new ChunkMngrComp.Init());
        connect(chunkMngrComp.getNegative(Timer.class), extPorts.timerPort, Channel.TWO_WAY);
        ShortCircuitChannel.getChannel(
                nxNetComp.getPositive(Network.class), chunkMngrComp.getPositive(Network.class), new CMTrafficSelector.Outgoing(),
                networkPort, chunkMngrComp.getNegative(Network.class), new CMTrafficSelector.Incoming());
    }

    private void setNatDetection() {
        NatDetectionComp.ExtPort natExtPorts = new NatDetectionComp.ExtPort(extPorts.timerPort, networkPort.getPair());
        Component natDetectionComp = create(NatDetectionComp.class, new NatDetectionComp.Init(natExtPorts, privateIp));
        Channel[] natDetectionChannels = new Channel[2];
        natDetectionChannels[0] = connect(natDetectionComp.getNegative(NxNetPort.class), nxNetComp.getPositive(NxNetPort.class), Channel.TWO_WAY);
        natDetectionChannels[1] = connect(natDetectionComp.getPositive(NatDetectionPort.class), natDetectionPort.getPair(), Channel.TWO_WAY);
        natDetection = Pair.with(natDetectionComp, natDetectionChannels);
    }

    private void cleanupNatDetection() {
        disconnect(natDetection.getValue1()[0]);
        disconnect(natDetection.getValue1()[1]);
        trigger(Kill.event, natDetection.getValue0().control());
        natDetection = null;
    }

    private void bindAppNetwork() {
        if (natType.isOpen()) {
            selfAdr = NatAwareAddressImpl.open(new BasicAddress(privateIp, systemConfig.port, systemConfig.id));
        } else if (natType.isSimpleNat()) {
            selfAdr = NatAwareAddressImpl.open(new BasicAddress(publicIp, systemConfig.port, systemConfig.id));
            Config.Builder cb = config().modify(id());
            cb.setValue("netty.bindInterface", privateIp);
            cb.finalise();
            updateConfig(cb.finalise());
        } else {
            //TODO Alex - fix this - hack 
            selfAdr = NatAwareAddressImpl.unknown(new BasicAddress(privateIp, systemConfig.port, systemConfig.id));
        }
        NxNetBind.Request bindReq = new NxNetBind.Request(selfAdr);
        trigger(bindReq, nxNetPort);
    }

    //******************************DETECTION***********************************
    Handler handlePrivateIpDetected = new Handler<IpSolve.Response>() {
        @Override
        public void handle(IpSolve.Response resp) {
            LOG.info("{}new ips detected", logPrefix);
            if (resp.boundIp == null) {
                throw new RuntimeException("no bound ip");
            }
            privateIp = resp.boundIp;
            setNatDetection();
            trigger(Start.event, natDetection.getValue0().control());
        }
    };

    Handler handleNatDetectionRetry = new Handler<NatDetectionRetry>() {
        @Override
        public void handle(NatDetectionRetry timeout) {
            setNatDetection();
            trigger(Start.event, natDetection.getValue0().control());
        }
    };
    
    Handler handleNatDetected = new Handler<NatDetected>() {
        @Override
        public void handle(NatDetected event) {
            LOG.info("{}detected nat - public ip:{} nat type:{}",
                    new Object[]{logPrefix, (event.publicIp.isPresent() ? event.publicIp.get() : "x"), event.natType});
            natType = event.natType;
            if (natType.isBlocked()) {
                LOG.warn("{}detected UDP blocked - might mean I could not connect to stun servers - retrying", logPrefix);
                cleanupNatDetection();
                scheduleNatDetectionRetry(30000);
                return;
            }
            if (!(natType.isSimpleNat() || natType.isOpen())) {
                LOG.error("{}currently only open or simple nats allowed");
                //TODO Alex - fix this - hack 
//                throw new RuntimeException("only open or simple nats allowed");
                publicIp = privateIp;
            } else {
                publicIp = event.publicIp.get();
            }
            cleanupNatDetection();
            bindAppNetwork();
        }
    };
        
    private void scheduleNatDetectionRetry(long period) {
        if (natDetectionRetryTid != null) {
            LOG.warn("{}double starting nat detection timeout", logPrefix);
            return;
        }
        ScheduleTimeout st = new ScheduleTimeout(period);
        NatDetectionRetry sc = new NatDetectionRetry(st);
        st.setTimeoutEvent(sc);
        trigger(st, extPorts.timerPort);

        natDetectionRetryTid = sc.getTimeoutId();
    }
    
    private void cancelNatDetectionRetry() {
        if (natDetectionRetryTid == null) {
            return;
        }
        CancelTimeout cpt = new CancelTimeout(natDetectionRetryTid);
        natDetectionRetryTid = null;
        trigger(cpt, extPorts.timerPort);
    }

    private static class NatDetectionRetry extends Timeout {

        NatDetectionRetry(ScheduleTimeout request) {
            super(request);
        }

        @Override
        public String toString() {
            return "NatDetectionTimeout";
        }
    }

    //****************************NXNET_CONTROL*********************************
    private Handler handleBindResp = new Handler<NxNetBind.Response>() {
        @Override
        public void handle(NxNetBind.Response resp) {
            LOG.trace("{}received:{}", logPrefix, resp);

            if (resp.req.bindAdr.getPort() == systemConfig.port) {
                //tell everyone
                NetMngrReady ready = new NetMngrReady((NatAwareAddress) resp.req.bindAdr);
                LOG.info("{}ready", logPrefix);
                trigger(new Status.Internal(ready), statusPort);
            } else {
                NetMngrBind.Request req = proxiedPendingBind.remove(resp.getId());
                if (req == null) {
                    throw new RuntimeException("logic error - cleanup problems");
                }
                answer(req, req.answer(resp.req.bindAdr));
            }
        }
    };

    private Handler handleBindReq = new Handler<NetMngrBind.Request>() {
        @Override
        public void handle(NetMngrBind.Request req) {
            LOG.trace("{}received:{}", logPrefix, req);
            NatAwareAddress adr = NatAwareAddressImpl.open(new BasicAddress(privateIp, req.port, systemConfig.id));
            NxNetBind.Request bindReq = new NxNetBind.Request(adr);
            trigger(bindReq, nxNetPort);
            proxiedPendingBind.put(bindReq.getId(), req);
        }
    };

    private Handler handleUnbindReq = new Handler<NetMngrUnbind.Request>() {
        @Override
        public void handle(NetMngrUnbind.Request req) {
            LOG.trace("{}received:{}", logPrefix, req);
            NxNetUnbind.Request unbindReq = new NxNetUnbind.Request(req.port);
            trigger(unbindReq, nxNetPort);
            proxiedPendingUnbind.put(unbindReq.getId(), req);
        }
    };

    private Handler handleUnbindResp = new Handler<NxNetUnbind.Response>() {
        @Override
        public void handle(NxNetUnbind.Response resp) {
            LOG.trace("{}received:{}", logPrefix, resp);
            NetMngrUnbind.Request req = proxiedPendingUnbind.remove(resp.getId());
            if (req == null) {
                throw new RuntimeException("logic error - cleanup problems");
            }
            answer(req, req.answer());
        }
    };

    //**************************************************************************
    public static class Init extends se.sics.kompics.Init<SimpleNatMngrComp> {

        public final ExtPort extPorts;

        public Init(ExtPort extPorts) {
            this.extPorts = extPorts;
        }
    }

    public static class ExtPort {

        public final Positive<Timer> timerPort;

        public ExtPort(Positive<Timer> timerPort) {
            this.timerPort = timerPort;
        }
    }
}
