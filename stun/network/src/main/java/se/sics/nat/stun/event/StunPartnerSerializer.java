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
import io.netty.buffer.ByteBuf;
import org.javatuples.Pair;
import se.sics.kompics.network.netty.serialization.Serializer;
import se.sics.kompics.network.netty.serialization.Serializers;
import se.sics.kompics.util.Identifier;
import se.sics.ktoolbox.util.network.nat.NatAwareAddress;
import se.sics.ktoolbox.util.network.nat.NatAwareAddressImpl;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class StunPartnerSerializer {

    public static class Request implements Serializer {

        private final int id;

        public Request(int id) {
            this.id = id;
        }

        @Override
        public int identifier() {
            return id;
        }

        @Override
        public void toBinary(Object o, ByteBuf buf) {
            StunPartner.Request req = (StunPartner.Request) o;
            Serializers.toBinary(req.msgId, buf);
            Serializers.lookupSerializer(NatAwareAddressImpl.class).toBinary(req.partnerAdr.getValue0(), buf);
            buf.writeInt(req.partnerAdr.getValue1().getPort());
        }

        @Override
        public Object fromBinary(ByteBuf buf, Optional<Object> hint) {
            Identifier eventId = (Identifier)Serializers.fromBinary(buf, hint);
            NatAwareAddress partnerAdr1 = (NatAwareAddressImpl) Serializers.lookupSerializer(NatAwareAddressImpl.class).fromBinary(buf, hint);
            int partnerPort2 = buf.readInt();
            NatAwareAddress partnerAdr2 = ((NatAwareAddressImpl)partnerAdr1).changePublicPort(partnerPort2);
            return new StunPartner.Request(eventId, Pair.with(partnerAdr1, partnerAdr2));
        }
    }

    public static class Response implements Serializer {

        private final int id;

        public Response(int id) {
            this.id = id;
        }

        @Override
        public int identifier() {
            return id;
        }

        @Override
        public void toBinary(Object o, ByteBuf buf) {
            StunPartner.Response resp = (StunPartner.Response) o;
            Serializers.toBinary(resp.msgId, buf);
            buf.writeBoolean(resp.accept);
            if (resp.accept) {
                Serializers.lookupSerializer(NatAwareAddressImpl.class).toBinary(resp.partnerAdr.get().getValue0(), buf);
                buf.writeInt(resp.partnerAdr.get().getValue1().getPort());
            }
        }

        @Override
        public Object fromBinary(ByteBuf buf, Optional<Object> hint) {
            Identifier eventId = (Identifier) Serializers.fromBinary(buf, hint);
            boolean accept = buf.readBoolean();
            if(accept) {
                NatAwareAddress partnerAdr1 = (NatAwareAddressImpl) Serializers.lookupSerializer(NatAwareAddressImpl.class).fromBinary(buf, hint);
                int partnerPort2 = buf.readInt();
                NatAwareAddress partnerAdr2 = ((NatAwareAddressImpl)partnerAdr1).changePublicPort(partnerPort2);
                return new StunPartner.Response(eventId, accept, Optional.of(Pair.with(partnerAdr1, partnerAdr2)));
            }
            Optional<Pair<NatAwareAddress, NatAwareAddress>> partnerAdr = Optional.absent();
            return new StunPartner.Response(eventId, accept, partnerAdr);
        }
    }
}
