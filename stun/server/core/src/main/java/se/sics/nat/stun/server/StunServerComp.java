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
package se.sics.nat.stun.server;

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
import se.sics.kompics.network.Network;
import se.sics.kompics.network.Transport;
import se.sics.kompics.timer.Timer;
import se.sics.nat.stun.msg.StunEcho;
import se.sics.p2ptoolbox.util.config.KConfigCache;
import se.sics.p2ptoolbox.util.config.KConfigCore;
import se.sics.p2ptoolbox.util.network.ContentMsg;
import se.sics.p2ptoolbox.util.network.impl.BasicContentMsg;
import se.sics.p2ptoolbox.util.network.impl.BasicHeader;
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

    private final KConfigCache config;
    private final Pair<DecoratedAddress, DecoratedAddress> self;

    private final Positive<Network> network1;
    private final Positive<Network> network2;
    private final Positive<Timer> timer = requires(Timer.class);

    private final List<DecoratedAddress> partners;

    public StunServerComp(StunServerInit init) {
        this.config = init.config;
        this.network1 = init.network1;
        this.network2 = init.network2;
        this.self = init.self;
        this.logPrefix = getPrefix(self);
        LOG.info("{}initiating...", logPrefix);

        this.partners = null; //TODO Alex bootstrap partners

        subscribe(handleStart, control);
        subscribe(handleEchoRequest, network1);
        subscribe(handleEchoRequest, network2);
    }

    Handler handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            LOG.info("{}starting...", logPrefix);
        }
    };

    ClassMatchedHandler handleEchoRequest
            = new ClassMatchedHandler<StunEcho.Request, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, StunEcho.Request>>() {

                @Override
                public void handle(StunEcho.Request content, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, StunEcho.Request> container) {
                    DecoratedAddress recSelf = container.getDestination();
                    LOG.debug("{}received:{} from:{} on:{}",
                            new Object[]{logPrefix, content, container.getSource().getBase(), recSelf.getBase()});
                    switch (content.type) {
                        case SIP_SP: {
                            sendResponse(content.answer(container.getSource()),
                                    container.getDestination(), container.getSource());
                        }
                        break;
                        case SIP_DP: {
                            sendResponse(content.answer(container.getSource()),
                                    self.getValue1(), container.getSource());
                        }
                        break;
                        case DIP_DP: {
                            if (container.getSource().getId().equals(content.target.getId())) {
                                sendResponse(content, self.getValue0(), getPartner());
                            } else {
                                sendResponse(content.answer(), self.getValue1(), content.target);
                            }
                        }
                        break;
                    }
                }
            };

    private void sendResponse(StunEcho echo, DecoratedAddress src, DecoratedAddress dst) {
        DecoratedHeader<DecoratedAddress> responseHeader = new DecoratedHeader(new BasicHeader(
                src, dst, Transport.UDP), null, null);
        ContentMsg response = new BasicContentMsg(responseHeader, echo);
        LOG.debug("{}sending:{} from:{} to:{}",
                new Object[]{logPrefix, echo, responseHeader.getSource().getBase(),
                    responseHeader.getDestination().getBase()});
        if (src.equals(self.getValue0())) {
            trigger(response, network1);
        } else if (src.equals(self.getValue1())) {
            trigger(response, network2);
        } else {
            LOG.error("{}unknown self:{}", new Object[]{logPrefix, src});
            throw new RuntimeException("unknown self:" + src);
        }
    }

    private DecoratedAddress getPartner() {
        return partners.get(0);
    }

    private String getPrefix(Pair<DecoratedAddress, DecoratedAddress> self) {
        String ret = self.getValue0().getIp().toString();
        ret += ":<" + self.getValue0().getBase().getPort() + "," + self.getValue1().getBase().getPort() + ">:";
        ret += self.getValue0().getBase().getId() + " ";
        return ret;
    }

    public static class StunServerInit extends Init<StunServerComp> {

        public final KConfigCache config;
        public final Pair<DecoratedAddress, DecoratedAddress> self;
        public final Positive<Network> network1;
        public final Positive<Network> network2;

        public StunServerInit(KConfigCore config, Pair<DecoratedAddress, DecoratedAddress> self,
                Positive<Network> network1, Positive<Network> network2) {
            this.config = new KConfigCache(config);
            this.self = self;
            this.network1 = network1;
            this.network2 = network2;
        }
    }
}
