package com.viglet.turing.sn.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Characterization test for {@link TurDslQueryDeserializer}: pins the
 * JSON-key -> {@link TurDslQuery} record mapping across every supported query
 * type (the polymorphic Elasticsearch-style DSL), exercising both dispatch
 * switches and the per-type parse helpers.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurDslQueryDeserializerTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static TurDslQuery parse(String json) {
        JsonNode node = MAPPER.readTree(json);
        return new TurDslQueryDeserializer().deserializeNode(node);
    }

    static Stream<Arguments> queryCases() {
        return Stream.of(
                Arguments.of("{}", TurDslQuery.MatchAll.class),
                Arguments.of("{\"match_all\":{}}", TurDslQuery.MatchAll.class),
                Arguments.of("{\"match\":{\"f\":\"hi\"}}", TurDslQuery.Match.class),
                Arguments.of("{\"match\":{\"f\":{\"query\":\"x\",\"operator\":\"and\"}}}", TurDslQuery.Match.class),
                Arguments.of("{\"multi_match\":{\"query\":\"x\",\"fields\":[\"a\",\"b\"],\"type\":\"best_fields\"}}", TurDslQuery.MultiMatch.class),
                Arguments.of("{\"term\":{\"f\":\"v\"}}", TurDslQuery.Term.class),
                Arguments.of("{\"term\":{\"f\":{\"value\":\"v\"}}}", TurDslQuery.Term.class),
                Arguments.of("{\"terms\":{\"f\":[\"a\",\"b\"]}}", TurDslQuery.Terms.class),
                Arguments.of("{\"bool\":{\"must\":[{\"match_all\":{}}],\"minimum_should_match\":1}}", TurDslQuery.Bool.class),
                Arguments.of("{\"range\":{\"price\":{\"gte\":1,\"lt\":10}}}", TurDslQuery.Range.class),
                Arguments.of("{\"wildcard\":{\"f\":\"a*\"}}", TurDslQuery.Wildcard.class),
                Arguments.of("{\"prefix\":{\"f\":\"a\"}}", TurDslQuery.Prefix.class),
                Arguments.of("{\"exists\":{\"field\":\"f\"}}", TurDslQuery.Exists.class),
                Arguments.of("{\"query_string\":{\"query\":\"x\",\"default_field\":\"f\",\"default_operator\":\"AND\"}}", TurDslQuery.QueryString.class),
                Arguments.of("{\"fuzzy\":{\"f\":{\"value\":\"x\",\"fuzziness\":2}}}", TurDslQuery.Fuzzy.class),
                Arguments.of("{\"ids\":{\"values\":[\"1\",\"2\"]}}", TurDslQuery.Ids.class),
                Arguments.of("{\"match_phrase\":{\"f\":{\"query\":\"a b\",\"slop\":1}}}", TurDslQuery.MatchPhrase.class),
                Arguments.of("{\"match_phrase_prefix\":{\"f\":\"a b\"}}", TurDslQuery.MatchPhrasePrefix.class),
                Arguments.of("{\"simple_query_string\":{\"query\":\"x\",\"fields\":[\"a\"],\"default_operator\":\"OR\"}}", TurDslQuery.SimpleQueryString.class),
                Arguments.of("{\"regexp\":{\"f\":{\"value\":\"a.*\",\"flags\":\"ALL\"}}}", TurDslQuery.Regexp.class),
                Arguments.of("{\"constant_score\":{\"filter\":{\"match_all\":{}},\"boost\":2.0}}", TurDslQuery.ConstantScore.class),
                Arguments.of("{\"dis_max\":{\"queries\":[{\"match_all\":{}}],\"tie_breaker\":0.3}}", TurDslQuery.DisMax.class),
                Arguments.of("{\"boosting\":{\"positive\":{\"match_all\":{}},\"negative\":{\"term\":{\"f\":\"v\"}},\"negative_boost\":0.5}}", TurDslQuery.Boosting.class),
                Arguments.of("{\"nested\":{\"path\":\"p\",\"query\":{\"match_all\":{}},\"score_mode\":\"avg\"}}", TurDslQuery.Nested.class),
                Arguments.of("{\"script_score\":{\"query\":{\"match_all\":{}},\"script\":{\"source\":\"_score*2\",\"lang\":\"painless\",\"params\":{\"a\":1}},\"min_score\":0.1}}", TurDslQuery.ScriptScore.class),
                Arguments.of("{\"knn\":{\"field\":\"vec\",\"query_vector\":[1.0,2.0],\"k\":5,\"num_candidates\":50,\"similarity\":0.8,\"filter\":{\"match_all\":{}}}}", TurDslQuery.Knn.class),
                Arguments.of("{\"more_like_this\":{\"fields\":[\"a\"],\"like\":\"text\",\"min_term_freq\":1,\"min_doc_freq\":1,\"max_query_terms\":10}}", TurDslQuery.MoreLikeThis.class),
                Arguments.of("{\"more_like_this\":{\"fields\":[\"a\"],\"like\":[\"txt\",{\"_id\":\"1\"}]}}", TurDslQuery.MoreLikeThis.class),
                Arguments.of("{\"combined_fields\":{\"query\":\"x\",\"fields\":[\"a\"],\"operator\":\"and\"}}", TurDslQuery.CombinedFields.class),
                Arguments.of("{\"match_bool_prefix\":{\"f\":{\"query\":\"a b\"}}}", TurDslQuery.MatchBoolPrefix.class),
                // extended dispatch
                Arguments.of("{\"pinned\":{\"ids\":[\"1\"],\"organic\":{\"match_all\":{}}}}", TurDslQuery.Pinned.class),
                Arguments.of("{\"geo_distance\":{\"distance\":\"10km\",\"loc\":{\"lat\":1.0,\"lon\":2.0}}}", TurDslQuery.GeoDistance.class),
                Arguments.of("{\"geo_distance\":{\"distance\":\"5km\",\"loc\":\"1.0,2.0\"}}", TurDslQuery.GeoDistance.class),
                Arguments.of("{\"geo_distance\":{\"distance\":\"5km\",\"loc\":[2.0,1.0]}}", TurDslQuery.GeoDistance.class),
                Arguments.of("{\"geo_bounding_box\":{\"loc\":{\"top_left\":{\"lat\":2.0,\"lon\":1.0},\"bottom_right\":{\"lat\":1.0,\"lon\":2.0}}}}", TurDslQuery.GeoBoundingBox.class),
                Arguments.of("{\"terms_set\":{\"f\":{\"terms\":[\"a\",\"b\"],\"minimum_should_match_field\":\"m\"}}}", TurDslQuery.TermsSet.class),
                Arguments.of("{\"has_child\":{\"type\":\"t\",\"query\":{\"match_all\":{}},\"score_mode\":\"max\",\"min_children\":1,\"max_children\":5}}", TurDslQuery.HasChild.class),
                Arguments.of("{\"has_parent\":{\"parent_type\":\"t\",\"query\":{\"match_all\":{}},\"score\":true}}", TurDslQuery.HasParent.class),
                Arguments.of("{\"intervals\":{\"f\":{\"match\":{\"query\":\"x\",\"analyzer\":\"std\",\"max_gaps\":2,\"ordered\":true}}}}", TurDslQuery.Intervals.class),
                Arguments.of("{\"span_term\":{\"f\":\"v\"}}", TurDslQuery.SpanTerm.class),
                Arguments.of("{\"span_near\":{\"clauses\":[{\"span_term\":{\"f\":\"v\"}}],\"slop\":1,\"in_order\":true}}", TurDslQuery.SpanNear.class),
                Arguments.of("{\"span_or\":{\"clauses\":[{\"span_term\":{\"f\":\"v\"}}]}}", TurDslQuery.SpanOr.class),
                Arguments.of("{\"span_not\":{\"include\":{\"span_term\":{\"f\":\"a\"}},\"exclude\":{\"span_term\":{\"f\":\"b\"}}}}", TurDslQuery.SpanNot.class),
                Arguments.of("{\"span_first\":{\"match\":{\"span_term\":{\"f\":\"v\"}},\"end\":3}}", TurDslQuery.SpanFirst.class),
                Arguments.of("{\"rank_feature\":{\"field\":\"f\",\"boost\":2.0}}", TurDslQuery.RankFeature.class),
                Arguments.of("{\"distance_feature\":{\"field\":\"f\",\"origin\":\"now\",\"pivot\":\"7d\"}}", TurDslQuery.DistanceFeature.class),
                Arguments.of("{\"wrapper\":{\"query\":\"eyJ9\"}}", TurDslQuery.Wrapper.class),
                Arguments.of("{\"geo_shape\":{\"loc\":{\"shape\":{\"type\":\"envelope\"},\"relation\":\"within\"}}}", TurDslQuery.GeoShape.class),
                Arguments.of("{\"percolate\":{\"field\":\"q\",\"document\":{\"title\":\"x\"}}}", TurDslQuery.Percolate.class));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("queryCases")
    void shouldDeserializeToExpectedQueryType(String json, Class<?> expectedType) {
        assertThat(parse(json)).isInstanceOf(expectedType);
    }

    @Test
    void shouldParseBoolClauses() {
        TurDslQuery.Bool bool = (TurDslQuery.Bool) parse(
                "{\"bool\":{\"must\":[{\"match_all\":{}}],\"should\":[{\"term\":{\"f\":\"v\"}}],"
                        + "\"must_not\":[{\"exists\":{\"field\":\"x\"}}],\"filter\":[{\"prefix\":{\"f\":\"a\"}}],"
                        + "\"minimum_should_match\":2}}");
        assertThat(bool.must()).hasSize(1);
        assertThat(bool.should()).hasSize(1);
        assertThat(bool.mustNot()).hasSize(1);
        assertThat(bool.filter()).hasSize(1);
        assertThat(bool.minimumShouldMatch()).isEqualTo(2);
    }

    @Test
    void shouldParseRangeBounds() {
        TurDslQuery.Range range = (TurDslQuery.Range) parse(
                "{\"range\":{\"age\":{\"gte\":18,\"lte\":65}}}");
        assertThat(range.field()).isEqualTo("age");
        assertThat(range.gte()).isEqualTo(18);
        assertThat(range.lte()).isEqualTo(65);
    }

    @Test
    void shouldParseFunctionScoreWithDecayAndFieldValueFactor() {
        TurDslQuery.FunctionScore fs = (TurDslQuery.FunctionScore) parse(
                "{\"function_score\":{\"query\":{\"match_all\":{}},\"functions\":["
                        + "{\"weight\":2.0,\"field_value_factor\":{\"field\":\"pop\",\"factor\":1.2,\"modifier\":\"log1p\"}},"
                        + "{\"gauss\":{\"date\":{\"origin\":\"now\",\"scale\":\"10d\",\"offset\":\"1d\",\"decay\":0.5}}},"
                        + "{\"linear\":{\"d\":{\"origin\":\"0\",\"scale\":\"1\"}}},"
                        + "{\"exp\":{\"d\":{\"origin\":\"0\",\"scale\":\"1\"}}},"
                        + "{\"script_score\":{\"script\":{\"source\":\"1\"}}}],"
                        + "\"score_mode\":\"sum\",\"boost_mode\":\"multiply\",\"max_boost\":5.0,\"min_score\":0.1}}");
        assertThat(fs.functions()).hasSize(5);
        assertThat(fs.scoreMode()).isEqualTo("sum");
        assertThat(fs.functions().get(0).fieldValueFactor().field()).isEqualTo("pop");
        assertThat(fs.functions().get(1).gauss().field()).isEqualTo("date");
    }

    @Test
    void shouldCollectMoreLikeThisIds() {
        TurDslQuery.MoreLikeThis mlt = (TurDslQuery.MoreLikeThis) parse(
                "{\"more_like_this\":{\"fields\":[\"title\"],\"like\":[\"some text\",{\"_id\":\"42\"}]}}");
        assertThat(mlt.likeText()).isEqualTo("some text");
        assertThat(mlt.likeIds()).containsExactly("42");
    }

    @Test
    void shouldParseKnnVector() {
        TurDslQuery.Knn knn = (TurDslQuery.Knn) parse(
                "{\"knn\":{\"field\":\"vec\",\"query_vector\":[0.1,0.2,0.3],\"k\":7}}");
        assertThat(knn.field()).isEqualTo("vec");
        assertThat(knn.queryVector()).containsExactly(0.1, 0.2, 0.3);
        assertThat(knn.k()).isEqualTo(7);
    }
}
