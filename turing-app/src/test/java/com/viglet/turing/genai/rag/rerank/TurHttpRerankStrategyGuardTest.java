/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.rag.rerank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;

import com.viglet.turing.system.TurGlobalSettingsService;

/**
 * T338 / T339 — the HTTP strategies must short-circuit to an empty list (so the
 * facade keeps retrieval order) when their backend is not configured, without
 * touching the network.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@ExtendWith(MockitoExtension.class)
class TurHttpRerankStrategyGuardTest {

    @Mock
    private TurHttpRerankClient httpRerankClient;

    @Mock
    private TurGlobalSettingsService settings;

    private static TurRagRerankRequest request() {
        return new TurRagRerankRequest("q",
                List.of(Document.builder().id("d0").text("a").build(),
                        Document.builder().id("d1").text("b").build()),
                1, null);
    }

    @Test
    void crossEncoderReturnsEmptyWhenEndpointBlank() {
        when(settings.getRagSnRerankEndpoint()).thenReturn("  ");
        TurCrossEncoderRerankStrategy strategy =
                new TurCrossEncoderRerankStrategy(httpRerankClient, settings);

        assertThat(strategy.rerank(request())).isEmpty();
        verify(httpRerankClient, never())
                .rankIndices(anyString(), any(), any(), anyString(), anyList(), anyInt());
    }

    @Test
    void cohereReturnsEmptyWhenApiKeyBlank() {
        when(settings.getRagSnRerankApiKey()).thenReturn("");
        TurCohereRerankStrategy strategy =
                new TurCohereRerankStrategy(httpRerankClient, settings);

        assertThat(strategy.rerank(request())).isEmpty();
        verify(httpRerankClient, never())
                .rankIndices(anyString(), any(), any(), anyString(), anyList(), anyInt());
    }
}
