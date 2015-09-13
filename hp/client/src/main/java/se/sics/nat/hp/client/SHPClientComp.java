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

import java.util.HashMap;
import java.util.Map;
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
import se.sics.kompics.network.Network;
import se.sics.kompics.network.Transport;
import se.sics.kompics.timer.CancelPeriodicTimeout;
import se.sics.kompics.timer.CancelTimeout;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;
import se.sics.kompics.timer.Timer;
import se.sics.nat.hp.client.msg.OpenConnection;
import se.sics.nat.hp.client.util.SHPInitiatorSession;
import se.sics.nat.hp.client.util.SHPSession;
import se.sics.nat.hp.client.util.SHPTargetSession;
import se.sics.nat.common.NatTraverserConfig;
import se.sics.nat.hp.common.msg.SimpleHolePunching.Initiate;
import se.sics.nat.hp.common.msg.SimpleHolePunching.Ping;
import se.sics.nat.hp.common.msg.SimpleHolePunching.Pong;
import se.sics.nat.hp.common.msg.SimpleHolePunching.Ready;
import se.sics.nat.hp.common.msg.SimpleHolePunching.Relay;
import se.sics.p2ptoolbox.util.nat.NatedTrait;
import se.sics.p2ptoolbox.util.network.ContentMsg;
import se.sics.p2ptoolbox.util.network.impl.BasicContentMsg;
import se.sics.p2ptoolbox.util.network.impl.BasicHeader;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;
import se.sics.p2ptoolbox.util.network.impl.DecoratedHeader;
import se.sics.p2ptoolbox.util.update.SelfAddressUpdate;
import se.sics.p2ptoolbox.util.update.SelfAddressUpdatePort;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class SHPClientComp extends ComponentDefinition {

    private static final Logger LOG = LoggerFactory.getLogger(SHPClientComp.class);
    private String logPrefix = "";

    private final Negative<SHPClientPort> holePunching = provides(SHPClientPort.class);
    private final Positive<Network> network = requires(Network.class);
    private final Positive<Timer> timer = requires(Timer.class);
    private final Positive<SelfAddressUpdatePort> parentMaker = requires(SelfAddressUpdatePort.class);

    private final NatTraverserConfig config;
    private DecoratedAddress self;

    private UUID internalStateCheckId;

    private final Map<UUID, SHPInitiatorSession> initiatorSessions;
    private final Map<UUID, SHPTargetSession> targetSessions;

    public SHPClientComp(SHPClientInit init) {
        this.config = init.config;
        this.self = init.self;
        this.logPrefix = self.getBase() + " ";
        LOG.info("{}initiating...", logPrefix);

        this.initiatorSessions = new HashMap<>();
        this.targetSessions = new HashMap<>();

        //control
        subscribe(handleStart, control);
        subscribe(handleStop, control);
        subscribe(handleInternalStateCheck, timer);
        subscribe(handleSelfAddressUpdate, parentMaker);

        subscribe(handleOpenConnection, holePunching);

        //connection initiator
        subscribe(handlePing, network);
        subscribe(handleReady, network);

        //connection target
        subscribe(handleInitiate, network);
        subscribe(handlePong, network);

        subscribe(handleMsgTimeout, timer);
    }

    //********************************CONTROL***********************************
    Handler handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            LOG.info("{}starting...", logPrefix);
            scheduleInternalStateCheck();
        }
    };

    Handler handleStop = new Handler<Stop>() {
        @Override
        public void handle(Stop event) {
            LOG.info("{}stopping...", logPrefix);
            cancelInternalStateCheck();
        }
    };

    Handler handleInternalStateCheck = new Handler<PeriodicInternalStateCheck>() {
        @Override
        public void handle(PeriodicInternalStateCheck event) {
            LOG.info("{}sessions - initiator:{} target:{}",
                    new Object[]{logPrefix, initiatorSessions.size(), targetSessions.size()});
        }
    };

    Handler handleSelfAddressUpdate = new Handler<SelfAddressUpdate>() {
        @Override
        public void handle(SelfAddressUpdate update) {
            LOG.info("{}updating self from:{} to:{}",
                    new Object[]{logPrefix, self, update.self});
            self = update.self;
        }
    };

    //********************************LOCAL*************************************
    Handler handleOpenConnection = new Handler<OpenConnection.Request>() {
        @Override
        public void handle(OpenConnection.Request request) {
            LOG.info("{}open connection request to:{}", logPrefix, request.target.getBase());
            
            assert request.target.hasTrait(NatedTrait.class);
            if(request.target.getTrait(NatedTrait.class).parents.isEmpty()) {
                LOG.info("{}open connection to:{} failed - no parents", logPrefix, request.target.getBase());
                trigger(request.fail(HPFailureStatus.TIMEOUT), holePunching);
                return;
            }

            SHPInitiatorSession session = new SHPInitiatorSession(UUID.randomUUID(), request);
            initiatorSessions.put(session.id, session);

            Relay relayContent = new Relay(Pair.with(session.id, UUID.randomUUID()), request.target);
            for (DecoratedAddress parent : request.target.getTrait(NatedTrait.class).parents) {
                LOG.debug("{}sending shp relay to parent:{}", logPrefix, parent.getBase());
                DecoratedHeader<DecoratedAddress> relayHeader = new DecoratedHeader(new BasicHeader(self, parent, Transport.UDP), null, null);
                ContentMsg relayMsg = new BasicContentMsg(relayHeader, relayContent);
                trigger(relayMsg, network);
            }
            session.pendingMsg(relayContent.msgId, scheduleMsgTimeout(relayContent.msgId, request.target));
        }
    };

    //*******************************COMMON*************************************
    Handler handleMsgTimeout = new Handler<MsgTimeout>() {
        @Override
        public void handle(MsgTimeout timeout) {
            LOG.debug("{}timeout", logPrefix);
            if (initiatorSessions.containsKey(timeout.msgId.getValue0())) {
                SHPInitiatorSession session = initiatorSessions.get(timeout.msgId.getValue0());
                if (session == null) {
                    LOG.error("{}initiator session management error", logPrefix);
                    throw new RuntimeException("initiator session management error");
                }
                if (session.timeout(timeout.target)) {
                    LOG.info("{}session initiator to:{} timed out",
                            new Object[]{logPrefix, session.req.target.getBase()});
                    trigger(session.req.fail(session.status), holePunching);
                    cleanSession(session);
                    initiatorSessions.remove(session.id);
                } else {
                    LOG.error("{}initiator timeout management error", logPrefix);
                    throw new RuntimeException("initiator timeout management error");
                }
            } else if (targetSessions.containsKey(timeout.msgId.getValue0())) {
                SHPTargetSession session = targetSessions.get(timeout.msgId.getValue0());
                if (session == null) {
                    LOG.error("{}target session management error", logPrefix);
                    throw new RuntimeException("target session management error");
                }
                if (session.timeout(timeout.target)) {
                    LOG.info("{}session target from:{} timed out",
                            new Object[]{logPrefix, session.target, session.status});
                    cleanSession(session);
                    targetSessions.remove(session.id);
                } else {
                    LOG.error("{}target timeout management error", logPrefix);
                    throw new RuntimeException("target timeout management error");
                }
            } else {
                LOG.trace("{}late timeout", logPrefix);
            }
        }
    };

    //************************CONNECTION_INITIATOR******************************
    ClassMatchedHandler handlePing
            = new ClassMatchedHandler<Ping, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Ping>>() {
                @Override
                public void handle(Ping content, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Ping> container) {
                    LOG.debug("{}received shp ping from:{}", logPrefix, container.getSource().getBase());
                    SHPInitiatorSession session = initiatorSessions.get(content.msgId.getValue0());
                    if (session == null) {
                        LOG.debug("{}late msg:{}", logPrefix, content);
                        return;
                    }
                    cleanSession(session);

                    if (NatedTrait.isOpen(self)) {
                        initiatorSessions.remove(session.id);

                        Ready readyContent = content.ready(container.getSource());
                        DecoratedHeader<DecoratedAddress> readyHeader = new DecoratedHeader(
                                new BasicHeader(container.getDestination(), container.getSource(), Transport.UDP), null, null);
                        ContentMsg readyMsg = new BasicContentMsg(readyHeader, readyContent);
                        trigger(readyMsg, network);

                        LOG.info("{}connection ready from:{} on:{}",
                                new Object[]{logPrefix, container.getSource().getBase(), container.getDestination().getBase()});
                        trigger(session.req.success(container.getDestination(), container.getSource()), holePunching);
                    } else {
                        Pong pongContent = content.answer();
                        DecoratedHeader<DecoratedAddress> pongHeader = new DecoratedHeader(
                                new BasicHeader(container.getDestination(), container.getSource(), Transport.UDP), null, null);
                        ContentMsg pongMsg = new BasicContentMsg(pongHeader, pongContent);
                        trigger(pongMsg, network);
                        session.pendingMsg(pongContent.msgId, scheduleMsgTimeout(pongContent.msgId, container.getSource()));
                    }
                }
            };

    //can be on both initiator and target
    ClassMatchedHandler handleReady
            = new ClassMatchedHandler<Ready, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Ready>>() {
                @Override
                public void handle(Ready content, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Ready> container) {
                    LOG.debug("{}received shp ready from:{} on:{}",
                            new Object[]{logPrefix, container.getSource().getBase(), content.observed.getBase()});
                    if (initiatorSessions.containsKey(content.msgId.getValue0())) {
                        SHPInitiatorSession session = initiatorSessions.get(content.msgId.getValue0());

                        cleanSession(session);
                        initiatorSessions.remove(session.id);

                        LOG.info("{}connection ready from:{} on:{}",
                                new Object[]{logPrefix, container.getSource().getBase(), content.observed.getBase()});
                        trigger(session.req.success(content.observed, container.getSource()), holePunching);
                        return;
                    } else if (targetSessions.containsKey(content.msgId.getValue0())) {
                        SHPTargetSession session = targetSessions.get(content.msgId.getValue0());

                        cleanSession(session);
                        targetSessions.remove(session.id);

                        LOG.info("{}connection ready from:{} on:{}",
                                new Object[]{logPrefix, container.getSource().getBase(), content.observed.getBase()});
                        trigger(new OpenConnection.Success(UUID.randomUUID(), content.observed, container.getSource()), holePunching);
                        return;
                    }
                    LOG.debug("{}late msg:{}", logPrefix, content);
                    return;
                }
            };
//**************************CONNECTION_TARGET*******************************
    ClassMatchedHandler handleInitiate
            = new ClassMatchedHandler<Initiate, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Initiate>>() {
                @Override
                public void handle(Initiate content, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Initiate> container) {
                    LOG.debug("{}received shp initiate to:{}", logPrefix, content.connectTo.getBase());

                    if (!self.getTrait(NatedTrait.class).parents.contains(container.getSource())) {
                        LOG.warn("{}node:{} that is not my parent tried to make me open a connection to:{}",
                                new Object[]{logPrefix, container.getSource().getBase(), content.connectTo.getBase()});
                        return;
                    }
                    if (targetSessions.containsKey(content.msgId.getValue0())) {
                        LOG.trace("{}session already started by another parent");
                        return;
                    }
                    SHPTargetSession session = new SHPTargetSession(UUID.randomUUID(), content.connectTo.getBase());
                    targetSessions.put(session.id, session);

                    Ping pingContent = content.answer();
                    DecoratedHeader<DecoratedAddress> pingHeader = new DecoratedHeader(
                            new BasicHeader(self, content.connectTo, Transport.UDP), null, null);
                    ContentMsg pingMsg = new BasicContentMsg(pingHeader, pingContent);
                    trigger(pingMsg, network);
                    session.pendingMsg(pingContent.msgId, scheduleMsgTimeout(pingContent.msgId, content.connectTo));
                }
            };

    ClassMatchedHandler handlePong
            = new ClassMatchedHandler<Pong, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Pong>>() {
                @Override
                public void handle(Pong content, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Pong> container) {
                    LOG.debug("{}received shp pong from:{}", logPrefix, container.getSource().getBase());
                    SHPTargetSession session = targetSessions.get(content.msgId.getValue0());
                    if (session == null) {
                        LOG.debug("{}late msg:{}", logPrefix, content);
                        return;
                    }

                    cleanSession(session);
                    initiatorSessions.remove(session.id);

                    Ready readyContent = content.answer(container.getSource());
                    DecoratedHeader<DecoratedAddress> readyHeader = new DecoratedHeader(
                            new BasicHeader(container.getDestination(), container.getSource(), Transport.UDP), null, null);
                    ContentMsg readyMsg = new BasicContentMsg(readyHeader, readyContent);
                    trigger(readyMsg, network);

                    LOG.info("{}connection ready from:{} on:{}",
                            new Object[]{logPrefix, container.getSource().getBase(), container.getDestination().getBase()});
                    trigger(new OpenConnection.Success(UUID.randomUUID(), container.getDestination(), container.getSource()), holePunching);
                }
            };

    private void cleanSession(SHPSession session) {
        for (UUID timeoutId : session.clearMsgs().values()) {
            cancelMsgTimeout(timeoutId);

        }
    }

    public static class SHPClientInit extends Init<SHPClientComp> {

        public final NatTraverserConfig config;
        public final DecoratedAddress self;

        public SHPClientInit(NatTraverserConfig config, DecoratedAddress self) {
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

    private UUID scheduleMsgTimeout(Pair<UUID, UUID> msgId, DecoratedAddress target) {
        ScheduleTimeout st = new ScheduleTimeout(config.msgRTT);
        MsgTimeout mt = new MsgTimeout(st, msgId, target);
        st.setTimeoutEvent(mt);
        trigger(st, timer);
        return mt.getTimeoutId();
    }

    private void cancelMsgTimeout(UUID timeoutId) {
        CancelTimeout ct = new CancelTimeout(timeoutId);
        trigger(ct, timer);

    }

    public static class MsgTimeout extends Timeout {

        public final Pair<UUID, UUID> msgId; //<sessionId, msgId>
        public final DecoratedAddress target;

        public MsgTimeout(ScheduleTimeout st, Pair<UUID, UUID> msgId, DecoratedAddress target) {
            super(st);
            this.msgId = msgId;
            this.target = target;
        }
    }
}
