/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.viglet.turing.client.sn.manifest;

import java.math.BigDecimal;
import java.util.Map;

/**
 * T1084 — one bucket of a {@link TurSNManifestCustomFacet}. A numeric field
 * reads {@code rangeStart}/{@code rangeEnd}; a DATE field reads
 * {@code rangeStartDate}/{@code rangeEndDate}, each an ISO date or Solr date
 * math such as {@code NOW-7DAYS}.
 *
 * @param label          the default label (required)
 * @param labels         per-locale labels
 * @param position       order inside the facet; {@code null} means its place in
 *                       the list
 * @param operator       {@code EQUAL}, {@code GREATER_THAN},
 *                       {@code GREATER_THAN_OR_EQUAL}, {@code LESS_THAN},
 *                       {@code LESS_THAN_OR_EQUAL}, {@code BETWEEN_INCLUSIVE}
 *                       (when {@code null}) or {@code BETWEEN_EXCLUSIVE}
 * @param rangeStart     lower bound for a numeric field
 * @param rangeEnd       upper bound for a numeric field
 * @param rangeStartDate lower bound for a DATE field
 * @param rangeEndDate   upper bound for a DATE field
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurSNManifestCustomFacetItem(String label, Map<String, String> labels, Integer position,
        String operator, BigDecimal rangeStart, BigDecimal rangeEnd, String rangeStartDate,
        String rangeEndDate) {

    /** A DATE bucket from {@code start} on, such as {@code since("Last 7 days", "NOW-7DAYS")}. */
    public static TurSNManifestCustomFacetItem since(String label, String start) {
        return dateRange(label, start, null);
    }

    /** A DATE bucket between two bounds, either an ISO date or date math; {@code null} is open. */
    public static TurSNManifestCustomFacetItem dateRange(String label, String start, String end) {
        return new TurSNManifestCustomFacetItem(label, null, null, null, null, null, start, end);
    }

    /** A numeric bucket between two bounds; {@code null} is open. */
    public static TurSNManifestCustomFacetItem range(String label, BigDecimal start, BigDecimal end) {
        return new TurSNManifestCustomFacetItem(label, null, null, null, start, end, null, null);
    }

    /** This bucket with its per-locale labels. */
    public TurSNManifestCustomFacetItem withLabels(Map<String, String> localeLabels) {
        return new TurSNManifestCustomFacetItem(label, localeLabels, position, operator, rangeStart,
                rangeEnd, rangeStartDate, rangeEndDate);
    }
}
