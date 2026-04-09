package com.viglet.turing.sn.dsl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Root request body for the Query DSL endpoint, following the Elasticsearch
 * {@code _search} request format.
 * <p>
 * Example:
 * <pre>{@code
 * {
 *   "query": { "match": { "title": "search text" } },
 *   "from": 0,
 *   "size": 10,
 *   "sort": [{ "date": "desc" }, "_score"],
 *   "_source": ["title", "description"],
 *   "highlight": { "fields": { "title": {}, "body": {} } },
 *   "aggs": { "by_category": { "terms": { "field": "category", "size": 10 } } }
 * }
 * }</pre>
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TurDslQueryRequest(
        TurDslQuery query,
        Integer from,
        Integer size,
        List<Object> sort,
        @JsonProperty("_source") Object source,
        TurDslHighlight highlight,
        Map<String, TurDslAggregation> aggs,
        @JsonProperty("post_filter") TurDslQuery postFilter,
        @JsonProperty("min_score") Double minScore,
        @JsonProperty("search_after") List<Object> searchAfter,
        TurDslCollapse collapse,
        Map<String, TurDslSuggest> suggest,
        List<TurDslRescore> rescore,
        String timeout,
        // --- Medium-priority features ---
        Boolean explain,
        @JsonProperty("script_fields") Map<String, TurDslScriptField> scriptFields,
        @JsonProperty("indices_boost") List<Map<String, Double>> indicesBoost,
        @JsonProperty("track_total_hits") Object trackTotalHits,
        @JsonProperty("stored_fields") List<String> storedFields,
        String scroll,
        // --- Low-priority features ---
        Boolean profile,
        TurDslPit pit,
        @JsonProperty("docvalue_fields") List<Object> docvalueFields,
        Boolean version,
        @JsonProperty("seq_no_primary_term") Boolean seqNoPrimaryTerm,
        String preference,
        String routing,
        @JsonProperty("search_type") String searchType,
        @JsonProperty("terminate_after") Integer terminateAfter
) {
    /**
     * Highlight configuration following the Elasticsearch format.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TurDslHighlight(
            Map<String, Object> fields,
            @JsonProperty("pre_tags") List<String> preTags,
            @JsonProperty("post_tags") List<String> postTags,
            @JsonProperty("fragment_size") Integer fragmentSize,
            @JsonProperty("number_of_fragments") Integer numberOfFragments
    ) {}

    /**
     * Field collapsing configuration.
     * <pre>{@code "collapse": { "field": "user_id", "inner_hits": { "name": "recent", "size": 3 } } }</pre>
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TurDslCollapse(
            String field,
            @JsonProperty("max_concurrent_group_searches") Integer maxConcurrentGroupSearches,
            @JsonProperty("inner_hits") TurDslInnerHits innerHits
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TurDslInnerHits(
            String name,
            Integer size,
            List<Object> sort,
            @JsonProperty("_source") Object source
    ) {}

    /**
     * Suggest configuration (term, phrase, or completion).
     * <pre>{@code "suggest": { "my-suggest": { "text": "tring", "term": { "field": "title" } } } }</pre>
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TurDslSuggest(
            String text,
            TurDslTermSuggest term,
            TurDslPhraseSuggest phrase,
            TurDslCompletionSuggest completion
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TurDslTermSuggest(String field, Integer size) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TurDslPhraseSuggest(String field, Integer size) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TurDslCompletionSuggest(
            String field, Integer size,
            @JsonProperty("skip_duplicates") Boolean skipDuplicates,
            @JsonProperty("fuzzy") TurDslCompletionFuzzy fuzzy
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TurDslCompletionFuzzy(Integer fuzziness) {}

    /**
     * Rescore configuration for re-scoring top N results.
     * <pre>{@code "rescore": { "window_size": 50, "query": { "rescore_query": { "match_phrase": ... }, "query_weight": 0.7, "rescore_query_weight": 1.2 } } }</pre>
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TurDslRescore(
            @JsonProperty("window_size") Integer windowSize,
            TurDslRescoreQuery query
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TurDslRescoreQuery(
            @JsonProperty("rescore_query") TurDslQuery rescoreQuery,
            @JsonProperty("query_weight") Double queryWeight,
            @JsonProperty("rescore_query_weight") Double rescoreQueryWeight,
            @JsonProperty("score_mode") String scoreMode
    ) {}

    /**
     * Script field definition.
     * <pre>{@code "script_fields": { "my_field": { "script": { "source": "doc['price'].value * 2" } } } }</pre>
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TurDslScriptField(TurDslQuery.Script script) {}

    /**
     * Point in time reference for consistent pagination.
     * <pre>{@code "pit": { "id": "abc123", "keep_alive": "5m" } }</pre>
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TurDslPit(
            String id,
            @JsonProperty("keep_alive") String keepAlive
    ) {}
}
