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

package se.sics.nat.hp.msg;

import com.google.common.base.Optional;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import org.javatuples.Pair;
import se.sics.kompics.network.netty.serialization.Serializer;
import se.sics.kompics.network.netty.serialization.Serializers;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class SHPSerializer {
    public static class Relay implements Serializer {
        private final int id;
        
        public Relay(int id) {
            this.id = id;
        }

        @Override
        public int identifier() {
            return id;
        }

        @Override
        public void toBinary(Object o, ByteBuf buf) {
            SimpleHolePunching.Relay obj = (SimpleHolePunching.Relay)o;
            Serializers.lookupSerializer(UUID.class).toBinary(obj.msgId.getValue0(), buf);
            Serializers.lookupSerializer(UUID.class).toBinary(obj.msgId.getValue1(), buf);
            Serializers.lookupSerializer(DecoratedAddress.class).toBinary(obj.relayTo, buf);
        }

        @Override
        public Object fromBinary(ByteBuf buf, Optional<Object> hint) {
            UUID msgId0 = (UUID)Serializers.lookupSerializer(UUID.class).fromBinary(buf, hint);
            UUID msgId1 = (UUID)Serializers.lookupSerializer(UUID.class).fromBinary(buf, hint);
            DecoratedAddress relayTo = (DecoratedAddress)Serializers.lookupSerializer(DecoratedAddress.class).fromBinary(buf, hint);
            return new SimpleHolePunching.Relay(Pair.with(msgId0, msgId1), relayTo);
        }
    }
    
    public static class Initiate implements Serializer {
        private final int id;
        
        public Initiate(int id) {
            this.id = id;
        }

        @Override
        public int identifier() {
            return id;
        }

        @Override
        public void toBinary(Object o, ByteBuf buf) {
            SimpleHolePunching.Initiate obj = (SimpleHolePunching.Initiate)o;
            Serializers.lookupSerializer(UUID.class).toBinary(obj.msgId.getValue0(), buf);
            Serializers.lookupSerializer(UUID.class).toBinary(obj.msgId.getValue1(), buf);
            Serializers.lookupSerializer(DecoratedAddress.class).toBinary(obj.connectTo, buf);
        }

        @Override
        public Object fromBinary(ByteBuf buf, Optional<Object> hint) {
            UUID msgId0 = (UUID)Serializers.lookupSerializer(UUID.class).fromBinary(buf, hint);
            UUID msgId1 = (UUID)Serializers.lookupSerializer(UUID.class).fromBinary(buf, hint);
            DecoratedAddress connectTo = (DecoratedAddress)Serializers.lookupSerializer(DecoratedAddress.class).fromBinary(buf, hint);
            return new SimpleHolePunching.Initiate(Pair.with(msgId0, msgId1), connectTo);
        }
    }
    
    public static class Ping implements Serializer {
        private final int id;
        
        public Ping(int id) {
            this.id = id;
        }

        @Override
        public int identifier() {
            return id;
        }

        @Override
        public void toBinary(Object o, ByteBuf buf) {
            SimpleHolePunching.Ping obj = (SimpleHolePunching.Ping)o;
            Serializers.lookupSerializer(UUID.class).toBinary(obj.msgId.getValue0(), buf);
            Serializers.lookupSerializer(UUID.class).toBinary(obj.msgId.getValue1(), buf);
        }

        @Override
        public Object fromBinary(ByteBuf buf, Optional<Object> hint) {
            UUID msgId0 = (UUID)Serializers.lookupSerializer(UUID.class).fromBinary(buf, hint);
            UUID msgId1 = (UUID)Serializers.lookupSerializer(UUID.class).fromBinary(buf, hint);
            return new SimpleHolePunching.Ping(Pair.with(msgId0, msgId1));
        }
    }
    
    public static class Pong implements Serializer {
        private final int id;
        
        public Pong(int id) {
            this.id = id;
        }

        @Override
        public int identifier() {
            return id;
        }

        @Override
        public void toBinary(Object o, ByteBuf buf) {
            SimpleHolePunching.Pong obj = (SimpleHolePunching.Pong)o;
            Serializers.lookupSerializer(UUID.class).toBinary(obj.msgId.getValue0(), buf);
            Serializers.lookupSerializer(UUID.class).toBinary(obj.msgId.getValue1(), buf);
        }

        @Override
        public Object fromBinary(ByteBuf buf, Optional<Object> hint) {
            UUID msgId0 = (UUID)Serializers.lookupSerializer(UUID.class).fromBinary(buf, hint);
            UUID msgId1 = (UUID)Serializers.lookupSerializer(UUID.class).fromBinary(buf, hint);
            return new SimpleHolePunching.Pong(Pair.with(msgId0, msgId1));
        }
    }
    
    public static class Ready implements Serializer {
        private final int id;
        
        public Ready(int id) {
            this.id = id;
        }

        @Override
        public int identifier() {
            return id;
        }

        @Override
        public void toBinary(Object o, ByteBuf buf) {
            SimpleHolePunching.Ready obj = (SimpleHolePunching.Ready)o;
            Serializers.lookupSerializer(UUID.class).toBinary(obj.msgId.getValue0(), buf);
            Serializers.lookupSerializer(UUID.class).toBinary(obj.msgId.getValue1(), buf);
            Serializers.lookupSerializer(DecoratedAddress.class).toBinary(obj.observed, buf);
        }

        @Override
        public Object fromBinary(ByteBuf buf, Optional<Object> hint) {
            UUID msgId0 = (UUID)Serializers.lookupSerializer(UUID.class).fromBinary(buf, hint);
            UUID msgId1 = (UUID)Serializers.lookupSerializer(UUID.class).fromBinary(buf, hint);
            DecoratedAddress observed = (DecoratedAddress)Serializers.lookupSerializer(DecoratedAddress.class).fromBinary(buf, hint);
            return new SimpleHolePunching.Ready(Pair.with(msgId0, msgId1), observed);
        }
    }
}
