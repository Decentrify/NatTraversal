///*
// * Copyright (C) 2009 Swedish Institute of Computer Science (SICS) Copyright (C)
// * 2009 Royal Institute of Technology (KTH)
// *
// * NatTraverser is free software; you can redistribute it and/or
// * modify it under the terms of the GNU General Public License
// * as published by the Free Software Foundation; either version 2
// * of the License, or (at your option) any later version.
// *
// * This program is distributed in the hope that it will be useful,
// * but WITHOUT ANY WARRANTY; without even the implied warranty of
// * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// * GNU General Public License for more details.
// *
// * You should have received a copy of the GNU General Public License
// * along with this program; if not, write to the Free Software
// * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
// */
//package se.sics.nat.junk;
//
//import com.typesafe.config.Config;
//import com.typesafe.config.ConfigException;
//import java.net.InetAddress;
//import java.net.UnknownHostException;
//import java.util.ArrayList;
//import java.util.List;
//import org.javatuples.Pair;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import se.sics.ktoolbox.util.identifiable.basic.IntIdentifier;
//import se.sics.ktoolbox.util.network.basic.BasicAddress;
//import se.sics.ktoolbox.util.network.nat.NatAwareAddress;
//import se.sics.ktoolbox.util.network.nat.NatAwareAddressImpl;
//
///**
// * @author Alex Ormenisan <aaor@kth.se>
// */
//public class StunServerInitHelper {
//
//    private static final Logger LOG = LoggerFactory.getLogger("Config");
//    private final String logPrefix = "StunServer:";
//
//    public final NatTraverserConfig ntConfig;
//    public final Pair<Integer, Integer> selfPorts;
//    public final int selfId;
//    public final List<NatAwareAddress> partners;
//
//    public StunServerInitHelper(Config config) {
//        this.ntConfig = NatTraverserConfig.getDefault();
//
//        try {
//            selfPorts = Pair.with(
//                    config.getInt("stun.server.port1"),
//                    config.getInt("stun.server.port2"));
//            selfId = config.getInt("stun.server.id");
//            LOG.info("{}stun server port1:{} port2:{} selfId:{}",
//                    new Object[]{logPrefix, selfPorts.getValue0(), selfPorts.getValue1(), selfId});
//
//            partners = new ArrayList<>();
//            List<String> partnerNames = config.getStringList("stun.server.partners");
//            for (String partnerName : partnerNames) {
//                NatAwareAddress partner = NatAwareAddressImpl.open(new BasicAddress(
//                        InetAddress.getByName(config.getString("stun.server." + partnerName + ".ip")),
//                        config.getInt("stun.server." + partnerName + ".port"),
//                        new IntIdentifier(config.getInt("stun.server." + partnerName + ".id"))));
//                partners.add(partner);
//            }
//            LOG.info("{}stun partners:{}", logPrefix, partners);
//
//        } catch (ConfigException.Missing ex) {
//            LOG.error("{}missing config", logPrefix);
//            throw new RuntimeException(ex);
//        } catch (UnknownHostException ex) {
//            LOG.error("{}ip error", logPrefix);
//            throw new RuntimeException(ex);
//        }
//    }
//}
