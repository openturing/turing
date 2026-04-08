package com.viglet.turing.sn.dsl;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
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
public class TurDslQueryDeserializer extends JsonDeserializer<TurDslQuery> {

    @Override
    public TurDslQuery deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        return deserializeNode(node);
    }

    private TurDslQuery deserializeNode(JsonNode node) throws IOException {
        if (node == null || node.isNull() || node.isEmpty()) {
            return new TurDslQuery.MatchAll();
        }

        String type = node.fieldNames().next();
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
            case "exists" -> new TurDslQuery.Exists(body.get("field").asText());
            case "query_string" -> deserializeQueryString(body);
            case "fuzzy" -> deserializeFuzzy(body);
            case "ids" -> deserializeIds(body);
            default -> throw new IOException("Unknown query type: " + type);
        };
    }

    private TurDslQuery.Match deserializeMatch(JsonNode body) {
        Map.Entry<String, JsonNode> entry = body.fields().next();
        String field = entry.getKey();
        JsonNode value = entry.getValue();
        if (value.isObject()) {
            return new TurDslQuery.Match(
                    field,
                    value.get("query").asText(),
                    value.has("operator") ? value.get("operator").asText() : null
            );
        }
        return new TurDslQuery.Match(field, value.asText(), null);
    }

    private TurDslQuery.MultiMatch deserializeMultiMatch(JsonNode body) {
        String query = body.get("query").asText();
        List<String> fields = new ArrayList<>();
        if (body.has("fields")) {
            for (JsonNode f : body.get("fields")) {
                fields.add(f.asText());
            }
        }
        String type = body.has("type") ? body.get("type").asText() : null;
        return new TurDslQuery.MultiMatch(query, fields, type);
    }

    private TurDslQuery.Term deserializeTerm(JsonNode body) {
        Map.Entry<String, JsonNode> entry = body.fields().next();
        String field = entry.getKey();
        JsonNode value = entry.getValue();
        if (value.isObject() && value.has("value")) {
            return new TurDslQuery.Term(field, nodeToObject(value.get("value")));
        }
        return new TurDslQuery.Term(field, nodeToObject(value));
    }

    private TurDslQuery.Terms deserializeTerms(JsonNode body) {
        Map.Entry<String, JsonNode> entry = body.fields().next();
        String field = entry.getKey();
        List<Object> values = new ArrayList<>();
        for (JsonNode v : entry.getValue()) {
            values.add(nodeToObject(v));
        }
        return new TurDslQuery.Terms(field, values);
    }

    private TurDslQuery.Bool deserializeBool(JsonNode body) throws IOException {
        return new TurDslQuery.Bool(
                deserializeQueryList(body.get("must")),
                deserializeQueryList(body.get("should")),
                deserializeQueryList(body.get("must_not")),
                deserializeQueryList(body.get("filter")),
                body.has("minimum_should_match") ? body.get("minimum_should_match").asInt() : null
        );
    }

    private TurDslQuery.Range deserializeRange(JsonNode body) {
        Map.Entry<String, JsonNode> entry = body.fields().next();
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
        Map.Entry<String, JsonNode> entry = body.fields().next();
        String field = entry.getKey();
        JsonNode value = entry.getValue();
        String text = value.isObject() ? value.get("value").asText() : value.asText();
        return factory.create(field, text);
    }

    private TurDslQuery.QueryString deserializeQueryString(JsonNode body) {
        return new TurDslQuery.QueryString(
                body.get("query").asText(),
                body.has("default_field") ? body.get("default_field").asText() : null,
                body.has("default_operator") ? body.get("default_operator").asText() : null
        );
    }

    private TurDslQuery.Fuzzy deserializeFuzzy(JsonNode body) {
        Map.Entry<String, JsonNode> entry = body.fields().next();
        String field = entry.getKey();
        JsonNode value = entry.getValue();
        if (value.isObject()) {
            return new TurDslQuery.Fuzzy(
                    field,
                    value.get("value").asText(),
                    value.has("fuzziness") ? value.get("fuzziness").asInt() : null
            );
        }
        return new TurDslQuery.Fuzzy(field, value.asText(), null);
    }

    private TurDslQuery.Ids deserializeIds(JsonNode body) {
        List<String> values = new ArrayList<>();
        if (body.has("values")) {
            for (JsonNode v : body.get("values")) {
                values.add(v.asText());
            }
        }
        return new TurDslQuery.Ids(values);
    }

    private List<TurDslQuery> deserializeQueryList(JsonNode node) throws IOException {
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
        return node.asText();
    }
}
