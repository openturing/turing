/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.properties;

import lombok.Getter;
import lombok.Setter;

/**
 * F.7 / §X.8 — configuration for the Batch inference tier ({@code turing.batch.*}).
 *
 * <p>Off by default: every workload that can use Batch ({@code T157} nightly
 * insights, {@code T158} bulk re-embedding, {@code T159} eval) checks
 * {@link #enabled} and falls back to its existing synchronous path when the
 * tier is disabled, so turning the feature off restores prior behaviour exactly.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
public class TurBatchProperty {

    /** Master switch for the Batch tier. When false, consumers run synchronously. */
    private boolean enabled = false;

    /** Cron for the poller that refreshes in-flight batch jobs (default every 5 min). */
    private String pollCron = "0 */5 * * * *";

    /** Max requests packed into a single submitted batch (vendor limits are far higher). */
    private int maxRequestsPerBatch = 1000;

    /** Nested per-workload toggles. */
    private TurBatchSummarizationProperty summarization = new TurBatchSummarizationProperty();
    private TurBatchEmbeddingProperty embedding = new TurBatchEmbeddingProperty();
    private TurBatchEvalProperty eval = new TurBatchEvalProperty();

    /** T157 — nightly per-site AI Insights summarization via Batch. */
    @Getter
    @Setter
    public static class TurBatchSummarizationProperty {
        private boolean enabled = false;
        /** Cron for the nightly enqueue job (default 03:30 daily). */
        private String cron = "0 30 3 * * *";
    }

    /** T158 — bulk re-embedding via Batch on embedding-model switch. */
    @Getter
    @Setter
    public static class TurBatchEmbeddingProperty {
        private boolean enabled = false;
    }

    /** T159 — LLM-Judge eval at the Batch tier. */
    @Getter
    @Setter
    public static class TurBatchEvalProperty {
        private boolean enabled = false;
    }
}
