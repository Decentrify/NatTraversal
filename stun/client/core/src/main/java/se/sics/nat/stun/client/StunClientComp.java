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
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.ClassMatchedHandler;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.network.Network;
import se.sics.kompics.network.Transport;
import se.sics.kompics.timer.CancelTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;
import se.sics.kompics.timer.Timer;
import se.sics.ktoolbox.util.config.impl.SystemKCWrapper;
import se.sics.ktoolbox.util.network.KContentMsg;
import se.sics.ktoolbox.util.network.KHeader;
import se.sics.ktoolbox.util.network.basic.BasicAddress;
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

    private static final Logger LOG = LoggerFactory.getLogger(StunClientComp.class);
    private String logPrefix = "";

    //******************************CONNECTIONS*********************************
    private final Negative<StunClientPort> stunPort = provides(StunClientPort.class);
    private final Positive<Timer> timer = requires(Timer.class);
    private final Positive<Network> network = requires(Network.class);
    //*******************************CONFIG*************************************
    private final StunClientKCWrapper stunClientConfig;
    //****************************STATE_INTERNAL********************************
    private StunSession session;
    private UUID echoTId;

    public StunClientComp(Init init) {
        SystemKCWrapper systemConfig = new SystemKCWrapper(config());
        stunClientConfig = new StunClientKCWrapper(config());
        this.logPrefix = "<nid:" + systemConfig.id + " > ";
        LOG.info("{}initiating...", logPrefix);

        
        session = new StunSession(init.selfAdr, Pair.with(init.stunView.selfStunAdr, init.stunView.partnerStunAdr.get()));

        subscribe(handleStart, control);
        subscribe(handleEchoResponse, network);
        subscribe(handleEchoTimeout, timer);
    }
    //*******************************CONTROL************************************
    Handler handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            LOG.info("{}starting...", logPrefix);
            startEchoSession();
        }
    };
    
    @Override
    public void tearDown() {
        LOG.info("{}tearing down...", logPrefix);
    }

    //********************************ECHO**************************************
    private void startEchoSession() {
        LOG.info("{}starting new echo session:{}", logPrefix, session.sessionId);
        LOG.info("{}stun server1:{} {}", new Object[]{logPrefix,
            session.stunServers.getValue0().getValue0(), session.stunServers.getValue0().getValue1()});
        LOG.info("{}stun server2:{} {}", new Object[]{logPrefix,
            session.stunServers.getValue1().getValue0(), session.stunServers.getValue1().getValue1()});
        processSession(session);
    }

    private void processSession(StunSession session) {
        Pair<StunEcho.Request, Pair<NatAwareAddress, NatAwareAddress>> next = session.next();
        KHeader<NatAwareAddress> requestHeader = new BasicHeader(next.getValue1().getValue0(), next.getValue1().getValue1(), Transport.UDP);
        KContentMsg request = new BasicContentMsg(requestHeader, next.getValue0());
        LOG.trace("{}sending:{}", new Object[]{logPrefix, request});
        trigger(request, network);
        scheduleTimeout(next);
    }

    private void processResult(StunSession session) {
        StunSession.Result sessionResult = session.getResult();
        if (sessionResult.isFailed()) {
            LOG.warn("{}result failed with:{}", logPrefix, sessionResult.failureDescription.get());
            throw new RuntimeException("stun session failed with:" + sessionResult.failureDescription.get());
        } else {
            LOG.info("{}result:{}", logPrefix, sessionResult.natState.get());
            switch (sessionResult.natState.get()) {
                case UDP_BLOCKED:
                    Optional<InetAddress> missing = Optional.absent();
                    trigger(new StunNatDetected(NatType.udpBlocked(), missing), stunPort);
                    break;
                case OPEN:
                    trigger(new StunNatDetected(NatType.open(), sessionResult.publicIp), stunPort);
                    break;
                case FIREWALL:
                    trigger(new StunNatDetected(NatType.firewall(), sessionResult.publicIp), stunPort);
                    break;
                case NAT:
                    LOG.info("{}result:NAT filter:{} mapping:{} allocation:{}",
                            new Object[]{logPrefix, sessionResult.filterPolicy.get(), sessionResult.mappingPolicy.get(), sessionResult.allocationPolicy.get()});
                    NatType nat;
                    if (sessionResult.allocationPolicy.get().equals(Nat.AllocationPolicy.PORT_CONTIGUITY)) {
                        LOG.info("{}session result:NAT delta:{}", logPrefix, sessionResult.delta.get());
                        nat = NatType.nated(sessionResult.mappingPolicy.get(), sessionResult.allocationPolicy.get(),
                                sessionResult.delta.get(), sessionResult.filterPolicy.get(), 0);
                    } else {
                        nat = NatType.nated(sessionResult.mappingPolicy.get(), sessionResult.allocationPolicy.get(),
                                0, sessionResult.filterPolicy.get(), 10000);
                    }
                    trigger(new StunNatDetected(nat, sessionResult.publicIp), stunPort);
                    break;
                default:
                    LOG.error("{}unknown session result:{}", logPrefix, sessionResult.natState.get());
            }
        }
    }

    Handler handleEchoTimeout = new Handler<EchoTimeout>() {
        @Override
        public void handle(EchoTimeout timeout) {
            if (echoTId == null || !timeout.getTimeoutId().equals(echoTId)) {
                //junk timeout - either late or someone elses
                return;
            }
            LOG.trace("{}timeout echo:{} to:{}",
                    new Object[]{logPrefix, timeout.echo.getValue0(), timeout.echo.getValue1().getValue1()});
            echoTId = null;

            session.timeout();
            if (!session.finished()) {
                processSession(session);
            } else {
                processResult(session);
            }
        }
    };

    ClassMatchedHandler handleEchoResponse
            = new ClassMatchedHandler<StunEcho.Response, KContentMsg<NatAwareAddress, ?, StunEcho.Response>>() {

                @Override
                public void handle(StunEcho.Response content, KContentMsg<NatAwareAddress, ?, StunEcho.Response> container) {
                    LOG.trace("{}received:{}", new Object[]{logPrefix, container});
                    cancelEchoTimeout();
                    session.receivedResponse(content, container.getHeader().getSource());
                    if (!session.finished()) {
                        processSession(session);
                    } else {
                        processResult(session);
                    }
                }
            };

    public static class Init extends se.sics.kompics.Init<StunClientComp> {

        public final Pair<BasicAddress, BasicAddress> selfAdr;
        public final StunView stunView;

        public Init(Pair<BasicAddress, BasicAddress> selfAdr,
                StunView stunView) {
            this.selfAdr = selfAdr;
            this.stunView = stunView;
        }
    }

    private void scheduleTimeout(Pair<StunEcho.Request, Pair<NatAwareAddress, NatAwareAddress>> echo) {
        ScheduleTimeout st = new ScheduleTimeout(stunClientConfig.ECHO_TIMEOUT);
        EchoTimeout timeout = new EchoTimeout(st, echo);
        st.setTimeoutEvent(timeout);
        trigger(st, timer);
        echoTId = timeout.getTimeoutId();
    }

    private void cancelEchoTimeout() {
        if (echoTId == null) {
            return;
        }
        CancelTimeout ct = new CancelTimeout(echoTId);
        trigger(ct, timer);
        echoTId = null;
    }

    private static class EchoTimeout extends Timeout {

        Pair<StunEcho.Request, Pair<NatAwareAddress, NatAwareAddress>> echo;

        public EchoTimeout(ScheduleTimeout request, Pair<StunEcho.Request, Pair<NatAwareAddress, NatAwareAddress>> echo) {
            super(request);
            this.echo = echo;
        }

        @Override
        public String toString() {
            return "EchoTimeout<s:" + echo.getValue0().sessionId + ", t:" + getTimeoutId()+ ">";
        }
    }
}
