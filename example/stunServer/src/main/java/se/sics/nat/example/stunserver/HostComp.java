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
package se.sics.nat.example.stunserver;

import com.google.common.collect.ImmutableMap;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.net.InetAddress;
import java.util.EnumSet;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.Component;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Fault;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.Kompics;
import se.sics.kompics.Start;
import se.sics.kompics.Stop;
import se.sics.kompics.network.Network;
import se.sics.kompics.network.netty.NettyInit;
import se.sics.kompics.network.netty.NettyNetwork;
import se.sics.kompics.timer.Timer;
import se.sics.kompics.timer.java.JavaTimer;
import se.sics.ktoolbox.ipsolver.IpSolverComp;
import se.sics.ktoolbox.ipsolver.IpSolverPort;
import se.sics.ktoolbox.ipsolver.msg.GetIp;
import se.sics.nat.stun.server.StunServerComp;
import se.sics.nat.stun.server.StunServerComp.StunServerInit;
import se.sics.nat.common.StunServerInitHelper;
import se.sics.nat.stun.StunSerializerSetup;
import se.sics.nat.stun.server.SSNetworkHook;
import se.sics.p2ptoolbox.util.nat.NatedTrait;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;
import se.sics.p2ptoolbox.util.proxy.ComponentProxy;
import se.sics.p2ptoolbox.util.serializer.BasicSerializerSetup;
import se.sics.p2ptoolbox.util.traits.AcceptedTraits;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class HostComp extends ComponentDefinition {

    private Logger LOG = LoggerFactory.getLogger(HostComp.class);
    private String logPrefix = "";

    private Component timer;
    private Component ipSolver;
    private Component stunServer;
    private final StunServerInitHelper ssInit;
    private Pair<DecoratedAddress, DecoratedAddress> self;

    public HostComp(StunServerHostInit init) {
        LOG.info("{}initializing...", logPrefix);
        this.ssInit = init.ssInit;

        subscribe(handleStart, control);
        subscribe(handleStop, control);
    }

    //*************************CONTROL******************************************
    Handler handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            LOG.info("{}starting...", logPrefix);
            connectTimer();
            connectIpSolver();
        }
    };

    Handler handleStop = new Handler<Stop>() {
        @Override
        public void handle(Stop event) {
            LOG.info("{}stopping...", logPrefix);
        }
    };

    @Override
    public Fault.ResolveAction handleFault(Fault fault) {
        LOG.error("{}child component failure:{}", logPrefix, fault);
        System.exit(1);
        return Fault.ResolveAction.RESOLVED;
    }

    //**************************************************************************
    private void connectTimer() {
        timer = create(JavaTimer.class, Init.NONE);
        trigger(Start.event, timer.control());
    }

    private void connectIpSolver() {
        ipSolver = create(IpSolverComp.class, new IpSolverComp.IpSolverInit());
        subscribe(handleGetIp, ipSolver.getPositive(IpSolverPort.class));
        trigger(Start.event, ipSolver.control());
        trigger(new GetIp.Req(EnumSet.of(GetIp.NetworkInterfacesMask.PUBLIC)), ipSolver.getPositive(IpSolverPort.class));
    }

    public Handler handleGetIp = new Handler<GetIp.Resp>() {
        @Override
        public void handle(GetIp.Resp resp) {
            LOG.trace("{}received ips", logPrefix);
            if (!resp.addrs.isEmpty()) {
                InetAddress selfIp = resp.addrs.get(0).getAddr();
                if (resp.addrs.size() > 1) {
                    LOG.warn("{}multiple ips detected, proceeding with first one", logPrefix);
                }
                self = Pair.with(
                        new DecoratedAddress(new BasicAddress(selfIp, ssInit.selfPorts.getValue0(), ssInit.selfId)),
                        new DecoratedAddress(new BasicAddress(selfIp, ssInit.selfPorts.getValue1(), ssInit.selfId)));
                LOG.info("{}starting:{}", logPrefix, self);
                connectStunServer();
            } else {
                LOG.error("{}no public ip detected", logPrefix);
                throw new RuntimeException("no public ip detected");
            }
        }
    };

    private void connectStunServer() {
        stunServer = create(StunServerComp.class, new StunServerInit(self, ssInit.partners,
                new SSNetworkHook.Definition() {
                    @Override
                    public SSNetworkHook.SetupResult setup(ComponentProxy proxy, SSNetworkHook.SetupInit hookInit) {
                        Component[] comp = new Component[1];
                        comp[0] = proxy.create(NettyNetwork.class, new NettyInit(hookInit.adr));
                        return new SSNetworkHook.SetupResult(comp[0].getPositive(Network.class), comp);
                    }

                    @Override
                    public void start(ComponentProxy proxy, SSNetworkHook.SetupResult setupResult, SSNetworkHook.StartInit startInit) {
                        if(!startInit.started) {
                            trigger(Start.event, setupResult.components[0].control());
                        }
                    }

                    @Override
                    public void preStop(ComponentProxy proxy, SSNetworkHook.Tear hookTear) {
                    }
                }));
        connect(stunServer.getNegative(Timer.class), timer.getPositive(Timer.class));
        trigger(Start.event, stunServer.control());
    }

    public static class StunServerHostInit extends Init<HostComp> {

        public final StunServerInitHelper ssInit;

        public StunServerHostInit(Config config) {
            this.ssInit = new StunServerInitHelper(config);
        }
    }
    
    private static void systemSetup() {
        int serializerId = 128;
        serializerId = BasicSerializerSetup.registerBasicSerializers(serializerId);
        serializerId = StunSerializerSetup.registerSerializers(serializerId);

        ImmutableMap acceptedTraits = ImmutableMap.of(NatedTrait.class, Pair.with(0, (byte) 1));
        DecoratedAddress.setAcceptedTraits(new AcceptedTraits(acceptedTraits));
    }

    public static void main(String[] args) {
        systemSetup();
        if (Kompics.isOn()) {
            Kompics.shutdown();
        }
        Kompics.createAndStart(HostComp.class,
                new StunServerHostInit(ConfigFactory.load()),
                Runtime.getRuntime().availableProcessors(), 20); // Yes 20 is totally arbitrary
        try {
            Kompics.waitForTermination();
        } catch (InterruptedException ex) {
            System.exit(1);
        }
    }
}
