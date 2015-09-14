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

package se.sics.nat.hp;

import se.sics.kompics.network.netty.serialization.Serializers;
import se.sics.nat.hp.msg.SHPSerializer;
import se.sics.nat.hp.msg.SimpleHolePunching;
import se.sics.p2ptoolbox.util.serializer.BasicSerializerSetup;

/**
 *
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class SHPSerializerSetup {
    public static int serializerIds = 5;

    public static enum SHPSerializers {

        Relay(SimpleHolePunching.Relay.class, "shpRelay"),
        Initiate(SimpleHolePunching.Initiate.class, "shpInitiate"),
        Ping(SimpleHolePunching.Ping.class, "shpPing"),
        Pong(SimpleHolePunching.Pong.class, "shpPong"),
        Ready(SimpleHolePunching.Ready.class, "shpReady");
        
        public final Class serializedClass;

        public final String serializerName;

        private SHPSerializers(Class serializedClass, String serializerName) {
            this.serializedClass = serializedClass;
            this.serializerName = serializerName;
        }
    }

    public static void checkSetup() {
        for (SHPSerializers cs : SHPSerializers.values()) {
            if (Serializers.lookupSerializer(cs.serializedClass) == null) {
                throw new RuntimeException("No serializer for " + cs.serializedClass);
            }
        }
        BasicSerializerSetup.checkSetup();
    }

    public static int registerSerializers(int startingId) {
        int currentId = startingId;

        SHPSerializer.Relay shpRelaySerializer = new SHPSerializer.Relay(currentId++);
        Serializers.register(shpRelaySerializer, SHPSerializers.Relay.serializerName);
        Serializers.register(SHPSerializers.Relay.serializedClass, SHPSerializers.Relay.serializerName);
        
        SHPSerializer.Initiate shpInitiateSerializer = new SHPSerializer.Initiate(currentId++);
        Serializers.register(shpInitiateSerializer, SHPSerializers.Initiate.serializerName);
        Serializers.register(SHPSerializers.Initiate.serializedClass, SHPSerializers.Initiate.serializerName);
        
        SHPSerializer.Ping shpPingSerializer = new SHPSerializer.Ping(currentId++);
        Serializers.register(shpPingSerializer, SHPSerializers.Ping.serializerName);
        Serializers.register(SHPSerializers.Ping.serializedClass, SHPSerializers.Ping.serializerName);
        
        SHPSerializer.Pong shpPongSerializer = new SHPSerializer.Pong(currentId++);
        Serializers.register(shpPongSerializer, SHPSerializers.Pong.serializerName);
        Serializers.register(SHPSerializers.Pong.serializedClass, SHPSerializers.Pong.serializerName);
        
        SHPSerializer.Ready shpReadySerializer = new SHPSerializer.Ready(currentId++);
        Serializers.register(shpReadySerializer, SHPSerializers.Ready.serializerName);
        Serializers.register(SHPSerializers.Ready.serializedClass, SHPSerializers.Ready.serializerName);

        assert startingId + serializerIds == currentId;
        return currentId;
    }
}
