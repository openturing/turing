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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.research.dto.TurResearchGraphDto;
import com.viglet.turing.genai.research.dto.TurResearchGraphEdgeDto;
import com.viglet.turing.genai.research.dto.TurResearchGraphNodeDto;
import com.viglet.turing.genai.research.dto.TurResearchQuoteDto;
import com.viglet.turing.genai.research.dto.TurResearchReportDto;
import com.viglet.turing.genai.research.dto.TurResearchThemeDto;

/**
 * Pure, LLM-free unit test for the T724 theme-graph builder (Block AW / §XLVI.3).
 * The graph is a deterministic re-projection of the T722 report, so it is fully
 * testable from a hand-built report with no Spring context and no model.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurResearchThemeGraphBuilderTest {

    private final TurResearchThemeGraphBuilder builder = new TurResearchThemeGraphBuilder();

    @Test
    void unavailableWhenReportHasNoThemes() {
        assertFalse(builder.build(null).available());
        assertFalse(builder.build(TurResearchReportDto.unavailable("no llm", false)).available());
        assertFalse(builder.build(new TurResearchReportDto(true, null, false, "summary",
                List.of(), List.of(), List.of(), null)).available());
    }

    @Test
    void projectsThemesAndPersonasIntoAffinityGraph() {
        TurResearchQuoteDto ana = new TurResearchQuoteDto("p1", "Ana", "checkout is confusing", true);
        TurResearchQuoteDto bob = new TurResearchQuoteDto("p2", "Bob", "payment failed twice", true);
        // Ana appears twice in theme 1 → edge weight 2.
        TurResearchQuoteDto anaAgain = new TurResearchQuoteDto("p1", "Ana", "too many steps", true);
        // A fabricated attribution (resolved=false) must be dropped.
        TurResearchQuoteDto ghost = new TurResearchQuoteDto("p9", "Ghost", "invented", false);

        TurResearchThemeDto checkout = new TurResearchThemeDto("Checkout friction",
                "Users struggle at checkout", 2, List.of(ana, anaAgain, bob, ghost));
        TurResearchThemeDto search = new TurResearchThemeDto("Search relevance",
                "Results feel off", 1, List.of(bob));

        TurResearchReportDto report = new TurResearchReportDto(true, null, true, "summary",
                List.of(checkout, search), List.of("fix checkout"), List.of(), null);

        TurResearchGraphDto graph = builder.build(report);

        assertTrue(graph.available());
        // 2 theme nodes + 2 persona nodes (Ghost dropped).
        assertEquals(4, graph.nodes().size());
        long themeNodes = graph.nodes().stream()
                .filter(n -> TurResearchGraphNodeDto.TYPE_THEME.equals(n.type())).count();
        long personaNodes = graph.nodes().stream()
                .filter(n -> TurResearchGraphNodeDto.TYPE_PERSONA.equals(n.type())).count();
        assertEquals(2, themeNodes);
        assertEquals(2, personaNodes);

        // Ana: 2 quotes in checkout → persona weight 2; Bob: 1 + 1 = 2 across both.
        TurResearchGraphNodeDto anaNode = graph.nodes().stream()
                .filter(n -> "persona-p1".equals(n.id())).findFirst().orElseThrow();
        assertEquals("Ana", anaNode.label());
        assertEquals(2, anaNode.weight());

        // Edges: Ana→checkout(2), Bob→checkout(1), Bob→search(1) = 3 edges, no Ghost.
        assertEquals(3, graph.edges().size());
        TurResearchGraphEdgeDto anaCheckout = graph.edges().stream()
                .filter(e -> "persona-p1".equals(e.source())).findFirst().orElseThrow();
        assertEquals("theme-0", anaCheckout.target());
        assertEquals(2, anaCheckout.weight());
        assertTrue(graph.edges().stream().noneMatch(e -> "persona-p9".equals(e.source())),
                "fabricated (unresolved) attributions must not enter the graph");
    }
}
