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

package se.sics.nat.common.util;

import com.google.common.base.Optional;
import java.net.InetAddress;
import org.javatuples.Pair;
import se.sics.p2ptoolbox.util.nat.NatedTrait;

/**
 *
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class NatDetectionResult {
    private Optional<InetAddress> upnp = null;
    private Pair<NatedTrait, InetAddress> nat = null;
    
    public void setNatReady(NatedTrait trait, InetAddress natAdr) {
        nat = Pair.with(trait, natAdr);
    }
    
    public void setUpnpReady(Optional<InetAddress> upnpAdr) {
        this.upnp = upnpAdr;
    }
    
    public boolean isReady() {
        return upnp != null && nat != null;
    }
    
    public Pair<NatedTrait, InetAddress> getResult() {
        if(upnp.isPresent()) {
            return Pair.with(NatedTrait.upnp(), upnp.get());
        } else {
            return nat;
        }
    }
}
