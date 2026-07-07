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
 * F.7 / §X.8.a — a point-in-time status snapshot of a submitted batch.
 *
 * @param vendorBatchId the vendor's batch id (OpenAI {@code batch_...} /
 *                      Anthropic {@code msgbatch_...})
 * @param state         normalized lifecycle state
 * @param total         total requests in the batch
 * @param completed     requests that have completed successfully so far
 * @param failed        requests that have failed so far
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurBatchStatus(
        String vendorBatchId,
        TurBatchState state,
        long total,
        long completed,
        long failed) {
}
