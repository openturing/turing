/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.catalog.planning;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.model.sn.genai.TurCopilotPlanningStrategy;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.properties.TurCatalogCopilotProperty;

import lombok.extern.slf4j.Slf4j;

/**
 * T818 / §LIX.1 (Block BK) — resolves which {@link TurCopilotQueryPlanner} (and which
 * analysis depth) a given SN site's copilot turn should use.
 *
 * <p>Precedence: the site's own {@code TurSNSiteGenAi.copilotPlanningStrategy} /
 * {@code copilotPlanningMaxPasses}, else the deployment-wide
 * {@code turing.genai.copilot.planning.*} properties, else
 * {@link TurCopilotPlanningStrategy#DETERMINISTIC} — today's behaviour. So an existing
 * site that pins nothing is byte-for-byte unchanged.
 *
 * <p>Fail-open: any lookup problem (site missing, no GenAI binding, an unparseable
 * stored value) resolves to the global default rather than throwing.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurCopilotQueryPlannerFactory {

    private final TurSNSiteRepository turSNSiteRepository;
    private final TurCatalogCopilotProperty copilotProperty;
    private final Map<TurCopilotPlanningStrategy, TurCopilotQueryPlanner> planners =
            new EnumMap<>(TurCopilotPlanningStrategy.class);

    public TurCopilotQueryPlannerFactory(TurSNSiteRepository turSNSiteRepository,
            TurCatalogCopilotProperty copilotProperty, List<TurCopilotQueryPlanner> allPlanners) {
        this.turSNSiteRepository = turSNSiteRepository;
        this.copilotProperty = copilotProperty;
        for (TurCopilotQueryPlanner planner : allPlanners) {
            planners.put(planner.strategy(), planner);
        }
    }

    /**
     * The planner + resolved depth for {@code siteName}. Never null; a strategy with no
     * registered planner degrades to the deterministic one.
     */
    public Resolution resolve(String siteName) {
        Optional<TurSNSiteGenAi> genAi = findGenAi(siteName);
        TurCopilotPlanningStrategy strategy = genAi
                .map(TurSNSiteGenAi::getCopilotPlanningStrategy)
                .orElse(null);
        if (strategy == null) {
            strategy = copilotProperty.getPlanning().getStrategy();
        }
        Integer siteDepth = genAi.map(TurSNSiteGenAi::getCopilotPlanningMaxPasses).orElse(null);
        int depth = siteDepth != null ? siteDepth : copilotProperty.getPlanning().getMaxPasses();
        return new Resolution(plannerFor(strategy), Math.max(0, depth));
    }

    /** The registered planner for {@code strategy}, degrading to the deterministic one. */
    private TurCopilotQueryPlanner plannerFor(TurCopilotPlanningStrategy strategy) {
        TurCopilotQueryPlanner planner = planners.get(
                strategy == null ? TurCopilotPlanningStrategy.DETERMINISTIC : strategy);
        if (planner != null) {
            return planner;
        }
        log.warn("[CopilotPlanner] no planner registered for strategy {} — using DETERMINISTIC",
                strategy);
        return planners.get(TurCopilotPlanningStrategy.DETERMINISTIC);
    }

    /**
     * The site's GenAI binding, read through a projection query so only the two plain
     * planning columns are touched — no LAZY association is navigated outside a
     * session (the copilot path is not transactional).
     */
    private Optional<TurSNSiteGenAi> findGenAi(String siteName) {
        try {
            return turSNSiteRepository.findGenAiByNameIgnoreCase(siteName);
        } catch (RuntimeException e) {
            log.debug("[CopilotPlanner] could not read the GenAI binding for '{}': {}",
                    siteName, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * The resolved planning configuration for one copilot turn.
     *
     * @param planner   the planner to drive
     * @param maxPasses the analysis depth to pass along in the {@code PlanRequest}
     */
    public record Resolution(TurCopilotQueryPlanner planner, int maxPasses) {
    }
}
