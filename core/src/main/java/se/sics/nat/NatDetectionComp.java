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
package se.sics.nat;

import java.net.InetAddress;
import java.util.List;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.Component;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Fault;
import se.sics.kompics.Fault.ResolveAction;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.Stop;
import se.sics.kompics.timer.Timer;
import se.sics.nat.common.util.NatDetectionResult;
import se.sics.nat.stun.NatReady;
import se.sics.nat.stun.StunClientPort;
import se.sics.nat.stun.client.SCNetworkHook;
import se.sics.nat.stun.client.StunClientComp;
import se.sics.nat.stun.upnp.UpnpComp;
import se.sics.nat.stun.upnp.UpnpPort;
import se.sics.nat.stun.upnp.msg.UpnpReady;
import se.sics.p2ptoolbox.util.nat.Nat;
import se.sics.p2ptoolbox.util.nat.NatedTrait;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class NatDetectionComp extends ComponentDefinition {

    private static final Logger LOG = LoggerFactory.getLogger(NatDetectionComp.class);
    private String logPrefix = "";

    private final Positive<Timer> timer = requires(Timer.class);
    private final Negative<NatDetectionPort> natDetection = provides(NatDetectionPort.class);
    private final Negative<UpnpPort> upnp = provides(UpnpPort.class);

    private NatDetectionInit init;

    private Component stunClient;
    private Component upnpComp;

    private NatDetectionResult natDetectionResult;

    public NatDetectionComp(NatDetectionInit init) {
        this.init = init;
        this.logPrefix = init.privateAdr + " ";
        LOG.info("{}initiating...", logPrefix);
        this.natDetectionResult = new NatDetectionResult();

        subscribe(handleStart, control);
        subscribe(handleStop, control);
    }

    //**************************CONTROL*****************************************
    Handler handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            LOG.info("{}starting...", logPrefix);
            connectStunClient();
            connectUpnp();
        }
    };

    Handler handleStop = new Handler<Stop>() {
        @Override
        public void handle(Stop event) {
            LOG.info("{}stopping...", logPrefix);
        }
    };

    @Override
    public ResolveAction handleFault(Fault fault) {
        LOG.error("{}fault:{} on comp:{}", new Object[]{logPrefix, fault.getCause().getMessage(), fault.getSourceCore().id()});
        return ResolveAction.ESCALATE;
    }

    //**************************************************************************
    private void connectStunClient() {
        Pair<DecoratedAddress, DecoratedAddress> scAdr = Pair.with(
                new DecoratedAddress(new BasicAddress(init.privateAdr.getIp(), init.scPorts.getValue0(), init.privateAdr.getId())),
                new DecoratedAddress(new BasicAddress(init.privateAdr.getIp(), init.scPorts.getValue1(), init.privateAdr.getId())));
        stunClient = create(StunClientComp.class, new StunClientComp.StunClientInit(scAdr, init.stunServers, init.scNetworkDefinition));
        connect(stunClient.getNegative(Timer.class), timer);
        trigger(Start.event, stunClient.control());
        subscribe(handleNatReady, stunClient.getPositive(StunClientPort.class));
    }

    private void connectUpnp() {
        upnpComp = create(UpnpComp.class, new UpnpComp.UpnpInit(1234, "nat upnp"));
        connect(upnp, upnpComp.getPositive(UpnpPort.class));
        trigger(Start.event, upnpComp.control());
        subscribe(handleUpnpReady, upnpComp.getPositive(UpnpPort.class));
    }

    Handler handleUpnpReady = new Handler<UpnpReady>() {
        @Override
        public void handle(UpnpReady ready) {
            LOG.info("{}upnp ready:{}", logPrefix, ready.externalIp);
            natDetectionResult.setUpnpReady(ready.externalIp);
            if (natDetectionResult.isReady()) {
                finalise();
            }
        }
    };

    private Handler handleNatReady = new Handler<NatReady>() {
        @Override
        public void handle(NatReady ready) {
            LOG.info("{}nat detected:{} public ip:{}",
                    new Object[]{logPrefix, ready.nat, ready.publicIp});
            natDetectionResult.setNatReady(ready.nat, ready.publicIp);
            if (natDetectionResult.isReady()) {
                Pair<NatedTrait, InetAddress> result = natDetectionResult.getResult();
                trigger(new NatReady(result.getValue0(), result.getValue1()), natDetection);
            }
        }
    };

    private void finalise() {
        Pair<NatedTrait, InetAddress> result = natDetectionResult.getResult();
        trigger(new NatReady(result.getValue0(), result.getValue1()), natDetection);
    }

    public static class NatDetectionInit extends Init<NatDetectionComp> {

        public final BasicAddress privateAdr;
        public final StunClientComp.StunClientConfig scConfig;
        public final Pair<Integer, Integer> scPorts;
        public final List<Pair<DecoratedAddress, DecoratedAddress>> stunServers;
        public final SCNetworkHook.Definition scNetworkDefinition;

        public NatDetectionInit(BasicAddress privateAdr, NatInitHelper ntInit, SCNetworkHook.Definition scNetworkDefinition) {
            this.privateAdr = privateAdr;
            this.scConfig = new StunClientComp.StunClientConfig();
            this.scPorts = ntInit.stunClientPorts;
            this.stunServers = ntInit.stunServers;
            this.scNetworkDefinition = scNetworkDefinition;
        }
    }
}
