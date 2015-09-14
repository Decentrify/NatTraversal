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
package se.sics.nat.example.node;

import com.google.common.collect.ImmutableMap;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.net.InetAddress;
import java.util.EnumSet;
import java.util.Iterator;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.Component;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Fault;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.Kompics;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;
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
import se.sics.ktoolbox.ipsolver.util.IpAddressStatus;
import se.sics.ktoolbox.ipsolver.util.IpHelper;
import se.sics.nat.NatDetectionComp;
import se.sics.nat.NatDetectionPort;
import se.sics.nat.NatInitHelper;
import se.sics.nat.hooks.NatNetworkHook;
import se.sics.nat.filters.NatTrafficFilter;
import se.sics.nat.NatTraverserComp;
import se.sics.nat.stun.NatReady;
import se.sics.nat.stun.StunClientPort;
import se.sics.nat.stun.client.StunClientComp;
import se.sics.nat.stun.client.StunClientComp.StunClientInit;
import se.sics.nat.common.NatTraverserConfig;
import se.sics.nat.common.croupier.GlobalCroupierView;
import se.sics.nat.NatSerializerSetup;
import se.sics.nat.hp.SHPSerializerSetup;
import se.sics.nat.hp.client.SHPClientComp;
import se.sics.nat.hp.client.SHPClientPort;
import se.sics.nat.hp.server.HPServerComp;
import se.sics.nat.pm.PMSerializerSetup;
import se.sics.nat.pm.client.PMClientComp;
import se.sics.nat.pm.server.PMServerComp;
import se.sics.nat.pm.server.PMServerPort;
import se.sics.nat.stun.StunSerializerSetup;
import se.sics.nat.stun.client.SCNetworkHook;
import se.sics.p2ptoolbox.croupier.CroupierComp;
import se.sics.p2ptoolbox.croupier.CroupierConfig;
import se.sics.p2ptoolbox.croupier.CroupierControlPort;
import se.sics.p2ptoolbox.croupier.CroupierPort;
import se.sics.p2ptoolbox.croupier.CroupierSerializerSetup;
import se.sics.p2ptoolbox.croupier.msg.CroupierDisconnected;
import se.sics.p2ptoolbox.croupier.msg.CroupierJoin;
import se.sics.p2ptoolbox.croupier.msg.CroupierUpdate;
import se.sics.p2ptoolbox.util.config.SystemConfig;
import se.sics.p2ptoolbox.util.filters.IntegerOverlayFilter;
import se.sics.p2ptoolbox.util.filters.PortTrafficFilter;
import se.sics.p2ptoolbox.util.helper.SystemConfigBuilder;
import se.sics.p2ptoolbox.util.nat.Nat;
import se.sics.p2ptoolbox.util.nat.NatedTrait;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;
import se.sics.p2ptoolbox.util.proxy.ComponentProxy;
import se.sics.p2ptoolbox.util.serializer.BasicSerializerSetup;
import se.sics.p2ptoolbox.util.traits.AcceptedTraits;
import se.sics.p2ptoolbox.util.update.SelfAddressUpdate;
import se.sics.p2ptoolbox.util.update.SelfAddressUpdatePort;
import se.sics.p2ptoolbox.util.update.SelfViewUpdatePort;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class HostComp extends ComponentDefinition {

    private static Logger LOG = LoggerFactory.getLogger(HostComp.class);
    private String logPrefix = "";

    private final HostInit init;

    private Component timer;
    private Component ipSolver;
    private Component natDetection;
    private Component nat;
    private Component globalCroupier;
    private Component node;

    private Positive<Network> natNetwork;
    private Positive<Network> directNetwork;
    private InetAddress privateIp;
    private SystemConfigBuilder systemConfigBuilder;
    private SystemConfig systemConfig;

    public HostComp(HostInit init) {
        LOG.info("{}initializing...", logPrefix);
        this.init = init;
        this.systemConfigBuilder = init.systemConfigBuilder;

        subscribe(handleStart, control);
        subscribe(handleStop, control);
    }

    //*************************CONTROL******************************************
    Handler handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            LOG.info("{}starting - timer, ipSolver", logPrefix);
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

    //**************************IP_DETECTION************************************
    private void connectTimer() {
        timer = create(JavaTimer.class, Init.NONE);
        trigger(Start.event, timer.control());
    }

    private void connectIpSolver() {
        ipSolver = create(IpSolverComp.class, new IpSolverComp.IpSolverInit());
        subscribe(handleGetIp, ipSolver.getPositive(IpSolverPort.class));
        trigger(Start.event, ipSolver.control());
        trigger(new GetIp.Req(EnumSet.of(GetIp.NetworkInterfacesMask.ALL)), ipSolver.getPositive(IpSolverPort.class));
    }

    public Handler handleGetIp = new Handler<GetIp.Resp>() {
        @Override
        public void handle(GetIp.Resp resp) {
            LOG.info("{}received ips:{}", logPrefix, resp.addrs);
            if (!resp.addrs.isEmpty()) {
                Iterator<IpAddressStatus> it = resp.addrs.iterator();
                while (it.hasNext()) {
                    IpAddressStatus next = it.next();
                    if (IpHelper.isPublic(next.getAddr())) {
                        privateIp = next.getAddr();
                        break;
                    }
                }
                if (privateIp == null) {
                    it = resp.addrs.iterator();
                    while (it.hasNext()) {
                        IpAddressStatus next = it.next();
                        if (IpHelper.isPrivate(next.getAddr())) {
                            privateIp = next.getAddr();
                            break;
                        }
                    }
                }
                if(privateIp == null) {
                    privateIp = resp.addrs.get(0).getAddr();
                }
                if (resp.addrs.size() > 1) {
                    LOG.warn("{}multiple ips detected, proceeding with:{}", logPrefix, privateIp);
                }
                LOG.info("{}starting: private ip:{}", logPrefix, privateIp);
                LOG.info("{}starting: stunClient", logPrefix);
                connectNatDetection();
            } else {
                LOG.error("{}no private ip detected", logPrefix);
                throw new RuntimeException("no private ip detected");
            }
        }
    };

    private void connectNatDetection() {
        natDetection = create(NatDetectionComp.class, new NatDetectionComp.NatDetectionInit(
                new BasicAddress(privateIp, systemConfigBuilder.getSelfPort(), systemConfigBuilder.getSelfId()),
                new NatInitHelper(ConfigFactory.load()),
                new SCNetworkHook.Definition() {

                    @Override
                    public SCNetworkHook.InitResult setUp(ComponentProxy proxy, SCNetworkHook.Init hookInit) {
                        Component[] comp = new Component[1];
                        comp[0] = proxy.create(NettyNetwork.class, new NettyInit(hookInit.adr));
                        proxy.trigger(Start.event, comp[0].control());
                        return new SCNetworkHook.InitResult(comp[0].getPositive(Network.class), comp);
                    }

                    @Override
                    public void tearDown(ComponentProxy proxy, SCNetworkHook.Tear hookTear) {
                        proxy.trigger(Stop.event, hookTear.components[0].control());
                    }
                }));

        connect(natDetection.getNegative(Timer.class), timer.getPositive(Timer.class));
        trigger(Start.event, natDetection.control());
        subscribe(handleNatReady, natDetection.getPositive(NatDetectionPort.class));
    }

    private Handler handleNatReady = new Handler<NatReady>() {
        @Override
        public void handle(NatReady ready) {
            LOG.info("{}nat detected:{} public ip:{} private ip:{}",
                    new Object[]{logPrefix, ready.nat, ready.publicIp.get(), privateIp});
            if (ready.publicIp.isPresent()) {
                systemConfigBuilder.setSelfIp(ready.publicIp.get());
            }
            systemConfigBuilder.setSelfNat(ready.nat);
            systemConfig = systemConfigBuilder.build();
            connectNatCroupier();
            connectApp();
        }
    };

    private void connectNatCroupier() {
        globalCroupier = create(CroupierComp.class, new CroupierComp.CroupierInit(systemConfig, init.croupierConfig, init.natInit.globalCroupierOverlayId));
        nat = create(NatTraverserComp.class, new NatTraverserComp.NatTraverserInit(
                systemConfig,
                new NatInitHelper(ConfigFactory.load()),
                new NatNetworkHook.Definition() {

                    @Override
                    public NatNetworkHook.InitResult setUp(ComponentProxy proxy, NatNetworkHook.Init hookInit) {
                        Component[] comp = new Component[1];
                        DecoratedAddress bindAddress = hookInit.adr;
                        if (!privateIp.equals(hookInit.adr.getIp())) {
                            LOG.info("{}binding on private:{}", logPrefix, privateIp.getHostAddress());
                            System.setProperty("altBindIf", privateIp.getHostAddress());
                        }
                        LOG.info("{}binding on public:{}", new Object[]{logPrefix, hookInit.adr});
                        comp[0] = proxy.create(NettyNetwork.class, new NettyInit(hookInit.adr));
                        proxy.trigger(Start.event, comp[0].control());
                        return new NatNetworkHook.InitResult(comp[0].getPositive(Network.class), comp);
                    }

                    @Override
                    public void tearDown(ComponentProxy proxy, NatNetworkHook.Tear hookTear) {
                        proxy.trigger(Stop.event, hookTear.components[0].control());
                    }

                },
                new CroupierConfig(ConfigFactory.load())));

        connect(globalCroupier.getNegative(Timer.class), timer.getPositive(Timer.class));
        connect(globalCroupier.getNegative(SelfAddressUpdatePort.class), nat.getPositive(SelfAddressUpdatePort.class));
        connect(globalCroupier.getNegative(Network.class), nat.getPositive(Network.class), new IntegerOverlayFilter(init.natInit.globalCroupierOverlayId));
        connect(nat.getNegative(Timer.class), timer.getPositive(Timer.class));
        connect(nat.getNegative(CroupierPort.class), globalCroupier.getPositive(CroupierPort.class));

        trigger(Start.event, nat.control());
        trigger(Start.event, globalCroupier.control());
        trigger(new CroupierUpdate(new GlobalCroupierView()), globalCroupier.getNegative(SelfViewUpdatePort.class));
        trigger(new CroupierJoin(init.natInit.croupierBoostrap), globalCroupier.getPositive(CroupierControlPort.class));
    }

    private void connectApp() {
        node = create(NodeComp.class, new NodeComp.NodeInit(systemConfig.self, init.ping));
        connect(node.getNegative(CroupierPort.class), globalCroupier.getPositive(CroupierPort.class));
        connect(node.getNegative(Network.class), nat.getPositive(Network.class));
        connect(node.getNegative(SelfAddressUpdatePort.class), nat.getPositive(SelfAddressUpdatePort.class));
        trigger(Start.event, node.control());
    }

    public static class HostInit extends Init<HostComp> {

        public final SystemConfigBuilder systemConfigBuilder;
        public final NatInitHelper natInit;
        public final CroupierConfig croupierConfig;

        public final boolean ping;

        public HostInit(Config config, boolean ping) {
            this.systemConfigBuilder = new SystemConfigBuilder(config);
            this.natInit = new NatInitHelper(config);
            this.croupierConfig = new CroupierConfig(config);
            this.ping = ping;
        }
    }

    private static void systemSetup() {
        int serializerId = 128;
        serializerId = BasicSerializerSetup.registerBasicSerializers(serializerId);
        serializerId = StunSerializerSetup.registerSerializers(serializerId);
        serializerId = CroupierSerializerSetup.registerSerializers(serializerId);
        serializerId = PMSerializerSetup.registerSerializers(serializerId);
        serializerId = SHPSerializerSetup.registerSerializers(serializerId);
        serializerId = NatSerializerSetup.registerSerializers(serializerId);
        serializerId = NodeSerializerSetup.registerSerializers(serializerId);

        ImmutableMap acceptedTraits = ImmutableMap.of(NatedTrait.class, 0);
        DecoratedAddress.setAcceptedTraits(new AcceptedTraits(acceptedTraits));
    }

    public static void main(String[] args) {
        Options options = new Options();
        Option pingOpt = new Option("ping", false, "ping target address");
        options.addOption(pingOpt);
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException ex) {
            LOG.error("command line parsing error");
            System.exit(1);
        }
        boolean ping = cmd.hasOption(pingOpt.getOpt());
        systemSetup();

        if (Kompics.isOn()) {
            Kompics.shutdown();
        }
        Kompics.createAndStart(HostComp.class,
                new HostComp.HostInit(ConfigFactory.load(), ping),
                Runtime.getRuntime().availableProcessors(), 20); // Yes 20 is totally arbitrary
        try {
            Kompics.waitForTermination();
        } catch (InterruptedException ex) {
            System.exit(1);
        }
    }
}
