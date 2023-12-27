/*
 * Copyright (C) 2016-2022 the original author or authors. 
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.viglet.turing.api.sn.queue;

import com.google.inject.Inject;
import com.viglet.turing.api.sn.job.TurSNJob;
import com.viglet.turing.api.sn.job.TurSNJobItem;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.sn.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.sn.TurSNConstants;
import com.viglet.turing.sn.TurSNFieldType;
import com.viglet.turing.sn.TurSNNLPProcess;
import com.viglet.turing.sn.TurSNThesaurusProcess;
import com.viglet.turing.sn.spotlight.TurSNSpotlightProcess;
import com.viglet.turing.solr.TurSolr;
import com.viglet.turing.solr.TurSolrInstanceProcess;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.Map.Entry;

@Component
@Slf4j
public class TurSNProcessQueue {
    public static final String CREATED = "Created";
    public static final String DELETED = "Deleted";
    private TurSolr turSolr;
    private TurSNSiteRepository turSNSiteRepository;
    private TurSolrInstanceProcess turSolrInstanceProcess;
    private TurSNMergeProvidersProcess turSNMergeProvidersProcess;
    private TurSNSpotlightProcess turSNSpotlightProcess;
    private TurSNNLPProcess turSNNLPProcess;
    private TurSNThesaurusProcess turSNThesaurusProcess;
    private TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
    @Inject
    public TurSNProcessQueue(TurSolr turSolr, TurSNSiteRepository turSNSiteRepository,
                             TurSolrInstanceProcess turSolrInstanceProcess,
                             TurSNMergeProvidersProcess turSNMergeProvidersProcess,
                             TurSNSpotlightProcess turSNSpotlightProcess,
                             TurSNNLPProcess turSNNLPProcess,
                             TurSNThesaurusProcess turSNThesaurusProcess,
                             TurSNSiteFieldExtRepository turSNSiteFieldExtRepository) {
        this.turSolr = turSolr;
        this.turSNSiteRepository = turSNSiteRepository;
        this.turSolrInstanceProcess = turSolrInstanceProcess;
        this.turSNMergeProvidersProcess = turSNMergeProvidersProcess;
        this.turSNSpotlightProcess = turSNSpotlightProcess;
        this.turSNNLPProcess = turSNNLPProcess;
        this.turSNThesaurusProcess = turSNThesaurusProcess;
        this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
    }

    public TurSNProcessQueue() {
        // Empty
    }

    @JmsListener(destination = TurSNConstants.INDEXING_QUEUE)
    @Transactional
    public void receiveIndexingQueue(TurSNJob turSNJob) {
        log.debug("receiveQueue turSNJob: {}", turSNJob);
        Optional.ofNullable(turSNJob).ifPresentOrElse(job ->
                this.turSNSiteRepository.findById(job.getSiteId())
                        .ifPresent(turSNSite ->
                                job.getTurSNJobItems().forEach(turSNJobItem -> {
                                    if (processJob(turSNSite, turSNJobItem)) {
                                        processQueueInfo(turSNSite, turSNJobItem);
                                    } else {
                                        noProcessedWarning(turSNSite, turSNJobItem);
                                    }
                                })
                        ), () -> log.debug("turSNJob empty or siteId empty"));
    }

    private void noProcessedWarning(TurSNSite turSNSite, TurSNJobItem turSNJobItem) {
        log.warn("Object ID '{}' of '{}' SN Site ({}) was not processed",
                turSNJobItem.getAttributes().get(TurSNConstants.ID_ATTRIBUTE),
                turSNSite.getName(),
                turSNJobItem.getLocale());
    }

    private boolean processJob(TurSNSite turSNSite, TurSNJobItem turSNJobItem) {
        log.debug("processJob TurSNJobItem: {}", turSNJobItem);
        return switch (turSNJobItem.getTurSNJobAction()) {
            case CREATE -> createJob(turSNSite, turSNJobItem);
            case DELETE -> deleteJob(turSNSite, turSNJobItem);
        };
    }

    private boolean deleteJob(TurSNSite turSNSite, TurSNJobItem turSNJobItem) {
        return (turSNSpotlightProcess.isSpotlightJob(turSNJobItem))
                ? turSNSpotlightProcess.deleteUnmanagedSpotlight(turSNJobItem, turSNSite)
                : deIndex(turSNJobItem, turSNSite);
    }

    private boolean createJob(TurSNSite turSNSite, TurSNJobItem turSNJobItem) {
        return turSNSpotlightProcess.isSpotlightJob(turSNJobItem) ?
                turSNSpotlightProcess.createUnmanagedSpotlight(turSNJobItem, turSNSite)
                : index(turSNJobItem, turSNSite);
    }

    private void processQueueInfo(TurSNSite turSNSite, TurSNJobItem turSNJobItem) {
        if (ObjectUtils.allNotNull(turSNSite, turSNJobItem) && turSNJobItem.getAttributes() != null) {
            switch (turSNJobItem.getTurSNJobAction()) {
                case CREATE -> logCrudObject(turSNSite, turSNJobItem, CREATED);
                case DELETE -> logCrudObject(turSNSite, turSNJobItem, DELETED);
            }
        }
    }

    private static void logCrudObject(TurSNSite turSNSite, TurSNJobItem turSNJobItem, String action) {
        if (turSNJobItem.getAttributes().containsKey(TurSNConstants.ID_ATTRIBUTE))
            logCrudObjectMessage(turSNSite, turSNJobItem, action, TurSNConstants.ID_ATTRIBUTE);
        else if (turSNJobItem.getAttributes().containsKey(TurSNConstants.TYPE_ATTRIBUTE))
            logCrudObjectMessage(turSNSite, turSNJobItem, action, TurSNConstants.TYPE_ATTRIBUTE);
    }

    private static void logCrudObjectMessage(TurSNSite turSNSite, TurSNJobItem turSNJobItem,
                                             String action, String attribute) {
        log.info("{} the Object ID '{}' of '{}' SN Site ({}).", action,
                turSNJobItem.getAttributes().get(attribute),
                turSNSite.getName(), turSNJobItem.getLocale());
    }

    public boolean deIndex(TurSNJobItem turSNJobItem, TurSNSite turSNSite) {
        log.debug("DeIndex");
        return turSolrInstanceProcess.initSolrInstance(turSNSite.getName(), turSNJobItem.getLocale())
                .map(turSolrInstance -> {
            if (turSNJobItem.getAttributes().containsKey(TurSNConstants.ID_ATTRIBUTE)) {
                turSolr.deIndexing(turSolrInstance,
                        (String) turSNJobItem.getAttributes().get(TurSNConstants.ID_ATTRIBUTE));
            } else if (turSNJobItem.getAttributes().containsKey(TurSNConstants.TYPE_ATTRIBUTE)) {
                turSolr.deIndexingByType(turSolrInstance,
                        (String) turSNJobItem.getAttributes().get(TurSNConstants.TYPE_ATTRIBUTE));
            }
            return true;
        }).orElse(false);
    }

    private boolean index(TurSNJobItem turSNJobItem, TurSNSite turSNSite) {
        log.debug("Index");
        Map<String, Object> attributes = this.removeDuplicateTerms(
                turSNMergeProvidersProcess.mergeDocuments(turSNSite,
                        getConsolidateResults(turSNJobItem, turSNSite),
                        turSNJobItem.getLocale()));
        createMissingFields(turSNSite, attributes);
        return turSolrInstanceProcess.initSolrInstance(turSNSite.getName(), turSNJobItem.getLocale()).map(turSolrInstance -> {
            turSolr.indexing(turSolrInstance, turSNSite, attributes);
            return true;
        }).orElse(false);

    }

    private void createMissingFields(TurSNSite turSNSite, Map<String, Object> attributes) {
        attributes.forEach((key, value) -> {
            if (!turSNSiteFieldExtRepository.existsByTurSNSiteAndName(turSNSite, key)) {
                TurSEFieldType type = TurSEFieldType.STRING;
                int multivalued = 0;
                if (value instanceof ArrayList<?>) {
                    multivalued = 1;
                } else if (value instanceof Date) {
                    type = TurSEFieldType.DATE;
                } else if (value instanceof Boolean) {
                    type = TurSEFieldType.BOOL;
                } else if (value instanceof Integer) {
                    type = TurSEFieldType.INT;
                }
                turSNSiteFieldExtRepository.save(TurSNSiteFieldExt
                        .builder()
                        .turSNSite(turSNSite)
                        .enabled(1)
                        .name(key)
                        .description(key)
                        .facetName(key + "s")
                        .snType(TurSNFieldType.SE)
                        .multiValued(multivalued)
                        .type(type)
                        .build());
            }
        });
    }

    private Map<String, Object> getConsolidateResults(TurSNJobItem turSNJobItem, TurSNSite turSNSite) {
        Map<String, Object> consolidateResults = new HashMap<>();
        turSNJobItem.getAttributes().forEach((key, value1) -> {
            log.debug("SE Consolidate Value: {}", value1);
            Optional.ofNullable(value1).ifPresent(value -> {
                log.debug("SE Consolidate Class: {}", value.getClass().getName());
                consolidateResults.put(key, value);
            });
        });
        turSNNLPProcess.processNLP(turSNJobItem, turSNSite, consolidateResults);
        turSNThesaurusProcess.processThesaurus(turSNJobItem, turSNSite, consolidateResults);
        return consolidateResults;
    }

    public Map<String, Object> removeDuplicateTerms(Map<String, Object> attributes) {
        Map<String, Object> attributesWithUniqueTerms = new HashMap<>();
        Optional.ofNullable(attributes).ifPresent(attr ->
                attr.entrySet().stream()
                        .filter(attribute -> attribute.getValue() != null)
                        .forEach(attribute -> {
                            log.debug("removeDuplicateTerms: attribute Value: {}", attribute.getValue());
                            log.debug("removeDuplicateTerms: attribute Class: {}",
                            attribute.getValue().getClass().getName());
                    if (attribute.getValue() instanceof ArrayList) {
                        removeDuplicateTermsFromMultiValue(attributesWithUniqueTerms, attribute);
                    } else {
                        attributesWithUniqueTerms.put(attribute.getKey(), attribute.getValue());
                    }
                        }));
        log.debug("removeDuplicateTerms: attributesWithUniqueTerms: {}", attributesWithUniqueTerms);
        return attributesWithUniqueTerms;
    }

    private void removeDuplicateTermsFromMultiValue(Map<String, Object> attributesWithUniqueTerms,
                                                    Entry<String, Object> attribute) {
        List<?> nlpAttributeArray = (ArrayList<?>) attribute.getValue();
        if (!nlpAttributeArray.isEmpty()) {
            List<String> list = TurCommonsUtils.cloneListOfTermsAsString(nlpAttributeArray);
            Set<String> termsUnique = new HashSet<>(list);
            List<Object> arrayValue = new ArrayList<>(termsUnique);
            attributesWithUniqueTerms.put(attribute.getKey(), arrayValue);
            termsUnique.forEach(
                    term -> log.debug("removeDuplicateTerms: attributesWithUniqueTerms Array Value: {}", term));
        }
    }
}
