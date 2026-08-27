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
package com.viglet.turing.plugins.se.solr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.solr.client.solrj.impl.HttpJdkSolrClient;
import org.apache.solr.common.SolrInputDocument;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.viglet.turing.commons.sn.field.TurSNFieldName;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.solr.TurSolr;
import com.viglet.turing.solr.TurSolrInstance;
import com.viglet.turing.solr.TurSolrInstanceProcess;

/**
 * T1040 / §CVI.5 — asks Solr whether the queries this plugin builds mean what it
 * thinks.
 *
 * <p>Nothing did before. Of 57 {@code *IT} classes none ran a search-engine
 * container, so every engine-specific string was checked against a mock written
 * from the same understanding as the code — and a wrong understanding passes
 * both. T1039 is what that cost: an unescaped space made a malformed range bound,
 * Solr rejected it, the rejection came back as an empty result, and an empty page
 * reads to a cursor sweep as end-of-source. A truncated export, an audit blaming
 * the source, and a green unit suite.
 *
 * <p>So the assertions here are deliberately about <em>completeness</em> and
 * <em>bound semantics</em>, the two properties that failure silently violated.
 * The plugin is the real one; only the instance lookup is mocked, so the query
 * strings under test are the ones production sends.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TurSolrCursorPagingIT {

    private static final String CORE = "turing";
    private static final String SOURCE = "AEM Author";

    private static final GenericContainer<?> SOLR =
            new GenericContainer<>(DockerImageName.parse("solr:9"))
                    .withExposedPorts(8983)
                    .withCommand("solr-precreate", CORE)
                    .waitingFor(Wait.forHttp("/solr/" + CORE + "/admin/ping")
                            .forStatusCode(200));

    /**
     * Ids chosen for what has broken: a space, a path with spaces, a colon and a
     * hyphen. Lexicographic order is the cursor's contract, so the expected sweep
     * order is these sorted.
     */
    private static final List<String> IDS = List.of(
            "/content/wknd/ski touring mont blanc",
            "/content/wknd/skiing",
            "doc-00001",
            "doc-00002",
            "my doc",
            "ns:doc",
            "plain");

    private TurSolrSearchEnginePlugin plugin;
    private TurSNSiteLocale siteLocale;

    @BeforeAll
    void startAndSeed() throws Exception {
        SOLR.start();
        String baseUrl = "http://" + SOLR.getHost() + ":" + SOLR.getMappedPort(8983) + "/solr";
        HttpJdkSolrClient client = new HttpJdkSolrClient.Builder(baseUrl).build();
        TurSolrInstance instance = new TurSolrInstance(client, new URL(baseUrl), CORE);

        for (String id : IDS) {
            SolrInputDocument doc = new SolrInputDocument();
            doc.addField(TurSNFieldName.ID, id);
            doc.addField(TurSNFieldName.TYPE, "Page");
            doc.addField(TurSNFieldName.SOURCE_APPS, SOURCE);
            doc.addField(TurSNFieldName.CHECKSUM, "sum-" + Math.abs(id.hashCode()));
            client.add(CORE, doc);
        }
        client.commit(CORE);

        TurSolrInstanceProcess instanceProcess = mock(TurSolrInstanceProcess.class);
        siteLocale = new TurSNSiteLocale();
        when(instanceProcess.initSolrInstance(any(TurSNSiteLocale.class)))
                .thenReturn(Optional.of(instance));

        plugin = new TurSolrSearchEnginePlugin(mock(TurSolr.class), instanceProcess,
                new com.viglet.turing.properties.TurConfigProperties(),
                mock(org.springframework.core.io.ResourceLoader.class),
                mock(com.viglet.turing.solr.source.TurSolrInstanceSource.class));
    }

    @AfterAll
    void stopContainer() {
        SOLR.stop();
    }

    /** Drains the inventory with the given page size, following the cursor. */
    private List<String> sweepInventory(int rows) {
        List<String> ids = new ArrayList<>();
        String cursor = null;
        for (int guard = 0; guard < 100; guard++) {
            var page = plugin.retrieveInventoryPage(siteLocale, SOURCE, cursor, rows);
            assertThat(page.supported()).isTrue();
            if (page.entries().isEmpty()) {
                break;
            }
            page.entries().forEach(entry -> ids.add(entry.id()));
            cursor = page.nextCursor();
            if (!page.hasMore() || cursor == null) {
                break;
            }
        }
        return ids;
    }

    /** Drains the export with the given page size, following the cursor. */
    private List<String> sweepExport(int rows) {
        List<String> ids = new ArrayList<>();
        String cursor = null;
        for (int guard = 0; guard < 100; guard++) {
            List<Map<String, Object>> page =
                    plugin.retrieveDocumentPageAfter(siteLocale, siteFieldMap(), cursor, rows);
            if (page.isEmpty()) {
                break;
            }
            page.forEach(doc -> ids.add(doc.get(TurSNFieldName.ID).toString()));
            cursor = ids.get(ids.size() - 1);
            if (page.size() < rows) {
                break;
            }
        }
        return ids;
    }

    private static Map<String, ?> siteFieldMap() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(TurSNFieldName.SOURCE_APPS, null);
        return fields;
    }

    @Test
    @DisplayName("An inventory sweep reaches every document, whatever its id contains")
    void inventorySweepIsComplete() {
        assertThat(sweepInventory(2))
                .as("a sweep that stops early is what T1039 was")
                .containsExactlyInAnyOrderElementsOf(IDS)
                .hasSameSizeAs(IDS);
    }

    @Test
    @DisplayName("An export sweep reaches every document too, and the two agree")
    void exportSweepIsComplete() {
        assertThat(sweepExport(2)).containsExactlyInAnyOrderElementsOf(IDS);
        assertThat(sweepExport(3)).containsExactlyInAnyOrderElementsOf(sweepInventory(3));
    }

    @Test
    @DisplayName("The page size does not change what a sweep finds")
    void sweepIsIndependentOfPageSize() {
        for (int rows : new int[] {1, 2, 3, 7, 50}) {
            assertThat(sweepInventory(rows))
                    .as("page size %d", rows)
                    .containsExactlyInAnyOrderElementsOf(IDS);
        }
    }

    @Test
    @DisplayName("Pages come back id-ascending, which is what makes the last id a valid cursor")
    void pagesAreIdAscending() {
        List<String> swept = sweepInventory(2);

        assertThat(swept).isSorted();
    }

    @Test
    @DisplayName("The cursor bound is exclusive — Solr agrees, which was only ever assumed")
    void cursorBoundIsExclusive() {
        List<String> sorted = IDS.stream().sorted().toList();
        String third = sorted.get(2);

        var page = plugin.retrieveInventoryPage(siteLocale, SOURCE, third, 50);

        assertThat(page.entries()).extracting("id")
                .doesNotContain(third)
                .containsExactlyElementsOf(sorted.subList(3, sorted.size()));
    }

    @Test
    @DisplayName("A cursor on an id containing a space continues instead of ending the sweep")
    void spacedCursorDoesNotEndTheSweep() {
        // The exact T1039 regression: before the fix this returned an empty page,
        // which the caller read as "fully enumerated".
        var page = plugin.retrieveInventoryPage(siteLocale, SOURCE, "my doc", 50);

        assertThat(page.entries()).isNotEmpty();
        assertThat(page.entries()).extracting("id").containsExactly("ns:doc", "plain");
    }

    @Test
    @DisplayName("A cursor on a path with spaces continues too — the AEM id shape")
    void pathLikeCursorContinues() {
        var page = plugin.retrieveInventoryPage(siteLocale,
                SOURCE, "/content/wknd/ski touring mont blanc", 50);

        assertThat(page.entries()).extracting("id")
                .containsExactly("/content/wknd/skiing", "doc-00001", "doc-00002", "my doc",
                        "ns:doc", "plain");
    }

    @Test
    @DisplayName("A source name containing a space filters, rather than matching nothing")
    void sourceFilterHandlesASpace() {
        var matching = plugin.retrieveInventoryPage(siteLocale, SOURCE, null, 50);
        var other = plugin.retrieveInventoryPage(siteLocale, "AEM Publish", null, 50);

        assertThat(matching.entries()).hasSize(IDS.size());
        assertThat(other.entries()).isEmpty();
    }

    @Test
    @DisplayName("The indexed checksum comes back on every row")
    void checksumIsReported() {
        var page = plugin.retrieveInventoryPage(siteLocale, SOURCE, null, 50);

        assertThat(page.entries()).allSatisfy(entry ->
                assertThat(entry.checksum()).isNotBlank().startsWith("sum-"));
    }

    @Test
    @DisplayName("A cursor past the last id ends the sweep, and the count is the source total")
    void cursorPastTheEndTerminates() {
        var first = plugin.retrieveInventoryPage(siteLocale, SOURCE, null, 50);
        assertThat(first.remaining()).isEqualTo(IDS.size());

        var past = plugin.retrieveInventoryPage(siteLocale, SOURCE, "zzz", 50);
        assertThat(past.entries()).isEmpty();
        assertThat(past.supported()).isTrue();
    }
}
