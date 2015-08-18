/*
 * Copyright (C) 2009 Swedish Institute of Computer Science (SICS) Copyright (C)
 * 2009 Royal Institute of Technology (KTH)
 *
 * GVoD is free software; you can redistribute it and/or
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
package se.sics.ktoolbox.nat.stun.server;

import java.util.List;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.ClassMatchedHandler;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.Stop;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.Timer;
import se.sics.ktoolbox.nat.stun.msg.Echo;
import se.sics.p2ptoolbox.util.network.impl.BasicContentMsg;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;
import se.sics.p2ptoolbox.util.network.impl.DecoratedHeader;

/**
 * A partner is required to provide the stun service. Only nodes with the same
 * polarity can be partners - even or odd IDs can be partners. The reason is
 * that clients can then send 2 echo requests to 2 servers in parallel, knowing
 * that it won't mess up the NAT type identification by creating a NAT binding
 * to a partner as a side-effect of parallelizing the first Echo test.
 *
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class StunServerComp extends ComponentDefinition {

    private static final Logger LOG = LoggerFactory.getLogger(StunServerComp.class);
    private String logPrefix = "";

    private Positive<Network> networkAPort = requires(Network.class);
    private Positive<Network> networkBPort = requires(Network.class);
    private Positive<Timer> timerPort = requires(Timer.class);

    private final StunServerConfig stunServerConfig;
    private final Pair<DecoratedAddress, DecoratedAddress> self;
    private final List<DecoratedAddress> partners;

    public StunServerComp(StunServerInit init) {
        this.self = init.self;
        this.logPrefix = getPrefix(self);
        LOG.info("{}initiating...");

        this.partners = init.partners;
        this.stunServerConfig = init.stunServerConfig;

        subscribe(handleStart, control);
        subscribe(handleStop, control);
    }

    Handler handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            LOG.info("{}starting...");
        }
    };

    Handler handleStop = new Handler<Stop>() {
        @Override
        public void handle(Stop event) {
            LOG.info("{} stopping...");
        }
    };

    //**************************************************************************
    private class EchoMngr {

        ClassMatchedHandler handleEchoReq
                = new ClassMatchedHandler<Echo.Request, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Echo.Request>>() {

                    @Override
                    public void handle(Echo.Request content, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Echo.Request> container) {
                        DecoratedAddress recSelf = container.getDestination();
                        LOG.debug("{}received:{} from:{} on:{}",
                                new Object[]{logPrefix, content, container.getSource().getBase(), recSelf.getBase()});

                    }
                };

    }

    private String getPrefix(Pair<DecoratedAddress, DecoratedAddress> self) {
        String ret = self.getValue0().getIp().toString();
        ret += ":<" + self.getValue0().getBase().getPort() + "," + self.getValue1().getBase().getPort() + ">:";
        ret += self.getValue0().getBase().getId();
        return ret;
    }

    public static class StunServerInit extends Init<StunServerComp> {

        public final StunServerConfig stunServerConfig;
        public final Pair<DecoratedAddress, DecoratedAddress> self;
        public final List<DecoratedAddress> partners;

        public StunServerInit(StunServerConfig stunServerConfig, Pair<DecoratedAddress, DecoratedAddress> self, List<DecoratedAddress> partners) {
            this.stunServerConfig = stunServerConfig;
            this.self = self;
            this.partners = partners;
        }
    }

    public static class StunServerConfig {

        public final int NUM_NEW_PARTNERS_PER_CYCLE = 2;
    }
}
