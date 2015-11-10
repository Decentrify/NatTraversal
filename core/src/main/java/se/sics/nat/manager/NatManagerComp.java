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
package se.sics.nat.manager;

import com.google.common.base.Optional;
import java.net.InetAddress;
import java.util.UUID;
import org.javatuples.Pair;
import org.javatuples.Triplet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.Channel;
import se.sics.kompics.ChannelFilter;
import se.sics.kompics.Component;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.ConfigurationException;
import se.sics.kompics.ControlPort;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.Kill;
import se.sics.kompics.KompicsEvent;
import se.sics.kompics.Negative;
import se.sics.kompics.Port;
import se.sics.kompics.PortType;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.Timer;
import se.sics.ktoolbox.ipsolver.hooks.IpSolverHook;
import se.sics.ktoolbox.ipsolver.hooks.IpSolverResult;
import se.sics.ktoolbox.overlaymngr.OverlayMngrComp;
import se.sics.ktoolbox.overlaymngr.OverlayMngrPort;
import se.sics.ktoolbox.overlaymngr.events.OMngrCroupier;
import se.sics.nat.common.util.NatDetectionResult;
import se.sics.nat.detection.NatDetectionHooks;
import se.sics.nat.detection.NatStatus;
import se.sics.nat.hooks.BaseHooks;
import se.sics.nat.network.NetworkMngrKCWrapper;
import se.sics.nat.stun.NatDetected;
import se.sics.nat.stun.StunClientPort;
import se.sics.nat.stun.client.StunClientComp;
import se.sics.nat.stun.client.StunClientKCWrapper;
import se.sics.nat.stun.upnp.hooks.UpnpHook;
import se.sics.nat.stun.upnp.msg.UpnpReady;
import se.sics.nat.traverser.NatTraverserComp;
import se.sics.p2ptoolbox.croupier.CroupierPort;
import se.sics.p2ptoolbox.util.config.KConfigCore;
import se.sics.p2ptoolbox.util.config.impl.SystemKCWrapper;
import se.sics.p2ptoolbox.util.nat.Nat;
import se.sics.p2ptoolbox.util.nat.NatedTrait;
import se.sics.p2ptoolbox.util.network.hooks.NetworkHook;
import se.sics.p2ptoolbox.util.network.hooks.NetworkResult;
import se.sics.p2ptoolbox.util.network.hooks.PortBindingHook;
import se.sics.p2ptoolbox.util.network.hooks.PortBindingResult;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;
import se.sics.p2ptoolbox.util.other.ComponentHelper;
import se.sics.p2ptoolbox.util.proxy.ComponentProxy;
import se.sics.p2ptoolbox.util.proxy.SystemHookSetup;
import se.sics.p2ptoolbox.util.status.Status;
import se.sics.p2ptoolbox.util.status.StatusPort;
import se.sics.p2ptoolbox.util.truefilters.SourcePortFilter;
import se.sics.p2ptoolbox.util.update.SelfAddressUpdate;
import se.sics.p2ptoolbox.util.update.SelfAddressUpdatePort;
import se.sics.p2ptoolbox.util.update.SelfViewUpdatePort;

/**
 *
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class NatManagerComp extends ComponentDefinition {

    private static final Logger LOG = LoggerFactory.getLogger(NatManagerComp.class);
    private String logPrefix = "";

    private final Positive<Timer> timer = requires(Timer.class);
    private final Negative<StatusPort> status = provides(StatusPort.class);
    //indirectly provided by children
    private final Negative<Network> networkPort = provides(Network.class);
    private final Negative<OverlayMngrPort> overlayMngrPort = provides(OverlayMngrPort.class);
    private final Negative<SelfAddressUpdatePort> addressUpdate = provides(SelfAddressUpdatePort.class);

    private final SystemKCWrapper systemConfig;
    private final SystemHookSetup systemHooks;
    private NetworkMngrKCWrapper networkConfig;
    private StunClientKCWrapper stunConfig;

    private IpSolverHook.SetupResult ipSolverSetup;
    private Triplet<NetworkParent, NetworkParent, NetworkParent> auxNetwork;
    private NatUpnpParent upnp;
    private Component stunClient;
    private NatDetectionResult natDetectionResult;
    private Component overlayMngr;

    private NetworkParent network;
    private Component natTraverser;
    private DecoratedAddress systemAdr;

    public NatManagerComp(NatManagerInit init) {
        systemConfig = new SystemKCWrapper(init.configCore);
        stunConfig = new StunClientKCWrapper(systemConfig.configCore);
        systemHooks = init.systemHooks;
        networkConfig = new NetworkMngrKCWrapper(init.configCore);
        logPrefix = "<nid:" + systemConfig.id + "> ";
        LOG.info("{}initiating...", logPrefix);

        subscribe(handleStart, control);
    }

    Handler handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            LOG.info("{}starting...", logPrefix);
            step1(true);
        }
    };

    public void step1(boolean started) {
        LOG.info("{}solving local ip", logPrefix);
        IpSolverHook.Definition ipSolver = systemHooks.getHook(BaseHooks.RequiredHooks.IP_SOLVER.hookName,
                BaseHooks.IP_SOLVER_HOOK);
        IpSolverHook.Parent ipSolverParent = new IpSolverHook.Parent() {
            @Override
            public void onResult(IpSolverResult result) {
                if (!result.getIp().isPresent()) {
                    LOG.error("{}could not get any ip", logPrefix);
                    throw new RuntimeException("could not get any ip");
                }
                step2(result.getIp().get());
            }
        };
        ipSolverSetup = ipSolver.setup(new NatManagerProxy(), ipSolverParent, new IpSolverHook.SetupInit());
        ipSolver.start(new NatManagerProxy(), ipSolverParent, ipSolverSetup, new IpSolverHook.StartInit(started,
                networkConfig.rPrefferedInterface, networkConfig.rPrefferedInterfaces));
    }

    private void step2(InetAddress localIp) {
        LOG.info("{}solved local ip:{}", logPrefix, localIp.getHostAddress());
        boolean started = false;
        networkConfig.setLocalIp(localIp);
        setupLocalIpNetworks();
    }

    private void setupLocalIpNetworks() {
        boolean started = false;

        LOG.info("{}setting up stun networks", logPrefix);
        
        NetworkSetupCallback callback = new NetworkSetupCallback() {
            @Override
            public void networkSetupComplete() {
                if (auxNetwork.getValue0().networkPort != null && auxNetwork.getValue1().networkPort != null
                        && auxNetwork.getValue2().networkPort != null) {
                    step3();
                }
            }
        };
        auxNetwork = Triplet.with(
                new NetworkParent(UUID.randomUUID(), callback,
                        DecoratedAddress.open(networkConfig.localIp, systemConfig.port, systemConfig.id), true),
                new NetworkParent(UUID.randomUUID(), callback,
                        DecoratedAddress.open(networkConfig.localIp, stunConfig.stunClientPorts.getValue0(), systemConfig.id), true),
                new NetworkParent(UUID.randomUUID(), callback,
                        DecoratedAddress.open(networkConfig.localIp, stunConfig.stunClientPorts.getValue1(), systemConfig.id), true));
        auxNetwork.getValue0().bindPort();
        auxNetwork.getValue1().bindPort();
        auxNetwork.getValue2().bindPort();
    }

    //step3 nat detection upnp + stun + overlay
    private void step3() {
        LOG.info("{}stun networks aux:{} stun1:{} stun2:{}", new Object[]{logPrefix, auxNetwork.getValue0().boundAdr.getPort(), 
            auxNetwork.getValue1().boundAdr.getPort(), auxNetwork.getValue2().boundAdr.getPort()});
        natDetectionResult = new NatDetectionResult();
        upnp = new NatUpnpParent();
        upnp.setupUpnp();
        setupStun();
        setupTempOverlays();
    }

    private void setupStun() {
        stunClient = create(StunClientComp.class, new StunClientComp.StunClientInit(stunConfig.configCore,
                Pair.with(auxNetwork.getValue1().boundAdr, auxNetwork.getValue2().boundAdr)));
        connect(stunClient.getNegative(Timer.class), timer);
        connect(stunClient.getNegative(Network.class), auxNetwork.getValue1().networkPort,
                new SourcePortFilter(auxNetwork.getValue1().boundAdr.getPort(), false));
        connect(stunClient.getNegative(Network.class), auxNetwork.getValue2().networkPort,
                new SourcePortFilter(auxNetwork.getValue2().boundAdr.getPort(), false));
        //TODO Alex - connect failure detector port
    }

    private void setupTempOverlays() {
        overlayMngr = create(OverlayMngrComp.class, new OverlayMngrComp.OverlayMngrInit(systemConfig.configCore, auxNetwork.getValue0().boundAdr, true));

        connect(overlayMngr.getNegative(Timer.class), timer);
        connect(overlayMngr.getNegative(Network.class), auxNetwork.getValue0().networkPort);

        trigger(Start.event, overlayMngr.control());

        subscribe(handleStunCroupierReady, overlayMngr.getPositive(OverlayMngrPort.class));
        OMngrCroupier.ConnectRequestBuilder reqBuilder = new OMngrCroupier.ConnectRequestBuilder(UUID.randomUUID());
        reqBuilder.setIdentifiers(stunConfig.globalCroupier, stunConfig.stunService);
        reqBuilder.setupCroupier(false);
        reqBuilder.connectTo(stunClient.getNegative(CroupierPort.class), stunClient.getPositive(SelfViewUpdatePort.class));
        LOG.info("{}waiting for stun croupier...", logPrefix);
        trigger(reqBuilder.build(), overlayMngr.getPositive(OverlayMngrPort.class));
    }

    Handler handleStunCroupierReady = new Handler<OMngrCroupier.ConnectResponse>() {
        @Override
        public void handle(OMngrCroupier.ConnectResponse resp) {
            LOG.info("{}stun croupier ready", logPrefix);
            subscribe(handleNatReady, stunClient.getPositive(StunClientPort.class));
            trigger(Start.event, stunClient.control());
        }
    };

    private Handler handleNatReady = new Handler<NatDetected>() {
        @Override
        public void handle(NatDetected ready) {
            LOG.info("{}nat detection ready", logPrefix);
            natDetectionResult.setNatReady(ready.nat, ready.publicIp);
            if (natDetectionResult.isReady()) {
                step4();
            }
        }
    };

    private void step4() {
        Pair<NatedTrait, Optional<InetAddress>> result = natDetectionResult.getResult();
        if (result.getValue0().type.equals(Nat.Type.OPEN)) {
            LOG.info("{}open node ip:{}", logPrefix, result.getValue1().get());
            networkConfig.setPublicIp(result.getValue1().get());
            systemAdr = DecoratedAddress.open(result.getValue1().get(), systemConfig.port, systemConfig.id);
        } else if (result.getValue0().type.equals(Nat.Type.NAT)) {
            LOG.info("{}detected nat:{} public ip:{}",
                    new Object[]{logPrefix, result.getValue0().toString(), result.getValue1().get().getHostAddress()});
            if(!result.getValue0().mappingPolicy.equals(Nat.MappingPolicy.ENDPOINT_INDEPENDENT)) {
                LOG.info("{}nat:{} not supported yet", logPrefix, result.getValue0().toString());
                System.exit(1);
                return;
            }
            systemAdr = new DecoratedAddress(new BasicAddress(result.getValue1().get(), systemConfig.port, systemConfig.id));
            systemAdr.addTrait(result.getValue0());
            networkConfig.setPublicIp(result.getValue1().get());
        } else {
            LOG.error("{}not yet handling nat result:{}", logPrefix, result.getValue0());
            throw new RuntimeException("not yet handling nat result:" + result.getValue0());
        }
        cleanNatDetection();

        setupSystemNetwork();
    }

    private void setupSystemNetwork() {
        boolean started = false;

        NetworkSetupCallback callback = new NetworkSetupCallback() {
            @Override
            public void networkSetupComplete() {
                if (network.networkPort != null) {
                    systemAdr = network.boundAdr;
                    LOG.info("{}system network ready:{}", logPrefix, systemAdr);
                    step5();
                }
            }
        };
        network = new NetworkParent(UUID.randomUUID(), callback, systemAdr, true);
        network.bindPort();
    }

    private void step5() {
        cleanNatDetection();
        setupOverlaysNNatTraverser();
    }


    private void cleanNatDetection() {
        trigger(Kill.event, overlayMngr.control());
        trigger(Kill.event, stunClient.control());
        upnp.kill();
        auxNetwork.getValue0().kill();
        auxNetwork.getValue1().kill();
        auxNetwork.getValue2().kill();
    }

    private void setupOverlaysNNatTraverser() {
        overlayMngr = create(OverlayMngrComp.class, new OverlayMngrComp.OverlayMngrInit(systemConfig.configCore, network.boundAdr, false));
        connect(overlayMngr.getNegative(Timer.class), timer);
        
        natTraverser = create(NatTraverserComp.class,
                new NatTraverserComp.NatTraverserInit(systemConfig.configCore, systemHooks, systemAdr));
        connect(natTraverser.getNegative(Timer.class), timer);
        connect(natTraverser.getNegative(Network.class), network.networkPort);
        connect(natTraverser.getPositive(Network.class), networkPort);
        connect(natTraverser.getPositive(SelfAddressUpdatePort.class), addressUpdate);
        connect(natTraverser.getNegative(OverlayMngrPort.class), overlayMngr.getPositive(OverlayMngrPort.class));
        //TODO Alex connect fd

        connect(overlayMngr.getNegative(Network.class), networkPort.getPair());
        connect(overlayMngr.getPositive(OverlayMngrPort.class), overlayMngrPort);
        
        subscribe(handleNatTraverserReady, natTraverser.getPositive(StatusPort.class));
        subscribe(handleSelfAddressUpdate, natTraverser.getPositive(SelfAddressUpdatePort.class));
        
        trigger(Start.event, natTraverser.control());
        trigger(Start.event, overlayMngr.control());
    }
    
    Handler handleNatTraverserReady = new Handler<Status.Internal<NatStatus>>() {
        @Override
        public void handle(Status.Internal<NatStatus> event) {
            LOG.info("{}nat traverser ready", logPrefix);
            
            
            trigger(new Status.Internal(new NatManagerReady(systemAdr)), status);
        }
    };
    
    Handler handleSelfAddressUpdate = new Handler<SelfAddressUpdate>() {
        @Override
        public void handle(SelfAddressUpdate event) {
            LOG.trace("{}received self address update:{}", logPrefix, event.id);
            systemAdr = event.self;
            LOG.info("{}changed self address:{}", new Object[]{logPrefix, systemAdr});
        }
    };

    public static class NatManagerInit extends Init<NatManagerComp> {

        public final KConfigCore configCore;
        public final SystemHookSetup systemHooks;

        public NatManagerInit(KConfigCore configCore, SystemHookSetup systemHooks) {
            this.configCore = configCore;
            this.systemHooks = systemHooks;
        }
    }

    public class NatUpnpParent {

        private UpnpHook.Definition upnpHook;
        private UpnpHook.Parent upnpParent;
        private UpnpHook.SetupResult upnpSetup;

        private NatUpnpParent() {
            upnpHook = systemHooks.getHook(NatDetectionHooks.RequiredHooks.UPNP.hookName,
                    NatDetectionHooks.UPNP_HOOK);
            upnpParent = new UpnpHook.Parent() {
                @Override
                public void onResult(UpnpReady ready) {
                    String upnpResult = (ready.externalIp.isPresent() ? ready.externalIp.get().toString() : "absent");
                    LOG.info("{}upnp ready:{}", logPrefix, upnpResult);
                    natDetectionResult.setUpnpReady(ready.externalIp);
                    if (natDetectionResult.isReady()) {
                        step4();
                    }
                }
            };
        }

        public void setupUpnp() {
            boolean started = false;
            upnpSetup = upnpHook.setup(new NatManagerProxy(), upnpParent, new UpnpHook.SetupInit());
            upnpHook.start(new NatManagerProxy(), upnpParent, upnpSetup, new UpnpHook.StartInit(started));
        }

        public void kill() {
            upnpHook.preStop(new NatManagerProxy(), upnpParent, upnpSetup, new UpnpHook.TearInit());
        }
    }

    private static interface NetworkSetupCallback {

        public void networkSetupComplete();
    }

    private class NetworkParent {

        public final UUID id;
        public final DecoratedAddress tryAdr;
        public final boolean forcePort;
        public final NetworkSetupCallback callback;

        private final PortBindingHook.Definition portBindingHook;
        private final PortBindingHook.Parent portParent;
        private final NetworkHook.Definition networkHook;
        private final NetworkHook.Parent networkParent;

        public PortBindingHook.SetupResult portBindingSetup;
        public DecoratedAddress boundAdr;

        public NetworkHook.SetupResult networkSetup;

        public Positive<Network> networkPort = null;

        public NetworkParent(UUID id, final NetworkSetupCallback callback, final DecoratedAddress tryAdr,
                boolean forcePort) {
            this.id = id;
            this.tryAdr = tryAdr;
            this.forcePort = forcePort;
            this.callback = callback;

            portBindingHook = systemHooks.getHook(BaseHooks.RequiredHooks.PORT_BINDING.hookName, BaseHooks.PORT_BINDING_HOOK);
            portParent = new PortBindingHook.Parent() {
                @Override
                public void onResult(PortBindingResult result) {
                    boundAdr = tryAdr.changePort(result.boundPort);
                    setNetwork();
                }
            };

            networkHook = systemHooks.getHook(BaseHooks.RequiredHooks.NETWORK.hookName, BaseHooks.NETWORK_HOOK);
            networkParent = new NetworkHook.Parent() {
                @Override
                public void onResult(NetworkResult result) {
                    networkPort = result.getNetwork();
                    callback.networkSetupComplete();
                }
            };
        }

        public void bindPort() {
            boolean started = false;
            portBindingSetup = portBindingHook.setup(new NatManagerProxy(), portParent,
                    new PortBindingHook.SetupInit());
            portBindingHook.start(new NatManagerProxy(), portParent, portBindingSetup,
                    new PortBindingHook.StartInit(started, tryAdr.getIp(), tryAdr.getPort(), forcePort));
        }

        public void setNetwork() {
            boolean started = false;
            networkSetup = networkHook.setup(new NatManagerProxy(), networkParent,
                    new NetworkHook.SetupInit(boundAdr, Optional.of(networkConfig.localIp)));
            networkHook.start(new NatManagerProxy(), networkParent, networkSetup, new NetworkHook.StartInit(started));
        }

        public void kill() {
            portBindingHook.preStop(new NatManagerProxy(), portParent, portBindingSetup, new PortBindingHook.TearInit());
            networkHook.preStop(new NatManagerProxy(), networkParent, networkSetup, new NetworkHook.TearInit());
        }
    }

    public class NatManagerProxy implements ComponentProxy {

        @Override
        public <P extends PortType> Positive<P> requires(Class<P> portType) {
            return NatManagerComp.this.requires(portType);
        }

        @Override
        public <P extends PortType> Negative<P> provides(Class<P> portType) {
            return NatManagerComp.this.provides(portType);
        }

        @Override
        public Negative<ControlPort> getControlPort() {
            return NatManagerComp.this.control;
        }

        @Override
        public <T extends ComponentDefinition> Component create(Class<T> definition, Init<T> initEvent) {
            return NatManagerComp.this.create(definition, initEvent);
        }

        @Override
        public <T extends ComponentDefinition> Component create(Class<T> definition, Init.None initEvent) {
            return NatManagerComp.this.create(definition, initEvent);
        }

        @Override
        public <P extends PortType> Channel<P> connect(Positive<P> positive, Negative<P> negative) {
            return NatManagerComp.this.connect(negative, positive);
        }

        @Override
        public <P extends PortType> Channel<P> connect(Positive<P> positive, Negative<P> negative, ChannelFilter filter) {
            return NatManagerComp.this.connect(negative, positive, filter);
        }

        @Override
        public <P extends PortType> Channel<P> connect(Negative<P> negative, Positive<P> positive) {
            return NatManagerComp.this.connect(negative, positive);
        }

        @Override
        public <P extends PortType> Channel<P> connect(Negative<P> negative, Positive<P> positive, ChannelFilter filter) {
            return NatManagerComp.this.connect(negative, positive, filter);
        }

        @Override
        public <P extends PortType> void disconnect(Negative<P> negative, Positive<P> positive) {
            NatManagerComp.this.disconnect(negative, positive);
        }

        @Override
        public <P extends PortType> void disconnect(Positive<P> positive, Negative<P> negative) {
            NatManagerComp.this.disconnect(negative, positive);
        }

        @Override
        public <P extends PortType> void trigger(KompicsEvent e, Port<P> p) {
            NatManagerComp.this.trigger(e, p);
        }

        @Override
        public <E extends KompicsEvent, P extends PortType> void subscribe(Handler<E> handler, Port<P> port) throws ConfigurationException {
            NatManagerComp.this.subscribe(handler, port);
        }
    }
}
