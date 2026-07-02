/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.viglet.turing.genai.multimodal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import com.viglet.turing.genai.TurRagContextBuilder;
import com.viglet.turing.genai.TurRagContextBuilder.RagInfrastructure;
import com.viglet.turing.genai.provider.llm.TurMultimodalEmbeddingModel;
import com.viglet.turing.genai.provider.store.TurGenAiStoreProvider;
import com.viglet.turing.genai.provider.store.TurStoreImportResult;
import com.viglet.turing.persistence.model.store.TurStoreInstance;

@ExtendWith(MockitoExtension.class)
class TurMultimodalRetrievalServiceTest {

    @Mock
    private TurRagContextBuilder ragContextBuilder;

    private TurMultimodalRetrievalService service;

    @BeforeEach
    void setUp() {
        service = new TurMultimodalRetrievalService(ragContextBuilder);
    }

    @Test
    void isAvailable_falseWhenInfraMissing() {
        when(ragContextBuilder.build(anyString(), anyString(), any())).thenReturn(Optional.empty());
        assertFalse(service.isAvailable("emb", "store"));
    }

    @Test
    void isAvailable_falseForTextOnlyModel() {
        RagInfrastructure infra = infra(mock(EmbeddingModel.class), mock(VectorStore.class),
                mock(TurGenAiStoreProvider.class));
        when(ragContextBuilder.build(anyString(), anyString(), any())).thenReturn(Optional.of(infra));
        assertFalse(service.isAvailable("emb", "store"));
    }

    @Test
    void isAvailable_trueForMultimodalModel() {
        EmbeddingModel mm = multimodalModel();
        RagInfrastructure infra = infra(mm, mock(VectorStore.class), mock(TurGenAiStoreProvider.class));
        when(ragContextBuilder.build(anyString(), anyString(), any())).thenReturn(Optional.of(infra));
        assertTrue(service.isAvailable("emb", "store"));
    }

    @Test
    void indexImage_textOnlyModel_returnsFalse() {
        RagInfrastructure infra = infra(mock(EmbeddingModel.class), mock(VectorStore.class),
                mock(TurGenAiStoreProvider.class));
        when(ragContextBuilder.build(anyString(), anyString(), eq("col"))).thenReturn(Optional.of(infra));

        boolean ok = service.indexImage("emb", "store", "col", "img-1",
                new byte[] { 1, 2, 3 }, "image/png", Map.of("label", "Chart"));
        assertFalse(ok);
    }

    @Test
    void indexImage_multimodal_upsertsPrecomputedVector() {
        EmbeddingModel mm = multimodalModel();
        when(((TurMultimodalEmbeddingModel) mm).embedImage(any(), any()))
                .thenReturn(new float[] { 0.1f, 0.2f });
        TurGenAiStoreProvider storeProvider = mock(TurGenAiStoreProvider.class);
        RagInfrastructure infra = infra(mm, mock(VectorStore.class), storeProvider);
        when(ragContextBuilder.build(anyString(), anyString(), eq("col"))).thenReturn(Optional.of(infra));
        when(storeProvider.importChunks(any(), any(), any(), any(), any()))
                .thenReturn(new TurStoreImportResult(1, 1, 1, 0, 0));

        boolean ok = service.indexImage("emb", "store", "col", "img-1",
                new byte[] { 1, 2, 3 }, "image/png", Map.of("label", "Chart"));
        assertTrue(ok);
    }

    @Test
    void searchImagesByText_returnsOnlyImageModalityHits() {
        EmbeddingModel mm = multimodalModel();
        VectorStore vectorStore = mock(VectorStore.class);
        RagInfrastructure infra = infra(mm, vectorStore, mock(TurGenAiStoreProvider.class));
        when(ragContextBuilder.build(anyString(), anyString(), eq("col"))).thenReturn(Optional.of(infra));

        Document image = Document.builder().id("img-1").text("Chart")
                .metadata(Map.of(TurMultimodalRetrievalService.META_MODALITY,
                        TurMultimodalRetrievalService.MODALITY_IMAGE))
                .build();
        Document textChunk = Document.builder().id("txt-1").text("paragraph").metadata(Map.of()).build();
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(textChunk, image));

        List<Document> results = service.searchImagesByText("emb", "store", "col", "sales chart", 5);
        assertEquals(1, results.size());
        assertEquals("img-1", results.get(0).getId());
    }

    // --- helpers -----------------------------------------------------------

    private RagInfrastructure infra(EmbeddingModel embeddingModel, VectorStore vectorStore,
            TurGenAiStoreProvider storeProvider) {
        return new RagInfrastructure(vectorStore, embeddingModel, storeProvider,
                new TurStoreInstance(), "cred", "col");
    }

    private EmbeddingModel multimodalModel() {
        EmbeddingModel mm = mock(EmbeddingModel.class,
                withSettings().extraInterfaces(TurMultimodalEmbeddingModel.class));
        TurMultimodalEmbeddingModel asMm = (TurMultimodalEmbeddingModel) mm;
        when(asMm.supportsImageEmbedding()).thenReturn(true);
        return mm;
    }
}
