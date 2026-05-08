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

    /** Snapshot cache outcomes — appear as {@code outcome} tag values. */
    public static final String OUTCOME_HIT = "hit";
    public static final String OUTCOME_MISS = "miss";
    public static final String OUTCOME_EVICT = "evict";

    private TurMeterNames() {}
}
