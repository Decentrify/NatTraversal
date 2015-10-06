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

import java.net.InetAddress;
import se.sics.kompics.Component;
import se.sics.kompics.Start;
import se.sics.kompics.network.Network;
import se.sics.kompics.network.netty.NettyInit;
import se.sics.kompics.network.netty.NettyNetwork;
import se.sics.kompics.timer.Timer;
import se.sics.nat.hooks.NatNetworkHook;
import se.sics.nat.stun.client.SCNetworkHook;
import se.sics.p2ptoolbox.chunkmanager.ChunkManagerComp;
import se.sics.p2ptoolbox.chunkmanager.ChunkManagerConfig;
import se.sics.p2ptoolbox.util.config.SystemConfig;
import se.sics.p2ptoolbox.util.proxy.ComponentProxy;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class NatSetupHooks {

    public static SCNetworkHook.Definition getSimpleStunClientNetwork() {
        return new SCNetworkHook.Definition() {

            @Override
            public SCNetworkHook.SetupResult setup(ComponentProxy hookProxy, SCNetworkHook.SetupInit hookInit) {
                Component[] comp = new Component[1];
                //network
                comp[0] = hookProxy.create(NettyNetwork.class, new NettyInit(hookInit.adr));

                return new SCNetworkHook.SetupResult(comp[0].getPositive(Network.class), comp);
            }

            @Override
            public void start(ComponentProxy proxy, SCNetworkHook.SetupResult setupResult, SCNetworkHook.StartInit startInit) {
                if (!startInit.started) {
                    proxy.trigger(Start.event, setupResult.components[0].control());
                }
            }

            @Override
            public void preStop(ComponentProxy proxy, SCNetworkHook.Tear hookTear) {
            }
        };
    }

    public static NatNetworkHook.Definition getChunkMngrNetwork() {
        return new NatNetworkHook.Definition() {
            private InetAddress localInterface;
            private SystemConfig systemConfig;
            
            @Override
            public void tempHack(InetAddress localInterface, SystemConfig systemConfig) {
                this.localInterface = localInterface;
                this.systemConfig = systemConfig;
            }

            @Override
            public NatNetworkHook.SetupResult setup(ComponentProxy hookProxy, NatNetworkHook.SetupInit hookInit) {
                Component[] comp = new Component[2];
                if (!localInterface.equals(hookInit.adr.getIp())) {
                    System.setProperty("altBindIf", localInterface.getHostAddress());
                }
                //network
                comp[0] = hookProxy.create(NettyNetwork.class, new NettyInit(hookInit.adr));

                //chunkmanager
                comp[1] = hookProxy.create(ChunkManagerComp.class, new ChunkManagerComp.CMInit(systemConfig, new ChunkManagerConfig(systemConfig.config)));
                hookProxy.connect(comp[1].getNegative(Network.class), comp[0].getPositive(Network.class));
                hookProxy.connect(comp[1].getNegative(Timer.class), hookInit.timer);
                return new NatNetworkHook.SetupResult(comp[1].getPositive(Network.class), comp);
            }

            @Override
            public void start(ComponentProxy proxy, NatNetworkHook.SetupResult setupResult, NatNetworkHook.StartInit startInit) {
                if (!startInit.started) {
                    proxy.trigger(Start.event, setupResult.components[0].control());
                    proxy.trigger(Start.event, setupResult.components[1].control());
                }
            }

            @Override
            public void preStop(ComponentProxy proxy, NatNetworkHook.Tear hookTear) {
            }
        };
    }
}
