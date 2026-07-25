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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.genai.catalog.TurCatalogCopilotService.ChatTurn;
import com.viglet.turing.genai.catalog.planning.TurCopilotQueryPlannerFactory;
import com.viglet.turing.genai.catalog.planning.TurDeterministicCopilotQueryPlanner;
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
    // T818 / §LIX.1 (Block BK) — planning is a seam now. These tests assert the
    // copilot's compose-and-cite pipeline, so they run the REAL default (DETERMINISTIC)
    // planner over a stubbed factory: the T811 ranking overlay + stripped-query parse
    // behaviour they cover is unchanged, and the strategies themselves are covered by
    // the planner tests in genai.catalog.planning.
    @Mock
    private TurCopilotQueryPlannerFactory plannerFactory;
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

    /**
     * T818 / §LIX.1 — resolve every site to the REAL {@code DETERMINISTIC} planner over
     * the same mocked parser, so these tests keep asserting the pre-block planning
     * behaviour (T811 ranking overlay + stripped-query parse) end to end.
     *
     * <p>{@code lenient()} because the T793 stuff-all tests answer without ever reaching
     * the planning seam.
     */
    @BeforeEach
    void resolveDeterministicPlanner() {
        lenient().when(plannerFactory.resolve(anyString())).thenReturn(
                new TurCopilotQueryPlannerFactory.Resolution(
                        new TurDeterministicCopilotQueryPlanner(parser,
                                new com.viglet.turing.sn.dsl.eval.TurNLRankingPlanner()),
                        2));
    }

    @Test
    void answersGroundedWithCitationsAndAppliedFilters() {
        TurSNSite site = new TurSNSite();
        site.setName("catalog");

        ChatModel chatModel = modelReturning("The best fit is the Online Data MSc [1], then [2].");
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
    void stuffAllUsesCompactProjectionWhenFullExceedsBudget() {
        // T822 / §LX.1 — the full (described) projection blows a tiny budget, but the
        // compact projection (key fields only, no descriptions) fits, so stuff-all
        // still grounds on ALL rows instead of dropping to filtered retrieval.
        TurSNSite site = new TurSNSite();
        site.setName("catalog");
        ChatModel chatModel = modelReturning("Cheapest option is [1].");
        when(chatModelResolver.resolve()).thenReturn(Optional.of(chatModel));
        when(turSNSiteRepository.findByNameIgnoreCase("catalog")).thenReturn(Optional.of(site));
        when(copilotProperty.isStuffAllEnabled()).thenReturn(true);
        when(copilotProperty.getStuffAllMaxDocs()).thenReturn(1000);
        // Budget sits between the verbose block (~120 tokens, long description) and the
        // compact block (~6 tokens): full is over, compact is under.
        when(copilotProperty.getStuffAllTokenBudget()).thenReturn(50);
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1)).thenReturn(List.of(
                fieldWithDescription("price", TurSEFieldType.INT,
                        "A deliberately very long declared field description ".repeat(12))));
        when(dslSearchService.search(eq("catalog"), eq("en"), any(TurDslQueryRequest.class)))
                .thenReturn(Optional.of(pricedResponse()));

        TurCatalogCopilotResult result = service.answer("catalog", "en",
                List.of(new ChatTurn("user", "cheap option?")));

        assertThat(result.available()).isTrue();
        assertThat(result.citations()).hasSize(1);
        assertThat(result.totalHits()).isEqualTo(1L);
        // Compact stuff-all handled it — the NL parser was never consulted...
        verify(parser, never()).parse(any());
        // ...and the stuff-all path leaves the grounded summary blank (vs filtered).
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
    void stuffAllReturnsOnlyTheCitationsTheAnswerCitesRenumbered() {
        // T798 / §LIV.9 + T800 / §LIV.10 — stuff-all grounds the LLM on all 12 rows,
        // but the answer only names [3] and [7]; the returned citations must shrink
        // to just those two (never the whole grounding bundle) AND be renumbered to
        // compact answer-order footnotes, with the answer text rewritten to match.
        TurSNSite site = new TurSNSite();
        site.setName("catalog");
        ChatModel chatModel = modelReturning("Consider the third [3] or the seventh [7] option.");
        when(chatModelResolver.resolve()).thenReturn(Optional.of(chatModel));
        when(turSNSiteRepository.findByNameIgnoreCase("catalog")).thenReturn(Optional.of(site));
        when(copilotProperty.isStuffAllEnabled()).thenReturn(true);
        when(copilotProperty.getStuffAllMaxDocs()).thenReturn(1000);
        when(copilotProperty.getStuffAllTokenBudget()).thenReturn(40_000);
        when(dslSearchService.search(eq("catalog"), eq("en"), any(TurDslQueryRequest.class)))
                .thenReturn(Optional.of(nHitResponse(12)));

        TurCatalogCopilotResult result = service.answer("catalog", "en",
                List.of(new ChatTurn("user", "what do you have?")));

        assertThat(result.available()).isTrue();
        // Grounded on all 12 rows...
        assertThat(result.totalHits()).isEqualTo(12L);
        // ...but only the two cited rows are returned, renumbered 1..k in answer
        // order — the raw ranks 3 and 7 become footnotes 1 and 2.
        assertThat(result.citations()).extracting(TurCatalogCitation::rank)
                .containsExactly(1, 2);
        assertThat(result.citations()).extracting(TurCatalogCitation::title)
                .containsExactly("Model 3", "Model 7");
        // ...and the answer's markers are rewritten to the compact footnotes.
        assertThat(result.answer())
                .isEqualTo("Consider the third [1] or the seventh [2] option.");
    }

    @Test
    void failsOpenToBoundedTopKWhenAnswerHasNoMarkers() {
        // T798 / §LIV.9 — stuff-all grounds on 12 rows and the answer emits no [n]
        // markers; fail-open to the filtered top-k slice (MAX_RESULTS = 8), never
        // back to all 12 rows.
        TurSNSite site = new TurSNSite();
        site.setName("catalog");
        ChatModel chatModel = modelReturning("I could not find a clear match for that request.");
        when(chatModelResolver.resolve()).thenReturn(Optional.of(chatModel));
        when(turSNSiteRepository.findByNameIgnoreCase("catalog")).thenReturn(Optional.of(site));
        when(copilotProperty.isStuffAllEnabled()).thenReturn(true);
        when(copilotProperty.getStuffAllMaxDocs()).thenReturn(1000);
        when(copilotProperty.getStuffAllTokenBudget()).thenReturn(40_000);
        when(dslSearchService.search(eq("catalog"), eq("en"), any(TurDslQueryRequest.class)))
                .thenReturn(Optional.of(nHitResponse(12)));

        TurCatalogCopilotResult result = service.answer("catalog", "en",
                List.of(new ChatTurn("user", "what do you have?")));

        assertThat(result.available()).isTrue();
        assertThat(result.totalHits()).isEqualTo(12L);
        // Bounded to the top-k slice, in rank order — not the full 12-row bundle.
        assertThat(result.citations()).hasSize(8);
        assertThat(result.citations().getFirst().rank()).isEqualTo(1);
        assertThat(result.citations().getLast().rank()).isEqualTo(8);
    }

    @Test
    void contextLabelsValuesByFieldDescriptionAndPromptsRankByNamedMetric() {
        // T799 / §LIV — the grounding context must label each attribute by its
        // declared field description (so the LLM ranks by the OVERALL metric, not
        // the biggest number), and the system prompt must carry the named-metric
        // ranking rule.
        TurSNSite site = new TurSNSite();
        site.setName("catalog");
        ChatModel chatModel = modelReturning("The most intelligent is [1].");
        when(chatModelResolver.resolve()).thenReturn(Optional.of(chatModel));
        when(turSNSiteRepository.findByNameIgnoreCase("catalog")).thenReturn(Optional.of(site));
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1)).thenReturn(List.of(
                fieldWithDescription("benchmarks_intelligenceIndex", TurSEFieldType.FLOAT,
                        "Overall intelligence index — the single headline metric"),
                fieldWithDescription("benchmarks_scores_math_value", TurSEFieldType.FLOAT,
                        "Math domain sub-score only, NOT the overall index")));
        when(parser.isAvailable()).thenReturn(true);
        when(parser.parse(any())).thenReturn(groundedQuery());
        when(hybridRankingService.isEnabled(site)).thenReturn(false);
        when(dslSearchService.search(eq("catalog"), eq("en"), any(TurDslQueryRequest.class)))
                .thenReturn(Optional.of(scoredResponse()));

        service.answer("catalog", "en",
                List.of(new ChatTurn("user", "which model is the most intelligent?")));

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        String systemText = promptCaptor.getValue().getInstructions().stream()
                .filter(SystemMessage.class::isInstance)
                .map(Message::getText)
                .findFirst().orElse("");

        // (a) values labelled by their declared description (with the flat key anchor),
        // and (b) the system prompt carries the rank-by-named-metric rule.
        assertThat(systemText)
                .contains("Overall intelligence index — the single headline metric (benchmarks_intelligenceIndex): 23.8")
                .contains("Math domain sub-score only, NOT the overall index (benchmarks_scores_math_value): 93.4")
                .contains("OVERALL metric")
                .contains("not the biggest number");
    }

    @Test
    void superlativeInjectsNumericSortAndKeepsFacetViaStrippedQuery() {
        // T811 / §LVII.1 — "chat models sorted by intelligence index" must (a) reach
        // the LLM with the ranking clause stripped (so it keeps kind=CHAT) and (b)
        // execute with a deterministic numeric sort + a top-k size.
        TurSNSite site = new TurSNSite();
        site.setName("catalog");
        ChatModel chatModel = modelReturning("Top pick is [1].");
        when(chatModelResolver.resolve()).thenReturn(Optional.of(chatModel));
        when(turSNSiteRepository.findByNameIgnoreCase("catalog")).thenReturn(Optional.of(site));
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1)).thenReturn(List.of(
                field("kind", TurSEFieldType.STRING, true),
                fieldWithDescription("benchmarks_intelligenceIndex", TurSEFieldType.FLOAT,
                        "Overall intelligence index")));
        when(parser.isAvailable()).thenReturn(true);
        ArgumentCaptor<TurNLFacetParser.ParseRequest> parseCaptor =
                ArgumentCaptor.forClass(TurNLFacetParser.ParseRequest.class);
        when(parser.parse(parseCaptor.capture())).thenReturn(kindChatQuery());
        ArgumentCaptor<TurDslQueryRequest> reqCaptor = ArgumentCaptor.forClass(TurDslQueryRequest.class);
        when(dslSearchService.search(eq("catalog"), eq("en"), reqCaptor.capture()))
                .thenReturn(Optional.of(twoHitResponse()));

        service.answer("catalog", "en",
                List.of(new ChatTurn("user", "chat models sorted by intelligence index")));

        assertThat(parseCaptor.getValue().query())
                .doesNotContainIgnoringCase("sorted by")
                .containsIgnoringCase("chat");
        TurDslQueryRequest executed = reqCaptor.getValue();
        assertThat(executed.sort()).isNotNull().hasSize(1);
        assertThat(executed.sort().getFirst()).isEqualTo(Map.of("benchmarks_intelligenceIndex", "desc"));
        assertThat(executed.size()).isNotNull();
    }

    @Test
    void sortOnlySuperlativeRunsMatchAllWithSortAndTopK() {
        // T811 / §LVII.1 — "which model has the lowest price?" strips to a blank facet
        // query, so retrieval must skip the LLM parse and run match_all + sort(asc) + size.
        TurSNSite site = new TurSNSite();
        site.setName("catalog");
        ChatModel chatModel = modelReturning("Cheapest is [1].");
        when(chatModelResolver.resolve()).thenReturn(Optional.of(chatModel));
        when(turSNSiteRepository.findByNameIgnoreCase("catalog")).thenReturn(Optional.of(site));
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1)).thenReturn(List.of(
                fieldWithDescription("pricing_inputPer1M", TurSEFieldType.DOUBLE,
                        "Input price per 1M tokens (USD)")));
        ArgumentCaptor<TurDslQueryRequest> reqCaptor = ArgumentCaptor.forClass(TurDslQueryRequest.class);
        when(dslSearchService.search(eq("catalog"), eq("en"), reqCaptor.capture()))
                .thenReturn(Optional.of(twoHitResponse()));

        service.answer("catalog", "en",
                List.of(new ChatTurn("user", "which model has the lowest price?")));

        // The LLM parser is never consulted (nothing to facet after stripping).
        verify(parser, never()).parse(any());
        TurDslQueryRequest executed = reqCaptor.getValue();
        assertThat(executed.sort()).isNotNull().hasSize(1);
        assertThat(executed.sort().getFirst()).isEqualTo(Map.of("pricing_inputPer1M", "asc"));
        assertThat(executed.size()).isNotNull();
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

    private static TurSNSiteFieldExt fieldWithDescription(String name, TurSEFieldType type,
            String description) {
        TurSNSiteFieldExt ext = field(name, type, true);
        ext.setDescription(description);
        return ext;
    }

    /** One hit whose math sub-score (93.4) dwarfs its overall index (23.8) — the T799 trap. */
    private static TurDslSearchResponse scoredResponse() {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("title", "GPT-OSS-120B");
        doc.put("url", "https://example.ai/gpt-oss-120b");
        doc.put("benchmarks_intelligenceIndex", "23.8");
        doc.put("benchmarks_scores_math_value", "93.4");
        TurDslSearchResponse.Hit h1 = new TurDslSearchResponse.Hit("m1", 1.0, doc, null);
        TurDslSearchResponse.Hits hits = new TurDslSearchResponse.Hits(
                new TurDslSearchResponse.Total(1, "eq"), 1.0, List.of(h1));
        return new TurDslSearchResponse(7L, false, hits, null);
    }

    /** A facet-only query (kind=chat, no sort) — the LLM's output after ranking is stripped. */
    private static TurDslQueryRequest kindChatQuery() {
        return MAPPER.readValue("""
                {"query":{"bool":{"filter":[{"term":{"kind":"chat"}}]}}}""",
                TurDslQueryRequest.class);
    }

    /** One hit with a title + a single numeric key field — for the T822 compact test. */
    private static TurDslSearchResponse pricedResponse() {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("title", "Cheap Model");
        doc.put("price", "10");
        TurDslSearchResponse.Hit h = new TurDslSearchResponse.Hit("m1", 1.0, doc, null);
        TurDslSearchResponse.Hits hits = new TurDslSearchResponse.Hits(
                new TurDslSearchResponse.Total(1, "eq"), 1.0, List.of(h));
        return new TurDslSearchResponse(7L, false, hits, null);
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

    /** A response holding {@code n} distinct hits — for the T798 grounding tests. */
    private static TurDslSearchResponse nHitResponse(int n) {
        List<TurDslSearchResponse.Hit> list = new java.util.ArrayList<>(n);
        for (int i = 1; i <= n; i++) {
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("title", "Model " + i);
            doc.put("url", "https://example.edu/model-" + i);
            doc.put("abstract", "Catalog entry number " + i + ".");
            list.add(new TurDslSearchResponse.Hit("d" + i, (double) (n - i + 1), doc, null));
        }
        TurDslSearchResponse.Hits hits = new TurDslSearchResponse.Hits(
                new TurDslSearchResponse.Total(n, "eq"), (double) n, list);
        return new TurDslSearchResponse(7L, false, hits, null);
    }
}
