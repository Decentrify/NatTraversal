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
package se.sics.nat.pm.client;

import com.google.common.collect.Sets;
import java.util.HashSet;
import java.util.Iterator;
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
import se.sics.nat.pm.client.msg.Update;
import se.sics.nat.pm.common.PMConfig;
import se.sics.nat.pm.common.PMMsg;
import se.sics.p2ptoolbox.croupier.CroupierPort;
import se.sics.p2ptoolbox.croupier.msg.CroupierSample;
import se.sics.p2ptoolbox.util.network.ContentMsg;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;
import se.sics.p2ptoolbox.util.network.impl.BasicContentMsg;
import se.sics.p2ptoolbox.util.network.impl.BasicHeader;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;
import se.sics.p2ptoolbox.util.network.impl.DecoratedHeader;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class PMClientComp extends ComponentDefinition {

    private final static Logger LOG = LoggerFactory.getLogger(PMClientComp.class);
    private final String logPrefix;

    private Negative<PMClientPort> parentMaker = provides(PMClientPort.class);
    private Positive<Network> network = requires(Network.class);
    private Positive<Timer> timer = requires(Timer.class);
    private Positive<CroupierPort> croupier = requires(CroupierPort.class);

    private final PMConfig config;
    private DecoratedAddress self;
    private boolean changed;
    private final Set<DecoratedAddress> parents;
    private final Set<DecoratedAddress> heartbeats; //TODO Alex - check timeouts and rtts

    private UUID insternalStateCheckId;
    private UUID heartbeatId;
    private UUID heartbeatCheckId;

    public PMClientComp(PMClientInit init) {
        this.self = new DecoratedAddress(init.self);
        this.logPrefix = self.getBase() + " ";
        LOG.info("{}initiating...", logPrefix);

        this.config = init.config;
        this.changed = true;
        this.parents = new HashSet<DecoratedAddress>();
        this.heartbeats = new HashSet<DecoratedAddress>();

        subscribe(handleStart, control);
        subscribe(handleStop, control);
        subscribe(handleInternalStateCheck, timer);
        subscribe(handleCroupierSample, croupier);
        subscribe(handleRegisterResp, network);
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
            LOG.info("{}internal state check - parents:{}, heartbeats:{}",
                    new Object[]{logPrefix, parents.size(), heartbeats.size()});
        }
    };

    Handler handleCroupierSample = new Handler<CroupierSample>() {
        @Override
        public void handle(CroupierSample sample) {
            LOG.debug("{}received sample - \n public:{}",
                    new Object[]{logPrefix, sample.publicSample});
            if (parents.size() < config.nrParents) {
                int nextParents = 2 * config.nrParents - parents.size();
                Iterator<DecoratedAddress> it = sample.publicSample.iterator();
                while (it.hasNext() && nextParents > 0) {
                    DecoratedAddress nextParent = it.next();
                    if (!parents.contains(nextParent)) {
                        LOG.info("{}connecting to parent:{}", logPrefix, nextParent.getBase());
                        DecoratedHeader<DecoratedAddress> requestHeader = new DecoratedHeader(new BasicHeader(self, nextParent, Transport.UDP), null, null);
                        ContentMsg request = new BasicContentMsg(requestHeader, new PMMsg.RegisterReq());
                        trigger(request, network);
                    }
                }
            }
        }
    };

    ClassMatchedHandler handleRegisterResp
            = new ClassMatchedHandler<PMMsg.RegisterResp, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, PMMsg.RegisterResp>>() {
                @Override
                public void handle(PMMsg.RegisterResp content, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, PMMsg.RegisterResp> container) {
                    LOG.info("{}register response:{}", logPrefix, content.status);
                    if (content.status.equals(PMMsg.RegisterStatus.ACCEPTED)) {
                        if (parents.size() < config.nrParents) {
                            LOG.info("{}register parent:{}", logPrefix, container.getSource());
                            parents.add(container.getSource());
                            changed = true;
                        }
                    }
                }
            };

    ClassMatchedHandler handleUnRegister
            = new ClassMatchedHandler<PMMsg.UnRegister, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, PMMsg.UnRegister>>() {
                @Override
                public void handle(PMMsg.UnRegister content, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, PMMsg.UnRegister> container) {
                    LOG.info("{}unregister from:{}", logPrefix, container.getSource());
                    if (parents.contains(container.getSource())) {
                        parents.remove(container.getSource());
                        changed = true;
                    }
                }
            };

    ClassMatchedHandler handleHeartbeat
            = new ClassMatchedHandler<PMMsg.UnRegister, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, PMMsg.UnRegister>>() {
                @Override
                public void handle(PMMsg.UnRegister content, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, PMMsg.UnRegister> container) {
                    LOG.debug("{}heartbeat from:{}", logPrefix, container.getSource());
                    heartbeats.add(container.getSource());
                }
            };

    Handler handleHeartbeatTimeout = new Handler<PeriodicHeartbeat>() {
        @Override
        public void handle(PeriodicHeartbeat event) {
            LOG.debug("{}periodic heartbeat", logPrefix);
            for (DecoratedAddress parent : parents) {
                LOG.debug("{}heartbeating to parent:{}", logPrefix, parent.getBase());
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
            Set<DecoratedAddress> suspected = Sets.difference(parents, heartbeats);
            if (!suspected.isEmpty()) {
                LOG.info("{}removing suspected parents:{}", logPrefix, suspected);
                parents.removeAll(suspected);
                changed = true;
            }
            if(changed && parents.size() == config.nrParents) {
                self = new DecoratedAddress(self.getBase(), parents);
                LOG.info("{}update self:{}", logPrefix, self);
                trigger(new Update(self), parentMaker);
                changed = false;
            }
        }
    };

    public static class PMClientInit extends Init<PMClientComp> {

        public final PMConfig config;
        public final BasicAddress self;

        public PMClientInit(PMConfig config, BasicAddress self) {
            this.config = config;
            this.self = self;
        }
    }

    private void scheduleInternalStateCheck() {
        if (insternalStateCheckId != null) {
            LOG.warn("{}double starting internal state check timeout", logPrefix);
            return;
        }
        SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(config.internalStateCheck, config.internalStateCheck);
        PeriodicInternalStateCheck sc = new PeriodicInternalStateCheck(spt);
        spt.setTimeoutEvent(sc);
        insternalStateCheckId = sc.getTimeoutId();
        trigger(spt, timer);
    }

    private void cancelInternalStateCheck() {
        if (insternalStateCheckId == null) {
            LOG.warn("{}double stopping internal state check timeout", logPrefix);
            return;
        }
        CancelPeriodicTimeout cpt = new CancelPeriodicTimeout(insternalStateCheckId);
        insternalStateCheckId = null;
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
