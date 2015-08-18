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


public class ForwardPortStatus {

	public final int status;
	/** The port forward definitely succeeded. */
	public static final int DEFINITE_SUCCESS = 3;
	/** The port forward probably succeeded. I.e. it succeeded unless there was
	 * for example hostile action on the part of the router. */
	public static final int PROBABLE_SUCCESS = 2;
	/** The port forward may have succeeded. Or it may not have. We should
	 * definitely try to check out of band. See UP&P: Many routers say they've
	 * forwarded the port when they haven't. */
	public static final int MAYBE_SUCCESS = 1;
	/** The port forward is in progress */
	public static final int IN_PROGRESS = 0;
	/** The port forward probably failed */
	public static final int PROBABLE_FAILURE = -1;
	/** The port forward definitely failed. */
	public static final int DEFINITE_FAILURE = -2;

	public final String reasonString;

	/** Some plugins may need to change the external port. They can return it
	 * to the node here. */
	public final int externalPort;

	public ForwardPortStatus(int status, String reason, int externalPort) {
		this.status = status;
		this.reasonString = reason;
		this.externalPort = externalPort;
	}

    @Override
    public String toString() {
        return "External port " + externalPort + " status " + status + " " +  reasonString;
    }


}