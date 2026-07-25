/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.sn.genai.TurCopilotPlanningStrategy;

import lombok.Getter;
import lombok.Setter;

/**
 * T793 / §LIV.4 (Block BF) — configuration for the Vectorless (Structured-Data)
 * RAG copilot's <strong>stuff-all</strong> mode: the extreme-vectorless path
 * where a small catalog fits entirely in one prompt, so the copilot skips the
 * NL→DSL retrieval step and grounds the LLM on <em>every</em> row.
 *
 * <p>Self-gating by corpus size: stuff-all is attempted only when the whole index
 * fits within {@link #stuffAllTokenBudget} estimated tokens and
 * {@link #stuffAllMaxDocs} documents; above either bound the copilot falls back to
 * the normal filtered NL→DSL retrieval unchanged. Enabled by default because it is
 * self-limiting (it only activates when the catalog genuinely fits) and cited +
 * fail-open like the filtered path.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "turing.genai.copilot")
public class TurCatalogCopilotProperty {

    /**
     * Whether the copilot may use stuff-all mode when the catalog fits the budget.
     * Default {@code true} (self-gated by size, so safe for every site).
     */
    private boolean stuffAllEnabled = true;

    /**
     * Maximum estimated corpus size (in {@code chars/4} tokens) that still
     * qualifies for stuff-all. Above it the copilot falls back to filtered
     * retrieval. Default 40 000 — a few hundred structured rows.
     */
    private int stuffAllTokenBudget = 40_000;

    /**
     * Hard cap on documents pulled for a stuff-all attempt. When the index holds
     * more than this, the corpus is deemed too large and the copilot falls back to
     * filtered retrieval. Default 1 000.
     */
    private int stuffAllMaxDocs = 1_000;

    /**
     * T818–T820 / §LIX (Block BK) — NL→query planning strategy defaults, bound
     * under {@code turing.genai.copilot.planning.*}. A site may pin its own values
     * on {@code TurSNSiteGenAi}; these are the deployment-wide fallbacks.
     */
    private final Planning planning = new Planning();

    /**
     * T818–T820 / §LIX (Block BK) — the deployment-wide planning defaults every SN
     * site inherits when it pins nothing of its own.
     *
     * @author Alexandre Oliveira
     * @since 2026.3.4
     */
    @Getter
    @Setter
    public static class Planning {

        /**
         * The default planning strategy for sites that pin none
         * ({@code turing.genai.copilot.planning.strategy}). Default
         * {@link TurCopilotPlanningStrategy#DETERMINISTIC} — today's behaviour, so
         * the block is purely additive.
         */
        private TurCopilotPlanningStrategy strategy = TurCopilotPlanningStrategy.DETERMINISTIC;

        /**
         * Analysis depth for the LLM-assisted planner
         * ({@code turing.genai.copilot.planning.max-passes}): how many LLM passes
         * beyond the initial parse may run — {@code 0} = parse only, {@code 1} =
         * + judge, {@code 2} = + refine. Values are clamped to {@code [0, 2]}.
         * Default {@code 2} (the full parse → judge → refine loop), which only
         * takes effect on a site that actually opts into an LLM strategy.
         */
        private int maxPasses = 2;
    }
}
