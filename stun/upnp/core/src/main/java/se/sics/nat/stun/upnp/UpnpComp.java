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
package se.sics.nat.stun.upnp;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArraySet;
import org.cybergarage.upnp.UPnP;
import org.cybergarage.upnp.ssdp.SSDPPacket;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Negative;
import se.sics.kompics.Start;
import se.sics.ktoolbox.util.identifiable.BasicIdentifiers;
import se.sics.ktoolbox.util.identifiable.IdentifierFactory;
import se.sics.ktoolbox.util.identifiable.IdentifierRegistryV2;
import se.sics.nat.stun.upnp.cybergarage.Cybergarage;
import se.sics.nat.stun.upnp.cybergarage.DetectedIP;
import se.sics.nat.stun.upnp.cybergarage.ForwardPort;
import se.sics.nat.stun.upnp.event.UPnPMap;
import se.sics.nat.stun.upnp.event.UPnPReady;
import se.sics.nat.stun.upnp.event.UPnPUnmap;
import se.sics.nat.stun.upnp.util.Protocol;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class UpnpComp extends ComponentDefinition {

  private static final Logger LOG = LoggerFactory.getLogger(UpnpComp.class);
  private String logPrefix = "";

  //**************************CONNECTIONS*************************************
  //**********************EXTERNAL_CONNECT_TO*********************************
  private Negative<UPnPPort> upnpPort = provides(UPnPPort.class);
  //*************************CONFIGURATION************************************
  private final String applicationName;
  //*************************INTERNAL_STATE***********************************
  private final Random rand;
  private Cybergarage upnp;
  private InetAddress upnpDeviceIp;
  //<privatePort, req> - answer req on change
  private Map<Integer, UPnPMap.Request> portMapReq = new HashMap<>();
  //***************************AUX_STATE**************************************
  private final IdentifierFactory eventIds;

  public UpnpComp(Init init) {
    applicationName = init.applicationName;
    logPrefix = applicationName + " ";
    LOG.info("{}initiating...", logPrefix);
    this.rand = new Random(init.seed);

    UPnP.setEnable(UPnP.USE_ONLY_IPV4_ADDR);
    this.eventIds = IdentifierRegistryV2.instance(BasicIdentifiers.Values.NODE, Optional.of(1234l));
    subscribe(handleStart, control);
    subscribe(handleMapPorts, upnpPort);
    subscribe(handleUnmapPorts, upnpPort);
  }

  Handler handleStart = new Handler<Start>() {
    @Override
    public void handle(Start event) {
      LOG.info("{}starting...", logPrefix);
      upnp = new Cybergarage(UpnpComp.this, UpnpConfig.DEFAULT_MULTICAST_PORT);
      try {
        upnp.init();
      } catch (RuntimeException ex) {
        LOG.warn("{}cybergarage init error:{}", logPrefix, ex.toString());
        throw ex;
      }
      try {
        Thread.currentThread().sleep(UpnpConfig.DEFAULT_UPNP_DISCOVERY_TIMEOUT);
      } catch (InterruptedException ex) {
        LOG.error("{}thread sleep exception:{}", logPrefix, ex.toString());
        throw new RuntimeException("upnp thread exception", ex);
      }
      DetectedIP[] ips = upnp.getAddress();
      if (ips != null) {
        if (ips.length > 1) {
          LOG.warn("{}multiple upnp ips detected, selecting first", logPrefix);
        }
        for (DetectedIP ip : ips) {
          LOG.info("{}public ip detected:{}", logPrefix, ip.publicAddress);
          upnpDeviceIp = ip.publicAddress;
          break;
        }
      }
      if (upnpDeviceIp == null) {
        upnp.terminate(); //no upnp found should stop looking
      }
      trigger(new UPnPReady(eventIds.randomId(), upnpDeviceIp), upnpPort);
    }
  };

  @Override
  public void tearDown() {
    LOG.info("{}tear down...", logPrefix);
    upnp.terminate();
  }
  //**************************************************************************

  Handler handleMapPorts = new Handler<UPnPMap.Request>() {
    @Override
    public void handle(UPnPMap.Request req) {
      LOG.trace("{}received:{}", logPrefix, req);
      CopyOnWriteArraySet<ForwardPort> registerPorts = new CopyOnWriteArraySet<ForwardPort>();
      for (Map.Entry<Integer, Pair<Protocol, Integer>> e : req.ports.entrySet()) {
        String mappingName = applicationName;
        int protocolType;
        switch (e.getValue().getValue0()) {
          case UDP:
            mappingName += "UDP" + e.getKey();
            protocolType = ForwardPort.PROTOCOL_UDP_IPV4;
            break;
          case TCP:
            mappingName += "TCP" + e.getKey();
            protocolType = ForwardPort.PROTOCOL_TCP_IPV4;
            break;
          default:
            LOG.error("{}unhandlerd protocol type:{}", logPrefix, e.getValue().getValue0());
            throw new RuntimeException("unhandled protocol type:" + e.getValue().getValue0());
        }
        ForwardPort registerPort = new ForwardPort(mappingName, false, protocolType, e.getKey());
        portMapReq.put(e.getKey(), req); //in order to send back a port changed notify if necessary
        registerPorts.add(registerPort);
      }
      Map<ForwardPort, Boolean> resp = upnp.registerPorts(registerPorts);
      Map<Integer, Pair<Protocol, Integer>> mappedPorts = new HashMap<Integer, Pair<Protocol, Integer>>();
      for (Map.Entry<ForwardPort, Boolean> e : resp.entrySet()) {
        Protocol portProtocol;
        switch (e.getKey().protocol) {
          case ForwardPort.PROTOCOL_UDP_IPV4:
            portProtocol = Protocol.UDP;
            break;
          case ForwardPort.PROTOCOL_TCP_IPV4:
            portProtocol = Protocol.TCP;
            break;
          default:
            LOG.error("{}unknown protocol:{}", logPrefix, e.getKey().protocol);
            throw new RuntimeException("unknown protocol:" + e.getKey().protocol);
        }
        if (e.getValue()) {
          LOG.info("{}mapped port:{} protocol:{}", new Object[]{logPrefix, e.getKey().portNumber, portProtocol});
          mappedPorts.put(e.getKey().portNumber, Pair.with(portProtocol, e.getKey().portNumber));
        } else {
          LOG.warn("{}failed to map port:{} protocol:{}", new Object[]{logPrefix, e.getKey().portNumber, portProtocol});
          portMapReq.remove(e.getKey().portNumber);
        }
      }
      answer(req, req.answer(mappedPorts));
    }
  };

  Handler handleUnmapPorts = new Handler<UPnPUnmap.Request>() {
    @Override
    public void handle(UPnPUnmap.Request req) {
      LOG.trace("{}received:{}", logPrefix, req);
      CopyOnWriteArraySet<ForwardPort> unregisterPorts = new CopyOnWriteArraySet<ForwardPort>();
      for (Map.Entry<Integer, Pair<Protocol, Integer>> e : req.ports.entrySet()) {
        if (portMapReq.remove(e.getKey()) == null) {
          continue;
        }

        String mappingName = applicationName;
        int protocolType;
        switch (e.getValue().getValue0()) {
          case UDP:
            mappingName += "UDP" + e.getKey();
            protocolType = ForwardPort.PROTOCOL_UDP_IPV4;
            break;
          case TCP:
            mappingName += "TCP" + e.getKey();
            protocolType = ForwardPort.PROTOCOL_TCP_IPV4;
            break;
          default:
            LOG.error("{}unhandlerd protocol type:{}", logPrefix, e.getValue().getValue0());
            throw new RuntimeException("unhandled protocol type:" + e.getValue().getValue0());
        }
        ForwardPort unregisterPort = new ForwardPort(mappingName, false, protocolType, e.getKey());
        unregisterPorts.add(unregisterPort);
      }
      upnp.unregisterPorts(unregisterPorts);
      answer(req, req.answer(req.ports));
    }
  };

  //TODO Alex - is this important
  public void deviceNotifyReceived(SSDPPacket ssdpPacket) {
    // TODO trigger some results back to client
    LOG.debug("{}:Device notified:{}", logPrefix, ssdpPacket);
  }

  public static class Init extends se.sics.kompics.Init<UpnpComp> {

    public final long seed;
    public final String applicationName;

    public Init(long seed, String applicationName) {
      this.seed = seed;
      this.applicationName = applicationName;
    }
  }

  public static class UpnpConfig {

    public static final int DEFAULT_UPNP_DISCOVERY_TIMEOUT = 1 * 1200;
    public static final int DEFAULT_ROOT_DEVICE_TIMEOUT = 1 * 1200;

    public static final int DEFAULT_MULTICAST_PORT = 55555;
  }
}
