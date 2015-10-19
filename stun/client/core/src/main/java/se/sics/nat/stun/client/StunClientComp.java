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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
import se.sics.kompics.network.Network;
import se.sics.kompics.network.Transport;
import se.sics.kompics.timer.CancelTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;
import se.sics.kompics.timer.Timer;
import se.sics.nat.stun.NatDetected;
import se.sics.nat.stun.StunClientPort;
import se.sics.nat.stun.client.util.StunSession;
import se.sics.nat.stun.msg.StunEcho;
import se.sics.p2ptoolbox.util.nat.Nat;
import se.sics.p2ptoolbox.util.nat.NatedTrait;
import se.sics.p2ptoolbox.util.network.ContentMsg;
import se.sics.p2ptoolbox.util.network.impl.BasicContentMsg;
import se.sics.p2ptoolbox.util.network.impl.BasicHeader;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;
import se.sics.p2ptoolbox.util.network.impl.DecoratedHeader;

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
 * <-------(EchoChangeIpAndPort.Resp not revd)----| | | CHANGED_IP,
 * <------(EchoChangeIpAndPort.Resp recvd)---------------| CHANGE_IP_TIMEOUT | |
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
public class StunClientComp extends ComponentDefinition {

    private static final Logger LOG = LoggerFactory.getLogger(StunClientComp.class);
    private String logPrefix = "";
    
    private final Negative<StunClientPort> stunPort = provides(StunClientPort.class);
    private final Positive<Network> network = requires(Network.class);
    private final Positive<Timer> timer = requires(Timer.class);

    private final Pair<DecoratedAddress, DecoratedAddress> self;
    private final EchoMngr echoMngr;
    private final StunServerMngr stunServersMngr;

    public StunClientComp(StunClientInit init) {
        this.logPrefix = init.self.getValue0().getIp() + "<" + init.self.getValue0().getPort() + "," + init.self.getValue1().getPort() + "> ";
        LOG.info("{}initiating...", logPrefix);
        this.self = init.self;
        this.echoMngr = new EchoMngr();
        this.stunServersMngr = new StunServerMngr(init.stunServers);

        subscribe(handleStart, control);
        subscribe(echoMngr.handleEchoTimeout, timer);
        subscribe(echoMngr.handleEchoResponse, network);
    }

    Handler handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            LOG.info("{}starting...", logPrefix);
            echoMngr.startEchoSession();
        }
    };

    //**************************************************************************
    private class EchoMngr {

        Map<UUID, StunSession> ongoingSessions;
        Map<UUID, UUID> echoTimeouts;

        public EchoMngr() {
            this.ongoingSessions = new HashMap<UUID, StunSession>();
            this.echoTimeouts = new HashMap<UUID, UUID>();
        }

        void startEchoSession() {
            Pair<Pair<DecoratedAddress, DecoratedAddress>, Pair<DecoratedAddress, DecoratedAddress>> stunServers = stunServersMngr.getStunServers();
            StunSession session = new StunSession(UUID.randomUUID(), self, stunServers);
            LOG.info("{}starting new echo session:{}", logPrefix, session.id);
            LOG.info("{}stun server1:{} {}", new Object[]{logPrefix, stunServers.getValue0().getValue0().getBase(), stunServers.getValue0().getValue1().getBase()});
            LOG.info("{}stun server2:{} {}", new Object[]{logPrefix, stunServers.getValue1().getValue0().getBase(), stunServers.getValue1().getValue1().getBase()});
            ongoingSessions.put(session.id, session);

            processSession(session);
        }

        Handler handleEchoTimeout = new Handler<EchoTimeout>() {
            @Override
            public void handle(EchoTimeout timeout) {
                LOG.trace("{}echo:{} timeout to:{}",
                        new Object[]{logPrefix, timeout.echo.getValue0(), timeout.echo.getValue1().getValue1().getBase()});
                echoTimeouts.remove(timeout.echo.getValue0().id);

                StunSession session = ongoingSessions.get(timeout.echo.getValue0().sessionId);
                if (session == null) {
                    LOG.warn("{}session logic error", logPrefix);
                    return;
                }
                session.timeout();

                if (!session.finished()) {
                    processSession(session);
                } else {
                    processResult(session);
                }
            }
        };

        ClassMatchedHandler handleEchoResponse
                = new ClassMatchedHandler<StunEcho.Response, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, StunEcho.Response>>() {

                    @Override
                    public void handle(StunEcho.Response content, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, StunEcho.Response> container) {
                        LOG.debug("{}received:{} from:{}", new Object[]{logPrefix, content, container.getHeader().getSource().getBase()});
                        cancelEchoTimeout(content.id);
                        StunSession session = ongoingSessions.get(content.sessionId);
                        if (session == null) {
                            LOG.error("{}session logic error", logPrefix);
                            throw new RuntimeException("session logic error");
                        }
                        session.receivedResponse(content, container.getSource());
                        if (!session.finished()) {
                            processSession(session);
                        } else {
                            processResult(session);
                        }
                    }
                };

        private void processResult(StunSession session) {
            //TODO Alex - deal with result
            ongoingSessions.remove(session.id);
            StunSession.Result sessionResult = session.getResult();
            if (sessionResult.isFailed()) {
                //TODO Alex - what to do on session fail
                LOG.warn("{}stun session failed with:{}", logPrefix, sessionResult.failureDescription.get());
                throw new RuntimeException("stun session failed with:" + sessionResult.failureDescription.get());
            } else {
                LOG.info("{}session result:{}", logPrefix, sessionResult.natState.get());
                switch (sessionResult.natState.get()) {
                    case UDP_BLOCKED:
                        trigger(new NatDetected(NatedTrait.udpBlocked(), null), stunPort);
                        break;
                    case OPEN:
                        trigger(new NatDetected(NatedTrait.open(), sessionResult.publicIp.get()), stunPort);
                        break;
                    case FIREWALL:
                        trigger(new NatDetected(NatedTrait.firewall(), sessionResult.publicIp.get()), stunPort);
                        break;
                    case NAT:
                        LOG.info("{}session result:NAT filter:{} mapping:{} allocation:{}",
                                new Object[]{logPrefix, sessionResult.filterPolicy.get(), sessionResult.mappingPolicy.get(), sessionResult.allocationPolicy.get()});
                        NatedTrait nat;
                        if (sessionResult.allocationPolicy.get().equals(Nat.AllocationPolicy.PORT_CONTIGUITY)) {
                            LOG.info("{}session result:NAT delta:{}", logPrefix, sessionResult.delta.get());
                            nat = NatedTrait.nated(sessionResult.mappingPolicy.get(), sessionResult.allocationPolicy.get(),
                                    sessionResult.delta.get(), sessionResult.filterPolicy.get(), 0, new ArrayList<DecoratedAddress>());
                        } else {
                            nat = NatedTrait.nated(sessionResult.mappingPolicy.get(), sessionResult.allocationPolicy.get(),
                                    0, sessionResult.filterPolicy.get(), 10000, new ArrayList<DecoratedAddress>());
                        }
                        trigger(new NatDetected(nat, sessionResult.publicIp.get()), stunPort);
                        break;
                    default:
                        LOG.error("{}unknown session result:{}", logPrefix, sessionResult.natState.get());
                }
            }
        }

        private void processSession(StunSession session) {
            Pair<StunEcho.Request, Pair<DecoratedAddress, DecoratedAddress>> next = session.next();
            DecoratedHeader<DecoratedAddress> requestHeader = new DecoratedHeader(new BasicHeader(next.getValue1().getValue0(), next.getValue1().getValue1(), Transport.UDP), null, null);
            ContentMsg request = new BasicContentMsg(requestHeader, next.getValue0());
            LOG.debug("{}sending:{} from:{} to:{}",
                    new Object[]{logPrefix, next.getValue0().type, next.getValue1().getValue0().getBase(), next.getValue1().getValue1().getBase()});
                trigger(request, network);

            ScheduleTimeout st = new ScheduleTimeout(StunClientConfig.echoTimeout);
            EchoTimeout timeout = new EchoTimeout(st, next);
            st.setTimeoutEvent(timeout);
            trigger(st, timer);
            echoTimeouts.put(timeout.echo.getValue0().id, timeout.getTimeoutId());
        }

        private void cancelEchoTimeout(UUID echoId) {
            UUID timeoutId = echoTimeouts.remove(echoId);
            CancelTimeout ct = new CancelTimeout(timeoutId);
            trigger(ct, timer);
        }
    }

    private class StunServerMngr {

        List<Pair<DecoratedAddress, DecoratedAddress>> stunServers;

        public StunServerMngr(List<Pair<DecoratedAddress, DecoratedAddress>> stunServers) {
            this.stunServers = stunServers;
        }

        public Pair<Pair<DecoratedAddress, DecoratedAddress>, Pair<DecoratedAddress, DecoratedAddress>> getStunServers() {
            return Pair.with(stunServers.get(0), stunServers.get(1));
        }
    }

    public static class StunClientInit extends Init<StunClientComp> {

        public final Pair<DecoratedAddress, DecoratedAddress> self;
        public final List<Pair<DecoratedAddress, DecoratedAddress>> stunServers;
        
        public StunClientInit(Pair<DecoratedAddress, DecoratedAddress> startSelf,
                List<Pair<DecoratedAddress, DecoratedAddress>> stunServers) {
            this.self = startSelf;
            this.stunServers = stunServers;
        }
    }

    public static class StunClientConfig {

        public static long echoTimeout = 2000;
        public static int fatalRetries = 5;
    }

    public static class EchoTimeout extends Timeout {

        public final Pair<StunEcho.Request, Pair<DecoratedAddress, DecoratedAddress>> echo;

        public EchoTimeout(ScheduleTimeout request, Pair<StunEcho.Request, Pair<DecoratedAddress, DecoratedAddress>> echo) {
            super(request);
            this.echo = echo;
        }
    }
}
