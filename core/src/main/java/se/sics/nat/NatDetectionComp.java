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

import java.net.InetAddress;
import java.util.List;
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
import se.sics.kompics.Stop;
import se.sics.kompics.timer.Timer;
import se.sics.nat.common.util.NatDetectionResult;
import se.sics.nat.hooks.NatUpnpHook;
import se.sics.nat.stun.NatReady;
import se.sics.nat.stun.StunClientPort;
import se.sics.nat.stun.client.StunClientComp;
import se.sics.nat.stun.upnp.UpnpPort;
import se.sics.nat.stun.upnp.msg.UpnpReady;
import se.sics.p2ptoolbox.util.nat.NatedTrait;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;
import se.sics.p2ptoolbox.util.proxy.ComponentProxy;
import se.sics.p2ptoolbox.util.proxy.Hook;
import se.sics.p2ptoolbox.util.proxy.SystemHookSetup;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class NatDetectionComp extends ComponentDefinition {

    private static final Logger LOG = LoggerFactory.getLogger(NatDetectionComp.class);
    private String logPrefix = "";

    public static enum RequiredHooks implements Hook.Required {

        NAT_UPNP
    }

    private final Positive<Timer> timer = requires(Timer.class);
    private final Negative<NatDetectionPort> natDetection = provides(NatDetectionPort.class);
    private final Negative<UpnpPort> upnp = provides(UpnpPort.class);

    private NatDetectionInit init;

    private Component stunClient;
    private NatUpnpHook.SetupResult upnpSetup;

    private NatDetectionResult natDetectionResult;

    public NatDetectionComp(NatDetectionInit init) {
        this.init = init;
        this.logPrefix = init.privateAdr + " ";
        LOG.info("{}initiating...", logPrefix);
        this.natDetectionResult = new NatDetectionResult();

        subscribe(handleStart, control);
        subscribe(handleStop, control);

        connectStunClient();
        connectUpnp();
    }

    //**************************CONTROL*****************************************
    Handler handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            LOG.info("{}starting...", logPrefix);
            startUpnp(true);
        }
    };

    Handler handleStop = new Handler<Stop>() {
        @Override
        public void handle(Stop event) {
            LOG.info("{}stopping...", logPrefix);
        }
    };

    @Override
    public ResolveAction handleFault(Fault fault) {
        LOG.error("{}fault:{} on comp:{}", new Object[]{logPrefix, fault.getCause().getMessage(), fault.getSourceCore().id()});
        return ResolveAction.ESCALATE;
    }

    //**************************************************************************
    private void connectStunClient() {
        Pair<DecoratedAddress, DecoratedAddress> scAdr = Pair.with(
                new DecoratedAddress(new BasicAddress(init.privateAdr.getIp(), init.scPorts.getValue0(), init.privateAdr.getId())),
                new DecoratedAddress(new BasicAddress(init.privateAdr.getIp(), init.scPorts.getValue1(), init.privateAdr.getId())));
        stunClient = create(StunClientComp.class, new StunClientComp.StunClientInit(scAdr, init.stunServers, init.systemHooks));
        connect(stunClient.getNegative(Timer.class), timer);

        subscribe(handleNatReady, stunClient.getPositive(StunClientPort.class));
    }

    private Handler handleNatReady = new Handler<NatReady>() {
        @Override
        public void handle(NatReady ready) {
            LOG.info("{}nat detected:{} public ip:{}",
                    new Object[]{logPrefix, ready.nat, ready.publicIp});
            natDetectionResult.setNatReady(ready.nat, ready.publicIp);
            if (natDetectionResult.isReady()) {
                finish();
            }
        }
    };

    private void connectUpnp() {
        NatUpnpHook.Definition upnpHook = init.systemHooks.getHook(RequiredHooks.NAT_UPNP.toString(), NatUpnpHook.Definition.class);
        upnpSetup = upnpHook.setup(new NatDetectionProxy(), new NatUpnpHookParent(), new NatUpnpHook.SetupInit());
    }

    private void startUpnp(boolean started) {
        NatUpnpHook.Definition upnpHook = init.systemHooks.getHook(RequiredHooks.NAT_UPNP.toString(), NatUpnpHook.Definition.class);
        upnpHook.start(new NatDetectionProxy(), new NatUpnpHookParent(), upnpSetup, new NatUpnpHook.StartInit(started));
    }

    private void finish() {
        Pair<NatedTrait, InetAddress> result = natDetectionResult.getResult();
        trigger(new NatReady(result.getValue0(), result.getValue1()), natDetection);
    }

    public class NatUpnpHookParent implements Hook.Parent {
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

        public final BasicAddress privateAdr;
        public final StunClientComp.StunClientConfig scConfig;
        public final Pair<Integer, Integer> scPorts;
        public final List<Pair<DecoratedAddress, DecoratedAddress>> stunServers;
        public final SystemHookSetup systemHooks;

        public NatDetectionInit(BasicAddress privateAdr, NatInitHelper ntInit, SystemHookSetup systemHooks) {
            this.privateAdr = privateAdr;
            this.scConfig = new StunClientComp.StunClientConfig();
            this.scPorts = ntInit.stunClientPorts;
            this.stunServers = ntInit.stunServers;
            this.systemHooks = systemHooks;
        }
    }
}
