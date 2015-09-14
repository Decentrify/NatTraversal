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
package se.sics.nat.pm;

import se.sics.kompics.network.netty.serialization.Serializers;
import se.sics.nat.pm.common.PMMsg;
import se.sics.nat.pm.msg.PMMsgSerializer;
import se.sics.p2ptoolbox.croupier.CroupierSerializerSetup;
import se.sics.p2ptoolbox.util.serializer.BasicSerializerSetup;

/**
 *
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class PMSerializerSetup {

    public static int serializerIds = 4;

    public static enum PMSerializers {

        RegisterReq(PMMsg.RegisterReq.class, "pmRegisterReqSerializer"),
        RegisterResp(PMMsg.RegisterResp.class, "pmRegisterRespSerializer"),
        UnRegister(PMMsg.UnRegister.class, "pmUnRegisterSerializer"),
        Heartbeat(PMMsg.Heartbeat.class, "pmHeartbeatSerializer");

        public final Class serializedClass;
        public final String serializerName;

        private PMSerializers(Class serializedClass, String serializerName) {
            this.serializedClass = serializedClass;
            this.serializerName = serializerName;
        }
    }

    public static void checkSetup() {
        for (PMSerializers cs : PMSerializers.values()) {
            if (Serializers.lookupSerializer(cs.serializedClass) == null) {
                throw new RuntimeException("No serializer for " + cs.serializedClass);
            }
        }
        BasicSerializerSetup.checkSetup();
        CroupierSerializerSetup.checkSetup();
    }

    public static int registerSerializers(int startingId) {
        int currentId = startingId;

        PMMsgSerializer.RegisterReq pmRegisterReqSerializer = new PMMsgSerializer.RegisterReq(currentId++);
        Serializers.register(pmRegisterReqSerializer, PMSerializers.RegisterReq.serializerName);
        Serializers.register(PMSerializers.RegisterReq.serializedClass, PMSerializers.RegisterReq.serializerName);
        
        PMMsgSerializer.RegisterResp pmRegisterRespSerializer = new PMMsgSerializer.RegisterResp(currentId++);
        Serializers.register(pmRegisterRespSerializer, PMSerializers.RegisterResp.serializerName);
        Serializers.register(PMSerializers.RegisterResp.serializedClass, PMSerializers.RegisterResp.serializerName);

        PMMsgSerializer.UnRegister pmUnRegisterSerializer = new PMMsgSerializer.UnRegister(currentId++);
        Serializers.register(pmUnRegisterSerializer, PMSerializers.UnRegister.serializerName);
        Serializers.register(PMSerializers.UnRegister.serializedClass, PMSerializers.UnRegister.serializerName);

        PMMsgSerializer.Heartbeat pmHeartbeatSerializer = new PMMsgSerializer.Heartbeat(currentId++);
        Serializers.register(pmHeartbeatSerializer, PMSerializers.Heartbeat.serializerName);
        Serializers.register(PMSerializers.Heartbeat.serializedClass, PMSerializers.Heartbeat.serializerName);

        assert startingId + serializerIds == currentId;
        return currentId;
    }
}
