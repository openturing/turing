/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.mcp.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.sn.dsl.TurDslQueryRequest;
import com.viglet.turing.sn.dsl.TurDslSearchResponse;
import com.viglet.turing.sn.dsl.TurDslSearchService;
import com.viglet.turing.system.TurLlmSummaryService;
import com.viglet.turing.system.TurLlmSummaryService.SummaryResult;

/**
 * Unit tests for {@link TurMcpRagToolService} — the T248 {@code rag_answer} tool.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@ExtendWith(MockitoExtension.class)
class TurMcpRagToolServiceTest {

    @Mock
    private TurDslSearchService turDslSearchService;

    @Mock
    private TurLlmSummaryService llmSummaryService;

    @InjectMocks
    private TurMcpRagToolService service;

    private TurDslSearchResponse responseWithOneHit() {
        TurDslSearchResponse.Hit hit = new TurDslSearchResponse.Hit(
                "doc-1", 0.9,
                Map.of("title", "Auth Refresh Flow", "url", "/docs/auth.html",
                        "text", "The auth refresh flow rotates tokens every hour."),
                null);
        TurDslSearchResponse.Hits hits = new TurDslSearchResponse.Hits(
                new TurDslSearchResponse.Total(1, "eq"), 0.9, List.of(hit));
        return new TurDslSearchResponse(3, false, hits, Map.of());
    }

    @Test
    void missingSite_returnsError() {
        String result = service.ragAnswer("", "en_US", "how does auth work?", 5);
        assertTrue(result.startsWith("Error"), result);
        verify(turDslSearchService, never()).search(anyString(), anyString(), any());
    }

    @Test
    void missingQuestion_returnsError() {
        String result = service.ragAnswer("wiki", "en_US", "  ", 5);
        assertTrue(result.startsWith("Error"), result);
    }

    @Test
    void noHits_returnsNotFoundMessage() {
        when(turDslSearchService.search(eq("wiki"), eq("en_US"), any(TurDslQueryRequest.class)))
                .thenReturn(Optional.empty());
        String result = service.ragAnswer("wiki", "en_US", "anything", 5);
        assertTrue(result.startsWith("No documents found"), result);
    }

    @Test
    void hitsButNoLlm_returnsSourcesOnly() {
        when(turDslSearchService.search(eq("wiki"), eq("en_US"), any(TurDslQueryRequest.class)))
                .thenReturn(Optional.of(responseWithOneHit()));
        when(llmSummaryService.isAvailable()).thenReturn(false);

        String result = service.ragAnswer("wiki", "en_US", "how does auth refresh work?", 5);

        assertTrue(result.contains("Auth Refresh Flow"), result);
        assertTrue(result.contains("/docs/auth.html"), result);
        verify(llmSummaryService, never()).generate(anyString(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void hitsWithLlm_returnsGroundedAnswerWithCitations() {
        when(turDslSearchService.search(eq("wiki"), eq("en_US"), any(TurDslQueryRequest.class)))
                .thenReturn(Optional.of(responseWithOneHit()));
        when(llmSummaryService.isAvailable()).thenReturn(true);
        when(llmSummaryService.generate(anyString(), anyString(), anyString(), eq(false)))
                .thenReturn(new SummaryResult(true, null, "Tokens rotate hourly [1].", false));

        String result = service.ragAnswer("wiki", "en_US", "how does auth refresh work?", 5);

        assertTrue(result.contains("Tokens rotate hourly [1]."), result);
        // Citation footer present and mapping [1] to the source.
        assertTrue(result.contains("Sources:"), result);
        assertTrue(result.contains("[1] Auth Refresh Flow"), result);
        assertTrue(result.contains("/docs/auth.html"), result);
    }

    @Test
    void llmFailure_fallsBackToSources() {
        when(turDslSearchService.search(eq("wiki"), eq("en_US"), any(TurDslQueryRequest.class)))
                .thenReturn(Optional.of(responseWithOneHit()));
        when(llmSummaryService.isAvailable()).thenReturn(true);
        when(llmSummaryService.generate(anyString(), anyString(), anyString(), eq(false)))
                .thenReturn(new SummaryResult(false, "rate limited", null, false));

        String result = service.ragAnswer("wiki", "en_US", "q", 5);

        assertTrue(result.contains("Most relevant sources"), result);
        assertTrue(result.contains("Auth Refresh Flow"), result);
        assertFalse(result.contains("Tokens rotate"), result);
    }
}
