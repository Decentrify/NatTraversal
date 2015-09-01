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
import se.sics.nat.hp.client.HPFailureStatus;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;

/**
 *
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class OpenConnection {
    public static class Request extends Direct.Request {
        public final UUID id;
        public final DecoratedAddress target;
        
        public Request(UUID id, DecoratedAddress target) {
            super();
            this.id = id;
            this.target = target;
        }
        
        public Success success(DecoratedAddress self, DecoratedAddress target) {
            return new Success(id, self, target);
        }
        
        public Fail fail(HPFailureStatus status, DecoratedAddress target) {
            return new Fail(id, status, target);
        }
    }
    
    public static class Success implements Direct.Response{
        public final UUID id;
        public final DecoratedAddress self;
        public final DecoratedAddress target;
        
        public Success(UUID id, DecoratedAddress self, DecoratedAddress target) {
            this.id = id;
            this.self = self;
            this.target = target;
        }
    }
    
    public static class Fail implements Direct.Response {
        public final UUID id;
        public final HPFailureStatus status;
        public final DecoratedAddress target;
        
        public Fail(UUID id, HPFailureStatus status, DecoratedAddress target) {
            this.id = id;
            this.status = status;
            this.target = target;
        }
    }
}
