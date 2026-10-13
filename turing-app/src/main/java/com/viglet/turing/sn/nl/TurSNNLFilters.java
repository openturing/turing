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

package com.viglet.turing.sn.nl;

import java.util.List;

/**
 * The filters a natural-language request names, as data a search can use
 * (T1085): what {@code POST /api/sn/{site}/nl-filters} answers.
 *
 * <p>{@code q} and {@code fq} are exactly what the SN search API takes, so a site
 * turns "part-time near the subway in Sao Paulo" into its own ranked, faceted
 * page with one search call. The {@code fq} values are the same shape the chat
 * conversation takes as {@code filters}.
 *
 * @param available {@code false} when no LLM can parse the text; {@code q} is then
 *                  the text itself and nothing is filtered
 * @param text      the request as sent
 * @param q         what is left to match as free text once the filters are taken out
 * @param filters   one entry per field, with the words each came from
 * @param fq        the filter queries for the search API: {@code field:value} per
 *                  value, {@code field:[start TO end]} per range
 * @param dropped   values the parser proposed that the site does not hold, with why
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurSNNLFilters(boolean available, String text, String q, List<Filter> filters,
        List<String> fq, List<Dropped> dropped) {

    /** Nothing parsed: the whole text stays the query. */
    public static TurSNNLFilters unavailable(String text) {
        return new TurSNNLFilters(false, text, text, List.of(), List.of(), List.of());
    }

    /**
     * One field's filter.
     *
     * @param field       the site field
     * @param values      the values, spelled as the site indexes them; empty for a range
     * @param rangeStart  the range's lower bound, {@code null} when open or not a range
     * @param rangeEnd    the range's upper bound, {@code null} when open or not a range
     * @param sourceWords the words of the text each value or bound was read from
     * @param confidence  1 when every value appears in the text, lower when the
     *                    parser inferred it (a synonym, a translation)
     * @param grounded    {@code true} when the values were checked against the
     *                    site's own; {@code false} when the engine could not list them
     */
    public record Filter(String field, List<String> values, String rangeStart, String rangeEnd,
            List<String> sourceWords, double confidence, boolean grounded) {

        public boolean isRange() {
            return values.isEmpty();
        }
    }

    /**
     * A value left out of the filters.
     *
     * @param field  the field it was proposed for
     * @param value  the value as the parser spelled it
     * @param reason {@code UNKNOWN_FIELD} or {@code NOT_IN_SITE}
     */
    public record Dropped(String field, String value, String reason) {
    }
}
