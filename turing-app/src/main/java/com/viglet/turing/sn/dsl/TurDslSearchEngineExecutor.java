package com.viglet.turing.sn.dsl;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Strategy interface for executing DSL search queries against a specific
 * search engine backend.
 * <p>
 * Each implementation handles the full lifecycle: query translation,
 * execution, highlighting, aggregations, and response building.
 * <p>
 * Uses the <b>Template Method</b> pattern — {@link #execute(String, Locale, TurDslQueryRequest)}
 * provides the public contract, while subclasses implement the engine-specific
 * translation ({@link #translateQuery}, {@link #translateSort}),
 * response building ({@link #buildResponse}), and aggregation ({@link #buildAggregations})
 * methods.
 *
 * @param <Q> the native query type (e.g. {@code SolrQuery}, {@code org.apache.lucene.search.Query})
 * @param <R> the native result type (e.g. {@code QueryResponse}, {@code TopDocs})
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
public interface TurDslSearchEngineExecutor<Q, R> {

    /**
     * Returns the engine type this executor handles (e.g. "solr", "lucene").
     */
    String getEngineType();

    /**
     * Executes a DSL search request end-to-end: resolves the engine instance,
     * translates the query, executes it, and builds the ES-compatible response.
     *
     * @param siteName the SN site name
     * @param locale   the search locale
     * @param request  the DSL query request
     * @return the search response, or empty if the site/engine is not available
     */
    Optional<TurDslSearchResponse> execute(String siteName, Locale locale, TurDslQueryRequest request);

    /**
     * Translates a single DSL query clause into the engine's native query type.
     *
     * @param query the DSL query clause
     * @return the native query representation
     */
    Q translateQuery(TurDslQuery query);

    /**
     * Translates the DSL sort specification into the engine's native sort format.
     *
     * @param sortEntries the sort entries from the DSL request (may be null)
     * @return the native sort representation, or null for default relevance sort
     */
    Object translateSort(java.util.List<Object> sortEntries);

    /**
     * Builds the ES-compatible search response from the engine's native result.
     *
     * @param nativeResult the native engine result
     * @param request      the original DSL request (for aggregation/highlight config)
     * @param elapsedMs    elapsed execution time in milliseconds
     * @return the ES-compatible response
     */
    TurDslSearchResponse buildResponse(R nativeResult, TurDslQueryRequest request, long elapsedMs);

    /**
     * Builds aggregation results from the engine's native result set.
     *
     * @param nativeResult the native engine result
     * @param aggs         the requested aggregations
     * @return aggregation results map, or null if empty
     */
    Map<String, TurDslSearchResponse.AggregationResult> buildAggregations(
            R nativeResult, Map<String, TurDslAggregation> aggs);
}
