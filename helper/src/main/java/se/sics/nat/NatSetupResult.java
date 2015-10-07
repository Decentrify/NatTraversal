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

package se.sics.nat;

import se.sics.kompics.Negative;
import se.sics.kompics.Positive;
import se.sics.kompics.network.Network;
import se.sics.p2ptoolbox.croupier.CroupierPort;
import se.sics.p2ptoolbox.util.config.SystemConfig;
import se.sics.p2ptoolbox.util.update.SelfAddressUpdatePort;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class NatSetupResult {
    public final Positive<Network> network;
    public final Positive<SelfAddressUpdatePort> adrUpdate;
    public final Negative<CroupierPort> peerSampling;
    public final SystemConfig systemConfig;
    
    public NatSetupResult(Positive<Network> network, Positive<SelfAddressUpdatePort> adrUpdate, 
            Negative<CroupierPort> peerSampling, SystemConfig systemConfig) {
        this.network = network;
        this.adrUpdate = adrUpdate;
        this.systemConfig = systemConfig;
        this.peerSampling = peerSampling;
    }
}
