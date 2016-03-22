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
package se.sics.nat.filters;

import com.google.common.base.Optional;
import se.sics.kompics.ChannelFilter;
import se.sics.kompics.network.Msg;
import se.sics.kompics.network.Transport;
import se.sics.p2ptoolbox.croupier.msg.CroupierMsg;
import se.sics.p2ptoolbox.util.nat.NatedTrait;
import se.sics.ktoolbox.util.msg.BasicContentMsg;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;
import se.sics.ktoolbox.util.msg.DecoratedHeader;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class NatTrafficFilter extends ChannelFilter<Msg, Boolean> {
    public NatTrafficFilter() {
        super(Msg.class, true, true);
    }

    @Override
    public Boolean getValue(Msg msg) {
        return checkNatTraffic(msg).isPresent();
    }

    public static Optional<BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Object>>
            checkNatTraffic(Msg msg) {
        BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, Object> contentMsg = null;
        if (msg instanceof BasicContentMsg) {
            contentMsg = (BasicContentMsg) msg;
        }
        if (contentMsg == null) {
            return Optional.absent();
        }
        if (!contentMsg.getProtocol().equals(Transport.UDP)) {
            return Optional.absent();
        }
        if (NatedTrait.isOpen(contentMsg.getSource()) && NatedTrait.isOpen(contentMsg.getDestination())) {
            return Optional.absent();
        }
        if (contentMsg.getContent() instanceof CroupierMsg) {
            return Optional.absent();
        }
        return Optional.of(contentMsg);
    }
}
