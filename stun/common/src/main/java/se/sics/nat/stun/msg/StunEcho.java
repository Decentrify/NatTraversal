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
package se.sics.nat.stun.msg;

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
public abstract class StunEcho implements StunMsg {

    public static enum Type {
        SIP_SP, SIP_DP, DIP_DP, DIP_SP
    }

    public final UUID id;
    public final UUID sessionId;
    public final Type type;

    protected StunEcho(UUID id, UUID sessionId, Type type) {
        this.id = id;
        this.sessionId = sessionId;
        this.type = type;
    }

    public static class Request extends StunEcho {
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
        
        public Reset reset() {
            return new Reset(id, sessionId, type);
        }

        @Override
        public String toString() {
            return "STUN_ECHO_REQ<" + type.toString() + ">";
        }
    }
    
    public static class Response extends StunEcho {
        public final Optional<DecoratedAddress> observed;

        Response(UUID id, UUID sessionId, Type type, Optional observed) {
            super(id, sessionId, type);
            this.observed = observed;
        }
        
        @Override
        public String toString() {
            return "STUN_ECHO_RESP<" + type.toString() + ">";
        }
    }
    
    public static class Reset extends StunEcho {

        public Reset(UUID id, UUID sessionId, Type type) {
            super(id, sessionId, type);
        }
     
        @Override
        public String toString() {
            return "STUN_ECHO_RESET<" + type.toString() + ">";
        }
    }
}
