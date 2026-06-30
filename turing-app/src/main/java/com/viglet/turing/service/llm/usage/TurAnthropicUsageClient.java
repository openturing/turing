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

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.service.llm.usage.TurAnthropicUsageParser.CostRow;
import com.viglet.turing.service.llm.usage.TurAnthropicUsageParser.UsageRow;

import lombok.extern.slf4j.Slf4j;

/**
 * T183 / §X.14.c — HTTP client for the Anthropic Admin API usage + cost reports.
 * Needs an <strong>Admin</strong> API key (distinct from the model keys stored
 * per LLM instance), supplied via {@code turing.usage.anthropic.admin-api-key}.
 * Fail-open: a missing key, non-2xx response, or transport error yields empty
 * lists so the nightly import is a no-op rather than an error.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurAnthropicUsageClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final TurAnthropicUsageParser parser = new TurAnthropicUsageParser();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    private final String baseUrl;
    private final String adminApiKey;

    public TurAnthropicUsageClient(
            @Value("${turing.usage.anthropic.base-url:https://api.anthropic.com}") String baseUrl,
            @Value("${turing.usage.anthropic.admin-api-key:}") String adminApiKey) {
        this.baseUrl = StringUtils.hasText(baseUrl) ? baseUrl.replaceAll("/+$", "")
                : "https://api.anthropic.com";
        this.adminApiKey = adminApiKey == null ? "" : adminApiKey.trim();
    }

    /** A day window's usage + cost rows as parsed from the two Admin reports. */
    public record Report(List<UsageRow> usage, List<CostRow> cost) {
        static Report empty() {
            return new Report(List.of(), List.of());
        }
    }

    public boolean isConfigured() {
        return StringUtils.hasText(adminApiKey);
    }

    /**
     * Fetch usage + cost for the {@code [from, to]} day window (inclusive),
     * bucketed daily and grouped by model / workspace / service tier where the
     * endpoint supports it. Fail-open to empty on any error.
     */
    public Report fetch(LocalDate from, LocalDate to) {
        if (!isConfigured()) {
            return Report.empty();
        }
        try {
            String startingAt = from.atStartOfDay(ZoneOffset.UTC).toInstant().toString();
            // ending_at is exclusive on the Admin API → start of the day after `to`.
            String endingAt = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toString();

            String usageJson = get("/v1/organizations/usage_report/messages",
                    startingAt, endingAt, List.of("model", "workspace_id", "service_tier"));
            String costJson = get("/v1/organizations/cost_report",
                    startingAt, endingAt, List.of("workspace_id"));

            return new Report(parser.parseUsage(usageJson), parser.parseCost(costJson));
        } catch (RuntimeException e) {
            log.warn("[Usage][Anthropic] usage import failed — failing open: {}", e.getMessage());
            return Report.empty();
        }
    }

    private String get(String path, String startingAt, String endingAt, List<String> groupBy) {
        StringBuilder url = new StringBuilder(baseUrl).append(path)
                .append("?starting_at=").append(enc(startingAt))
                .append("&ending_at=").append(enc(endingAt))
                .append("&bucket_width=1d");
        for (String group : groupBy) {
            url.append("&group_by[]=").append(enc(group));
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .timeout(TIMEOUT)
                .header("x-api-key", adminApiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("[Usage][Anthropic] {} returned HTTP {}", path, response.statusCode());
                return "";
            }
            return response.body();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Anthropic usage request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Anthropic usage request interrupted", e);
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
