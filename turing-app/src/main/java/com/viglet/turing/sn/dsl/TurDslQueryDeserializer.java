package com.viglet.turing.sn.dsl;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Custom Jackson deserializer for {@link TurDslQuery}.
 * <p>
 * Handles the Elasticsearch Query DSL polymorphic format where the query type
 * is determined by the JSON object key name (e.g. {@code "match"}, {@code "bool"}).
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
public class TurDslQueryDeserializer extends ValueDeserializer<TurDslQuery> {

    @Override
    public TurDslQuery deserialize(JsonParser p, DeserializationContext ctxt) {
        JsonNode node = p.readValueAsTree();
        return deserializeNode(node);
    }

    TurDslQuery deserializeNode(JsonNode node) {
        if (node == null || node.isNull() || node.isEmpty()) {
            return new TurDslQuery.MatchAll();
        }

        String type = node.propertyNames().iterator().next();
        JsonNode body = node.get(type);

        return switch (type) {
            case "match_all" -> new TurDslQuery.MatchAll();
            case "match" -> deserializeMatch(body);
            case "multi_match" -> deserializeMultiMatch(body);
            case "term" -> deserializeTerm(body);
            case "terms" -> deserializeTerms(body);
            case "bool" -> deserializeBool(body);
            case "range" -> deserializeRange(body);
            case "wildcard" -> deserializeSingleField(body, TurDslQuery.Wildcard::new);
            case "prefix" -> deserializeSingleField(body, TurDslQuery.Prefix::new);
            case "exists" -> new TurDslQuery.Exists(body.get("field").asString());
            case "query_string" -> deserializeQueryString(body);
            case "fuzzy" -> deserializeFuzzy(body);
            case "ids" -> deserializeIds(body);
            case "match_phrase" -> deserializeMatchPhrase(body);
            case "match_phrase_prefix" -> deserializeMatchPhrasePrefix(body);
            case "simple_query_string" -> deserializeSimpleQueryString(body);
            case "regexp" -> deserializeSingleFieldWithFlags(body);
            case "constant_score" -> deserializeConstantScore(body);
            case "dis_max" -> deserializeDisMax(body);
            case "boosting" -> deserializeBoosting(body);
            default -> throw new IllegalArgumentException("Unknown query type: " + type);
        };
    }

    private TurDslQuery.Match deserializeMatch(JsonNode body) {
        Map.Entry<String, JsonNode> entry = body.properties().iterator().next();
        String field = entry.getKey();
        JsonNode value = entry.getValue();
        if (value.isObject()) {
            return new TurDslQuery.Match(
                    field,
                    value.get("query").asString(),
                    value.has("operator") ? value.get("operator").asString() : null
            );
        }
        return new TurDslQuery.Match(field, value.asString(), null);
    }

    private TurDslQuery.MultiMatch deserializeMultiMatch(JsonNode body) {
        String query = body.get("query").asString();
        List<String> fields = new ArrayList<>();
        if (body.has("fields")) {
            for (JsonNode f : body.get("fields")) {
                fields.add(f.asString());
            }
        }
        String type = body.has("type") ? body.get("type").asString() : null;
        return new TurDslQuery.MultiMatch(query, fields, type);
    }

    private TurDslQuery.Term deserializeTerm(JsonNode body) {
        Map.Entry<String, JsonNode> entry = body.properties().iterator().next();
        String field = entry.getKey();
        JsonNode value = entry.getValue();
        if (value.isObject() && value.has("value")) {
            return new TurDslQuery.Term(field, nodeToObject(value.get("value")));
        }
        return new TurDslQuery.Term(field, nodeToObject(value));
    }

    private TurDslQuery.Terms deserializeTerms(JsonNode body) {
        Map.Entry<String, JsonNode> entry = body.properties().iterator().next();
        String field = entry.getKey();
        List<Object> values = new ArrayList<>();
        for (JsonNode v : entry.getValue()) {
            values.add(nodeToObject(v));
        }
        return new TurDslQuery.Terms(field, values);
    }

    private TurDslQuery.Bool deserializeBool(JsonNode body) {
        return new TurDslQuery.Bool(
                deserializeQueryList(body.get("must")),
                deserializeQueryList(body.get("should")),
                deserializeQueryList(body.get("must_not")),
                deserializeQueryList(body.get("filter")),
                body.has("minimum_should_match") ? body.get("minimum_should_match").asInt() : null
        );
    }

    private TurDslQuery.Range deserializeRange(JsonNode body) {
        Map.Entry<String, JsonNode> entry = body.properties().iterator().next();
        String field = entry.getKey();
        JsonNode range = entry.getValue();
        return new TurDslQuery.Range(
                field,
                range.has("gte") ? nodeToObject(range.get("gte")) : null,
                range.has("gt") ? nodeToObject(range.get("gt")) : null,
                range.has("lte") ? nodeToObject(range.get("lte")) : null,
                range.has("lt") ? nodeToObject(range.get("lt")) : null
        );
    }

    @FunctionalInterface
    private interface FieldValueFactory<T> {
        T create(String field, String value);
    }

    private <T extends TurDslQuery> T deserializeSingleField(JsonNode body,
                                                              FieldValueFactory<T> factory) {
        Map.Entry<String, JsonNode> entry = body.properties().iterator().next();
        String field = entry.getKey();
        JsonNode value = entry.getValue();
        String text = value.isObject() ? value.get("value").asString() : value.asString();
        return factory.create(field, text);
    }

    private TurDslQuery.QueryString deserializeQueryString(JsonNode body) {
        return new TurDslQuery.QueryString(
                body.get("query").asString(),
                body.has("default_field") ? body.get("default_field").asString() : null,
                body.has("default_operator") ? body.get("default_operator").asString() : null
        );
    }

    private TurDslQuery.Fuzzy deserializeFuzzy(JsonNode body) {
        Map.Entry<String, JsonNode> entry = body.properties().iterator().next();
        String field = entry.getKey();
        JsonNode value = entry.getValue();
        if (value.isObject()) {
            return new TurDslQuery.Fuzzy(
                    field,
                    value.get("value").asString(),
                    value.has("fuzziness") ? value.get("fuzziness").asInt() : null
            );
        }
        return new TurDslQuery.Fuzzy(field, value.asString(), null);
    }

    private TurDslQuery.Ids deserializeIds(JsonNode body) {
        List<String> values = new ArrayList<>();
        if (body.has("values")) {
            for (JsonNode v : body.get("values")) {
                values.add(v.asString());
            }
        }
        return new TurDslQuery.Ids(values);
    }

    // --- Group 2: phrase, string, pattern, compound queries ---

    private TurDslQuery.MatchPhrase deserializeMatchPhrase(JsonNode body) {
        Map.Entry<String, JsonNode> entry = body.properties().iterator().next();
        String field = entry.getKey();
        JsonNode value = entry.getValue();
        if (value.isObject()) {
            return new TurDslQuery.MatchPhrase(field,
                    value.get("query").asString(),
                    value.has("slop") ? value.get("slop").asInt() : null);
        }
        return new TurDslQuery.MatchPhrase(field, value.asString(), null);
    }

    private TurDslQuery.MatchPhrasePrefix deserializeMatchPhrasePrefix(JsonNode body) {
        Map.Entry<String, JsonNode> entry = body.properties().iterator().next();
        String field = entry.getKey();
        JsonNode value = entry.getValue();
        if (value.isObject()) {
            return new TurDslQuery.MatchPhrasePrefix(field,
                    value.get("query").asString(),
                    value.has("max_expansions") ? value.get("max_expansions").asInt() : null);
        }
        return new TurDslQuery.MatchPhrasePrefix(field, value.asString(), null);
    }

    private TurDslQuery.SimpleQueryString deserializeSimpleQueryString(JsonNode body) {
        String query = body.get("query").asString();
        List<String> fields = new ArrayList<>();
        if (body.has("fields")) {
            for (JsonNode f : body.get("fields")) fields.add(f.asString());
        }
        String defaultOperator = body.has("default_operator")
                ? body.get("default_operator").asString() : null;
        return new TurDslQuery.SimpleQueryString(query, fields, defaultOperator);
    }

    private TurDslQuery.Regexp deserializeSingleFieldWithFlags(JsonNode body) {
        Map.Entry<String, JsonNode> entry = body.properties().iterator().next();
        String field = entry.getKey();
        JsonNode value = entry.getValue();
        if (value.isObject()) {
            return new TurDslQuery.Regexp(field,
                    value.get("value").asString(),
                    value.has("flags") ? value.get("flags").asString() : null);
        }
        return new TurDslQuery.Regexp(field, value.asString(), null);
    }

    private TurDslQuery.ConstantScore deserializeConstantScore(JsonNode body) {
        TurDslQuery filter = body.has("filter") ? deserializeNode(body.get("filter")) : new TurDslQuery.MatchAll();
        Double boost = body.has("boost") ? body.get("boost").asDouble() : null;
        return new TurDslQuery.ConstantScore(filter, boost);
    }

    private TurDslQuery.DisMax deserializeDisMax(JsonNode body) {
        List<TurDslQuery> queries = deserializeQueryList(body.get("queries"));
        Double tieBreaker = body.has("tie_breaker") ? body.get("tie_breaker").asDouble() : null;
        return new TurDslQuery.DisMax(queries, tieBreaker);
    }

    private TurDslQuery.Boosting deserializeBoosting(JsonNode body) {
        TurDslQuery positive = body.has("positive") ? deserializeNode(body.get("positive")) : new TurDslQuery.MatchAll();
        TurDslQuery negative = body.has("negative") ? deserializeNode(body.get("negative")) : new TurDslQuery.MatchAll();
        Double negativeBoost = body.has("negative_boost") ? body.get("negative_boost").asDouble() : null;
        return new TurDslQuery.Boosting(positive, negative, negativeBoost);
    }

    private List<TurDslQuery> deserializeQueryList(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (node.isArray()) {
            List<TurDslQuery> queries = new ArrayList<>();
            for (JsonNode item : node) {
                queries.add(deserializeNode(item));
            }
            return queries;
        }
        return List.of(deserializeNode(node));
    }

    private static Object nodeToObject(JsonNode node) {
        if (node.isInt()) return node.asInt();
        if (node.isLong()) return node.asLong();
        if (node.isDouble()) return node.asDouble();
        if (node.isBoolean()) return node.asBoolean();
        return node.asString();
    }
}
