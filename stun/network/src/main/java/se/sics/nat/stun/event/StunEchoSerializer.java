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
import se.sics.kompics.network.netty.serialization.Serializer;
import se.sics.kompics.network.netty.serialization.Serializers;
import se.sics.kompics.util.Identifier;
import se.sics.ktoolbox.util.network.nat.NatAwareAddress;
import se.sics.ktoolbox.util.network.nat.NatAwareAddressImpl;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class StunEchoSerializer {

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
            StunEcho.Request req = (StunEcho.Request) o;
            Serializers.toBinary(req.eventId, buf);
            Serializers.toBinary(req.sessionId, buf);
            buf.writeByte((byte) req.type.ordinal());
            if (req.target == null) {
                buf.writeByte(0);
            } else {
                buf.writeByte(1);
                Serializers.lookupSerializer(NatAwareAddressImpl.class).toBinary(req.target, buf);
            }

        }

        @Override
        public Object fromBinary(ByteBuf buf, Optional<Object> hint) {
            Identifier eventId = (Identifier) Serializers.fromBinary(buf, hint);
            Identifier sessionId = (Identifier) Serializers.fromBinary(buf, hint);
            StunEcho.Type type = StunEcho.Type.values()[buf.readByte()];
            byte flag = buf.readByte();
            NatAwareAddress target = null;
            if (flag == 1) {
                target = (NatAwareAddress) Serializers.lookupSerializer(NatAwareAddressImpl.class).fromBinary(buf, hint);
            }
            return new StunEcho.Request(eventId, sessionId, type, target);
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
            StunEcho.Response resp = (StunEcho.Response) o;
            Serializers.toBinary(resp.eventId, buf);
            Serializers.toBinary(resp.sessionId, buf);
            buf.writeByte((byte) resp.type.ordinal());
            if (!resp.observed.isPresent()) {
                buf.writeByte(0);
            } else {
                buf.writeByte(1);
                Serializers.lookupSerializer(NatAwareAddressImpl.class).toBinary(resp.observed.get(), buf);
            }
        }

        @Override
        public Object fromBinary(ByteBuf buf, Optional<Object> hint) {
            Identifier eventId = (Identifier) Serializers.fromBinary(buf, hint);
            Identifier sessionId = (Identifier) Serializers.fromBinary(buf, hint);
            StunEcho.Type type = StunEcho.Type.values()[buf.readByte()];
            byte flag = buf.readByte();
            Optional<NatAwareAddress> observed = Optional.absent();
            if (flag == 1) {
                observed = Optional.of(
                        (NatAwareAddress) Serializers.lookupSerializer(NatAwareAddressImpl.class).fromBinary(buf, hint));
            }
            return new StunEcho.Response(eventId, sessionId, type, observed);
        }
    }
    
    public static class Reset implements Serializer {

        private final int id;

        public Reset(int id) {
            this.id = id;
        }

        @Override
        public int identifier() {
            return id;
        }

        @Override
        public void toBinary(Object o, ByteBuf buf) {
            StunEcho.Reset resp = (StunEcho.Reset) o;
            Serializers.toBinary(resp.eventId, buf);
            Serializers.toBinary(resp.sessionId, buf);
            buf.writeByte((byte) resp.type.ordinal());
        }

        @Override
        public Object fromBinary(ByteBuf buf, Optional<Object> hint) {
            Identifier eventId = (Identifier) Serializers.fromBinary(buf, hint);
            Identifier sessionId = (Identifier) Serializers.fromBinary(buf, hint);
            StunEcho.Type type = StunEcho.Type.values()[buf.readByte()];
            return new StunEcho.Reset(eventId, sessionId, type);
        }
    }
}
