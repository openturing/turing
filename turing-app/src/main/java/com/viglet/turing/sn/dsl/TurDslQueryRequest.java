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
        Map<String, TurDslAggregation> aggs
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
}
