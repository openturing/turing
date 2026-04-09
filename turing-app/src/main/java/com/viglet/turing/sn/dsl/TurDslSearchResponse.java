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
        Map<String, List<SuggestResult>> suggest,
        @JsonProperty("_scroll_id") String scrollId,
        // --- Low-priority response fields ---
        Map<String, Object> profile,
        @JsonProperty("pit_id") String pitId,
        @JsonProperty("_shards") ShardInfo shards,
        @JsonProperty("terminated_early") Boolean terminatedEarly,
        @JsonProperty("num_reduce_phases") Integer numReducePhases
) {
    /** Constructor without suggest/scroll/low-priority (backward-compatible). */
    public TurDslSearchResponse(long took, boolean timedOut, Hits hits,
                                 Map<String, AggregationResult> aggregations) {
        this(took, timedOut, hits, aggregations, null, null, null, null, null, null, null);
    }
    /** Constructor with suggest but no scroll/low-priority. */
    public TurDslSearchResponse(long took, boolean timedOut, Hits hits,
                                 Map<String, AggregationResult> aggregations,
                                 Map<String, List<SuggestResult>> suggest) {
        this(took, timedOut, hits, aggregations, suggest, null, null, null, null, null, null);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ShardInfo(int total, int successful, int skipped, int failed) {}

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
            Map<String, List<String>> highlight,
            @JsonProperty("_explanation") Map<String, Object> explanation,
            Map<String, Object> fields,
            @JsonProperty("matched_queries") List<String> matchedQueries,
            @JsonProperty("_version") Long version,
            @JsonProperty("_seq_no") Long seqNo,
            @JsonProperty("_primary_term") Long primaryTerm,
            List<Object> sort
    ) {
        /** Backward-compatible constructor. */
        public Hit(String id, Double score, Map<String, Object> source,
                   Map<String, List<String>> highlight) {
            this(id, score, source, highlight, null, null, null, null, null, null, null);
        }
    }

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
