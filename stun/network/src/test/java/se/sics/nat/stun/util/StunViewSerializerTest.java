/*
 * Copyright (C) 2009 Swedish Institute of Computer Science (SICS) Copyright (C)
 * 2009 Royal Institute of Technology (KTH)
 *
 * KompicsToolbox is free software; you can redistribute it and/or
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
import io.netty.buffer.Unpooled;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.javatuples.Pair;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import se.sics.kompics.network.netty.serialization.Serializer;
import se.sics.kompics.network.netty.serialization.Serializers;
import se.sics.ktoolbox.util.identifiable.BasicBuilders;
import se.sics.ktoolbox.util.identifiable.BasicIdentifiers;
import se.sics.ktoolbox.util.identifiable.IdentifierFactory;
import se.sics.ktoolbox.util.identifiable.IdentifierRegistryV2;
import se.sics.ktoolbox.util.identifiable.overlay.OverlayId;
import se.sics.ktoolbox.util.identifiable.overlay.OverlayRegistryV2;
import se.sics.ktoolbox.util.network.basic.BasicAddress;
import se.sics.ktoolbox.util.network.nat.FullAddressEquivalence;
import se.sics.ktoolbox.util.network.nat.NatAwareAddress;
import se.sics.ktoolbox.util.network.nat.NatAwareAddressImpl;
import se.sics.ktoolbox.util.setup.BasicSerializerSetup;
import se.sics.nat.stun.StunSerializerSetup;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class StunViewSerializerTest {

    private static IdentifierFactory nodeIdFactory;
    
    @BeforeClass
    public static void setup() {
        systemSetup();
        testSetup();
    }

    private static void systemSetup() {
        IdentifierRegistryV2.registerBaseDefaults1(64);
        OverlayRegistryV2.initiate(new OverlayId.BasicTypeFactory((byte) 0), new OverlayId.BasicTypeComparator());

        int serializerId = 128;
        serializerId = BasicSerializerSetup.registerBasicSerializers(serializerId);
        serializerId = StunSerializerSetup.registerSerializers(serializerId);
    }

    private static void testSetup() {
        nodeIdFactory = IdentifierRegistryV2.instance(BasicIdentifiers.Values.NODE, java.util.Optional.of(1234l));
    }

    private void compareStunView(StunView original, StunView copy) {
        FullAddressEquivalence fae = new FullAddressEquivalence();
        Assert.assertTrue(fae.equivalent(
                (NatAwareAddressImpl) original.selfStunAdr.getValue0(),
                (NatAwareAddressImpl) copy.selfStunAdr.getValue0()));
        Assert.assertTrue(fae.equivalent(
                (NatAwareAddressImpl) original.selfStunAdr.getValue1(),
                (NatAwareAddressImpl) copy.selfStunAdr.getValue1()));
        Assert.assertEquals(original.partnerStunAdr.isPresent(), copy.partnerStunAdr.isPresent());
        if (original.partnerStunAdr.isPresent()) {
            Assert.assertTrue(fae.equivalent(
                    (NatAwareAddressImpl) original.partnerStunAdr.get().getValue0(),
                    (NatAwareAddressImpl) copy.partnerStunAdr.get().getValue0()));
            Assert.assertTrue(fae.equivalent(
                    (NatAwareAddressImpl) original.partnerStunAdr.get().getValue1(),
                    (NatAwareAddressImpl) copy.partnerStunAdr.get().getValue1()));
        }
    }

    @Test
    public void testEmpty() throws UnknownHostException {
        Serializer serializer = Serializers.lookupSerializer(StunView.class);
        StunView original, copy;
        ByteBuf serializedOriginal, serializedCopy;

        NatAwareAddress adr1 = NatAwareAddressImpl.open(new BasicAddress(InetAddress.getLocalHost(), 30000, nodeIdFactory.id(new BasicBuilders.IntBuilder(1))));
        NatAwareAddress adr2 = NatAwareAddressImpl.open(new BasicAddress(InetAddress.getLocalHost(), 30001, nodeIdFactory.id(new BasicBuilders.IntBuilder(1))));
        original = StunView.empty(Pair.with(adr1, adr2));

        serializedOriginal = Unpooled.buffer();
        serializer.toBinary(original, serializedOriginal);

        serializedCopy = Unpooled.buffer();
        serializedOriginal.getBytes(0, serializedCopy, serializedOriginal.readableBytes());
        copy = (StunView) serializer.fromBinary(serializedCopy, Optional.absent());

        Assert.assertEquals(0, serializedCopy.readableBytes());
        compareStunView(original, copy);
    }

    @Test
    public void testWithPartner() throws UnknownHostException {
        Serializer serializer = Serializers.lookupSerializer(StunView.class);
        StunView original, copy;
        ByteBuf serializedOriginal, serializedCopy;

        NatAwareAddress adr1 = NatAwareAddressImpl.open(new BasicAddress(InetAddress.getLocalHost(), 30000, nodeIdFactory.id(new BasicBuilders.IntBuilder(1))));
        NatAwareAddress adr2 = NatAwareAddressImpl.open(new BasicAddress(InetAddress.getLocalHost(), 30001, nodeIdFactory.id(new BasicBuilders.IntBuilder(1))));
        NatAwareAddress adr3 = NatAwareAddressImpl.open(new BasicAddress(InetAddress.getLocalHost(), 30000, nodeIdFactory.id(new BasicBuilders.IntBuilder(2))));
        NatAwareAddress adr4 = NatAwareAddressImpl.open(new BasicAddress(InetAddress.getLocalHost(), 30001, nodeIdFactory.id(new BasicBuilders.IntBuilder(2))));
        original = StunView.empty(Pair.with(adr3, adr4));

        serializedOriginal = Unpooled.buffer();
        serializer.toBinary(original, serializedOriginal);

        serializedCopy = Unpooled.buffer();
        serializedOriginal.getBytes(0, serializedCopy, serializedOriginal.readableBytes());
        copy = (StunView) serializer.fromBinary(serializedCopy, Optional.absent());

        Assert.assertEquals(0, serializedCopy.readableBytes());
        compareStunView(original, copy);
    }
}
