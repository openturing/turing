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
package com.viglet.testsupport.contentai;

import static com.viglet.testsupport.contentai.TurContentAiSpecValidator.SEARCH_REQUEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T1048 / §XX.25 — the guard on the guard.
 *
 * <p>A validator that silently accepts everything is the defect it was written to
 * remove, one level up. So this asserts it <em>rejects</em> the body T1031 actually
 * shipped, and each other bound the spec declares that Turing could plausibly
 * breach — and that it still accepts the bodies the code emits today.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurContentAiSpecValidatorTest {

    private static final String SOURCE = """
            "contentSource": {"name": "content-ai-articles-index", "type": "ACQUISITION"}
            """;

    private static String body(String query) {
        return "{" + SOURCE + ", \"query\": " + query + "}";
    }

    @Test
    @DisplayName("It validates against the pinned spec version, not whatever is live")
    void pinsTheSpecVersion() {
        // Refreshing the bundle should be a decision somebody makes and reads a diff
        // for; the API is experimental and dated.
        assertThat(TurContentAiSpecValidator.specVersion()).isEqualTo("2026.07-experimental");
    }

    // ---- the bug this exists for -----------------------------------------

    @Test
    @DisplayName("It rejects T1031's empty FilterGroup, the body that shipped for four tasks")
    void rejectsTheEmptyFilterGroup() {
        String shipped = body("""
                {"type": "filter", "filter": {"filterComponent":
                  {"type": "FilterGroup", "operator": "AND", "filters": []}}}
                """);

        var violations = TurContentAiSpecValidator.violations(shipped, SEARCH_REQUEST);

        // If this list is ever empty, the validator has stopped following the
        // discriminator into FilterGroup and is checking nothing that matters.
        assertThat(violations).isNotEmpty();
        assertThat(TurContentAiSpecValidator.render(violations)).contains("filters");
    }

    @Test
    @DisplayName("It accepts the NOT-exists match-all that replaced it")
    void acceptsTheSentinelMatchAll() {
        String current = body("""
                {"type": "filter", "filter": {"filterComponent":
                  {"type": "FilterGroup", "operator": "NOT", "filters": [
                    {"type": "ExistsFilter", "field": "__turing_match_all__"}]}}}
                """);

        TurContentAiSpecValidator.assertValid(current, SEARCH_REQUEST);
    }

    // ---- the other bounds a body could breach ----------------------------

    @Test
    @DisplayName("It rejects a pagination limit above the API's 50")
    void rejectsAnOversizedLimit() {
        String over = "{" + SOURCE + """
                , "query": {"type": "fulltext", "text": "cars"},
                  "queryOptions": {"pagination": {"limit": 500}}}
                """;

        assertThat(TurContentAiSpecValidator.violations(over, SEARCH_REQUEST)).isNotEmpty();
    }

    @Test
    @DisplayName("It rejects a blank text on a scoring query")
    void rejectsBlankQueryText() {
        assertThat(TurContentAiSpecValidator.violations(
                body("""
                        {"type": "fulltext", "text": ""}
                        """), SEARCH_REQUEST)).isNotEmpty();
    }

    @Test
    @DisplayName("It rejects a composite with fewer than two arms")
    void rejectsADegenerateComposite() {
        assertThat(TurContentAiSpecValidator.violations(
                body("""
                        {"type": "composite", "operator": "OR", "queries": [
                          {"type": "fulltext", "text": "cars"}]}
                        """), SEARCH_REQUEST)).isNotEmpty();
    }

    @Test
    @DisplayName("It rejects a request naming no content source")
    void rejectsAMissingContentSource() {
        assertThat(TurContentAiSpecValidator.violations(
                "{\"query\": {\"type\": \"fulltext\", \"text\": \"cars\"}}", SEARCH_REQUEST))
                        .isNotEmpty();
    }

    @Test
    @DisplayName("It rejects an unknown filter type, so a typo is not silently sent")
    void rejectsAnUnknownFilterType() {
        assertThat(TurContentAiSpecValidator.violations(
                body("""
                        {"type": "filter", "filter": {"filterComponent":
                          {"type": "StringFiltre", "field": "a", "value": "b", "operator": "EQ"}}}
                        """), SEARCH_REQUEST)).isNotEmpty();
    }

    // ---- the shapes the code emits today ---------------------------------

    @Test
    @DisplayName("It accepts the hybrid composite the plugin sends")
    void acceptsTheHybridComposite() {
        TurContentAiSpecValidator.assertValid(body("""
                {"type": "composite", "operator": "OR", "queries": [
                  {"type": "vector", "text": "electric cars",
                   "options": {"boost": 1.0, "chunks": {"includeInResponse": true,
                     "scoreMode": "MAX"}}},
                  {"type": "fulltext", "text": "electric cars", "options": {"boost": 1.0}}]}
                """), SEARCH_REQUEST);
    }

    @Test
    @DisplayName("It accepts a facet FilterGroup and a termBuckets aggregate")
    void acceptsFacetsAndAggregates() {
        String withFacets = "{" + SOURCE + """
                , "query": {"type": "filter", "filter": {"filterComponent":
                    {"type": "FilterGroup", "operator": "AND", "filters": [
                      {"type": "StringFilter", "field": "category", "value": "news",
                       "operator": "EQ"},
                      {"type": "InFilter", "field": "tags", "values": ["a", "b"]}]}}},
                  "queryOptions": {"pagination": {"limit": 10}},
                  "aggregates": [{"type": "termBuckets", "id": "category",
                    "field": {"name": "category"}, "size": 10}]}
                """;

        TurContentAiSpecValidator.assertValid(withFacets, SEARCH_REQUEST);
    }

    @Test
    @DisplayName("It accepts the executor's range translation and its date bound")
    void acceptsRangeTranslations() {
        TurContentAiSpecValidator.assertValid(body("""
                {"type": "filter", "filter": {"filterComponent":
                  {"type": "FilterGroup", "operator": "AND", "filters": [
                    {"type": "NumberFilter", "field": "price", "value": 10.0,
                     "operator": "GTE"},
                    {"type": "DateFilter", "field": "createdAt", "value": "2026-01-01T00:00:00Z",
                     "operator": "LTE"}]}}}
                """), SEARCH_REQUEST);
    }

    @Test
    @DisplayName("assertValid names the schema, the spec version and every violation")
    void failureMessageIsActionable() {
        assertThatThrownBy(() -> TurContentAiSpecValidator.assertValid(
                body("""
                        {"type": "filter", "filter": {"filterComponent":
                          {"type": "FilterGroup", "operator": "AND", "filters": []}}}
                        """), SEARCH_REQUEST))
                                .isInstanceOf(AssertionError.class)
                                .hasMessageContaining(SEARCH_REQUEST)
                                .hasMessageContaining("2026.07-experimental")
                                .hasMessageContaining("filters");
    }
}
