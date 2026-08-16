/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.resilience.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.http.HttpStatus;

import com.viglet.turing.api.exception.TurQuotaExhaustedException;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProvider;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProviderFactory;
import com.viglet.turing.observability.TurLlmObservation;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.resilience.TurResilienceExecutor;
import com.viglet.turing.resilience.TurResilienceRegistry;
import com.viglet.turing.tenant.TurTenantQuotaService;

/**
 * T963 / §LXXII — the tenant's token budget is admitted at the one boundary
 * every LLM call comes through.
 *
 * <p>These tests exist because the alternative placement — a list of the ~45
 * endpoints that reach a model — is a list that silently stops being complete.
 * What is asserted here is the property that makes the single boundary worth
 * choosing: a refusal happens before the provider is ever consulted, so no
 * partial work and no open stream precedes it.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurLlmModelFactoryQuotaTest {

    @Mock
    private TurGenAiLlmProviderFactory providerFactory;
    @Mock
    private TurResilienceExecutor executor;
    @Mock
    private TurResilienceRegistry registry;
    @Mock
    private TurLlmObservation observation;
    @Mock
    private TurTenantQuotaService turTenantQuotaService;
    @Mock
    private TurGenAiLlmProvider provider;
    @Mock
    private ChatModel chatModel;
    @Mock
    private EmbeddingModel embeddingModel;

    private TurLlmModelFactory factory;
    private final TurLLMInstance instance = new TurLLMInstance();

    @BeforeEach
    void setUp() {
        factory = new TurLlmModelFactory(providerFactory, executor, registry, observation,
                turTenantQuotaService);
        lenient().when(providerFactory.getProvider(any())).thenReturn(provider);
        lenient().when(provider.getPluginType()).thenReturn("openai");
        lenient().when(provider.createChatModel(any(), anyString())).thenReturn(chatModel);
        lenient().when(provider.createStreamingChatModel(any(), anyString())).thenReturn(chatModel);
        lenient().when(provider.createEmbeddingModel(any(), anyString())).thenReturn(embeddingModel);
    }

    @Test
    void chatIsAdmittedAgainstTheTenantBudget() {
        assertThat(factory.createChatModel(instance, "key")).isNotNull();
        verify(turTenantQuotaService).checkLlmAdmission();
    }

    @Test
    void streamingChatIsAdmittedToo() {
        assertThat(factory.createStreamingChatModel(instance, "key")).isNotNull();
        verify(turTenantQuotaService).checkLlmAdmission();
    }

    @Test
    void anExhaustedBudgetRefusesBeforeTheProviderIsConsulted() {
        doThrow(quotaExhausted()).when(turTenantQuotaService).checkLlmAdmission();

        assertThatThrownBy(() -> factory.createChatModel(instance, "key"))
                .isInstanceOf(TurQuotaExhaustedException.class);

        verify(providerFactory, never()).getProvider(any());
    }

    /**
     * The streaming refusal has to land before the {@code Flux} exists, or the
     * client gets a 200 carrying an error event and the console's banner — which
     * reads HTTP status — never fires.
     */
    @Test
    void theStreamingRefusalPrecedesTheStream() {
        doThrow(quotaExhausted()).when(turTenantQuotaService).checkLlmAdmission();

        assertThatThrownBy(() -> factory.createStreamingChatModel(instance, "key"))
                .isInstanceOf(TurQuotaExhaustedException.class);

        verify(provider, never()).createStreamingChatModel(any(), anyString());
    }

    /**
     * Embeddings are what indexing and retrieval run on. Refusing them would
     * turn an exhausted chat credit into a search outage, so the budget does not
     * reach here — deliberately, and asserted so a later refactor has to argue
     * with a test rather than with a comment.
     */
    @Test
    void embeddingsAreNotAdmitted() {
        // lenient: the whole assertion is that this stub is never reached.
        lenient().doThrow(quotaExhausted()).when(turTenantQuotaService).checkLlmAdmission();

        assertThatCode(() -> factory.createEmbeddingModel(instance, "key")).doesNotThrowAnyException();
    }

    private static TurQuotaExhaustedException quotaExhausted() {
        return new TurQuotaExhaustedException(HttpStatus.TOO_MANY_REQUESTS, "monthlyTokens",
                100_000L, 100_000L, null, null, "Plan limit reached.");
    }
}
