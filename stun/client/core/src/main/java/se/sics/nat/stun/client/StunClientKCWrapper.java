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
package se.sics.nat.stun.client;

import com.google.common.primitives.Ints;
import org.javatuples.Pair;
import se.sics.p2ptoolbox.util.config.KConfigCore;
import se.sics.p2ptoolbox.util.config.KConfigHelper;
import se.sics.p2ptoolbox.util.config.impl.SystemKCWrapper;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class StunClientKCWrapper {
    
    public final KConfigCore configCore;
    public final SystemKCWrapper system;
    public final Pair<Integer, Integer> stunClientPorts;
    public final boolean hardBind = true;
    public final byte[] globalCroupier;
    public final byte[] stunService;
    public final long rtt = 2000;
    public final static long CONFIG_TIMEOUT = 1000;
    public final static long ECHO_TIMEOUT = 1000;
    
    public StunClientKCWrapper(KConfigCore configCore) {
        this.configCore = configCore;
        system = new SystemKCWrapper(configCore);
        stunClientPorts = Pair.with(KConfigHelper.read(configCore, StunClientKConfig.stunClientPort1),
                KConfigHelper.read(configCore, StunClientKConfig.stunClientPort2));
        globalCroupier = Ints.toByteArray(KConfigHelper.read(configCore, StunClientKConfig.globalCroupier));
        stunService = Ints.toByteArray(KConfigHelper.read(configCore, StunClientKConfig.stunService));
    }
}
