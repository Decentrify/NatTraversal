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
package se.sics.nat.stun.msg.server;

import com.google.common.base.Optional;
import java.util.UUID;
import org.javatuples.Pair;
import se.sics.nat.stun.msg.StunMsg;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class StunPartner {

    public static class Request implements StunMsg {

        public final UUID id;
        public final Pair<DecoratedAddress, DecoratedAddress> partnerAdr;

        public Request(UUID id, Pair<DecoratedAddress, DecoratedAddress> partnerAdr) {
            this.id = id;
            this.partnerAdr = partnerAdr;
        }

        public Response accept(Pair<DecoratedAddress, DecoratedAddress> partnerAdr) {
            return new Response(id, true, Optional.of(partnerAdr));
        }

        public Response deny() {
            Optional<Pair<DecoratedAddress, DecoratedAddress>> noPartner = Optional.absent();
            return new Response(id, false, noPartner);
        }
    }

    public static class Response implements StunMsg {
        public final UUID id;
        public final boolean accept;
        public final Optional<Pair<DecoratedAddress, DecoratedAddress>> partnerAdr;

        public Response(UUID id, boolean accept, Optional<Pair<DecoratedAddress, DecoratedAddress>> partnerAdr) {
            this.id = id;
            this.accept = accept;
            this.partnerAdr = partnerAdr;
        }
    }
}
