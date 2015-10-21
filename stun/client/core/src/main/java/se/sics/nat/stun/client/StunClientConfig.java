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

import java.util.HashSet;
import java.util.Set;
import se.sics.p2ptoolbox.util.config.KConfigLevel;
import se.sics.p2ptoolbox.util.config.KConfigOption;
import se.sics.p2ptoolbox.util.config.options.OpenAddressBootstrapOption;
import se.sics.p2ptoolbox.util.config.options.OpenAddressOption;

/**
 *
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class StunClientConfig implements KConfigLevel {
    public final static long CONFIG_TIMEOUT = 1000;
    public final static long ECHO_TIMEOUT = 1000;
    public final static KConfigOption.Basic<Integer> stunClientPort1 = new KConfigOption.Basic("stun.client.address.port1", Integer.class, new StunClientConfig());
    public final static KConfigOption.Basic<Integer> stunClientPort2 = new KConfigOption.Basic("stun.client.address.port2", Integer.class, new StunClientConfig());
    public final static OpenAddressOption stunServer1Adr1 = new OpenAddressOption("stun.server1.address1", new StunClientConfig());
    public final static OpenAddressOption stunServer1Adr2 = new OpenAddressOption("stun.server1.address2", new StunClientConfig());
    public final static OpenAddressOption stunServer2Adr1 = new OpenAddressOption("stun.server2.address1", new StunClientConfig());
    public final static OpenAddressOption stunServer2Adr2 = new OpenAddressOption("stun.server2.address2", new StunClientConfig());

    
    @Override
    public Set<String> canWrite() {
        Set<String> canWrite = new HashSet<>();
        canWrite.add(toString());
        return canWrite;
    }

    @Override
    public String toString() {
        return "StunClientConfig";
    }
}
