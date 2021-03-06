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
package se.sics.nat.stun.ccserver;

import java.util.Optional;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.Channel;
import se.sics.kompics.Component;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Fault;
import se.sics.kompics.Handler;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.Timer;
import se.sics.ktoolbox.croupier.CroupierPort;
import se.sics.ktoolbox.netmngr.NetMngrBind;
import se.sics.ktoolbox.netmngr.event.NetMngrPort;
import se.sics.ktoolbox.overlaymngr.OverlayMngrPort;
import se.sics.ktoolbox.overlaymngr.events.OMngrCroupier;
import se.sics.ktoolbox.util.config.impl.SystemKCWrapper;
import se.sics.ktoolbox.util.identifiable.BasicIdentifiers;
import se.sics.ktoolbox.util.identifiable.IdentifierFactory;
import se.sics.ktoolbox.util.identifiable.IdentifierRegistryV2;
import se.sics.ktoolbox.util.identifiable.overlay.OverlayId;
import se.sics.ktoolbox.util.network.nat.NatAwareAddress;
import se.sics.ktoolbox.util.overlays.view.OverlayViewUpdatePort;
import se.sics.nat.overlays.NatOverlayId;
import se.sics.nat.stun.server.StunServerComp;
import se.sics.nat.stun.server.StunServerKCWrapper;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class StunServerHostComp extends ComponentDefinition {

  private static final Logger LOG = LoggerFactory.getLogger(StunServerHostComp.class);
  private String logPrefix = "";

  //*****************************CONNECTIONS**********************************
  //***************************EXTERNAL_CONNECT*******************************
  Positive<OverlayMngrPort> oMngrPort = requires(OverlayMngrPort.class);
  Positive<NetMngrPort> netMngrPort = requires(NetMngrPort.class);
  //*****************************CONFIGURATION********************************
  private final SystemKCWrapper systemConfig;
  private final StunServerHostKCWrapper stunServerHostConfig;
  //*****************************EXTERNAL_STATE*******************************
  private final ExtPort extPorts;
  //*****************************INTERNAL_STATE*******************************
  private Component stunServerComp;
  //********************************AUX_STATE*********************************
  private Pair<NetMngrBind.Request, NetMngrBind.Request> bindReq;
  private Pair<NatAwareAddress, NatAwareAddress> stunServerAdr = Pair.with(null, null);
  private OMngrCroupier.ConnectRequest croupierReq;
  private final IdentifierFactory eventIds;

  public StunServerHostComp(Init init) {
    systemConfig = new SystemKCWrapper(config());
    stunServerHostConfig = new StunServerHostKCWrapper(config());
    logPrefix = "<nid:" + systemConfig.id + "> ";
    LOG.info("{}initializing...", logPrefix);

    extPorts = init.extPorts;
    this.eventIds = IdentifierRegistryV2.instance(BasicIdentifiers.Values.EVENT, Optional.of(systemConfig.seed));
    subscribe(handleStart, control);
    subscribe(handleBindResp, netMngrPort);
    subscribe(handleCroupierConnected, oMngrPort);
  }

  Handler handleStart = new Handler<Start>() {
    @Override
    public void handle(Start event) {
      LOG.info("{}starting...", logPrefix);
      StunServerKCWrapper stunServerConfig = new StunServerKCWrapper(config());
      LOG.info("{}binding stun ports", logPrefix);
      NetMngrBind.Request bindReq1 = NetMngrBind.Request.useLocal(eventIds.randomId(), 
        stunServerConfig.stunServerPorts.getValue0());
      trigger(bindReq1, netMngrPort);
      NetMngrBind.Request bindReq2 = NetMngrBind.Request.useLocal(eventIds.randomId(), 
        stunServerConfig.stunServerPorts.getValue1());
      trigger(bindReq2, netMngrPort);
      bindReq = Pair.with(bindReq1, bindReq2);
    }
  };

  private boolean ready() {
    if (stunServerAdr.getValue0() == null || stunServerAdr.getValue1() == null) {
      LOG.warn("{}stun self address not ready yet", logPrefix);
      return false;
    }
    return true;
  }

  Handler handleBindResp = new Handler<NetMngrBind.Response>() {
    @Override
    public void handle(NetMngrBind.Response resp) {
      LOG.trace("{}received:{}", logPrefix, resp);
      if (resp.getId().equals(bindReq.getValue0().getId())) {
        stunServerAdr = stunServerAdr.setAt0((NatAwareAddress) resp.boundAdr);
      } else if (resp.req.getId().equals(bindReq.getValue1().getId())) {
        stunServerAdr = stunServerAdr.setAt1((NatAwareAddress) resp.boundAdr);
      }
      if (ready()) {
        OverlayId croupierId = NatOverlayId.getStunCroupierId(stunServerHostConfig.natOverlayPrefix);
        LOG.info("{}connecting stun server", logPrefix);
        connectStunServer(croupierId);
        LOG.info("{}connecting overlays");
        croupierReq = new OMngrCroupier.ConnectRequest(eventIds.randomId(), croupierId, false);
        trigger(croupierReq, oMngrPort);
      }
    }
  };

  private void connectStunServer(OverlayId croupierId) {
    stunServerComp = create(StunServerComp.class, new StunServerComp.Init(stunServerAdr, croupierId));
    connect(stunServerComp.getNegative(Timer.class), extPorts.timerPort, Channel.TWO_WAY);
    connect(stunServerComp.getNegative(Network.class), extPorts.networkPort, Channel.TWO_WAY);
    connect(stunServerComp.getNegative(CroupierPort.class), extPorts.croupierPort, Channel.TWO_WAY);
    connect(stunServerComp.getPositive(OverlayViewUpdatePort.class), extPorts.viewUpdatePort, Channel.TWO_WAY);
  }

  Handler handleCroupierConnected = new Handler<OMngrCroupier.ConnectResponse>() {
    @Override
    public void handle(OMngrCroupier.ConnectResponse resp) {
      LOG.trace("{}received:{}", logPrefix, resp);
      if (resp.req.getId().equals(croupierReq.getId())) {
        trigger(Start.event, stunServerComp.control());
        LOG.info("{}setup complete", logPrefix);
      }
    }
  };

  @Override
  public Fault.ResolveAction handleFault(Fault fault) {
    LOG.error("{}child component failure:{}", logPrefix, fault);
    System.exit(1);
    return Fault.ResolveAction.RESOLVED;
  }

  public static class Init extends se.sics.kompics.Init<StunServerHostComp> {

    public final ExtPort extPorts;

    public Init(ExtPort extPorts) {
      this.extPorts = extPorts;
    }
  }

  public static class ExtPort {

    public final Positive<Timer> timerPort;
    public final Positive<Network> networkPort;
    public final Positive<CroupierPort> croupierPort;
    public final Negative<OverlayViewUpdatePort> viewUpdatePort;

    public ExtPort(Positive<Timer> timerPort, Positive<Network> networkPort,
      Positive<CroupierPort> croupierPort, Negative<OverlayViewUpdatePort> viewUpdatePort) {
      this.timerPort = timerPort;
      this.networkPort = networkPort;
      this.croupierPort = croupierPort;
      this.viewUpdatePort = viewUpdatePort;
    }
  }
}
