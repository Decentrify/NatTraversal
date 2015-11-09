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
package se.sics.nat.network.util;

import com.google.common.base.Optional;
import java.util.ArrayList;
import java.util.List;
import se.sics.ktoolbox.ipsolver.msg.GetIp;
import se.sics.p2ptoolbox.util.config.KConfigCache;
import se.sics.p2ptoolbox.util.config.KConfigCore;
import se.sics.p2ptoolbox.util.config.KConfigLevel;
import se.sics.p2ptoolbox.util.config.KConfigOption;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class InterfaceMasksOption extends KConfigOption.Composite<List> {
    private final KConfigOption.Basic<List> rawOption;
    
    public InterfaceMasksOption(String name, KConfigLevel lvl, KConfigOption.Basic<List> rawOption) {
        super(name, List.class, lvl);
        this.rawOption = rawOption;
    }

    @Deprecated
    @Override
    public Optional<List> read(KConfigCache config) {
        throw new UnsupportedOperationException("not deleted for backward compile - change to readValue");
    }
    
    @Override
    public Optional<List> readValue(KConfigCore config) {
        Optional<List> sPrefferedInterfaces = config.readValue(rawOption);
        if (!sPrefferedInterfaces.isPresent()) {
            List<GetIp.NetworkInterfacesMask> masks = new ArrayList<>();
            masks.add(GetIp.NetworkInterfacesMask.ALL);
            return Optional.of((List)masks);
        }
        List<GetIp.NetworkInterfacesMask> masks = new ArrayList<>();
        for (String prefInt : (List<String>)sPrefferedInterfaces.get()) {
            switch (prefInt) {
                case "PUBLIC":
                    masks.add(GetIp.NetworkInterfacesMask.PUBLIC);
                    break;
                case "PRIVATE":
                    masks.add(GetIp.NetworkInterfacesMask.PRIVATE);
                    break;
                case "TENDOT":
                    masks.add(GetIp.NetworkInterfacesMask.TEN_DOT_PRIVATE);
                    break;
                default:
                    throw new RuntimeException("unknown:" + prefInt);
            }
        }
        return Optional.of((List) masks);
    }
}
