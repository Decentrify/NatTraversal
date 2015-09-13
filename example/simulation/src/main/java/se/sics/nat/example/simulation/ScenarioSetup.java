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
package se.sics.nat.example.simulation;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.javatuples.Pair;
import se.sics.ktoolbox.nat.stun.server.StunServerComp;
import se.sics.nat.simulation.system.StunServerHostComp.StunServerHostInit;
import se.sics.p2ptoolbox.util.nat.Nat;
import se.sics.p2ptoolbox.util.nat.NatedTrait;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class ScenarioSetup {

    public static final int globalCroupierOverlayId = 0;
    public static final long baseSeed = 1234;
    public static final int appPort = 30000;
    public static final Pair<Integer, Integer> stunClientPorts = Pair.with(43211, 43210);
    public static final Pair<Integer, Integer> stunServerPorts = Pair.with(54321, 54320);
    public static final NatedTrait[] nats = new NatedTrait[2];
    public static final List<Pair<DecoratedAddress, DecoratedAddress>> stunServers
            = new ArrayList<Pair<DecoratedAddress, DecoratedAddress>>();
    public static final Map<Integer, StunServerHostInit> stunServerInits = new HashMap<>();
    public static final List<DecoratedAddress> bootstrapNodes = new ArrayList<DecoratedAddress>();

    static {
        nats[0] = NatedTrait.open();
        nats[1] = NatedTrait.nated(Nat.MappingPolicy.ENDPOINT_INDEPENDENT, Nat.AllocationPolicy.PORT_PRESERVATION, 0,
                Nat.FilteringPolicy.ENDPOINT_INDEPENDENT, 10000, new ArrayList<DecoratedAddress>());
        
        try {
            int stun1Id = -1;
            InetAddress stun1Ip = InetAddress.getByName("100.0.0.1");
            Pair<DecoratedAddress, DecoratedAddress> stun1Adr
                    = Pair.with(new DecoratedAddress(new BasicAddress(stun1Ip, stunServerPorts.getValue0(), stun1Id)),
                            new DecoratedAddress(new BasicAddress(stun1Ip, stunServerPorts.getValue1(), stun1Id)));

            int stun2Id = -2;
            InetAddress stun2Ip = InetAddress.getByName("100.0.0.2");
            Pair<DecoratedAddress, DecoratedAddress> stun2Adr
                    = Pair.with(new DecoratedAddress(new BasicAddress(stun2Ip, stunServerPorts.getValue0(), stun2Id)),
                            new DecoratedAddress(new BasicAddress(stun2Ip, stunServerPorts.getValue1(), stun2Id)));

            stunServers.add(stun1Adr);
            stunServers.add(stun2Adr);

            //stun server 1
            List<DecoratedAddress> stun1Partners = new ArrayList<DecoratedAddress>();
            stun1Partners.add(stunServers.get(1).getValue0());
            StunServerComp.StunServerInit stun1SSInit = new StunServerComp.StunServerInit(
                    new StunServerComp.StunServerConfig(), stunServers.get(0), stun1Partners);
            stunServerInits.put(stun1Id, new StunServerHostInit(stun1SSInit));

            //stun server 2
            List<DecoratedAddress> stun2Partners = new ArrayList<DecoratedAddress>();
            stun2Partners.add(stunServers.get(0).getValue0());
            StunServerComp.StunServerInit stun2SSInit = new StunServerComp.StunServerInit(
                    new StunServerComp.StunServerConfig(), stunServers.get(1), stun2Partners);
            stunServerInits.put(stun2Id, new StunServerHostInit(stun2SSInit));
        } catch (UnknownHostException ex) {
            System.out.println("stun ip error");
            System.exit(1);
        }

        try {
            DecoratedAddress node1 = new DecoratedAddress(new BasicAddress(InetAddress.getByName("10.0.0.1"), appPort, 1));
            node1.addTrait(NatedTrait.open());
            bootstrapNodes.add(node1);

            DecoratedAddress node2 = new DecoratedAddress(new BasicAddress(InetAddress.getByName("10.0.0.2"), appPort, 2));
            node2.addTrait(NatedTrait.open());
            bootstrapNodes.add(node2);

            DecoratedAddress node3 = new DecoratedAddress(new BasicAddress(InetAddress.getByName("10.0.0.3"), appPort, 3));
            node3.addTrait(NatedTrait.open());
            bootstrapNodes.add(node3);
        } catch (UnknownHostException ex) {
            System.out.println("open node ip error");
            System.exit(1);
        }
    }

}
