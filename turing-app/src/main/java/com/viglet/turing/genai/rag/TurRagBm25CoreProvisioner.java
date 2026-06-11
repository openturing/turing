/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.rag;

import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.model.rag.TurRagBm25Core;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.store.TurStoreInstance;
import com.viglet.turing.persistence.repository.rag.TurRagBm25CoreRepository;
import com.viglet.turing.plugins.se.TurSearchEnginePlugin;
import com.viglet.turing.plugins.se.TurSearchEnginePluginFactory;

import lombok.extern.slf4j.Slf4j;

/**
 * T24b / §III.2 — orchestrates lifecycle of per-locale BM25 cores backing
 * the hybrid RAG path. Idempotent CRUD over {@link TurRagBm25Core} rows +
 * the matching SE core/index via {@link TurSearchEnginePlugin}.
 *
 * <h2>Two-layer state</h2>
 *
 * The provisioner maintains coherent state across two systems:
 *
 * <ul>
 *   <li><b>JPA layer</b>: {@code TurRagBm25Core} row tracks intent +
 *       lifecycle status ({@code NOT_PROVISIONED} / {@code PROVISIONING}
 *       / {@code PROVISIONED} / {@code ERROR} / {@code DELETING}).</li>
 *   <li><b>SE layer</b>: the actual Solr core / Elasticsearch index /
 *       Lucene directory. Owned by the configured search engine instance,
 *       created via {@link TurSearchEnginePlugin#createStandaloneIndex}
 *       with the locale-appropriate analyzer chain.</li>
 * </ul>
 *
 * Operations are designed to be safely retryable — calling
 * {@link #provision(TurStoreInstance, Locale, TurSEInstance)} twice with
 * the same args is a no-op when the row already exists in {@code PROVISIONED}
 * state, and recovers cleanly from a stuck {@code PROVISIONING} or
 * {@code ERROR} state.
 *
 * <h2>What this class does NOT do</h2>
 *
 * <ul>
 *   <li>Indexing chunks — see {@code TurRagBm25Indexer} (Phase 2).</li>
 *   <li>Querying — see the hybrid path in
 *       {@code TurRagSearchToolService}, refactored in Phase 3.</li>
 *   <li>Solr/ES BM25 similarity tuning beyond defaults — schema is
 *       generated with stock {@code k1=1.2, b=0.75} values; admins
 *       wanting other values edit the core/index config directly
 *       (documented in {@code docs/RAG_HYBRID_CONFIG.md}).</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@Slf4j
@Service
public class TurRagBm25CoreProvisioner {

    /**
     * Maximum length of the {@code storeShortId} prefix in core names.
     * 8 hex chars give 2^32 unique values per store — far more than any
     * realistic deployment, and short enough to keep core names readable.
     */
    private static final int STORE_SHORT_ID_LENGTH = 8;

    private final TurRagBm25CoreRepository coreRepository;
    private final TurSearchEnginePluginFactory pluginFactory;
    private final com.viglet.turing.tenant.TurTenantCoreNaming turTenantCoreNaming;

    public TurRagBm25CoreProvisioner(TurRagBm25CoreRepository coreRepository,
            TurSearchEnginePluginFactory pluginFactory,
            com.viglet.turing.tenant.TurTenantCoreNaming turTenantCoreNaming) {
        this.coreRepository = coreRepository;
        this.pluginFactory = pluginFactory;
        this.turTenantCoreNaming = turTenantCoreNaming;
    }

    /**
     * Builds the canonical core name for a (store, locale) pair. Pure
     * function — same inputs always produce the same name, no DB access.
     * Visible so the indexer / query path can compute the name without
     * hitting the repository when they already know the inputs.
     */
    public static String coreNameFor(TurStoreInstance store, Locale locale) {
        if (store == null || store.getId() == null || locale == null) {
            throw new IllegalArgumentException(
                    "coreNameFor requires non-null store + locale (got store="
                            + store + ", locale=" + locale + ")");
        }
        String shortId = store.getId().replace("-", "");
        if (shortId.length() > STORE_SHORT_ID_LENGTH) {
            shortId = shortId.substring(0, STORE_SHORT_ID_LENGTH);
        }
        // BCP 47 tag (pt-BR, en-US). Already URL-/file-system-safe and
        // case-stable, so safe to embed in the core name verbatim.
        return "rag_" + shortId + "_" + locale.toLanguageTag();
    }

    /**
     * Provisions (or recovers) the BM25 core for a (store, locale) tuple
     * on the supplied SE instance. Idempotent:
     *
     * <ul>
     *   <li>Row absent → creates row in {@code PROVISIONING}, calls the
     *       SE plugin to create the standalone index, marks
     *       {@code PROVISIONED} on success.</li>
     *   <li>Row in {@code PROVISIONED} → no-op (verifies the SE-side
     *       index still exists; logs a WARN if not, then re-creates).</li>
     *   <li>Row in {@code ERROR} or stuck {@code PROVISIONING} →
     *       reattempts the SE-side creation.</li>
     *   <li>Row in {@code DELETING} → throws (caller must wait for
     *       deletion to complete or explicitly cancel).</li>
     * </ul>
     *
     * @return the persisted {@link TurRagBm25Core} in its final state
     */
    @Transactional
    public TurRagBm25Core provision(TurStoreInstance store, Locale locale, TurSEInstance seInstance) {
        if (store == null || locale == null || seInstance == null) {
            throw new IllegalArgumentException(
                    "provision requires non-null store, locale, seInstance");
        }
        TurRagBm25Core core = coreRepository
                .findByTurStoreInstance_IdAndLocale(store.getId(), locale)
                .orElseGet(() -> newRowFor(store, locale, seInstance,
                        turTenantCoreNaming.scoped(coreNameFor(store, locale))));

        if (core.getStatus() == TurRagBm25Core.Status.DELETING) {
            throw new IllegalStateException(
                    "Core " + core.getCoreName() + " is being deleted — wait for it to complete");
        }

        core.setStatus(TurRagBm25Core.Status.PROVISIONING);
        core.setLastError(null);
        core = coreRepository.save(core);

        try {
            TurSearchEnginePlugin plugin = pluginFactory.getPluginForInstance(seInstance);
            // Idempotent at the SE level: createStandaloneIndex is a no-op
            // when the core/index already exists (Solr 9 returns OK, ES 8
            // accepts the resource_already_exists_exception silently in
            // our wrapper). When state diverges (DB says PROVISIONED but
            // SE has lost the index), this recreates it cleanly.
            if (!plugin.indexExists(seInstance, core.getCoreName())) {
                log.info("[RagBm25Provisioner] Creating SE core '{}' (locale={}, plugin={})",
                        core.getCoreName(), locale.toLanguageTag(), plugin.getPluginType());
                plugin.createStandaloneIndex(seInstance, locale, core.getCoreName());
            } else {
                log.info("[RagBm25Provisioner] SE core '{}' already exists — skipping create",
                        core.getCoreName());
            }
            core.setStatus(TurRagBm25Core.Status.PROVISIONED);
            return coreRepository.save(core);
        } catch (RuntimeException e) {
            log.warn("[RagBm25Provisioner] Provisioning failed for core '{}': {}",
                    core.getCoreName(), e.getMessage(), e);
            core.setStatus(TurRagBm25Core.Status.ERROR);
            core.setLastError(truncateError(e.getMessage()));
            coreRepository.save(core);
            throw e;
        }
    }

    /**
     * Deletes the (store, locale) BM25 core. Sets row status to
     * {@code DELETING}, calls {@link TurSearchEnginePlugin#deleteIndex}
     * on the SE side, then deletes the row. Best-effort: when the SE
     * delete fails (network, permissions), the row remains in
     * {@code DELETING} for manual recovery.
     */
    @Transactional
    public void deprovision(TurStoreInstance store, Locale locale) {
        Optional<TurRagBm25Core> existing = coreRepository
                .findByTurStoreInstance_IdAndLocale(store.getId(), locale);
        if (existing.isEmpty()) {
            return;
        }
        TurRagBm25Core core = existing.get();
        core.setStatus(TurRagBm25Core.Status.DELETING);
        coreRepository.save(core);
        try {
            TurSearchEnginePlugin plugin = pluginFactory.getPluginForInstance(core.getTurSEInstance());
            plugin.deleteIndex(core.getTurSEInstance(), core.getCoreName());
            coreRepository.delete(core);
            log.info("[RagBm25Provisioner] Deleted core '{}' (store={}, locale={})",
                    core.getCoreName(), store.getId(), locale.toLanguageTag());
        } catch (RuntimeException e) {
            // Don't delete the row when SE delete failed — admin needs
            // visibility to retry. Log loud and re-throw.
            log.error("[RagBm25Provisioner] Failed to delete SE core '{}': {}",
                    core.getCoreName(), e.getMessage(), e);
            core.setLastError(truncateError(e.getMessage()));
            coreRepository.save(core);
            throw e;
        }
    }

    /**
     * Returns the existing core row for the (store, locale) pair, or
     * empty when not provisioned. Used by query / indexing paths that
     * shouldn't auto-create — the SN site GenAi save path is the one
     * that explicitly provisions.
     */
    public Optional<TurRagBm25Core> findCore(TurStoreInstance store, Locale locale) {
        if (store == null || store.getId() == null || locale == null) {
            return Optional.empty();
        }
        return coreRepository.findByTurStoreInstance_IdAndLocale(store.getId(), locale);
    }

    private static TurRagBm25Core newRowFor(TurStoreInstance store, Locale locale,
            TurSEInstance seInstance, String coreName) {
        TurRagBm25Core core = new TurRagBm25Core();
        core.setTurStoreInstance(store);
        core.setTurSEInstance(seInstance);
        core.setLocale(locale);
        core.setCoreName(coreName);
        core.setStatus(TurRagBm25Core.Status.NOT_PROVISIONED);
        core.setDocCount(0L);
        return core;
    }

    /**
     * Caps captured error messages at the column length (2000) so a
     * verbose stack trace from the SE doesn't trip Hibernate's "string
     * too long" rejection on save.
     */
    private static String truncateError(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 2000 ? message : message.substring(0, 1997) + "...";
    }
}
