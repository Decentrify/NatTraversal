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

import se.sics.nat.filters.NatTrafficFilter;
import se.sics.nat.hooks.NatNetworkHook;
import com.google.common.base.Optional;
import com.google.common.collect.Sets;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.Channel;
import se.sics.kompics.ChannelFilter;
import se.sics.kompics.ClassMatchedHandler;
import se.sics.kompics.Component;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.ConfigurationException;
import se.sics.kompics.ControlPort;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.KompicsEvent;
import se.sics.kompics.Negative;
import se.sics.kompics.Port;
import se.sics.kompics.PortType;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.Stop;
import se.sics.kompics.network.Msg;
import se.sics.kompics.network.Network;
import se.sics.kompics.network.Transport;
import se.sics.kompics.timer.CancelPeriodicTimeout;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.Timeout;
import se.sics.kompics.timer.Timer;
import se.sics.nat.hp.client.SHPClientPort;
import se.sics.nat.hp.client.msg.OpenConnection;
import se.sics.nat.common.NatTraverserConfig;
import se.sics.nat.filters.NatInternalFilter;
import se.sics.nat.msg.NatConnection.Close;
import se.sics.nat.msg.NatConnection.Heartbeat;
import se.sics.nat.util.NatTraverserFeasibility;
import se.sics.nat.hp.client.SHPClientComp;
import se.sics.nat.hp.server.HPServerComp;
import se.sics.nat.pm.client.PMClientComp;
import se.sics.nat.pm.server.PMServerComp;
import se.sics.nat.pm.server.PMServerPort;
import se.sics.p2ptoolbox.croupier.CroupierConfig;
import se.sics.p2ptoolbox.croupier.CroupierPort;
import se.sics.p2ptoolbox.util.config.SystemConfig;
import se.sics.p2ptoolbox.util.filters.AndFilter;
import se.sics.p2ptoolbox.util.filters.NotFilter;
import se.sics.p2ptoolbox.util.nat.NatedTrait;
import se.sics.p2ptoolbox.util.network.ContentMsg;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;
import se.sics.p2ptoolbox.util.network.impl.BasicContentMsg;
import se.sics.p2ptoolbox.util.network.impl.BasicHeader;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;
import se.sics.p2ptoolbox.util.network.impl.DecoratedHeader;
import se.sics.p2ptoolbox.util.proxy.ComponentProxy;
import se.sics.p2ptoolbox.util.proxy.Hook;
import se.sics.p2ptoolbox.util.proxy.SystemHookSetup;
import se.sics.p2ptoolbox.util.update.SelfAddress;
import se.sics.p2ptoolbox.util.update.SelfAddressUpdate;
import se.sics.p2ptoolbox.util.update.SelfAddressUpdatePort;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class NatTraverserComp extends ComponentDefinition {

    private static final Logger LOG = LoggerFactory.getLogger(NatTraverserComp.class);
    private String logPrefix = "";

    public static enum RequiredHooks implements Hook.Required {

        NAT_NETWORK
    }

    private final Negative<SelfAddressUpdatePort> providedSAUpdate = provides(SelfAddressUpdatePort.class);
    private final Negative<Network> providedNetwork = provides(Network.class);
    private final Positive<CroupierPort> peerSampling = requires(CroupierPort.class);
    private final Positive<Timer> timer = requires(Timer.class);

    private final ChannelFilter<Msg, Boolean> handleTraffic = new AndFilter(new NatTrafficFilter(), new NotFilter(new NatInternalFilter()));
    private final ChannelFilter<Msg, Boolean> forwardTraffic = new AndFilter(new NotFilter(new NatTrafficFilter()), new NotFilter(new NatInternalFilter()));

    private Positive<Network> network;
    private Positive<SHPClientPort> hpClient;

    private final NatTraverserConfig natConfig;
    private final SystemConfig systemConfig;
    private final int globalCroupierOverlayId;
    private final CroupierConfig croupierConfig;
    private final List<DecoratedAddress> croupierBootstrap;

    private final InetAddress localIp;
    private DecoratedAddress self;

    private UUID internalStateCheckId;

    private final ConnectionTracker connectionTracker;
    private final ConnectionMaker connectionMaker;
    private final TrafficTracker trafficTracker;
    private final ComponentTracker compTracker;

    public NatTraverserComp(NatTraverserInit init) {
        this.localIp = init.localIp;
        this.systemConfig = init.systemConfig;
        this.natConfig = init.natConfig;
        this.self = systemConfig.self;
        this.logPrefix = self.getBase() + " ";
        LOG.info("{}initiating...", logPrefix);
        this.globalCroupierOverlayId = init.globalCroupierOverlayId;
        this.croupierConfig = init.croupierConfig;
        this.croupierBootstrap = init.croupierBootstrap;

        this.connectionTracker = new ConnectionTracker();
        this.connectionMaker = new ConnectionMaker();
        this.trafficTracker = new TrafficTracker();
        NatNetworkHook.Definition natNetwork = init.systemHooks.getHook(RequiredHooks.NAT_NETWORK.toString(), NatNetworkHook.Definition.class);
        this.compTracker = new ComponentTracker(natNetwork);
        subscribe(handleStart, control);
        subscribe(handleStop, control);
        subscribe(handleInternalStateCheck, timer);

        compTracker.setup();
        //connection tracker
        subscribe(connectionTracker.handleHeartbeat, timer);
        subscribe(connectionTracker.handleHeartbeatCheck, timer);
        subscribe(connectionTracker.handlePartnerHeartbeat, network);
        subscribe(connectionTracker.handleNetCloseConnection, network);

        //connection maker
        subscribe(connectionMaker.handleOpenConnectionSuccess, hpClient);
        subscribe(connectionMaker.handleOpenConnectionFail, hpClient);

        //traffic tracker
        subscribe(trafficTracker.handleLocal, providedNetwork);
        subscribe(trafficTracker.handleNetwork, network);
    }

    //********************************CONTROL***********************************
    Handler handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            LOG.info("{}starting with self:{}", logPrefix, self);
            compTracker.start(true);

            scheduleInternalStateCheck();
            connectionTracker.scheduleHeartbeat();
            connectionTracker.scheduleHeartbeatCheck();
        }
    };

    Handler handleStop = new Handler<Stop>() {
        @Override
        public void handle(Stop event) {
            LOG.info("{}stopping...", logPrefix);
            compTracker.preStop();
            cancelInternalStateCheck();
            connectionTracker.cancelHeartbeat();
            connectionTracker.cancelHeartbeatCheck();
        }
    };

    Handler handleInternalStateCheck = new Handler<PeriodicInternalStateCheck>() {
        @Override
        public void handle(PeriodicInternalStateCheck event) {
            LOG.info("{}internal state check connection tracker - open:{} new:{}",
                    new Object[]{logPrefix, connectionTracker.openConnections.size(),
                        connectionTracker.newConnections.size()});
            LOG.info("{}internal state check connection maker - pending:{}",
                    new Object[]{logPrefix, connectionMaker.pendingConnections.size()});
            LOG.info("{}internal state check traffic tracker - buffering for targets:{}",
                    new Object[]{logPrefix, trafficTracker.pendingMsgs.size()});
            compTracker.resetRetries();
        }
    };

    //*************************COMPONENT_TRACKER********************************
    public class ComponentTracker {

        //hooks
        private final NatNetworkHook.Definition natNetworkDefinition;
        private NatNetworkHook.SetupResult networkSetup;
        private final Map<UUID, Integer> compToHook;
        private Component[] networkHook;

        //components
        private Component pmClientComp;
        private Component pmServerComp;
        private Component hpServerComp;
        private Component hpClientComp;

        private int hookRetry;

        public ComponentTracker(NatNetworkHook.Definition networkHookDefinition) {
            this.natNetworkDefinition = networkHookDefinition;
            this.compToHook = new HashMap<>();
            this.hookRetry = natConfig.fatalRetries;
        }

        //*********************NAT_NETWORK_HOOK*********************************
        private void setupNetwork() {
            LOG.info("{}connecting network", logPrefix);
            networkSetup = natNetworkDefinition.setup(new NatTraverserProxy(), new NatNetworkHookParent(), new NatNetworkHook.SetupInit(self, timer));
            networkHook = networkSetup.components;
            for (Component component : networkHook) {
                compToHook.put(component.id(), 1);
            }
            network = networkSetup.network;
        }

        private void startNetwork(boolean started) {
            LOG.info("{}starting network", logPrefix);
            natNetworkDefinition.start(new NatTraverserProxy(), new NatNetworkHookParent(), networkSetup, new NatNetworkHook.StartInit(started));
            networkSetup = null;
        }

        private void preStopNetwork() {
            LOG.info("{}tearing down network", new Object[]{logPrefix});
            natNetworkDefinition.preStop(new NatTraverserProxy(), new NatNetworkHookParent(), networkSetup, new NatNetworkHook.TearInit(timer));
            for (Component component : networkHook) {
                compToHook.remove(component.id());
            }
            networkHook = null;
            network = null;
        }

        private void restartNetwork(UUID compId) {
            hookRetry--;
            if (hookRetry == 0) {
                LOG.error("{}nat network hook fatal error - recurring errors", logPrefix);
                throw new RuntimeException("nat network hook fatal error - recurring errors");
            }
            LOG.info("{}restarting network", logPrefix);
            Integer hookNr = compToHook.get(compId);
            if (hookNr != 1) {
                LOG.error("{}hook logic exception", logPrefix);
                throw new RuntimeException("hook logic exception");
            }
            preStopNetwork();
            setupNetwork();
            startNetwork(false);
        }

        public void resetRetries() {
            hookRetry = natConfig.fatalRetries;
        }

        //*************************COMPONENTS***********************************
        private void setup() {
            setupNetwork();
            if (NatedTrait.isOpen(self)) {
                setupPMServer();
                setupHPServer();
            } else {
                setupPMClient();
            }
            setupHPClient();
        }

        private void start(boolean started) {
            startNetwork(started);
            if (!started) {
                if (NatedTrait.isOpen(self)) {
                    trigger(Start.event, pmServerComp.control());
                    trigger(Start.event, hpServerComp.control());
                } else {
                    trigger(Start.event, pmClientComp.control());
                }
                trigger(Start.event, hpClientComp.control());
            }
        }

        private void preStop() {
            preStopNetwork();
        }

        private void setupPMServer() {
            LOG.info("{}connecting pm server", logPrefix);
            pmServerComp = create(PMServerComp.class, new PMServerComp.PMServerInit(natConfig, self));
            connect(pmServerComp.getNegative(Timer.class), timer);
            connect(pmServerComp.getNegative(Network.class), network);
            subscribe(handleSelfAddressRequest, providedSAUpdate);
        }

        private void setupPMClient() {
            LOG.info("{}connecting pm client", logPrefix);
            pmClientComp = create(PMClientComp.class, new PMClientComp.PMClientInit(natConfig, systemConfig.self));
            connect(pmClientComp.getNegative(Timer.class), timer);
            connect(pmClientComp.getNegative(Network.class), network);
            connect(pmClientComp.getNegative(CroupierPort.class), peerSampling);
            connect(pmClientComp.getPositive(SelfAddressUpdatePort.class), providedSAUpdate);
            subscribe(handleSelfAddressUpdate, pmClientComp.getPositive(SelfAddressUpdatePort.class));
        }

        private void setupHPServer() {
            LOG.info("{}connecting hp server", logPrefix);
            hpServerComp = create(HPServerComp.class, new HPServerComp.HPServerInit(natConfig, self));
            connect(hpServerComp.getNegative(Timer.class), timer);
            connect(hpServerComp.getNegative(Network.class), network);
            connect(hpServerComp.getNegative(PMServerPort.class), pmServerComp.getPositive(PMServerPort.class));
        }

        private void setupHPClient() {
            LOG.info("{}connecting hp client", logPrefix);
            hpClientComp = create(SHPClientComp.class, new SHPClientComp.SHPClientInit(natConfig, self));
            connect(hpClientComp.getNegative(Timer.class), timer);
            connect(hpClientComp.getNegative(Network.class), network);
            if (!NatedTrait.isOpen(systemConfig.self)) {
                connect(hpClientComp.getNegative(SelfAddressUpdatePort.class),
                        pmClientComp.getPositive(SelfAddressUpdatePort.class));
            }
            hpClient = hpClientComp.getPositive(SHPClientPort.class);
        }

        Handler handleSelfAddressUpdate = new Handler<SelfAddressUpdate>() {
            @Override
            public void handle(SelfAddressUpdate update) {
                LOG.info("{}updating self from:{} to:{}",
                        new Object[]{logPrefix, self, update.self});
                self = update.self;
                trigger(update, providedSAUpdate);
            }
        };

        Handler handleSelfAddressRequest = new Handler<SelfAddress.Request>() {
            @Override
            public void handle(SelfAddress.Request req) {
                LOG.trace("{}received self request");
                answer(req, req.answer(self));
            }
        };

    }

    public class NatNetworkHookParent implements Hook.Parent {

        public InetAddress getLocalInterface() {
            return localIp;
        }
    }

    public class NatTraverserProxy implements ComponentProxy {

        @Override
        public <P extends PortType> Positive<P> requires(Class<P> portType) {
            return NatTraverserComp.this.requires(portType);
        }

        @Override
        public <P extends PortType> Negative<P> provides(Class<P> portType) {
            return NatTraverserComp.this.provides(portType);
        }

        @Override
        public <P extends PortType> void trigger(KompicsEvent e, Port<P> p) {
            NatTraverserComp.this.trigger(e, p);
        }

        @Override
        public <T extends ComponentDefinition> Component create(Class<T> definition, Init<T> initEvent) {
            return NatTraverserComp.this.create(definition, initEvent);
        }

        @Override
        public <T extends ComponentDefinition> Component create(Class<T> definition, Init.None initEvent) {
            return NatTraverserComp.this.create(definition, initEvent);
        }

        @Override
        public <P extends PortType> Channel<P> connect(Positive<P> positive, Negative<P> negative) {
            return NatTraverserComp.this.connect(negative, positive);
        }

        @Override
        public <P extends PortType> Channel<P> connect(Positive<P> positive, Negative<P> negative, ChannelFilter filter) {
            return NatTraverserComp.this.connect(negative, positive, filter);
        }

        @Override
        public <P extends PortType> Channel<P> connect(Negative<P> negative, Positive<P> positive) {
            return NatTraverserComp.this.connect(positive, negative);
        }

        @Override
        public <P extends PortType> Channel<P> connect(Negative<P> negative, Positive<P> positive, ChannelFilter filter) {
            return NatTraverserComp.this.connect(negative, positive, filter);
        }

        @Override
        public <P extends PortType> void disconnect(Negative<P> negative, Positive<P> positive) {
            NatTraverserComp.this.disconnect(negative, positive);
        }

        @Override
        public <P extends PortType> void disconnect(Positive<P> positive, Negative<P> negative) {
            NatTraverserComp.this.disconnect(negative, positive);
        }

        @Override
        public Negative<ControlPort> getControlPort() {
            return NatTraverserComp.this.control;
        }

        @Override
        public <E extends KompicsEvent, P extends PortType> void subscribe(Handler<E> handler, Port<P> port) throws ConfigurationException {
            NatTraverserComp.this.subscribe(handler, port);
        }
    }

//*************************TRAFFIC TRACKER**********************************
    public class TrafficTracker {

        public Map<BasicAddress, List<BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Object>>> pendingMsgs;

        public TrafficTracker() {
            this.pendingMsgs = new HashMap<BasicAddress, List<BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Object>>>();
        }

        Handler handleLocal = new Handler<Msg>() {
            @Override
            public void handle(Msg msg) {
                LOG.trace("{}received outgoing:{}", logPrefix, msg);
                if (!handleTraffic.getValue(msg)) {
                    /**
                     * should not get here. If I get here - the NatTrafficFilter
                     * is not set or not working properly and I am processing
                     * more msgs than necessary
                     */
                    LOG.info("{}forwarding outgoing:{}", new Object[]{logPrefix, msg});
                    trigger(msg, network);
                    return;
                }
                BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Object> contentMsg
                        = (BasicContentMsg) msg;

                DecoratedAddress destination = contentMsg.getDestination();
                if (NatTraverserFeasibility.direct(self, destination)) {
                    LOG.debug("{}forwarding msg:{} local to network", logPrefix, contentMsg.getContent());
                    trigger(msg, network);
                    return;
                }
                Optional<Pair<BasicAddress, DecoratedAddress>> connection
                        = connectionTracker.connected(destination.getBase());
                //already connected
                if (connection.isPresent()) {
                    DecoratedAddress connSelf = self.changeBase(connection.get().getValue0());
                    DecoratedAddress connTarget = connection.get().getValue1();
                    if (!self.getBase().equals(connSelf.getBase())) {
                        LOG.warn("{}mapping alocation < EI", logPrefix);
                        //TODO Alex - further check for correctness required
                    }

                    BasicHeader basicHeader = new BasicHeader(connSelf, connTarget, Transport.UDP);
                    DecoratedHeader<DecoratedAddress> forwardHeader = contentMsg.getHeader().changeBasicHeader(basicHeader);
                    ContentMsg forwardMsg = new BasicContentMsg(forwardHeader, contentMsg.getContent());
                    LOG.debug("{}forwarding msg:{} local to network", logPrefix, forwardMsg);
                    trigger(forwardMsg, network);
                    return;
                }

                //connecting
                List<BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Object>> pending
                        = pendingMsgs.get(destination.getBase());
                if (pending == null) {
                    if (connectionMaker.connect(contentMsg.getSource(), destination)) {
                        pending = new ArrayList<BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Object>>();
                        pendingMsgs.put(destination.getBase(), pending);
                    } else {
                        LOG.warn("{}dropping msg:{}", logPrefix, msg);
                        return;
                    }
                }
                LOG.debug("{}waiting on connection to:{} buffering msg:{}",
                        new Object[]{logPrefix, destination.getBase(), msg});
                pending.add(contentMsg);
            }
        };

        Handler handleNetwork = new Handler<Msg>() {
            @Override
            public void handle(Msg msg) {
                if (!handleTraffic.getValue(msg)) {
                    /**
                     * should not get here. If I get here - the NatTrafficFilter
                     * is not set or not working properly and I am processing
                     * more msgs than necessary
                     */
                    LOG.info("{}forwarding incoming:{}", new Object[]{logPrefix, msg});
                    trigger(msg, providedNetwork);
                    return;
                }
                BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Object> contentMsg
                        = (BasicContentMsg) msg;
                LOG.trace("{}forwarding msg:{} network to local", logPrefix, contentMsg.getContent());
                trigger(msg, providedNetwork);
            }
        };

        public void connected(Pair<BasicAddress, DecoratedAddress> connection) {
            List<BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Object>> pending
                    = pendingMsgs.remove(connection.getValue1().getBase());
            if (pending == null) {
                LOG.warn("{}weird pending empty to:{}", logPrefix, connection.getValue1().getBase());
                return;
            }
            DecoratedAddress connSelf = self.changeBase(connection.getValue0());
            DecoratedAddress connTarget = connection.getValue1();
            if (!self.getBase().equals(connSelf.getBase())) {
                LOG.warn("{}mapping alocation < EI", logPrefix);
                //TODO Alex - further check for correctness required
            }

            for (BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Object> msg : pending) {
                BasicHeader basicHeader = new BasicHeader(connSelf, connTarget, Transport.UDP);
                DecoratedHeader<DecoratedAddress> forwardHeader = msg.getHeader().changeBasicHeader(basicHeader);
                ContentMsg forwardMsg = new BasicContentMsg(forwardHeader, msg.getContent());
                LOG.debug("{}forwarding outgoing buffered msg:{} from:{} to:{} ",
                        new Object[]{logPrefix, forwardMsg.getContent(), connection.getValue0(), connection.getValue1().getBase()});
                trigger(forwardMsg, network);
            }
        }

        public void connectFailed(BasicAddress target) {
            LOG.info("{}connection to:{} failed, dropping buffered msgs",
                    new Object[]{logPrefix, target});
            pendingMsgs.remove(target);
        }
    } //***************************CONNECTION MAKER*******************************

    public class ConnectionMaker {

        private final Map<BasicAddress, UUID> pendingConnections;

        public ConnectionMaker() {
            this.pendingConnections = new HashMap<BasicAddress, UUID>();
        }

        public boolean connect(DecoratedAddress self, DecoratedAddress target) {
            switch (NatTraverserFeasibility.check(self, target)) {
                case DIRECT:
                    LOG.error("{}logical error - direct connection, should not get here");
                    throw new RuntimeException("logical error - direct connection, should not get here");
                case SHP:
                    pendingConnections.put(target.getBase(), null); //no timeout needed - the shp deals with timeout
                    LOG.info("{}connection request to shp to:{}", logPrefix, target.getBase());
                    trigger(new OpenConnection.Request(UUID.randomUUID(), target), hpClient);
                    return true;
                case UNFEASIBLE:
                default: //act as UNFEASIBLE
                    LOG.warn("{}unfeasible nat combination self:{} target:{}",
                            new Object[]{logPrefix, self.getTrait(NatedTrait.class).type, target.getTrait(NatedTrait.class).type});

                    return false;
            }
        }

        Handler handleOpenConnectionSuccess = new Handler<OpenConnection.Success>() {
            @Override
            public void handle(OpenConnection.Success success) {
                LOG.info("{}created connection to:{} on:{}",
                        new Object[]{logPrefix, success.target.getBase(), success.self.getBase()});
                connectSuccess(Pair.with(success.self.getBase(), success.target));
            }
        };

        Handler handleOpenConnectionFail = new Handler<OpenConnection.Fail>() {

            @Override
            public void handle(OpenConnection.Fail fail) {
                LOG.warn("{}connection to:{} failed to initialize", logPrefix, fail.target.getBase());
                connectFailed(fail.target.getBase());
            }
        };

        private void connectSuccess(Pair<BasicAddress, DecoratedAddress> connection) {
            pendingConnections.remove(connection.getValue1().getBase());
            connectionTracker.newConnection(connection);
            trafficTracker.connected(connection);
        }

        private void connectFailed(BasicAddress target) {
            pendingConnections.remove(target);
            trafficTracker.connectFailed(target);
        }
    }

//***************************CONNECTION TRACKER*****************************
    public class ConnectionTracker {

        private UUID heartbeatId;
        private UUID heartbeatCheckId;

        private final Map<BasicAddress, Pair<BasicAddress, DecoratedAddress>> openConnections; //<remoteEnd, <localEnd, remoteFullAddress>>
        private final Map<BasicAddress, Pair<BasicAddress, DecoratedAddress>> newConnections; //<remoteEnd, <localEnd, remoteFullAddress>>
        private final Set<BasicAddress> heartbeats;

        public ConnectionTracker() {
            this.openConnections = new HashMap<BasicAddress, Pair<BasicAddress, DecoratedAddress>>();
            this.newConnections = new HashMap<BasicAddress, Pair<BasicAddress, DecoratedAddress>>();
            this.heartbeats = new HashSet<BasicAddress>();
        }

        public void newConnection(Pair<BasicAddress, DecoratedAddress> connection) {
            newConnections.put(connection.getValue1().getBase(), connection);
        }

        public Optional<Pair<BasicAddress, DecoratedAddress>> connected(BasicAddress target) {
            if (openConnections.containsKey(target)) {
                return Optional.of(openConnections.get(target));
            }
            if (newConnections.containsKey(target)) {
                return Optional.of(newConnections.get(target));
            }
            return Optional.absent();
        }

        Handler handleHeartbeat = new Handler<PeriodicHeartbeat>() {
            @Override
            public void handle(PeriodicHeartbeat event) {
                LOG.trace("{}heartbeating on open connections:{}", logPrefix, openConnections.size());
                for (Pair<BasicAddress, DecoratedAddress> target : openConnections.values()) {
                    LOG.trace("{}heartbeating to:{}", logPrefix, target.getValue1().getBase());
                    if (!self.getBase().equals(target.getValue0())) {
                        LOG.error("{}not yet handling mapping policy different than EI", logPrefix);
                        throw new RuntimeException("not yet handling mapping policy different than EI");
                    }
                    DecoratedHeader<DecoratedAddress> heartbeatHeader = new DecoratedHeader(new BasicHeader(self, target.getValue1(), Transport.UDP), null, null);
                    ContentMsg heartbeat = new BasicContentMsg(heartbeatHeader, new Heartbeat(UUID.randomUUID()));
                    trigger(heartbeat, network);
                }
                for (Pair<BasicAddress, DecoratedAddress> target : newConnections.values()) {
                    LOG.trace("{}heartbeating to:{}", logPrefix, target.getValue1().getBase());
                    if (!self.getBase().equals(target.getValue0())) {
                        LOG.warn("{}mapping policy different than EI", logPrefix);
//                        throw new RuntimeException("not yet handling mapping policy different than EI");
                    }
                    DecoratedHeader<DecoratedAddress> heartbeatHeader = new DecoratedHeader(new BasicHeader(self, target.getValue1(), Transport.UDP), null, null);
                    ContentMsg heartbeat = new BasicContentMsg(heartbeatHeader, new Heartbeat(UUID.randomUUID()));
                    trigger(heartbeat, network);
                }
            }
        };

        Handler handleHeartbeatCheck = new Handler<PeriodicHeartbeatCheck>() {
            @Override
            public void handle(PeriodicHeartbeatCheck event) {
                LOG.trace("{}heartbeat check", logPrefix);
                Set<BasicAddress> suspected = Sets.difference(openConnections.keySet(), heartbeats);
                for (BasicAddress target : suspected) {
                    LOG.info("{}suspected:{} closing connection", logPrefix, target);
                    closeConnection(target);
                }
                openConnections.putAll(newConnections);
                newConnections.clear();
            }
        };

        ClassMatchedHandler handlePartnerHeartbeat
                = new ClassMatchedHandler<Heartbeat, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Heartbeat>>() {
                    @Override
                    public void handle(Heartbeat content, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Heartbeat> container) {
                        LOG.trace("{}partner:{} heartbeat", logPrefix, container.getSource().getBase());
                        heartbeats.add(container.getSource().getBase());
                    }
                };

        ClassMatchedHandler handleNetCloseConnection
                = new ClassMatchedHandler<Close, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Close>>() {
                    @Override
                    public void handle(Close content, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Close> container) {
                        LOG.info("{}target:{} closing connection", logPrefix, container.getSource().getBase());
                        closeConnection(container.getSource().getBase());
                    }
                };

        private DecoratedAddress closeConnection(BasicAddress target) {
            return openConnections.remove(target).getValue1();
            //TODO Alex - any other cleanup to do here?
        }

        private void scheduleHeartbeat() {
            if (heartbeatId != null) {
                LOG.warn("{}double starting heartbeat timeout", logPrefix);
                return;
            }
            SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(natConfig.connectionHeartbeat, natConfig.connectionHeartbeat);
            PeriodicHeartbeat sc = new PeriodicHeartbeat(spt);
            spt.setTimeoutEvent(sc);
            heartbeatId = sc.getTimeoutId();
            trigger(spt, timer);
        }

        private void cancelHeartbeat() {
            if (heartbeatId == null) {
                LOG.warn("{}double stopping heartbeat timeout", logPrefix);
                return;
            }
            CancelPeriodicTimeout cpt = new CancelPeriodicTimeout(heartbeatId);
            heartbeatId = null;
            trigger(cpt, timer);
        }

        private void scheduleHeartbeatCheck() {
            if (heartbeatCheckId != null) {
                LOG.warn("{}double starting heartbeat check timeout", logPrefix);
                return;
            }
            SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(2 * natConfig.connectionHeartbeat, 2 * natConfig.connectionHeartbeat);
            PeriodicHeartbeatCheck sc = new PeriodicHeartbeatCheck(spt);
            spt.setTimeoutEvent(sc);
            heartbeatCheckId = sc.getTimeoutId();
            trigger(spt, timer);
        }

        private void cancelHeartbeatCheck() {
            if (heartbeatCheckId == null) {
                LOG.warn("{}double stopping heartbeat check timeout", logPrefix);
                return;
            }
            CancelPeriodicTimeout cpt = new CancelPeriodicTimeout(heartbeatCheckId);
            heartbeatCheckId = null;
            trigger(cpt, timer);
        }
    }

    private void scheduleInternalStateCheck() {
        if (internalStateCheckId != null) {
            LOG.warn("{}double starting internal state check timeout", logPrefix);
            return;
        }
        SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(natConfig.internalStateCheck, natConfig.internalStateCheck);
        PeriodicInternalStateCheck sc = new PeriodicInternalStateCheck(spt);
        spt.setTimeoutEvent(sc);
        internalStateCheckId = sc.getTimeoutId();
        trigger(spt, timer);
    }

    private void cancelInternalStateCheck() {
        if (internalStateCheckId == null) {
            LOG.warn("{}double stopping internal state check timeout", logPrefix);
            return;
        }
        CancelPeriodicTimeout cpt = new CancelPeriodicTimeout(internalStateCheckId);
        internalStateCheckId = null;
        trigger(cpt, timer);

    }

    public static class PeriodicInternalStateCheck extends Timeout {

        public PeriodicInternalStateCheck(SchedulePeriodicTimeout spt) {
            super(spt);
        }
    }

    public static class PeriodicHeartbeat extends Timeout {

        public PeriodicHeartbeat(SchedulePeriodicTimeout spt) {
            super(spt);
        }
    }

    public static class PeriodicHeartbeatCheck extends Timeout {

        public PeriodicHeartbeatCheck(SchedulePeriodicTimeout spt) {
            super(spt);
        }
    }

    public static class NatTraverserInit extends Init<NatTraverserComp> {

        public final InetAddress localIp;
        public final SystemConfig systemConfig;
        public final NatTraverserConfig natConfig;
        public final int globalCroupierOverlayId;
        public final CroupierConfig croupierConfig;
        public final List<DecoratedAddress> croupierBootstrap;
        public final SystemHookSetup systemHooks;

        public NatTraverserInit(InetAddress localIp, SystemConfig systemConfig, NatInitHelper nhInit,
                CroupierConfig croupierConfig, SystemHookSetup systemHooks) {
            this.localIp = localIp;
            this.systemConfig = systemConfig;
            this.natConfig = nhInit.ntConfig;
            this.globalCroupierOverlayId = nhInit.globalCroupierOverlayId;
            this.croupierConfig = croupierConfig;
            this.croupierBootstrap = nhInit.croupierBoostrap;
            this.systemHooks = systemHooks;
        }
    }
}
