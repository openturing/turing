package com.viglet.turing.api.sn.dsl;

import com.viglet.turing.sn.dsl.TurDslQueryRequest;
import com.viglet.turing.sn.dsl.TurDslSearchResponse;
import com.viglet.turing.sn.dsl.TurDslSearchService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoint for Elasticsearch-compatible Query DSL searches.
 * <p>
 * Provides the same {@code _search} contract used by Elasticsearch, translating
 * queries to the configured search backend (currently Apache Solr).
 * <p>
 * Usage examples:
 * <pre>{@code
 * # Match query
 * POST /api/sn/MySite/_search?locale=en
 * { "query": { "match": { "title": "enterprise search" } } }
 *
 * # Bool query with filter and aggregations
 * POST /api/sn/MySite/_search?locale=en
 * {
 *   "query": {
 *     "bool": {
 *       "must": [{ "match": { "title": "enterprise search" } }],
 *       "filter": [{ "term": { "status": "published" } }]
 *     }
 *   },
 *   "from": 0,
 *   "size": 10,
 *   "sort": [{ "date": "desc" }],
 *   "aggs": {
 *     "categories": { "terms": { "field": "category", "size": 5 } }
 *   }
 * }
 *
 * # Match all (GET without body)
 * GET /api/sn/MySite/_search?locale=en
 * }</pre>
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@RestController
@RequestMapping("/api/sn/{siteName}/_search")
public class TurSNSiteDslSearchAPI {

    private final TurDslSearchService turDslSearchService;

    public TurSNSiteDslSearchAPI(TurDslSearchService turDslSearchService) {
        this.turDslSearchService = turDslSearchService;
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TurDslSearchResponse> search(
            @PathVariable String siteName,
            @RequestParam(name = "locale", defaultValue = "en") String locale,
            @RequestBody TurDslQueryRequest request) {
        return turDslSearchService.search(siteName, locale, request)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TurDslSearchResponse> searchGet(
            @PathVariable String siteName,
            @RequestParam(name = "locale", defaultValue = "en") String locale) {
        return search(siteName, locale,
                new TurDslQueryRequest(null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null,
                        null, null, null, null, null, null,
                        null, null, null, null, null, null, null, null, null));
    }
}
