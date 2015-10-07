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

package se.sics.nat.stun.client.hooks;

import se.sics.kompics.Component;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.network.Network;
import se.sics.kompics.network.netty.NettyInit;
import se.sics.kompics.network.netty.NettyNetwork;
import se.sics.nat.stun.client.StunClientComp.SCNetworkHookParent;
import se.sics.nat.stun.client.hooks.SCNetworkHook.Definition;
import se.sics.nat.stun.client.hooks.SCNetworkHook.SetupInit;
import se.sics.nat.stun.client.hooks.SCNetworkHook.SetupResult;
import se.sics.nat.stun.client.hooks.SCNetworkHook.StartInit;
import se.sics.nat.stun.client.hooks.SCNetworkHook.TearInit;
import se.sics.p2ptoolbox.util.proxy.ComponentProxy;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class SCNetworkHookFactory {
    public static Definition getSimpleNettyNetwork() {
       return new Definition() {

            @Override
            public SetupResult setup(ComponentProxy hookProxy, SCNetworkHookParent hookParent, SetupInit hookInit) {
                Component[] comp = new Component[1];
                //network
                comp[0] = hookProxy.create(NettyNetwork.class, new NettyInit(hookInit.adr));
                return new SetupResult(comp[0].getPositive(Network.class), comp);
            }

            @Override
            public void start(ComponentProxy proxy, SCNetworkHookParent hookParent, SetupResult setupResult, 
                    StartInit startInit) {
                if (!startInit.started) {
                    proxy.trigger(Start.event, setupResult.components[0].control());
                }
            }

            @Override
            public void preStop(ComponentProxy proxy, SCNetworkHookParent hookParent, 
                    SetupResult setupResult, TearInit hookTear) {
            }
        };
    }
    
    public static Definition getSimpleNetwork(final Positive<Network> network) {
        return new Definition() {

            @Override
            public SetupResult setup(ComponentProxy hookProxy, SCNetworkHookParent hookParent, SetupInit hookInit) {
                return new SCNetworkHook.SetupResult(network, new Component[0]);
            }

            @Override
            public void start(ComponentProxy proxy, SCNetworkHookParent hookParent, SetupResult setupResult, 
                    StartInit startInit) {
            }

            @Override
            public void preStop(ComponentProxy proxy, SCNetworkHookParent hookParent, SetupResult setupResult, 
                    TearInit hookTear) {
            }
        };
    }
}
