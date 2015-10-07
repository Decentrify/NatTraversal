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
package se.sics.nat.hooks;

import com.google.common.base.Optional;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Iterator;
import org.javatuples.Pair;
import se.sics.kompics.Component;
import se.sics.kompics.Handler;
import se.sics.kompics.Start;
import se.sics.ktoolbox.ipsolver.IpSolverComp;
import se.sics.ktoolbox.ipsolver.IpSolverPort;
import se.sics.ktoolbox.ipsolver.msg.GetIp;
import se.sics.ktoolbox.ipsolver.util.IpAddressStatus;
import se.sics.ktoolbox.ipsolver.util.IpHelper;
import se.sics.nat.hooks.NatAddressSolverHook.Definition;
import se.sics.nat.hooks.NatAddressSolverHook.SetupInit;
import se.sics.nat.hooks.NatAddressSolverHook.SetupResult;
import se.sics.nat.hooks.NatAddressSolverHook.StartInit;
import se.sics.nat.hooks.NatAddressSolverHook.TearInit;
import se.sics.p2ptoolbox.util.proxy.ComponentProxy;

/**
 *
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class NatAddressSolverHookFactory {

    private static int MIN_PORT = 10000;
    private static int MAX_PORT = (int) Math.pow((double) 2, (double) 16);

    public Definition getIpSolver() {
        return new Definition() {

            @Override
            public SetupResult setup(ComponentProxy proxy, final NatAddressSolverHP hookParent,
                    final SetupInit setupInit) {
                Component[] comp = new Component[1];
                comp[0] = proxy.create(IpSolverComp.class, new IpSolverComp.IpSolverInit());

                Handler handleGetIp = new Handler<GetIp.Resp>() {
                    @Override
                    public void handle(GetIp.Resp resp) {
                        InetAddress localIp = null;
                        if (!resp.addrs.isEmpty()) {
                            Iterator<IpAddressStatus> it = resp.addrs.iterator();
                            while (it.hasNext()) {
                                IpAddressStatus next = it.next();
                                if (IpHelper.isPublic(next.getAddr())) {
                                    localIp = next.getAddr();
                                    break;
                                }
                            }
                            if (localIp == null) {
                                it = resp.addrs.iterator();
                                while (it.hasNext()) {
                                    IpAddressStatus next = it.next();
                                    if (IpHelper.isPrivate(next.getAddr())) {
                                        localIp = next.getAddr();
                                        break;
                                    }
                                }
                            }
                            if (localIp == null) {
                                localIp = resp.addrs.get(0).getAddr();
                            }
                            Pair<Integer, Optional<Socket>> stunClientPort1 = bind(localIp, hookParent.getStunClientPrefferedPorts().getValue0());
                            Pair<Integer, Optional<Socket>> stunClientPort2 = bind(localIp, hookParent.getStunClientPrefferedPorts().getValue1());
                            Pair<Integer, Optional<Socket>> appPort = bind(localIp, hookParent.getAppPrefferedPort());
                            hookParent.onResult(new NatAddressSolverResult(localIp, stunClientPort1, stunClientPort2, appPort));
                        } else {
                            throw new RuntimeException("no local interfaces detected");
                        }
                    }
                };
                proxy.subscribe(handleGetIp, comp[0].getPositive(IpSolverPort.class));

                return new SetupResult(comp);
            }

            @Override
            public void start(ComponentProxy proxy, NatAddressSolverHP hookParnt,
                    SetupResult setupResult, StartInit startInit) {
                if (!startInit.started) {
                    proxy.trigger(Start.event, setupResult.comp[0].control());
                }
                proxy.trigger(new GetIp.Req(startInit.netInterfaces), setupResult.comp[0].getPositive(IpSolverPort.class));
            }

            @Override
            public void preStop(ComponentProxy proxy, NatAddressSolverHP hookParnt,
                    SetupResult setupResult, TearInit hookTear) {
            }
        };
    }

    private Pair<Integer, Optional<Socket>> bind(InetAddress localIp, Integer prefferedPort) {
        Integer port = (prefferedPort < MIN_PORT ? MIN_PORT : prefferedPort);
        Socket socket;
        while (port < MAX_PORT) {
            try {
                socket = new Socket();
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress(localIp, port));
                socket.close();
                return Pair.with(port, Optional.of(socket));
            } catch (IOException e) {
                port++;
            }
        }

        port = MIN_PORT;
        while (port < prefferedPort) {
            try {
                socket = new Socket();
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress(localIp, port));
                socket.close();
                return Pair.with(port, Optional.of(socket));
            } catch (IOException e) {
                port++;
            }
        }
        throw new RuntimeException("could not bind on any port");
    }

    public Definition getIpSolverEmulator(final InetAddress localIp) {
        return new Definition() {

            @Override
            public SetupResult setup(ComponentProxy proxy, NatAddressSolverHP hookParent,
                    SetupInit hookInit) {
                return new NatAddressSolverHook.SetupResult(new Component[0]);
            }

            @Override
            public void start(ComponentProxy proxy, NatAddressSolverHP hookParent,
                    SetupResult setupResult, StartInit startInit) {
                Pair<Integer, Optional<Socket>> stunClientPort1 = bind(localIp, hookParent.getStunClientPrefferedPorts().getValue0());
                Pair<Integer, Optional<Socket>> stunClientPort2 = bind(localIp, hookParent.getStunClientPrefferedPorts().getValue1());
                Pair<Integer, Optional<Socket>> appPort = bind(localIp, hookParent.getAppPrefferedPort());
                hookParent.onResult(new NatAddressSolverResult(localIp, stunClientPort1, stunClientPort2, appPort));
            }

            @Override
            public void preStop(ComponentProxy proxy, NatAddressSolverHP hookParent,
                    SetupResult setupResult, TearInit hookTear) {
            }
        };
    }
}
