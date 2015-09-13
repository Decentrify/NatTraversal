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

import java.net.InetAddress;
import java.util.Map;
import java.util.Set;
import se.sics.p2ptoolbox.util.nat.Nat;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public abstract class FilterPolicyImpl {

    public static FilterPolicyImpl create(Nat.FilteringPolicy filterPolicy) {
        switch (filterPolicy) {
            case ENDPOINT_INDEPENDENT:
                return new EIFilter();
            case HOST_DEPENDENT:
                return new HDFilter();
            case PORT_DEPENDENT:
                return new PDFilter();
            default:
                throw new RuntimeException("unhandled filter policy:" + filterPolicy);
        }
    }

    //******************************INTERFACE***********************************
    public abstract boolean allow(BasicAddress target, Map<InetAddress, Set<Integer>> allowed);
    //**************************************************************************

    public static class EIFilter extends FilterPolicyImpl {

        @Override
        public boolean allow(BasicAddress target, Map<InetAddress, Set<Integer>> allowed) {
            return true;
        }
    }

    public static class HDFilter extends FilterPolicyImpl {

        @Override
        public boolean allow(BasicAddress target, Map<InetAddress, Set<Integer>> allowed) {
            return allowed.containsKey(target.getIp());
        }
    }

    public static class PDFilter extends FilterPolicyImpl {
        @Override
        public boolean allow(BasicAddress target, Map<InetAddress, Set<Integer>> allowed) {
            Set<Integer> allowedPorts = allowed.get(target.getIp());
            if(allowedPorts == null) {
                return false;
            }
            return allowedPorts.contains(target.getPort());
        }
    }
}
