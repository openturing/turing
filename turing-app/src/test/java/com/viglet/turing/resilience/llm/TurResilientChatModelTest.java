/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.resilience.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import com.viglet.turing.observability.TurLlmObservation;
import com.viglet.turing.resilience.TurResilienceExecutor;
import com.viglet.turing.resilience.TurResilienceRegistry;
import com.viglet.turing.resilience.config.TurResilienceProperties;
import com.viglet.turing.resilience.config.TurResilienceProperties.Group;
import com.viglet.turing.resilience.config.TurResilienceProperties.Profile;
import com.viglet.turing.resilience.config.TurResilienceProperties.RetryConfig;

import io.micrometer.observation.ObservationRegistry;

import reactor.core.publisher.Flux;

class TurResilientChatModelTest {

    private ChatModel delegate;
    private TurResilienceProperties properties;
    private TurResilienceRegistry registry;
    private TurResilientChatModel resilient;

    @BeforeEach
    void setUp() {
        delegate = mock(ChatModel.class);

        properties = new TurResilienceProperties();
        properties.setEnabled(true);
        Group llm = new Group();
        llm.setEnabled(true);
        Profile defaults = new Profile();
        RetryConfig retry = new RetryConfig();
        retry.setMaxAttempts(3);
        retry.setWaitDuration(java.time.Duration.ofMillis(5));
        retry.setRetryOnExceptions(List.of("java.io.IOException"));
        defaults.setRetry(retry);
        llm.setDefaults(defaults);
        properties.setLlm(llm);

        registry = new TurResilienceRegistry(properties, null);
        TurResilienceExecutor executor = new TurResilienceExecutor(registry);
        TurLlmObservation observation = new TurLlmObservation(ObservationRegistry.NOOP, null);
        resilient = new TurResilientChatModel(delegate, executor, registry, observation, "openai");
    }

    @Test
    void call_retriesOnTransientIoException() {
        ChatResponse expected = mock(ChatResponse.class);
        when(delegate.call(any(Prompt.class)))
                .thenThrow(new RuntimeException(new IOException("retryable")))
                .thenThrow(new RuntimeException(new IOException("retryable")))
                .thenReturn(expected);

        ChatResponse actual = resilient.call(new Prompt(new UserMessage("hi")));

        assertEquals(expected, actual);
        verify(delegate, times(3)).call(any(Prompt.class));
    }

    @Test
    void stream_doesNotRetry_evenIfErrorOccurs() {
        Prompt prompt = new Prompt(new UserMessage("hi"));
        when(delegate.stream(prompt))
                .thenReturn(Flux.error(new RuntimeException(new IOException("transient"))));

        Flux<ChatResponse> output = resilient.stream(prompt);

        assertNotNull(output);
        assertThrows(RuntimeException.class, output::blockLast);
        // Stream errors must not trigger retry: replaying would duplicate tokens for SSE consumers.
        verify(delegate, times(1)).stream(prompt);
    }
}
