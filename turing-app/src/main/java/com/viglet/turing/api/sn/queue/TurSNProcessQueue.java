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
import java.util.LinkedHashMap;
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
import com.viglet.turing.sn.contentfit.TurSNContentFitIndexer;
import com.viglet.turing.sn.media.TurSNGeminiMediaIndexer;
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
    private final TurSNContentFitIndexer turSNContentFitIndexer;
    private final TurSNGeminiMediaIndexer turSNGeminiMediaIndexer;
    private final com.viglet.turing.sn.kb.TurSNMicrothesaurusIndexer turSNMicrothesaurusIndexer;
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
            TurSNContentFitIndexer turSNContentFitIndexer,
            TurSNGeminiMediaIndexer turSNGeminiMediaIndexer,
            com.viglet.turing.sn.kb.TurSNMicrothesaurusIndexer turSNMicrothesaurusIndexer,
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
        this.turSNContentFitIndexer = turSNContentFitIndexer;
        this.turSNGeminiMediaIndexer = turSNGeminiMediaIndexer;
        this.turSNMicrothesaurusIndexer = turSNMicrothesaurusIndexer;
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
        if (turSNJobItems == null) {
            log.debug("turSNJob empty or siteId empty");
            return;
        }
        receiveQueueLog(turSNJobItems);
        // T335 / §XIV.8.2 — track the cores already verified in this batch so a
        // bulk index pays at most one on-demand provisioning check per distinct
        // core, not one per document.
        Set<String> ensuredCores = new HashSet<>();
        // T803 / §LV.1 — group consecutive CREATE (non-spotlight) items by
        // (site, locale) and route each group through the existing bulk
        // indexDocuments(...) API. DELETE / COMMIT / spotlight items flush the
        // open batches first — so ordering relative to the CREATEs is preserved —
        // and are then processed per item exactly as before.
        Map<TurSNBatchKey, List<TurSNJobItem>> batches = new LinkedHashMap<>();
        for (TurSNJobItem turSNJobItem : turSNJobItems) {
            if (isBatchableCreate(turSNJobItem)) {
                turSNJobItem.getSiteNames()
                        .forEach(siteName -> batches
                                .computeIfAbsent(new TurSNBatchKey(siteName, turSNJobItem.getLocale()),
                                        key -> new ArrayList<>())
                                .add(turSNJobItem));
            } else {
                flushBatches(batches, ensuredCores);
                turSNJobItem.getSiteNames().forEach(
                        siteName -> processJobItemForSite(siteName, turSNJobItem, ensuredCores));
            }
        }
        flushBatches(batches, ensuredCores);
    }

    /**
     * A CREATE job item that is not an (unmanaged) spotlight is eligible for the
     * T803 bulk path. Spotlight CREATE / DELETE / COMMIT keep the per-item path.
     */
    private boolean isBatchableCreate(TurSNJobItem turSNJobItem) {
        return turSNJobItem.getTurSNJobAction() == TurSNJobAction.CREATE
                && !turSNSpotlightProcess.isSpotlightJob(turSNJobItem);
    }

    /** Flushes every accumulated (site, locale) CREATE group, then clears the map. */
    private void flushBatches(Map<TurSNBatchKey, List<TurSNJobItem>> batches, Set<String> ensuredCores) {
        if (batches.isEmpty()) {
            return;
        }
        batches.forEach((key, items) -> flushGroup(key.siteName(), key.locale(), items, ensuredCores));
        batches.clear();
    }

    /**
     * T803 / §LV.1 — indexes a group of consecutive CREATE items for one
     * (site, locale) in a single bulk pass: one {@code indexDocuments(...)} call
     * plus one trailing {@code commit(...)}, after converging the union of the
     * group's field specs once (T805) and pre-building each document's attribute
     * map through the per-document enrichment chain. A single-document group
     * stays on the proven per-item path (it is already one round-trip). On any
     * bulk failure — or a partial result — the whole group degrades gracefully to
     * per-item indexing so ordering and robustness are preserved.
     */
    private void flushGroup(String siteName, Locale locale, List<TurSNJobItem> items,
            Set<String> ensuredCores) {
        if (items.isEmpty()) {
            return;
        }
        if (items.size() == 1) {
            processJobItemForSite(siteName, items.getFirst(), ensuredCores);
            return;
        }
        var siteOpt = turSNSiteRepository.findByNameIgnoreCase(siteName);
        if (siteOpt.isEmpty()) {
            log.warn("receiveIndexingQueue: site '{}' not found in DB — skipping batch of {} docs",
                    siteName, items.size());
            return;
        }
        TurSNSite turSNSite = siteOpt.get();
        ensureCoreExists(turSNSite, locale, ensuredCores);
        // T805 / §LV.3 — converge the union of the batch's field specs once, not
        // once per document (the provisioner is idempotent, so this cuts the
        // schema-check cost from O(docs × fields) to O(distinct fields)).
        convergeFields(turSNSite, items);
        List<Map<String, Object>> documents = new ArrayList<>(items.size());
        for (TurSNJobItem item : items) {
            documents.add(buildIndexAttributes(turSNSite, item));
        }
        TurSearchEnginePlugin plugin = pluginFactory.getPluginForSite(turSNSite);
        int indexed;
        try {
            indexed = plugin.indexDocuments(turSNSite, locale, documents);
        } catch (RuntimeException e) {
            log.warn("Bulk index of {} docs on site '{}' failed ({}) — falling back to per-item",
                    items.size(), siteName, e.getMessage());
            items.forEach(item -> processJobItemForSite(siteName, item, ensuredCores));
            return;
        }
        if (indexed < documents.size()) {
            log.warn("Bulk index reported {}/{} docs on site '{}' — falling back to per-item",
                    indexed, documents.size(), siteName);
            items.forEach(item -> processJobItemForSite(siteName, item, ensuredCores));
            return;
        }
        plugin.commit(turSNSite, locale);
        for (int i = 0; i < items.size(); i++) {
            // T383 — side-write the document embedding for HYBRID_RRF sites (no-op
            // for vectorless / legacy sites, the case this block optimizes).
            turSNHybridRankingService.indexDocument(turSNSite, locale, documents.get(i));
            processQueueInfo(turSNSite, items.get(i));
            TurLoggingUtils.setSuccessStatus(items.get(i), TurIndexingStatus.FINISHED);
        }
        log.info("Bulk-indexed {} documents into '{}' ({}) in a single pass",
                documents.size(), siteName, locale);
    }

    /**
     * T805 / §LV.3 — converges the union of every {@link TurSNJobAttributeSpec}
     * carried by the batch (deduplicated by field name) exactly once per site,
     * reusing the idempotent {@link TurSNFieldProvisioner}.
     */
    private void convergeFields(TurSNSite turSNSite, List<TurSNJobItem> items) {
        Map<String, TurSNJobAttributeSpec> union = new LinkedHashMap<>();
        for (TurSNJobItem item : items) {
            List<TurSNJobAttributeSpec> specs = item.getSpecs();
            if (specs == null) {
                continue;
            }
            for (TurSNJobAttributeSpec spec : specs) {
                if (spec != null && spec.getName() != null) {
                    union.putIfAbsent(spec.getName(), spec);
                }
            }
        }
        union.values().forEach(spec -> turSNFieldProvisioner.ensureField(turSNSite, spec));
    }

    /** Composite key for the T803 per-(site, locale) CREATE batching. */
    private record TurSNBatchKey(String siteName, Locale locale) {
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
        createMissingFields(turSNSite, turSNJobItem.getSpecs());
        Map<String, Object> attributes = buildIndexAttributes(turSNSite, turSNJobItem);
        boolean indexed = pluginFactory.getPluginForSite(turSNSite)
                .indexDocument(turSNSite, turSNJobItem.getLocale(), attributes);
        if (indexed) {
            // T383 — side-write the document embedding for HYBRID_RRF sites (no-op otherwise).
            turSNHybridRankingService.indexDocument(turSNSite, turSNJobItem.getLocale(), attributes);
        }
        return indexed;
    }

    /**
     * Builds the final indexable attribute map for one job item: consolidation +
     * merge + duplicate-term removal, then the per-document enrichers. Shared by
     * the single-document {@link #index} path and the T803 bulk path so a batched
     * document is enriched identically to a single one. Field-schema convergence
     * ({@code createMissingFields} / {@link #convergeFields}) is deliberately
     * <em>not</em> done here — the bulk path hoists it out of the per-doc loop.
     */
    private Map<String, Object> buildIndexAttributes(TurSNSite turSNSite, TurSNJobItem turSNJobItem) {
        Map<String, Object> attributes = this.removeDuplicateTerms(turSNMergeProvidersProcess
                .mergeDocuments(turSNSite, getConsolidateResults(turSNJobItem), turSNJobItem.getLocale()));
        // T472 / §XXVI.9 — attach the index-time audience content-fit score (opt-in,
        // no-op otherwise). Provisions its own field; never throws.
        turSNContentFitIndexer.enrich(turSNSite, attributes);
        // T501 / §X.19 — append native Gemini video/audio understanding for a media
        // document's transcript/scenes (opt-in, Gemini-only; no-op otherwise). Never throws.
        turSNGeminiMediaIndexer.enrich(turSNSite, attributes);
        // T672 / §XL — expand the document with the hierarchical path of each
        // controlled-vocabulary term it mentions (opt-in; no-op otherwise). Never throws.
        turSNMicrothesaurusIndexer.enrich(turSNSite, turSNJobItem.getLocale(), attributes);
        return attributes;
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
