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
package se.sics.nat.example.node;

import com.google.common.collect.ImmutableMap;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.net.InetAddress;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
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
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.Kompics;
import se.sics.kompics.KompicsEvent;
import se.sics.kompics.Negative;
import se.sics.kompics.Port;
import se.sics.kompics.PortType;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.Stop;
import se.sics.kompics.network.Network;
import se.sics.kompics.network.netty.NettyInit;
import se.sics.kompics.network.netty.NettyNetwork;
import se.sics.kompics.timer.Timer;
import se.sics.kompics.timer.java.JavaTimer;
import se.sics.ktoolbox.ipsolver.IpSolverComp;
import se.sics.ktoolbox.ipsolver.IpSolverPort;
import se.sics.ktoolbox.ipsolver.msg.GetIp;
import se.sics.ktoolbox.ipsolver.util.IpAddressStatus;
import se.sics.ktoolbox.ipsolver.util.IpHelper;
import se.sics.nat.NatDetectionComp;
import se.sics.nat.NatDetectionPort;
import se.sics.nat.NatInitHelper;
import se.sics.nat.NatLauncherProxy;
import se.sics.nat.hooks.NatNetworkHook;
import se.sics.nat.NatTraverserComp;
import se.sics.nat.stun.NatReady;
import se.sics.nat.common.croupier.GlobalCroupierView;
import se.sics.nat.NatSerializerSetup;
import se.sics.nat.NatSetup;
import se.sics.nat.NatSetupResult;
import se.sics.nat.common.util.NatDetectionResult;
import se.sics.nat.hp.SHPSerializerSetup;
import se.sics.nat.pm.PMSerializerSetup;
import se.sics.nat.stun.StunSerializerSetup;
import se.sics.nat.stun.client.SCNetworkHook;
import se.sics.nat.stun.upnp.UpnpPort;
import se.sics.nat.stun.upnp.msg.MapPorts;
import se.sics.nat.stun.upnp.msg.UnmapPorts;
import se.sics.nat.stun.upnp.util.Protocol;
import se.sics.p2ptoolbox.croupier.CroupierComp;
import se.sics.p2ptoolbox.croupier.CroupierConfig;
import se.sics.p2ptoolbox.croupier.CroupierControlPort;
import se.sics.p2ptoolbox.croupier.CroupierPort;
import se.sics.p2ptoolbox.croupier.CroupierSerializerSetup;
import se.sics.p2ptoolbox.croupier.msg.CroupierJoin;
import se.sics.p2ptoolbox.croupier.msg.CroupierUpdate;
import se.sics.p2ptoolbox.util.config.SystemConfig;
import se.sics.p2ptoolbox.util.filters.IntegerOverlayFilter;
import se.sics.p2ptoolbox.util.helper.SystemConfigBuilder;
import se.sics.p2ptoolbox.util.nat.Nat;
import se.sics.p2ptoolbox.util.nat.NatedTrait;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;
import se.sics.p2ptoolbox.util.proxy.ComponentProxy;
import se.sics.p2ptoolbox.util.serializer.BasicSerializerSetup;
import se.sics.p2ptoolbox.util.traits.AcceptedTraits;
import se.sics.p2ptoolbox.util.update.SelfAddressUpdatePort;
import se.sics.p2ptoolbox.util.update.SelfViewUpdatePort;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class NodeLauncher extends ComponentDefinition {

    private static Logger LOG = LoggerFactory.getLogger(NodeLauncher.class);
    private String logPrefix = "";

    private final HostInit init;

    private Component timer;
    private Positive<Network> network;
    private Positive<SelfAddressUpdatePort> adrUpdate;
    private Positive<CroupierPort> globalCroupier;
    private Component node;

    private SystemConfig systemConfig;

    public NodeLauncher(HostInit init) {
        LOG.info("{}initializing...", logPrefix);
        this.init = init;
        subscribe(handleStart, control);
    }
    
    //*****************************CONTROL**************************************
    private Handler handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            LOG.info("{}starting", logPrefix);
            connectNStartTimer();
            connectNStartNat();
            LOG.info("{}waiting for nat", logPrefix);
        }
    };

    private void connectNStartTimer() {
        timer = create(JavaTimer.class, Init.NONE);
        trigger(Start.event, timer.control());
    }

    private void connectNStartNat() {
        NatSetup natSetup = new NatSetup(new HostLauncherProxy(),
                timer.getPositive(Timer.class),
                new SystemConfigBuilder(ConfigFactory.load()));
        natSetup.setup();
        natSetup.start(false);
    }

    private void connectNStartApp() {
        node = create(NodeComp.class, new NodeComp.NodeInit(systemConfig.self, init.ping));
        connect(node.getNegative(Network.class), network);
        connect(node.getNegative(SelfAddressUpdatePort.class), adrUpdate);
        connect(node.getNegative(CroupierPort.class), globalCroupier);
        trigger(Start.event, node.control());
    }

    public class HostLauncherProxy implements NatLauncherProxy {

        @Override
        public void startApp(NatSetupResult result) {
            NodeLauncher.this.network = result.network;
            NodeLauncher.this.adrUpdate = result.adrUpdate;
            NodeLauncher.this.globalCroupier = result.globalCroupier;
            NodeLauncher.this.systemConfig = result.systemConfig;
            LOG.info("{}nat started with:{}", logPrefix, result.systemConfig.self);
            NodeLauncher.this.connectNStartApp();
        }

        @Override
        public <P extends PortType> Positive<P> requires(Class<P> portType) {
            return NodeLauncher.this.requires(portType);
        }

        @Override
        public <P extends PortType> Negative<P> provides(Class<P> portType) {
            return NodeLauncher.this.provides(portType);
        }

        @Override
        public Negative<ControlPort> getControlPort() {
            return NodeLauncher.this.control;
        }

        @Override
        public <T extends ComponentDefinition> Component create(Class<T> definition, Init<T> initEvent) {
            return NodeLauncher.this.create(definition, initEvent);
        }

        @Override
        public <T extends ComponentDefinition> Component create(Class<T> definition, Init.None initEvent) {
            return NodeLauncher.this.create(definition, initEvent);
        }

        @Override
        public <P extends PortType> Channel<P> connect(Positive<P> positive, Negative<P> negative) {
            return NodeLauncher.this.connect(positive, negative);
        }

        @Override
        public <P extends PortType> Channel<P> connect(Positive<P> positive, Negative<P> negative, ChannelFilter filter) {
            return NodeLauncher.this.connect(positive, negative, filter);
        }

        @Override
        public <P extends PortType> Channel<P> connect(Negative<P> negative, Positive<P> positive) {
            return NodeLauncher.this.connect(negative, positive);
        }

        @Override
        public <P extends PortType> Channel<P> connect(Negative<P> negative, Positive<P> positive, ChannelFilter filter) {
            return NodeLauncher.this.connect(negative, positive, filter);
        }

        @Override
        public <P extends PortType> void disconnect(Negative<P> negative, Positive<P> positive) {
            NodeLauncher.this.disconnect(negative, positive);
        }

        @Override
        public <P extends PortType> void disconnect(Positive<P> positive, Negative<P> negative) {
            NodeLauncher.this.disconnect(positive, negative);
        }

        @Override
        public <P extends PortType> void trigger(KompicsEvent e, Port<P> p) {
            NodeLauncher.this.trigger(e, p);
        }

        @Override
        public <E extends KompicsEvent, P extends PortType> void subscribe(Handler<E> handler, Port<P> port) throws ConfigurationException {
            NodeLauncher.this.subscribe(handler, port);
        }
    }

    public static class HostInit extends Init<NodeLauncher> {

        public final SystemConfigBuilder systemConfigBuilder;
        public final NatInitHelper natInit;
        public final CroupierConfig croupierConfig;

        public final boolean ping;

        public HostInit(Config config, boolean ping) {
            this.systemConfigBuilder = new SystemConfigBuilder(config);
            this.natInit = new NatInitHelper(config);
            this.croupierConfig = new CroupierConfig(config);
            this.ping = ping;
        }
    }

    private static void systemSetup() {
        int serializerId = 128;
        serializerId = BasicSerializerSetup.registerBasicSerializers(serializerId);
        serializerId = StunSerializerSetup.registerSerializers(serializerId);
        serializerId = CroupierSerializerSetup.registerSerializers(serializerId);
        serializerId = PMSerializerSetup.registerSerializers(serializerId);
        serializerId = SHPSerializerSetup.registerSerializers(serializerId);
        serializerId = NatSerializerSetup.registerSerializers(serializerId);
        serializerId = NodeSerializerSetup.registerSerializers(serializerId);

        ImmutableMap acceptedTraits = ImmutableMap.of(NatedTrait.class, 0);
        DecoratedAddress.setAcceptedTraits(new AcceptedTraits(acceptedTraits));
    }
    
    public static void main(String[] args) {
        systemSetup();
        
        Options options = new Options();
        Option pingOpt = new Option("ping", false, "ping target address");
        options.addOption(pingOpt);
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException ex) {
            LOG.error("command line parsing error");
            System.exit(1);
        }
        boolean ping = cmd.hasOption(pingOpt.getOpt());

        if (Kompics.isOn()) {
            Kompics.shutdown();
        }
        Kompics.createAndStart(NodeLauncher.class,
                new NodeLauncher.HostInit(ConfigFactory.load(), ping),
                Runtime.getRuntime().availableProcessors(), 20); // Yes 20 is totally arbitrary
        try {
            Kompics.waitForTermination();
        } catch (InterruptedException ex) {
            System.exit(1);
        }
    }
}
