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
package se.sics.nat.hp.client;

import se.sics.kompics.PortType;
import se.sics.nat.hp.client.msg.CloseConnection;
import se.sics.nat.hp.client.msg.OpenConnection;
import se.sics.nat.pm.client.msg.Update;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class HPClientPort extends PortType {
    {
        negative(Update.class);
        negative(OpenConnection.Request.class);
        positive(OpenConnection.Response.class);
        negative(CloseConnection.class);
        positive(CloseConnection.class);
    }
}
