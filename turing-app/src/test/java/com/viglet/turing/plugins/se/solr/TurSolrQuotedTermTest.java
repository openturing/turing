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
package com.viglet.turing.plugins.se.solr;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T1040 / §CVI.5 — the two things about a quoted Solr term that only reading the
 * string can tell you: the quotes are there, and what is inside them is escaped
 * for a quoted context (backslash and quote, and nothing else).
 *
 * <p>Deliberately narrow. Its predecessor asserted the <em>shape</em> of a
 * backslash-escaped bound and passed while Solr rejected the query outright —
 * which is what {@link TurSolrCursorPagingIT} exists to catch and this class
 * cannot. Whether a bound actually selects the right documents is a question for
 * the engine, and it is asked there.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurSolrQuotedTermTest {

    @Test
    @DisplayName("A term is wrapped in quotes, so whitespace inside it is not a separator")
    void termIsQuoted() {
        assertThat(TurSolrSearchEnginePlugin.solrQuotedTerm("my doc")).isEqualTo("\"my doc\"");
        assertThat(TurSolrSearchEnginePlugin.solrQuotedTerm("plain")).isEqualTo("\"plain\"");
    }

    @Test
    @DisplayName("Inside quotes only the quote and the backslash need escaping")
    void onlyQuoteAndBackslashAreEscaped() {
        assertThat(TurSolrSearchEnginePlugin.solrQuotedTerm("say \"hi\""))
                .isEqualTo("\"say \\\"hi\\\"\"");
        assertThat(TurSolrSearchEnginePlugin.solrQuotedTerm("a\\b"))
                .isEqualTo("\"a\\\\b\"");
        // The Lucene specials are literal inside quotes; escaping them there would
        // put a backslash into the value being matched.
        assertThat(TurSolrSearchEnginePlugin.solrQuotedTerm("/content/a-b:c+d"))
                .isEqualTo("\"/content/a-b:c+d\"");
    }

    @Test
    @DisplayName("The backslash is escaped before the quote, so an escape is never doubled wrongly")
    void escapingOrderIsSafe() {
        // A trailing backslash must not end up escaping the closing quote.
        assertThat(TurSolrSearchEnginePlugin.solrQuotedTerm("ends\\"))
                .isEqualTo("\"ends\\\\\"");
    }

    @Test
    @DisplayName("The cursor filter keeps the range syntax literal and quotes only the bound")
    void cursorFilterQuotesOnlyTheBound() {
        assertThat(TurSolrSearchEnginePlugin.idAfterFilterQuery("my doc"))
                .isEqualTo("id:{\"my doc\" TO *]")
                .startsWith("id:{\"")
                .endsWith("\" TO *]");
    }
}
