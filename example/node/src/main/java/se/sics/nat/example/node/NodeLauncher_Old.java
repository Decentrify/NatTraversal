///*
// * Copyright (C) 2009 Swedish Institute of Computer Science (SICS) Copyright (C)
// * 2009 Royal Institute of Technology (KTH)
// *
// * NatTraverser is free software; you can redistribute it and/or
// * modify it under the terms of the GNU General Public License
// * as published by the Free Software Foundation; either version 2
// * of the License, or (at your option) any later version.
// *
// * This program is distributed in the hope that it will be useful,
// * but WITHOUT ANY WARRANTY; without even the implied warranty of
// * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// * GNU General Public License for more details.
// *
// * You should have received a copy of the GNU General Public License
// * along with this program; if not, write to the Free Software
// * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
// */
//package se.sics.nat.example.node;
//
//import com.google.common.collect.ImmutableMap;
//import com.typesafe.config.Config;
//import com.typesafe.config.ConfigFactory;
//import java.net.InetAddress;
//import java.util.EnumSet;
//import java.util.HashMap;
//import java.util.Iterator;
//import java.util.Map;
//import java.util.UUID;
//import org.apache.commons.cli.CommandLine;
//import org.apache.commons.cli.CommandLineParser;
//import org.apache.commons.cli.DefaultParser;
//import org.apache.commons.cli.Option;
//import org.apache.commons.cli.Options;
//import org.apache.commons.cli.ParseException;
//import org.javatuples.Pair;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import se.sics.kompics.Component;
//import se.sics.kompics.ComponentDefinition;
//import se.sics.kompics.Fault;
//import se.sics.kompics.Handler;
//import se.sics.kompics.Init;
//import se.sics.kompics.Kompics;
//import se.sics.kompics.Positive;
//import se.sics.kompics.Start;
//import se.sics.kompics.Stop;
//import se.sics.kompics.network.Network;
//import se.sics.kompics.network.netty.NettyInit;
//import se.sics.kompics.network.netty.NettyNetwork;
//import se.sics.kompics.timer.Timer;
//import se.sics.kompics.timer.java.JavaTimer;
//import se.sics.ktoolbox.ipsolver.IpSolverComp;
//import se.sics.ktoolbox.ipsolver.IpSolverPort;
//import se.sics.ktoolbox.ipsolver.msg.GetIp;
//import se.sics.ktoolbox.ipsolver.util.IpAddressStatus;
//import se.sics.ktoolbox.ipsolver.util.IpHelper;
//import se.sics.nat.NatDetectionComp;
//import se.sics.nat.NatDetectionPort;
//import se.sics.nat.NatInitHelper;
//import se.sics.nat.hooks.NatNetworkHook;
//import se.sics.nat.NatTraverserComp;
//import se.sics.nat.stun.NatReady;
//import se.sics.nat.common.croupier.GlobalCroupierView;
//import se.sics.nat.NatSerializerSetup;
//import se.sics.nat.common.util.NatDetectionResult;
//import se.sics.nat.hp.SHPSerializerSetup;
//import se.sics.nat.pm.PMSerializerSetup;
//import se.sics.nat.stun.StunSerializerSetup;
//import se.sics.nat.stun.client.SCNetworkHook;
//import se.sics.nat.stun.upnp.UpnpPort;
//import se.sics.nat.stun.upnp.msg.MapPorts;
//import se.sics.nat.stun.upnp.msg.UnmapPorts;
//import se.sics.nat.stun.upnp.util.Protocol;
//import se.sics.p2ptoolbox.croupier.CroupierComp;
//import se.sics.p2ptoolbox.croupier.CroupierConfig;
//import se.sics.p2ptoolbox.croupier.CroupierControlPort;
//import se.sics.p2ptoolbox.croupier.CroupierPort;
//import se.sics.p2ptoolbox.croupier.CroupierSerializerSetup;
//import se.sics.p2ptoolbox.croupier.msg.CroupierJoin;
//import se.sics.p2ptoolbox.croupier.msg.CroupierUpdate;
//import se.sics.p2ptoolbox.util.config.SystemConfig;
//import se.sics.p2ptoolbox.util.filters.IntegerOverlayFilter;
//import se.sics.p2ptoolbox.util.helper.SystemConfigBuilder;
//import se.sics.p2ptoolbox.util.nat.Nat;
//import se.sics.p2ptoolbox.util.nat.NatedTrait;
//import se.sics.p2ptoolbox.util.network.impl.BasicAddress;
//import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;
//import se.sics.p2ptoolbox.util.proxy.ComponentProxy;
//import se.sics.p2ptoolbox.util.serializer.BasicSerializerSetup;
//import se.sics.p2ptoolbox.util.traits.AcceptedTraits;
//import se.sics.p2ptoolbox.util.update.SelfAddressUpdatePort;
//import se.sics.p2ptoolbox.util.update.SelfViewUpdatePort;
//
///**
// * @author Alex Ormenisan <aaor@kth.se>
// */
//public class HostLauncher_Old extends ComponentDefinition {
//
//    private static Logger LOG = LoggerFactory.getLogger(HostLauncher_Old.class);
//    private String logPrefix = "";
//
//    private final HostInit init;
//
//    private Component timerComp;
//    private Component ipSolverComp;
//    private Component natDetectionComp;
//    private Component natComp;
//    private Component globalCroupierComp;
//    private Component node;
//
//    private Positive<Network> natNetwork;
//    private Positive<Network> directNetwork;
//    private InetAddress localIp;
//    private SystemConfigBuilder systemConfigBuilder;
//    private SystemConfig systemConfig;
//
//    public HostLauncher_Old(HostInit init) {
//        LOG.info("{}initializing...", logPrefix);
//        this.init = init;
//        this.systemConfigBuilder = init.systemConfigBuilder;
//
//        subscribe(handleStart, control);
//        subscribe(handleStop, control);
//    }
//
//    //*****************************CONTROL**************************************
//    Handler handleStart = new Handler<Start>() {
//        @Override
//        public void handle(Start event) {
//            LOG.info("{}starting - timer, ipSolver", logPrefix);
//            connectTimer();
//            connectIpSolver();
//        }
//    };
//
//    Handler handleStop = new Handler<Stop>() {
//        @Override
//        public void handle(Stop event) {
//            LOG.info("{}stopping...", logPrefix);
//        }
//    };
//
//    @Override
//    public Fault.ResolveAction handleFault(Fault fault) {
//        LOG.error("{}child component failure:{}", logPrefix, fault);
//        System.exit(1);
//        return Fault.ResolveAction.RESOLVED;
//    }
//
//    //****************************ADDRESS_DETECTION*****************************
//    private void connectTimer() {
//        timerComp = create(JavaTimer.class, Init.NONE);
//        trigger(Start.event, timerComp.control());
//    }
//
//    private void connectIpSolver() {
//        ipSolverComp = create(IpSolverComp.class, new IpSolverComp.IpSolverInit());
//        subscribe(handleGetIp, ipSolverComp.getPositive(IpSolverPort.class));
//        trigger(Start.event, ipSolverComp.control());
//        trigger(new GetIp.Req(EnumSet.of(GetIp.NetworkInterfacesMask.ALL)), ipSolverComp.getPositive(IpSolverPort.class));
//    }
//
//    public Handler handleGetIp = new Handler<GetIp.Resp>() {
//        @Override
//        public void handle(GetIp.Resp resp) {
//            LOG.info("{}received ips:{}", logPrefix, resp.addrs);
//            if (!resp.addrs.isEmpty()) {
//                Iterator<IpAddressStatus> it = resp.addrs.iterator();
//                while (it.hasNext()) {
//                    IpAddressStatus next = it.next();
//                    if (IpHelper.isPublic(next.getAddr())) {
//                        localIp = next.getAddr();
//                        break;
//                    }
//                }
//                if (localIp == null) {
//                    it = resp.addrs.iterator();
//                    while (it.hasNext()) {
//                        IpAddressStatus next = it.next();
//                        if (IpHelper.isPrivate(next.getAddr())) {
//                            localIp = next.getAddr();
//                            break;
//                        }
//                    }
//                }
//                if (localIp == null) {
//                    localIp = resp.addrs.get(0).getAddr();
//                }
//                if (resp.addrs.size() > 1) {
//                    LOG.warn("{}multiple ips detected, proceeding with:{}", logPrefix, localIp);
//                }
//                LOG.info("{}starting: private ip:{}", logPrefix, localIp);
//                LOG.info("{}starting: stunClient", logPrefix);
//                connectNatDetection();
//            } else {
//                LOG.error("{}no private ip detected", logPrefix);
//                throw new RuntimeException("no private ip detected");
//            }
//        }
//    };
//
//    private void connectNatDetection() {
//        natDetectionComp = create(NatDetectionComp.class, new NatDetectionComp.NatDetectionInit(
//                new BasicAddress(localIp, systemConfigBuilder.getSelfPort(), systemConfigBuilder.getSelfId()),
//                new NatInitHelper(ConfigFactory.load()),
//                new SCNetworkHook.Definition() {
//
//                    @Override
//                    public SCNetworkHook.InitResult setUp(ComponentProxy proxy, SCNetworkHook.SetupInit hookInit) {
//                        Component[] comp = new Component[1];
//                        comp[0] = proxy.create(NettyNetwork.class, new NettyInit(hookInit.adr));
//                        proxy.trigger(Start.event, comp[0].control());
//                        return new SCNetworkHook.InitResult(comp[0].getPositive(Network.class), comp);
//                    }
//
//                    @Override
//                    public void tearDown(ComponentProxy proxy, SCNetworkHook.Tear hookTear) {
//                        proxy.trigger(Stop.event, hookTear.components[0].control());
//                    }
//                }));
//
//        connect(natDetectionComp.getNegative(Timer.class), timerComp.getPositive(Timer.class));
//        subscribe(handleNatReady, natDetectionComp.getPositive(NatDetectionPort.class));
//        trigger(Start.event, natDetectionComp.control());
//    }
//
//    private Handler handleNatReady = new Handler<NatReady>() {
//        @Override
//        public void handle(NatReady ready) {
//            LOG.info("{}nat detected:{} public ip:{} private ip:{}",
//                    new Object[]{logPrefix, ready.nat, ready.publicIp, localIp});
//            systemConfigBuilder.setSelfIp(ready.publicIp);
//            systemConfigBuilder.setSelfNat(ready.nat);
//            if (ready.nat.type.equals(Nat.Type.UPNP)) {
//                subscribe(handleMapPorts, natDetectionComp.getPositive(UpnpPort.class));
//                subscribe(handleUnmapPorts, natDetectionComp.getPositive(UpnpPort.class));
//                Map<Integer, Pair<Protocol, Integer>> mapPort = new HashMap<Integer, Pair<Protocol, Integer>>();
//                mapPort.put(systemConfigBuilder.getSelfPort(), Pair.with(Protocol.UDP, systemConfigBuilder.getSelfPort()));
//                trigger(new MapPorts.Req(UUID.randomUUID(), mapPort), natDetectionComp.getPositive(UpnpPort.class));
//            } else {
//                systemConfig = systemConfigBuilder.build();
//                connectRest();
//            }
//        }
//    };
//
//    private void connectRest() {
//        connectNatCroupier();
//        connectApp();
//    }
//
//    Handler handleMapPorts = new Handler<MapPorts.Resp>() {
//        @Override
//        public void handle(MapPorts.Resp resp) {
//            LOG.info("{}received map:{}", logPrefix, resp.ports);
//            int localPort = systemConfigBuilder.getSelfPort();
//            int upnpPort = resp.ports.get(systemConfigBuilder.getSelfPort()).getValue1();
//            if(localPort != upnpPort) {
//                //TODO Alex - fix
//                LOG.error("{}not handling yet upnp port different than local");
//                throw new RuntimeException("not handling yet upnp port different than local");
//            }
//            systemConfig = systemConfigBuilder.build();
//            connectRest();
//        }
//    };
//
//    Handler handleUnmapPorts = new Handler<UnmapPorts.Resp>() {
//        @Override
//        public void handle(UnmapPorts.Resp resp) {
//            LOG.info("received unmap:{}", resp.ports);
//        }
//    };
//
//    private void connectNatCroupier() {
//        globalCroupierComp = create(CroupierComp.class, new CroupierComp.CroupierInit(systemConfig, init.croupierConfig, init.natInit.globalCroupierOverlayId));
//        natComp = create(NatTraverserComp.class, new NatTraverserComp.NatTraverserInit(
//                systemConfig,
//                new NatInitHelper(ConfigFactory.load()),
//                new NatNetworkHook.Definition() {
//
//                    @Override
//                    public NatNetworkHook.InitResult setUp(ComponentProxy proxy, NatNetworkHook.Init hookInit) {
//                        Component[] comp = new Component[1];
//                        if (!localIp.equals(hookInit.adr.getIp())) {
//                            LOG.info("{}binding on private:{}", logPrefix, localIp.getHostAddress());
//                            System.setProperty("altBindIf", localIp.getHostAddress());
//                        }
//                        LOG.info("{}binding on public:{}", new Object[]{logPrefix, hookInit.adr});
//                        comp[0] = proxy.create(NettyNetwork.class, new NettyInit(hookInit.adr));
//                        proxy.trigger(Start.event, comp[0].control());
//                        return new NatNetworkHook.InitResult(comp[0].getPositive(Network.class), comp);
//                    }
//
//                    @Override
//                    public void tearDown(ComponentProxy proxy, NatNetworkHook.Tear hookTear) {
//                        proxy.trigger(Stop.event, hookTear.components[0].control());
//                    }
//
//                },
//                new CroupierConfig(ConfigFactory.load())));
//
//        connect(globalCroupierComp.getNegative(Timer.class), timerComp.getPositive(Timer.class));
//        connect(globalCroupierComp.getNegative(SelfAddressUpdatePort.class), natComp.getPositive(SelfAddressUpdatePort.class));
//        connect(globalCroupierComp.getNegative(Network.class), natComp.getPositive(Network.class), new IntegerOverlayFilter(init.natInit.globalCroupierOverlayId));
//        connect(natComp.getNegative(Timer.class), timerComp.getPositive(Timer.class));
//        connect(natComp.getNegative(CroupierPort.class), globalCroupierComp.getPositive(CroupierPort.class));
//
//        trigger(Start.event, natComp.control());
//        trigger(Start.event, globalCroupierComp.control());
//        trigger(new CroupierUpdate(new GlobalCroupierView()), globalCroupierComp.getNegative(SelfViewUpdatePort.class));
//        trigger(new CroupierJoin(init.natInit.croupierBoostrap), globalCroupierComp.getPositive(CroupierControlPort.class));
//    }
//
//    private void connectApp() {
//        node = create(NodeComp.class, new NodeComp.NodeInit(systemConfig.self, init.ping));
//        connect(node.getNegative(CroupierPort.class), globalCroupierComp.getPositive(CroupierPort.class));
//        connect(node.getNegative(Network.class), natComp.getPositive(Network.class));
//        connect(node.getNegative(SelfAddressUpdatePort.class), natComp.getPositive(SelfAddressUpdatePort.class));
//        trigger(Start.event, node.control());
//    }
//
//    public static class HostInit extends Init<HostLauncher_Old> {
//
//        public final SystemConfigBuilder systemConfigBuilder;
//        public final NatInitHelper natInit;
//        public final CroupierConfig croupierConfig;
//
//        public final boolean ping;
//
//        public HostInit(Config config, boolean ping) {
//            this.systemConfigBuilder = new SystemConfigBuilder(config);
//            this.natInit = new NatInitHelper(config);
//            this.croupierConfig = new CroupierConfig(config);
//            this.ping = ping;
//        }
//    }
//
//    private static void systemSetup() {
//        int serializerId = 128;
//        serializerId = BasicSerializerSetup.registerBasicSerializers(serializerId);
//        serializerId = StunSerializerSetup.registerSerializers(serializerId);
//        serializerId = CroupierSerializerSetup.registerSerializers(serializerId);
//        serializerId = PMSerializerSetup.registerSerializers(serializerId);
//        serializerId = SHPSerializerSetup.registerSerializers(serializerId);
//        serializerId = NatSerializerSetup.registerSerializers(serializerId);
//        serializerId = NodeSerializerSetup.registerSerializers(serializerId);
//
//        ImmutableMap acceptedTraits = ImmutableMap.of(NatedTrait.class, 0);
//        DecoratedAddress.setAcceptedTraits(new AcceptedTraits(acceptedTraits));
//    }
//
//    public static void main(String[] args) {
//        Options options = new Options();
//        Option pingOpt = new Option("ping", false, "ping target address");
//        options.addOption(pingOpt);
//        CommandLineParser parser = new DefaultParser();
//        CommandLine cmd = null;
//        try {
//            cmd = parser.parse(options, args);
//        } catch (ParseException ex) {
//            LOG.error("command line parsing error");
//            System.exit(1);
//        }
//        boolean ping = cmd.hasOption(pingOpt.getOpt());
//        systemSetup();
//
//        if (Kompics.isOn()) {
//            Kompics.shutdown();
//        }
//        Kompics.createAndStart(HostLauncher_Old.class,
//                new HostLauncher_Old.HostInit(ConfigFactory.load(), ping),
//                Runtime.getRuntime().availableProcessors(), 20); // Yes 20 is totally arbitrary
//        try {
//            Kompics.waitForTermination();
//        } catch (InterruptedException ex) {
//            System.exit(1);
//        }
//    }
//}
