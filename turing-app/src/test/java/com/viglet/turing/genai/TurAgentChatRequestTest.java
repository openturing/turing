/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

/**
 * T647 / §XXXVII.9 — the tool trust flag on the chat request. The
 * backward-compatible constructor must default to a trusted (non-anonymous)
 * caller so authenticated agent/persona chat is never gated; only the anonymous
 * public SN path opts into {@code untrustedCaller=true}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurAgentChatRequestTest {

    private static final TurAIAgent AGENT = new TurAIAgent();
    private static final TurLLMInstance LLM = new TurLLMInstance();

    @Test
    void backwardCompatibleConstructorIsTrustedByDefault() {
        var request = new TurAgentChatRequest(AGENT, LLM, List.of(), null, null, null, null, null);
        assertThat(request.untrustedCaller()).isFalse();
    }

    @Test
    void untrustedCallerFlagIsCarried() {
        var request = new TurAgentChatRequest(AGENT, LLM, List.of(), null, null, null, null, null, true);
        assertThat(request.untrustedCaller()).isTrue();
    }
}
