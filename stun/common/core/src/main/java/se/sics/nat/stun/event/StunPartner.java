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
package se.sics.nat.stun.event;

import com.google.common.base.Optional;
import org.javatuples.Pair;
import se.sics.kompics.id.Identifier;
import se.sics.ktoolbox.util.identifiable.BasicIdentifiers;
import se.sics.ktoolbox.util.network.nat.NatAwareAddress;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class StunPartner {

    public static class Request implements StunEvent {

        public final Identifier eventId;
        public final Pair<NatAwareAddress, NatAwareAddress> partnerAdr;

        public Request(Identifier id, Pair<NatAwareAddress, NatAwareAddress> partnerAdr) {
            this.eventId = id;
            this.partnerAdr = partnerAdr;
        }
        
        public Request(Pair<NatAwareAddress, NatAwareAddress> partnerAdr) {
            this(BasicIdentifiers.eventId(), partnerAdr);
        }

        public Response accept(Pair<NatAwareAddress, NatAwareAddress> partnerAdr) {
            return new Response(eventId, true, Optional.of(partnerAdr));
        }

        public Response deny() {
            Optional<Pair<NatAwareAddress, NatAwareAddress>> noPartner = Optional.absent();
            return new Response(eventId, false, noPartner);
        }

        @Override
        public Identifier getId() {
            return eventId;
        }
        
        @Override
        public String toString() {
            return "StunPartnerRequest<" + eventId + ">";
        }
    }

    public static class Response implements StunEvent {
        public final Identifier eventId;
        public final boolean accept;
        public final Optional<Pair<NatAwareAddress, NatAwareAddress>> partnerAdr;

        Response(Identifier id, boolean accept, Optional<Pair<NatAwareAddress, NatAwareAddress>> partnerAdr) {
            this.eventId = id;
            this.accept = accept;
            this.partnerAdr = partnerAdr;
        }

        @Override
        public Identifier getId() {
            return eventId;
        }
        
        @Override
        public String toString() {
            return "StunPartnerResponse<" + eventId + ">";
        }
    }
}
