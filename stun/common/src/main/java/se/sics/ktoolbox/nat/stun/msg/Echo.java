/*
 * Copyright (C) 2009 Swedish Institute of Computer Science (SICS) Copyright (C)
 * 2009 Royal Institute of Technology (KTH)
 *
 * GVoD is free software; you can redistribute it and/or
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
package se.sics.ktoolbox.nat.stun.msg;

import java.util.UUID;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;

/**
 * SAME_PORT discovers port mapping and allocation policies. The server replies
 * with the the public IP it received the req on. SAME_PORT is performed on 2
 * stun servers to test (1) for presence of NAT, and (2) for presence of a
 * firewall.
 *
 * DIFF_PORT and PARTNER discover port filtering policy. DIFF_PORT involves
 * sending req to StunServer1 who replies over a different socket bound on a
 * different port.
 *
 * PARTNER involves sending req to StunServer1 who delegates to StunServer2 who
 * sends response.
 *
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class Echo {

    public static enum Type {

        UDP_BLOCKED, SAME_PORT1, SAME_PORT2, DIFF_PORT, PARTNER
    }
    
    public static class Request {
        public final UUID id;
        public final Type type;
        public final DecoratedAddress replyTo;
        
        public Request(UUID id, Type type, DecoratedAddress replyTo) {
            assert replyTo != null;
            this.id = id;
            this.type = type;
            this.replyTo = replyTo;
        }
        
        public Response answer() {
            return new Response(id, type);
        }
        
        @Override
        public String toString() {
            return "ECHO<" + type.toString() + "> from:" + replyTo.getBase().toString();
        }
    }
    
    public static class Response {
        public final UUID id;
        public final Type type;
        
        public Response(UUID id, Type type) {
            this.id = id;
            this.type = type;
        }
    }
}
