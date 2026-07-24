/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.feed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.list;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.viglet.turing.api.sn.job.TurSNImportAPI;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.turing.commons.sn.field.TurSNFieldName;
import com.viglet.turing.genai.urlfetch.TurUrlFetchService;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.properties.TurStructuredFeedProperty;
import com.viglet.turing.properties.TurStructuredFeedProperty.Source;
import com.viglet.turing.sn.field.TurSNFieldProvisioner;

/**
 * T791 / §LIV.2 (Block BF) — deterministic coverage for the structured-feed
 * ingester: feed-shape parsing (array / NDJSON / envelope), record→attribute
 * mapping under the grounding contract, the import call, and cross-run de-index.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TurStructuredFeedIngestServiceTest {

    @Mock
    private TurUrlFetchService urlFetchService;
    @Mock
    private TurSNSiteRepository turSNSiteRepository;
    @Mock
    private TurSNFieldProvisioner fieldProvisioner;
    @Mock
    private TurSNImportAPI turSNImportAPI;

    private TurStructuredFeedProperty property;
    private TurStructuredFeedIngestService service;

    @BeforeEach
    void setUp() {
        property = new TurStructuredFeedProperty();
        service = new TurStructuredFeedIngestService(property, urlFetchService,
                turSNSiteRepository, fieldProvisioner, turSNImportAPI);
    }

    private static Source source() {
        Source s = new Source();
        s.setId("cat");
        s.setSiteName("catalog");
        s.setFeedUrl("https://example.com/catalog.json");
        s.setIdField("id");
        s.setLocale("en_US");
        return s;
    }

    private TurSNJobItems captureSend() {
        ArgumentCaptor<TurSNJobItems> captor = ArgumentCaptor.forClass(TurSNJobItems.class);
        verify(turSNImportAPI).send(captor.capture());
        return captor.getValue();
    }

    // ─────────────────────────── parse shapes ───────────────────────────

    @Test
    void parseRecords_topLevelArray() {
        var records = service.parseRecords("[{\"id\":\"a\"},{\"id\":\"b\"}]", null);
        assertThat(records).hasSize(2);
    }

    @Test
    void parseRecords_ndjson() {
        var records = service.parseRecords("{\"id\":\"a\"}\n\n{\"id\":\"b\"}\n{\"id\":\"c\"}", null);
        assertThat(records).hasSize(3);
    }

    @Test
    void parseRecords_objectEnvelopeMapOfArraysFlattened() {
        // The model-catalog vendors shape: object whose values are arrays.
        String body = "{\"version\":1,\"vendors\":{\"openai\":[{\"id\":\"a\"}],\"gemini\":[{\"id\":\"b\"},{\"id\":\"c\"}]}}";
        var records = service.parseRecords(body, "vendors");
        assertThat(records).hasSize(3);
    }

    @Test
    void toAttributes_keepsScalarsAndArrays_omitsEmptyAndObjects() {
        var node = ((tools.jackson.databind.node.ObjectNode) tools.jackson.databind.json.JsonMapper.builder()
                .build().readTree(
                        "{\"id\":\"m1\",\"price\":1.5,\"ctx\":128000,\"tools\":true,"
                                + "\"tags\":[\"a\",\"\",\"b\"],\"blank\":\"\",\"nested\":{\"x\":1}}"));
        var attrs = service.toAttributes(node, "m1", false);
        assertThat(attrs.get(TurSNFieldName.ID)).isEqualTo("m1");
        assertThat(attrs.get("price")).isEqualTo(1.5);
        assertThat(((Number) attrs.get("ctx")).intValue()).isEqualTo(128000);
        assertThat(attrs.get("tools")).isEqualTo(Boolean.TRUE);
        assertThat(attrs).containsKey("tags");
        assertThat(attrs.get("tags")).asInstanceOf(list(Object.class)).containsExactly("a", "b"); // blank dropped
        assertThat(attrs).doesNotContainKey("blank");   // grounding contract
        assertThat(attrs).doesNotContainKey("nested");  // objects skipped (flatten off)
    }

    @Test
    void toAttributes_flattensNestedObjectsWhenEnabled() {
        var node = ((tools.jackson.databind.node.ObjectNode) tools.jackson.databind.json.JsonMapper.builder()
                .build().readTree(
                        "{\"id\":\"m1\",\"contextWindow\":128000,"
                                + "\"pricing\":{\"inputPer1M\":5.0,\"currency\":\"USD\"},"
                                + "\"modalities\":{\"input\":[\"image\",\"text\"]}}"));
        var attrs = service.toAttributes(node, "m1", true);
        // Nested objects become parent_child flat fields (queryable/citable).
        assertThat(attrs.get("pricing_inputPer1M")).isEqualTo(5.0);
        assertThat(attrs.get("pricing_currency")).isEqualTo("USD");
        assertThat(attrs.get("modalities_input")).asInstanceOf(list(Object.class)).containsExactly("image", "text");
        assertThat(((Number) attrs.get("contextWindow")).intValue()).isEqualTo(128000);
        // The raw nested object key is never kept.
        assertThat(attrs).doesNotContainKey("pricing");
    }

    // ─────────────────────────── ingest ───────────────────────────

    @Test
    void ingestSource_siteNotFound_failsFailOpen() {
        when(turSNSiteRepository.findByNameIgnoreCase("catalog")).thenReturn(Optional.empty());
        var result = service.ingestSource(source());
        assertThat(result.success()).isFalse();
        verify(turSNImportAPI, never()).send(any());
    }

    @Test
    void ingestSource_mapsRecordsAndImportsCreateItems() {
        TurSNSite site = new TurSNSite();
        site.setName("catalog");
        when(turSNSiteRepository.findByNameIgnoreCase("catalog")).thenReturn(Optional.of(site));
        when(urlFetchService.fetchRaw("https://example.com/catalog.json"))
                .thenReturn("[{\"id\":\"a\",\"title\":\"A\"},{\"id\":\"b\"}]");

        var result = service.ingestSource(source());

        assertThat(result.success()).isTrue();
        assertThat(result.imported()).isEqualTo(2);
        assertThat(result.deIndexed()).isZero();
        TurSNJobItems sent = captureSend();
        // T806 — the chunk carries the 2 CREATEs plus one terminal COMMIT.
        assertThat(sent.getTuringDocuments())
                .filteredOn(i -> i.getTurSNJobAction() == TurSNJobAction.CREATE).hasSize(2)
                .allMatch(i -> i.getId() != null);
        assertThat(sent.getTuringDocuments())
                .anyMatch(i -> i.getTurSNJobAction() == TurSNJobAction.COMMIT);
    }

    @Test
    void ingestSource_splitsIntoBoundedChunksEachWithTerminalCommit() {
        // T806 / §LV.4 — a feed larger than batch-size is split into bounded
        // chunks, each sent as its own bulk import ending in a COMMIT.
        property.setBatchSize(2);
        TurSNSite site = new TurSNSite();
        site.setName("catalog");
        when(turSNSiteRepository.findByNameIgnoreCase("catalog")).thenReturn(Optional.of(site));
        when(urlFetchService.fetchRaw("https://example.com/catalog.json")).thenReturn(
                "[{\"id\":\"a\"},{\"id\":\"b\"},{\"id\":\"c\"},{\"id\":\"d\"},{\"id\":\"e\"}]");

        var result = service.ingestSource(source());

        assertThat(result.success()).isTrue();
        assertThat(result.imported()).isEqualTo(5);
        // 5 records / batch-size 2 → 3 chunks → 3 sends.
        ArgumentCaptor<TurSNJobItems> captor = ArgumentCaptor.forClass(TurSNJobItems.class);
        verify(turSNImportAPI, times(3)).send(captor.capture());
        // Every chunk ends in exactly one COMMIT; the CREATEs total 5 across chunks.
        int creates = 0;
        for (TurSNJobItems chunk : captor.getAllValues()) {
            assertThat(chunk.getTuringDocuments())
                    .filteredOn(i -> i.getTurSNJobAction() == TurSNJobAction.COMMIT).hasSize(1);
            creates += (int) chunk.getTuringDocuments().stream()
                    .filter(i -> i.getTurSNJobAction() == TurSNJobAction.CREATE).count();
        }
        assertThat(creates).isEqualTo(5);
    }

    @Test
    void ingestSource_oneBadChunkNeverAbortsTheRest() {
        // T806 — per-chunk fail-open: the first chunk throwing does not stop later chunks.
        property.setBatchSize(2);
        TurSNSite site = new TurSNSite();
        site.setName("catalog");
        when(turSNSiteRepository.findByNameIgnoreCase("catalog")).thenReturn(Optional.of(site));
        when(urlFetchService.fetchRaw("https://example.com/catalog.json")).thenReturn(
                "[{\"id\":\"a\"},{\"id\":\"b\"},{\"id\":\"c\"},{\"id\":\"d\"}]");
        org.mockito.Mockito.doThrow(new RuntimeException("boom"))
                .doNothing()
                .when(turSNImportAPI).send(any());

        var result = service.ingestSource(source());

        assertThat(result.success()).isTrue();
        // 4 records / 2 → 2 chunks; both are attempted even though the first throws.
        verify(turSNImportAPI, times(2)).send(any());
    }

    @Test
    void partition_splitsAndHandlesEdgeCases() {
        assertThat(TurStructuredFeedIngestService.partition(java.util.List.of(), 3)).isEmpty();
        assertThat(TurStructuredFeedIngestService.partition(java.util.List.of(1, 2, 3, 4, 5), 2))
                .hasSize(3);
        // Non-positive size → a single chunk (legacy unbounded behaviour).
        assertThat(TurStructuredFeedIngestService.partition(java.util.List.of(1, 2, 3), 0))
                .hasSize(1);
    }

    @Test
    void ingestSource_deIndexesVanishedIdsOnSecondRun() {
        TurSNSite site = new TurSNSite();
        site.setName("catalog");
        when(turSNSiteRepository.findByNameIgnoreCase("catalog")).thenReturn(Optional.of(site));
        when(urlFetchService.fetchRaw("https://example.com/catalog.json"))
                .thenReturn("[{\"id\":\"a\"},{\"id\":\"b\"}]")   // run 1: a,b
                .thenReturn("[{\"id\":\"a\"}]");                  // run 2: b vanished

        service.ingestSource(source()); // primes last-seen {a,b}
        var second = service.ingestSource(source());

        assertThat(second.imported()).isEqualTo(1);
        assertThat(second.deIndexed()).isEqualTo(1);
        // second send carries 1 CREATE (a) + 1 DELETE (b)
        ArgumentCaptor<TurSNJobItems> captor = ArgumentCaptor.forClass(TurSNJobItems.class);
        verify(turSNImportAPI, times(2)).send(captor.capture());
        TurSNJobItems secondSend = captor.getAllValues().get(1);
        assertThat(secondSend.getTuringDocuments())
                .anyMatch(i -> i.getTurSNJobAction() == TurSNJobAction.DELETE && "b".equals(i.getId()));
        assertThat(secondSend.getTuringDocuments())
                .anyMatch(i -> i.getTurSNJobAction() == TurSNJobAction.CREATE && "a".equals(i.getId()));
    }

    @Test
    void ingestSource_declaresSchemaFromManifest() {
        TurSNSite site = new TurSNSite();
        site.setName("catalog");
        Source s = source();
        s.setManifestUrl("https://example.com/query-manifest.json");
        when(turSNSiteRepository.findByNameIgnoreCase("catalog")).thenReturn(Optional.of(site));
        when(urlFetchService.fetchRaw("https://example.com/query-manifest.json")).thenReturn(
                "{\"schemaVersion\":\"1\",\"fields\":[{\"name\":\"kind\",\"type\":\"STRING\",\"facet\":true},"
                        + "{\"name\":\"price\",\"type\":\"DOUBLE\"}]}");
        when(urlFetchService.fetchRaw("https://example.com/catalog.json"))
                .thenReturn("[{\"id\":\"a\",\"kind\":\"CHAT\",\"price\":1.0}]");
        when(fieldProvisioner.ensureField(eq(site), any())).thenReturn(true);

        var result = service.ingestSource(s);

        assertThat(result.success()).isTrue();
        assertThat(result.fieldsDeclared()).isEqualTo(2);
        verify(fieldProvisioner, times(2)).ensureField(eq(site), any());
    }
}
