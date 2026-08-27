/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.exchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.viglet.turing.exchange.sn.TurSNSiteContentExchangeService;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.plugins.se.TurSearchEnginePlugin;
import com.viglet.turing.plugins.se.TurSearchEnginePluginFactory;

/**
 * T1036 / §CVI.1 — the import streams the content file instead of deserialising
 * it whole. Driven through the real {@code TurSNSiteContentExchangeService} sink
 * rather than a mock, because the behaviour worth proving spans both: documents
 * reach the engine in {@code IMPORT_CHUNK_SIZE} chunks, in order, under the right
 * locale, and the tail is flushed.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TurImportExchangeContentStreamTest {

    private static final int CHUNK = TurSNSiteContentExchangeService.IMPORT_CHUNK_SIZE;

    @Mock
    private TurSNSiteRepository turSNSiteRepository;
    @Mock
    private TurSearchEnginePluginFactory pluginFactory;
    @Mock
    private TurSearchEnginePlugin plugin;

    private TurSNSiteContentExchangeService contentExchangeService;
    private TurSNSite site;

    /** Every chunk the engine was handed, as (locale, ids). */
    private final List<Map.Entry<Locale, List<String>>> indexedChunks = new ArrayList<>();

    @BeforeEach
    void setUp() {
        contentExchangeService = new TurSNSiteContentExchangeService(turSNSiteRepository,
                org.mockito.Mockito.mock(
                        com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository.class),
                pluginFactory,
                org.mockito.Mockito.mock(com.viglet.turing.sn.field.TurSNSiteFieldService.class),
                org.mockito.Mockito.mock(com.viglet.turing.genai.TurSNGenAi.class),
                org.mockito.Mockito.mock(com.viglet.turing.genai.TurGenAiContextFactory.class),
                org.mockito.Mockito.mock(com.viglet.turing.genai.TurDefaultAgentResolver.class),
                new com.viglet.turing.properties.TurConfigProperties());

        site = new TurSNSite();
        site.setId("site-1");
        site.setName("Catalog");
        when(turSNSiteRepository.findByNameIgnoreCase("Catalog")).thenReturn(Optional.of(site));
        when(pluginFactory.getPluginForSite(site)).thenReturn(plugin);
        when(plugin.indexDocuments(eq(site), any(), any())).thenAnswer(call -> {
            Locale locale = call.getArgument(1);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> chunk = call.getArgument(2);
            indexedChunks.add(Map.entry(locale,
                    chunk.stream().map(d -> d.get("id").toString()).toList()));
            return chunk.size();
        });
    }

    private static Map<String, Object> doc(String id) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", id);
        document.put("title", "Title " + id);
        return document;
    }

    /** Feeds the sink the way the streaming parser does: (locale, doc) in file order. */
    private int feed(List<Map.Entry<String, Map<String, Object>>> documents, int expectedTotal) {
        var sink = contentExchangeService.openContentImport(site, "task-1", expectedTotal);
        documents.forEach(e -> sink.document(e.getKey(), e.getValue()));
        return sink.finish();
    }

    private static List<Map.Entry<String, Map<String, Object>>> localeRun(String locale, int count,
            int firstIndex) {
        List<Map.Entry<String, Map<String, Object>>> documents = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            documents.add(Map.entry(locale, doc("doc-%05d".formatted(firstIndex + i))));
        }
        return documents;
    }

    @Test
    @DisplayName("Documents are indexed in chunks, in order, and the tail is flushed")
    void chunksAreFlushedIncludingTheTail() {
        int indexed = feed(localeRun("en_US", CHUNK + 7, 0), CHUNK + 7);

        assertThat(indexed).isEqualTo(CHUNK + 7);
        assertThat(indexedChunks).hasSize(2);
        assertThat(indexedChunks.get(0).getValue()).hasSize(CHUNK);
        assertThat(indexedChunks.get(0).getValue().get(0)).isEqualTo("doc-00000");
        assertThat(indexedChunks.get(1).getValue()).hasSize(7);
        assertThat(indexedChunks.get(1).getValue().get(6))
                .isEqualTo("doc-%05d".formatted(CHUNK + 6));
    }

    @Test
    @DisplayName("A locale change flushes first, so a chunk is never indexed under the wrong locale")
    void localeChangeFlushesTheTail() {
        List<Map.Entry<String, Map<String, Object>>> documents = new ArrayList<>();
        documents.addAll(localeRun("en_US", 3, 0));
        documents.addAll(localeRun("pt_BR", 2, 100));

        int indexed = feed(documents, 5);

        assertThat(indexed).isEqualTo(5);
        assertThat(indexedChunks).hasSize(2);
        assertThat(indexedChunks.get(0).getKey()).isEqualTo(Locale.forLanguageTag("en-US"));
        assertThat(indexedChunks.get(0).getValue()).hasSize(3);
        assertThat(indexedChunks.get(1).getKey()).isEqualTo(Locale.forLanguageTag("pt-BR"));
        assertThat(indexedChunks.get(1).getValue()).hasSize(2);
    }

    @Test
    @DisplayName("The file's en_US spelling becomes the en-US locale the engine expects")
    void localeKeyIsNormalised() {
        feed(localeRun("pt_BR", 1, 0), 1);

        assertThat(indexedChunks.get(0).getKey()).isEqualTo(Locale.forLanguageTag("pt-BR"));
    }

    @Test
    @DisplayName("No documents means nothing is indexed and no empty chunk is sent")
    void emptyContentIndexesNothing() {
        int indexed = feed(List.of(), 0);

        assertThat(indexed).isZero();
        assertThat(indexedChunks).isEmpty();
        verify(plugin, never()).indexDocuments(any(), any(), any());
    }

    @Test
    @DisplayName("An empty document is skipped rather than indexed as a blank row")
    void emptyDocumentsAreSkipped() {
        var sink = contentExchangeService.openContentImport(site, "task-1", 1);
        sink.document("en_US", Map.of());
        sink.document("en_US", null);

        assertThat(sink.finish()).isZero();
        assertThat(indexedChunks).isEmpty();
    }

    @Test
    @DisplayName("finish() is idempotent — calling it twice does not re-index the tail")
    void finishIsIdempotent() {
        var sink = contentExchangeService.openContentImport(site, "task-1", 2);
        sink.document("en_US", doc("a"));
        sink.document("en_US", doc("b"));

        assertThat(sink.finish()).isEqualTo(2);
        assertThat(sink.finish()).isEqualTo(2);
        assertThat(indexedChunks).hasSize(1);
    }

    @Test
    @DisplayName("Progress advances per chunk and completes at the end")
    void progressAdvancesPerChunk() {
        var sink = contentExchangeService.openContentImport(site, "task-1", CHUNK + 1);
        assertThat(contentExchangeService.getProgress("task-1").orElseThrow()
                .processedDocuments()).isZero();

        for (int i = 0; i <= CHUNK; i++) {
            sink.document("en_US", doc("doc-%05d".formatted(i)));
        }
        // The full chunk flushed; the tail has not.
        assertThat(contentExchangeService.getProgress("task-1").orElseThrow()
                .processedDocuments()).isEqualTo(CHUNK);

        sink.finish();
        var done = contentExchangeService.getProgress("task-1").orElseThrow();
        assertThat(done.processedDocuments()).isEqualTo(CHUNK + 1);
        assertThat(done.phase()).isEqualTo("completed");
        assertThat(done.percentage()).isEqualTo(100);
    }

    @Test
    @DisplayName("An unknown expected total leaves the percentage at 0 rather than misreporting")
    void unknownTotalReportsNoPercentage() {
        var sink = contentExchangeService.openContentImport(site, "task-1", -1);
        sink.document("en_US", doc("a"));
        sink.finish();

        var progress = contentExchangeService.getProgress("task-1").orElseThrow();
        assertThat(progress.processedDocuments()).isEqualTo(1);
        assertThat(progress.totalDocuments()).isZero();
        assertThat(progress.percentage()).isZero();
    }

    @Test
    @DisplayName("Each flushed chunk is committed, so a long import is durable as it goes")
    void everyChunkIsCommitted() {
        feed(localeRun("en_US", CHUNK + 1, 0), CHUNK + 1);

        verify(plugin, org.mockito.Mockito.times(2)).commit(eq(site), any());
    }

    @Test
    @DisplayName("A batch failure falls back to per-document indexing, as the map path did")
    void batchFailureFallsBackPerDocument() {
        when(plugin.indexDocuments(eq(site), any(), any()))
                .thenThrow(new IllegalStateException("engine refused the batch"));
        when(plugin.indexDocument(eq(site), any(), any())).thenReturn(true);

        int indexed = feed(localeRun("en_US", 3, 0), 3);

        assertThat(indexed).isEqualTo(3);
        verify(plugin, org.mockito.Mockito.times(3)).indexDocument(eq(site), any(), any());
    }

    @Test
    @DisplayName("A site with no chunk still reports completed, so a caller is never left waiting")
    void completionIsAlwaysPublished() {
        contentExchangeService.openContentImport(site, "task-1", 0).finish();

        assertThat(contentExchangeService.getProgress("task-1").orElseThrow().phase())
                .isEqualTo("completed");
    }

    @Test
    @DisplayName("Nothing is indexed before a chunk fills — the sink holds one chunk at most")
    void nothingIsIndexedBeforeAChunkFills() {
        var sink = contentExchangeService.openContentImport(site, "task-1", CHUNK);
        for (int i = 0; i < CHUNK - 1; i++) {
            sink.document("en_US", doc("doc-%05d".formatted(i)));
        }

        verify(plugin, never()).indexDocuments(any(), any(), any());
        assertThat(sink.finish()).isEqualTo(CHUNK - 1);
    }

    @Test
    @DisplayName("The chunk handed to the engine is a copy, so the sink's buffer cannot mutate it")
    void chunksAreCopied() {
        List<List<Map<String, Object>>> captured = new ArrayList<>();
        when(plugin.indexDocuments(eq(site), any(), any())).thenAnswer(call -> {
            List<Map<String, Object>> chunk = call.getArgument(2);
            captured.add(chunk);
            return chunk.size();
        });

        feed(localeRun("en_US", CHUNK + 2, 0), CHUNK + 2);

        assertThat(captured).hasSize(2);
        assertThat(captured.get(0)).hasSize(CHUNK);
        assertThat(captured.get(1)).hasSize(2);
    }

    // ---- the streaming read itself -----------------------------------------

    private TurImportExchange importExchange() {
        return new TurImportExchange(
                org.mockito.Mockito.mock(com.viglet.turing.exchange.sn.TurSNSiteImport.class),
                contentExchangeService, turSNSiteRepository,
                org.mockito.Mockito.mock(com.viglet.turing.service.storage.TurStorageService.class),
                org.mockito.Mockito.mock(
                        com.viglet.turing.exchange.agent.TurAIAgentImportService.class));
    }

    private static java.io.File contentFile(String json) throws Exception {
        java.io.File file = java.io.File.createTempFile("Catalog_content", ".json");
        file.deleteOnExit();
        java.nio.file.Files.writeString(file.toPath(), json);
        return file;
    }

    @Test
    @DisplayName("The file's nested shape is walked without deserialising it whole")
    void streamsTheNestedShape() throws Exception {
        var file = contentFile("""
                {"Catalog":{"en_US":[{"id":"a","title":"A"},{"id":"b","title":"B"}],\
                "pt_BR":[{"id":"c","title":"C"}]}}""");

        int indexed = importExchange().streamContentFile(
                tools.jackson.databind.json.JsonMapper.builder().build(), file, "task-1");

        assertThat(indexed).isEqualTo(3);
        assertThat(indexedChunks).hasSize(2);
        assertThat(indexedChunks.get(0).getValue()).containsExactly("a", "b");
        assertThat(indexedChunks.get(1).getValue()).containsExactly("c");
    }

    @Test
    @DisplayName("The counting pass gives progress a real total")
    void countingPassFeedsTheProgressTotal() throws Exception {
        var file = contentFile("""
                {"Catalog":{"en_US":[{"id":"a"},{"id":"b"},{"id":"c"}]}}""");

        importExchange().streamContentFile(
                tools.jackson.databind.json.JsonMapper.builder().build(), file, "task-1");

        var progress = contentExchangeService.getProgress("task-1").orElseThrow();
        assertThat(progress.totalDocuments()).isEqualTo(3);
        assertThat(progress.processedDocuments()).isEqualTo(3);
        assertThat(progress.percentage()).isEqualTo(100);
    }

    @Test
    @DisplayName("A site the database does not have is skipped, and the next one still imports")
    void unknownSiteIsSkipped() throws Exception {
        var file = contentFile("""
                {"Ghost":{"en_US":[{"id":"x"},{"id":"y"}]},\
                "Catalog":{"en_US":[{"id":"a"}]}}""");
        when(turSNSiteRepository.findByNameIgnoreCase("Ghost")).thenReturn(Optional.empty());

        int indexed = importExchange().streamContentFile(
                tools.jackson.databind.json.JsonMapper.builder().build(), file, "task-1");

        assertThat(indexed).isEqualTo(1);
        assertThat(indexedChunks).hasSize(1);
        assertThat(indexedChunks.get(0).getValue()).containsExactly("a");
    }

    @Test
    @DisplayName("A locale whose value is not an array is skipped rather than crashing the import")
    void nonArrayLocaleIsSkipped() throws Exception {
        var file = contentFile("""
                {"Catalog":{"broken":{"not":"an array"},"en_US":[{"id":"a"}]}}""");

        int indexed = importExchange().streamContentFile(
                tools.jackson.databind.json.JsonMapper.builder().build(), file, "task-1");

        assertThat(indexed).isEqualTo(1);
    }

    @Test
    @DisplayName("A file that is not the expected shape fails loudly instead of importing nothing")
    void malformedFileIsReported() throws Exception {
        var file = contentFile("[{\"id\":\"a\"}]");
        var exchange = importExchange();
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> exchange.streamContentFile(mapper, file, "task-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Malformed content");
    }

    @Test
    @DisplayName("An empty content object imports nothing without complaint")
    void emptyFileImportsNothing() throws Exception {
        var file = contentFile("{}");

        assertThat(importExchange().streamContentFile(
                tools.jackson.databind.json.JsonMapper.builder().build(), file, "task-1")).isZero();
    }

    @Test
    @DisplayName("A document that spans more than the chunk boundary keeps every field")
    void documentFieldsSurviveTheStream() throws Exception {
        List<Map<String, Object>> captured = new ArrayList<>();
        when(plugin.indexDocuments(eq(site), any(), any())).thenAnswer(call -> {
            List<Map<String, Object>> chunk = call.getArgument(2);
            captured.addAll(chunk);
            return chunk.size();
        });
        var file = contentFile("""
                {"Catalog":{"en_US":[{"id":"a","title":"A","tags":["x","y"],"score":7,\
                "nested":{"k":"v"}}]}}""");

        importExchange().streamContentFile(
                tools.jackson.databind.json.JsonMapper.builder().build(), file, "task-1");

        assertThat(captured).hasSize(1);
        assertThat(captured.get(0)).containsEntry("id", "a").containsEntry("title", "A")
                .containsEntry("tags", List.of("x", "y")).containsEntry("score", 7)
                .containsEntry("nested", Map.of("k", "v"));
    }

    @Test
    @DisplayName("IMPORT_CHUNK_SIZE is the batch size, unchanged by the streaming read")
    void chunkSizeIsUnchanged() {
        assertThat(TurSNSiteContentExchangeService.IMPORT_CHUNK_SIZE).isEqualTo(100);
    }

    @Test
    @DisplayName("What the streaming export writes, the streaming import reads back")
    void roundTripsThroughTheRealExportWriter() throws Exception {
        // The claim T1036 makes: both halves now stream, so the file one produces
        // is the file the other consumes. Written by the real export writer
        // (T1033), read by the real import reader.
        var exportService = new com.viglet.turing.exchange.sn.TurSNSiteExportFileService(
                new com.viglet.turing.exchange.sn.TurSNSiteExportValidator(),
                org.mockito.Mockito.mock(com.viglet.turing.service.storage.TurStorageService.class));

        var seVendor = new com.viglet.turing.persistence.model.se.TurSEVendor();
        seVendor.setId("SOLR");
        seVendor.setTitle("Solr");
        var seInstance = new com.viglet.turing.persistence.model.se.TurSEInstance();
        seInstance.setId("se-1");
        seInstance.setTitle("Search Engine 1");
        seInstance.setEnabled(1);
        seInstance.setEndpointUrl("http://localhost:8983/solr");
        seInstance.setTurSEVendor(seVendor);
        var exported = new TurSNSite();
        exported.setId("site-1");
        exported.setName("Catalog");
        exported.setFacet(0);
        exported.setHl(0);
        exported.setMlt(0);
        exported.setThesaurus(0);
        exported.setTurSEInstance(seInstance);

        // Two pages in one locale, so the writer crosses a page boundary.
        var pages = List.of(localeRun("en_US", CHUNK, 0), localeRun("en_US", 5, CHUNK)).stream()
                .map(run -> run.stream().map(Map.Entry::getValue).toList())
                .toList();
        var iterator = pages.iterator();
        var content = new com.viglet.turing.exchange.sn.TurSNSiteContentPages("Catalog",
                List.of(new com.viglet.turing.exchange.sn.TurSNSiteContentPages.LocalePages(
                        "en_US", iterator)));

        java.nio.file.Path zipPath = exportService.exportSNSitesToZip(List.of(exported), content,
                false);
        java.nio.file.Path exportFolder = java.nio.file.Path
                .of(zipPath.toString().replace(".zip", ""));
        try {
            java.io.File written = exportFolder.resolve("Catalog_content.json").toFile();
            assertThat(written).exists();

            int indexed = importExchange().streamContentFile(
                    tools.jackson.databind.json.JsonMapper.builder().build(), written, "task-1");

            assertThat(indexed).isEqualTo(CHUNK + 5);
            assertThat(indexedChunks).hasSize(2);
            assertThat(indexedChunks.get(0).getValue().get(0)).isEqualTo("doc-00000");
            assertThat(indexedChunks.get(1).getValue()).hasSize(5);
        } finally {
            java.nio.file.Files.deleteIfExists(zipPath);
            if (java.nio.file.Files.exists(exportFolder)) {
                org.apache.commons.io.FileUtils.deleteDirectory(exportFolder.toFile());
            }
        }
    }
}
