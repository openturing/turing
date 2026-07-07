/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.batch;

/**
 * F.7 / §X.8.a — one unit of work in a chat batch submission.
 *
 * <p>Deliberately a small system+user shape: it covers every bulk,
 * non-interactive workload Turing routes through the Batch tier (per-site AI
 * Insights — T157, per-document summaries, LLM-Judge eval rubrics — T159)
 * without dragging in the full conversation/tool machinery, which never applies
 * to batch jobs. The {@link #customId()} is the caller's correlation key: it is
 * echoed back verbatim on the matching {@link TurBatchChatResult}, so callers
 * can reassemble results to their domain objects.
 *
 * @param customId    caller-chosen correlation id, unique within the batch
 * @param systemPrompt system instruction (nullable / blank → omitted)
 * @param userPrompt   the user content (required)
 * @param model        model id; {@code null} → the instance default model
 * @param temperature  sampling temperature; {@code null} → provider default
 * @param maxTokens    max output tokens; {@code null} → provider default
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurBatchChatRequest(
        String customId,
        String systemPrompt,
        String userPrompt,
        String model,
        Double temperature,
        Integer maxTokens) {

    /** Convenience factory for a system+user request with provider defaults. */
    public static TurBatchChatRequest of(String customId, String systemPrompt, String userPrompt) {
        return new TurBatchChatRequest(customId, systemPrompt, userPrompt, null, null, null);
    }
}
