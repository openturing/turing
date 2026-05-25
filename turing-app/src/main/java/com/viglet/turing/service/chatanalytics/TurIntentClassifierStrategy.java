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

import java.util.List;
import java.util.Map;

/**
 * T28 / §III.5 — pluggable classification backend for the chat
 * analytics enricher. Implementations register themselves as
 * {@code @Component}s and the {@link TurChatIntentClassifier}
 * orchestrator selects which one to use per session based on
 * {@code turing.chat.analytics.classifier.mode}.
 *
 * <p>Three backends contemplated by §III.5; this interface covers all:
 * <ul>
 *   <li>{@code TurLlmIntentClassifier} — asks the default LLM to label
 *       the transcript. Production default when an LLM is configured;
 *       costs an LLM round-trip per session.</li>
 *   <li>{@code TurLuceneIntentClassifier} — embedded Lucene
 *       {@code MoreLikeThis} over the per-agent
 *       {@link com.viglet.turing.persistence.model.chatanalytics.TurAnalyticsIntent}
 *       catalog. Zero external deps, runs in-process; the
 *       dev/single-instance/no-LLM fallback.</li>
 *   <li>{@code TurSeMltIntentClassifier} (T28 Phase B, deferred) —
 *       search-engine-backed MLT through
 *       {@code TurSearchEnginePlugin}, so the same code path covers
 *       Solr ({@code MoreLikeThisHandler}) and Elasticsearch
 *       ({@code more_like_this} query) — whichever engine the agent's
 *       {@code TurSEInstance} resolves to. The cluster-aware production
 *       target.</li>
 * </ul>
 *
 * <p>Strategy contract:
 * <ol>
 *   <li>{@link #getType()} — unique id used by the orchestrator's
 *       property-based selection.</li>
 *   <li>{@link #isAvailable(String)} — fast check: is this strategy
 *       <em>configured</em> at all for this agent (catalog present, LLM
 *       configured, etc.). Decides whether to consider the strategy in
 *       the {@code auto} cascade.</li>
 *   <li>{@link #classify(String, String, List)} — does the actual work.
 *       Returns {@link TurChatSessionEnrichment#unclassified()} when the
 *       strategy <em>could</em> classify but didn't find a strong-enough
 *       match — signals to the orchestrator that the next cascade step
 *       can have a shot.</li>
 * </ol>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public interface TurIntentClassifierStrategy {

    /** Unique identifier — {@code llm}, {@code lucene}, {@code elasticsearch-mlt}. */
    String getType();

    /**
     * Fast, non-classifying availability check. Cheap on purpose — runs
     * in every enricher cycle before the orchestrator even considers
     * spending an LLM round-trip or a Lucene index build.
     *
     * @param agentId the session's owning agent id; may be {@code null}
     *        for legacy rows without an agent identity. Strategies that
     *        require per-agent state (catalog) MUST return {@code false}
     *        for null/blank agent ids.
     */
    boolean isAvailable(String agentId);

    /**
     * Classify one session's transcript into an
     * {@link TurChatSessionEnrichment}. Implementations MUST be defensive
     * — failures fall back to {@link TurChatSessionEnrichment#unclassified()}
     * so a single misbehaving session doesn't poison the enricher cycle.
     *
     * @param agentId the session's owning agent id (may be null — see
     *        {@link #isAvailable(String)})
     * @param firstUserMessage convenience fallback when {@code messages}
     *        is empty (e.g. agents without chat memory enabled)
     * @param messages full transcript ordered chronologically; each entry
     *        carries {@code role} + {@code content} as strings
     */
    TurChatSessionEnrichment classify(String agentId, String firstUserMessage,
            List<Map<String, Object>> messages);
}
