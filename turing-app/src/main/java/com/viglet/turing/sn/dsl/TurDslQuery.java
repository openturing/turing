package com.viglet.turing.sn.dsl;

import tools.jackson.databind.annotation.JsonDeserialize;

import java.util.List;
import java.util.Map;

/**
 * Sealed interface representing all supported Query DSL clause types,
 * following the Elasticsearch Query DSL pattern.
 * <p>
 * Each query type is modeled as an inner record, enabling pattern matching
 * in the translator layer for type-safe query conversion to Solr syntax.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@JsonDeserialize(using = TurDslQueryDeserializer.class)
public sealed interface TurDslQuery {

    record MatchAll() implements TurDslQuery {}

    record Match(String field, String query, String operator) implements TurDslQuery {}

    record MultiMatch(String query, List<String> fields, String type) implements TurDslQuery {}

    record Term(String field, Object value) implements TurDslQuery {}

    record Terms(String field, List<Object> values) implements TurDslQuery {}

    record Bool(
            List<TurDslQuery> must,
            List<TurDslQuery> should,
            List<TurDslQuery> mustNot,
            List<TurDslQuery> filter,
            Integer minimumShouldMatch
    ) implements TurDslQuery {}

    record Range(String field, Object gte, Object gt, Object lte, Object lt) implements TurDslQuery {}

    record Wildcard(String field, String value) implements TurDslQuery {}

    record Prefix(String field, String value) implements TurDslQuery {}

    record Exists(String field) implements TurDslQuery {}

    record QueryString(String query, String defaultField, String defaultOperator) implements TurDslQuery {}

    record Fuzzy(String field, String value, Integer fuzziness) implements TurDslQuery {}

    record Ids(List<String> values) implements TurDslQuery {}

    // --- Group 2: phrase, string, pattern queries ---

    record MatchPhrase(String field, String query, Integer slop) implements TurDslQuery {}

    record MatchPhrasePrefix(String field, String query, Integer maxExpansions) implements TurDslQuery {}

    record SimpleQueryString(String query, List<String> fields, String defaultOperator) implements TurDslQuery {}

    record Regexp(String field, String value, String flags) implements TurDslQuery {}

    record ConstantScore(TurDslQuery filter, Double boost) implements TurDslQuery {}

    // --- Group 3: compound scoring queries ---

    record DisMax(List<TurDslQuery> queries, Double tieBreaker) implements TurDslQuery {}

    record Boosting(TurDslQuery positive, TurDslQuery negative, Double negativeBoost) implements TurDslQuery {}

    // --- Group 4: nested, scoring, vector queries ---

    record Nested(String path, TurDslQuery query, String scoreMode) implements TurDslQuery {}

    record FunctionScore(TurDslQuery query, List<ScoreFunction> functions,
                         String scoreMode, String boostMode,
                         Double maxBoost, Double minScore) implements TurDslQuery {}

    record ScriptScore(TurDslQuery query, Script script, Double minScore) implements TurDslQuery {}

    record Knn(String field, List<Double> queryVector, int k,
               int numCandidates, Double similarity,
               TurDslQuery filter) implements TurDslQuery {}

    // --- Group 5: medium-priority queries ---

    record MoreLikeThis(List<String> fields, String likeText, List<String> likeIds,
                         Integer minTermFreq, Integer minDocFreq,
                         Integer maxQueryTerms) implements TurDslQuery {}

    record CombinedFields(String query, List<String> fields,
                           String operator) implements TurDslQuery {}

    record MatchBoolPrefix(String field, String query) implements TurDslQuery {}

    record Pinned(List<String> ids, TurDslQuery organic) implements TurDslQuery {}

    record GeoDistance(String field, String distance,
                       GeoPoint location) implements TurDslQuery {}

    record GeoBoundingBox(String field, GeoPoint topLeft,
                           GeoPoint bottomRight) implements TurDslQuery {}

    record TermsSet(String field, List<Object> terms,
                     String minimumShouldMatchField,
                     Script minimumShouldMatchScript) implements TurDslQuery {}

    record GeoPoint(double lat, double lon) {}

    // --- Group 6: low-priority queries ---

    record HasChild(String type, TurDslQuery query, String scoreMode,
                     Integer minChildren, Integer maxChildren) implements TurDslQuery {}

    record HasParent(String parentType, TurDslQuery query,
                      Boolean score) implements TurDslQuery {}

    record Intervals(String field, IntervalsRule rule) implements TurDslQuery {}

    record SpanTerm(String field, String value) implements TurDslQuery {}

    record SpanNear(List<TurDslQuery> clauses, Integer slop,
                     Boolean inOrder) implements TurDslQuery {}

    record SpanOr(List<TurDslQuery> clauses) implements TurDslQuery {}

    record SpanNot(TurDslQuery include, TurDslQuery exclude) implements TurDslQuery {}

    record SpanFirst(TurDslQuery match, Integer end) implements TurDslQuery {}

    record RankFeature(String field, Double boost,
                        String saturation, String log, String sigmoid) implements TurDslQuery {}

    record DistanceFeature(String field, String origin, String pivot) implements TurDslQuery {}

    record Wrapper(String query) implements TurDslQuery {}

    record GeoShape(String field, java.util.Map<String, Object> shape,
                     String relation) implements TurDslQuery {}

    record Percolate(String field, java.util.Map<String, Object> document) implements TurDslQuery {}

    // --- Supporting records ---

    record IntervalsRule(String type, String query, String analyzer,
                          Integer maxGaps, Boolean ordered,
                          List<IntervalsRule> intervals) {}

    // --- Supporting records for function_score ---

    record ScoreFunction(TurDslQuery filter, Double weight,
                         FieldValueFactor fieldValueFactor,
                         DecayFunction linear, DecayFunction exp, DecayFunction gauss,
                         Script scriptScore) {}

    record FieldValueFactor(String field, Double factor, String modifier, Double missing) {}

    record DecayFunction(String field, String origin, String scale,
                         String offset, Double decay) {}

    record Script(String source, String lang, java.util.Map<String, Object> params) {}
}
