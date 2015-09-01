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
package se.sics.nat.pm.server;

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
import se.sics.nat.common.NatTraverserConfig;
import se.sics.nat.pm.common.PMMsg;
import se.sics.nat.pm.server.msg.Update;
import se.sics.p2ptoolbox.util.network.ContentMsg;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;
import se.sics.p2ptoolbox.util.network.impl.BasicContentMsg;
import se.sics.p2ptoolbox.util.network.impl.BasicHeader;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;
import se.sics.p2ptoolbox.util.network.impl.DecoratedHeader;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class PMServerComp extends ComponentDefinition {

    private final static Logger LOG = LoggerFactory.getLogger(PMServerComp.class);
    private final String logPrefix;

    private Negative<PMServerPort> parentMaker = provides(PMServerPort.class);
    private Positive<Network> network = requires(Network.class);
    private Positive<Timer> timer = requires(Timer.class);

    private final NatTraverserConfig config;
    private DecoratedAddress self;
    private boolean changed;
    private final Map<BasicAddress, DecoratedAddress> children;
    private final Set<BasicAddress> heartbeats; //TODO Alex - check timeouts and rtts

    private UUID internalStateCheckId;
    private UUID heartbeatId;
    private UUID heartbeatCheckId;

    public PMServerComp(PMServerInit init) {
        this.self = new DecoratedAddress(init.self);
        this.logPrefix = self.getBase() + " ";
        LOG.info("{}initiating...", logPrefix);

        this.config = init.config;
        this.changed = false;
        this.children = new HashMap<BasicAddress, DecoratedAddress>();
        this.heartbeats = new HashSet<BasicAddress>();

        subscribe(handleStart, control);
        subscribe(handleStop, control);
        subscribe(handleInternalStateCheck, timer);
        subscribe(handleRegisterReq, network);
        subscribe(handleUnRegister, network);
        subscribe(handleHeartbeat, network);
        subscribe(handleHeartbeatTimeout, timer);
        subscribe(handleHeartbeatCheckTimeout, timer);
    }

    Handler handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            LOG.info("{}starting...", logPrefix);
            scheduleInternalStateCheck();
            scheduleHeartbeat();
            scheduleHeartbeatCheck();
        }
    };

    Handler handleStop = new Handler<Stop>() {
        @Override
        public void handle(Stop event) {
            LOG.info("{}stopping...", logPrefix);
            cancelInternalStateCheck();
            cancelHeartbeat();
            cancelHeartbeatCheck();
        }
    };

    Handler handleInternalStateCheck = new Handler<PeriodicInternalStateCheck>() {
        @Override
        public void handle(PeriodicInternalStateCheck event) {
            LOG.info("{}internal state check - children:{}, heartbeats:{}",
                    new Object[]{logPrefix, children.size(), heartbeats.size()});
        }
    };

    ClassMatchedHandler handleRegisterReq
            = new ClassMatchedHandler<PMMsg.RegisterReq, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, PMMsg.RegisterReq>>() {
                @Override
                public void handle(PMMsg.RegisterReq content, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, PMMsg.RegisterReq> container) {
                    LOG.info("{}register request from:{}", logPrefix, container.getSource());
                    PMMsg.RegisterResp respContent;
                    if (children.size() < config.nrChildren) {
                        children.put(container.getSource().getBase(), container.getSource());
                        changed = true;
                        respContent = new PMMsg.RegisterResp(PMMsg.RegisterStatus.ACCEPTED);
                    } else {
                        respContent = new PMMsg.RegisterResp(PMMsg.RegisterStatus.DENIED);
                    }
                    LOG.info("{}register resp:{} to child:{}",
                            new Object[]{logPrefix, respContent.status, container.getSource()});
                    DecoratedHeader<DecoratedAddress> responseHeader = new DecoratedHeader(new BasicHeader(self, container.getSource(), Transport.UDP), null, null);
                    ContentMsg response = new BasicContentMsg(responseHeader, respContent);
                    trigger(response, network);
                }
            };

    ClassMatchedHandler handleUnRegister
            = new ClassMatchedHandler<PMMsg.UnRegister, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, PMMsg.UnRegister>>() {
                @Override
                public void handle(PMMsg.UnRegister content, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, PMMsg.UnRegister> container) {
                    LOG.info("{}unregister from:{}", logPrefix, container.getSource());
                    if (children.containsKey(container.getSource().getBase())) {
                        children.remove(container.getSource().getBase());
                        changed = true;
                    }
                }
            };

    ClassMatchedHandler handleHeartbeat
            = new ClassMatchedHandler<PMMsg.Heartbeat, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, PMMsg.Heartbeat>>() {
                @Override
                public void handle(PMMsg.Heartbeat content, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, PMMsg.Heartbeat> container) {
                    LOG.debug("{}heartbeat from:{}", logPrefix, container.getSource());
                    heartbeats.add(container.getSource().getBase());
                    if(children.containsKey(container.getSource().getBase())) {
                        children.put(container.getSource().getBase(), container.getSource());
                    }
                }
            };

    Handler handleHeartbeatTimeout = new Handler<PeriodicHeartbeat>() {
        @Override
        public void handle(PeriodicHeartbeat event) {
            LOG.debug("{}periodic heartbeat", logPrefix);
            for (DecoratedAddress parent : children.values()) {
                LOG.debug("{}heartbeating to child:{}", logPrefix, parent.getBase());
                DecoratedHeader<DecoratedAddress> requestHeader = new DecoratedHeader(new BasicHeader(self, parent, Transport.UDP), null, null);
                ContentMsg request = new BasicContentMsg(requestHeader, new PMMsg.Heartbeat());
                trigger(request, network);
            }
        }
    };

    Handler handleHeartbeatCheckTimeout = new Handler<PeriodicHeartbeatCheck>() {
        @Override
        public void handle(PeriodicHeartbeatCheck event) {
            LOG.debug("{}periodic heartbeat check", logPrefix);
            Set<BasicAddress> suspected = Sets.difference(children.keySet(), heartbeats);
            if (!suspected.isEmpty()) {
                LOG.info("{}removing suspected children:{}", logPrefix, suspected);
                for (BasicAddress suspectChild : suspected) {
                    children.remove(suspectChild);
                }
                changed = true;
            }
            if (changed) {
                trigger(new Update(new HashMap(children)), parentMaker);
                changed = false;
            }
        }
    };

    public static class PMServerInit extends Init<PMServerComp> {

        public final NatTraverserConfig config;
        public final BasicAddress self;

        public PMServerInit(NatTraverserConfig config, BasicAddress self) {
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

    private void scheduleHeartbeat() {
        if (heartbeatId != null) {
            LOG.warn("{}double starting heartbeat timeout", logPrefix);
            return;
        }
        SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(config.heartbeat, config.heartbeat);
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

    public static class PeriodicHeartbeat extends Timeout {

        public PeriodicHeartbeat(SchedulePeriodicTimeout spt) {
            super(spt);
        }
    }

    private void scheduleHeartbeatCheck() {
        if (heartbeatCheckId != null) {
            LOG.warn("{}double starting heartbeat check timeout", logPrefix);
            return;
        }
        SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(2 * config.heartbeat, 2 * config.heartbeat);
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

    public static class PeriodicHeartbeatCheck extends Timeout {

        public PeriodicHeartbeatCheck(SchedulePeriodicTimeout spt) {
            super(spt);
        }
    }
}
