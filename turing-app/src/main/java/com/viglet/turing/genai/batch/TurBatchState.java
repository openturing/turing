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
 * F.7 / §X.8.a — vendor-neutral lifecycle state of a batch inference job.
 *
 * <p>Both vendors expose a richer state machine that this enum normalizes:
 * OpenAI's {@code validating / in_progress / finalizing / completed / failed /
 * expired / cancelling / cancelled} and Anthropic's {@code in_progress /
 * canceling / ended} (Anthropic reports per-request success/error inside the
 * {@code ended} results, not as a batch-level state). The mapping lives in each
 * {@link TurBatchProvider}; consumers only ever see these six states.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurBatchState {

    /** The batch was accepted and is still being validated / processed. */
    VALIDATING,
    /** The batch is actively running. */
    IN_PROGRESS,
    /** The batch finished; results are retrievable. */
    COMPLETED,
    /** The batch failed wholesale (validation or vendor-side error). */
    FAILED,
    /** The batch exceeded the completion window before finishing. */
    EXPIRED,
    /** The batch was cancelled (by request or vendor). */
    CANCELLED;

    /** True once the job has reached a state that will not change again. */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == EXPIRED || this == CANCELLED;
    }
}
