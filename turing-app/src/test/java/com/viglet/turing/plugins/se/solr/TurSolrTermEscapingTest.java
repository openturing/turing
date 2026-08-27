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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * T1039 / §CVI.4 — a value that must reach Solr as one term is not the same
 * problem as free text, and conflating them truncated a sweep.
 *
 * <p>An unescaped space in a range bound makes {@code id:{my doc TO *]}, which
 * Solr rejects; the rejection returns an empty result, and a cursor sweep reads
 * an empty page as "fully enumerated". So an export silently dropped every
 * document from that id onward and the T1029 audit reported them missing from the
 * index — blaming the source for what was never read.
 *
 * <p>These assertions are on the constructed string on purpose. The failure they
 * guard does not throw, so nothing downstream would have noticed.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurSolrTermEscapingTest {

    @Test
    @DisplayName("A space in a term is escaped, so it stays one term")
    void whitespaceIsEscapedInATerm() {
        assertThat(TurSolrSearchEnginePlugin.escapeSolrTerm("my doc"))
                .isEqualTo("my\\ doc");
    }

    @ParameterizedTest
    @DisplayName("Every kind of whitespace ends a term, so every kind is escaped")
    @CsvSource(delimiter = '|', value = {
            "'a b'|'a\\ b'",
            "'a\tb'|'a\\\tb'",
            "'a\nb'|'a\\\nb'",
    })
    void allWhitespaceIsEscaped(String raw, String expected) {
        assertThat(TurSolrSearchEnginePlugin.escapeSolrTerm(raw)).isEqualTo(expected);
    }

    @Test
    @DisplayName("The Lucene specials are still escaped, and only once")
    void specialsAreStillEscapedOnce() {
        // A path-like AEM id: the slashes and the colon were already handled; the
        // space is what T1039 added.
        assertThat(TurSolrSearchEnginePlugin.escapeSolrTerm("/content/wknd/ski touring"))
                .isEqualTo("\\/content\\/wknd\\/ski\\ touring");
        assertThat(TurSolrSearchEnginePlugin.escapeSolrTerm("a+b"))
                .isEqualTo("a\\+b");
        assertThat(TurSolrSearchEnginePlugin.escapeSolrTerm("a:b"))
                .isEqualTo("a\\:b");
    }

    @Test
    @DisplayName("A term with no special character is left exactly as it is")
    void plainTermIsUntouched() {
        assertThat(TurSolrSearchEnginePlugin.escapeSolrTerm("doc-00042"))
                .isEqualTo("doc-00042".replace("-", "\\-"));
        assertThat(TurSolrSearchEnginePlugin.escapeSolrTerm("plaintext"))
                .isEqualTo("plaintext");
    }

    @Test
    @DisplayName("The cursor filter is an exclusive lower bound, and its bound is one term")
    void cursorFilterIsAnExclusiveSingleTermBound() {
        assertThat(TurSolrSearchEnginePlugin.idAfterFilterQuery("doc1"))
                .isEqualTo("id:{doc1 TO *]");
        // The brace and bracket are the range syntax and must stay literal; only
        // the bound is escaped.
        assertThat(TurSolrSearchEnginePlugin.idAfterFilterQuery("my doc"))
                .isEqualTo("id:{my\\ doc TO *]")
                .startsWith("id:{")
                .endsWith(" TO *]");
    }

    @Test
    @DisplayName("A path-like id with a space produces a bound Solr can read")
    void pathLikeCursorIsWellFormed() {
        String filter = TurSolrSearchEnginePlugin
                .idAfterFilterQuery("/content/wknd/ski touring mont blanc");

        assertThat(filter).isEqualTo(
                "id:{\\/content\\/wknd\\/ski\\ touring\\ mont\\ blanc TO *]");
        // The only unescaped space left is the one separating TO from the bounds.
        assertThat(filter.replace("\\ ", "")).containsOnlyOnce(" TO *]");
    }
}
