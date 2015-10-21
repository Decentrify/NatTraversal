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
package se.sics.nat;

import com.google.common.base.Optional;
import java.net.InetAddress;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.Channel;
import se.sics.kompics.ChannelFilter;
import se.sics.kompics.Component;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.ConfigurationException;
import se.sics.kompics.ControlPort;
import se.sics.kompics.Fault;
import se.sics.kompics.Fault.ResolveAction;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.KompicsEvent;
import se.sics.kompics.Negative;
import se.sics.kompics.Port;
import se.sics.kompics.PortType;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.Timer;
import se.sics.ktoolbox.networkmngr.NetworkMngrPort;
import se.sics.nat.common.util.NatDetectionResult;
import se.sics.nat.stun.upnp.hooks.UpnpHook;
import se.sics.nat.stun.NatDetected;
import se.sics.nat.stun.StunClientPort;
import se.sics.nat.stun.client.StunClientComp;
import se.sics.nat.stun.upnp.UpnpPort;
import se.sics.nat.stun.upnp.msg.UpnpReady;
import se.sics.p2ptoolbox.util.config.KConfigCache;
import se.sics.p2ptoolbox.util.config.KConfigCore;
import se.sics.p2ptoolbox.util.nat.Nat;
import se.sics.p2ptoolbox.util.nat.NatedTrait;
import se.sics.p2ptoolbox.util.proxy.ComponentProxy;
import se.sics.p2ptoolbox.util.proxy.SystemHookSetup;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class NatDetectionComp extends ComponentDefinition {

    private static final Logger LOG = LoggerFactory.getLogger(NatDetectionComp.class);
    private String logPrefix = "";

    private final Positive<Timer> timer = requires(Timer.class);
    private final Positive<Network> network = requires(Network.class);
    private final Positive<NetworkMngrPort> networkMngr = requires(NetworkMngrPort.class);
    private final Negative<NatDetectionPort> natDetection = provides(NatDetectionPort.class);
    private final Negative<UpnpPort> upnp = provides(UpnpPort.class);

    private final SystemHookSetup systemHooks;
    private final KConfigCache config;

    private Component stunClient;
    private NatUpnpParent upnpParent;
    private NatDetectionResult natDetectionResult;

    public NatDetectionComp(NatDetectionInit init) {
        this.config = init.config;
        this.systemHooks = init.systemHooks;
        LOG.info("{}initiating...", logPrefix);

        this.upnpParent = new NatUpnpParent();
        this.natDetectionResult = new NatDetectionResult();

        subscribe(handleStart, control);

        connectStunClient();
        upnpParent.connectUpnp();
    }

    //**************************CONTROL*****************************************
    Handler handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            LOG.info("{}starting...", logPrefix);
            upnpParent.startUpnp(true);
        }
    };

    @Override
    public ResolveAction handleFault(Fault fault) {
        LOG.error("{}fault:{} on comp:{}", new Object[]{logPrefix, fault.getCause().getMessage(), fault.getSourceCore().id()});
        return ResolveAction.ESCALATE;
    }
    
    private void finish() {
        Pair<NatedTrait, Optional<InetAddress>> result = natDetectionResult.getResult();
        trigger(new NatDetected(result.getValue0(), result.getValue1()), natDetection);
        if (!result.getValue0().type.equals(Nat.Type.UPNP)) {
            upnpParent.stopUpnp();
        }
    }

    //*****************************STUN_CLIENT***************************************
    private void connectStunClient() {
        stunClient = create(StunClientComp.class, new StunClientComp.StunClientInit(config.configCore));
        connect(stunClient.getNegative(Timer.class), timer);
        connect(stunClient.getNegative(Network.class), network);
        connect(stunClient.getNegative(NetworkMngrPort.class), networkMngr);
        subscribe(handleNatReady, stunClient.getPositive(StunClientPort.class));
    }

    private Handler handleNatReady = new Handler<NatDetected>() {
        @Override
        public void handle(NatDetected ready) {
            String printIp = (ready.publicIp.isPresent() ? ready.publicIp.get().getHostAddress().toString() : "x");
            LOG.info("{}nat detected:{} public ip:{}",
                    new Object[]{logPrefix, ready.nat, printIp});
            natDetectionResult.setNatReady(ready.nat, ready.publicIp);
            if (natDetectionResult.isReady()) {
                finish();
            }
        }
    };

    public class NatUpnpParent implements UpnpHook.Parent {

        private UpnpHook.SetupResult upnpSetup;
        private final UpnpHook.Definition upnpHook;

        public NatUpnpParent() {
            upnpHook = systemHooks.getHook(NatDetectionHooks.RequiredHooks.UPNP.hookName,
                    NatDetectionHooks.UPNP_HOOK);
        }

        void connectUpnp() {
            upnpSetup = upnpHook.setup(new NatDetectionProxy(), new NatUpnpParent(), new UpnpHook.SetupInit());
        }

        void startUpnp(boolean started) {
            upnpHook.start(new NatDetectionProxy(), new NatUpnpParent(), upnpSetup, new UpnpHook.StartInit(started));
        }

        void stopUpnp() {
            upnpHook.preStop(new NatDetectionProxy(), new NatUpnpParent(), upnpSetup, new UpnpHook.TearInit());
        }

        @Override
        public void onResult(UpnpReady ready) {
            String upnpResult = (ready.externalIp.isPresent() ? ready.externalIp.get().toString() : "absent");
            LOG.info("{}upnp ready:{}", logPrefix, upnpResult);
            natDetectionResult.setUpnpReady(ready.externalIp);
            if (natDetectionResult.isReady()) {
                finish();
            }
        }
    }

    public class NatDetectionProxy implements ComponentProxy {

        @Override
        public <P extends PortType> Positive<P> requires(Class<P> portType) {
            return NatDetectionComp.this.requires(portType);
        }

        @Override
        public <P extends PortType> Negative<P> provides(Class<P> portType) {
            return NatDetectionComp.this.provides(portType);
        }

        @Override
        public Negative<ControlPort> getControlPort() {
            return NatDetectionComp.this.control;
        }

        @Override
        public <T extends ComponentDefinition> Component create(Class<T> definition, Init<T> initEvent) {
            return NatDetectionComp.this.create(definition, initEvent);
        }

        @Override
        public <T extends ComponentDefinition> Component create(Class<T> definition, Init.None initEvent) {
            return NatDetectionComp.this.create(definition, initEvent);
        }

        @Override
        public <P extends PortType> Channel<P> connect(Positive<P> positive, Negative<P> negative) {
            return NatDetectionComp.this.connect(negative, positive);
        }

        @Override
        public <P extends PortType> Channel<P> connect(Positive<P> positive, Negative<P> negative, ChannelFilter filter) {
            return NatDetectionComp.this.connect(positive, negative, filter);
        }

        @Override
        public <P extends PortType> Channel<P> connect(Negative<P> negative, Positive<P> positive) {
            return NatDetectionComp.this.connect(negative, positive);
        }

        @Override
        public <P extends PortType> Channel<P> connect(Negative<P> negative, Positive<P> positive, ChannelFilter filter) {
            return NatDetectionComp.this.connect(negative, positive, filter);
        }

        @Override
        public <P extends PortType> void disconnect(Negative<P> negative, Positive<P> positive) {
            NatDetectionComp.this.disconnect(negative, positive);
        }

        @Override
        public <P extends PortType> void disconnect(Positive<P> positive, Negative<P> negative) {
            NatDetectionComp.this.disconnect(negative, positive);
        }

        @Override
        public <P extends PortType> void trigger(KompicsEvent e, Port<P> p) {
            NatDetectionComp.this.trigger(e, p);
        }

        @Override
        public <E extends KompicsEvent, P extends PortType> void subscribe(Handler<E> handler, Port<P> port) throws ConfigurationException {
            NatDetectionComp.this.subscribe(handler, port);
        }
    }

    public static class NatDetectionInit extends Init<NatDetectionComp> {

        public final KConfigCache config;
        public final SystemHookSetup systemHooks;

        public NatDetectionInit(KConfigCore configCore, SystemHookSetup systemHooks) {
            this.config = new KConfigCache(configCore);
            this.systemHooks = systemHooks;
        }
    }
}
