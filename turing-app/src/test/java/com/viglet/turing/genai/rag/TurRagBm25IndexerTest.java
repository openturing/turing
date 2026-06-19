/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;

import com.viglet.turing.persistence.model.rag.TurRagBm25Core;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.store.TurStoreInstance;
import com.viglet.turing.persistence.repository.rag.TurRagBm25CoreRepository;
import com.viglet.turing.plugins.se.TurSearchEnginePlugin;
import com.viglet.turing.plugins.se.TurSearchEnginePluginFactory;

/**
 * Unit tests for {@link TurRagBm25Indexer}. Pure-Mockito; no SE wire-up.
 * Pins the chunk-to-doc mapping contract + the docCount accounting +
 * the provisioned-state gate.
 */
@ExtendWith(MockitoExtension.class)
class TurRagBm25IndexerTest {

    @Mock
    private TurRagBm25CoreRepository coreRepository;
    @Mock
    private TurSearchEnginePluginFactory pluginFactory;
    @Mock
    private TurSearchEnginePlugin plugin;

    private TurRagBm25Indexer indexer;

    private TurStoreInstance store;
    private TurSEInstance seInstance;
    private TurRagBm25Core core;

    @BeforeEach
    void setUp() {
        indexer = new TurRagBm25Indexer(coreRepository, pluginFactory);
        store = new TurStoreInstance();
        store.setId("a3b2c1d4");
        seInstance = new TurSEInstance();
        seInstance.setId("se-1");
        core = new TurRagBm25Core();
        core.setId("core-1");
        core.setTurStoreInstance(store);
        core.setTurSEInstance(seInstance);
        core.setLocale(Locale.forLanguageTag("pt-BR"));
        core.setCoreName("rag_a3b2c1d4_pt-BR");
        core.setStatus(TurRagBm25Core.Status.PROVISIONED);
        core.setDocCount(0L);
    }

    // ── indexChunks ──

    @Test
    void indexChunks_emptyOrNullList_isNoOp() {
        assertThat(indexer.indexChunks(core, List.of())).isZero();
        assertThat(indexer.indexChunks(core, null)).isZero();
        verify(plugin, never()).indexStandaloneDocuments(any(), anyString(), anyList());
    }

    @Test
    void indexChunks_corePresent_pushesBatchAndCommitsAndBumpsDocCount() {
        when(pluginFactory.getPluginForInstance(seInstance)).thenReturn(plugin);
        when(plugin.indexStandaloneDocuments(eq(seInstance), eq("rag_a3b2c1d4_pt-BR"), anyList()))
                .thenReturn(3);

        List<Document> chunks = List.of(
                chunkWithMetadata("c1", "primeiro chunk em português",
                        Map.of("objectName", "doc-a.pdf", "chunkIndex", 0, "fileName", "doc-a.pdf")),
                chunkWithMetadata("c2", "segundo chunk com mais informação",
                        Map.of("objectName", "doc-a.pdf", "chunkIndex", 1, "fileName", "doc-a.pdf")),
                chunkWithMetadata("c3", "terceiro chunk diferente assunto",
                        Map.of("objectName", "doc-b.pdf", "chunkIndex", 0, "fileName", "doc-b.pdf")));

        int indexed = indexer.indexChunks(core, chunks);

        assertThat(indexed).isEqualTo(3);
        verify(plugin).commitStandalone(seInstance, "rag_a3b2c1d4_pt-BR");
        // docCount must reflect the SE's count (3 chunks pushed).
        ArgumentCaptor<TurRagBm25Core> saved = ArgumentCaptor.forClass(TurRagBm25Core.class);
        verify(coreRepository).save(saved.capture());
        assertThat(saved.getValue().getDocCount()).isEqualTo(3L);
    }

    @Test
    void indexChunks_mapsFieldsToFixedSchema() {
        when(pluginFactory.getPluginForInstance(seInstance)).thenReturn(plugin);
        when(plugin.indexStandaloneDocuments(any(), anyString(), anyList())).thenReturn(1);

        Document chunk = chunkWithMetadata("c1", "conteúdo",
                Map.of("objectName", "report.pdf", "chunkIndex", 5, "fileName", "report.pdf"));

        indexer.indexChunks(core, List.of(chunk));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.forClass(List.class);
        verify(plugin).indexStandaloneDocuments(eq(seInstance), eq("rag_a3b2c1d4_pt-BR"),
                captor.capture());
        List<Map<String, Object>> sent = captor.getValue();
        assertThat(sent).hasSize(1);
        Map<String, Object> doc = sent.get(0);
        assertThat(doc)
                .containsEntry(TurRagBm25Indexer.FIELD_ID, "c1")
                .containsEntry(TurRagBm25Indexer.FIELD_CONTENT, "conteúdo")
                .containsEntry(TurRagBm25Indexer.FIELD_ASSET_ID, "report.pdf")
                .containsEntry(TurRagBm25Indexer.FIELD_CHUNK_INDEX, 5)
                .containsEntry(TurRagBm25Indexer.FIELD_SOURCE_FILE, "report.pdf");
    }

    @Test
    void indexChunks_chunksWithoutIdOrText_areDropped() {
        when(pluginFactory.getPluginForInstance(seInstance)).thenReturn(plugin);
        when(plugin.indexStandaloneDocuments(any(), anyString(), anyList())).thenReturn(1);

        List<Document> chunks = List.of(
                chunkWithMetadata("ok", "valid text", Map.of("objectName", "f.pdf")),
                // empty text → dropped by toIndexableDoc
                chunkWithMetadata("blank", "", Map.of("objectName", "f.pdf")));

        indexer.indexChunks(core, chunks);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.forClass(List.class);
        verify(plugin).indexStandaloneDocuments(any(), anyString(), captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0)).containsEntry(TurRagBm25Indexer.FIELD_ID, "ok");
    }

    @Test
    void indexChunks_partialFailure_returnsActualIndexedCount() {
        when(pluginFactory.getPluginForInstance(seInstance)).thenReturn(plugin);
        // Plugin reports 2 out of 3 batched docs accepted by the SE.
        when(plugin.indexStandaloneDocuments(any(), anyString(), anyList())).thenReturn(2);

        List<Document> chunks = List.of(
                chunkWithMetadata("c1", "a", Map.of()),
                chunkWithMetadata("c2", "b", Map.of()),
                chunkWithMetadata("c3", "c", Map.of()));
        int indexed = indexer.indexChunks(core, chunks);

        assertThat(indexed).isEqualTo(2);
        // docCount must NOT include the failed doc.
        ArgumentCaptor<TurRagBm25Core> saved = ArgumentCaptor.forClass(TurRagBm25Core.class);
        verify(coreRepository).save(saved.capture());
        assertThat(saved.getValue().getDocCount()).isEqualTo(2L);
    }

    @Test
    void indexChunks_corePending_throws() {
        core.setStatus(TurRagBm25Core.Status.NOT_PROVISIONED);
        var chunks = List.of(chunkWithMetadata("c1", "x", Map.of()));
        assertThatThrownBy(() -> indexer.indexChunks(core, chunks))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NOT_PROVISIONED");
    }

    // ── deleteByAsset ──

    @Test
    void deleteByAsset_callsPluginDeleteByFieldAndCommits() {
        when(pluginFactory.getPluginForInstance(seInstance)).thenReturn(plugin);
        when(plugin.deIndexStandaloneByField(any(), anyString(), anyString(), anyString()))
                .thenReturn(true);

        boolean ok = indexer.deleteByAsset(core, "report.pdf");

        assertThat(ok).isTrue();
        verify(plugin).deIndexStandaloneByField(seInstance, "rag_a3b2c1d4_pt-BR",
                TurRagBm25Indexer.FIELD_ASSET_ID, "report.pdf");
        verify(plugin).commitStandalone(seInstance, "rag_a3b2c1d4_pt-BR");
    }

    @Test
    void deleteByAsset_blankAssetId_isNoOp() {
        assertThat(indexer.deleteByAsset(core, null)).isFalse();
        assertThat(indexer.deleteByAsset(core, "")).isFalse();
        assertThat(indexer.deleteByAsset(core, "   ")).isFalse();
        verify(plugin, never()).deIndexStandaloneByField(any(), anyString(), anyString(), anyString());
    }

    // ── helpers ──

    private static Document chunkWithMetadata(String id, String text, Map<String, Object> metadata) {
        return Document.builder()
                .id(id)
                .text(text)
                .metadata(metadata)
                .build();
    }
}
