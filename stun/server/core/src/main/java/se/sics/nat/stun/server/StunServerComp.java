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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.Channel;
import se.sics.kompics.ChannelFilter;
import se.sics.kompics.ClassMatchedHandler;
import se.sics.kompics.Component;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.ControlPort;
import se.sics.kompics.Fault;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.KompicsEvent;
import se.sics.kompics.Negative;
import se.sics.kompics.Port;
import se.sics.kompics.PortType;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.Stop;
import se.sics.kompics.network.Network;
import se.sics.kompics.network.Transport;
import se.sics.kompics.timer.Timer;
import se.sics.nat.stun.msg.StunEcho;
import se.sics.p2ptoolbox.util.network.ContentMsg;
import se.sics.p2ptoolbox.util.network.impl.BasicContentMsg;
import se.sics.p2ptoolbox.util.network.impl.BasicHeader;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;
import se.sics.p2ptoolbox.util.network.impl.DecoratedHeader;
import se.sics.p2ptoolbox.util.proxy.ComponentProxy;

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

    private Positive<Network> network1;
    private Positive<Network> network2;
    private final Positive<Timer> timer = requires(Timer.class);

    private final Pair<DecoratedAddress, DecoratedAddress> self;
    private final List<DecoratedAddress> partners;
    private final EchoMngr echoMngr;
    private final HookTracker hookTracker;

    public StunServerComp(StunServerInit init) {
        this.self = init.self;
        this.logPrefix = getPrefix(self);
        LOG.info("{}initiating...", logPrefix);

        this.echoMngr = new EchoMngr();
        this.hookTracker = new HookTracker(init.networkHookDefinition);
        this.partners = init.partners;

        subscribe(handleStart, control);
        subscribe(handleStop, control);
    }

    Handler handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            LOG.info("{}starting...", logPrefix);
            hookTracker.setupHook1();
            hookTracker.setupHook2();
            subscribe(echoMngr.handleEchoRequest, network1);
            subscribe(echoMngr.handleEchoRequest, network2);
        }
    };

    Handler handleStop = new Handler<Stop>() {
        @Override
        public void handle(Stop event) {
            LOG.info("{} stopping...", logPrefix);
            hookTracker.tearDown1();
            hookTracker.tearDown2();
        }
    };

    public Fault.ResolveAction handleFault(Fault fault) {
        LOG.error("{}fault:{} from component:{} - restarting hook...", new Object[]{logPrefix, fault.getCause().getMessage(),
            fault.getSourceCore().id()});
        hookTracker.restartHook(fault.getSourceCore().id());

        return Fault.ResolveAction.RESOLVED;
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
                trigger(response, network1);
            } else if(src.equals(self.getValue1())) {
                trigger(response, network2);
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

    //**************************HOOK_PARENT*************************************
    public class HookTracker implements ComponentProxy {

        private final SSNetworkHook.Definition networkHookDefinition;
        private final Map<UUID, Integer> compToHook;
        private Component[] networkHook1;
        private Component[] networkHook2;

        public HookTracker(SSNetworkHook.Definition networkHookDefinition) {
            this.networkHookDefinition = networkHookDefinition;
            this.compToHook = new HashMap<>();
        }

        private void setupHook1() {
            LOG.info("{}setting up network hook1",
                    new Object[]{logPrefix});
            SSNetworkHook.InitResult result = networkHookDefinition.setUp(this, new SSNetworkHook.Init(self.getValue0()));
            networkHook1 = result.components;
            for (Component component : networkHook1) {
                compToHook.put(component.id(), 1);
            }
            network1 = result.network;
        }

        private void setupHook2() {
            LOG.info("{}setting up network hook2",
                    new Object[]{logPrefix});
            SSNetworkHook.InitResult result = networkHookDefinition.setUp(this, new SSNetworkHook.Init(self.getValue1()));
            networkHook2 = result.components;
            for (Component component : networkHook2) {
                compToHook.put(component.id(), 2);
            }
            network2 = result.network;
        }

        private void restartHook(UUID compId) {
            Integer hookNr = compToHook.get(compId);
            switch (hookNr) {
                case 1:
                    tearDown1();
                    setupHook1();
                    break;
                case 2:
                    tearDown2();
                    setupHook2();
                    break;
            }
        }

        private void tearDown1() {
            LOG.info("{}tearing down hook1", new Object[]{logPrefix});

            networkHookDefinition.tearDown(this, new SSNetworkHook.Tear(networkHook1));
            for (Component component : networkHook1) {
                compToHook.remove(component.id());
            }
            networkHook1 = null;
            network1 = null;
        }

        private void tearDown2() {
            LOG.info("{}tearing down hook2", new Object[]{logPrefix});

            networkHookDefinition.tearDown(this, new SSNetworkHook.Tear(networkHook2));
            for (Component component : networkHook2) {
                compToHook.remove(component.id());
            }
            networkHook2 = null;
            network2 = null;
        }

        //*******************************PROXY**********************************
        @Override
        public <P extends PortType> void trigger(KompicsEvent e, Port<P> p) {
            StunServerComp.this.trigger(e, p);
        }

        @Override
        public <T extends ComponentDefinition> Component create(Class<T> definition, Init<T> initEvent) {
            return StunServerComp.this.create(definition, initEvent);
        }

        @Override
        public <T extends ComponentDefinition> Component create(Class<T> definition, Init.None initEvent) {
            return StunServerComp.this.create(definition, initEvent);
        }

        @Override
        public <P extends PortType> Channel<P> connect(Positive<P> positive, Negative<P> negative) {
            return StunServerComp.this.connect(negative, positive);
        }

        @Override
        public <P extends PortType> Channel<P> connect(Negative<P> negative, Positive<P> positive) {
            return StunServerComp.this.connect(negative, positive);
        }

        @Override
        public <P extends PortType> void disconnect(Negative<P> negative, Positive<P> positive) {
            StunServerComp.this.disconnect(negative, positive);
        }

        @Override
        public <P extends PortType> void disconnect(Positive<P> positive, Negative<P> negative) {
            StunServerComp.this.disconnect(negative, positive);
        }

        @Override
        public Negative<ControlPort> getControlPort() {
            return StunServerComp.this.control;
        }

        @Override
        public <P extends PortType> Positive<P> requires(Class<P> portType) {
            return StunServerComp.this.requires(portType);
        }

        @Override
        public <P extends PortType> Negative<P> provides(Class<P> portType) {
            return StunServerComp.this.provides(portType);
        }

        @Override
        public <P extends PortType> Channel<P> connect(Positive<P> positive, Negative<P> negative, ChannelFilter filter) {
            return StunServerComp.this.connect(negative, positive, filter);
        }

        @Override
        public <P extends PortType> Channel<P> connect(Negative<P> negative, Positive<P> positive, ChannelFilter filter) {
            return StunServerComp.this.connect(positive, negative, filter);
        }
    }

    public static class StunServerInit extends Init<StunServerComp> {

        public final Pair<DecoratedAddress, DecoratedAddress> self;
        public final List<DecoratedAddress> partners;
        public final SSNetworkHook.Definition networkHookDefinition;

        public StunServerInit(Pair<DecoratedAddress, DecoratedAddress> self, List<DecoratedAddress> partners,
                SSNetworkHook.Definition networkHookDefinition) {
            this.self = self;
            this.partners = partners;
            this.networkHookDefinition = networkHookDefinition;
        }
    }
}
