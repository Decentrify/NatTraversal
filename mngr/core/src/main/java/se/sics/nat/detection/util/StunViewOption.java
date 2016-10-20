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
package se.sics.nat.detection.util;

import com.google.common.base.Optional;
import java.net.InetAddress;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.config.Config;
import se.sics.ktoolbox.util.config.KConfigOption;
import se.sics.ktoolbox.util.config.KConfigOption.Basic;
import se.sics.ktoolbox.util.config.options.InetAddressOption;
import se.sics.ktoolbox.util.identifiable.BasicBuilders;
import se.sics.ktoolbox.util.identifiable.BasicIdentifiers;
import se.sics.ktoolbox.util.identifiable.Identifier;
import se.sics.ktoolbox.util.identifiable.IdentifierFactory;
import se.sics.ktoolbox.util.identifiable.IdentifierRegistry;
import se.sics.ktoolbox.util.network.basic.BasicAddress;
import se.sics.ktoolbox.util.network.nat.NatAwareAddress;
import se.sics.ktoolbox.util.network.nat.NatAwareAddressImpl;
import se.sics.nat.stun.util.StunView;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class StunViewOption extends KConfigOption.Base<StunView> {
    private static final Logger LOG = LoggerFactory.getLogger("KConfig");
    
    public StunViewOption(String optName) {
        super(optName, StunView.class);
    }

    @Override
    public Optional<StunView> readValue(Config config) {
        Optional<Pair<NatAwareAddress, NatAwareAddress>> serverAdr = getAdr(config, name+".server");
        if(!serverAdr.isPresent()){
            LOG.debug("missing:{}", name+".server");
            return Optional.absent();
        }
        Optional<Pair<NatAwareAddress, NatAwareAddress>> partnerAdr = getAdr(config, name+".partner");
        if(!partnerAdr.isPresent()){
            LOG.debug("missing:{}", name+".partner");
            return Optional.absent();
        }
        return Optional.of(new StunView(serverAdr.get(), partnerAdr));
    }
    
    private Optional<Pair<NatAwareAddress, NatAwareAddress>> getAdr(Config config, String name) {
        InetAddressOption ipOpt = new InetAddressOption(name + ".ip");
        Optional<InetAddress> ip = ipOpt.readValue(config);
        if (!ip.isPresent()) {
            LOG.debug("missing:{}", ipOpt.name);
            return Optional.absent();
        }
        Basic<Integer> port1Opt = new Basic(name + ".port1", Integer.class);
        Optional<Integer> port1 = port1Opt.readValue(config);
        if (!port1.isPresent()) {
            LOG.debug("missing:{}", port1Opt.name);
            return Optional.absent();
        }
        Basic<Integer> port2Opt = new Basic(name + ".port2", Integer.class);
        Optional<Integer> port2 = port2Opt.readValue(config);
        if (!port2.isPresent()) {
            LOG.debug("missing:{}", port2Opt.name);
            return Optional.absent();
        }
        Basic<Integer> idOpt = new Basic(name + ".id", Integer.class);
        Optional<Integer> id = idOpt.readValue(config);
        if (!id.isPresent()) {
            LOG.debug("missing:{}", idOpt.name);
            return Optional.absent();
        }
        IdentifierFactory nodeIdFactory = IdentifierRegistry.lookup(BasicIdentifiers.Values.NODE.toString());
        Identifier nodeId = nodeIdFactory.id(new BasicBuilders.IntBuilder(id.get()));
        return Optional.of(Pair.with(
                (NatAwareAddress)NatAwareAddressImpl.open(new BasicAddress(ip.get(), port1.get(), nodeId)),
                (NatAwareAddress)NatAwareAddressImpl.open(new BasicAddress(ip.get(), port2.get(), nodeId))));
    }
    
}
