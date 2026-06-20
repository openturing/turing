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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.conversions.Bson;

import com.mongodb.client.AggregateIterable;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;

import lombok.extern.slf4j.Slf4j;

/**
 * MongoDB-backed analytics store. One document per {@code conversationId};
 * upserts use {@code $setOnInsert} for start-only fields ({@code startedAt})
 * and {@code $set} for everything else, so the end record fully merges with
 * the start record without erasing the start timestamp.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@Slf4j
public class TurMongoChatAnalyticsStore implements TurChatAnalyticsStore {

    // --- S1192: extracted duplicated literals ---
    private static final String STARTED_AT = "startedAt";
    private static final String FIRST_USER_MESSAGE = "firstUserMessage";
    private static final String AGENT_ID = "agentId";
    private static final String PERSONA_ID = "personaId";
    private static final String LLM_INSTANCE_ID = "llmInstanceId";
    private static final String EMBEDDING_MODEL_ID = "embeddingModelId";
    private static final String STORE_INSTANCE_ID = "storeInstanceId";
    private static final String USER_ID = "userId";
    private static final String LOCALE = "locale";
    private static final String TIMEZONE = "timezone";
    private static final String DEVICE_TYPE = "deviceType";
    private static final String OUTCOME = "outcome";
    private static final String TOTAL_TURNS = "totalTurns";
    private static final String TOTAL_TOKENS_IN = "totalTokensIn";
    private static final String TOTAL_TOKENS_OUT = "totalTokensOut";
    private static final String TOTAL_TOOL_CALLS = "totalToolCalls";
    private static final String TOTAL_TOOL_ERRORS = "totalToolErrors";
    private static final String TOTAL_TOOL_LATENCY_MS = "totalToolLatencyMs";
    private static final String EXPERIMENT_KEY = "experimentKey";
    private static final String VARIANT_LABEL = "variantLabel";
    private static final String PARENT_CONVERSATION_ID = "parentConversationId";
    private static final String DURATION_MS = "durationMs";
    private static final String COMPLETED_AT = "completedAt";
    private static final String INTENT_LABEL = "intentLabel";
    private static final String GOAL_ACHIEVED = "goalAchieved";
    private static final String SENTIMENT = "sentiment";
    private static final String DOLLAR_MATCH = "$match";
    private static final String DOLLAR_GROUP = "$group";
    private static final String DOLLAR_IF_NULL = "$ifNull";
    private static final String UNKNOWN = "unknown";
    private static final String COUNT = "count";
    private static final String DOLLAR_SORT = "$sort";
    private static final String BUCKET = "bucket";
    private static final String PROCESSED_AT = "processedAt";
    private static final String INTENT_CONFIDENCE = "intentConfidence";
    private static final String GOAL_SUMMARY = "goalSummary";
    private static final String KEY_TERMS = "keyTerms";
    private static final String TOTAL = "total";
    private static final String METRIC_SUM = "metricSum";
    private static final String GOAL_ACHIEVEMENT_RATE = "goal_achievement_rate";
    private static final String DOLLAR_GOAL_ACHIEVED = "$goalAchieved";
    private static final String NEGATIVE_SENTIMENT_RATE = "negative_sentiment_rate";
    private static final String DOLLAR_COND = "$cond";
    private static final String DOLLAR_SENTIMENT = "$sentiment";
    private static final String METRIC_AVG = "metricAvg";
    private static final String AVG_DURATION_MS = "avg_duration_ms";
    private static final String AVG_TOKENS_OUT = "avg_tokens_out";
    private static final String TOOL_CALLS_PER_SESSION = "tool_calls_per_session";
    private static final String TOOL_ERRORS_PER_SESSION = "tool_errors_per_session";
    private static final String AVG_TOOL_LATENCY_MS = "avg_tool_latency_ms";
    private static final String TOOL_ERROR_RATE_PCT = "tool_error_rate_pct";
    private static final String DURATION_SUM = "durationSum";
    private static final String PROCESSED_COUNT = "processedCount";
    private static final String ACHIEVED_SCORE = "achievedScore";
    private static final String NEGATIVE_COUNT = "negativeCount";
    private static final String SESSIONS = "sessions";
    private static final String TOP_INTENT = "topIntent";


    private static final String FIELD_SENTIMENT_TRAJECTORY = "sentimentTrajectory";
    private static final String FIELD_TOOL_LATENCIES = "toolLatencies";

    private final MongoClient client;
    private final String databaseName;
    private final String collectionName;

    public TurMongoChatAnalyticsStore(String mongoUri, String databaseName, String collectionName) {
        this.client = MongoClients.create(mongoUri);
        this.databaseName = databaseName;
        this.collectionName = collectionName;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public TurChatAnalyticsEngine getEngine() {
        return TurChatAnalyticsEngine.MONGODB;
    }

    @Override
    public void upsertSession(TurChatSessionEvent event) {
        try {
            MongoCollection<Document> collection = collection();
            List<Bson> setOnInsert = new ArrayList<>();
            List<Bson> set = new ArrayList<>();

            // start-only fields — never overwritten on subsequent upserts
            if (event.startedAt() != null) {
                setOnInsert.add(Updates.setOnInsert(STARTED_AT, Date.from(event.startedAt())));
            }
            if (StringUtils.isNotBlank(event.firstUserMessage())) {
                setOnInsert.add(Updates.setOnInsert(FIRST_USER_MESSAGE, event.firstUserMessage()));
            }

            // identity fields — set once but allowed to refine if missing initially
            putIfNotBlank(set, AGENT_ID, event.agentId());
            putIfNotBlank(set, PERSONA_ID, event.personaId());
            putIfNotBlank(set, LLM_INSTANCE_ID, event.llmInstanceId());
            putIfNotBlank(set, EMBEDDING_MODEL_ID, event.embeddingModelId());
            putIfNotBlank(set, STORE_INSTANCE_ID, event.storeInstanceId());
            putIfNotBlank(set, USER_ID, event.userId());
            putIfNotBlank(set, LOCALE, event.locale());
            // T74 — visitor cohort (timezone / device class), set once on start.
            putIfNotBlank(set, TIMEZONE, event.timezone());
            putIfNotBlank(set, DEVICE_TYPE, event.deviceType());

            // outcome / aggregates — overwritten on every upsert
            if (event.outcome() != null) {
                set.add(Updates.set(OUTCOME, event.outcome().name()));
            }
            set.add(Updates.set(TOTAL_TURNS, event.totalTurns()));
            set.add(Updates.set(TOTAL_TOKENS_IN, event.totalTokensIn()));
            set.add(Updates.set(TOTAL_TOKENS_OUT, event.totalTokensOut()));
            set.add(Updates.set(TOTAL_TOOL_CALLS, event.totalToolCalls()));
            set.add(Updates.set(TOTAL_TOOL_ERRORS, event.totalToolErrors()));
            set.add(Updates.set(TOTAL_TOOL_LATENCY_MS, event.totalToolLatencyMs()));
            putIfNotBlank(set, EXPERIMENT_KEY, event.experimentKey());
            putIfNotBlank(set, VARIANT_LABEL, event.variantLabel());
            // T110 — child-session linkage written once on start, preserved on end.
            putIfNotBlank(set, PARENT_CONVERSATION_ID, event.parentConversationId());
            // T88 — raw per-tool latency samples; only written when present (the
            // end record), so a re-upsert never wipes a populated array with the
            // empty start one.
            if (event.toolLatencies() != null && !event.toolLatencies().isEmpty()) {
                List<Document> samples = new ArrayList<>(event.toolLatencies().size());
                for (TurToolLatencySample s : event.toolLatencies()) {
                    samples.add(new Document("tool", s.tool())
                            .append("latencyMs", s.latencyMs())
                            .append("success", s.success()));
                }
                set.add(Updates.set(FIELD_TOOL_LATENCIES, samples));
            }
            set.add(Updates.set(DURATION_MS, event.durationMs()));
            set.add(Updates.set("updatedAt", new Date()));
            if (event.completedAt() != null) {
                set.add(Updates.set(COMPLETED_AT, Date.from(event.completedAt())));
            }

            List<Bson> all = new ArrayList<>(setOnInsert);
            all.addAll(set);
            collection.updateOne(
                    Filters.eq("_id", event.conversationId()),
                    Updates.combine(all),
                    new UpdateOptions().upsert(true));
        } catch (Exception e) {
            log.warn("Failed to upsert chat session analytics for conversation {}: {}",
                    event.conversationId(), e.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> findRecentSessions(Instant from, Instant to,
            TurChatSessionFilter filter, int limit) {
        String agentId = filter.agentId();
        String personaId = filter.personaId();
        String outcome = filter.outcome();
        String intentLabel = filter.intentLabel();
        String goalAchieved = filter.goalAchieved();
        String sentiment = filter.sentiment();
        try {
            List<Bson> filters = new ArrayList<>();
            filters.add(Filters.gte(STARTED_AT, Date.from(from)));
            filters.add(Filters.lte(STARTED_AT, Date.from(to)));
            if (StringUtils.isNotBlank(agentId))      filters.add(Filters.eq(AGENT_ID, agentId));
            if (StringUtils.isNotBlank(personaId))    filters.add(Filters.eq(PERSONA_ID, personaId));
            if (StringUtils.isNotBlank(outcome))      filters.add(Filters.eq(OUTCOME, outcome));
            if (StringUtils.isNotBlank(intentLabel))  filters.add(Filters.eq(INTENT_LABEL, intentLabel));
            if (StringUtils.isNotBlank(goalAchieved)) filters.add(Filters.eq(GOAL_ACHIEVED, goalAchieved));
            if (StringUtils.isNotBlank(sentiment))    filters.add(Filters.eq(SENTIMENT, sentiment));

            FindIterable<Document> cursor = collection()
                    .find(Filters.and(filters))
                    .sort(Sorts.descending(STARTED_AT))
                    .limit(Math.clamp(limit, 1, 1000));
            List<Map<String, Object>> result = new ArrayList<>();
            for (Document doc : cursor) result.add(toRow(doc));
            return result;
        } catch (Exception e) {
            log.warn("Chat analytics findRecentSessions failed: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<Map<String, Object>> aggregate(Instant from, Instant to, String groupBy, int maxGroups) {
        try {
            String field = sanitizeFieldName(groupBy);
            List<Bson> pipeline = List.of(
                    new Document(DOLLAR_MATCH, new Document()
                            .append(STARTED_AT, new Document()
                                    .append("$gte", Date.from(from))
                                    .append("$lte", Date.from(to)))),
                    new Document(DOLLAR_GROUP, new Document()
                            .append("_id",
                                    new Document(DOLLAR_IF_NULL, List.of("$" + field, UNKNOWN)))
                            .append(COUNT, new Document("$sum", 1))),
                    new Document(DOLLAR_SORT, new Document(COUNT, -1)),
                    new Document("$limit", Math.clamp(maxGroups, 1, 100)));
            AggregateIterable<Document> cursor = collection().aggregate(pipeline);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Document doc : cursor) {
                Map<String, Object> row = new LinkedHashMap<>();
                Object id = doc.get("_id");
                row.put(BUCKET, id == null ? UNKNOWN : String.valueOf(id));
                row.put(COUNT, doc.getInteger(COUNT, 0));
                result.add(row);
            }
            return result;
        } catch (Exception e) {
            log.warn("Chat analytics aggregate({}) failed: {}", groupBy, e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<Map<String, Object>> findUnenrichedSessions(int limit) {
        try {
            List<Bson> filters = new ArrayList<>();
            // Outcome must be set and not IN_PROGRESS — i.e. session terminated.
            filters.add(Filters.exists(OUTCOME, true));
            filters.add(Filters.ne(OUTCOME, "IN_PROGRESS"));
            // No processedAt yet → not enriched.
            filters.add(Filters.exists(PROCESSED_AT, false));
            int cap = Math.clamp(limit, 1, 200);
            com.mongodb.client.FindIterable<Document> cursor = collection()
                    .find(Filters.and(filters))
                    .sort(com.mongodb.client.model.Sorts.ascending(STARTED_AT))
                    .limit(cap);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Document doc : cursor) result.add(toRow(doc));
            return result;
        } catch (Exception e) {
            log.warn("Chat analytics findUnenrichedSessions failed: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public void enrichSession(String conversationId, TurChatSessionEnrichment enrichment) {
        if (conversationId == null || conversationId.isBlank() || enrichment == null) return;
        try {
            List<Bson> set = new ArrayList<>();
            if (enrichment.intentLabel() != null) {
                set.add(Updates.set(INTENT_LABEL, enrichment.intentLabel().name()));
            }
            set.add(Updates.set(INTENT_CONFIDENCE, enrichment.intentConfidence()));
            if (StringUtils.isNotBlank(enrichment.goalSummary())) {
                set.add(Updates.set(GOAL_SUMMARY, enrichment.goalSummary()));
            }
            if (enrichment.goalAchieved() != null) {
                set.add(Updates.set(GOAL_ACHIEVED, enrichment.goalAchieved().name()));
            }
            if (enrichment.sentiment() != null) {
                set.add(Updates.set(SENTIMENT, enrichment.sentiment().name()));
            }
            if (enrichment.keyTerms() != null) {
                set.add(Updates.set(KEY_TERMS, enrichment.keyTerms()));
            }
            // T87 — per-turn sentiment trajectory persisted as an array of enum
            // names; absent when the catalog-driven classifiers ran (empty list).
            if (enrichment.sentimentTrajectory() != null && !enrichment.sentimentTrajectory().isEmpty()) {
                set.add(Updates.set(FIELD_SENTIMENT_TRAJECTORY,
                        enrichment.sentimentTrajectory().stream().map(Enum::name).toList()));
            }
            Date stamp = Date.from(enrichment.processedAt() == null
                    ? java.time.Instant.now()
                    : enrichment.processedAt());
            set.add(Updates.set(PROCESSED_AT, stamp));

            collection().updateOne(
                    Filters.eq("_id", conversationId),
                    Updates.combine(set));
        } catch (Exception e) {
            log.warn("Failed to enrich chat session {}: {}", conversationId, e.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> timeseries(Instant from, Instant to, String metric, String interval) {
        try {
            String unit = "day".equals(interval) ? "day" : "hour";
            String validatedMetric = sanitizeMetric(metric);
            boolean rateMetric = isRateMetric(validatedMetric);

            List<Bson> pipeline = new ArrayList<>();
            pipeline.add(new Document(DOLLAR_MATCH, new Document()
                    .append(STARTED_AT, new Document()
                            .append("$gte", Date.from(from))
                            .append("$lte", Date.from(to)))));
            // Rate metrics only count sessions the enricher has touched —
            // otherwise a backlog of unenriched data drags the rate to 0.
            if (rateMetric) {
                pipeline.add(new Document(DOLLAR_MATCH,
                        new Document(PROCESSED_AT, new Document("$exists", true))));
            }

            Document group = new Document("_id",
                    new Document("$dateTrunc", new Document()
                            .append("date", "$startedAt")
                            .append("unit", unit)));
            group.append(TOTAL, new Document("$sum", 1));
            switch (validatedMetric) {
                case GOAL_ACHIEVEMENT_RATE -> group.append(METRIC_SUM, new Document("$sum",
                        new Document("$switch", new Document()
                                .append("branches", List.of(
                                        new Document("case",
                                                new Document("$eq", List.of(DOLLAR_GOAL_ACHIEVED, "YES")))
                                                .append("then", 1),
                                        new Document("case",
                                                new Document("$eq", List.of(DOLLAR_GOAL_ACHIEVED, "PARTIAL")))
                                                .append("then", 0.5)))
                                .append("default", 0))));
                case NEGATIVE_SENTIMENT_RATE -> group.append(METRIC_SUM, new Document("$sum",
                        new Document(DOLLAR_COND, new Document()
                                .append("if", new Document("$or", List.of(
                                        new Document("$eq", List.of(DOLLAR_SENTIMENT, "NEGATIVE")),
                                        new Document("$eq", List.of(DOLLAR_SENTIMENT, "FRUSTRATED")))))
                                .append("then", 1).append("else", 0))));
                case AVG_DURATION_MS        -> group.append(METRIC_AVG, new Document("$avg", "$durationMs"));
                case AVG_TOKENS_OUT         -> group.append(METRIC_AVG, new Document("$avg", "$totalTokensOut"));
                // Per-session averages for tool metrics — same family as
                // avg_X above. Sessions that never invoked a tool contribute
                // 0 to the numerator and 1 to the denominator (sessions),
                // which gives "avg per session" not "avg per tool call".
                case TOOL_CALLS_PER_SESSION  -> group.append(METRIC_AVG, new Document("$avg", "$totalToolCalls"));
                case TOOL_ERRORS_PER_SESSION -> group.append(METRIC_AVG, new Document("$avg", "$totalToolErrors"));
                case AVG_TOOL_LATENCY_MS     -> group.append(METRIC_AVG, new Document("$avg", "$totalToolLatencyMs"));
                // Errors / calls × 100 — needs two sums in the bucket because
                // the denominator (tool calls) varies across sessions. Sessions
                // with zero tool calls contribute 0 to both sums; division by
                // zero is guarded in computeValue. Returned as 0-100 so the
                // dashboard renders directly as a percent without scaling.
                case TOOL_ERROR_RATE_PCT -> {
                    group.append(METRIC_SUM,       new Document("$sum", "$totalToolErrors"));
                    group.append("metricDivisor",   new Document("$sum", "$totalToolCalls"));
                }
                default -> { /* sessions: total alone is enough */ }
            }
            pipeline.add(new Document(DOLLAR_GROUP, group));
            pipeline.add(new Document(DOLLAR_SORT, new Document("_id", 1)));

            AggregateIterable<Document> cursor = collection().aggregate(pipeline);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Document doc : cursor) {
                Map<String, Object> row = new LinkedHashMap<>();
                Date bucket = doc.getDate("_id");
                row.put(BUCKET, bucket == null ? null : bucket.toInstant().toString());
                int total = doc.getInteger(TOTAL, 0);
                row.put(TOTAL, total);
                row.put("value", computeValue(doc, validatedMetric, total));
                result.add(row);
            }
            return result;
        } catch (Exception e) {
            log.warn("Chat analytics timeseries({}, {}) failed: {}", metric, interval, e.getMessage());
            return List.of();
        }
    }

    /**
     * Builds the {@code $match} doc: the time range plus each whitelisted,
     * non-blank cohort/identity filter (T74 — narrows before grouping).
     */
    private Document buildScorecardMatch(Instant from, Instant to, Map<String, String> filters) {
        Document match = new Document()
                .append(STARTED_AT, new Document()
                        .append("$gte", Date.from(from))
                        .append("$lte", Date.from(to)));
        if (filters != null) {
            for (Map.Entry<String, String> e : filters.entrySet()) {
                if (TurChatAnalyticsStore.SCORECARD_FILTERABLE_FIELDS.contains(e.getKey())
                        && StringUtils.isNotBlank(e.getValue())) {
                    match.append(e.getKey(), e.getValue());
                }
            }
        }
        return match;
    }

    /** Renders one aggregated dimension doc into a scorecard row (rates + top intent). */
    private Map<String, Object> toScorecardRow(Document doc) {
        Map<String, Object> row = new LinkedHashMap<>();
        Object dim = doc.get("_id");
        row.put(BUCKET, dim == null ? UNKNOWN : String.valueOf(dim));
        int sessions = doc.getInteger(SESSIONS, 0);
        long durationSum = numberAsLong(doc.get(DURATION_SUM));
        int processed = doc.getInteger(PROCESSED_COUNT, 0);
        double achieved = numberAsDouble(doc.get(ACHIEVED_SCORE));
        int negative = doc.getInteger(NEGATIVE_COUNT, 0);
        row.put(SESSIONS, sessions);
        row.put("avgDurationMs", sessions > 0 ? durationSum / (double) sessions : 0d);
        row.put("goalAchievedRate", processed > 0 ? achieved / processed : 0d);
        row.put("negativeRate",     processed > 0 ? negative / (double) processed : 0d);
        String topIntent = doc.getString(TOP_INTENT);
        row.put(TOP_INTENT, topIntent == null ? UNKNOWN : topIntent);
        return row;
    }

    @Override
    public List<Map<String, Object>> scorecard(Instant from, Instant to, String dimension,
            Map<String, String> filters, int maxRows) {
        try {
            // Whitelisted group field (identity / experiment / T74 cohort);
            // anything outside the list falls back to agentId. Shared with the
            // Redis store via the interface so both pivot identically.
            String field = TurChatAnalyticsStore.scorecardDimensionField(dimension);
            int cap = Math.clamp(maxRows, 1, 100);

            // T74 — narrow the population BEFORE grouping. The match merges the
            // time-range with each whitelisted cohort/identity filter so
            // "variant X on mobile" is one server-side aggregation, not a
            // client-side post-filter.
            Document match = buildScorecardMatch(from, to, filters);

            List<Bson> pipeline = new ArrayList<>();
            pipeline.add(new Document(DOLLAR_MATCH, match));

            // First pass: per (dimension, intentLabel) counts, plus session
            // aggregates. We pull intent counts in the same group so the
            // second stage can pick the top intent without a second query.
            Document firstGroupId = new Document()
                    .append("dim", new Document(DOLLAR_IF_NULL, List.of("$" + field, UNKNOWN)))
                    .append("intent", "$intentLabel");
            pipeline.add(new Document(DOLLAR_GROUP, new Document()
                    .append("_id", firstGroupId)
                    .append(COUNT, new Document("$sum", 1))
                    .append(DURATION_SUM, new Document("$sum", "$durationMs"))
                    .append(PROCESSED_COUNT, new Document("$sum",
                            new Document(DOLLAR_COND, new Document()
                                    .append("if", new Document(DOLLAR_IF_NULL, List.of("$processedAt", false)))
                                    .append("then", 1).append("else", 0))))
                    .append(ACHIEVED_SCORE, new Document("$sum",
                            new Document("$switch", new Document()
                                    .append("branches", List.of(
                                            new Document("case",
                                                    new Document("$eq", List.of(DOLLAR_GOAL_ACHIEVED, "YES")))
                                                    .append("then", 1),
                                            new Document("case",
                                                    new Document("$eq", List.of(DOLLAR_GOAL_ACHIEVED, "PARTIAL")))
                                                    .append("then", 0.5)))
                                    .append("default", 0))))
                    .append(NEGATIVE_COUNT, new Document("$sum",
                            new Document(DOLLAR_COND, new Document()
                                    .append("if", new Document("$or", List.of(
                                            new Document("$eq", List.of(DOLLAR_SENTIMENT, "NEGATIVE")),
                                            new Document("$eq", List.of(DOLLAR_SENTIMENT, "FRUSTRATED")))))
                                    .append("then", 1).append("else", 0))))));

            // Second pass: collapse intents into the top-1 per dimension.
            pipeline.add(new Document(DOLLAR_SORT, new Document(COUNT, -1)));
            pipeline.add(new Document(DOLLAR_GROUP, new Document()
                    .append("_id", "$_id.dim")
                    .append(SESSIONS,       new Document("$sum", "$count"))
                    .append(DURATION_SUM,    new Document("$sum", "$durationSum"))
                    .append(PROCESSED_COUNT, new Document("$sum", "$processedCount"))
                    .append(ACHIEVED_SCORE,  new Document("$sum", "$achievedScore"))
                    .append(NEGATIVE_COUNT,  new Document("$sum", "$negativeCount"))
                    .append(TOP_INTENT,      new Document("$first", "$_id.intent"))));

            pipeline.add(new Document(DOLLAR_SORT, new Document(SESSIONS, -1)));
            pipeline.add(new Document("$limit", cap));

            AggregateIterable<Document> cursor = collection().aggregate(pipeline);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Document doc : cursor) {
                result.add(toScorecardRow(doc));
            }
            return result;
        } catch (Exception e) {
            log.warn("Chat analytics scorecard({}) failed: {}", dimension, e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<Map<String, Object>> toolLatency(Instant from, Instant to, String agentId, int maxTools) {
        try {
            List<Bson> filters = new ArrayList<>();
            filters.add(Filters.gte(STARTED_AT, Date.from(from)));
            filters.add(Filters.lte(STARTED_AT, Date.from(to)));
            if (StringUtils.isNotBlank(agentId)) filters.add(Filters.eq(AGENT_ID, agentId));
            // Only sessions that actually captured samples carry the array;
            // skip the rest server-side so the in-process pool stays small.
            filters.add(Filters.exists(FIELD_TOOL_LATENCIES, true));

            FindIterable<Document> cursor = collection()
                    .find(Filters.and(filters))
                    .projection(new Document(FIELD_TOOL_LATENCIES, 1));

            TurToolLatencyAccumulator acc = new TurToolLatencyAccumulator();
            for (Document doc : cursor) {
                List<Document> samples = doc.getList(FIELD_TOOL_LATENCIES, Document.class);
                if (samples == null) continue;
                for (Document s : samples) {
                    acc.add(s.getString("tool"),
                            numberAsLong(s.get("latencyMs")),
                            Boolean.TRUE.equals(s.getBoolean("success")));
                }
            }
            return acc.toRows(maxTools);
        } catch (Exception e) {
            log.warn("Chat analytics toolLatency failed: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    @SuppressWarnings("java:S1168") // null = not found, distinct from empty map
    public Map<String, Object> findSessionById(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) return null;
        try {
            Document doc = collection().find(Filters.eq("_id", conversationId)).first();
            return doc == null ? null : toRow(doc);
        } catch (Exception e) {
            log.warn("Chat analytics findSessionById({}) failed: {}", conversationId, e.getMessage());
            return null;
        }
    }

    @Override
    public long purgeOlderThan(Instant threshold) {
        if (threshold == null) return 0L;
        try {
            return collection().deleteMany(
                    Filters.lt(STARTED_AT, Date.from(threshold))).getDeletedCount();
        } catch (Exception e) {
            log.warn("Chat analytics purgeOlderThan({}) failed: {}", threshold, e.getMessage());
            return 0L;
        }
    }

    @Override
    public void ensureIndexes() {
        try {
            MongoCollection<Document> c = collection();
            // Most reads sort or filter by startedAt; covers timeseries match,
            // recent-sessions list, retention scan, scorecard match.
            c.createIndex(new Document(STARTED_AT, -1));
            // Enricher walks "outcome != IN_PROGRESS && processedAt missing"
            // ascending by startedAt. A sparse partial index on processedAt
            // would be nicer but every Mongo driver version supports plain.
            c.createIndex(new Document(PROCESSED_AT, 1));
            // Frequent filter combos in the dashboard.
            c.createIndex(new Document().append(AGENT_ID, 1).append(STARTED_AT, -1));
            c.createIndex(new Document().append(PERSONA_ID, 1).append(STARTED_AT, -1));
            c.createIndex(new Document(OUTCOME, 1));
            // T74 — cohort scorecard filters/group-bys. deviceType has very low
            // cardinality (5 values) but pairs with startedAt for the windowed
            // scan; timezone/locale similar.
            c.createIndex(new Document().append(DEVICE_TYPE, 1).append(STARTED_AT, -1));
            c.createIndex(new Document().append(LOCALE, 1).append(STARTED_AT, -1));
            c.createIndex(new Document().append(TIMEZONE, 1).append(STARTED_AT, -1));
        } catch (Exception e) {
            log.warn("Chat analytics ensureIndexes failed: {}", e.getMessage());
        }
    }

    public void close() {
        if (client != null) client.close();
    }

    // ── helpers ──

    private MongoCollection<Document> collection() {
        MongoDatabase database = client.getDatabase(databaseName);
        return database.getCollection(collectionName);
    }

    private static void putIfNotBlank(List<Bson> set, String key, String value) {
        if (StringUtils.isNotBlank(value)) set.add(Updates.set(key, value));
    }

    /**
     * Whitelist the field name used by {@link #aggregate} — Mongo expressions
     * containing user-supplied $-prefixed strings would otherwise be a small
     * injection vector.
     */
    private static String sanitizeFieldName(String groupBy) {
        if (groupBy == null) return OUTCOME;
        String trimmed = groupBy.trim();
        return switch (trimmed) {
            case AGENT_ID, PERSONA_ID, LLM_INSTANCE_ID, EMBEDDING_MODEL_ID,
                 STORE_INSTANCE_ID, OUTCOME, LOCALE, USER_ID,
                 INTENT_LABEL, GOAL_ACHIEVED, SENTIMENT,
                 // T74 cohort + experiment dimensions.
                 TIMEZONE, DEVICE_TYPE, EXPERIMENT_KEY, VARIANT_LABEL -> trimmed;
            default -> OUTCOME;
        };
    }

    private static String sanitizeMetric(String metric) {
        if (metric == null) return SESSIONS;
        return switch (metric.trim()) {
            case SESSIONS,
                 GOAL_ACHIEVEMENT_RATE,
                 NEGATIVE_SENTIMENT_RATE,
                 AVG_DURATION_MS,
                 AVG_TOKENS_OUT,
                 TOOL_CALLS_PER_SESSION,
                 TOOL_ERRORS_PER_SESSION,
                 AVG_TOOL_LATENCY_MS,
                 TOOL_ERROR_RATE_PCT -> metric.trim();
            default -> SESSIONS;
        };
    }

    private static boolean isRateMetric(String metric) {
        return GOAL_ACHIEVEMENT_RATE.equals(metric)
                || NEGATIVE_SENTIMENT_RATE.equals(metric);
    }

    private static Object computeValue(Document doc, String metric, int total) {
        return switch (metric) {
            case GOAL_ACHIEVEMENT_RATE, NEGATIVE_SENTIMENT_RATE ->
                    total > 0 ? numberAsDouble(doc.get(METRIC_SUM)) / total : 0d;
            case AVG_DURATION_MS, AVG_TOKENS_OUT,
                 TOOL_CALLS_PER_SESSION, TOOL_ERRORS_PER_SESSION,
                 AVG_TOOL_LATENCY_MS ->
                    numberAsDouble(doc.get(METRIC_AVG));
            case TOOL_ERROR_RATE_PCT -> {
                // errors / calls × 100 — guard divide-by-zero when no session
                // in the bucket invoked any tool (calls=0). Returns 0d so the
                // chart shows a clean baseline instead of NaN.
                double divisor = numberAsDouble(doc.get("metricDivisor"));
                yield divisor > 0 ? numberAsDouble(doc.get(METRIC_SUM)) * 100d / divisor : 0d;
            }
            default -> total; // SESSIONS — count is the value
        };
    }

    private static long numberAsLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        return 0L;
    }

    private static double numberAsDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        return 0d;
    }

    private static Map<String, Object> toRow(Document doc) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("conversationId",   doc.get("_id"));
        row.put(AGENT_ID,          doc.getString(AGENT_ID));
        row.put(PERSONA_ID,        doc.getString(PERSONA_ID));
        row.put(LLM_INSTANCE_ID,    doc.getString(LLM_INSTANCE_ID));
        row.put(EMBEDDING_MODEL_ID, doc.getString(EMBEDDING_MODEL_ID));
        row.put(STORE_INSTANCE_ID,  doc.getString(STORE_INSTANCE_ID));
        row.put(USER_ID,           doc.getString(USER_ID));
        row.put(LOCALE,           doc.getString(LOCALE));
        // T74 — visitor cohort dimensions.
        row.put(TIMEZONE,         doc.getString(TIMEZONE));
        row.put(DEVICE_TYPE,       doc.getString(DEVICE_TYPE));
        row.put(OUTCOME,          doc.getString(OUTCOME));
        row.put(STARTED_AT,        toIsoString(doc.getDate(STARTED_AT)));
        row.put(COMPLETED_AT,      toIsoString(doc.getDate(COMPLETED_AT)));
        row.put(TOTAL_TURNS,         doc.get(TOTAL_TURNS, 0));
        row.put(TOTAL_TOKENS_IN,      doc.get(TOTAL_TOKENS_IN, 0L));
        row.put(TOTAL_TOKENS_OUT,     doc.get(TOTAL_TOKENS_OUT, 0L));
        row.put(TOTAL_TOOL_CALLS,     doc.get(TOTAL_TOOL_CALLS, 0));
        row.put(TOTAL_TOOL_ERRORS,    doc.get(TOTAL_TOOL_ERRORS, 0));
        row.put(TOTAL_TOOL_LATENCY_MS, doc.get(TOTAL_TOOL_LATENCY_MS, 0L));
        row.put(EXPERIMENT_KEY,      doc.getString(EXPERIMENT_KEY));
        row.put(VARIANT_LABEL,       doc.getString(VARIANT_LABEL));
        // T110 — child-session linkage to parent conversation.
        row.put(PARENT_CONVERSATION_ID, doc.getString(PARENT_CONVERSATION_ID));
        row.put(DURATION_MS,         doc.get(DURATION_MS, 0L));
        row.put(FIRST_USER_MESSAGE, doc.getString(FIRST_USER_MESSAGE));
        // Enrichment columns — null until the scheduled enricher runs.
        row.put(INTENT_LABEL,      doc.getString(INTENT_LABEL));
        row.put(INTENT_CONFIDENCE, doc.get(INTENT_CONFIDENCE));
        row.put(GOAL_SUMMARY,      doc.getString(GOAL_SUMMARY));
        row.put(GOAL_ACHIEVED,     doc.getString(GOAL_ACHIEVED));
        row.put(SENTIMENT,        doc.getString(SENTIMENT));
        row.put(KEY_TERMS,         doc.get(KEY_TERMS));
        // T87 — per-turn sentiment trajectory (array of enum names), null until enriched.
        row.put(FIELD_SENTIMENT_TRAJECTORY, doc.get(FIELD_SENTIMENT_TRAJECTORY));
        row.put(PROCESSED_AT,      toIsoString(doc.getDate(PROCESSED_AT)));
        return row;
    }

    private static String toIsoString(Date date) {
        return date == null ? null : date.toInstant().toString();
    }
}
