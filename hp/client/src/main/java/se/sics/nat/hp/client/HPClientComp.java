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
package se.sics.nat.hp.client;

import com.google.common.collect.Sets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
import se.sics.kompics.network.Network;
import se.sics.kompics.network.Transport;
import se.sics.kompics.timer.CancelPeriodicTimeout;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.Timeout;
import se.sics.kompics.timer.Timer;
import se.sics.nat.hp.client.msg.CloseConnection;
import se.sics.nat.hp.client.msg.OpenConnection;
import se.sics.nat.hp.common.HPConfig;
import se.sics.nat.hp.common.msg.Heartbeat;
import se.sics.nat.hp.common.msg.NetCloseConnection;
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
public class HPClientComp extends ComponentDefinition {

    private static final Logger LOG = LoggerFactory.getLogger(HPClientComp.class);
    private String logPrefix = "";

    private final Negative<HPClientPort> holePunching = provides(HPClientPort.class);
    private final Positive<Network> network = requires(Network.class);
    private final Positive<Timer> timer = requires(Timer.class);
    private final Positive<PMClientPort> parentMaker = requires(PMClientPort.class);

    private final HPConfig config;
    private DecoratedAddress self;

    private UUID internalStateCheckId;

    private final ConnectionTracker connectionTracker;
    private final ConnectionMaker connectionMaker;

    public HPClientComp(HPClientInit init) {
        this.config = init.config;
        this.self = init.self;
        this.logPrefix = self.getBase() + " ";
        LOG.info("{}initiating...", logPrefix);

        this.connectionTracker = new ConnectionTracker();
        this.connectionMaker = new ConnectionMaker();

        //control
        subscribe(handleStart, control);
        subscribe(handleStop, control);
        subscribe(handleInternalStateCheck, timer);
        subscribe(handleSelfUpdate, parentMaker);

        //connection tracker
        subscribe(connectionTracker.handleHeartbeat, timer);
        subscribe(connectionTracker.handleHeartbeatCheck, timer);
        subscribe(connectionTracker.handlePartnerHeartbeat, network);
        subscribe(connectionTracker.handleOpenConnection, holePunching);
        subscribe(connectionTracker.handleCloseConnection, holePunching);
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
            LOG.info("{}connection tracker - openConnections:{}",
                    new Object[]{logPrefix, connectionTracker.openConnections.size()});
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

    //***************************CONNECTION TRACKER*****************************
    public class ConnectionTracker {

        private UUID heartbeatId;
        private UUID heartbeatCheckId;

        private final Map<BasicAddress, DecoratedAddress> openConnections;

        private final Set<BasicAddress> heartbeats;

        public ConnectionTracker() {
            this.openConnections = new HashMap<BasicAddress, DecoratedAddress>();
            this.heartbeats = new HashSet<BasicAddress>();
        }

        Handler handleHeartbeat = new Handler<PeriodicHeartbeat>() {
            @Override
            public void handle(PeriodicHeartbeat event) {
                LOG.trace("{}heartbeating on open connections", logPrefix);
                for (DecoratedAddress target : openConnections.values()) {
                    LOG.trace("{}heartbeating to:{}", logPrefix, target.getBase());
                    DecoratedHeader<DecoratedAddress> heartbeatHeader = new DecoratedHeader(new BasicHeader(self, target, Transport.UDP), null, null);
                    ContentMsg heartbeat = new BasicContentMsg(heartbeatHeader, new Heartbeat());
                    trigger(heartbeat, network);
                }
            }
        };

        Handler handleHeartbeatCheck = new Handler<PeriodicHeartbeatCheck>() {
            @Override
            public void handle(PeriodicHeartbeatCheck event) {
                LOG.trace("{}heartbeat check", logPrefix);
                Set<BasicAddress> suspected = Sets.difference(openConnections.keySet(), heartbeats);
                if (!suspected.isEmpty()) {
                    LOG.info("{}suspected:{} closing connections", logPrefix, suspected);
                    for (BasicAddress target : suspected) {
                        DecoratedAddress targetAdr = closeConnection(target);
                        trigger(new CloseConnection(targetAdr), holePunching);
                    }
                }
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

        Handler handleOpenConnection = new Handler<OpenConnection.Request>() {
            @Override
            public void handle(OpenConnection.Request req) {
                LOG.info("{}open connection request to:{}", logPrefix, req.target.getBase());
                if(openConnections.containsKey(req.target.getBase())) {
                    LOG.info("{}connection already exists to:{}", logPrefix, req.target.getBase());
                    answer(req, req.success());
                } else {
                    connectionMaker.startConnection(req);
                }
            }
        };

        Handler handleCloseConnection = new Handler<CloseConnection>() {
            @Override
            public void handle(CloseConnection req) {
                LOG.info("{}close connection request to:{}", logPrefix, req.target.getBase());
                DecoratedAddress targetAdr = closeConnection(req.target.getBase());

                DecoratedHeader<DecoratedAddress> closeHeader = new DecoratedHeader(new BasicHeader(self, targetAdr, Transport.UDP), null, null);
                ContentMsg close = new BasicContentMsg(closeHeader, new NetCloseConnection());
                trigger(close, network);
            }
        };

        ClassMatchedHandler handleNetCloseConnection
                = new ClassMatchedHandler<NetCloseConnection, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, NetCloseConnection>>() {
                    @Override
                    public void handle(NetCloseConnection content, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, NetCloseConnection> container) {
                        LOG.info("{}target:{} closing connection", logPrefix, container.getSource().getBase());
                        closeConnection(container.getSource().getBase());
                        trigger(new CloseConnection(container.getSource()), holePunching);
                    }
                };

        private DecoratedAddress closeConnection(BasicAddress target) {
            return openConnections.remove(target);
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

    //**************************CONNECTION MAKER********************************
    public class ConnectionMaker {
        public void startConnection(OpenConnection.Request req) {
            
        }
    }

    public static class HPClientInit extends Init<HPClientComp> {

        public final HPConfig config;
        public final DecoratedAddress self;

        public HPClientInit(HPConfig config, DecoratedAddress self) {
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
}
