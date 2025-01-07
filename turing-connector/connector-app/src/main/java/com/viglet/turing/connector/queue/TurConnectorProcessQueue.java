/*
 *
 * Copyright (C) 2016-2024 the original author or authors.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.connector.queue;

import com.viglet.turing.client.auth.credentials.TurApiKeyCredentials;
import com.viglet.turing.client.sn.TurSNServer;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.turing.client.sn.job.TurSNJobUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;

import static com.viglet.turing.commons.sn.field.TurSNFieldName.ID;
import static com.viglet.turing.connector.TurConnectorConstants.CONNECTOR_INDEXING_QUEUE;

@Component
@Slf4j
public class TurConnectorProcessQueue {
    private final String turingUrl;
    private final String turingApiKey;
    public TurConnectorProcessQueue(@Value("${turing.url}") String turingUrl,
                                    @Value("${turing.apiKey}") String turingApiKey) {
        this.turingUrl = turingUrl;
        this.turingApiKey = turingApiKey;
    }

    @JmsListener(destination = CONNECTOR_INDEXING_QUEUE)
    @Transactional
    public void receiveIndexingQueue(TurSNJobItems turSNJobItems) {
        log.info("receiveIndexingQueue");
        sendToTuring(turSNJobItems);
    }

    private void sendToTuring(TurSNJobItems turSNJobItems) {
        if (log.isDebugEnabled()) {
            for (TurSNJobItem turSNJobItem : turSNJobItems) {
                log.info("TurSNJobItem Id: {}", turSNJobItem.getAttributes().get(ID));
            }
        }
        TurSNJobUtils.importItems(turSNJobItems,
                new TurSNServer(URI.create(turingUrl), null,
                        new TurApiKeyCredentials(turingApiKey)),
                false);

    }
}
