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
package se.sics.nat.stun.server;

import java.util.UUID;
import org.javatuples.Pair;
import org.javatuples.Triplet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.Component;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Fault;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.Timer;
import se.sics.ktoolbox.networkmngr.NetworkKCWrapper;
import se.sics.ktoolbox.networkmngr.NetworkMngrComp;
import se.sics.ktoolbox.networkmngr.NetworkMngrPort;
import se.sics.ktoolbox.networkmngr.events.Bind;
import se.sics.ktoolbox.networkmngr.events.NetworkMngrReady;
import se.sics.ktoolbox.overlaymngr.OverlayMngrComp;
import se.sics.ktoolbox.overlaymngr.OverlayMngrComp.OverlayMngrInit;
import se.sics.ktoolbox.overlaymngr.OverlayMngrPort;
import se.sics.ktoolbox.overlaymngr.events.OMngrCroupier;
import se.sics.nat.stun.server.StunServerComp.StunServerInit;
import se.sics.p2ptoolbox.croupier.CroupierPort;
import se.sics.p2ptoolbox.util.config.KConfigCore;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;
import se.sics.p2ptoolbox.util.proxy.SystemHookSetup;
import se.sics.p2ptoolbox.util.update.SelfViewUpdatePort;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class StunServerHostComp extends ComponentDefinition {

    private static final Logger LOG = LoggerFactory.getLogger(StunServerHostComp.class);
    private String logPrefix = "";

    private Positive<Timer> timer = requires(Timer.class);

    private final StunServerKCWrapper config;
    private final SystemHookSetup systemHooks;
    private NetworkKCWrapper networkConfig;
    private Pair<DecoratedAddress, DecoratedAddress> stunAdr = Pair.with(null, null);
    private DecoratedAddress nodeAdr = null;

    private Component networkMngr;
    private Component overlayMngr;
    private Component failureDetector; //TODO Alex - create and connect failure detetcor
    private Component stunServer;

    private Triplet<UUID, UUID, UUID> binding;

    public StunServerHostComp(StunServerHostInit init) {
        this.config = init.config;
        this.systemHooks = init.systemHooks;
        this.logPrefix = "<" + config.system.id + "> ";
        LOG.info("{}initializing with seed:{}", logPrefix, config.system.seed);

        subscribe(handleStart, control);
        connectNetworkMngr(true);
    }

    private Handler handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            LOG.info("{}starting...", logPrefix);
        }
    };

    @Override
    public Fault.ResolveAction handleFault(Fault fault) {
        LOG.error("{}child component failure:{}", logPrefix, fault);
        System.exit(1);
        return Fault.ResolveAction.RESOLVED;
    }

    private void connectNetworkMngr(boolean started) {
        networkMngr = create(NetworkMngrComp.class, new NetworkMngrComp.NetworkMngrInit(config.configCore, systemHooks));
        subscribe(handleNetworkMngrReady, networkMngr.getPositive(NetworkMngrPort.class));
        subscribe(handleBindPort, networkMngr.getPositive(NetworkMngrPort.class));
    }

    Handler handleNetworkMngrReady = new Handler<NetworkMngrReady>() {
        @Override
        public void handle(NetworkMngrReady event) {
            networkConfig = new NetworkKCWrapper(config.configCore);
            LOG.info("{}network manager ready on local interface:{}", logPrefix, networkConfig.localIp);

            DecoratedAddress adr1 = DecoratedAddress.open(networkConfig.localIp, config.stunServerPorts.getValue0(), config.system.id);
            DecoratedAddress adr2 = DecoratedAddress.open(networkConfig.localIp, config.stunServerPorts.getValue1(), config.system.id);
            DecoratedAddress adr3 = DecoratedAddress.open(networkConfig.localIp, config.nodePort, config.system.id);

            binding = Triplet.with(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
            trigger(new Bind.Request(binding.getValue0(), adr1, config.hardBind), networkMngr.getPositive(NetworkMngrPort.class));
            trigger(new Bind.Request(binding.getValue1(), adr2, config.hardBind), networkMngr.getPositive(NetworkMngrPort.class));
            trigger(new Bind.Request(binding.getValue2(), adr3, config.hardBind), networkMngr.getPositive(NetworkMngrPort.class));
            LOG.info("{}waiting for network binding", logPrefix);
        }
    };

    Handler handleBindPort = new Handler<Bind.Response>() {
        @Override
        public void handle(Bind.Response resp) {
            if (binding.getValue0().equals(resp.req.id)) {
                stunAdr = stunAdr.setAt0(DecoratedAddress.open(networkConfig.localIp, resp.boundPort, config.system.id));
            } else if (binding.getValue1().equals(resp.req.id)) {
                stunAdr = stunAdr.setAt1(DecoratedAddress.open(networkConfig.localIp, resp.boundPort, config.system.id));
            } else if (binding.getValue2().equals(resp.req.id)) {
                nodeAdr = DecoratedAddress.open(networkConfig.localIp, resp.boundPort, config.system.id);
            } else {
                throw new RuntimeException("logic error in network manager");
            }
            if (stunAdr.getValue0() == null || stunAdr.getValue1() == null || nodeAdr == null) {
                return;
            }
            logPrefix = "<" + stunAdr.getValue0().getId() + ">";
            LOG.info("{}bound ports stun1:{} stun2:{} node:{}", new Object[]{logPrefix, stunAdr.getValue0().getPort(),
                stunAdr.getValue1().getPort(), nodeAdr.getPort()});
            connectOverlayMngr();
            connectApp();
            setupAppCroupier();
        }
    };

    private void connectOverlayMngr() {
        overlayMngr = create(OverlayMngrComp.class, new OverlayMngrInit(config.configCore, nodeAdr));
        connect(overlayMngr.getNegative(Timer.class), timer);
        connect(overlayMngr.getNegative(Network.class), networkMngr.getPositive(Network.class));

        trigger(Start.event, overlayMngr.control());
    }

    private void connectApp() {
        stunServer = create(StunServerComp.class, new StunServerInit(config.configCore, stunAdr));
        connect(stunServer.getNegative(Timer.class), timer);
        connect(stunServer.getNegative(Network.class), networkMngr.getPositive(Network.class));
    }

    private void setupAppCroupier() {
        subscribe(handleAppCroupierReady, overlayMngr.getPositive(OverlayMngrPort.class));

        OMngrCroupier.ConnectRequestBuilder reqBuilder = new OMngrCroupier.ConnectRequestBuilder(UUID.randomUUID());
        reqBuilder.setIdentifiers(config.globalCroupier, config.stunService);
        reqBuilder.setupCroupier(false);
        reqBuilder.connectTo(stunServer.getNegative(CroupierPort.class), stunServer.getPositive(SelfViewUpdatePort.class));
        LOG.info("{}waiting for croupier app...", logPrefix);
        trigger(reqBuilder.build(), overlayMngr.getPositive(OverlayMngrPort.class));
    }

    Handler handleAppCroupierReady = new Handler<OMngrCroupier.ConnectResponse>() {
        @Override
        public void handle(OMngrCroupier.ConnectResponse resp) {
            LOG.info("{}app croupier ready", logPrefix);
            startApp();
        }
    };

    private void startApp() {
        trigger(Start.event, stunServer.control());
    }

    public static class StunServerHostInit extends Init<StunServerHostComp> {

        public final StunServerKCWrapper config;
        public final SystemHookSetup systemHooks;

        public StunServerHostInit(KConfigCore configCore, SystemHookSetup systemHooks) {
            this.config = new StunServerKCWrapper(configCore);
            this.systemHooks = systemHooks;
        }
    }
}
