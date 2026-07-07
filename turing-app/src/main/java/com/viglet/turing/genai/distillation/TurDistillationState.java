/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.distillation;

/**
 * F.9 / §X.10.c — lifecycle of a distillation job (T169).
 *
 * <pre>
 *   RUNNING ──► (fine-tune succeeds) ──► eval gate ──► SWAPPED   (eval passed → model swapped)
 *          │                                       ├─► EVAL_FAILED (eval failed → model reverted)
 *          │                                       └─► PROPOSED   (T191 propose-only: candidate beat
 *          │                                                       baseline → suggestion opened, NOT applied)
 *          └──► (fine-tune fails/cancelled) ─────────► FAILED
 * </pre>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurDistillationState {
    /** Fine-tuning job submitted, not yet terminal. */
    RUNNING,
    /** Fine-tune succeeded and the eval gate passed → the agent's model was swapped. */
    SWAPPED,
    /** Fine-tune succeeded but the eval gate failed → the original model was kept. */
    EVAL_FAILED,
    /**
     * T191 / §X.15.e — propose-only run: the candidate beat the baseline on the
     * eval gate and a {@code TurAgentSuggestion(DISTILLATION_CANDIDATE)} was opened
     * for human approval; the agent's model was deliberately NOT applied.
     */
    PROPOSED,
    /** Fine-tuning failed / was cancelled, or the export/submit step errored. */
    FAILED;

    public boolean isTerminal() {
        return this != RUNNING;
    }
}
