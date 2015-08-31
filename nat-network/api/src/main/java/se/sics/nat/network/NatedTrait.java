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
package se.sics.nat.network;

import java.util.ArrayList;
import java.util.List;
import se.sics.nat.network.Nat.AllocationPolicy;
import se.sics.nat.network.Nat.FilteringPolicy;
import se.sics.nat.network.Nat.MappingPolicy;
import se.sics.nat.network.Nat.Type;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;
import se.sics.p2ptoolbox.util.traits.Trait;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class NatedTrait implements Trait {

    public final Type type;
    public final MappingPolicy mappingPolicy;
    public final AllocationPolicy allocationPolicy;
    public final FilteringPolicy filteringPolicy;
    public final long bindingTimeout;
    public final List<DecoratedAddress> parents;

    public static NatedTrait open() {
        return new NatedTrait(Type.OPEN, null, null, null, 0, new ArrayList<DecoratedAddress>());
    }

    public static NatedTrait nated(MappingPolicy mappingPolicy, AllocationPolicy allocationPolicy, 
            FilteringPolicy filteringPolicy, long bindingTimeout, ArrayList<DecoratedAddress> parents) {
        assert mappingPolicy != null;
        assert allocationPolicy != null;
        assert filteringPolicy != null;
        assert bindingTimeout > 0;
        return new NatedTrait(Type.NAT, mappingPolicy, allocationPolicy, filteringPolicy, bindingTimeout, parents);
    }

    private NatedTrait(Type type, MappingPolicy mappingPolicy, AllocationPolicy allocationPolicy, 
            FilteringPolicy filteringPolicy, long bindingTimeout, List<DecoratedAddress> parents) {
        this.type = type;
        this.mappingPolicy = mappingPolicy;
        this.allocationPolicy = allocationPolicy;
        this.filteringPolicy = filteringPolicy;
        this.bindingTimeout = bindingTimeout;
        this.parents = parents;
    }

    @Override
    public String toString() {
        switch (type) {
            case OPEN:
                return type.code;
            case NAT:
                return type.code + "-" + mappingPolicy.code + "-" + allocationPolicy.code + "-" + filteringPolicy.code;
            case UDP_BLOCKED:
                return type.code;
            case UPNP:
                return type.code;
            default:
                return "unknown";
        }

    }
}
