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

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.javatuples.Pair;
import se.sics.kompics.ClassMatchedHandler;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.network.Network;
import se.sics.kompics.network.Transport;
import se.sics.kompics.timer.Timer;
import se.sics.ktoolbox.croupier.CroupierPort;
import se.sics.ktoolbox.croupier.event.CroupierSample;
import se.sics.ktoolbox.nutil.timer.TimerProxy;
import se.sics.ktoolbox.nutil.timer.TimerProxyImpl;
import se.sics.ktoolbox.util.identifiable.BasicIdentifiers;
import se.sics.ktoolbox.util.identifiable.IdentifierFactory;
import se.sics.ktoolbox.util.identifiable.IdentifierRegistryV2;
import se.sics.ktoolbox.util.identifiable.overlay.OverlayId;
import se.sics.ktoolbox.util.network.KAddress;
import se.sics.ktoolbox.util.network.KContentMsg;
import se.sics.ktoolbox.util.network.KHeader;
import se.sics.ktoolbox.util.network.basic.BasicContentMsg;
import se.sics.ktoolbox.util.network.basic.BasicHeader;
import se.sics.ktoolbox.util.network.nat.NatAwareAddress;
import se.sics.ktoolbox.util.other.Container;
import se.sics.ktoolbox.util.overlays.view.OverlayViewUpdate;
import se.sics.ktoolbox.util.overlays.view.OverlayViewUpdatePort;
import se.sics.nat.stun.event.StunEcho;
import se.sics.nat.stun.event.StunPartner;
import se.sics.nat.stun.util.StunView;

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

  //****************************CONNECTIONS***********************************
  private final Positive<Timer> timerPort = requires(Timer.class);
  private final Positive<Network> networkPort = requires(Network.class);
//    private final Positive<EPFDPort> fdPort = requires(EPFDPort.class);
  private final Positive<CroupierPort> croupierPort = requires(CroupierPort.class);
  private final Negative<OverlayViewUpdatePort> croupierViewPort = provides(OverlayViewUpdatePort.class);
  private final TimerProxy timerProxy;
  //****************************CONFIGURATION*********************************
  private final StunServerKCWrapper stunServerConfig;
  private Pair<NatAwareAddress, NatAwareAddress> selfAdr;
  private final OverlayId croupierId;
  //*****************************INTERNAL_STATE*******************************
  private final EchoMngr echoMngr;
  private final PartnerMngr partnerMngr;

  private final IdentifierFactory eventIds;
  private final IdentifierFactory msgIds;

  public StunServerComp(Init init) {
    stunServerConfig = new StunServerKCWrapper(config());
    selfAdr = init.selfAdr;
    loggingCtxPutAlways("nid", selfAdr.getValue0().getId().toString());
    timerProxy = new TimerProxyImpl();
    croupierId = init.croupierId;
    echoMngr = new EchoMngr();
    partnerMngr = new PartnerMngr();

    this.eventIds = IdentifierRegistryV2.instance(BasicIdentifiers.Values.EVENT, Optional.of(1234l));
    this.msgIds = IdentifierRegistryV2.instance(BasicIdentifiers.Values.MSG, Optional.of(1234l));
    subscribe(handleStart, control);
    subscribe(handleViewRequest, croupierViewPort);

    subscribe(partnerMngr.handleSamples, croupierPort);
    subscribe(partnerMngr.handlePartnerRequest, networkPort);
    subscribe(partnerMngr.handlePartnerResponse, networkPort);
//        subscribe(partnerMngr.handleSuspectPartner, fdPort);
  }

  //******************************CONTROL*************************************
  Handler handleStart = new Handler<Start>() {
    @Override
    public void handle(Start event) {
      timerProxy.setup(proxy);
      partnerMngr.start();
    }
  };

  @Override
  public void tearDown() {
    timerProxy.cancel();
  }

  Handler handleViewRequest = new Handler<OverlayViewUpdate.Request>() {
    @Override
    public void handle(OverlayViewUpdate.Request req) {
      logger.debug("received:{}", req);
      if (partnerMngr.partner == null) {
        answer(req, req.update(StunView.empty(selfAdr)));
      } else {
        answer(req, req.update(StunView.partner(selfAdr, partnerMngr.partner)));
      }
    }
  };

  //**********************************AUX*************************************
  private void send(Object content, NatAwareAddress src, NatAwareAddress dst) {
    KHeader<NatAwareAddress> header = new BasicHeader(src, dst, Transport.UDP);
    KContentMsg container = new BasicContentMsg(header, content);
    logger.trace("sending:{}", new Object[]{container});
    trigger(container, networkPort);
  }

  private void send(KContentMsg container) {
    logger.trace("sending:{}", new Object[]{container});
    trigger(container, networkPort);
  }

  //**************************************************************************
  private class EchoMngr {

    ClassMatchedHandler handleEchoRequest
      = new ClassMatchedHandler<StunEcho.Request, BasicContentMsg<NatAwareAddress, KHeader<NatAwareAddress>, StunEcho.Request>>() {

      @Override
      public void handle(StunEcho.Request content,
        BasicContentMsg<NatAwareAddress, KHeader<NatAwareAddress>, StunEcho.Request> container) {
        if (partnerMngr.getPartner() == null) {
          send(container.answer(content.reset()));
          return;
        }
        logger.debug("received:{}", new Object[]{container});
        switch (content.type) {
          case SIP_SP: {
            send(container.answer(content.answer(container.getSource())));
          }
          break;
          case SIP_DP: {
            send(content.answer(container.getSource()), selfAdr.getValue1(), container.getSource());
          }
          break;
          case DIP_DP: {
            if (container.getSource().getId().equals(content.target.getId())) {
              //forward to partner
              send(content, selfAdr.getValue0(), partnerMngr.getPartner());
            } else {
              send(content.answer(), selfAdr.getValue1(), content.target);
            }
          }
          break;
        }
      }
    };
  }

  private class PartnerMngr {

    private Pair<NatAwareAddress, NatAwareAddress> partner;
    private Pair<UUID, NatAwareAddress> pendingPartner;

    void start() {
      logger.info("looking for partner");
      trigger(new OverlayViewUpdate.Indication(eventIds.randomId(), croupierId, false, StunView.empty(selfAdr)),
        croupierViewPort);
    }

    NatAwareAddress getPartner() {
      return partner.getValue0();
    }

    Handler handleSamples = new Handler<CroupierSample<StunView>>() {

      @Override
      public void handle(CroupierSample<StunView> sample) {
        if (partner != null) {
          return;
        }
        if (selfAdr.getValue0().getId().partition(2) == 1) {
          return; // 0s search for partners actively
        }
        for (Container<KAddress, StunView> source : sample.publicSample.values()) {
          if (source.getContent().hasPartner()) {
            continue;
          }
          if (source.getSource().getId().partition(2) == 0) {
            continue;
          }
          NatAwareAddress partnerAdr = (NatAwareAddress) source.getSource();
          pendingPartner = Pair.with(msgTimeout(), partnerAdr);
          send(new StunPartner.Request(msgIds.randomId(), selfAdr), selfAdr.getValue0(), partnerAdr);
          break;
        }
      }
    };

    private UUID msgTimeout() {
      long msgDelay = stunServerConfig.rtt;
      return timerProxy.scheduleTimer(msgDelay, msgTimer());
    }

    private Consumer<Boolean> msgTimer() {
      return (_ignore) -> {
        logger.warn("{}timeout - partner:{}", new Object[]{pendingPartner.getValue1()});
        pendingPartner = null;
      };
    }

    ClassMatchedHandler handlePartnerRequest
      = new ClassMatchedHandler<StunPartner.Request, KContentMsg<NatAwareAddress, KHeader<NatAwareAddress>, StunPartner.Request>>() {

      @Override
      public void handle(StunPartner.Request content,
        KContentMsg<NatAwareAddress, KHeader<NatAwareAddress>, StunPartner.Request> container) {
        logger.trace("received:{}", new Object[]{container});
        NatAwareAddress requestingPartner = container.getHeader().getSource();
        if (partner != null && partner.getValue0().getId().equals(requestingPartner.getId())) {
          logger.debug("already have a partner");
          send(container.answer(content.deny()));
        }
        if (selfAdr.getValue0().getId().partition(2) == 0) {
          throw new RuntimeException("logic error - active searchers should not get requests");
        }

        partner = content.partnerAdr;
        logger.info("partnered with:{}", new Object[]{partner.getValue0().getId()});
        foundPartner();
        send(container.answer(content.accept(selfAdr)));
      }
    };

    ClassMatchedHandler handlePartnerResponse
      = new ClassMatchedHandler<StunPartner.Response, KContentMsg<NatAwareAddress, KHeader<NatAwareAddress>, StunPartner.Response>>() {

      @Override
      public void handle(StunPartner.Response content,
        KContentMsg<NatAwareAddress, KHeader<NatAwareAddress>, StunPartner.Response> container) {
        logger.trace("{}received:{}", new Object[]{container});
        NatAwareAddress respondingPartner = container.getHeader().getSource();
        if (partner != null) {
          if (partner.getValue0().getId().equals(respondingPartner.getId())) {
            //a bit weird I already answered him, but maybe message got lost
            return;
          } else {
            //probably a slow connection an he timedout in a previous round
            //the epfd should fix this - no need to fix
            return;
          }
        }
        if (pendingPartner != null && !pendingPartner.getValue1().getId().equals(respondingPartner.getId())) {
          //probably a slow connection an he timedout in a previous round
          //the epfd should fix this - no need to fix;
          return;
        }
        //clean session
        if (pendingPartner != null) {
          timerProxy.cancelTimer(pendingPartner.getValue0());
          pendingPartner = null;
        }
        if (!content.accept) {
          return;
        }
        //success
        partner = content.partnerAdr.get();

        logger.info("partnered with:{}", new Object[]{partner.getValue0().getId()});
        foundPartner();
      }
    };

    private void foundPartner() {
      subscribe(echoMngr.handleEchoRequest, networkPort);
      //TODO Alex - fix epfd and add
      //trigger(new EPFDFollow(partner.getValue0(), stunServerConfig.stunService,
      //                StunServerComp.this.getComponentCore().id()), fdPort);
      trigger(new OverlayViewUpdate.Indication(eventIds.randomId(), croupierId, false, StunView.
        partner(selfAdr, partner)), croupierViewPort);

    }

//        Handler handleSuspectPartner = new Handler<EPFDSuspect>() {
//            @Override
//            public void handle(EPFDSuspect suspect) {
//                if (partner == null || !partner.getValue0().getBase().equals(suspect.req.target.getBase())) {
//                    LOG.warn("{}possible old partner suspected");
//                    trigger(new EPFDUnfollow(suspect.req), fdPort);
//                    return;
//                }
//                LOG.info("{}partner:{} suspected - resetting", new Object[]{logPrefix, partner.getValue0().getBase()});
//                partner = null;
//                trigger(new EPFDUnfollow(suspect.req), fdPort);
//                trigger(CroupierUpdate.update(StunView.empty(selfAdr)), croupierView);
//            }
//        };
  }

  public static class Init extends se.sics.kompics.Init<StunServerComp> {

    public final Pair<NatAwareAddress, NatAwareAddress> selfAdr;
    public final OverlayId croupierId;

    public Init(Pair<NatAwareAddress, NatAwareAddress> selfAdr, OverlayId stunOverlayId) {
      this.selfAdr = selfAdr;
      this.croupierId = stunOverlayId;
    }
  }

}
