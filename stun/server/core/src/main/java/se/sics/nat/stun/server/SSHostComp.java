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
package se.sics.nat.stun.server;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
import se.sics.kompics.KompicsEvent;
import se.sics.kompics.Negative;
import se.sics.kompics.Port;
import se.sics.kompics.PortType;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.timer.Timer;
import se.sics.ktoolbox.ipsolver.hooks.AddressSolverHook;
import se.sics.ktoolbox.ipsolver.hooks.AddressSolverResult;
import se.sics.ktoolbox.ipsolver.msg.GetIp;
import static se.sics.nat.stun.server.config.SSConfigOptions.*;
import se.sics.nat.stun.server.hooks.SSNetworkHook;
import se.sics.p2ptoolbox.util.config.KConfigCache;
import se.sics.p2ptoolbox.util.config.KConfigCore;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;
import se.sics.p2ptoolbox.util.proxy.ComponentProxy;
import se.sics.p2ptoolbox.util.proxy.Hook;
import se.sics.p2ptoolbox.util.proxy.SystemHookSetup;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class SSHostComp extends ComponentDefinition {

    private static final Logger LOG = LoggerFactory.getLogger(StunServerComp.class);
    private String logPrefix = "";

    public static enum RequiredHooks implements Hook.Required {

        ADDRESS_SOLVER, STUN_SERVER_NETWORK
    }
    
    private final Positive<Timer> timer = requires(Timer.class);

    private final KConfigCache config;
    private final SystemHookSetup systemHooks;
    private final EnumSet<GetIp.NetworkInterfacesMask> netInterfaces;

    private AddressSolverHook.SetupResult addressSolverSetup;
    private SSNetworkHook.SetupResult network1Setup;
    private SSNetworkHook.SetupResult network2Setup;
    private final Map<UUID, Integer> compToHook = new HashMap<>();
    private Component stunServer;
    
    private Pair<DecoratedAddress, DecoratedAddress> self;

    public SSHostComp(SSHostInit init) {
        this.config = init.config;
        this.systemHooks = init.systemHooks;
        this.logPrefix = config.getNodeId() + " ";
        LOG.info("{}initiating...", logPrefix);

        this.netInterfaces = init.netInterfaces;
        
        subscribe(handleStart, control);
        setupAddressSolver();
    }

    Handler handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            LOG.info("{}starting...", logPrefix);
            startAddressSolver(true);
        }
    };

    @Override
    public Fault.ResolveAction handleFault(Fault fault) {
        LOG.error("{}fault:{} from component:{} - restarting hook...",
                new Object[]{logPrefix, fault.getCause().getMessage(), fault.getSourceCore().id()});
        return Fault.ResolveAction.ESCALATE;
    }

    private void setupAddressSolver() {
        LOG.info("{}setting up address solver", logPrefix);
        AddressSolverHook.Definition addressSolverHD = systemHooks.getHook(
                RequiredHooks.ADDRESS_SOLVER.toString(), AddressSolverHook.Definition.class);
        addressSolverSetup = addressSolverHD.setup(new SSHostProxy(), new AddressSolverHP(), new AddressSolverHook.SetupInit());
    }
    
    private void startAddressSolver(boolean started) {
        LOG.info("{}starting address solver", logPrefix);
        AddressSolverHook.Definition addressSolverHD = systemHooks.getHook(
                RequiredHooks.ADDRESS_SOLVER.toString(), AddressSolverHook.Definition.class);
        addressSolverHD.start(new SSHostProxy(), new AddressSolverHP(), addressSolverSetup, new AddressSolverHook.StartInit(started));

    }
    
    public class AddressSolverHP implements AddressSolverHook.Parent {
        @Override
        public void onResult(AddressSolverResult result) {
            //update configs
            config.write(LOCAL_IP, result.localIp.getCanonicalHostName());
            config.write(SS_PORT1, result.ports.get(config.read(SS_PORT1)));
            config.write(SS_PORT2, result.ports.get(config.read(SS_PORT2)));
            DecoratedAddress adr1 = DecoratedAddress.open(
                config.read(LOCAL_INET_IP),
                config.read(SS_PORT1),
                config.getNodeId());
            DecoratedAddress adr2 = DecoratedAddress.open(
                config.read(LOCAL_INET_IP),
                config.read(SS_PORT2),
                config.getNodeId());
            self = Pair.with(adr1, adr2);
            //TODO Alex - handle sockets - possible resource leak once restarting is implemented
            
            setupNetwork1();
            setupNetwork2();
            setupStunServer();
            startNetwork1(false);
            startNetwork2(false);
            startStunServer(false);
        }

        @Override
        public Set<Integer> bindPorts() {
            HashSet<Integer> ports = new HashSet<Integer>();
            ports.add(config.read(SS_PORT1));
            ports.add(config.read(SS_PORT2));
            return ports;
        }

        @Override
        public EnumSet<GetIp.NetworkInterfacesMask> netInterfaces() {
            return netInterfaces;
        }
    }
    
    private void setupNetwork1() {
        LOG.info("{}setting up network1", logPrefix);
        SSNetworkHook.Definition networkHD = systemHooks.getHook(
                RequiredHooks.STUN_SERVER_NETWORK.toString(), SSNetworkHook.Definition.class);
        network1Setup = networkHD.setup(new SSHostProxy(), new SSNetworkHP(), new SSNetworkHook.SetupInit(self.getValue0()));
        for (Component component : network1Setup.components) {
            compToHook.put(component.id(), 1);
        }
    }
    private void setupNetwork2() {
        LOG.info("{}setting up network2", logPrefix);
        SSNetworkHook.Definition networkHD = systemHooks.getHook(
                RequiredHooks.STUN_SERVER_NETWORK.toString(), SSNetworkHook.Definition.class);
        
        network2Setup = networkHD.setup(new SSHostProxy(), new SSNetworkHP(), new SSNetworkHook.SetupInit(self.getValue1()));
        for (Component component : network2Setup.components) {
            compToHook.put(component.id(), 2);
        }
    }
    private void setupStunServer() {
        LOG.info("{}setting up stun server", logPrefix);
        stunServer = create(StunServerComp.class, new StunServerComp.StunServerInit(config.configCore, self, 
            network1Setup.network, network2Setup.network));
        connect(stunServer.getNegative(Timer.class), timer);
    }

    private void startNetwork1(boolean started) {
        SSNetworkHook.Definition networkHD = systemHooks.getHook(
                RequiredHooks.STUN_SERVER_NETWORK.toString(), SSNetworkHook.Definition.class);
        networkHD.start(new SSHostProxy(), new SSNetworkHP(), network1Setup, new SSNetworkHook.StartInit(started));
    }

    private void startNetwork2(boolean started) {
        SSNetworkHook.Definition networkHD = systemHooks.getHook(
                RequiredHooks.STUN_SERVER_NETWORK.toString(), SSNetworkHook.Definition.class);
        networkHD.start(new SSHostProxy(), new SSNetworkHP(), network2Setup, new SSNetworkHook.StartInit(started));
    }
    
    private void startStunServer(boolean started) {
        if(!started) {
            trigger(Start.event, stunServer.control());
        }
    }
    
    public class SSNetworkHP implements SSNetworkHook.Parent {
    }

    public class SSHostProxy implements ComponentProxy {

        @Override
        public <P extends PortType> void trigger(KompicsEvent e, Port<P> p) {
            SSHostComp.this.trigger(e, p);
        }

        @Override
        public <T extends ComponentDefinition> Component create(Class<T> definition, Init<T> initEvent) {
            return SSHostComp.this.create(definition, initEvent);
        }

        @Override
        public <T extends ComponentDefinition> Component create(Class<T> definition, Init.None initEvent) {
            return SSHostComp.this.create(definition, initEvent);
        }

        @Override
        public <P extends PortType> Channel<P> connect(Positive<P> positive, Negative<P> negative) {
            return SSHostComp.this.connect(negative, positive);
        }

        @Override
        public <P extends PortType> Channel<P> connect(Negative<P> negative, Positive<P> positive) {
            return SSHostComp.this.connect(negative, positive);
        }

        @Override
        public <P extends PortType> void disconnect(Negative<P> negative, Positive<P> positive) {
            SSHostComp.this.disconnect(negative, positive);
        }

        @Override
        public <P extends PortType> void disconnect(Positive<P> positive, Negative<P> negative) {
            SSHostComp.this.disconnect(negative, positive);
        }

        @Override
        public Negative<ControlPort> getControlPort() {
            return SSHostComp.this.control;
        }

        @Override
        public <P extends PortType> Positive<P> requires(Class<P> portType) {
            return SSHostComp.this.requires(portType);
        }

        @Override
        public <P extends PortType> Negative<P> provides(Class<P> portType) {
            return SSHostComp.this.provides(portType);
        }

        @Override
        public <P extends PortType> Channel<P> connect(Positive<P> positive, Negative<P> negative, ChannelFilter filter) {
            return SSHostComp.this.connect(negative, positive, filter);
        }

        @Override
        public <P extends PortType> Channel<P> connect(Negative<P> negative, Positive<P> positive, ChannelFilter filter) {
            return SSHostComp.this.connect(positive, negative, filter);
        }

        @Override
        public <E extends KompicsEvent, P extends PortType> void subscribe(Handler<E> handler, Port<P> port) throws ConfigurationException {
            SSHostComp.this.subscribe(handler, port);
        }
    }

    public static class SSHostInit extends Init<SSHostComp> {

        public final KConfigCache config;
        public final SystemHookSetup systemHooks;
        public final EnumSet<GetIp.NetworkInterfacesMask> netInterfaces;

        public SSHostInit(KConfigCore config, SystemHookSetup systemHooks, EnumSet<GetIp.NetworkInterfacesMask> netInterfaces) {
            this.config = new KConfigCache(config);
            this.systemHooks = systemHooks;
            this.netInterfaces = netInterfaces;
        }
    }
}
