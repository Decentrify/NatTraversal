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
package se.sics.nat.detection;

import com.google.common.base.Optional;
import java.net.InetAddress;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.Channel;
import se.sics.kompics.Component;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Fault;
import se.sics.kompics.Fault.ResolveAction;
import se.sics.kompics.Handler;
import se.sics.kompics.Kill;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.Timer;
import se.sics.ktoolbox.netmngr.nxnet.NxNetBind;
import se.sics.ktoolbox.netmngr.nxnet.NxNetPort;
import se.sics.ktoolbox.netmngr.nxnet.NxNetUnbind;
import se.sics.ktoolbox.util.config.impl.SystemKCWrapper;
import se.sics.ktoolbox.util.identifiable.BasicIdentifiers;
import se.sics.ktoolbox.util.identifiable.IdentifierFactory;
import se.sics.ktoolbox.util.identifiable.IdentifierRegistryV2;
import se.sics.ktoolbox.util.network.basic.BasicAddress;
import se.sics.ktoolbox.util.network.nat.NatAwareAddress;
import se.sics.ktoolbox.util.network.nat.NatAwareAddressImpl;
import se.sics.ktoolbox.util.network.nat.NatType;
import se.sics.nat.detection.event.NatDetected;
import se.sics.nat.stun.StunClientPort;
import se.sics.nat.stun.StunNatDetected;
import se.sics.nat.stun.client.StunClientComp;
import se.sics.nat.stun.client.StunClientKCWrapper;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class NatDetectionComp extends ComponentDefinition {

  private static final Logger LOG = LoggerFactory.getLogger(NatDetectionComp.class);
  private String logPrefix = "";

  //*****************************CONNECTIONS**********************************
  //*************************EXTERNAL_CONNECT_TO******************************
  Negative<NatDetectionPort> natDetectionPort = provides(NatDetectionPort.class);
  Positive<NxNetPort> nxNetPort = requires(NxNetPort.class);
  //***********************INTERNAL_DO_NOT_CONNECT****************************
  Positive<StunClientPort> stunPort = requires(StunClientPort.class);
  //*******************************CONFIG*************************************
  private SystemKCWrapper systemConfig;
  private StunClientKCWrapper stunClientConfig;
  private NatDetectionKCWrapper natDetectionConfig;
  //****************************EXTERNAL_STATE********************************
  private final ExtPort extPorts;
  private InetAddress privateIp;
  //****************************INTERNAL_STATE********************************
  private Pair<NatAwareAddress, NatAwareAddress> stunAdr = Pair.with(null, null);
  private NatType natType;
  private Optional<InetAddress> publicIp;
  //******************************COMPONENTS**********************************
  private Pair<Component, Channel[]> stunClient;
  //******************************AUX_STATE***********************************
  private NxNetBind.Request stun1BindReq;
  private NxNetBind.Request stun2BindReq;
  private NxNetUnbind.Request stun1UnbindReq;
  private NxNetUnbind.Request stun2UnbindReq;

  public NatDetectionComp(Init init) {
    systemConfig = new SystemKCWrapper(config());
    logPrefix = "<nid:" + systemConfig.id + "> ";
    LOG.debug("{}initiating...", logPrefix);

    SystemKCWrapper systemConfig = new SystemKCWrapper(config());
    stunClientConfig = new StunClientKCWrapper(config());
    IdentifierFactory nodeIdFactory = IdentifierRegistryV2.instance(BasicIdentifiers.Values.NODE, java.util.Optional.of(systemConfig.seed));
    natDetectionConfig = new NatDetectionKCWrapper(config(), nodeIdFactory);
    extPorts = init.extPorts;
    privateIp = init.privateIp;

    subscribe(handleStart, control);
    subscribe(handleBindResp, nxNetPort);
    subscribe(handleStunResp, stunPort);
    subscribe(handleUnbindResp, nxNetPort);
  }

  @Override
  public ResolveAction handleFault(Fault fault) {
    LOG.error("{}fault:{} on comp:{}",
      new Object[]{logPrefix, fault.getCause().getMessage(), fault.getSourceCore().id()});
    return ResolveAction.ESCALATE;
  }
  //**************************CONTROL*****************************************
  Handler handleStart = new Handler<Start>() {
    @Override
    public void handle(Start event) {
      LOG.debug("{}starting...", logPrefix);
      setupStunClient1();
    }
  };

  private void setupStunClient1() {
    LOG.debug("{}setting up public ip detection", logPrefix);
    int stunClientPort1 = stunClientConfig.stunClientPorts.getValue0();
    int stunClientPort2 = stunClientConfig.stunClientPorts.getValue1();
    if (stunClientConfig.stunClientIp.isPresent()) {
      InetAddress stunClientIp = stunClientConfig.stunClientIp.get();
      BasicAddress stunAdr1 = new BasicAddress(stunClientIp, stunClientPort1, systemConfig.id);
      BasicAddress stunAdr2 = new BasicAddress(stunClientIp, stunClientPort2, systemConfig.id);
      stun1BindReq = NxNetBind.Request.providedAdr(stunAdr1, privateIp);
      stun2BindReq = NxNetBind.Request.providedAdr(stunAdr2, privateIp);
    } else {
      BasicAddress stunAdr1 = new BasicAddress(privateIp, stunClientPort1, systemConfig.id);
      BasicAddress stunAdr2 = new BasicAddress(privateIp, stunClientPort2, systemConfig.id);
      stun1BindReq = NxNetBind.Request.localAdr(stunAdr1);
      stun2BindReq = NxNetBind.Request.localAdr(stunAdr2);
    }
    trigger(stun1BindReq, nxNetPort);
    trigger(stun2BindReq, nxNetPort);
  }

  private void setupStunClient2() {
    if (stunAdr.getValue0() == null || stunAdr.getValue1() == null) {
      return;
    }
    setStunClient();
    trigger(Start.event, stunClient.getValue0().control());
  }

  private void setStunClient() {
    Component stunClientComp = create(StunClientComp.class,
      new StunClientComp.Init(stunAdr, natDetectionConfig.stunView));
    Channel[] stunClientChannels = new Channel[3];
    stunClientChannels[0] = connect(stunClientComp.getNegative(Timer.class), extPorts.timerPort, Channel.TWO_WAY);
    stunClientChannels[1] = connect(stunClientComp.getNegative(Network.class), extPorts.networkPort, Channel.TWO_WAY);
    stunClientChannels[2] = connect(stunClientComp.getPositive(StunClientPort.class), stunPort.getPair(),
      Channel.TWO_WAY);
    stunClient = Pair.with(stunClientComp, stunClientChannels);
  }

  private void cleanupStunClient1() {
    LOG.debug("{}clean up stun client", logPrefix);

    disconnect(stunClient.getValue1()[0]);
    disconnect(stunClient.getValue1()[1]);
    disconnect(stunClient.getValue1()[2]);

    trigger(Kill.event, stunClient.getValue0().control());

    stunClient = null;

    stun1UnbindReq = new NxNetUnbind.Request(stunAdr.getValue0().getPort());
    trigger(stun1UnbindReq, nxNetPort);
    stun2UnbindReq = new NxNetUnbind.Request(stunAdr.getValue1().getPort());
    trigger(stun2UnbindReq, nxNetPort);
  }

  private void cleanupStunClient2() {
    if (stunAdr.getValue0() != null || stunAdr.getValue1() != null) {
      return;
    }
    trigger(new NatDetected(natType, publicIp), natDetectionPort);
  }

  //**************************************************************************
  private Handler handleBindResp = new Handler<NxNetBind.Response>() {
    @Override
    public void handle(NxNetBind.Response resp) {
      LOG.trace("{}received:{}", logPrefix, resp);
      NatAwareAddress adr;
      if (stunClientConfig.stunClientOpenPorts.isPresent() && stunClientConfig.stunClientOpenPorts.get()) {
        adr = NatAwareAddressImpl.natPortForwarding((BasicAddress) resp.req.adr);
      } else {
        adr = NatAwareAddressImpl.unknown((BasicAddress) resp.req.adr);
      }
      if (resp.getId().equals(stun1BindReq.getId())) {
        stunAdr = stunAdr.setAt0(adr);
        setupStunClient2();
        return;
      }
      if (resp.getId().equals(stun2BindReq.getId())) {
        stunAdr = stunAdr.setAt1(adr);
        setupStunClient2();
        return;
      }
    }
  };

  private Handler handleUnbindResp = new Handler<NxNetUnbind.Response>() {
    @Override
    public void handle(NxNetUnbind.Response resp) {
      LOG.trace("{}received:{}", logPrefix, resp);
      if (resp.getId().equals(stun1UnbindReq.getId())) {
        stunAdr = stunAdr.setAt0((NatAwareAddress) null);
        cleanupStunClient2();
        return;
      }
      if (resp.getId().equals(stun2UnbindReq.getId())) {
        stunAdr = stunAdr.setAt1((NatAwareAddress) null);
        cleanupStunClient2();
        return;
      }
    }
  };

  Handler handleStunResp = new Handler<StunNatDetected>() {
    @Override
    public void handle(StunNatDetected event) {
      LOG.info("{}detected nat - public ip:{} nat type:{}",
        new Object[]{logPrefix, (event.publicIp.isPresent() ? event.publicIp.get() : "x"), event.natType});
      publicIp = event.publicIp;
      natType = event.natType;
      cleanupStunClient1();
    }
  };

  public static class Init extends se.sics.kompics.Init<NatDetectionComp> {

    public final ExtPort extPorts;
    public final InetAddress privateIp;

    public Init(ExtPort extPorts, InetAddress privateIp) {
      this.extPorts = extPorts;
      this.privateIp = privateIp;
    }
  }

  public static class ExtPort {

    public final Positive<Timer> timerPort;
    public final Positive<Network> networkPort;

    public ExtPort(Positive<Timer> timerPort, Positive<Network> networkPort) {
      this.timerPort = timerPort;
      this.networkPort = networkPort;
    }
  }
}
