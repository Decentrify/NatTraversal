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

package se.sics.nat.example.node;

import se.sics.kompics.network.netty.serialization.Serializers;
import se.sics.nat.NatSerializerSetup;
import se.sics.nat.example.node.msg.NodeMsg;
import se.sics.nat.example.node.serializer.NodeMsgSerializer;
import se.sics.nat.stun.StunSerializerSetup;
import se.sics.p2ptoolbox.croupier.CroupierSerializerSetup;
import se.sics.p2ptoolbox.util.serializer.BasicSerializerSetup;

/**
 *
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class NodeSerializerSetup {
    public static int serializerIds = 2;
    
    public static enum NodeSerializers {
        Ping(NodeMsg.Ping.class, "nodePingSerializer"),
        Pong(NodeMsg.Pong.class, "nodePongSerializer");
        
        public final Class serializedClass;
        public final String serializerName;

        private NodeSerializers(Class serializedClass, String serializerName) {
            this.serializedClass = serializedClass;
            this.serializerName = serializerName;
        }
    }
    
    public static void checkSetup() {
        for (NodeSerializers cs : NodeSerializers.values()) {
            if (Serializers.lookupSerializer(cs.serializedClass) == null) {
                throw new RuntimeException("No serializer for " + cs.serializedClass);
            }
        }
        BasicSerializerSetup.checkSetup();
         CroupierSerializerSetup.checkSetup();
        StunSerializerSetup.checkSetup();
        NatSerializerSetup.checkSetup();
    }
    
    public static int registerSerializers(int startingId) {
        int currentId = startingId;
        
        NodeMsgSerializer.Ping pingSerializer = new NodeMsgSerializer.Ping(currentId++);
        Serializers.register(pingSerializer, NodeSerializers.Ping.serializerName);
        Serializers.register(NodeSerializers.Ping.serializedClass, NodeSerializers.Ping.serializerName);
        
        NodeMsgSerializer.Pong pongSerializer = new NodeMsgSerializer.Pong(currentId++);
        Serializers.register(pongSerializer, NodeSerializers.Pong.serializerName);
        Serializers.register(NodeSerializers.Pong.serializedClass, NodeSerializers.Pong.serializerName);
        
        assert startingId + serializerIds == currentId;
        return currentId;
    }
}
