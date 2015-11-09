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
import se.sics.nat.emulator.util.AllocationPolicyImpl;
import se.sics.nat.emulator.util.FilterPolicyImpl;
import se.sics.nat.emulator.util.MappingPolicyImpl;
import se.sics.nat.emulator.util.PortMappings;
import se.sics.p2ptoolbox.util.nat.Nat;
import se.sics.p2ptoolbox.util.nat.NatedTrait;
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

    private Negative<Network> localNetwork = provides(Network.class);
    private Positive<Timer> timer = requires(Timer.class);
    private Positive<Network> network = requires(Network.class);

    private final InetAddress selfIp;
    private final int selfId;

    private final NatedTrait natType;
    //only get initialized if natType is NAT************************************
    private MappingPolicyImpl mappingPolicy = null;
    private AllocationPolicyImpl allocationPolicy = null;
    private FilterPolicyImpl filterPolicy = null;
    private Map<Pair<InetAddress, Integer>, DecoratedAddress> addresses = null;
    private PortMappings udpMappings = null;
    //**************************************************************************

    public NatEmulatorComp(NatEmulatorInit init) {
        this.natType = init.natType;
        this.selfIp = init.publicIp;
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
    Handler handleIncoming = new Handler<BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Object>>() {
        @Override
        public void handle(BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Object> container) {
            LOG.trace("{}received incoming:{} from:{} on:{}",
                    new Object[]{logPrefix, container.getContent(), container.getSource().getBase(), container.getDestination().getBase()});
            if (natType.type.equals(Nat.Type.OPEN)) {
                LOG.debug("{}open - forwarding incoming", logPrefix);
                trigger(container, localNetwork);
                return;
            }
            if (!container.getProtocol().equals(Transport.UDP)) {
                LOG.error("{}nat emulator does not emulate non UDP trafic");
                throw new RuntimeException("nat emulator does not emulate non UDP trafic");
            }

            Optional<DecoratedAddress> privateAddress
                    = receive(container.getDestination().getPort(), container.getSource().getBase(), container.getProtocol());
            if (!privateAddress.isPresent()) {
                LOG.warn("{}unable to forward incoming msg - filtered...", logPrefix);
                return;
            }

            DecoratedHeader<DecoratedAddress> fHeader
                    = container.getHeader().changeBasicHeader(new BasicHeader(container.getSource(), privateAddress.get(), Transport.UDP));
            ContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Object> fMsg
                    = new BasicContentMsg(fHeader, container.getContent());
            LOG.debug("{}forwarding incoming:{} from:{} to:{}",
                    new Object[]{logPrefix, container.getContent(), fMsg.getHeader().getSource().getBase(), fMsg.getHeader().getDestination().getBase()});
            trigger(fMsg, localNetwork);
        }
    };

    Handler handleOutgoing = new Handler<BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Object>>() {
        @Override
        public void handle(BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Object> container) {

            LOG.trace("{}received outgoing:{} from:{} to:{}",
                    new Object[]{logPrefix, container.getContent(), container.getSource().getBase(), container.getDestination().getBase()});
            if (natType.type.equals(Nat.Type.OPEN)) {
                LOG.debug("{}open - forwarding outgoing", logPrefix);
                trigger(container, network);
                return;
            }
            if (!container.getProtocol().equals(Transport.UDP)) {
                LOG.error("{}nat emulator does not emulate non UDP trafic");
                throw new RuntimeException("nat emulator does not emulate non UDP trafic");
            }

            Integer publicPort;
            try {
                publicPort = send(container.getSource(), container.getDestination().getBase(), container.getProtocol());
            } catch (AllocationPolicyImpl.PortAllocationException ex) {
                LOG.warn("{}unable to forward outgoing msg - cannot allocate port...", logPrefix);
                return;
            }

            DecoratedAddress sendingAddress;
            if (container.getSource().getIp().equals(selfIp)) {
                if (container.getSource().getPort() == publicPort) {
                    sendingAddress = container.getSource();
                } else {
                    LOG.debug("{}changing port", logPrefix);
                    sendingAddress = container.getSource().changePort(publicPort);
                }
            } else {
                LOG.debug("{}changing ip and port", logPrefix);
                sendingAddress = new DecoratedAddress(new BasicAddress(selfIp, publicPort, selfId));
            }

            DecoratedHeader<DecoratedAddress> fHeader = container.getHeader().changeBasicHeader(new BasicHeader(sendingAddress, container.getDestination(), Transport.UDP));
            ContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Object> fMsg
                    = new BasicContentMsg(fHeader, container.getContent());
            LOG.debug("{}forwarding outgoing:{} from:{} to:{}",
                    new Object[]{logPrefix, container.getContent(), fMsg.getHeader().getSource().getBase(), fMsg.getHeader().getDestination().getBase()});
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
            LOG.info("{}mapping private:{} public:{}", new Object[]{logPrefix, src.getPort(), publicPort.get()});
        }
        portMappings.map(publicPort.get(), Pair.with(src.getBase().getIp(), src.getBase().getPort()), Pair.with(dst.getIp(), dst.getPort()));
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
            Pair<InetAddress, Integer> mapping = portMappings.getPrivateAddress(publicPort);
           if(mapping == null) {
                LOG.warn("{}no mapping for public port:{}, don't know where to forward, dropping",
                        new Object[]{logPrefix, publicPort});
                return Optional.absent();
            }
            return Optional.of(addresses.get(portMappings.getPrivateAddress(publicPort)));
        } else {
            LOG.warn("{}filter policy:{} does not allow this target:{} through public port:{}",
                    new Object[]{logPrefix, filterPolicy.policy, target, publicPort});
            return Optional.absent();
        }
    }

    public static class NatEmulatorInit extends Init<NatEmulatorComp> {

        public final long seed;
        public final int selfId;
        public final InetAddress publicIp;
        public final NatedTrait natType;

        public NatEmulatorInit(long seed, NatedTrait natType, InetAddress publicIp, int selfId) {
            this.seed = seed;
            this.selfId = selfId;
            this.publicIp = publicIp;
            this.natType = natType;
        }
    }
}
