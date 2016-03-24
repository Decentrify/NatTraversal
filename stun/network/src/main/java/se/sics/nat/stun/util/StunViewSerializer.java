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
package se.sics.nat.stun.util;

import com.google.common.base.Optional;
import io.netty.buffer.ByteBuf;
import org.javatuples.Pair;
import se.sics.kompics.network.netty.serialization.Serializer;
import se.sics.kompics.network.netty.serialization.Serializers;
import se.sics.ktoolbox.util.network.nat.NatAwareAddress;
import se.sics.ktoolbox.util.network.nat.NatAwareAddressImpl;
import se.sics.nat.stun.util.StunView;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class StunViewSerializer implements Serializer {
    private final int id;
    
    public StunViewSerializer(int id) {
        this.id = id;
    }
    
    @Override
    public int identifier() {
        return id;
    }

    @Override
    public void toBinary(Object o, ByteBuf buf) {
        StunView sv = (StunView)o;
        Serializer adrSerializer = Serializers.lookupSerializer(NatAwareAddressImpl.class);
        adrSerializer.toBinary(sv.selfStunAdr.getValue0(), buf);
        buf.writeInt(sv.selfStunAdr.getValue1().getPort());
        buf.writeBoolean(sv.partnerStunAdr.isPresent());
        if(sv.partnerStunAdr.isPresent()) {
            adrSerializer.toBinary(sv.partnerStunAdr.get().getValue0(), buf);
            buf.writeInt(sv.partnerStunAdr.get().getValue1().getPort());
        }
    }

    @Override
    public Object fromBinary(ByteBuf buf, Optional<Object> hint) {
        Serializer adrSerializer = Serializers.lookupSerializer(NatAwareAddressImpl.class);
        NatAwareAddressImpl selfStunAdr1 = (NatAwareAddressImpl)adrSerializer.fromBinary(buf, hint);
        int selfStunPort2 = buf.readInt();
        NatAwareAddress selfStunAdr2 = selfStunAdr1.changePublicPort(selfStunPort2);
        boolean withPartner = buf.readBoolean();
        if(withPartner) {
            NatAwareAddressImpl partnerStunAdr1 = (NatAwareAddressImpl)adrSerializer.fromBinary(buf, hint);
            int partnerStunPort2 = buf.readInt();
            NatAwareAddress partnerStunAdr2 = partnerStunAdr1.changePublicPort(partnerStunPort2);
            return StunView.partner(Pair.with((NatAwareAddress)selfStunAdr1, selfStunAdr2), 
                    Pair.with((NatAwareAddress)partnerStunAdr1, partnerStunAdr2));
        } else {
            return StunView.empty(Pair.with((NatAwareAddress)selfStunAdr1, selfStunAdr2));
        }
    }
}
