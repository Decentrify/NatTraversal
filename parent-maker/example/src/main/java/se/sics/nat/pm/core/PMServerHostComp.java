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
import se.sics.nat.pm.client.PMClientComp.PMClientInit;
import se.sics.nat.pm.server.PMServerComp;
import se.sics.nat.pm.server.PMServerComp.PMServerInit;
import se.sics.nat.pm.server.PMServerPort;
import se.sics.nat.pm.server.msg.Update;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class PMServerHostComp extends ComponentDefinition {
    private static final Logger LOG = LoggerFactory.getLogger(PMServerHostComp.class);
    private String logPrefix = "";

    private Positive<Network> network = requires(Network.class);
    private Positive<Timer> timer = requires(Timer.class);
    
    private final PMServerInit pmServerInit;
    
    private Component pmServer;
    
    public PMServerHostComp(PMServerHostInit init) {
        LOG.info("{}initiating", logPrefix);

        this.pmServerInit = init.pmServerInit;

        subscribe(handleStart, control);
        subscribe(handleStop, control);
    }

    Handler handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            LOG.info("{}starting", logPrefix);
            connectPMServer();
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

    private void connectPMServer() {
        pmServer = create(PMServerComp.class, pmServerInit);
        connect(pmServer.getNegative(Timer.class), timer);
        connect(pmServer.getNegative(Network.class), network);
        trigger(Start.event, pmServer.control());
        subscribe(handleUpdate, pmServer.getPositive(PMServerPort.class));
    }
    
    Handler handleUpdate = new Handler<Update>() {
        @Override
        public void handle(Update event) {
            LOG.info("{}updated children:{}", logPrefix, event.registeredChildren);
        }
    };
    
    public static class PMServerHostInit extends Init<PMServerHostComp> {
        public final PMServerInit pmServerInit;

        public PMServerHostInit(PMServerInit pmServerInit) {
            this.pmServerInit = pmServerInit;
        }
    }
}
