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
 * is determined by the JSON object key name (e.g. {@code MATCH}, {@code "bool"}).
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
public class TurDslQueryDeserializer extends ValueDeserializer<TurDslQuery> {

    // --- S1192: extracted duplicated literals ---
    private static final String MATCH = "match";
    private static final String TERMS = "terms";
    private static final String FIELD = "field";
    private static final String SCRIPT_SCORE = "script_score";
    private static final String QUERY = "query";
    private static final String SCORE_MODE = "score_mode";
    private static final String INTERVALS = "intervals";
    private static final String VALUE = "value";
    private static final String BOOST = "boost";
    private static final String ORIGIN = "origin";
    private static final String SHAPE = "shape";
    private static final String DOCUMENT = "document";
    private static final String OPERATOR = "operator";
    private static final String FIELDS = "fields";
    private static final String FILTER = "filter";
    private static final String DEFAULT_OPERATOR = "default_operator";
    private static final String FUNCTIONS = "functions";
    private static final String MIN_SCORE = "min_score";
    private static final String SCRIPT = "script";
    private static final String PARAMS = "params";
    private static final String DISTANCE = "distance";


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
            case MATCH -> deserializeMatch(body);
            case "multi_match" -> deserializeMultiMatch(body);
            case "term" -> deserializeTerm(body);
            case TERMS -> deserializeTerms(body);
            case "bool" -> deserializeBool(body);
            case "range" -> deserializeRange(body);
            case "wildcard" -> deserializeSingleField(body, TurDslQuery.Wildcard::new);
            case "prefix" -> deserializeSingleField(body, TurDslQuery.Prefix::new);
            case "exists" -> new TurDslQuery.Exists(body.get(FIELD).asString());
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
            case SCRIPT_SCORE -> deserializeScriptScore(body);
            case "knn" -> deserializeKnn(body);
            case "more_like_this" -> deserializeMoreLikeThis(body);
            case "combined_fields" -> deserializeCombinedFields(body);
            case "match_bool_prefix" -> deserializeMatchBoolPrefix(body);
            // Less-common query families (span / geo / join / feature / wrapper)
            // live in a sibling switch to keep each below the case-count limit.
            default -> deserializeExtendedQuery(type, body);
        };
    }

    /** Second-tier dispatch for the rarer query families — see {@link #deserializeNode}. */
    private TurDslQuery deserializeExtendedQuery(String type, JsonNode body) {
        return switch (type) {
            case "pinned" -> deserializePinned(body);
            case "geo_distance" -> deserializeGeoDistance(body);
            case "geo_bounding_box" -> deserializeGeoBoundingBox(body);
            case "terms_set" -> deserializeTermsSet(body);
            case "has_child" -> deserializeHasChild(body);
            case "has_parent" -> deserializeHasParent(body);
            case INTERVALS -> deserializeIntervals(body);
            case "span_term" -> deserializeSpanTerm(body);
            case "span_near" -> deserializeSpanNear(body);
            case "span_or" -> new TurDslQuery.SpanOr(
                    deserializeQueryList(body.get("clauses")));
            case "span_not" -> deserializeSpanNot(body);
            case "span_first" -> deserializeSpanFirst(body);
            case "rank_feature" -> deserializeRankFeature(body);
            case "distance_feature" -> deserializeDistanceFeature(body);
            case "wrapper" -> new TurDslQuery.Wrapper(body.get(QUERY).asString());
            case "geo_shape" -> deserializeGeoShape(body);
            case "percolate" -> deserializePercolate(body);
            default -> throw new IllegalArgumentException("Unknown query type: " + type);
        };
    }

    private TurDslQuery.HasChild deserializeHasChild(JsonNode body) {
        return new TurDslQuery.HasChild(
                body.get("type").asString(),
                body.has(QUERY) ? deserializeNode(body.get(QUERY)) : new TurDslQuery.MatchAll(),
                body.has(SCORE_MODE) ? body.get(SCORE_MODE).asString() : null,
                body.has("min_children") ? body.get("min_children").asInt() : null,
                body.has("max_children") ? body.get("max_children").asInt() : null);
    }

    private TurDslQuery.HasParent deserializeHasParent(JsonNode body) {
        return new TurDslQuery.HasParent(
                body.get("parent_type").asString(),
                body.has(QUERY) ? deserializeNode(body.get(QUERY)) : new TurDslQuery.MatchAll(),
                body.has("score") ? body.get("score").asBoolean() : null);
    }

    private TurDslQuery.Intervals deserializeIntervals(JsonNode body) {
        var entry = body.properties().iterator().next();
        return new TurDslQuery.Intervals(entry.getKey(),
                deserializeIntervalsRule(entry.getValue()));
    }

    private TurDslQuery.SpanTerm deserializeSpanTerm(JsonNode body) {
        var entry = body.properties().iterator().next();
        return new TurDslQuery.SpanTerm(entry.getKey(),
                entry.getValue().isObject()
                        ? entry.getValue().get(VALUE).asString()
                        : entry.getValue().asString());
    }

    private TurDslQuery.SpanNear deserializeSpanNear(JsonNode body) {
        return new TurDslQuery.SpanNear(
                deserializeQueryList(body.get("clauses")),
                body.has("slop") ? body.get("slop").asInt() : null,
                body.has("in_order") ? body.get("in_order").asBoolean() : null);
    }

    private TurDslQuery.SpanNot deserializeSpanNot(JsonNode body) {
        return new TurDslQuery.SpanNot(
                body.has("include") ? deserializeNode(body.get("include")) : new TurDslQuery.MatchAll(),
                body.has("exclude") ? deserializeNode(body.get("exclude")) : new TurDslQuery.MatchAll());
    }

    private TurDslQuery.SpanFirst deserializeSpanFirst(JsonNode body) {
        return new TurDslQuery.SpanFirst(
                body.has(MATCH) ? deserializeNode(body.get(MATCH)) : new TurDslQuery.MatchAll(),
                body.has("end") ? body.get("end").asInt() : null);
    }

    private TurDslQuery.RankFeature deserializeRankFeature(JsonNode body) {
        return new TurDslQuery.RankFeature(
                body.get(FIELD).asString(),
                body.has(BOOST) ? body.get(BOOST).asDouble() : null,
                null, null, null);
    }

    private TurDslQuery.DistanceFeature deserializeDistanceFeature(JsonNode body) {
        return new TurDslQuery.DistanceFeature(
                body.get(FIELD).asString(),
                body.has(ORIGIN) ? body.get(ORIGIN).asString() : null,
                body.has("pivot") ? body.get("pivot").asString() : null);
    }

    private TurDslQuery.GeoShape deserializeGeoShape(JsonNode body) {
        var entry = body.properties().iterator().next();
        JsonNode spec = entry.getValue();
        java.util.Map<String, Object> shape = null;
        if (spec.has(SHAPE) && spec.get(SHAPE).isObject()) {
            shape = new java.util.LinkedHashMap<>();
            for (var p : spec.get(SHAPE).properties()) {
                shape.put(p.getKey(), nodeToObject(p.getValue()));
            }
        }
        return new TurDslQuery.GeoShape(entry.getKey(), shape,
                spec.has("relation") ? spec.get("relation").asString() : null);
    }

    private TurDslQuery.Percolate deserializePercolate(JsonNode body) {
        java.util.Map<String, Object> doc = new java.util.LinkedHashMap<>();
        if (body.has(DOCUMENT) && body.get(DOCUMENT).isObject()) {
            for (var p : body.get(DOCUMENT).properties()) {
                doc.put(p.getKey(), nodeToObject(p.getValue()));
            }
        }
        return new TurDslQuery.Percolate(
                body.has(FIELD) ? body.get(FIELD).asString() : null, doc);
    }

    private TurDslQuery.Match deserializeMatch(JsonNode body) {
        Map.Entry<String, JsonNode> entry = body.properties().iterator().next();
        String field = entry.getKey();
        JsonNode value = entry.getValue();
        if (value.isObject()) {
            return new TurDslQuery.Match(
                    field,
                    value.get(QUERY).asString(),
                    value.has(OPERATOR) ? value.get(OPERATOR).asString() : null
            );
        }
        return new TurDslQuery.Match(field, value.asString(), null);
    }

    private TurDslQuery.MultiMatch deserializeMultiMatch(JsonNode body) {
        String query = body.get(QUERY).asString();
        List<String> fields = new ArrayList<>();
        if (body.has(FIELDS)) {
            for (JsonNode f : body.get(FIELDS)) {
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
        if (value.isObject() && value.has(VALUE)) {
            return new TurDslQuery.Term(field, nodeToObject(value.get(VALUE)));
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
                deserializeQueryList(body.get(FILTER)),
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
        String text = value.isObject() ? value.get(VALUE).asString() : value.asString();
        return factory.create(field, text);
    }

    private TurDslQuery.QueryString deserializeQueryString(JsonNode body) {
        return new TurDslQuery.QueryString(
                body.get(QUERY).asString(),
                body.has("default_field") ? body.get("default_field").asString() : null,
                body.has(DEFAULT_OPERATOR) ? body.get(DEFAULT_OPERATOR).asString() : null
        );
    }

    private TurDslQuery.Fuzzy deserializeFuzzy(JsonNode body) {
        Map.Entry<String, JsonNode> entry = body.properties().iterator().next();
        String field = entry.getKey();
        JsonNode value = entry.getValue();
        if (value.isObject()) {
            return new TurDslQuery.Fuzzy(
                    field,
                    value.get(VALUE).asString(),
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
                    value.get(QUERY).asString(),
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
                    value.get(QUERY).asString(),
                    value.has("max_expansions") ? value.get("max_expansions").asInt() : null);
        }
        return new TurDslQuery.MatchPhrasePrefix(field, value.asString(), null);
    }

    private TurDslQuery.SimpleQueryString deserializeSimpleQueryString(JsonNode body) {
        String query = body.get(QUERY).asString();
        List<String> fields = new ArrayList<>();
        if (body.has(FIELDS)) {
            for (JsonNode f : body.get(FIELDS)) fields.add(f.asString());
        }
        String defaultOperator = body.has(DEFAULT_OPERATOR)
                ? body.get(DEFAULT_OPERATOR).asString() : null;
        return new TurDslQuery.SimpleQueryString(query, fields, defaultOperator);
    }

    private TurDslQuery.Regexp deserializeSingleFieldWithFlags(JsonNode body) {
        Map.Entry<String, JsonNode> entry = body.properties().iterator().next();
        String field = entry.getKey();
        JsonNode value = entry.getValue();
        if (value.isObject()) {
            return new TurDslQuery.Regexp(field,
                    value.get(VALUE).asString(),
                    value.has("flags") ? value.get("flags").asString() : null);
        }
        return new TurDslQuery.Regexp(field, value.asString(), null);
    }

    private TurDslQuery.ConstantScore deserializeConstantScore(JsonNode body) {
        TurDslQuery filter = body.has(FILTER) ? deserializeNode(body.get(FILTER)) : new TurDslQuery.MatchAll();
        Double boost = body.has(BOOST) ? body.get(BOOST).asDouble() : null;
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
        TurDslQuery query = body.has(QUERY) ? deserializeNode(body.get(QUERY)) : new TurDslQuery.MatchAll();
        String scoreMode = body.has(SCORE_MODE) ? body.get(SCORE_MODE).asString() : null;
        return new TurDslQuery.Nested(path, query, scoreMode);
    }

    private TurDslQuery.FunctionScore deserializeFunctionScore(JsonNode body) {
        TurDslQuery query = body.has(QUERY) ? deserializeNode(body.get(QUERY)) : new TurDslQuery.MatchAll();
        List<TurDslQuery.ScoreFunction> functions = new ArrayList<>();
        if (body.has(FUNCTIONS) && body.get(FUNCTIONS).isArray()) {
            for (JsonNode fn : body.get(FUNCTIONS)) {
                functions.add(deserializeScoreFunction(fn));
            }
        }
        return new TurDslQuery.FunctionScore(query, functions,
                body.has(SCORE_MODE) ? body.get(SCORE_MODE).asString() : null,
                body.has("boost_mode") ? body.get("boost_mode").asString() : null,
                body.has("max_boost") ? body.get("max_boost").asDouble() : null,
                body.has(MIN_SCORE) ? body.get(MIN_SCORE).asDouble() : null);
    }

    private TurDslQuery.ScoreFunction deserializeScoreFunction(JsonNode fn) {
        TurDslQuery filter = fn.has(FILTER) ? deserializeNode(fn.get(FILTER)) : null;
        Double weight = fn.has("weight") ? fn.get("weight").asDouble() : null;
        TurDslQuery.FieldValueFactor fvf = fn.has("field_value_factor")
                ? deserializeFieldValueFactor(fn.get("field_value_factor")) : null;
        TurDslQuery.DecayFunction linear = fn.has("linear") ? deserializeDecay(fn.get("linear")) : null;
        TurDslQuery.DecayFunction exp = fn.has("exp") ? deserializeDecay(fn.get("exp")) : null;
        TurDslQuery.DecayFunction gauss = fn.has("gauss") ? deserializeDecay(fn.get("gauss")) : null;
        TurDslQuery.Script scriptScore = fn.has(SCRIPT_SCORE) && fn.get(SCRIPT_SCORE).has(SCRIPT)
                ? deserializeScript(fn.get(SCRIPT_SCORE).get(SCRIPT)) : null;
        return new TurDslQuery.ScoreFunction(filter, weight, fvf, linear, exp, gauss, scriptScore);
    }

    private TurDslQuery.FieldValueFactor deserializeFieldValueFactor(JsonNode n) {
        return new TurDslQuery.FieldValueFactor(
                n.get(FIELD).asString(),
                n.has("factor") ? n.get("factor").asDouble() : null,
                n.has("modifier") ? n.get("modifier").asString() : null,
                n.has("missing") ? n.get("missing").asDouble() : null);
    }

    private TurDslQuery.DecayFunction deserializeDecay(JsonNode n) {
        var entry = n.properties().iterator().next();
        String field = entry.getKey();
        JsonNode spec = entry.getValue();
        return new TurDslQuery.DecayFunction(field,
                spec.has(ORIGIN) ? spec.get(ORIGIN).asString() : null,
                spec.has("scale") ? spec.get("scale").asString() : null,
                spec.has("offset") ? spec.get("offset").asString() : null,
                spec.has("decay") ? spec.get("decay").asDouble() : null);
    }

    private TurDslQuery.Script deserializeScript(JsonNode n) {
        String source = n.has("source") ? n.get("source").asString() : null;
        String lang = n.has("lang") ? n.get("lang").asString() : null;
        java.util.Map<String, Object> params = null;
        if (n.has(PARAMS) && n.get(PARAMS).isObject()) {
            params = new java.util.LinkedHashMap<>();
            for (var prop : n.get(PARAMS).properties()) {
                params.put(prop.getKey(), nodeToObject(prop.getValue()));
            }
        }
        return new TurDslQuery.Script(source, lang, params);
    }

    private TurDslQuery.ScriptScore deserializeScriptScore(JsonNode body) {
        TurDslQuery query = body.has(QUERY) ? deserializeNode(body.get(QUERY)) : new TurDslQuery.MatchAll();
        TurDslQuery.Script script = body.has(SCRIPT) ? deserializeScript(body.get(SCRIPT)) : null;
        Double minScore = body.has(MIN_SCORE) ? body.get(MIN_SCORE).asDouble() : null;
        return new TurDslQuery.ScriptScore(query, script, minScore);
    }

    private TurDslQuery.Knn deserializeKnn(JsonNode body) {
        String field = body.get(FIELD).asString();
        List<Double> queryVector = new ArrayList<>();
        if (body.has("query_vector")) {
            for (JsonNode v : body.get("query_vector")) queryVector.add(v.asDouble());
        }
        int k = body.has("k") ? body.get("k").asInt() : 10;
        int numCandidates = body.has("num_candidates") ? body.get("num_candidates").asInt() : 100;
        Double similarity = body.has("similarity") ? body.get("similarity").asDouble() : null;
        TurDslQuery filter = body.has(FILTER) ? deserializeNode(body.get(FILTER)) : null;
        return new TurDslQuery.Knn(field, queryVector, k, numCandidates, similarity, filter);
    }

    // --- Group 5: medium-priority queries ---

    private TurDslQuery.MoreLikeThis deserializeMoreLikeThis(JsonNode body) {
        List<String> fields = new ArrayList<>();
        if (body.has(FIELDS)) for (JsonNode f : body.get(FIELDS)) fields.add(f.asString());
        List<String> likeIds = new ArrayList<>();
        String likeText = body.has("like") ? parseLikeField(body.get("like"), likeIds) : null;
        return new TurDslQuery.MoreLikeThis(fields, likeText, likeIds,
                body.has("min_term_freq") ? body.get("min_term_freq").asInt() : null,
                body.has("min_doc_freq") ? body.get("min_doc_freq").asInt() : null,
                body.has("max_query_terms") ? body.get("max_query_terms").asInt() : null);
    }

    /**
     * Parses the MoreLikeThis {@code like} clause: a bare string, or an array of
     * strings (first wins as the like text) and {@code {"_id": …}} objects
     * (collected into {@code likeIds}). Returns the like text, or null.
     */
    private String parseLikeField(JsonNode like, List<String> likeIds) {
        if (like.isString()) {
            return like.asString();
        }
        if (like.isArray()) {
            String likeText = null;
            for (JsonNode l : like) {
                if (l.isString()) {
                    if (likeText == null) likeText = l.asString();
                } else if (l.isObject() && l.has("_id")) {
                    likeIds.add(l.get("_id").asString());
                }
            }
            return likeText;
        }
        return null;
    }

    private TurDslQuery.CombinedFields deserializeCombinedFields(JsonNode body) {
        String query = body.get(QUERY).asString();
        List<String> fields = new ArrayList<>();
        if (body.has(FIELDS)) for (JsonNode f : body.get(FIELDS)) fields.add(f.asString());
        String operator = body.has(OPERATOR) ? body.get(OPERATOR).asString() : null;
        return new TurDslQuery.CombinedFields(query, fields, operator);
    }

    private TurDslQuery.MatchBoolPrefix deserializeMatchBoolPrefix(JsonNode body) {
        var entry = body.properties().iterator().next();
        String field = entry.getKey();
        JsonNode value = entry.getValue();
        String query = value.isObject() ? value.get(QUERY).asString() : value.asString();
        return new TurDslQuery.MatchBoolPrefix(field, query);
    }

    private TurDslQuery.Pinned deserializePinned(JsonNode body) {
        List<String> ids = new ArrayList<>();
        if (body.has("ids")) for (JsonNode id : body.get("ids")) ids.add(id.asString());
        TurDslQuery organic = body.has("organic") ? deserializeNode(body.get("organic")) : null;
        return new TurDslQuery.Pinned(ids, organic);
    }

    private TurDslQuery.GeoDistance deserializeGeoDistance(JsonNode body) {
        String distance = body.has(DISTANCE) ? body.get(DISTANCE).asString() : null;
        // Field is any key that isn't DISTANCE
        String field = null;
        TurDslQuery.GeoPoint location = null;
        for (var prop : body.properties()) {
            if (!DISTANCE.equals(prop.getKey())) {
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
        if (spec.has(TERMS)) for (JsonNode t : spec.get(TERMS)) terms.add(nodeToObject(t));
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
        String query = spec.has(QUERY) ? spec.get(QUERY).asString() : null;
        String analyzer = spec.has("analyzer") ? spec.get("analyzer").asString() : null;
        Integer maxGaps = spec.has("max_gaps") ? spec.get("max_gaps").asInt() : null;
        Boolean ordered = spec.has("ordered") ? spec.get("ordered").asBoolean() : null;
        List<TurDslQuery.IntervalsRule> subIntervals = null;
        if (spec.has(INTERVALS) && spec.get(INTERVALS).isArray()) {
            subIntervals = new ArrayList<>();
            for (JsonNode sub : spec.get(INTERVALS))
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
