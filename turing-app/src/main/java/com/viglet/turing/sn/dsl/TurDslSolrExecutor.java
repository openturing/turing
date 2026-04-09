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
                mainParts.add("+(%s)".formatted(translateClause(m)));
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
                String solrField = "_score".equals(field) ? "score" : field;
                sortClauses.add(SolrQuery.SortClause.desc(solrField));
            } else if (entry instanceof Map<?, ?> map) {
                map.forEach((key, val) -> {
                    String field = "_score".equals(key.toString()) ? "score" : key.toString();
                    String direction = "desc";
                    if (val instanceof String s) direction = s;
                    else if (val instanceof Map<?, ?> opts) {
                        Object order = opts.get("order");
                        direction = order != null ? order.toString() : "asc";
                    }
                    sortClauses.add("desc".equalsIgnoreCase(direction)
                            ? SolrQuery.SortClause.desc(field)
                            : SolrQuery.SortClause.asc(field));
                });
            }
        }
        return sortClauses;
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

            if (agg instanceof TurDslAggregation.TermsAgg termsAgg) {
                FacetField facetField = response.getFacetField(termsAgg.field());
                if (facetField != null) {
                    List<TurDslSearchResponse.Bucket> buckets = facetField.getValues().stream()
                            .filter(count -> count.getCount() > 0)
                            .map(count -> new TurDslSearchResponse.Bucket(
                                    count.getName(), count.getCount()))
                            .toList();
                    result.put(aggName, new TurDslSearchResponse.AggregationResult(buckets));
                }
            } else if (agg instanceof TurDslAggregation.RangeAgg rangeAgg) {
                Map<String, Integer> facetQueryMap = response.getFacetQuery();
                if (facetQueryMap != null) {
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
                    result.put(aggName, new TurDslSearchResponse.AggregationResult(buckets));
                }
            }
        }
        return result.isEmpty() ? null : result;
    }

    // ==================== Full Request Translation ====================

    SolrQuery translateRequest(TurDslQueryRequest request) {
        TurDslQuery query = request.query() != null ? request.query() : new TurDslQuery.MatchAll();
        SolrQuery solrQuery = translateQuery(query);

        solrQuery.setStart(request.from() != null ? request.from() : 0);
        solrQuery.setRows(request.size() != null ? request.size() : 10);
        if (solrQuery.getFields() == null) solrQuery.setFields("*", "score");

        if (request.sort() != null) {
            Object sortResult = translateSort(request.sort());
            if (sortResult instanceof List<?> clauses) {
                for (Object clause : clauses) {
                    if (clause instanceof SolrQuery.SortClause sc) {
                        solrQuery.addSort(sc);
                    }
                }
            }
        }

        translateSource(request.source(), solrQuery);
        if (request.highlight() != null) translateHighlight(request.highlight(), solrQuery);
        if (request.aggs() != null && !request.aggs().isEmpty()) {
            translateAggregationParams(request.aggs(), solrQuery);
        }
        if (request.postFilter() != null) {
            solrQuery.addFilterQuery(translateClause(request.postFilter()));
        }
        if (request.minScore() != null) {
            solrQuery.setParam("minScore", request.minScore().toString());
        }
        return solrQuery;
    }

    // ==================== Clause Translation ====================

    String translateClause(TurDslQuery query) {
        return switch (query) {
            case TurDslQuery.MatchAll() -> "*:*";
            case TurDslQuery.Match(var field, var queryText, var operator) -> {
                String[] terms = queryText.trim().split("\\s+");
                if ("and".equalsIgnoreCase(operator)) {
                    yield "%s:(%s)".formatted(field,
                            Arrays.stream(terms)
                                    .map(t -> "+%s".formatted(escapeQuotes(t)))
                                    .collect(Collectors.joining(" ")));
                }
                yield "%s:(%s)".formatted(field, escapeQuotes(queryText));
            }
            case TurDslQuery.MultiMatch(var queryText, var fields, var type) -> {
                if (fields == null || fields.isEmpty()) yield escapeQuotes(queryText);
                yield fields.stream()
                        .map(f -> "%s:(%s)".formatted(f, escapeQuotes(queryText)))
                        .collect(Collectors.joining(" OR ", "(", ")"));
            }
            case TurDslQuery.Term(var field, var value) ->
                    "%s:\"%s\"".formatted(field, escapeQuotes(value.toString()));
            case TurDslQuery.Terms(var field, var values) -> {
                String joined = values.stream()
                        .map(v -> "\"%s\"".formatted(escapeQuotes(v.toString())))
                        .collect(Collectors.joining(" OR "));
                yield "%s:(%s)".formatted(field, joined);
            }
            case TurDslQuery.Bool(var must, var should, var mustNot, var filter, var msm) -> {
                List<String> parts = new ArrayList<>();
                for (var m : must) parts.add("+(%s)".formatted(translateClause(m)));
                for (var s : should) parts.add("(%s)".formatted(translateClause(s)));
                for (var mn : mustNot) parts.add("-(%s)".formatted(translateClause(mn)));
                for (var f : filter) parts.add("+(%s)".formatted(translateClause(f)));
                yield parts.isEmpty() ? "*:*" : String.join(" ", parts);
            }
            case TurDslQuery.Range(var field, var gte, var gt, var lte, var lt) -> {
                String lower = gte != null ? gte.toString() : gt != null ? gt.toString() : "*";
                String upper = lte != null ? lte.toString() : lt != null ? lt.toString() : "*";
                String lBracket = gte != null ? "[" : gt != null ? "{" : "[";
                String rBracket = lte != null ? "]" : lt != null ? "}" : "]";
                yield "%s:%s%s TO %s%s".formatted(field, lBracket, lower, upper, rBracket);
            }
            case TurDslQuery.Wildcard(var field, var value) -> "%s:%s".formatted(field, value);
            case TurDslQuery.Prefix(var field, var value) ->
                    "%s:%s*".formatted(field, escapeQuotes(value));
            case TurDslQuery.Exists(var field) -> "%s:[* TO *]".formatted(field);
            case TurDslQuery.QueryString(var queryText, var defaultField, var defaultOperator) -> {
                if (defaultField != null) yield "%s:(%s)".formatted(defaultField, queryText);
                yield queryText;
            }
            case TurDslQuery.Fuzzy(var field, var value, var fuzziness) -> {
                int fuzz = fuzziness != null ? fuzziness : 2;
                yield "%s:%s~%d".formatted(field, escapeQuotes(value), fuzz);
            }
            case TurDslQuery.Ids(var values) -> {
                String idList = values.stream()
                        .map(v -> "\"%s\"".formatted(escapeQuotes(v)))
                        .collect(Collectors.joining(" OR "));
                yield "id:(%s)".formatted(idList);
            }
            case TurDslQuery.MatchPhrase(var field, var queryText, var slop) ->
                    "%s:\"%s\"".formatted(field, escapeQuotes(queryText));
            case TurDslQuery.MatchPhrasePrefix(var field, var queryText, var maxExp) ->
                    "%s:\"%s\"".formatted(field, escapeQuotes(queryText));
            case TurDslQuery.SimpleQueryString(var queryText, var fields, var defOp) -> {
                if (fields != null && !fields.isEmpty()) {
                    yield fields.stream()
                            .map(f -> "%s:(%s)".formatted(f, escapeQuotes(queryText)))
                            .collect(Collectors.joining(" OR ", "(", ")"));
                }
                yield escapeQuotes(queryText);
            }
            case TurDslQuery.Regexp(var field, var value, var flags) ->
                    "%s:/%s/".formatted(field, value);
            case TurDslQuery.ConstantScore(var filter, var boost) ->
                    translateClause(filter);
            case TurDslQuery.DisMax(var queries, var tieBreaker) -> {
                if (queries.isEmpty()) yield "*:*";
                yield queries.stream()
                        .map(this::translateClause)
                        .collect(Collectors.joining(" OR ", "(", ")"));
            }
            case TurDslQuery.Boosting(var positive, var negative, var negBoost) ->
                    "+(%s) -(%s)".formatted(translateClause(positive), translateClause(negative));
        };
    }

    // ==================== Helpers ====================

    private void translateSource(Object source, SolrQuery solrQuery) {
        if (source == null) return;
        if (source instanceof Boolean b && !b) {
            solrQuery.setFields("id");
            return;
        }
        if (source instanceof List<?> fields) {
            solrQuery.setFields(fields.stream().map(Object::toString).toArray(String[]::new));
            solrQuery.addField("score");
            return;
        }
        if (source instanceof Map<?, ?> map && map.containsKey("includes")) {
            Object includes = map.get("includes");
            if (includes instanceof List<?> list) {
                solrQuery.setFields(list.stream().map(Object::toString).toArray(String[]::new));
                solrQuery.addField("score");
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
                case TurDslAggregation.TermsAgg(var field, var size) -> {
                    solrQuery.addFacetField(field);
                    if (size != null) {
                        solrQuery.setParam("f.%s.facet.limit".formatted(field), size.toString());
                    }
                }
                case TurDslAggregation.RangeAgg(var field, var ranges) -> {
                    for (var range : ranges) {
                        String lower = range.from() != null ? range.from().toString() : "*";
                        String upper = range.to() != null ? range.to().toString() : "*";
                        solrQuery.addFacetQuery("%s:[%s TO %s]".formatted(field, lower, upper));
                    }
                }
                case TurDslAggregation.DateHistogramAgg dh ->
                        log.warn("date_histogram aggregation is not yet supported in Solr DSL translator");
                case TurDslAggregation.AvgAgg a ->
                        solrQuery.setParam("json.facet",
                                "{%s:{type:func,func:\"avg(%s)\"}}".formatted(entry.getKey(), a.field()));
                case TurDslAggregation.SumAgg a ->
                        solrQuery.setParam("json.facet",
                                "{%s:{type:func,func:\"sum(%s)\"}}".formatted(entry.getKey(), a.field()));
                case TurDslAggregation.MinAgg a ->
                        solrQuery.setParam("json.facet",
                                "{%s:{type:func,func:\"min(%s)\"}}".formatted(entry.getKey(), a.field()));
                case TurDslAggregation.MaxAgg a ->
                        solrQuery.setParam("json.facet",
                                "{%s:{type:func,func:\"max(%s)\"}}".formatted(entry.getKey(), a.field()));
                case TurDslAggregation.CardinalityAgg a ->
                        solrQuery.setParam("json.facet",
                                "{%s:{type:func,func:\"unique(%s)\"}}".formatted(entry.getKey(), a.field()));
                case TurDslAggregation.ValueCountAgg a ->
                        solrQuery.addGetFieldStatistics(a.field());
                case TurDslAggregation.HistogramAgg h ->
                        log.warn("histogram aggregation requires Solr JSON facet API — not yet implemented");
                case TurDslAggregation.FilterAgg f ->
                        solrQuery.addFacetQuery(translateClause(f.filter()));
                case TurDslAggregation.StatsAgg a ->
                        solrQuery.addGetFieldStatistics(a.field());
                case TurDslAggregation.ExtendedStatsAgg a ->
                        solrQuery.addGetFieldStatistics(a.field());
                case TurDslAggregation.PercentilesAgg a ->
                        solrQuery.addGetFieldStatistics(a.field());
                case TurDslAggregation.TopHitsAgg th ->
                        log.warn("top_hits aggregation is not supported in Solr DSL translator");
                case TurDslAggregation.CompositeAgg c ->
                        log.warn("composite aggregation is not supported in Solr DSL translator");
                case TurDslAggregation.FiltersAgg f -> {
                    for (var fe : f.filters().entrySet()) {
                        solrQuery.addFacetQuery(translateClause(fe.getValue()));
                    }
                }
                case TurDslAggregation.WithSubAggs(var parent, var subAggs) ->
                        log.warn("Sub-aggregations are not supported in Solr DSL translator");
            }
        }
    }

    private List<TurDslSearchResponse.Hit> buildHits(SolrDocumentList docs,
                                                      Map<String, Map<String, List<String>>> hlMap) {
        if (docs == null) return List.of();
        List<TurDslSearchResponse.Hit> hits = new ArrayList<>();
        for (SolrDocument doc : docs) {
            String id = Optional.ofNullable(doc.getFieldValue("id"))
                    .map(Object::toString).orElse(null);
            Double score = doc.getFieldValue("score") instanceof Number n
                    ? n.doubleValue() : null;

            Map<String, Object> source = new LinkedHashMap<>();
            for (String fieldName : doc.getFieldNames()) {
                if (!"score".equals(fieldName) && !"_version_".equals(fieldName)) {
                    source.put(fieldName, doc.getFieldValue(fieldName));
                }
            }

            Map<String, List<String>> highlight = null;
            if (hlMap != null && id != null && hlMap.containsKey(id)) {
                Map<String, List<String>> docHl = hlMap.get(id);
                if (docHl != null && !docHl.isEmpty()) highlight = docHl;
            }
            hits.add(new TurDslSearchResponse.Hit(id, score, source, highlight));
        }
        return hits;
    }

    private static String escapeQuotes(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
