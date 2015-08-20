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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.javatuples.Pair;
import se.sics.ktoolbox.nat.network.Nat;
import se.sics.ktoolbox.nat.stun.client.StunClientComp.StunClientInit;
import se.sics.ktoolbox.nat.stun.server.StunServerComp;
import se.sics.ktoolbox.nat.stun.server.StunServerComp.StunServerConfig;
import se.sics.ktoolbox.nat.stun.server.StunServerComp.StunServerInit;
import se.sics.nat.emulator.NatEmulatorComp;
import se.sics.nat.stun.core.StunClientHostComp;
import se.sics.nat.stun.core.StunClientHostComp.StunClientHostInit;
import se.sics.p2ptoolbox.simulator.cmd.impl.StartNodeCmd;
import se.sics.p2ptoolbox.simulator.dsl.SimulationScenario;
import se.sics.p2ptoolbox.simulator.dsl.adaptor.Operation1;
import se.sics.p2ptoolbox.simulator.dsl.distribution.ConstantDistribution;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class ScenarionGen {

    private static final Map<Integer, StunClientHostInit> stunClients = new HashMap<Integer, StunClientHostInit>();
    private static final Map<Integer, StunServerInit> stunServers = new HashMap<Integer, StunServerInit>();

    static {
        try {
            InetAddress server1Ip = InetAddress.getByName("193.10.66.1");
            Pair<DecoratedAddress, DecoratedAddress> server1Adr = Pair.with(new DecoratedAddress(new BasicAddress(server1Ip, 56788, 1)), new DecoratedAddress(new BasicAddress(server1Ip, 56789, 1)));

            InetAddress server2Ip = InetAddress.getByName("193.10.66.2");
            Pair<DecoratedAddress, DecoratedAddress> server2Adr = Pair.with(new DecoratedAddress(new BasicAddress(server2Ip, 56788, 2)), new DecoratedAddress(new BasicAddress(server2Ip, 56789, 2)));

            List<DecoratedAddress> server1Partners = new ArrayList<DecoratedAddress>();
            server1Partners.add(server2Adr.getValue0());
            StunServerInit server1 = new StunServerInit(new StunServerConfig(), server1Adr, server1Partners);
            stunServers.put(1, server1);

            List<DecoratedAddress> server2Partners = new ArrayList<DecoratedAddress>();
            server2Partners.add(server1Adr.getValue0());
            StunServerInit server2 = new StunServerInit(new StunServerConfig(), server2Adr, server2Partners);
            stunServers.put(2, server2);

            int openNode1Id = 11;
            InetAddress openNode1Ip = InetAddress.getByName("193.10.67.1");
            NatEmulatorComp.NatEmulatorInit openNode1Nat = new NatEmulatorComp.NatEmulatorInit(openNode1Id, Nat.open(), openNode1Ip, openNode1Id);
            List<DecoratedAddress> openNode1Servers = new ArrayList<DecoratedAddress>();
            openNode1Servers.add(server1Adr.getValue0());
            StunClientInit openNode1Init = new StunClientInit(new DecoratedAddress(new BasicAddress(openNode1Ip, 43210, openNode1Id)), openNode1Servers);
            stunClients.put(openNode1Id, new StunClientHostInit(openNode1Nat, openNode1Init));

            int natedNode1Id = 12;
            InetAddress nat1Ip = InetAddress.getByName("193.10.67.2");
            InetAddress natedNode1Ip = InetAddress.getByName("192.168.1.2");
            Nat nat1 = Nat.nated(Nat.MappingPolicy.ENDPOINT_INDEPENDENT, Nat.AllocationPolicy.PORT_PRESERVATION, Nat.FilteringPolicy.ENDPOINT_INDEPENDENT, 10000);
            NatEmulatorComp.NatEmulatorInit nat1Init = new NatEmulatorComp.NatEmulatorInit(natedNode1Id, nat1, nat1Ip, natedNode1Id);
            List<DecoratedAddress> natedNode1Servers = new ArrayList<DecoratedAddress>();
            natedNode1Servers.add(server1Adr.getValue0());
            StunClientInit natedNode1Init = new StunClientInit(new DecoratedAddress(new BasicAddress(natedNode1Ip, 43210, natedNode1Id)), natedNode1Servers);
            stunClients.put(natedNode1Id, new StunClientHostInit(nat1Init, natedNode1Init));
        } catch (UnknownHostException ex) {
            System.err.println("scenario error while binding localhost");
            System.exit(1);
        }
    }

    static Operation1<StartNodeCmd, Integer> startStunServer = new Operation1<StartNodeCmd, Integer>() {

        @Override
        public StartNodeCmd generate(final Integer nodeId) {
            return new StartNodeCmd<StunServerComp, DecoratedAddress>() {
                private final StunServerInit stunServerInit;

                {
                    stunServerInit = stunServers.get(nodeId);
                    assert stunServerInit != null;
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
                    return stunServerInit;
                }

                @Override
                public DecoratedAddress getAddress() {
                    return stunServerInit.self.getValue0();
                }

                @Override
                public int bootstrapSize() {
                    return 0;
                }
            };
        }
    };

    static Operation1<StartNodeCmd, Integer> startStunClient = new Operation1<StartNodeCmd, Integer>() {

        @Override
        public StartNodeCmd generate(final Integer nodeId) {
            return new StartNodeCmd<StunClientHostComp, DecoratedAddress>() {
                private final StunClientHostInit stunClientHostInit;

                {
                    stunClientHostInit = stunClients.get(nodeId);
                    assert stunClientHostInit != null;
                }

                @Override
                public Integer getNodeId() {
                    return nodeId;
                }

                @Override
                public Class getNodeComponentDefinition() {
                    return StunClientHostComp.class;
                }

                @Override
                public StunClientHostInit getNodeComponentInit(DecoratedAddress aggregatorServer, Set<DecoratedAddress> bootstrapNodes) {
                    return stunClientHostInit;
                }

                @Override
                public DecoratedAddress getAddress() {
                    if (stunClientHostInit.natEmulatorInit.natType.type.equals(Nat.Type.NAT)) {
                        return new DecoratedAddress(new BasicAddress(stunClientHostInit.natEmulatorInit.selfIp, 0, stunClientHostInit.natEmulatorInit.selfId));
                    } else {
                        return stunClientHostInit.stunClientInit.self;
                    }
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
                StochasticProcess startStunServer1 = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, startStunServer, new ConstantDistribution<Integer>(Integer.class, 1));
                    }
                };
                StochasticProcess startStunClient1 = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, startStunClient, new ConstantDistribution<Integer>(Integer.class, 11));
                    }
                };
                StochasticProcess startStunClient2 = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, startStunClient, new ConstantDistribution<Integer>(Integer.class, 12));
                    }
                };
                startStunServer1.start();
//                startStunClient1.startAfterTerminationOf(1000, startStunServer1);
                startStunClient2.startAfterTerminationOf(1000, startStunServer1);
                terminateAfterTerminationOf(10000, startStunClient2);
//                terminateAfterTerminationOf(10000, startStunClient2);
            }
        };
        return scen;
    }
}
