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
            case "nested" -> deserializeNested(body);
            case "function_score" -> deserializeFunctionScore(body);
            case "script_score" -> deserializeScriptScore(body);
            case "knn" -> deserializeKnn(body);
            case "more_like_this" -> deserializeMoreLikeThis(body);
            case "combined_fields" -> deserializeCombinedFields(body);
            case "match_bool_prefix" -> deserializeMatchBoolPrefix(body);
            case "pinned" -> deserializePinned(body);
            case "geo_distance" -> deserializeGeoDistance(body);
            case "geo_bounding_box" -> deserializeGeoBoundingBox(body);
            case "terms_set" -> deserializeTermsSet(body);
            case "has_child" -> new TurDslQuery.HasChild(
                    body.get("type").asString(),
                    body.has("query") ? deserializeNode(body.get("query")) : new TurDslQuery.MatchAll(),
                    body.has("score_mode") ? body.get("score_mode").asString() : null,
                    body.has("min_children") ? body.get("min_children").asInt() : null,
                    body.has("max_children") ? body.get("max_children").asInt() : null);
            case "has_parent" -> new TurDslQuery.HasParent(
                    body.get("parent_type").asString(),
                    body.has("query") ? deserializeNode(body.get("query")) : new TurDslQuery.MatchAll(),
                    body.has("score") ? body.get("score").asBoolean() : null);
            case "intervals" -> {
                var entry = body.properties().iterator().next();
                yield new TurDslQuery.Intervals(entry.getKey(),
                        deserializeIntervalsRule(entry.getValue()));
            }
            case "span_term" -> {
                var entry = body.properties().iterator().next();
                yield new TurDslQuery.SpanTerm(entry.getKey(),
                        entry.getValue().isObject()
                                ? entry.getValue().get("value").asString()
                                : entry.getValue().asString());
            }
            case "span_near" -> new TurDslQuery.SpanNear(
                    deserializeQueryList(body.get("clauses")),
                    body.has("slop") ? body.get("slop").asInt() : null,
                    body.has("in_order") ? body.get("in_order").asBoolean() : null);
            case "span_or" -> new TurDslQuery.SpanOr(
                    deserializeQueryList(body.get("clauses")));
            case "span_not" -> new TurDslQuery.SpanNot(
                    body.has("include") ? deserializeNode(body.get("include")) : new TurDslQuery.MatchAll(),
                    body.has("exclude") ? deserializeNode(body.get("exclude")) : new TurDslQuery.MatchAll());
            case "span_first" -> new TurDslQuery.SpanFirst(
                    body.has("match") ? deserializeNode(body.get("match")) : new TurDslQuery.MatchAll(),
                    body.has("end") ? body.get("end").asInt() : null);
            case "rank_feature" -> new TurDslQuery.RankFeature(
                    body.get("field").asString(),
                    body.has("boost") ? body.get("boost").asDouble() : null,
                    null, null, null);
            case "distance_feature" -> new TurDslQuery.DistanceFeature(
                    body.get("field").asString(),
                    body.has("origin") ? body.get("origin").asString() : null,
                    body.has("pivot") ? body.get("pivot").asString() : null);
            case "wrapper" -> new TurDslQuery.Wrapper(body.get("query").asString());
            case "geo_shape" -> {
                var entry = body.properties().iterator().next();
                JsonNode spec = entry.getValue();
                java.util.Map<String, Object> shape = null;
                if (spec.has("shape") && spec.get("shape").isObject()) {
                    shape = new java.util.LinkedHashMap<>();
                    for (var p : spec.get("shape").properties())
                        shape.put(p.getKey(), nodeToObject(p.getValue()));
                }
                yield new TurDslQuery.GeoShape(entry.getKey(), shape,
                        spec.has("relation") ? spec.get("relation").asString() : null);
            }
            case "percolate" -> {
                java.util.Map<String, Object> doc = new java.util.LinkedHashMap<>();
                if (body.has("document") && body.get("document").isObject()) {
                    for (var p : body.get("document").properties())
                        doc.put(p.getKey(), nodeToObject(p.getValue()));
                }
                yield new TurDslQuery.Percolate(
                        body.has("field") ? body.get("field").asString() : null, doc);
            }
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

    // --- Group 4: nested, scoring, vector queries ---

    private TurDslQuery.Nested deserializeNested(JsonNode body) {
        String path = body.get("path").asString();
        TurDslQuery query = body.has("query") ? deserializeNode(body.get("query")) : new TurDslQuery.MatchAll();
        String scoreMode = body.has("score_mode") ? body.get("score_mode").asString() : null;
        return new TurDslQuery.Nested(path, query, scoreMode);
    }

    private TurDslQuery.FunctionScore deserializeFunctionScore(JsonNode body) {
        TurDslQuery query = body.has("query") ? deserializeNode(body.get("query")) : new TurDslQuery.MatchAll();
        List<TurDslQuery.ScoreFunction> functions = new ArrayList<>();
        if (body.has("functions") && body.get("functions").isArray()) {
            for (JsonNode fn : body.get("functions")) {
                functions.add(deserializeScoreFunction(fn));
            }
        }
        return new TurDslQuery.FunctionScore(query, functions,
                body.has("score_mode") ? body.get("score_mode").asString() : null,
                body.has("boost_mode") ? body.get("boost_mode").asString() : null,
                body.has("max_boost") ? body.get("max_boost").asDouble() : null,
                body.has("min_score") ? body.get("min_score").asDouble() : null);
    }

    private TurDslQuery.ScoreFunction deserializeScoreFunction(JsonNode fn) {
        TurDslQuery filter = fn.has("filter") ? deserializeNode(fn.get("filter")) : null;
        Double weight = fn.has("weight") ? fn.get("weight").asDouble() : null;
        TurDslQuery.FieldValueFactor fvf = fn.has("field_value_factor")
                ? deserializeFieldValueFactor(fn.get("field_value_factor")) : null;
        TurDslQuery.DecayFunction linear = fn.has("linear") ? deserializeDecay(fn.get("linear")) : null;
        TurDslQuery.DecayFunction exp = fn.has("exp") ? deserializeDecay(fn.get("exp")) : null;
        TurDslQuery.DecayFunction gauss = fn.has("gauss") ? deserializeDecay(fn.get("gauss")) : null;
        TurDslQuery.Script scriptScore = fn.has("script_score") && fn.get("script_score").has("script")
                ? deserializeScript(fn.get("script_score").get("script")) : null;
        return new TurDslQuery.ScoreFunction(filter, weight, fvf, linear, exp, gauss, scriptScore);
    }

    private TurDslQuery.FieldValueFactor deserializeFieldValueFactor(JsonNode n) {
        return new TurDslQuery.FieldValueFactor(
                n.get("field").asString(),
                n.has("factor") ? n.get("factor").asDouble() : null,
                n.has("modifier") ? n.get("modifier").asString() : null,
                n.has("missing") ? n.get("missing").asDouble() : null);
    }

    private TurDslQuery.DecayFunction deserializeDecay(JsonNode n) {
        var entry = n.properties().iterator().next();
        String field = entry.getKey();
        JsonNode spec = entry.getValue();
        return new TurDslQuery.DecayFunction(field,
                spec.has("origin") ? spec.get("origin").asString() : null,
                spec.has("scale") ? spec.get("scale").asString() : null,
                spec.has("offset") ? spec.get("offset").asString() : null,
                spec.has("decay") ? spec.get("decay").asDouble() : null);
    }

    private TurDslQuery.Script deserializeScript(JsonNode n) {
        String source = n.has("source") ? n.get("source").asString() : null;
        String lang = n.has("lang") ? n.get("lang").asString() : null;
        java.util.Map<String, Object> params = null;
        if (n.has("params") && n.get("params").isObject()) {
            params = new java.util.LinkedHashMap<>();
            for (var prop : n.get("params").properties()) {
                params.put(prop.getKey(), nodeToObject(prop.getValue()));
            }
        }
        return new TurDslQuery.Script(source, lang, params);
    }

    private TurDslQuery.ScriptScore deserializeScriptScore(JsonNode body) {
        TurDslQuery query = body.has("query") ? deserializeNode(body.get("query")) : new TurDslQuery.MatchAll();
        TurDslQuery.Script script = body.has("script") ? deserializeScript(body.get("script")) : null;
        Double minScore = body.has("min_score") ? body.get("min_score").asDouble() : null;
        return new TurDslQuery.ScriptScore(query, script, minScore);
    }

    private TurDslQuery.Knn deserializeKnn(JsonNode body) {
        String field = body.get("field").asString();
        List<Double> queryVector = new ArrayList<>();
        if (body.has("query_vector")) {
            for (JsonNode v : body.get("query_vector")) queryVector.add(v.asDouble());
        }
        int k = body.has("k") ? body.get("k").asInt() : 10;
        int numCandidates = body.has("num_candidates") ? body.get("num_candidates").asInt() : 100;
        Double similarity = body.has("similarity") ? body.get("similarity").asDouble() : null;
        TurDslQuery filter = body.has("filter") ? deserializeNode(body.get("filter")) : null;
        return new TurDslQuery.Knn(field, queryVector, k, numCandidates, similarity, filter);
    }

    // --- Group 5: medium-priority queries ---

    private TurDslQuery.MoreLikeThis deserializeMoreLikeThis(JsonNode body) {
        List<String> fields = new ArrayList<>();
        if (body.has("fields")) for (JsonNode f : body.get("fields")) fields.add(f.asString());
        String likeText = null;
        List<String> likeIds = new ArrayList<>();
        if (body.has("like")) {
            JsonNode like = body.get("like");
            if (like.isString()) likeText = like.asString();
            else if (like.isArray()) {
                for (JsonNode l : like) {
                    if (l.isString()) { if (likeText == null) likeText = l.asString(); }
                    else if (l.isObject() && l.has("_id")) likeIds.add(l.get("_id").asString());
                }
            }
        }
        return new TurDslQuery.MoreLikeThis(fields, likeText, likeIds,
                body.has("min_term_freq") ? body.get("min_term_freq").asInt() : null,
                body.has("min_doc_freq") ? body.get("min_doc_freq").asInt() : null,
                body.has("max_query_terms") ? body.get("max_query_terms").asInt() : null);
    }

    private TurDslQuery.CombinedFields deserializeCombinedFields(JsonNode body) {
        String query = body.get("query").asString();
        List<String> fields = new ArrayList<>();
        if (body.has("fields")) for (JsonNode f : body.get("fields")) fields.add(f.asString());
        String operator = body.has("operator") ? body.get("operator").asString() : null;
        return new TurDslQuery.CombinedFields(query, fields, operator);
    }

    private TurDslQuery.MatchBoolPrefix deserializeMatchBoolPrefix(JsonNode body) {
        var entry = body.properties().iterator().next();
        String field = entry.getKey();
        JsonNode value = entry.getValue();
        String query = value.isObject() ? value.get("query").asString() : value.asString();
        return new TurDslQuery.MatchBoolPrefix(field, query);
    }

    private TurDslQuery.Pinned deserializePinned(JsonNode body) {
        List<String> ids = new ArrayList<>();
        if (body.has("ids")) for (JsonNode id : body.get("ids")) ids.add(id.asString());
        TurDslQuery organic = body.has("organic") ? deserializeNode(body.get("organic")) : null;
        return new TurDslQuery.Pinned(ids, organic);
    }

    private TurDslQuery.GeoDistance deserializeGeoDistance(JsonNode body) {
        String distance = body.has("distance") ? body.get("distance").asString() : null;
        // Field is any key that isn't "distance"
        String field = null;
        TurDslQuery.GeoPoint location = null;
        for (var prop : body.properties()) {
            if (!"distance".equals(prop.getKey())) {
                field = prop.getKey();
                location = parseGeoPoint(prop.getValue());
                break;
            }
        }
        return new TurDslQuery.GeoDistance(field, distance, location);
    }

    private TurDslQuery.GeoBoundingBox deserializeGeoBoundingBox(JsonNode body) {
        var entry = body.properties().iterator().next();
        String field = entry.getKey();
        JsonNode box = entry.getValue();
        TurDslQuery.GeoPoint topLeft = box.has("top_left") ? parseGeoPoint(box.get("top_left")) : null;
        TurDslQuery.GeoPoint bottomRight = box.has("bottom_right") ? parseGeoPoint(box.get("bottom_right")) : null;
        return new TurDslQuery.GeoBoundingBox(field, topLeft, bottomRight);
    }

    private TurDslQuery.TermsSet deserializeTermsSet(JsonNode body) {
        var entry = body.properties().iterator().next();
        String field = entry.getKey();
        JsonNode spec = entry.getValue();
        List<Object> terms = new ArrayList<>();
        if (spec.has("terms")) for (JsonNode t : spec.get("terms")) terms.add(nodeToObject(t));
        String msmField = spec.has("minimum_should_match_field")
                ? spec.get("minimum_should_match_field").asString() : null;
        TurDslQuery.Script msmScript = spec.has("minimum_should_match_script")
                ? deserializeScript(spec.get("minimum_should_match_script")) : null;
        return new TurDslQuery.TermsSet(field, terms, msmField, msmScript);
    }

    private TurDslQuery.GeoPoint parseGeoPoint(JsonNode node) {
        if (node.isObject()) {
            return new TurDslQuery.GeoPoint(
                    node.has("lat") ? node.get("lat").asDouble() : 0,
                    node.has("lon") ? node.get("lon").asDouble() : 0);
        }
        if (node.isArray()) {
            // GeoJSON: [lon, lat]
            double lon = node.size() > 0 ? node.get(0).asDouble() : 0;
            double lat = node.size() > 1 ? node.get(1).asDouble() : 0;
            return new TurDslQuery.GeoPoint(lat, lon);
        }
        // String format "lat,lon"
        String[] parts = node.asString().split(",");
        return new TurDslQuery.GeoPoint(Double.parseDouble(parts[0].trim()),
                Double.parseDouble(parts[1].trim()));
    }

    private TurDslQuery.IntervalsRule deserializeIntervalsRule(JsonNode node) {
        if (node == null || node.isNull()) return null;
        // Intervals can be: match, prefix, wildcard, fuzzy, all_of, any_of
        String type = node.propertyNames().iterator().next();
        JsonNode spec = node.get(type);
        String query = spec.has("query") ? spec.get("query").asString() : null;
        String analyzer = spec.has("analyzer") ? spec.get("analyzer").asString() : null;
        Integer maxGaps = spec.has("max_gaps") ? spec.get("max_gaps").asInt() : null;
        Boolean ordered = spec.has("ordered") ? spec.get("ordered").asBoolean() : null;
        List<TurDslQuery.IntervalsRule> subIntervals = null;
        if (spec.has("intervals") && spec.get("intervals").isArray()) {
            subIntervals = new ArrayList<>();
            for (JsonNode sub : spec.get("intervals"))
                subIntervals.add(deserializeIntervalsRule(sub));
        }
        return new TurDslQuery.IntervalsRule(type, query, analyzer, maxGaps, ordered, subIntervals);
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
