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
package com.viglet.turing.exchange.sn;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.se.TurSEVendor;
import com.viglet.turing.persistence.model.sn.TurSNSite;

/**
 * T1033 / §D.13 — the export writes the content file from page iterators now, so
 * the thing worth proving is that the bytes did not change: the same
 * <code>{ site: { locale: [ … ] } }</code> shape an import already reads, built
 * without ever holding the site in memory.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurSNSiteContentStreamingExportTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static TurSNSiteExportFileService service() {
        return new TurSNSiteExportFileService(new TurSNSiteExportValidator(),
                org.mockito.Mockito.mock(com.viglet.turing.service.storage.TurStorageService.class));
    }

    private static TurSNSite site(String name) {
        TurSEVendor seVendor = new TurSEVendor();
        seVendor.setId("SOLR");
        seVendor.setTitle("Solr");
        TurSEInstance seInstance = new TurSEInstance();
        seInstance.setId("se-1");
        seInstance.setTitle("Search Engine 1");
        seInstance.setEnabled(1);
        seInstance.setEndpointUrl("http://localhost:8983/solr");
        seInstance.setTurSEVendor(seVendor);

        TurSNSite site = new TurSNSite();
        site.setId("site-a");
        site.setName(name);
        site.setFacet(0);
        site.setHl(0);
        site.setMlt(0);
        site.setThesaurus(0);
        site.setTurSEInstance(seInstance);
        return site;
    }

    private static Map<String, Object> doc(String id, String title) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", id);
        document.put("title", title);
        return document;
    }

    /** A locale whose pages are handed over one at a time, tracking how many were pulled. */
    private static TurSNSiteContentPages.LocalePages locale(String key,
            List<List<Map<String, Object>>> pages, List<String> pulled) {
        var iterator = pages.iterator();
        return new TurSNSiteContentPages.LocalePages(key, new java.util.Iterator<>() {
            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public List<Map<String, Object>> next() {
                pulled.add(key);
                return iterator.next();
            }
        });
    }

    private JsonNode contentJsonFromZip(Path zipPath, String fileName) throws Exception {
        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            var entry = zipFile.stream()
                    .filter(e -> e.getName().endsWith(fileName))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no " + fileName + " in the zip"));
            try (var in = zipFile.getInputStream(entry)) {
                return objectMapper.readTree(in);
            }
        }
    }

    @Test
    @DisplayName("The content file keeps its { site: { locale: [docs] } } shape across every page")
    void writesTheSameStructureFromPages() throws Exception {
        List<String> pulled = new ArrayList<>();
        var content = new TurSNSiteContentPages("Site A", List.of(
                locale("en_US", List.of(
                        List.of(doc("a", "Alpha"), doc("b", "Bravo")),
                        List.of(doc("c", "Charlie"))), pulled),
                locale("pt_BR", List.of(
                        List.of(doc("d", "Delta"))), pulled)));

        Path zipPath = service().exportSNSitesToZip(List.of(site("Site A")), content, false);
        Path exportFolder = Path.of(zipPath.toString().replace(".zip", ""));
        try {
            JsonNode json = contentJsonFromZip(zipPath, "Site_A_content.json");

            JsonNode siteNode = json.path("Site A");
            assertThat(siteNode.path("en_US")).hasSize(3);
            assertThat(siteNode.path("pt_BR")).hasSize(1);
            // Order is preserved across the page boundary.
            assertThat(siteNode.path("en_US").get(0).path("id").asText()).isEqualTo("a");
            assertThat(siteNode.path("en_US").get(2).path("id").asText()).isEqualTo("c");
            assertThat(siteNode.path("en_US").get(2).path("title").asText()).isEqualTo("Charlie");
            assertThat(siteNode.path("pt_BR").get(0).path("id").asText()).isEqualTo("d");
            // Every page was pulled, and only while writing.
            assertThat(pulled).containsExactly("en_US", "en_US", "pt_BR");
        } finally {
            Files.deleteIfExists(zipPath);
            if (Files.exists(exportFolder)) {
                FileUtils.deleteDirectory(exportFolder.toFile());
            }
        }
    }

    @Test
    @DisplayName("A locale with no documents keeps its empty array, as the old writer left it")
    void emptyLocaleKeepsAnEmptyArray() throws Exception {
        var content = new TurSNSiteContentPages("Site A", List.of(
                locale("en_US", List.of(), new ArrayList<>())));

        Path zipPath = service().exportSNSitesToZip(List.of(site("Site A")), content, false);
        Path exportFolder = Path.of(zipPath.toString().replace(".zip", ""));
        try {
            JsonNode json = contentJsonFromZip(zipPath, "Site_A_content.json");

            assertThat(json.path("Site A").path("en_US").isArray()).isTrue();
            assertThat(json.path("Site A").path("en_US")).isEmpty();
        } finally {
            Files.deleteIfExists(zipPath);
            if (Files.exists(exportFolder)) {
                FileUtils.deleteDirectory(exportFolder.toFile());
            }
        }
    }

    @Test
    @DisplayName("A site name with punctuation is sanitised into the file name, as before")
    void siteNameIsSanitisedForTheFileName() throws Exception {
        var content = new TurSNSiteContentPages("Site A/B", List.of(
                locale("en_US", List.of(List.of(doc("a", "Alpha"))), new ArrayList<>())));

        Path zipPath = service().exportSNSitesToZip(List.of(site("Site A/B")), content, false);
        Path exportFolder = Path.of(zipPath.toString().replace(".zip", ""));
        try {
            // The JSON key keeps the real name; only the file name is sanitised.
            JsonNode json = contentJsonFromZip(zipPath, "Site_A_B_content.json");
            assertThat(json.has("Site A/B")).isTrue();
        } finally {
            Files.deleteIfExists(zipPath);
            if (Files.exists(exportFolder)) {
                FileUtils.deleteDirectory(exportFolder.toFile());
            }
        }
    }

    @Test
    @DisplayName("No content means no content file, exactly as a null map used to")
    void noContentWritesNoFile() throws Exception {
        Path zipPath = service().exportSNSitesToZip(List.of(site("Site A")), null, false);
        Path exportFolder = Path.of(zipPath.toString().replace(".zip", ""));
        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            assertThat(zipFile.stream().map(java.util.zip.ZipEntry::getName))
                    .noneMatch(name -> name.endsWith("_content.json"));
        } finally {
            Files.deleteIfExists(zipPath);
            if (Files.exists(exportFolder)) {
                FileUtils.deleteDirectory(exportFolder.toFile());
            }
        }
    }
}
