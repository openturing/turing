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

import com.viglet.turing.commons.sn.field.TurSNFieldName;

/**
 * Engine-agnostic transform of a source record (a parsed JSON object) into an SN
 * job-item attribute map, shared by the Elasticsearch (T657) and Algolia (T658)
 * importers (§XXXVIII).
 *
 * <p>Object sub-fields are flattened to dotted names ({@code parent.child}) to
 * match how the manifest builders derive their field names, so a document
 * attribute and its manifest field always share a key. Per the grounding contract
 * on {@link com.viglet.turing.client.sn.job.TurSNJobItem#getAttributes()}, an
 * absent value is dropped entirely — {@code null}, blank strings, and empty
 * collections never become empty index values. A list of nested objects collapses
 * into per-leaf multi-valued lists.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurMigrationDocumentMapper {

    private TurMigrationDocumentMapper() {
        // static-only
    }

    /**
     * Reshapes one source record into an indexable attribute map.
     *
     * @param id       the document id (ES {@code _id} / Algolia {@code objectID}).
     * @param source   the record body (already free of the id field).
     * @param type     the value stored under {@code type} (typically the index name).
     * @param provider the value stored under {@code source_apps} (the importer name).
     */
    public static Map<String, Object> toAttributes(String id, Map<String, Object> source, String type,
            String provider) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        if (source != null) {
            flatten("", source, attributes);
        }
        if (id != null && !id.isBlank()) {
            attributes.put(TurSNFieldName.ID, id);
        }
        if (type != null && !type.isBlank()) {
            attributes.put(TurSNFieldName.TYPE, type);
        }
        if (provider != null && !provider.isBlank()) {
            attributes.put(TurSNFieldName.SOURCE_APPS, provider);
        }
        return attributes;
    }

    private static void flatten(String prefix, Map<String, Object> source, Map<String, Object> out) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            put(key, entry.getValue(), out);
        }
    }

    @SuppressWarnings("unchecked")
    private static void put(String key, Object value, Map<String, Object> out) {
        if (isAbsent(value)) {
            return;
        }
        if (value instanceof Map<?, ?> nested) {
            flatten(key, (Map<String, Object>) nested, out);
            return;
        }
        if (value instanceof List<?> list) {
            putList(key, list, out);
            return;
        }
        out.put(key, value);
    }

    @SuppressWarnings("unchecked")
    private static void putList(String key, List<?> list, Map<String, Object> out) {
        List<Object> scalars = new ArrayList<>();
        for (Object element : list) {
            if (isAbsent(element)) {
                continue;
            }
            if (element instanceof Map<?, ?> nestedMap) {
                // A list of nested objects: flatten each into a scratch map, then
                // merge every produced leaf into a multi-valued list on the parent key.
                Map<String, Object> scratch = new LinkedHashMap<>();
                flatten(key, (Map<String, Object>) nestedMap, scratch);
                scratch.forEach((leafKey, leafValue) -> mergeLeaf(leafKey, leafValue, out));
            } else if (element instanceof List<?> innerList) {
                putList(key, innerList, out); // flatten nested arrays of scalars
            } else {
                scalars.add(element);
            }
        }
        if (!scalars.isEmpty()) {
            mergeLeaf(key, scalars.size() == 1 ? scalars.get(0) : scalars, out);
        }
    }

    @SuppressWarnings("unchecked")
    private static void mergeLeaf(String key, Object value, Map<String, Object> out) {
        Object existing = out.get(key);
        if (existing == null) {
            out.put(key, value);
            return;
        }
        List<Object> merged = new ArrayList<>();
        if (existing instanceof List<?> existingList) {
            merged.addAll((List<Object>) existingList);
        } else {
            merged.add(existing);
        }
        if (value instanceof List<?> valueList) {
            merged.addAll((List<Object>) valueList);
        } else {
            merged.add(value);
        }
        out.put(key, merged);
    }

    private static boolean isAbsent(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String s) {
            return s.isBlank();
        }
        if (value instanceof List<?> list) {
            return list.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        return false;
    }
}
