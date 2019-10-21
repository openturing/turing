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

package com.viglet.turing.xmpp.storage;

import org.apache.vysper.xmpp.modules.extension.xep0045_muc.stanzas.Delay;
import org.apache.vysper.xmpp.modules.extension.xep0160_offline_storage.MemoryOfflineStorageProvider;
import org.apache.vysper.xmpp.stanza.Stanza;
import org.apache.vysper.xmpp.stanza.StanzaBuilder;

import java.util.Calendar;
import java.util.TimeZone;

public class TurXMPPMemoryOfflineStorageProvider extends MemoryOfflineStorageProvider {

    @Override
    protected void storeStanza(Stanza stanza) {
        StanzaBuilder stanzaBuilder = StanzaBuilder.createClone(stanza, true, null);
        Delay delay = new Delay(stanza.getFrom().getBareJID(), Calendar.getInstance(TimeZone.getTimeZone("UTC")));
        Stanza delayedStanza = stanzaBuilder.addPreparedElement(delay).build();
        super.storeStanza(delayedStanza);
    }

}
