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
package se.sics.nat;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.Component;
import se.sics.kompics.Handler;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.network.Network;
import se.sics.kompics.network.netty.NettyInit;
import se.sics.kompics.network.netty.NettyNetwork;
import se.sics.kompics.timer.Timer;
import se.sics.ktoolbox.ipsolver.IpSolverComp;
import se.sics.ktoolbox.ipsolver.IpSolverPort;
import se.sics.ktoolbox.ipsolver.msg.GetIp;
import se.sics.ktoolbox.ipsolver.util.IpAddressStatus;
import se.sics.ktoolbox.ipsolver.util.IpHelper;
import se.sics.nat.common.croupier.GlobalCroupierView;
import se.sics.nat.hooks.NatNetworkHook;
import se.sics.nat.stun.NatReady;
import se.sics.nat.stun.client.SCNetworkHook;
import se.sics.nat.stun.upnp.UpnpPort;
import se.sics.nat.stun.upnp.msg.MapPorts;
import se.sics.nat.stun.upnp.msg.UnmapPorts;
import se.sics.nat.stun.upnp.util.Protocol;
import se.sics.p2ptoolbox.chunkmanager.ChunkManagerComp;
import se.sics.p2ptoolbox.chunkmanager.ChunkManagerConfig;
import se.sics.p2ptoolbox.croupier.CroupierComp;
import se.sics.p2ptoolbox.croupier.CroupierConfig;
import se.sics.p2ptoolbox.croupier.CroupierControlPort;
import se.sics.p2ptoolbox.croupier.CroupierPort;
import se.sics.p2ptoolbox.croupier.msg.CroupierJoin;
import se.sics.p2ptoolbox.croupier.msg.CroupierUpdate;
import se.sics.p2ptoolbox.util.config.SystemConfig;
import se.sics.p2ptoolbox.util.filters.IntegerOverlayFilter;
import se.sics.p2ptoolbox.util.helper.SystemConfigBuilder;
import se.sics.p2ptoolbox.util.nat.Nat;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;
import se.sics.p2ptoolbox.util.proxy.ComponentProxy;
import se.sics.p2ptoolbox.util.update.SelfAddressUpdatePort;
import se.sics.p2ptoolbox.util.update.SelfViewUpdatePort;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class NatSetup {

    private static final int BIND_RETRY = 3;

    private Logger LOG = LoggerFactory.getLogger(NatSetup.class);
    private String logPrefix = "";

    private final NatLauncherProxy proxy;
    private final Positive<Timer> timer;
    private final SystemConfigBuilder systemCBuilder;
    private final NatInitHelper natCBuilder;

    private Component ipSolver;
    private Component natDetection;
    private Component natTraverser;
    private Component globalCroupier;
    private SystemConfig systemConfig;

    public NatSetup(NatLauncherProxy proxy, Positive<Timer> timer, SystemConfigBuilder systemCBuilder) {
        this.proxy = proxy;
        this.timer = timer;
        this.systemCBuilder = systemCBuilder;
        this.natCBuilder = new NatInitHelper(systemCBuilder.getConfig());
    }

    //***********************PHASE1 = LOCAL_INTERFACE***************************
    private InetAddress localInterface;

    public void setup() {
        ipSolver = proxy.create(IpSolverComp.class, new IpSolverComp.IpSolverInit());
        proxy.subscribe(handleGetIp, ipSolver.getPositive(IpSolverPort.class));
    }

    public void start(boolean started) {
        if (!started) {
            proxy.trigger(Start.event, ipSolver.control());
            proxy.trigger(new GetIp.Req(EnumSet.of(GetIp.NetworkInterfacesMask.ALL)),
                    ipSolver.getPositive(IpSolverPort.class));
        }
    }

    private Handler handleGetIp = new Handler<GetIp.Resp>() {
        @Override
        public void handle(GetIp.Resp resp) {
            LOG.info("{}received local interfaces:{}", logPrefix, resp.addrs);
            if (!resp.addrs.isEmpty()) {
                Iterator<IpAddressStatus> it = resp.addrs.iterator();
                while (it.hasNext()) {
                    IpAddressStatus next = it.next();
                    if (IpHelper.isPublic(next.getAddr())) {
                        localInterface = next.getAddr();
                        break;
                    }
                }
                if (localInterface == null) {
                    it = resp.addrs.iterator();
                    while (it.hasNext()) {
                        IpAddressStatus next = it.next();
                        if (IpHelper.isPrivate(next.getAddr())) {
                            localInterface = next.getAddr();
                            break;
                        }
                    }
                }
                if (localInterface == null) {
                    localInterface = resp.addrs.get(0).getAddr();
                }
                if (resp.addrs.size() > 1) {
                    LOG.info("{}multiple ips detected, proceeding with:{}", logPrefix, localInterface);
                }
                LOG.info("{}starting with local interface:{}", logPrefix, localInterface);
                setupPhase2();
                startPhase2();
            } else {
                LOG.error("{}no private ip detected", logPrefix);
                throw new RuntimeException("no private ip detected");
            }
        }
    };

    //**********************PHASE2 = NAT_DETECTION******************************
    private void setupPhase2() {
        natDetection = proxy.create(NatDetectionComp.class, new NatDetectionComp.NatDetectionInit(
                new BasicAddress(localInterface, systemCBuilder.getSelfPort(), systemCBuilder.getSelfId()),
                new NatInitHelper(systemCBuilder.getConfig()),
                new SCNetworkHook.Definition() {

                    @Override
                    public SCNetworkHook.SetupResult setup(ComponentProxy hookProxy, SCNetworkHook.SetupInit hookInit) {
                        Component[] comp = new Component[1];
                        LOG.info("{}binding on stun:{}", new Object[]{logPrefix, hookInit.adr});
                        //network
                        comp[0] = hookProxy.create(NettyNetwork.class, new NettyInit(hookInit.adr));

                        return new SCNetworkHook.SetupResult(comp[0].getPositive(Network.class), comp);
                    }

                    @Override
                    public void start(ComponentProxy proxy, SCNetworkHook.SetupResult setupResult, SCNetworkHook.StartInit startInit) {
                        if (!startInit.started) {
                            proxy.trigger(Start.event, setupResult.components[0].control());
                        }
                    }

                    @Override
                    public void preStop(ComponentProxy proxy, SCNetworkHook.Tear hookTear) {
                    }
                }));

        proxy.connect(natDetection.getNegative(Timer.class), timer);
        proxy.subscribe(handleNatReady, natDetection.getPositive(NatDetectionPort.class));
    }

    private void startPhase2() {
        proxy.trigger(Start.event, natDetection.control());
    }

    private Handler handleNatReady = new Handler<NatReady>() {
        @Override
        public void handle(NatReady ready) {
            LOG.info("{}nat detected:{} public ip:{} private ip:{}",
                    new Object[]{logPrefix, ready.nat, ready.publicIp, localInterface});
            systemCBuilder.setSelfIp(ready.publicIp);
            systemCBuilder.setSelfNat(ready.nat);
            if (ready.nat.type.equals(Nat.Type.UPNP)) {
                proxy.subscribe(handleMapPorts, natDetection.getPositive(UpnpPort.class));
                proxy.subscribe(handleUnmapPorts, natDetection.getPositive(UpnpPort.class));
                Map<Integer, Pair<Protocol, Integer>> mapPort = new HashMap<Integer, Pair<Protocol, Integer>>();
                mapPort.put(systemCBuilder.getSelfPort(), Pair.with(Protocol.UDP, systemCBuilder.getSelfPort()));
                proxy.trigger(new MapPorts.Req(UUID.randomUUID(), mapPort), natDetection.getPositive(UpnpPort.class));
            } else {
                phase3();
            }
        }
    };

    private Handler handleMapPorts = new Handler<MapPorts.Resp>() {
        @Override
        public void handle(MapPorts.Resp resp) {
            LOG.info("{}received map:{}", logPrefix, resp.ports);
            int localPort = systemCBuilder.getSelfPort();
            int upnpPort = resp.ports.get(systemCBuilder.getSelfPort()).getValue1();
            if (localPort != upnpPort) {
                //TODO Alex - fix
                LOG.error("{}not handling yet upnp port different than local");
                throw new RuntimeException("not handling yet upnp port different than local");
            }
            phase3();
        }
    };

    private Handler handleUnmapPorts = new Handler<UnmapPorts.Resp>() {
        @Override
        public void handle(UnmapPorts.Resp resp) {
            LOG.info("received unmap:{}", resp.ports);
        }
    };

    private void buildSysConfig() {
        initiateSocketBind();
        LOG.debug("{}Socket successfully bound to ip :{} and port: {}",
                new Object[]{logPrefix, systemCBuilder.getSelfIp(), systemCBuilder.getSelfPort()});

        LOG.debug("{}Building the system configuration.");
        systemConfig = systemCBuilder.build();
    }

    /**
     * Try to bind on the socket and keep a reference of the socket.
     */
    private void initiateSocketBind() {

        LOG.debug("{}Initiating the binding on the socket to keep the port being used by some other service.", logPrefix);

        int retries = BIND_RETRY;
        Socket socket;
        while (retries > 0) {
//          Port gets updated, so needs to be reset.
            Integer selfPort = systemCBuilder.getSelfPort();
            try {

                LOG.debug("{}Trying to bind on the socket1 with ip: {} and port: {}",
                        new Object[]{logPrefix, localInterface, selfPort});
                socket = new Socket();
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress(localInterface, selfPort));
                socket.close();
                break;  // If exception is not thrown, break the loop.
            } catch (IOException e) {

                LOG.debug("{}Socket Bind failed, retrying.", logPrefix);
                systemCBuilder.setPort();
            }
            retries--;
        }

        if (retries <= 0) {
            LOG.error("{}Unable to bind on a socket, exiting.", logPrefix);
            throw new RuntimeException("Unable to identify port for the socket to bind on.");
        }
    }

    //******************PHASE3 = NAT, CARACAL, G_CROUPIER***********************
    private void phase3() {
        buildSysConfig();
        setupPhase3();
        startPhase3();
        proxy.startApp(new NatSetupResult(
                natTraverser.getPositive(Network.class),
                natTraverser.getPositive(SelfAddressUpdatePort.class),
                globalCroupier.getPositive(CroupierPort.class),
                systemConfig));
    }

    private void setupPhase3() {
        setupNat();
        setupGlobalCroupier();
    }

    private void startPhase3() {
        proxy.trigger(Start.event, natTraverser.control());
        proxy.trigger(Start.event, globalCroupier.control());
        proxy.trigger(new CroupierUpdate(new GlobalCroupierView()),
                globalCroupier.getNegative(SelfViewUpdatePort.class));
        proxy.trigger(new CroupierJoin(natCBuilder.croupierBoostrap),
                globalCroupier.getPositive(CroupierControlPort.class));
    }

    private void setupNat() {
        natTraverser = proxy.create(NatTraverserComp.class, new NatTraverserComp.NatTraverserInit(
                systemConfig,
                new NatInitHelper(systemConfig.config),
                new NatNetworkHook.Definition() {

                    @Override
                    public NatNetworkHook.SetupResult setup(ComponentProxy hookProxy, NatNetworkHook.SetupInit hookInit) {
                        Component[] comp = new Component[2];
                        if (!localInterface.equals(hookInit.adr.getIp())) {
                            LOG.info("{}binding on private:{}", logPrefix, localInterface.getHostAddress());
                            System.setProperty("altBindIf", localInterface.getHostAddress());
                        }
                        LOG.info("{}binding on nat:{}", new Object[]{logPrefix, hookInit.adr});
                        //network
                        comp[0] = hookProxy.create(NettyNetwork.class, new NettyInit(hookInit.adr));

                        //chunkmanager
                        comp[1] = hookProxy.create(ChunkManagerComp.class, new ChunkManagerComp.CMInit(systemConfig, new ChunkManagerConfig(systemConfig.config)));
                        hookProxy.connect(comp[1].getNegative(Network.class), comp[0].getPositive(Network.class));
                        hookProxy.connect(comp[1].getNegative(Timer.class), hookInit.timer);
                        return new NatNetworkHook.SetupResult(comp[1].getPositive(Network.class), comp);
                    }

                    @Override
                    public void start(ComponentProxy proxy, NatNetworkHook.SetupResult setupResult, NatNetworkHook.StartInit startInit) {
                        if (!startInit.started) {
                            proxy.trigger(Start.event, setupResult.components[0].control());
                            proxy.trigger(Start.event, setupResult.components[1].control());
                        }
                    }

                    @Override
                    public void preStop(ComponentProxy proxy, NatNetworkHook.Tear hookTear) {
                    }
                },
                new CroupierConfig(systemConfig.config)));

        proxy.connect(natTraverser.getNegative(Timer.class), timer);
    }

    private void setupGlobalCroupier() {
        CroupierConfig croupierConfig = new CroupierConfig(systemConfig.config);
        globalCroupier = proxy.create(CroupierComp.class,
                new CroupierComp.CroupierInit(systemConfig, croupierConfig, natCBuilder.globalCroupierOverlayId));
        proxy.connect(globalCroupier.getNegative(Timer.class), timer);
        proxy.connect(globalCroupier.getNegative(SelfAddressUpdatePort.class),
                natTraverser.getPositive(SelfAddressUpdatePort.class));
        proxy.connect(globalCroupier.getNegative(Network.class),
                natTraverser.getPositive(Network.class), new IntegerOverlayFilter(natCBuilder.globalCroupierOverlayId));
        proxy.connect(globalCroupier.getPositive(CroupierPort.class), natTraverser.getNegative(CroupierPort.class));
    }

}
