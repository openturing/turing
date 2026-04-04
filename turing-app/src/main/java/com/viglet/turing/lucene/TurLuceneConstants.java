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

/**
 * Constants for the Lucene search engine plugin.
 *
 * @author Alexandre Oliveira
 * @since 2026.1
 */
public final class TurLuceneConstants {

    private TurLuceneConstants() {
        throw new IllegalStateException("Lucene Constants class");
    }

    public static final String ID = "id";
    public static final String TYPE = "type";
    public static final String TITLE = "title";
    public static final String URL = "url";
    public static final String SCORE = "score";
    public static final String VERSION = "_version_";
    public static final String BOOST = "boost";
    public static final String TURING_ENTITY = "turing_entity_";

    public static final String NEWEST = "newest";
    public static final String OLDEST = "oldest";
    public static final String SOLR_DATE_PATTERN = "yyyy-MM-dd'T'HH:mm:ss'Z'";

    /** Suffix for autocomplete ngram field. */
    public static final String AC_SUFFIX = "_ac";

    /** Minimum ngram length for autocomplete. */
    public static final int AC_MIN_GRAM = 2;

    /** Maximum ngram length for autocomplete. */
    public static final int AC_MAX_GRAM = 20;

    /** Suffix for SortedSetDocValuesFacetField (faceting support). */
    public static final String FACET_DIM_CONFIG = "$facets";

    /**
     * Suffix appended to LONG and DATE field names when storing SortedSetDocValues
     * for faceting, to avoid conflict with their NumericDocValuesField.
     * e.g. field "price" → facet DocValues stored under "price_sf".
     */
    public static final String SORTED_SET_FACET_SUFFIX = "_sf";
}
