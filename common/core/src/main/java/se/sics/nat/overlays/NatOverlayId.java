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
package se.sics.nat.overlays;

import se.sics.kompics.id.Identifier;
import se.sics.ktoolbox.util.identifiable.BasicBuilders;
import se.sics.ktoolbox.util.identifiable.BasicIdentifiers;
import se.sics.ktoolbox.util.identifiable.IdentifierFactory;
import se.sics.ktoolbox.util.identifiable.IdentifierRegistry;
import se.sics.ktoolbox.util.identifiable.overlay.OverlayId;
import se.sics.ktoolbox.util.identifiable.overlay.OverlayIdFactory;

/**
 *
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class NatOverlayId {
    public static OverlayId getStunCroupierId(byte owner) {
        IdentifierFactory baseIdFactory = IdentifierRegistry.lookup(BasicIdentifiers.Values.OVERLAY.toString());
        OverlayIdFactory overlayIdFactory = new OverlayIdFactory(baseIdFactory, OverlayId.BasicTypes.CROUPIER, owner);
        return overlayIdFactory.id(new BasicBuilders.IntBuilder(1));
    }
    
    public static Identifier getParentMakerCroupierId(byte owner) {
        IdentifierFactory baseIdFactory = IdentifierRegistry.lookup(BasicIdentifiers.Values.OVERLAY.toString());
        OverlayIdFactory overlayIdFactory = new OverlayIdFactory(baseIdFactory, OverlayId.BasicTypes.CROUPIER, owner);
        return overlayIdFactory.id(new BasicBuilders.IntBuilder(2));
    }
}
