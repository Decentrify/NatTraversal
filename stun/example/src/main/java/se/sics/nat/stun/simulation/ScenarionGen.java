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
import se.sics.nat.network.Nat;
import se.sics.ktoolbox.nat.stun.client.StunClientComp.StunClientInit;
import se.sics.ktoolbox.nat.stun.server.StunServerComp;
import se.sics.ktoolbox.nat.stun.server.StunServerComp.StunServerConfig;
import se.sics.ktoolbox.nat.stun.server.StunServerComp.StunServerInit;
import se.sics.nat.emulator.NatEmulatorComp;
import se.sics.nat.network.NatedTrait;
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
            Pair<DecoratedAddress, DecoratedAddress> server1Adr = 
                    Pair.with(new DecoratedAddress(new BasicAddress(server1Ip, 56788, 1)), new DecoratedAddress(new BasicAddress(server1Ip, 56789, 1)));

            InetAddress server2Ip = InetAddress.getByName("193.10.66.2");
            Pair<DecoratedAddress, DecoratedAddress> server2Adr = 
                    Pair.with(new DecoratedAddress(new BasicAddress(server2Ip, 56788, 2)), new DecoratedAddress(new BasicAddress(server2Ip, 56789, 2)));

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
            Pair<DecoratedAddress, DecoratedAddress> openNode1Adr = 
                    Pair.with(new DecoratedAddress(new BasicAddress(openNode1Ip, 43210, openNode1Id)), new DecoratedAddress(new BasicAddress(openNode1Ip, 43211, openNode1Id)));
            NatEmulatorComp.NatEmulatorInit openNode1Nat = new NatEmulatorComp.NatEmulatorInit(openNode1Id, NatedTrait.open(), openNode1Ip, openNode1Id);
            List<Pair<DecoratedAddress, DecoratedAddress>> openNode1Servers = new ArrayList<Pair<DecoratedAddress, DecoratedAddress>>();
            openNode1Servers.add(server1Adr);
            openNode1Servers.add(server2Adr);
            StunClientInit openNode1Init = new StunClientInit(openNode1Adr, openNode1Servers);
            stunClients.put(openNode1Id, new StunClientHostInit(openNode1Nat, openNode1Init));

            //MP:EI, FP:EI, AP:PP
            int natedNode1Id = 12;
            InetAddress nat1Ip = InetAddress.getByName("193.10.67.2");
            InetAddress natedNode1Ip = InetAddress.getByName("192.168.1.2");
            Pair<DecoratedAddress, DecoratedAddress> natedNode1Adr = 
                    Pair.with(new DecoratedAddress(new BasicAddress(natedNode1Ip, 43210, natedNode1Id)), new DecoratedAddress(new BasicAddress(natedNode1Ip, 43211, natedNode1Id)));
            NatedTrait nat1 = NatedTrait.nated(Nat.MappingPolicy.ENDPOINT_INDEPENDENT, Nat.AllocationPolicy.PORT_PRESERVATION, 0,
                    Nat.FilteringPolicy.ENDPOINT_INDEPENDENT, 10000, new ArrayList<DecoratedAddress>());
            NatEmulatorComp.NatEmulatorInit nat1Init = new NatEmulatorComp.NatEmulatorInit(natedNode1Id, nat1, nat1Ip, natedNode1Id);
            List<Pair<DecoratedAddress, DecoratedAddress>> natedNode1Servers = new ArrayList<Pair<DecoratedAddress, DecoratedAddress>>();
            natedNode1Servers.add(server1Adr);
            natedNode1Servers.add(server2Adr);
            StunClientInit natedNode1Init = new StunClientInit(natedNode1Adr, natedNode1Servers);
            stunClients.put(natedNode1Id, new StunClientHostInit(nat1Init, natedNode1Init));
            
            //MP:EI, FP:HD, AP:PP
            int natedNode2Id = 13;
            InetAddress nat2Ip = InetAddress.getByName("193.10.67.3");
            InetAddress natedNode2Ip = InetAddress.getByName("192.168.1.3");
            Pair<DecoratedAddress, DecoratedAddress> natedNode2Adr = 
                    Pair.with(new DecoratedAddress(new BasicAddress(natedNode2Ip, 43210, natedNode2Id)), new DecoratedAddress(new BasicAddress(natedNode2Ip, 43211, natedNode2Id)));
            NatedTrait nat2 = NatedTrait.nated(Nat.MappingPolicy.ENDPOINT_INDEPENDENT, Nat.AllocationPolicy.PORT_PRESERVATION, 0,
                    Nat.FilteringPolicy.HOST_DEPENDENT, 10000,new ArrayList<DecoratedAddress>());
            NatEmulatorComp.NatEmulatorInit nat2Init = new NatEmulatorComp.NatEmulatorInit(natedNode2Id, nat2, nat2Ip, natedNode2Id);
            List<Pair<DecoratedAddress, DecoratedAddress>> natedNode2Servers = new ArrayList<Pair<DecoratedAddress, DecoratedAddress>>();
            natedNode2Servers.add(server1Adr);
            natedNode2Servers.add(server2Adr);
            StunClientInit natedNode2Init = new StunClientInit(natedNode2Adr, natedNode2Servers);
            stunClients.put(natedNode2Id, new StunClientHostInit(nat2Init, natedNode2Init));
            
            //MP:EI, FP:PD, AP:PP
            int natedNode3Id = 14;
            InetAddress nat3Ip = InetAddress.getByName("193.10.67.4");
            InetAddress natedNode3Ip = InetAddress.getByName("192.168.1.4");
            Pair<DecoratedAddress, DecoratedAddress> natedNode3Adr = 
                    Pair.with(new DecoratedAddress(new BasicAddress(natedNode3Ip, 43210, natedNode3Id)), new DecoratedAddress(new BasicAddress(natedNode3Ip, 43211, natedNode3Id)));
            NatedTrait nat3 = NatedTrait.nated(Nat.MappingPolicy.ENDPOINT_INDEPENDENT, Nat.AllocationPolicy.PORT_PRESERVATION, 0,
                    Nat.FilteringPolicy.PORT_DEPENDENT, 10000, new ArrayList<DecoratedAddress>());
            NatEmulatorComp.NatEmulatorInit nat3Init = new NatEmulatorComp.NatEmulatorInit(natedNode3Id, nat3, nat3Ip, natedNode3Id);
            List<Pair<DecoratedAddress, DecoratedAddress>> natedNode3Servers = new ArrayList<Pair<DecoratedAddress, DecoratedAddress>>();
            natedNode3Servers.add(server1Adr);
            natedNode3Servers.add(server2Adr);
            StunClientInit natedNode3Init = new StunClientInit(natedNode3Adr, natedNode3Servers);
            stunClients.put(natedNode3Id, new StunClientHostInit(nat3Init, natedNode3Init));
            
            //MP:EI, FP:PD, AP:PC
            int natedNode4Id = 15;
            InetAddress nat4Ip = InetAddress.getByName("193.10.67.5");
            InetAddress natedNode4Ip = InetAddress.getByName("192.168.1.5");
            Pair<DecoratedAddress, DecoratedAddress> natedNode4Adr = 
                    Pair.with(new DecoratedAddress(new BasicAddress(natedNode4Ip, 43210, natedNode4Id)), new DecoratedAddress(new BasicAddress(natedNode4Ip, 43211, natedNode4Id)));
            NatedTrait nat4 = NatedTrait.nated(Nat.MappingPolicy.ENDPOINT_INDEPENDENT, Nat.AllocationPolicy.PORT_CONTIGUITY, 1,
                    Nat.FilteringPolicy.PORT_DEPENDENT, 10000, new ArrayList<DecoratedAddress>());
            NatEmulatorComp.NatEmulatorInit nat4Init = new NatEmulatorComp.NatEmulatorInit(natedNode4Id, nat4, nat4Ip, natedNode4Id);
            List<Pair<DecoratedAddress, DecoratedAddress>> natedNode4Servers = new ArrayList<Pair<DecoratedAddress, DecoratedAddress>>();
            natedNode4Servers.add(server1Adr);
            natedNode4Servers.add(server2Adr);
            StunClientInit natedNode4Init = new StunClientInit(natedNode4Adr, natedNode4Servers);
            stunClients.put(natedNode4Id, new StunClientHostInit(nat4Init, natedNode4Init));
            
            //MP:EI, FP:PD, AP:R
            int natedNode5Id = 16;
            InetAddress nat5Ip = InetAddress.getByName("193.10.67.6");
            InetAddress natedNode5Ip = InetAddress.getByName("192.168.1.6");
            Pair<DecoratedAddress, DecoratedAddress> natedNode5Adr = 
                    Pair.with(new DecoratedAddress(new BasicAddress(natedNode5Ip, 43210, natedNode5Id)), new DecoratedAddress(new BasicAddress(natedNode5Ip, 43211, natedNode5Id)));
            NatedTrait nat5 = NatedTrait.nated(Nat.MappingPolicy.ENDPOINT_INDEPENDENT, Nat.AllocationPolicy.RANDOM, 0,
                    Nat.FilteringPolicy.PORT_DEPENDENT, 10000, new ArrayList<DecoratedAddress>());
            NatEmulatorComp.NatEmulatorInit nat5Init = new NatEmulatorComp.NatEmulatorInit(natedNode5Id, nat5, nat5Ip, natedNode5Id);
            List<Pair<DecoratedAddress, DecoratedAddress>> natedNode5Servers = new ArrayList<Pair<DecoratedAddress, DecoratedAddress>>();
            natedNode5Servers.add(server1Adr);
            natedNode5Servers.add(server2Adr);
            StunClientInit natedNode5Init = new StunClientInit(natedNode5Adr, natedNode5Servers);
            stunClients.put(natedNode5Id, new StunClientHostInit(nat5Init, natedNode5Init));
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
                        return stunClientHostInit.stunClientInit.self.getValue0();
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
                StochasticProcess startStunServer2 = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, startStunServer, new ConstantDistribution<Integer>(Integer.class, 2));
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
                
                StochasticProcess startStunClient3 = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, startStunClient, new ConstantDistribution<Integer>(Integer.class, 13));
                    }
                };
                
                StochasticProcess startStunClient4 = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, startStunClient, new ConstantDistribution<Integer>(Integer.class, 14));
                    }
                };
                
                StochasticProcess startStunClient5 = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, startStunClient, new ConstantDistribution<Integer>(Integer.class, 15));
                    }
                };
                
                StochasticProcess startStunClient6 = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, startStunClient, new ConstantDistribution<Integer>(Integer.class, 16));
                    }
                };
                
                startStunServer1.start();
                startStunServer2.startAfterTerminationOf(1000, startStunServer1);
                startStunClient6.startAfterTerminationOf(1000, startStunServer2);
                terminateAfterTerminationOf(10000, startStunClient6);
            }
        };
        return scen;
    }
}
