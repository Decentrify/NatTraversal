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
import com.google.common.collect.ImmutableMap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;
import org.javatuples.Pair;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import se.sics.kompics.network.netty.serialization.Serializer;
import se.sics.kompics.network.netty.serialization.Serializers;
import se.sics.nat.stun.StunSerializerSetup;
import se.sics.p2ptoolbox.util.nat.NatedTrait;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;
import se.sics.p2ptoolbox.util.serializer.BasicSerializerSetup;
import se.sics.p2ptoolbox.util.traits.AcceptedTraits;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class StunEchoSerializerTest {

    @BeforeClass
    public static void setup() {
        int serializerId = 128;
        serializerId = BasicSerializerSetup.registerBasicSerializers(serializerId);
        serializerId = StunSerializerSetup.registerSerializers(serializerId);

        ImmutableMap acceptedTraits = ImmutableMap.of(NatedTrait.class, Pair.with(0, (byte) 1));
        DecoratedAddress.setAcceptedTraits(new AcceptedTraits(acceptedTraits));
    }

    @Test
    public void testReq1() throws UnknownHostException {
        testReq(StunEcho.Type.DIP_DP);
    }
    
    @Test
    public void testReq2() throws UnknownHostException {
        testReq(StunEcho.Type.DIP_SP);
    }
    
    @Test
    public void testReq3() throws UnknownHostException {
        testReq(StunEcho.Type.SIP_DP);
    }
    
    @Test
    public void testReq4() throws UnknownHostException {
        testReq(StunEcho.Type.SIP_SP);
    }
    
    private void testReq(StunEcho.Type type) throws UnknownHostException {
        Serializer serializer = Serializers.lookupSerializer(StunEcho.Request.class);
        StunEcho.Request original, copy;
        ByteBuf buf;

        original = new StunEcho.Request(UUID.randomUUID(), UUID.randomUUID(), type,
                new DecoratedAddress(new BasicAddress(InetAddress.getLocalHost(), 10000, 1)));
        buf = Unpooled.buffer();
        serializer.toBinary(original, buf);
        copy = (StunEcho.Request) serializer.fromBinary(Unpooled.wrappedBuffer(buf.array()), Optional.absent());
        Assert.assertEquals(original.id, copy.id);
        Assert.assertEquals(original.sessionId, copy.sessionId);
        Assert.assertEquals(original.type, copy.type);
        Assert.assertEquals(original.target, copy.target);
    }
    
    @Test
    public void testResp1() throws UnknownHostException {
        testResp(StunEcho.Type.SIP_SP, Optional.absent());
    }
    
    @Test
    public void testResp2() throws UnknownHostException {
        testResp(StunEcho.Type.SIP_DP, Optional.absent());
    }
    
    @Test
    public void testResp3() throws UnknownHostException {
        testResp(StunEcho.Type.DIP_SP, Optional.absent());
    }
    
    @Test
    public void testResp4() throws UnknownHostException {
        testResp(StunEcho.Type.DIP_DP, Optional.absent());
    }
    
    @Test
    public void testResp5() throws UnknownHostException {
        testResp(StunEcho.Type.SIP_SP, Optional.of(new DecoratedAddress(new BasicAddress(InetAddress.getLocalHost(), 10000, 1))));
    }
    
    @Test
    public void testResp6() throws UnknownHostException {
        testResp(StunEcho.Type.SIP_DP, Optional.of(new DecoratedAddress(new BasicAddress(InetAddress.getLocalHost(), 10000, 1))));
    }
    
    @Test
    public void testResp7() throws UnknownHostException {
        testResp(StunEcho.Type.DIP_SP, Optional.of(new DecoratedAddress(new BasicAddress(InetAddress.getLocalHost(), 10000, 1))));
    }
    
    @Test
    public void testResp8() throws UnknownHostException {
        testResp(StunEcho.Type.DIP_DP, Optional.of(new DecoratedAddress(new BasicAddress(InetAddress.getLocalHost(), 10000, 1))));
    }
    
    private void testResp(StunEcho.Type type, Optional observed) {
        Serializer serializer = Serializers.lookupSerializer(StunEcho.Response.class);
        StunEcho.Response original, copy;
        ByteBuf buf;

        original = new StunEcho.Response(UUID.randomUUID(), UUID.randomUUID(), type, observed);
        buf = Unpooled.buffer();
        serializer.toBinary(original, buf);
        copy = (StunEcho.Response) serializer.fromBinary(Unpooled.wrappedBuffer(buf.array()), Optional.absent());
        Assert.assertEquals(original.id, copy.id);
        Assert.assertEquals(original.sessionId, copy.sessionId);
        Assert.assertEquals(original.type, copy.type);
        Assert.assertEquals(original.observed, copy.observed);
    }
}
