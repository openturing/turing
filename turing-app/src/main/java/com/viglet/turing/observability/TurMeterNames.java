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
package com.viglet.turing.observability;

/**
 * Stable Micrometer/OpenTelemetry metric and span names. Treat these as a
 * public contract — dashboards, alerts and trace queries depend on them and
 * must be migrated when changed.
 *
 * <p>Naming follows OpenTelemetry semantic conventions: lower-case, dot
 * separators, plural for counters where appropriate.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public final class TurMeterNames {

    /** Timer for chat or embedding LLM calls. Tags: provider, operation, status. */
    public static final String LLM_CALLS = "turing.llm.calls";

    /** Counter for token usage. Tags: provider, model, direction (in|out). */
    public static final String LLM_TOKENS = "turing.llm.tokens";

    /** Timer for search engine plugin invocations. Tags: engine, operation, status. */
    public static final String SEARCH_CALLS = "turing.search.calls";

    /**
     * Timer for SN search pipeline stages — instruments the Java work around the
     * Solr/SE call (snapshot lookup, query building, result processing, widget
     * assembly). Tags: stage, status. Combined with {@link #SEARCH_CALLS} this
     * splits a request into the parts that can drive different optimizations.
     */
    public static final String SEARCH_PIPELINE = "turing.search.pipeline";

    /** Counter for SN search snapshot cache outcomes. Tags: outcome (hit|miss|evict). */
    public static final String SEARCH_SNAPSHOT_CACHE = "turing.search.snapshot.cache";

    /**
     * Timer for the AI-on-AI chat analytics enricher's full processing cycle.
     * Tags: status (success|empty|skipped). One sample per scheduled tick.
     */
    public static final String CHAT_ANALYTICS_ENRICH_CYCLE = "turing.chat.analytics.enrich.cycle";

    /**
     * Timer for a single session classification. Tags: outcome (classified|
     * unclassified|error). Lets us separate LLM latency from JSON-parsing
     * fallbacks without re-deriving from log lines.
     */
    public static final String CHAT_ANALYTICS_CLASSIFY = "turing.chat.analytics.classify";

    /**
     * Counter for classifier outcomes. Tags: outcome (classified|unclassified|
     * error|skipped|empty_transcript). The enricher's "are we keeping up?" KPI.
     */
    public static final String CHAT_ANALYTICS_CLASSIFICATIONS = "turing.chat.analytics.classifications";

    /**
     * Gauge for the size of the in-memory in-flight session map. Slow growth
     * indicates a leak (sessions started but never closed); a periodic sweep
     * trims entries whose {@code startedAt} is older than
     * {@code turing.chat.analytics.inflight.max-age-hours}.
     */
    public static final String CHAT_ANALYTICS_INFLIGHT_SIZE = "turing.chat.analytics.inflight.size";

    // ---- Tag keys --------------------------------------------------------

    public static final String TAG_PROVIDER = "provider";
    public static final String TAG_MODEL = "model";
    public static final String TAG_OPERATION = "operation";
    public static final String TAG_STATUS = "status";
    public static final String TAG_DIRECTION = "direction";
    public static final String TAG_ENGINE = "engine";
    public static final String TAG_STAGE = "stage";
    public static final String TAG_OUTCOME = "outcome";

    /**
     * Tag key for cache-stats gauges (T32 / §IV.7) — identifies which
     * Hazelcast {@code IMap} the gauge reads. Cardinality is bounded by
     * the project's static {@code @Cacheable} catalog (~10 named caches).
     *
     * @since 2026.3.1
     */
    public static final String TAG_CACHE = "cache";

    /**
     * Tag key for the bypass-reason on T29 tool pre-filter samples — used
     * to count how often each short-circuit branch fires. Cardinality is
     * bounded by the enum-like set of reasons declared in
     * {@link com.viglet.turing.genai.tool.TurToolPreFilterService}.
     *
     * @since 2026.3.1
     */
    public static final String TAG_REASON = "reason";

    // ---- Tag values ------------------------------------------------------

    public static final String OP_CHAT = "chat";
    public static final String OP_STREAM = "stream";
    public static final String OP_EMBED = "embed";
    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_ERROR = "error";
    public static final String DIRECTION_IN = "in";
    public static final String DIRECTION_OUT = "out";

    /** Pipeline stages — names appear as the {@code stage} tag value. */
    public static final String STAGE_SEARCH_TOTAL = "search.total";
    public static final String STAGE_SNAPSHOT_BUILD = "snapshot.build";
    public static final String STAGE_SEARCH_RESPONSE = "search.response";
    public static final String STAGE_RESULT_PROCESS = "result.process";
    public static final String STAGE_WIDGET_BUILD = "widget.build";

    /**
     * Timer for the agent chat pipeline stages — instruments the Java work
     * around the LLM call (entity load, persona resolution, tool callbacks,
     * flow context, prompt build, post-processing). Tags: stage, status.
     * Combined with {@link #LLM_CALLS} this splits a chat turn into the parts
     * we can independently optimize (DB/cache vs. provider round-trip).
     *
     * @since 2026.2.8
     */
    public static final String CHAT_PIPELINE = "turing.chat.pipeline";

    /** Chat pipeline stages — names appear as the {@code stage} tag value. */
    public static final String STAGE_CHAT_TOTAL = "chat.total";
    public static final String STAGE_CHAT_SETUP = "chat.setup";
    public static final String STAGE_CHAT_LLM_CALL = "chat.llm.call";
    public static final String STAGE_CHAT_POST = "chat.post";
    /** Time inside {@code chatFlowEngineService.advance} — includes LlmJudge round-trip if configured. */
    public static final String STAGE_CHAT_ADVANCE = "chat.advance";
    /** Time inside {@code regenerateReplyForNewNode} — second LLM round-trip when the flow advanced to a new aiQuestion. */
    public static final String STAGE_CHAT_REGEN = "chat.regen";

    // ---- Chat setup sub-stages (T32 / §IV.7) -----------------------------
    //
    // Sub-stages of the {@link #STAGE_CHAT_SETUP} parent. Each is a separate
    // {@code stage} tag value on the {@link #CHAT_PIPELINE} timer so the
    // setup phase can be broken down into the parts that drive different
    // optimizations (decrypt + model build are dominated by the first-call
    // cold path; flow / persona resolution are dominated by JPA / cache;
    // tools is dominated by MCP handshake on cold start; compose is the
    // T31 cache hit/miss path).
    //
    // The parent {@link #STAGE_CHAT_SETUP} timer stays — sum of all
    // sub-stages plus the assembly cost between them. Dashboards keep
    // working unchanged; new dashboards can drill in.

    public static final String STAGE_CHAT_SETUP_DECRYPT = "chat.setup.decrypt";
    public static final String STAGE_CHAT_SETUP_MODEL = "chat.setup.model";
    public static final String STAGE_CHAT_SETUP_FLOW = "chat.setup.flow";
    public static final String STAGE_CHAT_SETUP_PERSONA = "chat.setup.persona";
    public static final String STAGE_CHAT_SETUP_TOOLS = "chat.setup.tools";
    public static final String STAGE_CHAT_SETUP_COMPOSE = "chat.setup.compose";

    // ---- Cache stats & retrieval (T32 / §IV.7) ---------------------------

    /**
     * Gauges for Hazelcast cache hit/miss/size, tagged by
     * {@link #TAG_CACHE}. Read from {@code IMap.getLocalMapStats()} on
     * every Prometheus scrape via
     * {@link com.viglet.turing.observability.TurChatCacheStatsRegistrar}.
     * One sample per cache per scrape; cardinality bounded by the static
     * {@code @Cacheable} catalog (~10 names).
     *
     * @since 2026.3.1
     */
    public static final String CHAT_CACHE_HITS = "turing.chat.cache.hits";
    public static final String CHAT_CACHE_MISSES = "turing.chat.cache.misses";
    public static final String CHAT_CACHE_SIZE = "turing.chat.cache.size";

    /**
     * Timer for the T30 conversation-relevance retrieval pass — covers
     * the persisted-history read, the {@link
     * org.apache.lucene.queries.mlt.MoreLikeThis} scoring, and the result
     * assembly. Tagged by {@link #TAG_OUTCOME}:
     * {@code hits} (retrieved &gt;= 1 older turn), {@code no-hits}
     * (BM25 returned nothing), {@code bypass} (one of the disable
     * conditions fired). One sample per chat turn that called
     * {@code TurChatMemoryRelevanceRetriever.enrich(...)}.
     *
     * @since 2026.3.1
     */
    public static final String CHAT_MEMORY_RETRIEVAL = "turing.chat.memory.retrieval";

    /**
     * Distribution summary for the size of the BM25 candidate pool the
     * T30 retriever indexes (= persisted-history size minus the recent-N
     * window). Sampled only when the retrieval ran (bypasses skip the
     * sample).
     *
     * @since 2026.3.1
     */
    public static final String CHAT_MEMORY_RETRIEVAL_POOL = "turing.chat.memory.retrieval.pool";

    /**
     * Distribution summary for the number of older turns the T30
     * retriever actually prepended. Sampled only on non-bypass turns.
     *
     * @since 2026.3.1
     */
    public static final String CHAT_MEMORY_RETRIEVAL_HITS = "turing.chat.memory.retrieval.hits";

    /**
     * Timer for the T29 tool pre-filter pass — covers the BM25 indexing
     * of tool descriptions and the score-then-keep-top-K filtering.
     * Tagged by {@link #TAG_OUTCOME}: {@code filtered} (tool count was
     * actually reduced) or {@code passthrough} (the bypass conditions
     * left the array unchanged).
     *
     * @since 2026.3.1
     */
    public static final String CHAT_TOOL_PREFILTER = "turing.chat.tool.prefilter";

    /**
     * Distribution summary for the input tool count fed into T29's
     * pre-filter on each turn. Pairs with
     * {@link #CHAT_TOOL_PREFILTER_OUTPUT} so dashboards can derive the
     * reduction ratio.
     *
     * @since 2026.3.1
     */
    public static final String CHAT_TOOL_PREFILTER_INPUT = "turing.chat.tool.prefilter.input";

    /**
     * Distribution summary for the output tool count after T29 pruning.
     * On bypass turns this equals the input count.
     *
     * @since 2026.3.1
     */
    public static final String CHAT_TOOL_PREFILTER_OUTPUT = "turing.chat.tool.prefilter.output";

    /**
     * Counter for T29 tool pre-filter bypass branches — tagged by
     * {@link #TAG_REASON}: {@code disabled}, {@code below-threshold},
     * {@code blank-query}, {@code already-covered}, {@code cap-covers-pool},
     * or {@code engaged} when the filter actually ran. Lets ops verify
     * the cost/benefit trade-off is being engaged on the right turns.
     *
     * @since 2026.3.1
     */
    public static final String CHAT_TOOL_PREFILTER_BYPASS = "turing.chat.tool.prefilter.bypass";

    // ---- Tag values for the T30 / T29 outcomes ---------------------------

    /** T30 outcome — at least one older turn was prepended. */
    public static final String OUTCOME_HITS = "hits";
    /** T30 outcome — BM25 returned zero matches. */
    public static final String OUTCOME_NO_HITS = "no-hits";
    /** T30 / T29 outcome — a bypass condition fired; nothing happened. */
    public static final String OUTCOME_BYPASS = "bypass";
    /** T29 outcome — the input tool array was pruned. */
    public static final String OUTCOME_FILTERED = "filtered";
    /** T29 outcome — input tool array was returned unchanged (cap covered the pool). */
    public static final String OUTCOME_PASSTHROUGH = "passthrough";

    // ---- T29 bypass reason values ----------------------------------------

    public static final String REASON_DISABLED = "disabled";
    public static final String REASON_BELOW_THRESHOLD = "below-threshold";
    public static final String REASON_BLANK_QUERY = "blank-query";
    public static final String REASON_ALREADY_COVERED = "already-covered";
    public static final String REASON_CAP_COVERS_POOL = "cap-covers-pool";
    /** T29 — the filter engaged and actually scored the pool. */
    public static final String REASON_ENGAGED = "engaged";

    /** Snapshot cache outcomes — appear as {@code outcome} tag values. */
    public static final String OUTCOME_HIT = "hit";
    public static final String OUTCOME_MISS = "miss";
    public static final String OUTCOME_EVICT = "evict";

    private TurMeterNames() {}
}
