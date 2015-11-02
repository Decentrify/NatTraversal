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
package se.sics.nat.stun.util;

import com.google.common.base.Optional;
import org.javatuples.Pair;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class StunView {
    public final DecoratedAddress selfStunAdr1;
    public final Optional<DecoratedAddress> partner;
    
    public StunView(DecoratedAddress selfStunAdr1, Optional<DecoratedAddress> partner) {
        this.selfStunAdr1 = selfStunAdr1;
        this.partner = partner;
    }
    
    @Override
    public String toString() {
        String toS = "selfStun:<" + selfStunAdr1.getIp().getHostAddress() 
                + ":" + selfStunAdr1.getPort() + ":" + selfStunAdr1.getId() + "> "
                + "partner:" + (partner.isPresent() ? partner.get().getBase().toString() : "x");
        return toS;
    }
    
    public static StunView empty(DecoratedAddress selfStunAdr1) {
        Optional<DecoratedAddress> p = Optional.absent();
        return new StunView(selfStunAdr1, p);
    }
    
    public static StunView partner(DecoratedAddress selfStunAdr1, DecoratedAddress partner) {
        return new StunView(selfStunAdr1, Optional.of(partner));
    }
}
