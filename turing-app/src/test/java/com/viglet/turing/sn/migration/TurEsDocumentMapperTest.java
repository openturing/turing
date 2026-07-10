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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.viglet.turing.commons.sn.field.TurSNFieldName;

/**
 * Unit tests for the ES {@code _source} -> SN attribute-map transform (T657).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurEsDocumentMapperTest {

    @Test
    void setsIdTypeAndProviderAndKeepsScalars() {
        Map<String, Object> source = Map.of("title", "Widget", "price", 9.99);

        Map<String, Object> attrs = TurEsDocumentMapper.toAttributes("doc-1", source, "products", "elasticsearch");

        assertEquals("doc-1", attrs.get(TurSNFieldName.ID));
        assertEquals("products", attrs.get(TurSNFieldName.TYPE));
        assertEquals("elasticsearch", attrs.get(TurSNFieldName.SOURCE_APPS));
        assertEquals("Widget", attrs.get("title"));
        assertEquals(9.99, attrs.get("price"));
    }

    @Test
    void dropsNullBlankAndEmptyPerGroundingContract() {
        Map<String, Object> source = new HashMap<>();
        source.put("title", "Widget");
        source.put("subtitle", "");
        source.put("notes", null);
        source.put("tags", List.of());

        Map<String, Object> attrs = TurEsDocumentMapper.toAttributes("doc-1", source, "products", "elasticsearch");

        assertTrue(attrs.containsKey("title"));
        assertFalse(attrs.containsKey("subtitle"), "blank string dropped");
        assertFalse(attrs.containsKey("notes"), "null dropped");
        assertFalse(attrs.containsKey("tags"), "empty list dropped");
    }

    @Test
    void flattensNestedObjectToDottedNames() {
        Map<String, Object> source = Map.of(
                "title", "Book",
                "author", Map.of("firstName", "Ada", "lastName", "Lovelace"));

        Map<String, Object> attrs = TurEsDocumentMapper.toAttributes("doc-1", source, "books", "elasticsearch");

        assertEquals("Ada", attrs.get("author.firstName"));
        assertEquals("Lovelace", attrs.get("author.lastName"));
    }

    @Test
    void keepsScalarArrayAsList() {
        Map<String, Object> source = Map.of("tags", Arrays.asList("a", "b", "c"));

        Map<String, Object> attrs = TurEsDocumentMapper.toAttributes("doc-1", source, "products", "elasticsearch");

        assertInstanceOf(List.class, attrs.get("tags"));
        assertEquals(3, ((List<?>) attrs.get("tags")).size());
    }

    @Test
    void collapsesListOfNestedObjectsIntoPerLeafLists() {
        Map<String, Object> source = Map.of("variants", List.of(
                Map.of("sku", "A1", "color", "red"),
                Map.of("sku", "A2", "color", "blue")));

        Map<String, Object> attrs = TurEsDocumentMapper.toAttributes("doc-1", source, "products", "elasticsearch");

        assertInstanceOf(List.class, attrs.get("variants.sku"));
        assertEquals(List.of("A1", "A2"), attrs.get("variants.sku"));
        assertEquals(List.of("red", "blue"), attrs.get("variants.color"));
    }
}
