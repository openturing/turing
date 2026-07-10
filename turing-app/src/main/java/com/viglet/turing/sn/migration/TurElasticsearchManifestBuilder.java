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
import com.viglet.core.manifest.VigletFieldType;

/**
 * Pure, deterministic transform of an Elasticsearch {@code _mapping} into a set of
 * neutral manifest field specs (T657 / §XXXVIII.1).
 *
 * <p>This is the defensible core of the ES importer: an ES type maps cleanly onto
 * a {@link VigletFieldType} ({@code text}&rarr;{@code TEXT},
 * {@code keyword}&rarr;{@code STRING}, numeric/date/boolean &rarr; the matching
 * type), a {@code keyword}-family or {@code boolean} field becomes a navigable
 * facet, and {@code object}/{@code nested} properties recurse into dotted field
 * names ({@code parent.child}). Because {@code _mapping} never declares whether a
 * field holds a collection, cardinality is inferred from a sample of documents.
 * Unsupported types (geo, vector, completion, …) are skipped with a warning rather
 * than silently dropped.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurElasticsearchManifestBuilder {

    /** Guards against pathological / recursive mappings. */
    private static final int MAX_DEPTH = 8;

    private TurElasticsearchManifestBuilder() {
        // static-only
    }

    /**
     * The derived fields plus any non-fatal notes (skipped field types, etc.).
     */
    public record Result(List<VigletFieldSpec> fields, List<String> warnings) {
    }

    /**
     * Builds field specs from an ES {@code _mapping} response body.
     *
     * @param mappingResponse the parsed JSON of {@code GET /{index}/_mapping} (or
     *                        the inner {@code mappings}/{@code properties} object).
     * @param sampleSources   a sample of document {@code _source} maps used only to
     *                        detect multi-valued fields; may be empty.
     */
    public static Result build(Map<String, Object> mappingResponse, List<Map<String, Object>> sampleSources) {
        List<VigletFieldSpec> fields = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, Object> properties = extractProperties(mappingResponse);
        if (properties.isEmpty()) {
            warnings.add("No mapping properties found in the Elasticsearch response");
            return new Result(fields, warnings);
        }
        collect(properties, "", 0,
                sampleSources == null ? List.of() : sampleSources, fields, warnings);
        return new Result(fields, warnings);
    }

    /** Descends {@code {index: {mappings: {properties: ...}}}} to the properties map. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractProperties(Map<String, Object> root) {
        if (root == null) {
            return Map.of();
        }
        if (root.get("properties") instanceof Map<?, ?> props) {
            return (Map<String, Object>) props;
        }
        if (root.get("mappings") instanceof Map<?, ?> mappings) {
            return extractProperties((Map<String, Object>) mappings);
        }
        // {index-name: {mappings: {...}}}
        for (Object value : root.values()) {
            if (value instanceof Map<?, ?> child
                    && (child.containsKey("mappings") || child.containsKey("properties"))) {
                return extractProperties((Map<String, Object>) child);
            }
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private static void collect(Map<String, Object> properties, String prefix, int depth,
            List<Map<String, Object>> samples, List<VigletFieldSpec> out, List<String> warnings) {
        if (depth > MAX_DEPTH) {
            warnings.add("Skipped fields under '" + prefix + "': mapping nesting exceeds depth " + MAX_DEPTH);
            return;
        }
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String name = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            if (!(entry.getValue() instanceof Map<?, ?> defRaw)) {
                continue;
            }
            Map<String, Object> def = (Map<String, Object>) defRaw;
            String esType = asString(def.get("type"));
            Map<String, Object> childProps = def.get("properties") instanceof Map<?, ?> p
                    ? (Map<String, Object>) p
                    : null;

            if (childProps != null && (esType == null || "object".equals(esType) || "nested".equals(esType))) {
                collect(childProps, name, depth + 1, samples, out, warnings);
                continue;
            }
            if (esType == null) {
                warnings.add("Skipped field '" + name + "': no Elasticsearch type declared");
                continue;
            }
            VigletFieldType type = mapType(esType);
            if (type == null) {
                warnings.add("Skipped field '" + name + "': unsupported Elasticsearch type '" + esType + "'");
                continue;
            }
            out.add(VigletFieldSpec.builder()
                    .name(name)
                    .type(type)
                    .facet(isFacetable(esType))
                    .multiValued(detectMultiValued(name, samples))
                    .description("Imported from Elasticsearch (" + esType + ")")
                    .build());
        }
    }

    /** Maps an Elasticsearch field type to a neutral logical type; {@code null} = unsupported. */
    private static VigletFieldType mapType(String esType) {
        return switch (esType) {
            case "text", "match_only_text", "annotated_text", "search_as_you_type" -> VigletFieldType.TEXT;
            case "keyword", "constant_keyword", "wildcard", "ip", "version" -> VigletFieldType.STRING;
            case "integer", "short", "byte" -> VigletFieldType.INT;
            case "long", "unsigned_long" -> VigletFieldType.LONG;
            case "float", "half_float" -> VigletFieldType.FLOAT;
            case "double", "scaled_float" -> VigletFieldType.DOUBLE;
            case "boolean" -> VigletFieldType.BOOL;
            case "date", "date_nanos" -> VigletFieldType.DATE;
            default -> null;
        };
    }

    /** Term-facetable in practice: the keyword family and booleans. */
    private static boolean isFacetable(String esType) {
        return switch (esType) {
            case "keyword", "constant_keyword", "wildcard", "boolean" -> true;
            default -> false;
        };
    }

    /** A field is multi-valued if any sampled document holds it (or a parent) as a list. */
    private static boolean detectMultiValued(String dottedName, List<Map<String, Object>> samples) {
        for (Map<String, Object> source : samples) {
            if (pathHoldsList(source, dottedName.split("\\."), 0)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static boolean pathHoldsList(Object node, String[] segments, int index) {
        if (index >= segments.length || !(node instanceof Map<?, ?> map)) {
            return false;
        }
        Object value = ((Map<String, Object>) map).get(segments[index]);
        if (value == null) {
            return false;
        }
        boolean leaf = index == segments.length - 1;
        if (leaf) {
            return value instanceof List<?>;
        }
        if (value instanceof List<?> list) {
            // list of nested objects: descend into each element
            for (Object element : list) {
                if (pathHoldsList(element, segments, index + 1)) {
                    return true;
                }
            }
            return true; // the parent itself is a collection of objects
        }
        return pathHoldsList(value, segments, index + 1);
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    /** Convenience for callers that want the field specs keyed by name. */
    public static Map<String, VigletFieldSpec> byName(List<VigletFieldSpec> fields) {
        Map<String, VigletFieldSpec> map = new LinkedHashMap<>();
        for (VigletFieldSpec field : fields) {
            map.put(field.name(), field);
        }
        return map;
    }
}
