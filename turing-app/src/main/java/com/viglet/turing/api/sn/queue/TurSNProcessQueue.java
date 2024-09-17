/*
 * Copyright (C) 2016-2024 the original author or authors.
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobAttributeSpec;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.lucene.TurLuceneWriter;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteField;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExtFacet;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtFacetRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.sn.TurSNConstants;
import com.viglet.turing.sn.TurSNFieldType;
import com.viglet.turing.sn.TurSNNLPProcess;
import com.viglet.turing.sn.TurSNThesaurusProcess;
import com.viglet.turing.sn.spotlight.TurSNSpotlightProcess;
import com.viglet.turing.solr.TurSolr;
import com.viglet.turing.solr.TurSolrFieldAction;
import com.viglet.turing.solr.TurSolrInstanceProcess;
import com.viglet.turing.solr.TurSolrUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.LocaleUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
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
    public static final String DEFAULT = "default";
    public static final String SOLR = "SOLR";
    public static final String LUCENE = "LUCENE";
    private final TurSolr turSolr;
    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;
    private final TurSolrInstanceProcess turSolrInstanceProcess;
    private final TurSNMergeProvidersProcess turSNMergeProvidersProcess;
    private final TurSNSpotlightProcess turSNSpotlightProcess;
    private final TurSNNLPProcess turSNNLPProcess;
    private final TurSNThesaurusProcess turSNThesaurusProcess;
    private final TurSNSiteFieldRepository turSNSiteFieldRepository;
    private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
    private final TurSNSiteFieldExtFacetRepository turSNSiteFieldExtFacetRepository;
    private final TurSEInstanceRepository turSEInstanceRepository;
    private final TurLuceneWriter turLuceneWriter;
    @Autowired
    public TurSNProcessQueue(TurSolr turSolr, TurSNSiteRepository turSNSiteRepository,
                             TurSNSiteLocaleRepository turSNSiteLocaleRepository,
                             TurSolrInstanceProcess turSolrInstanceProcess,
                             TurSNMergeProvidersProcess turSNMergeProvidersProcess,
                             TurSNSpotlightProcess turSNSpotlightProcess,
                             TurSNNLPProcess turSNNLPProcess, TurSNThesaurusProcess turSNThesaurusProcess,
                             TurSNSiteFieldRepository turSNSiteFieldRepository,
                             TurSNSiteFieldExtRepository turSNSiteFieldExtRepository,
                             TurSNSiteFieldExtFacetRepository turSNSiteFieldExtFacetRepository,
                             TurSEInstanceRepository turSEInstanceRepository,
                             TurLuceneWriter turLuceneWriter) {
        this.turSolr = turSolr;
        this.turSNSiteRepository = turSNSiteRepository;
        this.turSNSiteLocaleRepository = turSNSiteLocaleRepository;
        this.turSolrInstanceProcess = turSolrInstanceProcess;
        this.turSNMergeProvidersProcess = turSNMergeProvidersProcess;
        this.turSNSpotlightProcess = turSNSpotlightProcess;
        this.turSNNLPProcess = turSNNLPProcess;
        this.turSNThesaurusProcess = turSNThesaurusProcess;
        this.turSNSiteFieldRepository = turSNSiteFieldRepository;
        this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
        this.turSNSiteFieldExtFacetRepository = turSNSiteFieldExtFacetRepository;
        this.turSEInstanceRepository = turSEInstanceRepository;
        this.turLuceneWriter = turLuceneWriter;
    }

    @JmsListener(destination = TurSNConstants.INDEXING_QUEUE)
    @Transactional
    public void receiveIndexingQueue(TurSNJobItems turSNJobItems) {
        receiveQueueDebugLog(turSNJobItems);
        Optional.ofNullable(turSNJobItems)
                .ifPresentOrElse(jobItems ->
                        jobItems.forEach(turSNJobItem ->
                                turSNJobItem.getSiteNames().forEach(siteName ->
                                        turSNSiteRepository.findByName(siteName)
                                                .ifPresent(turSNSite -> {
                                                    if (processJob(turSNSite, turSNJobItem)) {
                                                        processQueueInfo(turSNSite, turSNJobItem);
                                                    } else {
                                                        noProcessedWarning(turSNSite, turSNJobItem);
                                                    }
                                                }))), () -> log.debug("turSNJob empty or siteId empty"));
    }

    private static void receiveQueueDebugLog(TurSNJobItems turSNJobItems) {
        if (log.isDebugEnabled()) {
            try {
                log.debug("receiveQueue turSNJobItems: {}", new ObjectMapper().writer()
                        .withDefaultPrettyPrinter()
                        .writeValueAsString(turSNJobItems));
            } catch (JsonProcessingException e) {
                log.error(e.getMessage(), e);
            }
        }
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
            if (Objects.requireNonNull(turSNJobItem.getTurSNJobAction()) == TurSNJobAction.CREATE) {
                logCrudObject(turSNSite, turSNJobItem, CREATED);
            } else if (turSNJobItem.getTurSNJobAction() == TurSNJobAction.DELETE) {
                logCrudObject(turSNSite, turSNJobItem, DELETED);
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
        createMissingFields(turSNSite, turSNJobItem.getSpecs());
        return turSolrInstanceProcess.initSolrInstance(turSNSite.getName(),
                turSNJobItem.getLocale()).map(turSolrInstance -> {
            turSEInstanceRepository
                    .findById(turSNSite.getTurSEInstance().getId()).ifPresent(seInstance -> {
                        if (seInstance.getTurSEVendor().getId().equals(SOLR)) {
                            turSolr.indexing(turSolrInstance, turSNSite, attributes);
                        } else if (seInstance.getTurSEVendor().getId().equals(LUCENE)) {
                            turLuceneWriter.indexing(turSNSite, attributes);
                        }
                    });
            return true;
        }).orElse(false);

    }

    private void createMissingFields(TurSNSite turSNSite, List<TurSNJobAttributeSpec> turSNAttributeSpecs) {
        turSNAttributeSpecs.forEach(spec -> {
            if (!turSNSiteFieldExtRepository.existsByTurSNSiteAndName(turSNSite, spec.getName())) {
                final TurSNSiteField turSNSiteField = saveSiteField(turSNSite, spec);
                saveFaceLocales(spec, saveSiteFieldExt(turSNSite, spec, turSNSiteField));
                turSNSiteLocaleRepository.findByTurSNSite(turSNSite).stream()
                        .filter(turSNSiteLocale ->
                                !existsFieldInSearchEngine(turSNSite, turSNSiteLocale.getCore(), spec.getName()))
                        .forEach(turSNSiteLocale ->
                                createFieldInSearchEngine(turSNSite, turSNSiteLocale.getCore(), turSNSiteField));
            }
        });
    }

    private void saveFaceLocales(TurSNJobAttributeSpec spec, TurSNSiteFieldExt turSNSiteFieldExt) {
        Set<TurSNSiteFieldExtFacet> facetLocales = new HashSet<>();
        spec.getFacetName().forEach((key, value) -> {
            if (!key.equals(DEFAULT)) {
                TurSNSiteFieldExtFacet turSNSiteFieldExtFacet = new TurSNSiteFieldExtFacet();
                turSNSiteFieldExtFacet.setLocale(LocaleUtils.toLocale(key));
                turSNSiteFieldExtFacet.setLabel(value);
                turSNSiteFieldExtFacet.setTurSNSiteFieldExt(turSNSiteFieldExt);
                facetLocales.add(turSNSiteFieldExtFacet);
            }
        });
        turSNSiteFieldExtFacetRepository.saveAll(facetLocales);
    }

    @NotNull
    private TurSNSiteFieldExt saveSiteFieldExt(TurSNSite turSNSite, TurSNJobAttributeSpec spec, TurSNSiteField turSNSiteField) {
        TurSNSiteFieldExt turSNSiteFieldExt = TurSNSiteFieldExt.builder()
                .enabled(1)
                .name(turSNSiteField.getName())
                .description(turSNSiteField.getDescription())
                .facet(spec.isFacet() ? 1 : 0)
                .facetName(spec.getFacetName().get(DEFAULT))
                .hl(0)
                .multiValued(turSNSiteField.getMultiValued())
                .mlt(0)
                .externalId(turSNSiteField.getId())
                .snType(TurSNFieldType.SE)
                .type(turSNSiteField.getType())
                .turSNSite(turSNSite).build();
        turSNSiteFieldExtRepository.save(turSNSiteFieldExt);
        return turSNSiteFieldExt;
    }

    @NotNull
    private TurSNSiteField saveSiteField(TurSNSite turSNSite, TurSNJobAttributeSpec spec) {
        TurSNSiteField turSNSiteField = TurSNSiteField.builder()
                .name(spec.getName())
                .description(spec.getDescription())
                .type(spec.getType())
                .multiValued(spec.isMultiValued() ? 1 : 0)
                .turSNSite(turSNSite).build();
        turSNSiteFieldRepository.save(turSNSiteField);
        return turSNSiteField;
    }

    private void createFieldInSearchEngine(TurSNSite turSNSite, String coreName, TurSNSiteField turSNSiteField) {
        turSEInstanceRepository
                .findById(turSNSite.getTurSEInstance().getId()).ifPresent(turSEInstance ->
                        TurSolrUtils.addOrUpdateField(TurSolrFieldAction.ADD,
                                turSEInstance,
                                coreName,
                                turSNSiteField.getName(),
                                turSNSiteField.getType(),
                                true,
                                turSNSiteField.getMultiValued() == 1));

    }

    private boolean existsFieldInSearchEngine(TurSNSite turSNSite, String coreName, String name) {
        return TurSolrUtils.existsField(turSNSite.getTurSEInstance(), coreName, name);
    }

    private Map<String, Object> getConsolidateResults(TurSNJobItem turSNJobItem, TurSNSite turSNSite) {
        Map<String, Object> consolidateResults = new HashMap<>();
        Optional.ofNullable(turSNJobItem.getAttributes()).ifPresent(attributes ->
                attributes.forEach((key, value1) -> {
                    log.debug("SE Consolidate Value: {}", value1);
                    Optional.ofNullable(value1).ifPresent(value -> {
                        log.debug("SE Consolidate Class: {}", value.getClass().getName());
                        consolidateResults.put(key, value);
                    });
                }));

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
