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
package se.sics.ktoolbox.nat.stun.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.Stop;
import se.sics.kompics.network.Network;
import se.sics.kompics.network.Transport;
import se.sics.kompics.timer.Timer;
import se.sics.ktoolbox.nat.stun.client.util.Session;
import se.sics.ktoolbox.nat.stun.msg.Echo;
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

    private Positive<Network> networkPort = requires(Network.class);
    private Positive<Timer> timerPort = requires(Timer.class);

    private DecoratedAddress self;
    private final EchoMngr echoMngr;
    private final StunServerMngr stunServersMngr;

    public StunClientComp(StunClientInit init) {
        LOG.info("{}initiating...", logPrefix);
        this.self = init.startSelf;
        this.echoMngr = new EchoMngr();
        this.stunServersMngr = new StunServerMngr(init.stunServers);

        subscribe(handleStart, control);
        subscribe(handleStop, control);
    }

    Handler handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            LOG.info("{}starting...", logPrefix);
            echoMngr.startEcho1();
        }
    };

    Handler handleStop = new Handler<Stop>() {
        @Override
        public void handle(Stop event) {
            LOG.info("{} stopping...", logPrefix);
        }
    };

    private class EchoMngr {

        Map<UUID, Session> ongoingSessions;

        public EchoMngr() {
            this.ongoingSessions = new HashMap<UUID, Session>();
        }

        void startEcho1() {
            DecoratedAddress stunServer = stunServersMngr.getStunServer();
            Session session = new Session(UUID.randomUUID(), stunServer);
            LOG.debug("{}starting new echo session:{} stun server:{}",
                    new Object[]{logPrefix, session.id, session.stunServer.getBase()});
            ongoingSessions.put(session.id, session);

            Echo.Request requestContent = new Echo.Request(session.id, Echo.Type.UDP_BLOCKED, self);
            DecoratedHeader<DecoratedAddress> requestHeader = new DecoratedHeader(new BasicHeader(self, session.stunServer, Transport.UDP), null, null);
            ContentMsg request = new BasicContentMsg(requestHeader, requestContent);
            LOG.debug("{}sending:{}", logPrefix, requestContent);
            trigger(request, networkPort);
        }
    }

    private class StunServerMngr {

        List<DecoratedAddress> stunServers;

        public StunServerMngr(List<DecoratedAddress> stunServers) {
            this.stunServers = stunServers;
        }

        public DecoratedAddress getStunServer() {
            return stunServers.get(0);
        }
    }

    public static class StunClientInit extends Init<StunClientComp> {

        public final DecoratedAddress startSelf;
        public final List<DecoratedAddress> stunServers;

        public StunClientInit(DecoratedAddress startSelf, List<DecoratedAddress> stunServers) {
            this.startSelf = startSelf;
            this.stunServers = stunServers;
        }
    }
    
    public static class StunClientConfig {
        
    }
}
