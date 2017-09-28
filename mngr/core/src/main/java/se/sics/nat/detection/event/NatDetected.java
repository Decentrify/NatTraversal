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
package se.sics.nat.detection.event;

import com.google.common.base.Optional;
import java.net.InetAddress;
import se.sics.kompics.KompicsEvent;
import se.sics.kompics.id.Identifiable;
import se.sics.kompics.id.Identifier;
import se.sics.ktoolbox.util.identifiable.BasicIdentifiers;
import se.sics.ktoolbox.util.network.nat.NatType;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class NatDetected implements KompicsEvent, Identifiable<Identifier> {
    public final Identifier eventId;
    public NatType natType;
    public Optional<InetAddress> publicIp;
    
    /**
     * @param natType
     * @param publicIp - optional - missing only if natType - udpBlocked
     */
    public NatDetected(Identifier eventId, NatType natType, Optional<InetAddress> publicIp) {
        this.eventId = eventId;
        this.natType = natType;
        this.publicIp = publicIp;
    }
    
    /**
     * @param natType
     * @param publicIp - optional - missing only if natType - udpBlocked
     */
    public NatDetected(NatType natType, Optional<InetAddress> publicIp) {
        this(BasicIdentifiers.eventId(), natType, publicIp);
    }

    @Override
    public Identifier getId() {
        return eventId;
    }
    
    @Override
    public String toString() {
        return "NatDetected<" + eventId + ">";
    }
}
