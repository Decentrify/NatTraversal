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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.caracaldb.MessageRegistrator;
import se.sics.kompics.Channel;
import se.sics.kompics.ClassMatchedHandler;
import se.sics.kompics.Component;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.Kompics;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.Timer;
import se.sics.kompics.timer.java.JavaTimer;
import se.sics.ktoolbox.cc.heartbeat.CCHeartbeatPort;
import se.sics.ktoolbox.cc.mngr.CCMngrComp;
import se.sics.ktoolbox.cc.mngr.event.CCMngrStatus;
import se.sics.ktoolbox.croupier.CroupierPort;
import se.sics.ktoolbox.croupier.CroupierSerializerSetup;
import se.sics.ktoolbox.gradient.GradientSerializerSetup;
import se.sics.ktoolbox.netmngr.NetMngrPort;
import se.sics.ktoolbox.netmngr.NetworkMngrComp;
import se.sics.ktoolbox.netmngr.NetworkMngrSerializerSetup;
import se.sics.ktoolbox.netmngr.event.NetMngrReady;
import se.sics.ktoolbox.overlaymngr.OMngrSerializerSetup;
import se.sics.ktoolbox.overlaymngr.OverlayMngrComp;
import se.sics.ktoolbox.overlaymngr.OverlayMngrPort;
import se.sics.ktoolbox.util.network.nat.NatAwareAddress;
import se.sics.ktoolbox.util.overlays.view.OverlayViewUpdatePort;
import se.sics.ktoolbox.util.setup.BasicSerializerSetup;
import se.sics.ktoolbox.util.status.Status;
import se.sics.ktoolbox.util.status.StatusPort;

/**
 *
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class StunServerHostLauncher extends ComponentDefinition {
    
    private static final Logger LOG = LoggerFactory.getLogger(StunServerHostLauncher.class);
    private String logPrefix = " ";

    //******************************CONNECTIONS*********************************
    //*************************INTERNAL_NO_CONNECTION***************************
    private Positive<StatusPort> externalStatusPort = requires(StatusPort.class);
    //****************************EXTRENAL_STATE********************************
    private NatAwareAddress systemAdr;
    //********************************CLEANUP***********************************
    private Component timerComp;
    private Component netMngrComp;
    private Component ccMngrComp;
    private Component oMngrComp;
    private Component hostComp;

    public StunServerHostLauncher() {
        LOG.info("initiating...");

        subscribe(handleStart, control);
        subscribe(handleNetReady, externalStatusPort);
        subscribe(handleCCReady, externalStatusPort);
    }

    private Handler handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            LOG.info("{}starting...", logPrefix);
            
            timerComp = create(JavaTimer.class, Init.NONE);
            setNetMngr();
            trigger(Start.event, timerComp.control());
            trigger(Start.event, netMngrComp.control());
        }
    };

    private void setNetMngr() {
        LOG.info("{}setting up network mngr", logPrefix);
        NetworkMngrComp.ExtPort netExtPorts = new NetworkMngrComp.ExtPort(timerComp.getPositive(Timer.class));
        netMngrComp = create(NetworkMngrComp.class, new NetworkMngrComp.Init(netExtPorts));
        connect(netMngrComp.getPositive(StatusPort.class), externalStatusPort.getPair(), Channel.TWO_WAY);
    }

    ClassMatchedHandler handleNetReady
            = new ClassMatchedHandler<NetMngrReady, Status.Internal<NetMngrReady>>() {
                @Override
                public void handle(NetMngrReady content, Status.Internal<NetMngrReady> container) {
                    LOG.info("{}network mngr ready", logPrefix);
                    systemAdr = content.systemAdr;
                    setCCMngr();
                    trigger(Start.event, ccMngrComp.control());
                }
            };

    private void setCCMngr() {
        LOG.info("{}setting up caracal client", logPrefix);
        CCMngrComp.ExtPort ccMngrExtPorts = new CCMngrComp.ExtPort(timerComp.getPositive(Timer.class),
                netMngrComp.getPositive(Network.class));
        ccMngrComp = create(CCMngrComp.class, new CCMngrComp.Init(systemAdr, ccMngrExtPorts));
        connect(ccMngrComp.getPositive(StatusPort.class), externalStatusPort.getPair(), Channel.TWO_WAY);
    }

    ClassMatchedHandler handleCCReady
            = new ClassMatchedHandler<CCMngrStatus.Ready, Status.Internal<CCMngrStatus.Ready>>() {
                @Override
                public void handle(CCMngrStatus.Ready content, Status.Internal<CCMngrStatus.Ready> container) {
                    LOG.info("{}caracal client ready", logPrefix);
                    setOMngr();
                    setHost();
                    trigger(Start.event, oMngrComp.control());
                    trigger(Start.event, hostComp.control());
                }
            };

    private void setOMngr() {
        LOG.info("{}setting up overlay mngr", logPrefix);
        OverlayMngrComp.ExtPort oMngrExtPorts = new OverlayMngrComp.ExtPort(timerComp.getPositive(Timer.class),
                netMngrComp.getPositive(Network.class), ccMngrComp.getPositive(CCHeartbeatPort.class));
        oMngrComp = create(OverlayMngrComp.class, new OverlayMngrComp.Init(systemAdr, oMngrExtPorts));
    }

    private void setHost() {
        LOG.info("{}setting up host", logPrefix);
        StunServerHostComp.ExtPort hostExtPorts = new StunServerHostComp.ExtPort(timerComp.getPositive(Timer.class),
                netMngrComp.getPositive(Network.class), oMngrComp.getPositive(CroupierPort.class),
                oMngrComp.getNegative(OverlayViewUpdatePort.class));
        hostComp = create(StunServerHostComp.class, new StunServerHostComp.Init(hostExtPorts));
        connect(hostComp.getNegative(NetMngrPort.class), netMngrComp.getPositive(NetMngrPort.class), Channel.TWO_WAY);
        connect(hostComp.getNegative(OverlayMngrPort.class), oMngrComp.getPositive(OverlayMngrPort.class), Channel.TWO_WAY);
    }

    private static void systemSetup() {
        //serializers setup
        int serializerId = 128;
        MessageRegistrator.register();
        serializerId = BasicSerializerSetup.registerBasicSerializers(serializerId);
        serializerId = CroupierSerializerSetup.registerSerializers(serializerId);
        serializerId = GradientSerializerSetup.registerSerializers(serializerId);
        serializerId = OMngrSerializerSetup.registerSerializers(serializerId);
        serializerId = NetworkMngrSerializerSetup.registerSerializers(serializerId);

        if (serializerId > 255) {
            throw new RuntimeException("switch to bigger serializerIds, last serializerId:" + serializerId);
        }

        //hooks setup
        //no hooks needed
    }

    public static void main(String[] args) {
        systemSetup();
        start();
        try {
            Kompics.waitForTermination();
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex.getMessage());
        }
    }

    public static void start() {
        if (Kompics.isOn()) {
            Kompics.shutdown();
        }
        Kompics.createAndStart(StunServerHostLauncher.class, Runtime.getRuntime().availableProcessors(), 20); // Yes 20 is totally arbitrary
    }

    public static void stop() {
        Kompics.shutdown();
    }
}
