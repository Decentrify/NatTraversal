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
package se.sics.nat;

import se.sics.kompics.network.netty.serialization.Serializers;
import se.sics.nat.common.croupier.GlobalCroupierView;
import se.sics.nat.croupier.GlobalCroupierViewSerializer;
import se.sics.nat.msg.NatConnection;
import se.sics.nat.msg.NatConnectionSerializer;
import se.sics.nat.pm.PMSerializerSetup;
import se.sics.nat.stun.StunSerializerSetup;
import se.sics.p2ptoolbox.croupier.CroupierSerializerSetup;
import se.sics.p2ptoolbox.util.serializer.BasicSerializerSetup;

/**
 *
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class NatSerializerSetup {

    public static int serializerIds = 5;

    public static enum NatSerializers {

        GlobalCroupierView(GlobalCroupierView.class, "globalCroupierView"),
        NatConnectionOpenReq(NatConnection.OpenRequest.class, "natConnectionOpenReq"),
        NatConnectionOpenResp(NatConnection.OpenResponse.class, "natConnectionOpenResp"),
        NatConnectionClose(NatConnection.Close.class, "natConnectionClose"),
        NatConnectionHeartbeat(NatConnection.Heartbeat.class, "natConnectionHeartbeat");

        public final Class serializedClass;

        public final String serializerName;

        private NatSerializers(Class serializedClass, String serializerName) {
            this.serializedClass = serializedClass;
            this.serializerName = serializerName;
        }
    }

    public static void checkSetup() {
        for (NatSerializers cs : NatSerializers.values()) {
            if (Serializers.lookupSerializer(cs.serializedClass) == null) {
                throw new RuntimeException("No serializer for " + cs.serializedClass);
            }
        }
        BasicSerializerSetup.checkSetup();
        StunSerializerSetup.checkSetup();
        CroupierSerializerSetup.checkSetup();
        PMSerializerSetup.checkSetup();
    }

    public static int registerSerializers(int startingId) {
        int currentId = startingId;

        GlobalCroupierViewSerializer globalCroupierViewSerializer = new GlobalCroupierViewSerializer(currentId++);
        Serializers.register(globalCroupierViewSerializer, NatSerializers.GlobalCroupierView.serializerName);
        Serializers.register(NatSerializers.GlobalCroupierView.serializedClass, NatSerializers.GlobalCroupierView.serializerName);

        NatConnectionSerializer.OpenRequest natConnectionOpenReqSerializer = new NatConnectionSerializer.OpenRequest(currentId++);
        Serializers.register(natConnectionOpenReqSerializer, NatSerializers.NatConnectionOpenReq.serializerName);
        Serializers.register(NatSerializers.NatConnectionOpenReq.serializedClass, NatSerializers.NatConnectionOpenReq.serializerName);

        NatConnectionSerializer.OpenResponse natConnectionOpenRespSerializer = new NatConnectionSerializer.OpenResponse(currentId++);
        Serializers.register(natConnectionOpenRespSerializer, NatSerializers.NatConnectionOpenResp.serializerName);
        Serializers.register(NatSerializers.NatConnectionOpenResp.serializedClass, NatSerializers.NatConnectionOpenResp.serializerName);

        NatConnectionSerializer.Close natConnectionCloseSerializer = new NatConnectionSerializer.Close(currentId++);
        Serializers.register(natConnectionCloseSerializer, NatSerializers.NatConnectionClose.serializerName);
        Serializers.register(NatSerializers.NatConnectionClose.serializedClass, NatSerializers.NatConnectionClose.serializerName);

        NatConnectionSerializer.Heartbeat natConnectionHeartbeatSerializer = new NatConnectionSerializer.Heartbeat(currentId++);
        Serializers.register(natConnectionHeartbeatSerializer, NatSerializers.NatConnectionHeartbeat.serializerName);
        Serializers.register(NatSerializers.NatConnectionHeartbeat.serializedClass, NatSerializers.NatConnectionHeartbeat.serializerName);

        assert startingId + serializerIds == currentId;
        return currentId;
    }
}
