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
package se.sics.ktoolbox.nat.network;

import com.google.common.base.Optional;
import com.google.common.collect.Sets;
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
import se.sics.kompics.ClassMatchedHandler;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.Stop;
import se.sics.kompics.network.Msg;
import se.sics.kompics.network.Network;
import se.sics.kompics.network.Transport;
import se.sics.kompics.timer.CancelPeriodicTimeout;
import se.sics.kompics.timer.CancelTimeout;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;
import se.sics.kompics.timer.Timer;
import se.sics.nat.common.NatMsg;
import se.sics.nat.hp.client.SHPClientPort;
import se.sics.nat.hp.client.msg.OpenConnection;
import se.sics.nat.common.NatTraverserConfig;
import se.sics.ktoolbox.nat.network.msg.NatConnection.Close;
import se.sics.ktoolbox.nat.network.msg.NatConnection.Heartbeat;
import se.sics.ktoolbox.nat.network.msg.NatConnection.OpenRequest;
import se.sics.ktoolbox.nat.network.msg.NatConnection.OpenResponse;
import se.sics.ktoolbox.nat.network.util.Feasibility;
import se.sics.nat.network.NatedTrait;
import se.sics.nat.pm.client.PMClientPort;
import se.sics.nat.pm.client.msg.Update;
import se.sics.p2ptoolbox.util.network.ContentMsg;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;
import se.sics.p2ptoolbox.util.network.impl.BasicContentMsg;
import se.sics.p2ptoolbox.util.network.impl.BasicHeader;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;
import se.sics.p2ptoolbox.util.network.impl.DecoratedHeader;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class NatTraverserComp extends ComponentDefinition {

    private static final Logger LOG = LoggerFactory.getLogger(NatTraverserComp.class);
    private String logPrefix = "";

    private final Negative<Network> local = provides(Network.class);
    private final Positive<Network> network = requires(Network.class);
    private final Positive<Timer> timer = requires(Timer.class);
    private final Positive<PMClientPort> parentMaker = requires(PMClientPort.class);
    private final Positive<SHPClientPort> simpleHP = requires(SHPClientPort.class);

    private final NatTraverserConfig config;
    private DecoratedAddress self;

    private UUID internalStateCheckId;

    private final ConnectionTracker connectionTracker;
    private final ConnectionMaker connectionMaker;
    private final TrafficTracker trafficTracker;

    public NatTraverserComp(NatTraverserInit init) {
        this.config = init.config;
        this.self = init.self;
        this.logPrefix = self.getBase() + " ";
        LOG.info("{}initiating...", logPrefix);

        this.connectionTracker = new ConnectionTracker();
        this.connectionMaker = new ConnectionMaker();
        this.trafficTracker = new TrafficTracker();

        //control
        subscribe(handleStart, control);
        subscribe(handleStop, control);
        subscribe(handleInternalStateCheck, timer);
        subscribe(handleSelfUpdate, parentMaker);

        //connection tracker
        subscribe(connectionTracker.handleHeartbeat, timer);
        subscribe(connectionTracker.handleHeartbeatCheck, timer);
        subscribe(connectionTracker.handlePartnerHeartbeat, network);
        subscribe(connectionTracker.handleNetCloseConnection, network);

        //connection maker
        if (!self.hasTrait(NatedTrait.class)) {
            subscribe(connectionMaker.handleOpenRequest, network);
        }
        subscribe(connectionMaker.handleOpenResponse, network);
        subscribe(connectionMaker.handleOpenConnectionTimeout, timer);
        subscribe(connectionMaker.handleOpenConnectionSuccess, simpleHP);
        subscribe(connectionMaker.handleOpenConnectionFail, simpleHP);

        //traffic tracker
        subscribe(trafficTracker.handleLocal, local);
        subscribe(trafficTracker.handleNetwork, network);
    }

    //********************************CONTROL***********************************
    Handler handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            LOG.info("{}starting...", logPrefix);
            scheduleInternalStateCheck();
            connectionTracker.scheduleHeartbeat();
            connectionTracker.scheduleHeartbeatCheck();
        }
    };

    Handler handleStop = new Handler<Stop>() {
        @Override
        public void handle(Stop event) {
            LOG.info("{}stopping...", logPrefix);
            cancelInternalStateCheck();
            connectionTracker.cancelHeartbeat();
            connectionTracker.cancelHeartbeatCheck();
        }
    };

    Handler handleInternalStateCheck = new Handler<PeriodicInternalStateCheck>() {
        @Override
        public void handle(PeriodicInternalStateCheck event) {
            LOG.info("{}connection tracker - open:{} new:{}",
                    new Object[]{logPrefix, connectionTracker.openConnections.size(),
                        connectionTracker.newConnections.size()});
            LOG.info("{}connection maker - pending:{}",
                    new Object[]{logPrefix, connectionMaker.pendingConnections.size()});
        }
    };

    Handler handleSelfUpdate = new Handler<Update>() {
        @Override
        public void handle(Update update) {
            LOG.info("{}updating self from:{} to:{}",
                    new Object[]{logPrefix, self, update.self});
            self = update.self;
        }
    };

    //*************************TRAFFIC TRACKER**********************************
    public class TrafficTracker {

        public Map<BasicAddress, List<BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Object>>> pendingMsgs;

        public TrafficTracker() {
            this.pendingMsgs = new HashMap<BasicAddress, List<BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Object>>>();
        }

        Handler handleLocal = new Handler<Msg>() {
            @Override
            public void handle(Msg msg) {
                BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Object> contentMsg = null;
                try {
                    contentMsg = (BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Object>) msg;
                } catch (ClassCastException ex) {
                    LOG.info("{}forwarding msg:{}, not touching non BasicContent traffic", logPrefix, msg);
                    trigger(msg, network);
                    return;
                }
                if (!contentMsg.getProtocol().equals(Transport.UDP)) {
                    LOG.info("{}forwarding msg:{}, not touching non UDP traffic", logPrefix, msg);
                    trigger(msg, network);
                    return;
                }

                DecoratedAddress destination = contentMsg.getDestination();
                Optional<Pair<BasicAddress, DecoratedAddress>> connection
                        = connectionTracker.connected(destination.getBase());
                //already connected
                if (connection.isPresent()) {
                    if (!self.getBase().equals(connection.get().getValue0())) {
                        LOG.error("{}not yet handling mapping policy different than EI", logPrefix);
                        throw new RuntimeException("not yet handling mapping policy different than EI");
                    }

                    //TODO Alex - should I do this header change all the time? or force the application to use correct adr
                    BasicHeader basicHeader = new BasicHeader(self, connection.get().getValue1(), Transport.UDP);
                    DecoratedHeader<DecoratedAddress> forwardHeader = contentMsg.getHeader().changeBasicHeader(basicHeader);
                    ContentMsg forwardMsg = new BasicContentMsg(forwardHeader, contentMsg.getContent());
                    LOG.debug("{}forwarding msg:{} local to network", logPrefix, forwardMsg);
                    trigger(forwardMsg, network);
                    return;
                }
                //open - open
                if (!self.hasTrait(NatedTrait.class) && !destination.hasTrait(NatedTrait.class)) {
                    LOG.debug("{}forwarding msg:{} local to network", logPrefix, msg);
                    trigger(msg, network);
                    return;
                }
                //connecting
                List<BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Object>> pending = pendingMsgs.get(destination.getBase());
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
                BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Object> contentMsg = null;
                try {
                    contentMsg = (BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Object>) msg;
                } catch (ClassCastException ex) {
                    LOG.info("{}forwarding msg:{}, not touching non BasicContent traffic", logPrefix, msg);
                    trigger(msg, network);
                    return;
                }
                if (!contentMsg.getProtocol().equals(Transport.UDP)) {
                    LOG.info("{}forwarding msg:{}, not touching non UDP traffic", logPrefix, msg);
                    trigger(msg, network);
                    return;
                }
                if (contentMsg.getContent() instanceof NatMsg) {
                    //skip - nat internal msgs
                    return;
                }
                LOG.trace("{}forwarding msg:{} network to local", logPrefix, msg);
                trigger(msg, local);
            }
        };

        public void connected(Pair<BasicAddress, DecoratedAddress> connection) {
            List<BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Object>> pending
                    = pendingMsgs.remove(connection.getValue1().getBase());
            if (pending == null) {
                LOG.warn("{}weird pending empty to:{}", logPrefix, connection.getValue1().getBase());
                return;
            }
            for (BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Object> msg : pending) {
                BasicHeader basicHeader = new BasicHeader(self, connection.getValue1(), Transport.UDP);
                DecoratedHeader<DecoratedAddress> forwardHeader = msg.getHeader().changeBasicHeader(basicHeader);
                ContentMsg forwardMsg = new BasicContentMsg(forwardHeader, msg.getContent());
                LOG.debug("{}forwarding msg:{} local to network", logPrefix, forwardMsg);
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

        private void connectOpenNode(DecoratedAddress target) {
            LOG.info("{}opening connection to:{}", logPrefix, target.getBase());
            pendingConnections.put(target.getBase(), scheduleOpenConnectTimeout(target.getBase()));
            OpenRequest openContent = new OpenRequest(UUID.randomUUID());
            DecoratedHeader<DecoratedAddress> openHeader = new DecoratedHeader(new BasicHeader(self, target, Transport.UDP), null, null);
            ContentMsg openMsg = new BasicContentMsg(openHeader, openContent);
            trigger(openMsg, network);
        }

        public boolean connect(DecoratedAddress self, DecoratedAddress target) {
            if(!target.hasTrait(NatedTrait.class)) {
                connectOpenNode(target);
                return true;
            } else if (Feasibility.simpleHolePunching(self, target).equals(Feasibility.State.INITIATE)) {
                pendingConnections.put(target.getBase(), null); //no timeout needed - the shp deals with timeout
                LOG.info("{}connection request to shp to:{}", logPrefix, target.getBase());
                trigger(new OpenConnection.Request(UUID.randomUUID(), target), simpleHP);
                return true;
            } else {
                LOG.warn("{}unfeasible nat combination self:{} target:{}",
                        new Object[]{logPrefix, self.getTrait(NatedTrait.class).type, target.getTrait(NatedTrait.class).type});
                return false;
            }
        }

        ClassMatchedHandler handleOpenRequest
                = new ClassMatchedHandler<OpenRequest, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, OpenRequest>>() {
                    @Override
                    public void handle(OpenRequest content, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, OpenRequest> container) {
                        LOG.info("{}opened connection to:{} from:{}",
                                new Object[]{logPrefix, container.getSource().getBase(), self});
                        connectionTracker.newConnection(self, container.getSource());
                        OpenResponse openContent = content.answer(container.getSource());
                        DecoratedHeader<DecoratedAddress> openHeader = new DecoratedHeader(new BasicHeader(self, container.getSource(), Transport.UDP), null, null);
                        ContentMsg openMsg = new BasicContentMsg(openHeader, openContent);
                        trigger(openMsg, network);
                    }
                };

        ClassMatchedHandler handleOpenResponse
                = new ClassMatchedHandler<OpenResponse, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, OpenResponse>>() {
                    @Override
                    public void handle(OpenResponse content, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, OpenResponse> container) {
                        LOG.info("{}opened connection to:{} from:{}",
                                new Object[]{logPrefix, container.getSource().getBase(), content.observed.getBase()});
                        UUID timeoutId = pendingConnections.remove(container.getSource().getBase());
                        if (timeoutId == null) {
                            LOG.info("{}late msg");
                            return;
                        }
                        connectionTracker.newConnection(self, container.getSource());
                        cancelOpenConnectTimeout(timeoutId);
                    }
                };

        Handler handleOpenConnectionTimeout = new Handler<OpenConnectTimeout>() {
            @Override
            public void handle(OpenConnectTimeout timeout) {
                LOG.info("{}open connection timeout to:{}", logPrefix, timeout.target);
                pendingConnections.remove(timeout.target);
            }
        };

        Handler handleOpenConnectionSuccess = new Handler<OpenConnection.Success>() {
            @Override
            public void handle(OpenConnection.Success success) {
                LOG.info("{}created connection to:{} on:{}",
                        new Object[]{logPrefix, success.target.getBase(), success.self.getBase()});
                pendingConnections.remove(success.target.getBase());
                connectionTracker.newConnection(success.self, success.target);
            }
        };

        Handler handleOpenConnectionFail = new Handler<OpenConnection.Fail>() {

            @Override
            public void handle(OpenConnection.Fail fail) {
                LOG.warn("{}connection to:{} failed to initialize", logPrefix, fail.target.getBase());
                pendingConnections.remove(fail.target.getBase());
            }
        };

        private UUID scheduleOpenConnectTimeout(BasicAddress target) {
            ScheduleTimeout st = new ScheduleTimeout(config.msgRTT);
            OpenConnectTimeout oc = new OpenConnectTimeout(st, target);
            st.setTimeoutEvent(oc);
            trigger(oc, timer);
            return oc.getTimeoutId();
        }

        private void cancelOpenConnectTimeout(UUID timeoutId) {
            CancelTimeout cpt = new CancelTimeout(timeoutId);
            trigger(cpt, timer);
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

        public void newConnection(DecoratedAddress self, DecoratedAddress target) {
            newConnections.put(target.getBase(), Pair.with(self.getBase(), target));
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
                LOG.trace("{}heartbeating on open connections", logPrefix);
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
                        LOG.error("{}not yet handling mapping policy different than EI", logPrefix);
                        throw new RuntimeException("not yet handling mapping policy different than EI");
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
            SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(config.connectionHeartbeat, config.connectionHeartbeat);
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
            SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(2 * config.connectionHeartbeat, 2 * config.connectionHeartbeat);
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

    public static class NatTraverserInit extends Init<NatTraverserComp> {

        public final NatTraverserConfig config;
        public final DecoratedAddress self;

        public NatTraverserInit(NatTraverserConfig config, DecoratedAddress self) {
            this.config = config;
            this.self = self;
        }
    }

    private void scheduleInternalStateCheck() {
        if (internalStateCheckId != null) {
            LOG.warn("{}double starting internal state check timeout", logPrefix);
            return;
        }
        SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(config.internalStateCheck, config.internalStateCheck);
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

    public static class OpenConnectTimeout extends Timeout {

        public final BasicAddress target;

        public OpenConnectTimeout(ScheduleTimeout spt, BasicAddress target) {
            super(spt);
            this.target = target;
        }
    }
}
