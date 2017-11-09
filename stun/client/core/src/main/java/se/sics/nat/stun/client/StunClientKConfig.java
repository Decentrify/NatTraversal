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
package se.sics.nat.stun.client;

import se.sics.ktoolbox.util.config.KConfigOption;
import se.sics.ktoolbox.util.config.options.InetAddressOption;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class StunClientKConfig {
    public final static KConfigOption.Basic<Integer> stunClientPort1 = new KConfigOption.Basic("stun.client.address.port1", Integer.class);
    public final static KConfigOption.Basic<Integer> stunClientPort2 = new KConfigOption.Basic("stun.client.address.port2", Integer.class);
    public final static InetAddressOption stunClientIp = new InetAddressOption("stun.client.address.ip");
    public final static KConfigOption.Basic<Boolean> stunClientOpenPorts = new KConfigOption.Basic("stun.client.openports", Boolean.class);
    public final static KConfigOption.Basic<Integer> globalCroupier = new KConfigOption.Basic("services.globalCroupier", Integer.class);
    public final static KConfigOption.Basic<Integer> stunService = new KConfigOption.Basic("services.stun", Integer.class);
}
