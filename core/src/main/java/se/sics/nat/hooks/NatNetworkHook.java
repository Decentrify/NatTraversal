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

import se.sics.kompics.Component;
import se.sics.kompics.Positive;
import se.sics.kompics.timer.Timer;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;
import se.sics.p2ptoolbox.util.proxy.Hook;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class NatNetworkHook {

    public static interface Definition extends Hook.Definition<SetupInit, SetupResult, StartInit, Tear> {
    }

    public static class SetupInit implements Hook.SetupInit {

        public final DecoratedAddress adr;
        public final Positive<Timer> timer;

        public SetupInit(DecoratedAddress adr, Positive<Timer> timer) {
            this.adr = adr;
            this.timer = timer;
        }
    }

    public static class SetupResult implements Hook.SetupResult {

        public final Positive network;
        public final Component[] components;

        public SetupResult(Positive network, Component[] components) {
            this.network = network;
            this.components = components;
        }
    }
    
    public static class StartInit extends Hook.StartInit {
        public StartInit(boolean started) {
            super(started);
        }
    }

    public static class Tear implements Hook.Tear {

        public final Component[] components;
        public final Positive<Timer> timer;

        public Tear(Component[] components, Positive<Timer> timer) {
            this.components = components;
            this.timer = timer;
        }
    }
}
