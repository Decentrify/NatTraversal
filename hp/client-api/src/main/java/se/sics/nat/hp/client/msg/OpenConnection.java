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

package se.sics.nat.hp.client.msg;

import java.util.UUID;
import se.sics.kompics.Direct;
import se.sics.nat.hp.client.HPMsg;
import se.sics.nat.hp.client.HPResponse;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;

/**
 *
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class OpenConnection {
    public static class Request extends Direct.Request<Response> implements HPMsg {
        public final UUID id;
        public final DecoratedAddress target;
        
        public Request(UUID id, DecoratedAddress target) {
            super();
            this.id = id;
            this.target = target;
        }
        
        public Response success() {
            return new Response(id, HPResponse.SUCCESS);
        }
    }
    
    public static class Response implements Direct.Response, HPMsg {
        public final UUID id;
        public final HPResponse response;
        
        public Response(UUID id, HPResponse response) {
            this.id = id;
            this.response = response;
        }
    }
}
