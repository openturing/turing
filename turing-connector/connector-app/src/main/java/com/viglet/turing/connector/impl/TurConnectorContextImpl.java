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

package com.viglet.turing.connector.impl;

import com.google.common.collect.Iterators;
import com.viglet.turing.client.auth.TurServer;
import com.viglet.turing.client.auth.credentials.TurApiKeyCredentials;
import com.viglet.turing.client.sn.TurSNConstants;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.turing.connector.commons.plugin.TurConnectorSource;
import com.viglet.turing.connector.commons.plugin.TurConnectorContext;
import com.viglet.turing.connector.persistence.model.TurConnectorIndexing;
import com.viglet.turing.connector.persistence.model.TurConnectorStatus;
import com.viglet.turing.connector.persistence.repository.TurConnectorIndexingRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsMessagingTemplate;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.*;

import static com.viglet.turing.commons.sn.field.TurSNFieldName.ID;
import static com.viglet.turing.connector.TurConnectorConstants.CONNECTOR_INDEXING_QUEUE;

@Slf4j
@Component
public class TurConnectorContextImpl implements TurConnectorContext {
    private TurConnectorSource source;
    private TurSNJobItems turSNJobItems = new TurSNJobItems();
    private final Queue<TurSNJobItem> queueLinks = new LinkedList<>();
    private final JmsMessagingTemplate jmsMessagingTemplate;
    private final TurConnectorIndexingRepository turConnectorIndexingRepository;
    private final String turingUrl;
    private final String turingApiKey;
    private final int jobSize;

    public TurConnectorContextImpl(@Value("${turing.url}") String turingUrl,
                                   @Value("${turing.apiKey}") String turingApiKey,
                                   @Value("${turing.connector.job.size:50}") int jobSize,
                                   JmsMessagingTemplate jmsMessagingTemplate,
                                   TurConnectorIndexingRepository turConnectorIndexingRepository) {
        this.jmsMessagingTemplate = jmsMessagingTemplate;
        this.turingUrl = turingUrl;
        this.turingApiKey = turingApiKey;
        this.jobSize = jobSize;
        this.turConnectorIndexingRepository = turConnectorIndexingRepository;

    }

    @Override
    public TurServer getTurServer() {
        return new TurServer(URI.create(turingUrl), new TurApiKeyCredentials(turingApiKey));
    }

    @Override
    public void startIndexing(TurConnectorSource turConnectorSource) {
        source = turConnectorSource;
    }

    @Override
    public void addJobItem(TurSNJobItem turSNJobItem) {
        if (turSNJobItem != null) {
            queueLinks.offer(turSNJobItem);
            processRemainingJobs(source);
        }
    }

    @Override
    public void finishIndexing() {
        if (turSNJobItems.size() > 0) {
            sendToMessageQueue();
            getInfoQueue();
        }
        deIndexObjects(source);
        queueLinks.clear();
    }

    private void processRemainingJobs(TurConnectorSource turConnectorSource) {
        while (!queueLinks.isEmpty()) {
            TurSNJobItem turSNJobItem = queueLinks.poll();
            if (objectNeedBeIndexed(turSNJobItem, turConnectorSource)) {
                createStatus(turSNJobItem, turConnectorSource);
                addJobToMessageQueue(turSNJobItem);
            } else {
                if (objectNeedBeReIndexed(turSNJobItem, turConnectorSource)) {
                    reindexLog(turSNJobItem, turConnectorSource);
                    addJobToMessageQueue(turSNJobItem);
                    modifyStatus(turSNJobItem, turConnectorSource, TurConnectorStatus.REINDEX);
                } else {
                    noModificationLog(turSNJobItem, turConnectorSource);
                    modifyStatus(turSNJobItem, turConnectorSource, TurConnectorStatus.KEEP);
                }

            }
        }
    }

    private void addJobToMessageQueue(TurSNJobItem turSNJobItem) {
        turSNJobItems.add(turSNJobItem);
        sendToMessageQueueWhenMaxSize();
        getInfoQueue();
    }

    private void sendToMessageQueue() {
        if (log.isDebugEnabled()) {
            for (TurSNJobItem turSNJobItem : turSNJobItems) {
                log.debug("TurSNJobItem Id: {}", turSNJobItem.getAttributes().get(ID));
            }
        }
        this.jmsMessagingTemplate.convertAndSend(CONNECTOR_INDEXING_QUEUE, turSNJobItems);

    }

    private void getInfoQueue() {
        log.info("Total Job Item: {}", Iterators.size(turSNJobItems.iterator()));
        log.info("Queue Size: {}", (long) queueLinks.size());
    }

    private void sendToMessageQueueWhenMaxSize() {
        if (turSNJobItems.size() >= jobSize) {
            sendToMessageQueue();
            turSNJobItems = new TurSNJobItems();
        }
    }

    private void noModificationLog(TurSNJobItem turSNJobItem,
                                   TurConnectorSource turConnectorSource) {
        turConnectorIndexingRepository.findByObjectIdAndIndexGroup(turSNJobItem.getId(),
                        turConnectorSource.getSystemId())
                .ifPresent(turAemIndexingsList ->
                        log.info("No Modification {} object ({}) and transactionId = {}",
                                turSNJobItem.getId(), turConnectorSource.getSystemId(),
                                turConnectorSource.getTransactionId()));
    }

    private void reindexLog(TurSNJobItem turSNJobItem,
                            TurConnectorSource turConnectorSource) {
        turConnectorIndexingRepository.findByObjectIdAndIndexGroup(turSNJobItem.getId(),
                        turConnectorSource.getSystemId())
                .ifPresent(turAemIndexingsList ->
                        log.info("ReIndexed {} object ({}) from {} to {} and transactionId = {}",
                                turSNJobItem.getId(), turConnectorSource.getSystemId(),
                                turAemIndexingsList.getFirst().getChecksum(),
                                turSNJobItem.getChecksum(), turConnectorSource.getTransactionId()));
    }

    private void deIndexObjects(TurConnectorSource turConnectorSource) {
        turConnectorIndexingRepository.findContentsShouldBeDeIndexed(turConnectorSource.getSystemId(),
                        turConnectorSource.getTransactionId())
                .ifPresent(turConnectorIndexingList -> {
                            turConnectorIndexingList.forEach(turConnectorIndexing -> {
                                log.info("DeIndex {} object from {} systemId and {} transactionId",
                                        turConnectorIndexing.getId(), turConnectorSource.getSystemId(),
                                        turConnectorSource.getTransactionId());
                                Map<String, Object> attributes = new HashMap<>();
                                attributes.put(TurSNConstants.ID_ATTR, turConnectorIndexing.getId());
                                attributes.put(TurSNConstants.SOURCE_APPS_ATTR,
                                        turConnectorSource.getProviderName());
                                addJobToMessageQueue(new TurSNJobItem(TurSNJobAction.DELETE,
                                        turConnectorSource.getSites().stream().toList(),
                                        turConnectorSource.getLocale(), attributes));
                            });
                            turConnectorIndexingRepository.deleteContentsWereDeIndexed(turConnectorSource.getSystemId(),
                                    turConnectorSource.getTransactionId());
                        }
                );
    }

    private void modifyStatus(TurSNJobItem turSNJobItem, TurConnectorSource turConnectorSource, TurConnectorStatus status) {
        turConnectorIndexingRepository.findByObjectIdAndIndexGroup(turSNJobItem.getId(), turConnectorSource.getSystemId())
                .filter(turConnectorIndexingList -> !turConnectorIndexingList.isEmpty())
                .ifPresent(turConnectorIndexingList -> {
                    if (turConnectorIndexingList.size() > 1) {
                        recreateDuplicatedStatus(turSNJobItem, turConnectorSource);
                    } else {
                        updateStatus(turSNJobItem, turConnectorSource, turConnectorIndexingList, status);
                    }
                });
    }

    private void recreateDuplicatedStatus(TurSNJobItem turSNJobItem, TurConnectorSource turConnectorSource) {
        turConnectorIndexingRepository.deleteByObjectIdAndIndexGroup(turSNJobItem.getId(),
                source.getSystemId());
        log.info("Removed duplicated status {} object ({})",
                turSNJobItem.getId(), turConnectorSource.getSystemId());
        turConnectorIndexingRepository.save(createTurConnectorIndexing(turSNJobItem, turConnectorSource,
                TurConnectorStatus.RECREATE));
        log.info("Recreated status {} object ({}) and transactionId() = {}",
                turSNJobItem.getId(), turConnectorSource.getSystemId(), turConnectorSource.getTransactionId());
    }

    private void updateStatus(TurSNJobItem turSNJobItem, TurConnectorSource turConnectorSource,
                              List<TurConnectorIndexing> turConnectorIndexingList, TurConnectorStatus status) {
        turConnectorIndexingRepository.save(updateTurConnectorIndexing(turConnectorIndexingList.getFirst(),
                turSNJobItem, turConnectorSource, status));
        log.info("Updated status {} object ({}) transactionId() = {}",
                turSNJobItem.getId(), turConnectorSource.getSystemId(), turConnectorSource.getTransactionId());
    }


    private void createStatus(TurSNJobItem turSNJobItem,
                              TurConnectorSource turConnectorSource) {
        turConnectorIndexingRepository.save(createTurConnectorIndexing(turSNJobItem, turConnectorSource,
                TurConnectorStatus.NEW));
        log.info("Created status {} object ({})", turSNJobItem.getId(), source.getSystemId());
    }

    private TurConnectorIndexing createTurConnectorIndexing(TurSNJobItem turSNJobItem,
                                                            TurConnectorSource turConnectorSource,
                                                            TurConnectorStatus status) {
        return new TurConnectorIndexing()
                .setObjectId(turSNJobItem.getId())
                .setIndexGroup(turConnectorSource.getSystemId())
                .setTransactionId(turConnectorSource.getTransactionId())
                .setLocale(turSNJobItem.getLocale())
                .setChecksum(turSNJobItem.getChecksum())
                .setCreated(new Date())
                .setStatus(status);
    }

    private static TurConnectorIndexing updateTurConnectorIndexing(TurConnectorIndexing turConnectorIndexing,
                                                                   TurSNJobItem turSNJobItem,
                                                                   TurConnectorSource turConnectorSource,
                                                                   TurConnectorStatus status) {
        return turConnectorIndexing
                .setChecksum(turSNJobItem.getChecksum())
                .setTransactionId(turConnectorSource.getTransactionId())
                .setModificationDate(new Date())
                .setStatus(status);
    }

    private boolean objectNeedBeIndexed(TurSNJobItem turSNJobItem, TurConnectorSource turConnectorSource) {
        return (!StringUtils.isEmpty(turSNJobItem.getId()) &&
                !turConnectorIndexingRepository.existsByObjectIdAndIndexGroup(turSNJobItem.getId(),
                        turConnectorSource.getSystemId()));
    }

    private boolean objectNeedBeReIndexed(TurSNJobItem turSNJobItem, TurConnectorSource turConnectorSource) {
        return !StringUtils.isEmpty(turSNJobItem.getId()) &&
                turConnectorIndexingRepository.existsByObjectIdAndIndexGroupAndChecksumNot(turSNJobItem.getId(),
                        turConnectorSource.getSystemId(), turSNJobItem.getChecksum());
    }
}
