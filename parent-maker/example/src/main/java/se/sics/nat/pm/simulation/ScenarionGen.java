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
package se.sics.nat.pm.simulation;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import se.sics.nat.common.NatTraverserConfig;
import se.sics.nat.network.Nat;
import se.sics.nat.emulator.NatEmulatorComp;
import se.sics.nat.network.NatedTrait;
import se.sics.nat.pm.client.PMClientComp.PMClientInit;
import se.sics.nat.pm.core.PMClientHostComp;
import se.sics.nat.pm.core.PMClientHostComp.PMClientHostInit;
import se.sics.nat.pm.core.PMServerHostComp;
import se.sics.nat.pm.core.PMServerHostComp.PMServerHostInit;
import se.sics.nat.pm.server.PMServerComp;
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

    private static final Map<Integer, PMClientHostInit> pmClients = new HashMap<Integer, PMClientHostInit>();
    private static final Map<Integer, PMServerHostInit> pmServers = new HashMap<Integer, PMServerHostInit>();

    static {
        try {
            int server1Id = 1;
            InetAddress server1Ip = InetAddress.getByName("193.10.66.1");
            BasicAddress server1Adr = new BasicAddress(server1Ip, 56788, server1Id);
            pmServers.put(server1Id, new PMServerHostInit(new PMServerComp.PMServerInit(new NatTraverserConfig(), server1Adr)));

            int server2Id = 2;
            InetAddress server2Ip = InetAddress.getByName("193.10.66.2");
            BasicAddress server2Adr = new BasicAddress(server2Ip, 56788, server2Id);
            pmServers.put(server2Id, new PMServerHostInit(new PMServerComp.PMServerInit(new NatTraverserConfig(), server2Adr)));

            int server3Id = 3;
            InetAddress server3Ip = InetAddress.getByName("193.10.66.3");
            BasicAddress server3Adr = new BasicAddress(server3Ip, 56788, server3Id);
            pmServers.put(server3Id, new PMServerHostInit(new PMServerComp.PMServerInit(new NatTraverserConfig(), server3Adr)));


            //MP:EI, FP:EI, AP:PP
            int natedNode1Id = 11;
            InetAddress nat1Ip = InetAddress.getByName("193.10.67.1");
            InetAddress natedNode1Ip = InetAddress.getByName("192.168.1.1");
            BasicAddress natedNode1Adr = new BasicAddress(natedNode1Ip, 43210, natedNode1Id);
            NatedTrait nat1 = NatedTrait.nated(Nat.MappingPolicy.ENDPOINT_INDEPENDENT, Nat.AllocationPolicy.PORT_PRESERVATION, 0,
                    Nat.FilteringPolicy.ENDPOINT_INDEPENDENT, 10000, new ArrayList<DecoratedAddress>());
            NatEmulatorComp.NatEmulatorInit nat1Init = new NatEmulatorComp.NatEmulatorInit(natedNode1Id, nat1, nat1Ip, natedNode1Id);
            Set<DecoratedAddress> publicSample1 = new HashSet<DecoratedAddress>();
            publicSample1.add(new DecoratedAddress(server1Adr));
            publicSample1.add(new DecoratedAddress(server2Adr));
            publicSample1.add(new DecoratedAddress(server3Adr));
            PMClientInit natedNode1Init = new PMClientInit(new NatTraverserConfig(), natedNode1Adr);
            pmClients.put(natedNode1Id, new PMClientHostInit(nat1Init, natedNode1Init, publicSample1));
        } catch (UnknownHostException ex) {
            System.err.println("scenario error while binding localhost");
            System.exit(1);
        }
    }

    static Operation1<StartNodeCmd, Integer> startPMServer = new Operation1<StartNodeCmd, Integer>() {

        @Override
        public StartNodeCmd generate(final Integer nodeId) {
            return new StartNodeCmd<PMServerHostComp, DecoratedAddress>() {
                private final PMServerHostInit pmServerHostInit;

                {
                    pmServerHostInit = pmServers.get(nodeId);
                    assert pmServerHostInit != null;
                }

                @Override
                public Integer getNodeId() {
                    return nodeId;
                }

                @Override
                public Class getNodeComponentDefinition() {
                    return PMServerHostComp.class;
                }

                @Override
                public PMServerHostInit getNodeComponentInit(DecoratedAddress aggregatorServer, Set<DecoratedAddress> bootstrapNodes) {
                    return pmServerHostInit;
                }

                @Override
                public DecoratedAddress getAddress() {
                    return new DecoratedAddress(pmServerHostInit.pmServerInit.self);
                }

                @Override
                public int bootstrapSize() {
                    return 0;
                }
            };
        }
    };

    static Operation1<StartNodeCmd, Integer> startPMClient = new Operation1<StartNodeCmd, Integer>() {

        @Override
        public StartNodeCmd generate(final Integer nodeId) {
            return new StartNodeCmd<PMClientHostComp, DecoratedAddress>() {
                private final PMClientHostInit pmClientHostInit;
                {
                    pmClientHostInit = pmClients.get(nodeId);
                    assert pmClientHostInit != null;
                }

                @Override
                public Integer getNodeId() {
                    return nodeId;
                }

                @Override
                public Class getNodeComponentDefinition() {
                    return PMClientHostComp.class;
                }

                @Override
                public PMClientHostInit getNodeComponentInit(DecoratedAddress aggregatorServer, Set<DecoratedAddress> bootstrapNodes) {
                    return pmClientHostInit;
                }

                @Override
                public DecoratedAddress getAddress() {
                    if (pmClientHostInit.natEmulatorInit.natType.type.equals(Nat.Type.NAT)) {
                        return new DecoratedAddress(new BasicAddress(pmClientHostInit.natEmulatorInit.selfIp, 0, pmClientHostInit.natEmulatorInit.selfId));
                    } else {
                        return new DecoratedAddress(pmClientHostInit.pmClientInit.self);
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
                StochasticProcess startPMServer1 = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, startPMServer, new ConstantDistribution<Integer>(Integer.class, 1));
                    }
                };
                StochasticProcess startPMServer2 = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, startPMServer, new ConstantDistribution<Integer>(Integer.class, 2));
                    }
                };
                StochasticProcess startPMServer3 = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, startPMServer, new ConstantDistribution<Integer>(Integer.class, 3));
                    }
                };
                StochasticProcess startPMClient1 = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, startPMClient, new ConstantDistribution<Integer>(Integer.class, 11));
                    }
                };
                
                startPMServer1.start();
                startPMServer2.startAfterTerminationOf(1000, startPMServer1);
                startPMServer3.startAfterTerminationOf(1000, startPMServer2);
                startPMClient1.startAfterTerminationOf(1000, startPMServer3);
                terminateAfterTerminationOf(20000, startPMClient1);
            }
        };
        return scen;
    }
}
