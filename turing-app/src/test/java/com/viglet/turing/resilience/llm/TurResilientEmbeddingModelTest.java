/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.resilience.llm;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;

import com.viglet.turing.observability.TurLlmObservation;
import com.viglet.turing.resilience.TurResilienceExecutor;
import com.viglet.turing.resilience.TurResilienceRegistry;
import com.viglet.turing.resilience.config.TurResilienceProperties;
import com.viglet.turing.resilience.config.TurResilienceProperties.Group;
import com.viglet.turing.resilience.config.TurResilienceProperties.Profile;
import com.viglet.turing.resilience.config.TurResilienceProperties.RetryConfig;

import io.micrometer.observation.ObservationRegistry;

class TurResilientEmbeddingModelTest {

    private EmbeddingModel delegate;
    private TurResilientEmbeddingModel resilient;

    @BeforeEach
    void setUp() {
        delegate = mock(EmbeddingModel.class);

        TurResilienceProperties properties = new TurResilienceProperties();
        properties.setEnabled(true);
        Group llm = new Group();
        llm.setEnabled(true);
        Profile defaults = new Profile();
        RetryConfig retry = new RetryConfig();
        retry.setMaxAttempts(2);
        retry.setWaitDuration(java.time.Duration.ofMillis(5));
        retry.setRetryOnExceptions(List.of("java.io.IOException"));
        defaults.setRetry(retry);
        llm.setDefaults(defaults);
        properties.setLlm(llm);

        TurResilienceRegistry registry = new TurResilienceRegistry(properties, null);
        TurResilienceExecutor executor = new TurResilienceExecutor(registry);
        TurLlmObservation observation = new TurLlmObservation(ObservationRegistry.NOOP, null);
        resilient = new TurResilientEmbeddingModel(delegate, executor, observation, "openai");
    }

    @Test
    void embed_retriesAndReturnsResult() {
        float[] expected = new float[] { 0.1f, 0.2f };
        when(delegate.embed(any(Document.class)))
                .thenThrow(new RuntimeException(new IOException("transient")))
                .thenReturn(expected);

        Document doc = new Document("hello");
        float[] actual = resilient.embed(doc);

        assertArrayEquals(expected, actual);
        verify(delegate, times(2)).embed(doc);
    }
}
