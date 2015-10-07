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

import java.net.InetAddress;
import java.util.EnumSet;
import java.util.HashMap;
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
import se.sics.kompics.timer.Timer;
import se.sics.ktoolbox.ipsolver.msg.GetIp;
import se.sics.nat.hooks.NatAddressSolverHP;
import se.sics.nat.hooks.NatAddressSolverHook;
import se.sics.nat.hooks.NatAddressSolverResult;
import se.sics.nat.stun.NatReady;
import se.sics.nat.stun.upnp.UpnpPort;
import se.sics.nat.stun.upnp.msg.MapPorts;
import se.sics.nat.stun.upnp.msg.UnmapPorts;
import se.sics.nat.stun.upnp.util.Protocol;
import se.sics.p2ptoolbox.croupier.CroupierConfig;
import se.sics.p2ptoolbox.croupier.CroupierPort;
import se.sics.p2ptoolbox.util.config.SystemConfig;
import se.sics.p2ptoolbox.util.helper.SystemConfigBuilder;
import se.sics.p2ptoolbox.util.nat.Nat;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;
import se.sics.p2ptoolbox.util.proxy.ComponentProxy;
import se.sics.p2ptoolbox.util.proxy.SystemHookSetup;
import se.sics.p2ptoolbox.util.update.SelfAddressUpdatePort;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class NatSetup {

    private Logger LOG = LoggerFactory.getLogger(NatSetup.class);
    private String logPrefix = "";

    private final ComponentProxy proxy;
    private final NatSetupHP parent;
    private final SystemHookSetup systemHooks;
    private final SystemConfigBuilder systemConfigBuilder;
    private final EnumSet<GetIp.NetworkInterfacesMask> netInterfaces;
    private final Positive<Timer> timer;

    private NatAddressSolverHook.SetupResult addressSolverSetup;
    private Component natDetection;
    private Component natTraverser;

    private SystemConfig systemConfig;
    private InetAddress localInterface;
    private final NatInitHelper natInitHelper;

    public NatSetup(ComponentProxy proxy, NatSetupHP parent, SystemHookSetup systemHooks,
            NatSetupInit init) {
        this.proxy = proxy;
        this.parent = parent;
        this.systemHooks = systemHooks;
        if (!systemHooks.containsHooks(NatRequiredHooks.values())) {
            LOG.error("{}setup problem, hooks missing", logPrefix);
            throw new RuntimeException("setup problem, hooks missing");
        }
        this.natInitHelper = new NatInitHelper(init.configBuilder.getConfig());
        this.netInterfaces = init.netInterfaces;
        this.systemConfigBuilder = init.configBuilder;
        this.timer = init.timer;
    }

    //********************PHASE1 = LOCAL_ADDRESS_DETECTION**********************
    public void setup() {
        NatAddressSolverHook.Definition addressSolver = systemHooks.getHook(
                NatRequiredHooks.NAT_ADDRESS_SOLVER.toString(),
                NatAddressSolverHook.Definition.class);
        addressSolverSetup = addressSolver.setup(proxy, new NatAddressSolverHPImpl(), new NatAddressSolverHook.SetupInit());
    }

    public void start(boolean started) {
        NatAddressSolverHook.Definition addressSolver = systemHooks.getHook(
                NatRequiredHooks.NAT_ADDRESS_SOLVER.toString(),
                NatAddressSolverHook.Definition.class);
        addressSolver.start(proxy, new NatAddressSolverHPImpl(), addressSolverSetup,
                new NatAddressSolverHook.StartInit(started, netInterfaces));
    }

    public class NatAddressSolverHPImpl implements NatAddressSolverHP {

        @Override
        public void onResult(NatAddressSolverResult result) {
            localInterface = result.localIp;
            systemConfigBuilder.setSelfIp(result.localIp);
            systemConfigBuilder.setSelfPort(result.appPort);
            natInitHelper.setStunClientPorts(result.stunClientPorts);
            natDetection();
        }

        @Override
        public Pair<Integer, Integer> getStunClientPrefferedPorts() {
            return natInitHelper.stunClientPorts;
        }

        @Override
        public Integer getAppPrefferedPort() {
            return systemConfigBuilder.getSelfPort();
        }
    }

    //**********************PHASE2 = NAT_DETECTION******************************
    private void natDetection() {
        setupNatDetection();
        startNatDetection(false);
    }
    
    private void setupNatDetection() {
        natDetection = proxy.create(NatDetectionComp.class, new NatDetectionComp.NatDetectionInit(
                new BasicAddress(localInterface, systemConfigBuilder.getSelfPort(), systemConfigBuilder.getSelfId()),
                natInitHelper, systemHooks));

        proxy.connect(natDetection.getNegative(Timer.class), timer);
        proxy.subscribe(handleNatReady, natDetection.getPositive(NatDetectionPort.class));
    }

    private void startNatDetection(boolean started) {
        if (!started) {
            proxy.trigger(Start.event, natDetection.control());
        }
    }

    private Handler handleNatReady = new Handler<NatReady>() {
        @Override
        public void handle(NatReady ready) {
            LOG.info("{}nat detected:{} public ip:{} private ip:{}",
                    new Object[]{logPrefix, ready.nat, ready.publicIp, localInterface});
            systemConfigBuilder.setSelfIp(ready.publicIp);
            systemConfigBuilder.setSelfNat(ready.nat);
            if (ready.nat.type.equals(Nat.Type.UPNP)) {
                proxy.subscribe(handleMapPorts, natDetection.getPositive(UpnpPort.class));
                proxy.subscribe(handleUnmapPorts, natDetection.getPositive(UpnpPort.class));
                Map<Integer, Pair<Protocol, Integer>> mapPort = new HashMap<Integer, Pair<Protocol, Integer>>();
                mapPort.put(systemConfigBuilder.getSelfPort(), Pair.with(Protocol.UDP, systemConfigBuilder.getSelfPort()));
                proxy.trigger(new MapPorts.Req(UUID.randomUUID(), mapPort), natDetection.getPositive(UpnpPort.class));
            } else {
                natTraversal();
            }
        }
    };

    private Handler handleMapPorts = new Handler<MapPorts.Resp>() {
        @Override
        public void handle(MapPorts.Resp resp) {
            LOG.info("{}received map:{}", logPrefix, resp.ports);
            int localPort = systemConfigBuilder.getSelfPort();
            int upnpPort = resp.ports.get(systemConfigBuilder.getSelfPort()).getValue1();
            if (localPort != upnpPort) {
                //TODO Alex - fix
                LOG.error("{}not handling yet upnp port different than local");
                throw new RuntimeException("not handling yet upnp port different than local");
            }
            natTraversal();
        }
    };

    private Handler handleUnmapPorts = new Handler<UnmapPorts.Resp>() {
        @Override
        public void handle(UnmapPorts.Resp resp) {
            LOG.info("received unmap:{}", resp.ports);
        }
    };

    //***********************PHASE3 = NAT_TRAVERSAL*****************************
    private void natTraversal() {
        systemConfig = systemConfigBuilder.build();
        setupNatTraversal();
        startNatTraversal(false);
    }
    private void setupNatTraversal() {
        natTraverser = proxy.create(NatTraverserComp.class, new NatTraverserComp.NatTraverserInit(
                localInterface, systemConfig, natInitHelper, new CroupierConfig(systemConfig.config), systemHooks));
        proxy.connect(natTraverser.getNegative(Timer.class), timer);
    }

    private void startNatTraversal(boolean started) {
        if (!started) {
            proxy.trigger(Start.event, natTraverser.control());
        }
        parent.onResult(new NatSetupResult(
                natTraverser.getPositive(Network.class),
                natTraverser.getPositive(SelfAddressUpdatePort.class),
                natTraverser.getNegative(CroupierPort.class),
                systemConfig));
    }

    public static class NatSetupInit {

        public final EnumSet<GetIp.NetworkInterfacesMask> netInterfaces;
        public final Positive<Timer> timer;
        public final SystemConfigBuilder configBuilder;

        public NatSetupInit(EnumSet<GetIp.NetworkInterfacesMask> netInterfaces, Positive<Timer> timer,
                SystemConfigBuilder configBuilder) {
            this.netInterfaces = netInterfaces;
            this.configBuilder = configBuilder;
            this.timer = timer;
        }
    }
}
