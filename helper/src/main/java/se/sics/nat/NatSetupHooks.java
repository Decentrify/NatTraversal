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
package se.sics.nat;

import se.sics.ktoolbox.networkmngr.NetworkMngrHooks;
import se.sics.p2ptoolbox.util.proxy.Hook;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public enum NatSetupHooks {

    IP_SOLVER(NetworkMngrHooks.RequiredHooks.IP_SOLVER.hookName, NetworkMngrHooks.RequiredHooks.IP_SOLVER.hookType),
    PORT_BINDING(NetworkMngrHooks.RequiredHooks.PORT_BINDING.hookName, NetworkMngrHooks.RequiredHooks.PORT_BINDING.hookType),
    NETWORK(NetworkMngrHooks.RequiredHooks.NETWORK.hookName, NetworkMngrHooks.RequiredHooks.NETWORK.hookType),
    UPNP(NatDetectionHooks.RequiredHooks.UPNP.hookName, NatDetectionHooks.RequiredHooks.UPNP.hookType);

    public final String hookName;
    public final Class<? extends Hook.Definition> hookType;

    NatSetupHooks(String name, Class<? extends Hook.Definition> hookType) {
        this.hookName = name;
        this.hookType = hookType;
    }
}
