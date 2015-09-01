/*
 * Copyright (C) 2009 Swedish Institute of Computer Science (SICS) Copyright (C)
 * 2009 Royal Institute of Technology (KTH)
 *
 * GVoD is free software; you can redistribute it and/or
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

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class Nat {

    public static final int DEFAULT_RULE_EXPIRATION_TIME = 30 * 1000;
    public static final int UPPER_RULE_EXPIRATION_TIME = 90 * 1000;

    public static final String[] NAT_COMBINATIONS = {
        "NAT_EI_PC_EI", "NAT_EI_PC_HD", "NAT_EI_PC_PD",
        "NAT_EI_PP_EI_AltPC", "NAT_EI_PP_HD", "NAT_EI_PP_PD",
        "NAT_EI_RD_EI", "NAT_EI_RD_HD", "NAT_EI_RD_PD",
        "NAT_HD_PC_HD", "NAT_HD_PP_HD_AltPC", "NAT_HD_PP_HD_AltRD",
        "NAT_HD_RD_HD", "NAT_PD_PC_EI", "NAT_PD_PC_PD",
        "NAT_PD_RD_PD", "NAT_PD_PP_EI", "NAT_PD_PP_PD"
    };

    public static enum Type {

        OPEN("OP"), NAT("NAT"), UPNP("UPNP"), UDP_BLOCKED("UB"), FIREWALL("FW");
        String code;

        private Type(String code) {
            this.code = code;
        }

        public static Type decode(String ap) {

            for (Type mp : Type.values()) {
                if (mp.code.equals(ap)) {
                    return mp;
                }
            }

            return null;
        }
    }

    public static enum MappingPolicy {

        // Ordering of policies is from least restrictive to most restrictive
        OPEN("OP"), ENDPOINT_INDEPENDENT("EI"),
        HOST_DEPENDENT("HD"), PORT_DEPENDENT("PD");
        String code;

        private MappingPolicy(String code) {
            this.code = code;
        }

        public static MappingPolicy decode(String ap) {

            for (MappingPolicy mp : MappingPolicy.values()) {
                if (mp.code.equals(ap)) {
                    return mp;
                }
            }

            return null;
        }
    }

    public static enum AllocationPolicy {

        // Ordering of policies is from least restrictive to most restrictive
        OPEN("OP"), PORT_PRESERVATION("PP"), PORT_CONTIGUITY("PC"), RANDOM("RD");
        String code;

        private AllocationPolicy(String code) {
            this.code = code;
        }

        public static AllocationPolicy decode(String ap) {

            for (AllocationPolicy mp : AllocationPolicy.values()) {
                if (mp.code.equals(ap)) {
                    return mp;
                }
            }

            return null;
        }
    };

    public static enum FilteringPolicy {
        // Ordering of policies is from least restrictive to most restrictive

        OPEN("OP"), ENDPOINT_INDEPENDENT("EI"), HOST_DEPENDENT("HD"), PORT_DEPENDENT("PD");
        String code;

        private FilteringPolicy(String code) {
            this.code = code;
        }

        public static FilteringPolicy decode(String ap) {

            for (FilteringPolicy mp : FilteringPolicy.values()) {
                if (mp.code.equals(ap)) {
                    return mp;
                }
            }

            return null;
        }
    };

    public static enum BindingTimeoutCategory {

        LOW(Nat.DEFAULT_RULE_EXPIRATION_TIME), HIGH(Nat.UPPER_RULE_EXPIRATION_TIME);
        int bindingTimeout;

        private BindingTimeoutCategory(int timeout) {
            this.bindingTimeout = timeout;
        }

        public static BindingTimeoutCategory create(long timeout) {
            if (timeout < LOW.bindingTimeout) {
                return LOW;
            }
            return HIGH;
        }

        public int getBindingTimeout() {
            return bindingTimeout;
        }
    }
}
