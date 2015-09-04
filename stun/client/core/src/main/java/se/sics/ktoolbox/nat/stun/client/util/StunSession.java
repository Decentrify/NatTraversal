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
package se.sics.ktoolbox.nat.stun.client.util;

import com.google.common.base.Optional;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import org.javatuples.Pair;
import se.sics.ktoolbox.nat.stun.msg.StunEcho;
import se.sics.nat.network.Nat;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class StunSession {

    private final MsgHandler[][] handlers = new MsgHandler[2][];
    private final Pair<DecoratedAddress, DecoratedAddress>[] maTargets = new Pair[8];

    public final UUID id;
    public Pair<DecoratedAddress, DecoratedAddress> self;
    public Pair<Pair<DecoratedAddress, DecoratedAddress>, Pair<DecoratedAddress, DecoratedAddress>> stunServers;
    public Phase phase;

    private final DecoratedAddress[] echoResps;
    private final Result sessionResult;

    public StunSession(UUID id, Pair<DecoratedAddress, DecoratedAddress> self, Pair<Pair<DecoratedAddress, DecoratedAddress>, Pair<DecoratedAddress, DecoratedAddress>> stunServers) {
        this.id = id;
        this.self = self;
        this.stunServers = stunServers;
        this.phase = new Phase();
        this.echoResps = new DecoratedAddress[8];
        this.sessionResult = new Result();
        setHandlers();
    }

    private void setHandlers() {
        handlers[State.TEST.index] = new MsgHandler[3];
        handlers[State.TEST.index][0] = new Test1();
        handlers[State.TEST.index][1] = new Test2();
        handlers[State.TEST.index][2] = new Test3();
        handlers[State.MA.index] = new MsgHandler[8];
        handlers[State.MA.index][0] = new MA();
        handlers[State.MA.index][1] = new MA();
        handlers[State.MA.index][2] = new MA();
        handlers[State.MA.index][3] = new MA();
        handlers[State.MA.index][4] = new MA();
        handlers[State.MA.index][5] = new MA();
        handlers[State.MA.index][6] = new MA();
        handlers[State.MA.index][7] = new MA();

        maTargets[0] = Pair.with(self.getValue0(), stunServers.getValue0().getValue0());
        maTargets[1] = Pair.with(self.getValue0(), stunServers.getValue0().getValue1());
        maTargets[2] = Pair.with(self.getValue0(), stunServers.getValue1().getValue0());
        maTargets[3] = Pair.with(self.getValue0(), stunServers.getValue1().getValue1());
        maTargets[4] = Pair.with(self.getValue1(), stunServers.getValue0().getValue0());
        maTargets[5] = Pair.with(self.getValue1(), stunServers.getValue0().getValue1());
        maTargets[6] = Pair.with(self.getValue1(), stunServers.getValue1().getValue0());
        maTargets[7] = Pair.with(self.getValue1(), stunServers.getValue1().getValue1());
    }

    public boolean finished() {
        return phase.state.equals(State.SUCCESS) || phase.state.equals(State.FAIL);
    }

    public Result getResult() {
        return sessionResult;
    }

    public Pair<StunEcho.Request, Pair<DecoratedAddress, DecoratedAddress>> next() {
        return handlers[phase.state.index][phase.subPhase].next();
    }

    public void receivedResponse(StunEcho.Response resp, DecoratedAddress src) {
        handlers[phase.state.index][phase.subPhase].receive(resp, src);
    }

    public void timeout() {
        handlers[phase.state.index][phase.subPhase].timeout();
    }

    private void determineMappingPolicy() {
        // if try 0 to 3 have same public address then the mapping is EI
        if (echoResps[0].equals(echoResps[1])
                && echoResps[0].equals(echoResps[2])
                && echoResps[0].equals(echoResps[3])) {
            sessionResult.setMappingPolicy(Nat.MappingPolicy.ENDPOINT_INDEPENDENT);
        } // if try 1 and try 2 are same and try 3 and try 4 are same then
        // the mapping is Host Dependent
        // TODO this isn't working. HD mapping policy is very rare. Ignore for now.
        else if (echoResps[0].equals(echoResps[1]) && echoResps[2].equals(echoResps[3])) {
            sessionResult.setMappingPolicy(Nat.MappingPolicy.HOST_DEPENDENT);
        } // try 1 to try 4 are all different then mapping policy is
        else {
            sessionResult.setMappingPolicy(Nat.MappingPolicy.PORT_DEPENDENT);
        }
    }

    private void determineAllocationPolicy() {
        //TODO Alex - alternate allocation policy?

        // first check for the PP coz alternative policy is difficult to determine
        // not always possible if mapping is EI
        // TODO work on it. i.e. how to determine the alternative allocation policy
        // if the mapping policy is EI
        if (self.getValue0().getPort() == echoResps[0].getPort()
                || self.getValue1().getPort() == echoResps[4].getPort()) {
            sessionResult.setAllocationPolicy(Nat.AllocationPolicy.PORT_PRESERVATION);
        } else {
            List<Pair<DecoratedAddress, DecoratedAddress>> list = new ArrayList<Pair<DecoratedAddress, DecoratedAddress>>();
            switch (sessionResult.mappingPolicy.get()) {
                case ENDPOINT_INDEPENDENT: {
                    list.add(Pair.with(echoResps[0], echoResps[4]));
                }
                break;
                case HOST_DEPENDENT: {
                    list.add(Pair.with(echoResps[0], echoResps[3]));
                    list.add(Pair.with(echoResps[3], echoResps[5]));
                    list.add(Pair.with(echoResps[5], echoResps[7]));
                }
                break;
                case PORT_DEPENDENT: {
                    list.add(Pair.with(echoResps[0], echoResps[1]));
                    list.add(Pair.with(echoResps[1], echoResps[2]));
                    list.add(Pair.with(echoResps[2], echoResps[3]));
                    list.add(Pair.with(echoResps[3], echoResps[4]));
                    list.add(Pair.with(echoResps[4], echoResps[5]));
                    list.add(Pair.with(echoResps[5], echoResps[6]));
                    list.add(Pair.with(echoResps[6], echoResps[7]));
                }
                break;
                default:
                    assert false;
            }
            int ret = checkContiguity(list);
            if (ret == -1) {
                sessionResult.setAllocationPolicy(Nat.AllocationPolicy.RANDOM);
            } else {
                sessionResult.setAllocationPolicy(Nat.AllocationPolicy.PORT_CONTIGUITY);
                sessionResult.setDelta(ret);
            }
        }
    }

    /**
     * return -1 if the port allocation policy is random return / positive
     * number that is the delta i.e. port increment number
     */
    private int checkContiguity(List<Pair<DecoratedAddress, DecoratedAddress>> list) {
        // set minDelta to be an arbitrarily high number. it will be decreased to
        // the minDelta observed over all TriesPairs.
        int minDelta = 1000;
        int tolerance = 50;
        Iterator<Pair<DecoratedAddress, DecoratedAddress>> it = list.iterator();
        while (it.hasNext()) {
            Pair<DecoratedAddress, DecoratedAddress> tPair = it.next();
            int localPort = tPair.getValue0().getPort();
            int natPort = tPair.getValue1().getPort();
            int difference = Math.abs(localPort - natPort);
            if (difference > tolerance) {
                return -1;
            } else {
                if (difference < minDelta) {
                    minDelta = difference;
                }
            }
        }
        return minDelta;
    }

    public static interface MsgHandler {

        public Pair<StunEcho.Request, Pair<DecoratedAddress, DecoratedAddress>> next();

        public void receive(StunEcho.Response resp, DecoratedAddress src);

        public void timeout();
    }

    public class Test1 implements MsgHandler {

        @Override
        public Pair<StunEcho.Request, Pair<DecoratedAddress, DecoratedAddress>> next() {
            Pair<DecoratedAddress, DecoratedAddress> routing = Pair.with(self.getValue0(), stunServers.getValue0().getValue0());
            return Pair.with(new StunEcho.Request(UUID.randomUUID(), id, StunEcho.Type.SIP_SP, null), routing);
        }

        @Override
        public void receive(StunEcho.Response resp, DecoratedAddress src) {
            sessionResult.setPublicIp(resp.observed.get().getIp());
            echoResps[0] = resp.observed.get(); //we use test1 msg as MA0
            phase.setState(State.TEST, 1);
        }

        @Override
        public void timeout() {
            sessionResult.setNatState(NatState.UDP_BLOCKED);
            phase.setState(State.SUCCESS, 0);
            sessionResult.success();
        }
    }

    public class Test2 implements MsgHandler {

        @Override
        public Pair<StunEcho.Request, Pair<DecoratedAddress, DecoratedAddress>> next() {
            assert echoResps[0] != null;
            Pair<DecoratedAddress, DecoratedAddress> routing = Pair.with(self.getValue0(), stunServers.getValue0().getValue0());
            return Pair.with(new StunEcho.Request(UUID.randomUUID(), id, StunEcho.Type.DIP_DP, echoResps[0]), routing);
        }

        @Override
        public void receive(StunEcho.Response resp, DecoratedAddress src) {
            if (self.getValue0().getIp().equals(echoResps[0].getIp())) {
                sessionResult.setNatState(NatState.OPEN);
                phase.setState(State.SUCCESS, 0);
                sessionResult.success();
            } else {
                sessionResult.setNatState(NatState.NAT);
                sessionResult.setFilterPolicy(Nat.FilteringPolicy.ENDPOINT_INDEPENDENT);
                phase.setState(State.MA, 0);
            }
        }

        @Override
        public void timeout() {
            if (self.getValue0().getIp().equals(echoResps[0].getIp())) {
                sessionResult.setNatState(NatState.FIREWALL);
                phase.setState(State.SUCCESS, 0);
                sessionResult.success();
            } else {
                phase.setState(State.TEST, 2);
            }
        }
    }

    public class Test3 implements MsgHandler {

        @Override
        public Pair<StunEcho.Request, Pair<DecoratedAddress, DecoratedAddress>> next() {
            Pair<DecoratedAddress, DecoratedAddress> routing = Pair.with(self.getValue0(), stunServers.getValue0().getValue0());
            return Pair.with(new StunEcho.Request(UUID.randomUUID(), id, StunEcho.Type.SIP_DP, echoResps[0]), routing);
        }

        @Override
        public void receive(StunEcho.Response resp, DecoratedAddress src) {
            sessionResult.setNatState(NatState.NAT);
            sessionResult.setFilterPolicy(Nat.FilteringPolicy.HOST_DEPENDENT);
            phase.setState(State.MA, 0);
        }

        @Override
        public void timeout() {
            sessionResult.setNatState(NatState.NAT);
            sessionResult.setFilterPolicy(Nat.FilteringPolicy.PORT_DEPENDENT);
            phase.setState(State.MA, 0);
        }

    }

    public class MA implements MsgHandler {

        @Override
        public Pair<StunEcho.Request, Pair<DecoratedAddress, DecoratedAddress>> next() {
            return Pair.with(new StunEcho.Request(UUID.randomUUID(), id, StunEcho.Type.SIP_SP, null), maTargets[phase.subPhase]);
        }

        @Override
        public void receive(StunEcho.Response resp, DecoratedAddress src) {
            echoResps[phase.subPhase] = resp.observed.get();
            if (phase.subPhase == 7) {
                phase.setState(State.SUCCESS, 0);
                determineMappingPolicy();
                determineAllocationPolicy();
                sessionResult.success();
            } else {
                phase.setState(State.MA, phase.subPhase + 1);
            }
        }

        @Override
        public void timeout() {
            phase.setState(State.FAIL, 0);
            sessionResult.fail("mapping allocation timeout");
        }
    }

    public static enum State {

        TEST(0), MA(1), SUCCESS(2), FAIL(3);
        final int index;

        State(int index) {
            this.index = index;
        }
    }

    public class Phase {

        public State state;
        public int subPhase;

        Phase() {
            state = State.TEST;
            subPhase = 0;
        }

        public void setState(State state, int subPhase) {
            this.state = state;
            this.subPhase = subPhase;
        }
    }

    public static enum NatState {

        UDP_BLOCKED, FIREWALL, OPEN, NAT
    }

    public static class Result {

        public Optional<NatState> natState;
        public Optional<Nat.FilteringPolicy> filterPolicy;
        public Optional<Nat.MappingPolicy> mappingPolicy;
        public Optional<Nat.AllocationPolicy> allocationPolicy;
        public Optional<Integer> delta;
        public Optional<InetAddress> publicIp;
        public Optional<String> failureDescription;

        private Result() {
            this.natState = Optional.absent();
            this.filterPolicy = Optional.absent();
            this.mappingPolicy = Optional.absent();
            this.allocationPolicy = Optional.absent();
            this.delta = Optional.absent();
            this.publicIp = Optional.absent();
            this.failureDescription = Optional.of("incomplete");
        }

        private void setNatState(NatState setState) {
            assert !natState.isPresent() && setState != null;
            this.natState = Optional.of(setState);
        }

        private void setPublicIp(InetAddress publicIp) {
            this.publicIp = Optional.of(publicIp);
        }

        private void setFilterPolicy(Nat.FilteringPolicy setPolicy) {
            assert !filterPolicy.isPresent() && setPolicy != null;
            this.filterPolicy = Optional.of(setPolicy);
        }

        private void setMappingPolicy(Nat.MappingPolicy setPolicy) {
            assert !mappingPolicy.isPresent() && setPolicy != null;
            this.mappingPolicy = Optional.of(setPolicy);
        }

        private void setAllocationPolicy(Nat.AllocationPolicy setPolicy) {
            assert !allocationPolicy.isPresent() && setPolicy != null;
            this.allocationPolicy = Optional.of(setPolicy);
        }

        private void setDelta(int setDelta) {
            assert !delta.isPresent() && setDelta > 0;
            this.delta = Optional.of(setDelta);
        }

        private void setFailure(String setDescription) {
            this.failureDescription = Optional.of(setDescription);
        }

        private void success() {
            assert natState.isPresent();
            assert natState.get().equals(NatState.NAT) ? checkNat() : true;
            this.failureDescription = Optional.absent();
        }

        private boolean checkNat() {
            if (filterPolicy.isPresent() && mappingPolicy.isPresent() && allocationPolicy.isPresent() && publicIp.isPresent()) {
                if (allocationPolicy.get().equals(Nat.AllocationPolicy.PORT_CONTIGUITY)) {
                    return delta.isPresent();
                }
                return true;
            }
            return false;
        }

        private void fail(String failureDescription) {
            this.failureDescription = Optional.of(failureDescription);
        }

        public boolean isFailed() {
            return failureDescription.isPresent();
        }
    }
}
