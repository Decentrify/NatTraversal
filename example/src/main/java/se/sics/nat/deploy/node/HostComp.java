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
package se.sics.nat.simulation.core;

import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.Component;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Fault;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.Stop;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.Timer;
import se.sics.ktoolbox.nat.network.NatTraverserComp;
import se.sics.ktoolbox.nat.stun.NatReady;
import se.sics.ktoolbox.nat.stun.StunClientPort;
import se.sics.ktoolbox.nat.stun.client.StunClientComp;
import se.sics.ktoolbox.nat.stun.client.StunClientComp.StunClientInit;
import se.sics.nat.common.NatTraverserConfig;
import se.sics.nat.emulator.NatEmulatorComp;
import se.sics.nat.emulator.NatEmulatorComp.NatEmulatorInit;
import se.sics.nat.hp.client.SHPClientComp;
import se.sics.nat.hp.client.SHPClientPort;
import se.sics.nat.hp.server.HPServerComp;
import se.sics.nat.network.Nat;
import static se.sics.nat.network.Nat.Type.OPEN;
import se.sics.nat.network.NatedTrait;
import se.sics.nat.pm.client.PMClientComp;
import se.sics.nat.pm.client.PMClientPort;
import se.sics.nat.pm.client.msg.SelfUpdate;
import se.sics.nat.pm.server.PMServerComp;
import se.sics.nat.pm.server.PMServerPort;
import se.sics.p2ptoolbox.croupier.CroupierComp;
import se.sics.p2ptoolbox.croupier.CroupierConfig;
import se.sics.p2ptoolbox.croupier.CroupierControlPort;
import se.sics.p2ptoolbox.croupier.CroupierPort;
import se.sics.p2ptoolbox.croupier.msg.CroupierDisconnected;
import se.sics.p2ptoolbox.croupier.msg.CroupierJoin;
import se.sics.p2ptoolbox.croupier.msg.CroupierUpdate;
import se.sics.p2ptoolbox.util.config.SystemConfig;
import se.sics.p2ptoolbox.util.helper.SystemConfigBuilder;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class HostComp extends ComponentDefinition {

    private Logger LOG = LoggerFactory.getLogger(HostComp.class);
    private String logPrefix = "";

    private Positive<Timer> timer = requires(Timer.class);
    private Positive<Network> network = requires(Network.class);

    private final HostInit init;

    private Component natEmulator;
    private Component stunClient;
    private Component parentMaker; //client/server
    private Component croupier;
    private Component hpServer;
    private Component simpleHP;
    private Component nat;
    private Component node;

    private SystemConfig systemConfig;

    public HostComp(HostInit init) {
        LOG.info("{}initializing...", logPrefix);
        this.init = init;

        subscribe(handleStart, control);
        subscribe(handleStop, control);
    }

    //*************************CONTROL******************************************
    Handler handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            LOG.info("{}starting...", logPrefix);
            connectNatEmulator();
            connectStunClient();
        }
    };

    Handler handleStop = new Handler<Stop>() {
        @Override
        public void handle(Stop event) {
            LOG.info("{}stopping...", logPrefix);
        }
    };

    @Override
    public Fault.ResolveAction handleFault(Fault fault) {
        LOG.error("{}child component failure:{}", logPrefix, fault);
        System.exit(1);
        return Fault.ResolveAction.RESOLVED;
    }

    //**************************NAT_DETECTION***********************************
    private void connectNatEmulator() {
        natEmulator = create(NatEmulatorComp.class, init.natEmulatorInit);
        connect(natEmulator.getNegative(Timer.class), timer);
        connect(natEmulator.getNegative(Network.class), network);
        trigger(Start.event, natEmulator.control());
    }

    private void connectStunClient() {
        stunClient = create(StunClientComp.class, init.stunClientInit);
        connect(stunClient.getNegative(Timer.class), timer);
        connect(stunClient.getNegative(Network.class), natEmulator.getPositive(Network.class));
        trigger(Start.event, stunClient.control());
        subscribe(handleNatReady, stunClient.getPositive(StunClientPort.class));
    }
    private Handler handleNatReady = new Handler<NatReady>() {
        @Override
        public void handle(NatReady ready) {
            LOG.info("{}nat detected:{} public ip:{}",
                    new Object[]{logPrefix, ready.nat, ready.publicIp.get()});
            if (!checkStunResult(ready)) {
                LOG.error("{}stun result is wrong", logPrefix);
                throw new RuntimeException("stun result is wrong");
            }
            systemConfig = init.systemConfigBuilder.setSelfIp(ready.publicIp.get()).setSelfNat(ready.nat).build();
            connectGlobalCroupier();
            if (ready.nat.type.equals(Nat.Type.OPEN)) {
                connectPMServer();
                connectHPServer();
            } else {
                connectPMClient();
            }
            connectSHPClient();
            connectNat();
            connectApp();
        }
    };

    private boolean checkStunResult(NatReady ready) {
        switch (init.natEmulatorInit.natType.type) {
            case OPEN:
                return ready.nat.type.equals(Nat.Type.OPEN);
            case NAT:
                return ready.nat.type.equals(Nat.Type.NAT)
                        && ready.nat.allocationPolicy.equals(init.natEmulatorInit.natType.allocationPolicy)
                        && ready.nat.mappingPolicy.equals(init.natEmulatorInit.natType.mappingPolicy)
                        && ready.nat.filteringPolicy.equals(init.natEmulatorInit.natType.filteringPolicy)
                        && ready.publicIp.get().equals(init.natEmulatorInit.selfIp);
            default:
                return false;
        }
    }

    //*****************************REST*****************************************
    private void connectGlobalCroupier() {
        croupier = create(CroupierComp.class, new CroupierComp.CroupierInit(systemConfig, init.croupierConfig, init.globalCroupierOverlayId));
        connect(croupier.getNegative(Timer.class), timer);
        connect(croupier.getNegative(Network.class), natEmulator.getPositive(Network.class));
        trigger(Start.event, croupier.control());
        trigger(new CroupierJoin(init.publicNodes), croupier.getPositive(CroupierControlPort.class));
        trigger(new CroupierUpdate(new Object()), croupier.getPositive(CroupierPort.class));
        subscribe(handleCroupierDisconnect, croupier.getPositive(CroupierControlPort.class));
    }

    Handler handleCroupierDisconnect = new Handler<CroupierDisconnected>() {
        @Override
        public void handle(CroupierDisconnected event) {
            LOG.warn("{}croupier disconnected", logPrefix);
        }
    };

    private void connectPMServer() {
        parentMaker = create(PMServerComp.class, new PMServerComp.PMServerInit(init.ntConfig, systemConfig.self));
        connect(parentMaker.getNegative(Timer.class), timer);
        connect(parentMaker.getNegative(Network.class), network);
        trigger(Start.event, parentMaker.control());
    }

    private void connectPMClient() {
        parentMaker = create(PMClientComp.class, new PMClientComp.PMClientInit(init.ntConfig, systemConfig.self));
        connect(parentMaker.getNegative(Timer.class), timer);
        connect(parentMaker.getNegative(Network.class), natEmulator.getPositive(Network.class));
        connect(parentMaker.getNegative(CroupierPort.class), croupier.getPositive(CroupierPort.class));
        trigger(Start.event, parentMaker.control());
        subscribe(handleSelfUpdate, parentMaker.getPositive(PMClientPort.class));
    }

    Handler handleSelfUpdate = new Handler<SelfUpdate>() {
        @Override
        public void handle(SelfUpdate update) {
            LOG.info("{}self update:{}", logPrefix, update.self);
        }
    };

    private void connectHPServer() {
        hpServer = create(HPServerComp.class, new HPServerComp.HPServerInit(init.ntConfig, systemConfig.self));
        connect(hpServer.getNegative(Timer.class), timer);
        connect(hpServer.getNegative(Network.class), natEmulator.getPositive(Network.class));
        connect(hpServer.getNegative(PMServerPort.class), parentMaker.getPositive(PMServerPort.class));
        trigger(Start.event, hpServer.control());
    }

    private void connectSHPClient() {
        simpleHP = create(SHPClientComp.class, new SHPClientComp.SHPClientInit(init.ntConfig, systemConfig.self));
        connect(simpleHP.getNegative(Timer.class), timer);
        connect(simpleHP.getNegative(Network.class), natEmulator.getPositive(Network.class));
        if (!NatedTrait.isOpen(systemConfig.self)) {
            connect(simpleHP.getNegative(PMClientPort.class), parentMaker.getPositive(PMClientPort.class));
        }
        trigger(Start.event, simpleHP.control());
    }

    private void connectNat() {
        nat = create(NatTraverserComp.class, new NatTraverserComp.NatTraverserInit(init.ntConfig, systemConfig.self));
        connect(nat.getNegative(Timer.class), timer);
        connect(nat.getNegative(Network.class), natEmulator.getPositive(Network.class));
        if (!NatedTrait.isOpen(systemConfig.self)) {
            connect(nat.getNegative(PMClientPort.class), parentMaker.getPositive(PMClientPort.class));
        }
        connect(nat.getNegative(SHPClientPort.class), simpleHP.getPositive(SHPClientPort.class));
        trigger(Start.event, nat.control());
    }

    private void connectApp() {
        node = create(NodeComp.class, new NodeComp.NodeInit(systemConfig.self));
        connect(node.getNegative(Network.class), nat.getPositive(Network.class));
        connect(node.getNegative(CroupierPort.class), croupier.getPositive(CroupierPort.class));
        if (!NatedTrait.isOpen(systemConfig.self)) {
            connect(node.getNegative(PMClientPort.class), parentMaker.getPositive(PMClientPort.class));
        }
        trigger(Start.event, node.control());
    }

    public static class HostInit extends Init<HostComp> {

        public final NatEmulatorInit natEmulatorInit;
        public final StunClientInit stunClientInit;
        public final NatTraverserConfig ntConfig;
        public final SystemConfigBuilder systemConfigBuilder;
        public final CroupierConfig croupierConfig;
        public final int globalCroupierOverlayId;
        public final Set<DecoratedAddress> publicNodes;

        public HostInit(NatEmulatorInit natEmulatorInit, StunClientInit stunClientInit,
                NatTraverserConfig ntConfig, SystemConfigBuilder systemConfigBuilder, CroupierConfig croupierConfig,
                int globalCroupierOverlayId, Set<DecoratedAddress> publicNodes) {
            this.natEmulatorInit = natEmulatorInit;
            this.stunClientInit = stunClientInit;
            this.ntConfig = ntConfig;
            this.systemConfigBuilder = systemConfigBuilder;
            this.croupierConfig = croupierConfig;
            this.globalCroupierOverlayId = globalCroupierOverlayId;
            this.publicNodes = publicNodes;
        }
    }
}
