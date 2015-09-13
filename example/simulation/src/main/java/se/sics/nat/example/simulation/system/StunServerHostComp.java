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
package se.sics.nat.example.simulation.system;

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
import se.sics.ktoolbox.nat.stun.client.StunClientComp;
import se.sics.ktoolbox.nat.stun.server.StunServerComp;
import se.sics.ktoolbox.nat.stun.server.StunServerComp.StunServerInit;
import se.sics.nat.emulator.NatEmulatorComp;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class StunServerHostComp extends ComponentDefinition {

    private Logger LOG = LoggerFactory.getLogger(StunServerHostComp.class);
    private String logPrefix = "";
    
    private Positive<Timer> timer = requires(Timer.class);
    private Positive<Network> network = requires(Network.class);
    
    private Component stunServer;
    private final Init stunServerInit;
    
    public StunServerHostComp(StunServerHostInit init) {
        LOG.info("{}initializing...", logPrefix);
        this.stunServerInit = init.stunServerInit;
        
        subscribe(handleStart, control);
        subscribe(handleStop, control);
    }
    
    //*************************CONTROL******************************************
    Handler handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            LOG.info("{}starting...", logPrefix);
            connectStunServer();
        }
    };
    
    Handler handleStop = new Handler<Stop>() {
        @Override
        public void handle(Stop event) {
            LOG.info("{}stopping...", logPrefix);
        }
    };
    
    @Override
    public Fault.ResolveAction handleFault(Fault fault) {
        LOG.error("{}child component failure:{}", logPrefix, fault);
        System.exit(1);
        return Fault.ResolveAction.RESOLVED;
    }
    //**************************************************************************
    private void connectStunServer() {
        stunServer = create(StunServerComp.class, stunServerInit);
        connect(stunServer.getNegative(Timer.class), timer);
        connect(stunServer.getNegative(Network.class), network);
        trigger(Start.event, stunServer.control());
    }
    
    public static class StunServerHostInit extends Init<StunServerHostComp> {
        public final StunServerInit stunServerInit;
        
        public StunServerHostInit(StunServerInit stunServerInit) {
            this.stunServerInit = stunServerInit;
        }
    }
}
