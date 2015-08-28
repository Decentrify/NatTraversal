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
package se.sics.nat.hp.client;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.Stop;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.CancelPeriodicTimeout;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.Timeout;
import se.sics.kompics.timer.Timer;
import se.sics.nat.hp.common.HPConfig;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class HPClientComp extends ComponentDefinition {

    private static final Logger LOG = LoggerFactory.getLogger(HPClientComp.class);
    private String logPrefix = "";

    private final Positive<Network> network = requires(Network.class);
    private final Positive<Timer> timer = requires(Timer.class);
    
    private final HPConfig config;
    private final DecoratedAddress self;
    
    private UUID internalStateCheckId;
    
    public HPClientComp(HPClientInit init) {
        this.config = init.config;
        this.self = init.self;
        this.logPrefix = self.getBase() + " ";
        LOG.info("{}initiating...", logPrefix);
        
        subscribe(handleStart, control);
        subscribe(handleStop, control);
        subscribe(handleInternalStateCheck, timer);
    }
    
    Handler handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            LOG.info("{}starting...", logPrefix);
            scheduleInternalStateCheck();
        }
    };

    Handler handleStop = new Handler<Stop>() {
        @Override
        public void handle(Stop event) {
            LOG.info("{}stopping...", logPrefix);
            cancelInternalStateCheck();
        }
    };

    Handler handleInternalStateCheck = new Handler<PeriodicInternalStateCheck>() {
        @Override
        public void handle(PeriodicInternalStateCheck event) {
        }
    };
    
    public static class HPClientInit extends Init<HPClientComp> {
        public final HPConfig config;
        public final DecoratedAddress self;
        
        public HPClientInit(HPConfig config, DecoratedAddress self) {
            this.config = config;
            this.self = self;
        }
    }
    
    private void scheduleInternalStateCheck() {
        if (internalStateCheckId != null) {
            LOG.warn("{}double starting internal state check timeout", logPrefix);
            return;
        }
        SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(config.internalStateCheck, config.internalStateCheck);
        PeriodicInternalStateCheck sc = new PeriodicInternalStateCheck(spt);
        spt.setTimeoutEvent(sc);
        internalStateCheckId = sc.getTimeoutId();
        trigger(spt, timer);
    }

    private void cancelInternalStateCheck() {
        if (internalStateCheckId == null) {
            LOG.warn("{}double stopping internal state check timeout", logPrefix);
            return;
        }
        CancelPeriodicTimeout cpt = new CancelPeriodicTimeout(internalStateCheckId);
        internalStateCheckId = null;
        trigger(cpt, timer);

    }

    public static class PeriodicInternalStateCheck extends Timeout {

        public PeriodicInternalStateCheck(SchedulePeriodicTimeout spt) {
            super(spt);
        }
    }
}
