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
package se.sics.nat.detection;

import com.google.common.base.Optional;
import java.net.InetAddress;
import java.util.UUID;
import org.javatuples.Pair;
import org.javatuples.Triplet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.Channel;
import se.sics.kompics.Component;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Fault;
import se.sics.kompics.Fault.ResolveAction;
import se.sics.kompics.Handler;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.Timer;
import se.sics.ktoolbox.netmngr.ipsolver.IpSolve;
import se.sics.ktoolbox.netmngr.ipsolver.IpSolverComp;
import se.sics.ktoolbox.netmngr.ipsolver.IpSolverPort;
import se.sics.ktoolbox.netmngr.nxnet.NxNetBind;
import se.sics.ktoolbox.netmngr.nxnet.NxNetPort;
import se.sics.ktoolbox.netmngr.nxnet.NxNetUnbind;
import se.sics.ktoolbox.util.config.impl.SystemKCWrapper;
import se.sics.ktoolbox.util.network.basic.BasicAddress;
import se.sics.ktoolbox.util.status.StatusPort;
import se.sics.nat.stun.StunClientPort;
import se.sics.nat.stun.client.StunClientComp;
import se.sics.nat.stun.client.StunClientKCWrapper;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class NatDetectionComp extends ComponentDefinition {

    private static final Logger LOG = LoggerFactory.getLogger(NatDetectionComp.class);
    private String logPrefix = "";

    //*****************************CONNECTIONS**********************************
    //*************************EXTERNAL_CONNECT_TO******************************
    Negative<StatusPort> status = provides(StatusPort.class);
    Positive<NxNetPort> nxNetPort = requires(NxNetPort.class);
    //*************************INTERNAL_NO_CONNECT******************************
    Positive<IpSolverPort> ipSolverPort = requires(IpSolverPort.class);
    //*******************************CONFIG*************************************
    private SystemKCWrapper systemConfig;
    private StunClientKCWrapper stunClientConfig;
    private NatDetectionKCWrapper natDetectionConfig;
    //****************************EXTERNAL_STATE********************************
    private final ExtPort extPorts;
    //****************************INTERNAL_STATE********************************
    private InetAddress privateIp;
    private Pair<BasicAddress, BasicAddress> stunAdr = Pair.with(null, null);
    //******************************AUX_STATE***********************************
    private NxNetBind.Request stun1BindReq;
    private NxNetBind.Request stun2BindReq;
    private NxNetUnbind.Request stun1UnbindReq;
    private NxNetUnbind.Request stun2UnbindReq;
    //******************************COMPONENTS**********************************
    private Component ipSolverComp;
    private Component stunClientComp;
    private Component upnpComp;

    public NatDetectionComp(Init init) {
        systemConfig = new SystemKCWrapper(config());
        logPrefix = "<nid:" + systemConfig.id + "> ";
        LOG.info("{}initiating...", logPrefix);

        stunClientConfig = new StunClientKCWrapper(config());
        extPorts = init.extPorts;

        subscribe(handleStart, control);
        subscribe(handleIpDetected, ipSolverPort);
        subscribe(handleBindResp, nxNetPort);
    }

    //**************************CONTROL*****************************************
    Handler handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            LOG.info("{}starting...", logPrefix);
            setIpSolver();
            trigger(Start.event, ipSolverComp.control());
        }
    };

    private Handler handleIpDetected = new Handler<IpSolve.Response>() {
        @Override
        public void handle(IpSolve.Response resp) {
            LOG.info("{}new ips detected", logPrefix);
            if (resp.boundIp == null) {
                throw new RuntimeException("no bound ip");
            }
            privateIp = resp.boundIp;

            LOG.info("{}setting up public ip detection", logPrefix);
            BasicAddress stunAdr1 = new BasicAddress(privateIp, stunClientConfig.stunClientPorts.getValue0(), systemConfig.id);
            stun1BindReq = new NxNetBind.Request(stunAdr1);
            trigger(stun1BindReq, nxNetPort);
            BasicAddress stunAdr2 = new BasicAddress(privateIp, stunClientConfig.stunClientPorts.getValue1(), systemConfig.id);
            trigger(new NxNetBind.Request(stunAdr2), nxNetPort);
        }
    };

    private void setIpSolver() {
        ipSolverComp = create(IpSolverComp.class, new IpSolverComp.IpSolverInit());
        connect(ipSolverComp.getPositive(IpSolverPort.class), ipSolverPort.getPair(), Channel.TWO_WAY);
    }

    private Handler handleBindResp = new Handler<NxNetBind.Response>() {
        @Override
        public void handle(NxNetBind.Response resp) {
            LOG.trace("{}received:{}", logPrefix, resp);
            if (resp.getId().equals(stun1BindReq.getId())) {
                stunAdr = stunAdr.setAt0((BasicAddress) resp.req.bindAdr);
                advancePublicIpDetection();
                return;
            }
            if (resp.getId().equals(stun2BindReq.getId())) {
                stunAdr = stunAdr.setAt1((BasicAddress) resp.req.bindAdr);
                advancePublicIpDetection();
                return;
            }
        }
    };
    
    private void advancePublicIpDetection() {
        if (stunAdr.getValue0() == null || stunAdr.getValue1() == null) {
            return;
        }
        setStunClient();
        trigger(Start.event, stunClientComp.control());
    }

    private void setStunClient() {
        stunClientComp = create(StunClientComp.class, new StunClientComp.Init(stunAdr, natDetectionConfig.stunView));
        connect(stunClientComp.getNegative(Timer.class), extPorts.timerPort, Channel.TWO_WAY);
        connect(stunClientComp.getNegative(Network.class), extPorts.networkPort, Channel.TWO_WAY);
        connect(stunClientComp.getPositive(StunClientPort.class), stunPort.getPair(), Channel.TWO_WAY);
    }

    @Override
    public ResolveAction handleFault(Fault fault) {
        LOG.error("{}fault:{} on comp:{}", new Object[]{logPrefix, fault.getCause().getMessage(), fault.getSourceCore().id()});
        return ResolveAction.ESCALATE;
    }

    public static class Init extends se.sics.kompics.Init<NatDetectionComp> {

        public final ExtPort extPorts;

        public Init(ExtPort extPorts) {
            this.extPorts = extPorts;
        }
    }

    public static class ExtPort {

        public final Positive<Timer> timerPort;
        public final Positive<Network> networkPort;

        public ExtPort(Positive<Timer> timerPort, Positive<Network> networkPort) {
            this.timerPort = timerPort;
            this.networkPort = networkPort;
        }
    }
}
