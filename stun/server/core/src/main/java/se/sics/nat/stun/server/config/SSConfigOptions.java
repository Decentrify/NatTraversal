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
package se.sics.nat.stun.server.config;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.p2ptoolbox.util.config.KConfigCache;
import se.sics.p2ptoolbox.util.config.KConfigLevel;
import se.sics.p2ptoolbox.util.config.KConfigOption;
import se.sics.p2ptoolbox.util.config.KConfigOption.Base;
import se.sics.p2ptoolbox.util.config.KConfigOption.Basic;
import se.sics.p2ptoolbox.util.config.KConfigOption.Composite;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class SSConfigOptions {

    private static final Logger LOG = LoggerFactory.getLogger(SSConfigOptions.class);
    private String logPrefix = "";

    public static final Basic<String> LOCAL_IP
            = new KConfigOption.Basic("system.address.ip", String.class, SSConfigSetup.configLvl);
    public static final Basic<Integer> HOOK_RETRIES
            = new KConfigOption.Basic("system.hookRetries", Integer.class, SSConfigSetup.configLvl);
    public static final Basic<Integer> SS_PORT1
            = new KConfigOption.Basic("stunServer.address.port1", Integer.class, SSConfigSetup.configLvl);
    public static final Basic<Integer> SS_PORT2
            = new KConfigOption.Basic("stunServer.address.port2", Integer.class, SSConfigSetup.configLvl);
    public static final Composite<InetAddress> LOCAL_INET_IP
            = new InetAddressOption("system.address.inetIp", InetAddress.class, SSConfigSetup.configLvl, LOCAL_IP);

    public static final Basic[] ssOptions = new Basic[4];
    static {
        ssOptions[0] = LOCAL_IP;
        ssOptions[1] = HOOK_RETRIES;
        ssOptions[2] = SS_PORT1;
        ssOptions[3] = SS_PORT2;
    }
    public static class InetAddressOption extends KConfigOption.Composite<InetAddress> {

        private final KConfigOption.Basic<String> stringIpOption;

        public InetAddressOption(String name, Class<InetAddress> type, KConfigLevel lvl,
                KConfigOption.Basic stringIpOption) {
            super(name, type, lvl);
            this.stringIpOption = stringIpOption;
        }

        @Override
        public InetAddress read(KConfigCache config) {
            String stringIp = config.read(stringIpOption);
            try {
                return InetAddress.getByName(stringIp);
            } catch (UnknownHostException ex) {
                LOG.error("{}config error - could not determine ip:{}", config.getNodeId(), stringIp);
                throw new RuntimeException("config error - could not determine ip:" + stringIp);
            }
        }
    }

    private static KConfigOption.Composite<BasicAddress> getBasicAddressOption(
            String optionName, KConfigOption.Basic portOption) {
        KConfigOption.Basic<String> stringIpOption = (KConfigOption.Basic<String>) LOCAL_IP;
        KConfigOption.Composite<InetAddress> ipOption = new InetAddressOption("system.address.inetIp",
                InetAddress.class, SSConfigSetup.configLvl, stringIpOption);
        return new BasicAddressOption(optionName, BasicAddress.class, SSConfigSetup.configLvl,
                ipOption, portOption);
    }

    public static class BasicAddressOption extends KConfigOption.Composite<BasicAddress> {

        private final KConfigOption.Composite<InetAddress> ipOption;
        private final KConfigOption.Basic<Integer> portOption;

        public BasicAddressOption(String name, Class<BasicAddress> type, KConfigLevel lvl,
                KConfigOption.Composite<InetAddress> ipOption, KConfigOption.Basic<Integer> portOption) {
            super(name, type, lvl);
            this.ipOption = ipOption;
            this.portOption = portOption;
        }

        @Override
        public BasicAddress read(KConfigCache config) {
            InetAddress ip = config.read(ipOption);
            int port = config.read(portOption);
            return new BasicAddress(ip, port, config.getNodeId());
        }
    }

}
