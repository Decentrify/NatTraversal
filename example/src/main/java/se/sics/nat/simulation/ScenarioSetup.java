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

import com.typesafe.config.ConfigFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.javatuples.Pair;
import se.sics.ktoolbox.nat.stun.client.StunClientComp;
import se.sics.ktoolbox.nat.stun.server.StunServerComp;
import se.sics.nat.simulation.core.HostComp.HostInit;
import se.sics.nat.simulation.core.StunServerHostComp.StunServerHostInit;
import se.sics.nat.common.NatTraverserConfig;
import se.sics.nat.emulator.NatEmulatorComp;
import se.sics.nat.network.Nat;
import se.sics.nat.network.NatedTrait;
import se.sics.p2ptoolbox.croupier.CroupierConfig;
import se.sics.p2ptoolbox.util.helper.SystemConfigBuilder;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class ScenarioSetup {

    public static final Map<Integer, StunServerHostInit> stunServers = new HashMap<>();
    public static final Map<Integer, HostInit> normalHosts = new HashMap<>();

    static {
        try {
            NatTraverserConfig ntConfig = new NatTraverserConfig();
            CroupierConfig croupierConfig = new CroupierConfig(ConfigFactory.load());
            
            int stunClientPort1 = 43211;
            int stunClientPort2 = 43210;
            int stunServerPort1 = 54321;
            int stunServerPort2 = 54320;
            int applicationPort = 30000;
            int croupierGlobalOverlayId = 0;
            //in simulation these ids have to be unique
            int id1 = 1;
            int id2 = 2;
            int id11 = 11;
            int id12 = 12;
            int id13 = 13;
            int id14 = 14;
            
            //parent 1
            int parent1Id = id1;
            InetAddress parent1Ip = InetAddress.getByName("193.0.0.1");
            Pair<DecoratedAddress, DecoratedAddress> parent1Adr
                    = Pair.with(new DecoratedAddress(new BasicAddress(parent1Ip, stunServerPort1, parent1Id)), 
                            new DecoratedAddress(new BasicAddress(parent1Ip, stunServerPort2, parent1Id)));

            //parent 2
            int parent2Id = id2;
            InetAddress parent2Ip = InetAddress.getByName("193.0.0.2");
            Pair<DecoratedAddress, DecoratedAddress> parent2Adr
                    = Pair.with(new DecoratedAddress(new BasicAddress(parent2Ip, stunServerPort1, parent2Id)), 
                            new DecoratedAddress(new BasicAddress(parent2Ip, stunServerPort2, parent2Id)));

            //stun server 1
            List<DecoratedAddress> parent1Partners = new ArrayList<DecoratedAddress>();
            parent1Partners.add(parent2Adr.getValue0());
            StunServerComp.StunServerInit parent1SSInit = new StunServerComp.StunServerInit(
                    new StunServerComp.StunServerConfig(), parent1Adr, parent1Partners);
            stunServers.put(parent1Id, new StunServerHostInit(parent1SSInit));

            //stun server 2
            List<DecoratedAddress> parent2Partners = new ArrayList<DecoratedAddress>();
            parent2Partners.add(parent1Adr.getValue0());
            StunServerComp.StunServerInit parent2SSInit = new StunServerComp.StunServerInit(
                    new StunServerComp.StunServerConfig(), parent2Adr, parent2Partners);
            stunServers.put(parent2Id, new StunServerHostInit(parent2SSInit));

            List<Pair<DecoratedAddress, DecoratedAddress>> stuns
                    = new ArrayList<Pair<DecoratedAddress, DecoratedAddress>>();
            stuns.add(parent1Adr);
            stuns.add(parent2Adr);
            
            //normal hosts
            //node1 - open
            int node1Id = id11;
            InetAddress node1Ip = InetAddress.getByName("193.0.1.11");

            Pair<DecoratedAddress, DecoratedAddress> node1LocalStun
                    = Pair.with(new DecoratedAddress(new BasicAddress(node1Ip, stunClientPort1, node1Id)),
                            new DecoratedAddress(new BasicAddress(node1Ip, stunClientPort2, node1Id)));
            NatedTrait nat1 = NatedTrait.open();
            NatEmulatorComp.NatEmulatorInit nat1Init = new NatEmulatorComp.NatEmulatorInit(
                    node1Id, nat1, node1Ip, node1Id);

            StunClientComp.StunClientInit node1SSInit = new StunClientComp.StunClientInit(
                    node1LocalStun, stuns);

            SystemConfigBuilder systemConfig1 = new SystemConfigBuilder(node1Id, node1Ip, applicationPort, node1Id);
            Set<DecoratedAddress> globalCroupierBootstrap1 = new HashSet<DecoratedAddress>();
            
            normalHosts.put(node1Id, new HostInit(nat1Init, node1SSInit, ntConfig,
            systemConfig1, croupierConfig, croupierGlobalOverlayId, globalCroupierBootstrap1));
            
            //node2 - open
            int node2Id = id12;
            InetAddress node2Ip = InetAddress.getByName("193.0.1.12");

            Pair<DecoratedAddress, DecoratedAddress> node2LocalStun
                    = Pair.with(new DecoratedAddress(new BasicAddress(node2Ip, stunClientPort1, node2Id)),
                            new DecoratedAddress(new BasicAddress(node2Ip, stunClientPort2, node2Id)));
            NatedTrait nat2 = NatedTrait.open();
            NatEmulatorComp.NatEmulatorInit nat2Init = new NatEmulatorComp.NatEmulatorInit(
                    node2Id, nat2, node2Ip, node2Id);

            StunClientComp.StunClientInit node2SSInit = new StunClientComp.StunClientInit(
                    node2LocalStun, stuns);

            SystemConfigBuilder systemConfig2 = new SystemConfigBuilder(node2Id, node2Ip, applicationPort, node2Id);
            Set<DecoratedAddress> globalCroupierBootstrap2 = new HashSet<DecoratedAddress>();
            globalCroupierBootstrap2.add(new DecoratedAddress(new BasicAddress(node1Ip, applicationPort, node1Id)));
            normalHosts.put(node2Id, new HostInit(nat2Init, node2SSInit, ntConfig,
            systemConfig2, croupierConfig, croupierGlobalOverlayId, globalCroupierBootstrap2));

            //node3 - open
            int node3Id = id13;
            InetAddress node3Ip = InetAddress.getByName("193.0.1.13");

            Pair<DecoratedAddress, DecoratedAddress> node3LocalStun
                    = Pair.with(new DecoratedAddress(new BasicAddress(node3Ip, stunClientPort1, node3Id)),
                            new DecoratedAddress(new BasicAddress(node3Ip, stunClientPort2, node3Id)));
            NatedTrait nat3 = NatedTrait.open();
            NatEmulatorComp.NatEmulatorInit nat3Init = new NatEmulatorComp.NatEmulatorInit(
                    node3Id, nat3, node3Ip, node3Id);

            StunClientComp.StunClientInit node3SSInit = new StunClientComp.StunClientInit(
                    node3LocalStun, stuns);

            SystemConfigBuilder systemConfig3 = new SystemConfigBuilder(node3Id, node3Ip, applicationPort, node3Id);
            Set<DecoratedAddress> globalCroupierBootstrap3 = new HashSet<DecoratedAddress>();
            globalCroupierBootstrap3.add(new DecoratedAddress(new BasicAddress(node1Ip, applicationPort, node1Id)));
            normalHosts.put(node3Id, new HostInit(nat3Init, node3SSInit, ntConfig,
            systemConfig3, croupierConfig, croupierGlobalOverlayId, globalCroupierBootstrap3));
            
            //node4 - nated MP:EI, FP:EI, AP:PP
            int node4Id = id14;
            InetAddress nat4Ip = InetAddress.getByName("193.0.1.14");
            InetAddress node4Ip = InetAddress.getByName("193.0.2.14");

            Pair<DecoratedAddress, DecoratedAddress> node4LocalStun
                    = Pair.with(new DecoratedAddress(new BasicAddress(node4Ip, stunClientPort1, node4Id)),
                            new DecoratedAddress(new BasicAddress(node4Ip, stunClientPort2, node4Id)));
            NatedTrait nat4 = NatedTrait.nated(Nat.MappingPolicy.ENDPOINT_INDEPENDENT,
                    Nat.AllocationPolicy.PORT_PRESERVATION, 0,
                    Nat.FilteringPolicy.ENDPOINT_INDEPENDENT,
                    10000, new ArrayList<DecoratedAddress>());
            NatEmulatorComp.NatEmulatorInit nat4Init = new NatEmulatorComp.NatEmulatorInit(
                    node4Id, nat4, nat4Ip, node4Id);

            StunClientComp.StunClientInit node4SSInit = new StunClientComp.StunClientInit(
                    node4LocalStun, stuns);
            
            SystemConfigBuilder systemConfig4 = new SystemConfigBuilder(node4Id, node4Ip, applicationPort, node4Id);
            Set<DecoratedAddress> globalCroupierBootstrap4 = new HashSet<DecoratedAddress>();
            globalCroupierBootstrap4.add(new DecoratedAddress(new BasicAddress(node1Ip, applicationPort, node1Id)));
            normalHosts.put(node4Id, new HostInit(nat4Init, node4SSInit, ntConfig,
            systemConfig4, croupierConfig, croupierGlobalOverlayId, globalCroupierBootstrap4));
        } catch (UnknownHostException ex) {
            System.err.println("scenario error while binding localhost");
            System.exit(1);
        }
    }
}
