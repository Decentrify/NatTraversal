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

import se.sics.nat.pm.ParentMakerKCWrapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
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
import se.sics.nat.pm.util.ParentMakerView;
import se.sics.p2ptoolbox.croupier.CroupierPort;
import se.sics.p2ptoolbox.croupier.msg.CroupierSample;
import se.sics.p2ptoolbox.croupier.msg.CroupierUpdate;
import se.sics.p2ptoolbox.util.Container;
import se.sics.p2ptoolbox.util.config.KConfigCore;
import se.sics.p2ptoolbox.util.nat.NatedTrait;
import se.sics.p2ptoolbox.util.network.ContentMsg;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;
import se.sics.p2ptoolbox.util.network.impl.BasicContentMsg;
import se.sics.p2ptoolbox.util.network.impl.BasicHeader;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;
import se.sics.p2ptoolbox.util.network.impl.DecoratedHeader;
import se.sics.p2ptoolbox.util.update.SelfAddress;
import se.sics.p2ptoolbox.util.update.SelfAddressUpdate;
import se.sics.p2ptoolbox.util.update.SelfAddressUpdatePort;
import se.sics.p2ptoolbox.util.update.SelfViewUpdatePort;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class PMClientComp extends ComponentDefinition {

    private final static Logger LOG = LoggerFactory.getLogger(PMClientComp.class);
    private final String logPrefix;

    private final Positive<Network> network = requires(Network.class);
    private final Positive<Timer> timer = requires(Timer.class);
    private final Positive<CroupierPort> pmCroupier = requires(CroupierPort.class);
    private final Negative<SelfViewUpdatePort> pmViewUpdate = provides(SelfViewUpdatePort.class); 
    private final Positive<FailureDetectorPort> fd = requires(FailureDetectorPort.class);
    private final Negative<SelfAddressUpdatePort> addressUpdate = provides(SelfAddressUpdatePort.class);
    

    private final ParentMakerKCWrapper config;
    private DecoratedAddress self;
    
    private final Map<BasicAddress, DecoratedAddress> parents = new HashMap<>();

    private UUID insternalStateCheckId;

    public PMClientComp(PMClientInit init) {
        this.config = init.config;
        this.self = init.self;
        this.logPrefix = "<nid:" + self.getId() + "> ";
        LOG.info("{}initiating with self:{}", logPrefix, self);

        subscribe(handleStart, control);
        subscribe(handleSelfAddressRequest, addressUpdate);
        subscribe(handleInternalStateCheck, timer);
        subscribe(handleParentsSample, pmCroupier);
        subscribe(handleRegisterResp, network);
        subscribe(handleUnRegister, network);
        subscribe(handleSuspectParent, fd);
    }

    Handler handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            LOG.info("{}starting...", logPrefix);
            trigger(CroupierUpdate.observer(), pmViewUpdate);
            scheduleInternalStateCheck();
        }
    };

    Handler handleSelfAddressRequest = new Handler<SelfAddress.Request>() {
        @Override
        public void handle(SelfAddress.Request req) {
            LOG.trace("{}self request", logPrefix);
            answer(req, req.answer(self));
        }
    };

    Handler handleInternalStateCheck = new Handler<PeriodicInternalStateCheck>() {
        @Override
        public void handle(PeriodicInternalStateCheck event) {
            LOG.info("{}internal state check - parents:{}",
                    new Object[]{logPrefix, parents.size()});
        }
    };

    Handler handleParentsSample = new Handler<CroupierSample<ParentMakerView>>() {
        @Override
        public void handle(CroupierSample<ParentMakerView> sample) {
            LOG.debug("{}received sample - public:{}",
                    new Object[]{logPrefix, sample.publicSample});
            if (parents.size() < config.nrParents) {
                int nextParents = config.nrParents - parents.size();
                Iterator<Container<DecoratedAddress, ParentMakerView>> it = sample.publicSample.iterator();
                while (it.hasNext() && nextParents > 0) {
                    DecoratedAddress nextParent = it.next().getSource();
                    if (!parents.containsKey(nextParent.getBase())) {
                        LOG.debug("{}connecting to parent:{}", logPrefix, nextParent.getBase());
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
                    LOG.debug("{}register response:{}", logPrefix, content.status);
                    if (content.status.equals(PMMsg.RegisterStatus.ACCEPTED)) {
                        if (parents.size() < config.nrParents) {
                            LOG.info("{}register parent:{}", logPrefix, container.getSource());
                            addParent(container.getSource());
                        }
                    }
                }
            };

    ClassMatchedHandler handleUnRegister
            = new ClassMatchedHandler<PMMsg.UnRegister, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, PMMsg.UnRegister>>() {
                @Override
                public void handle(PMMsg.UnRegister content, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, PMMsg.UnRegister> container) {
                    LOG.debug("{}unregister from:{}", logPrefix, container.getSource());
                    if (parents.containsKey(container.getSource().getBase())) {
                        removeParent(container.getSource());
                    }
                }
            };

    Handler handleSuspectParent = new Handler<FDEvent.Suspect>() {
        @Override
        public void handle(FDEvent.Suspect event) {
            if (!parents.containsKey(event.target.getBase())) {
                LOG.debug("{}possibly old child:{} - obsolete suspect", new Object[]{logPrefix, event.target.getBase()});
                return;
            }
            LOG.info("{}suspect:{}", new Object[]{logPrefix, event.target.getBase()});
            removeParent(event.target);
            DecoratedHeader<DecoratedAddress> msgHeader = new DecoratedHeader(
                    new BasicHeader(self, event.target, Transport.UDP), null, null);
            ContentMsg msg = new BasicContentMsg(msgHeader, new PMMsg.UnRegister());
            trigger(msg, network);
        }
    };

    private void addParent(DecoratedAddress child) {
        parents.put(child.getBase(), child);
        trigger(new FDEvent.Follow(child, config.natParentService, PMClientComp.this.getComponentCore().id()), fd);
        updateSelf();
    }

    private void removeParent(DecoratedAddress child) {
        parents.remove(child.getBase());
        trigger(new FDEvent.Unfollow(child, config.natParentService, PMClientComp.this.getComponentCore().id()), fd);
        updateSelf();
    }
    
    private void updateSelf() {
        NatedTrait nat = self.getTrait(NatedTrait.class).changeParents(new ArrayList<>(parents.values()));
        self = new DecoratedAddress(self.getBase());
        self.addTrait(nat);
        SelfAddressUpdate event = new SelfAddressUpdate(UUID.randomUUID(), self);
        LOG.trace("{}sending self address update:{}", logPrefix, event.id);
        trigger(event, addressUpdate);
    }
    public static class PMClientInit extends Init<PMClientComp> {

        public final ParentMakerKCWrapper config;
        public final DecoratedAddress self;

        public PMClientInit(KConfigCore configCore, DecoratedAddress self) {
            this.config = new ParentMakerKCWrapper(configCore);
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
}
