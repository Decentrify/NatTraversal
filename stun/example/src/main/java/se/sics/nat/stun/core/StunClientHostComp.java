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
package se.sics.nat.stun.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.Component;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Fault;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.Stop;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.Timer;
import se.sics.ktoolbox.nat.network.Nat;
import se.sics.ktoolbox.nat.stun.client.StunClientComp;
import se.sics.ktoolbox.nat.stun.client.StunClientComp.StunClientInit;
import se.sics.nat.emulator.NatEmulatorComp;
import se.sics.nat.emulator.NatEmulatorComp.NatEmulatorInit;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class StunClientHostComp extends ComponentDefinition {

    private static final Logger LOG = LoggerFactory.getLogger(StunClientHostComp.class);
    private String logPrefix = "";

    private Positive<Network> network = requires(Network.class);
    private Positive<Timer> timer = requires(Timer.class);

    private final Nat natType;
    private final StunClientInit stunClientInit;
    
    private Component natEmulator;
    private Component stunClient;

    public StunClientHostComp(StunClientHostInit init) {
        LOG.info("{}initiating", logPrefix);
        this.natType = init.natType;
        this.stunClientInit = init.stunClientInit;

        subscribe(handleStart, control);
        subscribe(handleStop, control);
    }

    Handler handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            LOG.info("{}starting", logPrefix);
            connectNatEmulator();
            connectStunClient();
        }
    };
    Handler handleStop = new Handler<Stop>() {
        @Override
        public void handle(Stop event) {
            LOG.info("{}stopping", logPrefix);
        }
    };

    public Fault.ResolveAction handleFault(Fault fault) {
        LOG.error("{}host exception", logPrefix);
        System.exit(1);
        return Fault.ResolveAction.RESOLVED;
    }
    //*************************************************************************

    private void connectNatEmulator() {
        natEmulator = create(NatEmulatorComp.class, new NatEmulatorInit(natType));
        connect(natEmulator.getNegative(Timer.class), timer);
        connect(natEmulator.getNegative(Network.class), network);
        trigger(Start.event, natEmulator.control());
    }
    
    private void connectStunClient() {
        stunClient = create(StunClientComp.class, stunClientInit);
        connect(stunClient.getNegative(Timer.class), timer);
        connect(stunClient.getNegative(Network.class), natEmulator.getPositive(Network.class));
        trigger(Start.event, stunClient.control());
    }
    
    public static class StunClientHostInit extends Init<StunClientHostComp> {
        public final Nat natType;
        public final StunClientInit stunClientInit;

        public StunClientHostInit(Nat natType, StunClientInit stunClientInit) {
            this.natType = natType;
            this.stunClientInit = stunClientInit;
        }
    }
}
