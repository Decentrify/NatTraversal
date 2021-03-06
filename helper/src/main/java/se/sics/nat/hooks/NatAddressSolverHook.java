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
package se.sics.nat.hooks;

import java.util.EnumSet;
import se.sics.kompics.Component;
import se.sics.ktoolbox.ipsolver.msg.GetIp;
import se.sics.p2ptoolbox.util.proxy.Hook;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class NatAddressSolverHook {

    public static interface Definition extends Hook.Definition<NatAddressSolverHP, SetupInit, SetupResult, StartInit, TearInit> {
    }

    public static class SetupInit implements Hook.SetupInit {
    }

    public static class SetupResult implements Hook.SetupResult {

        public final Component[] comp;

        public SetupResult(Component[] comp) {
            this.comp = comp;
        }
    }

    public static class StartInit extends Hook.StartInit {

        public final EnumSet<GetIp.NetworkInterfacesMask> netInterfaces;

        public StartInit(boolean started, EnumSet<GetIp.NetworkInterfacesMask> netInterfaces) {
            super(started);
            this.netInterfaces = netInterfaces;
        }
    }

    public static class TearInit implements Hook.TearInit {
    }
}
