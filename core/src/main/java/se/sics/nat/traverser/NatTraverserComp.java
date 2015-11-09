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
package se.sics.nat.traverser;

import se.sics.nat.filters.NatTrafficFilter;
import com.google.common.base.Optional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.ChannelFilter;
import se.sics.kompics.ClassMatchedHandler;
import se.sics.kompics.Component;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.network.Msg;
import se.sics.kompics.network.Network;
import se.sics.kompics.network.Transport;
import se.sics.kompics.timer.CancelPeriodicTimeout;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.Timeout;
import se.sics.kompics.timer.Timer;
import se.sics.ktoolbox.fd.FailureDetectorPort;
import se.sics.ktoolbox.fd.event.FDEvent;
import se.sics.ktoolbox.overlaymngr.OverlayMngrPort;
import se.sics.ktoolbox.overlaymngr.events.OMngrCroupier;
import se.sics.nat.detection.NatStatus;
import se.sics.nat.hp.client.SHPClientPort;
import se.sics.nat.hp.client.msg.OpenConnection;
import se.sics.nat.filters.NatInternalFilter;
import se.sics.nat.msg.NatConnection.Close;
import se.sics.nat.util.NatTraverserFeasibility;
import se.sics.nat.hp.client.SHPClientComp;
import se.sics.nat.hp.server.HPServerComp;
import se.sics.nat.pm.client.PMClientComp;
import se.sics.nat.pm.server.PMServerComp;
import se.sics.nat.pm.server.PMServerPort;
import se.sics.p2ptoolbox.croupier.CroupierPort;
import se.sics.p2ptoolbox.util.config.KConfigCore;
import se.sics.p2ptoolbox.util.filters.AndFilter;
import se.sics.p2ptoolbox.util.filters.NotFilter;
import se.sics.p2ptoolbox.util.nat.NatedTrait;
import se.sics.p2ptoolbox.util.network.ContentMsg;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;
import se.sics.p2ptoolbox.util.network.impl.BasicContentMsg;
import se.sics.p2ptoolbox.util.network.impl.BasicHeader;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;
import se.sics.p2ptoolbox.util.network.impl.DecoratedHeader;
import se.sics.p2ptoolbox.util.proxy.SystemHookSetup;
import se.sics.p2ptoolbox.util.status.Status;
import se.sics.p2ptoolbox.util.status.StatusPort;
import se.sics.p2ptoolbox.util.update.SelfAddress;
import se.sics.p2ptoolbox.util.update.SelfAddressUpdate;
import se.sics.p2ptoolbox.util.update.SelfAddressUpdatePort;
import se.sics.p2ptoolbox.util.update.SelfViewUpdatePort;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class NatTraverserComp extends ComponentDefinition {

    private static final Logger LOG = LoggerFactory.getLogger(NatTraverserComp.class);
    private String logPrefix = "";

    private final Negative<StatusPort> status = provides(StatusPort.class);
    private final Negative<SelfAddressUpdatePort> providedSAUpdate = provides(SelfAddressUpdatePort.class);
    private final Negative<Network> providedNetwork = provides(Network.class);
    private final Positive<Network> network = requires(Network.class);
    private final Positive<Timer> timer = requires(Timer.class);
    private final Positive<OverlayMngrPort> overlayMngr = requires(OverlayMngrPort.class);
    private final Positive<FailureDetectorPort> fd = requires(FailureDetectorPort.class);

    private Positive<SHPClientPort> hpClient;

    private final ChannelFilter<Msg, Boolean> handleTraffic = new AndFilter(new NatTrafficFilter(), new NotFilter(new NatInternalFilter()));
    private final ChannelFilter<Msg, Boolean> forwardTraffic = new AndFilter(new NotFilter(new NatTrafficFilter()), new NotFilter(new NatInternalFilter()));

    private final NatTraverserKCWrapper config;
    private final SystemHookSetup systemHooks;

    private DecoratedAddress selfAdr;
    private UUID internalStateCheckId;

    private final ConnectionTracker connectionTracker;
    private final ConnectionMaker connectionMaker;
    private final TrafficTracker trafficTracker;
    private final ComponentTracker compTracker;

    public NatTraverserComp(NatTraverserInit init) {
        this.config = init.config;
        this.systemHooks = init.systemHooks;
        this.selfAdr = init.selfAdr;

        this.logPrefix = "<nid:" + selfAdr.getId() + "> ";
        LOG.info("{}initiating...", logPrefix);

        this.connectionTracker = new ConnectionTracker();
        this.connectionMaker = new ConnectionMaker();
        this.trafficTracker = new TrafficTracker();
        this.compTracker = new ComponentTracker();
        
        subscribe(handleStart, control);
        subscribe(handleInternalStateCheck, timer);
    }
    
    //********************************CONTROL***********************************
    Handler handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            LOG.info("{}starting with self:{}", logPrefix, selfAdr);
            compTracker.start();
            scheduleInternalStateCheck();
        }
    };
    
    private void ready() {
        //connection tracker
        subscribe(connectionTracker.handleNetCloseConnection, network);
        subscribe(connectionTracker.handleSuspectConnectionEnd, fd);

        //connection maker
        subscribe(connectionMaker.handleOpenConnectionSuccess, hpClient);
        subscribe(connectionMaker.handleOpenConnectionFail, hpClient);

        //traffic tracker
        subscribe(trafficTracker.handleLocal, providedNetwork);
        subscribe(trafficTracker.handleNetwork, network);
        
        trigger(new Status.Internal(new NatStatus()), status);
    }

    @Override
    public void tearDown() {
        cancelInternalStateCheck();
    }

    Handler handleInternalStateCheck = new Handler<PeriodicInternalStateCheck>() {
        @Override
        public void handle(PeriodicInternalStateCheck event) {
            LOG.info("{}internal state check connection tracker - open:{}",
                    new Object[]{logPrefix, connectionTracker.openConnections.size()});
            LOG.info("{}internal state check connection maker - pending:{}",
                    new Object[]{logPrefix, connectionMaker.pendingConnections.size()});
            LOG.info("{}internal state check traffic tracker - buffering for targets:{}",
                    new Object[]{logPrefix, trafficTracker.pendingMsgs.size()});
        }
    };

    //*************************COMPONENT_TRACKER********************************
    public class ComponentTracker {

        private Component pmClientComp;
        private Component pmServerComp;
        private Component hpServerComp;
        private Component hpClientComp;

        private UUID natParentServiceReq;

        public ComponentTracker() {
        }

        //*************************COMPONENTS***********************************
        private void start() {
            if (NatedTrait.isOpen(selfAdr)) {
                setupPMServer();
                setupHPServer();
            } else {
                setupPMClient();
            }
            setupHPClient();
            
            subscribe(handlePMCroupierReady, overlayMngr);
            if (NatedTrait.isOpen(selfAdr)) {
                setupPMServerCroupier();
            } else {
                setupPMClientCroupier();
            }
        }

        private void setupPMServer() {
            LOG.info("{}connecting pm server", logPrefix);
            pmServerComp = create(PMServerComp.class, new PMServerComp.PMServerInit(config.configCore, selfAdr));
            connect(pmServerComp.getNegative(Timer.class), timer);
            connect(pmServerComp.getNegative(Network.class), network);
            //TODO Alex connect fd
            subscribe(handleSelfAddressRequest, providedSAUpdate);
        }

        private void setupPMClient() {
            LOG.info("{}connecting pm client", logPrefix);
            pmClientComp = create(PMClientComp.class, new PMClientComp.PMClientInit(config.configCore, selfAdr));
            connect(pmClientComp.getNegative(Timer.class), timer);
            connect(pmClientComp.getNegative(Network.class), network);
            connect(pmClientComp.getPositive(SelfAddressUpdatePort.class), providedSAUpdate);
            //TODO Alex connect fd 
            subscribe(handleSelfAddressUpdate, pmClientComp.getPositive(SelfAddressUpdatePort.class));
        }

        private void setupHPServer() {
            LOG.info("{}connecting hp server", logPrefix);
            hpServerComp = create(HPServerComp.class, new HPServerComp.HPServerInit(config.configCore, selfAdr));
            connect(hpServerComp.getNegative(Timer.class), timer);
            connect(hpServerComp.getNegative(Network.class), network);
            connect(hpServerComp.getNegative(PMServerPort.class), pmServerComp.getPositive(PMServerPort.class));
        }

        private void setupHPClient() {
            LOG.info("{}connecting hp client", logPrefix);
            hpClientComp = create(SHPClientComp.class, new SHPClientComp.SHPClientInit(config.configCore, selfAdr));
            connect(hpClientComp.getNegative(Timer.class), timer);
            connect(hpClientComp.getNegative(Network.class), network);
            if (!NatedTrait.isOpen(selfAdr)) {
                connect(hpClientComp.getNegative(SelfAddressUpdatePort.class),
                        pmClientComp.getPositive(SelfAddressUpdatePort.class));
            }
            hpClient = hpClientComp.getPositive(SHPClientPort.class);
        }
        
        //pm server and client are mutually exclusive
        private void setupPMServerCroupier() {
            natParentServiceReq = UUID.randomUUID();
            OMngrCroupier.ConnectRequestBuilder reqBuilder = new OMngrCroupier.ConnectRequestBuilder(natParentServiceReq);
            reqBuilder.setIdentifiers(config.parentMaker.globalCroupier.array(), config.parentMaker.natParentService.array());
            reqBuilder.setupCroupier(false);
            reqBuilder.connectTo(pmServerComp.getNegative(CroupierPort.class), pmServerComp.getPositive(SelfViewUpdatePort.class));
            LOG.info("{}waiting for croupier app...", logPrefix);
            trigger(reqBuilder.build(), overlayMngr);
        }

        private void setupPMClientCroupier() {
            natParentServiceReq = UUID.randomUUID();
            OMngrCroupier.ConnectRequestBuilder reqBuilder = new OMngrCroupier.ConnectRequestBuilder(natParentServiceReq);
            reqBuilder.setIdentifiers(config.parentMaker.globalCroupier.array(), config.parentMaker.natParentService.array());
            reqBuilder.setupCroupier(true);
            reqBuilder.connectTo(pmClientComp.getNegative(CroupierPort.class), pmClientComp.getPositive(SelfViewUpdatePort.class));
            LOG.info("{}waiting for croupier app...", logPrefix);
            trigger(reqBuilder.build(), overlayMngr);
        }

        Handler handlePMCroupierReady = new Handler<OMngrCroupier.ConnectResponse>() {
            @Override
            public void handle(OMngrCroupier.ConnectResponse resp) {
                LOG.info("{}app croupier ready", logPrefix);

                if (NatedTrait.isOpen(selfAdr)) {
                    trigger(Start.event, pmServerComp.control());
                    trigger(Start.event, hpServerComp.control());
                } else {
                    trigger(Start.event, pmClientComp.control());
                }
                trigger(Start.event, hpClientComp.control());
                ready();
            }
        };

        Handler handleSelfAddressUpdate = new Handler<SelfAddressUpdate>() {
            @Override
            public void handle(SelfAddressUpdate update) {
                LOG.trace("{}received self update address:{}", logPrefix, update.id);
                selfAdr = update.self;
                LOG.info("{}updating self address:{}", logPrefix, selfAdr);
            }
        };

        Handler handleSelfAddressRequest = new Handler<SelfAddress.Request>() {
            @Override
            public void handle(SelfAddress.Request req) {
                LOG.trace("{}received self request");
                answer(req, req.answer(selfAdr));
            }
        };
    }

//*************************TRAFFIC TRACKER**********************************
    public class TrafficTracker {

        public Map<BasicAddress, List<BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Object>>> pendingMsgs;

        public TrafficTracker() {
            this.pendingMsgs = new HashMap<BasicAddress, List<BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Object>>>();
        }

        Handler handleLocal = new Handler<Msg>() {
            @Override
            public void handle(Msg msg) {
                BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Object> contentMsg
                        = (BasicContentMsg) msg;
                LOG.trace("{}received outgoing:{}", logPrefix, contentMsg.getContent());
                if (!handleTraffic.getValue(msg)) {
                    /**
                     * should not get here. If I get here - the NatTrafficFilter
                     * is not set or not working properly and I am processing
                     * more msgs than necessary
                     */
                    LOG.debug("{}bad config - forwarding outgoing:{}", new Object[]{logPrefix, msg});
                    trigger(msg, network);
                    return;
                }
                

                DecoratedAddress destination = contentMsg.getDestination();
                if (NatTraverserFeasibility.direct(selfAdr, destination)) {
                    LOG.debug("{}forwarding msg:{} local to network", logPrefix, contentMsg.getContent());
                    trigger(msg, network);
                    return;
                }
                Optional<Pair<BasicAddress, DecoratedAddress>> connection
                        = connectionTracker.connected(destination.getBase());
                //already connected
                if (connection.isPresent()) {
                    DecoratedAddress connSelf = selfAdr.changeBase(connection.get().getValue0());
                    DecoratedAddress connTarget = connection.get().getValue1();
                    if (!selfAdr.getBase().equals(connSelf.getBase())) {
                        LOG.warn("{}mapping alocation < EI", logPrefix);
                        //TODO Alex - further check for correctness required
                    }

                    BasicHeader basicHeader = new BasicHeader(connSelf, connTarget, Transport.UDP);
                    DecoratedHeader<DecoratedAddress> forwardHeader = contentMsg.getHeader().changeBasicHeader(basicHeader);
                    ContentMsg forwardMsg = new BasicContentMsg(forwardHeader, contentMsg.getContent());
                    LOG.debug("{}forwarding msg:{} local to network", logPrefix, forwardMsg);
                    trigger(forwardMsg, network);
                    return;
                }

                //connecting
                List<BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Object>> pending
                        = pendingMsgs.get(destination.getBase());
                if (pending == null) {
                    if (connectionMaker.connect(contentMsg.getSource(), destination)) {
                        pending = new ArrayList<BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Object>>();
                        pendingMsgs.put(destination.getBase(), pending);
                    } else {
                        LOG.warn("{}dropping msg:{}", logPrefix, msg);
                        return;
                    }
                }
                LOG.debug("{}waiting on connection to:{} buffering msg:{}",
                        new Object[]{logPrefix, destination.getBase(), msg});
                pending.add(contentMsg);
            }
        };

        Handler handleNetwork = new Handler<Msg>() {
            @Override
            public void handle(Msg msg) {
                BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Object> contentMsg
                        = (BasicContentMsg) msg;
                LOG.trace("{}received outgoing:{}", logPrefix, contentMsg.getContent());
                if (!handleTraffic.getValue(msg)) {
                    /**
                     * should not get here. If I get here - the NatTrafficFilter
                     * is not set or not working properly and I am processing
                     * more msgs than necessary
                     */
                    LOG.debug("{}bad config - forwarding incoming:{}", new Object[]{logPrefix, msg});
                    trigger(msg, providedNetwork);
                    return;
                }
                LOG.debug("{}forwarding msg:{} network to local", logPrefix, contentMsg.getContent());
                trigger(msg, providedNetwork);
            }
        };

        public void connected(Pair<BasicAddress, DecoratedAddress> connection) {
            List<BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Object>> pending
                    = pendingMsgs.remove(connection.getValue1().getBase());
            if (pending == null) {
                LOG.warn("{}weird pending empty to:{}", logPrefix, connection.getValue1().getBase());
                return;
            }
            DecoratedAddress connSelf = selfAdr.changeBase(connection.getValue0());
            DecoratedAddress connTarget = connection.getValue1();
            if (!selfAdr.getBase().equals(connSelf.getBase())) {
                LOG.warn("{}mapping alocation < EI", logPrefix);
                //TODO Alex - further check for correctness required
            }

            for (BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Object> msg : pending) {
                BasicHeader basicHeader = new BasicHeader(connSelf, connTarget, Transport.UDP);
                DecoratedHeader<DecoratedAddress> forwardHeader = msg.getHeader().changeBasicHeader(basicHeader);
                ContentMsg forwardMsg = new BasicContentMsg(forwardHeader, msg.getContent());
                LOG.debug("{}forwarding outgoing buffered msg:{} from:{} to:{} ",
                        new Object[]{logPrefix, forwardMsg.getContent(), connection.getValue0(), connection.getValue1().getBase()});
                trigger(forwardMsg, network);
            }
        }

        public void connectFailed(BasicAddress target) {
            LOG.info("{}connection to:{} failed, dropping buffered msgs",
                    new Object[]{logPrefix, target});
            pendingMsgs.remove(target);
        }
    } //***************************CONNECTION MAKER*******************************

    public class ConnectionMaker {

        private final Map<BasicAddress, UUID> pendingConnections;

        public ConnectionMaker() {
            this.pendingConnections = new HashMap<BasicAddress, UUID>();
        }

        public boolean connect(DecoratedAddress self, DecoratedAddress target) {
            switch (NatTraverserFeasibility.check(self, target)) {
                case DIRECT:
                    LOG.error("{}logical error - direct connection, should not get here");
                    throw new RuntimeException("logical error - direct connection, should not get here");
                case SHP:
                    pendingConnections.put(target.getBase(), null); //no timeout needed - the shp deals with timeout
                    LOG.info("{}connection request to shp to:{}", logPrefix, target.getBase());
                    trigger(new OpenConnection.Request(UUID.randomUUID(), target), hpClient);
                    return true;
                case UNFEASIBLE:
                default: //act as UNFEASIBLE
                    LOG.warn("{}unfeasible nat combination self:{} target:{}",
                            new Object[]{logPrefix, self.getTrait(NatedTrait.class).type, target.getTrait(NatedTrait.class).type});

                    return false;
            }
        }

        Handler handleOpenConnectionSuccess = new Handler<OpenConnection.Success>() {
            @Override
            public void handle(OpenConnection.Success success) {
                LOG.info("{}created connection to:{} on:{}",
                        new Object[]{logPrefix, success.target.getBase(), success.self.getBase()});
                connectSuccess(Pair.with(success.self.getBase(), success.target));
            }
        };

        Handler handleOpenConnectionFail = new Handler<OpenConnection.Fail>() {

            @Override
            public void handle(OpenConnection.Fail fail) {
                LOG.warn("{}connection to:{} failed to initialize", logPrefix, fail.target.getBase());
                connectFailed(fail.target.getBase());
            }
        };

        private void connectSuccess(Pair<BasicAddress, DecoratedAddress> connection) {
            pendingConnections.remove(connection.getValue1().getBase());
            connectionTracker.newConnection(connection);
            trafficTracker.connected(connection);
        }

        private void connectFailed(BasicAddress target) {
            pendingConnections.remove(target);
            trafficTracker.connectFailed(target);
        }
    }

//***************************CONNECTION TRACKER*****************************
    public class ConnectionTracker {

        //<remoteEnd, <localEnd, remoteFullAddress>>
        private final Map<BasicAddress, Pair<BasicAddress, DecoratedAddress>> openConnections = new HashMap<>(); 

        public ConnectionTracker() {
        }

        public void newConnection(Pair<BasicAddress, DecoratedAddress> connection) {
            openConnections.put(connection.getValue1().getBase(), connection);
            trigger(new FDEvent.Follow(connection.getValue1(), config.natTraverserService, 
                NatTraverserComp.this.getComponentCore().id()), fd);
        }

        public Optional<Pair<BasicAddress, DecoratedAddress>> connected(BasicAddress target) {
            return Optional.fromNullable(openConnections.get(target));
        }

        ClassMatchedHandler handleNetCloseConnection
                = new ClassMatchedHandler<Close, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Close>>() {
                    @Override
                    public void handle(Close content, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Close> container) {
                        LOG.info("{}target:{} closing connection", logPrefix, container.getSource().getBase());
                        cleanConnection(container.getSource().getBase());
                    }
                };
        
        Handler handleSuspectConnectionEnd = new Handler<FDEvent.Suspect>() {
            @Override
            public void handle(FDEvent.Suspect event) {
                LOG.info("{}suspect:{}", new Object[]{logPrefix, event.target});
                cleanConnection(event.target.getBase());
            }
        };

        private void cleanConnection(BasicAddress target) {
            Pair<BasicAddress, DecoratedAddress> connection = openConnections.remove(target);
            if(connection == null) {
                //ended before
                //TODO Alex what to do here
                return;
            }
            trigger(new FDEvent.Unfollow(connection.getValue1(), config.natTraverserService, 
                NatTraverserComp.this.getComponentCore().id()), fd);
            
            //TODO Alex - any other cleanup to do here?
        }
    }

    private void scheduleInternalStateCheck() {
        if (internalStateCheckId != null) {
            LOG.warn("{}double starting internal state check timeout", logPrefix);
            return;
        }
        SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(config.stateCheckTimeout, config.stateCheckTimeout);
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

    public static class PeriodicInternalStateCheck extends Timeout {

        public PeriodicInternalStateCheck(SchedulePeriodicTimeout spt) {
            super(spt);
        }
    }
    public static class NatTraverserInit extends Init<NatTraverserComp> {

        public final NatTraverserKCWrapper config;
        public final SystemHookSetup systemHooks;
        public final DecoratedAddress selfAdr;

        public NatTraverserInit(KConfigCore configCore, SystemHookSetup systemHooks, DecoratedAddress selfAdr) {
            this.config = new NatTraverserKCWrapper(configCore);
            this.systemHooks = systemHooks;
            this.selfAdr = selfAdr;
        }
    }
}
