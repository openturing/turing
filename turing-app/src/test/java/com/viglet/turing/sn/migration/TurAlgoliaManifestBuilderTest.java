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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.viglet.core.manifest.VigletFieldSpec;
import com.viglet.core.manifest.VigletFieldType;

/**
 * Unit tests for the Algolia settings -> manifest overlay (T658).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurAlgoliaManifestBuilderTest {

    private static VigletFieldSpec field(String name, VigletFieldType type, boolean facet) {
        return VigletFieldSpec.builder().name(name).type(type).facet(facet).build();
    }

    @Test
    void unwrapsModifiersAndSplitsCommaGroups() {
        Set<String> names = TurAlgoliaManifestBuilder.parseAttributes(
                List.of("title", "unordered(description)", "brand,name", "filterOnly(price)"));

        assertEquals(Set.of("title", "description", "brand", "name", "price"), names);
    }

    @Test
    void flipsFacetOnAttributesForFaceting() {
        List<VigletFieldSpec> base = List.of(
                field("title", VigletFieldType.TEXT, false),
                field("brand", VigletFieldType.STRING, false));
        Map<String, Object> settings = Map.of(
                "attributesForFaceting", List.of("brand", "searchable(category)"));

        TurAlgoliaManifestBuilder.Result result = TurAlgoliaManifestBuilder.build(base, settings);
        Map<String, VigletFieldSpec> byName = index(result.fields());

        assertTrue(byName.get("brand").facet(), "brand becomes a facet");
        assertFalse(byName.get("title").facet(), "title stays non-facet");
        assertTrue(byName.get("category").facet(), "unwrapped searchable(category) added as facet");
    }

    @Test
    void addsSearchableAttributeMissingFromSampleAsText() {
        List<VigletFieldSpec> base = List.of(field("title", VigletFieldType.TEXT, false));
        Map<String, Object> settings = Map.of(
                "searchableAttributes", List.of("title", "unordered(summary)"));

        TurAlgoliaManifestBuilder.Result result = TurAlgoliaManifestBuilder.build(base, settings);
        Map<String, VigletFieldSpec> byName = index(result.fields());

        assertEquals(VigletFieldType.TEXT, byName.get("summary").type());
        assertFalse(result.warnings().isEmpty());
    }

    @Test
    void ignoresObjectIdAsAField() {
        List<VigletFieldSpec> base = List.of(
                field("objectID", VigletFieldType.STRING, false),
                field("title", VigletFieldType.TEXT, false));
        Map<String, Object> settings = Map.of("attributesForFaceting", List.of("objectID"));

        TurAlgoliaManifestBuilder.Result result = TurAlgoliaManifestBuilder.build(base, settings);

        assertFalse(index(result.fields()).containsKey("objectID"), "objectID is the id, not a field");
    }

    private static Map<String, VigletFieldSpec> index(List<VigletFieldSpec> fields) {
        return fields.stream().collect(java.util.stream.Collectors.toMap(VigletFieldSpec::name, f -> f));
    }
}
