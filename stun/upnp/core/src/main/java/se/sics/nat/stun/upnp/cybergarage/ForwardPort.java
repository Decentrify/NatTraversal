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
package se.sics.nat.stun.upnp.cybergarage;

/**
 * A public Internet Protocol port on the node which needs to be forwarded if the
 * node is NATed.
 * @author toad
 */
public class ForwardPort {

	/** Name of the interface e.g. "opennet" */
	public final String name;
	/** IPv4 vs IPv6? */
	public final boolean isIP6;
	/** Protocol number. See constants. */
	public final int protocol;
	public static final int PROTOCOL_UDP_IPV4 = 17;
	public static final int PROTOCOL_TCP_IPV4 = 6;
	/** Port number to forward */
	public final int portNumber;
	// We don't currently support binding to a specific internal interface.
	// It would be complicated: Different interfaces may be on different LANs,
	// and an IGD is normally on only one LAN.
	private final int hashCode;

	public ForwardPort(String name, boolean isIP6, int protocol, int portNumber) {
		this.name = name;
		this.isIP6 = isIP6;
		this.protocol = protocol;
		this.portNumber = portNumber;
		hashCode = name.hashCode() | (isIP6 ? 1 : 0) | protocol | portNumber;
	}

	public int hashCode() {
		return hashCode;
	}

	public boolean equals(Object o) {
		if(o == this) return true;
		if(!(o instanceof ForwardPort)) return false;
		ForwardPort f = (ForwardPort) o;
		return (f.name.equals(name)) && f.isIP6 == isIP6 && f.protocol == protocol && f.portNumber == portNumber;
	}
}