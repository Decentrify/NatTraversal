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

package se.sics.nat.util;

import se.sics.p2ptoolbox.util.nat.Nat;
import se.sics.p2ptoolbox.util.nat.NatedTrait;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class Feasibility {
    public static enum State {
        INITIATE, TARGET_INITIATE, UNFEASIBLE
    }
    public static State simpleHolePunching(DecoratedAddress self, DecoratedAddress target) {
        if(NatedTrait.isOpen(self)) {
            return State.INITIATE;
        } else {
            if(target.getTrait(NatedTrait.class).filteringPolicy.equals(Nat.FilteringPolicy.ENDPOINT_INDEPENDENT)) {
                if(self.getTrait(NatedTrait.class).mappingPolicy.equals(Nat.MappingPolicy.ENDPOINT_INDEPENDENT)) {
                    return State.INITIATE;
                }
            }
        }
        return State.UNFEASIBLE;
    }
}