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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.javatuples.Pair;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
//TODO Alex - low priority - assuming one nated device behind nat
public class PortMappings {

    /**
     * Keep in check with Policy implementations FilterPolicy - EI - out is
     * emptySet; HD - set with multiple addresses, same Ip, diff port; PD - set
     * with address with dif ip/port
     */
    //<port, <conninAdr, outAdrSet>> 
    private final Map<Integer, Pair<BasicAddress, Set<BasicAddress>>> mappings = new HashMap<>();
    //<inAdr, port>
    private final Map<BasicAddress, Integer> inMapping = new HashMap<>();
    //<natPort, <outAdr, timeoutId>>
    private final Map<Integer, Map<BasicAddress, UUID>> timeoutMappings = new HashMap<>();

    public Optional<Pair<BasicAddress, Set<BasicAddress>>> getMapping(int natPort) {
        Pair<BasicAddress, Set<BasicAddress>> portMapping = mappings.get(natPort);
        return Optional.fromNullable(portMapping);
    }

    public Optional<Integer> getNatPort(BasicAddress inAdr) {
        return Optional.fromNullable(inMapping.get(inAdr));
    }

    public Set<Integer> getAllocatedPorts() {
        return mappings.keySet();
    }

    public Optional<UUID> send(int natPort, BasicAddress inAdr, BasicAddress outAdr, UUID tid) {
        Pair<BasicAddress, Set<BasicAddress>> portMapping = mappings.get(natPort);
        Map<BasicAddress, UUID> portConnectionTimeouts = timeoutMappings.get(natPort);
        if (portMapping == null) {
            Set<BasicAddress> targets = new HashSet<>();
            portMapping = Pair.with(inAdr, targets);
            mappings.put(natPort, portMapping);

            portConnectionTimeouts = new HashMap<>();
            timeoutMappings.put(natPort, portConnectionTimeouts);
        }
        portMapping.getValue1().add(outAdr);
        inMapping.put(inAdr, natPort);
        UUID oldConnectionTid = portConnectionTimeouts.put(outAdr, tid);
        return Optional.fromNullable(oldConnectionTid);
    }

    public void cleanConnection(int natPort, BasicAddress outAdr, UUID tid) {
        UUID oldTid = timeoutMappings.get(natPort).get(outAdr);
        if (oldTid.equals(tid)) {
            timeoutMappings.get(natPort).remove(outAdr);
            Pair<BasicAddress, Set<BasicAddress>> portMapping = mappings.get(natPort);
            portMapping.getValue1().remove(outAdr);
            if (portMapping.getValue1().isEmpty()) {
                mappings.remove(natPort);
                inMapping.remove(portMapping.getValue0());
                timeoutMappings.remove(natPort);
            }
        } else {
            /**
             * probably a message was sent through, concurrently with the
             * timeout popping but being stalled in the queue. We ignore the
             * timeout
             */
        }
    }

    @Override
    public String toString() {
        return simpleToString();
    }
    
    public String simpleToString() {
        int connections = 0;
        for (Pair<BasicAddress, Set<BasicAddress>> portMapping : mappings.values()) {
            connections += portMapping.getValue1().size();
        }
        return mappings.size() + " active ports and " + connections + " connections";
    }
    
    public String complexToString() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, Pair<BasicAddress, Set<BasicAddress>>> portMapping : mappings.entrySet()) {
            sb.append("\nport:" + portMapping.getKey() + "-");
            for(BasicAddress outAdr : portMapping.getValue().getValue1()) {
                sb.append(outAdr.toString() + ",");
            }
            sb.deleteCharAt(sb.length()-1);
        }
        return sb.toString();
    }
}
