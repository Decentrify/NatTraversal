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
import se.sics.kompics.Start;
import se.sics.kompics.network.Network;
import se.sics.kompics.network.netty.NettyInit;
import se.sics.kompics.network.netty.NettyNetwork;
import se.sics.nat.NatTraverserComp.NatNetworkHookParent;
import se.sics.nat.hooks.NatNetworkHook.Definition;
import se.sics.nat.hooks.NatNetworkHook.SetupInit;
import se.sics.nat.hooks.NatNetworkHook.SetupResult;
import se.sics.nat.hooks.NatNetworkHook.StartInit;
import se.sics.nat.hooks.NatNetworkHook.TearInit;
import se.sics.p2ptoolbox.util.proxy.ComponentProxy;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class NatNetworkHookFactory {
    public static Definition getSimpleNettyNetwork() {
        return new Definition() {

            @Override
            public SetupResult setup(ComponentProxy hookProxy, NatNetworkHookParent hookParent, SetupInit hookInit) {
                Component[] comp = new Component[1];
                if (!hookParent.getLocalInterface().equals(hookInit.adr.getIp())) {
                    System.setProperty("altBindIf", hookParent.getLocalInterface().getHostAddress());
                }
                //network
                comp[0] = hookProxy.create(NettyNetwork.class, new NettyInit(hookInit.adr));
                return new NatNetworkHook.SetupResult(comp[0].getPositive(Network.class), comp);
            }

            @Override
            public void start(ComponentProxy proxy, NatNetworkHookParent hookParent, SetupResult setupResult, 
                    StartInit startInit) {
                if (!startInit.started) {
                    proxy.trigger(Start.event, setupResult.components[0].control());
                }
            }

            @Override
            public void preStop(ComponentProxy proxy, NatNetworkHookParent hookParent, SetupResult setupResult, 
                    TearInit hookTear) {
            }
        };
    }
    
    public static Definition getSimpleNetwork(final Positive<Network> network) {
        return new Definition() {
            
            @Override
            public SetupResult setup(ComponentProxy hookProxy, NatNetworkHookParent hookParent, SetupInit hookInit) {
                Component[] comp = new Component[0];
                return new NatNetworkHook.SetupResult(network, comp);
            }

            @Override
            public void start(ComponentProxy proxy, NatNetworkHookParent hookParent, SetupResult setupResult, 
                    StartInit startInit) {
            }

            @Override
            public void preStop(ComponentProxy proxy, NatNetworkHookParent hookParent, SetupResult setupResult, 
                    TearInit hookTear) {
            }
        };
    }
}
