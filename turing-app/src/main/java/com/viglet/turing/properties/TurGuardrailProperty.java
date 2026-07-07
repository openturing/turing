/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * T516 / §XXVIII.12 — configuration for the pluggable answer-grounding guardrail
 * ({@code turing.safety.guardrail.*}). The guardrail validates that a streamed
 * RAG answer is actually grounded in the retrieved context (and screens it for
 * unsafe content / PII) before it reaches the user — a managed, answer-time
 * complement to the next-day T155 citation-drift scan.
 *
 * <p>Off by default: when {@link #enabled} is false (or {@link #strategy}
 * resolves to {@code NONE}), the chat path is byte-for-byte unchanged — no extra
 * call, no extra SSE event. Fully fail-open: any guardrail error degrades to "no
 * verdict" so the answer is never blocked by an outage.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "turing.safety.guardrail")
public class TurGuardrailProperty {

    /** Master switch for the answer-grounding guardrail. */
    private boolean enabled = false;

    /**
     * Which backend runs: {@code NONE} (default), {@code BEDROCK},
     * {@code OPENAI_MODERATION}, or {@code MISTRAL_MODERATION}. Parsed leniently
     * ({@code TurAnswerGuardrailType.fromValue}) — an unknown value disables.
     */
    private String strategy = "NONE";

    /**
     * When {@code true}, an answer the guardrail flags as ungrounded/unsafe is
     * replaced with {@link #blockedMessage} (or the redacted variant when the
     * adapter produced one) instead of being delivered as-is. Default
     * {@code false}: non-blocking — the answer is delivered and the verdict is
     * surfaced beside it as a confidence signal.
     */
    private boolean blockOnViolation = false;

    /** Replacement text used when {@link #blockOnViolation} blocks an answer. */
    private String blockedMessage =
            "This answer could not be verified against the available sources.";

    /** Bedrock Guardrails settings ({@code turing.safety.guardrail.bedrock.*}). */
    private Bedrock bedrock = new Bedrock();

    /** Mistral moderation settings ({@code turing.safety.guardrail.mistral.*}). */
    private Mistral mistral = new Mistral();

    @Getter
    @Setter
    public static class Bedrock {
        /** The Bedrock guardrail identifier (id or ARN). Blank → adapter inert. */
        private String guardrailId;
        /** The guardrail version, or {@code DRAFT} for the working draft. */
        private String guardrailVersion = "DRAFT";
        /** AWS region for the {@code ApplyGuardrail} call. */
        private String region = "us-east-1";
        /** Static access key id; blank → AWS default credentials chain (IAM role). */
        private String accessKeyId;
        /** Static secret access key; paired with {@link #accessKeyId}. */
        private String secretAccessKey;
    }

    @Getter
    @Setter
    public static class Mistral {
        /** Mistral API key. When blank, the adapter stays inert. */
        private String apiKey;
        /** Moderation model id. */
        private String model = "mistral-moderation-latest";
        /** REST endpoint for the moderation API. */
        private String endpoint = "https://api.mistral.ai/v1/moderations";
        /** Per-request timeout in seconds. */
        private int timeoutSeconds = 30;
    }
}
