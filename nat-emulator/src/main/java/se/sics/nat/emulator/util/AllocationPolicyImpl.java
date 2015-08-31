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
package se.sics.nat.emulator.util;

import java.util.Random;
import java.util.Set;
import se.sics.nat.network.Nat;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public abstract class AllocationPolicyImpl {

    static int MIN_PORT = 10000;
    static int MAX_PORT = 65000;

    static int maxPorts() {
        return MAX_PORT - MIN_PORT;
    }

    public static AllocationPolicyImpl create(Nat.AllocationPolicy allocationPolicy, long seed) {
        switch (allocationPolicy) {
            case PORT_PRESERVATION:
                return new PortPreservation();
            case PORT_CONTIGUITY:
                return new PortContiguity();
            case RANDOM:
                return new PortRandom(seed);
            default:
                throw new RuntimeException("unhandled allocation policy:" + allocationPolicy);
        }
    }

    //***************************INTERFACE**************************************
    public abstract Integer allocatePort(int privatePort, Set<Integer> allocatedPorts) throws PortAllocationException;
    //**************************************************************************

    public static class PortAllocationException extends Exception {

        public PortAllocationException(String msg) {
            super(msg);
        }
    }

    public static class PortPreservation extends AllocationPolicyImpl {

        @Override
        public Integer allocatePort(int privatePort, Set<Integer> allocatedPorts) throws PortAllocationException {
            if (allocatedPorts.contains(privatePort)) {
                throw new PortAllocationException("port is taken - port preservation");
            }
            return privatePort;
        }
    }

    public static class PortContiguity extends AllocationPolicyImpl {

        private int nextPort = MIN_PORT;

        @Override
        public Integer allocatePort(int privatePort, Set<Integer> allocatedPorts) throws PortAllocationException {
            if (allocatedPorts.size() >= maxPorts()) {
                throw new PortAllocationException("out of ports");
            }
            do {
                nextPort++;
                if (nextPort > MAX_PORT) {
                    nextPort = MIN_PORT;
                }
            } while (allocatedPorts.contains(nextPort));
            return nextPort;
        }
    }

    //TODO Alex - write test to be sure you do not get stuck in allocatePort when almost all ports are taken.
    public static class PortRandom extends AllocationPolicyImpl {

        private Random rand;

        public PortRandom(long seed) {
            this.rand = new Random(seed);
        }

        @Override
        public Integer allocatePort(int privatePort, Set<Integer> allocatedPorts) throws PortAllocationException {
            if (allocatedPorts.size() >= maxPorts()) {
                throw new PortAllocationException("out of ports");
            }
            int nextPort;
            do {
                nextPort = rand.nextInt(MAX_PORT + 1);
            } while (nextPort < MIN_PORT || allocatedPorts.contains(nextPort));
            return nextPort;
        }
    }
}
