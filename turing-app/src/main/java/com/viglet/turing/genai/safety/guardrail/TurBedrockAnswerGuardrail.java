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

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.properties.TurGuardrailProperty;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ApplyGuardrailRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ApplyGuardrailResponse;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailAction;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailAssessment;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailContentQualifier;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailContentSource;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailContextualGroundingFilter;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailContextualGroundingFilterType;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailContextualGroundingPolicyAction;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailPiiEntityFilter;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailTextBlock;

/**
 * T516 / §XXVIII.12 — the flagship answer-grounding guardrail: AWS Bedrock
 * Guardrails {@code ApplyGuardrail}. In one managed call it checks the model
 * <em>answer</em> for contextual grounding (is it supported by the retrieved
 * passages?), answer↔query relevance, and PII — returning a masked variant when
 * the guardrail's PII policy redacts. This is the answer-time complement to the
 * next-day T155 citation-drift scan: it catches hallucination as it happens.
 *
 * <p>Reuses the T506 Bedrock dependency and credential model. Inert when no
 * {@code guardrailId} is configured; fail-open — any AWS/transport error throws
 * and the {@link TurAnswerGuardrailService} facade degrades to "no verdict".
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurBedrockAnswerGuardrail implements TurAnswerGuardrailStrategy {

    private final TurGuardrailProperty guardrailProperty;

    public TurBedrockAnswerGuardrail(TurGuardrailProperty guardrailProperty) {
        this.guardrailProperty = guardrailProperty;
    }

    @Override
    public TurAnswerGuardrailType getType() {
        return TurAnswerGuardrailType.BEDROCK;
    }

    @Override
    public TurAnswerGuardrailVerdict evaluate(TurAnswerGuardrailRequest request) {
        TurGuardrailProperty.Bedrock cfg = guardrailProperty.getBedrock();
        if (cfg == null || !StringUtils.hasText(cfg.getGuardrailId())) {
            log.debug("[Guardrail] Bedrock guardrail has no guardrailId — skipping (fail-open)");
            return TurAnswerGuardrailVerdict.pass(TurAnswerGuardrailType.BEDROCK);
        }
        try (BedrockRuntimeClient client = buildClient(cfg)) {
            ApplyGuardrailResponse response = client.applyGuardrail(
                    buildRequest(cfg, request));
            return mapVerdict(response, request.answer());
        }
    }

    /** Builds the {@code ApplyGuardrail} request: the answer as OUTPUT, with the
     *  retrieved context as a grounding source and the query for relevance. */
    private ApplyGuardrailRequest buildRequest(TurGuardrailProperty.Bedrock cfg,
            TurAnswerGuardrailRequest request) {
        List<GuardrailContentBlock> content = new ArrayList<>();
        content.add(GuardrailContentBlock.fromText(GuardrailTextBlock.builder()
                .text(request.answer())
                .qualifiers(GuardrailContentQualifier.GUARD_CONTENT)
                .build()));
        if (request.hasContext()) {
            content.add(GuardrailContentBlock.fromText(GuardrailTextBlock.builder()
                    .text(request.joinedContext())
                    .qualifiers(GuardrailContentQualifier.GROUNDING_SOURCE)
                    .build()));
        }
        if (StringUtils.hasText(request.query())) {
            content.add(GuardrailContentBlock.fromText(GuardrailTextBlock.builder()
                    .text(request.query())
                    .qualifiers(GuardrailContentQualifier.QUERY)
                    .build()));
        }
        return ApplyGuardrailRequest.builder()
                .guardrailIdentifier(cfg.getGuardrailId())
                .guardrailVersion(StringUtils.hasText(cfg.getGuardrailVersion())
                        ? cfg.getGuardrailVersion() : "DRAFT")
                .source(GuardrailContentSource.OUTPUT)
                .content(content)
                .build();
    }

    /** Maps a Bedrock guardrail response onto the provider-agnostic verdict.
     *  Package-private for focused unit testing of the mapping logic. */
    TurAnswerGuardrailVerdict mapVerdict(ApplyGuardrailResponse response, String originalAnswer) {
        boolean intervened = response.action() == GuardrailAction.GUARDRAIL_INTERVENED;
        boolean grounded = true;
        Double groundingScore = null;
        Double relevanceScore = null;
        List<String> categories = new ArrayList<>();

        for (GuardrailAssessment assessment : response.assessments()) {
            if (assessment.contextualGroundingPolicy() != null) {
                for (GuardrailContextualGroundingFilter filter
                        : assessment.contextualGroundingPolicy().filters()) {
                    boolean blocked = filter.action() == GuardrailContextualGroundingPolicyAction.BLOCKED;
                    if (filter.type() == GuardrailContextualGroundingFilterType.GROUNDING) {
                        groundingScore = filter.score();
                        if (blocked) {
                            grounded = false;
                            categories.add("ungrounded");
                        }
                    } else if (filter.type() == GuardrailContextualGroundingFilterType.RELEVANCE) {
                        relevanceScore = filter.score();
                        if (blocked) {
                            categories.add("irrelevant");
                        }
                    }
                }
            }
            if (assessment.sensitiveInformationPolicy() != null) {
                for (GuardrailPiiEntityFilter pii
                        : assessment.sensitiveInformationPolicy().piiEntities()) {
                    String type = pii.typeAsString();
                    if (StringUtils.hasText(type) && !categories.contains("pii:" + type)) {
                        categories.add("pii:" + type);
                    }
                }
            }
        }

        // When the guardrail intervened and produced a (masked) output, surface
        // that as the redacted variant the facade can deliver instead.
        String redactedAnswer = null;
        if (intervened && response.hasOutputs() && !response.outputs().isEmpty()) {
            String masked = response.outputs().get(0).text();
            if (StringUtils.hasText(masked) && !masked.equals(originalAnswer)) {
                redactedAnswer = masked;
            }
        }

        TurAnswerGuardrailAction action = (intervened || !grounded || !categories.isEmpty())
                ? TurAnswerGuardrailAction.FLAG
                : TurAnswerGuardrailAction.PASS;
        return new TurAnswerGuardrailVerdict(grounded, groundingScore, relevanceScore,
                action, categories, redactedAnswer, TurAnswerGuardrailType.BEDROCK);
    }

    /** Builds a Bedrock client from the guardrail config: static keys when set,
     *  else the AWS default credentials chain (IAM role / env / instance profile). */
    private BedrockRuntimeClient buildClient(TurGuardrailProperty.Bedrock cfg) {
        AwsCredentialsProvider credentials;
        if (StringUtils.hasText(cfg.getAccessKeyId()) && StringUtils.hasText(cfg.getSecretAccessKey())) {
            credentials = StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(cfg.getAccessKeyId(), cfg.getSecretAccessKey()));
        } else {
            credentials = DefaultCredentialsProvider.create();
        }
        return BedrockRuntimeClient.builder()
                .region(Region.of(StringUtils.hasText(cfg.getRegion()) ? cfg.getRegion() : "us-east-1"))
                .credentialsProvider(credentials)
                .build();
    }
}
