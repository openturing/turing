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
package com.viglet.turing.api.system.chatanalytics;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.persona.TurPersonaRepository;
import com.viglet.turing.service.chatanalytics.TurChatAnalyticsService;
import com.viglet.turing.service.chatanalytics.TurChatAnalyticsStore;
import com.viglet.turing.service.chatanalytics.TurChatSessionFilter;
import com.viglet.turing.service.chatmemory.TurChatMemoryStore;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Read-only window into the chat analytics store. Powers the Grafana
 * dashboards via the Infinity datasource (HTTP/JSON) — staying engine-agnostic
 * so swapping {@code turing.logging.engine} between {@code mongodb} and
 * {@code redis} doesn't ripple into the dashboards.
 *
 * <p>Time bounds default to the last 7 days when omitted; {@code from}/{@code to}
 * accept ISO-8601 instants ({@code 2026-05-01T00:00:00Z}).
 *
 * <p><b>Authorization (T639/T640, §XXXVII.2):</b> these reads expose session
 * enumeration and full chat transcripts (end-user content / PII), so the whole
 * controller requires an authenticated admin — it is no longer {@code permitAll}
 * at the HTTP layer. Grafana's Infinity datasource must authenticate.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@RestController
@RequestMapping("/api/system/chat-analytics")
@Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
@Tag(name = "Chat Analytics",
        description = "Read API over the chat analytics store; consumed by Grafana via Infinity datasource.")
public class TurChatAnalyticsAPI {

    // --- S1192: extracted duplicated literals ---
    private static final String AGENT_ID = "agentId";
    private static final String PERSONA_ID = "personaId";
    private static final String BUCKET = "bucket";


    private final TurChatAnalyticsService analyticsService;
    private final TurChatMemoryStore chatMemoryStore;
    private final TurAIAgentRepository agentRepository;
    private final TurPersonaRepository personaRepository;
    private final com.viglet.turing.service.chatanalytics.TurExperimentSignificanceService significanceService;
    private final com.viglet.turing.service.chatanalytics.TurChampionChallengerService championChallengerService;
    private final com.viglet.turing.genai.flow.router.TurChatFlowRouterDecisionLog routerDecisionLog;
    private final com.viglet.turing.service.chatslots.TurChatSlotSseRegistry slotSseRegistry;
    private final com.viglet.turing.service.chatanalytics.TurToolCallTraceService toolCallTraceService;

    public TurChatAnalyticsAPI(TurChatAnalyticsService analyticsService,
            TurChatMemoryStore chatMemoryStore,
            TurAIAgentRepository agentRepository,
            TurPersonaRepository personaRepository,
            com.viglet.turing.service.chatanalytics.TurExperimentSignificanceService significanceService,
            com.viglet.turing.service.chatanalytics.TurChampionChallengerService championChallengerService,
            com.viglet.turing.genai.flow.router.TurChatFlowRouterDecisionLog routerDecisionLog,
            com.viglet.turing.service.chatslots.TurChatSlotSseRegistry slotSseRegistry,
            com.viglet.turing.service.chatanalytics.TurToolCallTraceService toolCallTraceService) {
        this.analyticsService = analyticsService;
        this.chatMemoryStore = chatMemoryStore;
        this.agentRepository = agentRepository;
        this.personaRepository = personaRepository;
        this.toolCallTraceService = toolCallTraceService;
        this.significanceService = significanceService;
        this.championChallengerService = championChallengerService;
        this.routerDecisionLog = routerDecisionLog;
        this.slotSseRegistry = slotSseRegistry;
    }

    /**
     * Decorates each row with {@code agentTitle} and {@code personaName}
     * resolved from the JPA repositories. Both lookups go through the
     * existing {@code @Cacheable} repos, so even a 100-row response only
     * costs distinct lookups (one per unique id). Names that no longer exist
     * in the catalog (deleted agent/persona) collapse to {@code null} —
     * the UI falls back to showing the id in that case.
     */
    private void enrichWithNames(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) return;
        Set<String> agentIds = new HashSet<>();
        Set<String> personaIds = new HashSet<>();
        for (Map<String, Object> row : rows) {
            collectId(row, AGENT_ID, agentIds);
            collectId(row, PERSONA_ID, personaIds);
        }
        Map<String, String> agentNames = new HashMap<>();
        for (String id : agentIds) {
            agentRepository.findById(id).ifPresent(a -> agentNames.put(id, a.getTitle()));
        }
        Map<String, String> personaNames = new HashMap<>();
        for (String id : personaIds) {
            personaRepository.findById(id).ifPresent(p -> personaNames.put(id, p.getName()));
        }
        for (Map<String, Object> row : rows) {
            String agentId = stringValue(row.get(AGENT_ID));
            String personaId = stringValue(row.get(PERSONA_ID));
            row.put("agentTitle",  agentId   == null ? null : agentNames.get(agentId));
            row.put("personaName", personaId == null ? null : personaNames.get(personaId));
        }
    }

    private void enrichWithNames(Map<String, Object> row) {
        if (row != null) enrichWithNames(List.of(row));
    }

    /**
     * Adds a {@code bucketLabel} field to each row when the aggregation /
     * scorecard dimension is {@code agentId} or {@code personaId} — so the
     * Grafana donut and bar charts can render human-readable names instead
     * of raw UUIDs while still keeping {@code bucket} (the id) for
     * filtering. Other dimensions (outcome, locale, intentLabel) leave the
     * label equal to the bucket itself.
     */
    private void enrichBucketLabels(List<Map<String, Object>> rows, String dimension) {
        if (rows == null || rows.isEmpty()) return;
        boolean isAgent   = AGENT_ID.equals(dimension);
        boolean isPersona = PERSONA_ID.equals(dimension);
        if (!isAgent && !isPersona) {
            applyRawBucketLabels(rows);
            return;
        }
        Set<String> ids = new HashSet<>();
        for (Map<String, Object> row : rows) collectId(row, BUCKET, ids);
        Map<String, String> names = resolveBucketNames(ids, isAgent);
        applyResolvedBucketLabels(rows, names);
    }

    /** Labels each row with its raw bucket value (non agent/persona dimensions). */
    private void applyRawBucketLabels(List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            Object bucket = row.get(BUCKET);
            row.put("bucketLabel", bucket == null ? "unknown" : bucket.toString());
        }
    }

    /** Resolves agent titles / persona names for the bucket ids. */
    private Map<String, String> resolveBucketNames(Set<String> ids, boolean isAgent) {
        Map<String, String> names = new HashMap<>();
        if (isAgent) {
            for (String id : ids) {
                agentRepository.findById(id).ifPresent(a -> names.put(id, a.getTitle()));
            }
        } else {
            for (String id : ids) {
                personaRepository.findById(id).ifPresent(p -> names.put(id, p.getName()));
            }
        }
        return names;
    }

    /** Labels each row with the resolved name, falling back to the id (or "unknown"). */
    private void applyResolvedBucketLabels(List<Map<String, Object>> rows, Map<String, String> names) {
        for (Map<String, Object> row : rows) {
            String id = stringValue(row.get(BUCKET));
            String name = id == null ? null : names.get(id);
            String fallbackLabel = id == null ? "unknown" : id;
            row.put("bucketLabel", name != null ? name : fallbackLabel);
        }
    }

    private static void collectId(Map<String, Object> row, String key, Set<String> sink) {
        String value = stringValue(row.get(key));
        if (value != null) sink.add(value);
    }

    private static String stringValue(Object raw) {
        if (raw == null) return null;
        String s = raw.toString().trim();
        return s.isEmpty() ? null : s;
    }

    /**
     * Reports whether the analytics store is wired and which engine backs it.
     * Used by Grafana provisioning to surface a friendly "store disabled"
     * banner instead of silently empty panels.
     */
    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> health() {
        TurChatAnalyticsStore store = analyticsService.getStore();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", store.isEnabled());
        body.put("engine", store.getEngine().name().toLowerCase());
        return body;
    }

    /**
     * Most recent sessions in the time range, sorted desc by {@code startedAt}.
     * Optional filters: agent, persona, outcome, intentLabel, goalAchieved,
     * sentiment. Filters by enrichment fields rely on the scheduled
     * {@link com.viglet.turing.service.chatanalytics.TurChatAnalyticsEnricher}
     * having processed the session — recent terminal sessions may not be
     * classified yet.
     */
    @GetMapping(value = "/sessions", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Map<String, Object>> sessions(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) String personaId,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String intentLabel,
            @RequestParam(required = false) String goalAchieved,
            @RequestParam(required = false) String sentiment,
            @RequestParam(defaultValue = "100") int limit) {
        Instant fromInstant = parseInstant(from, defaultFrom());
        Instant toInstant   = parseInstant(to,   defaultTo());
        List<Map<String, Object>> rows = analyticsService.getStore().findRecentSessions(
                fromInstant, toInstant,
                new TurChatSessionFilter(agentId, personaId, outcome, intentLabel, goalAchieved, sentiment),
                limit);
        enrichWithNames(rows);
        return rows;
    }

    /**
     * Aggregated session counts grouped by a chosen dimension. The {@code by}
     * parameter is whitelisted by the store implementation (defaults to
     * {@code outcome}).
     */
    @GetMapping(value = "/aggregate", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Map<String, Object>> aggregate(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "outcome") String by,
            @RequestParam(defaultValue = "20") int limit) {
        Instant fromInstant = parseInstant(from, defaultFrom());
        Instant toInstant   = parseInstant(to,   defaultTo());
        List<Map<String, Object>> rows = analyticsService.getStore().aggregate(
                fromInstant, toInstant, by, limit);
        enrichBucketLabels(rows, by);
        return rows;
    }

    /**
     * Time-bucketed values for a single metric — drives the trend panels in
     * Grafana. {@code metric} accepts {@code sessions}, {@code goal_achievement_rate},
     * {@code negative_sentiment_rate}, {@code avg_duration_ms}, {@code avg_tokens_out},
     * {@code tool_calls_per_session}, {@code tool_errors_per_session},
     * {@code avg_tool_latency_ms}, {@code tool_error_rate_pct} (errors / calls
     * × 100, 0-100). {@code interval} accepts {@code hour} or {@code day}.
     */
    @GetMapping(value = "/timeseries", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Map<String, Object>> timeseries(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "sessions") String metric,
            @RequestParam(defaultValue = "hour") String interval) {
        Instant fromInstant = parseInstant(from, defaultFrom());
        Instant toInstant   = parseInstant(to,   defaultTo());
        return analyticsService.getStore().timeseries(fromInstant, toInstant, metric, interval);
    }

    /**
     * T88 / §VII.10.e — per-tool latency percentiles over the time window, one
     * row per distinct tool sorted by p95 descending. Surfaces "<i>which</i>
     * tool is the p95 outlier?" — the {@code avg_tool_latency_ms} timeseries
     * metric only exposes a session-wide average across every tool, hiding a
     * single slow tool. Each row carries {@code tool}, {@code count},
     * {@code errors}, {@code errorRatePct}, {@code avgMs}, {@code minMs},
     * {@code p50Ms}, {@code p95Ms}, {@code p99Ms}, {@code maxMs}. Optional
     * {@code agentId} narrows to one agent's tool mix.
     *
     * @since 2026.3.1
     */
    @GetMapping(value = "/tool-latency", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Map<String, Object>> toolLatency(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String agentId,
            @RequestParam(defaultValue = "50") int limit) {
        Instant fromInstant = parseInstant(from, defaultFrom());
        Instant toInstant   = parseInstant(to,   defaultTo());
        return analyticsService.getStore().toolLatency(fromInstant, toInstant, agentId, limit);
    }

    /**
     * T89 / §VII.10.f — recent chat-flow <b>router</b> decisions: which
     * candidate flows competed, the keyword score each earned, the winner,
     * and the mechanism (procedural / LLM / cached-LLM) that picked it. The
     * question an operator answers after a customer complains "<i>it sent me
     * to the wrong track</i>" — pass {@code conversationId} to pull that one
     * visitor's decisions, omit it for the global recency stream.
     *
     * <p>Unlike the other endpoints here this reads an in-memory, per-process
     * ring rather than the analytics store — the durable copy is the
     * {@code [FlowRouter] decision …} structured log line. Decisions evict
     * after {@code MAX_PER_CONVERSATION}/{@code MAX_GLOBAL} or on restart.
     *
     * @since 2026.3.1
     */
    @GetMapping(value = "/router-decisions", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<com.viglet.turing.genai.flow.router.TurChatFlowRouterDecision> routerDecisions(
            @RequestParam(required = false) String conversationId,
            @RequestParam(defaultValue = "100") int limit) {
        return conversationId != null && !conversationId.isBlank()
                ? routerDecisionLog.recentForConversation(conversationId, limit)
                : routerDecisionLog.recent(limit);
    }

    /**
     * T90 / §VII.10.g — live view of the open slot-stream SSE channels on this
     * node: how many distinct {@code (conversationId, mode)} channels are open,
     * the total live connection count, and per-channel refcounts. The
     * server-side counterpart to the vanilla SDK's
     * {@code _slotsSseOpenChannelCount()} — an operator validates "<i>12 portal
     * tabs, 4 channels, refcount sane</i>" and that channels are reclaimed when
     * the last tab closes (no leaked subscriptions).
     *
     * <p>Per-process and ephemeral, same caveat as the router-decision ring:
     * the slot bus is single-node, so this counts only this node's channels and
     * resets on restart.
     *
     * @since 2026.3.1
     */
    @GetMapping(value = "/slot-sse-channels", produces = MediaType.APPLICATION_JSON_VALUE)
    public com.viglet.turing.persistence.dto.agent.TurChatSlotSseChannelsDto slotSseChannels() {
        return slotSseRegistry.snapshot();
    }

    /**
     * Scorecard grouped by a categorical dimension — same metric columns
     * (sessions, goal-achievement rate, negative+frustrated sentiment rate,
     * avg duration, top intent), pivoted by {@code dimension}:
     * <ul>
     *   <li>{@code agentId} (default) and {@code personaId} — compare
     *       agents / personas head-to-head.</li>
     *   <li>{@code experimentKey} and {@code variantLabel} — A/B test
     *       breakdown. Group by the key to see traffic across experiments;
     *       group by label to compare variants of the active one.</li>
     *   <li>{@code deviceType}, {@code locale}, {@code timezone} (T74) — the
     *       visitor cohort dimensions.</li>
     * </ul>
     *
     * <p>T74 / §VII.8.e — the optional {@code deviceType} / {@code locale} /
     * {@code timezone} / {@code experimentKey} / {@code variantLabel} params
     * are <b>cohort filters</b>: they narrow the population before grouping so
     * an operator can ask "does this variant win on <i>mobile</i>?" by setting
     * {@code dimension=variantLabel} + {@code deviceType=mobile}. Blank params
     * are ignored.
     */
    @GetMapping(value = "/scorecard", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Map<String, Object>> scorecard(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = AGENT_ID) String dimension,
            @RequestParam(required = false) String deviceType,
            @RequestParam(required = false) String locale,
            @RequestParam(required = false) String timezone,
            @RequestParam(required = false) String experimentKey,
            @RequestParam(required = false) String variantLabel,
            @RequestParam(defaultValue = "20") int limit) {
        Instant fromInstant = parseInstant(from, defaultFrom());
        Instant toInstant   = parseInstant(to,   defaultTo());
        Map<String, String> filters = new HashMap<>();
        putFilter(filters, "deviceType", deviceType);
        putFilter(filters, "locale", locale);
        putFilter(filters, "timezone", timezone);
        putFilter(filters, "experimentKey", experimentKey);
        putFilter(filters, "variantLabel", variantLabel);
        List<Map<String, Object>> rows = analyticsService.getStore().scorecard(
                fromInstant, toInstant, dimension, filters, limit);
        enrichBucketLabels(rows, dimension);
        return rows;
    }

    /** Adds a scorecard cohort filter only when the value is non-blank. */
    private static void putFilter(Map<String, String> filters, String key, String value) {
        if (value != null && !value.isBlank()) filters.put(key, value.trim());
    }

    /**
     * Statistical-significance evaluation for an A/B experiment. Pulls
     * per-variant counts from the analytics store, runs a two-proportion
     * z-test on the chosen {@code successMetric}, returns a verdict the
     * dashboard renders as a banner: "Winner: 'lucas-alumni' (p=0.013)"
     * vs "Underpowered: 47 sessions, need ≥ 100" vs "No significant
     * difference yet".
     *
     * <p>Endpoint params:
     * <ul>
     *   <li>{@code experimentKey} — the shared key declared on both variants</li>
     *   <li>{@code successMetric} — {@code GOAL_ACHIEVED} (default),
     *       {@code HANDOFF_WHATSAPP}, {@code LEAD_EMAIL_CAPTURED}</li>
     *   <li>{@code alpha} — significance threshold, default {@code 0.05}</li>
     *   <li>{@code from} / {@code to} — time window, defaults 30 days back</li>
     * </ul>
     *
     * @since 2026.2.7
     */
    @GetMapping(value = "/experiment/{experimentKey}/significance",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public com.viglet.turing.service.chatanalytics.TurExperimentSignificanceService.SignificanceResult
            experimentSignificance(
            @org.springframework.web.bind.annotation.PathVariable String experimentKey,
            @RequestParam(defaultValue = "GOAL_ACHIEVED") String successMetric,
            @RequestParam(required = false) Double alpha,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        com.viglet.turing.service.chatanalytics.TurExperimentSignificanceService.SuccessMetric metric;
        try {
            metric = com.viglet.turing.service.chatanalytics.TurExperimentSignificanceService.SuccessMetric
                    .valueOf(successMetric.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            metric = com.viglet.turing.service.chatanalytics.TurExperimentSignificanceService.SuccessMetric.GOAL_ACHIEVED;
        }
        Instant fromInstant = parseInstant(from, defaultFrom());
        Instant toInstant   = parseInstant(to,   defaultTo());
        return significanceService.evaluate(experimentKey, metric, fromInstant, toInstant, alpha);
    }

    /**
     * T71 / §VII.8.b — champion-challenger promotion. Operator-triggered
     * counterpart to {@link #experimentSignificance}: re-runs the same z-test
     * and, when a winner is declared, pins the winner to {@code trafficWeight=100}
     * and archives the losing arms ({@code trafficWeight=0} + closed assignment
     * window). When the experiment is underpowered / not yet significant the
     * call is a no-op and returns {@code applied=false} with the significance
     * service's recommendation as the reason.
     *
     * <p>This explicit action ignores the per-flow {@code autoPromote} opt-in
     * (an operator click is itself the opt-in); the flag only governs the daily
     * {@code TurChampionChallengerPromotionJob}.
     *
     * <p>Endpoint params mirror {@link #experimentSignificance}, plus:
     * <ul>
     *   <li>{@code dryRun} — when {@code true}, computes and returns the
     *       promotion plan (winner + archived labels) without persisting any
     *       traffic-weight change. Lets the UI preview "what would happen".</li>
     * </ul>
     *
     * @since 2026.3.1
     */
    @org.springframework.web.bind.annotation.PostMapping(
            value = "/experiment/{experimentKey}/promote",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @org.springframework.security.access.annotation.Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    public com.viglet.turing.service.chatanalytics.TurChampionChallengerService.PromotionResult
            promoteExperimentWinner(
            @PathVariable String experimentKey,
            @RequestParam(defaultValue = "GOAL_ACHIEVED") String successMetric,
            @RequestParam(required = false) Double alpha,
            @RequestParam(defaultValue = "false") boolean dryRun) {
        com.viglet.turing.service.chatanalytics.TurExperimentSignificanceService.SuccessMetric metric;
        try {
            metric = com.viglet.turing.service.chatanalytics.TurExperimentSignificanceService.SuccessMetric
                    .valueOf(successMetric.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            metric = com.viglet.turing.service.chatanalytics.TurExperimentSignificanceService.SuccessMetric.GOAL_ACHIEVED;
        }
        return championChallengerService.promote(experimentKey, metric, alpha, dryRun);
    }

    /**
     * Drill-down on a single session. Returns the full document (identity,
     * aggregates, and any AI-on-AI enrichment columns) or {@code 404} if the
     * conversation is unknown to the analytics store. Powers the per-session
     * detail panel in the React investigation UI.
     */
    @GetMapping(value = "/sessions/{conversationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> session(@PathVariable String conversationId) {
        Map<String, Object> session = analyticsService.getStore().findSessionById(conversationId);
        if (session == null) return ResponseEntity.notFound().build();
        enrichWithNames(session);
        return ResponseEntity.ok(session);
    }

    /**
     * Joins the analytics session document with the full chat-memory transcript
     * for the same {@code conversationId}. Two reads, one round-trip — the
     * UI doesn't need to coordinate them. {@code limit} caps the message count
     * (default 200) to keep the payload reasonable.
     */
    @GetMapping(value = "/sessions/{conversationId}/transcript",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> transcript(
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "200") int limit) {
        Map<String, Object> session = analyticsService.getStore().findSessionById(conversationId);
        if (session == null) return ResponseEntity.notFound().build();
        enrichWithNames(session);
        List<Map<String, Object>> messages = chatMemoryStore.findMessages(conversationId,
                Math.clamp(limit, 1, 1000));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("session", session);
        body.put("messages", messages);
        return ResponseEntity.ok(body);
    }

    /**
     * T427 — the tools that actually executed in a conversation, in order
     * (name, redacted arg digest, ok/err, duration). Read-only and process-local
     * (the same in-memory store the live T436 {@code tool_call} events drain
     * into), so a live event and this trace describe each invocation identically.
     *
     * <p>Backs {@code turing eval}'s {@code assert.tool_called} so the assertion
     * is exact rather than inferred from slot-audit {@code TOOL} writes; also a
     * per-conversation tool timeline for observability. Always 200 — an unknown
     * or tool-free conversation simply returns an empty list.
     */
    @GetMapping(value = "/sessions/{conversationId}/tool-calls",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<com.viglet.turing.genai.tool.TurChatToolCall>> toolCalls(
            @PathVariable String conversationId) {
        return ResponseEntity.ok(toolCallTraceService.getTrace(conversationId));
    }

    private static Instant parseInstant(String value, Instant fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            return fallback;
        }
    }

    private static Instant defaultFrom() {
        return Instant.now().minusSeconds(7L * 24 * 3600);
    }

    private static Instant defaultTo() {
        return Instant.now();
    }
}
