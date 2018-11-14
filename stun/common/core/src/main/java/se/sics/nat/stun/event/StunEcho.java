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
package se.sics.nat.stun.event;

import com.google.common.base.Optional;
import se.sics.kompics.util.Identifier;
import se.sics.ktoolbox.util.network.KAddress;
import se.sics.ktoolbox.util.network.nat.NatAwareAddress;

/**
 * SAME_PORT discovers port mapping and allocation policies. The server replies
 * with the the public IP it received the req on. SAME_PORT is performed on 2
 * stun servers to test (1) for presence of NAT, and (2) for presence of a
 * firewall.
 * <p>
 * DIFF_PORT and PARTNER discover port filtering policy. DIFF_PORT involves
 * sending req to StunServer1 who replies over a different socket bound on a
 * different port.
 * <p>
 * PARTNER involves sending req to StunServer1 who delegates to StunServer2 who
 * sends response.
 *
 * @author Alex Ormenisan <aaor@kth.se>
 */
public abstract class StunEcho implements StunEvent {

  public static enum Type {

    SIP_SP,
    SIP_DP,
    DIP_DP,
    DIP_SP
  }

  public final Identifier msgId;
  public final Identifier sessionId;
  public final Type type;

  private StunEcho(Identifier eventId, Identifier sessionId, Type type) {
    this.msgId = eventId;
    this.sessionId = sessionId;
    this.type = type;
  }

  public static class Request extends StunEcho {

    public final NatAwareAddress target;

    public Request(Identifier msgId, Identifier sessionId, Type type, NatAwareAddress target) {
      super(msgId, sessionId, type);
      this.target = target;
    }

    public Response answer(KAddress src) {
      return new Response(msgId, sessionId, type, Optional.of(src));
    }

    public Response answer() {
      return new Response(msgId, sessionId, type, Optional.absent());
    }

    public Reset reset() {
      return new Reset(msgId, sessionId, type);
    }

    @Override
    public String toString() {
      return "StunEchoReq<" + sessionId + ", " + type.toString() + ", " + msgId + ">";
    }

    @Override
    public Identifier getId() {
      return msgId;
    }
  }

  public static class Response extends StunEcho {

    public final Optional<NatAwareAddress> observed;

    Response(Identifier msgId, Identifier sessionId, Type type, Optional observed) {
      super(msgId, sessionId, type);
      this.observed = observed;
    }

    @Override
    public String toString() {
      return "StunEchoResp<" + sessionId + ", " + type.toString() + ", " + msgId + ">";
    }

    @Override
    public Identifier getId() {
      return msgId;
    }
  }

  public static class Reset extends StunEcho {

    public Reset(Identifier msgId, Identifier sessionId, Type type) {
      super(msgId, sessionId, type);
    }

    @Override
    public String toString() {
      return "StunEchoReset<" + sessionId + ", " + type.toString() + ", " + msgId + ">";
    }

    @Override
    public Identifier getId() {
      return msgId;
    }
  }
}
