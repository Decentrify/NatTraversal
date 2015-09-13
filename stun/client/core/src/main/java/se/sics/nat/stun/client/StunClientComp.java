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
import se.sics.kompics.Channel;
import se.sics.kompics.ChannelFilter;
import se.sics.kompics.ClassMatchedHandler;
import se.sics.kompics.Component;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.ControlPort;
import se.sics.kompics.Fault;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.KompicsEvent;
import se.sics.kompics.Negative;
import se.sics.kompics.Port;
import se.sics.kompics.PortType;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.Stop;
import se.sics.kompics.network.Network;
import se.sics.kompics.network.Transport;
import se.sics.kompics.timer.CancelTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;
import se.sics.kompics.timer.Timer;
import se.sics.nat.stun.NatReady;
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
import se.sics.p2ptoolbox.util.proxy.ComponentProxy;

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
    private Positive<Network> network1;
    private Positive<Network> network2;
    private final Positive<Timer> timer = requires(Timer.class);

    private final Pair<DecoratedAddress, DecoratedAddress> self;
    private final EchoMngr echoMngr;
    private final HookTracker hookTracker;
    private final StunServerMngr stunServersMngr;

    public StunClientComp(StunClientInit init) {
        this.logPrefix = init.self.getValue0().getIp() + "<" + init.self.getValue0().getPort() + "," + init.self.getValue1().getPort() + "> ";
        LOG.info("{}initiating...", logPrefix);
        this.self = init.self;
        this.echoMngr = new EchoMngr();
        this.hookTracker = new HookTracker(init.networkHookDefinition);
        this.stunServersMngr = new StunServerMngr(init.stunServers);

        subscribe(handleStart, control);
        subscribe(handleStop, control);
        subscribe(echoMngr.handleEchoTimeout, timer);
    }

    Handler handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            LOG.info("{}starting...", logPrefix);
            hookTracker.setupHook1();
            hookTracker.setupHook2();
            echoMngr.startEchoSession();
            subscribe(echoMngr.handleEchoResponse, network1);
            subscribe(echoMngr.handleEchoResponse, network2);
        }
    };

    Handler handleStop = new Handler<Stop>() {
        @Override
        public void handle(Stop event) {
            LOG.info("{}stopping...", logPrefix);
            hookTracker.tearDown1();
            hookTracker.tearDown2();
        }
    };

    public Fault.ResolveAction handleFault(Fault fault) {
        LOG.error("{}fault:{} from component:{} - restarting hook...", new Object[]{logPrefix, fault.getCause().getMessage(),
            fault.getSourceCore().id()});
        hookTracker.restartHook(fault.getSourceCore().id());

        return Fault.ResolveAction.RESOLVED;
    }

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
                        trigger(new NatReady(NatedTrait.udpBlocked(), sessionResult.publicIp), stunPort);
                        break;
                    case OPEN:
                        trigger(new NatReady(NatedTrait.open(), sessionResult.publicIp), stunPort);
                        break;
                    case FIREWALL:
                        trigger(new NatReady(NatedTrait.firewall(), sessionResult.publicIp), stunPort);
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
                        trigger(new NatReady(nat, sessionResult.publicIp), stunPort);
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
            if (next.getValue1().getValue0().getPort() == self.getValue0().getPort()) {
                trigger(request, network1);
            } else {
                trigger(request, network2);
            }

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

    //**************************HOOK_PARENT*************************************
    public class HookTracker implements ComponentProxy {

        private final SCNetworkHook.Definition networkHookDefinition;
        private final Map<UUID, Integer> compToHook;
        private Component[] networkHook1;
        private Component[] networkHook2;

        public HookTracker(SCNetworkHook.Definition networkHookDefinition) {
            this.networkHookDefinition = networkHookDefinition;
            this.compToHook = new HashMap<>();
        }

        private void setupHook1() {
            LOG.info("{}setting up network hook1",
                    new Object[]{logPrefix});
            SCNetworkHook.InitResult result = networkHookDefinition.setUp(this, new SCNetworkHook.Init(self.getValue0()));
            networkHook1 = result.components;
            for (Component component : networkHook1) {
                compToHook.put(component.id(), 1);
            }
            network1 = result.network;
        }

        private void setupHook2() {
            LOG.info("{}setting up network hook2",
                    new Object[]{logPrefix});
            SCNetworkHook.InitResult result = networkHookDefinition.setUp(this, new SCNetworkHook.Init(self.getValue1()));
            networkHook2 = result.components;
            for (Component component : networkHook2) {
                compToHook.put(component.id(), 2);
            }
            network2 = result.network;
        }

        private void restartHook(UUID compId) {
            Integer hookNr = compToHook.get(compId);
            switch (hookNr) {
                case 1:
                    tearDown1();
                    setupHook1();
                    break;
                case 2:
                    tearDown2();
                    setupHook2();
                    break;
            }
        }

        private void tearDown1() {
            LOG.info("{}tearing down hook1", new Object[]{logPrefix});

            networkHookDefinition.tearDown(this, new SCNetworkHook.Tear(networkHook1));
            for (Component component : networkHook1) {
                compToHook.remove(component.id());
            }
            networkHook1 = null;
            network1 = null;
        }

        private void tearDown2() {
            LOG.info("{}tearing down hook2", new Object[]{logPrefix});

            networkHookDefinition.tearDown(this, new SCNetworkHook.Tear(networkHook2));
            for (Component component : networkHook2) {
                compToHook.remove(component.id());
            }
            networkHook2 = null;
            network2 = null;
        }

        //*******************************PROXY**********************************
        @Override
        public <P extends PortType> void trigger(KompicsEvent e, Port<P> p) {
            StunClientComp.this.trigger(e, p);
        }

        @Override
        public <T extends ComponentDefinition> Component create(Class<T> definition, Init<T> initEvent) {
            return StunClientComp.this.create(definition, initEvent);
        }

        @Override
        public <T extends ComponentDefinition> Component create(Class<T> definition, Init.None initEvent) {
            return StunClientComp.this.create(definition, initEvent);
        }

        @Override
        public <P extends PortType> Channel<P> connect(Positive<P> positive, Negative<P> negative) {
            return StunClientComp.this.connect(negative, positive);
        }

        @Override
        public <P extends PortType> Channel<P> connect(Negative<P> negative, Positive<P> positive) {
            return StunClientComp.this.connect(negative, positive);
        }

        @Override
        public <P extends PortType> void disconnect(Negative<P> negative, Positive<P> positive) {
            StunClientComp.this.disconnect(negative, positive);
        }

        @Override
        public <P extends PortType> void disconnect(Positive<P> positive, Negative<P> negative) {
            StunClientComp.this.disconnect(negative, positive);
        }

        @Override
        public Negative<ControlPort> getControlPort() {
            return StunClientComp.this.control;
        }

        @Override
        public <P extends PortType> Positive<P> requires(Class<P> portType) {
            return StunClientComp.this.requires(portType);
        }

        @Override
        public <P extends PortType> Negative<P> provides(Class<P> portType) {
            return StunClientComp.this.provides(portType);
        }

        @Override
        public <P extends PortType> Channel<P> connect(Positive<P> positive, Negative<P> negative, ChannelFilter filter) {
            return StunClientComp.this.connect(negative, positive, filter);
        }

        @Override
        public <P extends PortType> Channel<P> connect(Negative<P> negative, Positive<P> positive, ChannelFilter filter) {
            return StunClientComp.this.connect(positive, negative, filter);
        }
    }

    public static class StunClientInit extends Init<StunClientComp> {

        public final Pair<DecoratedAddress, DecoratedAddress> self;
        public final List<Pair<DecoratedAddress, DecoratedAddress>> stunServers;
        public final SCNetworkHook.Definition networkHookDefinition;

        public StunClientInit(Pair<DecoratedAddress, DecoratedAddress> startSelf, 
                List<Pair<DecoratedAddress, DecoratedAddress>> stunServers,
                SCNetworkHook.Definition networkHookDefinition) {
            this.self = startSelf;
            this.stunServers = stunServers;
            this.networkHookDefinition = networkHookDefinition;
        }
    }

    public static class StunClientConfig {

        public static long echoTimeout = 2000;
    }

    public static class EchoTimeout extends Timeout {

        public final Pair<StunEcho.Request, Pair<DecoratedAddress, DecoratedAddress>> echo;

        public EchoTimeout(ScheduleTimeout request, Pair<StunEcho.Request, Pair<DecoratedAddress, DecoratedAddress>> echo) {
            super(request);
            this.echo = echo;
        }
    }
}
