/*
 * Copyright (C) 2009 Swedish Institute of Computer Science (SICS) Copyright (C)
 * 2009 Royal Institute of Technology (KTH)
 *
 * GVoD is free software; you can redistribute it and/or
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
import se.sics.kompics.network.Network;
import se.sics.kompics.network.Transport;
import se.sics.kompics.timer.CancelTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;
import se.sics.kompics.timer.Timer;
import se.sics.ktoolbox.fd.FailureDetectorPort;
import se.sics.ktoolbox.fd.event.FDEvent;
import se.sics.ktoolbox.fd.event.FDEvent.Unfollow;
import se.sics.nat.stun.msg.StunEcho;
import se.sics.nat.stun.msg.server.StunPartner;
import se.sics.nat.stun.util.StunView;
import se.sics.p2ptoolbox.croupier.CroupierPort;
import se.sics.p2ptoolbox.croupier.msg.CroupierSample;
import se.sics.p2ptoolbox.croupier.msg.CroupierUpdate;
import se.sics.p2ptoolbox.util.Container;
import se.sics.p2ptoolbox.util.config.KConfigCore;
import se.sics.p2ptoolbox.util.network.ContentMsg;
import se.sics.p2ptoolbox.util.network.impl.BasicContentMsg;
import se.sics.p2ptoolbox.util.network.impl.BasicHeader;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;
import se.sics.p2ptoolbox.util.network.impl.DecoratedHeader;
import se.sics.p2ptoolbox.util.update.SelfViewUpdatePort;

/**
 * A partner is required to provide the stun service. Only nodes with the same
 * polarity can be partners - even or odd IDs can be partners. The reason is
 * that clients can then send 2 echo requests to 2 servers in parallel, knowing
 * that it won't mess up the NAT type identification by creating a NAT binding
 * to a partner as a side-effect of parallelizing the first Echo test.
 *
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class StunServerComp extends ComponentDefinition {

    private static final Logger LOG = LoggerFactory.getLogger(StunServerComp.class);
    private String logPrefix = "";

    private final Positive<Timer> timer = requires(Timer.class);
    private final Positive<Network> network = requires(Network.class);
    private final Positive<CroupierPort> croupier = requires(CroupierPort.class);
    private final Negative<SelfViewUpdatePort> croupierView = provides(SelfViewUpdatePort.class);
    private final Positive<FailureDetectorPort> failureDetector = requires(FailureDetectorPort.class);

    private final StunServerKCWrapper config;

    private Pair<DecoratedAddress, DecoratedAddress> self;

    private final EchoMngr echoMngr;
    private final PartnerMngr partnerMngr;

    public StunServerComp(StunServerInit init) {
        config = init.config;
        self = init.self;
        logPrefix = "<nid:" + self.getValue0().getId() + "> ";
        LOG.info("{}initiating...", logPrefix);

        echoMngr = new EchoMngr();
        partnerMngr = new PartnerMngr();

        subscribe(handleStart, control);
        subscribe(partnerMngr.handleSamples, croupier);
        subscribe(partnerMngr.handlePartnerRequest, network);
        subscribe(partnerMngr.handlePartnerResponse, network);
        subscribe(partnerMngr.handleMsgTimeout, timer);
        subscribe(partnerMngr.handleSuspectPartner, failureDetector);
        
        subscribe(echoMngr.handleEchoRequest, network);
    }

    Handler handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            LOG.info("{}starting...", logPrefix);
            partnerMngr.start();
        }
    };
    
    private void send(Object msg, DecoratedAddress src, DecoratedAddress dst) {
        DecoratedHeader<DecoratedAddress> responseHeader = new DecoratedHeader(new BasicHeader(
                src, dst, Transport.UDP), null, null);
        ContentMsg response = new BasicContentMsg(responseHeader, msg);
        LOG.trace("{}sending:{} from:{} to:{}", new Object[]{logPrefix, msg,
            responseHeader.getSource().getBase(), responseHeader.getDestination().getBase()});
        trigger(response, network);
    }

    //**************************************************************************
    private class EchoMngr {

        ClassMatchedHandler handleEchoRequest
                = new ClassMatchedHandler<StunEcho.Request, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, StunEcho.Request>>() {

                    @Override
                    public void handle(StunEcho.Request content, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, StunEcho.Request> container) {
                        if(partnerMngr.getPartner() == null) {
                            send(content.reset(), container.getSource(), container.getDestination());
                            return;
                        }
                        DecoratedAddress recSelf = container.getDestination();
                        LOG.debug("{}received:{} from:{} on:{}",
                                new Object[]{logPrefix, content, container.getSource().getBase(), recSelf.getBase()});
                        switch (content.type) {
                            case SIP_SP: {
                                send(content.answer(container.getSource()), container.getDestination(), container.getSource());
                            }
                            break;
                            case SIP_DP: {
                                send(content.answer(container.getSource()), self.getValue1(), container.getSource());
                            }
                            break;
                            case DIP_DP: {
                                if (container.getSource().getId().equals(content.target.getId())) {
                                    send(content, self.getValue0(), partnerMngr.getPartner());
                                } else {
                                    send(content.answer(), self.getValue1(), content.target);
                                }
                            }
                            break;
                        }
                    }
                };
    }

    private class PartnerMngr {

        private Pair<DecoratedAddress, DecoratedAddress> partner;
        private Pair<UUID, DecoratedAddress> pendingPartner;

        void start() {
            LOG.info("{}looking for partner", logPrefix);
            trigger(CroupierUpdate.update(StunView.empty(self)), croupierView);
        }

        DecoratedAddress getPartner() {
            return partner.getValue0();
        }

        Handler handleSamples = new Handler<CroupierSample<StunView>>() {

            @Override
            public void handle(CroupierSample<StunView> sample) {
                if(partner != null) {
                    return;
                }
                if (self.getValue0().getId() % 2 == 1) {
                    return; // 0s search for partners actively
                }
                for (Container<DecoratedAddress, StunView> aux : sample.publicSample) {
                    if (aux.getSource().getId() % 2 == 1) {
                        pendingPartner = Pair.with(scheduleMsgTimeout(), aux.getContent().selfStunAdr.getValue0());
                        send(new StunPartner.Request(UUID.randomUUID(), self), self.getValue0(), pendingPartner.getValue1());
                        break;
                    }
                }
            }
        };

        ClassMatchedHandler handlePartnerRequest
                = new ClassMatchedHandler<StunPartner.Request, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, StunPartner.Request>>() {

                    @Override
                    public void handle(StunPartner.Request content, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, StunPartner.Request> container) {
                        LOG.trace("{}partner request from:{}", new Object[]{logPrefix, container.getSource()});
                        if (partner == null && pendingPartner == null) {
                            partner = content.partnerAdr;
                            LOG.info("{}partnered with:{}", new Object[]{logPrefix, partner.getValue0().getBase()});
                            trigger(new FDEvent.Follow(partner.getValue0(), config.stunService, 
                                    StunServerComp.this.getComponentCore().id()), failureDetector);
                            trigger(CroupierUpdate.update(StunView.partner(self, partner)), croupierView);
                            send(content.accept(self), self.getValue0(), partner.getValue0());
                        } else {
                            send(content.deny(), self.getValue0(), partner.getValue0());
                        }
                    }
                };

        ClassMatchedHandler handlePartnerResponse
                = new ClassMatchedHandler<StunPartner.Response, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, StunPartner.Response>>() {

                    @Override
                    public void handle(StunPartner.Response content, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, StunPartner.Response> container) {
                        if (pendingPartner == null || 
                                !pendingPartner.getValue1().getBase().equals(container.getSource().getBase())) {
                            LOG.trace("{}late response from:{}", new Object[]{logPrefix, container.getSource().getBase()});
                            return;
                        }
                        LOG.trace("{}partner response:{} from:{}", new Object[]{logPrefix, content.accept, 
                            container.getSource().getBase()});
                        cancelMsgTimeout(pendingPartner.getValue0());
                        pendingPartner = null;
                        if(!content.accept) {
                            return;
                        }
                        partner = content.partnerAdr.get();
                        LOG.info("{}partnered with:{}", new Object[]{logPrefix, partner.getValue0().getBase()});
                        trigger(new FDEvent.Follow(partner.getValue0(), config.stunService, 
                                StunServerComp.this.getComponentCore().id()), failureDetector);
                        trigger(CroupierUpdate.update(StunView.partner(self, partner)), croupierView);
                    }
                };

        Handler handleMsgTimeout = new Handler<MsgTimeout>() {
            @Override
            public void handle(MsgTimeout timeout) {
                if (pendingPartner == null || !pendingPartner.getValue0().equals(timeout.getTimeoutId())) {
                    LOG.trace("{}late timeout", logPrefix);
                    return;
                }
                LOG.debug("{}partner:{} response is late", new Object[]{logPrefix, pendingPartner.getValue1().getBase()});
                pendingPartner = null;
            }
        };
        
        Handler handleSuspectPartner = new Handler<FDEvent.Suspect>() {
            @Override
            public void handle(FDEvent.Suspect suspect) {
                if(partner == null || !partner.getValue0().getBase().equals(suspect.target.getBase())) {
                    LOG.warn("{}possible old partner suspected");
                    trigger(new Unfollow(suspect.target, config.stunService,
                            StunServerComp.this.getComponentCore().id()), failureDetector);
                    return;
                }
                LOG.info("{}partner:{} suspected - resetting", new Object[]{logPrefix, partner.getValue0().getBase()});
                partner = null;
                trigger(new Unfollow(suspect.target, config.stunService,
                        StunServerComp.this.getComponentCore().id()), failureDetector);
                trigger(CroupierUpdate.update(StunView.empty(self)), croupierView);
            }
        };
    }

    public static class StunServerInit extends Init<StunServerComp> {

        public final StunServerKCWrapper config;
        public final Pair<DecoratedAddress, DecoratedAddress> self;

        public StunServerInit(KConfigCore config, Pair<DecoratedAddress, DecoratedAddress> self) {
            this.config = new StunServerKCWrapper(config);
            this.self = self;
        }
    }

    private UUID scheduleMsgTimeout() {
        ScheduleTimeout spt = new ScheduleTimeout(config.rtt);
        MsgTimeout sc = new MsgTimeout(spt);
        spt.setTimeoutEvent(sc);
        trigger(spt, timer);
        return sc.getTimeoutId();
    }

    private void cancelMsgTimeout(UUID tid) {
        CancelTimeout cpt = new CancelTimeout(tid);
        trigger(cpt, timer);
    }

    public class MsgTimeout extends Timeout {

        public MsgTimeout(ScheduleTimeout request) {
            super(request);
        }

        @Override
        public String toString() {
            return "STUN_MSG_TIMEOUT";
        }
    }
}
