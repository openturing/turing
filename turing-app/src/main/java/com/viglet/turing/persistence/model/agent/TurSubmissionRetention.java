/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.model.agent;

/**
 * T66 / §VII.6.g — per-agent retention policy for completed
 * {@code chat_flow_submission} rows. A compliance hook (LGPD / GDPR
 * right-to-erasure) that bounds how long the captured submission archive
 * for an agent lives. Stored as a {@code STRING} column (see
 * {@link TurAIAgent#submissionRetention}) so adding modes later never risks
 * ordinal drift.
 *
 * <p>This governs the <em>finished-submission archive</em> only — the
 * complementary {@code pii_*} slot expiry on live {@code chat_flow_state}
 * rows is handled independently by the T61 TTL cleanup job
 * ({@code TurPiiSlotTtlCleanupJob}). The two layers compose: T61 strips PII
 * from in-flight conversations on a global hourly TTL; this purges the
 * per-agent completed-submission record on a daily job (or right after an
 * export).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public enum TurSubmissionRetention {

    /** Keep every submission indefinitely. Default — preserves pre-T66 behaviour. */
    RETAIN_FOREVER,

    /**
     * Delete submissions whose {@code completedAt} is older than
     * {@link TurAIAgent#submissionRetentionDays} days. A null / non-positive
     * day count disables enforcement (treated as {@link #RETAIN_FOREVER}).
     * Swept by the daily {@code TurSubmissionRetentionCleanupJob}.
     */
    RETAIN_DAYS,

    /**
     * Delete a conversation's submissions as soon as it is exported via the
     * T65 {@code GET /api/chat/sessions/{conversationId}/export} endpoint —
     * the export is treated as the system-of-record archive, after which
     * Turing purges its copy. Submissions for conversations that are never
     * exported are not touched by this mode.
     */
    DELETE_AFTER_EXPORT
}
