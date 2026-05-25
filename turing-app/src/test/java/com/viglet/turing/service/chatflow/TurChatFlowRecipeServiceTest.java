/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.chatflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import com.viglet.turing.persistence.dto.agent.TurChatFlowRecipeDto;
import com.viglet.turing.persistence.dto.agent.TurChatFlowRecipeSummaryDto;

/**
 * Loads the bundled recipes from the classpath and pins the catalog
 * contract: every shipped recipe parses, the three flagship ones are
 * present, and the install-bundle they expose is non-empty. Cheap to
 * run — no Spring context, just a real {@link PathMatchingResourcePatternResolver}
 * pointed at the test classpath (which includes
 * {@code src/main/resources/flow-recipes/}).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurChatFlowRecipeServiceTest {

    private TurChatFlowRecipeService service;

    @BeforeEach
    void setUp() {
        service = new TurChatFlowRecipeService();
        service.loadFrom(new PathMatchingResourcePatternResolver());
    }

    @Test
    void loadsAllBundledRecipes() {
        List<TurChatFlowRecipeSummaryDto> summaries = service.list();
        assertThat(summaries).extracting(TurChatFlowRecipeSummaryDto::id)
                .containsExactlyInAnyOrder(
                        "lead-capture-b2c",
                        "in-company-quote-b2b",
                        "micro-lesson-educational");
    }

    @Test
    void summariesCarryFlowAndSlotCounts() {
        TurChatFlowRecipeSummaryDto leadCapture = service.list().stream()
                .filter(r -> "lead-capture-b2c".equals(r.id()))
                .findFirst().orElseThrow();
        // Lead-capture v1 ships exactly one flow with the four lead slots.
        assertThat(leadCapture.flowCount()).isEqualTo(1);
        assertThat(leadCapture.slotCount()).isEqualTo(4);
        assertThat(leadCapture.vertical()).isEqualTo("education");
        assertThat(leadCapture.version()).isEqualTo("1.0.0");
    }

    @Test
    void getReturnsFullBundleWithDefinitionJson() {
        TurChatFlowRecipeDto recipe = service.get("lead-capture-b2c").orElseThrow();
        assertThat(recipe.getBundle()).isNotEmpty();
        var item = recipe.getBundle().get(0);
        assertThat(item.getChatFlow()).isNotNull();
        assertThat(item.getChatFlow().getDefinitionJson())
                .as("Bundled recipe must carry a real flow graph, not a placeholder")
                .contains("\"nodes\"")
                .contains("\"edges\"");
    }

    @Test
    void inCompanyQuoteWiresSlotInheritanceFromLeadCapture() {
        // T93 invariant: the B2B recipe must declare an inheritance map so
        // a visitor who already filled `name`/`email` in the B2C flow does
        // not get asked the same questions again.
        TurChatFlowRecipeDto recipe = service.get("in-company-quote-b2b").orElseThrow();
        String inheritance = recipe.getBundle().get(0).getChatFlow().getSlotInheritanceJson();
        assertThat(inheritance)
                .as("B2B recipe must inherit at least the name slot from the B2C lead-capture")
                .contains("name");
    }

    @Test
    void unknownIdReturnsEmpty() {
        assertThat(service.get("ghost-recipe")).isEmpty();
        assertThat(service.get(null)).isEmpty();
        assertThat(service.get("  ")).isEmpty();
    }
}
