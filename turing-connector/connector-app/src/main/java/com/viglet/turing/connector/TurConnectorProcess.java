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

package com.viglet.turing.connector;

import com.google.common.collect.Iterators;
import com.google.inject.Inject;
import com.viglet.turing.client.auth.credentials.TurApiKeyCredentials;
import com.viglet.turing.client.sn.TurSNServer;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.turing.client.sn.job.TurSNJobUtils;
import com.viglet.turing.connector.plugin.TurConnectorPlugin;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.*;

@Slf4j
@Component
public class TurConnectorProcess {
    public static final String ID_ATTR = "id";
    private final String turingUrl;
    private final String turingApiKey;
    private TurSNJobItems turSNJobItems = new TurSNJobItems();
    private final Set<String> visitedLinks = new HashSet<>();
    private final Queue<TurSNJobItem> queueLinks = new LinkedList<>();
    private final int timeout;
    private final int jobSize;
    @Inject
    public TurConnectorProcess(@Value("${turing.url}") String turingUrl,
                               @Value("${turing.apiKey}") String turingApiKey,
                               @Value("${turing.connector.timeout:5000}") int timeout,
                               @Value("${turing.connector.job.size:50}") int jobSize) {
        this.turingUrl = turingUrl;
        this.turingApiKey = turingApiKey;
        this.timeout = timeout;
        this.jobSize = jobSize;
    }

    public void start(TurConnectorPlugin plugin) {
        reset();
        TurSNJobItem currentItem = plugin.getNext();

        if (currentItem != null) {
                queueLinks.offer(currentItem);
                getPagesFromQueue(plugin);
        }
        if (turSNJobItems.size() > 0) {
            sendToTuring();
            getInfoQueue();
        }
    }
    private void sendToTuring() {
        if (log.isDebugEnabled()) {
            for (TurSNJobItem turSNJobItem : turSNJobItems) {
                log.debug("TurSNJobItem Id: {}", turSNJobItem.getAttributes().get(ID_ATTR));
            }
        }
        try {
            TurSNJobUtils.importItems(turSNJobItems,
                    new TurSNServer(URI.create(turingUrl).toURL(), null,
                            new TurApiKeyCredentials(turingApiKey)),
                    false);
        } catch (MalformedURLException e) {
            log.error(e.getMessage(), e);
        }

    }
    private void reset() {
        turSNJobItems = new TurSNJobItems();
        visitedLinks.clear();
    }

    private void getInfoQueue() {
        log.info("Total Job Item: {}", Iterators.size(turSNJobItems.iterator()));
        log.info("Total Visited Links: {}", (long) visitedLinks.size());
        log.info("Queue Size: {}", (long) queueLinks.size());
    }

    public void getPagesFromQueue(TurConnectorPlugin plugin) {
        while (!queueLinks.isEmpty()) {
            turSNJobItems.add(queueLinks.poll());
            sendToTuringWhenMaxSize();
            getInfoQueue();
        }
    }

    private void sendToTuringWhenMaxSize() {
        if (turSNJobItems.size() >= jobSize) {
            sendToTuring();
            turSNJobItems = new TurSNJobItems();
        }
    }
}