///*
// * Copyright (C) 2009 Swedish Institute of Computer Science (SICS) Copyright (C)
// * 2009 Royal Institute of Technology (KTH)
// *
// * KompicsToolbox is free software; you can redistribute it and/or
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
//package se.sics.nat;
//
//import se.sics.nat.network.NetworkMngrKCWrapper;
//import se.sics.nat.hooks.BaseHooks;
//import se.sics.nat.network.NetworkKCWrapper;
//import com.google.common.base.Optional;
//import java.net.InetAddress;
//import java.util.UUID;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import se.sics.kompics.Channel;
//import se.sics.kompics.ChannelFilter;
//import se.sics.kompics.Component;
//import se.sics.kompics.ComponentDefinition;
//import se.sics.kompics.ConfigurationException;
//import se.sics.kompics.ControlPort;
//import se.sics.kompics.Fault;
//import se.sics.kompics.Handler;
//import se.sics.kompics.Init;
//import se.sics.kompics.Kill;
//import se.sics.kompics.KompicsEvent;
//import se.sics.kompics.Negative;
//import se.sics.kompics.Port;
//import se.sics.kompics.PortType;
//import se.sics.kompics.Positive;
//import se.sics.kompics.Start;
//import se.sics.kompics.network.Network;
//import se.sics.kompics.timer.Timer;
//import se.sics.ktoolbox.ipsolver.hooks.IpSolverHook;
//import se.sics.ktoolbox.ipsolver.hooks.IpSolverResult;
//import se.sics.p2ptoolbox.util.network.hooks.NetworkHook;
//import se.sics.p2ptoolbox.util.network.hooks.NetworkResult;
//import se.sics.p2ptoolbox.util.network.hooks.PortBindingHook;
//import se.sics.p2ptoolbox.util.network.hooks.PortBindingResult;
//import se.sics.ktoolbox.overlaymngr.OverlayMngrPort;
//import se.sics.nat.detection.NatDetectionComp;
//import se.sics.nat.traverser.NatTraverserComp;
//import se.sics.nat.detection.NatDetectionStatus;
//import se.sics.nat.detection.NatStatus;
//import se.sics.nat.traverser.NatTraverserComp.NatTraverserInit;
//import se.sics.p2ptoolbox.util.config.KConfigCore;
//import se.sics.p2ptoolbox.util.nat.Nat;
//import se.sics.p2ptoolbox.util.network.impl.BasicAddress;
//import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;
//import se.sics.p2ptoolbox.util.proxy.ComponentProxy;
//import se.sics.p2ptoolbox.util.proxy.SystemHookSetup;
//import se.sics.p2ptoolbox.util.status.Status;
//import se.sics.p2ptoolbox.util.status.StatusPort;
//import se.sics.p2ptoolbox.util.update.SelfAddressUpdate;
//import se.sics.p2ptoolbox.util.update.SelfAddressUpdatePort;
//
///**
// * @author Alex Ormenisan <aaor@kth.se>
// */
//public class NatMngrComp extends ComponentDefinition {
//
//    private static final Logger LOG = LoggerFactory.getLogger(NatMngrComp.class);
//    private String logPrefix = "";
//
//    private final Negative<StatusPort> status = provides(StatusPort.class);
//    private final Negative<Network> network = provides(Network.class);
//    private final Negative<SelfAddressUpdatePort> addressUpdate = provides(SelfAddressUpdatePort.class);
//    private final Positive<Timer> timer = requires(Timer.class);
//    private final Positive<OverlayMngrPort> overlayMngr = requires(OverlayMngrPort.class);
//    
//    private final NetworkMngrKCWrapper privateConfig;
//    private NetworkKCWrapper publicConfig;
//    private final SystemHookSetup systemHooks;
//
//    private IpSolverParent ipSolver = new IpSolverParent();
//    ;
//    private Component natDetection;
//    private NetworkParent systemNetwork;
//    private Component natTraverser;
//
//    private DecoratedAddress systemAdr;
//
//    public NatMngrComp(NatMngrInit init) {
//        this.privateConfig = init.config;
//        this.systemHooks = init.systemHooks;
//        this.logPrefix = "<nid:" + privateConfig.system.id + "> ";
//        LOG.info("{}initiating...", logPrefix);
//
//        subscribe(handleStart, control);
//    }
//
//    Handler handleStart = new Handler<Start>() {
//        @Override
//        public void handle(Start event) {
//            LOG.info("{}starting...", logPrefix);
//            ipSolver.solveLocalIp(true);
//        }
//    };
//
//    @Override
//    public Fault.ResolveAction handleFault(Fault fault) {
//        LOG.error("{}one of the networks faulted", logPrefix);
//        return Fault.ResolveAction.ESCALATE;
//    }
//
//    //*********************STEP_1 - LOCAL IP DETECTION**************************
//    private class IpSolverParent implements IpSolverHook.Parent {
//
//        private IpSolverHook.SetupResult ipSolverSetup;
//
//        private void solveLocalIp(boolean started) {
//            IpSolverHook.Definition ipSolver = systemHooks.getHook(BaseHooks.RequiredHooks.IP_SOLVER.hookName,
//                    BaseHooks.IP_SOLVER_HOOK);
//            ipSolverSetup = ipSolver.setup(new NetworkMngrProxy(), this, new IpSolverHook.SetupInit());
//            ipSolver.start(new NetworkMngrProxy(), this, ipSolverSetup, new IpSolverHook.StartInit(started, privateConfig.rPrefferedInterface, privateConfig.rPrefferedInterfaces));
//        }
//
//        @Override
//        public void onResult(IpSolverResult result) {
//            if (!result.getIp().isPresent()) {
//                LOG.error("{}could not get any ip", logPrefix);
//                throw new RuntimeException("could not get any ip");
//            }
//            localIpDetected(result.getIp().get());
//        }
//    }
//
//    private void localIpDetected(InetAddress localIp) {
//        privateConfig.setLocalIp(localIp);
//        connectNatDetection();
//    }
//
//    //***********************STEP_2 - NAT DETECTION*****************************
//    private void connectNatDetection() {
//        natDetection = create(NatDetectionComp.class, new NatDetectionComp.NatDetectionInit(privateConfig.configCore, systemHooks, privateConfig.localIp));
//        connect(natDetection.getNegative(Timer.class), timer);
//
//        subscribe(handleNatDetectionReady, natDetection.getPositive(StatusPort.class));
//
//        trigger(Start.event, natDetection.control());
//    }
//
//    Handler handleNatDetectionReady = new Handler<Status<NatDetectionStatus>>() {
//        @Override
//        public void handle(Status<NatDetectionStatus> event) {
//            LOG.info("{}nat detection ready", logPrefix);
//            trigger(Kill.event, natDetection.control());
//
//            if (event.status.nat.type.equals(Nat.Type.OPEN)) {
//                LOG.info("{}open node", logPrefix);
//                systemAdr = DecoratedAddress.open(systemAdr.getIp(), privateConfig.system.port, privateConfig.system.id);
//            } else if (event.status.nat.type.equals(Nat.Type.NAT)) {
//                LOG.info("{}detected nat:{} public ip:{}",
//                        new Object[]{logPrefix, event.status.nat.type, event.status.publicIp.get().getHostAddress()});
//                systemAdr = new DecoratedAddress(new BasicAddress(event.status.publicIp.get(), privateConfig.system.port, privateConfig.system.id));
//                systemAdr.addTrait(event.status.nat);
//                privateConfig.setLocalIp(event.status.publicIp.get());
//            } else {
//                LOG.error("{}not yet handling nat result:{}", logPrefix, event.status.nat);
//                throw new RuntimeException("not yet handling nat result:" + event.status.nat);
//            }
//            bindSystemNetwork();
//        }
//    };
//
//    //*********************STEP_3 - SETUP SYSTEM NETWORK************************
//    private void bindSystemNetwork() {
//        systemNetwork = new NetworkParent(UUID.randomUUID(), privateConfig.system.port, true);
//        systemNetwork.bindPort(false);
//    }
//    
//     private class NetworkParent implements NetworkHook.Parent, PortBindingHook.Parent {
//
//        final UUID id;
//        final int tryPort;
//        final boolean forcePort;
//        int boundPort;
//        DecoratedAddress adr;
//
//        PortBindingHook.SetupResult portBindingSetup = null;
//        NetworkHook.SetupResult networkSetup = null;
//        NetworkResult networkResult = null;
//
//        public NetworkParent(UUID id, int port, boolean forcePort) {
//            this.id = id;
//            this.tryPort = port;
//            this.forcePort = forcePort;
//        }
//
//        void bindPort(boolean started) {
//            PortBindingHook.Definition portBindingHook = systemHooks.getHook(BaseHooks.RequiredHooks.PORT_BINDING.hookName, BaseHooks.PORT_BINDING_HOOK);
//            portBindingSetup = portBindingHook.setup(new NetworkMngrProxy(), this,
//                    new PortBindingHook.SetupInit());
//            portBindingHook.start(new NetworkMngrProxy(), this, portBindingSetup,
//                    new PortBindingHook.StartInit(started, publicConfig.localIp, tryPort, forcePort));
//        }
//
//        @Override
//        public void onResult(PortBindingResult result) {
//            boundPort = result.boundPort;
//            setNetwork(false);
//        }
//
//        void setNetwork(boolean started) {
//            NetworkHook.Definition networkHook = systemHooks.getHook(BaseHooks.RequiredHooks.NETWORK.hookName, BaseHooks.NETWORK_HOOK);
//            adr = DecoratedAddress.open(privateConfig.localIp, boundPort, privateConfig.system.id);
//            networkSetup = networkHook.setup(new NetworkMngrProxy(), this,
//                    new NetworkHook.SetupInit(adr, Optional.of(publicConfig.localIp)));
//            networkHook.start(new NetworkMngrProxy(), this, networkSetup, new NetworkHook.StartInit(started));
//        }
//
//        @Override
//        public void onResult(NetworkResult result) {
//            this.networkResult = result;
//            networkComplete();
//        }
//    }
//
//     private void networkComplete() {
//         systemAdr = systemNetwork.adr;
//         trigger(new Status(new NatMngrStatus.Phase1(systemAdr)), status);
//         connectNatTraverser();
//     }
//    //************************STEP_4 - NAT TRAVERSER****************************
//    private void connectNatTraverser() {
//        natTraverser = create(NatTraverserComp.class, 
//                new NatTraverserInit(privateConfig.configCore, systemHooks, systemAdr));
//        connect(natTraverser.getNegative(Timer.class), timer);
//        connect(natTraverser.getNegative(Network.class), network.getPair());
//        connect(natTraverser.getNegative(OverlayMngrPort.class), overlayMngr);
//        //TODO Alex connect fd
//
//        subscribe(handleNatTraverserReady, natTraverser.getPositive(StatusPort.class));
//        subscribe(handleSelfAddressUpdate, natTraverser.getPositive(SelfAddressUpdatePort.class));
//
//        trigger(Start.event, natTraverser.control());
//    }
//
//    Handler handleNatTraverserReady = new Handler<Status<NatStatus>>() {
//        @Override
//        public void handle(Status<NatStatus> event) {
//            LOG.info("{}nat traverser ready", logPrefix);
//            trigger(new Status(new NatMngrStatus.Phase2()), status);
//        }
//    };
//
//    Handler handleSelfAddressUpdate = new Handler<SelfAddressUpdate>() {
//        @Override
//        public void handle(SelfAddressUpdate event) {
//            systemAdr = event.self;
//            LOG.info("{}changed self address:{}", new Object[]{logPrefix, systemAdr});
//        }
//    };
//
//    public static class NatMngrInit extends Init<NatMngrComp> {
//
//        public final NetworkMngrKCWrapper config;
//        public final SystemHookSetup systemHooks;
//
//        public NatMngrInit(KConfigCore config, SystemHookSetup systemHooks) {
//            this.config = new NetworkMngrKCWrapper(config);
//            this.systemHooks = systemHooks;
//        }
//    }
//
//    public class NetworkMngrProxy implements ComponentProxy {
//
//        @Override
//        public <P extends PortType> Positive<P> requires(Class<P> portType) {
//            return NatMngrComp.this.requires(portType);
//        }
//
//        @Override
//        public <P extends PortType> Negative<P> provides(Class<P> portType) {
//            return NatMngrComp.this.provides(portType);
//        }
//
//        @Override
//        public Negative<ControlPort> getControlPort() {
//            return NatMngrComp.this.control;
//        }
//
//        @Override
//        public <T extends ComponentDefinition> Component create(Class<T> definition, Init<T> initEvent) {
//            return NatMngrComp.this.create(definition, initEvent);
//        }
//
//        @Override
//        public <T extends ComponentDefinition> Component create(Class<T> definition, Init.None initEvent) {
//            return NatMngrComp.this.create(definition, initEvent);
//        }
//
//        @Override
//        public <P extends PortType> Channel<P> connect(Positive<P> positive, Negative<P> negative) {
//            return NatMngrComp.this.connect(negative, positive);
//        }
//
//        @Override
//        public <P extends PortType> Channel<P> connect(Positive<P> positive, Negative<P> negative, ChannelFilter filter) {
//            return NatMngrComp.this.connect(negative, positive, filter);
//        }
//
//        @Override
//        public <P extends PortType> Channel<P> connect(Negative<P> negative, Positive<P> positive) {
//            return NatMngrComp.this.connect(negative, positive);
//        }
//
//        @Override
//        public <P extends PortType> Channel<P> connect(Negative<P> negative, Positive<P> positive, ChannelFilter filter) {
//            return NatMngrComp.this.connect(negative, positive, filter);
//        }
//
//        @Override
//        public <P extends PortType> void disconnect(Negative<P> negative, Positive<P> positive) {
//            NatMngrComp.this.disconnect(negative, positive);
//        }
//
//        @Override
//        public <P extends PortType> void disconnect(Positive<P> positive, Negative<P> negative) {
//            NatMngrComp.this.disconnect(negative, positive);
//        }
//
//        @Override
//        public <P extends PortType> void trigger(KompicsEvent e, Port<P> p) {
//            NatMngrComp.this.trigger(e, p);
//        }
//
//        @Override
//        public <E extends KompicsEvent, P extends PortType> void subscribe(Handler<E> handler, Port<P> port) throws ConfigurationException {
//            NatMngrComp.this.subscribe(handler, port);
//        }
//    }
//}
