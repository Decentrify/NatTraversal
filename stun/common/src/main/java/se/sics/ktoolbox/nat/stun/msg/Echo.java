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

import com.google.common.base.Optional;
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
public abstract class Echo implements StunMsg {

    public static enum Type {
        SIP_SP, SIP_DP, DIP_DP, DIP_SP
    }

    public final UUID id;
    public final UUID sessionId;
    public final Type type;

    protected Echo(UUID id, UUID sessionId, Type type) {
        this.id = id;
        this.sessionId = sessionId;
        this.type = type;
    }

    public static class Request extends Echo {
        public final DecoratedAddress target;

        public Request(UUID id, UUID sessionId, Type type, DecoratedAddress target) {
            super(id, sessionId, type);
            this.target = target;
        }

        public Response answer(DecoratedAddress src) {
            return new Response(id, sessionId, type, Optional.of(src));
        }
        
        public Response answer() {
            return new Response(id, sessionId, type, Optional.absent());
        }

        @Override
        public String toString() {
            return "ECHO_REQ<" + type.toString() + ">";
        }
    }
    
    public static class Response extends Echo {
        public final Optional<DecoratedAddress> observed;

        private Response(UUID id, UUID sessionId, Type type, Optional observed) {
            super(id, sessionId, type);
            this.observed = observed;
        }
        
        @Override
        public String toString() {
            return "ECHO_RESP<" + type.toString() + ">";
        }
    }
}
