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
package se.sics.nat.stun.server;

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
import se.sics.ktoolbox.ipsolver.hooks.IpSolverHook;
import se.sics.ktoolbox.ipsolver.hooks.IpSolverResult;
import se.sics.ktoolbox.overlaymngr.OverlayMngrComp;
import se.sics.ktoolbox.overlaymngr.OverlayMngrComp.OverlayMngrInit;
import se.sics.ktoolbox.overlaymngr.OverlayMngrPort;
import se.sics.ktoolbox.overlaymngr.events.OMngrCroupier;
import se.sics.nat.hooks.BaseHooks;
import se.sics.nat.network.NetworkKCWrapper;
import se.sics.nat.network.NetworkMngrKCWrapper;
import se.sics.nat.stun.server.StunServerComp.StunServerInit;
import se.sics.p2ptoolbox.croupier.CroupierPort;
import se.sics.p2ptoolbox.util.config.KConfigCore;
import se.sics.p2ptoolbox.util.config.impl.SystemKCWrapper;
import se.sics.p2ptoolbox.util.network.hooks.NetworkHook;
import se.sics.p2ptoolbox.util.network.hooks.NetworkResult;
import se.sics.p2ptoolbox.util.network.hooks.PortBindingHook;
import se.sics.p2ptoolbox.util.network.hooks.PortBindingResult;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;
import se.sics.p2ptoolbox.util.proxy.ComponentProxy;
import se.sics.p2ptoolbox.util.proxy.SystemHookSetup;
import se.sics.p2ptoolbox.util.truefilters.SourcePortFilter;
import se.sics.p2ptoolbox.util.update.SelfViewUpdatePort;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class StunServerHostComp extends ComponentDefinition {

    private static final Logger LOG = LoggerFactory.getLogger(StunServerHostComp.class);
    private String logPrefix = "";

    private final Positive<Timer> timer = requires(Timer.class);

    private final SystemKCWrapper systemConfig;
    private final NetworkMngrKCWrapper networkConfig;
    private final StunServerKCWrapper stunConfig;
    private final SystemHookSetup systemHooks;

    private IpSolverParent ipSolver = new IpSolverParent();
    private Triplet<NetworkParent, NetworkParent, NetworkParent> network;
    private Component overlayMngr;
    private Component failureDetector; //TODO Alex - create and connect failure detetcor
    private Component stunServer;

    public StunServerHostComp(StunServerHostInit init) {
        this.systemConfig = new SystemKCWrapper(init.configCore);
        this.networkConfig = new NetworkMngrKCWrapper(init.configCore);
        this.stunConfig = new StunServerKCWrapper(init.configCore);
        this.systemHooks = init.systemHooks;
        this.logPrefix = "<nid:" + systemConfig.id + "> ";
        LOG.info("{}initializing with seed:{}", logPrefix, systemConfig.seed);

        subscribe(handleStart, control);
    }

    private Handler handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            LOG.info("{}starting...", logPrefix);
            ipSolver.solveLocalIp(true);
        }
    };

    @Override
    public Fault.ResolveAction handleFault(Fault fault) {
        LOG.error("{}child component failure:{}", logPrefix, fault);
        System.exit(1);
        return Fault.ResolveAction.RESOLVED;
    }

    //*********************STEP_1 - LOCAL IP DETECTION**************************
    private class IpSolverParent implements IpSolverHook.Parent {

        private IpSolverHook.SetupResult ipSolverSetup;

        private void solveLocalIp(boolean started) {
            IpSolverHook.Definition ipSolver = systemHooks.getHook(BaseHooks.RequiredHooks.IP_SOLVER.hookName,
                    BaseHooks.IP_SOLVER_HOOK);
            ipSolverSetup = ipSolver.setup(new StunServerHostProxy(), this, new IpSolverHook.SetupInit());
            ipSolver.start(new StunServerHostProxy(), this, ipSolverSetup, new IpSolverHook.StartInit(started, networkConfig.rPrefferedInterface, networkConfig.rPrefferedInterfaces));
        }

        @Override
        public void onResult(IpSolverResult result) {
            if (!result.getIp().isPresent()) {
                LOG.error("{}could not get any ip", logPrefix);
                throw new RuntimeException("could not get any ip");
            }
            setupNetwork(result.getIp().get());
        }
    }

    private void setupNetwork(InetAddress localIp) {
        networkConfig.setLocalIp(localIp);
        network = Triplet.with(new NetworkParent(UUID.randomUUID(), systemConfig.port, true),
                new NetworkParent(UUID.randomUUID(), stunConfig.stunServerPorts.getValue0(), true),
                new NetworkParent(UUID.randomUUID(), stunConfig.stunServerPorts.getValue1(), true));
        network.getValue0().bindPort(false);
        network.getValue1().bindPort(false);
        network.getValue2().bindPort(false);
    }
    //************************STEP_2 - NETWORK**********************************

    private class NetworkParent implements NetworkHook.Parent, PortBindingHook.Parent {

        final UUID id;
        final int tryPort;
        final boolean forcePort;
        int boundPort;
        DecoratedAddress adr;

        PortBindingHook.SetupResult portBindingSetup = null;
        NetworkHook.SetupResult networkSetup = null;
        NetworkResult networkResult = null;

        public NetworkParent(UUID id, int port, boolean forcePort) {
            this.id = id;
            this.tryPort = port;
            this.forcePort = forcePort;
        }

        void bindPort(boolean started) {
            PortBindingHook.Definition portBindingHook = systemHooks.getHook(BaseHooks.RequiredHooks.PORT_BINDING.hookName, BaseHooks.PORT_BINDING_HOOK);
            portBindingSetup = portBindingHook.setup(new StunServerHostProxy(), this,
                    new PortBindingHook.SetupInit());
            portBindingHook.start(new StunServerHostProxy(), this, portBindingSetup,
                    new PortBindingHook.StartInit(started, networkConfig.localIp, tryPort, forcePort));
        }

        @Override
        public void onResult(PortBindingResult result) {
            boundPort = result.boundPort;
            setNetwork(false);
        }

        void setNetwork(boolean started) {
            NetworkHook.Definition networkHook = systemHooks.getHook(BaseHooks.RequiredHooks.NETWORK.hookName, BaseHooks.NETWORK_HOOK);
            adr = DecoratedAddress.open(networkConfig.localIp, boundPort, systemConfig.id);
            networkSetup = networkHook.setup(new StunServerHostProxy(), this,
                    new NetworkHook.SetupInit(adr, Optional.of(networkConfig.localIp)));
            networkHook.start(new StunServerHostProxy(), this, networkSetup, new NetworkHook.StartInit(started));
        }

        @Override
        public void onResult(NetworkResult result) {
            this.networkResult = result;
            checkNetworkComplete();
        }
    }

    private void checkNetworkComplete() {
        if (network.getValue0().networkResult != null && network.getValue1().networkResult != null
                && network.getValue2().networkResult != null) {
            connectOverlayMngr();
            connectStunServer();
            setupStunCroupier();
        }
    }

    //******************STEP_3 - OMNGR, STUN_CROUPIER, STUNS_SERVER*************

    private void connectOverlayMngr() {
        overlayMngr = create(OverlayMngrComp.class, new OverlayMngrInit(stunConfig.configCore, network.getValue0().adr));
        connect(overlayMngr.getNegative(Timer.class), timer);
        connect(overlayMngr.getNegative(Network.class), network.getValue0().networkResult.getNetwork());

        trigger(Start.event, overlayMngr.control());
    }

    private void connectStunServer() {
        stunServer = create(StunServerComp.class, new StunServerInit(stunConfig.configCore,
                Pair.with(network.getValue1().adr, network.getValue2().adr)));
        connect(stunServer.getNegative(Timer.class), timer);
        connect(stunServer.getNegative(Network.class), network.getValue1().networkResult.getNetwork(),
                new SourcePortFilter(network.getValue1().boundPort, false));
        connect(stunServer.getNegative(Network.class), network.getValue2().networkResult.getNetwork(),
                new SourcePortFilter(network.getValue2().boundPort, false));

    }

    private void setupStunCroupier() {
        subscribe(handleStunCroupierReady, overlayMngr.getPositive(OverlayMngrPort.class));

        OMngrCroupier.ConnectRequestBuilder reqBuilder = new OMngrCroupier.ConnectRequestBuilder(UUID.randomUUID());
        reqBuilder.setIdentifiers(stunConfig.globalCroupier.array(), stunConfig.stunService.array());
        reqBuilder.setupCroupier(false);
        reqBuilder.connectTo(stunServer.getNegative(CroupierPort.class), stunServer.getPositive(SelfViewUpdatePort.class));
        LOG.info("{}waiting for croupier app...", logPrefix);
        trigger(reqBuilder.build(), overlayMngr.getPositive(OverlayMngrPort.class));
    }

    Handler handleStunCroupierReady = new Handler<OMngrCroupier.ConnectResponse>() {
        @Override
        public void handle(OMngrCroupier.ConnectResponse resp) {
            LOG.info("{}app croupier ready", logPrefix);
            trigger(Start.event, stunServer.control());
        }
    };

    public static class StunServerHostInit extends Init<StunServerHostComp> {

        public final KConfigCore configCore;
        public final SystemHookSetup systemHooks;

        public StunServerHostInit(KConfigCore configCore, SystemHookSetup systemHooks) {
            this.configCore = configCore;
            this.systemHooks = systemHooks;
        }
    }

    public class StunServerHostProxy implements ComponentProxy {

        @Override
        public <P extends PortType> Positive<P> requires(Class<P> portType) {
            return StunServerHostComp.this.requires(portType);
        }

        @Override
        public <P extends PortType> Negative<P> provides(Class<P> portType) {
            return StunServerHostComp.this.provides(portType);
        }

        @Override
        public Negative<ControlPort> getControlPort() {
            return StunServerHostComp.this.control;
        }

        @Override
        public <T extends ComponentDefinition> Component create(Class<T> definition, Init<T> initEvent) {
            return StunServerHostComp.this.create(definition, initEvent);
        }

        @Override
        public <T extends ComponentDefinition> Component create(Class<T> definition, Init.None initEvent) {
            return StunServerHostComp.this.create(definition, initEvent);
        }

        @Override
        public <P extends PortType> Channel<P> connect(Positive<P> positive, Negative<P> negative) {
            return StunServerHostComp.this.connect(negative, positive);
        }

        @Override
        public <P extends PortType> Channel<P> connect(Positive<P> positive, Negative<P> negative, ChannelFilter filter) {
            return StunServerHostComp.this.connect(negative, positive, filter);
        }

        @Override
        public <P extends PortType> Channel<P> connect(Negative<P> negative, Positive<P> positive) {
            return StunServerHostComp.this.connect(negative, positive);
        }

        @Override
        public <P extends PortType> Channel<P> connect(Negative<P> negative, Positive<P> positive, ChannelFilter filter) {
            return StunServerHostComp.this.connect(negative, positive, filter);
        }

        @Override
        public <P extends PortType> void disconnect(Negative<P> negative, Positive<P> positive) {
            StunServerHostComp.this.disconnect(negative, positive);
        }

        @Override
        public <P extends PortType> void disconnect(Positive<P> positive, Negative<P> negative) {
            StunServerHostComp.this.disconnect(negative, positive);
        }

        @Override
        public <P extends PortType> void trigger(KompicsEvent e, Port<P> p) {
            StunServerHostComp.this.trigger(e, p);
        }

        @Override
        public <E extends KompicsEvent, P extends PortType> void subscribe(Handler<E> handler, Port<P> port) throws ConfigurationException {
            StunServerHostComp.this.subscribe(handler, port);
        }
    }
}
