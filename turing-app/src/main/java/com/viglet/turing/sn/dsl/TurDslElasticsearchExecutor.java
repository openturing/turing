package com.viglet.turing.sn.dsl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import com.viglet.turing.elasticsearch.TurElasticsearchInstanceProcess;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Elasticsearch implementation of {@link TurDslSearchEngineExecutor}.
 * <p>
 * Translates DSL queries into Elasticsearch Java API {@link Query} objects
 * and executes them via the official Elasticsearch client.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Slf4j
@Component
public class TurDslElasticsearchExecutor
        implements TurDslSearchEngineExecutor<Query, SearchResponse<Map>> {

    private final TurElasticsearchInstanceProcess turElasticsearchInstanceProcess;

    public TurDslElasticsearchExecutor(
            TurElasticsearchInstanceProcess turElasticsearchInstanceProcess) {
        this.turElasticsearchInstanceProcess = turElasticsearchInstanceProcess;
    }

    @Override
    public String getEngineType() {
        return "elasticsearch";
    }

    @Override
    public Optional<TurDslSearchResponse> execute(String siteName, Locale locale,
                                                    TurDslQueryRequest request) {
        return turElasticsearchInstanceProcess.initElasticsearchInstance(siteName, locale)
                .flatMap(instance -> {
                    long startTime = System.currentTimeMillis();
                    try {
                        ElasticsearchClient client = instance.getClient();
                        String index = instance.getIndex();

                        TurDslQuery dslQuery = request.query() != null
                                ? request.query() : new TurDslQuery.MatchAll();
                        Query esQuery = translateQuery(dslQuery);

                        SearchRequest.Builder reqBuilder = new SearchRequest.Builder()
                                .index(index)
                                .query(esQuery)
                                .from(request.from() != null ? request.from() : 0)
                                .size(request.size() != null ? request.size() : 10)
                                .trackScores(true)
                                .trackTotalHits(t -> t.enabled(true));

                        // Sort
                        if (request.sort() != null) {
                            applySortToRequest(request.sort(), reqBuilder);
                        }

                        // Source fields
                        applySourceToRequest(request.source(), reqBuilder);

                        // Highlight
                        if (request.highlight() != null) {
                            applyHighlightToRequest(request.highlight(), reqBuilder);
                        }

                        // Aggregations
                        if (request.aggs() != null && !request.aggs().isEmpty()) {
                            applyAggregationsToRequest(request.aggs(), reqBuilder);
                        }

                        // Post filter
                        if (request.postFilter() != null) {
                            reqBuilder.postFilter(translateQuery(request.postFilter()));
                        }

                        // Min score
                        if (request.minScore() != null) {
                            reqBuilder.minScore(request.minScore());
                        }

                        // Search after (cursor-based pagination)
                        if (request.searchAfter() != null && !request.searchAfter().isEmpty()) {
                            reqBuilder.searchAfter(request.searchAfter().stream()
                                    .map(v -> FieldValue.of(v.toString())).toList());
                        }

                        // Collapse (field collapsing / dedup)
                        if (request.collapse() != null) {
                            applyCollapseToRequest(request.collapse(), reqBuilder);
                        }

                        // Suggest
                        if (request.suggest() != null && !request.suggest().isEmpty()) {
                            applySuggestToRequest(request.suggest(), reqBuilder);
                        }

                        // Rescore
                        if (request.rescore() != null && !request.rescore().isEmpty()) {
                            applyRescoreToRequest(request.rescore(), reqBuilder);
                        }

                        // Timeout
                        if (request.timeout() != null) {
                            reqBuilder.timeout(request.timeout());
                        }

                        // Explain
                        if (request.explain() != null && request.explain()) {
                            reqBuilder.explain(true);
                        }

                        // Script fields
                        if (request.scriptFields() != null && !request.scriptFields().isEmpty()) {
                            applyScriptFieldsToRequest(request.scriptFields(), reqBuilder);
                        }

                        // Indices boost
                        if (request.indicesBoost() != null && !request.indicesBoost().isEmpty()) {
                            List<co.elastic.clients.util.NamedValue<Double>> boosts = new ArrayList<>();
                            for (var boostMap : request.indicesBoost()) {
                                boostMap.forEach((idx, val) ->
                                        boosts.add(co.elastic.clients.util.NamedValue.of(idx, val)));
                            }
                            reqBuilder.indicesBoost(boosts);
                        }

                        // Track total hits
                        if (request.trackTotalHits() != null) {
                            if (request.trackTotalHits() instanceof Boolean b) {
                                reqBuilder.trackTotalHits(t -> t.enabled(b));
                            } else if (request.trackTotalHits() instanceof Number n) {
                                reqBuilder.trackTotalHits(t -> t.count(n.intValue()));
                            }
                        }

                        // Stored fields
                        if (request.storedFields() != null && !request.storedFields().isEmpty()) {
                            reqBuilder.storedFields(request.storedFields());
                        }

                        // Scroll
                        if (request.scroll() != null) {
                            reqBuilder.scroll(s -> s.time(request.scroll()));
                        }

                        // --- Low-priority features ---
                        if (request.profile() != null && request.profile()) {
                            reqBuilder.profile(true);
                        }
                        if (request.pit() != null && request.pit().id() != null) {
                            reqBuilder.pit(p -> {
                                p.id(request.pit().id());
                                if (request.pit().keepAlive() != null)
                                    p.keepAlive(ka -> ka.time(request.pit().keepAlive()));
                                return p;
                            });
                        }
                        if (request.docvalueFields() != null && !request.docvalueFields().isEmpty()) {
                            List<co.elastic.clients.elasticsearch._types.query_dsl.FieldAndFormat> dvFields =
                                    request.docvalueFields().stream()
                                            .map(f -> co.elastic.clients.elasticsearch._types.query_dsl
                                                    .FieldAndFormat.of(ff -> ff.field(f.toString())))
                                            .toList();
                            reqBuilder.docvalueFields(dvFields);
                        }
                        if (request.version() != null && request.version()) {
                            reqBuilder.version(true);
                        }
                        if (request.seqNoPrimaryTerm() != null && request.seqNoPrimaryTerm()) {
                            reqBuilder.seqNoPrimaryTerm(true);
                        }
                        if (request.preference() != null) {
                            reqBuilder.preference(request.preference());
                        }
                        if (request.routing() != null) {
                            reqBuilder.routing(request.routing());
                        }
                        if (request.terminateAfter() != null) {
                            reqBuilder.terminateAfter((long) request.terminateAfter());
                        }

                        SearchResponse<Map> response = client.search(
                                reqBuilder.build(), (java.lang.reflect.Type) Map.class);

                        TurDslSearchResponse dslResponse = buildResponse(response, request,
                                System.currentTimeMillis() - startTime);

                        // Attach suggest results if present
                        if (!response.suggest().isEmpty()) {
                            dslResponse = attachSuggestResults(dslResponse, response);
                        }

                        return Optional.of(dslResponse);
                    } catch (Exception e) {
                        log.error("DSL Elasticsearch query execution failed: {}", e.getMessage(), e);
                        return Optional.empty();
                    }
                });
    }

    // ==================== DSL → Elasticsearch Query Translation ====================

    @Override
    public Query translateQuery(TurDslQuery query) {
        return switch (query) {
            case TurDslQuery.MatchAll() ->
                    Query.of(q -> q.matchAll(m -> m));

            case TurDslQuery.Match(var field, var queryText, var operator) ->
                    Query.of(q -> q.match(m -> {
                        m.field(field).query(queryText);
                        if ("and".equalsIgnoreCase(operator)) {
                            m.operator(co.elastic.clients.elasticsearch._types.query_dsl.Operator.And);
                        }
                        return m;
                    }));

            case TurDslQuery.MultiMatch(var queryText, var fields, var type) ->
                    Query.of(q -> q.multiMatch(m -> {
                        m.query(queryText);
                        if (fields != null && !fields.isEmpty()) m.fields(fields);
                        return m;
                    }));

            case TurDslQuery.Term(var field, var value) ->
                    Query.of(q -> q.term(t -> t.field(field)
                            .value(FieldValue.of(value.toString()))));

            case TurDslQuery.Terms(var field, var values) ->
                    Query.of(q -> q.terms(t -> t.field(field)
                            .terms(tv -> tv.value(values.stream()
                                    .map(v -> FieldValue.of(v.toString()))
                                    .toList()))));

            case TurDslQuery.Bool(var must, var should, var mustNot, var filter, var msm) ->
                    Query.of(q -> q.bool(b -> {
                        if (!must.isEmpty())
                            b.must(must.stream().map(this::translateQuery).toList());
                        if (!should.isEmpty())
                            b.should(should.stream().map(this::translateQuery).toList());
                        if (!mustNot.isEmpty())
                            b.mustNot(mustNot.stream().map(this::translateQuery).toList());
                        if (!filter.isEmpty())
                            b.filter(filter.stream().map(this::translateQuery).toList());
                        if (msm != null) b.minimumShouldMatch(msm.toString());
                        return b;
                    }));

            case TurDslQuery.Range(var field, var gte, var gt, var lte, var lt) ->
                    Query.of(q -> q.range(r -> r.untyped(u -> {
                        u.field(field);
                        if (gte != null) u.gte(co.elastic.clients.json.JsonData.of(gte));
                        if (gt != null) u.gt(co.elastic.clients.json.JsonData.of(gt));
                        if (lte != null) u.lte(co.elastic.clients.json.JsonData.of(lte));
                        if (lt != null) u.lt(co.elastic.clients.json.JsonData.of(lt));
                        return u;
                    })));

            case TurDslQuery.Wildcard(var field, var value) ->
                    Query.of(q -> q.wildcard(w -> w.field(field).value(value)));

            case TurDslQuery.Prefix(var field, var value) ->
                    Query.of(q -> q.prefix(p -> p.field(field).value(value)));

            case TurDslQuery.Exists(var field) ->
                    Query.of(q -> q.exists(e -> e.field(field)));

            case TurDslQuery.QueryString(var queryText, var defaultField, var defaultOperator) ->
                    Query.of(q -> q.queryString(qs -> {
                        qs.query(queryText);
                        if (defaultField != null) qs.defaultField(defaultField);
                        if ("AND".equalsIgnoreCase(defaultOperator)) {
                            qs.defaultOperator(
                                    co.elastic.clients.elasticsearch._types.query_dsl.Operator.And);
                        }
                        return qs;
                    }));

            case TurDslQuery.Fuzzy(var field, var value, var fuzziness) ->
                    Query.of(q -> q.fuzzy(f -> {
                        f.field(field).value(value);
                        if (fuzziness != null) f.fuzziness(fuzziness.toString());
                        return f;
                    }));

            case TurDslQuery.Ids(var values) ->
                    Query.of(q -> q.ids(i -> i.values(values)));

            case TurDslQuery.MatchPhrase(var field, var queryText, var slop) ->
                    Query.of(q -> q.matchPhrase(mp -> {
                        mp.field(field).query(queryText);
                        if (slop != null) mp.slop(slop);
                        return mp;
                    }));

            case TurDslQuery.MatchPhrasePrefix(var field, var queryText, var maxExp) ->
                    Query.of(q -> q.matchPhrasePrefix(mpp -> {
                        mpp.field(field).query(queryText);
                        if (maxExp != null) mpp.maxExpansions(maxExp);
                        return mpp;
                    }));

            case TurDslQuery.SimpleQueryString(var queryText, var fields, var defOp) ->
                    Query.of(q -> q.simpleQueryString(sqs -> {
                        sqs.query(queryText);
                        if (fields != null && !fields.isEmpty()) sqs.fields(fields);
                        if ("AND".equalsIgnoreCase(defOp))
                            sqs.defaultOperator(
                                    co.elastic.clients.elasticsearch._types.query_dsl.Operator.And);
                        return sqs;
                    }));

            case TurDslQuery.Regexp(var field, var value, var flags) ->
                    Query.of(q -> q.regexp(r -> {
                        r.field(field).value(value);
                        if (flags != null) r.flags(flags);
                        return r;
                    }));

            case TurDslQuery.ConstantScore(var filter, var boost) ->
                    Query.of(q -> q.constantScore(cs -> {
                        cs.filter(translateQuery(filter));
                        if (boost != null) cs.boost(boost.floatValue());
                        return cs;
                    }));

            case TurDslQuery.DisMax(var queries, var tieBreaker) ->
                    Query.of(q -> q.disMax(dm -> {
                        dm.queries(queries.stream().map(this::translateQuery).toList());
                        if (tieBreaker != null) dm.tieBreaker(tieBreaker);
                        return dm;
                    }));

            case TurDslQuery.Boosting(var positive, var negative, var negBoost) ->
                    Query.of(q -> q.boosting(b -> {
                        b.positive(translateQuery(positive));
                        b.negative(translateQuery(negative));
                        if (negBoost != null) b.negativeBoost(negBoost);
                        return b;
                    }));

            case TurDslQuery.Nested(var path, var nestedQuery, var scoreMode) ->
                    Query.of(q -> q.nested(n -> {
                        n.path(path).query(translateQuery(nestedQuery));
                        if (scoreMode != null) {
                            n.scoreMode(co.elastic.clients.elasticsearch._types.query_dsl
                                    .ChildScoreMode.valueOf(
                                    scoreMode.substring(0, 1).toUpperCase()
                                            + scoreMode.substring(1).toLowerCase()));
                        }
                        return n;
                    }));

            case TurDslQuery.FunctionScore(var innerQuery, var functions,
                                            var scoreMd, var boostMd, var maxBoost, var minSc) ->
                    Query.of(q -> q.functionScore(fs -> {
                        fs.query(translateQuery(innerQuery));
                        if (functions != null) {
                            fs.functions(functions.stream().map(this::translateScoreFunction).toList());
                        }
                        if (maxBoost != null) fs.maxBoost(maxBoost);
                        return fs;
                    }));

            case TurDslQuery.ScriptScore(var innerQuery, var script, var minSc) ->
                    Query.of(q -> q.scriptScore(ss -> {
                        ss.query(translateQuery(innerQuery));
                        if (script != null) {
                            ss.script(s -> {
                                if (script.source() != null) {
                                    s.source(src -> src.scriptString(script.source()));
                                    if (script.lang() != null) s.lang(script.lang());
                                    if (script.params() != null) {
                                        java.util.Map<String, co.elastic.clients.json.JsonData> p =
                                                new LinkedHashMap<>();
                                        script.params().forEach((k, v) ->
                                                p.put(k, co.elastic.clients.json.JsonData.of(v)));
                                        s.params(p);
                                    }
                                }
                                return s;
                            });
                        }
                        if (minSc != null) ss.minScore(minSc.floatValue());
                        return ss;
                    }));

            case TurDslQuery.Knn(var field, var queryVector, var k,
                                  var numCandidates, var similarity, var knnFilter) ->
                    Query.of(q -> q.knn(knn -> {
                        knn.field(field)
                                .queryVector(queryVector.stream()
                                        .map(Double::floatValue).toList())
                                .k(k)
                                .numCandidates(numCandidates);
                        if (similarity != null) knn.similarity(similarity.floatValue());
                        if (knnFilter != null) knn.filter(translateQuery(knnFilter));
                        return knn;
                    }));

            case TurDslQuery.MoreLikeThis(var fields, var likeText, var likeIds,
                                           var minTermFreq, var minDocFreq, var maxQueryTerms) ->
                    Query.of(q -> q.moreLikeThis(mlt -> {
                        if (fields != null && !fields.isEmpty()) mlt.fields(fields);
                        if (likeText != null) mlt.like(l -> l.text(likeText));
                        if (minTermFreq != null) mlt.minTermFreq(minTermFreq);
                        if (minDocFreq != null) mlt.minDocFreq(minDocFreq);
                        if (maxQueryTerms != null) mlt.maxQueryTerms(maxQueryTerms);
                        return mlt;
                    }));

            case TurDslQuery.CombinedFields(var queryText, var fields, var operator) ->
                    Query.of(q -> q.combinedFields(cf -> {
                        cf.query(queryText);
                        if (fields != null && !fields.isEmpty()) cf.fields(fields);
                        if ("and".equalsIgnoreCase(operator))
                            cf.operator(co.elastic.clients.elasticsearch._types.query_dsl
                                    .CombinedFieldsOperator.And);
                        return cf;
                    }));

            case TurDslQuery.MatchBoolPrefix(var field, var queryText) ->
                    Query.of(q -> q.matchBoolPrefix(mbp -> mbp.field(field).query(queryText)));

            case TurDslQuery.Pinned(var ids, var organic) ->
                    Query.of(q -> q.pinned(p -> {
                        if (ids != null && !ids.isEmpty()) p.ids(ids);
                        if (organic != null) p.organic(translateQuery(organic));
                        return p;
                    }));

            case TurDslQuery.GeoDistance(var field, var distance, var location) ->
                    Query.of(q -> q.geoDistance(gd -> {
                        gd.field(field);
                        if (distance != null) gd.distance(distance);
                        if (location != null) gd.location(gl ->
                                gl.latlon(ll -> ll.lat(location.lat()).lon(location.lon())));
                        return gd;
                    }));

            case TurDslQuery.GeoBoundingBox(var field, var topLeft, var bottomRight) ->
                    Query.of(q -> q.geoBoundingBox(gbb -> {
                        gbb.field(field);
                        gbb.boundingBox(bb -> bb.tlbr(tlbr -> {
                            if (topLeft != null) tlbr.topLeft(gl ->
                                    gl.latlon(ll -> ll.lat(topLeft.lat()).lon(topLeft.lon())));
                            if (bottomRight != null) tlbr.bottomRight(gl ->
                                    gl.latlon(ll -> ll.lat(bottomRight.lat()).lon(bottomRight.lon())));
                            return tlbr;
                        }));
                        return gbb;
                    }));

            case TurDslQuery.TermsSet(var field, var terms, var msmField, var msmScript) ->
                    Query.of(q -> q.termsSet(ts -> {
                        ts.field(field);
                        if (terms != null)
                            ts.terms(terms.stream()
                                    .map(v -> FieldValue.of(v.toString())).toList());
                        if (msmField != null) ts.minimumShouldMatchField(msmField);
                        return ts;
                    }));

            // --- Low-priority queries ---
            case TurDslQuery.HasChild(var type, var childQuery, var scoreMode,
                                       var minChildren, var maxChildren) ->
                    Query.of(q -> q.hasChild(hc -> {
                        hc.type(type).query(translateQuery(childQuery));
                        if (minChildren != null) hc.minChildren(minChildren);
                        if (maxChildren != null) hc.maxChildren(maxChildren);
                        return hc;
                    }));

            case TurDslQuery.HasParent(var parentType, var parentQuery, var score) ->
                    Query.of(q -> q.hasParent(hp -> {
                        hp.parentType(parentType).query(translateQuery(parentQuery));
                        if (score != null) hp.score(score);
                        return hp;
                    }));

            case TurDslQuery.Intervals(var field, var rule) ->
                    Query.of(q -> q.intervals(iv -> {
                        iv.field(field);
                        if (rule != null && "match".equals(rule.type())) {
                            iv.match(m -> {
                                if (rule.query() != null) m.query(rule.query());
                                if (rule.maxGaps() != null) m.maxGaps(rule.maxGaps());
                                if (rule.ordered() != null) m.ordered(rule.ordered());
                                return m;
                            });
                        }
                        return iv;
                    }));

            case TurDslQuery.SpanTerm(var field, var value) ->
                    Query.of(q -> q.spanTerm(st -> st.field(field).value(value)));

            case TurDslQuery.SpanNear(var clauses, var slop, var inOrder) ->
                    Query.of(q -> q.spanNear(sn -> {
                        sn.clauses(clauses.stream().map(this::toSpanQuery).toList());
                        if (slop != null) sn.slop(slop);
                        if (inOrder != null) sn.inOrder(inOrder);
                        return sn;
                    }));

            case TurDslQuery.SpanOr(var clauses) ->
                    Query.of(q -> q.spanOr(so ->
                            so.clauses(clauses.stream().map(this::toSpanQuery).toList())));

            case TurDslQuery.SpanNot(var include, var exclude) ->
                    Query.of(q -> q.spanNot(sn -> sn
                            .include(toSpanQuery(include))
                            .exclude(toSpanQuery(exclude))));

            case TurDslQuery.SpanFirst(var match, var end) ->
                    Query.of(q -> q.spanFirst(sf -> {
                        sf.match(toSpanQuery(match));
                        if (end != null) sf.end(end);
                        return sf;
                    }));

            case TurDslQuery.RankFeature(var field, var boost, var sat, var lg, var sig) ->
                    Query.of(q -> q.rankFeature(rf -> {
                        rf.field(field);
                        if (boost != null) rf.boost(boost.floatValue());
                        return rf;
                    }));

            case TurDslQuery.DistanceFeature(var field, var origin, var pivot) ->
                    Query.of(q -> q.distanceFeature(df -> df.untyped(u -> {
                        u.field(field);
                        if (origin != null) u.origin(co.elastic.clients.json.JsonData.of(origin));
                        if (pivot != null) u.pivot(co.elastic.clients.json.JsonData.of(pivot));
                        return u;
                    })));

            case TurDslQuery.Wrapper(var encodedQuery) ->
                    Query.of(q -> q.wrapper(w -> w.query(encodedQuery)));

            case TurDslQuery.GeoShape(var field, var shape, var relation) ->
                    Query.of(q -> q.geoShape(gs -> {
                        gs.field(field);
                        return gs;
                    }));

            case TurDslQuery.Percolate(var field, var document) ->
                    Query.of(q -> q.percolate(p -> {
                        if (field != null) p.field(field);
                        if (document != null) p.document(co.elastic.clients.json.JsonData.of(document));
                        return p;
                    }));
        };
    }

    private co.elastic.clients.elasticsearch._types.query_dsl.SpanQuery toSpanQuery(TurDslQuery dslQuery) {
        return co.elastic.clients.elasticsearch._types.query_dsl.SpanQuery.of(sq -> {
            if (dslQuery instanceof TurDslQuery.SpanTerm(var field, var value)) {
                sq.spanTerm(st -> st.field(field).value(value));
            } else if (dslQuery instanceof TurDslQuery.SpanNear(var clauses, var slop, var inOrder)) {
                sq.spanNear(sn -> {
                    sn.clauses(clauses.stream().map(this::toSpanQuery).toList());
                    if (slop != null) sn.slop(slop);
                    if (inOrder != null) sn.inOrder(inOrder);
                    return sn;
                });
            } else if (dslQuery instanceof TurDslQuery.SpanOr(var clauses)) {
                sq.spanOr(so -> so.clauses(clauses.stream().map(this::toSpanQuery).toList()));
            } else if (dslQuery instanceof TurDslQuery.SpanNot(var include, var exclude)) {
                sq.spanNot(sn -> sn.include(toSpanQuery(include)).exclude(toSpanQuery(exclude)));
            } else if (dslQuery instanceof TurDslQuery.SpanFirst(var match, var end)) {
                sq.spanFirst(sf -> { sf.match(toSpanQuery(match)); if (end != null) sf.end(end); return sf; });
            } else {
                // Fallback: wrap as span_term on "id" field
                sq.spanTerm(st -> st.field("id").value("*"));
            }
            return sq;
        });
    }

    private co.elastic.clients.elasticsearch._types.query_dsl.FunctionScore
    translateScoreFunction(TurDslQuery.ScoreFunction fn) {
        return co.elastic.clients.elasticsearch._types.query_dsl.FunctionScore.of(f -> {
            if (fn.filter() != null) f.filter(translateQuery(fn.filter()));
            if (fn.weight() != null) f.weight(fn.weight());
            if (fn.fieldValueFactor() != null) {
                var fvf = fn.fieldValueFactor();
                f.fieldValueFactor(v -> {
                    v.field(fvf.field());
                    if (fvf.factor() != null) v.factor(fvf.factor());
                    if (fvf.missing() != null) v.missing(fvf.missing());
                    if (fvf.modifier() != null) {
                        v.modifier(co.elastic.clients.elasticsearch._types.query_dsl
                                .FieldValueFactorModifier.valueOf(
                                fvf.modifier().substring(0, 1).toUpperCase()
                                        + fvf.modifier().substring(1).toLowerCase()));
                    }
                    return v;
                });
            }
            if (fn.scriptScore() != null) {
                f.scriptScore(ss -> ss.script(s -> {
                    s.source(src -> src.scriptString(fn.scriptScore().source()));
                    if (fn.scriptScore().lang() != null) s.lang(fn.scriptScore().lang());
                    return s;
                }));
            }
            return f;
        });
    }

    @Override
    public Object translateSort(List<Object> sortEntries) {
        // Sort is applied directly to the request builder via applySortToRequest
        return sortEntries;
    }

    // ==================== Response Building ====================

    @Override
    @SuppressWarnings("unchecked")
    public TurDslSearchResponse buildResponse(SearchResponse<Map> response,
                                               TurDslQueryRequest request, long elapsedMs) {
        long numFound = response.hits().total() != null ? response.hits().total().value() : 0;
        Double maxScore = response.hits().maxScore() != null
                ? response.hits().maxScore() : null;

        List<TurDslSearchResponse.Hit> hits = new ArrayList<>();
        for (Hit<Map> hit : response.hits().hits()) {
            Map<String, Object> source = hit.source() != null
                    ? (Map<String, Object>) hit.source() : Map.of();
            String id = hit.id();

            Map<String, List<String>> highlight = null;
            if (hit.highlight() != null && !hit.highlight().isEmpty()) {
                highlight = new LinkedHashMap<>(hit.highlight());
            }

            hits.add(new TurDslSearchResponse.Hit(id, hit.score(), source, highlight));
        }

        Map<String, TurDslSearchResponse.AggregationResult> aggregations = null;
        if (request.aggs() != null && !request.aggs().isEmpty()
                && response.aggregations() != null && !response.aggregations().isEmpty()) {
            aggregations = buildAggregations(response, request.aggs());
        }

        return new TurDslSearchResponse(elapsedMs, false,
                new TurDslSearchResponse.Hits(
                        new TurDslSearchResponse.Total(numFound, "eq"), maxScore, hits),
                aggregations);
    }

    @Override
    public Map<String, TurDslSearchResponse.AggregationResult> buildAggregations(
            SearchResponse<Map> response, Map<String, TurDslAggregation> aggs) {
        Map<String, TurDslSearchResponse.AggregationResult> result = new LinkedHashMap<>();
        for (var entry : aggs.entrySet()) {
            String aggName = entry.getKey();
            var esAgg = response.aggregations().get(aggName);
            if (esAgg == null) continue;

            if (entry.getValue() instanceof TurDslAggregation.TermsAgg) {
                try {
                    List<TurDslSearchResponse.Bucket> buckets = new ArrayList<>();
                    for (StringTermsBucket bucket : esAgg.sterms().buckets().array()) {
                        buckets.add(new TurDslSearchResponse.Bucket(
                                bucket.key().stringValue(), bucket.docCount()));
                    }
                    result.put(aggName, new TurDslSearchResponse.AggregationResult(buckets));
                } catch (Exception e) {
                    log.debug("Could not parse terms aggregation '{}': {}", aggName, e.getMessage());
                }
            } else if (entry.getValue() instanceof TurDslAggregation.RangeAgg) {
                try {
                    List<TurDslSearchResponse.Bucket> buckets = new ArrayList<>();
                    for (var bucket : esAgg.range().buckets().array()) {
                        String key = bucket.key() != null ? bucket.key()
                                : "%s-%s".formatted(
                                bucket.fromAsString() != null ? bucket.fromAsString() : "*",
                                bucket.toAsString() != null ? bucket.toAsString() : "*");
                        buckets.add(new TurDslSearchResponse.Bucket(key, bucket.docCount()));
                    }
                    result.put(aggName, new TurDslSearchResponse.AggregationResult(buckets));
                } catch (Exception e) {
                    log.debug("Could not parse range aggregation '{}': {}", aggName, e.getMessage());
                }
            } else if (entry.getValue() instanceof TurDslAggregation.AvgAgg
                    || entry.getValue() instanceof TurDslAggregation.SumAgg
                    || entry.getValue() instanceof TurDslAggregation.MinAgg
                    || entry.getValue() instanceof TurDslAggregation.MaxAgg
                    || entry.getValue() instanceof TurDslAggregation.ValueCountAgg
                    || entry.getValue() instanceof TurDslAggregation.CardinalityAgg) {
                try {
                    double value = esAgg._get()instanceof
                            co.elastic.clients.elasticsearch._types.aggregations.SingleMetricAggregateBase sm
                            ? sm.value() : 0.0;
                    result.put(aggName, new TurDslSearchResponse.AggregationResult(value));
                } catch (Exception e) {
                    log.debug("Could not parse metric aggregation '{}': {}", aggName, e.getMessage());
                }
            }
        }
        return result.isEmpty() ? null : result;
    }

    // ==================== Request Builders ====================

    private void applySortToRequest(List<Object> sortEntries, SearchRequest.Builder reqBuilder) {
        for (Object entry : sortEntries) {
            if (entry instanceof String field) {
                if ("_score".equals(field)) {
                    reqBuilder.sort(s -> s.score(sc -> sc.order(SortOrder.Desc)));
                } else {
                    reqBuilder.sort(s -> s.field(f -> f.field(field).order(SortOrder.Desc)));
                }
            } else if (entry instanceof Map<?, ?> map) {
                map.forEach((key, val) -> {
                    String field = key.toString();
                    SortOrder order = SortOrder.Desc;
                    if (val instanceof String s) {
                        order = "asc".equalsIgnoreCase(s) ? SortOrder.Asc : SortOrder.Desc;
                    } else if (val instanceof Map<?, ?> opts) {
                        Object o = opts.get("order");
                        order = o != null && "asc".equalsIgnoreCase(o.toString())
                                ? SortOrder.Asc : SortOrder.Desc;
                    }
                    SortOrder finalOrder = order;
                    if ("_score".equals(field)) {
                        reqBuilder.sort(s -> s.score(sc -> sc.order(finalOrder)));
                    } else {
                        reqBuilder.sort(s -> s.field(f -> f.field(field).order(finalOrder)));
                    }
                });
            }
        }
    }

    private void applySourceToRequest(Object source, SearchRequest.Builder reqBuilder) {
        if (source == null) return;
        if (source instanceof Boolean b && !b) {
            reqBuilder.source(s -> s.fetch(false));
            return;
        }
        if (source instanceof List<?> fields) {
            List<String> includes = fields.stream().map(Object::toString).toList();
            reqBuilder.source(s -> s.filter(f -> f.includes(includes)));
        } else if (source instanceof Map<?, ?> map && map.containsKey("includes")) {
            Object includes = map.get("includes");
            if (includes instanceof List<?> list) {
                List<String> includeList = list.stream().map(Object::toString).toList();
                reqBuilder.source(s -> s.filter(f -> f.includes(includeList)));
            }
        }
    }

    private void applyHighlightToRequest(TurDslQueryRequest.TurDslHighlight hl,
                                          SearchRequest.Builder reqBuilder) {
        reqBuilder.highlight(h -> {
            if (hl.fields() != null) {
                List<co.elastic.clients.util.NamedValue<HighlightField>> hlFields =
                        hl.fields().keySet().stream()
                                .map(fieldName -> co.elastic.clients.util.NamedValue.of(
                                        fieldName, HighlightField.of(hf -> hf)))
                                .toList();
                h.fields(hlFields);
            }
            if (hl.preTags() != null && !hl.preTags().isEmpty()) h.preTags(hl.preTags());
            if (hl.postTags() != null && !hl.postTags().isEmpty()) h.postTags(hl.postTags());
            if (hl.fragmentSize() != null) h.fragmentSize(hl.fragmentSize());
            if (hl.numberOfFragments() != null) h.numberOfFragments(hl.numberOfFragments());
            return h;
        });
    }

    private void applyAggregationsToRequest(Map<String, TurDslAggregation> aggs,
                                             SearchRequest.Builder reqBuilder) {
        Map<String, Aggregation> esAggs = new LinkedHashMap<>();
        buildAggMap(aggs, esAggs);
        reqBuilder.aggregations(esAggs);
    }

    private void buildAggMap(Map<String, TurDslAggregation> aggs,
                              Map<String, Aggregation> esAggs) {
        for (var entry : aggs.entrySet()) {
            String aggName = entry.getKey();
            switch (entry.getValue()) {
                case TurDslAggregation.TermsAgg(var field, var size) ->
                        esAggs.put(aggName, Aggregation.of(a -> a.terms(t -> {
                            t.field(field);
                            if (size != null) t.size(size);
                            return t;
                        })));
                case TurDslAggregation.RangeAgg(var field, var ranges) ->
                        esAggs.put(aggName, Aggregation.of(a -> a.range(r -> {
                            r.field(field);
                            for (var range : ranges) {
                                r.ranges(rr -> {
                                    if (range.key() != null) rr.key(range.key());
                                    if (range.from() != null)
                                        rr.from(toDouble(range.from()));
                                    if (range.to() != null)
                                        rr.to(toDouble(range.to()));
                                    return rr;
                                });
                            }
                            return r;
                        })));
                case TurDslAggregation.DateHistogramAgg(var field, var calendarInterval,
                                                         var fixedInterval, var format) ->
                        esAggs.put(aggName, Aggregation.of(a -> a.dateHistogram(dh -> {
                            dh.field(field);
                            if (calendarInterval != null)
                                dh.calendarInterval(co.elastic.clients.elasticsearch._types
                                        .aggregations.CalendarInterval._DESERIALIZER
                                        .parse(calendarInterval));
                            if (format != null) dh.format(format);
                            return dh;
                        })));
                case TurDslAggregation.AvgAgg(var field) ->
                        esAggs.put(aggName, Aggregation.of(a -> a.avg(av -> av.field(field))));
                case TurDslAggregation.SumAgg(var field) ->
                        esAggs.put(aggName, Aggregation.of(a -> a.sum(s -> s.field(field))));
                case TurDslAggregation.MinAgg(var field) ->
                        esAggs.put(aggName, Aggregation.of(a -> a.min(m -> m.field(field))));
                case TurDslAggregation.MaxAgg(var field) ->
                        esAggs.put(aggName, Aggregation.of(a -> a.max(m -> m.field(field))));
                case TurDslAggregation.CardinalityAgg(var field, var precision) ->
                        esAggs.put(aggName, Aggregation.of(a -> a.cardinality(c -> {
                            c.field(field);
                            if (precision != null) c.precisionThreshold(precision);
                            return c;
                        })));
                case TurDslAggregation.ValueCountAgg(var field) ->
                        esAggs.put(aggName, Aggregation.of(a -> a.valueCount(vc -> vc.field(field))));
                case TurDslAggregation.HistogramAgg(var field, var interval, var offset) ->
                        esAggs.put(aggName, Aggregation.of(a -> a.histogram(h -> {
                            h.field(field);
                            if (interval != null) h.interval(interval);
                            if (offset != null) h.offset(offset);
                            return h;
                        })));
                case TurDslAggregation.FilterAgg(var filter) ->
                        esAggs.put(aggName, Aggregation.of(a -> a.filter(translateQuery(filter))));
                case TurDslAggregation.StatsAgg(var field) ->
                        esAggs.put(aggName, Aggregation.of(a -> a.stats(s -> s.field(field))));
                case TurDslAggregation.ExtendedStatsAgg(var field) ->
                        esAggs.put(aggName, Aggregation.of(a -> a.extendedStats(es -> es.field(field))));
                case TurDslAggregation.PercentilesAgg(var field, var percents) ->
                        esAggs.put(aggName, Aggregation.of(a -> a.percentiles(pc -> {
                            pc.field(field);
                            if (percents != null) pc.percents(percents);
                            return pc;
                        })));
                case TurDslAggregation.TopHitsAgg(var size, var sort, var source) ->
                        esAggs.put(aggName, Aggregation.of(a -> a.topHits(th -> {
                            if (size != null) th.size(size);
                            return th;
                        })));
                case TurDslAggregation.CompositeAgg(var sources, var size, var after) ->
                        esAggs.put(aggName, Aggregation.of(a -> a.composite(c -> {
                            if (size != null) c.size(size);
                            List<co.elastic.clients.util.NamedValue<
                                    co.elastic.clients.elasticsearch._types.aggregations
                                            .CompositeAggregationSource>> esSources = new ArrayList<>();
                            for (var srcMap : sources) {
                                for (var e : srcMap.entrySet()) {
                                    var cs = e.getValue();
                                    esSources.add(co.elastic.clients.util.NamedValue.of(
                                            e.getKey(),
                                            co.elastic.clients.elasticsearch._types.aggregations
                                                    .CompositeAggregationSource.of(cas ->
                                                    cas.terms(t -> t.field(cs.field())))));
                                }
                            }
                            c.sources(esSources);
                            return c;
                        })));
                case TurDslAggregation.FiltersAgg(var filters) ->
                        esAggs.put(aggName, Aggregation.of(a -> a.filters(f -> {
                            Map<String, Query> esFilters = new LinkedHashMap<>();
                            filters.forEach((k, v) -> esFilters.put(k, translateQuery(v)));
                            f.filters(fb -> fb.keyed(esFilters));
                            return f;
                        })));
                case TurDslAggregation.SignificantTermsAgg(var field, var size, var minDocCount) ->
                        esAggs.put(aggName, Aggregation.of(a -> a.significantTerms(st -> {
                            st.field(field);
                            if (size != null) st.size(size);
                            if (minDocCount != null) st.minDocCount(minDocCount.longValue());
                            return st;
                        })));
                case TurDslAggregation.NestedAgg(var path) ->
                        esAggs.put(aggName, Aggregation.of(a -> a.nested(n -> n.path(path))));
                case TurDslAggregation.ReverseNestedAgg(var path) ->
                        esAggs.put(aggName, Aggregation.of(a -> a.reverseNested(rn -> {
                            if (path != null) rn.path(path);
                            return rn;
                        })));
                case TurDslAggregation.AutoDateHistogramAgg(var field, var buckets, var format) ->
                        esAggs.put(aggName, Aggregation.of(a -> a.autoDateHistogram(adh -> {
                            adh.field(field);
                            if (buckets != null) adh.buckets(buckets);
                            if (format != null) adh.format(format);
                            return adh;
                        })));
                case TurDslAggregation.MultiTermsAgg(var multiTermFields, var size) ->
                        esAggs.put(aggName, Aggregation.of(a -> a.multiTerms(mt -> {
                            mt.terms(multiTermFields.stream()
                                    .map(f -> co.elastic.clients.elasticsearch._types.aggregations
                                            .MultiTermLookup.of(l -> l.field(f.field())))
                                    .toList());
                            if (size != null) mt.size(size);
                            return mt;
                        })));
                case TurDslAggregation.RareTermsAgg(var field, var maxDocCount) ->
                        esAggs.put(aggName, Aggregation.of(a -> a.rareTerms(rt -> {
                            rt.field(field);
                            if (maxDocCount != null) rt.maxDocCount(maxDocCount.longValue());
                            return rt;
                        })));
                case TurDslAggregation.TopMetricsAgg(var sort, var metrics) ->
                        esAggs.put(aggName, Aggregation.of(a -> a.topMetrics(tm -> {
                            tm.metrics(metrics.stream()
                                    .map(m -> co.elastic.clients.elasticsearch._types.aggregations
                                            .TopMetricsValue.of(v -> v.field(m)))
                                    .toList());
                            return tm;
                        })));
                case TurDslAggregation.PercentileRanksAgg(var field, var values) ->
                        esAggs.put(aggName, Aggregation.of(a -> a.percentileRanks(pr -> {
                            pr.field(field);
                            if (values != null) pr.values(values);
                            return pr;
                        })));
                // --- Low-priority aggregations ---
                case TurDslAggregation.GeoDistanceAgg(var field, var origin, var ranges) ->
                        esAggs.put(aggName, Aggregation.of(a -> a.geoDistance(gd -> {
                            gd.field(field);
                            if (origin != null) gd.origin(g -> g.latlon(ll ->
                                    ll.lat(origin.lat()).lon(origin.lon())));
                            if (ranges != null) {
                                for (var range : ranges) {
                                    gd.ranges(r -> {
                                        if (range.from() != null) r.from(Double.parseDouble(range.from().toString()));
                                        if (range.to() != null) r.to(Double.parseDouble(range.to().toString()));
                                        if (range.key() != null) r.key(range.key());
                                        return r;
                                    });
                                }
                            }
                            return gd;
                        })));
                case TurDslAggregation.GeoBoundsAgg(var field) ->
                        esAggs.put(aggName, Aggregation.of(a -> a.geoBounds(gb -> gb.field(field))));
                case TurDslAggregation.GeoCentroidAgg(var field) ->
                        esAggs.put(aggName, Aggregation.of(a -> a.geoCentroid(gc -> gc.field(field))));
                case TurDslAggregation.SamplerAgg(var shardSize) ->
                        esAggs.put(aggName, Aggregation.of(a -> a.sampler(s -> {
                            if (shardSize != null) s.shardSize(shardSize);
                            return s;
                        })));
                case TurDslAggregation.DiversifiedSamplerAgg(var field, var shardSize) ->
                        esAggs.put(aggName, Aggregation.of(a -> a.diversifiedSampler(ds -> {
                            ds.field(field);
                            if (shardSize != null) ds.shardSize(shardSize);
                            return ds;
                        })));
                case TurDslAggregation.AdjacencyMatrixAgg(var filters) ->
                        esAggs.put(aggName, Aggregation.of(a -> a.adjacencyMatrix(am -> {
                            Map<String, Query> ef = new LinkedHashMap<>();
                            filters.forEach((k, v) -> ef.put(k, translateQuery(v)));
                            am.filters(ef);
                            return am;
                        })));
                case TurDslAggregation.ScriptedMetricAgg(var initS, var mapS, var combS, var redS) ->
                        esAggs.put(aggName, Aggregation.of(a -> a.scriptedMetric(sm -> {
                            if (initS != null) sm.initScript(s -> s.source(src -> src.scriptString(initS)));
                            if (mapS != null) sm.mapScript(s -> s.source(src -> src.scriptString(mapS)));
                            if (combS != null) sm.combineScript(s -> s.source(src -> src.scriptString(combS)));
                            if (redS != null) sm.reduceScript(s -> s.source(src -> src.scriptString(redS)));
                            return sm;
                        })));
                case TurDslAggregation.MedianAbsoluteDeviationAgg(var field) ->
                        esAggs.put(aggName, Aggregation.of(a -> a.medianAbsoluteDeviation(
                                mad -> mad.field(field))));
                case TurDslAggregation.MatrixStatsAgg(var fields) ->
                        esAggs.put(aggName, Aggregation.of(a -> a.matrixStats(ms -> ms.fields(fields))));
                case TurDslAggregation.BoxplotAgg(var field) ->
                        esAggs.put(aggName, Aggregation.of(a -> a.boxplot(bp -> bp.field(field))));
                case TurDslAggregation.StringStatsAgg(var field) ->
                        esAggs.put(aggName, Aggregation.of(a -> a.stringStats(ss -> ss.field(field))));
                case TurDslAggregation.TTestAgg(var popA, var popB, var type) ->
                        esAggs.put(aggName, Aggregation.of(a -> a.tTest(tt -> {
                            if (popA != null) tt.a(pa -> {
                                if (popA.field() != null) pa.field(popA.field());
                                if (popA.filter() != null) pa.filter(translateQuery(popA.filter()));
                                return pa;
                            });
                            if (popB != null) tt.b(pb -> {
                                if (popB.field() != null) pb.field(popB.field());
                                if (popB.filter() != null) pb.filter(translateQuery(popB.filter()));
                                return pb;
                            });
                            return tt;
                        })));
                case TurDslAggregation.VariableWidthHistogramAgg(var field, var buckets) ->
                        esAggs.put(aggName, Aggregation.of(a -> a.variableWidthHistogram(vwh -> {
                            vwh.field(field);
                            if (buckets != null) vwh.buckets(buckets);
                            return vwh;
                        })));
                case TurDslAggregation.RateAgg(var field, var unit) ->
                        esAggs.put(aggName, Aggregation.of(a -> a.rate(r -> {
                            if (field != null) r.field(field);
                            return r;
                        })));
                case TurDslAggregation.WithSubAggs(var parent, var subAggs) -> {
                    // Build parent aggregation first
                    Map<String, TurDslAggregation> parentWrapper = Map.of(aggName, parent);
                    Map<String, Aggregation> builtParent = new LinkedHashMap<>();
                    buildAggMap(parentWrapper, builtParent);
                    // Build sub-aggregations
                    Map<String, Aggregation> builtSubs = new LinkedHashMap<>();
                    buildAggMap(subAggs, builtSubs);
                    // Wrap: create a new Aggregation that has the parent type + sub-aggs
                    if (builtParent.containsKey(aggName)) {
                        esAggs.put(aggName, Aggregation.of(a -> {
                            a.aggregations(builtSubs);
                            // Copy the parent type — use the _kind and serialize approach
                            // Simplification: re-add parent type directly
                            if (parent instanceof TurDslAggregation.TermsAgg ta) {
                                a.terms(t -> { t.field(ta.field()); if (ta.size() != null) t.size(ta.size()); return t; });
                            } else if (parent instanceof TurDslAggregation.DateHistogramAgg dha) {
                                a.dateHistogram(dh -> { dh.field(dha.field()); return dh; });
                            } else if (parent instanceof TurDslAggregation.HistogramAgg ha) {
                                a.histogram(h -> { h.field(ha.field()); if (ha.interval() != null) h.interval(ha.interval()); return h; });
                            } else if (parent instanceof TurDslAggregation.FilterAgg fa) {
                                a.filter(translateQuery(fa.filter()));
                            } else if (parent instanceof TurDslAggregation.FiltersAgg fla) {
                                Map<String, Query> ef = new LinkedHashMap<>();
                                fla.filters().forEach((k, v) -> ef.put(k, translateQuery(v)));
                                a.filters(f -> { f.filters(fb -> fb.keyed(ef)); return f; });
                            } else if (parent instanceof TurDslAggregation.RangeAgg ra) {
                                a.range(r -> { r.field(ra.field()); return r; });
                            }
                            return a;
                        }));
                    }
                }
            }
        }
    }

    // ==================== Collapse ====================

    private void applyCollapseToRequest(TurDslQueryRequest.TurDslCollapse collapse,
                                         SearchRequest.Builder reqBuilder) {
        reqBuilder.collapse(c -> {
            c.field(collapse.field());
            if (collapse.maxConcurrentGroupSearches() != null) {
                c.maxConcurrentGroupSearches(collapse.maxConcurrentGroupSearches());
            }
            if (collapse.innerHits() != null) {
                var ih = collapse.innerHits();
                c.innerHits(i -> {
                    if (ih.name() != null) i.name(ih.name());
                    if (ih.size() != null) i.size(ih.size());
                    return i;
                });
            }
            return c;
        });
    }

    // ==================== Suggest ====================

    private void applySuggestToRequest(Map<String, TurDslQueryRequest.TurDslSuggest> suggests,
                                        SearchRequest.Builder reqBuilder) {
        reqBuilder.suggest(s -> {
            for (var entry : suggests.entrySet()) {
                String name = entry.getKey();
                var suggest = entry.getValue();
                s.suggesters(name, sg -> {
                    if (suggest.text() != null) sg.text(suggest.text());
                    if (suggest.term() != null) {
                        sg.term(t -> {
                            t.field(suggest.term().field());
                            if (suggest.term().size() != null) t.size(suggest.term().size());
                            return t;
                        });
                    } else if (suggest.phrase() != null) {
                        sg.phrase(p -> {
                            p.field(suggest.phrase().field());
                            if (suggest.phrase().size() != null) p.size(suggest.phrase().size());
                            return p;
                        });
                    } else if (suggest.completion() != null) {
                        sg.completion(comp -> {
                            comp.field(suggest.completion().field());
                            if (suggest.completion().size() != null)
                                comp.size(suggest.completion().size());
                            if (suggest.completion().skipDuplicates() != null)
                                comp.skipDuplicates(suggest.completion().skipDuplicates());
                            if (suggest.completion().fuzzy() != null) {
                                comp.fuzzy(fz -> fz.fuzziness(
                                        suggest.completion().fuzzy().fuzziness() != null
                                                ? suggest.completion().fuzzy().fuzziness().toString()
                                                : "AUTO"));
                            }
                            return comp;
                        });
                    }
                    return sg;
                });
            }
            return s;
        });
    }

    @SuppressWarnings("unchecked")
    private TurDslSearchResponse attachSuggestResults(TurDslSearchResponse dslResponse,
                                                       SearchResponse<Map> response) {
        Map<String, List<TurDslSearchResponse.SuggestResult>> suggestMap = new LinkedHashMap<>();
        for (var entry : response.suggest().entrySet()) {
            List<TurDslSearchResponse.SuggestResult> results = new ArrayList<>();
            for (var suggestion : entry.getValue()) {
                List<TurDslSearchResponse.SuggestOption> options = new ArrayList<>();
                for (var option : suggestion.completion().options()) {
                    Map<String, Object> src = option.source() != null
                            ? (Map<String, Object>) option.source() : null;
                    options.add(new TurDslSearchResponse.SuggestOption(
                            option.text(), option.score(), option.id(), src));
                }
                results.add(new TurDslSearchResponse.SuggestResult(
                        suggestion.completion().text(),
                        suggestion.completion().offset(),
                        suggestion.completion().length(),
                        options));
            }
            suggestMap.put(entry.getKey(), results);
        }
        return new TurDslSearchResponse(dslResponse.took(), dslResponse.timedOut(),
                dslResponse.hits(), dslResponse.aggregations(), suggestMap);
    }

    // ==================== Rescore ====================

    private void applyRescoreToRequest(List<TurDslQueryRequest.TurDslRescore> rescores,
                                        SearchRequest.Builder reqBuilder) {
        for (var rescore : rescores) {
            reqBuilder.rescore(r -> {
                if (rescore.windowSize() != null) r.windowSize(rescore.windowSize());
                if (rescore.query() != null) {
                    r.query(rq -> {
                        if (rescore.query().rescoreQuery() != null) {
                            rq.query(translateQuery(rescore.query().rescoreQuery()));
                        }
                        if (rescore.query().queryWeight() != null) {
                            rq.queryWeight(rescore.query().queryWeight());
                        }
                        if (rescore.query().rescoreQueryWeight() != null) {
                            rq.rescoreQueryWeight(rescore.query().rescoreQueryWeight());
                        }
                        return rq;
                    });
                }
                return r;
            });
        }
    }

    // ==================== Script Fields ====================

    private void applyScriptFieldsToRequest(
            Map<String, TurDslQueryRequest.TurDslScriptField> scriptFields,
            SearchRequest.Builder reqBuilder) {
        Map<String, co.elastic.clients.elasticsearch._types.ScriptField> esFields = new LinkedHashMap<>();
        scriptFields.forEach((name, sf) -> {
            if (sf.script() != null && sf.script().source() != null) {
                esFields.put(name, co.elastic.clients.elasticsearch._types.ScriptField.of(f ->
                        f.script(s -> {
                            s.source(src -> src.scriptString(sf.script().source()));
                            if (sf.script().lang() != null) s.lang(sf.script().lang());
                            if (sf.script().params() != null) {
                                Map<String, co.elastic.clients.json.JsonData> p = new LinkedHashMap<>();
                                sf.script().params().forEach((k, v) ->
                                        p.put(k, co.elastic.clients.json.JsonData.of(v)));
                                s.params(p);
                            }
                            return s;
                        })));
            }
        });
        reqBuilder.scriptFields(esFields);
    }

    // ==================== Helpers ====================

    private static Double toDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
