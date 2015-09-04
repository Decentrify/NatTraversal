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
package se.sics.nat.hp.common.msg;

import java.util.UUID;
import org.javatuples.Pair;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class SimpleHolePunching {

    public static class Relay extends SHPMsg {

        public final DecoratedAddress relayTo;

        public Relay(Pair<UUID, UUID> msgId, DecoratedAddress relayTarget) {
            super(msgId);
            this.relayTo = relayTarget;
        }
        
        public Initiate answer(DecoratedAddress connectTo) {
            return new Initiate(Pair.with(msgId.getValue0(), UUID.randomUUID()), connectTo);
        }
    }

    public static class Initiate extends SHPMsg {

        public final DecoratedAddress connectTo;

        private Initiate(Pair<UUID, UUID> msgId, DecoratedAddress connectTo) {
            super(msgId);
            this.connectTo = connectTo;
        }
        
        public Ping answer() {
            return new Ping(Pair.with(msgId.getValue0(), UUID.randomUUID()));
        }
    }

    public static class Ping extends SHPMsg {

        private Ping(Pair<UUID, UUID> msgId) {
            super(msgId);
        }

        public Pong answer() {
            return new Pong(Pair.with(msgId.getValue0(), UUID.randomUUID()));
        }
        
        public Ready ready(DecoratedAddress observed) {
            return new Ready(Pair.with(msgId.getValue0(), UUID.randomUUID()), observed);
        }
    }

    public static class Pong extends SHPMsg {

        private Pong(Pair<UUID, UUID> msgId) {
            super(msgId);
        }
        
        public Ready answer(DecoratedAddress observed) {
            return new Ready(Pair.with(msgId.getValue0(), UUID.randomUUID()), observed);
        }
    }

    public static class Ready extends SHPMsg {

        public final DecoratedAddress observed;

        private Ready(Pair<UUID, UUID> msgId, DecoratedAddress observed) {
            super(msgId);
            this.observed = observed;
        }
    }
}
