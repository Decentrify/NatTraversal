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
package se.sics.nat.common;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class NatTraverserConfig {

    public static long internalStateCheck = 30000;
    public static long connectionHeartbeat = 10000;
    public static long msgRTT = 1000;

    /**
     * connection specific - should be half the binding timeout at most, since i
     * do roughly two heartbeats per heartbeat check
     */
    public static long heartbeat = 5000;
    
    //pm specific
    public static int nrParents = 3;
    public static int nrChildren = 100;
}
