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
package se.sics.nat.simulation;

import com.google.common.collect.ImmutableMap;
import java.util.Set;
import org.javatuples.Pair;
import se.sics.nat.simulation.core.HostComp;
import se.sics.nat.simulation.core.HostComp.HostInit;
import se.sics.nat.simulation.core.StunServerHostComp;
import se.sics.nat.simulation.core.StunServerHostComp.StunServerHostInit;
import se.sics.nat.network.NatedTrait;
import se.sics.p2ptoolbox.simulator.cmd.impl.SetupCmd;
import se.sics.p2ptoolbox.simulator.cmd.impl.StartNodeCmd;
import se.sics.p2ptoolbox.simulator.dsl.SimulationScenario;
import se.sics.p2ptoolbox.simulator.dsl.adaptor.Operation;
import se.sics.p2ptoolbox.simulator.dsl.adaptor.Operation1;
import se.sics.p2ptoolbox.simulator.dsl.distribution.ConstantDistribution;
import se.sics.p2ptoolbox.simulator.dsl.distribution.extra.BasicIntSequentialDistribution;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;
import se.sics.p2ptoolbox.util.traits.AcceptedTraits;
import se.sics.p2ptoolbox.util.traits.Trait;

/**
 *
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class ScenarioGen {

    static Operation<SetupCmd> runSetup = new Operation<SetupCmd>() {
        @Override
        public SetupCmd generate() {
            return new SetupCmd() {
                @Override
                public void runSetup() {
                    Class<? extends Trait> trait1 = NatedTrait.class;
                    Pair<Integer, Byte> traitInfo = Pair.with(0, (byte) 0);
                    ImmutableMap<Class<? extends Trait>, Pair<Integer, Byte>> acceptedTraits
                            = ImmutableMap.<Class<? extends Trait>, Pair<Integer, Byte>>builder().
                            put(trait1, traitInfo).build();
                    DecoratedAddress.setAcceptedTraits(new AcceptedTraits(acceptedTraits));
                }
            };
        }
    };

    static Operation1<StartNodeCmd, Integer> startStunServer = new Operation1<StartNodeCmd, Integer>() {

        @Override
        public StartNodeCmd generate(final Integer nodeId) {
            return new StartNodeCmd<StunServerHostComp, DecoratedAddress>() {
                private final StunServerHostInit parentInit;

                {
                    parentInit = ScenarioSetup.stunServers.get(nodeId);
                    assert parentInit != null;
                }

                @Override
                public Integer getNodeId() {
                    return nodeId;
                }

                @Override
                public Class getNodeComponentDefinition() {
                    return StunServerHostComp.class;
                }

                @Override
                public StunServerHostInit getNodeComponentInit(DecoratedAddress aggregatorServer, Set<DecoratedAddress> bootstrapNodes) {
                    return parentInit;
                }

                @Override
                public DecoratedAddress getAddress() {
                    return parentInit.stunServerInit.self.getValue0();
                }

                @Override
                public int bootstrapSize() {
                    return 0;
                }
            };
        }
    };

    static Operation1<StartNodeCmd, Integer> startNormalNodes = new Operation1<StartNodeCmd, Integer>() {

        @Override
        public StartNodeCmd generate(final Integer nodeId) {
            return new StartNodeCmd<HostComp, DecoratedAddress>() {
                private final HostInit natedNodeInit;

                {
                    natedNodeInit = ScenarioSetup.normalHosts.get(nodeId);
                    assert natedNodeInit != null;
                }

                @Override
                public Integer getNodeId() {
                    return nodeId;
                }

                @Override
                public Class getNodeComponentDefinition() {
                    return HostComp.class;
                }

                @Override
                public HostInit getNodeComponentInit(DecoratedAddress aggregatorServer, Set<DecoratedAddress> bootstrapNodes) {
                    return natedNodeInit;
                }

                @Override
                public DecoratedAddress getAddress() {
                    return new DecoratedAddress(new BasicAddress(natedNodeInit.natEmulatorInit.selfIp, 0, natedNodeInit.natEmulatorInit.selfId));
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
                StochasticProcess setup = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(1, runSetup);
                    }
                };
                StochasticProcess stunServers = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(2, startStunServer, new BasicIntSequentialDistribution(1));
                    }
                };
                StochasticProcess normalNodes = new StochasticProcess() {
                    {
                        eventInterArrivalTime(constant(1000));
                        raise(4, startNormalNodes, new BasicIntSequentialDistribution(11));
                    }
                };

                setup.start();
                stunServers.startAfterTerminationOf(1000, setup);
                normalNodes.startAfterTerminationOf(10000, stunServers);
                terminateAfterTerminationOf(100000, normalNodes);
            }
        };
        return scen;
    }

}
