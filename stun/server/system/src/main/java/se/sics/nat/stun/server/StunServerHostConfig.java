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

import com.google.common.base.Optional;
import se.sics.kompics.config.Config;
import se.sics.ktoolbox.util.trysf.Try;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class StunServerHostConfig {
  public static final String NAT_OVERLAY_PREFIX = "overlayOwners.nat";
  public final byte natOverlayPrefix;

  public StunServerHostConfig(byte natOverlayPrefix) {
    this.natOverlayPrefix = natOverlayPrefix;
  }
  
  public static Try<StunServerHostConfig> instance(Config config) {
    Optional<Integer> intPrefix = config.readValue(NAT_OVERLAY_PREFIX, Integer.class);
    if(!intPrefix.isPresent()) {
      return new Try.Failure(new IllegalStateException("missing:" + NAT_OVERLAY_PREFIX));
    }
    if (intPrefix.get() > 255) {
      return new Try.Failure(new IllegalStateException("expected byte(<255):" + NAT_OVERLAY_PREFIX));
    }
    return new Try.Success(new StunServerHostConfig((byte)(int)intPrefix.get()));
  }
}
