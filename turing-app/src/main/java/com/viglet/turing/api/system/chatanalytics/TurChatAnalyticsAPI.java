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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.service.chatanalytics.TurChatAnalyticsService;
import com.viglet.turing.service.chatanalytics.TurChatAnalyticsStore;
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
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@RestController
@RequestMapping("/api/system/chat-analytics")
@Tag(name = "Chat Analytics",
        description = "Read API over the chat analytics store; consumed by Grafana via Infinity datasource.")
public class TurChatAnalyticsAPI {

    private final TurChatAnalyticsService analyticsService;
    private final TurChatMemoryStore chatMemoryStore;

    public TurChatAnalyticsAPI(TurChatAnalyticsService analyticsService,
            TurChatMemoryStore chatMemoryStore) {
        this.analyticsService = analyticsService;
        this.chatMemoryStore = chatMemoryStore;
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
        return analyticsService.getStore().findRecentSessions(
                fromInstant, toInstant, agentId, personaId, outcome,
                intentLabel, goalAchieved, sentiment, limit);
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
        return analyticsService.getStore().aggregate(fromInstant, toInstant, by, limit);
    }

    /**
     * Time-bucketed values for a single metric — drives the trend panels in
     * Grafana. {@code metric} accepts {@code sessions}, {@code goal_achievement_rate},
     * {@code negative_sentiment_rate}, {@code avg_duration_ms}, {@code avg_tokens_out}.
     * {@code interval} accepts {@code hour} or {@code day}.
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
     * Per-agent (or per-persona) scorecard — sessions, goal-achievement rate,
     * negative+frustrated sentiment rate, avg duration, top intent. Lets us
     * compare which agent/persona drags or lifts the mean.
     */
    @GetMapping(value = "/scorecard", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Map<String, Object>> scorecard(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "agentId") String dimension,
            @RequestParam(defaultValue = "20") int limit) {
        Instant fromInstant = parseInstant(from, defaultFrom());
        Instant toInstant   = parseInstant(to,   defaultTo());
        return analyticsService.getStore().scorecard(fromInstant, toInstant, dimension, limit);
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
        return session == null
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(session);
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
        List<Map<String, Object>> messages = chatMemoryStore.findMessages(conversationId,
                Math.max(1, Math.min(limit, 1000)));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("session", session);
        body.put("messages", messages);
        return ResponseEntity.ok(body);
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
