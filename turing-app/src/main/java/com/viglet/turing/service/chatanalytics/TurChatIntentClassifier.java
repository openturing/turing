/*
 * Copyright (C) 2016-2026 the original author or authors.
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
package com.viglet.turing.service.chatanalytics;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.viglet.turing.observability.TurMeterNames;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

/**
 * T28 / §III.5 — orchestrator that picks one of the registered
 * {@link TurIntentClassifierStrategy} backends per session and records
 * the outcome telemetry. Replaces the original single-strategy LLM
 * classifier from 2026.2.7; the LLM logic now lives in
 * {@link TurLlmIntentClassifier}, the Lucene MLT fallback in
 * {@link TurLuceneIntentClassifier}, and the deferred Elasticsearch MLT
 * production target ({@code TurEsMltIntentClassifier}) will plug into
 * the same interface in T28 Phase B.
 *
 * <h2>Selection cascade</h2>
 *
 * Driven by {@code turing.chat.analytics.classifier.mode}:
 * <ul>
 *   <li>{@code auto} (default) — tries strategies in declared priority
 *       order ({@code llm} → {@code elasticsearch-mlt} → {@code lucene}).
 *       First strategy whose {@link TurIntentClassifierStrategy#isAvailable(String)}
 *       returns {@code true} runs; if it returns
 *       {@link TurChatSessionEnrichment#unclassified()} the cascade
 *       falls through to the next available strategy.</li>
 *   <li>{@code llm} — only the LLM classifier; preserves legacy
 *       behavior for installs that depend on LLM-only labels.</li>
 *   <li>{@code lucene} — only the embedded Lucene MLT classifier; useful
 *       for offline / no-LLM deployments without burning API budget.</li>
 *   <li>{@code elasticsearch-mlt} — only the SE-backed classifier
 *       (T28 Phase B; until shipped, falls through to "no strategy
 *       picked" and returns unclassified).</li>
 * </ul>
 *
 * <p>Telemetry: same meter names as the original single-strategy
 * classifier — {@code turing.chat.analytics.classify} timer and
 * {@code turing.chat.analytics.classifications} counter, with an extra
 * {@code strategy} tag so operators can split outcomes per backend.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1 (was a single-strategy LLM classifier in 2026.2.7)
 */
@Slf4j
@Service
public class TurChatIntentClassifier {

    /**
     * Strategy type for the deferred search-engine-backed classifier
     * (T28 Phase B). Goes through {@code TurSearchEnginePlugin} so the
     * same code path serves Solr {@code MoreLikeThisHandler} and
     * Elasticsearch {@code more_like_this} — whichever engine the
     * agent's {@code TurSEInstance} resolves to. The property value
     * {@code elasticsearch-mlt} is accepted as a back-compat alias.
     */
    public static final String SEARCH_ENGINE_MLT_TYPE = "search-engine-mlt";

    /** Selection-mode property values for {@code turing.chat.analytics.classifier.mode}. */
    public enum Mode {
        AUTO, LLM, LUCENE, SEARCH_ENGINE_MLT;

        static Mode parse(String value) {
            if (value == null || value.isBlank()) return AUTO;
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "llm" -> LLM;
                case "lucene" -> LUCENE;
                case "search-engine-mlt", "se-mlt", "se",
                        "elasticsearch-mlt", "es-mlt", "es", "solr-mlt", "solr"
                        -> SEARCH_ENGINE_MLT;
                default -> AUTO;
            };
        }
    }

    /**
     * Priority order for the {@code auto} cascade: prefer the LLM (richest
     * output: intent label + goal + sentiment + key terms), fall back to
     * the search-engine-backed MLT (catalog-driven, no LLM call, runs
     * over Solr/ES via {@code TurSearchEnginePlugin}), finally the
     * embedded Lucene MLT.
     */
    private static final List<String> AUTO_PRIORITY = List.of(
            TurLlmIntentClassifier.TYPE,
            SEARCH_ENGINE_MLT_TYPE,
            TurLuceneIntentClassifier.TYPE);

    private final Map<String, TurIntentClassifierStrategy> strategiesByType;
    private final Mode mode;
    private final MeterRegistry meterRegistry;

    public TurChatIntentClassifier(List<TurIntentClassifierStrategy> strategies,
            @Value("${turing.chat.analytics.classifier.mode:auto}") String modeValue,
            @Autowired(required = false) MeterRegistry meterRegistry) {
        this.strategiesByType = strategies.stream()
                .collect(Collectors.toUnmodifiableMap(
                        TurIntentClassifierStrategy::getType,
                        Function.identity()));
        this.mode = Mode.parse(modeValue);
        this.meterRegistry = meterRegistry;
        log.info("[ChatIntentClassifier] mode={} registered strategies={}",
                mode, strategiesByType.keySet());
    }

    /**
     * Backwards-compat shim — kept so callers still on the 2026.2.7
     * signature (no agent id) keep working. Routes to the agent-aware
     * variant with a null agent id, which means LLM-only paths still
     * run while catalog-driven paths short-circuit.
     */
    public TurChatSessionEnrichment classify(String firstUserMessage,
            List<Map<String, Object>> messages) {
        return classify(null, firstUserMessage, messages);
    }

    /**
     * Classify one session. Returns
     * {@link TurChatSessionEnrichment#unclassified()} when no strategy is
     * available or all available strategies returned unclassified — the
     * caller (enricher) still marks the session as processed so it
     * doesn't requeue forever.
     */
    public TurChatSessionEnrichment classify(String agentId, String firstUserMessage,
            List<Map<String, Object>> messages) {
        Timer.Sample sample = meterRegistry == null ? null : Timer.start(meterRegistry);
        TurIntentClassifierStrategy chosen = pickStrategy(agentId);
        if (chosen == null) {
            return finish(sample, "skipped", "none", TurChatSessionEnrichment.unclassified());
        }
        try {
            TurChatSessionEnrichment result = chosen.classify(agentId, firstUserMessage, messages);
            if (mode == Mode.AUTO
                    && result.intentLabel() == TurChatIntentLabel.UNCLASSIFIED) {
                // Auto cascade: try the next available strategy if the
                // primary one bailed out — catches the "LLM offline at
                // classification time but catalog is loaded" gap.
                TurIntentClassifierStrategy next = pickNextStrategy(agentId, chosen.getType());
                if (next != null) {
                    log.debug("[ChatIntentClassifier] '{}' returned unclassified — cascading to '{}'",
                            chosen.getType(), next.getType());
                    TurChatSessionEnrichment cascaded = next.classify(agentId, firstUserMessage, messages);
                    String outcome = cascaded.intentLabel() == TurChatIntentLabel.UNCLASSIFIED
                            ? "unclassified" : "classified";
                    return finish(sample, outcome, next.getType(), cascaded);
                }
            }
            String outcome = result.intentLabel() == TurChatIntentLabel.UNCLASSIFIED
                    ? "unclassified" : "classified";
            return finish(sample, outcome, chosen.getType(), result);
        } catch (RuntimeException e) {
            log.warn("[ChatIntentClassifier] '{}' threw: {}", chosen.getType(), e.getMessage());
            return finish(sample, "error", chosen.getType(), TurChatSessionEnrichment.unclassified());
        }
    }

    /**
     * Mirrors the original {@code isAvailable()} from 2026.2.7 — gates
     * the enricher cycle. Returns true iff at least one registered
     * strategy reports itself available for SOME agent (we don't know
     * which sessions will land in the cycle yet). For mode-pinned
     * setups, the gate respects the pin.
     */
    public boolean isAvailable() {
        return switch (mode) {
            case LLM -> strategyAvailable(TurLlmIntentClassifier.TYPE, null);
            case LUCENE -> hasAnyStrategy(TurLuceneIntentClassifier.TYPE);
            case SEARCH_ENGINE_MLT -> hasAnyStrategy(SEARCH_ENGINE_MLT_TYPE);
            case AUTO -> strategyAvailable(TurLlmIntentClassifier.TYPE, null)
                    || hasAnyStrategy(TurLuceneIntentClassifier.TYPE)
                    || hasAnyStrategy(SEARCH_ENGINE_MLT_TYPE);
        };
    }

    private boolean hasAnyStrategy(String type) {
        return strategiesByType.containsKey(type);
    }

    private boolean strategyAvailable(String type, String agentId) {
        TurIntentClassifierStrategy strategy = strategiesByType.get(type);
        return strategy != null && strategy.isAvailable(agentId);
    }

    private TurIntentClassifierStrategy pickStrategy(String agentId) {
        return switch (mode) {
            case LLM -> available(TurLlmIntentClassifier.TYPE, agentId);
            case LUCENE -> available(TurLuceneIntentClassifier.TYPE, agentId);
            case SEARCH_ENGINE_MLT -> available(SEARCH_ENGINE_MLT_TYPE, agentId);
            case AUTO -> firstAvailable(AUTO_PRIORITY, agentId, null);
        };
    }

    private TurIntentClassifierStrategy pickNextStrategy(String agentId, String alreadyTried) {
        return firstAvailable(AUTO_PRIORITY, agentId, alreadyTried);
    }

    private TurIntentClassifierStrategy firstAvailable(List<String> priorityOrder, String agentId,
            String exclude) {
        for (String type : priorityOrder) {
            if (type.equals(exclude)) continue;
            TurIntentClassifierStrategy strategy = strategiesByType.get(type);
            if (strategy != null && strategy.isAvailable(agentId)) {
                return strategy;
            }
        }
        return null;
    }

    private TurIntentClassifierStrategy available(String type, String agentId) {
        TurIntentClassifierStrategy strategy = strategiesByType.get(type);
        if (strategy == null || !strategy.isAvailable(agentId)) {
            return null;
        }
        return strategy;
    }

    private TurChatSessionEnrichment finish(Timer.Sample sample, String outcome, String strategy,
            TurChatSessionEnrichment value) {
        if (meterRegistry != null) {
            if (sample != null) {
                sample.stop(Timer.builder(TurMeterNames.CHAT_ANALYTICS_CLASSIFY)
                        .tag(TurMeterNames.TAG_OUTCOME, outcome)
                        .tag("strategy", strategy)
                        .register(meterRegistry));
            }
            Counter.builder(TurMeterNames.CHAT_ANALYTICS_CLASSIFICATIONS)
                    .tag(TurMeterNames.TAG_OUTCOME, outcome)
                    .tag("strategy", strategy)
                    .register(meterRegistry)
                    .increment();
        }
        return value;
    }

    /** Test-visible: which strategies are registered. */
    java.util.Set<String> registeredTypes() {
        return strategiesByType.keySet();
    }

    /** Test-visible: the active selection mode. */
    Mode mode() {
        return mode;
    }
}
