package com.viglet.turing.sn.dsl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Elasticsearch-compatible search response format for the Query DSL endpoint.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TurDslSearchResponse(
        long took,
        @JsonProperty("timed_out") boolean timedOut,
        Hits hits,
        Map<String, AggregationResult> aggregations,
        Map<String, List<SuggestResult>> suggest
) {
    /** Constructor without suggest (backward-compatible). */
    public TurDslSearchResponse(long took, boolean timedOut, Hits hits,
                                 Map<String, AggregationResult> aggregations) {
        this(took, timedOut, hits, aggregations, null);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SuggestResult(
            String text,
            Integer offset,
            Integer length,
            List<SuggestOption> options
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SuggestOption(
            String text,
            Double score,
            @JsonProperty("_id") String id,
            @JsonProperty("_source") Map<String, Object> source
    ) {}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Hits(
            Total total,
            @JsonProperty("max_score") Double maxScore,
            List<Hit> hits
    ) {}

    public record Total(long value, String relation) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Hit(
            @JsonProperty("_id") String id,
            @JsonProperty("_score") Double score,
            @JsonProperty("_source") Map<String, Object> source,
            Map<String, List<String>> highlight
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AggregationResult(
            List<Bucket> buckets,
            Double value,
            Map<String, Double> values,
            List<Hit> hits,
            Map<String, AggregationResult> subAggregations
    ) {
        /** Bucket aggregation constructor. */
        public AggregationResult(List<Bucket> buckets) {
            this(buckets, null, null, null, null);
        }
        /** Metric aggregation constructor (single value). */
        public AggregationResult(Double value) {
            this(null, value, null, null, null);
        }
        /** Multi-value metric (stats, percentiles). */
        public AggregationResult(Map<String, Double> values) {
            this(null, null, values, null, null);
        }
        /** Top hits constructor. */
        public AggregationResult(List<Bucket> buckets, List<Hit> hits) {
            this(buckets, null, null, hits, null);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Bucket(
            String key,
            @JsonProperty("doc_count") long docCount,
            Map<String, AggregationResult> subAggregations
    ) {
        public Bucket(String key, long docCount) {
            this(key, docCount, null);
        }
    }
}
