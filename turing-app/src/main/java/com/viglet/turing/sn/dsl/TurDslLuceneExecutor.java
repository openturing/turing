package com.viglet.turing.sn.dsl;

import com.viglet.turing.lucene.TurLuceneInstanceProcess;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.search.uhighlight.UnifiedHighlighter;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Lucene implementation of {@link TurDslSearchEngineExecutor}.
 * <p>
 * Translates DSL queries into Lucene {@link Query} objects and executes them
 * against the embedded Lucene index via {@link TurLuceneInstanceProcess}.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Slf4j
@Component
public class TurDslLuceneExecutor implements TurDslSearchEngineExecutor<Query, TopDocs> {

    private final TurLuceneInstanceProcess turLuceneInstanceProcess;

    public TurDslLuceneExecutor(TurLuceneInstanceProcess turLuceneInstanceProcess) {
        this.turLuceneInstanceProcess = turLuceneInstanceProcess;
    }

    @Override
    public String getEngineType() {
        return "lucene";
    }

    @Override
    public Optional<TurDslSearchResponse> execute(String siteName, Locale locale,
                                                    TurDslQueryRequest request) {
        return turLuceneInstanceProcess.initLuceneInstance(siteName, locale)
                .flatMap(instance -> {
                    long startTime = System.currentTimeMillis();
                    try {
                        IndexSearcher searcher = instance.getSearcher();
                        TurDslQuery dslQuery = request.query() != null
                                ? request.query() : new TurDslQuery.MatchAll();
                        Query luceneQuery = translateQuery(dslQuery);

                        int from = request.from() != null ? request.from() : 0;
                        int size = request.size() != null ? request.size() : 10;
                        int hitsNeeded = Math.max(1, from + size);

                        // Min score filter
                        if (request.minScore() != null) {
                            luceneQuery = new BooleanQuery.Builder()
                                    .add(luceneQuery, BooleanClause.Occur.MUST)
                                    .setMinimumNumberShouldMatch(0)
                                    .build();
                        }

                        // Post filter: wrap as FILTER clause
                        if (request.postFilter() != null) {
                            luceneQuery = new BooleanQuery.Builder()
                                    .add(luceneQuery, BooleanClause.Occur.MUST)
                                    .add(translateQuery(request.postFilter()), BooleanClause.Occur.FILTER)
                                    .build();
                        }

                        Sort sort = (Sort) translateSort(request.sort());

                        // Timeout: use TimeLimitingCollector approach (simplified)
                        TopDocs topDocs = sort != null
                                ? searcher.search(luceneQuery, hitsNeeded, sort)
                                : searcher.search(luceneQuery, hitsNeeded);

                        // Build response using template methods
                        TurDslSearchResponse response = buildResponse(topDocs, request,
                                System.currentTimeMillis() - startTime, searcher, luceneQuery, from,
                                hitsNeeded);

                        // Collapse not natively supported in Lucene DSL
                        if (request.collapse() != null) {
                            log.debug("Collapse is not natively supported in Lucene DSL executor");
                        }
                        // Suggest not supported in Lucene DSL
                        if (request.suggest() != null && !request.suggest().isEmpty()) {
                            log.debug("Suggest is not supported in Lucene DSL executor");
                        }

                        return Optional.of(response);
                    } catch (Exception e) {
                        log.error("DSL Lucene query execution failed: {}", e.getMessage(), e);
                        return Optional.empty();
                    }
                });
    }

    // ==================== Interface Methods ====================

    @Override
    public Query translateQuery(TurDslQuery query) {
        return switch (query) {
            case TurDslQuery.MatchAll() -> new MatchAllDocsQuery();

            case TurDslQuery.Match(var field, var queryText, var operator) -> {
                try {
                    QueryParser parser = new QueryParser(field, new StandardAnalyzer());
                    if ("and".equalsIgnoreCase(operator)) {
                        parser.setDefaultOperator(QueryParser.Operator.AND);
                    }
                    yield parser.parse(queryText);
                } catch (Exception e) {
                    yield new TermQuery(new Term(field, queryText.toLowerCase()));
                }
            }

            case TurDslQuery.MultiMatch(var queryText, var fields, var type) -> {
                if (fields == null || fields.isEmpty()) {
                    try {
                        yield new QueryParser("_text_", new StandardAnalyzer()).parse(queryText);
                    } catch (Exception e) {
                        yield new MatchAllDocsQuery();
                    }
                }
                try {
                    MultiFieldQueryParser parser = new MultiFieldQueryParser(
                            fields.toArray(String[]::new), new StandardAnalyzer());
                    parser.setDefaultOperator(QueryParser.Operator.OR);
                    yield parser.parse(queryText);
                } catch (Exception e) {
                    yield new MatchAllDocsQuery();
                }
            }

            case TurDslQuery.Term(var field, var value) ->
                    new TermQuery(new Term(field, value.toString()));

            case TurDslQuery.Terms(var field, var values) -> {
                BooleanQuery.Builder builder = new BooleanQuery.Builder();
                for (Object v : values) {
                    builder.add(new TermQuery(new Term(field, v.toString())),
                            BooleanClause.Occur.SHOULD);
                }
                yield builder.build();
            }

            case TurDslQuery.Bool(var must, var should, var mustNot, var filter, var msm) -> {
                BooleanQuery.Builder builder = new BooleanQuery.Builder();
                for (var m : must) builder.add(translateQuery(m), BooleanClause.Occur.MUST);
                for (var s : should) builder.add(translateQuery(s), BooleanClause.Occur.SHOULD);
                for (var mn : mustNot) builder.add(translateQuery(mn), BooleanClause.Occur.MUST_NOT);
                for (var f : filter) builder.add(translateQuery(f), BooleanClause.Occur.FILTER);
                if (msm != null) builder.setMinimumNumberShouldMatch(msm);
                yield builder.build();
            }

            case TurDslQuery.Range(var field, var gte, var gt, var lte, var lt) -> {
                String lower = gte != null ? gte.toString() : gt != null ? gt.toString() : null;
                String upper = lte != null ? lte.toString() : lt != null ? lt.toString() : null;
                boolean includeLower = gte != null;
                boolean includeUpper = lte != null;
                yield TermRangeQuery.newStringRange(field, lower, upper, includeLower, includeUpper);
            }

            case TurDslQuery.Wildcard(var field, var value) ->
                    new WildcardQuery(new Term(field, value));

            case TurDslQuery.Prefix(var field, var value) ->
                    new PrefixQuery(new Term(field, value));

            case TurDslQuery.Exists(var field) -> new FieldExistsQuery(field);

            case TurDslQuery.QueryString(var queryText, var defaultField, var defaultOperator) -> {
                try {
                    String df = defaultField != null ? defaultField : "_text_";
                    QueryParser parser = new QueryParser(df, new StandardAnalyzer());
                    if ("AND".equalsIgnoreCase(defaultOperator)) {
                        parser.setDefaultOperator(QueryParser.Operator.AND);
                    }
                    yield parser.parse(queryText);
                } catch (Exception e) {
                    yield new MatchAllDocsQuery();
                }
            }

            case TurDslQuery.Fuzzy(var field, var value, var fuzziness) -> {
                int maxEdits = fuzziness != null ? Math.min(fuzziness, 2) : 2;
                yield new FuzzyQuery(new Term(field, value), maxEdits);
            }

            case TurDslQuery.Ids(var values) -> {
                BooleanQuery.Builder builder = new BooleanQuery.Builder();
                for (String id : values) {
                    builder.add(new TermQuery(new Term("id", id)), BooleanClause.Occur.SHOULD);
                }
                yield builder.build();
            }

            case TurDslQuery.MatchPhrase(var field, var queryText, var slop) -> {
                try {
                    QueryParser parser = new QueryParser(field, new StandardAnalyzer());
                    yield parser.parse("\"%s\"".formatted(queryText));
                } catch (Exception e) {
                    yield new TermQuery(new Term(field, queryText.toLowerCase()));
                }
            }

            case TurDslQuery.MatchPhrasePrefix(var field, var queryText, var maxExp) -> {
                try {
                    QueryParser parser = new QueryParser(field, new StandardAnalyzer());
                    yield parser.parse("\"%s\"".formatted(queryText));
                } catch (Exception e) {
                    yield new PrefixQuery(new Term(field, queryText.toLowerCase()));
                }
            }

            case TurDslQuery.SimpleQueryString(var queryText, var fields, var defOp) -> {
                try {
                    String[] f = (fields != null && !fields.isEmpty())
                            ? fields.toArray(String[]::new) : new String[]{"_text_"};
                    MultiFieldQueryParser parser = new MultiFieldQueryParser(f, new StandardAnalyzer());
                    if ("AND".equalsIgnoreCase(defOp))
                        parser.setDefaultOperator(QueryParser.Operator.AND);
                    yield parser.parse(queryText);
                } catch (Exception e) {
                    yield new MatchAllDocsQuery();
                }
            }

            case TurDslQuery.Regexp(var field, var value, var flags) ->
                    new RegexpQuery(new Term(field, value));

            case TurDslQuery.ConstantScore(var filter, var boost) -> {
                Query inner = translateQuery(filter);
                ConstantScoreQuery csq = new ConstantScoreQuery(inner);
                yield boost != null ? new BoostQuery(csq, boost.floatValue()) : csq;
            }

            case TurDslQuery.DisMax(var queries, var tieBreaker) -> {
                DisjunctionMaxQuery dmq = new DisjunctionMaxQuery(
                        queries.stream().map(this::translateQuery).toList(),
                        tieBreaker != null ? tieBreaker.floatValue() : 0.0f);
                yield dmq;
            }

            case TurDslQuery.Boosting(var positive, var negative, var negBoost) -> {
                BooleanQuery.Builder builder = new BooleanQuery.Builder();
                builder.add(translateQuery(positive), BooleanClause.Occur.MUST);
                builder.add(translateQuery(negative), BooleanClause.Occur.MUST_NOT);
                yield builder.build();
            }

            case TurDslQuery.Nested(var path, var nestedQuery, var scoreMode) ->
                    // Lucene: no native nested — translate inner query directly
                    translateQuery(nestedQuery);

            case TurDslQuery.FunctionScore(var innerQuery, var functions,
                                            var sm, var bm, var maxBoost, var ms) -> {
                // Lucene: apply BoostQuery for field_value_factor, otherwise just inner query
                Query base = translateQuery(innerQuery);
                if (functions != null) {
                    for (var fn : functions) {
                        if (fn.weight() != null) {
                            base = new BoostQuery(base, fn.weight().floatValue());
                            break;
                        }
                    }
                }
                yield base;
            }

            case TurDslQuery.ScriptScore(var innerQuery, var script, var ms) ->
                    // Lucene: script scoring not supported — fallback to inner query
                    translateQuery(innerQuery);

            case TurDslQuery.Knn(var field, var queryVector, var k,
                                  var numCandidates, var similarity, var knnFilter) -> {
                // Lucene 10: KnnFloatVectorQuery
                float[] vector = new float[queryVector.size()];
                for (int idx = 0; idx < queryVector.size(); idx++) {
                    vector[idx] = queryVector.get(idx).floatValue();
                }
                yield new org.apache.lucene.search.KnnFloatVectorQuery(field, vector, k);
            }

            case TurDslQuery.MoreLikeThis(var fields, var likeText, var likeIds,
                                           var mtf, var mdf, var mqt) -> {
                // Lucene: fallback to multi-field query with like text
                if (likeText != null && fields != null && !fields.isEmpty()) {
                    try {
                        MultiFieldQueryParser parser = new MultiFieldQueryParser(
                                fields.toArray(String[]::new), new StandardAnalyzer());
                        yield parser.parse(likeText);
                    } catch (Exception e) {
                        yield new MatchAllDocsQuery();
                    }
                }
                yield new MatchAllDocsQuery();
            }

            case TurDslQuery.CombinedFields(var queryText, var fields, var operator) -> {
                if (fields == null || fields.isEmpty()) {
                    try {
                        yield new QueryParser("_text_", new StandardAnalyzer()).parse(queryText);
                    } catch (Exception e) { yield new MatchAllDocsQuery(); }
                }
                try {
                    MultiFieldQueryParser parser = new MultiFieldQueryParser(
                            fields.toArray(String[]::new), new StandardAnalyzer());
                    if ("and".equalsIgnoreCase(operator))
                        parser.setDefaultOperator(QueryParser.Operator.AND);
                    yield parser.parse(queryText);
                } catch (Exception e) { yield new MatchAllDocsQuery(); }
            }

            case TurDslQuery.MatchBoolPrefix(var field, var queryText) -> {
                // Last token as prefix, previous tokens as term queries
                String[] tokens = queryText.trim().split("\\s+");
                BooleanQuery.Builder builder = new BooleanQuery.Builder();
                for (int i = 0; i < tokens.length - 1; i++) {
                    builder.add(new TermQuery(new Term(field, tokens[i].toLowerCase())),
                            BooleanClause.Occur.MUST);
                }
                builder.add(new PrefixQuery(new Term(field,
                        tokens[tokens.length - 1].toLowerCase())), BooleanClause.Occur.MUST);
                yield builder.build();
            }

            case TurDslQuery.Pinned(var ids, var organic) -> {
                // Boost pinned IDs high, then add organic query
                BooleanQuery.Builder builder = new BooleanQuery.Builder();
                for (String id : ids) {
                    builder.add(new BoostQuery(new TermQuery(new Term("id", id)), 1000f),
                            BooleanClause.Occur.SHOULD);
                }
                if (organic != null) {
                    builder.add(translateQuery(organic), BooleanClause.Occur.SHOULD);
                }
                yield builder.build();
            }

            case TurDslQuery.GeoDistance(var field, var distance, var location) ->
                    // Lucene: LatLonPoint.newDistanceQuery requires LatLonPoint indexed field
                    new MatchAllDocsQuery();

            case TurDslQuery.GeoBoundingBox(var field, var topLeft, var bottomRight) ->
                    // Lucene: LatLonPoint.newBoxQuery requires LatLonPoint indexed field
                    new MatchAllDocsQuery();

            case TurDslQuery.TermsSet(var field, var terms, var msmField, var msmScript) -> {
                BooleanQuery.Builder builder = new BooleanQuery.Builder();
                for (Object t : terms) {
                    builder.add(new TermQuery(new Term(field, t.toString())),
                            BooleanClause.Occur.SHOULD);
                }
                yield builder.build();
            }

            // --- Low-priority queries (translate inner query or fallback) ---
            case TurDslQuery.HasChild(var type, var childQuery, var sm, var minC, var maxC) ->
                    translateQuery(childQuery);
            case TurDslQuery.HasParent(var parentType, var parentQuery, var score) ->
                    translateQuery(parentQuery);
            case TurDslQuery.Intervals(var field, var rule) -> {
                if (rule != null && rule.query() != null) {
                    try {
                        yield new QueryParser(field, new StandardAnalyzer())
                                .parse("\"%s\"".formatted(rule.query()));
                    } catch (Exception e) { yield new MatchAllDocsQuery(); }
                }
                yield new MatchAllDocsQuery();
            }
            case TurDslQuery.SpanTerm(var field, var value) ->
                    new TermQuery(new Term(field, value));
            case TurDslQuery.SpanNear(var clauses, var slop, var inOrder) -> {
                BooleanQuery.Builder builder = new BooleanQuery.Builder();
                for (var c : clauses) builder.add(translateQuery(c), BooleanClause.Occur.MUST);
                yield builder.build();
            }
            case TurDslQuery.SpanOr(var clauses) -> {
                BooleanQuery.Builder builder = new BooleanQuery.Builder();
                for (var c : clauses) builder.add(translateQuery(c), BooleanClause.Occur.SHOULD);
                yield builder.build();
            }
            case TurDslQuery.SpanNot(var include, var exclude) -> {
                BooleanQuery.Builder builder = new BooleanQuery.Builder();
                builder.add(translateQuery(include), BooleanClause.Occur.MUST);
                builder.add(translateQuery(exclude), BooleanClause.Occur.MUST_NOT);
                yield builder.build();
            }
            case TurDslQuery.SpanFirst(var match, var end) -> translateQuery(match);
            case TurDslQuery.RankFeature(var field, var boost, var s1, var s2, var s3) ->
                    new FieldExistsQuery(field);
            case TurDslQuery.DistanceFeature(var field, var origin, var pivot) ->
                    new MatchAllDocsQuery();
            case TurDslQuery.Wrapper(var encodedQuery) -> new MatchAllDocsQuery();
            case TurDslQuery.GeoShape(var field, var shape, var relation) ->
                    new MatchAllDocsQuery();
            case TurDslQuery.Percolate(var field, var document) -> new MatchAllDocsQuery();
        };
    }

    @Override
    public Object translateSort(List<Object> sortEntries) {
        if (sortEntries == null || sortEntries.isEmpty()) return null;
        List<SortField> sortFields = new ArrayList<>();
        for (Object entry : sortEntries) {
            if (entry instanceof String field) {
                sortFields.add("_score".equals(field)
                        ? SortField.FIELD_SCORE
                        : new SortedSetSortField(field, true));
            } else if (entry instanceof Map<?, ?> map) {
                map.forEach((key, val) -> {
                    String field = key.toString();
                    if ("_score".equals(field)) {
                        sortFields.add(SortField.FIELD_SCORE);
                        return;
                    }
                    boolean reverse = true;
                    if (val instanceof String s) reverse = "desc".equalsIgnoreCase(s);
                    else if (val instanceof Map<?, ?> opts) {
                        Object order = opts.get("order");
                        reverse = order == null || "desc".equalsIgnoreCase(order.toString());
                    }
                    sortFields.add(new SortedSetSortField(field, reverse));
                });
            }
        }
        return sortFields.isEmpty() ? null : new Sort(sortFields.toArray(SortField[]::new));
    }

    @Override
    public TurDslSearchResponse buildResponse(TopDocs topDocs, TurDslQueryRequest request,
                                               long elapsedMs) {
        // Delegate to overloaded method — requires searcher and query context
        throw new UnsupportedOperationException(
                "Use buildResponse(TopDocs, TurDslQueryRequest, long, IndexSearcher, Query, int, int)");
    }

    /**
     * Builds the response with full Lucene context (searcher, query, pagination).
     */
    TurDslSearchResponse buildResponse(TopDocs topDocs, TurDslQueryRequest request,
                                        long elapsedMs, IndexSearcher searcher,
                                        Query luceneQuery, int from, int hitsNeeded) throws IOException {
        long numFound = topDocs.totalHits.value();

        // Highlighting
        Map<Integer, Map<String, List<String>>> hlByDocId = Collections.emptyMap();
        if (request.highlight() != null && request.highlight().fields() != null) {
            hlByDocId = buildHighlighting(searcher, luceneQuery, topDocs, request.highlight());
        }

        // Build hits
        List<TurDslSearchResponse.Hit> hits = new ArrayList<>();
        Set<String> sourceFields = resolveSourceFields(request.source());
        Double maxScore = null;

        for (int i = from; i < Math.min(topDocs.scoreDocs.length, hitsNeeded); i++) {
            ScoreDoc sd = topDocs.scoreDocs[i];
            Document doc = searcher.storedFields().document(sd.doc);
            String id = doc.get("id");
            double score = sd.score;
            if (maxScore == null || score > maxScore) maxScore = score;

            Map<String, Object> source = buildDocSource(doc, sourceFields);
            Map<String, List<String>> highlight = hlByDocId.getOrDefault(sd.doc, null);
            hits.add(new TurDslSearchResponse.Hit(id, score, source, highlight));
        }

        // Aggregations
        Map<String, TurDslSearchResponse.AggregationResult> aggregations = null;
        if (request.aggs() != null && !request.aggs().isEmpty()) {
            aggregations = buildAggregations(
                    searcher.search(luceneQuery, Integer.MAX_VALUE), request.aggs(),
                    searcher);
        }

        return new TurDslSearchResponse(elapsedMs, false,
                new TurDslSearchResponse.Hits(
                        new TurDslSearchResponse.Total(numFound, "eq"), maxScore, hits),
                aggregations);
    }

    @Override
    public Map<String, TurDslSearchResponse.AggregationResult> buildAggregations(
            TopDocs topDocs, Map<String, TurDslAggregation> aggs) {
        // Requires searcher context — use the overloaded version
        return null;
    }

    Map<String, TurDslSearchResponse.AggregationResult> buildAggregations(
            TopDocs allDocs, Map<String, TurDslAggregation> aggs, IndexSearcher searcher) {
        Map<String, TurDslSearchResponse.AggregationResult> result = new LinkedHashMap<>();
        try {
            for (var entry : aggs.entrySet()) {
                TurDslAggregation agg = entry.getValue();
                // Unwrap sub-aggs (process parent only at Lucene level)
                if (agg instanceof TurDslAggregation.WithSubAggs wsa) agg = wsa.parent();

                if (agg instanceof TurDslAggregation.TermsAgg termsAgg) {
                    result.put(entry.getKey(), buildLuceneTermsAgg(allDocs, searcher, termsAgg));
                } else if (agg instanceof TurDslAggregation.AvgAgg
                        || agg instanceof TurDslAggregation.SumAgg
                        || agg instanceof TurDslAggregation.MinAgg
                        || agg instanceof TurDslAggregation.MaxAgg
                        || agg instanceof TurDslAggregation.ValueCountAgg
                        || agg instanceof TurDslAggregation.CardinalityAgg) {
                    result.put(entry.getKey(), buildLuceneNumericAgg(allDocs, searcher, agg));
                } else if (agg instanceof TurDslAggregation.StatsAgg statsAgg) {
                    result.put(entry.getKey(), buildLuceneStatsAgg(allDocs, searcher, statsAgg.field()));
                } else if (agg instanceof TurDslAggregation.ExtendedStatsAgg esAgg) {
                    result.put(entry.getKey(), buildLuceneStatsAgg(allDocs, searcher, esAgg.field()));
                } else if (agg instanceof TurDslAggregation.PercentilesAgg pAgg) {
                    result.put(entry.getKey(), buildLucenePercentilesAgg(allDocs, searcher, pAgg));
                } else if (agg instanceof TurDslAggregation.FiltersAgg fAgg) {
                    result.put(entry.getKey(), buildLuceneFiltersAgg(allDocs, searcher, fAgg));
                } else if (agg instanceof TurDslAggregation.SignificantTermsAgg stAgg) {
                    result.put(entry.getKey(), buildLuceneTermsAgg(allDocs, searcher,
                            new TurDslAggregation.TermsAgg(stAgg.field(), stAgg.size())));
                } else if (agg instanceof TurDslAggregation.RareTermsAgg rtAgg) {
                    // Rare terms: collect all, sort by count ascending, limit
                    result.put(entry.getKey(), buildLuceneRareTermsAgg(allDocs, searcher, rtAgg));
                } else if (agg instanceof TurDslAggregation.PercentileRanksAgg prAgg) {
                    result.put(entry.getKey(), buildLucenePercentileRanksAgg(allDocs, searcher, prAgg));
                }
                // NestedAgg, ReverseNestedAgg, AutoDateHistogramAgg, MultiTermsAgg,
                // TopMetricsAgg, TopHitsAgg, CompositeAgg — not supported in Lucene DSL
            }
        } catch (IOException e) {
            log.warn("Error building Lucene aggregations: {}", e.getMessage());
        }
        return result.isEmpty() ? null : result;
    }

    private TurDslSearchResponse.AggregationResult buildLuceneTermsAgg(
            TopDocs allDocs, IndexSearcher searcher, TurDslAggregation.TermsAgg termsAgg)
            throws IOException {
        Map<String, Long> counts = new LinkedHashMap<>();
        int maxSize = termsAgg.size() != null ? termsAgg.size() : 10;
        for (ScoreDoc sd : allDocs.scoreDocs) {
            Document doc = searcher.storedFields().document(sd.doc);
            for (String val : doc.getValues(termsAgg.field())) {
                if (val != null && !val.isEmpty()) counts.merge(val, 1L, Long::sum);
            }
        }
        List<TurDslSearchResponse.Bucket> buckets = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(maxSize)
                .map(e -> new TurDslSearchResponse.Bucket(e.getKey(), e.getValue()))
                .toList();
        return new TurDslSearchResponse.AggregationResult(buckets);
    }

    private TurDslSearchResponse.AggregationResult buildLuceneNumericAgg(
            TopDocs allDocs, IndexSearcher searcher, TurDslAggregation agg) throws IOException {
        String field = switch (agg) {
            case TurDslAggregation.AvgAgg a -> a.field();
            case TurDslAggregation.SumAgg a -> a.field();
            case TurDslAggregation.MinAgg a -> a.field();
            case TurDslAggregation.MaxAgg a -> a.field();
            case TurDslAggregation.ValueCountAgg a -> a.field();
            case TurDslAggregation.CardinalityAgg a -> a.field();
            default -> null;
        };
        if (field == null) return new TurDslSearchResponse.AggregationResult(0.0);

        List<Double> values = new ArrayList<>();
        Set<String> unique = new LinkedHashSet<>();
        for (ScoreDoc sd : allDocs.scoreDocs) {
            Document doc = searcher.storedFields().document(sd.doc);
            for (String val : doc.getValues(field)) {
                if (val != null && !val.isEmpty()) {
                    unique.add(val);
                    try { values.add(Double.parseDouble(val)); } catch (NumberFormatException ignored) {}
                }
            }
        }

        double result = switch (agg) {
            case TurDslAggregation.AvgAgg a -> values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            case TurDslAggregation.SumAgg a -> values.stream().mapToDouble(Double::doubleValue).sum();
            case TurDslAggregation.MinAgg a -> values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
            case TurDslAggregation.MaxAgg a -> values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
            case TurDslAggregation.ValueCountAgg a -> values.size();
            case TurDslAggregation.CardinalityAgg a -> unique.size();
            default -> 0;
        };
        return new TurDslSearchResponse.AggregationResult(result);
    }

    private TurDslSearchResponse.AggregationResult buildLuceneStatsAgg(
            TopDocs allDocs, IndexSearcher searcher, String field) throws IOException {
        List<Double> values = new ArrayList<>();
        for (ScoreDoc sd : allDocs.scoreDocs) {
            Document doc = searcher.storedFields().document(sd.doc);
            for (String val : doc.getValues(field)) {
                try { values.add(Double.parseDouble(val)); } catch (NumberFormatException ignored) {}
            }
        }
        Map<String, Double> stats = new LinkedHashMap<>();
        stats.put("count", (double) values.size());
        stats.put("min", values.stream().mapToDouble(Double::doubleValue).min().orElse(0));
        stats.put("max", values.stream().mapToDouble(Double::doubleValue).max().orElse(0));
        stats.put("avg", values.stream().mapToDouble(Double::doubleValue).average().orElse(0));
        stats.put("sum", values.stream().mapToDouble(Double::doubleValue).sum());
        return new TurDslSearchResponse.AggregationResult(stats);
    }

    private TurDslSearchResponse.AggregationResult buildLucenePercentilesAgg(
            TopDocs allDocs, IndexSearcher searcher, TurDslAggregation.PercentilesAgg pAgg)
            throws IOException {
        List<Double> values = new ArrayList<>();
        for (ScoreDoc sd : allDocs.scoreDocs) {
            Document doc = searcher.storedFields().document(sd.doc);
            for (String val : doc.getValues(pAgg.field())) {
                try { values.add(Double.parseDouble(val)); } catch (NumberFormatException ignored) {}
            }
        }
        Collections.sort(values);
        double[] percents = (pAgg.percents() != null && !pAgg.percents().isEmpty())
                ? pAgg.percents().stream().mapToDouble(Double::doubleValue).toArray()
                : new double[]{1, 5, 25, 50, 75, 95, 99};
        Map<String, Double> percentiles = new LinkedHashMap<>();
        for (double p : percents) {
            int idx = (int) Math.ceil(p / 100.0 * values.size()) - 1;
            idx = Math.max(0, Math.min(idx, values.size() - 1));
            percentiles.put(String.valueOf(p), values.isEmpty() ? 0.0 : values.get(idx));
        }
        return new TurDslSearchResponse.AggregationResult(percentiles);
    }

    private TurDslSearchResponse.AggregationResult buildLuceneFiltersAgg(
            TopDocs allDocs, IndexSearcher searcher, TurDslAggregation.FiltersAgg fAgg)
            throws IOException {
        List<TurDslSearchResponse.Bucket> buckets = new ArrayList<>();
        for (var fe : fAgg.filters().entrySet()) {
            Query filterQuery = translateQuery(fe.getValue());
            TopDocs filtered = searcher.search(filterQuery, Integer.MAX_VALUE);
            buckets.add(new TurDslSearchResponse.Bucket(fe.getKey(), filtered.totalHits.value()));
        }
        return new TurDslSearchResponse.AggregationResult(buckets);
    }

    private TurDslSearchResponse.AggregationResult buildLuceneRareTermsAgg(
            TopDocs allDocs, IndexSearcher searcher, TurDslAggregation.RareTermsAgg rtAgg)
            throws IOException {
        Map<String, Long> counts = new LinkedHashMap<>();
        int maxDocCount = rtAgg.maxDocCount() != null ? rtAgg.maxDocCount() : 1;
        for (ScoreDoc sd : allDocs.scoreDocs) {
            Document doc = searcher.storedFields().document(sd.doc);
            for (String val : doc.getValues(rtAgg.field())) {
                if (val != null && !val.isEmpty()) counts.merge(val, 1L, Long::sum);
            }
        }
        List<TurDslSearchResponse.Bucket> buckets = counts.entrySet().stream()
                .filter(e -> e.getValue() <= maxDocCount)
                .sorted(Map.Entry.comparingByValue())
                .map(e -> new TurDslSearchResponse.Bucket(e.getKey(), e.getValue()))
                .toList();
        return new TurDslSearchResponse.AggregationResult(buckets);
    }

    private TurDslSearchResponse.AggregationResult buildLucenePercentileRanksAgg(
            TopDocs allDocs, IndexSearcher searcher, TurDslAggregation.PercentileRanksAgg prAgg)
            throws IOException {
        List<Double> allValues = new ArrayList<>();
        for (ScoreDoc sd : allDocs.scoreDocs) {
            Document doc = searcher.storedFields().document(sd.doc);
            for (String val : doc.getValues(prAgg.field())) {
                try { allValues.add(Double.parseDouble(val)); } catch (NumberFormatException ignored) {}
            }
        }
        Collections.sort(allValues);
        Map<String, Double> ranks = new LinkedHashMap<>();
        for (Double threshold : prAgg.values()) {
            long countBelow = allValues.stream().filter(v -> v <= threshold).count();
            double rank = allValues.isEmpty() ? 0.0 : (countBelow * 100.0) / allValues.size();
            ranks.put(String.valueOf(threshold), rank);
        }
        return new TurDslSearchResponse.AggregationResult(ranks);
    }

    // ==================== Highlighting ====================

    private Map<Integer, Map<String, List<String>>> buildHighlighting(
            IndexSearcher searcher, Query query, TopDocs topDocs,
            TurDslQueryRequest.TurDslHighlight hl) {
        if (topDocs.scoreDocs.length == 0 || hl.fields() == null) return Collections.emptyMap();

        Map<Integer, Map<String, List<String>>> hlByDocId = new LinkedHashMap<>();
        try {
            UnifiedHighlighter highlighter = new UnifiedHighlighter.Builder(
                    searcher, new StandardAnalyzer()).build();

            String preTag = (hl.preTags() != null && !hl.preTags().isEmpty())
                    ? hl.preTags().getFirst() : "<em>";
            String postTag = (hl.postTags() != null && !hl.postTags().isEmpty())
                    ? hl.postTags().getFirst() : "</em>";

            for (String fieldName : hl.fields().keySet()) {
                try {
                    int maxPassages = hl.numberOfFragments() != null ? hl.numberOfFragments() : 3;
                    String[] snippets = highlighter.highlight(fieldName, query, topDocs,
                            maxPassages);
                    if (snippets == null) continue;
                    for (int i = 0; i < snippets.length && i < topDocs.scoreDocs.length; i++) {
                        if (snippets[i] == null) continue;
                        String highlighted = snippets[i]
                                .replace("<b>", preTag).replace("</b>", postTag);
                        hlByDocId.computeIfAbsent(topDocs.scoreDocs[i].doc,
                                        k -> new LinkedHashMap<>())
                                .computeIfAbsent(fieldName, k -> new ArrayList<>())
                                .add(highlighted);
                    }
                } catch (Exception e) {
                    log.debug("Could not highlight field '{}': {}", fieldName, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Error building Lucene highlighting: {}", e.getMessage());
        }
        return hlByDocId;
    }

    // ==================== Helpers ====================

    private Set<String> resolveSourceFields(Object source) {
        if (source instanceof List<?> fields) {
            return fields.stream().map(Object::toString).collect(Collectors.toSet());
        }
        if (source instanceof Map<?, ?> map && map.containsKey("includes")) {
            Object includes = map.get("includes");
            if (includes instanceof List<?> list) {
                return list.stream().map(Object::toString).collect(Collectors.toSet());
            }
        }
        return Set.of();
    }

    private Map<String, Object> buildDocSource(Document doc, Set<String> sourceFields) {
        Map<String, Object> source = new LinkedHashMap<>();
        for (var field : doc.getFields()) {
            String name = field.name();
            if ("_version_".equals(name)) continue;
            if (!sourceFields.isEmpty() && !sourceFields.contains(name) && !"id".equals(name))
                continue;
            Object existing = source.get(name);
            if (existing != null) {
                if (existing instanceof List<?> list) {
                    @SuppressWarnings("unchecked")
                    var mutableList = (List<Object>) list;
                    mutableList.add(field.stringValue());
                } else {
                    var list = new ArrayList<>();
                    list.add(existing);
                    list.add(field.stringValue());
                    source.put(name, list);
                }
            } else {
                source.put(name, field.stringValue());
            }
        }
        return source;
    }
}
