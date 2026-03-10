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
package com.viglet.turing.lucene;

import static com.viglet.turing.lucene.TurLuceneConstants.*;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteField;
import com.viglet.turing.sn.field.TurSNSiteFieldService;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.document.*;
import org.apache.lucene.index.Term;
import org.apache.lucene.util.BytesRef;
import org.json.JSONArray;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.IntStream;

/**
 * Handles document indexing and de-indexing operations against a Lucene index.
 * Mirrors {@code TurSolrDocumentHandler} for the Lucene engine.
 *
 * @author Alexandre Oliveira
 * @since 2026.1
 */
@Slf4j
public class TurLuceneDocumentHandler {

    private final TurSNSiteFieldService turSNSiteFieldService;

    public TurLuceneDocumentHandler(TurSNSiteFieldService turSNSiteFieldService) {
        this.turSNSiteFieldService = turSNSiteFieldService;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void indexing(TurLuceneInstance instance, TurSNSite turSNSite, Map<String, Object> attributes) {
        log.debug("Lucene indexing ...");
        Map<String, Object> cleaned = new LinkedHashMap<>(attributes);
        cleaned.remove(SCORE);
        cleaned.remove(VERSION);
        cleaned.remove(BOOST);
        addDocument(instance, turSNSite, cleaned);
    }

    public void deIndexing(TurLuceneInstance instance, String id) {
        log.debug("Lucene deIndexing id={}", id);
        try {
            instance.getWriter().deleteDocuments(new Term(ID, id));
            instance.getWriter().commit();
        } catch (IOException e) {
            log.error("Error deIndexing id={}", id, e);
        }
    }

    public void deIndexingByType(TurLuceneInstance instance, String type) {
        log.debug("Lucene deIndexing type={}", type);
        try {
            instance.getWriter().deleteDocuments(new Term(TYPE, type));
            instance.getWriter().commit();
        } catch (IOException e) {
            log.error("Error deIndexing type={}", type, e);
        }
    }

    // -------------------------------------------------------------------------
    // Document building
    // -------------------------------------------------------------------------

    private void addDocument(TurLuceneInstance instance, TurSNSite turSNSite, Map<String, Object> attributes) {
        Map<String, TurSNSiteField> fieldMap = turSNSiteFieldService.toMap(turSNSite);

        Document doc = new Document();
        Optional.ofNullable(attributes).ifPresent(attr ->
                attr.forEach((key, value) -> {
                    if (value == null) return;
                    TurSNSiteField siteField = fieldMap.get(key);
                    TurSEFieldType fieldType = siteField != null ? siteField.getType() : TurSEFieldType.STRING;
                    boolean multiValued = siteField != null && siteField.getMultiValued() == 1;

                    addAttributeToDocument(doc, key, value, fieldType, multiValued);
                }));

        String docId = Optional.ofNullable(attributes.get(ID))
                .map(Object::toString)
                .orElse(UUID.randomUUID().toString());
        try {
            instance.getWriter().updateDocument(new Term(ID, docId), doc);
            instance.getWriter().commit();
        } catch (IOException e) {
            log.error("Error writing Lucene document id={}", docId, e);
        }
    }

    // -------------------------------------------------------------------------
    // Field-type dispatch
    // -------------------------------------------------------------------------

    private void addAttributeToDocument(Document doc, String key, Object value,
            TurSEFieldType fieldType, boolean multiValued) {
        switch (value) {
            case JSONArray jsonArray -> addJsonArrayToDocument(doc, key, jsonArray, fieldType, multiValued);
            case ArrayList<?> list -> addListToDocument(doc, key, list, fieldType, multiValued);
            default -> addSingleValueToDocument(doc, key, value, fieldType);
        }
    }

    private void addJsonArrayToDocument(Document doc, String key, JSONArray jsonArray,
            TurSEFieldType fieldType, boolean multiValued) {
        if (jsonArray.isEmpty()) return;
        if (multiValued || key.startsWith(TURING_ENTITY)) {
            IntStream.range(0, jsonArray.length())
                    .mapToObj(jsonArray::get)
                    .forEach(item -> addSingleValueToDocument(doc, key, item, fieldType));
        } else {
            addSingleValueToDocument(doc, key, jsonArray.get(0), fieldType);
        }
    }

    private void addListToDocument(Document doc, String key, List<?> list,
            TurSEFieldType fieldType, boolean multiValued) {
        if (list.isEmpty()) return;
        if (multiValued || key.startsWith(TURING_ENTITY)) {
            list.forEach(item -> addSingleValueToDocument(doc, key, item, fieldType));
        } else {
            addSingleValueToDocument(doc, key, list.getFirst(), fieldType);
        }
    }

    private void addSingleValueToDocument(Document doc, String key, Object value,
            TurSEFieldType fieldType) {
        String strValue = value != null ? value.toString().trim() : "";
        if (strValue.isEmpty()) return;

        switch (fieldType) {
            case TEXT -> addTextField(doc, key, strValue);
            case STRING, ARRAY, BOOL -> addStringField(doc, key, strValue);
            case INT -> addIntField(doc, key, strValue);
            case LONG -> addLongField(doc, key, strValue);
            case FLOAT -> addFloatField(doc, key, strValue);
            case DOUBLE, CURRENCY -> addDoubleField(doc, key, strValue);
            case DATE -> addDateField(doc, key, strValue);
        }
    }

    private void addTextField(Document doc, String key, String strValue) {
        doc.add(new TextField(key, strValue, Field.Store.YES));
        addFacetField(doc, key, strValue);
    }

    private void addStringField(Document doc, String key, String strValue) {
        doc.add(new StringField(key, strValue, Field.Store.YES));
        addFacetField(doc, key, strValue);
    }

    private void addIntField(Document doc, String key, String strValue) {
        try {
            int intVal = Integer.parseInt(strValue);
            doc.add(new IntPoint(key, intVal));
            doc.add(new StoredField(key, intVal));
        } catch (NumberFormatException e) {
            log.warn("Cannot parse INT value '{}' for field '{}'", strValue, key);
            doc.add(new StringField(key, strValue, Field.Store.YES));
        }
        addFacetField(doc, key, strValue);
    }

    private void addLongField(Document doc, String key, String strValue) {
        try {
            long longVal = Long.parseLong(strValue);
            doc.add(new LongPoint(key, longVal));
            doc.add(new StoredField(key, longVal));
            doc.add(new NumericDocValuesField(key, longVal));
        } catch (NumberFormatException e) {
            log.warn("Cannot parse LONG value '{}' for field '{}'", strValue, key);
            doc.add(new StringField(key, strValue, Field.Store.YES));
        }
        // Use _sf suffix to avoid conflict with NumericDocValuesField on same field name
        addFacetField(doc, key + SORTED_SET_FACET_SUFFIX, strValue);
    }

    private void addFloatField(Document doc, String key, String strValue) {
        try {
            float floatVal = Float.parseFloat(strValue);
            doc.add(new FloatPoint(key, floatVal));
            doc.add(new StoredField(key, floatVal));
        } catch (NumberFormatException e) {
            log.warn("Cannot parse FLOAT value '{}' for field '{}'", strValue, key);
            doc.add(new StringField(key, strValue, Field.Store.YES));
        }
        addFacetField(doc, key, strValue);
    }

    private void addDoubleField(Document doc, String key, String strValue) {
        // CURRENCY is stored as its numeric amount (comma-separated ISO 4217 handled externally)
        String numPart = strValue.contains(",") ? strValue.substring(0, strValue.lastIndexOf(',')) : strValue;
        try {
            double doubleVal = Double.parseDouble(numPart);
            doc.add(new DoublePoint(key, doubleVal));
            doc.add(new StoredField(key, doubleVal));
        } catch (NumberFormatException e) {
            log.warn("Cannot parse DOUBLE/CURRENCY value '{}' for field '{}'", strValue, key);
            doc.add(new StringField(key, strValue, Field.Store.YES));
        }
        addFacetField(doc, key, strValue);
    }

    private void addDateField(Document doc, String key, String strValue) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(SOLR_DATE_PATTERN);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            long epochMs = sdf.parse(strValue).getTime();
            doc.add(new LongPoint(key, epochMs));
            doc.add(new StoredField(key, epochMs));
            doc.add(new NumericDocValuesField(key, epochMs));
        } catch (ParseException e) {
            log.warn("Cannot parse DATE value '{}' for field '{}'", strValue, key);
            doc.add(new StringField(key, strValue, Field.Store.YES));
        }
        // Use _sf suffix to avoid conflict with NumericDocValuesField on same field name
        addFacetField(doc, key + SORTED_SET_FACET_SUFFIX, strValue);
    }

    private void addFacetField(Document doc, String fieldName, String value) {
        // Truncate very long values to avoid BytesRef limit (32766 bytes)
        String truncated = value.length() > 500 ? value.substring(0, 500) : value;
        doc.add(new SortedSetDocValuesField(fieldName, new BytesRef(truncated)));
    }
}
