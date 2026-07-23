/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.genai.catalog.TurCatalogCopilotService.ChatTurn;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.sn.dsl.TurDslQueryRequest;
import com.viglet.turing.sn.dsl.TurDslSearchResponse;
import com.viglet.turing.sn.dsl.TurDslSearchService;
import com.viglet.turing.sn.dsl.eval.TurNLFacetParser;
import com.viglet.turing.sn.ranking.TurSNHybridRankingService;
import com.viglet.turing.system.TurDefaultChatModelResolver;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Deterministic unit coverage for the T392 grounded catalog copilot: a fake
 * NL→facet parser, a stubbed DSL search and a canned LLM let us assert the whole
 * compose-and-cite pipeline without an LLM or a live index.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurCatalogCopilotServiceTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    @Mock
    private TurNLFacetParser parser;
    @Mock
    private TurDslSearchService dslSearchService;
    @Mock
    private TurSNSiteRepository turSNSiteRepository;
    @Mock
    private TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
    @Mock
    private TurSNHybridRankingService hybridRankingService;
    @Mock
    private TurDefaultChatModelResolver chatModelResolver;
    // T793 / §LIV.4 — stuff-all config. Mock default isStuffAllEnabled()=false keeps
    // the existing (filtered) tests on the NL→DSL path unchanged.
    @Mock
    private com.viglet.turing.properties.TurCatalogCopilotProperty copilotProperty;

    @InjectMocks
    private TurCatalogCopilotService service;

    @Test
    void answersGroundedWithCitationsAndAppliedFilters() {
        TurSNSite site = new TurSNSite();
        site.setName("catalog");

        ChatModel chatModel = modelReturning("The best fit is the Online Data MSc [1].");
        when(chatModelResolver.resolve()).thenReturn(Optional.of(chatModel));
        when(turSNSiteRepository.findByNameIgnoreCase("catalog")).thenReturn(Optional.of(site));
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1)).thenReturn(List.of(
                field("modality", TurSEFieldType.STRING, true),
                field("price", TurSEFieldType.INT, true)));
        when(parser.isAvailable()).thenReturn(true);
        when(parser.parse(any())).thenReturn(groundedQuery());
        when(hybridRankingService.isEnabled(site)).thenReturn(false);
        when(dslSearchService.search(eq("catalog"), eq("en"), any(TurDslQueryRequest.class)))
                .thenReturn(Optional.of(twoHitResponse()));

        TurCatalogCopilotResult result = service.answer("catalog", "en",
                List.of(new ChatTurn("user", "online data masters under 20k")));

        assertThat(result.available()).isTrue();
        assertThat(result.answer()).contains("[1]");
        assertThat(result.totalHits()).isEqualTo(2L);
        assertThat(result.citations()).hasSize(2);
        assertThat(result.citations().getFirst().rank()).isEqualTo(1);
        assertThat(result.citations().getFirst().title()).isEqualTo("Online Data MSc");
        assertThat(result.citations().getFirst().url()).isEqualTo("https://example.edu/data-msc");
        assertThat(result.groundedQuerySummary())
                .contains("modality=online")
                .contains("price")
                .contains("20000");
    }

    @Test
    void stuffAllGroundsOnAllRowsWhenWithinBudget() {
        // T793 — the whole catalog fits the budget → skip NL→DSL, ground on all rows.
        TurSNSite site = new TurSNSite();
        site.setName("catalog");
        ChatModel chatModel = modelReturning("Both options [1][2].");
        when(chatModelResolver.resolve()).thenReturn(Optional.of(chatModel));
        when(turSNSiteRepository.findByNameIgnoreCase("catalog")).thenReturn(Optional.of(site));
        when(copilotProperty.isStuffAllEnabled()).thenReturn(true);
        when(copilotProperty.getStuffAllMaxDocs()).thenReturn(1000);
        when(copilotProperty.getStuffAllTokenBudget()).thenReturn(40_000);
        when(dslSearchService.search(eq("catalog"), eq("en"), any(TurDslQueryRequest.class)))
                .thenReturn(Optional.of(twoHitResponse()));

        TurCatalogCopilotResult result = service.answer("catalog", "en",
                List.of(new ChatTurn("user", "what do you have?")));

        assertThat(result.available()).isTrue();
        assertThat(result.citations()).hasSize(2);
        assertThat(result.totalHits()).isEqualTo(2L);
        // Stuff-all applies no query filters, so the grounded summary is blank —
        // distinguishing it from the filtered path (which carries filter clauses).
        assertThat(result.groundedQuerySummary()).isNullOrEmpty();
    }

    @Test
    void stuffAllFallsBackToFilteredRetrievalWhenOverBudget() {
        // T793 — tiny budget forces the fallback to the normal NL→DSL retrieval.
        TurSNSite site = new TurSNSite();
        site.setName("catalog");
        ChatModel chatModel = modelReturning("Filtered [1].");
        when(chatModelResolver.resolve()).thenReturn(Optional.of(chatModel));
        when(turSNSiteRepository.findByNameIgnoreCase("catalog")).thenReturn(Optional.of(site));
        when(copilotProperty.isStuffAllEnabled()).thenReturn(true);
        when(copilotProperty.getStuffAllMaxDocs()).thenReturn(1000);
        when(copilotProperty.getStuffAllTokenBudget()).thenReturn(1); // ~0 → always over budget
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1)).thenReturn(List.of(
                field("modality", TurSEFieldType.STRING, true),
                field("price", TurSEFieldType.INT, true)));
        when(parser.isAvailable()).thenReturn(true);
        when(parser.parse(any())).thenReturn(groundedQuery());
        when(hybridRankingService.isEnabled(site)).thenReturn(false);
        when(dslSearchService.search(eq("catalog"), eq("en"), any(TurDslQueryRequest.class)))
                .thenReturn(Optional.of(twoHitResponse()));

        TurCatalogCopilotResult result = service.answer("catalog", "en",
                List.of(new ChatTurn("user", "online data masters under 20k")));

        assertThat(result.available()).isTrue();
        // Filtered fallback ran → the grounded summary carries the parsed clauses.
        assertThat(result.groundedQuerySummary()).contains("modality=online");
    }

    @Test
    void returnsErrorWhenNoDefaultLlm() {
        when(chatModelResolver.resolve()).thenReturn(Optional.empty());

        TurCatalogCopilotResult result = service.answer("catalog", "en",
                List.of(new ChatTurn("user", "anything")));

        assertThat(result.available()).isFalse();
        assertThat(result.error()).containsIgnoringCase("default LLM");
        assertThat(result.citations()).isEmpty();
    }

    @Test
    void returnsErrorWhenSiteMissing() {
        when(chatModelResolver.resolve()).thenReturn(Optional.of(mock(ChatModel.class)));
        when(turSNSiteRepository.findByNameIgnoreCase("ghost")).thenReturn(Optional.empty());

        TurCatalogCopilotResult result = service.answer("ghost", "en",
                List.of(new ChatTurn("user", "anything")));

        assertThat(result.available()).isFalse();
        assertThat(result.error()).containsIgnoringCase("not found");
    }

    @Test
    void isAvailableDelegatesToParser() {
        when(parser.isAvailable()).thenReturn(true);
        assertThat(service.isAvailable()).isTrue();
    }

    // ─────────────────────────── fixtures ───────────────────────────

    private static ChatModel modelReturning(String text) {
        ChatModel model = mock(ChatModel.class);
        ChatResponse response = new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
        when(model.call(any(Prompt.class))).thenReturn(response);
        return model;
    }

    private static TurSNSiteFieldExt field(String name, TurSEFieldType type, boolean facet) {
        TurSNSiteFieldExt ext = new TurSNSiteFieldExt();
        ext.setName(name);
        ext.setType(type);
        ext.setFacet(facet ? 1 : 0);
        return ext;
    }

    private static TurDslQueryRequest groundedQuery() {
        return MAPPER.readValue("""
                {"query":{"bool":{"filter":[
                  {"term":{"modality":"online"}},
                  {"range":{"price":{"lte":20000}}}
                ]}},"size":8}""", TurDslQueryRequest.class);
    }

    private static TurDslSearchResponse twoHitResponse() {
        Map<String, Object> doc1 = new LinkedHashMap<>();
        doc1.put("title", "Online Data MSc");
        doc1.put("url", "https://example.edu/data-msc");
        doc1.put("abstract", "A fully online master's program in data science.");
        doc1.put("modality", "online");
        doc1.put("price", "18000");

        Map<String, Object> doc2 = new LinkedHashMap<>();
        doc2.put("title", "Analytics Specialization");
        doc2.put("url", "https://example.edu/analytics");
        doc2.put("modality", "online");
        doc2.put("price", "15000");

        TurDslSearchResponse.Hit h1 = new TurDslSearchResponse.Hit("d1", 2.5, doc1, null);
        TurDslSearchResponse.Hit h2 = new TurDslSearchResponse.Hit("d2", 1.2, doc2, null);
        TurDslSearchResponse.Hits hits = new TurDslSearchResponse.Hits(
                new TurDslSearchResponse.Total(2, "eq"), 2.5, List.of(h1, h2));
        return new TurDslSearchResponse(7L, false, hits, null);
    }
}
