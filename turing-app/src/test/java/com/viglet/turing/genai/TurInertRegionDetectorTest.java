/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.turing.persistence.dto.agent.TurSystemPromptInertRegionDto;

/**
 * T609 — "inert this turn" heuristic + author-marker detection.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurInertRegionDetectorTest {

    private final TurInertRegionDetector detector = new TurInertRegionDetector();

    private static final String PROMPT = """
            # Role
            You are a helpful concierge.

            ## Welcome
            On the first message, greet the visitor and present the three experiences.

            ## Hard rules
            Never invent facts.
            """;

    @Test
    void welcomeHeadingIsInertWhenFlowActive() {
        List<TurSystemPromptInertRegionDto> regions = detector.detect(PROMPT, true);
        assertThat(regions).hasSize(1);
        assertThat(regions.get(0).label()).isEqualTo("Welcome");
        assertThat(regions.get(0).reason()).isEqualTo("heuristic");
        assertThat(regions.get(0).tokens()).isGreaterThan(0);
        // Offsets bound the Welcome section only.
        String span = PROMPT.substring(regions.get(0).start(), regions.get(0).end());
        assertThat(span).contains("Welcome").doesNotContain("Hard rules");
    }

    @Test
    void nothingInertWithoutFlowAndNoMarkers() {
        assertThat(detector.detect(PROMPT, false)).isEmpty();
    }

    @Test
    void portugueseHeadingIsDetected() {
        String pt = """
                ## Boas-vindas
                Na primeira mensagem, cumprimente o visitante.

                ## Regras
                Seja objetivo.
                """;
        List<TurSystemPromptInertRegionDto> regions = detector.detect(pt, true);
        assertThat(regions).extracting(TurSystemPromptInertRegionDto::label).containsExactly("Boas-vindas");
    }

    @Test
    void conciergeOnlyMarkerIsInertOnlyWhenFlowActive() {
        String marked = """
                ## Routing helper
                <!-- turing:concierge-only -->
                Route the visitor to the right area.

                ## Core
                Answer accurately.
                """;
        // The heading also matches the heuristic, but the marker attributes it.
        assertThat(detector.detect(marked, true))
                .extracting(TurSystemPromptInertRegionDto::reason)
                .containsExactly("concierge_only");
        assertThat(detector.detect(marked, false)).isEmpty();
    }

    @Test
    void flowOnlyMarkerIsInertOnlyOnNoFlowTurn() {
        String marked = """
                ## Deep dive
                <!-- turing:flow-only -->
                Only used inside the guided flow.
                """;
        assertThat(detector.detect(marked, false))
                .extracting(TurSystemPromptInertRegionDto::reason)
                .containsExactly("flow_only");
        assertThat(detector.detect(marked, true)).isEmpty();
    }

    @Test
    void blankTextYieldsNoRegions() {
        assertThat(detector.detect("   ", true)).isEmpty();
        assertThat(detector.detect(null, true)).isEmpty();
    }
}
