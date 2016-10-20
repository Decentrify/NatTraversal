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
package se.sics.nat.pm;

import com.google.common.primitives.Ints;
import java.nio.ByteBuffer;
import se.sics.p2ptoolbox.util.config.KConfigCore;
import se.sics.p2ptoolbox.util.config.KConfigHelper;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class ParentMakerKCWrapper {

    public final KConfigCore configCore;
    public final ByteBuffer globalCroupier;
    public final ByteBuffer natParentService;
    public final long heartbeatTimeout = 2000;
    public final long internalStateCheck = 30000;
    public final int nrParents = 2;
    public final int nrChildren = 5;

    public ParentMakerKCWrapper(KConfigCore configCore) {
        this.configCore = configCore;
        this.globalCroupier = ByteBuffer.wrap(Ints.toByteArray(KConfigHelper.read(configCore, ParentMakerKConfig.globalCroupier)));
        this.natParentService = ByteBuffer.wrap(Ints.toByteArray(KConfigHelper.read(configCore, ParentMakerKConfig.natParentService)));
    }
}
