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
 * F.7 / §X.8.a — the outcome of a single {@link TurBatchChatRequest}.
 *
 * <p>One result per submitted request, correlated by {@link #customId()}. A
 * batch can finish with a mix of successes and per-request failures (a request
 * that errored vendor-side, expired, or was cancelled), so success is reported
 * per result rather than per batch.
 *
 * @param customId     echoes the request's correlation id
 * @param success      true when the request produced a usable completion
 * @param content      the assistant text when {@link #success()}, else {@code null}
 * @param error        a short error message when not {@link #success()}, else {@code null}
 * @param inputTokens  prompt/input tokens billed (nullable when unknown)
 * @param outputTokens completion/output tokens billed (nullable when unknown)
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurBatchChatResult(
        String customId,
        boolean success,
        String content,
        String error,
        Long inputTokens,
        Long outputTokens) {

    public static TurBatchChatResult ok(String customId, String content, Long in, Long out) {
        return new TurBatchChatResult(customId, true, content, null, in, out);
    }

    public static TurBatchChatResult failed(String customId, String error) {
        return new TurBatchChatResult(customId, false, null, error, null, null);
    }
}
