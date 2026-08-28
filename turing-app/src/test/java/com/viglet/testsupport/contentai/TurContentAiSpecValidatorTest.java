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
import static org.assertj.core.api.Assertions.assertThatCode;
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

    /**
     * T1049 — the three bounds nothing in the translation enforced. Each is
     * asserted here so the guard added for it is load-bearing rather than
     * decorative: if a bound stops being checked, this fails before the guard's
     * own test starts passing for the wrong reason.
     */
    @Test
    @DisplayName("It rejects a composite with more than ten arms")
    void rejectsAnOverfullComposite() {
        StringBuilder arms = new StringBuilder();
        for (int i = 0; i <= 10; i++) {
            arms.append(i == 0 ? "" : ",")
                    .append("{\"type\": \"fulltext\", \"text\": \"term %d\"}".formatted(i));
        }

        assertThat(TurContentAiSpecValidator.violations(
                body("{\"type\": \"composite\", \"operator\": \"OR\", \"queries\": ["
                        + arms + "]}"),
                SEARCH_REQUEST)).isNotEmpty();
    }

    @Test
    @DisplayName("It rejects more than fifty aggregates")
    void rejectsTooManyAggregates() {
        StringBuilder aggregates = new StringBuilder();
        for (int i = 0; i <= 50; i++) {
            aggregates.append(i == 0 ? "" : ",").append(aggregate("agg_" + i, "field_" + i, 5));
        }

        assertThat(TurContentAiSpecValidator.violations(withAggregates(aggregates.toString()),
                SEARCH_REQUEST)).isNotEmpty();
    }

    @Test
    @DisplayName("It rejects a term bucket size above the API's 2000")
    void rejectsAnOversizedBucketCount() {
        assertThat(TurContentAiSpecValidator.violations(
                withAggregates(aggregate("by_category", "category", 9000)),
                SEARCH_REQUEST)).isNotEmpty();
    }

    @Test
    @DisplayName("It accepts fifty aggregates each at the 2000 ceiling")
    void acceptsTheAggregateCeilings() {
        StringBuilder aggregates = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            aggregates.append(i == 0 ? "" : ",").append(aggregate("agg_" + i, "field_" + i, 2000));
        }

        TurContentAiSpecValidator.assertValid(withAggregates(aggregates.toString()),
                SEARCH_REQUEST);
    }

    // ---- the reply half of the contract (T1050) --------------------------

    @Test
    @DisplayName("It rejects a result missing the id, score or data the schema requires")
    void rejectsAnIncompleteResult() {
        assertThat(TurContentAiSpecValidator.violations("""
                {"totalResults": 1, "results": [{"id": "doc_1", "score": 0.5}]}
                """, TurContentAiSpecValidator.SEARCH_RESPONSE)).isNotEmpty();
    }

    @Test
    @DisplayName("It rejects a response with no totalResults, which the mapper reads as a count")
    void rejectsAMissingTotal() {
        assertThat(TurContentAiSpecValidator.violations("{\"results\": []}",
                TurContentAiSpecValidator.SEARCH_RESPONSE)).isNotEmpty();
    }

    @Test
    @DisplayName("It rejects an aggregate with no field, which every real one carries")
    void rejectsAnAggregateWithNoField() {
        assertThat(TurContentAiSpecValidator.violations("""
                {"totalResults": 0, "results": [], "aggregates": [
                  {"type": "termBuckets", "id": "by_category",
                   "buckets": [{"label": "news", "count": 2}]}]}
                """, TurContentAiSpecValidator.SEARCH_RESPONSE)).isNotEmpty();
    }

    /**
     * The bundle's one self-contradiction: {@code TermBucketResultData} requires
     * {@code values} and defines {@code buckets}. Both keys are accepted and
     * neither is not, which is the reading {@code TurContentAiResponseMapper}
     * already implements. If Adobe reconciles the two, one of these three fails
     * and the exception in the validator is what to revisit.
     */
    @Test
    @DisplayName("A term bucket result may carry 'buckets' or 'values', but not neither")
    void acceptsEitherBucketKey() {
        TurContentAiSpecValidator.assertValid(bucketResult("\"buckets\": [] "),
                TurContentAiSpecValidator.SEARCH_RESPONSE);
        TurContentAiSpecValidator.assertValid(bucketResult("\"values\": [] "),
                TurContentAiSpecValidator.SEARCH_RESPONSE);

        assertThat(TurContentAiSpecValidator.violations(bucketResult("\"id\": \"by_category\""),
                TurContentAiSpecValidator.SEARCH_RESPONSE)).isNotEmpty();
    }

    private static String bucketResult(String bucketKey) {
        return """
                {"totalResults": 0, "results": [], "aggregates": [
                  {"type": "termBuckets", "id": "by_category", "field": "category", %s}]}
                """.formatted(bucketKey);
    }

    // ---- the transform's own coverage (T1054) ----------------------------

    /**
     * The transform is what makes every other test here mean anything, and it had
     * nothing asserting it — both holes found in it surfaced as a
     * {@code StackOverflowError} and a silently dropped branch when a third
     * endpoint was added. These four hold it to the bundle it actually loaded, so
     * a refresh fails here rather than several tasks later.
     */
    @Test
    @DisplayName("Every discriminated parent in the bundle was rewritten, none skipped")
    void everyDiscriminatedParentIsFlattened() {
        var report = TurContentAiSpecValidator.transformReport();

        assertThat(report.discriminated()).isNotEmpty();
        assertThat(report.flattened())
                .as("a parent left unflattened is one the validator descends no further than")
                .containsExactlyInAnyOrderElementsOf(report.discriminated());
    }

    @Test
    @DisplayName("No discriminator mapping names a schema the bundle does not define")
    void noMappingTargetIsMissing() {
        // A dropped branch is a legitimate body refused with no clue why. It used
        // to be a bare `continue`; it is now recorded so this can refuse it.
        assertThat(TurContentAiSpecValidator.transformReport().missingTargets()).isEmpty();
    }

    @Test
    @DisplayName("The self-mapped types are inlined, which is the StackOverflow T1052 hit")
    void selfMappedTypesAreInlined() {
        assertThat(TurContentAiSpecValidator.transformReport().inlinedSelf())
                .contains("PublicFieldConfig.BOOLEAN", "PublicFieldConfig.DOUBLE",
                        "PublicFieldConfig.LONG", "PublicFieldConfig.DATE");
    }

    @Test
    @DisplayName("Every rewritten parent validates without recursing")
    void everyRewrittenParentIsUsable() {
        // The check that would have caught T1052's StackOverflowError before an
        // endpoint did: an empty object against each parent exercises every branch
        // of its oneOf, so a branch containing itself blows up here.
        for (String parent : TurContentAiSpecValidator.transformReport().flattened()) {
            assertThatCode(() -> TurContentAiSpecValidator.violations("{}", parent))
                    .as("%s recurses", parent)
                    .doesNotThrowAnyException();
        }
    }

    // ---- the second endpoint (T1052) -------------------------------------

    @Test
    @DisplayName("It accepts a content-source page carrying a config block")
    void acceptsASourceListing() {
        TurContentAiSpecValidator.assertValid("""
                {"items": [
                  {"name": "content-ai-articles-index", "type": "ACQUISITION",
                   "config": {
                     "vectorSpaces": {"semantic": {"default": true}},
                     "lexicalSpaces": {"fulltext": {"default": true, "fields": [
                       {"field": "category", "type": "STRING", "filter": {"enabled": true}}
                     ]}}
                   }},
                  {"name": "aem-assets", "type": "AEM_PUBLISH", "config": {}}
                ]}
                """, TurContentAiSpecValidator.SOURCE_LIST);
    }

    @Test
    @DisplayName("It rejects a listing with no items, which the catalog reads as a page")
    void rejectsAListingWithNoItems() {
        assertThat(TurContentAiSpecValidator.violations("{\"cursor\": \"c1\"}",
                TurContentAiSpecValidator.SOURCE_LIST)).isNotEmpty();
    }

    @Test
    @DisplayName("It rejects a source with no name, which the catalog keys everything by")
    void rejectsANamelessSource() {
        assertThat(TurContentAiSpecValidator.violations(
                "{\"items\": [{\"type\": \"ACQUISITION\"}]}",
                TurContentAiSpecValidator.SOURCE_LIST)).isNotEmpty();
    }

    private static String aggregate(String id, String field, int size) {
        return """
                {"type": "termBuckets", "id": "%s", "field": {"name": "%s"}, "size": %d}"""
                .formatted(id, field, size);
    }

    private static String withAggregates(String aggregates) {
        return "{" + SOURCE + ", \"query\": {\"type\": \"fulltext\", \"text\": \"cars\"}"
                + ", \"aggregates\": [" + aggregates + "]}";
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
