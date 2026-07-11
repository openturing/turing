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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.viglet.core.manifest.VigletFieldSpec;
import com.viglet.core.manifest.VigletFieldType;

/**
 * Unit tests for the field-mapping override applier (T660).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurMigrationOverridesTest {

    private static VigletFieldSpec field(String name, VigletFieldType type) {
        return VigletFieldSpec.builder().name(name).type(type).build();
    }

    private static Map<String, VigletFieldSpec> index(List<VigletFieldSpec> fields) {
        return fields.stream().collect(java.util.stream.Collectors.toMap(VigletFieldSpec::name, f -> f));
    }

    @Test
    void nullOverridesReturnFieldsUnchanged() {
        List<VigletFieldSpec> fields = List.of(field("title", VigletFieldType.TEXT));
        assertEquals(fields, TurMigrationOverrides.applyToFields(fields, null));
    }

    @Test
    void renameRetypeDropAndAddFieldsOnManifest() {
        List<VigletFieldSpec> base = List.of(
                field("title", VigletFieldType.TEXT),
                field("cost", VigletFieldType.STRING),
                field("legacy", VigletFieldType.STRING));
        List<TurMigrationOverride> overrides = List.of(
                new TurMigrationOverride("cost", "price", VigletFieldType.CURRENCY, false, null),
                new TurMigrationOverride("legacy", null, null, true, null),
                new TurMigrationOverride("source", null, VigletFieldType.STRING, false, "import"));

        Map<String, VigletFieldSpec> byName = index(TurMigrationOverrides.applyToFields(base, overrides));

        assertFalse(byName.containsKey("cost"), "renamed away");
        assertFalse(byName.containsKey("legacy"), "dropped");
        assertEquals(VigletFieldType.CURRENCY, byName.get("price").type(), "renamed + retyped");
        assertEquals(VigletFieldType.STRING, byName.get("source").type(), "added constant field");
        assertTrue(byName.containsKey("title"));
    }

    @Test
    void renameDropAndDefaultOnAttributes() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("cost", 9.99);
        attrs.put("legacy", "x");
        attrs.put("title", "Widget");
        List<TurMigrationOverride> overrides = List.of(
                new TurMigrationOverride("cost", "price", VigletFieldType.CURRENCY, false, null),
                new TurMigrationOverride("legacy", null, null, true, null),
                new TurMigrationOverride("source", null, VigletFieldType.STRING, false, "import"));

        Map<String, Object> out = TurMigrationOverrides.applyToAttributes(attrs, overrides);

        assertEquals(9.99, out.get("price"), "value moved under the new name");
        assertFalse(out.containsKey("cost"));
        assertFalse(out.containsKey("legacy"), "dropped");
        assertEquals("import", out.get("source"), "default injected for absent field");
        assertEquals("Widget", out.get("title"));
    }

    @Test
    void defaultDoesNotOverrideAPresentValue() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("brand", "Acme");
        List<TurMigrationOverride> overrides = List.of(
                new TurMigrationOverride("brand", null, null, false, "Unknown"));

        Map<String, Object> out = TurMigrationOverrides.applyToAttributes(attrs, overrides);

        assertEquals("Acme", out.get("brand"), "present value kept, default ignored");
    }
}
