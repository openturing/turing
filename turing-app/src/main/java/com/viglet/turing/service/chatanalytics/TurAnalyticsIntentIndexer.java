/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.chatanalytics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.chatanalytics.TurAnalyticsIntent;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.chatanalytics.TurAnalyticsIntentRepository;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.plugins.se.TurSearchEnginePlugin;
import com.viglet.turing.plugins.se.TurSearchEnginePluginFactory;

import lombok.extern.slf4j.Slf4j;

/**
 * T28 / §III.5 — keeps the search engine's per-agent intent index in
 * sync with {@link TurAnalyticsIntent} catalog mutations. Pushes one
 * document per enabled catalog row to the index named by
 * {@link #indexNameFor(String)} on the resolved {@link TurSEInstance}
 * — Solr/ES through {@link TurSearchEnginePlugin} so the same code path
 * covers both engines transparently.
 *
 * <p>Index schema (per document):
 * <ul>
 *   <li>{@code id} — the catalog row id (so subsequent updates are
 *       in-place upserts, not duplicates).</li>
 *   <li>{@code agentId} — the owning agent id; used by
 *       {@link #reindexAgent(String)} to wipe the agent's slice before
 *       re-pushing.</li>
 *   <li>{@code label} — the catalog label, returned as the classifier's
 *       intent on a match.</li>
 *   <li>{@code samples} — the concatenated samples; the MLT-scored
 *       field. Indexed without storing (text-only) for engines that
 *       support that split.</li>
 * </ul>
 *
 * <h2>SE instance binding</h2>
 *
 * Phase B uses the global default: the SE id from
 * {@code turing.chat.analytics.classifier.se-instance-id}, falling back
 * to the first registered SE instance. The per-agent SE binding (new
 * column on {@code TurAIAgent}) is a Phase C concern.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurAnalyticsIntentIndexer {

    private static final String FIELD_ID = "id";
    private static final String FIELD_AGENT_ID = "agentId";
    private static final String FIELD_LABEL = "label";
    private static final String FIELD_SAMPLES = "samples";

    private final TurAnalyticsIntentRepository intentRepository;
    private final TurSEInstanceRepository seInstanceRepository;
    private final TurAIAgentRepository agentRepository;
    private final TurSearchEnginePluginFactory pluginFactory;
    private final String configuredSeInstanceId;

    // T268 / §XIV.4.2 note: the intent index name is `intent_<agentId>` where
    // agentId is a globally-unique UUID, so it cannot collide across tenants —
    // no tenant prefix is required here (unlike the SN core, which is keyed by
    // the human-chosen site name and IS prefixed in TurSNTemplate).

    public TurAnalyticsIntentIndexer(TurAnalyticsIntentRepository intentRepository,
            TurSEInstanceRepository seInstanceRepository,
            TurAIAgentRepository agentRepository,
            TurSearchEnginePluginFactory pluginFactory,
            @Value("${turing.chat.analytics.classifier.se-instance-id:}") String configuredSeInstanceId) {
        this.intentRepository = intentRepository;
        this.seInstanceRepository = seInstanceRepository;
        this.agentRepository = agentRepository;
        this.pluginFactory = pluginFactory;
        this.configuredSeInstanceId = configuredSeInstanceId;
    }

    /**
     * Resolves the SE instance to use for {@code agentId}'s analytics
     * intent index. Resolution order:
     * <ol>
     *   <li>Phase C per-agent binding: {@link TurAIAgent#analyticsSeInstance}
     *       when set on the agent row.</li>
     *   <li>Property override: {@code turing.chat.analytics.classifier.se-instance-id}.</li>
     *   <li>Global fallback: the first registered SE instance.</li>
     * </ol>
     *
     * <p>Returns empty when the project has no SE configured at all —
     * the classifier's {@code isAvailable} reports false in that case.
     *
     * <p>Pass {@code null} for {@code agentId} to skip the per-agent
     * binding lookup (the indexer falls back to the global resolution
     * order — useful for bootstrap paths where no agent context is yet
     * established).
     */
    public Optional<TurSEInstance> resolveSeInstance(String agentId) {
        if (agentId != null && !agentId.isBlank()) {
            Optional<TurSEInstance> agentBinding = agentRepository.findById(agentId)
                    .map(TurAIAgent::getAnalyticsSeInstance);
            if (agentBinding.isPresent()) {
                return agentBinding;
            }
        }
        if (configuredSeInstanceId != null && !configuredSeInstanceId.isBlank()) {
            return seInstanceRepository.findById(configuredSeInstanceId);
        }
        List<TurSEInstance> all = seInstanceRepository.findAll();
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
    }

    /**
     * Backwards-compat overload used by the SE-MLT classifier's
     * availability check. Delegates to {@link #resolveSeInstance(String)}
     * with {@code null} so the caller falls straight through to the
     * global resolution order.
     */
    public Optional<TurSEInstance> resolveSeInstance() {
        return resolveSeInstance(null);
    }

    /**
     * Per-agent index name. The {@code intent_} prefix lets the SN site
     * search API filter these out the same way T24b uses {@code rag_}
     * for the BM25 cores — admins on shared Solr installs don't see
     * analytics indexes leaking into public search.
     *
     * <p>The agent id is normalized (UUID dashes stripped, truncated to
     * 12 chars) because Solr/ES core names cap out at 256 chars and we
     * want the prefix to read cleanly. Collisions across agents are
     * astronomically unlikely with a UUID first-12-chars space.
     */
    public static String indexNameFor(String agentId) {
        if (agentId == null) {
            return "intent_unknown";
        }
        String normalized = agentId.replace("-", "").toLowerCase(Locale.ROOT);
        if (normalized.length() > 12) {
            normalized = normalized.substring(0, 12);
        }
        return "intent_" + normalized;
    }

    /**
     * Upsert a single catalog row into the SE-side index. Called from
     * the JPA {@code @PostPersist}/{@code @PostUpdate} hook on
     * {@link TurAnalyticsIntent}.
     *
     * <p>Returns {@code false} (logged) when SE isn't reachable — the
     * row stays in JPA so the next mutation or reindex retries.
     * Tolerant by design: the analytics enricher cycle runs minutes
     * after the mutation, plenty of time for a transient SE blip to
     * resolve.
     */
    public boolean indexRow(TurAnalyticsIntent intent) {
        if (intent == null || intent.getTurAIAgent() == null) {
            return false;
        }
        String agentId = intent.getTurAIAgent().getId();
        if (intent.getEnabled() != 1) {
            // Disabled rows are scrubbed from the SE so they don't keep
            // surfacing on MLT matches against a label admins paused.
            return removeRow(intent.getId(), agentId);
        }
        Optional<TurSEInstance> seInstance = resolveSeInstance(agentId);
        if (seInstance.isEmpty()) {
            return false;
        }
        TurSearchEnginePlugin plugin = pluginFactory.getPluginForInstance(seInstance.get());
        String indexName = indexNameFor(intent.getTurAIAgent().getId());
        try {
            boolean ok = plugin.indexStandaloneDocument(seInstance.get(), indexName,
                    toDocument(intent));
            if (ok) {
                plugin.commitStandalone(seInstance.get(), indexName);
            }
            return ok;
        } catch (UnsupportedOperationException e) {
            log.debug("[AnalyticsIntentIndexer] plugin '{}' has no standalone indexing — skipping",
                    plugin.getPluginType());
            return false;
        } catch (RuntimeException e) {
            log.warn("[AnalyticsIntentIndexer] index '{}' for row '{}' failed: {}",
                    indexName, intent.getId(), e.getMessage());
            return false;
        }
    }

    /**
     * Remove a single catalog row from the SE-side index. Called from
     * the JPA {@code @PostRemove} hook.
     */
    public boolean removeRow(String intentId, String agentId) {
        if (intentId == null || agentId == null) {
            return false;
        }
        Optional<TurSEInstance> seInstance = resolveSeInstance(agentId);
        if (seInstance.isEmpty()) {
            return false;
        }
        TurSearchEnginePlugin plugin = pluginFactory.getPluginForInstance(seInstance.get());
        String indexName = indexNameFor(agentId);
        try {
            boolean ok = plugin.deIndexStandalone(seInstance.get(), indexName, intentId);
            if (ok) {
                plugin.commitStandalone(seInstance.get(), indexName);
            }
            return ok;
        } catch (UnsupportedOperationException e) {
            log.debug("[AnalyticsIntentIndexer] plugin '{}' has no standalone deindexing — skipping",
                    plugin.getPluginType());
            return false;
        } catch (RuntimeException e) {
            log.warn("[AnalyticsIntentIndexer] deindex id='{}' on '{}' failed: {}",
                    intentId, indexName, e.getMessage());
            return false;
        }
    }

    /**
     * Bootstrap path — wipes the agent's slice and re-pushes every
     * enabled row. Used by the {@code POST /reindex} admin endpoint
     * when the catalog existed before the SE was configured, OR after
     * an SE schema change that the incremental hooks can't recover from.
     *
     * @return number of catalog rows actually accepted by the SE
     */
    public int reindexAgent(String agentId) {
        Optional<TurSEInstance> seInstance = resolveSeInstance(agentId);
        if (seInstance.isEmpty()) {
            return 0;
        }
        TurSearchEnginePlugin plugin = pluginFactory.getPluginForInstance(seInstance.get());
        String indexName = indexNameFor(agentId);
        try {
            // Wipe the agent's slice first so stale rows (renamed
            // labels, deleted samples) don't keep showing up as MLT hits.
            plugin.deIndexStandaloneByField(seInstance.get(), indexName, FIELD_AGENT_ID, agentId);
        } catch (UnsupportedOperationException e) {
            log.debug("[AnalyticsIntentIndexer] plugin '{}' has no deIndexByField — skipping wipe",
                    plugin.getPluginType());
        } catch (RuntimeException e) {
            log.warn("[AnalyticsIntentIndexer] reindex wipe on '{}' failed: {}",
                    indexName, e.getMessage());
        }
        List<TurAnalyticsIntent> catalog = intentRepository
                .findByTurAIAgent_IdAndEnabledOrderByLabelAsc(agentId, 1);
        if (catalog == null || catalog.isEmpty()) {
            plugin.commitStandalone(seInstance.get(), indexName);
            return 0;
        }
        List<Map<String, Object>> docs = new ArrayList<>(catalog.size());
        for (TurAnalyticsIntent intent : catalog) {
            if (intent.getSamples() == null || intent.getSamples().isBlank()) continue;
            docs.add(toDocument(intent));
        }
        int indexed = 0;
        try {
            indexed = plugin.indexStandaloneDocuments(seInstance.get(), indexName, docs);
        } catch (UnsupportedOperationException e) {
            log.debug("[AnalyticsIntentIndexer] plugin '{}' has no standalone batch indexing",
                    plugin.getPluginType());
        } catch (RuntimeException e) {
            log.warn("[AnalyticsIntentIndexer] reindex batch on '{}' failed: {}",
                    indexName, e.getMessage());
        }
        try {
            plugin.commitStandalone(seInstance.get(), indexName);
        } catch (RuntimeException e) {
            log.debug("[AnalyticsIntentIndexer] commit on '{}' failed: {}",
                    indexName, e.getMessage());
        }
        return indexed;
    }

    private Map<String, Object> toDocument(TurAnalyticsIntent intent) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put(FIELD_ID, intent.getId());
        doc.put(FIELD_AGENT_ID, intent.getTurAIAgent().getId());
        doc.put(FIELD_LABEL, intent.getLabel());
        doc.put(FIELD_SAMPLES, normalizeSamples(intent.getSamples()));
        return doc;
    }

    /**
     * Same normalization as the Phase A {@code TurLuceneIntentClassifier}
     * — newline-separated samples become space-separated text, with
     * empty lines dropped. Keeps the SE-indexed payload identical to
     * the embedded-classifier path so scoring is comparable.
     */
    private static String normalizeSamples(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder(raw.length());
        for (String line : raw.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(trimmed);
        }
        return sb.toString();
    }
}
