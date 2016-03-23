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

import se.sics.ktoolbox.util.identifiable.Identifier;
import se.sics.ktoolbox.util.identifiable.basic.OverlayIdFactory;

/**
 *
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class NatOverlayId {
    public static Identifier getStunCroupierId(byte owner) {
        return OverlayIdFactory.getId(owner, OverlayIdFactory.Type.CROUPIER, new byte[]{0,0,1});
    }
    
    public static Identifier getParentMakerCroupierId(byte owner) {
        return OverlayIdFactory.getId(owner, OverlayIdFactory.Type.CROUPIER, new byte[]{0,0,2});
    }
}
