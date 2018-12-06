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
package se.sics.nat.stun.client;

import com.google.common.base.Optional;
import java.net.InetAddress;
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
import se.sics.ktoolbox.nutil.timer.TimerProxy;
import se.sics.ktoolbox.nutil.timer.TimerProxyImpl;
import se.sics.ktoolbox.util.config.impl.SystemKCWrapper;
import se.sics.ktoolbox.util.identifiable.BasicIdentifiers;
import se.sics.ktoolbox.util.identifiable.IdentifierFactory;
import se.sics.ktoolbox.util.identifiable.IdentifierRegistryV2;
import se.sics.ktoolbox.util.network.KAddress;
import se.sics.ktoolbox.util.network.KContentMsg;
import se.sics.ktoolbox.util.network.KHeader;
import se.sics.ktoolbox.util.network.basic.BasicContentMsg;
import se.sics.ktoolbox.util.network.basic.BasicHeader;
import se.sics.ktoolbox.util.network.nat.Nat;
import se.sics.ktoolbox.util.network.nat.NatAwareAddress;
import se.sics.ktoolbox.util.network.nat.NatType;
import se.sics.nat.stun.StunClientPort;
import se.sics.nat.stun.StunNatDetected;
import se.sics.nat.stun.client.util.StunSession;
import se.sics.nat.stun.event.StunEcho;
import se.sics.nat.stun.util.StunView;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 *
 * * algorithm used in this stun client is described in detail in the
 * http://tools.ietf.org/html/draft-takeda-symmetric-nat-traversal-00
 * http://www.rfc-editor.org/rfc/rfc4787.txt
 *
 *
 * SS1 = Stun Server 1 SS2 = Stun Server 2
 *
 * (Client) UDP_BLOCKED -----------------EchoMsg.Req----------------------->SS1
 * | UDP_BLOCKED <-------------(no reply - timeout)------------------| | | Check
 * replyToIp matches | private IP | | | | V V | NAT_UDP_OK OPEN_CHECK_FIREWALL |
 * | | UDP_WORKS, <---------------(EchoMsg.Resp)------------------------|
 * SS2_FAILED | | ----------EchoChangeIpAndPort.Req----------------------->SS1 |
 * ServerHostChangeMsg.Req | SS2_FAILED <------EchoChangeIpAndPort.Resp(Failed
 * SS2)-----(If not Ack'd at SS1) | V SS2 (port 2) | | CHANGE_IP_TIMEOUT
 * <-------(EchoChangeIpAndPort.Resp not revd)----| | | CHANGED_IP,  <------(EchoChangeIpAndPort.Resp recvd)---------------| CHANGE_IP_TIMEOUT | |
 * |---------------EchoChangePort.Req-----------------------> SS1 (port 1) |
 * CHANGE_PORT_TIMEOUT <-------(EchoChangePort.Resp not revd)-------| |
 * CHANGED_PORT <------(EchoChangeIpAndPort.Resp recvd)--------------|
 *
 * FIN_ means that the other branch has finished.
 *
 * CHANGE_IP_TIMEOUT_FIN_PORT, CHANGED_IP_FIN_PORT, CHANGED_PORT_FIN_IP,
 * CHANGED_PORT_TIMEOUT_FIN_IP | Allocate ports 2, 3 on Stun Client | (Port 2)
 * --------- EchoMsg.Req (Try-0)----------------------)-> SS1 (Port 3) ---------
 * EchoMsg.Req (Try-1)----------------------)-> SS1 (Port 2) ---------
 * EchoMsg.Req (Try-2)----------------------)-> SS1 (port 2) (Port 3) ---------
 * EchoMsg.Req (Try-3)----------------------)-> SS1 (port 2) (Port 2) ---------
 * EchoMsg.Req (Try-4)----------------------)-> SS2 (Port 3) ---------
 * EchoMsg.Req (Try-5)----------------------)-> SS2 (Port 2) ---------
 * EchoMsg.Req (Try-6)----------------------)-> SS2 (port 2) (Port 3) ---------
 * EchoMsg.Req (Try-7)----------------------)-> SS2 (port 2) | | | PING_FAILED
 * <------(EchoMsg.ReqTimeout Ping)--------------------| <------(EchoMsg.Req
 * Ping Received all 8)--------------------|
 *
 * For info on expected UDP Nat binding timeouts, see :
 * http://www.ietf.org/proceedings/78/slides/behave-8.pdf From these slides, we
 * measure UDP-2, but a NAT will refresh with UDP-1. Therefore, we need to be
 * conservative in setting the NAT binding timeout.
 *
 */
//TODO Alex - currently Stun does one session 
public class StunClientComp extends ComponentDefinition {

  //******************************CONNECTIONS*********************************
  private final Negative<StunClientPort> stunPort = provides(StunClientPort.class);
  private final Positive<Timer> timerPort = requires(Timer.class);
  private final Positive<Network> networkPort = requires(Network.class);
  private final TimerProxy timer;
  //*******************************CONFIG*************************************
  private final StunClientKCWrapper stunClientConfig;
  //****************************STATE_INTERNAL********************************
  private StunSession session;
  private UUID echoTId;
  private final IdentifierFactory eventIds;

  public StunClientComp(Init init) {
    loggingCtxPutAlways("nid", init.selfAdr.getValue0().getId().toString());
    SystemKCWrapper systemConfig = new SystemKCWrapper(config());
    stunClientConfig = new StunClientKCWrapper(config());
    logger.info("initiating:<{},{}>", new Object[]{init.selfAdr.getValue0(), init.selfAdr.getValue1()});

    this.eventIds = IdentifierRegistryV2.instance(BasicIdentifiers.Values.EVENT,
      java.util.Optional.of(systemConfig.seed));
    session = new StunSession(eventIds.randomId(), init.selfAdr,
      Pair.with(init.stunView.selfStunAdr, init.stunView.partnerStunAdr.get()));
    timer = new TimerProxyImpl();

    subscribe(handleStart, control);
    subscribe(handleEchoResponse, networkPort);
  }
  //*******************************CONTROL************************************
  Handler handleStart = new Handler<Start>() {
    @Override
    public void handle(Start event) {
      startEchoSession();
      timer.setup(proxy, logger);
    }
  };

  @Override
  public void tearDown() {
    timer.cancel();
  }

  //********************************ECHO**************************************
  private void startEchoSession() {
    logger.info("starting new echo session:{}", session.sessionId);
    logger.info("stun server1:{} {}", 
      new Object[]{session.stunServers.getValue0().getValue0(), session.stunServers.getValue0().getValue1()});
    logger.info("stun server2:{} {}", 
      new Object[]{session.stunServers.getValue1().getValue0(), session.stunServers.getValue1().getValue1()});
    processSession(session);
  }

  private void processSession(StunSession session) {
    Pair<StunEcho.Request, Pair<NatAwareAddress, NatAwareAddress>> next = session.next();
    StunEcho.Request req = next.getValue0();
    KAddress own = next.getValue1().getValue0();
    KAddress partner = next.getValue1().getValue1();
    KHeader<NatAwareAddress> requestHeader = new BasicHeader(own, partner,Transport.UDP);
    KContentMsg request = new BasicContentMsg(requestHeader, req);
    logger.trace("sending:{}", new Object[]{request});
    trigger(request, networkPort);
    echoTId = timer.scheduleTimer(stunClientConfig.ECHO_TIMEOUT, echoSessionTimeout(req, partner));
  }

  private Consumer<Boolean> echoSessionTimeout(StunEcho.Request req, KAddress partner) {
    return (_ignore) -> {
      if (echoTId == null) {
        //junk timeout - late
        return;
      }
      logger.trace("timeout echo:{} to:{}", new Object[]{req, partner});
      echoTId = null;
      session.timeout();
      if (!session.finished()) {
        processSession(session);
      } else {
        processResult(session);
      }
    };
  }

  private void processResult(StunSession session) {
    StunSession.Result sessionResult = session.getResult();
    if (sessionResult.isFailed()) {
      logger.warn("result failed with:{}", sessionResult.failureDescription.get());
      //TODO Alex - act like udp blocked or unknown?
      Optional<InetAddress> missing = Optional.absent();
      trigger(new StunNatDetected(eventIds.randomId(), NatType.udpBlocked(), missing), stunPort);
      return;
    } else {
      if (stunClientConfig.stunClientOpenPorts.isPresent() && stunClientConfig.stunClientOpenPorts.get()) {
        if (StunSession.NatState.NAT.equals(sessionResult.natState.get())
          && Nat.FilteringPolicy.ENDPOINT_INDEPENDENT.equals(sessionResult.filterPolicy.get())
          && Nat.MappingPolicy.PORT_DEPENDENT.equals(sessionResult.mappingPolicy.get())
          && Nat.AllocationPolicy.PORT_PRESERVATION.equals(sessionResult.allocationPolicy.get())) {
          trigger(new StunNatDetected(eventIds.randomId(), NatType.natPortForwarding(), sessionResult.publicIp),
            stunPort);
          return;
        }
      }
      logger.info("result:{}", sessionResult.natState.get());
      switch (sessionResult.natState.get()) {
        case UDP_BLOCKED:
          Optional<InetAddress> missing = Optional.absent();
          trigger(new StunNatDetected(eventIds.randomId(), NatType.udpBlocked(), missing), stunPort);
          break;
        case OPEN:
          trigger(new StunNatDetected(eventIds.randomId(), NatType.open(), sessionResult.publicIp), stunPort);
          break;
        case FIREWALL:
          trigger(new StunNatDetected(eventIds.randomId(), NatType.firewall(), sessionResult.publicIp), stunPort);
          break;
        case NAT:
          logger.info("{}result:NAT filter:{} mapping:{} allocation:{}",
            new Object[]{sessionResult.filterPolicy.get(), sessionResult.mappingPolicy.get(),
              sessionResult.allocationPolicy.get()});
          NatType nat;
          if (sessionResult.allocationPolicy.get().equals(Nat.AllocationPolicy.PORT_CONTIGUITY)) {
            logger.info("session result:NAT delta:{}", sessionResult.delta.get());
            nat = NatType.nated(sessionResult.mappingPolicy.get(), sessionResult.allocationPolicy.get(),
              sessionResult.delta.get(), sessionResult.filterPolicy.get(), 0);
          } else {
            nat = NatType.nated(sessionResult.mappingPolicy.get(), sessionResult.allocationPolicy.get(),
              0, sessionResult.filterPolicy.get(), 10000);
          }
          trigger(new StunNatDetected(eventIds.randomId(), nat, sessionResult.publicIp), stunPort);
          break;
        default:
          logger.error("{}unknown session result:{}", sessionResult.natState.get());
      }
    }
  }

  ClassMatchedHandler handleEchoResponse
    = new ClassMatchedHandler<StunEcho.Response, KContentMsg<NatAwareAddress, ?, StunEcho.Response>>() {

    @Override
    public void handle(StunEcho.Response content, KContentMsg<NatAwareAddress, ?, StunEcho.Response> container) {
      logger.trace("received:{}", new Object[]{container});
      if (echoTId != null) {
        timer.cancelTimer(echoTId);
        echoTId = null;
      }
      session.receivedResponse(content, container.getHeader().getSource());
      if (!session.finished()) {
        processSession(session);
      } else {
        processResult(session);
      }
    }
  };

  public static class Init extends se.sics.kompics.Init<StunClientComp> {

    public final Pair<NatAwareAddress, NatAwareAddress> selfAdr;
    public final StunView stunView;

    public Init(Pair<NatAwareAddress, NatAwareAddress> selfAdr,
      StunView stunView) {
      this.selfAdr = selfAdr;
      this.stunView = stunView;
    }
  }
}
