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
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.viglet.core.manifest.VigletGrounding;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobAttributeSpec;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.turing.commons.indexing.TurIndexingStatus;
import com.viglet.turing.commons.sn.field.TurSNFieldName;
import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.logging.TurLoggingUtils;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.plugins.se.TurSearchEnginePlugin;
import com.viglet.turing.plugins.se.TurSearchEnginePluginFactory;
import com.viglet.turing.sn.TurSNConstants;
import com.viglet.turing.sn.field.TurSNFieldProvisioner;
import com.viglet.turing.sn.ranking.TurSNHybridRankingService;
import com.viglet.turing.sn.spotlight.TurSNSpotlightProcess;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

@Component
@Slf4j
public class TurSNProcessQueue {
    public static final String INDEXED = "Indexed";
    public static final String DEINDEXED = "Deindexed";
    private final TurSearchEnginePluginFactory pluginFactory;
    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;
    private final TurSNMergeProvidersProcess turSNMergeProvidersProcess;
    private final TurSNSpotlightProcess turSNSpotlightProcess;
    private final TurSNFieldProvisioner turSNFieldProvisioner;
    private final TurSEInstanceRepository turSEInstanceRepository;
    private final com.viglet.turing.tenant.TurJmsTenantPropagation turJmsTenantPropagation;
    private final TurSNHybridRankingService turSNHybridRankingService;
    private final TurSNProcessQueue self;

    public TurSNProcessQueue(TurSearchEnginePluginFactory pluginFactory,
            TurSNSiteRepository turSNSiteRepository,
            TurSNSiteLocaleRepository turSNSiteLocaleRepository,
            TurSNMergeProvidersProcess turSNMergeProvidersProcess,
            TurSNSpotlightProcess turSNSpotlightProcess,
            TurSNFieldProvisioner turSNFieldProvisioner,
            TurSEInstanceRepository turSEInstanceRepository,
            com.viglet.turing.tenant.TurJmsTenantPropagation turJmsTenantPropagation,
            TurSNHybridRankingService turSNHybridRankingService,
            @org.springframework.context.annotation.Lazy TurSNProcessQueue self) {
        this.pluginFactory = pluginFactory;
        this.turSNSiteRepository = turSNSiteRepository;
        this.turSNSiteLocaleRepository = turSNSiteLocaleRepository;
        this.turSNMergeProvidersProcess = turSNMergeProvidersProcess;
        this.turSNSpotlightProcess = turSNSpotlightProcess;
        this.turSNFieldProvisioner = turSNFieldProvisioner;
        this.turSEInstanceRepository = turSEInstanceRepository;
        this.turJmsTenantPropagation = turJmsTenantPropagation;
        this.turSNHybridRankingService = turSNHybridRankingService;
        this.self = self;
    }

    /**
     * T272 / §XIV.4.6 — bind the tenant from the message header <em>before</em>
     * the {@code @Transactional} session opens (so the Hibernate tenant filter
     * sees the right tenant), then delegate through the Spring proxy.
     */
    @JmsListener(destination = TurSNConstants.INDEXING_QUEUE, id = TurSNConstants.INDEXING_QUEUE_LISTENER, concurrency = "${turing.jms.concurrency:1-1}")
    public void receiveIndexingQueue(TurSNJobItems turSNJobItems,
            @org.springframework.messaging.handler.annotation.Header(
                    name = com.viglet.turing.tenant.TurJmsTenantPropagation.HEADER,
                    required = false) String tenantId) {
        turJmsTenantPropagation.runForHeader(tenantId, () -> self.processIndexingQueue(turSNJobItems));
    }

    @Transactional
    public void processIndexingQueue(TurSNJobItems turSNJobItems) {
        receiveQueueLog(turSNJobItems);
        // T335 / §XIV.8.2 — track the cores already verified in this batch so a
        // bulk index pays at most one on-demand provisioning check per distinct
        // core, not one per document.
        Set<String> ensuredCores = new HashSet<>();
        Optional.of(turSNJobItems).ifPresentOrElse(jobItems -> jobItems.forEach(
                turSNJobItem -> turSNJobItem.getSiteNames().forEach(
                        siteName -> processJobItemForSite(siteName, turSNJobItem, ensuredCores))),
                () -> log.debug("turSNJob empty or siteId empty"));
    }

    private void processJobItemForSite(String siteName, TurSNJobItem turSNJobItem,
            Set<String> ensuredCores) {
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
        if (turSNJobItem.getTurSNJobAction() == TurSNJobAction.CREATE) {
            ensureCoreExists(turSNSite, turSNJobItem.getLocale(), ensuredCores);
        }
        if (processJob(turSNSite, turSNJobItem)) {
            processQueueInfo(turSNSite, turSNJobItem);
        } else {
            noProcessedWarning(turSNSite, turSNJobItem);
        }
        TurLoggingUtils.setSuccessStatus(turSNJobItem, TurIndexingStatus.FINISHED);
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
        return pluginFactory.getPluginForSite(turSNSite).commit(turSNSite, turSNJobItem.getLocale());
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

    public boolean deIndex(TurSNJobItem turSNJobItem, TurSNSite turSNSite) {
        log.debug("DeIndex");
        TurSearchEnginePlugin plugin = pluginFactory.getPluginForSite(turSNSite);
        if (turSNJobItem.getAttributes().containsKey(TurSNFieldName.ID)) {
            boolean deIndexed = plugin.deIndex(turSNSite, turSNJobItem.getLocale(), turSNJobItem.getId());
            if (deIndexed) {
                // T383 — mirror the removal into the per-site hybrid vector collection.
                turSNHybridRankingService.deIndex(turSNSite, turSNJobItem.getId());
            }
            return deIndexed;
        } else if (turSNJobItem.getAttributes().containsKey(TurSNFieldName.TYPE)) {
            String type = (String) turSNJobItem.getAttributes().get(TurSNFieldName.TYPE);
            boolean deIndexed = plugin.deIndexByType(turSNSite, turSNJobItem.getLocale(), type);
            if (deIndexed) {
                turSNHybridRankingService.deIndexByType(turSNSite, type);
            }
            return deIndexed;
        }
        return false;
    }

    private boolean index(TurSNJobItem turSNJobItem, TurSNSite turSNSite) {
        Map<String, Object> attributes = this.removeDuplicateTerms(turSNMergeProvidersProcess.mergeDocuments(turSNSite,
                getConsolidateResults(turSNJobItem), turSNJobItem.getLocale()));
        createMissingFields(turSNSite, turSNJobItem.getSpecs());
        boolean indexed = pluginFactory.getPluginForSite(turSNSite)
                .indexDocument(turSNSite, turSNJobItem.getLocale(), attributes);
        if (indexed) {
            // T383 — side-write the document embedding for HYBRID_RRF sites (no-op otherwise).
            turSNHybridRankingService.indexDocument(turSNSite, turSNJobItem.getLocale(), attributes);
        }
        return indexed;
    }

    /**
     * T335 / §XIV.8.2 — self-heal a missing search-engine core/index before the
     * first document of a (site, locale) is indexed in this batch.
     *
     * <p>The per-tenant prefixed core ({@code t<shortId>_<core>}) is normally
     * created at SN-site/locale creation ({@code TurSNTemplate.createSolrCore}).
     * On a standalone Solr ({@code turing.solr.cloud=false} — the Cloud
     * topology) a site whose core was never provisioned (created while the
     * tenant's engine was offline, restored without the core, or configset
     * missing at creation time) would otherwise 404 on its first index. We
     * verify the core exists and create it on demand from the locale's configset
     * if it does not — reusing the same {@code indexExists}/{@code createIndex}
     * plugin pair the import path uses.
     *
     * <p>Deduped via {@code ensuredCores} so a bulk index pays at most one
     * {@code indexExists} round-trip per distinct core. Failures are logged and
     * swallowed: indexing then proceeds and fails with its own diagnostics
     * rather than masking the original error.
     */
    private void ensureCoreExists(TurSNSite turSNSite, Locale locale, Set<String> ensuredCores) {
        TurSEInstance seInstance = turSNSite.getTurSEInstance();
        if (seInstance == null) {
            return;
        }
        TurSNSiteLocale turSNSiteLocale = turSNSiteLocaleRepository
                .findByTurSNSiteAndLanguage(turSNSite, locale);
        if (turSNSiteLocale == null || !StringUtils.hasText(turSNSiteLocale.getCore())) {
            return;
        }
        final String core = turSNSiteLocale.getCore();
        if (!ensuredCores.add(core)) {
            return;
        }
        turSEInstanceRepository.findById(seInstance.getId()).ifPresent(instance -> {
            try {
                TurSearchEnginePlugin plugin = pluginFactory.getPluginForInstance(instance);
                if (!plugin.indexExists(instance, core)) {
                    log.info("[T335] Core '{}' missing on SE Instance '{}' — provisioning on demand",
                            core, instance.getTitle());
                    plugin.createIndex(instance, turSNSiteLocale, core, Map.of());
                }
            } catch (Exception e) {
                log.error("[T335] Failed to provision core '{}' on demand on SE Instance '{}': {}",
                        core, instance.getTitle(), e.getMessage(), e);
            }
        });
    }

    private void createMissingFields(TurSNSite turSNSite,
            List<TurSNJobAttributeSpec> turSNAttributeSpecs) {
        if (turSNAttributeSpecs == null) return;
        // T382 — field creation now lives in the shared TurSNFieldProvisioner so
        // the indexing-time auto-create path and declarative manifest provisioning
        // converge on one idempotent implementation.
        turSNAttributeSpecs.forEach(spec -> turSNFieldProvisioner.ensureField(turSNSite, spec));
    }

    private Map<String, Object> getConsolidateResults(TurSNJobItem turSNJobItem) {
        Map<String, Object> consolidateResults = new HashMap<>();
        Optional.ofNullable(turSNJobItem.getAttributes())
                .ifPresent(attributes -> attributes.forEach((key, value1) -> {
                    log.debug("SE Consolidate Value: {}", value1);
                    if (isGroundedValue(value1)) {
                        log.debug("SE Consolidate Class: {}", value1.getClass().getName());
                        consolidateResults.put(key, value1);
                    } else {
                        log.debug("[T381] conservative grounding: dropping absent/empty attribute '{}'",
                                key);
                    }
                }));
        return consolidateResults;
    }

    /**
     * T381 / §XX.1 — conservative-grounding contract. An attribute that is
     * <em>absent</em> from {@link TurSNJobItem#getAttributes()} must never be
     * materialized as an empty indexed value, or facet counts and
     * {@code exists}-negation filters in Solr/ES break (absent ≠ empty). A
     * {@code null}, blank string, or empty collection value carries no
     * information and is treated as <em>absent</em> here — it is not indexed as
     * the empty value {@code ""}. This is the indexing-side half of Dumont's
     * grounding rule (D03).
     *
     * @return {@code true} when {@code value} should be indexed; {@code false}
     *         when it is grounding-absent and must be dropped at this boundary.
     */
    static boolean isGroundedValue(Object value) {
        // The contract is product-neutral (Turing, Shio, Dumont all index
        // structured sources): it lives in viglet-core as VigletGrounding (T393).
        return VigletGrounding.isGroundedValue(value);
    }

    public Map<String, Object> removeDuplicateTerms(Map<String, Object> attributes) {
        Map<String, Object> attributesWithUniqueTerms = new HashMap<>();
        Optional.ofNullable(attributes).ifPresent(attr -> attr.entrySet().stream()
                .filter(attribute -> isGroundedValue(attribute.getValue())).forEach(attribute -> {
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
