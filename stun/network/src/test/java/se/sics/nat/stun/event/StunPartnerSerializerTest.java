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
public class StunPartnerSerializerTest {

  private static IdentifierFactory nodeIdFactory;
  private static IdentifierFactory msgIds;

  @BeforeClass
  public static void setup() {
    systemSetup();
    testSetup();
  }

  private static void systemSetup() {
    IdentifierRegistryV2.registerBaseDefaults1(64);
    OverlayRegistry.initiate(new OverlayId.BasicTypeFactory((byte) 0), new OverlayId.BasicTypeComparator());

    int serializerId = 128;
    serializerId = BasicSerializerSetup.registerBasicSerializers(serializerId);
    serializerId = StunSerializerSetup.registerSerializers(serializerId);
  }

  private static void testSetup() {
    nodeIdFactory = IdentifierRegistryV2.instance(BasicIdentifiers.Values.NODE, java.util.Optional.of(1234l));
    msgIds = IdentifierRegistryV2.instance(BasicIdentifiers.Values.MSG, java.util.Optional.of(1234l));
  }

  private void compareRequest(StunPartner.Request original, StunPartner.Request copy) {
    Assert.assertEquals(original.msgId, copy.msgId);
    FullAddressEquivalence fae = new FullAddressEquivalence();
    Assert.assertTrue(fae.equivalent(
      (NatAwareAddressImpl) original.partnerAdr.getValue0(),
      (NatAwareAddressImpl) copy.partnerAdr.getValue0()));
    Assert.assertTrue(fae.equivalent(
      (NatAwareAddressImpl) original.partnerAdr.getValue1(),
      (NatAwareAddressImpl) copy.partnerAdr.getValue1()));
  }

  @Test
  public void testReq() throws UnknownHostException {
    Serializer serializer = Serializers.lookupSerializer(StunPartner.Request.class);
    StunPartner.Request original, copy;
    ByteBuf serializedOriginal, serializedCopy;

    NatAwareAddress adr1 = NatAwareAddressImpl.open(new BasicAddress(InetAddress.getLocalHost(), 30000, nodeIdFactory.
      id(new BasicBuilders.IntBuilder(1))));
    NatAwareAddress adr2 = NatAwareAddressImpl.open(new BasicAddress(InetAddress.getLocalHost(), 30001, nodeIdFactory.
      id(new BasicBuilders.IntBuilder(1))));
    original = new StunPartner.Request(msgIds.randomId(), Pair.with(adr1, adr2));

    serializedOriginal = Unpooled.buffer();
    serializer.toBinary(original, serializedOriginal);

    serializedCopy = Unpooled.buffer();
    serializedOriginal.getBytes(0, serializedCopy, serializedOriginal.readableBytes());
    copy = (StunPartner.Request) serializer.fromBinary(serializedCopy, Optional.absent());

    Assert.assertEquals(0, serializedCopy.readableBytes());
    compareRequest(original, copy);
  }

  private void compareResponse(StunPartner.Response original, StunPartner.Response copy) {
    Assert.assertEquals(original.msgId, copy.msgId);
    Assert.assertEquals(original.accept, copy.accept);
    Assert.assertEquals(original.partnerAdr.isPresent(), copy.partnerAdr.isPresent());
    if (original.partnerAdr.isPresent()) {
      FullAddressEquivalence fae = new FullAddressEquivalence();
      Assert.assertTrue(fae.equivalent(
        (NatAwareAddressImpl) original.partnerAdr.get().getValue0(),
        (NatAwareAddressImpl) copy.partnerAdr.get().getValue0()));
      Assert.assertTrue(fae.equivalent(
        (NatAwareAddressImpl) original.partnerAdr.get().getValue1(),
        (NatAwareAddressImpl) copy.partnerAdr.get().getValue1()));
    }
  }

  @Test
  public void testRespWithPartner() throws UnknownHostException {
    Serializer serializer = Serializers.lookupSerializer(StunPartner.Response.class);
    StunPartner.Response original, copy;
    ByteBuf serializedOriginal, serializedCopy;

    NatAwareAddress adr1 = NatAwareAddressImpl.open(new BasicAddress(InetAddress.getLocalHost(), 30000, nodeIdFactory.
      id(new BasicBuilders.IntBuilder(1))));
    NatAwareAddress adr2 = NatAwareAddressImpl.open(new BasicAddress(InetAddress.getLocalHost(), 30001, nodeIdFactory.
      id(new BasicBuilders.IntBuilder(1))));
    original = new StunPartner.Response(msgIds.randomId(), true, Optional.of(Pair.with(adr1, adr2)));

    serializedOriginal = Unpooled.buffer();
    serializer.toBinary(original, serializedOriginal);

    serializedCopy = Unpooled.buffer();
    serializedOriginal.getBytes(0, serializedCopy, serializedOriginal.readableBytes());
    copy = (StunPartner.Response) serializer.fromBinary(serializedCopy, Optional.absent());

    Assert.assertEquals(0, serializedCopy.readableBytes());
    compareResponse(original, copy);
  }

  @Test
  public void testRespNoPartner() throws UnknownHostException {
    Serializer serializer = Serializers.lookupSerializer(StunPartner.Response.class);
    StunPartner.Response original, copy;
    ByteBuf serializedOriginal, serializedCopy;

    Optional<Pair<NatAwareAddress, NatAwareAddress>> partner = Optional.absent();
    original = new StunPartner.Response(msgIds.randomId(), false, partner);

    serializedOriginal = Unpooled.buffer();
    serializer.toBinary(original, serializedOriginal);

    serializedCopy = Unpooled.buffer();
    serializedOriginal.getBytes(0, serializedCopy, serializedOriginal.readableBytes());
    copy = (StunPartner.Response) serializer.fromBinary(serializedCopy, Optional.absent());

    Assert.assertEquals(0, serializedCopy.readableBytes());
    compareResponse(original, copy);
  }
}
