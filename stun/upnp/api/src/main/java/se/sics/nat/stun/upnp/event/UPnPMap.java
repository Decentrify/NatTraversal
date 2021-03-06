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
package se.sics.nat.stun.upnp.event;

import java.util.Map;
import org.javatuples.Pair;
import se.sics.kompics.Direct;
import se.sics.kompics.util.Identifier;
import se.sics.nat.stun.event.StunEvent;
import se.sics.nat.stun.upnp.util.Protocol;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class UPnPMap {

  public static class Request extends Direct.Request implements StunEvent {

    public final Identifier eventId;
    //<privatePort, <protocol, externalPort>>
    public final Map<Integer, Pair<Protocol, Integer>> ports;

    public Request(Identifier eventId, Map<Integer, Pair<Protocol, Integer>> ports) {
      this.eventId = eventId;
      this.ports = ports;
    }

    public Response answer(Map<Integer, Pair<Protocol, Integer>> ports) {
      return new Response(eventId, ports);
    }

    @Override
    public Identifier getId() {
      return eventId;
    }

    @Override
    public String toString() {
      return "UPnPMap_Req<" + eventId + ">";
    }
  }

  public static class Response implements Direct.Response, StunEvent {

    public final Identifier eventId;
    public final Map<Integer, Pair<Protocol, Integer>> ports;

    Response(Identifier eventId, Map<Integer, Pair<Protocol, Integer>> ports) {
      this.eventId = eventId;
      this.ports = ports;
    }

    @Override
    public Identifier getId() {
      return eventId;
    }

    @Override
    public String toString() {
      return "UPnPMap_Resp<" + eventId + ">";
    }
  }
}
