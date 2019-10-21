/*
 * Copyright (C) 2019 the original author or authors. 
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.xmpp.message;

import org.apache.vysper.xml.fragment.Attribute;
import org.apache.vysper.xml.fragment.XMLElement;
import org.apache.vysper.xmpp.addressing.Entity;
import org.apache.vysper.xmpp.addressing.EntityImpl;
import org.apache.vysper.xmpp.modules.core.base.handler.MessageHandler;
import org.apache.vysper.xmpp.server.ServerRuntimeContext;
import org.apache.vysper.xmpp.server.SessionContext;
import org.apache.vysper.xmpp.stanza.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.converse.xmpp.TurXMPPMessage;

import java.util.Date;
import java.util.Map;

/**
 * @author Yuriy Tumakha.
 */
@Component
public class TurXMPPMessageHandler extends MessageHandler {

    private static final Logger LOG = LoggerFactory.getLogger(TurXMPPMessageHandler.class);

    @Autowired
    private TurXMPPMessageService turXMPPMessageService;

    @Override
    protected Stanza executeCore(XMPPCoreStanza stanza, ServerRuntimeContext serverRuntimeContext,
                                 boolean isOutboundStanza, SessionContext sessionContext) {
        if (isOutboundStanza) {
            Entity from = stanza.getFrom();
            if (from == null || !from.isResourceSet()) {
                // rewrite stanza with new from
                String resource = serverRuntimeContext.getResourceRegistry()
                        .getUniqueResourceForSession(sessionContext);
                if (resource == null)
                    throw new IllegalStateException("could not determine unique resource");
                from = new EntityImpl(sessionContext.getInitiatingEntity(), resource);
                StanzaBuilder stanzaBuilder = new StanzaBuilder(stanza.getName(), stanza.getNamespaceURI());
                for (Attribute attribute : stanza.getAttributes()) {
                    if ("from".equals(attribute.getName()))
                        continue;
                    stanzaBuilder.addAttribute(attribute);
                }
                stanzaBuilder.addAttribute("from", from.getFullQualifiedName());
                for (XMLElement preparedElement : stanza.getInnerElements()) {
                    stanzaBuilder.addPreparedElement(preparedElement);
                }
                stanza = XMPPCoreStanza.getWrapper(stanzaBuilder.build());
            }

            MessageStanza messageStanza = (MessageStanza) stanza;
            MessageStanzaType messageStanzaType = messageStanza.getMessageType();
            switch (messageStanzaType) {
                case CHAT:
                case GROUPCHAT:
                    try {
                        Map<String, XMLElement> bodies = messageStanza.getBodies();
                        if (bodies.size() > 0) {
                        	
                            TurXMPPMessage message = new TurXMPPMessage();
                            message.setFromJID(messageStanza.getFrom().getBareJID().getFullQualifiedName());
                            message.setToJID(messageStanza.getTo().getBareJID().getFullQualifiedName());
                            message.setBody(bodies.values().iterator().next().getSingleInnerText().getText());
                            message.setTime(new Date().getTime());
                            turXMPPMessageService.saveMessage(message);
                            
                        }
                    } catch (Exception e) {
                        LOG.error("Save message failed.", e);
                    }
            }
        }
        return super.executeCore(stanza, serverRuntimeContext, isOutboundStanza, sessionContext);
    }
}
