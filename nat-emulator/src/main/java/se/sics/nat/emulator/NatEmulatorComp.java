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

package se.sics.nat.emulator;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.Stop;
import se.sics.kompics.network.Msg;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.Timer;
import se.sics.ktoolbox.nat.network.Nat;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class NatEmulatorComp extends ComponentDefinition {
     private static final Logger LOG = LoggerFactory.getLogger(NatEmulatorComp.class);
     private String logPrefix = "";
     
     private Positive<Network> localNetwork = requires(Network.class);
     private Positive<Timer> timer = requires(Timer.class);
     private Negative<Network> network = provides(Network.class);
     
     private final Nat natType;
     
     private Map<BasicAddress, BasicAddress> natMappings;
     
     public NatEmulatorComp(NatEmulatorInit init) {
         this.natType = init.natType;
         logPrefix = natType.toString() + " ";
         LOG.info("{}initiating", logPrefix);
         
         this.natMappings = new HashMap<BasicAddress, BasicAddress>();
         
         subscribe(handleStart, control);
         subscribe(handleStop, control);
         subscribe(handleIncoming, network);
         subscribe(handleOutgoing, localNetwork);
     }
     
     Handler handleStart = new Handler<Start>() {
         @Override
         public void handle(Start event) {
             LOG.info("{}starting", logPrefix);
         }
     };
     Handler handleStop = new Handler<Stop>() { 
         @Override
         public void handle(Stop event) {
             LOG.info("{}stopping", logPrefix);
         }
     };
     //*************************************************************************
     Handler handleIncoming = new Handler<Msg>() {
         @Override
         public void handle(Msg msg) {
             LOG.trace("{}received incoming:{}", logPrefix, msg);
             trigger(msg, localNetwork);
         }
     };
     
     Handler handleOutgoing = new Handler<Msg>() {
         @Override
         public void handle(Msg msg) {
             LOG.trace("{}received outgoing:{}", logPrefix, msg);
             trigger(msg, network);
         }
     };
     
     public static class NatEmulatorInit extends Init<NatEmulatorComp> {
         public final Nat natType;
         
         public NatEmulatorInit(Nat natType) {
             this.natType = natType;
         }
     }
}
