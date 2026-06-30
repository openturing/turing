/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.llm.usage;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * T183 / §X.14.c — pure, dependency-free parser for the Anthropic Admin API
 * <em>usage report</em> ({@code /v1/organizations/usage_report/messages}) and
 * <em>cost report</em> ({@code /v1/organizations/cost_report}) JSON. Kept
 * separate from the HTTP client so the wire-shape handling is unit-testable
 * without a network.
 *
 * <p>Both endpoints return a {@code data[]} of time buckets, each carrying a
 * {@code starting_at} instant and a {@code results[]} of grouped figures. The
 * parser is deliberately tolerant of field-name variants seen across API
 * revisions (e.g. {@code uncached_input_tokens} + cache token fields vs. a flat
 * {@code input_tokens}; {@code amount} as string or number) and skips
 * un-parseable rows rather than failing the whole import.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
public final class TurAnthropicUsageParser {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    /** Token consumption for one (day × workspace × model × service tier) slice. */
    public record UsageRow(LocalDate date, String workspaceId, String model, String serviceTier,
            long inputTokens, long outputTokens) {
    }

    /** USD cost for one (day × workspace × model × service tier) slice. */
    public record CostRow(LocalDate date, String workspaceId, String model, String serviceTier,
            double costUsd) {
    }

    public List<UsageRow> parseUsage(String json) {
        List<UsageRow> rows = new ArrayList<>();
        for (Bucket bucket : buckets(json)) {
            for (JsonNode result : bucket.results()) {
                long input = firstLong(result, "uncached_input_tokens", "input_tokens")
                        + asLong(result.path("cache_creation_input_tokens"))
                        + asLong(result.path("cache_read_input_tokens"));
                long output = firstLong(result, "output_tokens");
                if (input == 0 && output == 0) {
                    continue;
                }
                rows.add(new UsageRow(bucket.date(), text(result, "workspace_id"),
                        text(result, "model"), text(result, "service_tier"), input, output));
            }
        }
        return rows;
    }

    public List<CostRow> parseCost(String json) {
        List<CostRow> rows = new ArrayList<>();
        for (Bucket bucket : buckets(json)) {
            for (JsonNode result : bucket.results()) {
                double amount = asDouble(result.path("amount"));
                if (amount == 0.0) {
                    continue;
                }
                rows.add(new CostRow(bucket.date(), text(result, "workspace_id"),
                        text(result, "model"), text(result, "service_tier"), amount));
            }
        }
        return rows;
    }

    private record Bucket(LocalDate date, List<JsonNode> results) {
    }

    private List<Bucket> buckets(String json) {
        List<Bucket> buckets = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return buckets;
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (RuntimeException e) {
            log.warn("[Usage][Anthropic] could not parse report JSON: {}", e.getMessage());
            return buckets;
        }
        JsonNode data = root.path("data");
        if (!data.isArray()) {
            return buckets;
        }
        for (JsonNode bucket : data) {
            LocalDate date = parseDate(bucket.path("starting_at").asString(null));
            if (date == null) {
                continue;
            }
            JsonNode results = bucket.path("results");
            List<JsonNode> resultList = new ArrayList<>();
            if (results.isArray()) {
                results.forEach(resultList::add);
            }
            buckets.add(new Bucket(date, resultList));
        }
        return buckets;
    }

    private static LocalDate parseDate(String startingAt) {
        if (startingAt == null || startingAt.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(startingAt).atZone(ZoneOffset.UTC).toLocalDate();
        } catch (DateTimeParseException e) {
            try {
                return LocalDate.parse(startingAt.substring(0, 10));
            } catch (RuntimeException ignored) {
                return null;
            }
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asString();
    }

    private static long firstLong(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isNumber()) {
                return value.asLong();
            }
        }
        return 0L;
    }

    private static long asLong(JsonNode node) {
        return node.isNumber() ? node.asLong() : 0L;
    }

    private static double asDouble(JsonNode node) {
        if (node.isNumber()) {
            return node.asDouble();
        }
        if (node.isString()) {
            try {
                return Double.parseDouble(node.asString().trim());
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
        return 0.0;
    }
}
