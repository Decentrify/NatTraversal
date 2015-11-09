/*
 * Copyright (C) 2009 Swedish Institute of Computer Science (SICS) Copyright (C)
 * 2009 Royal Institute of Technology (KTH)
 *
 * KompicsToolbox is free software; you can redistribute it and/or
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
package se.sics.nat.network;

import com.google.common.base.Optional;
import java.net.InetAddress;
import java.util.List;
import se.sics.p2ptoolbox.util.config.KConfigCore;
import se.sics.p2ptoolbox.util.config.KConfigHelper;
import se.sics.p2ptoolbox.util.config.impl.SystemKCWrapper;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class NetworkMngrKCWrapper {
    public final KConfigCore configCore;
    public final SystemKCWrapper system;
    public final Optional<String> rPrefferedInterface;
    public final List rPrefferedInterfaces;
    public InetAddress localIp;
    public InetAddress publicIp;
    
    public NetworkMngrKCWrapper(KConfigCore config) {
        this.configCore = config;
        this.system = new SystemKCWrapper(config);
        this.rPrefferedInterface = config.readValue(NetworkMngrKConfig.prefferedInterface);
        this.rPrefferedInterfaces = KConfigHelper.read(config, NetworkMngrKConfig.prefferedMasks);
    }
    
    public void setLocalIp(InetAddress localIp) {
        this.localIp = localIp;
        configCore.writeValue(NetworkKConfig.localIp, localIp);
    }
    
    public void setPublicIp(InetAddress publicIp) {
        this.publicIp = publicIp;
        configCore.writeValue(NetworkKConfig.publicIp, publicIp);
    }
}
