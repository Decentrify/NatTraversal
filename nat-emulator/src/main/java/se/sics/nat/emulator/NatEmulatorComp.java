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
package se.sics.nat.emulator;

import com.google.common.base.Optional;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.Stop;
import se.sics.kompics.network.Network;
import se.sics.kompics.network.Transport;
import se.sics.kompics.timer.Timer;
import se.sics.ktoolbox.nat.network.Nat;
import se.sics.nat.emulator.util.AllocationPolicyImpl;
import se.sics.nat.emulator.util.FilterPolicyImpl;
import se.sics.nat.emulator.util.MappingPolicyImpl;
import se.sics.nat.emulator.util.PortMappings;
import se.sics.p2ptoolbox.util.network.ContentMsg;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;
import se.sics.p2ptoolbox.util.network.impl.BasicContentMsg;
import se.sics.p2ptoolbox.util.network.impl.BasicHeader;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;
import se.sics.p2ptoolbox.util.network.impl.DecoratedHeader;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class NatEmulatorComp extends ComponentDefinition {

    private static final Logger LOG = LoggerFactory.getLogger(NatEmulatorComp.class);
    private String logPrefix = "";

    private Positive<Network> localNetwork = requires(Network.class);
    private Positive<Timer> timer = requires(Timer.class);
    private Negative<Network> network = provides(Network.class);

    private final InetAddress selfIp;
    private final int selfId;

    private final Nat natType;
    //only get initialized if natType is NAT************************************
    private MappingPolicyImpl mappingPolicy = null;
    private AllocationPolicyImpl allocationPolicy = null;
    private FilterPolicyImpl filterPolicy = null;
    private Map<Pair<InetAddress, Integer>, DecoratedAddress> addresses = null;
    private PortMappings udpMappings = null;
    //**************************************************************************

    public NatEmulatorComp(NatEmulatorInit init) {
        this.natType = init.natType;
        this.selfIp = init.selfIp;
        this.selfId = init.selfId;
        logPrefix = selfIp.toString() + "<" + natType.toString() + "> ";
        LOG.info("{}initiating", logPrefix);

        if (natType.type.equals(Nat.Type.NAT)) {
            this.mappingPolicy = MappingPolicyImpl.create(natType.mappingPolicy);
            this.allocationPolicy = AllocationPolicyImpl.create(natType.allocationPolicy, init.seed);
            this.filterPolicy = FilterPolicyImpl.create(natType.filteringPolicy);
            this.addresses = new HashMap<Pair<InetAddress, Integer>, DecoratedAddress>();
            this.udpMappings = new PortMappings();
        }

        subscribe(handleStart, control);
        subscribe(handleStop, control);
        subscribe(handleIncoming, network);
        subscribe(handleOutgoing, localNetwork);
    }

    Handler handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            LOG.info("{}starting", logPrefix);
        }
    };
    Handler handleStop = new Handler<Stop>() {
        @Override
        public void handle(Stop event) {
            LOG.info("{}stopping", logPrefix);
        }
    };
    //*************************************************************************
    Handler handleIncoming = new Handler<ContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Object>>() {
        @Override
        public void handle(ContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Object> msg) {
            LOG.trace("{}received incoming:{}", logPrefix, msg);
            if (natType.type.equals(Nat.Type.OPEN)) {
                LOG.debug("{}forwarding msg:{} network to local", logPrefix, msg);
                trigger(msg, localNetwork);
                return;
            }

            Integer publicPort;
            try {
                publicPort = send(msg.getHeader().getSource(), msg.getHeader().getDestination().getBase(), msg.getHeader().getProtocol());
            } catch (AllocationPolicyImpl.PortAllocationException ex) {
                LOG.warn("{}unable to forward msg:{} local to network - dropping...", logPrefix, msg);
                return;
            }
            DecoratedAddress self = new DecoratedAddress(new BasicAddress(selfIp, publicPort, selfId));
            DecoratedHeader<DecoratedAddress> responseHeader = new DecoratedHeader(new BasicHeader(self, msg.getHeader().getDestination(), Transport.UDP), null, null);
            ContentMsg fMsg = new BasicContentMsg(responseHeader, msg.getContent());
            LOG.debug("{}forwarding msg:{} local to network", logPrefix, fMsg);
            trigger(fMsg, localNetwork);
        }
    };

    Handler handleOutgoing = new Handler<ContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Object>>() {
        @Override
        public void handle(ContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Object> msg) {
            LOG.trace("{}received outgoing:{}", logPrefix, msg);
            if (natType.type.equals(Nat.Type.OPEN)) {
                LOG.debug("{}forwarding msg:{} local to network", logPrefix, msg);
                trigger(msg, network);
                return;
            }

            Optional<DecoratedAddress> privateAddress = receive(msg.getHeader().getDestination().getPort(), msg.getHeader().getSource().getBase(), msg.getHeader().getProtocol());
            if (!privateAddress.isPresent()) {
                LOG.warn("{}unable to forward msg:{} network to local - dropping...", logPrefix, msg);
                return;
            }
            DecoratedHeader<DecoratedAddress> responseHeader = new DecoratedHeader(new BasicHeader(msg.getHeader().getSource(), privateAddress.get(), Transport.UDP), null, null);
            ContentMsg fMsg = new BasicContentMsg(responseHeader, msg.getContent());
            LOG.debug("{}forwarding msg:{} local to network", logPrefix, fMsg);
            trigger(fMsg, network);
        }
    };

    private Integer send(DecoratedAddress src, BasicAddress dst, Transport protocol) throws AllocationPolicyImpl.PortAllocationException {
        PortMappings portMappings;
        switch (protocol) {
            case UDP:
                portMappings = udpMappings;
                break;
            default:
                throw new RuntimeException("unhandled protocol:" + protocol);
        }

        Optional<Integer> publicPort = mappingPolicy.usePort(src.getBase(), dst, portMappings);
        if (!publicPort.isPresent()) {
            //no state to clean if exception is thrown by allocatePort, safe to just propagate
            publicPort = Optional.of(allocationPolicy.allocatePort(src.getPort(), portMappings.getAllocatedPorts()));
            portMappings.map(publicPort.get(), Pair.with(src.getBase().getIp(), src.getBase().getPort()), Pair.with(dst.getIp(), dst.getPort()));
        }
        addresses.put(Pair.with(src.getBase().getIp(), src.getBase().getPort()), src);
        return publicPort.get();
    }

    private Optional<DecoratedAddress> receive(Integer publicPort, BasicAddress target, Transport protocol) {
        PortMappings portMappings;
        switch (protocol) {
            case UDP:
                portMappings = udpMappings;
                break;
            default:
                throw new RuntimeException("unhandled protocol:" + protocol);
        }

        if (filterPolicy.allow(target, portMappings.getPortActiveConn(publicPort))) {
            return Optional.of(addresses.get(portMappings.getPrivateAddress(publicPort)));
        }
        return Optional.absent();
    }

    public static class NatEmulatorInit extends Init<NatEmulatorComp> {

        public final long seed;
        public final int selfId;
        public final InetAddress selfIp;
        public final Nat natType;

        public NatEmulatorInit(long seed, Nat natType, InetAddress selfIp, int selfId) {
            this.seed = seed;
            this.selfId = selfId;
            this.selfIp = selfIp;
            this.natType = natType;
        }
    }
}
