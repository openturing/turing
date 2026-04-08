package com.viglet.turing.sn.dsl;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.io.IOException;
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

    record RangeBucket(Object from, Object to, String key) {}

    /**
     * Custom deserializer that inspects the JSON key to determine the aggregation type.
     */
    class Deserializer extends JsonDeserializer<TurDslAggregation> {
        @Override
        public TurDslAggregation deserialize(JsonParser p, DeserializationContext ctxt)
                throws IOException {
            JsonNode node = p.getCodec().readTree(p);

            if (node.has("terms")) {
                JsonNode termsNode = node.get("terms");
                String field = termsNode.get("field").asText();
                Integer size = termsNode.has("size") ? termsNode.get("size").asInt() : null;
                return new TermsAgg(field, size);
            }
            if (node.has("range")) {
                JsonNode rangeNode = node.get("range");
                String field = rangeNode.get("field").asText();
                List<RangeBucket> ranges = new ArrayList<>();
                if (rangeNode.has("ranges")) {
                    for (JsonNode r : rangeNode.get("ranges")) {
                        Object from = r.has("from") ? nodeToObject(r.get("from")) : null;
                        Object to = r.has("to") ? nodeToObject(r.get("to")) : null;
                        String key = r.has("key") ? r.get("key").asText() : null;
                        ranges.add(new RangeBucket(from, to, key));
                    }
                }
                return new RangeAgg(field, ranges);
            }
            if (node.has("date_histogram")) {
                JsonNode dhNode = node.get("date_histogram");
                return new DateHistogramAgg(
                        dhNode.get("field").asText(),
                        dhNode.has("calendar_interval") ? dhNode.get("calendar_interval").asText() : null,
                        dhNode.has("fixed_interval") ? dhNode.get("fixed_interval").asText() : null,
                        dhNode.has("format") ? dhNode.get("format").asText() : null
                );
            }
            throw new IOException("Unknown aggregation type in: " + node);
        }

        private static Object nodeToObject(JsonNode node) {
            if (node.isInt()) return node.asInt();
            if (node.isLong()) return node.asLong();
            if (node.isDouble()) return node.asDouble();
            return node.asText();
        }
    }
}
