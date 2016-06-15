package se.sics.nat.detection.junk;

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
//package se.sics.nat.detection;
//
//import com.google.common.base.Optional;
//import java.net.InetAddress;
//import org.javatuples.Pair;
//import se.sics.p2ptoolbox.util.nat.NatedTrait;
//import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;
//
///**
// * @author Alex Ormenisan <aaor@kth.se>
// */
//public class NatDetectionStatus {
//
//    public static class Phase1 {
//        public final DecoratedAddress privateAdr;
//        
//        public Phase1(DecoratedAddress privateAdr) {
//            this.privateAdr = privateAdr;
//        }
//    }
//
//    public static class Phase2 {
//        
//        public NatedTrait nat;
//        public Optional<InetAddress> publicIp;
//
//        /**
//         * @param nat
//         * @param publicIp - optional - missing only if nat - udpBlocked
//         */
//        public Phase2(Pair<NatedTrait, Optional<InetAddress>> result) {
//            this.nat = result.getValue0();
//            this.publicIp = result.getValue1();
//        }
//    }
//}
