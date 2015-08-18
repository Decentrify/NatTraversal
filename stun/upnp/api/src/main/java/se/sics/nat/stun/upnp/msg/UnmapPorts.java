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

package se.sics.nat.stun.upnp.msg;

import java.util.Map;
import java.util.UUID;
import org.javatuples.Pair;
import se.sics.kompics.Direct;
import se.sics.nat.stun.upnp.util.Protocol;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class UnmapPorts {
    public static class Req extends Direct.Request {
        public final UUID id;
        public final Map<Integer, Pair<Protocol, Integer>> ports; //<privatePort, <protocol, externalPort>>
        
        public Req(UUID id, Map<Integer, Pair<Protocol, Integer>> ports) {
            this.id = id;
            this.ports = ports;
        }
        
        public Resp answer(Map<Integer, Pair<Protocol, Integer>> ports) {
            return new Resp(id, ports);
        }
    }
    
    public static class Resp implements Direct.Response {
        public final UUID id;
        public final Map<Integer, Pair<Protocol, Integer>> ports;
        
        public Resp(UUID id, Map<Integer, Pair<Protocol, Integer>> ports) {
            this.id = id;
            this.ports = ports;
        }
    }
}
