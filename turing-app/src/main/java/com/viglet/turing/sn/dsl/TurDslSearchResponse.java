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
        Map<String, AggregationResult> aggregations
) {
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
    public record AggregationResult(List<Bucket> buckets) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Bucket(
            String key,
            @JsonProperty("doc_count") long docCount
    ) {}
}
