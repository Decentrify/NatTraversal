/*
 * Copyright (C) 2009 Swedish Institute of Computer Science (SICS) Copyright (C)
 * 2009 Royal Institute of Technology (KTH)
 *
 * KompicsToolbox is free software; you can redistribute it and/or
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
package se.sics.nat.hooks;

import se.sics.ktoolbox.ipsolver.hooks.IpSolverHook;
import se.sics.ktoolbox.util.proxy.Hook;
import se.sics.ktoolbox.util.proxy.network.NetworkHook;
import se.sics.ktoolbox.util.proxy.network.PortBindingHook;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class BaseHooks {

    public static final Class<IpSolverHook.Definition> IP_SOLVER_HOOK = IpSolverHook.Definition.class;
    public static final Class<PortBindingHook.Definition> PORT_BINDING_HOOK = PortBindingHook.Definition.class;
    public static final Class<NetworkHook.Definition> NETWORK_HOOK = NetworkHook.Definition.class;
    
    public static enum RequiredHooks {

        IP_SOLVER("NET_MNGR_IP_SOLVER", IP_SOLVER_HOOK),
        PORT_BINDING("NET_MNGR_PORT_BINDING", PORT_BINDING_HOOK),
        NETWORK("NET_MNGR_NETWORK", NETWORK_HOOK);

        public final String hookName;
        public final Class<? extends Hook.Definition> hookType;

        RequiredHooks(String name, Class<? extends Hook.Definition> hookType) {
            this.hookName = name;
            this.hookType = hookType;
        }
    }
}