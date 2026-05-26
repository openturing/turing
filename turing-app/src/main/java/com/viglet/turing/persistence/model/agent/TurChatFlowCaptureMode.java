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
 * T51 / §VII.4.e — when (relative to the guardrail judge) a slot-collecting
 * {@code aiQuestion} node persists the user's reply into its
 * {@code outputVariable}, and what gates the flow's advance.
 *
 * <p>Only the {@code LLM_JUDGE} guardrail honours this setting; the other
 * methodologies have no judge to invert. Default is
 * {@link #VALIDATE_THEN_CAPTURE} so every pre-T51 flow keeps its exact
 * behaviour with no migration.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public enum TurChatFlowCaptureMode {

    /**
     * Legacy behaviour (default). The judge runs first and <em>gates</em> the
     * capture: the slot is written only when the judge accepts the value and
     * declares {@code ready_to_advance}. A rejected reply leaves the slot
     * {@code null} and parks the cursor on the node — the "judge rejects →
     * flow stuck with slot=null" pattern T51 targets. {@code onJudgeReject}
     * (T45) is the per-node mitigation under this mode.
     */
    VALIDATE_THEN_CAPTURE,

    /**
     * Capture-first, validation still gates. The user's reply is persisted
     * into the slot <em>before</em> the judge runs (refined with the judge's
     * cleaner extraction when available), so the slot is never {@code null}
     * after a non-blank turn. The judge becomes advisory — it can no longer
     * block a value that passes the node's {@code validationRule}; it only
     * writes a {@code <slot>__confidence} grade. A <em>failing</em>
     * deterministic {@code validationRule} (CPF / email regex) still keeps
     * the cursor on the node and re-prompts (now with the value captured and
     * {@code confidence=low}).
     */
    CAPTURE_THEN_GATE,

    /**
     * Pure capture-then-grade. The reply is persisted into the slot before
     * the judge runs, and the flow <em>always</em> advances (unless the user
     * abandoned the flow). Both the {@code validationRule} and the judge are
     * purely advisory under this mode — they only set the
     * {@code <slot>__confidence} grade; the cursor never holds. Maximum
     * faithfulness to "the flow never stalls", widest behavioural change.
     */
    CAPTURE_THEN_GRADE
}
