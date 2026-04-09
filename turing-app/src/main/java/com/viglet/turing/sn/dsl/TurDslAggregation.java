package com.viglet.turing.sn.dsl;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.util.ArrayList;
import java.util.List;

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

    record RangeBucket(Object from, Object to, String key) {}

    /**
     * Custom deserializer that inspects the JSON key to determine the aggregation type.
     */
    class Deserializer extends ValueDeserializer<TurDslAggregation> {
        @Override
        public TurDslAggregation deserialize(JsonParser p, DeserializationContext ctxt) {
            JsonNode node = p.readValueAsTree();

            if (node.has("terms")) {
                JsonNode termsNode = node.get("terms");
                String field = termsNode.get("field").asString();
                Integer size = termsNode.has("size") ? termsNode.get("size").asInt() : null;
                return new TermsAgg(field, size);
            }
            if (node.has("range")) {
                JsonNode rangeNode = node.get("range");
                String field = rangeNode.get("field").asString();
                List<RangeBucket> ranges = new ArrayList<>();
                if (rangeNode.has("ranges")) {
                    for (JsonNode r : rangeNode.get("ranges")) {
                        Object from = r.has("from") ? nodeToObject(r.get("from")) : null;
                        Object to = r.has("to") ? nodeToObject(r.get("to")) : null;
                        String key = r.has("key") ? r.get("key").asString() : null;
                        ranges.add(new RangeBucket(from, to, key));
                    }
                }
                return new RangeAgg(field, ranges);
            }
            if (node.has("date_histogram")) {
                JsonNode dhNode = node.get("date_histogram");
                return new DateHistogramAgg(
                        dhNode.get("field").asString(),
                        dhNode.has("calendar_interval") ? dhNode.get("calendar_interval").asString() : null,
                        dhNode.has("fixed_interval") ? dhNode.get("fixed_interval").asString() : null,
                        dhNode.has("format") ? dhNode.get("format").asString() : null
                );
            }
            if (node.has("avg")) return new AvgAgg(node.get("avg").get("field").asString());
            if (node.has("sum")) return new SumAgg(node.get("sum").get("field").asString());
            if (node.has("min")) return new MinAgg(node.get("min").get("field").asString());
            if (node.has("max")) return new MaxAgg(node.get("max").get("field").asString());
            if (node.has("cardinality")) {
                JsonNode cn = node.get("cardinality");
                return new CardinalityAgg(cn.get("field").asString(),
                        cn.has("precision_threshold") ? cn.get("precision_threshold").asInt() : null);
            }
            if (node.has("value_count"))
                return new ValueCountAgg(node.get("value_count").get("field").asString());
            if (node.has("histogram")) {
                JsonNode hn = node.get("histogram");
                return new HistogramAgg(hn.get("field").asString(),
                        hn.has("interval") ? hn.get("interval").asDouble() : null,
                        hn.has("offset") ? hn.get("offset").asDouble() : null);
            }
            if (node.has("filter")) {
                return new FilterAgg(new TurDslQueryDeserializer()
                        .deserializeNode(node.get("filter")));
            }
            throw new IllegalArgumentException("Unknown aggregation type in: " + node);
        }

        private static Object nodeToObject(JsonNode node) {
            if (node.isInt()) return node.asInt();
            if (node.isLong()) return node.asLong();
            if (node.isDouble()) return node.asDouble();
            return node.asString();
        }
    }
}
