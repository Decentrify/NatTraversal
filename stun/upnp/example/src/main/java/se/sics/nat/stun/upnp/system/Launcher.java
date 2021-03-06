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
package se.sics.nat.stun.upnp.system;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.Component;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Fault;
import se.sics.kompics.Handler;
import se.sics.kompics.Kompics;
import se.sics.kompics.Start;
import se.sics.ktoolbox.util.identifiable.BasicIdentifiers;
import se.sics.ktoolbox.util.identifiable.IdentifierFactory;
import se.sics.ktoolbox.util.identifiable.IdentifierRegistryV2;
import se.sics.nat.stun.upnp.UPnPPort;
import se.sics.nat.stun.upnp.UpnpComp;
import se.sics.nat.stun.upnp.event.UPnPMap;
import se.sics.nat.stun.upnp.event.UPnPReady;
import se.sics.nat.stun.upnp.event.UPnPUnmap;
import se.sics.nat.stun.upnp.util.Protocol;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class Launcher extends ComponentDefinition {
  
  private static final Logger LOG = LoggerFactory.getLogger(Launcher.class);
  
  private Component upnpComp;
  private IdentifierFactory eventIds;
  
  public Launcher() {
    LOG.info("initiating...");
    this.eventIds = IdentifierRegistryV2.instance(BasicIdentifiers.Values.EVENT, Optional.of(1234l));
    subscribe(handleStart, control);
  }
  
  public Handler<Start> handleStart = new Handler<Start>() {
    
    @Override
    public void handle(Start event) {
      LOG.info("starting...");
      upnpComp = create(UpnpComp.class, new UpnpComp.Init(1234, "example"));
      trigger(Start.event, upnpComp.control());
      subscribe(handleUpnpReady, upnpComp.getPositive(UPnPPort.class));
      subscribe(handleMapPorts, upnpComp.getPositive(UPnPPort.class));
      subscribe(handleUnmapPorts, upnpComp.getPositive(UPnPPort.class));
    }
  };
  
  public Fault.ResolveAction handleFault(Fault fault) {
    LOG.error("error in one of the childre:{}", fault.getCause().getMessage());
    System.exit(1);
    return Fault.ResolveAction.RESOLVED;
  }
  
  Handler handleUpnpReady = new Handler<UPnPReady>() {
    @Override
    public void handle(UPnPReady ready) {
      if (ready.externalIp.isPresent()) {
        LOG.info("upnp present:{}", ready.externalIp.get());
        Map<Integer, Pair<Protocol, Integer>> portMapping = new HashMap<Integer, Pair<Protocol, Integer>>();
        portMapping.put(54345, Pair.with(Protocol.UDP, 54345));
        portMapping.put(54344, Pair.with(Protocol.TCP, 54344));
        trigger(new UPnPMap.Request(eventIds.randomId(), portMapping), upnpComp.getPositive(UPnPPort.class));
      } else {
        LOG.info("no upnp");
      }
    }
  };
  
  Handler handleMapPorts = new Handler<UPnPMap.Response>() {
    @Override
    public void handle(UPnPMap.Response resp) {
      LOG.info("received map:{}", resp.ports);
      trigger(new UPnPUnmap.Request(eventIds.randomId(), resp.ports), upnpComp.getPositive(UPnPPort.class));
    }
  };
  
  Handler handleUnmapPorts = new Handler<UPnPUnmap.Response>() {
    @Override
    public void handle(UPnPUnmap.Response resp) {
      LOG.info("received unmap:{}", resp.ports);
    }
  };
  
  public static void main(String[] args) {
    if (Kompics.isOn()) {
      Kompics.shutdown();
    }
    Kompics.createAndStart(Launcher.class, Runtime.getRuntime().availableProcessors(), 20); // Yes 20 is totally arbitrary
    try {
      Kompics.waitForTermination();
    } catch (InterruptedException ex) {
      throw new RuntimeException(ex.getMessage());
    }
  }
}
