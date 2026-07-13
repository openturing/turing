/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.prompt.contributor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.persona.TurPersonaGroundingService;
import com.viglet.turing.genai.prompt.TurPromptAssemblyContext;
import com.viglet.turing.genai.prompt.TurPromptContributor;
import com.viglet.turing.genai.prompt.TurPromptSegment;
import com.viglet.turing.genai.prompt.TurPromptStability;
import com.viglet.turing.persistence.model.persona.TurPersona;

/**
 * Unit tests for the persona grounding prompt contributor (T718).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurPersonaGroundingPromptContributorTest {

    @Mock
    private TurPersonaGroundingService groundingService;

    private TurPersonaGroundingPromptContributor contributor() {
        return new TurPersonaGroundingPromptContributor(groundingService);
    }

    private static TurPromptAssemblyContext context(TurPersona persona, String query) {
        return new TurPromptAssemblyContext(null, persona, null, query, null, null, null);
    }

    private static TurPersona persona() {
        TurPersona p = new TurPersona();
        p.setName("Ana");
        return p;
    }

    @Test
    void ordersRightAfterPersonaHead() {
        assertThat(contributor().order()).isEqualTo(TurPromptContributor.ORDER_GROUNDING);
        assertThat(TurPromptContributor.ORDER_GROUNDING)
                .isBetween(TurPromptContributor.ORDER_PERSONA, TurPromptContributor.ORDER_AGENT_BASE);
    }

    @Test
    void emptyWhenNoPersona() {
        assertThat(contributor().contribute(context(null, "q"))).isEmpty();
    }

    @Test
    void emptyWhenServiceReturnsBlank() {
        TurPersona p = persona();
        when(groundingService.groundingBlock(eq(p), any())).thenReturn("");
        assertThat(contributor().contribute(context(p, "q"))).isEmpty();
    }

    @Test
    void emitsSinglePerTurnGroundingSegment() {
        TurPersona p = persona();
        when(groundingService.groundingBlock(eq(p), eq("q")))
                .thenReturn("\n\n# Grounded Knowledge\n<persona_knowledge>\nX\n</persona_knowledge>");

        List<TurPromptSegment> segments = contributor().contribute(context(p, "q"));

        assertThat(segments).hasSize(1);
        TurPromptSegment seg = segments.get(0);
        assertThat(seg.origin()).isEqualTo(TurPromptSegment.ORIGIN_GROUNDING);
        assertThat(seg.stability()).isEqualTo(TurPromptStability.PER_TURN);
        assertThat(seg.isPersonaHead()).isFalse();
        assertThat(seg.text()).contains("# Grounded Knowledge");
    }
}
