/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.sn.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import com.viglet.turing.commons.sn.field.TurSNFieldName;
import com.viglet.turing.genai.TurRagContextBuilder;
import com.viglet.turing.genai.TurRagContextBuilder.RagInfrastructure;
import com.viglet.turing.genai.rag.TurRagReranker;
import com.viglet.turing.genai.rag.rerank.TurRagRerankStrategyType;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.genai.TurSNRankingMode;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import com.viglet.turing.se.result.TurSEResult;
import com.viglet.turing.system.TurDefaultChatModelResolver;
import com.viglet.turing.system.TurGlobalSettingsService;

/**
 * T383 / §XX.3 — unit coverage for the public-SN hybrid ranking service:
 * the opt-in gating, RRF reordering of a result page, and the fail-open
 * degradation back to lexical order.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@ExtendWith(MockitoExtension.class)
class TurSNHybridRankingServiceTest {

    @Mock
    private TurRagContextBuilder ragContextBuilder;
    @Mock
    private TurGlobalSettingsService globalSettingsService;
    @Mock
    private VectorStore vectorStore;
    @Mock
    private TurRagReranker reranker;
    @Mock
    private TurDefaultChatModelResolver defaultChatModelResolver;

    private TurSNHybridRankingService service;

    @BeforeEach
    void setUp() {
        service = new TurSNHybridRankingService(ragContextBuilder, globalSettingsService,
                reranker, defaultChatModelResolver);
        lenient().when(globalSettingsService.getDefaultEmbeddingModelId()).thenReturn("emb-1");
        lenient().when(globalSettingsService.getDefaultEmbeddingStoreId()).thenReturn("store-1");
    }

    private TurSNSite siteWithMode(TurSNRankingMode mode) {
        TurSNSite site = new TurSNSite();
        site.setId("site-1");
        site.setName("catalog");
        if (mode != null) {
            TurSNSiteGenAi genAi = new TurSNSiteGenAi();
            genAi.setSnRankingMode(mode);
            site.setTurSNSiteGenAi(genAi);
        }
        return site;
    }

    private RagInfrastructure infra() {
        return new RagInfrastructure(vectorStore, null, null, null, null, "sn_site-1");
    }

    private TurSEResult result(String id) {
        return TurSEResult.builder().fields(Map.of(TurSNFieldName.ID, id)).build();
    }

    // ---- isEnabled gating -------------------------------------------------

    @Test
    void legacyModeIsNotEnabled() {
        assertThat(service.isEnabled(siteWithMode(TurSNRankingMode.LEGACY))).isFalse();
    }

    @Test
    void noGenAiBindingIsNotEnabled() {
        assertThat(service.isEnabled(siteWithMode(null))).isFalse();
    }

    @Test
    void hybridModeWithDefaultsConfiguredIsEnabled() {
        assertThat(service.isEnabled(siteWithMode(TurSNRankingMode.HYBRID_RRF))).isTrue();
    }

    @Test
    void vectorlessStructuredSiteIsNeverEnabledEvenWithHybridMode() {
        // T790 — even with HYBRID_RRF ranking and the platform defaults present, a
        // VECTORLESS_STRUCTURED site has no vectors: hybrid ranking must stay off so
        // the catalog runs the pure lexical path (and never touches the embedder).
        TurSNSite site = siteWithMode(TurSNRankingMode.HYBRID_RRF);
        site.getTurSNSiteGenAi().setKnowledgeBaseMode(
                com.viglet.turing.persistence.model.sn.genai.TurSNKnowledgeBaseMode.VECTORLESS_STRUCTURED);

        assertThat(service.isEnabled(site)).isFalse();
    }

    @Test
    void fuseOnVectorlessStructuredSiteReturnsPageUntouchedAndNeverBuildsInfra() {
        TurSNSite site = siteWithMode(TurSNRankingMode.HYBRID_RRF);
        site.getTurSNSiteGenAi().setKnowledgeBaseMode(
                com.viglet.turing.persistence.model.sn.genai.TurSNKnowledgeBaseMode.VECTORLESS_STRUCTURED);

        List<TurSEResult> page = List.of(result("A"), result("B"));
        List<TurSEResult> fused = service.fuse(site, Locale.ENGLISH, "query", page);

        assertThat(fused).isSameAs(page);
        verify(ragContextBuilder, never()).build(anyString(), anyString(), anyString());
    }

    @Test
    void hybridModeWithoutDefaultEmbeddingModelIsNotEnabled() {
        when(globalSettingsService.getDefaultEmbeddingModelId()).thenReturn("");
        assertThat(service.isEnabled(siteWithMode(TurSNRankingMode.HYBRID_RRF))).isFalse();
    }

    @Test
    void rerankModeWithDefaultsConfiguredIsEnabled() {
        assertThat(service.isEnabled(siteWithMode(TurSNRankingMode.HYBRID_RRF_RERANK))).isTrue();
    }

    // ---- T513 per-site domain embedding model override --------------------

    @Test
    void siteEmbeddingOverrideEnablesEvenWithoutGlobalDefault() {
        // No platform default embedding model, but the site picks its own — the
        // override short-circuits the global lookup, so isEnabled is still true.
        lenient().when(globalSettingsService.getDefaultEmbeddingModelId()).thenReturn("");
        TurSNSite site = siteWithMode(TurSNRankingMode.HYBRID_RRF);
        site.getTurSNSiteGenAi().setEmbeddingModelId("voyage-law");

        assertThat(service.isEnabled(site)).isTrue();
    }

    @Test
    void siteEmbeddingOverrideRoutesInfraThroughChosenModel() {
        when(ragContextBuilder.build(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(infra()));
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(Document.builder().id("B").text("").build()));

        TurSNSite site = siteWithMode(TurSNRankingMode.HYBRID_RRF);
        site.getTurSNSiteGenAi().setEmbeddingModelId("voyage-law");

        service.fuse(site, Locale.ENGLISH, "query", List.of(result("A"), result("B")));

        // The per-site override id (not the global "emb-1") is the one used to build infra.
        verify(ragContextBuilder).build(eq("voyage-law"), anyString(), anyString());
    }

    // ---- fuse reordering --------------------------------------------------

    @Test
    void fuseMovesVectorWinnerToTheFront() {
        when(ragContextBuilder.build(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(infra()));
        // BM25 order: A, B. Vector pass returns only B → RRF lifts B above A.
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(Document.builder().id("B").text("").build()));

        List<TurSEResult> page = List.of(result("A"), result("B"));
        List<TurSEResult> fused = service.fuse(siteWithMode(TurSNRankingMode.HYBRID_RRF),
                Locale.ENGLISH, "query", page);

        assertThat(fused).hasSize(2);
        assertThat(fused.get(0).getFields()).containsEntry(TurSNFieldName.ID, "B");
        assertThat(fused.get(1).getFields()).containsEntry(TurSNFieldName.ID, "A");
    }

    @Test
    void fusePreservesTheFullResultSet() {
        when(ragContextBuilder.build(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(infra()));
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(Document.builder().id("C").text("").build(),
                        Document.builder().id("A").text("").build()));

        List<TurSEResult> page = List.of(result("A"), result("B"), result("C"));
        List<TurSEResult> fused = service.fuse(siteWithMode(TurSNRankingMode.HYBRID_RRF),
                Locale.ENGLISH, "query", page);

        assertThat(fused).extracting(r -> r.getFields().get(TurSNFieldName.ID))
                .containsExactlyInAnyOrder("A", "B", "C");
    }

    @Test
    void fuseIsNoOpWhenVectorPassReturnsNothingOnThePage() {
        when(ragContextBuilder.build(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(infra()));
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(Document.builder().id("Z").text("").build()));

        List<TurSEResult> page = List.of(result("A"), result("B"));
        List<TurSEResult> fused = service.fuse(siteWithMode(TurSNRankingMode.HYBRID_RRF),
                Locale.ENGLISH, "query", page);

        assertThat(fused).isSameAs(page);
    }

    // ---- ranking explanation (T389) --------------------------------------

    @Test
    void fuseAttachesObjectiveRankingExplanation() {
        when(ragContextBuilder.build(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(infra()));
        // BM25 order: A, B. Vector pass returns only B → RRF lifts B above A.
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(Document.builder().id("B").text("").build()));

        List<TurSEResult> page = List.of(result("A"), result("B"));
        List<TurSEResult> fused = service.fuse(siteWithMode(TurSNRankingMode.HYBRID_RRF),
                Locale.ENGLISH, "query", page);

        // B is the vector winner → fusedRank/finalRank 1, lexicalRank 2, semanticRank 1.
        Map<String, Object> bExplain = fused.get(0).getRankingExplanation();
        assertThat(bExplain).containsEntry("pipeline", "HYBRID_RRF")
                .containsEntry("lexicalRank", 2)
                .containsEntry("semanticRank", 1)
                .containsEntry("fusedRank", 1)
                .containsEntry("finalRank", 1)
                .containsKey("rrfScore")
                // No rerank stage on HYBRID_RRF → no rerankerDelta, reranker untouched.
                .doesNotContainKey("rerankerDelta");
        verify(reranker, never()).reorder(any(), anyString(), anyList());
    }

    // ---- rerank stage (T389) ---------------------------------------------

    @Test
    void rerankStageReordersFusedPageAndRecordsDelta() {
        when(ragContextBuilder.build(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(infra()));
        // BM25: A, B. Vector returns only B → fused order [B, A].
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(Document.builder().id("B").text("").build()));
        // HTTP strategy → no chat model needed; reranker reverses the fused order → [A, B].
        when(globalSettingsService.getRagSnRerankStrategy())
                .thenReturn(TurRagRerankStrategyType.CROSS_ENCODER);
        when(reranker.reorder(any(), anyString(), anyList())).thenAnswer(inv -> {
            List<Document> candidates = inv.getArgument(2);
            List<Document> reversed = new ArrayList<>(candidates);
            Collections.reverse(reversed);
            return reversed;
        });

        List<TurSEResult> page = List.of(result("A"), result("B"));
        List<TurSEResult> ranked = service.fuse(siteWithMode(TurSNRankingMode.HYBRID_RRF_RERANK),
                Locale.ENGLISH, "query", page);

        assertThat(ranked).extracting(r -> r.getFields().get(TurSNFieldName.ID))
                .containsExactly("A", "B");
        // A was fusedRank 2, reranked to finalRank 1 → delta +1; strategy recorded.
        Map<String, Object> aExplain = ranked.get(0).getRankingExplanation();
        assertThat(aExplain).containsEntry("pipeline", "HYBRID_RRF_RERANK")
                .containsEntry("fusedRank", 2)
                .containsEntry("finalRank", 1)
                .containsEntry("rerankerDelta", 1)
                .containsEntry("rerankStrategy", "CROSS_ENCODER");
        // HTTP strategy must not resolve a chat model.
        verify(defaultChatModelResolver, never()).resolve();
    }

    @Test
    void rerankStageKeepsFusedOrderWhenRerankerNoOps() {
        when(ragContextBuilder.build(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(infra()));
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(Document.builder().id("B").text("").build()));
        when(globalSettingsService.getRagSnRerankStrategy())
                .thenReturn(TurRagRerankStrategyType.CROSS_ENCODER);
        // Reranker fails open: returns the candidates unchanged (fused order [B, A]).
        when(reranker.reorder(any(), anyString(), anyList())).thenAnswer(inv -> inv.getArgument(2));

        List<TurSEResult> page = List.of(result("A"), result("B"));
        List<TurSEResult> ranked = service.fuse(siteWithMode(TurSNRankingMode.HYBRID_RRF_RERANK),
                Locale.ENGLISH, "query", page);

        assertThat(ranked).extracting(r -> r.getFields().get(TurSNFieldName.ID))
                .containsExactly("B", "A");
        // Rerank ran but changed nothing → delta 0 for the top result.
        assertThat(ranked.get(0).getRankingExplanation()).containsEntry("rerankerDelta", 0);
    }

    // ---- fail-open --------------------------------------------------------

    @Test
    void fuseOnLegacySiteReturnsPageUntouchedAndNeverBuildsInfra() {
        List<TurSEResult> page = List.of(result("A"), result("B"));
        List<TurSEResult> fused = service.fuse(siteWithMode(TurSNRankingMode.LEGACY),
                Locale.ENGLISH, "query", page);

        assertThat(fused).isSameAs(page);
        verify(ragContextBuilder, never()).build(anyString(), anyString(), anyString());
    }

    @Test
    void fuseDegradesToLexicalWhenInfraCannotBeBuilt() {
        when(ragContextBuilder.build(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());

        List<TurSEResult> page = List.of(result("A"), result("B"));
        List<TurSEResult> fused = service.fuse(siteWithMode(TurSNRankingMode.HYBRID_RRF),
                Locale.ENGLISH, "query", page);

        assertThat(fused).isSameAs(page);
    }

    @Test
    void fuseDegradesToLexicalWhenVectorSearchThrows() {
        when(ragContextBuilder.build(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(infra()));
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenThrow(new RuntimeException("store down"));

        List<TurSEResult> page = List.of(result("A"), result("B"));
        List<TurSEResult> fused = service.fuse(siteWithMode(TurSNRankingMode.HYBRID_RRF),
                Locale.ENGLISH, "query", page);

        assertThat(fused).isSameAs(page);
    }

    @Test
    void fuseSkipsSingleResultPage() {
        List<TurSEResult> page = List.of(result("A"));
        List<TurSEResult> fused = service.fuse(siteWithMode(TurSNRankingMode.HYBRID_RRF),
                Locale.ENGLISH, "query", page);

        assertThat(fused).isSameAs(page);
        verify(ragContextBuilder, never()).build(anyString(), anyString(), anyString());
    }

    // ---- indexing side-write ---------------------------------------------

    @Test
    void indexDocumentUpsertsVectorForHybridSite() {
        when(ragContextBuilder.build(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(infra()));

        service.indexDocument(siteWithMode(TurSNRankingMode.HYBRID_RRF), Locale.ENGLISH,
                Map.of(TurSNFieldName.ID, "doc-1", TurSNFieldName.TITLE, "Hello",
                        TurSNFieldName.TEXT, "World"));

        verify(vectorStore).delete(List.of("doc-1"));
        verify(vectorStore).add(any());
    }

    @Test
    void indexDocumentIsNoOpForLegacySite() {
        service.indexDocument(siteWithMode(TurSNRankingMode.LEGACY), Locale.ENGLISH,
                Map.of(TurSNFieldName.ID, "doc-1", TurSNFieldName.TEXT, "World"));

        verify(ragContextBuilder, never()).build(anyString(), anyString(), anyString());
        verify(vectorStore, never()).add(any());
    }

    // ---- vector-neighbor retrieval (T384) --------------------------------

    @Test
    void findNeighborsReturnsSimilarIdsExcludingSeed() {
        when(ragContextBuilder.build(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(infra()));
        // The seed (doc-1) is echoed back by the store and must be dropped.
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(
                        Document.builder().id("doc-1").text("").build(),
                        Document.builder().id("doc-2").text("").build(),
                        Document.builder().id("doc-3").text("").build()));

        List<String> neighbors = service.findNeighbors(siteWithMode(TurSNRankingMode.HYBRID_RRF),
                Map.of(TurSNFieldName.ID, "doc-1", TurSNFieldName.TITLE, "Data Science"), 5);

        assertThat(neighbors).containsExactly("doc-2", "doc-3");
    }

    @Test
    void findNeighborsHonoursTopKLimit() {
        when(ragContextBuilder.build(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(infra()));
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(
                        Document.builder().id("a").text("").build(),
                        Document.builder().id("b").text("").build(),
                        Document.builder().id("c").text("").build()));

        List<String> neighbors = service.findNeighbors(siteWithMode(TurSNRankingMode.HYBRID_RRF),
                Map.of(TurSNFieldName.ID, "seed", TurSNFieldName.TITLE, "X"), 2);

        assertThat(neighbors).containsExactly("a", "b");
    }

    @Test
    void findNeighborsIsEmptyOnLegacySite() {
        List<String> neighbors = service.findNeighbors(siteWithMode(TurSNRankingMode.LEGACY),
                Map.of(TurSNFieldName.ID, "seed", TurSNFieldName.TITLE, "X"), 5);

        assertThat(neighbors).isEmpty();
        verify(ragContextBuilder, never()).build(anyString(), anyString(), anyString());
    }

    @Test
    void findNeighborsDegradesToEmptyWhenVectorSearchThrows() {
        when(ragContextBuilder.build(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(infra()));
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenThrow(new RuntimeException("store down"));

        List<String> neighbors = service.findNeighbors(siteWithMode(TurSNRankingMode.HYBRID_RRF),
                Map.of(TurSNFieldName.ID, "seed", TurSNFieldName.TITLE, "X"), 5);

        assertThat(neighbors).isEmpty();
    }

    @Test
    void findNeighborsIsEmptyWhenSeedHasNoContent() {
        List<String> neighbors = service.findNeighbors(siteWithMode(TurSNRankingMode.HYBRID_RRF),
                Map.of(TurSNFieldName.ID, "seed", TurSNFieldName.URL, "http://ignored"), 5);

        assertThat(neighbors).isEmpty();
        verify(ragContextBuilder, never()).build(anyString(), anyString(), anyString());
    }

    // ---- scored vector-neighbor retrieval (T390) -------------------------

    @Test
    void findScoredNeighborsKeepsScoresAndExcludesSeed() {
        when(ragContextBuilder.build(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(infra()));
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(
                        Document.builder().id("seed").text("").score(1.0).build(),
                        Document.builder().id("dup-1").text("").score(0.97).build(),
                        Document.builder().id("dup-2").text("").score(0.94).build()));

        List<TurSNScoredNeighbor> neighbors = service.findScoredNeighbors(
                siteWithMode(TurSNRankingMode.HYBRID_RRF),
                Map.of(TurSNFieldName.ID, "seed", TurSNFieldName.TITLE, "Course"), 5, 0.9);

        assertThat(neighbors).extracting(TurSNScoredNeighbor::id).containsExactly("dup-1", "dup-2");
        assertThat(neighbors).extracting(TurSNScoredNeighbor::score).containsExactly(0.97, 0.94);
    }

    @Test
    void findScoredNeighborsDropsHitsBelowThreshold() {
        when(ragContextBuilder.build(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(infra()));
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(
                        Document.builder().id("dup-1").text("").score(0.95).build(),
                        Document.builder().id("related").text("").score(0.70).build()));

        List<TurSNScoredNeighbor> neighbors = service.findScoredNeighbors(
                siteWithMode(TurSNRankingMode.HYBRID_RRF),
                Map.of(TurSNFieldName.ID, "seed", TurSNFieldName.TITLE, "Course"), 5, 0.9);

        assertThat(neighbors).extracting(TurSNScoredNeighbor::id).containsExactly("dup-1");
    }

    @Test
    void findScoredNeighborsExcludesScorelessHitsUnderPositiveThreshold() {
        when(ragContextBuilder.build(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(infra()));
        // No score from the store → conservatively below any positive threshold.
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(Document.builder().id("dup-1").text("").build()));

        List<TurSNScoredNeighbor> neighbors = service.findScoredNeighbors(
                siteWithMode(TurSNRankingMode.HYBRID_RRF),
                Map.of(TurSNFieldName.ID, "seed", TurSNFieldName.TITLE, "Course"), 5, 0.9);

        assertThat(neighbors).isEmpty();
    }

    // ---- content extraction ----------------------------------------------

    @Test
    void buildContentPrefersTitleAbstractText() {
        String content = TurSNHybridRankingService.buildContent(Map.of(
                TurSNFieldName.ID, "x",
                TurSNFieldName.TITLE, "Course",
                TurSNFieldName.ABSTRACT, "Short",
                TurSNFieldName.TEXT, "Body"));
        assertThat(content).isEqualTo("Course\nShort\nBody");
    }

    @Test
    void buildContentFallsBackToOtherFieldsWhenNoMainText() {
        String content = TurSNHybridRankingService.buildContent(Map.of(
                TurSNFieldName.ID, "x",
                TurSNFieldName.URL, "http://ignored",
                TurSNFieldName.AUTHOR, "Ada"));
        assertThat(content).isEqualTo("Ada");
    }

    @Test
    void buildContentJoinsListValues() {
        String content = TurSNHybridRankingService.buildContent(Map.of(
                TurSNFieldName.TITLE, List.of("A", "B")));
        assertThat(content).isEqualTo("A B");
    }
}
