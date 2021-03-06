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
package se.sics.nat.stun.simulation;

import se.sics.kompics.Component;
import se.sics.kompics.Init;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.network.Network;
import se.sics.nat.stun.server.SSNetworkHook;
import se.sics.p2ptoolbox.util.filters.PortTrafficFilter;
import se.sics.p2ptoolbox.util.proxy.ComponentProxy;
import se.sics.p2ptoolbox.util.proxy.util.DummyNetwork;

/**
 *
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class ScenarioSSNetworkHook implements SSNetworkHook.Definition {

    private Positive<Network> network = null;

    @Override
    public SSNetworkHook.SetupResult setup(ComponentProxy proxy, SSNetworkHook.SetupInit setupInit) {
        if (network == null) {
            network = proxy.requires(Network.class);
        }
        Component[] comp = new Component[1];
        comp[0] = proxy.create(DummyNetwork.class, Init.NONE);
        proxy.connect(comp[0].getNegative(Network.class), network,
                new PortTrafficFilter(setupInit.adr.getPort()));
        return new SSNetworkHook.SetupResult(comp[0].getPositive(Network.class), comp);
    }

    @Override
    public void start(ComponentProxy proxy, SSNetworkHook.SetupResult setupResult,
            SSNetworkHook.StartInit startInit) {
        if (!startInit.started) {
            proxy.trigger(Start.event, setupResult.components[0].control());
        }
    }

    @Override
    public void preStop(ComponentProxy proxy, SSNetworkHook.Tear hookTear) {
    }
}