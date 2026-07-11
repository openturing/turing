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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.viglet.core.manifest.VigletFieldSpec;
import com.viglet.core.manifest.VigletFieldType;

/**
 * Pure, deterministic overlay of Algolia index <em>settings</em> onto a set of
 * base field specs derived from sample records (T658 / §XXXVIII.2).
 *
 * <p>Algolia has no strict field-type mapping, so type/cardinality come from
 * sample-record derivation (the T387 kernel heuristics, or an LLM). This builder
 * then layers what the settings <em>do</em> tell us:</p>
 * <ul>
 *   <li>{@code attributesForFaceting} &rarr; {@code facet:true} (unwrapping
 *       {@code filterOnly(x)} / {@code searchable(x)} / {@code afterDistinct(x)}).</li>
 *   <li>{@code searchableAttributes} &rarr; ensured present as {@code TEXT} when a
 *       sample never surfaced them (unwrapping {@code unordered(x)} / {@code ordered(x)}
 *       and splitting comma-grouped priorities).</li>
 *   <li>{@code customRanking} attributes &rarr; ensured present (unwrapping
 *       {@code asc(x)} / {@code desc(x)}).</li>
 * </ul>
 *
 * <p>{@code objectID} is never a manifest field — it becomes the SN document id.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurAlgoliaManifestBuilder {

    /** Matches an Algolia modifier wrapper like {@code unordered(name)} or {@code filterOnly(price)}. */
    private static final Pattern MODIFIER = Pattern.compile("^\\w+\\(([^)]+)\\)$");

    private static final String OBJECT_ID = "objectID";

    private TurAlgoliaManifestBuilder() {
        // static-only
    }

    /** The overlaid fields plus any non-fatal notes. */
    public record Result(List<VigletFieldSpec> fields, List<String> warnings) {
    }

    /**
     * Overlays Algolia settings onto base field specs.
     *
     * @param baseFields fields derived from sample records (heuristics or LLM).
     * @param settings   the parsed JSON of the Algolia index settings.
     */
    public static Result build(List<VigletFieldSpec> baseFields, Map<String, Object> settings) {
        List<String> warnings = new ArrayList<>();
        Map<String, VigletFieldSpec> byName = new LinkedHashMap<>();
        for (VigletFieldSpec spec : baseFields == null ? List.<VigletFieldSpec>of() : baseFields) {
            if (spec != null && spec.name() != null && !OBJECT_ID.equals(spec.name())) {
                byName.put(spec.name(), spec);
            }
        }

        Set<String> facetAttrs = parseAttributes(settings.get("attributesForFaceting"));
        Set<String> searchableAttrs = parseAttributes(settings.get("searchableAttributes"));
        Set<String> rankingAttrs = parseAttributes(settings.get("customRanking"));

        // 1) Facets: flip facet:true on every attributesForFaceting entry.
        for (String attr : facetAttrs) {
            VigletFieldSpec existing = byName.get(attr);
            if (existing != null) {
                byName.put(attr, withFacet(existing, true));
            } else {
                byName.put(attr, VigletFieldSpec.builder()
                        .name(attr).type(VigletFieldType.STRING).facet(true)
                        .description("Algolia facet attribute (not seen in sample)").build());
                warnings.add("Facet attribute '" + attr + "' not present in the sample; added as STRING");
            }
        }

        // 2) Searchable attributes absent from the sample: add as free text.
        for (String attr : searchableAttrs) {
            if (!byName.containsKey(attr)) {
                byName.put(attr, VigletFieldSpec.builder()
                        .name(attr).type(VigletFieldType.TEXT).facet(false)
                        .description("Algolia searchable attribute (not seen in sample)").build());
                warnings.add("Searchable attribute '" + attr + "' not present in the sample; added as TEXT");
            }
        }

        // 3) customRanking attributes absent from the sample: surface them.
        for (String attr : rankingAttrs) {
            if (!byName.containsKey(attr)) {
                byName.put(attr, VigletFieldSpec.builder()
                        .name(attr).type(VigletFieldType.STRING).facet(false)
                        .description("Algolia customRanking attribute (not seen in sample)").build());
                warnings.add("customRanking attribute '" + attr + "' not present in the sample; added as STRING");
            }
        }

        return new Result(new ArrayList<>(byName.values()), warnings);
    }

    private static VigletFieldSpec withFacet(VigletFieldSpec spec, boolean facet) {
        if (spec.facet() == facet) {
            return spec;
        }
        return VigletFieldSpec.builder()
                .name(spec.name()).type(spec.type()).mandatory(spec.mandatory())
                .multiValued(spec.multiValued()).description(spec.description())
                .facet(facet).facetName(spec.facetName()).build();
    }

    /**
     * Parses an Algolia attribute list into bare attribute names — unwrapping any
     * {@code modifier(name)} form and splitting comma-grouped priorities.
     */
    @SuppressWarnings("unchecked")
    static Set<String> parseAttributes(Object raw) {
        Set<String> names = new LinkedHashSet<>();
        if (!(raw instanceof List<?> list)) {
            return names;
        }
        for (Object entry : (List<Object>) list) {
            if (entry == null) {
                continue;
            }
            for (String part : entry.toString().split(",")) {
                String name = unwrap(part.trim());
                if (!name.isBlank() && !OBJECT_ID.equals(name)) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    private static String unwrap(String token) {
        Matcher matcher = MODIFIER.matcher(token);
        return matcher.matches() ? matcher.group(1).trim() : token;
    }
}
