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
                setOnInsert.add(Updates.setOnInsert("startedAt", Date.from(event.startedAt())));
            }
            if (StringUtils.isNotBlank(event.firstUserMessage())) {
                setOnInsert.add(Updates.setOnInsert("firstUserMessage", event.firstUserMessage()));
            }

            // identity fields — set once but allowed to refine if missing initially
            putIfNotBlank(set, "agentId", event.agentId());
            putIfNotBlank(set, "personaId", event.personaId());
            putIfNotBlank(set, "llmInstanceId", event.llmInstanceId());
            putIfNotBlank(set, "embeddingModelId", event.embeddingModelId());
            putIfNotBlank(set, "storeInstanceId", event.storeInstanceId());
            putIfNotBlank(set, "userId", event.userId());
            putIfNotBlank(set, "locale", event.locale());
            // T74 — visitor cohort (timezone / device class), set once on start.
            putIfNotBlank(set, "timezone", event.timezone());
            putIfNotBlank(set, "deviceType", event.deviceType());

            // outcome / aggregates — overwritten on every upsert
            if (event.outcome() != null) {
                set.add(Updates.set("outcome", event.outcome().name()));
            }
            set.add(Updates.set("totalTurns", event.totalTurns()));
            set.add(Updates.set("totalTokensIn", event.totalTokensIn()));
            set.add(Updates.set("totalTokensOut", event.totalTokensOut()));
            set.add(Updates.set("totalToolCalls", event.totalToolCalls()));
            set.add(Updates.set("totalToolErrors", event.totalToolErrors()));
            set.add(Updates.set("totalToolLatencyMs", event.totalToolLatencyMs()));
            putIfNotBlank(set, "experimentKey", event.experimentKey());
            putIfNotBlank(set, "variantLabel", event.variantLabel());
            // T110 — child-session linkage written once on start, preserved on end.
            putIfNotBlank(set, "parentConversationId", event.parentConversationId());
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
            set.add(Updates.set("durationMs", event.durationMs()));
            set.add(Updates.set("updatedAt", new Date()));
            if (event.completedAt() != null) {
                set.add(Updates.set("completedAt", Date.from(event.completedAt())));
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
            String agentId, String personaId, String outcome,
            String intentLabel, String goalAchieved, String sentiment,
            int limit) {
        try {
            List<Bson> filters = new ArrayList<>();
            filters.add(Filters.gte("startedAt", Date.from(from)));
            filters.add(Filters.lte("startedAt", Date.from(to)));
            if (StringUtils.isNotBlank(agentId))      filters.add(Filters.eq("agentId", agentId));
            if (StringUtils.isNotBlank(personaId))    filters.add(Filters.eq("personaId", personaId));
            if (StringUtils.isNotBlank(outcome))      filters.add(Filters.eq("outcome", outcome));
            if (StringUtils.isNotBlank(intentLabel))  filters.add(Filters.eq("intentLabel", intentLabel));
            if (StringUtils.isNotBlank(goalAchieved)) filters.add(Filters.eq("goalAchieved", goalAchieved));
            if (StringUtils.isNotBlank(sentiment))    filters.add(Filters.eq("sentiment", sentiment));

            FindIterable<Document> cursor = collection()
                    .find(Filters.and(filters))
                    .sort(Sorts.descending("startedAt"))
                    .limit(Math.max(1, Math.min(limit, 1000)));
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
                    new Document("$match", new Document()
                            .append("startedAt", new Document()
                                    .append("$gte", Date.from(from))
                                    .append("$lte", Date.from(to)))),
                    new Document("$group", new Document()
                            .append("_id",
                                    new Document("$ifNull", List.of("$" + field, "unknown")))
                            .append("count", new Document("$sum", 1))),
                    new Document("$sort", new Document("count", -1)),
                    new Document("$limit", Math.max(1, Math.min(maxGroups, 100))));
            AggregateIterable<Document> cursor = collection().aggregate(pipeline);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Document doc : cursor) {
                Map<String, Object> row = new LinkedHashMap<>();
                Object id = doc.get("_id");
                row.put("bucket", id == null ? "unknown" : String.valueOf(id));
                row.put("count", doc.getInteger("count", 0));
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
            filters.add(Filters.exists("outcome", true));
            filters.add(Filters.ne("outcome", "IN_PROGRESS"));
            // No processedAt yet → not enriched.
            filters.add(Filters.exists("processedAt", false));
            int cap = Math.max(1, Math.min(limit, 200));
            com.mongodb.client.FindIterable<Document> cursor = collection()
                    .find(Filters.and(filters))
                    .sort(com.mongodb.client.model.Sorts.ascending("startedAt"))
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
                set.add(Updates.set("intentLabel", enrichment.intentLabel().name()));
            }
            set.add(Updates.set("intentConfidence", enrichment.intentConfidence()));
            if (StringUtils.isNotBlank(enrichment.goalSummary())) {
                set.add(Updates.set("goalSummary", enrichment.goalSummary()));
            }
            if (enrichment.goalAchieved() != null) {
                set.add(Updates.set("goalAchieved", enrichment.goalAchieved().name()));
            }
            if (enrichment.sentiment() != null) {
                set.add(Updates.set("sentiment", enrichment.sentiment().name()));
            }
            if (enrichment.keyTerms() != null) {
                set.add(Updates.set("keyTerms", enrichment.keyTerms()));
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
            set.add(Updates.set("processedAt", stamp));

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
            pipeline.add(new Document("$match", new Document()
                    .append("startedAt", new Document()
                            .append("$gte", Date.from(from))
                            .append("$lte", Date.from(to)))));
            // Rate metrics only count sessions the enricher has touched —
            // otherwise a backlog of unenriched data drags the rate to 0.
            if (rateMetric) {
                pipeline.add(new Document("$match",
                        new Document("processedAt", new Document("$exists", true))));
            }

            Document group = new Document("_id",
                    new Document("$dateTrunc", new Document()
                            .append("date", "$startedAt")
                            .append("unit", unit)));
            group.append("total", new Document("$sum", 1));
            switch (validatedMetric) {
                case "goal_achievement_rate" -> group.append("metricSum", new Document("$sum",
                        new Document("$switch", new Document()
                                .append("branches", List.of(
                                        new Document("case",
                                                new Document("$eq", List.of("$goalAchieved", "YES")))
                                                .append("then", 1),
                                        new Document("case",
                                                new Document("$eq", List.of("$goalAchieved", "PARTIAL")))
                                                .append("then", 0.5)))
                                .append("default", 0))));
                case "negative_sentiment_rate" -> group.append("metricSum", new Document("$sum",
                        new Document("$cond", new Document()
                                .append("if", new Document("$or", List.of(
                                        new Document("$eq", List.of("$sentiment", "NEGATIVE")),
                                        new Document("$eq", List.of("$sentiment", "FRUSTRATED")))))
                                .append("then", 1).append("else", 0))));
                case "avg_duration_ms"        -> group.append("metricAvg", new Document("$avg", "$durationMs"));
                case "avg_tokens_out"         -> group.append("metricAvg", new Document("$avg", "$totalTokensOut"));
                // Per-session averages for tool metrics — same family as
                // avg_X above. Sessions that never invoked a tool contribute
                // 0 to the numerator and 1 to the denominator (sessions),
                // which gives "avg per session" not "avg per tool call".
                case "tool_calls_per_session"  -> group.append("metricAvg", new Document("$avg", "$totalToolCalls"));
                case "tool_errors_per_session" -> group.append("metricAvg", new Document("$avg", "$totalToolErrors"));
                case "avg_tool_latency_ms"     -> group.append("metricAvg", new Document("$avg", "$totalToolLatencyMs"));
                // Errors / calls × 100 — needs two sums in the bucket because
                // the denominator (tool calls) varies across sessions. Sessions
                // with zero tool calls contribute 0 to both sums; division by
                // zero is guarded in computeValue. Returned as 0-100 so the
                // dashboard renders directly as a percent without scaling.
                case "tool_error_rate_pct" -> {
                    group.append("metricSum",       new Document("$sum", "$totalToolErrors"));
                    group.append("metricDivisor",   new Document("$sum", "$totalToolCalls"));
                }
                default -> { /* sessions: total alone is enough */ }
            }
            pipeline.add(new Document("$group", group));
            pipeline.add(new Document("$sort", new Document("_id", 1)));

            AggregateIterable<Document> cursor = collection().aggregate(pipeline);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Document doc : cursor) {
                Map<String, Object> row = new LinkedHashMap<>();
                Date bucket = doc.getDate("_id");
                row.put("bucket", bucket == null ? null : bucket.toInstant().toString());
                int total = doc.getInteger("total", 0);
                row.put("total", total);
                row.put("value", computeValue(doc, validatedMetric, total));
                result.add(row);
            }
            return result;
        } catch (Exception e) {
            log.warn("Chat analytics timeseries({}, {}) failed: {}", metric, interval, e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<Map<String, Object>> scorecard(Instant from, Instant to, String dimension,
            Map<String, String> filters, int maxRows) {
        try {
            // Whitelisted group field (identity / experiment / T74 cohort);
            // anything outside the list falls back to agentId. Shared with the
            // Redis store via the interface so both pivot identically.
            String field = TurChatAnalyticsStore.scorecardDimensionField(dimension);
            int cap = Math.max(1, Math.min(maxRows, 100));

            // T74 — narrow the population BEFORE grouping. The match merges the
            // time-range with each whitelisted cohort/identity filter so
            // "variant X on mobile" is one server-side aggregation, not a
            // client-side post-filter.
            Document match = new Document()
                    .append("startedAt", new Document()
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

            List<Bson> pipeline = new ArrayList<>();
            pipeline.add(new Document("$match", match));

            // First pass: per (dimension, intentLabel) counts, plus session
            // aggregates. We pull intent counts in the same group so the
            // second stage can pick the top intent without a second query.
            Document firstGroupId = new Document()
                    .append("dim", new Document("$ifNull", List.of("$" + field, "unknown")))
                    .append("intent", "$intentLabel");
            pipeline.add(new Document("$group", new Document()
                    .append("_id", firstGroupId)
                    .append("count", new Document("$sum", 1))
                    .append("durationSum", new Document("$sum", "$durationMs"))
                    .append("processedCount", new Document("$sum",
                            new Document("$cond", new Document()
                                    .append("if", new Document("$ifNull", List.of("$processedAt", false)))
                                    .append("then", 1).append("else", 0))))
                    .append("achievedScore", new Document("$sum",
                            new Document("$switch", new Document()
                                    .append("branches", List.of(
                                            new Document("case",
                                                    new Document("$eq", List.of("$goalAchieved", "YES")))
                                                    .append("then", 1),
                                            new Document("case",
                                                    new Document("$eq", List.of("$goalAchieved", "PARTIAL")))
                                                    .append("then", 0.5)))
                                    .append("default", 0))))
                    .append("negativeCount", new Document("$sum",
                            new Document("$cond", new Document()
                                    .append("if", new Document("$or", List.of(
                                            new Document("$eq", List.of("$sentiment", "NEGATIVE")),
                                            new Document("$eq", List.of("$sentiment", "FRUSTRATED")))))
                                    .append("then", 1).append("else", 0))))));

            // Second pass: collapse intents into the top-1 per dimension.
            pipeline.add(new Document("$sort", new Document("count", -1)));
            pipeline.add(new Document("$group", new Document()
                    .append("_id", "$_id.dim")
                    .append("sessions",       new Document("$sum", "$count"))
                    .append("durationSum",    new Document("$sum", "$durationSum"))
                    .append("processedCount", new Document("$sum", "$processedCount"))
                    .append("achievedScore",  new Document("$sum", "$achievedScore"))
                    .append("negativeCount",  new Document("$sum", "$negativeCount"))
                    .append("topIntent",      new Document("$first", "$_id.intent"))));

            pipeline.add(new Document("$sort", new Document("sessions", -1)));
            pipeline.add(new Document("$limit", cap));

            AggregateIterable<Document> cursor = collection().aggregate(pipeline);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Document doc : cursor) {
                Map<String, Object> row = new LinkedHashMap<>();
                Object dim = doc.get("_id");
                row.put("bucket", dim == null ? "unknown" : String.valueOf(dim));
                int sessions = doc.getInteger("sessions", 0);
                long durationSum = numberAsLong(doc.get("durationSum"));
                int processed = doc.getInteger("processedCount", 0);
                double achieved = numberAsDouble(doc.get("achievedScore"));
                int negative = doc.getInteger("negativeCount", 0);
                row.put("sessions", sessions);
                row.put("avgDurationMs", sessions > 0 ? durationSum / (double) sessions : 0d);
                row.put("goalAchievedRate", processed > 0 ? achieved / processed : 0d);
                row.put("negativeRate",     processed > 0 ? negative / (double) processed : 0d);
                String topIntent = doc.getString("topIntent");
                row.put("topIntent", topIntent == null ? "unknown" : topIntent);
                result.add(row);
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
            filters.add(Filters.gte("startedAt", Date.from(from)));
            filters.add(Filters.lte("startedAt", Date.from(to)));
            if (StringUtils.isNotBlank(agentId)) filters.add(Filters.eq("agentId", agentId));
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
                    Filters.lt("startedAt", Date.from(threshold))).getDeletedCount();
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
            c.createIndex(new Document("startedAt", -1));
            // Enricher walks "outcome != IN_PROGRESS && processedAt missing"
            // ascending by startedAt. A sparse partial index on processedAt
            // would be nicer but every Mongo driver version supports plain.
            c.createIndex(new Document("processedAt", 1));
            // Frequent filter combos in the dashboard.
            c.createIndex(new Document().append("agentId", 1).append("startedAt", -1));
            c.createIndex(new Document().append("personaId", 1).append("startedAt", -1));
            c.createIndex(new Document("outcome", 1));
            // T74 — cohort scorecard filters/group-bys. deviceType has very low
            // cardinality (5 values) but pairs with startedAt for the windowed
            // scan; timezone/locale similar.
            c.createIndex(new Document().append("deviceType", 1).append("startedAt", -1));
            c.createIndex(new Document().append("locale", 1).append("startedAt", -1));
            c.createIndex(new Document().append("timezone", 1).append("startedAt", -1));
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
        if (groupBy == null) return "outcome";
        String trimmed = groupBy.trim();
        return switch (trimmed) {
            case "agentId", "personaId", "llmInstanceId", "embeddingModelId",
                 "storeInstanceId", "outcome", "locale", "userId",
                 "intentLabel", "goalAchieved", "sentiment",
                 // T74 cohort + experiment dimensions.
                 "timezone", "deviceType", "experimentKey", "variantLabel" -> trimmed;
            default -> "outcome";
        };
    }

    private static String sanitizeMetric(String metric) {
        if (metric == null) return "sessions";
        return switch (metric.trim()) {
            case "sessions",
                 "goal_achievement_rate",
                 "negative_sentiment_rate",
                 "avg_duration_ms",
                 "avg_tokens_out",
                 "tool_calls_per_session",
                 "tool_errors_per_session",
                 "avg_tool_latency_ms",
                 "tool_error_rate_pct" -> metric.trim();
            default -> "sessions";
        };
    }

    private static boolean isRateMetric(String metric) {
        return "goal_achievement_rate".equals(metric)
                || "negative_sentiment_rate".equals(metric);
    }

    private static Object computeValue(Document doc, String metric, int total) {
        return switch (metric) {
            case "goal_achievement_rate", "negative_sentiment_rate" ->
                    total > 0 ? numberAsDouble(doc.get("metricSum")) / total : 0d;
            case "avg_duration_ms", "avg_tokens_out",
                 "tool_calls_per_session", "tool_errors_per_session",
                 "avg_tool_latency_ms" ->
                    numberAsDouble(doc.get("metricAvg"));
            case "tool_error_rate_pct" -> {
                // errors / calls × 100 — guard divide-by-zero when no session
                // in the bucket invoked any tool (calls=0). Returns 0d so the
                // chart shows a clean baseline instead of NaN.
                double divisor = numberAsDouble(doc.get("metricDivisor"));
                yield divisor > 0 ? numberAsDouble(doc.get("metricSum")) * 100d / divisor : 0d;
            }
            default -> total; // "sessions" — count is the value
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
        row.put("agentId",          doc.getString("agentId"));
        row.put("personaId",        doc.getString("personaId"));
        row.put("llmInstanceId",    doc.getString("llmInstanceId"));
        row.put("embeddingModelId", doc.getString("embeddingModelId"));
        row.put("storeInstanceId",  doc.getString("storeInstanceId"));
        row.put("userId",           doc.getString("userId"));
        row.put("locale",           doc.getString("locale"));
        // T74 — visitor cohort dimensions.
        row.put("timezone",         doc.getString("timezone"));
        row.put("deviceType",       doc.getString("deviceType"));
        row.put("outcome",          doc.getString("outcome"));
        row.put("startedAt",        toIsoString(doc.getDate("startedAt")));
        row.put("completedAt",      toIsoString(doc.getDate("completedAt")));
        row.put("totalTurns",         doc.get("totalTurns", 0));
        row.put("totalTokensIn",      doc.get("totalTokensIn", 0L));
        row.put("totalTokensOut",     doc.get("totalTokensOut", 0L));
        row.put("totalToolCalls",     doc.get("totalToolCalls", 0));
        row.put("totalToolErrors",    doc.get("totalToolErrors", 0));
        row.put("totalToolLatencyMs", doc.get("totalToolLatencyMs", 0L));
        row.put("experimentKey",      doc.getString("experimentKey"));
        row.put("variantLabel",       doc.getString("variantLabel"));
        // T110 — child-session linkage to parent conversation.
        row.put("parentConversationId", doc.getString("parentConversationId"));
        row.put("durationMs",         doc.get("durationMs", 0L));
        row.put("firstUserMessage", doc.getString("firstUserMessage"));
        // Enrichment columns — null until the scheduled enricher runs.
        row.put("intentLabel",      doc.getString("intentLabel"));
        row.put("intentConfidence", doc.get("intentConfidence"));
        row.put("goalSummary",      doc.getString("goalSummary"));
        row.put("goalAchieved",     doc.getString("goalAchieved"));
        row.put("sentiment",        doc.getString("sentiment"));
        row.put("keyTerms",         doc.get("keyTerms"));
        // T87 — per-turn sentiment trajectory (array of enum names), null until enriched.
        row.put(FIELD_SENTIMENT_TRAJECTORY, doc.get(FIELD_SENTIMENT_TRAJECTORY));
        row.put("processedAt",      toIsoString(doc.getDate("processedAt")));
        return row;
    }

    private static String toIsoString(Date date) {
        return date == null ? null : date.toInstant().toString();
    }
}
