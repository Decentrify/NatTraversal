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
package se.sics.nat.emulator.util;

import com.google.common.base.Optional;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.javatuples.Pair;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class PortMappings {
    
    private final Map<Integer, Map<InetAddress, Set<Integer>>> portConnectionsMap; //<publicPort, <privateAdr, outgoingConn>>
    private final Map<Pair<InetAddress, Integer>, Integer> privateMap; //<privateAdr, publicPort>
    private final Map<Integer, Pair<InetAddress, Integer>> inversePrivateMap; //<privateAdr, publicPort>
    
    public PortMappings() {
        this.portConnectionsMap = new HashMap<Integer, Map<InetAddress, Set<Integer>>>();
        this.privateMap = new HashMap<Pair<InetAddress, Integer>, Integer>();
        this.inversePrivateMap = new HashMap<Integer, Pair<InetAddress, Integer>>();
    }
    
    public void map(Integer publicPort, Pair<InetAddress, Integer> privateAdr, Pair<InetAddress, Integer> target) {
        privateMap.put(privateAdr, publicPort);
        inversePrivateMap.put(publicPort, privateAdr);
        
        Map<InetAddress, Set<Integer>> portConnections = portConnectionsMap.get(publicPort);
        if(portConnections == null) {
            portConnections = new HashMap<InetAddress, Set<Integer>>();
            portConnectionsMap.put(publicPort, portConnections);
        }
        Set<Integer> allowedTargetPorts = portConnections.get(target.getValue0());
        if(allowedTargetPorts == null) {
            allowedTargetPorts = new HashSet<Integer>();
            portConnections.put(target.getValue0(), allowedTargetPorts);
        }
        allowedTargetPorts.add(target.getValue1());
    }
    
    public Optional<Integer> getPublicPort(Pair<InetAddress, Integer> privateAdr) {
        return Optional.fromNullable(privateMap.get(privateAdr));
    }
    
    public Set<Integer> getAllocatedPorts() {
        return portConnectionsMap.keySet();
    }
    
    public Map<InetAddress, Set<Integer>> getPortActiveConn(Integer publicPort) {
        return portConnectionsMap.get(publicPort);
    }
    
    public Pair<InetAddress, Integer> getPrivateAddress(int publicPort) {
        return inversePrivateMap.get(publicPort);
    }
}
