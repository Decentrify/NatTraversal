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

import com.google.common.base.Optional;
import org.javatuples.Pair;
import se.sics.nat.network.Nat;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public abstract class MappingPolicyImpl {

    public static MappingPolicyImpl create(Nat.MappingPolicy mappingPolicy) {
        switch(mappingPolicy) {
            case ENDPOINT_INDEPENDENT: return new EIPolicy();
            default: throw new RuntimeException("unhandled mapping policy case");
        }
    }
    //************************INTERFACE*****************************************
    public abstract Optional<Integer> usePort(BasicAddress src, BasicAddress dst, PortMappings portMappings);
    //**************************************************************************

    public static class EIPolicy extends MappingPolicyImpl {
        @Override
        public Optional<Integer> usePort(BasicAddress src, BasicAddress dst, PortMappings portMappings) {
            return portMappings.getPublicPort(Pair.with(src.getIp(), src.getPort()));
        }
    }
}
