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
 * Characterization test for {@link TurDslAggregation.Deserializer}: pins the
 * JSON-key -> aggregation record mapping across every supported aggregation
 * type so the dispatch can be refactored (S6539 Monster Class split) without
 * behavior change.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurDslAggregationDeserializerTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static TurDslAggregation parse(String json) {
        JsonNode node = MAPPER.readTree(json);
        return new TurDslAggregation.Deserializer().deserializeAggNode(node);
    }

    static Stream<Arguments> aggregationCases() {
        return Stream.of(
                // terms / range / histogram family
                Arguments.of("{\"terms\":{\"field\":\"f\",\"size\":5}}", TurDslAggregation.TermsAgg.class),
                Arguments.of("{\"range\":{\"field\":\"f\",\"ranges\":[{\"from\":1,\"to\":2}]}}", TurDslAggregation.RangeAgg.class),
                Arguments.of("{\"date_histogram\":{\"field\":\"d\",\"calendar_interval\":\"day\"}}", TurDslAggregation.DateHistogramAgg.class),
                Arguments.of("{\"histogram\":{\"field\":\"f\",\"interval\":5.0}}", TurDslAggregation.HistogramAgg.class),
                // core metrics
                Arguments.of("{\"avg\":{\"field\":\"f\"}}", TurDslAggregation.AvgAgg.class),
                Arguments.of("{\"sum\":{\"field\":\"f\"}}", TurDslAggregation.SumAgg.class),
                Arguments.of("{\"min\":{\"field\":\"f\"}}", TurDslAggregation.MinAgg.class),
                Arguments.of("{\"max\":{\"field\":\"f\"}}", TurDslAggregation.MaxAgg.class),
                Arguments.of("{\"cardinality\":{\"field\":\"f\",\"precision_threshold\":100}}", TurDslAggregation.CardinalityAgg.class),
                Arguments.of("{\"value_count\":{\"field\":\"f\"}}", TurDslAggregation.ValueCountAgg.class),
                Arguments.of("{\"filter\":{\"term\":{\"field\":\"f\",\"value\":\"v\"}}}", TurDslAggregation.FilterAgg.class),
                Arguments.of("{\"stats\":{\"field\":\"f\"}}", TurDslAggregation.StatsAgg.class),
                Arguments.of("{\"extended_stats\":{\"field\":\"f\"}}", TurDslAggregation.ExtendedStatsAgg.class),
                Arguments.of("{\"percentiles\":{\"field\":\"f\",\"percents\":[50.0,95.0]}}", TurDslAggregation.PercentilesAgg.class),
                Arguments.of("{\"top_hits\":{\"size\":3}}", TurDslAggregation.TopHitsAgg.class),
                // bucket extras
                Arguments.of("{\"composite\":{\"size\":10,\"sources\":[{\"s\":{\"terms\":{\"field\":\"f\"}}}]}}", TurDslAggregation.CompositeAgg.class),
                Arguments.of("{\"filters\":{\"filters\":{\"a\":{\"term\":{\"field\":\"f\",\"value\":\"v\"}}}}}", TurDslAggregation.FiltersAgg.class),
                Arguments.of("{\"significant_terms\":{\"field\":\"f\",\"size\":3,\"min_doc_count\":2}}", TurDslAggregation.SignificantTermsAgg.class),
                Arguments.of("{\"nested\":{\"path\":\"p\"}}", TurDslAggregation.NestedAgg.class),
                Arguments.of("{\"reverse_nested\":{\"path\":\"p\"}}", TurDslAggregation.ReverseNestedAgg.class),
                Arguments.of("{\"auto_date_histogram\":{\"field\":\"d\",\"buckets\":10}}", TurDslAggregation.AutoDateHistogramAgg.class),
                Arguments.of("{\"multi_terms\":{\"terms\":[{\"field\":\"a\"},{\"field\":\"b\"}],\"size\":5}}", TurDslAggregation.MultiTermsAgg.class),
                Arguments.of("{\"rare_terms\":{\"field\":\"f\",\"max_doc_count\":3}}", TurDslAggregation.RareTermsAgg.class),
                Arguments.of("{\"top_metrics\":{\"metrics\":[{\"field\":\"f\"}]}}", TurDslAggregation.TopMetricsAgg.class),
                Arguments.of("{\"percentile_ranks\":{\"field\":\"f\",\"values\":[10.0,20.0]}}", TurDslAggregation.PercentileRanksAgg.class),
                // geo / sampling
                Arguments.of("{\"geo_distance\":{\"field\":\"loc\",\"origin\":{\"lat\":1.0,\"lon\":2.0},\"ranges\":[{\"to\":100}]}}", TurDslAggregation.GeoDistanceAgg.class),
                Arguments.of("{\"geo_bounds\":{\"field\":\"loc\"}}", TurDslAggregation.GeoBoundsAgg.class),
                Arguments.of("{\"geo_centroid\":{\"field\":\"loc\"}}", TurDslAggregation.GeoCentroidAgg.class),
                Arguments.of("{\"sampler\":{\"shard_size\":100}}", TurDslAggregation.SamplerAgg.class),
                Arguments.of("{\"diversified_sampler\":{\"field\":\"f\",\"shard_size\":50}}", TurDslAggregation.DiversifiedSamplerAgg.class),
                Arguments.of("{\"adjacency_matrix\":{\"filters\":{\"a\":{\"term\":{\"field\":\"f\",\"value\":\"v\"}}}}}", TurDslAggregation.AdjacencyMatrixAgg.class),
                // stat-misc
                Arguments.of("{\"scripted_metric\":{\"init_script\":\"i\",\"map_script\":\"m\",\"combine_script\":\"c\",\"reduce_script\":\"r\"}}", TurDslAggregation.ScriptedMetricAgg.class),
                Arguments.of("{\"median_absolute_deviation\":{\"field\":\"f\"}}", TurDslAggregation.MedianAbsoluteDeviationAgg.class),
                Arguments.of("{\"matrix_stats\":{\"fields\":[\"a\",\"b\"]}}", TurDslAggregation.MatrixStatsAgg.class),
                Arguments.of("{\"boxplot\":{\"field\":\"f\"}}", TurDslAggregation.BoxplotAgg.class),
                Arguments.of("{\"string_stats\":{\"field\":\"f\"}}", TurDslAggregation.StringStatsAgg.class),
                Arguments.of("{\"t_test\":{\"a\":{\"field\":\"x\"},\"b\":{\"field\":\"y\"},\"type\":\"heteroscedastic\"}}", TurDslAggregation.TTestAgg.class),
                Arguments.of("{\"variable_width_histogram\":{\"field\":\"f\",\"buckets\":5}}", TurDslAggregation.VariableWidthHistogramAgg.class),
                Arguments.of("{\"rate\":{\"field\":\"f\",\"unit\":\"month\"}}", TurDslAggregation.RateAgg.class));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("aggregationCases")
    void shouldDeserializeToExpectedAggregationType(String json, Class<?> expectedType) {
        assertThat(parse(json)).isInstanceOf(expectedType);
    }

    @Test
    void shouldExtractTermsFieldAndSize() {
        TurDslAggregation.TermsAgg agg = (TurDslAggregation.TermsAgg) parse("{\"terms\":{\"field\":\"category\",\"size\":7}}");
        assertThat(agg.field()).isEqualTo("category");
        assertThat(agg.size()).isEqualTo(7);
    }

    @Test
    void shouldExtractRangeBuckets() {
        TurDslAggregation.RangeAgg agg = (TurDslAggregation.RangeAgg) parse(
                "{\"range\":{\"field\":\"price\",\"ranges\":[{\"from\":1,\"to\":2,\"key\":\"low\"},{\"from\":2}]}}");
        assertThat(agg.field()).isEqualTo("price");
        assertThat(agg.ranges()).hasSize(2);
        assertThat(agg.ranges().get(0).key()).isEqualTo("low");
    }

    @Test
    void shouldExtractGeoDistanceOrigin() {
        TurDslAggregation.GeoDistanceAgg agg = (TurDslAggregation.GeoDistanceAgg) parse(
                "{\"geo_distance\":{\"field\":\"loc\",\"origin\":{\"lat\":10.5,\"lon\":20.5},\"ranges\":[{\"to\":100}]}}");
        assertThat(agg.origin().lat()).isEqualTo(10.5);
        assertThat(agg.origin().lon()).isEqualTo(20.5);
        assertThat(agg.ranges()).hasSize(1);
    }

    @Test
    void shouldWrapSubAggregationsWithSubAggs() {
        TurDslAggregation agg = parse(
                "{\"terms\":{\"field\":\"f\"},\"aggs\":{\"inner\":{\"avg\":{\"field\":\"price\"}}}}");
        assertThat(agg).isInstanceOf(TurDslAggregation.WithSubAggs.class);
        TurDslAggregation.WithSubAggs wrapped = (TurDslAggregation.WithSubAggs) agg;
        assertThat(wrapped.parent()).isInstanceOf(TurDslAggregation.TermsAgg.class);
        assertThat(wrapped.subAggs()).containsKey("inner");
        assertThat(wrapped.subAggs().get("inner")).isInstanceOf(TurDslAggregation.AvgAgg.class);
    }

    @Test
    void shouldSupportAggregationsKeyForSubAggs() {
        TurDslAggregation agg = parse(
                "{\"terms\":{\"field\":\"f\"},\"aggregations\":{\"inner\":{\"sum\":{\"field\":\"q\"}}}}");
        assertThat(agg).isInstanceOf(TurDslAggregation.WithSubAggs.class);
    }
}
