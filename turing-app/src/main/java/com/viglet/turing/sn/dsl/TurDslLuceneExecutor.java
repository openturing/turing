package com.viglet.turing.sn.dsl;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.lucene.TurLuceneInstanceProcess;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteField;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.sn.field.TurSNSiteFieldService;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoublePoint;
import org.apache.lucene.document.FloatPoint;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.search.uhighlight.UnifiedHighlighter;
import org.apache.lucene.util.automaton.Operations;
import org.apache.lucene.util.automaton.RegExp;
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

    // --- S1192: extracted duplicated literals ---
    private static final String TEXT = "_text_";
    private static final String S = "\\\"%s\\\"";


    private final TurLuceneInstanceProcess turLuceneInstanceProcess;
    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNSiteFieldService turSNSiteFieldService;

    public TurDslLuceneExecutor(TurLuceneInstanceProcess turLuceneInstanceProcess,
            TurSNSiteRepository turSNSiteRepository,
            TurSNSiteFieldService turSNSiteFieldService) {
        this.turLuceneInstanceProcess = turLuceneInstanceProcess;
        this.turSNSiteRepository = turSNSiteRepository;
        this.turSNSiteFieldService = turSNSiteFieldService;
    }

    @Override
    public String getEngineType() {
        return "lucene";
    }

    /**
     * Resolves the {@code field → TurSEFieldType} map for a site so range and sort
     * translation can pick the right Lucene primitive — numeric fields are indexed
     * as {@code *Point} + {@code NumericDocValues} (not string terms), so a
     * lexicographic {@code TermRangeQuery} matches nothing and a
     * {@code SortedSetSortField} throws on them. Returns an empty map when the site
     * isn't found, in which case translation falls back to the string-term behavior.
     */
    private Map<String, TurSEFieldType> resolveFieldTypes(String siteName) {
        return turSNSiteRepository.findByNameIgnoreCase(siteName)
                .map(site -> {
                    Map<String, TurSEFieldType> types = new HashMap<>();
                    for (TurSNSiteField field : turSNSiteFieldService.toMap(site).values()) {
                        if (field.getName() != null && field.getType() != null) {
                            types.put(field.getName(), field.getType());
                        }
                    }
                    return types;
                })
                .orElseGet(Map::of);
    }

    @Override
    public Optional<TurDslSearchResponse> execute(String siteName, Locale locale,
                                                    TurDslQueryRequest request) {
        return turLuceneInstanceProcess.initLuceneInstance(siteName, locale)
                .flatMap(instance -> {
                    long startTime = System.currentTimeMillis();
                    try {
                        Map<String, TurSEFieldType> fieldTypes = resolveFieldTypes(siteName);
                        IndexSearcher searcher = instance.getSearcher();
                        TurDslQuery dslQuery = request.query() != null
                                ? request.query() : new TurDslQuery.MatchAll();

                        int from = request.from() != null ? request.from() : 0;
                        int size = request.size() != null ? request.size() : 10;
                        int hitsNeeded = Math.max(1, from + size);

                        Query luceneQuery = buildLuceneQuery(dslQuery, request, fieldTypes);

                        Sort sort = buildSort(request.sort(), fieldTypes);

                        // A sort over a field that lacks matching doc values throws at
                        // search time. Rather than failing the whole query (and losing
                        // the results), fall back to relevance order — the query is more
                        // valuable than the requested ordering.
                        TopDocs topDocs = searchWithSortFallback(searcher, luceneQuery, hitsNeeded, sort);

                        // Build response using template methods
                        TurDslSearchResponse response = buildResponse(topDocs, request,
                                System.currentTimeMillis() - startTime, searcher, luceneQuery, from,
                                hitsNeeded);

                        logUnsupportedDslFeatures(request);

                        return Optional.of(response);
                    } catch (Exception e) {
                        log.error("DSL Lucene query execution failed: {}", e.getMessage(), e);
                        return Optional.empty();
                    }
                });
    }

    /**
     * Translates {@code dslQuery} and wraps it with the optional min-score and
     * post-filter clauses from {@code request}.
     */
    private Query buildLuceneQuery(TurDslQuery dslQuery, TurDslQueryRequest request,
            Map<String, TurSEFieldType> fieldTypes) {
        Query luceneQuery = translateQuery(dslQuery, fieldTypes);

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
                    .add(translateQuery(request.postFilter(), fieldTypes),
                            BooleanClause.Occur.FILTER)
                    .build();
        }
        return luceneQuery;
    }

    /** Logs DSL features the Lucene executor does not support (collapse, suggest). */
    private void logUnsupportedDslFeatures(TurDslQueryRequest request) {
        // Collapse not natively supported in Lucene DSL
        if (request.collapse() != null) {
            log.debug("Collapse is not natively supported in Lucene DSL executor");
        }
        // Suggest not supported in Lucene DSL
        if (request.suggest() != null && !request.suggest().isEmpty()) {
            log.debug("Suggest is not supported in Lucene DSL executor");
        }
    }

    /**
     * Runs the search with the requested sort, falling back to relevance order when a sort over a
     * field lacking matching doc values throws — the query results matter more than the ordering.
     */
    private TopDocs searchWithSortFallback(IndexSearcher searcher, Query luceneQuery, int hitsNeeded,
            Sort sort) throws IOException {
        try {
            return sort != null
                    ? searcher.search(luceneQuery, hitsNeeded, sort)
                    : searcher.search(luceneQuery, hitsNeeded);
        } catch (IllegalStateException sortFailure) {
            log.warn("DSL Lucene sort failed ({}). Falling back to relevance order.",
                    sortFailure.getMessage());
            return searcher.search(luceneQuery, hitsNeeded);
        }
    }

    // ==================== Interface Methods ====================

    @Override
    public Query translateQuery(TurDslQuery query) {
        // Interface entry point — no field-type context (used by callers/tests that
        // don't resolve a site). Numeric ranges fall back to string-term behavior.
        return translateQuery(query, Map.of());
    }

    Query translateQuery(TurDslQuery query, Map<String, TurSEFieldType> fieldTypes) {
        return switch (query) {
            case TurDslQuery.MatchAll() -> MatchAllDocsQuery.INSTANCE;

            case TurDslQuery.Match q -> translateMatch(q);

            case TurDslQuery.MultiMatch q -> translateMultiMatch(q);

            case TurDslQuery.Term(var field, var value) ->
                    translateTerm(field, value, fieldTypes.get(field));

            case TurDslQuery.Terms q -> translateTerms(q, fieldTypes);

            case TurDslQuery.Bool q -> translateBool(q, fieldTypes);

            case TurDslQuery.Range(var field, var gte, var gt, var lte, var lt) ->
                    buildRangeQuery(field, gte, gt, lte, lt, fieldTypes.get(field));

            case TurDslQuery.Wildcard(var field, var value) ->
                    new WildcardQuery(new Term(field, value));

            case TurDslQuery.Prefix(var field, var value) ->
                    new PrefixQuery(new Term(field, value));

            case TurDslQuery.Exists(var field) -> new FieldExistsQuery(field);

            case TurDslQuery.QueryString q -> translateQueryString(q);

            case TurDslQuery.Fuzzy q -> translateFuzzy(q);

            case TurDslQuery.Ids q -> translateIds(q);

            case TurDslQuery.MatchPhrase q -> translateMatchPhrase(q);

            case TurDslQuery.MatchPhrasePrefix q -> translateMatchPhrasePrefix(q);

            case TurDslQuery.SimpleQueryString q -> translateSimpleQueryString(q);

            case TurDslQuery.Regexp(var field, var value, var flags) ->
                    new RegexpQuery(new Term(field, value));

            case TurDslQuery.ConstantScore q -> translateConstantScore(q, fieldTypes);

            case TurDslQuery.DisMax q -> translateDisMax(q, fieldTypes);

            case TurDslQuery.Boosting q -> translateBoosting(q, fieldTypes);

            case TurDslQuery.Nested(var path, var nestedQuery, var scoreMode) ->
                    // Lucene: no native nested — translate inner query directly
                    translateQuery(nestedQuery, fieldTypes);

            case TurDslQuery.FunctionScore q -> translateFunctionScore(q, fieldTypes);

            case TurDslQuery.ScriptScore(var innerQuery, var script, var ms) ->
                    // Lucene: script scoring not supported — fallback to inner query
                    translateQuery(innerQuery, fieldTypes);

            case TurDslQuery.Knn q -> translateKnn(q);

            case TurDslQuery.MoreLikeThis q -> translateMoreLikeThis(q);

            case TurDslQuery.CombinedFields q -> translateCombinedFields(q);

            case TurDslQuery.MatchBoolPrefix q -> translateMatchBoolPrefix(q);

            // Less-common query families (pinned / geo / span / join / feature / wrapper)
            // live in a sibling switch to keep each below the case-count limit.
            default -> translateQueryExtended(query, fieldTypes);
        };
    }

    /** Second-tier dispatch for the rarer query families — see {@link #translateQuery}. */
    private Query translateQueryExtended(TurDslQuery query, Map<String, TurSEFieldType> fieldTypes) {
        return switch (query) {
            case TurDslQuery.Pinned q -> translatePinned(q, fieldTypes);

            case TurDslQuery.GeoDistance(var field, var distance, var location) ->
                    // Lucene: LatLonPoint.newDistanceQuery requires LatLonPoint indexed field
                    MatchAllDocsQuery.INSTANCE;

            case TurDslQuery.GeoBoundingBox(var field, var topLeft, var bottomRight) ->
                    // Lucene: LatLonPoint.newBoxQuery requires LatLonPoint indexed field
                    MatchAllDocsQuery.INSTANCE;

            case TurDslQuery.TermsSet q -> translateTermsSet(q);

            // --- Low-priority queries (translate inner query or fallback) ---
            case TurDslQuery.HasChild(var type, var childQuery, var sm, var minC, var maxC) ->
                    translateQuery(childQuery, fieldTypes);
            case TurDslQuery.HasParent(var parentType, var parentQuery, var score) ->
                    translateQuery(parentQuery, fieldTypes);
            case TurDslQuery.Intervals q -> translateIntervals(q);
            case TurDslQuery.SpanTerm(var field, var value) ->
                    new TermQuery(new Term(field, value));
            case TurDslQuery.SpanNear q -> translateSpanNear(q, fieldTypes);
            case TurDslQuery.SpanOr q -> translateSpanOr(q, fieldTypes);
            case TurDslQuery.SpanNot q -> translateSpanNot(q, fieldTypes);
            case TurDslQuery.SpanFirst(var match, var end) -> translateQuery(match, fieldTypes);
            case TurDslQuery.RankFeature(var field, var boost, var s1, var s2, var s3) ->
                    new FieldExistsQuery(field);
            case TurDslQuery.DistanceFeature(var field, var origin, var pivot) ->
                    MatchAllDocsQuery.INSTANCE;
            case TurDslQuery.Wrapper(var encodedQuery) -> MatchAllDocsQuery.INSTANCE;
            case TurDslQuery.GeoShape(var field, var shape, var relation) ->
                    MatchAllDocsQuery.INSTANCE;
            case TurDslQuery.Percolate(var field, var document) -> MatchAllDocsQuery.INSTANCE;
            default -> throw new IllegalStateException("Unexpected DSL type: " + query);
        };
    }

    // ==================== Per-query-type translators ====================
    // Extracted from the translateQuery switch so each arm stays flat (S3776).
    // Each takes the matched record and uses its accessors; behavior is
    // identical to the original inline arm.

    private Query translateMatch(TurDslQuery.Match q) {
        try {
            QueryParser parser = new QueryParser(q.field(), new StandardAnalyzer());
            if ("and".equalsIgnoreCase(q.operator())) {
                parser.setDefaultOperator(QueryParser.Operator.AND);
            }
            return parser.parse(q.query());
        } catch (Exception e) {
            return new TermQuery(new Term(q.field(), q.query().toLowerCase()));
        }
    }

    private Query translateMultiMatch(TurDslQuery.MultiMatch q) {
        if (q.fields() == null || q.fields().isEmpty()) {
            try {
                return new QueryParser(TEXT, new StandardAnalyzer()).parse(q.query());
            } catch (Exception e) {
                return MatchAllDocsQuery.INSTANCE;
            }
        }
        try {
            MultiFieldQueryParser parser = new MultiFieldQueryParser(
                    q.fields().toArray(String[]::new), new StandardAnalyzer());
            parser.setDefaultOperator(QueryParser.Operator.OR);
            return parser.parse(q.query());
        } catch (Exception e) {
            return MatchAllDocsQuery.INSTANCE;
        }
    }

    private Query translateTerms(TurDslQuery.Terms q, Map<String, TurSEFieldType> fieldTypes) {
        TurSEFieldType type = fieldTypes.get(q.field());
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        for (Object v : q.values()) {
            builder.add(translateTerm(q.field(), v, type), BooleanClause.Occur.SHOULD);
        }
        return builder.build();
    }

    /**
     * Translates a single {@code term} filter. STRING facet values are matched
     * <strong>case-insensitively</strong> (T802): the values are indexed with their
     * declared case (e.g. {@code kind=EMBEDDING}), but the NL→facet planner routinely
     * emits them lowercased ({@code kind=embedding}), so an exact {@link TermQuery}
     * silently misses and the copilot returns "no matching results". Every other
     * type — and the {@code id} field, whose exact-match semantics must be preserved
     * — keeps the exact {@link TermQuery}.
     */
    private static Query translateTerm(String field, Object value, TurSEFieldType type) {
        String text = value.toString();
        if (type == TurSEFieldType.STRING && !"id".equals(field)) {
            return caseInsensitiveTermQuery(field, text);
        }
        return new TermQuery(new Term(field, text));
    }

    /**
     * A case-insensitive exact-value match for a STRING field. The value is wrapped
     * in a double-quoted Lucene {@link RegExp} literal (so no character is treated as
     * a regex metacharacter — {@code RegExp.NONE} disables optional syntax) and
     * matched with {@link RegExp#CASE_INSENSITIVE}, which folds case at match time
     * without needing the field to be lower-cased at index time.
     */
    private static Query caseInsensitiveTermQuery(String field, String value) {
        String literal = "\"" + value.replace("\"", "") + "\"";
        return new RegexpQuery(new Term(field, literal), RegExp.NONE,
                RegExp.CASE_INSENSITIVE, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT);
    }

    private Query translateBool(TurDslQuery.Bool q, Map<String, TurSEFieldType> fieldTypes) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        for (var m : q.must()) builder.add(translateQuery(m, fieldTypes), BooleanClause.Occur.MUST);
        for (var s : q.should()) builder.add(translateQuery(s, fieldTypes), BooleanClause.Occur.SHOULD);
        for (var mn : q.mustNot()) builder.add(translateQuery(mn, fieldTypes), BooleanClause.Occur.MUST_NOT);
        for (var f : q.filter()) builder.add(translateQuery(f, fieldTypes), BooleanClause.Occur.FILTER);
        if (q.minimumShouldMatch() != null) builder.setMinimumNumberShouldMatch(q.minimumShouldMatch());
        return builder.build();
    }

    private Query translateQueryString(TurDslQuery.QueryString q) {
        try {
            String df = q.defaultField() != null ? q.defaultField() : TEXT;
            QueryParser parser = new QueryParser(df, new StandardAnalyzer());
            if ("AND".equalsIgnoreCase(q.defaultOperator())) {
                parser.setDefaultOperator(QueryParser.Operator.AND);
            }
            return parser.parse(q.query());
        } catch (Exception e) {
            return MatchAllDocsQuery.INSTANCE;
        }
    }

    private Query translateFuzzy(TurDslQuery.Fuzzy q) {
        int maxEdits = q.fuzziness() != null ? Math.min(q.fuzziness(), 2) : 2;
        return new FuzzyQuery(new Term(q.field(), q.value()), maxEdits);
    }

    private Query translateIds(TurDslQuery.Ids q) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        for (String id : q.values()) {
            builder.add(new TermQuery(new Term("id", id)), BooleanClause.Occur.SHOULD);
        }
        return builder.build();
    }

    private Query translateMatchPhrase(TurDslQuery.MatchPhrase q) {
        try {
            QueryParser parser = new QueryParser(q.field(), new StandardAnalyzer());
            return parser.parse(S.formatted(q.query()));
        } catch (Exception e) {
            return new TermQuery(new Term(q.field(), q.query().toLowerCase()));
        }
    }

    private Query translateMatchPhrasePrefix(TurDslQuery.MatchPhrasePrefix q) {
        try {
            QueryParser parser = new QueryParser(q.field(), new StandardAnalyzer());
            return parser.parse(S.formatted(q.query()));
        } catch (Exception e) {
            return new PrefixQuery(new Term(q.field(), q.query().toLowerCase()));
        }
    }

    private Query translateSimpleQueryString(TurDslQuery.SimpleQueryString q) {
        try {
            String[] f = (q.fields() != null && !q.fields().isEmpty())
                    ? q.fields().toArray(String[]::new) : new String[]{TEXT};
            MultiFieldQueryParser parser = new MultiFieldQueryParser(f, new StandardAnalyzer());
            if ("AND".equalsIgnoreCase(q.defaultOperator())) {
                parser.setDefaultOperator(QueryParser.Operator.AND);
            }
            return parser.parse(q.query());
        } catch (Exception e) {
            return MatchAllDocsQuery.INSTANCE;
        }
    }

    private Query translateConstantScore(TurDslQuery.ConstantScore q, Map<String, TurSEFieldType> fieldTypes) {
        Query inner = translateQuery(q.filter(), fieldTypes);
        ConstantScoreQuery csq = new ConstantScoreQuery(inner);
        return q.boost() != null ? new BoostQuery(csq, q.boost().floatValue()) : csq;
    }

    private Query translateDisMax(TurDslQuery.DisMax q, Map<String, TurSEFieldType> fieldTypes) {
        return new DisjunctionMaxQuery(
                q.queries().stream().map(sub -> translateQuery(sub, fieldTypes)).toList(),
                q.tieBreaker() != null ? q.tieBreaker().floatValue() : 0.0f);
    }

    private Query translateBoosting(TurDslQuery.Boosting q, Map<String, TurSEFieldType> fieldTypes) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.add(translateQuery(q.positive(), fieldTypes), BooleanClause.Occur.MUST);
        builder.add(translateQuery(q.negative(), fieldTypes), BooleanClause.Occur.MUST_NOT);
        return builder.build();
    }

    private Query translateFunctionScore(TurDslQuery.FunctionScore q, Map<String, TurSEFieldType> fieldTypes) {
        // Lucene: apply BoostQuery for the first weighted function, otherwise just inner query.
        Query base = translateQuery(q.query(), fieldTypes);
        if (q.functions() != null) {
            for (var fn : q.functions()) {
                if (fn.weight() != null) {
                    base = new BoostQuery(base, fn.weight().floatValue());
                    break;
                }
            }
        }
        return base;
    }

    private Query translateKnn(TurDslQuery.Knn q) {
        // Lucene 10: KnnFloatVectorQuery
        float[] vector = new float[q.queryVector().size()];
        for (int idx = 0; idx < q.queryVector().size(); idx++) {
            vector[idx] = q.queryVector().get(idx).floatValue();
        }
        return new org.apache.lucene.search.KnnFloatVectorQuery(q.field(), vector, q.k());
    }

    private Query translateMoreLikeThis(TurDslQuery.MoreLikeThis q) {
        // Lucene: fallback to multi-field query with like text
        if (q.likeText() != null && q.fields() != null && !q.fields().isEmpty()) {
            try {
                MultiFieldQueryParser parser = new MultiFieldQueryParser(
                        q.fields().toArray(String[]::new), new StandardAnalyzer());
                return parser.parse(q.likeText());
            } catch (Exception e) {
                return MatchAllDocsQuery.INSTANCE;
            }
        }
        return MatchAllDocsQuery.INSTANCE;
    }

    private Query translateCombinedFields(TurDslQuery.CombinedFields q) {
        if (q.fields() == null || q.fields().isEmpty()) {
            try {
                return new QueryParser(TEXT, new StandardAnalyzer()).parse(q.query());
            } catch (Exception e) {
                return MatchAllDocsQuery.INSTANCE;
            }
        }
        try {
            MultiFieldQueryParser parser = new MultiFieldQueryParser(
                    q.fields().toArray(String[]::new), new StandardAnalyzer());
            if ("and".equalsIgnoreCase(q.operator())) {
                parser.setDefaultOperator(QueryParser.Operator.AND);
            }
            return parser.parse(q.query());
        } catch (Exception e) {
            return MatchAllDocsQuery.INSTANCE;
        }
    }

    private Query translateMatchBoolPrefix(TurDslQuery.MatchBoolPrefix q) {
        // Last token as prefix, previous tokens as term queries
        String[] tokens = q.query().trim().split("\\s+");
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        for (int i = 0; i < tokens.length - 1; i++) {
            builder.add(new TermQuery(new Term(q.field(), tokens[i].toLowerCase())),
                    BooleanClause.Occur.MUST);
        }
        builder.add(new PrefixQuery(new Term(q.field(),
                tokens[tokens.length - 1].toLowerCase())), BooleanClause.Occur.MUST);
        return builder.build();
    }

    private Query translatePinned(TurDslQuery.Pinned q, Map<String, TurSEFieldType> fieldTypes) {
        // Boost pinned IDs high, then add organic query
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        for (String id : q.ids()) {
            builder.add(new BoostQuery(new TermQuery(new Term("id", id)), 1000f),
                    BooleanClause.Occur.SHOULD);
        }
        if (q.organic() != null) {
            builder.add(translateQuery(q.organic(), fieldTypes), BooleanClause.Occur.SHOULD);
        }
        return builder.build();
    }

    private Query translateTermsSet(TurDslQuery.TermsSet q) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        for (Object t : q.terms()) {
            builder.add(new TermQuery(new Term(q.field(), t.toString())),
                    BooleanClause.Occur.SHOULD);
        }
        return builder.build();
    }

    private Query translateIntervals(TurDslQuery.Intervals q) {
        if (q.rule() != null && q.rule().query() != null) {
            try {
                return new QueryParser(q.field(), new StandardAnalyzer())
                        .parse(S.formatted(q.rule().query()));
            } catch (Exception e) {
                return MatchAllDocsQuery.INSTANCE;
            }
        }
        return MatchAllDocsQuery.INSTANCE;
    }

    private Query translateSpanNear(TurDslQuery.SpanNear q, Map<String, TurSEFieldType> fieldTypes) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        for (var c : q.clauses()) builder.add(translateQuery(c, fieldTypes), BooleanClause.Occur.MUST);
        return builder.build();
    }

    private Query translateSpanOr(TurDslQuery.SpanOr q, Map<String, TurSEFieldType> fieldTypes) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        for (var c : q.clauses()) builder.add(translateQuery(c, fieldTypes), BooleanClause.Occur.SHOULD);
        return builder.build();
    }

    private Query translateSpanNot(TurDslQuery.SpanNot q, Map<String, TurSEFieldType> fieldTypes) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.add(translateQuery(q.include(), fieldTypes), BooleanClause.Occur.MUST);
        builder.add(translateQuery(q.exclude(), fieldTypes), BooleanClause.Occur.MUST_NOT);
        return builder.build();
    }

    @Override
    public Object translateSort(List<Object> sortEntries) {
        // Interface entry point — no field-type context, so every field sorts as a
        // SortedSet (string). Numeric fields are sorted correctly via buildSort(...).
        return buildSort(sortEntries, Map.of());
    }

    Sort buildSort(List<Object> sortEntries, Map<String, TurSEFieldType> fieldTypes) {
        if (sortEntries == null || sortEntries.isEmpty()) return null;
        List<SortField> sortFields = new ArrayList<>();
        for (Object entry : sortEntries) {
            if (entry instanceof String field) {
                sortFields.add("_score".equals(field)
                        ? SortField.FIELD_SCORE
                        : sortFieldFor(field, true, fieldTypes.get(field)));
            } else if (entry instanceof Map<?, ?> map) {
                map.forEach((key, val) ->
                        addMapSortField(key.toString(), val, sortFields, fieldTypes));
            }
        }
        return sortFields.isEmpty() ? null : new Sort(sortFields.toArray(SortField[]::new));
    }

    /**
     * Appends the {@link SortField} for one {@code {field: order}} sort entry,
     * honouring {@code _score}, a {@code "asc"/"desc"} string, or a
     * {@code {"order": …}} options map (default descending).
     */
    private void addMapSortField(String field, Object val, List<SortField> sortFields,
            Map<String, TurSEFieldType> fieldTypes) {
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
        sortFields.add(sortFieldFor(field, reverse, fieldTypes.get(field)));
    }

    /**
     * Builds the right {@link SortField} for a field: numeric fields are indexed
     * with {@link org.apache.lucene.document.NumericDocValuesField} (raw bits for
     * FLOAT/DOUBLE, reconstructed losslessly by the matching comparator), so they
     * must sort via a typed {@link SortField}; everything else has SortedSet doc
     * values on the bare field name and sorts as a {@link SortedSetSortField}.
     */
    private static SortField sortFieldFor(String field, boolean reverse, TurSEFieldType type) {
        if (type == null) {
            return new SortedSetSortField(field, reverse);
        }
        return switch (type) {
            case INT -> new SortField(field, SortField.Type.INT, reverse);
            case LONG, DATE -> new SortField(field, SortField.Type.LONG, reverse);
            case FLOAT -> new SortField(field, SortField.Type.FLOAT, reverse);
            case DOUBLE, CURRENCY -> new SortField(field, SortField.Type.DOUBLE, reverse);
            default -> new SortedSetSortField(field, reverse);
        };
    }

    /**
     * Builds a range query honoring the field's indexed type. Numeric / currency /
     * date fields are indexed as {@code *Point} (not string terms), so they use the
     * matching point range query; all other types use a lexicographic
     * {@link TermRangeQuery}. {@code gte}/{@code lte} are inclusive bounds and
     * {@code gt}/{@code lt} exclusive — translated to inclusive point bounds via the
     * next representable value.
     */
    private static Query buildRangeQuery(String field, Object gte, Object gt, Object lte, Object lt,
            TurSEFieldType type) {
        if (type == null) {
            String lower = rangeBoundToString(gte, gt);
            String upper = rangeBoundToString(lte, lt);
            return TermRangeQuery.newStringRange(field, lower, upper, gte != null, lte != null);
        }
        return switch (type) {
            case INT -> intRangeQuery(field, gte, gt, lte, lt);
            case LONG, DATE -> longRangeQuery(field, gte, gt, lte, lt);
            case FLOAT -> floatRangeQuery(field, gte, gt, lte, lt);
            case DOUBLE, CURRENCY -> doubleRangeQuery(field, gte, gt, lte, lt);
            default -> stringRangeQuery(field, gte, gt, lte, lt);
        };
    }

    private static Query intRangeQuery(String field, Object gte, Object gt, Object lte, Object lt) {
        int lo;
        if (gte != null) {
            lo = toInt(gte);
        } else if (gt != null) {
            lo = Math.addExact(toInt(gt), 1);
        } else {
            lo = Integer.MIN_VALUE;
        }
        int hi;
        if (lte != null) {
            hi = toInt(lte);
        } else if (lt != null) {
            hi = Math.addExact(toInt(lt), -1);
        } else {
            hi = Integer.MAX_VALUE;
        }
        return IntPoint.newRangeQuery(field, lo, hi);
    }

    private static Query longRangeQuery(String field, Object gte, Object gt, Object lte, Object lt) {
        long lo;
        if (gte != null) {
            lo = toLong(gte);
        } else if (gt != null) {
            lo = Math.addExact(toLong(gt), 1);
        } else {
            lo = Long.MIN_VALUE;
        }
        long hi;
        if (lte != null) {
            hi = toLong(lte);
        } else if (lt != null) {
            hi = Math.addExact(toLong(lt), -1);
        } else {
            hi = Long.MAX_VALUE;
        }
        return LongPoint.newRangeQuery(field, lo, hi);
    }

    private static Query floatRangeQuery(String field, Object gte, Object gt, Object lte, Object lt) {
        float lo;
        if (gte != null) {
            lo = toFloat(gte);
        } else if (gt != null) {
            lo = Math.nextUp(toFloat(gt));
        } else {
            lo = Float.NEGATIVE_INFINITY;
        }
        float hi;
        if (lte != null) {
            hi = toFloat(lte);
        } else if (lt != null) {
            hi = Math.nextDown(toFloat(lt));
        } else {
            hi = Float.POSITIVE_INFINITY;
        }
        return FloatPoint.newRangeQuery(field, lo, hi);
    }

    private static Query doubleRangeQuery(String field, Object gte, Object gt, Object lte, Object lt) {
        double lo;
        if (gte != null) {
            lo = toDouble(gte);
        } else if (gt != null) {
            lo = Math.nextUp(toDouble(gt));
        } else {
            lo = Double.NEGATIVE_INFINITY;
        }
        double hi;
        if (lte != null) {
            hi = toDouble(lte);
        } else if (lt != null) {
            hi = Math.nextDown(toDouble(lt));
        } else {
            hi = Double.POSITIVE_INFINITY;
        }
        return DoublePoint.newRangeQuery(field, lo, hi);
    }

    private static Query stringRangeQuery(String field, Object gte, Object gt, Object lte, Object lt) {
        String lower = rangeBoundToString(gte, gt);
        String upper = rangeBoundToString(lte, lt);
        return TermRangeQuery.newStringRange(field, lower, upper, gte != null, lte != null);
    }

    /**
     * First non-null range bound rendered as a string ({@code inclusive} wins
     * over {@code exclusive}), or {@code null} when both are absent.
     */
    private static String rangeBoundToString(Object inclusive, Object exclusive) {
        if (inclusive != null) {
            return inclusive.toString();
        }
        if (exclusive != null) {
            return exclusive.toString();
        }
        return null;
    }

    private static int toInt(Object v) {
        return v instanceof Number n ? n.intValue() : Integer.parseInt(v.toString().trim());
    }

    private static long toLong(Object v) {
        return v instanceof Number n ? n.longValue() : Long.parseLong(v.toString().trim());
    }

    private static float toFloat(Object v) {
        return v instanceof Number n ? n.floatValue() : Float.parseFloat(v.toString().trim());
    }

    private static double toDouble(Object v) {
        // CURRENCY may arrive as "1500,BRL" — keep the numeric part (mirrors indexing).
        String s = v.toString().trim();
        int comma = s.lastIndexOf(',');
        if (comma > 0) {
            s = s.substring(0, comma);
        }
        return v instanceof Number n ? n.doubleValue() : Double.parseDouble(s);
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
        // Lucene aggregation needs an IndexSearcher to resolve doc values, which this
        // interface signature does not carry. The Lucene path always calls the
        // searcher-aware overload below, so this variant is never the right entry point.
        throw new UnsupportedOperationException(
                "Lucene aggregation requires an IndexSearcher; call buildAggregations(TopDocs, Map, IndexSearcher)");
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
                } else if (isNumericAgg(agg)) {
                    result.put(entry.getKey(), buildLuceneNumericAgg(allDocs, searcher, agg));
                } else if (agg instanceof TurDslAggregation.StatsAgg(String field)) {
                    result.put(entry.getKey(), buildLuceneStatsAgg(allDocs, searcher, field));
                } else if (agg instanceof TurDslAggregation.ExtendedStatsAgg(String field)) {
                    result.put(entry.getKey(), buildLuceneStatsAgg(allDocs, searcher, field));
                } else if (agg instanceof TurDslAggregation.PercentilesAgg pAgg) {
                    result.put(entry.getKey(), buildLucenePercentilesAgg(allDocs, searcher, pAgg));
                } else if (agg instanceof TurDslAggregation.FiltersAgg fAgg) {
                    result.put(entry.getKey(), buildLuceneFiltersAgg(searcher, fAgg));
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

    /** True when {@code agg} is one of the single-value numeric aggregations. */
    private static boolean isNumericAgg(TurDslAggregation agg) {
        return agg instanceof TurDslAggregation.AvgAgg
                || agg instanceof TurDslAggregation.SumAgg
                || agg instanceof TurDslAggregation.MinAgg
                || agg instanceof TurDslAggregation.MaxAgg
                || agg instanceof TurDslAggregation.ValueCountAgg
                || agg instanceof TurDslAggregation.CardinalityAgg;
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
            case TurDslAggregation.AvgAgg(String avgField) -> avgField;
            case TurDslAggregation.SumAgg(String sumField) -> sumField;
            case TurDslAggregation.MinAgg(String minField) -> minField;
            case TurDslAggregation.MaxAgg(String maxField) -> maxField;
            case TurDslAggregation.ValueCountAgg(String vcField) -> vcField;
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
                    try { values.add(Double.parseDouble(val)); } catch (NumberFormatException ignored) { /* skip non-numeric values in numeric aggregation */ }
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
                try { values.add(Double.parseDouble(val)); } catch (NumberFormatException ignored) { /* skip non-numeric values in numeric aggregation */ }
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
                try { values.add(Double.parseDouble(val)); } catch (NumberFormatException ignored) { /* skip non-numeric values in numeric aggregation */ }
            }
        }
        Collections.sort(values);
        double[] percents = (pAgg.percents() != null && !pAgg.percents().isEmpty())
                ? pAgg.percents().stream().mapToDouble(Double::doubleValue).toArray()
                : new double[]{1, 5, 25, 50, 75, 95, 99};
        Map<String, Double> percentiles = new LinkedHashMap<>();
        for (double p : percents) {
            int idx = (int) Math.ceil(p / 100.0 * values.size()) - 1;
            idx = Math.clamp(idx, 0, values.size() - 1);
            percentiles.put(String.valueOf(p), values.isEmpty() ? 0.0 : values.get(idx));
        }
        return new TurDslSearchResponse.AggregationResult(percentiles);
    }

    private TurDslSearchResponse.AggregationResult buildLuceneFiltersAgg(
            IndexSearcher searcher, TurDslAggregation.FiltersAgg fAgg)
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
                try { allValues.add(Double.parseDouble(val)); } catch (NumberFormatException ignored) { /* skip non-numeric values in numeric aggregation */ }
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
            int maxPassages = hl.numberOfFragments() != null ? hl.numberOfFragments() : 3;
            HlTags tags = new HlTags(preTag, postTag, maxPassages);

            for (String fieldName : hl.fields().keySet()) {
                highlightField(highlighter, fieldName, query, topDocs, hlByDocId, tags);
            }
        } catch (Exception e) {
            log.warn("Error building Lucene highlighting: {}", e.getMessage());
        }
        return hlByDocId;
    }

    /** Pre/post highlight tags + max passage count for one highlight pass. */
    private record HlTags(String preTag, String postTag, int maxPassages) {
    }

    /**
     * Highlights one field across the hit docs, appending the rewritten snippets
     * (b→configured tags) into {@code hlByDocId}. Per-field failures are logged
     * and skipped so one bad field can't void the whole highlight set.
     */
    private void highlightField(UnifiedHighlighter highlighter, String fieldName, Query query,
            TopDocs topDocs, Map<Integer, Map<String, List<String>>> hlByDocId, HlTags tags) {
        try {
            String[] snippets = highlighter.highlight(fieldName, query, topDocs, tags.maxPassages());
            if (snippets == null) return;
            for (int i = 0; i < snippets.length && i < topDocs.scoreDocs.length; i++) {
                if (snippets[i] == null) continue;
                String highlighted = snippets[i]
                        .replace("<b>", tags.preTag()).replace("</b>", tags.postTag());
                hlByDocId.computeIfAbsent(topDocs.scoreDocs[i].doc, k -> new LinkedHashMap<>())
                        .computeIfAbsent(fieldName, k -> new ArrayList<>())
                        .add(highlighted);
            }
        } catch (Exception e) {
            log.debug("Could not highlight field '{}': {}", fieldName, e.getMessage());
        }
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
            if ("_version_".equals(name)) {
                continue;
            }
            if (sourceFields.isEmpty() || sourceFields.contains(name) || "id".equals(name)) {
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
        }
        return source;
    }
}
