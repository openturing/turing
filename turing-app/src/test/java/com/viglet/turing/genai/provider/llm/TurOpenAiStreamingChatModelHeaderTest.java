/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.provider.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

/**
 * T964 / §LXXIII — the streaming transport mints its extra headers per request.
 *
 * <p>The supplier is read eagerly inside {@code stream(...)}, before the request
 * is issued, so counting invocations proves the timing without a network hop or
 * a subscription: what matters is that a second call does not reuse the first
 * call's assertion.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurOpenAiStreamingChatModelHeaderTest {

    @Test
    void eachStreamMintsItsOwnHeaders() {
        AtomicInteger mints = new AtomicInteger();
        TurOpenAiStreamingChatModel model = new TurOpenAiStreamingChatModel(
                "https://llm.viglet.cloud/v1", "key", "viglet-fast",
                OpenAiChatOptions.builder().model("viglet-fast").build(),
                () -> Map.of("X-Viglet-Install-Assertion", "v1.install." + mints.incrementAndGet()));

        model.stream(new Prompt("one"));
        model.stream(new Prompt("two"));

        assertThat(mints).hasValue(2);
    }

    /** The legacy constructor still works, and asks for no extra headers. */
    @Test
    void theHeaderlessConstructorStaysHeaderless() {
        TurOpenAiStreamingChatModel model = new TurOpenAiStreamingChatModel(
                "https://api.openai.com/v1", "key", "gpt-4o",
                OpenAiChatOptions.builder().model("gpt-4o").build());

        // No supplier, no NPE — the endpoint gets the bearer token and nothing more.
        assertThat(model.getOptions()).isNotNull();
        model.stream(new Prompt("hello"));
    }
}
