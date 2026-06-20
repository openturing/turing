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

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.params.ZRangeParams;

/**
 * Redis-backed analytics store. For each {@code conversationId} we keep:
 * <ul>
 *   <li>A HASH at {@code <prefix>:session:<convId>} with all session fields.</li>
 *   <li>A SORTED SET at {@code <prefix>:sessions_by_time} where the score is
 *       the {@code startedAt} epoch milliseconds and the member is the
 *       conversationId. Drives time-range scans for dashboards.</li>
 * </ul>
 *
 * <p>Aggregation is computed in-process by reading all sessions in the
 * window — fine for small/dev workloads, but for high volume prefer the
 * MongoDB store which can use server-side {@code $group}.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@Slf4j
public class TurRedisChatAnalyticsStore implements TurChatAnalyticsStore {

    // --- S1192: extracted duplicated literals ---
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
    private static final String STARTED_AT = "startedAt";
    private static final String FIRST_USER_MESSAGE = "firstUserMessage";
    private static final String UNKNOWN = "unknown";
    private static final String BUCKET = "bucket";
    private static final String PROCESSED_AT = "processedAt";
    private static final String INTENT_LABEL = "intentLabel";
    private static final String INTENT_CONFIDENCE = "intentConfidence";
    private static final String GOAL_SUMMARY = "goalSummary";
    private static final String GOAL_ACHIEVED = "goalAchieved";
    private static final String SENTIMENT = "sentiment";
    private static final String KEY_TERMS = "keyTerms";
    private static final String GOAL_ACHIEVEMENT_RATE = "goal_achievement_rate";
    private static final String NEGATIVE_SENTIMENT_RATE = "negative_sentiment_rate";
    private static final String AVG_DURATION_MS = "avg_duration_ms";
    private static final String AVG_TOKENS_OUT = "avg_tokens_out";
    private static final String TOOL_ERRORS_PER_SESSION = "tool_errors_per_session";
    private static final String TOOL_CALLS_PER_SESSION = "tool_calls_per_session";
    private static final String AVG_TOOL_LATENCY_MS = "avg_tool_latency_ms";
    private static final String TOOL_ERROR_RATE_PCT = "tool_error_rate_pct";
    private static final String SESSIONS = "sessions";


    private static final String FIELD_SENTIMENT_TRAJECTORY = "sentimentTrajectory";
    private static final String FIELD_TOOL_LATENCIES = "toolLatencies";

    private final JedisPool pool;
    private final String keyPrefix;

    public TurRedisChatAnalyticsStore(String redisUri, String keyPrefix) {
        JedisPoolConfig cfg = new JedisPoolConfig();
        cfg.setMaxTotal(20);
        cfg.setMaxWait(Duration.ofSeconds(2));
        this.pool = new JedisPool(cfg, URI.create(redisUri));
        this.keyPrefix = keyPrefix;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public TurChatAnalyticsEngine getEngine() {
        return TurChatAnalyticsEngine.REDIS;
    }

    @Override
    public void upsertSession(TurChatSessionEvent event) {
        try (Jedis jedis = pool.getResource()) {
            String hashKey = sessionKey(event.conversationId());
            Map<String, String> fields = new HashMap<>();
            putIfNotBlank(fields, AGENT_ID,          event.agentId());
            putIfNotBlank(fields, PERSONA_ID,        event.personaId());
            putIfNotBlank(fields, LLM_INSTANCE_ID,    event.llmInstanceId());
            putIfNotBlank(fields, EMBEDDING_MODEL_ID, event.embeddingModelId());
            putIfNotBlank(fields, STORE_INSTANCE_ID,  event.storeInstanceId());
            putIfNotBlank(fields, USER_ID,           event.userId());
            putIfNotBlank(fields, LOCALE,           event.locale());
            // T74 — visitor cohort (timezone / device class).
            putIfNotBlank(fields, TIMEZONE,         event.timezone());
            putIfNotBlank(fields, DEVICE_TYPE,       event.deviceType());
            if (event.outcome() != null) fields.put(OUTCOME, event.outcome().name());
            fields.put(TOTAL_TURNS,         String.valueOf(event.totalTurns()));
            fields.put(TOTAL_TOKENS_IN,      String.valueOf(event.totalTokensIn()));
            fields.put(TOTAL_TOKENS_OUT,     String.valueOf(event.totalTokensOut()));
            fields.put(TOTAL_TOOL_CALLS,     String.valueOf(event.totalToolCalls()));
            fields.put(TOTAL_TOOL_ERRORS,    String.valueOf(event.totalToolErrors()));
            fields.put(TOTAL_TOOL_LATENCY_MS, String.valueOf(event.totalToolLatencyMs()));
            putIfNotBlank(fields, EXPERIMENT_KEY, event.experimentKey());
            putIfNotBlank(fields, VARIANT_LABEL,  event.variantLabel());
            // T110 — child-session linkage; written once on start, preserved on end.
            putIfNotBlank(fields, PARENT_CONVERSATION_ID, event.parentConversationId());
            // T88 — raw per-tool latency samples, delimited string; only written
            // when present (end record) so the empty start record can't clobber it.
            if (event.toolLatencies() != null && !event.toolLatencies().isEmpty()) {
                fields.put(FIELD_TOOL_LATENCIES, encodeToolLatencies(event.toolLatencies()));
            }
            fields.put(DURATION_MS,         String.valueOf(event.durationMs()));
            fields.put("updatedAt",      Instant.now().toString());
            if (event.completedAt() != null) {
                fields.put(COMPLETED_AT, event.completedAt().toString());
            }
            if (!fields.isEmpty()) {
                jedis.hset(hashKey, fields);
            }
            // start-only — only set if absent
            if (event.startedAt() != null) {
                jedis.hsetnx(hashKey, STARTED_AT, event.startedAt().toString());
                jedis.zadd(byTimeKey(), event.startedAt().toEpochMilli(), event.conversationId());
            }
            if (StringUtils.isNotBlank(event.firstUserMessage())) {
                jedis.hsetnx(hashKey, FIRST_USER_MESSAGE, event.firstUserMessage());
            }
        } catch (Exception e) {
            log.warn("Failed to upsert chat session analytics to Redis for conversation {}: {}",
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
        int cap = Math.clamp(limit, 1, 1000);
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Jedis jedis = pool.getResource()) {
            // Sorted set is ascending by score; we walk from the highest score
            // backwards so the newest sessions come first.
            List<String> ids = jedis.zrange(byTimeKey(),
                    new ZRangeParams(Protocol.Keyword.BYSCORE,
                            String.valueOf(to.toEpochMilli()),
                            String.valueOf(from.toEpochMilli()))
                            .rev().limit(0, cap * 4)); // overshoot to absorb filtered-out rows
            for (String id : ids) {
                if (rows.size() >= cap) break;
                Map<String, String> hash = jedis.hgetAll(sessionKey(id));
                if (!hash.isEmpty() && matches(hash, agentId, personaId, outcome,
                        intentLabel, goalAchieved, sentiment)) {
                    rows.add(toRow(id, hash));
                }
            }
        } catch (Exception e) {
            log.warn("Chat analytics findRecentSessions(Redis) failed: {}", e.getMessage());
        }
        return rows;
    }

    @Override
    public List<Map<String, Object>> aggregate(Instant from, Instant to, String groupBy, int maxGroups) {
        String field = sanitizeFieldName(groupBy);
        Map<String, Integer> counts = new HashMap<>();
        try (Jedis jedis = pool.getResource()) {
            List<String> ids = jedis.zrange(byTimeKey(),
                    new ZRangeParams(Protocol.Keyword.BYSCORE,
                            String.valueOf(from.toEpochMilli()),
                            String.valueOf(to.toEpochMilli())));
            for (String id : ids) {
                Map<String, String> hash = jedis.hgetAll(sessionKey(id));
                if (hash.isEmpty()) continue;
                String key = hash.getOrDefault(field, "");
                if (key.isBlank()) key = UNKNOWN;
                counts.merge(key, 1, Integer::sum);
            }
        } catch (Exception e) {
            log.warn("Chat analytics aggregate(Redis,{}) failed: {}", groupBy, e.getMessage());
            return List.of();
        }
        return counts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(Math.clamp(maxGroups, 1, 100))
                .map(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put(BUCKET, e.getKey());
                    row.put("count", e.getValue());
                    return row;
                })
                .toList();
    }

    @Override
    public List<Map<String, Object>> findUnenrichedSessions(int limit) {
        int cap = Math.clamp(limit, 1, 200);
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Jedis jedis = pool.getResource()) {
            // Walk all known sessions oldest-first; for each, check eligibility
            // in-process. With a small `limit` this is cheap; for very large
            // datasets, prefer the Mongo backend.
            List<String> ids = jedis.zrange(byTimeKey(), 0, -1);
            for (String id : ids) {
                if (rows.size() >= cap) break;
                Map<String, String> hash = jedis.hgetAll(sessionKey(id));
                String outcome = hash.get(OUTCOME);
                if (!hash.isEmpty() && outcome != null && !"IN_PROGRESS".equals(outcome)
                        && StringUtils.isBlank(hash.get(PROCESSED_AT))) {
                    rows.add(toRow(id, hash));
                }
            }
        } catch (Exception e) {
            log.warn("Chat analytics findUnenrichedSessions(Redis) failed: {}", e.getMessage());
        }
        return rows;
    }

    @Override
    public void enrichSession(String conversationId, TurChatSessionEnrichment enrichment) {
        if (conversationId == null || conversationId.isBlank() || enrichment == null) return;
        try (Jedis jedis = pool.getResource()) {
            Map<String, String> fields = new HashMap<>();
            if (enrichment.intentLabel() != null) fields.put(INTENT_LABEL, enrichment.intentLabel().name());
            fields.put(INTENT_CONFIDENCE, String.valueOf(enrichment.intentConfidence()));
            if (StringUtils.isNotBlank(enrichment.goalSummary())) fields.put(GOAL_SUMMARY, enrichment.goalSummary());
            if (enrichment.goalAchieved() != null) fields.put(GOAL_ACHIEVED, enrichment.goalAchieved().name());
            if (enrichment.sentiment() != null) fields.put(SENTIMENT, enrichment.sentiment().name());
            if (enrichment.keyTerms() != null) {
                fields.put(KEY_TERMS, String.join(",", enrichment.keyTerms()));
            }
            // T87 — per-turn sentiment trajectory as a CSV of enum names; absent
            // when the catalog-driven classifiers ran (empty list).
            if (enrichment.sentimentTrajectory() != null && !enrichment.sentimentTrajectory().isEmpty()) {
                fields.put(FIELD_SENTIMENT_TRAJECTORY, String.join(",",
                        enrichment.sentimentTrajectory().stream().map(Enum::name).toList()));
            }
            fields.put(PROCESSED_AT, (enrichment.processedAt() == null
                    ? java.time.Instant.now() : enrichment.processedAt()).toString());
            if (!fields.isEmpty()) {
                jedis.hset(sessionKey(conversationId), fields);
            }
        } catch (Exception e) {
            log.warn("Failed to enrich chat session {} (Redis): {}", conversationId, e.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> timeseries(Instant from, Instant to, String metric, String interval) {
        String validatedMetric = sanitizeMetric(metric);
        boolean dailyBuckets = "day".equals(interval);
        boolean rateMetric = isRateMetric(validatedMetric);

        // bucket → [total, primarySum, divisorSum]. divisorSum is only used
        // by metrics like {@code tool_error_rate} that divide two session-
        // wide totals (errors / calls) — single-sum metrics leave it at 0.
        Map<Long, double[]> buckets = new java.util.TreeMap<>();
        try (Jedis jedis = pool.getResource()) {
            List<String> ids = jedis.zrange(byTimeKey(),
                    new ZRangeParams(Protocol.Keyword.BYSCORE,
                            String.valueOf(from.toEpochMilli()),
                            String.valueOf(to.toEpochMilli())));
            accumulateTimeseries(jedis, ids, buckets, rateMetric, dailyBuckets, validatedMetric);
        } catch (Exception e) {
            log.warn("Chat analytics timeseries(Redis) failed: {}", e.getMessage());
            return List.of();
        }
        return buildTimeseriesRows(buckets, validatedMetric);
    }

    /** Folds each in-range session into its time bucket's [total, primarySum, divisorSum]. */
    private void accumulateTimeseries(Jedis jedis, List<String> ids, Map<Long, double[]> buckets,
            boolean rateMetric, boolean dailyBuckets, String validatedMetric) {
        for (String id : ids) {
            Map<String, String> hash = jedis.hgetAll(sessionKey(id));
            String startedAt = hash.get(STARTED_AT);
            Instant when = StringUtils.isBlank(startedAt) ? null : parseInstantOrNull(startedAt);
            boolean rateEligible = !rateMetric || StringUtils.isNotBlank(hash.get(PROCESSED_AT));
            if (!hash.isEmpty() && when != null && rateEligible) {
                long bucketKey = truncate(when, dailyBuckets);
                double[] acc = buckets.computeIfAbsent(bucketKey, k -> new double[]{0d, 0d, 0d});
                acc[0] += 1; // total (sessions)
                acc[1] += metricContribution(hash, validatedMetric);
                acc[2] += metricDivisorContribution(hash, validatedMetric);
            }
        }
    }

    /** Renders the accumulated buckets into result rows, applying the per-metric formula. */
    private List<Map<String, Object>> buildTimeseriesRows(Map<Long, double[]> buckets,
            String validatedMetric) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Long, double[]> entry : buckets.entrySet()) {
            int total = (int) entry.getValue()[0];
            double sum = entry.getValue()[1];
            double divisor = entry.getValue()[2];
            Map<String, Object> row = new LinkedHashMap<>();
            row.put(BUCKET, Instant.ofEpochMilli(entry.getKey()).toString());
            row.put("total", total);
            row.put("value", switch (validatedMetric) {
                case GOAL_ACHIEVEMENT_RATE, NEGATIVE_SENTIMENT_RATE -> total > 0 ? sum / total : 0d;
                case AVG_DURATION_MS, AVG_TOKENS_OUT,
                     TOOL_CALLS_PER_SESSION, TOOL_ERRORS_PER_SESSION,
                     AVG_TOOL_LATENCY_MS -> total > 0 ? sum / total : 0d;
                case TOOL_ERROR_RATE_PCT -> divisor > 0 ? sum * 100d / divisor : 0d;
                default -> total;
            });
            result.add(row);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> scorecard(Instant from, Instant to, String dimension,
            Map<String, String> filters, int maxRows) {
        // Whitelisted group field — shared with the Mongo store via the
        // interface so both pivot identically (identity / experiment / T74
        // cohort dimensions).
        String field = TurChatAnalyticsStore.scorecardDimensionField(dimension);
        int cap = Math.clamp(maxRows, 1, 100);

        // dim → aggregates: [sessions, durationSum, processedCount, achievedScore, negativeCount]
        Map<String, double[]> agg = new HashMap<>();
        // dim → (intent → count) for top-intent selection
        Map<String, Map<String, Integer>> intentCounts = new HashMap<>();

        try (Jedis jedis = pool.getResource()) {
            List<String> ids = jedis.zrange(byTimeKey(),
                    new ZRangeParams(Protocol.Keyword.BYSCORE,
                            String.valueOf(from.toEpochMilli()),
                            String.valueOf(to.toEpochMilli())));
            accumulateScorecard(jedis, ids, field, filters, agg, intentCounts);
        } catch (Exception e) {
            log.warn("Chat analytics scorecard(Redis) failed: {}", e.getMessage());
            return List.of();
        }

        return agg.entrySet().stream()
                .sorted((l, r) -> Double.compare(r.getValue()[0], l.getValue()[0]))
                .limit(cap)
                .map(e -> scorecardRow(e, intentCounts))
                .toList();
    }

    /** Folds each in-range, filter-matching session into its dimension's aggregate + intent counts. */
    private void accumulateScorecard(Jedis jedis, List<String> ids, String field,
            Map<String, String> filters, Map<String, double[]> agg,
            Map<String, Map<String, Integer>> intentCounts) {
        for (String id : ids) {
            Map<String, String> hash = jedis.hgetAll(sessionKey(id));
            // T74 — narrow the population before grouping. Mongo pushes this
            // into a $match; the Redis store filters in-process.
            if (!hash.isEmpty() && matchesScorecardFilters(hash, filters)) {
                accumulateScorecardRow(hash, field, agg, intentCounts);
            }
        }
    }

    /** Folds one filter-matching session into its dimension's aggregate + intent counts. */
    private void accumulateScorecardRow(Map<String, String> hash, String field,
            Map<String, double[]> agg, Map<String, Map<String, Integer>> intentCounts) {
        String dim = hash.get(field);
        if (StringUtils.isBlank(dim)) dim = UNKNOWN;
        double[] a = agg.computeIfAbsent(dim, k -> new double[5]);
        a[0] += 1;
        a[1] += parseLong(hash.get(DURATION_MS));
        accumulateProcessed(hash, a);
        String intent = hash.get(INTENT_LABEL);
        if (StringUtils.isNotBlank(intent)) {
            intentCounts.computeIfAbsent(dim, k -> new HashMap<>())
                    .merge(intent, 1, Integer::sum);
        }
    }

    /** Folds the goal/sentiment outcomes of a processed session into its aggregate slots. */
    private void accumulateProcessed(Map<String, String> hash, double[] a) {
        if (StringUtils.isBlank(hash.get(PROCESSED_AT))) {
            return;
        }
        a[2] += 1;
        String goal = hash.get(GOAL_ACHIEVED);
        if ("YES".equals(goal)) a[3] += 1;
        else if ("PARTIAL".equals(goal)) a[3] += 0.5;
        String s = hash.get(SENTIMENT);
        if ("NEGATIVE".equals(s) || "FRUSTRATED".equals(s)) a[4] += 1;
    }

    /** Renders one dimension's aggregates into a scorecard row (rates + top intent). */
    private Map<String, Object> scorecardRow(Map.Entry<String, double[]> e,
            Map<String, Map<String, Integer>> intentCounts) {
        double[] a = e.getValue();
        int sessions = (int) a[0];
        int processed = (int) a[2];
        Map<String, Object> row = new LinkedHashMap<>();
        row.put(BUCKET, e.getKey());
        row.put(SESSIONS, sessions);
        row.put("avgDurationMs", sessions > 0 ? a[1] / sessions : 0d);
        row.put("goalAchievedRate", processed > 0 ? a[3] / processed : 0d);
        row.put("negativeRate", processed > 0 ? a[4] / processed : 0d);
        String topIntent = intentCounts.getOrDefault(e.getKey(), Map.of())
                .entrySet().stream()
                .max((x, y) -> Integer.compare(x.getValue(), y.getValue()))
                .map(Map.Entry::getKey).orElse(UNKNOWN);
        row.put("topIntent", topIntent);
        return row;
    }

    @Override
    public List<Map<String, Object>> toolLatency(Instant from, Instant to, String agentId, int maxTools) {
        TurToolLatencyAccumulator acc = new TurToolLatencyAccumulator();
        try (Jedis jedis = pool.getResource()) {
            List<String> ids = jedis.zrange(byTimeKey(),
                    new ZRangeParams(Protocol.Keyword.BYSCORE,
                            String.valueOf(from.toEpochMilli()),
                            String.valueOf(to.toEpochMilli())));
            for (String id : ids) {
                Map<String, String> hash = jedis.hgetAll(sessionKey(id));
                String encoded = hash.get(FIELD_TOOL_LATENCIES);
                if (!hash.isEmpty()
                        && (StringUtils.isBlank(agentId) || agentId.equals(hash.get(AGENT_ID)))
                        && StringUtils.isNotBlank(encoded)) {
                    decodeToolLatenciesInto(encoded, acc);
                }
            }
        } catch (Exception e) {
            log.warn("Chat analytics toolLatency(Redis) failed: {}", e.getMessage());
            return List.of();
        }
        return acc.toRows(maxTools);
    }

    @Override
    @SuppressWarnings("java:S1168") // null = not found, distinct from empty map
    public Map<String, Object> findSessionById(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) return null;
        try (Jedis jedis = pool.getResource()) {
            Map<String, String> hash = jedis.hgetAll(sessionKey(conversationId));
            return hash.isEmpty() ? null : toRow(conversationId, hash);
        } catch (Exception e) {
            log.warn("Chat analytics findSessionById({}) (Redis) failed: {}", conversationId, e.getMessage());
            return null;
        }
    }

    @Override
    public long purgeOlderThan(Instant threshold) {
        if (threshold == null) return 0L;
        long removed = 0L;
        try (Jedis jedis = pool.getResource()) {
            // ZRANGEBYSCORE returns members with score in [-∞, threshold) — i.e.
            // sessions whose startedAt is strictly older. Then delete the hash
            // and remove the index entry.
            List<String> ids = jedis.zrange(byTimeKey(),
                    new ZRangeParams(Protocol.Keyword.BYSCORE,
                            "-inf",
                            "(" + threshold.toEpochMilli()));
            for (String id : ids) {
                jedis.del(sessionKey(id));
                jedis.zrem(byTimeKey(), id);
                removed++;
            }
        } catch (Exception e) {
            log.warn("Chat analytics purgeOlderThan(Redis) failed: {}", e.getMessage());
            return 0L;
        }
        return removed;
    }

    public void close() {
        if (pool != null) pool.close();
    }

    // ── helpers ──

    private String sessionKey(String conversationId) {
        return keyPrefix + ":session:" + conversationId;
    }

    private String byTimeKey() {
        return keyPrefix + ":sessions_by_time";
    }

    private static void putIfNotBlank(Map<String, String> map, String key, String value) {
        if (StringUtils.isNotBlank(value)) map.put(key, value);
    }

    /**
     * T88 — encodes the per-tool latency samples as a compact delimited string
     * (same family as the {@code keyTerms} CSV idiom, but a triple per entry):
     * {@code tool|latencyMs|s} joined by {@code ;}, where {@code s} is
     * {@code 1}/{@code 0} for success/failure. Tool names have the two
     * delimiters stripped so a pathological name can't corrupt the framing
     * (Spring AI tool names are {@code [A-Za-z0-9_-]} in practice).
     */
    private static String encodeToolLatencies(List<TurToolLatencySample> samples) {
        StringBuilder sb = new StringBuilder();
        for (TurToolLatencySample s : samples) {
            if (!sb.isEmpty()) sb.append(';');
            sb.append(s.tool().replace(';', '_').replace('|', '_'))
              .append('|').append(s.latencyMs())
              .append('|').append(s.success() ? '1' : '0');
        }
        return sb.toString();
    }

    /**
     * Inverse of {@link #encodeToolLatencies} — folds each well-formed triple
     * into the accumulator. Malformed entries (wrong arity, non-numeric
     * latency) are skipped leniently so one corrupt row can't sink the whole
     * window.
     */
    private static void decodeToolLatenciesInto(String encoded, TurToolLatencyAccumulator acc) {
        for (String token : encoded.split(";")) {
            String[] parts = token.split("\\|", 3);
            if (!token.isBlank() && parts.length == 3) {
                try {
                    acc.add(parts[0], Long.parseLong(parts[1].trim()), "1".equals(parts[2].trim()));
                } catch (NumberFormatException ignored) {
                    // skip a corrupt sample, keep the rest
                }
            }
        }
    }

    /**
     * T74 — true when the session hash satisfies every whitelisted scorecard
     * filter (cohort + identity). Blank filter values and non-whitelisted keys
     * are ignored; a null/empty map matches everything.
     */
    private static boolean matchesScorecardFilters(Map<String, String> hash,
            Map<String, String> filters) {
        if (filters == null || filters.isEmpty()) return true;
        for (Map.Entry<String, String> e : filters.entrySet()) {
            if (TurChatAnalyticsStore.SCORECARD_FILTERABLE_FIELDS.contains(e.getKey())
                    && StringUtils.isNotBlank(e.getValue())
                    && !e.getValue().equals(hash.get(e.getKey()))) {
                return false;
            }
        }
        return true;
    }

    private static boolean matches(Map<String, String> hash, String agentId, String personaId, String outcome,
            String intentLabel, String goalAchieved, String sentiment) {
        if (StringUtils.isNotBlank(agentId)      && !agentId.equals(hash.get(AGENT_ID)))           return false;
        if (StringUtils.isNotBlank(personaId)    && !personaId.equals(hash.get(PERSONA_ID)))       return false;
        if (StringUtils.isNotBlank(outcome)      && !outcome.equals(hash.get(OUTCOME)))           return false;
        if (StringUtils.isNotBlank(intentLabel)  && !intentLabel.equals(hash.get(INTENT_LABEL)))   return false;
        if (StringUtils.isNotBlank(goalAchieved) && !goalAchieved.equals(hash.get(GOAL_ACHIEVED))) return false;
        if (StringUtils.isNotBlank(sentiment)    && !sentiment.equals(hash.get(SENTIMENT)))       return false;
        return true;
    }

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

    /** Parses an ISO-8601 instant, or returns {@code null} when the value is unparseable. */
    private static Instant parseInstantOrNull(String value) {
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            return null;
        }
    }

    private static long truncate(Instant when, boolean dailyBuckets) {
        java.time.temporal.ChronoUnit unit = dailyBuckets
                ? java.time.temporal.ChronoUnit.DAYS
                : java.time.temporal.ChronoUnit.HOURS;
        return when.truncatedTo(unit).toEpochMilli();
    }

    /**
     * Numerator contribution of a single session for the given metric. The
     * caller divides by the bucket {@code total} for rate metrics; for averages
     * the bucket holds running sum + count.
     */
    private static double metricContribution(Map<String, String> hash, String metric) {
        return switch (metric) {
            case GOAL_ACHIEVEMENT_RATE -> {
                String g = hash.get(GOAL_ACHIEVED);
                double partialGoal = "PARTIAL".equals(g) ? 0.5d : 0d;
                yield "YES".equals(g) ? 1d : partialGoal;
            }
            case NEGATIVE_SENTIMENT_RATE -> {
                String s = hash.get(SENTIMENT);
                yield ("NEGATIVE".equals(s) || "FRUSTRATED".equals(s)) ? 1d : 0d;
            }
            case AVG_DURATION_MS         -> parseLong(hash.get(DURATION_MS));
            case AVG_TOKENS_OUT          -> parseLong(hash.get(TOTAL_TOKENS_OUT));
            // The next three are per-session averages — divisor is bucket
            // session count, same family as avg_tokens_out / avg_duration_ms.
            // Each session contributes its own session-total to the sum.
            case TOOL_CALLS_PER_SESSION  -> parseLong(hash.get(TOTAL_TOOL_CALLS));
            case TOOL_ERRORS_PER_SESSION -> parseLong(hash.get(TOTAL_TOOL_ERRORS));
            case AVG_TOOL_LATENCY_MS     -> parseLong(hash.get(TOTAL_TOOL_LATENCY_MS));
            // tool_error_rate_pct is errors / calls × 100 — numerator is the
            // session's tool errors; divisor is filled by metricDivisorContribution.
            case TOOL_ERROR_RATE_PCT     -> parseLong(hash.get(TOTAL_TOOL_ERRORS));
            default -> 0d;
        };
    }

    /**
     * Denominator contribution for two-sum rate metrics. Returns 0 for the
     * single-sum family — those use {@link #metricContribution} alone and
     * divide by the bucket's session count.
     */
    private static double metricDivisorContribution(Map<String, String> hash, String metric) {
        // For tool_error_rate_pct the denominator is total tool calls across
        // sessions in the bucket — so a 1-call session with 1 error rates
        // 100% while a 10-call session with 1 error rates 10%, matching
        // the "rate" semantics the dashboard expects.
        if (TOOL_ERROR_RATE_PCT.equals(metric)) {
            return parseLong(hash.get(TOTAL_TOOL_CALLS));
        }
        return 0d;
    }

    private static Map<String, Object> toRow(String id, Map<String, String> hash) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("conversationId",   id);
        row.put(AGENT_ID,          hash.get(AGENT_ID));
        row.put(PERSONA_ID,        hash.get(PERSONA_ID));
        row.put(LLM_INSTANCE_ID,    hash.get(LLM_INSTANCE_ID));
        row.put(EMBEDDING_MODEL_ID, hash.get(EMBEDDING_MODEL_ID));
        row.put(STORE_INSTANCE_ID,  hash.get(STORE_INSTANCE_ID));
        row.put(USER_ID,           hash.get(USER_ID));
        row.put(LOCALE,           hash.get(LOCALE));
        // T74 — visitor cohort dimensions.
        row.put(TIMEZONE,         hash.get(TIMEZONE));
        row.put(DEVICE_TYPE,       hash.get(DEVICE_TYPE));
        row.put(OUTCOME,          hash.get(OUTCOME));
        row.put(STARTED_AT,        hash.get(STARTED_AT));
        row.put(COMPLETED_AT,      hash.get(COMPLETED_AT));
        row.put(TOTAL_TURNS,         parseLong(hash.get(TOTAL_TURNS)));
        row.put(TOTAL_TOKENS_IN,      parseLong(hash.get(TOTAL_TOKENS_IN)));
        row.put(TOTAL_TOKENS_OUT,     parseLong(hash.get(TOTAL_TOKENS_OUT)));
        row.put(TOTAL_TOOL_CALLS,     parseLong(hash.get(TOTAL_TOOL_CALLS)));
        row.put(TOTAL_TOOL_ERRORS,    parseLong(hash.get(TOTAL_TOOL_ERRORS)));
        row.put(TOTAL_TOOL_LATENCY_MS, parseLong(hash.get(TOTAL_TOOL_LATENCY_MS)));
        row.put(EXPERIMENT_KEY,      hash.get(EXPERIMENT_KEY));
        row.put(VARIANT_LABEL,       hash.get(VARIANT_LABEL));
        // T110 — child-session linkage to parent conversation.
        row.put(PARENT_CONVERSATION_ID, hash.get(PARENT_CONVERSATION_ID));
        row.put(DURATION_MS,         parseLong(hash.get(DURATION_MS)));
        row.put(FIRST_USER_MESSAGE, hash.get(FIRST_USER_MESSAGE));
        // Enrichment columns — null until the scheduled enricher runs.
        row.put(INTENT_LABEL,      hash.get(INTENT_LABEL));
        String confidence = hash.get(INTENT_CONFIDENCE);
        row.put(INTENT_CONFIDENCE, StringUtils.isBlank(confidence) ? null : parseDouble(confidence));
        row.put(GOAL_SUMMARY,      hash.get(GOAL_SUMMARY));
        row.put(GOAL_ACHIEVED,     hash.get(GOAL_ACHIEVED));
        row.put(SENTIMENT,        hash.get(SENTIMENT));
        String keyTerms = hash.get(KEY_TERMS);
        row.put(KEY_TERMS,         StringUtils.isBlank(keyTerms) ? null : List.of(keyTerms.split(",")));
        // T87 — per-turn sentiment trajectory (CSV of enum names), null until enriched.
        String trajectory = hash.get(FIELD_SENTIMENT_TRAJECTORY);
        row.put(FIELD_SENTIMENT_TRAJECTORY,
                StringUtils.isBlank(trajectory) ? null : List.of(trajectory.split(",")));
        row.put(PROCESSED_AT,      hash.get(PROCESSED_AT));
        return row;
    }

    private static double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0d;
        }
    }

    private static long parseLong(String value) {
        if (StringUtils.isBlank(value)) return 0L;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
