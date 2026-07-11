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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.viglet.core.manifest.VigletFieldSpec;

/**
 * Pure, deterministic application of {@link TurMigrationOverride}s to both the
 * derived field manifest and each document (T660 / §XXXVIII.4).
 *
 * <p>Applying the same overrides to the fields and to the attributes keeps a
 * renamed/dropped field consistent between the schema and the data — a rename
 * moves both the manifest field and the document key, a drop removes both, and a
 * default injects a value only where the (renamed) field is absent. An override
 * that carries a {@code type} but matches no derived field materialises a new
 * (typically constant) field.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurMigrationOverrides {

    private TurMigrationOverrides() {
        // static-only
    }

    /** True when there is nothing to apply. */
    public static boolean isEmpty(List<TurMigrationOverride> overrides) {
        return overrides == null || overrides.isEmpty();
    }

    /**
     * Applies rename / retype / drop to the derived field specs, and adds a spec for
     * any override that carries a {@code type} but matched no derived field.
     */
    public static List<VigletFieldSpec> applyToFields(List<VigletFieldSpec> fields,
            List<TurMigrationOverride> overrides) {
        if (isEmpty(overrides)) {
            return fields == null ? List.of() : fields;
        }
        Map<String, TurMigrationOverride> byField = indexByField(overrides);
        List<VigletFieldSpec> result = new ArrayList<>();
        List<String> matched = new ArrayList<>();
        for (VigletFieldSpec spec : fields == null ? List.<VigletFieldSpec>of() : fields) {
            if (spec == null || spec.name() == null) {
                continue;
            }
            TurMigrationOverride ov = byField.get(spec.name());
            if (ov == null) {
                result.add(spec);
                continue;
            }
            matched.add(ov.field());
            if (ov.drop()) {
                continue; // excluded from the manifest
            }
            result.add(rebuild(spec, ov));
        }
        // Overrides that add a brand-new field (a type given, no source match, not dropped).
        for (TurMigrationOverride ov : overrides) {
            if (ov != null && !ov.drop() && ov.type() != null && !matched.contains(ov.field())) {
                result.add(VigletFieldSpec.builder()
                        .name(ov.targetName()).type(ov.type())
                        .description("Added by migration override").build());
            }
        }
        return result;
    }

    /** Applies rename / drop / default to one document's attribute map. */
    public static Map<String, Object> applyToAttributes(Map<String, Object> attributes,
            List<TurMigrationOverride> overrides) {
        if (isEmpty(overrides) || attributes == null) {
            return attributes;
        }
        Map<String, Object> result = new LinkedHashMap<>(attributes);
        for (TurMigrationOverride ov : overrides) {
            if (ov == null || ov.field() == null) {
                continue;
            }
            if (ov.drop()) {
                result.remove(ov.field());
            } else if (ov.rename() != null && !ov.rename().isBlank() && result.containsKey(ov.field())) {
                result.put(ov.rename(), result.remove(ov.field()));
            }
        }
        // Defaults run after rename so they key off the final (target) name.
        for (TurMigrationOverride ov : overrides) {
            if (ov != null && !ov.drop() && ov.defaultValue() != null
                    && !result.containsKey(ov.targetName())) {
                result.put(ov.targetName(), ov.defaultValue());
            }
        }
        return result;
    }

    private static VigletFieldSpec rebuild(VigletFieldSpec spec, TurMigrationOverride ov) {
        return VigletFieldSpec.builder()
                .name(ov.targetName())
                .type(ov.type() != null ? ov.type() : spec.type())
                .mandatory(spec.mandatory())
                .multiValued(spec.multiValued())
                .description(spec.description())
                .facet(spec.facet())
                .facetName(spec.facetName())
                .build();
    }

    private static Map<String, TurMigrationOverride> indexByField(List<TurMigrationOverride> overrides) {
        Map<String, TurMigrationOverride> byField = new LinkedHashMap<>();
        for (TurMigrationOverride ov : overrides) {
            if (ov != null && ov.field() != null) {
                byField.put(ov.field(), ov);
            }
        }
        return byField;
    }
}
