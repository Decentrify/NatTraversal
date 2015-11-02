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
package se.sics.nat.stun.server;

import com.google.common.primitives.Ints;
import org.javatuples.Pair;
import se.sics.p2ptoolbox.util.config.KConfigCore;
import se.sics.p2ptoolbox.util.config.KConfigHelper;
import se.sics.p2ptoolbox.util.config.impl.SystemKCWrapper;

/**
 *
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class StunServerKCWrapper {

    public final KConfigCore configCore;
    public final SystemKCWrapper system;
    public final Pair<Integer, Integer> stunServerPorts;
    public final int nodePort;
    public final boolean hardBind = true;
    public final byte[] globalCroupier;
    public final byte[] stunService;
    public final long rtt = 2000;

    public StunServerKCWrapper(KConfigCore configCore) {
        this.configCore = configCore;
        this.system = new SystemKCWrapper(configCore);
        this.stunServerPorts = Pair.with(KConfigHelper.read(configCore, StunServerKConfig.stunServerPort1),
                KConfigHelper.read(configCore, StunServerKConfig.stunServerPort2));
        this.nodePort = KConfigHelper.read(configCore, StunServerKConfig.nodePort);
        this.globalCroupier = Ints.toByteArray(KConfigHelper.read(configCore, StunServerKConfig.globalCroupier));
        this.stunService = Ints.toByteArray(KConfigHelper.read(configCore, StunServerKConfig.stunService));
    }
}
