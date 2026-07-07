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

import java.util.List;

import com.viglet.turing.persistence.model.batch.TurBatchJob;

/**
 * F.7 / §X.8.a — the callback a workload registers to consume a finished batch.
 *
 * <p>Submission ({@link TurBatchInferenceService#submit}) and completion are
 * separated by up to 24h, so a workload cannot block on its results. Instead it
 * tags its job with a {@link #purpose()} and implements this handler; when the
 * {@code TurBatchJobPoller} sees the job end, it fetches the results and invokes
 * the matching handler exactly once. The handler reattaches results to its
 * domain objects via {@link TurBatchJob#contextJson}.
 *
 * <p>Each {@link #purpose()} must be unique across handler beans; the poller
 * resolves at most one handler per job.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurBatchCompletionHandler {

    /** The {@link TurBatchJob#purpose} value this handler consumes. */
    String purpose();

    /**
     * Invoked once when a batch with this handler's purpose completes. Results
     * may contain per-request failures (inspect {@link TurBatchChatResult#success()}).
     * Exceptions are logged by the poller and do not block other jobs.
     */
    void onBatchComplete(TurBatchJob job, List<TurBatchChatResult> results);
}
