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
package se.sics.nat;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.nat.common.NatTraverserConfig;
import se.sics.p2ptoolbox.util.nat.NatedTrait;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class NatInitHelper {

    private static final Logger LOG = LoggerFactory.getLogger("Config");
    private String logPrefix = "Nat:";

    public final NatTraverserConfig ntConfig;
    public Pair<Integer, Integer> stunClientPorts;
    public final List<Pair<DecoratedAddress, DecoratedAddress>> stunServers;
    public final Integer globalCroupierOverlayId;
    public final List<DecoratedAddress> croupierBoostrap;

    public NatInitHelper(Config config) {
        try {
            ntConfig = NatTraverserConfig.getDefault();
            globalCroupierOverlayId = config.getInt("croupier.global.overlayId");
            LOG.info("{}globalCroupier:{}", logPrefix, globalCroupierOverlayId);
            stunClientPorts = Pair.with(
                    config.getInt("stun.client.port1"),
                    config.getInt("stun.client.port2"));
            LOG.info("{}stun client port1:{} port2:{}", new Object[]{logPrefix, stunClientPorts.getValue0(), stunClientPorts.getValue1()});
            List<String> stunServerNames = config.getStringList("stun.server.list");
            stunServers = new ArrayList<Pair<DecoratedAddress, DecoratedAddress>>();
            for (String stunServerName : stunServerNames) {
                InetAddress stunIp = Inet4Address.getByName(config.getString("stun.server." + stunServerName + ".ip"));
                int stunId = config.getInt("stun.server." + stunServerName + ".id");
                Pair<DecoratedAddress, DecoratedAddress> stunServer = Pair.with(
                        new DecoratedAddress(new BasicAddress(
                                        stunIp,
                                        config.getInt("stun.server." + stunServerName + ".port1"),
                                        stunId)),
                        new DecoratedAddress(new BasicAddress(
                                        stunIp,
                                        config.getInt("stun.server." + stunServerName + ".port2"),
                                        stunId)));
                stunServers.add(stunServer);
            }
            LOG.info("{}stun servers:{}", logPrefix, stunServers);
            List<String> croupierBootstrapNames = config.getStringList("croupier.global.bootstrap.list");
            croupierBoostrap = new ArrayList<DecoratedAddress>();
            for (String cbName : croupierBootstrapNames) {
                InetAddress cbIp = Inet4Address.getByName(config.getString("croupier.global.bootstrap." + cbName + ".ip"));
                int cbId = config.getInt("croupier.global.bootstrap." + cbName + ".id");
                int cbPort = config.getInt("croupier.global.bootstrap." + cbName + ".port");
                DecoratedAddress cb = new DecoratedAddress(new BasicAddress(cbIp, cbPort, cbId));
                cb.addTrait(NatedTrait.open());
                croupierBoostrap.add(cb);
            }
            LOG.info("{}croupier bootstrap:{}", logPrefix, croupierBoostrap);
        } catch (ConfigException.Missing ex) {
            LOG.error("{}missing configuration", logPrefix);
            throw new RuntimeException(ex);
        } catch (UnknownHostException ex) {
            LOG.error("{}unknown stun ip", logPrefix);
            throw new RuntimeException(ex);
        }
    }
    
    public void setStunClientPorts(Pair<Integer, Integer> stunClientPorts) {
        this.stunClientPorts = stunClientPorts;
    }
}
