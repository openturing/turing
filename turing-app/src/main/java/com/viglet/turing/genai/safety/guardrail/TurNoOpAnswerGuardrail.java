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

import org.springframework.stereotype.Component;

/**
 * T516 / §XXVIII.12 — the always-present {@code NONE} guardrail. Returns a clean
 * PASS verdict for every answer; the facade treats {@code NONE} as "no signal"
 * and emits no event, so this is purely the factory's safe fallback (the chat
 * path is unchanged when no real strategy is configured).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurNoOpAnswerGuardrail implements TurAnswerGuardrailStrategy {

    @Override
    public TurAnswerGuardrailType getType() {
        return TurAnswerGuardrailType.NONE;
    }

    @Override
    public TurAnswerGuardrailVerdict evaluate(TurAnswerGuardrailRequest request) {
        return TurAnswerGuardrailVerdict.pass(TurAnswerGuardrailType.NONE);
    }
}
