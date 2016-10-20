package se.sics.nat.util.junk;

///*
// * Copyright (C) 2009 Swedish Institute of Computer Science (SICS) Copyright (C)
// * 2009 Royal Institute of Technology (KTH)
// *
// * NatTraverser is free software; you can redistribute it and/or
// * modify it under the terms of the GNU General Public License
// * as published by the Free Software Foundation; either version 2
// * of the License, or (at your option) any later version.
// *
// * This program is distributed in the hope that it will be useful,
// * but WITHOUT ANY WARRANTY; without even the implied warranty of
// * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// * GNU General Public License for more details.
// *
// * You should have received a copy of the GNU General Public License
// * along with this program; if not, write to the Free Software
// * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
// */
//package se.sics.nat.util;
//
//import se.sics.p2ptoolbox.util.nat.Nat;
//import se.sics.p2ptoolbox.util.nat.NatedTrait;
//import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;
//
///**
// * @author Alex Ormenisan <aaor@kth.se>
// */
//public class NatTraverserFeasibility {
//
//    public static enum State {
//        DIRECT, SHP, UNFEASIBLE
//    }
//    
//    public static boolean direct(DecoratedAddress self, DecoratedAddress target) {
//        return check(self, target).equals(State.DIRECT);
//    }
//
//    public static State check(DecoratedAddress self, DecoratedAddress target) {
//        if (NatedTrait.isOpen(target)) {
//            return State.DIRECT;
//        }
//        if (!target.getTrait(NatedTrait.class).mappingPolicy.equals(Nat.MappingPolicy.ENDPOINT_INDEPENDENT)) {
//            /**
//             * if the target mapping policy is not EI, with high probability the
//             * port that I have might not still be open. The target opens
//             * multiple ports on its NAT. Go through its parent to get correct
//             * port TODO Alex - investigate
//             */
//            if (NatedTrait.isOpen(self)) {
//                return State.SHP;
//            }
//            if (self.getTrait(NatedTrait.class).filteringPolicy.equals(Nat.FilteringPolicy.ENDPOINT_INDEPENDENT)) {
//                return State.SHP;
//            }
//            return State.UNFEASIBLE;
//        }
//        //target ma = EI
//        if (target.getTrait(NatedTrait.class).filteringPolicy.equals(Nat.FilteringPolicy.ENDPOINT_INDEPENDENT)) {
//            return State.DIRECT;
//        }
//        //target ma = EI, fp < EI
//        if (NatedTrait.isOpen(self)) {
//            return State.SHP;
//        }
//        if (self.getTrait(NatedTrait.class).filteringPolicy.equals(Nat.FilteringPolicy.ENDPOINT_INDEPENDENT)) {
//            return State.SHP;
//        }
//        //target ma = EI, fp < EI
//        //self fp < EI
//        return State.UNFEASIBLE;
//    }
//}
