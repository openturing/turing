package com.viglet.turing.sn.dsl;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.List;

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
}
