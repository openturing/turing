/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.viglet.turing.sn.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.viglet.core.manifest.VigletFieldSpec;
import com.viglet.core.manifest.VigletFieldType;

/**
 * Unit tests for the deterministic ES {@code _mapping} -> manifest transform (T657).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurElasticsearchManifestBuilderTest {

    private static Map<String, Object> mappingWith(Map<String, Object> properties) {
        return Map.of("my-index", Map.of("mappings", Map.of("properties", properties)));
    }

    @Test
    void mapsScalarTypesAndFacetsKeywordFamily() {
        Map<String, Object> mapping = mappingWith(Map.of(
                "title", Map.of("type", "text"),
                "brand", Map.of("type", "keyword"),
                "price", Map.of("type", "double"),
                "stock", Map.of("type", "integer"),
                "inStock", Map.of("type", "boolean"),
                "createdAt", Map.of("type", "date")));

        TurElasticsearchManifestBuilder.Result result =
                TurElasticsearchManifestBuilder.build(mapping, List.of());
        Map<String, VigletFieldSpec> byName = TurElasticsearchManifestBuilder.byName(result.fields());

        assertEquals(6, result.fields().size());
        assertEquals(VigletFieldType.TEXT, byName.get("title").type());
        assertFalse(byName.get("title").facet(), "text is not a term facet");
        assertEquals(VigletFieldType.STRING, byName.get("brand").type());
        assertTrue(byName.get("brand").facet(), "keyword becomes a facet");
        assertEquals(VigletFieldType.DOUBLE, byName.get("price").type());
        assertEquals(VigletFieldType.INT, byName.get("stock").type());
        assertEquals(VigletFieldType.BOOL, byName.get("inStock").type());
        assertTrue(byName.get("inStock").facet(), "boolean becomes a facet");
        assertEquals(VigletFieldType.DATE, byName.get("createdAt").type());
    }

    @Test
    void recursesObjectPropertiesIntoDottedNames() {
        Map<String, Object> mapping = mappingWith(Map.of(
                "name", Map.of("type", "text"),
                "author", Map.of("properties", Map.of(
                        "firstName", Map.of("type", "keyword"),
                        "lastName", Map.of("type", "keyword")))));

        TurElasticsearchManifestBuilder.Result result =
                TurElasticsearchManifestBuilder.build(mapping, List.of());
        Map<String, VigletFieldSpec> byName = TurElasticsearchManifestBuilder.byName(result.fields());

        assertNotNull(byName.get("author.firstName"));
        assertNotNull(byName.get("author.lastName"));
        assertEquals(VigletFieldType.STRING, byName.get("author.firstName").type());
    }

    @Test
    void skipsUnsupportedTypesWithWarning() {
        Map<String, Object> mapping = mappingWith(Map.of(
                "location", Map.of("type", "geo_point"),
                "embedding", Map.of("type", "dense_vector"),
                "title", Map.of("type", "text")));

        TurElasticsearchManifestBuilder.Result result =
                TurElasticsearchManifestBuilder.build(mapping, List.of());

        assertEquals(1, result.fields().size(), "only the supported field survives");
        assertEquals("title", result.fields().get(0).name());
        assertEquals(2, result.warnings().size(), "both unsupported types warned");
    }

    @Test
    void detectsMultiValuedFromSampleDocuments() {
        Map<String, Object> mapping = mappingWith(Map.of(
                "tags", Map.of("type", "keyword"),
                "brand", Map.of("type", "keyword")));
        List<Map<String, Object>> samples = List.of(
                Map.of("tags", List.of("a", "b"), "brand", "acme"));

        TurElasticsearchManifestBuilder.Result result =
                TurElasticsearchManifestBuilder.build(mapping, samples);
        Map<String, VigletFieldSpec> byName = TurElasticsearchManifestBuilder.byName(result.fields());

        assertTrue(byName.get("tags").multiValued(), "array value -> multi-valued");
        assertFalse(byName.get("brand").multiValued(), "scalar value -> single-valued");
    }

    @Test
    void emptyMappingYieldsNoFieldsAndAWarning() {
        TurElasticsearchManifestBuilder.Result result =
                TurElasticsearchManifestBuilder.build(Map.of(), List.of());

        assertTrue(result.fields().isEmpty());
        assertFalse(result.warnings().isEmpty());
    }
}
