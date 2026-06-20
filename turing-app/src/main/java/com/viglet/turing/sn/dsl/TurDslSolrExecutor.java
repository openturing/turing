package com.viglet.turing.sn.dsl;

import com.viglet.turing.solr.TurSolr;
import com.viglet.turing.solr.TurSolrInstanceProcess;
import lombok.extern.slf4j.Slf4j;
import org.apache.solr.client.solrj.request.SolrQuery;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Solr implementation of {@link TurDslSearchEngineExecutor}.
 * <p>
 * Translates DSL queries into Solr query syntax and executes them
 * via {@link TurSolrInstanceProcess}.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Slf4j
@Component
public class TurDslSolrExecutor implements TurDslSearchEngineExecutor<SolrQuery, QueryResponse> {

    // --- S1192: extracted duplicated literals ---
    private static final String S = "+(%s)";
    private static final String SCORE = "score";
    private static final String S_S = "%s:(%s)";
    private static final String S_S_2 = "%s:\\\"%s\\\"";
    private static final String S_2 = "\\\"%s\\\"";
    private static final String S_S_3 = "%s:%s*";
    private static final String S_TO = "%s:[* TO *]";
    private static final String F_S_FACET_LIMIT = "f.%s.facet.limit";
    private static final String JSON_FACET = "json.facet";


    private final TurSolrInstanceProcess turSolrInstanceProcess;

    public TurDslSolrExecutor(TurSolrInstanceProcess turSolrInstanceProcess) {
        this.turSolrInstanceProcess = turSolrInstanceProcess;
    }

    @Override
    public String getEngineType() {
        return "solr";
    }

    @Override
    public Optional<TurDslSearchResponse> execute(String siteName, Locale locale,
                                                    TurDslQueryRequest request) {
        return turSolrInstanceProcess.initSolrInstance(siteName, locale)
                .flatMap(solrInstance -> {
                    long startTime = System.currentTimeMillis();
                    try {
                        SolrQuery solrQuery = translateRequest(request);
                        log.debug("DSL translated to Solr: q={}, fq={}",
                                solrQuery.getQuery(), Arrays.toString(solrQuery.getFilterQueries()));
                        return TurSolr.executeSolrQuery(solrInstance, solrQuery)
                                .map(response -> buildResponse(response, request,
                                        System.currentTimeMillis() - startTime));
                    } catch (Exception e) {
                        log.error("DSL Solr query execution failed: {}", e.getMessage(), e);
                        return Optional.empty();
                    }
                });
    }

    // ==================== Interface Methods ====================

    @Override
    public SolrQuery translateQuery(TurDslQuery query) {
        SolrQuery solrQuery = new SolrQuery();
        if (query instanceof TurDslQuery.Bool bool) {
            List<String> mainParts = new ArrayList<>();
            for (TurDslQuery m : bool.must())
                mainParts.add(S.formatted(translateClause(m)));
            for (TurDslQuery s : bool.should())
                mainParts.add("(%s)".formatted(translateClause(s)));
            for (TurDslQuery mn : bool.mustNot())
                mainParts.add("-(%s)".formatted(translateClause(mn)));
            solrQuery.setQuery(mainParts.isEmpty() ? "*:*" : String.join(" ", mainParts));
            for (TurDslQuery f : bool.filter())
                solrQuery.addFilterQuery(translateClause(f));
        } else {
            solrQuery.setQuery(translateClause(query));
        }
        return solrQuery;
    }

    @Override
    public Object translateSort(List<Object> sortEntries) {
        if (sortEntries == null || sortEntries.isEmpty()) return null;
        List<SolrQuery.SortClause> sortClauses = new ArrayList<>();
        for (Object entry : sortEntries) {
            if (entry instanceof String field) {
                String solrField = "_score".equals(field) ? SCORE : field;
                sortClauses.add(SolrQuery.SortClause.desc(solrField));
            } else if (entry instanceof Map<?, ?> map) {
                map.forEach((key, val) -> addSolrMapSort(key, val, sortClauses));
            }
        }
        return sortClauses;
    }

    /**
     * Appends one {@code {field: order}} sort clause, honouring {@code _score},
     * an {@code "asc"/"desc"} string, or a {@code {"order": …}} options map
     * (default ascending). Descending unless explicitly otherwise.
     */
    private void addSolrMapSort(Object key, Object val, List<SolrQuery.SortClause> sortClauses) {
        String field = "_score".equals(key.toString()) ? SCORE : key.toString();
        String direction = "desc";
        if (val instanceof String s) {
            direction = s;
        } else if (val instanceof Map<?, ?> opts) {
            Object order = opts.get("order");
            direction = order != null ? order.toString() : "asc";
        }
        sortClauses.add("desc".equalsIgnoreCase(direction)
                ? SolrQuery.SortClause.desc(field)
                : SolrQuery.SortClause.asc(field));
    }

    @Override
    public TurDslSearchResponse buildResponse(QueryResponse response, TurDslQueryRequest request,
                                               long elapsedMs) {
        SolrDocumentList docs = response.getResults();
        List<TurDslSearchResponse.Hit> hits = buildHits(docs, response.getHighlighting());

        long numFound = docs != null ? docs.getNumFound() : 0;
        Double maxScore = docs != null && docs.getMaxScore() != null
                ? docs.getMaxScore().doubleValue() : null;

        Map<String, TurDslSearchResponse.AggregationResult> aggregations = null;
        if (request.aggs() != null && !request.aggs().isEmpty()) {
            aggregations = buildAggregations(response, request.aggs());
        }

        return new TurDslSearchResponse(elapsedMs, false,
                new TurDslSearchResponse.Hits(
                        new TurDslSearchResponse.Total(numFound, "eq"), maxScore, hits),
                aggregations);
    }

    @Override
    public Map<String, TurDslSearchResponse.AggregationResult> buildAggregations(
            QueryResponse response, Map<String, TurDslAggregation> requestedAggs) {
        Map<String, TurDslSearchResponse.AggregationResult> result = new LinkedHashMap<>();
        for (var entry : requestedAggs.entrySet()) {
            String aggName = entry.getKey();
            TurDslAggregation agg = entry.getValue();

            TurDslSearchResponse.AggregationResult aggResult = null;
            if (agg instanceof TurDslAggregation.TermsAgg termsAgg) {
                aggResult = termsAggResult(response, termsAgg);
            } else if (agg instanceof TurDslAggregation.RangeAgg rangeAgg) {
                aggResult = rangeAggResult(response, rangeAgg);
            }
            if (aggResult != null) {
                result.put(aggName, aggResult);
            }
        }
        return result.isEmpty() ? null : result;
    }

    /**
     * Builds the bucket list for a terms facet from the Solr response, or
     * {@code null} when the facet field is absent.
     *
     * @since 2026.3.1
     */
    private TurDslSearchResponse.AggregationResult termsAggResult(QueryResponse response,
            TurDslAggregation.TermsAgg termsAgg) {
        FacetField facetField = response.getFacetField(termsAgg.field());
        if (facetField == null) {
            return null;
        }
        List<TurDslSearchResponse.Bucket> buckets = facetField.getValues().stream()
                .filter(count -> count.getCount() > 0)
                .map(count -> new TurDslSearchResponse.Bucket(
                        count.getName(), count.getCount()))
                .toList();
        return new TurDslSearchResponse.AggregationResult(buckets);
    }

    /**
     * Builds the bucket list for a range facet from the Solr facet-query map, or
     * {@code null} when no facet queries were returned.
     *
     * @since 2026.3.1
     */
    private TurDslSearchResponse.AggregationResult rangeAggResult(QueryResponse response,
            TurDslAggregation.RangeAgg rangeAgg) {
        Map<String, Integer> facetQueryMap = response.getFacetQuery();
        if (facetQueryMap == null) {
            return null;
        }
        List<TurDslSearchResponse.Bucket> buckets = new ArrayList<>();
        for (var range : rangeAgg.ranges()) {
            String lower = range.from() != null ? range.from().toString() : "*";
            String upper = range.to() != null ? range.to().toString() : "*";
            String fqKey = "%s:[%s TO %s]".formatted(rangeAgg.field(), lower, upper);
            Integer count = facetQueryMap.get(fqKey);
            String key = range.key() != null ? range.key()
                    : "%s-%s".formatted(lower, upper);
            if (count != null) {
                buckets.add(new TurDslSearchResponse.Bucket(key, count));
            }
        }
        return new TurDslSearchResponse.AggregationResult(buckets);
    }

    // ==================== Full Request Translation ====================

    SolrQuery translateRequest(TurDslQueryRequest request) {
        TurDslQuery query = request.query() != null ? request.query() : new TurDslQuery.MatchAll();
        SolrQuery solrQuery = translateQuery(query);

        solrQuery.setStart(request.from() != null ? request.from() : 0);
        solrQuery.setRows(request.size() != null ? request.size() : 10);
        if (solrQuery.getFields() == null) solrQuery.setFields("*", SCORE);

        applySortClauses(request, solrQuery);

        translateSource(request.source(), solrQuery);
        if (request.highlight() != null) translateHighlight(request.highlight(), solrQuery);
        if (request.aggs() != null && !request.aggs().isEmpty()) {
            translateAggregationParams(request.aggs(), solrQuery);
        }
        applyRequestModifiers(request, solrQuery);
        applySpellcheckSuggest(request, solrQuery);
        applyRescoreFallback(request, solrQuery);
        // Script fields, indices_boost, track_total_hits, scroll — not supported in Solr
        return solrQuery;
    }

    /**
     * Applies the post-filter, min-score, collapse (group), timeout, explain
     * (debugQuery), and stored-fields request options to the Solr query.
     *
     * @since 2026.3.1
     */
    private void applyRequestModifiers(TurDslQueryRequest request, SolrQuery solrQuery) {
        if (request.postFilter() != null) {
            solrQuery.addFilterQuery(translateClause(request.postFilter()));
        }
        if (request.minScore() != null) {
            solrQuery.setParam("minScore", request.minScore().toString());
        }
        // Collapse → Solr group.field
        if (request.collapse() != null && request.collapse().field() != null) {
            solrQuery.setParam("group", "true");
            solrQuery.setParam("group.field", request.collapse().field());
            solrQuery.setParam("group.limit", "1");
        }
        // Timeout
        if (request.timeout() != null) {
            solrQuery.setParam("timeAllowed", request.timeout().replaceAll("\\D", ""));
        }
        // Explain → Solr debugQuery
        if (request.explain() != null && request.explain()) {
            solrQuery.setParam("debugQuery", "true");
        }
        // Stored fields
        if (request.storedFields() != null && !request.storedFields().isEmpty()) {
            solrQuery.setParam("fl", String.join(",", request.storedFields()));
        }
    }

    /** Adds each {@link SolrQuery.SortClause} from the translated sort spec. */
    private void applySortClauses(TurDslQueryRequest request, SolrQuery solrQuery) {
        if (request.sort() == null) {
            return;
        }
        Object sortResult = translateSort(request.sort());
        if (sortResult instanceof List<?> clauses) {
            for (Object clause : clauses) {
                if (clause instanceof SolrQuery.SortClause sc) {
                    solrQuery.addSort(sc);
                }
            }
        }
    }

    /** Suggest → Solr spellcheck component (uses the first suggester's text). */
    private void applySpellcheckSuggest(TurDslQueryRequest request, SolrQuery solrQuery) {
        if (request.suggest() == null || request.suggest().isEmpty()) {
            return;
        }
        solrQuery.setParam("spellcheck", "true");
        request.suggest().values().stream().findFirst().ifPresent(s -> {
            if (s.text() != null) solrQuery.setParam("spellcheck.q", s.text());
        });
    }

    /** Rescore is not native to Solr — apply the rescore query as a boost-query fallback. */
    private void applyRescoreFallback(TurDslQueryRequest request, SolrQuery solrQuery) {
        if (request.rescore() == null || request.rescore().isEmpty()) {
            return;
        }
        log.debug("Rescore is not supported in Solr DSL — applying boost query as fallback");
        for (var rescore : request.rescore()) {
            if (rescore.query() != null && rescore.query().rescoreQuery() != null) {
                solrQuery.setParam("bq", translateClause(rescore.query().rescoreQuery()));
            }
        }
    }

    // ==================== Clause Translation ====================

    String translateClause(TurDslQuery query) {
        return switch (query) {
            case TurDslQuery.MatchAll() -> "*:*";
            case TurDslQuery.Match q -> clauseMatch(q);
            case TurDslQuery.MultiMatch q -> clauseMultiMatch(q);
            case TurDslQuery.Term(var field, var value) ->
                    S_S_2.formatted(field, escapeQuotes(value.toString()));
            case TurDslQuery.Terms q -> clauseTerms(q);
            case TurDslQuery.Bool q -> clauseBool(q);
            case TurDslQuery.Range q -> clauseRange(q);
            case TurDslQuery.Wildcard(var field, var value) -> "%s:%s".formatted(field, value);
            case TurDslQuery.Prefix(var field, var value) ->
                    S_S_3.formatted(field, escapeQuotes(value));
            case TurDslQuery.Exists(var field) -> S_TO.formatted(field);
            case TurDslQuery.QueryString q -> clauseQueryString(q);
            case TurDslQuery.Fuzzy q -> clauseFuzzy(q);
            case TurDslQuery.Ids q -> clauseIds(q);
            case TurDslQuery.MatchPhrase(var field, var queryText, var slop) ->
                    S_S_2.formatted(field, escapeQuotes(queryText));
            case TurDslQuery.MatchPhrasePrefix(var field, var queryText, var maxExp) ->
                    S_S_2.formatted(field, escapeQuotes(queryText));
            case TurDslQuery.SimpleQueryString q -> clauseSimpleQueryString(q);
            case TurDslQuery.Regexp(var field, var value, var flags) ->
                    "%s:/%s/".formatted(field, value);
            case TurDslQuery.ConstantScore(var filter, var boost) ->
                    translateClause(filter);
            case TurDslQuery.DisMax q -> clauseDisMax(q);
            case TurDslQuery.Boosting(var positive, var negative, var negBoost) ->
                    "+(%s) -(%s)".formatted(translateClause(positive), translateClause(negative));
            // Solr: {!parent} or flatten — best effort: translate inner query
            case TurDslQuery.Nested(var path, var nestedQuery, var scoreMode) ->
                    translateClause(nestedQuery);
            case TurDslQuery.FunctionScore q -> clauseFunctionScore(q);
            case TurDslQuery.ScriptScore(var innerQuery, var script, var ms) ->
                    translateClause(innerQuery);
            case TurDslQuery.Knn q -> clauseKnn(q);
            case TurDslQuery.MoreLikeThis q -> clauseMoreLikeThis(q);
            case TurDslQuery.CombinedFields q -> clauseCombinedFields(q);
            case TurDslQuery.MatchBoolPrefix q -> clauseMatchBoolPrefix(q);
            // Less-common query families (pinned / geo / span / join / feature / wrapper)
            // live in a sibling switch to keep each below the case-count limit.
            default -> translateClauseExtended(query);
        };
    }

    /** Second-tier dispatch for the rarer query families — see {@link #translateClause}. */
    private String translateClauseExtended(TurDslQuery query) {
        return switch (query) {
            case TurDslQuery.Pinned q -> clausePinned(q);
            case TurDslQuery.GeoDistance q -> clauseGeoDistance(q);
            case TurDslQuery.GeoBoundingBox q -> clauseGeoBoundingBox(q);
            case TurDslQuery.TermsSet q -> clauseTermsSet(q);
            // --- Low-priority queries (best-effort / translate inner query) ---
            case TurDslQuery.HasChild(var type, var childQuery, var sm, var minC, var maxC) ->
                    translateClause(childQuery);
            case TurDslQuery.HasParent(var parentType, var parentQuery, var score) ->
                    translateClause(parentQuery);
            case TurDslQuery.Intervals(var field, var rule) ->
                    rule != null && rule.query() != null
                            ? S_S_2.formatted(field, escapeQuotes(rule.query()))
                            : S_TO.formatted(field);
            case TurDslQuery.SpanTerm(var field, var value) ->
                    S_S_2.formatted(field, escapeQuotes(value));
            case TurDslQuery.SpanNear q -> clauseSpanJoin(q.clauses(), " AND ");
            case TurDslQuery.SpanOr(var clauses) -> clauseSpanJoin(clauses, " OR ");
            case TurDslQuery.SpanNot(var include, var exclude) ->
                    "+(%s) -(%s)".formatted(translateClause(include), translateClause(exclude));
            case TurDslQuery.SpanFirst(var match, var end) -> translateClause(match);
            case TurDslQuery.RankFeature(var field, var boost, var s1, var s2, var s3) ->
                    S_TO.formatted(field);
            case TurDslQuery.DistanceFeature(var field, var origin, var pivot) ->
                    S_TO.formatted(field);
            case TurDslQuery.Wrapper(var encodedQuery) -> "*:*";
            case TurDslQuery.GeoShape(var field, var shape, var relation) ->
                    S_TO.formatted(field);
            case TurDslQuery.Percolate(var field, var document) -> "*:*";
            default -> throw new IllegalStateException("Unexpected DSL type: " + query);
        };
    }

    // ============ Per-query-type clause translators (S3776: keep the switch flat) ============

    private String clauseMatch(TurDslQuery.Match q) {
        String[] terms = q.query().trim().split("\\s+");
        if ("and".equalsIgnoreCase(q.operator())) {
            return S_S.formatted(q.field(),
                    Arrays.stream(terms)
                            .map(t -> "+%s".formatted(escapeQuotes(t)))
                            .collect(Collectors.joining(" ")));
        }
        return S_S.formatted(q.field(), escapeQuotes(q.query()));
    }

    private String clauseMultiMatch(TurDslQuery.MultiMatch q) {
        if (q.fields() == null || q.fields().isEmpty()) return escapeQuotes(q.query());
        return q.fields().stream()
                .map(f -> S_S.formatted(f, escapeQuotes(q.query())))
                .collect(Collectors.joining(" OR ", "(", ")"));
    }

    private String clauseTerms(TurDslQuery.Terms q) {
        String joined = q.values().stream()
                .map(v -> S_2.formatted(escapeQuotes(v.toString())))
                .collect(Collectors.joining(" OR "));
        return S_S.formatted(q.field(), joined);
    }

    private String clauseBool(TurDslQuery.Bool q) {
        List<String> parts = new ArrayList<>();
        for (var m : q.must()) parts.add(S.formatted(translateClause(m)));
        for (var s : q.should()) parts.add("(%s)".formatted(translateClause(s)));
        for (var mn : q.mustNot()) parts.add("-(%s)".formatted(translateClause(mn)));
        for (var f : q.filter()) parts.add(S.formatted(translateClause(f)));
        return parts.isEmpty() ? "*:*" : String.join(" ", parts);
    }

    private String clauseRange(TurDslQuery.Range q) {
        String lower = solrRangeBound(q.gte(), q.gt());
        String upper = solrRangeBound(q.lte(), q.lt());
        String lBracket = (q.gte() == null && q.gt() != null) ? "{" : "[";
        String rBracket = (q.lte() == null && q.lt() != null) ? "}" : "]";
        return "%s:%s%s TO %s%s".formatted(q.field(), lBracket, lower, upper, rBracket);
    }

    private String clauseQueryString(TurDslQuery.QueryString q) {
        if (q.defaultField() != null) return S_S.formatted(q.defaultField(), q.query());
        return q.query();
    }

    private String clauseFuzzy(TurDslQuery.Fuzzy q) {
        int fuzz = q.fuzziness() != null ? q.fuzziness() : 2;
        return "%s:%s~%d".formatted(q.field(), escapeQuotes(q.value()), fuzz);
    }

    private String clauseIds(TurDslQuery.Ids q) {
        String idList = q.values().stream()
                .map(v -> S_2.formatted(escapeQuotes(v)))
                .collect(Collectors.joining(" OR "));
        return "id:(%s)".formatted(idList);
    }

    private String clauseSimpleQueryString(TurDslQuery.SimpleQueryString q) {
        if (q.fields() != null && !q.fields().isEmpty()) {
            return q.fields().stream()
                    .map(f -> S_S.formatted(f, escapeQuotes(q.query())))
                    .collect(Collectors.joining(" OR ", "(", ")"));
        }
        return escapeQuotes(q.query());
    }

    private String clauseDisMax(TurDslQuery.DisMax q) {
        if (q.queries().isEmpty()) return "*:*";
        return q.queries().stream()
                .map(this::translateClause)
                .collect(Collectors.joining(" OR ", "(", ")"));
    }

    private String clauseFunctionScore(TurDslQuery.FunctionScore q) {
        // Solr: boost query as fallback
        String base = translateClause(q.query());
        if (q.functions() != null && !q.functions().isEmpty()) {
            for (var fn : q.functions()) {
                if (fn.fieldValueFactor() != null) {
                    base = "{!boost b=mul(%s,%s)}%s".formatted(
                            fn.fieldValueFactor().field(),
                            fn.fieldValueFactor().factor() != null
                                    ? fn.fieldValueFactor().factor() : "1",
                            base);
                    break;
                }
            }
        }
        return base;
    }

    private String clauseKnn(TurDslQuery.Knn q) {
        return "{!knn f=%s topK=%d}[%s]".formatted(q.field(), q.k(),
                q.queryVector().stream().map(String::valueOf)
                        .collect(Collectors.joining(",")));
    }

    private String clauseMoreLikeThis(TurDslQuery.MoreLikeThis q) {
        // Solr: {!mlt} query parser
        String fl = (q.fields() != null && !q.fields().isEmpty())
                ? String.join(",", q.fields()) : "*";
        return q.likeText() != null
                ? "{!mlt qf=%s}%s".formatted(fl, escapeQuotes(q.likeText()))
                : "*:*";
    }

    private String clauseCombinedFields(TurDslQuery.CombinedFields q) {
        if (q.fields() == null || q.fields().isEmpty()) return escapeQuotes(q.query());
        return q.fields().stream()
                .map(f -> S_S.formatted(f, escapeQuotes(q.query())))
                .collect(Collectors.joining(
                        "and".equalsIgnoreCase(q.operator()) ? " AND " : " OR ",
                        "(", ")"));
    }

    private String clauseMatchBoolPrefix(TurDslQuery.MatchBoolPrefix q) {
        String[] tokens = q.query().trim().split("\\s+");
        if (tokens.length <= 1) return S_S_3.formatted(q.field(), escapeQuotes(q.query()));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.length - 1; i++) {
            sb.append("+%s:%s ".formatted(q.field(), escapeQuotes(tokens[i])));
        }
        sb.append(S_S_3.formatted(q.field(), escapeQuotes(tokens[tokens.length - 1])));
        return sb.toString();
    }

    private String clausePinned(TurDslQuery.Pinned q) {
        // Solr: elevate or boost pinned IDs
        String idBoost = q.ids().stream()
                .map(id -> "id:\"%s\"^1000".formatted(escapeQuotes(id)))
                .collect(Collectors.joining(" OR "));
        String base = q.organic() != null ? translateClause(q.organic()) : "*:*";
        return "(%s) OR (%s)".formatted(idBoost, base);
    }

    private String clauseGeoDistance(TurDslQuery.GeoDistance q) {
        if (q.location() != null && q.distance() != null) {
            return "{!geofilt sfield=%s pt=%s,%s d=%s}".formatted(
                    q.field(), q.location().lat(), q.location().lon(),
                    q.distance().replaceAll("[^0-9.]", ""));
        }
        return "*:*";
    }

    private String clauseGeoBoundingBox(TurDslQuery.GeoBoundingBox q) {
        if (q.topLeft() != null && q.bottomRight() != null) {
            return "%s:[%s,%s TO %s,%s]".formatted(q.field(),
                    q.bottomRight().lat(), q.topLeft().lon(),
                    q.topLeft().lat(), q.bottomRight().lon());
        }
        return "*:*";
    }

    private String clauseTermsSet(TurDslQuery.TermsSet q) {
        String joined = q.terms().stream()
                .map(v -> S_2.formatted(escapeQuotes(v.toString())))
                .collect(Collectors.joining(" OR "));
        return S_S.formatted(q.field(), joined);
    }

    private String clauseSpanJoin(List<TurDslQuery> clauses, String delimiter) {
        if (clauses.isEmpty()) return "*:*";
        return clauses.stream().map(this::translateClause)
                .collect(Collectors.joining(delimiter, "(", ")"));
    }

    // ==================== Helpers ====================

    private void translateSource(Object source, SolrQuery solrQuery) {
        if (source == null) return;
        if (Boolean.FALSE.equals(source)) {
            solrQuery.setFields("id");
            return;
        }
        if (source instanceof List<?> fields) {
            solrQuery.setFields(fields.stream().map(Object::toString).toArray(String[]::new));
            solrQuery.addField(SCORE);
            return;
        }
        if (source instanceof Map<?, ?> map && map.containsKey("includes")) {
            Object includes = map.get("includes");
            if (includes instanceof List<?> list) {
                solrQuery.setFields(list.stream().map(Object::toString).toArray(String[]::new));
                solrQuery.addField(SCORE);
            }
        }
    }

    private void translateHighlight(TurDslQueryRequest.TurDslHighlight hl, SolrQuery solrQuery) {
        solrQuery.setHighlight(true);
        if (hl.fragmentSize() != null) solrQuery.setHighlightFragsize(hl.fragmentSize());
        if (hl.numberOfFragments() != null) solrQuery.setHighlightSnippets(hl.numberOfFragments());
        if (hl.preTags() != null && !hl.preTags().isEmpty())
            solrQuery.setParam("hl.tag.pre", hl.preTags().getFirst());
        if (hl.postTags() != null && !hl.postTags().isEmpty())
            solrQuery.setParam("hl.tag.post", hl.postTags().getFirst());
        if (hl.fields() != null) hl.fields().keySet().forEach(solrQuery::addHighlightField);
    }

    private void translateAggregationParams(Map<String, TurDslAggregation> aggs, SolrQuery solrQuery) {
        solrQuery.setFacet(true);
        for (var entry : aggs.entrySet()) {
            switch (entry.getValue()) {
                case TurDslAggregation.TermsAgg agg -> applyTermsFacet(agg, solrQuery);
                case TurDslAggregation.RangeAgg agg -> applyRangeFacet(agg, solrQuery);
                case TurDslAggregation.DateHistogramAgg dh ->
                        log.warn("date_histogram aggregation is not yet supported in Solr DSL translator");
                case TurDslAggregation.AvgAgg(String field) ->
                        solrQuery.setParam(JSON_FACET,
                                "{%s:{type:func,func:\"avg(%s)\"}}".formatted(entry.getKey(), field));
                case TurDslAggregation.SumAgg(String field) ->
                        solrQuery.setParam(JSON_FACET,
                                "{%s:{type:func,func:\"sum(%s)\"}}".formatted(entry.getKey(), field));
                case TurDslAggregation.MinAgg(String field) ->
                        solrQuery.setParam(JSON_FACET,
                                "{%s:{type:func,func:\"min(%s)\"}}".formatted(entry.getKey(), field));
                case TurDslAggregation.MaxAgg(String field) ->
                        solrQuery.setParam(JSON_FACET,
                                "{%s:{type:func,func:\"max(%s)\"}}".formatted(entry.getKey(), field));
                case TurDslAggregation.CardinalityAgg a ->
                        solrQuery.setParam(JSON_FACET,
                                "{%s:{type:func,func:\"unique(%s)\"}}".formatted(entry.getKey(), a.field()));
                case TurDslAggregation.ValueCountAgg(String field) ->
                        solrQuery.addGetFieldStatistics(field);
                case TurDslAggregation.HistogramAgg h ->
                        log.warn("histogram aggregation requires Solr JSON facet API — not yet implemented");
                case TurDslAggregation.FilterAgg(var filter) ->
                        solrQuery.addFacetQuery(translateClause(filter));
                // (single-clause FilterAgg above; multi-clause FiltersAgg below)
                case TurDslAggregation.StatsAgg(String field) ->
                        solrQuery.addGetFieldStatistics(field);
                case TurDslAggregation.ExtendedStatsAgg(String field) ->
                        solrQuery.addGetFieldStatistics(field);
                case TurDslAggregation.PercentilesAgg a ->
                        solrQuery.addGetFieldStatistics(a.field());
                case TurDslAggregation.TopHitsAgg th ->
                        log.warn("top_hits aggregation is not supported in Solr DSL translator");
                case TurDslAggregation.CompositeAgg c ->
                        log.warn("composite aggregation is not supported in Solr DSL translator");
                case TurDslAggregation.FiltersAgg agg -> applyFiltersFacet(agg, solrQuery);
                case TurDslAggregation.WithSubAggs(var parent, var subAggs) ->
                        log.warn("Sub-aggregations are not supported in Solr DSL translator");
                case TurDslAggregation.SignificantTermsAgg agg -> applySignificantTermsFacet(agg, solrQuery);
                case TurDslAggregation.NestedAgg n ->
                        log.warn("nested aggregation is not supported in Solr DSL translator");
                case TurDslAggregation.ReverseNestedAgg rn ->
                        log.warn("reverse_nested aggregation is not supported in Solr DSL translator");
                case TurDslAggregation.AutoDateHistogramAgg adh ->
                        log.warn("auto_date_histogram is not supported in Solr DSL translator");
                case TurDslAggregation.MultiTermsAgg mt ->
                        log.warn("multi_terms aggregation is not supported in Solr DSL translator");
                case TurDslAggregation.RareTermsAgg agg -> applyRareTermsFacet(agg, solrQuery);
                // Less-common aggregation families live in a sibling switch to
                // keep each below the case-count limit.
                default -> translateAggregationParamsExtended(entry.getValue(), solrQuery);
            }
        }
    }

    /** Second-tier dispatch for the rarer aggregation families — see {@link #translateAggregationParams}. */
    private void translateAggregationParamsExtended(TurDslAggregation aggregation, SolrQuery solrQuery) {
        switch (aggregation) {
            case TurDslAggregation.TopMetricsAgg tm ->
                    log.warn("top_metrics aggregation is not supported in Solr DSL translator");
            case TurDslAggregation.PercentileRanksAgg pr ->
                    solrQuery.addGetFieldStatistics(pr.field());
            case TurDslAggregation.GeoDistanceAgg gd ->
                    log.warn("geo_distance agg not supported in Solr DSL");
            case TurDslAggregation.GeoBoundsAgg gb ->
                    log.warn("geo_bounds agg not supported in Solr DSL");
            case TurDslAggregation.GeoCentroidAgg gc ->
                    log.warn("geo_centroid agg not supported in Solr DSL");
            case TurDslAggregation.SamplerAgg s ->
                    log.warn("sampler agg not supported in Solr DSL");
            case TurDslAggregation.DiversifiedSamplerAgg ds ->
                    log.warn("diversified_sampler agg not supported in Solr DSL");
            case TurDslAggregation.AdjacencyMatrixAgg am ->
                    log.warn("adjacency_matrix agg not supported in Solr DSL");
            case TurDslAggregation.ScriptedMetricAgg sm ->
                    log.warn("scripted_metric agg not supported in Solr DSL");
            case TurDslAggregation.MedianAbsoluteDeviationAgg mad ->
                    log.warn("median_absolute_deviation agg not supported in Solr DSL");
            case TurDslAggregation.MatrixStatsAgg ms ->
                    log.warn("matrix_stats agg not supported in Solr DSL");
            case TurDslAggregation.BoxplotAgg bp ->
                    log.warn("boxplot agg not supported in Solr DSL");
            case TurDslAggregation.StringStatsAgg ss ->
                    log.warn("string_stats agg not supported in Solr DSL");
            case TurDslAggregation.TTestAgg tt ->
                    log.warn("t_test agg not supported in Solr DSL");
            case TurDslAggregation.VariableWidthHistogramAgg vwh ->
                    log.warn("variable_width_histogram agg not supported in Solr DSL");
            case TurDslAggregation.RateAgg r ->
                    log.warn("rate agg not supported in Solr DSL");
            default -> throw new IllegalStateException("Unexpected DSL type: " + aggregation);
        }
    }

    private void applyTermsFacet(TurDslAggregation.TermsAgg agg, SolrQuery solrQuery) {
        solrQuery.addFacetField(agg.field());
        if (agg.size() != null) {
            solrQuery.setParam(F_S_FACET_LIMIT.formatted(agg.field()), agg.size().toString());
        }
    }

    private void applyRangeFacet(TurDslAggregation.RangeAgg agg, SolrQuery solrQuery) {
        for (var range : agg.ranges()) {
            String lower = range.from() != null ? range.from().toString() : "*";
            String upper = range.to() != null ? range.to().toString() : "*";
            solrQuery.addFacetQuery("%s:[%s TO %s]".formatted(agg.field(), lower, upper));
        }
    }

    private void applyFiltersFacet(TurDslAggregation.FiltersAgg agg, SolrQuery solrQuery) {
        for (var fe : agg.filters().entrySet()) {
            solrQuery.addFacetQuery(translateClause(fe.getValue()));
        }
    }

    private void applySignificantTermsFacet(TurDslAggregation.SignificantTermsAgg agg, SolrQuery solrQuery) {
        solrQuery.addFacetField(agg.field());
        if (agg.size() != null) {
            solrQuery.setParam(F_S_FACET_LIMIT.formatted(agg.field()), agg.size().toString());
        }
    }

    private void applyRareTermsFacet(TurDslAggregation.RareTermsAgg agg, SolrQuery solrQuery) {
        solrQuery.addFacetField(agg.field());
        solrQuery.setParam("f.%s.facet.sort".formatted(agg.field()), "count");
        solrQuery.setParam(F_S_FACET_LIMIT.formatted(agg.field()),
                agg.maxDocCount() != null ? agg.maxDocCount().toString() : "10");
    }

    private List<TurDslSearchResponse.Hit> buildHits(SolrDocumentList docs,
                                                      Map<String, Map<String, List<String>>> hlMap) {
        if (docs == null) return List.of();
        List<TurDslSearchResponse.Hit> hits = new ArrayList<>();
        for (SolrDocument doc : docs) {
            hits.add(buildHit(doc, hlMap));
        }
        return hits;
    }

    private TurDslSearchResponse.Hit buildHit(SolrDocument doc,
            Map<String, Map<String, List<String>>> hlMap) {
        String id = Optional.ofNullable(doc.getFieldValue("id"))
                .map(Object::toString).orElse(null);
        Double score = doc.getFieldValue(SCORE) instanceof Number n
                ? n.doubleValue() : null;

        Map<String, Object> source = new LinkedHashMap<>();
        for (String fieldName : doc.getFieldNames()) {
            if (!SCORE.equals(fieldName) && !"_version_".equals(fieldName)) {
                source.put(fieldName, doc.getFieldValue(fieldName));
            }
        }

        Map<String, List<String>> highlight = null;
        if (hlMap != null && id != null && hlMap.containsKey(id)) {
            Map<String, List<String>> docHl = hlMap.get(id);
            if (docHl != null && !docHl.isEmpty()) highlight = docHl;
        }
        return new TurDslSearchResponse.Hit(id, score, source, highlight);
    }

    private static String escapeQuotes(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * First non-null range bound rendered as a Solr range token
     * ({@code inclusive} wins over {@code exclusive}), or {@code "*"} (open
     * bound) when both are absent.
     */
    private static String solrRangeBound(Object inclusive, Object exclusive) {
        if (inclusive != null) {
            return inclusive.toString();
        }
        if (exclusive != null) {
            return exclusive.toString();
        }
        return "*";
    }
}
