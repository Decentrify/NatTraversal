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

package se.sics.nat.hooks;

import com.google.common.base.Optional;
import java.net.InetAddress;
import java.net.Socket;
import org.javatuples.Pair;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class NatAddressSolverResult {
    public final InetAddress localIp;
    public final Pair<Integer, Integer> stunClientPorts;
    public final Integer appPort;
    
    public final Pair<Optional<Socket>, Optional<Socket>> stunClientSockets;
    public final Optional<Socket> appSocket;
    
    public NatAddressSolverResult(InetAddress localIp, Pair<Integer, Optional<Socket>> stunClientPort1, 
            Pair<Integer, Optional<Socket>> stunClientPort2, Pair<Integer, Optional<Socket>> appPort) {
        this.localIp = localIp;
        this.stunClientPorts = Pair.with(stunClientPort1.getValue0(), stunClientPort2.getValue0());
        this.appPort = appPort.getValue0();
        
        this.stunClientSockets = Pair.with(stunClientPort1.getValue1(), stunClientPort2.getValue1());
        this.appSocket = appPort.getValue1();
    }
}
