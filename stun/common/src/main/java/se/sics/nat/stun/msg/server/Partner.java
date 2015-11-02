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
package se.sics.nat.stun.msg.server;

import java.util.UUID;
import se.sics.nat.stun.msg.StunMsg;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class Partner {

    public static class Request implements StunMsg {

        public final UUID id;

        public Request(UUID id) {
            this.id = id;
        }

        public Response accept() {
            return new Response(id, true);
        }

        public Response deny() {
            return new Response(id, false);
        }
    }

    public static class Response implements StunMsg {
        public final UUID id;
        public final boolean accept;

        public Response(UUID id, boolean accept) {
            this.id = id;
            this.accept = accept;
        }
    }
}
