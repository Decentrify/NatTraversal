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

package se.sics.nat.stun;

import se.sics.kompics.network.netty.serialization.Serializers;
import se.sics.ktoolbox.util.setup.BasicSerializerSetup;
import se.sics.nat.stun.event.StunEcho;
import se.sics.nat.stun.event.StunEchoSerializer;
import se.sics.nat.stun.event.StunPartner;
import se.sics.nat.stun.event.StunPartnerSerializer;
import se.sics.nat.stun.util.StunViewSerializer;
import se.sics.nat.stun.util.StunView;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class StunSerializerSetup {
    public static int serializerIds = 6;
    
    public static enum StunSerializers {
        StunView(StunView.class, "stunViewSerializer"),
        StunPReqSerializer(StunPartner.Request.class, "stunPReqSerializer"),
        StunPRespSerializer(StunPartner.Response.class, "stunPRespSerializer"),
        StunEchoRequest(StunEcho.Request.class, "stunEchoRequestSerializer"),
        StunEchoResponse(StunEcho.Response.class, "stunEchoResponseSerializer"),
        StunEchoReset(StunEcho.Reset.class, "stunEchoResetSerializer");
        
        public final Class serializedClass;
        public final String serializerName;

        private StunSerializers(Class serializedClass, String serializerName) {
            this.serializedClass = serializedClass;
            this.serializerName = serializerName;
        }
    }
    
    public static void checkSetup() {
        for (StunSerializers cs : StunSerializers.values()) {
            if (Serializers.lookupSerializer(cs.serializedClass) == null) {
                throw new RuntimeException("No serializer for " + cs.serializedClass);
            }
        }
        BasicSerializerSetup.checkSetup();
    }
    
    public static int registerSerializers(int startingId) {
        int currentId = startingId;
        
        StunViewSerializer stunViewSerializer = new StunViewSerializer(currentId++);
        Serializers.register(stunViewSerializer, StunSerializers.StunView.serializerName);
        Serializers.register(StunSerializers.StunView.serializedClass, StunSerializers.StunView.serializerName);
        
        StunPartnerSerializer.Request stunPReqSerializer = new StunPartnerSerializer.Request(currentId++);
        Serializers.register(stunPReqSerializer, StunSerializers.StunPReqSerializer.serializerName);
        Serializers.register(StunSerializers.StunPReqSerializer.serializedClass, StunSerializers.StunPReqSerializer.serializerName);
        
        StunPartnerSerializer.Response stunPRespSerializer = new StunPartnerSerializer.Response(currentId++);
        Serializers.register(stunPRespSerializer, StunSerializers.StunPRespSerializer.serializerName);
        Serializers.register(StunSerializers.StunPRespSerializer.serializedClass, StunSerializers.StunPRespSerializer.serializerName);
        
        StunEchoSerializer.Request stunEchoRequestSerializer = new StunEchoSerializer.Request(currentId++);
        Serializers.register(stunEchoRequestSerializer, StunSerializers.StunEchoRequest.serializerName);
        Serializers.register(StunSerializers.StunEchoRequest.serializedClass, StunSerializers.StunEchoRequest.serializerName);
        
        StunEchoSerializer.Response stunEchoResponseSerializer = new StunEchoSerializer.Response(currentId++);
        Serializers.register(stunEchoResponseSerializer, StunSerializers.StunEchoResponse.serializerName);
        Serializers.register(StunSerializers.StunEchoResponse.serializedClass, StunSerializers.StunEchoResponse.serializerName);
        
        StunEchoSerializer.Reset stunEchoResetSerializer = new StunEchoSerializer.Reset(currentId++);
        Serializers.register(stunEchoResetSerializer, StunSerializers.StunEchoReset.serializerName);
        Serializers.register(StunSerializers.StunEchoReset.serializedClass, StunSerializers.StunEchoReset.serializerName);
        
        assert startingId + serializerIds == currentId;
        return currentId;
    }
}
