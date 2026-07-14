/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.research;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.viglet.turing.genai.research.dto.TurResearchGraphDto;
import com.viglet.turing.genai.research.dto.TurResearchGraphEdgeDto;
import com.viglet.turing.genai.research.dto.TurResearchGraphNodeDto;
import com.viglet.turing.genai.research.dto.TurResearchQuoteDto;
import com.viglet.turing.genai.research.dto.TurResearchReportDto;
import com.viglet.turing.genai.research.dto.TurResearchThemeDto;

/**
 * A <strong>pure</strong> transform of a T722 insights report into a
 * theme/affinity knowledge graph (Block AW / §XLVI.3, T724). No IO, no LLM, no
 * Spring dependency beyond {@code @Component} (freely {@code new}-able in tests) —
 * the graph is <em>visualization only, no new inference</em>: it just re-projects
 * the already-synthesized themes and their roster-validated quotes into a
 * persona↔theme bipartite graph so the studio can show which cohorts cluster on
 * which pain point.
 *
 * <p>A {@code THEME} node per synthesized theme (weighted by prevalence) and a
 * {@code PERSONA} node per participant who has at least one <em>resolved</em>
 * (traceable) quote (weighted by total supporting quotes). An edge joins a persona
 * to a theme with a weight equal to how many of that theme's quotes came from that
 * persona. Unresolved (fabricated) attributions are dropped, consistent with the
 * T722 traceability stance.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurResearchThemeGraphBuilder {

    /** Project the report into a graph. Assumes {@code report.available()}. */
    public TurResearchGraphDto build(TurResearchReportDto report) {
        if (report == null || !report.available() || report.themes() == null
                || report.themes().isEmpty()) {
            return TurResearchGraphDto.unavailable(
                    "No synthesized themes to graph yet. Generate the insights report first.");
        }

        List<TurResearchGraphNodeDto> nodes = new ArrayList<>();
        List<TurResearchGraphEdgeDto> edges = new ArrayList<>();
        Map<String, Integer> personaQuoteTotals = new LinkedHashMap<>();
        Map<String, String> personaLabels = new LinkedHashMap<>();

        int themeIndex = 0;
        for (TurResearchThemeDto theme : report.themes()) {
            String themeId = "theme-" + themeIndex++;
            nodes.add(new TurResearchGraphNodeDto(themeId, theme.title(),
                    TurResearchGraphNodeDto.TYPE_THEME, theme.prevalence()));

            // How many of THIS theme's quotes each persona contributed → edge weight.
            Map<String, Integer> perPersona = new LinkedHashMap<>();
            for (TurResearchQuoteDto quote : safeQuotes(theme)) {
                if (!quote.resolved() || quote.personaId() == null) {
                    continue;
                }
                String pid = quote.personaId();
                perPersona.merge(pid, 1, Integer::sum);
                personaQuoteTotals.merge(pid, 1, Integer::sum);
                personaLabels.putIfAbsent(pid, quote.personaName());
            }
            perPersona.forEach((pid, weight) ->
                    edges.add(new TurResearchGraphEdgeDto("persona-" + pid, themeId, weight)));
        }

        // Persona nodes after themes; edge order defines graph regardless of node order.
        personaQuoteTotals.forEach((pid, total) ->
                nodes.add(new TurResearchGraphNodeDto("persona-" + pid, personaLabels.get(pid),
                        TurResearchGraphNodeDto.TYPE_PERSONA, total)));

        return new TurResearchGraphDto(true, null, nodes, edges);
    }

    private List<TurResearchQuoteDto> safeQuotes(TurResearchThemeDto theme) {
        return theme.quotes() == null ? List.of() : theme.quotes();
    }
}
