/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.llm.cost;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMTokenUsageRepository;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * T290 / §XVI.2 (Block L) — the live, all-provider AI-spend dashboard backend.
 * Reads the frozen per-turn {@code cost_usd} stamped by
 * {@link com.viglet.turing.service.llm.tokenusage.TurLLMTokenUsageService} and
 * rolls it up per agent / model / stage / tenant plus a daily time-series.
 *
 * <p>Complements (does not duplicate) the F.13 vendor-Usage import: where both
 * exist, the vendor number is the source of truth and this live number is the
 * early-warning estimate. Always-on — backed by the relational
 * {@code llm_token_usage} table, so it works without Mongo/Redis analytics.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@RestController
@RequestMapping("/api/v2/llm/cost")
@Tag(name = "LLM Cost Governance", description = "Block L — live AI-spend dashboard")
public class TurLLMCostAPI {

    private static final int DEFAULT_WINDOW_DAYS = 30;

    private final TurLLMTokenUsageRepository tokenUsageRepository;
    private final TurAIAgentRepository agentRepository;

    public TurLLMCostAPI(TurLLMTokenUsageRepository tokenUsageRepository,
            TurAIAgentRepository agentRepository) {
        this.tokenUsageRepository = tokenUsageRepository;
        this.agentRepository = agentRepository;
    }

    public record AgentCostRow(String agentId, String agentTitle,
            double costUsd, long totalTokens, long requestCount) {
    }

    public record ModelCostRow(String vendorId, String modelName,
            double costUsd, long totalTokens, long requestCount) {
    }

    public record StageCostRow(String stage,
            double costUsd, long totalTokens, long requestCount) {
    }

    public record TenantCostRow(String tenantId,
            double costUsd, long totalTokens, long requestCount) {
    }

    public record DailyCostPoint(String date,
            double costUsd, long totalTokens, long requestCount) {
    }

    public record CostReport(
            String periodStart,
            String periodEnd,
            double totalCostUsd,
            long totalTokens,
            long totalRequests,
            List<AgentCostRow> byAgent,
            List<ModelCostRow> byModel,
            List<StageCostRow> byStage,
            List<TenantCostRow> byTenant) {
    }

    @GetMapping("/summary")
    public CostReport summary(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        LocalDateTime start = parseFrom(from);
        LocalDateTime end = parseTo(to);

        Map<String, String> agentTitles = agentRepository.findAll().stream()
                .collect(Collectors.toMap(TurAIAgent::getId, a ->
                        a.getTitle() == null ? a.getId() : a.getTitle(), (x, y) -> x));

        List<AgentCostRow> byAgent = new ArrayList<>();
        double totalCost = 0;
        long totalTokens = 0;
        long totalReqs = 0;
        for (Object[] row : tokenUsageRepository.findCostByAgent(start, end)) {
            String agentId = (String) row[0];
            double cost = num(row[1]);
            long tokens = lng(row[2]);
            long reqs = lng(row[3]);
            byAgent.add(new AgentCostRow(agentId,
                    agentId == null ? "(non-agent)" : agentTitles.getOrDefault(agentId, agentId),
                    cost, tokens, reqs));
            // The per-agent grouping is exhaustive over all rows → use it for the totals.
            totalCost += cost;
            totalTokens += tokens;
            totalReqs += reqs;
        }

        List<ModelCostRow> byModel = new ArrayList<>();
        for (Object[] row : tokenUsageRepository.findCostByModel(start, end)) {
            byModel.add(new ModelCostRow((String) row[0], (String) row[1],
                    num(row[2]), lng(row[3]), lng(row[4])));
        }

        List<StageCostRow> byStage = new ArrayList<>();
        for (Object[] row : tokenUsageRepository.findCostByStage(start, end)) {
            byStage.add(new StageCostRow((String) row[0], num(row[1]), lng(row[2]), lng(row[3])));
        }

        List<TenantCostRow> byTenant = new ArrayList<>();
        for (Object[] row : tokenUsageRepository.findCostByTenant(start, end)) {
            byTenant.add(new TenantCostRow((String) row[0], num(row[1]), lng(row[2]), lng(row[3])));
        }

        return new CostReport(
                start.toLocalDate().toString(),
                end.toLocalDate().minusDays(1).toString(),
                totalCost, totalTokens, totalReqs,
                byAgent, byModel, byStage, byTenant);
    }

    @GetMapping("/timeseries")
    public List<DailyCostPoint> timeseries(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        LocalDateTime start = parseFrom(from);
        LocalDateTime end = parseTo(to);
        List<DailyCostPoint> points = new ArrayList<>();
        for (Object[] row : tokenUsageRepository.findDailyCostTimeseries(start, end)) {
            points.add(new DailyCostPoint(row[0].toString(),
                    num(row[1]), lng(row[2]), lng(row[3])));
        }
        return points;
    }

    private LocalDateTime parseFrom(String from) {
        if (from != null && !from.isBlank()) {
            return LocalDate.parse(from, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
        }
        return LocalDate.now().minusDays(DEFAULT_WINDOW_DAYS).atStartOfDay();
    }

    private LocalDateTime parseTo(String to) {
        // Exclusive upper bound at the START of the day AFTER `to` so the whole
        // `to` day is included; default = start of tomorrow (covers all of today).
        if (to != null && !to.isBlank()) {
            return LocalDate.parse(to, DateTimeFormatter.ISO_LOCAL_DATE).plusDays(1).atTime(LocalTime.MIN);
        }
        return LocalDate.now().plusDays(1).atStartOfDay();
    }

    private static double num(Object o) {
        return o == null ? 0.0 : ((Number) o).doubleValue();
    }

    private static long lng(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }
}
