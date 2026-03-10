/*
 * Copyright (C) 2016-2024 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.viglet.turing.api.sn.queue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.LocaleUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobAttributeSpec;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.turing.commons.indexing.TurIndexingStatus;
import com.viglet.turing.commons.sn.field.TurSNFieldName;
import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.logging.TurLoggingUtils;
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
import com.viglet.turing.sn.spotlight.TurSNSpotlightProcess;
import com.viglet.turing.elasticsearch.TurElasticsearch;
import com.viglet.turing.elasticsearch.TurElasticsearchInstanceProcess;
import com.viglet.turing.lucene.TurLucene;
import com.viglet.turing.lucene.TurLuceneInstanceProcess;
import com.viglet.turing.solr.TurSolr;
import com.viglet.turing.solr.TurSolrFieldAction;
import com.viglet.turing.solr.TurSolrInstanceProcess;
import com.viglet.turing.solr.TurSolrUtils;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

@Component
@Slf4j
public class TurSNProcessQueue {
    public static final String DEFAULT = "default";
    public static final String INDEXED = "Indexed";
    public static final String DEINDEXED = "Deindexed";
    private final TurSolr turSolr;
    private final TurLucene turLucene;
    private final TurElasticsearch turElasticsearch;
    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;
    private final TurSolrInstanceProcess turSolrInstanceProcess;
    private final TurLuceneInstanceProcess turLuceneInstanceProcess;
    private final TurElasticsearchInstanceProcess turElasticsearchInstanceProcess;
    private final TurSNMergeProvidersProcess turSNMergeProvidersProcess;
    private final TurSNSpotlightProcess turSNSpotlightProcess;
    private final TurSNSiteFieldRepository turSNSiteFieldRepository;
    private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
    private final TurSNSiteFieldExtFacetRepository turSNSiteFieldExtFacetRepository;
    private final TurSEInstanceRepository turSEInstanceRepository;

    public TurSNProcessQueue(TurSolr turSolr, TurLucene turLucene,
            TurElasticsearch turElasticsearch,
            TurSNSiteRepository turSNSiteRepository,
            TurSNSiteLocaleRepository turSNSiteLocaleRepository,
            TurSolrInstanceProcess turSolrInstanceProcess,
            TurLuceneInstanceProcess turLuceneInstanceProcess,
            TurElasticsearchInstanceProcess turElasticsearchInstanceProcess,
            TurSNMergeProvidersProcess turSNMergeProvidersProcess,
            TurSNSpotlightProcess turSNSpotlightProcess,
            TurSNSiteFieldRepository turSNSiteFieldRepository,
            TurSNSiteFieldExtRepository turSNSiteFieldExtRepository,
            TurSNSiteFieldExtFacetRepository turSNSiteFieldExtFacetRepository,
            TurSEInstanceRepository turSEInstanceRepository) {
        this.turSolr = turSolr;
        this.turLucene = turLucene;
        this.turElasticsearch = turElasticsearch;
        this.turSNSiteRepository = turSNSiteRepository;
        this.turSNSiteLocaleRepository = turSNSiteLocaleRepository;
        this.turSolrInstanceProcess = turSolrInstanceProcess;
        this.turLuceneInstanceProcess = turLuceneInstanceProcess;
        this.turElasticsearchInstanceProcess = turElasticsearchInstanceProcess;
        this.turSNMergeProvidersProcess = turSNMergeProvidersProcess;
        this.turSNSpotlightProcess = turSNSpotlightProcess;
        this.turSNSiteFieldRepository = turSNSiteFieldRepository;
        this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
        this.turSNSiteFieldExtFacetRepository = turSNSiteFieldExtFacetRepository;
        this.turSEInstanceRepository = turSEInstanceRepository;
    }

    @JmsListener(destination = TurSNConstants.INDEXING_QUEUE, id = TurSNConstants.INDEXING_QUEUE_LISTENER, concurrency = "${turing.jms.concurrency:1-1}")
    @Transactional
    public void receiveIndexingQueue(TurSNJobItems turSNJobItems) {
        receiveQueueLog(turSNJobItems);
        Optional.of(turSNJobItems).ifPresentOrElse(jobItems -> jobItems.forEach(
                turSNJobItem -> turSNJobItem.getSiteNames().forEach(siteName -> {
                    log.debug("receiveIndexingQueue: looking up site='{}' action={} locale={}",
                            siteName, turSNJobItem.getTurSNJobAction(), turSNJobItem.getLocale());
                    var siteOpt = turSNSiteRepository.findByNameIgnoreCase(siteName);
                    if (siteOpt.isEmpty()) {
                        log.warn("receiveIndexingQueue: site '{}' not found in DB — skipping", siteName);
                        return;
                    }
                    var turSNSite = siteOpt.get();
                    log.debug("receiveIndexingQueue: found site='{}' seInstance={}",
                            turSNSite.getName(),
                            turSNSite.getTurSEInstance() != null ? turSNSite.getTurSEInstance().getId() : "null");
                    if (processJob(turSNSite, turSNJobItem)) {
                        processQueueInfo(turSNSite, turSNJobItem);
                    } else {
                        noProcessedWarning(turSNSite, turSNJobItem);
                    }
                    TurLoggingUtils.setSuccessStatus(turSNJobItem, TurIndexingStatus.FINISHED);
                })),
                () -> log.debug("turSNJob empty or siteId empty"));
    }

    private static void receiveQueueLog(TurSNJobItems turSNJobItems) {
        turSNJobItems.forEach(turSNJobItem -> TurLoggingUtils.setSuccessStatus(turSNJobItem,
                TurIndexingStatus.RECEIVED_FROM_QUEUE));

        if (log.isDebugEnabled()) {
            String json = JsonMapper.builder()
                    .build()
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(turSNJobItems);
            log.debug("receiveQueue turSNJobItems: {}", json);
        }
    }

    private void noProcessedWarning(TurSNSite turSNSite, TurSNJobItem turSNJobItem) {
        log.warn("Object ID '{}' of '{}' SN Site ({}) was not processed",
                turSNJobItem.getAttributes().get(TurSNFieldName.ID), turSNSite.getName(),
                turSNJobItem.getLocale());
        TurLoggingUtils.setSuccessStatus(turSNJobItem, TurIndexingStatus.NOT_PROCESSED);
    }

    private boolean processJob(TurSNSite turSNSite, TurSNJobItem turSNJobItem) {
        log.debug("processJob TurSNJobItem: {}", turSNJobItem);
        return switch (turSNJobItem.getTurSNJobAction()) {
            case CREATE -> createJob(turSNSite, turSNJobItem);
            case DELETE -> deleteJob(turSNSite, turSNJobItem);
            case COMMIT -> commitJob(turSNSite, turSNJobItem);
        };
    }

    private boolean commitJob(TurSNSite turSNSite, TurSNJobItem turSNJobItem) {
        if (isLuceneSite(turSNSite) || isElasticsearchSite(turSNSite)) {
            // Lucene and Elasticsearch commits are synchronous — nothing extra needed
            return true;
        }
        return turSolrInstanceProcess
                .initSolrInstance(turSNSite.getName(), turSNJobItem.getLocale())
                .map(turSolr::commit).orElse(false);
    }

    private boolean deleteJob(TurSNSite turSNSite, TurSNJobItem turSNJobItem) {
        return (turSNSpotlightProcess.isSpotlightJob(turSNJobItem))
                ? turSNSpotlightProcess.deleteUnmanagedSpotlight(turSNJobItem, turSNSite)
                : deIndex(turSNJobItem, turSNSite);
    }

    private boolean createJob(TurSNSite turSNSite, TurSNJobItem turSNJobItem) {
        return turSNSpotlightProcess.isSpotlightJob(turSNJobItem)
                ? turSNSpotlightProcess.createUnmanagedSpotlight(turSNJobItem, turSNSite)
                : index(turSNJobItem, turSNSite);
    }

    private void processQueueInfo(TurSNSite turSNSite, TurSNJobItem turSNJobItem) {
        if (ObjectUtils.allNotNull(turSNSite, turSNJobItem)
                && turSNJobItem.getAttributes() != null) {
            if (Objects.requireNonNull(turSNJobItem.getTurSNJobAction()) == TurSNJobAction.CREATE) {
                logCrudObject(turSNSite, turSNJobItem, INDEXED);
                TurLoggingUtils.setSuccessStatus(turSNJobItem, TurIndexingStatus.INDEXED);
            } else if (turSNJobItem.getTurSNJobAction() == TurSNJobAction.DELETE) {
                logCrudObject(turSNSite, turSNJobItem, DEINDEXED);
                TurLoggingUtils.setSuccessStatus(turSNJobItem, TurIndexingStatus.DEINDEXED);
            }
        }
    }

    private static void logCrudObject(TurSNSite turSNSite, TurSNJobItem turSNJobItem,
            String action) {
        if (turSNJobItem.getAttributes().containsKey(TurSNFieldName.ID))
            logCrudObjectMessage(turSNSite, turSNJobItem, action, TurSNFieldName.ID);
        else if (turSNJobItem.getAttributes().containsKey(TurSNFieldName.TYPE))
            logCrudObjectMessage(turSNSite, turSNJobItem, action, TurSNFieldName.TYPE);
    }

    private static void logCrudObjectMessage(TurSNSite turSNSite, TurSNJobItem turSNJobItem,
            String action, String attribute) {
        log.info("{} the Object ID '{}' of '{}' SN Site ({}).", action,
                turSNJobItem.getAttributes().get(attribute), turSNSite.getName(),
                turSNJobItem.getLocale());
    }

    private boolean isLuceneSite(TurSNSite turSNSite) {
        if (turSNSite.getTurSEInstance() == null) return false;
        return turSEInstanceRepository.findById(turSNSite.getTurSEInstance().getId())
                .map(TurSolrUtils::isLuceneEngine)
                .orElse(false);
    }

    private boolean isElasticsearchSite(TurSNSite turSNSite) {
        if (turSNSite.getTurSEInstance() == null) {
            log.debug("isElasticsearchSite: turSEInstance is null for site={}", turSNSite.getName());
            return false;
        }
        boolean result = turSEInstanceRepository.findById(turSNSite.getTurSEInstance().getId())
                .map(TurSolrUtils::isElasticsearchEngine)
                .orElse(false);
        log.debug("isElasticsearchSite: site={} seInstanceId={} result={}",
                turSNSite.getName(), turSNSite.getTurSEInstance().getId(), result);
        return result;
    }

    public boolean deIndex(TurSNJobItem turSNJobItem, TurSNSite turSNSite) {
        log.debug("DeIndex");
        if (isLuceneSite(turSNSite)) {
            return turLuceneInstanceProcess.initLuceneInstance(turSNSite.getName(), turSNJobItem.getLocale())
                    .map(luceneInstance -> {
                        if (turSNJobItem.getAttributes().containsKey(TurSNFieldName.ID)) {
                            turLucene.deIndexing(luceneInstance, turSNJobItem.getId());
                        } else if (turSNJobItem.getAttributes().containsKey(TurSNFieldName.TYPE)) {
                            turLucene.deIndexingByType(luceneInstance,
                                    (String) turSNJobItem.getAttributes().get(TurSNFieldName.TYPE));
                        }
                        return true;
                    }).orElse(false);
        }
        if (isElasticsearchSite(turSNSite)) {
            return turElasticsearchInstanceProcess
                    .initElasticsearchInstance(turSNSite.getName(), turSNJobItem.getLocale())
                    .map(esInstance -> {
                        if (turSNJobItem.getAttributes().containsKey(TurSNFieldName.ID)) {
                            turElasticsearch.deIndexing(esInstance, turSNJobItem.getId());
                        } else if (turSNJobItem.getAttributes().containsKey(TurSNFieldName.TYPE)) {
                            turElasticsearch.deIndexingByType(esInstance,
                                    (String) turSNJobItem.getAttributes().get(TurSNFieldName.TYPE));
                        }
                        return true;
                    }).orElse(false);
        }
        return turSolrInstanceProcess
                .initSolrInstance(turSNSite.getName(), turSNJobItem.getLocale())
                .map(turSolrInstance -> {
                    if (turSNJobItem.getAttributes().containsKey(TurSNFieldName.ID)) {
                        turSolr.deIndexing(turSolrInstance, turSNJobItem.getId());
                    } else if (turSNJobItem.getAttributes().containsKey(TurSNFieldName.TYPE)) {
                        turSolr.deIndexingByType(turSolrInstance,
                                (String) turSNJobItem.getAttributes().get(TurSNFieldName.TYPE));
                    }
                    return true;
                }).orElse(false);
    }

    private boolean index(TurSNJobItem turSNJobItem, TurSNSite turSNSite) {
        Map<String, Object> attributes = this.removeDuplicateTerms(turSNMergeProvidersProcess.mergeDocuments(turSNSite,
                getConsolidateResults(turSNJobItem), turSNJobItem.getLocale()));
        createMissingFields(turSNSite, turSNJobItem.getSpecs());
        if (isLuceneSite(turSNSite)) {
            return turLuceneInstanceProcess.initLuceneInstance(turSNSite.getName(), turSNJobItem.getLocale())
                    .map(luceneInstance -> {
                        try {
                            turLucene.indexing(luceneInstance, turSNSite, attributes);
                            return true;
                        } catch (IllegalArgumentException e) {
                            if (e.getMessage() != null && e.getMessage().contains("doc values type")) {
                                log.warn("[Lucene] Schema conflict detected ({}). Recreating index and retrying.",
                                        e.getMessage());
                                turLuceneInstanceProcess.resetInstance(luceneInstance.getIndexPath());
                                return turLuceneInstanceProcess
                                        .initLuceneInstance(turSNSite.getName(), turSNJobItem.getLocale())
                                        .map(fresh -> {
                                            turLucene.indexing(fresh, turSNSite, attributes);
                                            return true;
                                        }).orElse(false);
                            }
                            log.error("[Lucene] Indexing failed: {}", e.getMessage(), e);
                            return false;
                        }
                    }).orElse(false);
        }
        if (isElasticsearchSite(turSNSite)) {
            log.debug("index: routing to Elasticsearch site={} locale={}", turSNSite.getName(), turSNJobItem.getLocale());
            return turElasticsearchInstanceProcess
                    .initElasticsearchInstance(turSNSite.getName(), turSNJobItem.getLocale())
                    .map(esInstance -> {
                        log.debug("index: got ES instance index={}", esInstance.getIndex());
                        turElasticsearch.indexing(esInstance, turSNSite, attributes);
                        return true;
                    })
                    .orElseGet(() -> {
                        log.warn("index: initElasticsearchInstance returned empty for site={} locale={}",
                                turSNSite.getName(), turSNJobItem.getLocale());
                        return false;
                    });
        }
        return turSolrInstanceProcess
                .initSolrInstance(turSNSite.getName(), turSNJobItem.getLocale())
                .map(turSolrInstance -> {
                    turSEInstanceRepository.findById(turSNSite.getTurSEInstance().getId())
                            .ifPresent(seInstance -> turSolr.indexing(turSolrInstance, turSNSite,
                                    attributes));
                    return true;
                }).orElse(false);
    }

    private void createMissingFields(TurSNSite turSNSite,
            List<TurSNJobAttributeSpec> turSNAttributeSpecs) {
        turSNAttributeSpecs.forEach(spec -> {
            if (!turSNSiteFieldExtRepository.existsByTurSNSiteAndName(turSNSite, spec.getName())) {
                final TurSNSiteField turSNSiteField = saveSiteField(turSNSite, spec);
                saveFaceLocales(spec, saveSiteFieldExt(turSNSite, spec, turSNSiteField));
                turSNSiteLocaleRepository.findByTurSNSite(turSNSite).stream()
                        .filter(turSNSiteLocale -> !existsFieldInSearchEngine(turSNSite,
                                turSNSiteLocale.getCore(), spec.getName()))
                        .forEach(turSNSiteLocale -> createFieldInSearchEngine(turSNSite,
                                turSNSiteLocale.getCore(), turSNSiteField));
            }
        });
    }

    private void saveFaceLocales(TurSNJobAttributeSpec spec, TurSNSiteFieldExt turSNSiteFieldExt) {
        if (spec.getFacetName() == null) return;
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
    private TurSNSiteFieldExt saveSiteFieldExt(TurSNSite turSNSite, TurSNJobAttributeSpec spec,
            TurSNSiteField turSNSiteField) {
        Map<String, String> facetName = spec.getFacetName();
        TurSNSiteFieldExt turSNSiteFieldExt = TurSNSiteFieldExt.builder().enabled(1)
                .name(turSNSiteField.getName()).description(turSNSiteField.getDescription())
                .facet(spec.isFacet() ? 1 : 0)
                .facetName(facetName != null ? facetName.get(DEFAULT) : null).hl(0)
                .multiValued(turSNSiteField.getMultiValued()).mlt(0)
                .externalId(turSNSiteField.getId()).snType(TurSNFieldType.SE)
                .type(turSNSiteField.getType()).turSNSite(turSNSite).build();
        turSNSiteFieldExtRepository.save(turSNSiteFieldExt);
        return turSNSiteFieldExt;
    }

    @NotNull
    private TurSNSiteField saveSiteField(TurSNSite turSNSite, TurSNJobAttributeSpec spec) {
        TurSNSiteField turSNSiteField = TurSNSiteField.builder().name(spec.getName())
                .description(spec.getDescription()).type(spec.getType())
                .multiValued(spec.isMultiValued() ? 1 : 0).turSNSite(turSNSite).build();
        turSNSiteFieldRepository.save(turSNSiteField);
        return turSNSiteField;
    }

    private void createFieldInSearchEngine(TurSNSite turSNSite, String coreName,
            TurSNSiteField turSNSiteField) {
        turSEInstanceRepository.findById(turSNSite.getTurSEInstance().getId())
                .ifPresent(turSEInstance -> TurSolrUtils.addOrUpdateField(TurSolrFieldAction.ADD,
                        turSEInstance, coreName, turSNSiteField.getName(), turSNSiteField.getType(),
                        true, turSNSiteField.getMultiValued() == 1));

    }

    private boolean existsFieldInSearchEngine(TurSNSite turSNSite, String coreName, String name) {
        return TurSolrUtils.existsField(turSNSite.getTurSEInstance(), coreName, name);
    }

    private Map<String, Object> getConsolidateResults(TurSNJobItem turSNJobItem) {
        Map<String, Object> consolidateResults = new HashMap<>();
        Optional.ofNullable(turSNJobItem.getAttributes())
                .ifPresent(attributes -> attributes.forEach((key, value1) -> {
                    log.debug("SE Consolidate Value: {}", value1);
                    Optional.ofNullable(value1).ifPresent(value -> {
                        log.debug("SE Consolidate Class: {}", value.getClass().getName());
                        consolidateResults.put(key, value);
                    });
                }));
        return consolidateResults;
    }

    public Map<String, Object> removeDuplicateTerms(Map<String, Object> attributes) {
        Map<String, Object> attributesWithUniqueTerms = new HashMap<>();
        Optional.ofNullable(attributes).ifPresent(attr -> attr.entrySet().stream()
                .filter(attribute -> attribute.getValue() != null).forEach(attribute -> {
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
        List<?> attributeArray = (ArrayList<?>) attribute.getValue();
        if (!attributeArray.isEmpty()) {
            List<String> list = TurCommonsUtils.cloneListOfTermsAsString(attributeArray);
            Set<String> termsUnique = new HashSet<>(list);
            List<Object> arrayValue = new ArrayList<>(termsUnique);
            attributesWithUniqueTerms.put(attribute.getKey(), arrayValue);
            termsUnique.forEach(term -> log.debug(
                    "removeDuplicateTerms: attributesWithUniqueTerms Array Value: {}", term));
        }
    }
}
