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
     * Custom deserializer that inspects the JSON key to determine the aggregation type.
     */
    class Deserializer extends ValueDeserializer<TurDslAggregation> {

        private static final String FIELD = "field";

        @Override
        public TurDslAggregation deserialize(JsonParser p, DeserializationContext ctxt) {
            JsonNode node = p.readValueAsTree();
            return deserializeAggNode(node);
        }

        TurDslAggregation deserializeAggNode(JsonNode node) {
            TurDslAggregation agg = parseAggType(node);

            // Sub-aggregations: "aggs" or "aggregations" key alongside the type
            Map<String, TurDslAggregation> subAggs = null;
            JsonNode subNode = node.has("aggs") ? node.get("aggs")
                    : node.has("aggregations") ? node.get("aggregations") : null;
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
            if (node.has("terms")) {
                JsonNode n = node.get("terms");
                return new TermsAgg(n.get(FIELD).asString(),
                        n.has("size") ? n.get("size").asInt() : null);
            }
            if (node.has("range")) {
                JsonNode n = node.get("range");
                List<RangeBucket> ranges = new ArrayList<>();
                if (n.has("ranges")) {
                    for (JsonNode r : n.get("ranges")) {
                        ranges.add(new RangeBucket(
                                r.has("from") ? nodeToObject(r.get("from")) : null,
                                r.has("to") ? nodeToObject(r.get("to")) : null,
                                r.has("key") ? r.get("key").asString() : null));
                    }
                }
                return new RangeAgg(n.get(FIELD).asString(), ranges);
            }
            if (node.has("date_histogram")) {
                JsonNode n = node.get("date_histogram");
                return new DateHistogramAgg(n.get(FIELD).asString(),
                        n.has("calendar_interval") ? n.get("calendar_interval").asString() : null,
                        n.has("fixed_interval") ? n.get("fixed_interval").asString() : null,
                        n.has("format") ? n.get("format").asString() : null);
            }
            if (node.has("avg")) return new AvgAgg(node.get("avg").get(FIELD).asString());
            if (node.has("sum")) return new SumAgg(node.get("sum").get(FIELD).asString());
            if (node.has("min")) return new MinAgg(node.get("min").get(FIELD).asString());
            if (node.has("max")) return new MaxAgg(node.get("max").get(FIELD).asString());
            if (node.has("cardinality")) {
                JsonNode n = node.get("cardinality");
                return new CardinalityAgg(n.get(FIELD).asString(),
                        n.has("precision_threshold") ? n.get("precision_threshold").asInt() : null);
            }
            if (node.has("value_count"))
                return new ValueCountAgg(node.get("value_count").get(FIELD).asString());
            if (node.has("histogram")) {
                JsonNode n = node.get("histogram");
                return new HistogramAgg(n.get(FIELD).asString(),
                        n.has("interval") ? n.get("interval").asDouble() : null,
                        n.has("offset") ? n.get("offset").asDouble() : null);
            }
            if (node.has("filter")) {
                return new FilterAgg(new TurDslQueryDeserializer()
                        .deserializeNode(node.get("filter")));
            }
            if (node.has("stats")) return new StatsAgg(node.get("stats").get(FIELD).asString());
            if (node.has("extended_stats"))
                return new ExtendedStatsAgg(node.get("extended_stats").get(FIELD).asString());
            if (node.has("percentiles")) {
                JsonNode n = node.get("percentiles");
                List<Double> percents = new ArrayList<>();
                if (n.has("percents")) {
                    for (JsonNode pv : n.get("percents")) percents.add(pv.asDouble());
                }
                return new PercentilesAgg(n.get(FIELD).asString(),
                        percents.isEmpty() ? null : percents);
            }
            if (node.has("top_hits")) {
                JsonNode n = node.get("top_hits");
                return new TopHitsAgg(
                        n.has("size") ? n.get("size").asInt() : null, null, null);
            }
            if (node.has("composite")) {
                JsonNode n = node.get("composite");
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
            if (node.has("filters")) {
                JsonNode n = node.get("filters");
                if (n.has("filters") && n.get("filters").isObject()) {
                    Map<String, TurDslQuery> filters = new LinkedHashMap<>();
                    var queryDeser = new TurDslQueryDeserializer();
                    for (var prop : n.get("filters").properties()) {
                        filters.put(prop.getKey(), queryDeser.deserializeNode(prop.getValue()));
                    }
                    return new FiltersAgg(filters);
                }
            }
            if (node.has("significant_terms")) {
                JsonNode n = node.get("significant_terms");
                return new SignificantTermsAgg(n.get(FIELD).asString(),
                        n.has("size") ? n.get("size").asInt() : null,
                        n.has("min_doc_count") ? n.get("min_doc_count").asInt() : null);
            }
            if (node.has("nested"))
                return new NestedAgg(node.get("nested").get("path").asString());
            if (node.has("reverse_nested")) {
                JsonNode rn = node.get("reverse_nested");
                return new ReverseNestedAgg(
                        rn.has("path") ? rn.get("path").asString() : null);
            }
            if (node.has("auto_date_histogram")) {
                JsonNode n = node.get("auto_date_histogram");
                return new AutoDateHistogramAgg(n.get(FIELD).asString(),
                        n.has("buckets") ? n.get("buckets").asInt() : null,
                        n.has("format") ? n.get("format").asString() : null);
            }
            if (node.has("multi_terms")) {
                JsonNode n = node.get("multi_terms");
                List<MultiTermField> terms = new ArrayList<>();
                if (n.has("terms")) {
                    for (JsonNode t : n.get("terms"))
                        terms.add(new MultiTermField(t.get(FIELD).asString()));
                }
                return new MultiTermsAgg(terms,
                        n.has("size") ? n.get("size").asInt() : null);
            }
            if (node.has("rare_terms")) {
                JsonNode n = node.get("rare_terms");
                return new RareTermsAgg(n.get(FIELD).asString(),
                        n.has("max_doc_count") ? n.get("max_doc_count").asInt() : null);
            }
            if (node.has("top_metrics")) {
                JsonNode n = node.get("top_metrics");
                List<String> metrics = new ArrayList<>();
                if (n.has("metrics")) {
                    for (JsonNode m : n.get("metrics"))
                        metrics.add(m.get(FIELD).asString());
                }
                return new TopMetricsAgg(null, metrics);
            }
            if (node.has("percentile_ranks")) {
                JsonNode n = node.get("percentile_ranks");
                List<Double> values = new ArrayList<>();
                if (n.has("values")) {
                    for (JsonNode v : n.get("values")) values.add(v.asDouble());
                }
                return new PercentileRanksAgg(n.get(FIELD).asString(), values);
            }
            // --- Low-priority aggregations ---
            if (node.has("geo_distance")) {
                JsonNode n = node.get("geo_distance");
                GeoOrigin origin = null;
                if (n.has("origin") && n.get("origin").isObject()) {
                    origin = new GeoOrigin(n.get("origin").get("lat").asDouble(),
                            n.get("origin").get("lon").asDouble());
                }
                List<RangeBucket> ranges = new ArrayList<>();
                if (n.has("ranges")) {
                    for (JsonNode r : n.get("ranges")) {
                        ranges.add(new RangeBucket(
                                r.has("from") ? nodeToObject(r.get("from")) : null,
                                r.has("to") ? nodeToObject(r.get("to")) : null,
                                r.has("key") ? r.get("key").asString() : null));
                    }
                }
                return new GeoDistanceAgg(n.get(FIELD).asString(), origin, ranges);
            }
            if (node.has("geo_bounds"))
                return new GeoBoundsAgg(node.get("geo_bounds").get(FIELD).asString());
            if (node.has("geo_centroid"))
                return new GeoCentroidAgg(node.get("geo_centroid").get(FIELD).asString());
            if (node.has("sampler"))
                return new SamplerAgg(node.get("sampler").has("shard_size")
                        ? node.get("sampler").get("shard_size").asInt() : null);
            if (node.has("diversified_sampler")) {
                JsonNode n = node.get("diversified_sampler");
                return new DiversifiedSamplerAgg(n.get(FIELD).asString(),
                        n.has("shard_size") ? n.get("shard_size").asInt() : null);
            }
            if (node.has("adjacency_matrix")) {
                JsonNode n = node.get("adjacency_matrix");
                Map<String, TurDslQuery> filters = new LinkedHashMap<>();
                if (n.has("filters")) {
                    var qd = new TurDslQueryDeserializer();
                    for (var prop : n.get("filters").properties())
                        filters.put(prop.getKey(), qd.deserializeNode(prop.getValue()));
                }
                return new AdjacencyMatrixAgg(filters);
            }
            if (node.has("scripted_metric")) {
                JsonNode n = node.get("scripted_metric");
                return new ScriptedMetricAgg(
                        n.has("init_script") ? n.get("init_script").asString() : null,
                        n.has("map_script") ? n.get("map_script").asString() : null,
                        n.has("combine_script") ? n.get("combine_script").asString() : null,
                        n.has("reduce_script") ? n.get("reduce_script").asString() : null);
            }
            if (node.has("median_absolute_deviation"))
                return new MedianAbsoluteDeviationAgg(
                        node.get("median_absolute_deviation").get(FIELD).asString());
            if (node.has("matrix_stats")) {
                JsonNode n = node.get("matrix_stats");
                List<String> fields = new ArrayList<>();
                if (n.has("fields")) for (JsonNode f : n.get("fields")) fields.add(f.asString());
                return new MatrixStatsAgg(fields);
            }
            if (node.has("boxplot"))
                return new BoxplotAgg(node.get("boxplot").get(FIELD).asString());
            if (node.has("string_stats"))
                return new StringStatsAgg(node.get("string_stats").get(FIELD).asString());
            if (node.has("t_test")) {
                JsonNode n = node.get("t_test");
                TTestPopulation a = n.has("a") ? parseTTestPop(n.get("a")) : null;
                TTestPopulation b = n.has("b") ? parseTTestPop(n.get("b")) : null;
                return new TTestAgg(a, b, n.has("type") ? n.get("type").asString() : null);
            }
            if (node.has("variable_width_histogram")) {
                JsonNode n = node.get("variable_width_histogram");
                return new VariableWidthHistogramAgg(n.get(FIELD).asString(),
                        n.has("buckets") ? n.get("buckets").asInt() : null);
            }
            if (node.has("rate")) {
                JsonNode n = node.get("rate");
                return new RateAgg(n.has(FIELD) ? n.get(FIELD).asString() : null,
                        n.has("unit") ? n.get("unit").asString() : null);
            }
            throw new IllegalArgumentException("Unknown aggregation type in: " + node);
        }

        private static TTestPopulation parseTTestPop(JsonNode n) {
            String field = n.has("field") ? n.get("field").asString() : null;
            TurDslQuery filter = n.has("filter")
                    ? new TurDslQueryDeserializer().deserializeNode(n.get("filter")) : null;
            return new TTestPopulation(field, filter);
        }

        private static Object nodeToObject(JsonNode node) {
            if (node.isInt()) return node.asInt();
            if (node.isLong()) return node.asLong();
            if (node.isDouble()) return node.asDouble();
            return node.asString();
        }
    }
}
