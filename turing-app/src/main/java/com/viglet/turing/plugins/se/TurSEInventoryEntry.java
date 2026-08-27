/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.plugins.se;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.viglet.turing.commons.sn.field.TurSNFieldName;

/**
 * T1029 / §XX.16 — one row of the index inventory: what the search engine
 * currently holds for a document, reduced to the two facts an external audit
 * needs to compare the index against its source.
 *
 * <p><b>id</b> is the document id exactly as indexed — the key the connector
 * used when it published, so both sides join on it without translation.
 *
 * <p><b>type</b> is the indexed {@code type} field (nullable). It is not part
 * of the comparison; it is carried so a caller auditing a mixed site can bucket
 * the differences by content type without a second round-trip.
 *
 * <p><b>version</b> is epoch milliseconds, normalised across engines (Solr
 * stores a {@code Date}, Lucene a {@code long}, Elasticsearch an ISO-8601
 * string). {@code null} means the document carries none of the
 * {@link #VERSION_FIELDS}, in which case a caller can still detect
 * present/absent differences but not a stale version.
 *
 * <p><b>versionField</b> names which of the {@link #VERSION_FIELDS} the version
 * was read from, so a caller comparing against its own source timestamp knows
 * whether it is looking at a modification or a publication date. {@code null}
 * whenever {@code version} is.
 *
 * <p>The index holds no checksum — {@code TurSNJobItem.checksum} reaches the
 * indexing log and never the document — so the version is the strongest
 * per-document identity Turing can honestly report today.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurSEInventoryEntry(String id, String type, Long version, String versionField) {

    /**
     * The document fields consulted for a version, in precedence order: a
     * modification date describes the revision, a publication date is the
     * fallback for sources that only stamp one date.
     */
    public static final List<String> VERSION_FIELDS =
            List.of(TurSNFieldName.MODIFICATION_DATE, TurSNFieldName.PUBLICATION_DATE);

    /**
     * Builds an entry from a flat field map, resolving the version from the
     * first {@link #VERSION_FIELDS} entry that yields a parsable instant.
     *
     * @param id     the document id (required — callers drop rows without one)
     * @param fields the document's stored fields, as the engine returned them
     */
    public static TurSEInventoryEntry from(String id, Map<String, Object> fields) {
        String type = firstString(fields, TurSNFieldName.TYPE);
        for (String field : VERSION_FIELDS) {
            Long epochMs = toEpochMs(fields.get(field));
            if (epochMs != null) {
                return new TurSEInventoryEntry(id, type, epochMs, field);
            }
        }
        return new TurSEInventoryEntry(id, type, null, null);
    }

    /**
     * Normalises whatever an engine returned for a date field into epoch
     * milliseconds. Returns {@code null} for absent, empty or unparsable
     * values — an unreadable date is reported as "no version", never as 0,
     * which a caller would read as 1970 and treat as a real difference.
     */
    public static Long toEpochMs(Object value) {
        Object single = value instanceof java.util.Collection<?> col
                ? (col.isEmpty() ? null : col.iterator().next())
                : value;
        if (single == null) {
            return null;
        }
        if (single instanceof Date date) {
            return date.getTime();
        }
        if (single instanceof Number number) {
            return number.longValue();
        }
        String text = single.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
            // Not epoch millis — fall through to ISO-8601.
        }
        try {
            return Instant.parse(text).toEpochMilli();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static String firstString(Map<String, Object> fields, String field) {
        if (fields == null) {
            return null;
        }
        Object value = fields.get(field);
        if (value instanceof java.util.Collection<?> col) {
            value = col.isEmpty() ? null : col.iterator().next();
        }
        return value == null ? null : value.toString();
    }
}
