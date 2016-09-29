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
import io.netty.buffer.Unpooled;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import se.sics.kompics.network.netty.serialization.Serializer;
import se.sics.kompics.network.netty.serialization.Serializers;
import se.sics.ktoolbox.util.identifiable.BasicBuilders;
import se.sics.ktoolbox.util.identifiable.BasicIdentifiers;
import se.sics.ktoolbox.util.identifiable.IdentifierFactory;
import se.sics.ktoolbox.util.identifiable.IdentifierRegistry;
import se.sics.ktoolbox.util.identifiable.overlay.OverlayId;
import se.sics.ktoolbox.util.identifiable.overlay.OverlayRegistry;
import se.sics.ktoolbox.util.network.basic.BasicAddress;
import se.sics.ktoolbox.util.network.nat.FullAddressEquivalence;
import se.sics.ktoolbox.util.network.nat.NatAwareAddress;
import se.sics.ktoolbox.util.network.nat.NatAwareAddressImpl;
import se.sics.ktoolbox.util.setup.BasicSerializerSetup;
import se.sics.nat.stun.StunSerializerSetup;

/**
 *
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class StunEchoSerializerTest {

    private static IdentifierFactory nodeIdFactory;
    
    @BeforeClass
    public static void setup() {
        systemSetup();
        testSetup();
    }

    private static void systemSetup() {
        BasicIdentifiers.registerDefaults(1234l);
        OverlayRegistry.initiate(new OverlayId.BasicTypeFactory((byte) 0), new OverlayId.BasicTypeComparator());

        int serializerId = 128;
        serializerId = BasicSerializerSetup.registerBasicSerializers(serializerId);
        serializerId = StunSerializerSetup.registerSerializers(serializerId);
    }

    private static void testSetup() {
        nodeIdFactory = IdentifierRegistry.lookup(BasicIdentifiers.Values.NODE.toString());
    }
    
    private void compareRequest(StunEcho.Request original, StunEcho.Request copy) {
        Assert.assertEquals(original.eventId, copy.eventId);
        Assert.assertEquals(original.sessionId, copy.sessionId);
        Assert.assertEquals(original.type, copy.type);
        FullAddressEquivalence fae = new FullAddressEquivalence();
        Assert.assertTrue(fae.equivalent(
                (NatAwareAddressImpl) original.target,
                (NatAwareAddressImpl) copy.target));
    }

    @Test
    public void testReqWithTarget() throws UnknownHostException {
        Serializer serializer = Serializers.lookupSerializer(StunEcho.Request.class);
        StunEcho.Request original, copy;
        ByteBuf serializedOriginal, serializedCopy;

        NatAwareAddress adr1 = NatAwareAddressImpl.open(new BasicAddress(InetAddress.getLocalHost(), 30000, nodeIdFactory.id(new BasicBuilders.IntBuilder(1))));
        original = new StunEcho.Request(BasicIdentifiers.eventId(), StunEcho.Type.DIP_DP, adr1);

        serializedOriginal = Unpooled.buffer();
        serializer.toBinary(original, serializedOriginal);

        serializedCopy = Unpooled.buffer();
        serializedOriginal.getBytes(0, serializedCopy, serializedOriginal.readableBytes());
        copy = (StunEcho.Request) serializer.fromBinary(serializedCopy, Optional.absent());

        Assert.assertEquals(0, serializedCopy.readableBytes());
        compareRequest(original, copy);
    }

    @Test
    public void testReqNoTarget() throws UnknownHostException {
        Serializer serializer = Serializers.lookupSerializer(StunEcho.Request.class);
        StunEcho.Request original, copy;
        ByteBuf serializedOriginal, serializedCopy;

        original = new StunEcho.Request(BasicIdentifiers.eventId(), StunEcho.Type.SIP_DP, null);

        serializedOriginal = Unpooled.buffer();
        serializer.toBinary(original, serializedOriginal);

        serializedCopy = Unpooled.buffer();
        serializedOriginal.getBytes(0, serializedCopy, serializedOriginal.readableBytes());
        copy = (StunEcho.Request) serializer.fromBinary(serializedCopy, Optional.absent());

        Assert.assertEquals(0, serializedCopy.readableBytes());
        compareRequest(original, copy);
    }

    private void compareResponse(StunEcho.Response original, StunEcho.Response copy) {
        Assert.assertEquals(original.eventId, copy.eventId);
        Assert.assertEquals(original.sessionId, copy.sessionId);
        Assert.assertEquals(original.type, copy.type);
        Assert.assertEquals(original.observed.isPresent(), copy.observed.isPresent());
        if (original.observed.isPresent()) {
            FullAddressEquivalence fae = new FullAddressEquivalence();
            Assert.assertTrue(fae.equivalent(
                    (NatAwareAddressImpl) original.observed.get(),
                    (NatAwareAddressImpl) copy.observed.get()));
        }
    }

    @Test
    public void testResp() throws UnknownHostException {
        Serializer serializer = Serializers.lookupSerializer(StunEcho.Response.class);
        StunEcho.Response original, copy;
        ByteBuf serializedOriginal, serializedCopy;

        NatAwareAddress adr1 = NatAwareAddressImpl.open(new BasicAddress(InetAddress.getLocalHost(), 30000, nodeIdFactory.id(new BasicBuilders.IntBuilder(1))));
        original = new StunEcho.Response(BasicIdentifiers.eventId(), BasicIdentifiers.eventId(),
                StunEcho.Type.SIP_DP, Optional.of(adr1));

        serializedOriginal = Unpooled.buffer();
        serializer.toBinary(original, serializedOriginal);

        serializedCopy = Unpooled.buffer();
        serializedOriginal.getBytes(0, serializedCopy, serializedOriginal.readableBytes());
        copy = (StunEcho.Response) serializer.fromBinary(serializedCopy, Optional.absent());

        Assert.assertEquals(0, serializedCopy.readableBytes());
        compareResponse(original, copy);
    }

    @Test
    public void testRespNoObserved() throws UnknownHostException {
        Serializer serializer = Serializers.lookupSerializer(StunEcho.Response.class);
        StunEcho.Response original, copy;
        ByteBuf serializedOriginal, serializedCopy;

        original = new StunEcho.Response(BasicIdentifiers.eventId(), BasicIdentifiers.eventId(),
                StunEcho.Type.SIP_DP, Optional.absent());

        serializedOriginal = Unpooled.buffer();
        serializer.toBinary(original, serializedOriginal);

        serializedCopy = Unpooled.buffer();
        serializedOriginal.getBytes(0, serializedCopy, serializedOriginal.readableBytes());
        copy = (StunEcho.Response) serializer.fromBinary(serializedCopy, Optional.absent());

        Assert.assertEquals(0, serializedCopy.readableBytes());
        compareResponse(original, copy);
    }
    
    private void compareReset(StunEcho.Reset original, StunEcho.Reset copy) {
        Assert.assertEquals(original.eventId, copy.eventId);
        Assert.assertEquals(original.sessionId, copy.sessionId);
        Assert.assertEquals(original.type, copy.type);
    }
    
    @Test
    public void testReset() throws UnknownHostException {
        Serializer serializer = Serializers.lookupSerializer(StunEcho.Reset.class);
        StunEcho.Reset original, copy;
        ByteBuf serializedOriginal, serializedCopy;

        original = new StunEcho.Reset(BasicIdentifiers.eventId(), BasicIdentifiers.eventId(), StunEcho.Type.SIP_DP);

        serializedOriginal = Unpooled.buffer();
        serializer.toBinary(original, serializedOriginal);

        serializedCopy = Unpooled.buffer();
        serializedOriginal.getBytes(0, serializedCopy, serializedOriginal.readableBytes());
        copy = (StunEcho.Reset) serializer.fromBinary(serializedCopy, Optional.absent());

        Assert.assertEquals(0, serializedCopy.readableBytes());
        compareReset(original, copy);
    }
}
