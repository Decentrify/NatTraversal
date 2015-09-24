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
package se.sics.nat.example.node;

import se.sics.nat.example.node.msg.NodeMsg;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.kompics.ClassMatchedHandler;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.Stop;
import se.sics.kompics.network.Network;
import se.sics.kompics.network.Transport;
import se.sics.p2ptoolbox.croupier.CroupierPort;
import se.sics.p2ptoolbox.croupier.msg.CroupierSample;
import se.sics.p2ptoolbox.util.Container;
import se.sics.p2ptoolbox.util.nat.NatedTrait;
import se.sics.p2ptoolbox.util.network.ContentMsg;
import se.sics.p2ptoolbox.util.network.impl.BasicContentMsg;
import se.sics.p2ptoolbox.util.network.impl.DecoratedAddress;
import se.sics.p2ptoolbox.util.network.impl.DecoratedHeader;
import se.sics.p2ptoolbox.util.update.SelfAddressUpdate;
import se.sics.p2ptoolbox.util.update.SelfAddressUpdatePort;

/**
 * @author Alex Ormenisan <aaor@kth.se>
 */
public class NodeComp extends ComponentDefinition {

    private static final Logger LOG = LoggerFactory.getLogger(NodeComp.class);
    private String logPrefix = "";

    private Positive<Network> network = requires(Network.class);
    private Positive<SelfAddressUpdatePort> selfUpdate = requires(SelfAddressUpdatePort.class);
    private Positive<CroupierPort> croupier = requires(CroupierPort.class);

    private DecoratedAddress self;
    private boolean ping;

    private final Set<String> pinged = new HashSet<String>();
    private final Set<String> ponged = new HashSet<String>();

    public NodeComp(NodeInit init) {
        this.self = init.self;
        this.logPrefix = self.getBase() + " ";
        this.ping = init.ping;
        LOG.info("{}initiating with self:{} ping:{}", 
                new Object[]{logPrefix, self, ping});

        subscribe(handleStart, control);
        subscribe(handleStop, control);
        subscribe(handleSelfUpdate, selfUpdate);
        subscribe(handleCroupierSample, croupier);
        subscribe(handlePing, network);
        subscribe(handlePong, network);
    }

    Handler handleStart = new Handler<Start>() {
        @Override
        public void handle(Start event) {
            LOG.info("{}starting", logPrefix);
        }
    };

    Handler handleStop = new Handler<Stop>() {
        @Override
        public void handle(Stop event) {
            LOG.info("{}stopping...", logPrefix);
        }
    };

    Handler handleSelfUpdate = new Handler<SelfAddressUpdate>() {
        @Override
        public void handle(SelfAddressUpdate update) {
            LOG.info("{}update self:{}", logPrefix, update.self);
            self = update.self;
        }
    };

    Handler handleCroupierSample = new Handler<CroupierSample<Object>>() {
        @Override
        public void handle(CroupierSample<Object> sample) {
            LOG.info("{}received public:{}, private:{}",
                    new Object[]{logPrefix, sample.publicSample, sample.privateSample});
            if (!ping) {
                return;
            }
            LOG.info("{}nat:{}", logPrefix, self.getTrait(NatedTrait.class).natToString());
            LOG.info("{}pinged:{}", logPrefix, pinged);
            LOG.info("{}ponged:{}", logPrefix, ponged);
            for (Container<DecoratedAddress, Object> target : sample.publicSample) {
                if (!ponged.contains(target.getSource().getTrait(NatedTrait.class).natToString())) {
                    pinged.add(target.getSource().getTrait(NatedTrait.class).natToString());
                    DecoratedHeader<DecoratedAddress> pingHeader = new DecoratedHeader(self, target.getSource(), Transport.UDP);
                    ContentMsg pingMsg = new BasicContentMsg(pingHeader, new NodeMsg.Ping());
                    LOG.info("{}pinging from:{} to:{}", new Object[]{logPrefix, self, target.getSource()});
                    trigger(pingMsg, network);
                }
            }
            for (Container<DecoratedAddress, Object> target : sample.privateSample) {
                if (!ponged.contains(target.getSource().getTrait(NatedTrait.class).natToString())) {
                    pinged.add(target.getSource().getTrait(NatedTrait.class).natToString());
                    DecoratedHeader<DecoratedAddress> pingHeader = new DecoratedHeader(self, target.getSource(), Transport.UDP);
                    ContentMsg pingMsg = new BasicContentMsg(pingHeader, new NodeMsg.Ping());
                    LOG.info("{}pinging from:{} to:{}", new Object[]{logPrefix, self, target.getSource()});
                    trigger(pingMsg, network);
                }
            }
        }
    };

    ClassMatchedHandler handlePing
            = new ClassMatchedHandler<NodeMsg.Ping, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, NodeMsg.Ping>>() {
                @Override
                public void handle(NodeMsg.Ping content, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, NodeMsg.Ping> container) {
                    LOG.info("{}ping from:{} on:{}",
                            new Object[]{logPrefix, container.getSource(), container.getDestination()});
                    DecoratedHeader<DecoratedAddress> pongHeader = new DecoratedHeader(self, container.getSource(), Transport.UDP);
                    ContentMsg pongMsg = new BasicContentMsg(pongHeader, new NodeMsg.Pong());
                    trigger(pongMsg, network);
                }
            };

    ClassMatchedHandler handlePong
            = new ClassMatchedHandler<NodeMsg.Pong, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, NodeMsg.Pong>>() {
                @Override
                public void handle(NodeMsg.Pong content, BasicContentMsg<DecoratedAddress, DecoratedHeader<DecoratedAddress>, NodeMsg.Pong> container) {
                    LOG.info("{}pong from:{} on:{}",
                            new Object[]{logPrefix, container.getSource(), container.getDestination()});
                    ponged.add(container.getSource().getTrait(NatedTrait.class).natToString());
                }
            };

    public static class NodeInit extends Init<NodeComp> {

        public final DecoratedAddress self;
        public final boolean ping;

        public NodeInit(DecoratedAddress self, boolean ping) {
            this.self = self;
            this.ping = ping;
        }
    }
}
