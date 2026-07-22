/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.llm.rightsizing;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.viglet.turing.genai.provider.llm.TurLlmModelCatalog;
import com.viglet.turing.genai.provider.llm.TurLlmModelMetadata;
import com.viglet.turing.genai.provider.llm.TurLlmModelOption;
import com.viglet.turing.persistence.repository.llm.TurLLMTokenUsageRepository;
import com.viglet.turing.service.llm.lifecycle.TurLLMModelLifecycleService;
import com.viglet.turing.service.llm.price.TurLLMPriceService;

import lombok.extern.slf4j.Slf4j;

/**
 * T786 / §LIII.3 (Block BE) — right-sizing / savings advisor. Runs the model
 * loop backwards: from each agent's <em>observed</em> usage (its dominant model,
 * request count, and average input/output tokens per turn from the T289
 * {@code llm_token_usage} ledger) it looks for a cheaper same-vendor catalog
 * model that still fits — big enough context for the observed turn size and
 * covering the current model's declared capabilities — and reports the projected
 * monthly savings.
 *
 * <p><b>Signal caveat.</b> Turing meters tokens/cost per turn but not which
 * capabilities (tools/vision) a turn actually exercised, so "capabilities
 * actually exercised" is approximated conservatively by the current model's
 * declared catalog capabilities — a suggestion never drops a capability the model
 * advertised, even if unused. Deprecated models are never suggested.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurLLMRightsizingService {

    private static final int DEFAULT_WINDOW_DAYS = 30;
    /** Safety margin on context: a candidate must hold the observed turn ×this. */
    private static final double CONTEXT_SAFETY_FACTOR = 1.5;
    private static final double DAYS_PER_MONTH = 30.0;

    private final TurLLMTokenUsageRepository usageRepository;
    private final TurLLMPriceService priceService;
    private final TurLlmModelCatalog modelCatalog;

    public TurLLMRightsizingService(TurLLMTokenUsageRepository usageRepository,
            TurLLMPriceService priceService, TurLlmModelCatalog modelCatalog) {
        this.usageRepository = usageRepository;
        this.priceService = priceService;
        this.modelCatalog = modelCatalog;
    }

    /** A cheaper-model suggestion for one agent, with projected monthly savings. */
    public record RightsizingSuggestion(
            String agentId,
            String currentVendorId,
            String currentModelName,
            String suggestedModelId,
            String suggestedLabel,
            long requestCount,
            long avgInputTokens,
            long avgOutputTokens,
            double currentMonthlyCostUsd,
            double projectedMonthlyCostUsd,
            double projectedMonthlySavingsUsd,
            double savingsPercent) {
    }

    /** Right-sizing suggestions over the last {@code windowDays} (default 30). */
    public List<RightsizingSuggestion> suggest(Integer windowDays) {
        int days = windowDays != null && windowDays > 0 ? windowDays : DEFAULT_WINDOW_DAYS;
        LocalDateTime end = LocalDate.now(ZoneId.systemDefault()).plusDays(1).atStartOfDay();
        LocalDateTime start = end.minusDays(days);

        List<RightsizingSuggestion> out = new ArrayList<>();
        Set<String> seenAgents = new HashSet<>();
        // Rows are cost-desc, so the first row per agent is its dominant model.
        for (Object[] row : usageRepository.findUsageByAgentAndModel(start, end)) {
            String agentId = (String) row[0];
            if (agentId == null || !seenAgents.add(agentId)) {
                continue;
            }
            suggestionFor(row, days).ifPresent(out::add);
        }
        return out;
    }

    private java.util.Optional<RightsizingSuggestion> suggestionFor(Object[] row, int days) {
        String agentId = (String) row[0];
        String vendorId = (String) row[1];
        String modelName = (String) row[2];
        double windowCost = toDouble(row[3]);
        long inputTokens = toLong(row[4]);
        long outputTokens = toLong(row[5]);
        long requestCount = toLong(row[6]);
        if (requestCount <= 0 || windowCost <= 0 || vendorId == null || modelName == null) {
            return java.util.Optional.empty();
        }

        long avgIn = inputTokens / requestCount;
        long avgOut = outputTokens / requestCount;
        double currentPerReq = priceService.computeCost(vendorId, modelName, avgIn, avgOut);
        if (currentPerReq <= 0) {
            return java.util.Optional.empty(); // unpriced / local — nothing to right-size
        }

        List<TurLlmModelOption> vendorModels = modelCatalog.allModels()
                .getOrDefault(vendorId.toLowerCase(Locale.ROOT), List.of());
        TurLlmModelOption current = vendorModels.stream()
                .filter(o -> modelName.equals(o.id())).findFirst().orElse(null);
        long minContext = Math.round((avgIn + avgOut) * CONTEXT_SAFETY_FACTOR);

        TurLlmModelOption best = cheapestFit(vendorId, vendorModels, current, minContext,
                avgIn, avgOut, currentPerReq);
        if (best == null) {
            return java.util.Optional.empty();
        }

        double candidatePerReq = priceService.computeCost(vendorId, best.id(), avgIn, avgOut);
        double monthlyCurrent = windowCost * (DAYS_PER_MONTH / days);
        double savingsRatio = 1.0 - (candidatePerReq / currentPerReq);
        double monthlySavings = monthlyCurrent * savingsRatio;
        double projectedMonthly = monthlyCurrent - monthlySavings;

        return java.util.Optional.of(new RightsizingSuggestion(
                agentId, vendorId, modelName, best.id(), best.label(),
                requestCount, avgIn, avgOut,
                round(monthlyCurrent), round(projectedMonthly), round(monthlySavings),
                round(savingsRatio * 100.0)));
    }

    /**
     * The cheapest same-kind, non-deprecated candidate that covers the current
     * model's capabilities, holds the observed turn size, and is strictly cheaper
     * per turn than the current model. {@code null} when none qualifies.
     */
    private TurLlmModelOption cheapestFit(String vendorId, List<TurLlmModelOption> vendorModels,
            TurLlmModelOption current, long minContext, long avgIn, long avgOut, double currentPerReq) {
        Set<String> requiredCaps = capabilities(current == null ? null : current.metadata());
        TurLlmModelOption best = null;
        double bestPerReq = currentPerReq;
        for (TurLlmModelOption candidate : vendorModels) {
            if (!qualifies(candidate, current, requiredCaps, minContext)) {
                continue;
            }
            double candidatePerReq = priceService.computeCost(vendorId, candidate.id(), avgIn, avgOut);
            if (candidatePerReq > 0 && candidatePerReq < bestPerReq) {
                best = candidate;
                bestPerReq = candidatePerReq;
            }
        }
        return best;
    }

    /** A candidate is eligible: a distinct, same-kind, GA model that covers the required capabilities and holds the observed turn size. */
    private static boolean qualifies(TurLlmModelOption candidate, TurLlmModelOption current,
            Set<String> requiredCaps, long minContext) {
        if (current != null && (candidate.id().equals(current.id()) || candidate.kind() != current.kind())) {
            return false;
        }
        TurLlmModelMetadata meta = candidate.metadata();
        if (TurLLMModelLifecycleService.isEndOfLife(meta) || !capabilities(meta).containsAll(requiredCaps)) {
            return false;
        }
        return meta == null || meta.contextWindow() == null || meta.contextWindow() >= minContext;
    }

    private static Set<String> capabilities(TurLlmModelMetadata meta) {
        if (meta == null || meta.capabilities() == null) {
            return Set.of();
        }
        Set<String> caps = new HashSet<>();
        for (String c : meta.capabilities()) {
            if (c != null) {
                caps.add(c.toLowerCase(Locale.ROOT));
            }
        }
        return caps;
    }

    private static double toDouble(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0.0;
    }

    private static long toLong(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }

    private static double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
