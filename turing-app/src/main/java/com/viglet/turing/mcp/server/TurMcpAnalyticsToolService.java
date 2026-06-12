/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.mcp.server;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.metric.TurSNSiteMetricAccessRepository;
import com.viglet.turing.persistence.repository.sn.metric.TurSNSiteMetricAccessTerm;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * T252 / §XIII.4 — search-log analytics published as MCP tools, so a content or
 * product team can ask "what are users searching that we can't answer?" straight
 * from their AI client and turn search logs into a content backlog without a BI
 * project.
 *
 * <p>Three tools, all site-scoped and backed by the existing
 * {@link TurSNSiteMetricAccessRepository} search-access log (no new telemetry,
 * no Mongo/Redis dependency — works on the default install):
 * <ul>
 *   <li>{@code search_metrics} — top queries with how often they ran and their
 *       average result count, over a period;</li>
 *   <li>{@code top_failed_searches} — queries that always returned zero results
 *       (the literal failures);</li>
 *   <li>{@code content_gaps} — high-demand, low-coverage queries (searched often
 *       but returning few results on average) — what to write next.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurMcpAnalyticsToolService {

    private static final int DEFAULT_ROWS = 10;
    private static final int MAX_ROWS = 50;
    /** A query is a "content gap" when its average result count is below this. */
    private static final double LOW_COVERAGE_THRESHOLD = 3.0;
    /** How wide to scan top queries when filtering for low coverage. */
    private static final int GAP_SCAN_MULTIPLIER = 5;

    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNSiteMetricAccessRepository metricAccessRepository;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    public TurMcpAnalyticsToolService(TurSNSiteRepository turSNSiteRepository,
            TurSNSiteMetricAccessRepository metricAccessRepository) {
        this.turSNSiteRepository = turSNSiteRepository;
        this.metricAccessRepository = metricAccessRepository;
    }

    // ==================== search_metrics ====================

    @Tool(name = "search_metrics", description = ".")
    public String searchMetrics(String site, String period, Integer rows) {
        TurSNSite snSite = resolveSite(site);
        if (snSite == null) {
            return siteNotFound(site);
        }
        int limit = clamp(rows);
        Instant since = periodStart(period);
        List<TurSNSiteMetricAccessTerm> terms;
        int totalSearches;
        if (since == null) {
            terms = metricAccessRepository.topTerms(snSite, PageRequest.of(0, limit));
            totalSearches = metricAccessRepository.countTerms(snSite);
        } else {
            Instant now = Instant.now();
            terms = metricAccessRepository.topTermsBetweenDates(snSite, since, now, PageRequest.of(0, limit));
            totalSearches = metricAccessRepository.countTermsByPeriod(snSite, since, now);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("site", snSite.getName());
        result.put("period", period == null || period.isBlank() ? "all-time" : period);
        result.put("totalSearches", totalSearches);
        result.put("topTerms", termList(terms));
        return toJson("Search metrics for '%s'.".formatted(snSite.getName()), result);
    }

    // ==================== top_failed_searches ====================

    @Tool(name = "top_failed_searches", description = ".")
    public String topFailedSearches(String site, Integer rows) {
        TurSNSite snSite = resolveSite(site);
        if (snSite == null) {
            return siteNotFound(site);
        }
        List<TurSNSiteMetricAccessTerm> terms =
                metricAccessRepository.topZeroResultTerms(snSite, PageRequest.of(0, clamp(rows)));
        if (terms.isEmpty()) {
            return "No zero-result searches recorded for site '%s'. Either everything users searched "
                    .formatted(snSite.getName()) + "returned hits, or search-metrics logging is disabled.";
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("site", snSite.getName());
        result.put("failedSearches", termList(terms));
        return toJson("Top zero-result searches for '%s'.".formatted(snSite.getName()), result);
    }

    // ==================== content_gaps ====================

    @Tool(name = "content_gaps", description = ".")
    public String contentGaps(String site, Integer rows) {
        TurSNSite snSite = resolveSite(site);
        if (snSite == null) {
            return siteNotFound(site);
        }
        int limit = clamp(rows);
        // Scan a wider window of popular queries, then keep the high-demand ones
        // whose average result count is low — searched a lot, answered poorly.
        List<TurSNSiteMetricAccessTerm> popular =
                metricAccessRepository.topTerms(snSite, PageRequest.of(0, limit * GAP_SCAN_MULTIPLIER));
        List<Map<String, Object>> gaps = new ArrayList<>();
        for (TurSNSiteMetricAccessTerm term : popular) {
            if (term.getNumFound() < LOW_COVERAGE_THRESHOLD) {
                gaps.add(term(term));
                if (gaps.size() >= limit) {
                    break;
                }
            }
        }
        if (gaps.isEmpty()) {
            return "No content gaps detected for site '%s' (popular queries are returning enough results)."
                    .formatted(snSite.getName());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("site", snSite.getName());
        result.put("threshold", "avgResults < " + (int) LOW_COVERAGE_THRESHOLD);
        result.put("gaps", gaps);
        return toJson("High-demand, low-coverage queries for '%s' — candidate content to create."
                .formatted(snSite.getName()), result);
    }

    // ==================== helpers ====================

    private TurSNSite resolveSite(String site) {
        if (site == null || site.isBlank()) {
            return null;
        }
        return turSNSiteRepository.findByNameIgnoreCase(site).orElse(null);
    }

    private String siteNotFound(String site) {
        return "Error: site not found: '" + site + "' (use list_sites to discover sites).";
    }

    private Instant periodStart(String period) {
        if (period == null || period.isBlank() || "all-time".equalsIgnoreCase(period)) {
            return null;
        }
        Instant now = Instant.now();
        return switch (period.toLowerCase()) {
            case "today" -> now.minus(1, ChronoUnit.DAYS);
            case "this-week", "week" -> now.minus(7, ChronoUnit.DAYS);
            case "this-month", "month" -> now.minus(30, ChronoUnit.DAYS);
            default -> null;
        };
    }

    private List<Map<String, Object>> termList(List<TurSNSiteMetricAccessTerm> terms) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (TurSNSiteMetricAccessTerm term : terms) {
            out.add(term(term));
        }
        return out;
    }

    private Map<String, Object> term(TurSNSiteMetricAccessTerm term) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("term", term.getTerm());
        m.put("searches", term.getTotal());
        m.put("avgResults", (long) term.getNumFound());
        return m;
    }

    private int clamp(Integer rows) {
        if (rows == null || rows < 1) {
            return DEFAULT_ROWS;
        }
        return Math.min(rows, MAX_ROWS);
    }

    private String toJson(String header, Map<String, Object> body) {
        try {
            return header + "\n" + objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            log.warn("[MCP] analytics serialization failed", e);
            return header;
        }
    }
}
