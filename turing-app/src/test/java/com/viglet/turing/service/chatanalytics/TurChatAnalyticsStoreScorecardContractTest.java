/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.chatanalytics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Pins the T74 scorecard contract shared by the Mongo and Redis stores via
 * {@link TurChatAnalyticsStore}: the dimension whitelist (group-by field
 * resolution) and the filterable-field set used to narrow a cohort. Keeping
 * these on the interface guarantees both backends pivot and filter on
 * identical fields.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurChatAnalyticsStoreScorecardContractTest {

    @Test
    void dimensionField_mapsCohortDimensions() {
        assertThat(TurChatAnalyticsStore.scorecardDimensionField("deviceType")).isEqualTo("deviceType");
        assertThat(TurChatAnalyticsStore.scorecardDimensionField("locale")).isEqualTo("locale");
        assertThat(TurChatAnalyticsStore.scorecardDimensionField("timezone")).isEqualTo("timezone");
    }

    @Test
    void dimensionField_mapsLegacyDimensions() {
        assertThat(TurChatAnalyticsStore.scorecardDimensionField("personaId")).isEqualTo("personaId");
        assertThat(TurChatAnalyticsStore.scorecardDimensionField("experimentKey")).isEqualTo("experimentKey");
        assertThat(TurChatAnalyticsStore.scorecardDimensionField("variantLabel")).isEqualTo("variantLabel");
        assertThat(TurChatAnalyticsStore.scorecardDimensionField("parentConversationId"))
                .isEqualTo("parentConversationId");
    }

    @Test
    void dimensionField_unknownNullAndBlankFallBackToAgentId() {
        assertThat(TurChatAnalyticsStore.scorecardDimensionField("nope")).isEqualTo("agentId");
        assertThat(TurChatAnalyticsStore.scorecardDimensionField(null)).isEqualTo("agentId");
        assertThat(TurChatAnalyticsStore.scorecardDimensionField("  ")).isEqualTo("agentId");
        // Whitespace around a valid value is trimmed.
        assertThat(TurChatAnalyticsStore.scorecardDimensionField(" deviceType ")).isEqualTo("deviceType");
    }

    @Test
    void filterableFields_coverCohortAndIdentityButNotArbitraryColumns() {
        assertThat(TurChatAnalyticsStore.SCORECARD_FILTERABLE_FIELDS)
                .contains("deviceType", "locale", "timezone",
                        "agentId", "personaId", "experimentKey", "variantLabel", "outcome");
        // Free-text / sensitive columns are NOT filterable (injection guard).
        assertThat(TurChatAnalyticsStore.SCORECARD_FILTERABLE_FIELDS)
                .doesNotContain("firstUserMessage", "userId", "conversationId", "goalSummary");
    }
}
