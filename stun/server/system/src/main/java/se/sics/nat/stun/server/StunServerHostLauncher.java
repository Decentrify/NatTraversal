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
package se.sics.nat.stun.server;

import java.util.Optional;
import se.sics.kompics.Channel;
import se.sics.kompics.ClassMatchedHandler;
import se.sics.kompics.Component;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.Kompics;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.Timer;
import se.sics.kompics.timer.java.JavaTimer;
import se.sics.kompics.util.Identifier;
import se.sics.ktoolbox.croupier.CroupierPort;
import se.sics.ktoolbox.croupier.CroupierSerializerSetup;
import se.sics.ktoolbox.gradient.GradientSerializerSetup;
import se.sics.ktoolbox.netmngr.NetworkMngrComp;
import se.sics.ktoolbox.netmngr.NetworkMngrSerializerSetup;
import se.sics.ktoolbox.netmngr.event.NetMngrPort;
import se.sics.ktoolbox.netmngr.event.NetMngrReady;
import se.sics.ktoolbox.omngr.bootstrap.BootstrapClientComp;
import se.sics.ktoolbox.omngr.OMngrSerializerSetup;
import se.sics.ktoolbox.omngr.bootstrap.BootstrapClientPort;
import se.sics.ktoolbox.overlaymngr.OverlayMngrComp;
import se.sics.ktoolbox.overlaymngr.OverlayMngrPort;
import se.sics.ktoolbox.util.identifiable.BasicBuilders;
import se.sics.ktoolbox.util.identifiable.IdentifierRegistryV2;
import se.sics.ktoolbox.util.identifiable.basic.IntIdFactory;
import se.sics.ktoolbox.util.identifiable.overlay.OverlayId;
import se.sics.ktoolbox.util.identifiable.overlay.OverlayRegistryV2;
import se.sics.ktoolbox.util.network.nat.NatAwareAddress;
import se.sics.ktoolbox.util.overlays.view.OverlayViewUpdatePort;
import se.sics.ktoolbox.util.setup.BasicSerializerSetup;
import se.sics.ktoolbox.util.status.Status;
import se.sics.ktoolbox.util.status.StatusPort;
import se.sics.nat.stun.StunSerializerSetup;

/**
 *
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class StunServerHostLauncher extends ComponentDefinition {

  //******************************CONNECTIONS*********************************
  //*************************INTERNAL_NO_CONNECTION***************************
  private Positive<StatusPort> externalStatusPort = requires(StatusPort.class);
  //****************************EXTRENAL_STATE********************************
  private NatAwareAddress systemAdr;
  //********************************CLEANUP***********************************
  private Component timerComp;
  private Component netMngrComp;
  private Component bootstrapClientComp;
  private Component oMngrComp;
  private Component hostComp;

  public StunServerHostLauncher() {
    subscribe(handleStart, control);
    subscribe(handleNetReady, externalStatusPort);
  }

  private Handler handleStart = new Handler<Start>() {
    @Override
    public void handle(Start event) {
      timerComp = create(JavaTimer.class, Init.NONE);
      setNetMngr();
      trigger(Start.event, timerComp.control());
      trigger(Start.event, netMngrComp.control());
    }
  };

  private void setNetMngr() {
    logger.info("setting up network mngr");
    NetworkMngrComp.ExtPort netExtPorts = new NetworkMngrComp.ExtPort(timerComp.getPositive(Timer.class));
    netMngrComp = create(NetworkMngrComp.class, new NetworkMngrComp.Init(netExtPorts));
    connect(netMngrComp.getPositive(StatusPort.class), externalStatusPort.getPair(), Channel.TWO_WAY);
  }

  ClassMatchedHandler handleNetReady
    = new ClassMatchedHandler<NetMngrReady, Status.Internal<NetMngrReady>>() {
    @Override
    public void handle(NetMngrReady content, Status.Internal<NetMngrReady> container) {
      logger.info("network mngr ready");
      systemAdr = content.systemAdr;
      setBootstrapClient();
      setOMngr();
      setHost();
      trigger(Start.event, bootstrapClientComp.control());
      trigger(Start.event, oMngrComp.control());
      trigger(Start.event, hostComp.control());
    }
  };

  private void setBootstrapClient() {
    IntIdFactory bootstrapIds = new IntIdFactory(Optional.empty());
    Identifier overlayBootstrapConnBatchId = bootstrapIds.id(new BasicBuilders.IntBuilder(0));
    Identifier overlayBootstrapConnBaseId = bootstrapIds.id(new BasicBuilders.IntBuilder(0));
    bootstrapClientComp = create(BootstrapClientComp.class, 
      new BootstrapClientComp.Init(systemAdr, overlayBootstrapConnBatchId, overlayBootstrapConnBaseId));
    connect(bootstrapClientComp.getNegative(Timer.class), timerComp.getPositive(Timer.class), Channel.TWO_WAY);
    connect(bootstrapClientComp.getNegative(Network.class), netMngrComp.getPositive(Network.class), Channel.TWO_WAY);
  }

  private void setOMngr() {
    logger.info("setting up overlay mngr");
    OverlayMngrComp.ExtPort oMngrExtPorts = new OverlayMngrComp.ExtPort(timerComp.getPositive(Timer.class),
      netMngrComp.getPositive(Network.class), bootstrapClientComp.getPositive(BootstrapClientPort.class));
    oMngrComp = create(OverlayMngrComp.class, new OverlayMngrComp.Init(systemAdr, oMngrExtPorts));
  }

  private void setHost() {
    logger.info("setting up host");
    StunServerHostComp.ExtPort hostExtPorts = new StunServerHostComp.ExtPort(timerComp.getPositive(Timer.class),
      netMngrComp.getPositive(Network.class), oMngrComp.getPositive(CroupierPort.class),
      oMngrComp.getNegative(OverlayViewUpdatePort.class));
    hostComp = create(StunServerHostComp.class, new StunServerHostComp.Init(hostExtPorts));
    connect(hostComp.getNegative(NetMngrPort.class), netMngrComp.getPositive(NetMngrPort.class), Channel.TWO_WAY);
    connect(hostComp.getNegative(OverlayMngrPort.class), oMngrComp.getPositive(OverlayMngrPort.class), Channel.TWO_WAY);
  }

  private static void systemSetup() {
    IdentifierRegistryV2.registerBaseDefaults1(64);
    OverlayRegistryV2.initiate(new OverlayId.BasicTypeFactory((byte) 0), new OverlayId.BasicTypeComparator());

    serializerSetup();
  }

  private static void serializerSetup() {
    //serializers setup
    int serializerId = 128;
//        MessageRegistrator.register();
    serializerId = BasicSerializerSetup.registerBasicSerializers(serializerId);
    serializerId = CroupierSerializerSetup.registerSerializers(serializerId);
    serializerId = GradientSerializerSetup.registerSerializers(serializerId);
    serializerId = OMngrSerializerSetup.registerSerializers(serializerId);
    serializerId = NetworkMngrSerializerSetup.registerSerializers(serializerId);
    serializerId = StunSerializerSetup.registerSerializers(serializerId);

    if (serializerId > 255) {
      throw new RuntimeException("switch to bigger serializerIds, last serializerId:" + serializerId);
    }

    //hooks setup
    //no hooks needed
  }

  public static void main(String[] args) {
    systemSetup();
    start();
    try {
      Kompics.waitForTermination();
    } catch (InterruptedException ex) {
      throw new RuntimeException(ex.getMessage());
    }
  }

  public static void start() {
    if (Kompics.isOn()) {
      Kompics.shutdown();
    }
    Kompics.createAndStart(StunServerHostLauncher.class, Runtime.getRuntime().availableProcessors(), 20); // Yes 20 is totally arbitrary
  }

  public static void stop() {
    Kompics.shutdown();
  }
}
