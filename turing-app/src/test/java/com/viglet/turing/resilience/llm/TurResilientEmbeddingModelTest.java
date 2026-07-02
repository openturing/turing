/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.resilience.llm;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;

import com.viglet.turing.genai.provider.llm.TurMultimodalEmbeddingModel;
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

    @Test
    void embedImage_textOnlyDelegate_unsupported() {
        // The default delegate is a plain EmbeddingModel — no image side.
        assertFalse(resilient.supportsImageEmbedding());
        assertThrows(UnsupportedOperationException.class,
                () -> resilient.embedImage(new byte[] { 1, 2 }, "image/png"));
    }

    @Test
    void embedImage_multimodalDelegate_delegatesThroughResilience() {
        EmbeddingModel multimodalDelegate = mock(EmbeddingModel.class,
                withSettings().extraInterfaces(TurMultimodalEmbeddingModel.class));
        float[] expected = new float[] { 0.7f, 0.8f, 0.9f };
        TurMultimodalEmbeddingModel asMultimodal = (TurMultimodalEmbeddingModel) multimodalDelegate;
        when(asMultimodal.supportsImageEmbedding()).thenReturn(true);
        when(asMultimodal.embedImage(any(), any())).thenReturn(expected);

        TurResilientEmbeddingModel mmResilient = new TurResilientEmbeddingModel(
                multimodalDelegate, resilientExecutor(), noopObservation(), "voyage");

        assertTrue(mmResilient.supportsImageEmbedding());
        byte[] png = new byte[] { 9, 8, 7 };
        assertArrayEquals(expected, mmResilient.embedImage(png, "image/png"));
        verify(asMultimodal).embedImage(png, "image/png");
    }

    @Test
    void embedDocumentChunks_textOnlyDelegate_unsupported() {
        assertFalse(resilient.supportsContextualChunks());
        assertThrows(UnsupportedOperationException.class,
                () -> resilient.embedDocumentChunks(List.of("a", "b")));
    }

    @Test
    void embedDocumentChunks_contextualDelegate_delegatesThroughResilience() {
        EmbeddingModel contextualDelegate = mock(EmbeddingModel.class,
                withSettings().extraInterfaces(
                        com.viglet.turing.genai.provider.llm.TurContextualEmbeddingModel.class));
        var asContextual =
                (com.viglet.turing.genai.provider.llm.TurContextualEmbeddingModel) contextualDelegate;
        List<float[]> expected = List.of(new float[] { 0.1f }, new float[] { 0.2f });
        when(asContextual.supportsContextualChunks()).thenReturn(true);
        when(asContextual.embedDocumentChunks(any())).thenReturn(expected);

        TurResilientEmbeddingModel ctxResilient = new TurResilientEmbeddingModel(
                contextualDelegate, resilientExecutor(), noopObservation(), "voyage");

        assertTrue(ctxResilient.supportsContextualChunks());
        List<String> chunks = List.of("chunk1", "chunk2");
        assertArrayEquals(expected.get(0), ctxResilient.embedDocumentChunks(chunks).get(0));
        verify(asContextual).embedDocumentChunks(chunks);
    }

    private TurResilienceExecutor resilientExecutor() {
        TurResilienceProperties properties = new TurResilienceProperties();
        properties.setEnabled(true);
        Group llm = new Group();
        llm.setEnabled(true);
        llm.setDefaults(new Profile());
        properties.setLlm(llm);
        return new TurResilienceExecutor(new TurResilienceRegistry(properties, null));
    }

    private TurLlmObservation noopObservation() {
        return new TurLlmObservation(ObservationRegistry.NOOP, null);
    }
}
