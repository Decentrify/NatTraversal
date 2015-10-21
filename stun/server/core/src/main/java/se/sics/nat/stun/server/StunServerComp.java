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

import com.google.common.base.Optional;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
import se.sics.kompics.timer.CancelTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;
import se.sics.kompics.timer.Timer;
import se.sics.ktoolbox.networkmngr.NetworkMngrConfig;
import se.sics.ktoolbox.networkmngr.NetworkMngrPort;
import se.sics.ktoolbox.networkmngr.events.Bind;
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

    private static final long CONFIG_TIMEOUT = 2000;

    private static final Logger LOG = LoggerFactory.getLogger(StunServerComp.class);
    private String logPrefix = "";

    private final Positive<NetworkMngrPort> networkMngr = requires(NetworkMngrPort.class);
    private final Positive<Network> network = requires(Network.class);
    private final Positive<Timer> timer = requires(Timer.class);

    private final KConfigCache config;
    private final NetworkSetupMngr networkSetupMngr;

    private Pair<UUID, UUID> bindingSelf;
    private Pair<DecoratedAddress, DecoratedAddress> self;
    private final List<DecoratedAddress> partners = new ArrayList<>();

    private UUID configTimeoutId;
    private final EchoMngr echoMngr;

    public StunServerComp(StunServerInit init) {
        this.config = init.config;
        this.logPrefix = "<" + config.getNodeId() + "> ";
        LOG.info("{}initiating...", logPrefix);
        
        this.partners.addAll(init.partners);
        this.networkSetupMngr = new NetworkSetupMngr();
        this.echoMngr = new EchoMngr();

        subscribe(handleStart, control);
    }

    Handler handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            LOG.info("{}starting...", logPrefix);
            networkSetupMngr.start();
        }
    };

    private void stunReady(Pair<DecoratedAddress, DecoratedAddress> self) {
        this.self = self;
        this.logPrefix = "<"+ self.getValue0().getIp().getHostAddress()
                + ":<" + self.getValue0().getPort() + ":" + self.getValue1().getPort() + ">:"
                + self.getValue0().getId() + ">";
        LOG.info("{}stun server ready", logPrefix);
        subscribe(echoMngr.handleEchoRequest, network);
    }

    private class NetworkSetupMngr {

        private InetAddress ip;
        private Pair<UUID, UUID> bindingReq;
        private Pair<DecoratedAddress, DecoratedAddress> adr = Pair.with(null, null);

        void start() {
            subscribe(handleBind, networkMngr);
            
            LOG.info("{}getting ip", logPrefix);
            Optional<String> localIp = config.read(NetworkMngrConfig.localIp);
            if (!localIp.isPresent()) {
                subscribe(handleConfig, timer);
                scheduleConfigTimeout();
            } else {
                setIp(localIp.get());
                bindPorts();
            }
        }

        private void setIp(String sIp) {
            try {
                ip = InetAddress.getByName(sIp);
                logPrefix = "<" + ip.getHostAddress() + ":" + config.getNodeId() + ">";
                LOG.info("{}ip set, binding ports...", logPrefix);
            } catch (UnknownHostException ex) {
                LOG.error("{}ip error:{}", logPrefix, ex.getMessage());
                throw new RuntimeException("ip error");
            }
        }

        Handler handleConfig = new Handler<ConfigTimeout>() {
            @Override
            public void handle(ConfigTimeout event) {
                Optional<String> localIp = config.read(NetworkMngrConfig.localIp);
                if (!localIp.isPresent()) {
                    LOG.error("{}no local ip set in config - check networkMngr", logPrefix);
                    throw new RuntimeException("no local ip set in config - check networkMngr");
                } else {
                    setIp(localIp.get());
                    bindPorts();
                }
            }
        };

        private void bindPorts() {
            Optional<Integer> port1 = config.read(StunServerConfig.stunServerPort1);
            if (!port1.isPresent()) {
                LOG.error("{}missing stun server port1", logPrefix);
                throw new RuntimeException("missing stun server port1");
            }
            Optional<Integer> port2 = config.read(StunServerConfig.stunServerPort2);
            if (!port2.isPresent()) {
                LOG.error("{}missing stun server port2", logPrefix);
                throw new RuntimeException("missing stun server port2");
            }
            DecoratedAddress adr1 = DecoratedAddress.open(ip, port1.get(), config.getNodeId());
            DecoratedAddress adr2 = DecoratedAddress.open(ip, port2.get(), config.getNodeId());
            bindingSelf = Pair.with(UUID.randomUUID(), UUID.randomUUID());
            LOG.info("{}binding:{}", logPrefix, adr1);
            trigger(new Bind.Request(bindingSelf.getValue0(), adr1, false), networkMngr);
            LOG.info("{}binding:{}", logPrefix, adr2);
            trigger(new Bind.Request(bindingSelf.getValue1(), adr2, false), networkMngr);
        }

        Handler handleBind = new Handler<Bind.Response>() {
            @Override
            public void handle(Bind.Response resp) {
                if (bindingSelf.getValue0().equals(resp.req.id)) {
                    LOG.info("{}bound adr1 port:{}", logPrefix, resp.boundPort);
                    DecoratedAddress adr1 = DecoratedAddress.open(ip, resp.boundPort, config.getNodeId());
                    adr = Pair.with(adr1, adr.getValue1());
                } else {
                    LOG.info("{}bound adr2 port:{}", logPrefix, resp.boundPort);
                    DecoratedAddress adr2 = DecoratedAddress.open(ip, resp.boundPort, config.getNodeId());
                    adr = Pair.with(adr.getValue0(), adr2);
                }
                if (adr.getValue0() != null && adr.getValue1() != null) {
                    stunReady(adr);
                }
            }
        };
    }

    //**************************************************************************
    private class EchoMngr {

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
                trigger(response, network);
            } else if (src.equals(self.getValue1())) {
                trigger(response, network);
            } else {
                LOG.error("{}unknown self:{}", new Object[]{logPrefix, src});
                throw new RuntimeException("unknown self:" + src);
            }
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
        public final List<DecoratedAddress> partners;

        public StunServerInit(KConfigCore config, List<DecoratedAddress> partners) {
            this.config = new KConfigCache(config);
            this.partners = partners;
        }
    }

    private void scheduleConfigTimeout() {
        if (configTimeoutId != null) {
            LOG.warn("{} double starting config timeout", logPrefix);
            return;
        }
        ScheduleTimeout spt = new ScheduleTimeout(CONFIG_TIMEOUT);
        ConfigTimeout ct = new ConfigTimeout(spt);
        spt.setTimeoutEvent(ct);
        configTimeoutId = ct.getTimeoutId();
        trigger(spt, timer);
    }

    private void cancelShuffleTimeout() {
        if (configTimeoutId == null) {
            LOG.warn("{} double stopping config timeout", logPrefix);
        }
        CancelTimeout cpt = new CancelTimeout(configTimeoutId);
        configTimeoutId = null;
        trigger(cpt, timer);
    }

    public class ConfigTimeout extends Timeout {

        public ConfigTimeout(ScheduleTimeout request) {
            super(request);
        }

        @Override
        public String toString() {
            return "CONFIG_TIMEOUT";
        }
    }
}
