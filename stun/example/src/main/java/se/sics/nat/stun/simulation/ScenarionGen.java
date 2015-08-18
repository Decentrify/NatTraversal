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
package se.sics.nat.stun.simulation;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.javatuples.Pair;
import se.sics.ktoolbox.nat.stun.client.StunClientComp;
import se.sics.ktoolbox.nat.stun.client.StunClientComp.StunClientConfig;
import se.sics.ktoolbox.nat.stun.client.StunClientComp.StunClientInit;
import se.sics.ktoolbox.nat.stun.server.StunServerComp;
import se.sics.ktoolbox.nat.stun.server.StunServerComp.StunServerConfig;
import se.sics.ktoolbox.nat.stun.server.StunServerComp.StunServerInit;
import se.sics.p2ptoolbox.simulator.cmd.impl.StartNodeCmd;
import se.sics.p2ptoolbox.simulator.dsl.SimulationScenario;
import se.sics.p2ptoolbox.simulator.dsl.adaptor.Operation1;
import se.sics.p2ptoolbox.simulator.dsl.adaptor.Operation2;
import se.sics.p2ptoolbox.simulator.dsl.distribution.ConstantDistribution;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class ScenarionGen {

    private static InetAddress localHost;

    static {
        try {
            localHost = InetAddress.getByName("127.0.0.1");
        } catch (UnknownHostException ex) {
            System.err.println("scenario error while binding localhost");
            System.exit(1);
        }
    }

    static Operation1<StartNodeCmd, Integer> startStunServer = new Operation1<StartNodeCmd, Integer>() {

        @Override
        public StartNodeCmd generate(final Integer nodeId) {
            return new StartNodeCmd<StunServerComp, DecoratedAddress>() {
                private final Pair<DecoratedAddress, DecoratedAddress> stunServerAdr;

                {
                    DecoratedAddress stunAddr1 = new DecoratedAddress(new BasicAddress(localHost, 56788, nodeId));
                    DecoratedAddress stunAddr2 = new DecoratedAddress(new BasicAddress(localHost, 56789, nodeId));
                    stunServerAdr = Pair.with(stunAddr1, stunAddr2);
                }

                @Override
                public Integer getNodeId() {
                    return nodeId;
                }

                @Override
                public Class getNodeComponentDefinition() {
                    return StunServerComp.class;
                }

                @Override
                public StunServerInit getNodeComponentInit(DecoratedAddress aggregatorServer, Set<DecoratedAddress> bootstrapNodes) {
                    StunServerConfig stunServerConfig = new StunServerConfig();
                    List<DecoratedAddress> stunServerAdrs = new ArrayList<DecoratedAddress>();
                    return new StunServerInit(stunServerConfig, stunServerAdr, stunServerAdrs);
                }

                @Override
                public DecoratedAddress getAddress() {
                    return stunServerAdr.getValue0();
                }

                @Override
                public int bootstrapSize() {
                    return 0;
                }
            };
        }
    };
    
    static Operation2<StartNodeCmd, Integer, Integer> startStunClient = new Operation2<StartNodeCmd, Integer, Integer>() {

        @Override
        public StartNodeCmd generate(final Integer clientId, final Integer serverId) {
            return new StartNodeCmd<StunClientComp, DecoratedAddress>() {
                private final DecoratedAddress stunClientAdr;

                {
                    stunClientAdr = new DecoratedAddress(new BasicAddress(localHost, 56788, clientId));
                }

                @Override
                public Integer getNodeId() {
                    return clientId;
                }

                @Override
                public Class getNodeComponentDefinition() {
                    return StunClientComp.class;
                }

                @Override
                public StunClientInit getNodeComponentInit(DecoratedAddress aggregatorServer, Set<DecoratedAddress> bootstrapNodes) {
                    StunClientConfig stunServerConfig = new StunClientConfig();
                    List<DecoratedAddress> stunServerAdrs = new ArrayList<DecoratedAddress>();
                    stunServerAdrs.add(new DecoratedAddress(new BasicAddress(localHost, 56788, serverId)));
                    return new StunClientInit(stunClientAdr, stunServerAdrs);
                }

                @Override
                public DecoratedAddress getAddress() {
                    return stunClientAdr;
                }

                @Override
                public int bootstrapSize() {
                    return 0;
                }

            };
        }
    };

    public static SimulationScenario simpleBoot() {
        SimulationScenario scen = new SimulationScenario() {
            {
                StochasticProcess startStunServers = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, startStunServer, new ConstantDistribution<Integer>(Integer.class, 1));
                    }
                };
                StochasticProcess startStunClients = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, startStunClient, new ConstantDistribution<Integer>(Integer.class, 2), new ConstantDistribution<Integer>(Integer.class, 1));
                    }
                };
                startStunServers.start();
                startStunClients.startAfterTerminationOf(1000, startStunServers);
                terminateAfterTerminationOf(10000, startStunClients);
            }
        };
        return scen;
    }
}
