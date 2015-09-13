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
package se.sics.nat.stun.msg;

import com.google.common.base.Optional;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import org.javatuples.Pair;
import se.sics.kompics.network.netty.serialization.Serializer;
import se.sics.kompics.network.netty.serialization.Serializers;
import se.sics.p2ptoolbox.util.BitBuffer;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;

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
            Serializer uuidSerializer = Serializers.lookupSerializer(UUID.class);
            uuidSerializer.toBinary(req.id, buf);
            uuidSerializer.toBinary(req.sessionId, buf);
            buf.writeByte((byte) req.type.ordinal());
            if (req.target == null) {
                buf.writeByte(0);
            } else {
                buf.writeByte(1);
                Serializer addressSerializer = Serializers.lookupSerializer(DecoratedAddress.class);
                addressSerializer.toBinary(req.target, buf);
            }

        }

        @Override
        public Object fromBinary(ByteBuf buf, Optional<Object> hint) {
            Serializer uuidSerializer = Serializers.lookupSerializer(UUID.class);
            UUID id = (UUID) uuidSerializer.fromBinary(buf, hint);
            UUID sessionId = (UUID) uuidSerializer.fromBinary(buf, hint);
            StunEcho.Type type = StunEcho.Type.values()[buf.readByte()];
            byte flag = buf.readByte();
            DecoratedAddress target = null;
            if (flag == 1) {
                Serializer addressSerializer = Serializers.lookupSerializer(DecoratedAddress.class);
                target = (DecoratedAddress) addressSerializer.fromBinary(buf, hint);
            }
            return new StunEcho.Request(id, sessionId, type, target);
        }
    }

    public static class Response implements Serializer {

        private static final int flags = 2 + 1;
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
            Serializer uuidSerializer = Serializers.lookupSerializer(UUID.class);
            uuidSerializer.toBinary(resp.id, buf);
            uuidSerializer.toBinary(resp.sessionId, buf);

            BitBuffer bb = BitBuffer.create(flags);
            switch (resp.type) {
                case DIP_DP:
                    break;
                case DIP_SP:
                    bb.write(Pair.with(0, true));
                    break;
                case SIP_DP:
                    bb.write(Pair.with(1, true));
                    break;
                case SIP_SP:
                    bb.write(Pair.with(0, true), Pair.with(1, true));
                    break;
                default:
                    throw new IllegalArgumentException("unknown echo type:" + resp.type);
            }
            if (resp.observed.isPresent()) {
                bb.write(Pair.with(2, true));
                buf.writeBytes(bb.finalise());
                Serializers.lookupSerializer(DecoratedAddress.class).toBinary(resp.observed.get(), buf);
            } else {
                buf.writeBytes(bb.finalise());
            }
        }

        @Override
        public Object fromBinary(ByteBuf buf, Optional<Object> hint) {
            Serializer uuidSerializer = Serializers.lookupSerializer(UUID.class);
            UUID id = (UUID) uuidSerializer.fromBinary(buf, hint);
            UUID sessionId = (UUID) uuidSerializer.fromBinary(buf, hint);
            byte[] bFlags = new byte[1];
            buf.readBytes(bFlags);
            boolean[] respFlags = BitBuffer.extract(flags, bFlags);
            StunEcho.Type type = getType(respFlags);
            Optional<DecoratedAddress> observed = Optional.absent();
            if (respFlags[2]) {
                observed = Optional.of((DecoratedAddress) Serializers.lookupSerializer(DecoratedAddress.class).fromBinary(buf, hint));
            }
            return new StunEcho.Response(id, sessionId, type, observed);
        }

        private StunEcho.Type getType(boolean[] flags) {
            if (flags[0] && flags[1]) {
                return StunEcho.Type.SIP_SP;
            }
            if (flags[0]) {
                return StunEcho.Type.DIP_SP;
            }
            if (flags[1]) {
                return StunEcho.Type.SIP_DP;
            }
            return StunEcho.Type.DIP_DP;
        }
    }
}
