/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.llm.advisor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.viglet.turing.genai.provider.llm.TurLlmModelCatalog;
import com.viglet.turing.genai.provider.llm.TurLlmModelKind;
import com.viglet.turing.genai.provider.llm.TurLlmModelMetadata;
import com.viglet.turing.genai.provider.llm.TurLlmModelOption;

/**
 * T785 / §LIII.3 (Block BE) — the "find me a model" advisor: a constraint-driven
 * recommender over the public model catalog. Given required capabilities/
 * modalities, a minimum context window, a budget ceiling, and a minimum tier /
 * intelligence index, it filters the catalog and ranks the survivors so an
 * operator can describe what they need ("vision, ≥128k context, ≤ $5/1M, ≥ High
 * tier") and get a ranked shortlist to one-click into a new instance.
 *
 * <p>Pure over catalog metadata (T776) — no LLM call, deterministic, so it's a
 * cheap onboarding + governance lever. Ranking favours the most capable model
 * that fits: intelligence index desc, then cheaper input price, then larger
 * context.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Service
public class TurLLMModelAdvisorService {

    /** Frontier &gt; High &gt; Mid &gt; Light; unknown tiers rank below Light. */
    private static final List<String> TIER_ORDER = List.of("light", "mid", "high", "frontier");
    private static final int DEFAULT_LIMIT = 10;

    private final TurLlmModelCatalog modelCatalog;

    public TurLLMModelAdvisorService(TurLlmModelCatalog modelCatalog) {
        this.modelCatalog = modelCatalog;
    }

    /**
     * A constraint set. Every field is optional; a {@code null}/empty field is not
     * constrained. {@code kind} defaults to {@link TurLlmModelKind#CHAT} when null.
     */
    public record AdvisorQuery(
            List<String> requiredCapabilities,
            List<String> requiredInputModalities,
            Integer minContextWindow,
            Double maxInputPricePer1M,
            Double minIntelligenceIndex,
            String minTier,
            TurLlmModelKind kind,
            Integer limit) {
    }

    /** A ranked recommendation. */
    public record Recommendation(
            String vendorId,
            String modelId,
            String label,
            String tier,
            Integer contextWindow,
            Double inputPricePer1M,
            Double intelligenceIndex,
            List<String> capabilities) {
    }

    /** Rank the catalog models that satisfy the query. Empty when none qualify. */
    public List<Recommendation> recommend(AdvisorQuery query) {
        TurLlmModelKind kind = query.kind() == null ? TurLlmModelKind.CHAT : query.kind();
        Set<String> requiredCaps = lowerSet(query.requiredCapabilities());
        Set<String> requiredModalities = lowerSet(query.requiredInputModalities());
        int minTierRank = tierRank(query.minTier());
        int limit = query.limit() != null && query.limit() > 0 ? query.limit() : DEFAULT_LIMIT;

        List<Recommendation> matches = new ArrayList<>();
        modelCatalog.allModels().forEach((vendor, options) -> {
            for (TurLlmModelOption option : options) {
                if (option.kind() == kind && satisfies(option, query, requiredCaps, requiredModalities, minTierRank)) {
                    matches.add(toRecommendation(vendor, option));
                }
            }
        });
        matches.sort(TurLLMModelAdvisorService::compareRecommendations);
        return matches.size() > limit ? matches.subList(0, limit) : matches;
    }

    private boolean satisfies(TurLlmModelOption option, AdvisorQuery query,
            Set<String> requiredCaps, Set<String> requiredModalities, int minTierRank) {
        TurLlmModelMetadata meta = option.metadata();
        if (meta == null) {
            // No metadata → can't prove it satisfies any positive constraint. Only
            // admit it when the query has no constraints at all.
            return requiredCaps.isEmpty() && requiredModalities.isEmpty()
                    && query.minContextWindow() == null && query.maxInputPricePer1M() == null
                    && query.minIntelligenceIndex() == null && minTierRank <= 0;
        }
        if (!lowerSet(meta.capabilities()).containsAll(requiredCaps)) {
            return false;
        }
        if (!requiredModalities.isEmpty()) {
            Set<String> inputs = lowerSet(meta.modalities() == null ? null : meta.modalities().input());
            if (!inputs.containsAll(requiredModalities)) {
                return false;
            }
        }
        if (query.minContextWindow() != null
                && (meta.contextWindow() == null || meta.contextWindow() < query.minContextWindow())) {
            return false;
        }
        if (query.maxInputPricePer1M() != null) {
            Double price = inputPrice(meta);
            if (price == null || price > query.maxInputPricePer1M()) {
                return false;
            }
        }
        if (query.minIntelligenceIndex() != null) {
            Double ii = intelligence(meta);
            if (ii == null || ii < query.minIntelligenceIndex()) {
                return false;
            }
        }
        return tierRank(meta.tier()) >= minTierRank;
    }

    private Recommendation toRecommendation(String vendor, TurLlmModelOption option) {
        TurLlmModelMetadata meta = option.metadata();
        return new Recommendation(
                vendor,
                option.id(),
                option.label(),
                meta == null ? null : meta.tier(),
                meta == null ? null : meta.contextWindow(),
                inputPrice(meta),
                intelligence(meta),
                meta == null ? List.of() : (meta.capabilities() == null ? List.of() : meta.capabilities()));
    }

    /** Best first: intelligence desc, then cheaper input price, then larger context. */
    private static int compareRecommendations(Recommendation a, Recommendation b) {
        int byIntelligence = Double.compare(nz(b.intelligenceIndex(), -1), nz(a.intelligenceIndex(), -1));
        if (byIntelligence != 0) {
            return byIntelligence;
        }
        int byPrice = Double.compare(nz(a.inputPricePer1M(), Double.MAX_VALUE), nz(b.inputPricePer1M(), Double.MAX_VALUE));
        if (byPrice != 0) {
            return byPrice;
        }
        return Integer.compare(nz(b.contextWindow()), nz(a.contextWindow()));
    }

    private static Set<String> lowerSet(List<String> values) {
        if (values == null) {
            return Set.of();
        }
        return values.stream().filter(v -> v != null && !v.isBlank())
                .map(v -> v.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    }

    /** Tier → rank (higher is better); 0 for null/unknown (no minimum applied). */
    private static int tierRank(String tier) {
        if (tier == null || tier.isBlank()) {
            return 0;
        }
        int idx = TIER_ORDER.indexOf(tier.trim().toLowerCase(Locale.ROOT));
        return idx < 0 ? 0 : idx + 1;
    }

    private static Double inputPrice(TurLlmModelMetadata meta) {
        return meta == null || meta.pricing() == null ? null : meta.pricing().inputPer1M();
    }

    private static Double intelligence(TurLlmModelMetadata meta) {
        return meta == null || meta.benchmarks() == null ? null : meta.benchmarks().intelligenceIndex();
    }

    private static double nz(Double value, double fallback) {
        return value == null ? fallback : value;
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }
}
