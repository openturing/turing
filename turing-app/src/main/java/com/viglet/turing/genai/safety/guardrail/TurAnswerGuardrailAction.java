/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.safety.guardrail;

/**
 * T516 / §XXVIII.12 — the action a {@link TurAnswerGuardrailStrategy} recommends
 * for a checked answer.
 *
 * <ul>
 *   <li>{@link #PASS} — the answer is grounded/clean; deliver it unchanged.</li>
 *   <li>{@link #FLAG} — a violation was detected but the answer is still
 *       delivered (the chat UI shows the confidence/category signal beside
 *       it). This is the default non-blocking posture.</li>
 *   <li>{@link #BLOCK} — the guardrail intervened and the answer should be
 *       suppressed or replaced; only honoured when the deployment opts into
 *       blocking ({@code turing.safety.guardrail.block-on-violation}).</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurAnswerGuardrailAction {
    PASS,
    FLAG,
    BLOCK
}
