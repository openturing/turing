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
            putIfNotBlank(fields, "agentId",          event.agentId());
            putIfNotBlank(fields, "personaId",        event.personaId());
            putIfNotBlank(fields, "llmInstanceId",    event.llmInstanceId());
            putIfNotBlank(fields, "embeddingModelId", event.embeddingModelId());
            putIfNotBlank(fields, "storeInstanceId",  event.storeInstanceId());
            putIfNotBlank(fields, "userId",           event.userId());
            putIfNotBlank(fields, "locale",           event.locale());
            // T74 — visitor cohort (timezone / device class).
            putIfNotBlank(fields, "timezone",         event.timezone());
            putIfNotBlank(fields, "deviceType",       event.deviceType());
            if (event.outcome() != null) fields.put("outcome", event.outcome().name());
            fields.put("totalTurns",         String.valueOf(event.totalTurns()));
            fields.put("totalTokensIn",      String.valueOf(event.totalTokensIn()));
            fields.put("totalTokensOut",     String.valueOf(event.totalTokensOut()));
            fields.put("totalToolCalls",     String.valueOf(event.totalToolCalls()));
            fields.put("totalToolErrors",    String.valueOf(event.totalToolErrors()));
            fields.put("totalToolLatencyMs", String.valueOf(event.totalToolLatencyMs()));
            putIfNotBlank(fields, "experimentKey", event.experimentKey());
            putIfNotBlank(fields, "variantLabel",  event.variantLabel());
            // T110 — child-session linkage; written once on start, preserved on end.
            putIfNotBlank(fields, "parentConversationId", event.parentConversationId());
            // T88 — raw per-tool latency samples, delimited string; only written
            // when present (end record) so the empty start record can't clobber it.
            if (event.toolLatencies() != null && !event.toolLatencies().isEmpty()) {
                fields.put(FIELD_TOOL_LATENCIES, encodeToolLatencies(event.toolLatencies()));
            }
            fields.put("durationMs",         String.valueOf(event.durationMs()));
            fields.put("updatedAt",      Instant.now().toString());
            if (event.completedAt() != null) {
                fields.put("completedAt", event.completedAt().toString());
            }
            if (!fields.isEmpty()) {
                jedis.hset(hashKey, fields);
            }
            // start-only — only set if absent
            if (event.startedAt() != null) {
                jedis.hsetnx(hashKey, "startedAt", event.startedAt().toString());
                jedis.zadd(byTimeKey(), event.startedAt().toEpochMilli(), event.conversationId());
            }
            if (StringUtils.isNotBlank(event.firstUserMessage())) {
                jedis.hsetnx(hashKey, "firstUserMessage", event.firstUserMessage());
            }
        } catch (Exception e) {
            log.warn("Failed to upsert chat session analytics to Redis for conversation {}: {}",
                    event.conversationId(), e.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> findRecentSessions(Instant from, Instant to,
            String agentId, String personaId, String outcome,
            String intentLabel, String goalAchieved, String sentiment,
            int limit) {
        int cap = Math.max(1, Math.min(limit, 1000));
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Jedis jedis = pool.getResource()) {
            // Sorted set is ascending by score; we walk from the highest score
            // backwards so the newest sessions come first.
            List<String> ids = jedis.zrevrangeByScore(byTimeKey(),
                    String.valueOf(to.toEpochMilli()),
                    String.valueOf(from.toEpochMilli()),
                    0, cap * 4); // overshoot to absorb filtered-out rows
            for (String id : ids) {
                if (rows.size() >= cap) break;
                Map<String, String> hash = jedis.hgetAll(sessionKey(id));
                if (hash.isEmpty()) continue;
                if (!matches(hash, agentId, personaId, outcome,
                        intentLabel, goalAchieved, sentiment)) continue;
                rows.add(toRow(id, hash));
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
            List<String> ids = jedis.zrangeByScore(byTimeKey(),
                    String.valueOf(from.toEpochMilli()),
                    String.valueOf(to.toEpochMilli()));
            for (String id : ids) {
                Map<String, String> hash = jedis.hgetAll(sessionKey(id));
                if (hash.isEmpty()) continue;
                String key = hash.getOrDefault(field, "");
                if (key.isBlank()) key = "unknown";
                counts.merge(key, 1, Integer::sum);
            }
        } catch (Exception e) {
            log.warn("Chat analytics aggregate(Redis,{}) failed: {}", groupBy, e.getMessage());
            return List.of();
        }
        return counts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(Math.max(1, Math.min(maxGroups, 100)))
                .map(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("bucket", e.getKey());
                    row.put("count", e.getValue());
                    return row;
                })
                .toList();
    }

    @Override
    public List<Map<String, Object>> findUnenrichedSessions(int limit) {
        int cap = Math.max(1, Math.min(limit, 200));
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Jedis jedis = pool.getResource()) {
            // Walk all known sessions oldest-first; for each, check eligibility
            // in-process. With a small `limit` this is cheap; for very large
            // datasets, prefer the Mongo backend.
            List<String> ids = jedis.zrange(byTimeKey(), 0, -1);
            for (String id : ids) {
                if (rows.size() >= cap) break;
                Map<String, String> hash = jedis.hgetAll(sessionKey(id));
                if (hash.isEmpty()) continue;
                String outcome = hash.get("outcome");
                if (outcome == null || "IN_PROGRESS".equals(outcome)) continue;
                if (StringUtils.isNotBlank(hash.get("processedAt"))) continue;
                rows.add(toRow(id, hash));
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
            if (enrichment.intentLabel() != null) fields.put("intentLabel", enrichment.intentLabel().name());
            fields.put("intentConfidence", String.valueOf(enrichment.intentConfidence()));
            if (StringUtils.isNotBlank(enrichment.goalSummary())) fields.put("goalSummary", enrichment.goalSummary());
            if (enrichment.goalAchieved() != null) fields.put("goalAchieved", enrichment.goalAchieved().name());
            if (enrichment.sentiment() != null) fields.put("sentiment", enrichment.sentiment().name());
            if (enrichment.keyTerms() != null) {
                fields.put("keyTerms", String.join(",", enrichment.keyTerms()));
            }
            // T87 — per-turn sentiment trajectory as a CSV of enum names; absent
            // when the catalog-driven classifiers ran (empty list).
            if (enrichment.sentimentTrajectory() != null && !enrichment.sentimentTrajectory().isEmpty()) {
                fields.put(FIELD_SENTIMENT_TRAJECTORY, String.join(",",
                        enrichment.sentimentTrajectory().stream().map(Enum::name).toList()));
            }
            fields.put("processedAt", (enrichment.processedAt() == null
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
            List<String> ids = jedis.zrangeByScore(byTimeKey(),
                    String.valueOf(from.toEpochMilli()),
                    String.valueOf(to.toEpochMilli()));
            for (String id : ids) {
                Map<String, String> hash = jedis.hgetAll(sessionKey(id));
                if (hash.isEmpty()) continue;
                String startedAt = hash.get("startedAt");
                if (StringUtils.isBlank(startedAt)) continue;
                if (rateMetric && StringUtils.isBlank(hash.get("processedAt"))) continue;
                Instant when;
                try { when = Instant.parse(startedAt); } catch (Exception e) { continue; }
                long bucketKey = truncate(when, dailyBuckets);
                double[] acc = buckets.computeIfAbsent(bucketKey, k -> new double[]{0d, 0d, 0d});
                acc[0] += 1; // total (sessions)
                acc[1] += metricContribution(hash, validatedMetric);
                acc[2] += metricDivisorContribution(hash, validatedMetric);
            }
        } catch (Exception e) {
            log.warn("Chat analytics timeseries(Redis) failed: {}", e.getMessage());
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Long, double[]> entry : buckets.entrySet()) {
            int total = (int) entry.getValue()[0];
            double sum = entry.getValue()[1];
            double divisor = entry.getValue()[2];
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("bucket", Instant.ofEpochMilli(entry.getKey()).toString());
            row.put("total", total);
            row.put("value", switch (validatedMetric) {
                case "goal_achievement_rate", "negative_sentiment_rate" -> total > 0 ? sum / total : 0d;
                case "avg_duration_ms", "avg_tokens_out",
                     "tool_calls_per_session", "tool_errors_per_session",
                     "avg_tool_latency_ms" -> total > 0 ? sum / total : 0d;
                case "tool_error_rate_pct" -> divisor > 0 ? sum * 100d / divisor : 0d;
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
        int cap = Math.max(1, Math.min(maxRows, 100));

        // dim → aggregates: [sessions, durationSum, processedCount, achievedScore, negativeCount]
        Map<String, double[]> agg = new HashMap<>();
        // dim → (intent → count) for top-intent selection
        Map<String, Map<String, Integer>> intentCounts = new HashMap<>();

        try (Jedis jedis = pool.getResource()) {
            List<String> ids = jedis.zrangeByScore(byTimeKey(),
                    String.valueOf(from.toEpochMilli()),
                    String.valueOf(to.toEpochMilli()));
            for (String id : ids) {
                Map<String, String> hash = jedis.hgetAll(sessionKey(id));
                if (hash.isEmpty()) continue;
                // T74 — narrow the population before grouping. Mongo pushes
                // this into a $match; the Redis store filters in-process.
                if (!matchesScorecardFilters(hash, filters)) continue;
                String dim = hash.get(field);
                if (StringUtils.isBlank(dim)) dim = "unknown";
                double[] a = agg.computeIfAbsent(dim, k -> new double[5]);
                a[0] += 1;
                a[1] += parseLong(hash.get("durationMs"));
                if (StringUtils.isNotBlank(hash.get("processedAt"))) {
                    a[2] += 1;
                    String goal = hash.get("goalAchieved");
                    if ("YES".equals(goal)) a[3] += 1;
                    else if ("PARTIAL".equals(goal)) a[3] += 0.5;
                    String s = hash.get("sentiment");
                    if ("NEGATIVE".equals(s) || "FRUSTRATED".equals(s)) a[4] += 1;
                }
                String intent = hash.get("intentLabel");
                if (StringUtils.isNotBlank(intent)) {
                    intentCounts.computeIfAbsent(dim, k -> new HashMap<>())
                            .merge(intent, 1, Integer::sum);
                }
            }
        } catch (Exception e) {
            log.warn("Chat analytics scorecard(Redis) failed: {}", e.getMessage());
            return List.of();
        }

        return agg.entrySet().stream()
                .sorted((l, r) -> Double.compare(r.getValue()[0], l.getValue()[0]))
                .limit(cap)
                .map(e -> {
                    double[] a = e.getValue();
                    int sessions = (int) a[0];
                    int processed = (int) a[2];
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("bucket", e.getKey());
                    row.put("sessions", sessions);
                    row.put("avgDurationMs", sessions > 0 ? a[1] / sessions : 0d);
                    row.put("goalAchievedRate", processed > 0 ? a[3] / processed : 0d);
                    row.put("negativeRate", processed > 0 ? a[4] / processed : 0d);
                    String topIntent = intentCounts.getOrDefault(e.getKey(), Map.of())
                            .entrySet().stream()
                            .max((x, y) -> Integer.compare(x.getValue(), y.getValue()))
                            .map(Map.Entry::getKey).orElse("unknown");
                    row.put("topIntent", topIntent);
                    return row;
                })
                .toList();
    }

    @Override
    public List<Map<String, Object>> toolLatency(Instant from, Instant to, String agentId, int maxTools) {
        TurToolLatencyAccumulator acc = new TurToolLatencyAccumulator();
        try (Jedis jedis = pool.getResource()) {
            List<String> ids = jedis.zrangeByScore(byTimeKey(),
                    String.valueOf(from.toEpochMilli()),
                    String.valueOf(to.toEpochMilli()));
            for (String id : ids) {
                Map<String, String> hash = jedis.hgetAll(sessionKey(id));
                if (hash.isEmpty()) continue;
                if (StringUtils.isNotBlank(agentId) && !agentId.equals(hash.get("agentId"))) continue;
                String encoded = hash.get(FIELD_TOOL_LATENCIES);
                if (StringUtils.isBlank(encoded)) continue;
                decodeToolLatenciesInto(encoded, acc);
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
            List<String> ids = jedis.zrangeByScore(byTimeKey(),
                    "-inf",
                    "(" + threshold.toEpochMilli());
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
            if (sb.length() > 0) sb.append(';');
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
            if (token.isBlank()) continue;
            String[] parts = token.split("\\|", 3);
            if (parts.length != 3) continue;
            try {
                acc.add(parts[0], Long.parseLong(parts[1].trim()), "1".equals(parts[2].trim()));
            } catch (NumberFormatException ignored) {
                // skip a corrupt sample, keep the rest
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
            if (!TurChatAnalyticsStore.SCORECARD_FILTERABLE_FIELDS.contains(e.getKey())) continue;
            if (StringUtils.isBlank(e.getValue())) continue;
            if (!e.getValue().equals(hash.get(e.getKey()))) return false;
        }
        return true;
    }

    private static boolean matches(Map<String, String> hash, String agentId, String personaId, String outcome,
            String intentLabel, String goalAchieved, String sentiment) {
        if (StringUtils.isNotBlank(agentId)      && !agentId.equals(hash.get("agentId")))           return false;
        if (StringUtils.isNotBlank(personaId)    && !personaId.equals(hash.get("personaId")))       return false;
        if (StringUtils.isNotBlank(outcome)      && !outcome.equals(hash.get("outcome")))           return false;
        if (StringUtils.isNotBlank(intentLabel)  && !intentLabel.equals(hash.get("intentLabel")))   return false;
        if (StringUtils.isNotBlank(goalAchieved) && !goalAchieved.equals(hash.get("goalAchieved"))) return false;
        if (StringUtils.isNotBlank(sentiment)    && !sentiment.equals(hash.get("sentiment")))       return false;
        return true;
    }

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

    /** Truncates {@code when} to the start of its hour or day, returning epoch millis. */
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
            case "goal_achievement_rate" -> {
                String g = hash.get("goalAchieved");
                yield "YES".equals(g) ? 1d : "PARTIAL".equals(g) ? 0.5d : 0d;
            }
            case "negative_sentiment_rate" -> {
                String s = hash.get("sentiment");
                yield ("NEGATIVE".equals(s) || "FRUSTRATED".equals(s)) ? 1d : 0d;
            }
            case "avg_duration_ms"         -> parseLong(hash.get("durationMs"));
            case "avg_tokens_out"          -> parseLong(hash.get("totalTokensOut"));
            // The next three are per-session averages — divisor is bucket
            // session count, same family as avg_tokens_out / avg_duration_ms.
            // Each session contributes its own session-total to the sum.
            case "tool_calls_per_session"  -> parseLong(hash.get("totalToolCalls"));
            case "tool_errors_per_session" -> parseLong(hash.get("totalToolErrors"));
            case "avg_tool_latency_ms"     -> parseLong(hash.get("totalToolLatencyMs"));
            // tool_error_rate_pct is errors / calls × 100 — numerator is the
            // session's tool errors; divisor is filled by metricDivisorContribution.
            case "tool_error_rate_pct"     -> parseLong(hash.get("totalToolErrors"));
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
        if ("tool_error_rate_pct".equals(metric)) {
            return parseLong(hash.get("totalToolCalls"));
        }
        return 0d;
    }

    private static Map<String, Object> toRow(String id, Map<String, String> hash) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("conversationId",   id);
        row.put("agentId",          hash.get("agentId"));
        row.put("personaId",        hash.get("personaId"));
        row.put("llmInstanceId",    hash.get("llmInstanceId"));
        row.put("embeddingModelId", hash.get("embeddingModelId"));
        row.put("storeInstanceId",  hash.get("storeInstanceId"));
        row.put("userId",           hash.get("userId"));
        row.put("locale",           hash.get("locale"));
        // T74 — visitor cohort dimensions.
        row.put("timezone",         hash.get("timezone"));
        row.put("deviceType",       hash.get("deviceType"));
        row.put("outcome",          hash.get("outcome"));
        row.put("startedAt",        hash.get("startedAt"));
        row.put("completedAt",      hash.get("completedAt"));
        row.put("totalTurns",         parseLong(hash.get("totalTurns")));
        row.put("totalTokensIn",      parseLong(hash.get("totalTokensIn")));
        row.put("totalTokensOut",     parseLong(hash.get("totalTokensOut")));
        row.put("totalToolCalls",     parseLong(hash.get("totalToolCalls")));
        row.put("totalToolErrors",    parseLong(hash.get("totalToolErrors")));
        row.put("totalToolLatencyMs", parseLong(hash.get("totalToolLatencyMs")));
        row.put("experimentKey",      hash.get("experimentKey"));
        row.put("variantLabel",       hash.get("variantLabel"));
        // T110 — child-session linkage to parent conversation.
        row.put("parentConversationId", hash.get("parentConversationId"));
        row.put("durationMs",         parseLong(hash.get("durationMs")));
        row.put("firstUserMessage", hash.get("firstUserMessage"));
        // Enrichment columns — null until the scheduled enricher runs.
        row.put("intentLabel",      hash.get("intentLabel"));
        String confidence = hash.get("intentConfidence");
        row.put("intentConfidence", StringUtils.isBlank(confidence) ? null : parseDouble(confidence));
        row.put("goalSummary",      hash.get("goalSummary"));
        row.put("goalAchieved",     hash.get("goalAchieved"));
        row.put("sentiment",        hash.get("sentiment"));
        String keyTerms = hash.get("keyTerms");
        row.put("keyTerms",         StringUtils.isBlank(keyTerms) ? null : List.of(keyTerms.split(",")));
        // T87 — per-turn sentiment trajectory (CSV of enum names), null until enriched.
        String trajectory = hash.get(FIELD_SENTIMENT_TRAJECTORY);
        row.put(FIELD_SENTIMENT_TRAJECTORY,
                StringUtils.isBlank(trajectory) ? null : List.of(trajectory.split(",")));
        row.put("processedAt",      hash.get("processedAt"));
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
