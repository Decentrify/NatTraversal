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
package se.sics.nat.node;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.Component;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.Timer;
import se.sics.ktoolbox.networkmngr.NetworkMngrComp;
import se.sics.ktoolbox.networkmngr.NetworkMngrComp.NetworkMngrInit;
import se.sics.ktoolbox.networkmngr.NetworkMngrPort;
import se.sics.nat.NatDetectionComp;
import se.sics.nat.NatDetectionComp.NatDetectionInit;
import se.sics.nat.stun.NatDetected;
import se.sics.p2ptoolbox.util.config.KConfigCache;
import se.sics.p2ptoolbox.util.config.KConfigCore;
import se.sics.p2ptoolbox.util.nat.Nat;
import se.sics.p2ptoolbox.util.proxy.SystemHookSetup;

/**
 *
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class NodeHostComp extends ComponentDefinition {

    private static final Logger LOG = LoggerFactory.getLogger(NodeHostComp.class);
    private String logPrefix = "";

    private final Positive<Timer> timer = requires(Timer.class);

    private final KConfigCache config;
    private final SystemHookSetup systemHooks;

    private Component networkMngr;
    private Component natDetection;
    private Component natTraversal;

    public NodeHostComp(NodeHostInit init) {
        this.config = init.config;
        this.systemHooks = init.systemHooks;
        this.logPrefix = config.getNodeId() + " ";
        LOG.info("{}initiating", logPrefix);

        subscribe(handleStart, control);
    }

    Handler handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            LOG.info("{}setting up network mngr", logPrefix);
            Component networkMngr = create(NetworkMngrComp.class, new NetworkMngrInit(config.configCore, systemHooks));
            Component natDetection = create(NatDetectionComp.class, new NatDetectionInit(config.configCore, systemHooks));
            connect(natDetection.getNegative(Timer.class), timer);
            connect(natDetection.getNegative(Network.class), networkMngr.getPositive(Network.class));
            connect(natDetection.getNegative(NetworkMngrPort.class), networkMngr.getPositive(NetworkMngrPort.class));
            trigger(Start.event, networkMngr.control());
            trigger(Start.event, natDetection.control());
        }
    };

    Handler handleNatReady = new Handler<NatDetected>() {
        @Override
        public void handle(NatDetected ready) {
            if (ready.nat.type.equals(Nat.Type.UDP_BLOCKED)) {
                LOG.error("{}address is UDP blocked cannot join system", logPrefix);
                System.exit(1);
            }
            LOG.info("{}nat detected:{} public ip:{}",
                    new Object[]{logPrefix, ready.nat, ready.publicIp.get()});
            
//            systemConfigBuilder.setSelfIp(ready.publicIp);
//            systemConfigBuilder.setSelfNat(ready.nat);
//            if (ready.nat.type.equals(Nat.Type.UPNP)) {
//                proxy.subscribe(handleMapPorts, natDetection.getPositive(UpnpPort.class));
//                proxy.subscribe(handleUnmapPorts, natDetection.getPositive(UpnpPort.class));
//                Map<Integer, Pair<Protocol, Integer>> mapPort = new HashMap<Integer, Pair<Protocol, Integer>>();
//                mapPort.put(systemConfigBuilder.getSelfPort(), Pair.with(Protocol.UDP, systemConfigBuilder.getSelfPort()));
//                proxy.trigger(new MapPorts.Req(UUID.randomUUID(), mapPort), natDetection.getPositive(UpnpPort.class));
//            } else {
//                natTraversal();
//            }
        }
    };

    public static class NodeHostInit extends Init<NodeHostComp> {

        public final KConfigCache config;
        public final SystemHookSetup systemHooks;

        public NodeHostInit(KConfigCore configCore, SystemHookSetup systemHooks) {
            this.config = new KConfigCache(configCore);
            this.systemHooks = systemHooks;
        }
    }
}
