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

import java.util.HashMap;
import java.util.Map;
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
import se.sics.kompics.network.Network;
import se.sics.kompics.network.Transport;
import se.sics.kompics.timer.CancelPeriodicTimeout;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.Timeout;
import se.sics.kompics.timer.Timer;
import se.sics.ktoolbox.fd.FailureDetectorPort;
import se.sics.ktoolbox.fd.event.FDEvent;
import se.sics.nat.pm.PMMsg;
import se.sics.nat.pm.ParentMakerKCWrapper;
import se.sics.nat.pm.server.msg.Update;
import se.sics.nat.pm.util.ParentMakerView;
import se.sics.p2ptoolbox.croupier.CroupierPort;
import se.sics.p2ptoolbox.croupier.msg.CroupierUpdate;
import se.sics.p2ptoolbox.util.config.KConfigCore;
import se.sics.p2ptoolbox.util.network.ContentMsg;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;
import se.sics.p2ptoolbox.util.network.impl.BasicContentMsg;
import se.sics.p2ptoolbox.util.network.impl.BasicHeader;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;
import se.sics.p2ptoolbox.util.network.impl.DecoratedHeader;
import se.sics.p2ptoolbox.util.update.SelfViewUpdatePort;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class PMServerComp extends ComponentDefinition {

    private final static Logger LOG = LoggerFactory.getLogger(PMServerComp.class);
    private final String logPrefix;

    private final Negative<PMServerPort> parentMaker = provides(PMServerPort.class);
    private final Positive<Network> network = requires(Network.class);
    private final Positive<Timer> timer = requires(Timer.class);
    private final Positive<CroupierPort> pmCroupier = requires(CroupierPort.class);
    private final Negative<SelfViewUpdatePort> pmViewUpdate = provides(SelfViewUpdatePort.class);
    private final Positive<FailureDetectorPort> fd = requires(FailureDetectorPort.class);

    private final ParentMakerKCWrapper config;
    private DecoratedAddress self;

    private final Map<BasicAddress, DecoratedAddress> children = new HashMap<>();

    private UUID internalStateCheckId;

    public PMServerComp(PMServerInit init) {
        this.config = init.config;
        this.self = init.self;
        this.logPrefix = "<nid:" + self.getId() + "> ";
        LOG.info("{}initiating...", logPrefix);

        subscribe(handleStart, control);
        subscribe(handleInternalStateCheck, timer);
        subscribe(handleRegisterReq, network);
        subscribe(handleUnRegister, network);
        subscribe(handleSuspectChild, fd);
    }

    Handler handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            LOG.info("{}starting...", logPrefix);
            trigger(CroupierUpdate.update(new ParentMakerView()), pmViewUpdate);
            scheduleInternalStateCheck();
        }
    };

    Handler handleInternalStateCheck = new Handler<PeriodicInternalStateCheck>() {
        @Override
        public void handle(PeriodicInternalStateCheck event) {
            LOG.info("{}internal state check - children:{}",
                    new Object[]{logPrefix, children.size()});
        }
    };

    ClassMatchedHandler handleRegisterReq
            = new ClassMatchedHandler<PMMsg.RegisterReq, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, PMMsg.RegisterReq>>() {
                @Override
                public void handle(PMMsg.RegisterReq content, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, PMMsg.RegisterReq> container) {
                    LOG.info("{}register request from:{}", logPrefix, container.getSource());
                    PMMsg.RegisterResp respContent;
                    if (children.size() < config.nrChildren) {
                        addChild(container.getSource());
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
                        removeChild(container.getSource());
                    }
                }
            };

    Handler handleSuspectChild = new Handler<FDEvent.Suspect>() {
        @Override
        public void handle(FDEvent.Suspect event) {
            if (!children.containsKey(event.target.getBase())) {
                LOG.info("{}possibly old child:{} - obsolete suspect", new Object[]{logPrefix, event.target});
                return;
            }
            LOG.info("{}suspect:{}", new Object[]{logPrefix, event.target});
            removeChild(event.target);
            DecoratedHeader<DecoratedAddress> msgHeader = new DecoratedHeader(
                    new BasicHeader(self, event.target, Transport.UDP), null, null);
            ContentMsg msg = new BasicContentMsg(msgHeader, new PMMsg.UnRegister());
            trigger(msg, network);
        }
    };

    private void addChild(DecoratedAddress child) {
        children.put(child.getBase(), child);
        trigger(new FDEvent.Follow(child, config.natParentService, PMServerComp.this.getComponentCore().id()), fd);
        if (children.size() == config.nrChildren) {
            trigger(CroupierUpdate.observer(), pmViewUpdate);
        }
        trigger(new Update(new HashMap(children)), parentMaker);
    }

    private void removeChild(DecoratedAddress child) {
        children.remove(child.getBase());
        trigger(new FDEvent.Unfollow(child, config.natParentService, PMServerComp.this.getComponentCore().id()), fd);
        trigger(CroupierUpdate.update(new ParentMakerView()), pmViewUpdate);
        trigger(new Update(new HashMap(children)), parentMaker);
    }

    public static class PMServerInit extends Init<PMServerComp> {

        public final ParentMakerKCWrapper config;
        public final DecoratedAddress self;

        public PMServerInit(KConfigCore configCore, DecoratedAddress self) {
            this.config = new ParentMakerKCWrapper(configCore);
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

    private static class PeriodicInternalStateCheck extends Timeout {

        public PeriodicInternalStateCheck(SchedulePeriodicTimeout spt) {
            super(spt);
        }
    }
}
