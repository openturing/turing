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

package com.viglet.turing.sn.facet;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.lucene.TurLuceneFilterQueries;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacetItem;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacetOperatorEnum;

/**
 * Builds a {@link TurSNSiteCustomFacetItem} from the shape both the console and
 * the SN manifest send: numeric bounds for a numeric field, and for a DATE field
 * bounds that are either an ISO date or relative date math ({@code NOW-7DAYS}).
 * One conversion, so a bucket provisioned from code is stored exactly as one
 * saved in the console (T1084).
 *
 * <p>A bound that is neither is refused with {@link IllegalArgumentException}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurSNCustomFacetItems {

    private static final int MAX_EXPRESSION_LENGTH = 64;

    private TurSNCustomFacetItems() {
    }

    /**
     * The item as it is persisted for a field of {@code fieldType}.
     *
     * @param rangeStartDate an ISO date or date math; read only for a DATE field
     * @param rangeEndDate   an ISO date or date math; read only for a DATE field
     */
    @SuppressWarnings("java:S107")
    public static TurSNSiteCustomFacetItem toEntity(String id, String label, Map<String, String> labels,
            Integer position, TurSNSiteCustomFacetOperatorEnum operator, BigDecimal rangeStart,
            BigDecimal rangeEnd, String rangeStartDate, String rangeEndDate, TurSEFieldType fieldType) {
        TurSNSiteCustomFacetItem item = TurSNSiteCustomFacetItem.builder().build();
        item.setId(id);
        item.setLabel(label);
        item.setLabels(new HashMap<>(Optional.ofNullable(labels).orElse(Map.of())));
        item.setPosition(position);
        item.setOperator(Optional.ofNullable(operator)
                .orElse(TurSNSiteCustomFacetOperatorEnum.BETWEEN_INCLUSIVE));

        if (TurSEFieldType.DATE.equals(fieldType)) {
            item.setRangeStart(null);
            item.setRangeEnd(null);
            // T1075 - a bound is either a date or relative date math (NOW-7DAYS)
            item.setRangeStartExpression(dateMathOrNull(rangeStartDate));
            item.setRangeEndExpression(dateMathOrNull(rangeEndDate));
            item.setRangeStartDate(item.getRangeStartExpression() == null
                    ? parseIsoDate(rangeStartDate) : null);
            item.setRangeEndDate(item.getRangeEndExpression() == null
                    ? parseIsoDate(rangeEndDate) : null);
        } else {
            item.setRangeStart(rangeStart);
            item.setRangeEnd(rangeEnd);
            item.setRangeStartDate(null);
            item.setRangeEndDate(null);
            item.setRangeStartExpression(null);
            item.setRangeEndExpression(null);
        }
        return item;
    }

    /**
     * T1075 - the bound as relative date math when it is one, validated by the
     * same parser the Lucene engine reads a filter with; a malformed expression
     * is refused here rather than at the first search.
     */
    static String dateMathOrNull(String value) {
        if (!TurSNCustomFacetFilterQuery.isDateMath(value)) {
            return null;
        }
        String expression = value.trim().toUpperCase(Locale.ROOT);
        if (expression.length() > MAX_EXPRESSION_LENGTH) {
            throw new IllegalArgumentException("Relative date is too long: " + value);
        }
        try {
            TurLuceneFilterQueries.parseDate(expression, Instant.now());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid relative date '" + value
                    + "': use NOW with steps such as NOW-7DAYS or NOW/DAY", e);
        }
        return expression;
    }

    static Instant parseIsoDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            // Fallback to ISO_OFFSET_DATE_TIME below.
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException ignored) {
            // Fallback to ISO_LOCAL_DATE below.
        }
        try {
            return LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException ignored) {
            // Parsed in all accepted ISO formats and failed.
        }
        throw new IllegalArgumentException("Invalid ISO date value: " + value);
    }
}
