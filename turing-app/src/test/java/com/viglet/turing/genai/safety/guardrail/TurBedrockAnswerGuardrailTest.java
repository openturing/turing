/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.safety.guardrail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.turing.properties.TurGuardrailProperty;

import software.amazon.awssdk.services.bedrockruntime.model.ApplyGuardrailResponse;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailAction;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailAssessment;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailContextualGroundingFilter;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailContextualGroundingFilterType;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailContextualGroundingPolicyAction;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailContextualGroundingPolicyAssessment;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailOutputContent;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailPiiEntityFilter;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailPiiEntityType;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailSensitiveInformationPolicyAction;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailSensitiveInformationPolicyAssessment;

/**
 * T516 — verdict mapping of {@link TurBedrockAnswerGuardrail} from a synthetic
 * {@code ApplyGuardrail} response (no AWS call), plus the inert (no config) path.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurBedrockAnswerGuardrailTest {

    private final TurBedrockAnswerGuardrail guardrail =
            new TurBedrockAnswerGuardrail(new TurGuardrailProperty());

    @Test
    void inertWhenNoGuardrailIdConfigured() {
        TurAnswerGuardrailVerdict verdict = guardrail.evaluate(
                new TurAnswerGuardrailRequest("q", "answer", List.of("ctx")));
        assertThat(verdict.isClean()).isTrue();
        assertThat(verdict.strategy()).isEqualTo(TurAnswerGuardrailType.BEDROCK);
    }

    @Test
    void mapsUngroundedAnswerToFlagWithScores() {
        ApplyGuardrailResponse response = ApplyGuardrailResponse.builder()
                .action(GuardrailAction.GUARDRAIL_INTERVENED)
                .assessments(GuardrailAssessment.builder()
                        .contextualGroundingPolicy(GuardrailContextualGroundingPolicyAssessment.builder()
                                .filters(
                                        GuardrailContextualGroundingFilter.builder()
                                                .type(GuardrailContextualGroundingFilterType.GROUNDING)
                                                .score(0.2).threshold(0.7)
                                                .action(GuardrailContextualGroundingPolicyAction.BLOCKED)
                                                .build(),
                                        GuardrailContextualGroundingFilter.builder()
                                                .type(GuardrailContextualGroundingFilterType.RELEVANCE)
                                                .score(0.9).threshold(0.5)
                                                .action(GuardrailContextualGroundingPolicyAction.NONE)
                                                .build())
                                .build())
                        .build())
                .build();

        TurAnswerGuardrailVerdict verdict = guardrail.mapVerdict(response, "hallucinated");

        assertThat(verdict.grounded()).isFalse();
        assertThat(verdict.groundingScore()).isCloseTo(0.2, within(1e-9));
        assertThat(verdict.relevanceScore()).isCloseTo(0.9, within(1e-9));
        assertThat(verdict.action()).isEqualTo(TurAnswerGuardrailAction.FLAG);
        assertThat(verdict.categories()).contains("ungrounded");
    }

    @Test
    void mapsCleanResponseToPass() {
        ApplyGuardrailResponse response = ApplyGuardrailResponse.builder()
                .action(GuardrailAction.NONE)
                .build();

        TurAnswerGuardrailVerdict verdict = guardrail.mapVerdict(response, "grounded answer");

        assertThat(verdict.grounded()).isTrue();
        assertThat(verdict.action()).isEqualTo(TurAnswerGuardrailAction.PASS);
        assertThat(verdict.isClean()).isTrue();
    }

    @Test
    void mapsPiiToCategoriesAndRedactedAnswer() {
        ApplyGuardrailResponse response = ApplyGuardrailResponse.builder()
                .action(GuardrailAction.GUARDRAIL_INTERVENED)
                .outputs(GuardrailOutputContent.builder().text("call me at {PHONE}").build())
                .assessments(GuardrailAssessment.builder()
                        .sensitiveInformationPolicy(GuardrailSensitiveInformationPolicyAssessment.builder()
                                .piiEntities(GuardrailPiiEntityFilter.builder()
                                        .type(GuardrailPiiEntityType.PHONE)
                                        .action(GuardrailSensitiveInformationPolicyAction.ANONYMIZED)
                                        .match("555-1234")
                                        .build())
                                .build())
                        .build())
                .build();

        TurAnswerGuardrailVerdict verdict = guardrail.mapVerdict(response, "call me at 555-1234");

        assertThat(verdict.categories()).contains("pii:PHONE");
        assertThat(verdict.redactedAnswer()).isEqualTo("call me at {PHONE}");
        assertThat(verdict.action()).isEqualTo(TurAnswerGuardrailAction.FLAG);
    }
}
