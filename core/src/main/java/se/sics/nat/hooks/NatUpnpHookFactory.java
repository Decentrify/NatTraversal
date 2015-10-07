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

import java.util.UUID;
import se.sics.kompics.Component;
import se.sics.kompics.Handler;
import se.sics.kompics.Start;
import se.sics.nat.NatDetectionComp.NatUpnpHookParent;
import se.sics.nat.hooks.NatUpnpHook.Definition;
import se.sics.nat.hooks.NatUpnpHook.SetupInit;
import se.sics.nat.hooks.NatUpnpHook.SetupResult;
import se.sics.nat.hooks.NatUpnpHook.StartInit;
import se.sics.nat.hooks.NatUpnpHook.TearInit;
import se.sics.nat.stun.upnp.UpnpComp;
import se.sics.nat.stun.upnp.UpnpPort;
import se.sics.nat.stun.upnp.msg.UpnpReady;
import se.sics.p2ptoolbox.util.proxy.ComponentProxy;

/**
 *
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class NatUpnpHookFactory {

    public final static long UPNP_SEED = 1234;

    public static Definition getGarageUpnp() {
        return new Definition() {

            @Override
            public NatUpnpHook.SetupResult setup(ComponentProxy hookProxy, final NatUpnpHookParent hookParent,
                    final SetupInit hookInit) {
                Component[] comp = new Component[1];
                comp[0] = hookProxy.create(UpnpComp.class, new UpnpComp.UpnpInit(UPNP_SEED, "nat upnp"));
                hookProxy.subscribe(new Handler<UpnpReady>() {
                    @Override
                    public void handle(UpnpReady ready) {
                        hookParent.onResult(ready);
                    }
                }, comp[0].getPositive(UpnpPort.class));
                return new SetupResult(comp);
            }

            @Override
            public void start(ComponentProxy proxy, NatUpnpHookParent hookParent, SetupResult setupResult,
                    StartInit startInit) {
                if (!startInit.started) {
                    proxy.trigger(Start.event, setupResult.comp[0].control());
                }
            }

            @Override
            public void preStop(ComponentProxy proxy, NatUpnpHookParent hookParent, SetupResult setupResult,
                    TearInit hookTear) {
            }
        };
    }

    public static Definition getNoUpnp() {
        return new Definition() {

            @Override
            public NatUpnpHook.SetupResult setup(ComponentProxy hookProxy, NatUpnpHookParent hookParent,
                    final SetupInit hookInit) {
                Component[] comp = new Component[0];
                return new NatUpnpHook.SetupResult(comp);
            }

            @Override
            public void start(ComponentProxy proxy, NatUpnpHookParent hookParent, SetupResult setupResult,
                    StartInit startInit) {
                hookParent.onResult(new UpnpReady(UUID.randomUUID(), null));
            }

            @Override
            public void preStop(ComponentProxy proxy, NatUpnpHookParent hookParent, SetupResult setupResult,
                    TearInit hookTear) {
            }
        };
    }
}
