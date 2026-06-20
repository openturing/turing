package com.viglet.turing.sn.dsl;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sealed interface representing supported aggregation types in the Query DSL.
 * <p>
 * Supported types:
 * <ul>
 *   <li>{@code terms} - Bucket aggregation by field values</li>
 *   <li>{@code range} - Bucket aggregation by numeric/date ranges</li>
 *   <li>{@code date_histogram} - Bucket aggregation by date intervals</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@JsonDeserialize(using = TurDslAggregation.Deserializer.class)
public sealed interface TurDslAggregation {

    // --- S1192: extracted duplicated literals ---
    String FIELD = "field";
    String TERMS = "terms";
    String RANGES = "ranges";
    String FORMAT = "format";
    String FILTER = "filter";
    String FILTERS = "filters";
    String BUCKETS = "buckets";
    String ORIGIN = "origin";
    String SAMPLER = "sampler";
    String SHARD_SIZE = "shard_size";


    record TermsAgg(String field, Integer size) implements TurDslAggregation {}

    record RangeAgg(String field, List<RangeBucket> ranges) implements TurDslAggregation {}

    record DateHistogramAgg(String field, String calendarInterval,
                            String fixedInterval, String format) implements TurDslAggregation {}

    // --- Metric aggregations ---

    record AvgAgg(String field) implements TurDslAggregation {}

    record SumAgg(String field) implements TurDslAggregation {}

    record MinAgg(String field) implements TurDslAggregation {}

    record MaxAgg(String field) implements TurDslAggregation {}

    record CardinalityAgg(String field, Integer precisionThreshold) implements TurDslAggregation {}

    record ValueCountAgg(String field) implements TurDslAggregation {}

    // --- Bucket aggregations ---

    record HistogramAgg(String field, Double interval, Double offset) implements TurDslAggregation {}

    record FilterAgg(TurDslQuery filter) implements TurDslAggregation {}

    // --- High-priority aggregations ---

    record StatsAgg(String field) implements TurDslAggregation {}

    record ExtendedStatsAgg(String field) implements TurDslAggregation {}

    record PercentilesAgg(String field, List<Double> percents) implements TurDslAggregation {}

    record TopHitsAgg(Integer size, List<Object> sort, Object source) implements TurDslAggregation {}

    record CompositeAgg(List<Map<String, CompositeSource>> sources, Integer size,
                        Map<String, Object> after) implements TurDslAggregation {}

    record FiltersAgg(Map<String, TurDslQuery> filters) implements TurDslAggregation {}

    // --- Medium-priority aggregations ---

    record SignificantTermsAgg(String field, Integer size,
                                Integer minDocCount) implements TurDslAggregation {}

    record NestedAgg(String path) implements TurDslAggregation {}

    record ReverseNestedAgg(String path) implements TurDslAggregation {}

    record AutoDateHistogramAgg(String field, Integer buckets,
                                 String format) implements TurDslAggregation {}

    record MultiTermsAgg(List<MultiTermField> multiTermFields,
                          Integer size) implements TurDslAggregation {}

    record RareTermsAgg(String field, Integer maxDocCount) implements TurDslAggregation {}

    record TopMetricsAgg(List<Object> sort,
                          List<String> metrics) implements TurDslAggregation {}

    record PercentileRanksAgg(String field, List<Double> values) implements TurDslAggregation {}

    record MultiTermField(String field) {}

    // --- Low-priority aggregations ---

    record GeoDistanceAgg(String field, GeoOrigin origin,
                           List<RangeBucket> ranges) implements TurDslAggregation {}

    record GeoBoundsAgg(String field) implements TurDslAggregation {}

    record GeoCentroidAgg(String field) implements TurDslAggregation {}

    record SamplerAgg(Integer shardSize) implements TurDslAggregation {}

    record DiversifiedSamplerAgg(String field, Integer shardSize) implements TurDslAggregation {}

    record AdjacencyMatrixAgg(Map<String, TurDslQuery> filters) implements TurDslAggregation {}

    record ScriptedMetricAgg(String initScript, String mapScript,
                              String combineScript, String reduceScript) implements TurDslAggregation {}

    record MedianAbsoluteDeviationAgg(String field) implements TurDslAggregation {}

    record MatrixStatsAgg(List<String> fields) implements TurDslAggregation {}

    record BoxplotAgg(String field) implements TurDslAggregation {}

    record StringStatsAgg(String field) implements TurDslAggregation {}

    record TTestAgg(TTestPopulation a, TTestPopulation b, String type) implements TurDslAggregation {}

    record VariableWidthHistogramAgg(String field, Integer buckets) implements TurDslAggregation {}

    record RateAgg(String field, String unit) implements TurDslAggregation {}

    record GeoOrigin(double lat, double lon) {}

    record TTestPopulation(String field, TurDslQuery filter) {}

    /** Wrapper that attaches sub-aggregations to any parent aggregation. */
    record WithSubAggs(TurDslAggregation parent,
                       Map<String, TurDslAggregation> subAggs) implements TurDslAggregation {}

    record CompositeSource(String type, String field, String order) {}

    record RangeBucket(Object from, Object to, String key) {}

    /**
     * Custom deserializer that inspects the JSON key to determine the aggregation
     * type. The per-family parsing is split across small {@code *Parser} classes
     * (see below) so no single class depends on every aggregation record (S6539).
     */
    class Deserializer extends ValueDeserializer<TurDslAggregation> {

        @Override
        public TurDslAggregation deserialize(JsonParser p, DeserializationContext ctxt) {
            JsonNode node = p.readValueAsTree();
            return deserializeAggNode(node);
        }

        TurDslAggregation deserializeAggNode(JsonNode node) {
            TurDslAggregation agg = parseAggType(node);

            // Sub-aggregations: "aggs" or "aggregations" key alongside the type
            Map<String, TurDslAggregation> subAggs = null;
            JsonNode aggregationsNode = node.has("aggregations") ? node.get("aggregations") : null;
            JsonNode subNode = node.has("aggs") ? node.get("aggs") : aggregationsNode;
            if (subNode != null && subNode.isObject()) {
                subAggs = new LinkedHashMap<>();
                for (var entry : subNode.properties()) {
                    subAggs.put(entry.getKey(), deserializeAggNode(entry.getValue()));
                }
            }

            return (subAggs != null && !subAggs.isEmpty())
                    ? new WithSubAggs(agg, subAggs) : agg;
        }

        private TurDslAggregation parseAggType(JsonNode node) {
            TurDslAggregation agg = TermsRangeParser.parse(node);
            if (agg == null) agg = MetricParser.parse(node);
            if (agg == null) agg = BucketExtrasParser.parse(node);
            if (agg == null) agg = GeoParser.parse(node);
            if (agg == null) agg = StatMiscParser.parse(node);
            if (agg == null) {
                throw new IllegalArgumentException("Unknown aggregation type in: " + node);
            }
            return agg;
        }
    }

    /** Shared JSON-node helpers for the aggregation sub-parsers. */
    final class AggJson {
        private AggJson() {
        }

        static Object nodeToObject(JsonNode node) {
            if (node.isInt()) return node.asInt();
            if (node.isLong()) return node.asLong();
            if (node.isDouble()) return node.asDouble();
            return node.asString();
        }
    }

    /** Parses the bucket family keyed on numeric/term/date intervals. */
    final class TermsRangeParser {
        private TermsRangeParser() {
        }

        static TurDslAggregation parse(JsonNode node) {
            if (node.has(TERMS)) return parseTermsAgg(node.get(TERMS));
            if (node.has("range")) return parseRangeAgg(node.get("range"));
            if (node.has("date_histogram")) return parseDateHistogramAgg(node.get("date_histogram"));
            if (node.has("histogram")) return parseHistogramAgg(node.get("histogram"));
            return null;
        }

        private static TermsAgg parseTermsAgg(JsonNode n) {
            return new TermsAgg(n.get(FIELD).asString(),
                    n.has("size") ? n.get("size").asInt() : null);
        }

        private static RangeAgg parseRangeAgg(JsonNode n) {
            List<RangeBucket> ranges = new ArrayList<>();
            if (n.has(RANGES)) {
                for (JsonNode r : n.get(RANGES)) {
                    ranges.add(new RangeBucket(
                            r.has("from") ? AggJson.nodeToObject(r.get("from")) : null,
                            r.has("to") ? AggJson.nodeToObject(r.get("to")) : null,
                            r.has("key") ? r.get("key").asString() : null));
                }
            }
            return new RangeAgg(n.get(FIELD).asString(), ranges);
        }

        private static DateHistogramAgg parseDateHistogramAgg(JsonNode n) {
            return new DateHistogramAgg(n.get(FIELD).asString(),
                    n.has("calendar_interval") ? n.get("calendar_interval").asString() : null,
                    n.has("fixed_interval") ? n.get("fixed_interval").asString() : null,
                    n.has(FORMAT) ? n.get(FORMAT).asString() : null);
        }

        private static HistogramAgg parseHistogramAgg(JsonNode n) {
            return new HistogramAgg(n.get(FIELD).asString(),
                    n.has("interval") ? n.get("interval").asDouble() : null,
                    n.has("offset") ? n.get("offset").asDouble() : null);
        }
    }

    /** Parses the single-value and stats metric aggregations. */
    final class MetricParser {
        private MetricParser() {
        }

        static TurDslAggregation parse(JsonNode node) {
            if (node.has("avg")) return new AvgAgg(node.get("avg").get(FIELD).asString());
            if (node.has("sum")) return new SumAgg(node.get("sum").get(FIELD).asString());
            if (node.has("min")) return new MinAgg(node.get("min").get(FIELD).asString());
            if (node.has("max")) return new MaxAgg(node.get("max").get(FIELD).asString());
            if (node.has("cardinality")) return parseCardinalityAgg(node.get("cardinality"));
            if (node.has("value_count"))
                return new ValueCountAgg(node.get("value_count").get(FIELD).asString());
            if (node.has(FILTER))
                return new FilterAgg(new TurDslQueryDeserializer().deserializeNode(node.get(FILTER)));
            if (node.has("stats")) return new StatsAgg(node.get("stats").get(FIELD).asString());
            if (node.has("extended_stats"))
                return new ExtendedStatsAgg(node.get("extended_stats").get(FIELD).asString());
            if (node.has("percentiles")) return parsePercentilesAgg(node.get("percentiles"));
            if (node.has("top_hits")) return parseTopHitsAgg(node.get("top_hits"));
            return null;
        }

        private static CardinalityAgg parseCardinalityAgg(JsonNode n) {
            return new CardinalityAgg(n.get(FIELD).asString(),
                    n.has("precision_threshold") ? n.get("precision_threshold").asInt() : null);
        }

        private static PercentilesAgg parsePercentilesAgg(JsonNode n) {
            List<Double> percents = new ArrayList<>();
            if (n.has("percents")) {
                for (JsonNode pv : n.get("percents")) percents.add(pv.asDouble());
            }
            return new PercentilesAgg(n.get(FIELD).asString(),
                    percents.isEmpty() ? null : percents);
        }

        private static TopHitsAgg parseTopHitsAgg(JsonNode n) {
            return new TopHitsAgg(n.has("size") ? n.get("size").asInt() : null, null, null);
        }
    }

    /** Parses the remaining bucket aggregations (composite, filters, nested, ...). */
    final class BucketExtrasParser {
        private BucketExtrasParser() {
        }

        static TurDslAggregation parse(JsonNode node) {
            if (node.has("composite")) return parseCompositeAgg(node.get("composite"));
            if (node.has(FILTERS)) {
                TurDslAggregation filters = parseFiltersAgg(node.get(FILTERS));
                if (filters != null) {
                    return filters;
                }
            }
            if (node.has("significant_terms")) return parseSignificantTermsAgg(node.get("significant_terms"));
            if (node.has("nested")) return new NestedAgg(node.get("nested").get("path").asString());
            if (node.has("reverse_nested")) {
                JsonNode rn = node.get("reverse_nested");
                return new ReverseNestedAgg(rn.has("path") ? rn.get("path").asString() : null);
            }
            if (node.has("auto_date_histogram")) return parseAutoDateHistogramAgg(node.get("auto_date_histogram"));
            if (node.has("multi_terms")) return parseMultiTermsAgg(node.get("multi_terms"));
            if (node.has("rare_terms")) return parseRareTermsAgg(node.get("rare_terms"));
            if (node.has("top_metrics")) return parseTopMetricsAgg(node.get("top_metrics"));
            if (node.has("percentile_ranks")) return parsePercentileRanksAgg(node.get("percentile_ranks"));
            return null;
        }

        private static CompositeAgg parseCompositeAgg(JsonNode n) {
            List<Map<String, CompositeSource>> sources = new ArrayList<>();
            if (n.has("sources")) {
                for (JsonNode srcEntry : n.get("sources")) {
                    Map<String, CompositeSource> srcMap = new LinkedHashMap<>();
                    for (var prop : srcEntry.properties()) {
                        JsonNode inner = prop.getValue();
                        String type = inner.propertyNames().iterator().next();
                        JsonNode spec = inner.get(type);
                        srcMap.put(prop.getKey(), new CompositeSource(type,
                                spec.get(FIELD).asString(),
                                spec.has("order") ? spec.get("order").asString() : null));
                    }
                    sources.add(srcMap);
                }
            }
            return new CompositeAgg(sources,
                    n.has("size") ? n.get("size").asInt() : null, null);
        }

        private static FiltersAgg parseFiltersAgg(JsonNode n) {
            if (n.has(FILTERS) && n.get(FILTERS).isObject()) {
                Map<String, TurDslQuery> filters = new LinkedHashMap<>();
                var queryDeser = new TurDslQueryDeserializer();
                for (var prop : n.get(FILTERS).properties()) {
                    filters.put(prop.getKey(), queryDeser.deserializeNode(prop.getValue()));
                }
                return new FiltersAgg(filters);
            }
            return null;
        }

        private static SignificantTermsAgg parseSignificantTermsAgg(JsonNode n) {
            return new SignificantTermsAgg(n.get(FIELD).asString(),
                    n.has("size") ? n.get("size").asInt() : null,
                    n.has("min_doc_count") ? n.get("min_doc_count").asInt() : null);
        }

        private static AutoDateHistogramAgg parseAutoDateHistogramAgg(JsonNode n) {
            return new AutoDateHistogramAgg(n.get(FIELD).asString(),
                    n.has(BUCKETS) ? n.get(BUCKETS).asInt() : null,
                    n.has(FORMAT) ? n.get(FORMAT).asString() : null);
        }

        private static MultiTermsAgg parseMultiTermsAgg(JsonNode n) {
            List<MultiTermField> terms = new ArrayList<>();
            if (n.has(TERMS)) {
                for (JsonNode t : n.get(TERMS))
                    terms.add(new MultiTermField(t.get(FIELD).asString()));
            }
            return new MultiTermsAgg(terms,
                    n.has("size") ? n.get("size").asInt() : null);
        }

        private static RareTermsAgg parseRareTermsAgg(JsonNode n) {
            return new RareTermsAgg(n.get(FIELD).asString(),
                    n.has("max_doc_count") ? n.get("max_doc_count").asInt() : null);
        }

        private static TopMetricsAgg parseTopMetricsAgg(JsonNode n) {
            List<String> metrics = new ArrayList<>();
            if (n.has("metrics")) {
                for (JsonNode m : n.get("metrics"))
                    metrics.add(m.get(FIELD).asString());
            }
            return new TopMetricsAgg(null, metrics);
        }

        private static PercentileRanksAgg parsePercentileRanksAgg(JsonNode n) {
            List<Double> values = new ArrayList<>();
            if (n.has("values")) {
                for (JsonNode v : n.get("values")) values.add(v.asDouble());
            }
            return new PercentileRanksAgg(n.get(FIELD).asString(), values);
        }
    }

    /** Parses the geo and sampling aggregations. */
    final class GeoParser {
        private GeoParser() {
        }

        static TurDslAggregation parse(JsonNode node) {
            if (node.has("geo_distance")) return parseGeoDistanceAgg(node.get("geo_distance"));
            if (node.has("geo_bounds"))
                return new GeoBoundsAgg(node.get("geo_bounds").get(FIELD).asString());
            if (node.has("geo_centroid"))
                return new GeoCentroidAgg(node.get("geo_centroid").get(FIELD).asString());
            if (node.has(SAMPLER))
                return new SamplerAgg(node.get(SAMPLER).has(SHARD_SIZE)
                        ? node.get(SAMPLER).get(SHARD_SIZE).asInt() : null);
            if (node.has("diversified_sampler")) {
                JsonNode n = node.get("diversified_sampler");
                return new DiversifiedSamplerAgg(n.get(FIELD).asString(),
                        n.has(SHARD_SIZE) ? n.get(SHARD_SIZE).asInt() : null);
            }
            if (node.has("adjacency_matrix")) return parseAdjacencyMatrixAgg(node.get("adjacency_matrix"));
            return null;
        }

        private static GeoDistanceAgg parseGeoDistanceAgg(JsonNode n) {
            GeoOrigin origin = null;
            if (n.has(ORIGIN) && n.get(ORIGIN).isObject()) {
                origin = new GeoOrigin(n.get(ORIGIN).get("lat").asDouble(),
                        n.get(ORIGIN).get("lon").asDouble());
            }
            List<RangeBucket> ranges = new ArrayList<>();
            if (n.has(RANGES)) {
                for (JsonNode r : n.get(RANGES)) {
                    ranges.add(new RangeBucket(
                            r.has("from") ? AggJson.nodeToObject(r.get("from")) : null,
                            r.has("to") ? AggJson.nodeToObject(r.get("to")) : null,
                            r.has("key") ? r.get("key").asString() : null));
                }
            }
            return new GeoDistanceAgg(n.get(FIELD).asString(), origin, ranges);
        }

        private static AdjacencyMatrixAgg parseAdjacencyMatrixAgg(JsonNode n) {
            Map<String, TurDslQuery> filters = new LinkedHashMap<>();
            if (n.has(FILTERS)) {
                var qd = new TurDslQueryDeserializer();
                for (var prop : n.get(FILTERS).properties())
                    filters.put(prop.getKey(), qd.deserializeNode(prop.getValue()));
            }
            return new AdjacencyMatrixAgg(filters);
        }
    }

    /** Parses the statistical-and-miscellaneous aggregations. */
    final class StatMiscParser {
        private StatMiscParser() {
        }

        static TurDslAggregation parse(JsonNode node) {
            if (node.has("scripted_metric")) return parseScriptedMetricAgg(node.get("scripted_metric"));
            if (node.has("median_absolute_deviation"))
                return new MedianAbsoluteDeviationAgg(
                        node.get("median_absolute_deviation").get(FIELD).asString());
            if (node.has("matrix_stats")) return parseMatrixStatsAgg(node.get("matrix_stats"));
            if (node.has("boxplot"))
                return new BoxplotAgg(node.get("boxplot").get(FIELD).asString());
            if (node.has("string_stats"))
                return new StringStatsAgg(node.get("string_stats").get(FIELD).asString());
            if (node.has("t_test")) return parseTTestAgg(node.get("t_test"));
            if (node.has("variable_width_histogram")) {
                JsonNode n = node.get("variable_width_histogram");
                return new VariableWidthHistogramAgg(n.get(FIELD).asString(),
                        n.has(BUCKETS) ? n.get(BUCKETS).asInt() : null);
            }
            if (node.has("rate")) {
                JsonNode n = node.get("rate");
                return new RateAgg(n.has(FIELD) ? n.get(FIELD).asString() : null,
                        n.has("unit") ? n.get("unit").asString() : null);
            }
            return null;
        }

        private static ScriptedMetricAgg parseScriptedMetricAgg(JsonNode n) {
            return new ScriptedMetricAgg(
                    n.has("init_script") ? n.get("init_script").asString() : null,
                    n.has("map_script") ? n.get("map_script").asString() : null,
                    n.has("combine_script") ? n.get("combine_script").asString() : null,
                    n.has("reduce_script") ? n.get("reduce_script").asString() : null);
        }

        private static MatrixStatsAgg parseMatrixStatsAgg(JsonNode n) {
            List<String> fields = new ArrayList<>();
            if (n.has("fields")) for (JsonNode f : n.get("fields")) fields.add(f.asString());
            return new MatrixStatsAgg(fields);
        }

        private static TTestAgg parseTTestAgg(JsonNode n) {
            TTestPopulation a = n.has("a") ? parseTTestPop(n.get("a")) : null;
            TTestPopulation b = n.has("b") ? parseTTestPop(n.get("b")) : null;
            return new TTestAgg(a, b, n.has("type") ? n.get("type").asString() : null);
        }

        private static TTestPopulation parseTTestPop(JsonNode n) {
            String field = n.has(FIELD) ? n.get(FIELD).asString() : null;
            TurDslQuery filter = n.has(FILTER)
                    ? new TurDslQueryDeserializer().deserializeNode(n.get(FILTER)) : null;
            return new TTestPopulation(field, filter);
        }
    }
}
