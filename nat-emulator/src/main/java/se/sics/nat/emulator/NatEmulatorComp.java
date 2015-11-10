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
import java.util.Set;
import java.util.UUID;
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
import se.sics.kompics.timer.CancelPeriodicTimeout;
import se.sics.kompics.timer.CancelTimeout;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;
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

    private final NatEmulatorKCWrapper config;
    private final InetAddress selfIp;
    private final int selfId;

    private final NatedTrait natType;
    //only get initialized if natType is NAT************************************
    private MappingPolicyImpl mappingPolicy = null;
    private AllocationPolicyImpl allocationPolicy = null;
    private FilterPolicyImpl filterPolicy = null;
    private PortMappings udpMappings = null;
    //**************************************************************************

    private UUID stateCheckTid;

    public NatEmulatorComp(NatEmulatorInit init) {
        config = init.config;
        natType = init.natType;
        selfIp = init.publicIp;
        selfId = init.selfId;
        logPrefix = "<nid:" + selfId + ",nat:" + natType.toString() + "> ";
        LOG.info("{}initiating", logPrefix);

        if (natType.type.equals(Nat.Type.NAT)) {
            this.mappingPolicy = MappingPolicyImpl.create(natType.mappingPolicy);
            this.allocationPolicy = AllocationPolicyImpl.create(natType.allocationPolicy, init.seed);
            this.filterPolicy = FilterPolicyImpl.create(natType.filteringPolicy);
            this.udpMappings = new PortMappings();
        }

        subscribe(handleStart, control);
        subscribe(handleStateCheck, timer);
        subscribe(handleConnectionTimeout, timer);
        subscribe(handleIncoming, network);
        subscribe(handleOutgoing, localNetwork);
    }

    Handler handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            LOG.info("{}starting", logPrefix);
            if (natType.type.equals(Nat.Type.NAT)) {
                scheduleInternalStateCheck();
            }
        }
    };

    Handler handleStateCheck = new Handler<PeriodicStateCheck>() {
        @Override
        public void handle(PeriodicStateCheck event) {
            LOG.info("{}port mappings:{}", logPrefix, udpMappings.toString());
        }
    };
    //*************************************************************************
    Handler handleConnectionTimeout = new Handler<ConnectionTimeout>() {
        @Override
        public void handle(ConnectionTimeout timeout) {
            LOG.debug("{}connection timeout nat port:{} outAdr:{}",
                    new Object[]{logPrefix, timeout.natPort, timeout.outAdr});
            udpMappings.cleanConnection(timeout.natPort, timeout.outAdr, timeout.getTimeoutId());
        }
    };

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

            Optional<BasicAddress> inBaseAdr
                    = receiveAdr(container.getDestination().getPort(), container.getSource().getBase(), container.getProtocol());
            if (!inBaseAdr.isPresent()) {
                LOG.warn("{}unable to forward incoming msg - filtered...", logPrefix);
                return;
            }

            DecoratedAddress inAdr = container.getDestination().changeBase(inBaseAdr.get());
            DecoratedHeader<DecoratedAddress> fHeader
                    = container.getHeader().changeBasicHeader(new BasicHeader(container.getSource(), inAdr, container.getProtocol()));
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
                publicPort = sendPort(container.getSource().getBase(), container.getDestination().getBase());
            } catch (AllocationPolicyImpl.PortAllocationException ex) {
                LOG.warn("{}unable to forward outgoing msg - cannot allocate port:{}",
                        new Object[]{logPrefix, container.getSource()});
                return;
            }

            DecoratedAddress sendingAddress = container.getSource().changeBase(
                    new BasicAddress(selfIp, publicPort, selfId));

            DecoratedHeader<DecoratedAddress> fHeader = container.getHeader().changeBasicHeader(
                    new BasicHeader(sendingAddress, container.getDestination(), Transport.UDP));
            BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Object> fMsg
                    = new BasicContentMsg(fHeader, container.getContent());
            LOG.debug("{}forwarding outgoing:{} from:{} to:{}",
                    new Object[]{logPrefix, container.getContent(), fMsg.getHeader().getSource().getBase(), fMsg.getHeader().getDestination().getBase()});
            trigger(fMsg, network);

            BasicAddress inAdr = container.getSource().getBase();
            BasicAddress outAdr = fMsg.getDestination().getBase();
            Optional<UUID> connectionTid = udpMappings.send(publicPort, inAdr, outAdr,
                    scheduleConnectionTimeout(publicPort, outAdr));
            if (connectionTid.isPresent()) {
                cancelConnectionTimeout(connectionTid.get());
            }
        }
    };

    private Integer sendPort(BasicAddress inAdr, BasicAddress outAdr) throws AllocationPolicyImpl.PortAllocationException {
        Optional<Integer> publicPort = mappingPolicy.usePort(inAdr, outAdr, udpMappings);
        if (!publicPort.isPresent()) {
            //no state to clean if exception is thrown by allocatePort, safe to just propagate
            publicPort = Optional.of(allocationPolicy.allocatePort(inAdr.getPort(), udpMappings.getAllocatedPorts()));
            LOG.info("{}mapping private:{} public:{}", new Object[]{logPrefix, inAdr.getPort(), publicPort.get()});
        }
        return publicPort.get();
    }

    private Optional<BasicAddress> receiveAdr(Integer publicPort, BasicAddress outAdr, Transport protocol) {
        PortMappings portMappings;
        switch (protocol) {
            case UDP:
                portMappings = udpMappings;
                break;
            default:
                throw new RuntimeException("unhandled protocol:" + protocol);
        }

        Optional<Pair<BasicAddress, Set<BasicAddress>>> portMapping = portMappings.getMapping(publicPort);
        if (!portMapping.isPresent()) {
            LOG.warn("{}no mapping for public port:{}, don't know where to forward, dropping",
                    new Object[]{logPrefix, publicPort});
            return Optional.absent();
        }
        if (filterPolicy.allow(outAdr, portMapping.get().getValue1())) {
            return Optional.of(portMapping.get().getValue0());
        } else {
            LOG.warn("{}filter policy:{} does not allow this target:{} through public port:{}",
                    new Object[]{logPrefix, filterPolicy.policy, outAdr, publicPort});
            return Optional.absent();
        }
    }

    public static class NatEmulatorInit extends Init<NatEmulatorComp> {

        public final NatEmulatorKCWrapper config;
        public final long seed;
        public final int selfId;
        public final InetAddress publicIp;
        public final NatedTrait natType;

        public NatEmulatorInit(long seed, NatedTrait natType, InetAddress publicIp, int selfId) {
            this.config = new NatEmulatorKCWrapper();
            this.seed = seed;
            this.selfId = selfId;
            this.publicIp = publicIp;
            this.natType = natType;
        }
    }

    private UUID scheduleConnectionTimeout(int natPort, BasicAddress outAdr) {
        ScheduleTimeout st = new ScheduleTimeout(config.connectionTimeout);
        ConnectionTimeout sc = new ConnectionTimeout(st, natPort, outAdr);
        st.setTimeoutEvent(sc);
        trigger(st, timer);
        return sc.getTimeoutId();
    }

    private void cancelConnectionTimeout(UUID tid) {
        CancelTimeout ct = new CancelTimeout(tid);
        trigger(ct, timer);
    }

    public class ConnectionTimeout extends Timeout {

        public final int natPort;
        public final BasicAddress outAdr;

        public ConnectionTimeout(ScheduleTimeout request, int natPort, BasicAddress outAdr) {
            super(request);
            this.natPort = natPort;
            this.outAdr = outAdr;
        }

        @Override
        public String toString() {
            return "CONNECTION_TIMEOUT";
        }
    }

    private void scheduleInternalStateCheck() {
        if (stateCheckTid != null) {
            LOG.warn("{}double starting internal state check timeout", logPrefix);
            return;
        }
        SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(config.stateCheckTimeout, config.stateCheckTimeout);
        PeriodicStateCheck sc = new PeriodicStateCheck(spt);
        spt.setTimeoutEvent(sc);
        stateCheckTid = sc.getTimeoutId();
        trigger(spt, timer);
    }

    private void cancelInternalStateCheck() {
        if (stateCheckTid == null) {
            LOG.warn("{}double stopping internal state check timeout", logPrefix);
            return;
        }
        CancelPeriodicTimeout cpt = new CancelPeriodicTimeout(stateCheckTid);
        stateCheckTid = null;
        trigger(cpt, timer);
    }

    public static class PeriodicStateCheck extends Timeout {

        public PeriodicStateCheck(SchedulePeriodicTimeout spt) {
            super(spt);
        }
    }
}
