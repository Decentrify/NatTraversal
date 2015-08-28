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

package se.sics.nat.pm.core;

import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.Component;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Fault;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.Stop;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.Timer;
import se.sics.nat.emulator.NatEmulatorComp;
import se.sics.nat.emulator.NatEmulatorComp.NatEmulatorInit;
import se.sics.nat.pm.client.PMClientComp;
import se.sics.nat.pm.client.PMClientComp.PMClientInit;
import se.sics.nat.pm.client.PMClientPort;
import se.sics.nat.pm.client.msg.Update;
import se.sics.p2ptoolbox.croupier.CroupierPort;
import se.sics.p2ptoolbox.croupier.msg.CroupierSample;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;
import se.sics.p2ptoolbox.util.traits.Nated;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class PMClientHostComp extends ComponentDefinition {
    private static final Logger LOG = LoggerFactory.getLogger(PMClientHostComp.class);
    private String logPrefix = "";

    private Positive<Network> network = requires(Network.class);
    private Positive<Timer> timer = requires(Timer.class);
    
    private final PMClientInit pmClientInit;
    private final NatEmulatorInit natEmulatorInit;
    
    private Component natEmulator;
    private Component pmClient;
    
    private Set<DecoratedAddress> publicSample;
    
    public PMClientHostComp(PMClientHostInit init) {
        LOG.info("{}initiating", logPrefix);

        this.natEmulatorInit = init.natEmulatorInit;
        this.pmClientInit = init.pmClientInit;
        
        this.publicSample = init.publicSample;

        subscribe(handleStart, control);
        subscribe(handleStop, control);
    }

    Handler handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            LOG.info("{}starting", logPrefix);
            connectNatEmulator();
            connectPMClient();
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
        natEmulator = create(NatEmulatorComp.class, natEmulatorInit);
        connect(natEmulator.getNegative(Timer.class), timer);
        connect(natEmulator.getNegative(Network.class), network);
        trigger(Start.event, natEmulator.control());
    }
    
    private void connectPMClient() {
        pmClient = create(PMClientComp.class, pmClientInit);
        connect(pmClient.getNegative(Timer.class), timer);
        connect(pmClient.getNegative(Network.class), natEmulator.getPositive(Network.class));
        trigger(Start.event, pmClient.control());
        subscribe(handleUpdate, pmClient.getPositive(PMClientPort.class));
        trigger(new CroupierSample(1, publicSample, new HashSet<DecoratedAddress>()), pmClient.getNegative(CroupierPort.class));
    }
    
    Handler handleUpdate = new Handler<Update>() {
        @Override
        public void handle(Update event) {
            LOG.info("{}self update:{}", logPrefix, event.self.getTrait(Nated.class).getParents());
        }
    };
    
    public static class PMClientHostInit extends Init<PMClientHostComp> {
        public final NatEmulatorInit natEmulatorInit;
        public final PMClientInit pmClientInit;
        public final Set<DecoratedAddress> publicSample;

        public PMClientHostInit(NatEmulatorInit natEmulatorInit, PMClientInit pmClientInit, Set<DecoratedAddress> publicSample) {
            this.natEmulatorInit = natEmulatorInit;
            this.pmClientInit = pmClientInit;
            this.publicSample = publicSample;
        }
    }
}
