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
import se.sics.kompics.ChannelFilter;
import se.sics.kompics.Component;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.ConfigurationException;
import se.sics.kompics.ControlPort;
import se.sics.kompics.Fault;
import se.sics.kompics.Fault.ResolveAction;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.KompicsEvent;
import se.sics.kompics.Negative;
import se.sics.kompics.Port;
import se.sics.kompics.PortType;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.Timer;
import se.sics.p2ptoolbox.util.network.hooks.NetworkHook;
import se.sics.p2ptoolbox.util.network.hooks.NetworkResult;
import se.sics.p2ptoolbox.util.network.hooks.PortBindingHook;
import se.sics.ktoolbox.overlaymngr.OverlayMngrComp;
import se.sics.ktoolbox.overlaymngr.OverlayMngrComp.OverlayMngrInit;
import se.sics.ktoolbox.overlaymngr.OverlayMngrPort;
import se.sics.ktoolbox.overlaymngr.events.OMngrCroupier;
import se.sics.nat.hooks.BaseHooks;
import se.sics.nat.common.util.NatDetectionResult;
import se.sics.p2ptoolbox.util.network.hooks.PortBindingResult;
import se.sics.nat.stun.upnp.hooks.UpnpHook;
import se.sics.nat.stun.NatDetected;
import se.sics.nat.stun.StunClientPort;
import se.sics.nat.stun.client.StunClientComp;
import se.sics.nat.stun.client.StunClientKCWrapper;
import se.sics.nat.stun.upnp.msg.UpnpReady;
import se.sics.p2ptoolbox.croupier.CroupierPort;
import se.sics.p2ptoolbox.util.config.KConfigCore;
import se.sics.p2ptoolbox.util.config.impl.SystemKCWrapper;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;
import se.sics.p2ptoolbox.util.proxy.ComponentProxy;
import se.sics.p2ptoolbox.util.proxy.SystemHookSetup;
import se.sics.p2ptoolbox.util.status.Status;
import se.sics.p2ptoolbox.util.status.StatusPort;
import se.sics.p2ptoolbox.util.truefilters.SourcePortFilter;
import se.sics.p2ptoolbox.util.update.SelfViewUpdatePort;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class NatDetectionComp extends ComponentDefinition {

    private static final Logger LOG = LoggerFactory.getLogger(NatDetectionComp.class);
    private String logPrefix = "";

    private final Negative<StatusPort> status = provides(StatusPort.class);
    private final Positive<Timer> timer = requires(Timer.class);
    
    private final Negative<Network> network = provides(Network.class);
    private final Positive<OverlayMngrPort> overlayMngr = requires(OverlayMngrPort.class);

    private final SystemKCWrapper systemConfig;
    private final StunClientKCWrapper stunConfig;
    private final SystemHookSetup systemHooks;
    private final InetAddress localIp;

    //NAT Detection
    private NatUpnpParent upnpParent;
    private Triplet<NetworkParent, NetworkParent, NetworkParent> networkParents;
    private Component natDetection;
    private Component stunClient;

    private NatDetectionResult natDetectionResult;

    public NatDetectionComp(NatDetectionInit init) {
        systemConfig = new SystemKCWrapper(init.configCore);
        stunConfig = new StunClientKCWrapper(init.configCore);
        systemHooks = init.systemHooks;
        localIp = init.localIp;
        logPrefix = "<nid:" + systemConfig.id + "> ";
        LOG.info("{}initiating...", logPrefix);

        subscribe(handleStart, control);

        upnpParent = new NatUpnpParent();
        natDetectionResult = new NatDetectionResult();
    }

    //**************************CONTROL*****************************************
    Handler handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            LOG.info("{}starting...", logPrefix);
            upnpParent.connectUpnp();
            upnpParent.startUpnp(true);
            setupNetwork(true);
        }
    };

    @Override
    public ResolveAction handleFault(Fault fault) {
        LOG.error("{}fault:{} on comp:{}", new Object[]{logPrefix, fault.getCause().getMessage(), fault.getSourceCore().id()});
        return ResolveAction.ESCALATE;
    }

    private void finish() {
        trigger(new Status.Internal(new NatDetectionStatus.Phase2(natDetectionResult.getResult())), status);
    }

    //*****************STEP_1 - NETWORK, UPNP****************
    private void setupNetwork(boolean started) {
        networkParents = Triplet.with(new NetworkParent(UUID.randomUUID(), systemConfig.port + 1, true),
                new NetworkParent(UUID.randomUUID(), stunConfig.stunClientPorts.getValue0(), true),
                new NetworkParent(UUID.randomUUID(), stunConfig.stunClientPorts.getValue1(), true));
        networkParents.getValue0().bindPort(started);
        networkParents.getValue1().bindPort(started);
        networkParents.getValue2().bindPort(started);
    }
    
    public class NatUpnpParent implements UpnpHook.Parent {

        private UpnpHook.SetupResult upnpSetup;
        private final UpnpHook.Definition upnpHook;

        public NatUpnpParent() {
            upnpHook = systemHooks.getHook(NatDetectionHooks.RequiredHooks.UPNP.hookName,
                    NatDetectionHooks.UPNP_HOOK);
        }

        void connectUpnp() {
            upnpSetup = upnpHook.setup(new NatDetectionProxy(), new NatUpnpParent(), new UpnpHook.SetupInit());
        }

        void startUpnp(boolean started) {
            upnpHook.start(new NatDetectionProxy(), new NatUpnpParent(), upnpSetup, new UpnpHook.StartInit(started));
        }

        void stopUpnp() {
            upnpHook.preStop(new NatDetectionProxy(), new NatUpnpParent(), upnpSetup, new UpnpHook.TearInit());
        }

        @Override
        public void onResult(UpnpReady ready) {
            String upnpResult = (ready.externalIp.isPresent() ? ready.externalIp.get().toString() : "absent");
            LOG.info("{}upnp ready:{}", logPrefix, upnpResult);
            natDetectionResult.setUpnpReady(ready.externalIp);
            if (natDetectionResult.isReady()) {
                finish();
            }
        }
    }

    private class NetworkParent implements NetworkHook.Parent, PortBindingHook.Parent {

        final UUID id;
        final int tryPort;
        final boolean forcePort;
        int boundPort;
        DecoratedAddress adr;

        PortBindingHook.SetupResult portBindingSetup;
        NetworkHook.SetupResult networkSetup;
        NetworkResult networkResult;

        public NetworkParent(UUID id, int port, boolean forcePort) {
            this.id = id;
            this.tryPort = port;
            this.forcePort = forcePort;
        }

        void bindPort(boolean started) {
            PortBindingHook.Definition portBindingHook = systemHooks.getHook(BaseHooks.RequiredHooks.PORT_BINDING.hookName, BaseHooks.PORT_BINDING_HOOK);
            portBindingSetup = portBindingHook.setup(new NatDetectionProxy(), this,
                    new PortBindingHook.SetupInit());
            portBindingHook.start(new NatDetectionProxy(), this, portBindingSetup,
                    new PortBindingHook.StartInit(started, localIp, tryPort, forcePort));
        }

        @Override
        public void onResult(PortBindingResult result) {
            boundPort = result.boundPort;
            setNetwork(false);
        }

        void setNetwork(boolean started) {
            NetworkHook.Definition networkHook = systemHooks.getHook(BaseHooks.RequiredHooks.NETWORK.hookName, BaseHooks.NETWORK_HOOK);
            adr = DecoratedAddress.open(localIp, boundPort, stunConfig.system.id);
            networkSetup = networkHook.setup(new NatDetectionProxy(), this,
                    new NetworkHook.SetupInit(adr, Optional.of(localIp)));
            networkHook.start(new NatDetectionProxy(), this, networkSetup, new NetworkHook.StartInit(started));
        }

        @Override
        public void onResult(NetworkResult result) {
            this.networkResult = result;
            checkNetworkSetupComplete();
        }
    }

    private void checkNetworkSetupComplete() {
        if (networkParents.getValue0().networkResult != null && networkParents.getValue1().networkResult != null
                && networkParents.getValue2().networkResult != null) {
            connectStunClient();
            setupStunCroupier();
            connect(network, networkParents.getValue0().networkResult.getNetwork());
            trigger(new Status.Internal(new NatDetectionStatus.Phase1(networkParents.getValue0().adr)),status);
        }
    }
    

    //******************STEP_2 - TEMPORARY OVERLAY MNGR AND STUN****************
    private void connectStunClient() {
        stunClient = create(StunClientComp.class, new StunClientComp.StunClientInit(stunConfig.configCore, 
                Pair.with(networkParents.getValue1().adr, networkParents.getValue2().adr)));
        connect(stunClient.getNegative(Timer.class), timer);
        connect(stunClient.getNegative(Network.class), networkParents.getValue1().networkResult.getNetwork(),
                new SourcePortFilter(networkParents.getValue1().boundPort, false));
        connect(stunClient.getNegative(Network.class), networkParents.getValue2().networkResult.getNetwork(), 
                new SourcePortFilter(networkParents.getValue2().boundPort, false));
        //TODO Alex - connect failure detector port
        subscribe(handleNatReady, stunClient.getPositive(StunClientPort.class));
    }

    private void setupStunCroupier() {
        subscribe(handleStunCroupierReady, overlayMngr);

        OMngrCroupier.ConnectRequestBuilder reqBuilder = new OMngrCroupier.ConnectRequestBuilder(UUID.randomUUID());
        reqBuilder.setIdentifiers(stunConfig.globalCroupier, stunConfig.stunService);
        reqBuilder.setupCroupier(false);
        reqBuilder.connectTo(stunClient.getNegative(CroupierPort.class), stunClient.getPositive(SelfViewUpdatePort.class));
        LOG.info("{}waiting for croupier app...", logPrefix);
        trigger(reqBuilder.build(), overlayMngr);
    }

    Handler handleStunCroupierReady = new Handler<OMngrCroupier.ConnectResponse>() {
        @Override
        public void handle(OMngrCroupier.ConnectResponse resp) {
            LOG.info("{}stun croupier ready", logPrefix);
            trigger(Start.event, stunClient.control());
        }
    };

    private Handler handleNatReady = new Handler<NatDetected>() {
        @Override
        public void handle(NatDetected ready) {
            String printIp = (ready.publicIp.isPresent() ? ready.publicIp.get().getHostAddress().toString() : "x");
            LOG.info("{}nat detected:{} public ip:{}",
                    new Object[]{logPrefix, ready.nat, printIp});
            natDetectionResult.setNatReady(ready.nat, ready.publicIp);
            if (natDetectionResult.isReady()) {
                finish();
            }
        }
    };

    public static class NatDetectionInit extends Init<NatDetectionComp> {

        public final KConfigCore configCore;
        public final SystemHookSetup systemHooks;
        public final InetAddress localIp;

        public NatDetectionInit(KConfigCore configCore, SystemHookSetup systemHooks, InetAddress localIp) {
            this.configCore = configCore;
            this.systemHooks = systemHooks;
            this.localIp = localIp;
        }
    }

    public class NatDetectionProxy implements ComponentProxy {

        @Override
        public <P extends PortType> Positive<P> requires(Class<P> portType) {
            return NatDetectionComp.this.requires(portType);
        }

        @Override
        public <P extends PortType> Negative<P> provides(Class<P> portType) {
            return NatDetectionComp.this.provides(portType);
        }

        @Override
        public Negative<ControlPort> getControlPort() {
            return NatDetectionComp.this.control;
        }

        @Override
        public <T extends ComponentDefinition> Component create(Class<T> definition, Init<T> initEvent) {
            return NatDetectionComp.this.create(definition, initEvent);
        }

        @Override
        public <T extends ComponentDefinition> Component create(Class<T> definition, Init.None initEvent) {
            return NatDetectionComp.this.create(definition, initEvent);
        }

        @Override
        public <P extends PortType> Channel<P> connect(Positive<P> positive, Negative<P> negative) {
            return NatDetectionComp.this.connect(negative, positive);
        }

        @Override
        public <P extends PortType> Channel<P> connect(Positive<P> positive, Negative<P> negative, ChannelFilter filter) {
            return NatDetectionComp.this.connect(positive, negative, filter);
        }

        @Override
        public <P extends PortType> Channel<P> connect(Negative<P> negative, Positive<P> positive) {
            return NatDetectionComp.this.connect(negative, positive);
        }

        @Override
        public <P extends PortType> Channel<P> connect(Negative<P> negative, Positive<P> positive, ChannelFilter filter) {
            return NatDetectionComp.this.connect(negative, positive, filter);
        }

        @Override
        public <P extends PortType> void disconnect(Negative<P> negative, Positive<P> positive) {
            NatDetectionComp.this.disconnect(negative, positive);
        }

        @Override
        public <P extends PortType> void disconnect(Positive<P> positive, Negative<P> negative) {
            NatDetectionComp.this.disconnect(negative, positive);
        }

        @Override
        public <P extends PortType> void trigger(KompicsEvent e, Port<P> p) {
            NatDetectionComp.this.trigger(e, p);
        }

        @Override
        public <E extends KompicsEvent, P extends PortType> void subscribe(Handler<E> handler, Port<P> port) throws ConfigurationException {
            NatDetectionComp.this.subscribe(handler, port);
        }
    }
}
