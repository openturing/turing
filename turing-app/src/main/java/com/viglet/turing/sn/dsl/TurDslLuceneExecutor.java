package com.viglet.turing.sn.dsl;

import com.viglet.turing.lucene.TurLuceneInstanceProcess;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
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

                        Sort sort = (Sort) translateSort(request.sort());
                        TopDocs topDocs = sort != null
                                ? searcher.search(luceneQuery, hitsNeeded, sort)
                                : searcher.search(luceneQuery, hitsNeeded);

                        // Build response using template methods
                        TurDslSearchResponse response = buildResponse(topDocs, request,
                                System.currentTimeMillis() - startTime, searcher, luceneQuery, from,
                                hitsNeeded);
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
                if (entry.getValue() instanceof TurDslAggregation.TermsAgg termsAgg) {
                    Map<String, Long> counts = new LinkedHashMap<>();
                    int maxSize = termsAgg.size() != null ? termsAgg.size() : 10;
                    for (ScoreDoc sd : allDocs.scoreDocs) {
                        Document doc = searcher.storedFields().document(sd.doc);
                        String[] values = doc.getValues(termsAgg.field());
                        for (String val : values) {
                            if (val != null && !val.isEmpty()) {
                                counts.merge(val, 1L, Long::sum);
                            }
                        }
                    }
                    List<TurDslSearchResponse.Bucket> buckets = counts.entrySet().stream()
                            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                            .limit(maxSize)
                            .map(e -> new TurDslSearchResponse.Bucket(e.getKey(), e.getValue()))
                            .toList();
                    result.put(entry.getKey(),
                            new TurDslSearchResponse.AggregationResult(buckets));
                }
            }
        } catch (IOException e) {
            log.warn("Error building Lucene aggregations: {}", e.getMessage());
        }
        return result.isEmpty() ? null : result;
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
