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
        implements TurDslSearchEngineExecutor<Query, SearchResponse<Map<String, Object>>> {

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

                        applyQueryShaping(reqBuilder, request);
                        applyResultFeatures(reqBuilder, request);
                        applyBoostingAndTracking(reqBuilder, request);
                        applyScrollPitOptions(reqBuilder, request);
                        applyFieldAndRoutingOptions(reqBuilder, request);

                        SearchResponse<Map<String, Object>> response = client.search(
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

    // ============ SearchRequest feature appliers (S3776: keep execute() flat) ============

    private void applyQueryShaping(SearchRequest.Builder reqBuilder, TurDslQueryRequest request) {
        if (request.sort() != null) {
            applySortToRequest(request.sort(), reqBuilder);
        }
        applySourceToRequest(request.source(), reqBuilder);
        if (request.highlight() != null) {
            applyHighlightToRequest(request.highlight(), reqBuilder);
        }
        if (request.aggs() != null && !request.aggs().isEmpty()) {
            applyAggregationsToRequest(request.aggs(), reqBuilder);
        }
        if (request.postFilter() != null) {
            reqBuilder.postFilter(translateQuery(request.postFilter()));
        }
        if (request.minScore() != null) {
            reqBuilder.minScore(request.minScore());
        }
    }

    private void applyResultFeatures(SearchRequest.Builder reqBuilder, TurDslQueryRequest request) {
        if (request.searchAfter() != null && !request.searchAfter().isEmpty()) {
            reqBuilder.searchAfter(request.searchAfter().stream()
                    .map(v -> FieldValue.of(v.toString())).toList());
        }
        if (request.collapse() != null) {
            applyCollapseToRequest(request.collapse(), reqBuilder);
        }
        if (request.suggest() != null && !request.suggest().isEmpty()) {
            applySuggestToRequest(request.suggest(), reqBuilder);
        }
        if (request.rescore() != null && !request.rescore().isEmpty()) {
            applyRescoreToRequest(request.rescore(), reqBuilder);
        }
        if (request.timeout() != null) {
            reqBuilder.timeout(request.timeout());
        }
        if (request.explain() != null && request.explain()) {
            reqBuilder.explain(true);
        }
        if (request.scriptFields() != null && !request.scriptFields().isEmpty()) {
            applyScriptFieldsToRequest(request.scriptFields(), reqBuilder);
        }
    }

    private void applyBoostingAndTracking(SearchRequest.Builder reqBuilder, TurDslQueryRequest request) {
        if (request.indicesBoost() != null && !request.indicesBoost().isEmpty()) {
            List<co.elastic.clients.util.NamedValue<Double>> boosts = new ArrayList<>();
            for (var boostMap : request.indicesBoost()) {
                boostMap.forEach((idx, val) ->
                        boosts.add(co.elastic.clients.util.NamedValue.of(idx, val)));
            }
            reqBuilder.indicesBoost(boosts);
        }
        if (request.trackTotalHits() != null) {
            if (request.trackTotalHits() instanceof Boolean b) {
                reqBuilder.trackTotalHits(t -> t.enabled(b));
            } else if (request.trackTotalHits() instanceof Number n) {
                reqBuilder.trackTotalHits(t -> t.count(n.intValue()));
            }
        }
    }

    private void applyScrollPitOptions(SearchRequest.Builder reqBuilder, TurDslQueryRequest request) {
        if (request.storedFields() != null && !request.storedFields().isEmpty()) {
            reqBuilder.storedFields(request.storedFields());
        }
        if (request.scroll() != null) {
            reqBuilder.scroll(s -> s.time(request.scroll()));
        }
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
    }

    private void applyFieldAndRoutingOptions(SearchRequest.Builder reqBuilder, TurDslQueryRequest request) {
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
    }

    // ==================== DSL → Elasticsearch Query Translation ====================

    @Override
    public Query translateQuery(TurDslQuery query) {
        return switch (query) {
            case TurDslQuery.MatchAll() ->
                    Query.of(q -> q.matchAll(m -> m));

            case TurDslQuery.Match dsl -> queryMatch(dsl);

            case TurDslQuery.MultiMatch dsl -> queryMultiMatch(dsl);

            case TurDslQuery.Term(var field, var value) ->
                    Query.of(q -> q.term(t -> t.field(field)
                            .value(FieldValue.of(value.toString()))));

            case TurDslQuery.Terms dsl -> queryTerms(dsl);

            case TurDslQuery.Bool dsl -> queryBool(dsl);

            case TurDslQuery.Range dsl -> queryRange(dsl);

            case TurDslQuery.Wildcard(var field, var value) ->
                    Query.of(q -> q.wildcard(w -> w.field(field).value(value)));

            case TurDslQuery.Prefix(var field, var value) ->
                    Query.of(q -> q.prefix(p -> p.field(field).value(value)));

            case TurDslQuery.Exists(var field) ->
                    Query.of(q -> q.exists(e -> e.field(field)));

            case TurDslQuery.QueryString dsl -> queryQueryString(dsl);

            case TurDslQuery.Fuzzy dsl -> queryFuzzy(dsl);

            case TurDslQuery.Ids(var values) ->
                    Query.of(q -> q.ids(i -> i.values(values)));

            case TurDslQuery.MatchPhrase dsl -> queryMatchPhrase(dsl);

            case TurDslQuery.MatchPhrasePrefix dsl -> queryMatchPhrasePrefix(dsl);

            case TurDslQuery.SimpleQueryString dsl -> querySimpleQueryString(dsl);

            case TurDslQuery.Regexp dsl -> queryRegexp(dsl);

            case TurDslQuery.ConstantScore dsl -> queryConstantScore(dsl);

            case TurDslQuery.DisMax dsl -> queryDisMax(dsl);

            case TurDslQuery.Boosting dsl -> queryBoosting(dsl);

            case TurDslQuery.Nested dsl -> queryNested(dsl);

            case TurDslQuery.FunctionScore dsl -> queryFunctionScore(dsl);

            case TurDslQuery.ScriptScore dsl -> queryScriptScore(dsl);

            case TurDslQuery.Knn dsl -> queryKnn(dsl);

            case TurDslQuery.MoreLikeThis dsl -> queryMoreLikeThis(dsl);

            case TurDslQuery.CombinedFields dsl -> queryCombinedFields(dsl);

            case TurDslQuery.MatchBoolPrefix(var field, var queryText) ->
                    Query.of(q -> q.matchBoolPrefix(mbp -> mbp.field(field).query(queryText)));

            // Less-common query families (pinned / geo / span / join / feature / wrapper)
            // live in a sibling switch to keep each below the case-count limit.
            default -> translateQueryExtended(query);
        };
    }

    /** Second-tier dispatch for the rarer query families — see {@link #translateQuery}. */
    private Query translateQueryExtended(TurDslQuery query) {
        return switch (query) {
            case TurDslQuery.Pinned dsl -> queryPinned(dsl);

            case TurDslQuery.GeoDistance dsl -> queryGeoDistance(dsl);

            case TurDslQuery.GeoBoundingBox dsl -> queryGeoBoundingBox(dsl);

            case TurDslQuery.TermsSet dsl -> queryTermsSet(dsl);

            // --- Low-priority queries ---
            case TurDslQuery.HasChild dsl -> queryHasChild(dsl);

            case TurDslQuery.HasParent dsl -> queryHasParent(dsl);

            case TurDslQuery.Intervals dsl -> queryIntervals(dsl);

            case TurDslQuery.SpanTerm(var field, var value) ->
                    Query.of(q -> q.spanTerm(st -> st.field(field).value(value)));

            case TurDslQuery.SpanNear dsl -> querySpanNear(dsl);

            case TurDslQuery.SpanOr(var clauses) ->
                    Query.of(q -> q.spanOr(so ->
                            so.clauses(clauses.stream().map(this::toSpanQuery).toList())));

            case TurDslQuery.SpanNot(var include, var exclude) ->
                    Query.of(q -> q.spanNot(sn -> sn
                            .include(toSpanQuery(include))
                            .exclude(toSpanQuery(exclude))));

            case TurDslQuery.SpanFirst dsl -> querySpanFirst(dsl);

            case TurDslQuery.RankFeature dsl -> queryRankFeature(dsl);

            case TurDslQuery.DistanceFeature dsl -> queryDistanceFeature(dsl);

            case TurDslQuery.Wrapper(var encodedQuery) ->
                    Query.of(q -> q.wrapper(w -> w.query(encodedQuery)));

            case TurDslQuery.GeoShape(var field, var shape, var relation) ->
                    Query.of(q -> q.geoShape(gs -> {
                        gs.field(field);
                        return gs;
                    }));

            case TurDslQuery.Percolate dsl -> queryPercolate(dsl);
            default -> throw new IllegalStateException("Unexpected DSL type: " + query);
        };
    }

    // ============ Per-query-type translators (S3776: keep the switch flat) ============

    private Query queryMatch(TurDslQuery.Match dsl) {
        return Query.of(q -> q.match(m -> {
            m.field(dsl.field()).query(dsl.query());
            if ("and".equalsIgnoreCase(dsl.operator())) {
                m.operator(co.elastic.clients.elasticsearch._types.query_dsl.Operator.And);
            }
            return m;
        }));
    }

    private Query queryMultiMatch(TurDslQuery.MultiMatch dsl) {
        return Query.of(q -> q.multiMatch(m -> {
            m.query(dsl.query());
            if (dsl.fields() != null && !dsl.fields().isEmpty()) m.fields(dsl.fields());
            return m;
        }));
    }

    private Query queryTerms(TurDslQuery.Terms dsl) {
        return Query.of(q -> q.terms(t -> t.field(dsl.field())
                .terms(tv -> tv.value(dsl.values().stream()
                        .map(v -> FieldValue.of(v.toString()))
                        .toList()))));
    }

    private Query queryBool(TurDslQuery.Bool dsl) {
        return Query.of(q -> q.bool(b -> applyBoolClauses(b, dsl)));
    }

    private co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder applyBoolClauses(
            co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder b, TurDslQuery.Bool dsl) {
        if (!dsl.must().isEmpty()) {
            b.must(dsl.must().stream().map(this::translateQuery).toList());
        }
        if (!dsl.should().isEmpty()) {
            b.should(dsl.should().stream().map(this::translateQuery).toList());
        }
        if (!dsl.mustNot().isEmpty()) {
            b.mustNot(dsl.mustNot().stream().map(this::translateQuery).toList());
        }
        if (!dsl.filter().isEmpty()) {
            b.filter(dsl.filter().stream().map(this::translateQuery).toList());
        }
        if (dsl.minimumShouldMatch() != null) {
            b.minimumShouldMatch(dsl.minimumShouldMatch().toString());
        }
        return b;
    }

    private Query queryRange(TurDslQuery.Range dsl) {
        return Query.of(q -> q.range(r -> r.untyped(u -> applyRangeBounds(u, dsl))));
    }

    private co.elastic.clients.elasticsearch._types.query_dsl.UntypedRangeQuery.Builder applyRangeBounds(
            co.elastic.clients.elasticsearch._types.query_dsl.UntypedRangeQuery.Builder u,
            TurDslQuery.Range dsl) {
        u.field(dsl.field());
        if (dsl.gte() != null) u.gte(co.elastic.clients.json.JsonData.of(dsl.gte()));
        if (dsl.gt() != null) u.gt(co.elastic.clients.json.JsonData.of(dsl.gt()));
        if (dsl.lte() != null) u.lte(co.elastic.clients.json.JsonData.of(dsl.lte()));
        if (dsl.lt() != null) u.lt(co.elastic.clients.json.JsonData.of(dsl.lt()));
        return u;
    }

    private Query queryQueryString(TurDslQuery.QueryString dsl) {
        return Query.of(q -> q.queryString(qs -> {
            qs.query(dsl.query());
            if (dsl.defaultField() != null) qs.defaultField(dsl.defaultField());
            if ("AND".equalsIgnoreCase(dsl.defaultOperator())) {
                qs.defaultOperator(
                        co.elastic.clients.elasticsearch._types.query_dsl.Operator.And);
            }
            return qs;
        }));
    }

    private Query queryFuzzy(TurDslQuery.Fuzzy dsl) {
        return Query.of(q -> q.fuzzy(f -> {
            f.field(dsl.field()).value(dsl.value());
            if (dsl.fuzziness() != null) f.fuzziness(dsl.fuzziness().toString());
            return f;
        }));
    }

    private Query queryMatchPhrase(TurDslQuery.MatchPhrase dsl) {
        return Query.of(q -> q.matchPhrase(mp -> {
            mp.field(dsl.field()).query(dsl.query());
            if (dsl.slop() != null) mp.slop(dsl.slop());
            return mp;
        }));
    }

    private Query queryMatchPhrasePrefix(TurDslQuery.MatchPhrasePrefix dsl) {
        return Query.of(q -> q.matchPhrasePrefix(mpp -> {
            mpp.field(dsl.field()).query(dsl.query());
            if (dsl.maxExpansions() != null) mpp.maxExpansions(dsl.maxExpansions());
            return mpp;
        }));
    }

    private Query querySimpleQueryString(TurDslQuery.SimpleQueryString dsl) {
        return Query.of(q -> q.simpleQueryString(sqs -> {
            sqs.query(dsl.query());
            if (dsl.fields() != null && !dsl.fields().isEmpty()) sqs.fields(dsl.fields());
            if ("AND".equalsIgnoreCase(dsl.defaultOperator()))
                sqs.defaultOperator(
                        co.elastic.clients.elasticsearch._types.query_dsl.Operator.And);
            return sqs;
        }));
    }

    private Query queryRegexp(TurDslQuery.Regexp dsl) {
        return Query.of(q -> q.regexp(r -> {
            r.field(dsl.field()).value(dsl.value());
            if (dsl.flags() != null) r.flags(dsl.flags());
            return r;
        }));
    }

    private Query queryConstantScore(TurDslQuery.ConstantScore dsl) {
        return Query.of(q -> q.constantScore(cs -> {
            cs.filter(translateQuery(dsl.filter()));
            if (dsl.boost() != null) cs.boost(dsl.boost().floatValue());
            return cs;
        }));
    }

    private Query queryDisMax(TurDslQuery.DisMax dsl) {
        return Query.of(q -> q.disMax(dm -> {
            dm.queries(dsl.queries().stream().map(this::translateQuery).toList());
            if (dsl.tieBreaker() != null) dm.tieBreaker(dsl.tieBreaker());
            return dm;
        }));
    }

    private Query queryBoosting(TurDslQuery.Boosting dsl) {
        return Query.of(q -> q.boosting(b -> {
            b.positive(translateQuery(dsl.positive()));
            b.negative(translateQuery(dsl.negative()));
            if (dsl.negativeBoost() != null) b.negativeBoost(dsl.negativeBoost());
            return b;
        }));
    }

    private Query queryNested(TurDslQuery.Nested dsl) {
        return Query.of(q -> q.nested(n -> {
            n.path(dsl.path()).query(translateQuery(dsl.query()));
            if (dsl.scoreMode() != null) {
                n.scoreMode(co.elastic.clients.elasticsearch._types.query_dsl
                        .ChildScoreMode.valueOf(
                        dsl.scoreMode().substring(0, 1).toUpperCase()
                                + dsl.scoreMode().substring(1).toLowerCase()));
            }
            return n;
        }));
    }

    private Query queryFunctionScore(TurDslQuery.FunctionScore dsl) {
        return Query.of(q -> q.functionScore(fs -> {
            fs.query(translateQuery(dsl.query()));
            if (dsl.functions() != null) {
                fs.functions(dsl.functions().stream().map(this::translateScoreFunction).toList());
            }
            if (dsl.maxBoost() != null) fs.maxBoost(dsl.maxBoost());
            return fs;
        }));
    }

    private Query queryScriptScore(TurDslQuery.ScriptScore dsl) {
        return Query.of(q -> q.scriptScore(ss -> {
            ss.query(translateQuery(dsl.query()));
            if (dsl.script() != null) {
                ss.script(s -> buildScriptScoreScript(s, dsl));
            }
            if (dsl.minScore() != null) ss.minScore(dsl.minScore().floatValue());
            return ss;
        }));
    }

    private co.elastic.clients.elasticsearch._types.Script.Builder buildScriptScoreScript(
            co.elastic.clients.elasticsearch._types.Script.Builder s, TurDslQuery.ScriptScore dsl) {
        if (dsl.script().source() != null) {
            s.source(src -> src.scriptString(dsl.script().source()));
            if (dsl.script().lang() != null) s.lang(dsl.script().lang());
            if (dsl.script().params() != null) {
                java.util.Map<String, co.elastic.clients.json.JsonData> p = new LinkedHashMap<>();
                dsl.script().params().forEach((k, v) ->
                        p.put(k, co.elastic.clients.json.JsonData.of(v)));
                s.params(p);
            }
        }
        return s;
    }

    private Query queryKnn(TurDslQuery.Knn dsl) {
        return Query.of(q -> q.knn(knn -> {
            knn.field(dsl.field())
                    .queryVector(dsl.queryVector().stream()
                            .map(Double::floatValue).toList())
                    .k(dsl.k())
                    .numCandidates(dsl.numCandidates());
            if (dsl.similarity() != null) knn.similarity(dsl.similarity().floatValue());
            if (dsl.filter() != null) knn.filter(translateQuery(dsl.filter()));
            return knn;
        }));
    }

    private Query queryMoreLikeThis(TurDslQuery.MoreLikeThis dsl) {
        return Query.of(q -> q.moreLikeThis(mlt -> applyMoreLikeThis(mlt, dsl)));
    }

    private co.elastic.clients.elasticsearch._types.query_dsl.MoreLikeThisQuery.Builder applyMoreLikeThis(
            co.elastic.clients.elasticsearch._types.query_dsl.MoreLikeThisQuery.Builder mlt,
            TurDslQuery.MoreLikeThis dsl) {
        if (dsl.fields() != null && !dsl.fields().isEmpty()) mlt.fields(dsl.fields());
        if (dsl.likeText() != null) mlt.like(l -> l.text(dsl.likeText()));
        if (dsl.minTermFreq() != null) mlt.minTermFreq(dsl.minTermFreq());
        if (dsl.minDocFreq() != null) mlt.minDocFreq(dsl.minDocFreq());
        if (dsl.maxQueryTerms() != null) mlt.maxQueryTerms(dsl.maxQueryTerms());
        return mlt;
    }

    private Query queryCombinedFields(TurDslQuery.CombinedFields dsl) {
        return Query.of(q -> q.combinedFields(cf -> {
            cf.query(dsl.query());
            if (dsl.fields() != null && !dsl.fields().isEmpty()) cf.fields(dsl.fields());
            if ("and".equalsIgnoreCase(dsl.operator()))
                cf.operator(co.elastic.clients.elasticsearch._types.query_dsl
                        .CombinedFieldsOperator.And);
            return cf;
        }));
    }

    private Query queryPinned(TurDslQuery.Pinned dsl) {
        return Query.of(q -> q.pinned(p -> {
            if (dsl.ids() != null && !dsl.ids().isEmpty()) p.ids(dsl.ids());
            if (dsl.organic() != null) p.organic(translateQuery(dsl.organic()));
            return p;
        }));
    }

    private Query queryGeoDistance(TurDslQuery.GeoDistance dsl) {
        return Query.of(q -> q.geoDistance(gd -> {
            gd.field(dsl.field());
            if (dsl.distance() != null) gd.distance(dsl.distance());
            if (dsl.location() != null) gd.location(gl ->
                    gl.latlon(ll -> ll.lat(dsl.location().lat()).lon(dsl.location().lon())));
            return gd;
        }));
    }

    private Query queryGeoBoundingBox(TurDslQuery.GeoBoundingBox dsl) {
        return Query.of(q -> q.geoBoundingBox(gbb -> {
            gbb.field(dsl.field());
            gbb.boundingBox(bb -> bb.tlbr(tlbr -> applyTlbrBounds(tlbr, dsl)));
            return gbb;
        }));
    }

    private co.elastic.clients.elasticsearch._types.TopLeftBottomRightGeoBounds.Builder applyTlbrBounds(
            co.elastic.clients.elasticsearch._types.TopLeftBottomRightGeoBounds.Builder tlbr,
            TurDslQuery.GeoBoundingBox dsl) {
        if (dsl.topLeft() != null) tlbr.topLeft(gl ->
                gl.latlon(ll -> ll.lat(dsl.topLeft().lat()).lon(dsl.topLeft().lon())));
        if (dsl.bottomRight() != null) tlbr.bottomRight(gl ->
                gl.latlon(ll -> ll.lat(dsl.bottomRight().lat()).lon(dsl.bottomRight().lon())));
        return tlbr;
    }

    private Query queryTermsSet(TurDslQuery.TermsSet dsl) {
        return Query.of(q -> q.termsSet(ts -> {
            ts.field(dsl.field());
            if (dsl.terms() != null)
                ts.terms(dsl.terms().stream()
                        .map(v -> FieldValue.of(v.toString())).toList());
            if (dsl.minimumShouldMatchField() != null) ts.minimumShouldMatchField(dsl.minimumShouldMatchField());
            return ts;
        }));
    }

    private Query queryHasChild(TurDslQuery.HasChild dsl) {
        return Query.of(q -> q.hasChild(hc -> {
            hc.type(dsl.type()).query(translateQuery(dsl.query()));
            if (dsl.minChildren() != null) hc.minChildren(dsl.minChildren());
            if (dsl.maxChildren() != null) hc.maxChildren(dsl.maxChildren());
            return hc;
        }));
    }

    private Query queryHasParent(TurDslQuery.HasParent dsl) {
        return Query.of(q -> q.hasParent(hp -> {
            hp.parentType(dsl.parentType()).query(translateQuery(dsl.query()));
            if (dsl.score() != null) hp.score(dsl.score());
            return hp;
        }));
    }

    private Query queryIntervals(TurDslQuery.Intervals dsl) {
        return Query.of(q -> q.intervals(iv -> {
            iv.field(dsl.field());
            if (dsl.rule() != null && "match".equals(dsl.rule().type())) {
                iv.match(m -> buildIntervalsMatch(m, dsl));
            }
            return iv;
        }));
    }

    private co.elastic.clients.elasticsearch._types.query_dsl.IntervalsMatch.Builder buildIntervalsMatch(
            co.elastic.clients.elasticsearch._types.query_dsl.IntervalsMatch.Builder m,
            TurDslQuery.Intervals dsl) {
        if (dsl.rule().query() != null) m.query(dsl.rule().query());
        if (dsl.rule().maxGaps() != null) m.maxGaps(dsl.rule().maxGaps());
        if (dsl.rule().ordered() != null) m.ordered(dsl.rule().ordered());
        return m;
    }

    private Query querySpanNear(TurDslQuery.SpanNear dsl) {
        return Query.of(q -> q.spanNear(sn -> {
            sn.clauses(dsl.clauses().stream().map(this::toSpanQuery).toList());
            if (dsl.slop() != null) sn.slop(dsl.slop());
            if (dsl.inOrder() != null) sn.inOrder(dsl.inOrder());
            return sn;
        }));
    }

    private Query querySpanFirst(TurDslQuery.SpanFirst dsl) {
        return Query.of(q -> q.spanFirst(sf -> {
            sf.match(toSpanQuery(dsl.match()));
            if (dsl.end() != null) sf.end(dsl.end());
            return sf;
        }));
    }

    private Query queryRankFeature(TurDslQuery.RankFeature dsl) {
        return Query.of(q -> q.rankFeature(rf -> {
            rf.field(dsl.field());
            if (dsl.boost() != null) rf.boost(dsl.boost().floatValue());
            return rf;
        }));
    }

    private Query queryDistanceFeature(TurDslQuery.DistanceFeature dsl) {
        return Query.of(q -> q.distanceFeature(df -> df.untyped(u -> {
            u.field(dsl.field());
            if (dsl.origin() != null) u.origin(co.elastic.clients.json.JsonData.of(dsl.origin()));
            if (dsl.pivot() != null) u.pivot(co.elastic.clients.json.JsonData.of(dsl.pivot()));
            return u;
        })));
    }

    private Query queryPercolate(TurDslQuery.Percolate dsl) {
        return Query.of(q -> q.percolate(p -> {
            if (dsl.field() != null) p.field(dsl.field());
            if (dsl.document() != null) p.document(co.elastic.clients.json.JsonData.of(dsl.document()));
            return p;
        }));
    }

    private co.elastic.clients.elasticsearch._types.query_dsl.SpanQuery toSpanQuery(TurDslQuery dslQuery) {
        return co.elastic.clients.elasticsearch._types.query_dsl.SpanQuery.of(sq -> {
            switch (dslQuery) {
                case TurDslQuery.SpanTerm(var field, var value) ->
                    sq.spanTerm(st -> st.field(field).value(value));
                case TurDslQuery.SpanNear(var clauses, var slop, var inOrder) ->
                    sq.spanNear(sn -> {
                        sn.clauses(clauses.stream().map(this::toSpanQuery).toList());
                        if (slop != null) { sn.slop(slop); }
                        if (inOrder != null) { sn.inOrder(inOrder); }
                        return sn;
                    });
                case TurDslQuery.SpanOr(var clauses) ->
                    sq.spanOr(so -> so.clauses(clauses.stream().map(this::toSpanQuery).toList()));
                case TurDslQuery.SpanNot(var include, var exclude) ->
                    sq.spanNot(sn -> sn.include(toSpanQuery(include)).exclude(toSpanQuery(exclude)));
                case TurDslQuery.SpanFirst(var match, var end) ->
                    sq.spanFirst(sf -> { sf.match(toSpanQuery(match)); if (end != null) { sf.end(end); } return sf; });
                // Fallback (incl. null): wrap as span_term on "id" field
                case null, default -> sq.spanTerm(st -> st.field("id").value("*"));
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
                f.fieldValueFactor(v -> buildFieldValueFactor(v, fvf));
            }
            if (fn.scriptScore() != null) {
                f.scriptScore(ss -> ss.script(s -> buildScoreFunctionScript(s, fn)));
            }
            return f;
        });
    }

    private co.elastic.clients.elasticsearch._types.query_dsl.FieldValueFactorScoreFunction.Builder
    buildFieldValueFactor(
            co.elastic.clients.elasticsearch._types.query_dsl.FieldValueFactorScoreFunction.Builder v,
            TurDslQuery.FieldValueFactor fvf) {
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
    }

    private co.elastic.clients.elasticsearch._types.Script.Builder buildScoreFunctionScript(
            co.elastic.clients.elasticsearch._types.Script.Builder s, TurDslQuery.ScoreFunction fn) {
        s.source(src -> src.scriptString(fn.scriptScore().source()));
        if (fn.scriptScore().lang() != null) s.lang(fn.scriptScore().lang());
        return s;
    }

    @Override
    public Object translateSort(List<Object> sortEntries) {
        // Sort is applied directly to the request builder via applySortToRequest
        return sortEntries;
    }

    // ==================== Response Building ====================

    @Override
    @SuppressWarnings("unchecked")
    public TurDslSearchResponse buildResponse(SearchResponse<Map<String, Object>> response,
                                               TurDslQueryRequest request, long elapsedMs) {
        long numFound = response.hits().total() != null ? response.hits().total().value() : 0;
        Double maxScore = response.hits().maxScore() != null
                ? response.hits().maxScore() : null;

        List<TurDslSearchResponse.Hit> hits = new ArrayList<>();
        for (Hit<Map<String, Object>> hit : response.hits().hits()) {
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
            SearchResponse<Map<String, Object>> response, Map<String, TurDslAggregation> aggs) {
        Map<String, TurDslSearchResponse.AggregationResult> result = new LinkedHashMap<>();
        for (var entry : aggs.entrySet()) {
            String aggName = entry.getKey();
            var esAgg = response.aggregations().get(aggName);
            if (esAgg == null) {
                continue;
            }
            TurDslSearchResponse.AggregationResult aggResult =
                    parseAggregateResult(entry.getValue(), aggName, esAgg);
            if (aggResult != null) {
                result.put(aggName, aggResult);
            }
        }
        return result.isEmpty() ? null : result;
    }

    /**
     * Maps one ES {@link co.elastic.clients.elasticsearch._types.aggregations.Aggregate}
     * to the DSL result for its declared agg type, or {@code null} when the type is
     * unsupported / unparseable.
     *
     * @since 2026.3.1
     */
    private TurDslSearchResponse.AggregationResult parseAggregateResult(TurDslAggregation agg,
            String aggName, co.elastic.clients.elasticsearch._types.aggregations.Aggregate esAgg) {
        if (agg instanceof TurDslAggregation.TermsAgg) {
            return parseTermsAggResult(aggName, esAgg);
        }
        if (agg instanceof TurDslAggregation.RangeAgg) {
            return parseRangeAggResult(aggName, esAgg);
        }
        if (isSingleMetricAgg(agg)) {
            return parseMetricAggResult(aggName, esAgg);
        }
        return null;
    }

    private static boolean isSingleMetricAgg(TurDslAggregation agg) {
        return agg instanceof TurDslAggregation.AvgAgg
                || agg instanceof TurDslAggregation.SumAgg
                || agg instanceof TurDslAggregation.MinAgg
                || agg instanceof TurDslAggregation.MaxAgg
                || agg instanceof TurDslAggregation.ValueCountAgg
                || agg instanceof TurDslAggregation.CardinalityAgg;
    }

    private TurDslSearchResponse.AggregationResult parseTermsAggResult(String aggName,
            co.elastic.clients.elasticsearch._types.aggregations.Aggregate esAgg) {
        try {
            List<TurDslSearchResponse.Bucket> buckets = new ArrayList<>();
            for (StringTermsBucket bucket : esAgg.sterms().buckets().array()) {
                buckets.add(new TurDslSearchResponse.Bucket(
                        bucket.key().stringValue(), bucket.docCount()));
            }
            return new TurDslSearchResponse.AggregationResult(buckets);
        } catch (Exception e) {
            log.debug("Could not parse terms aggregation '{}': {}", aggName, e.getMessage());
            return null;
        }
    }

    private TurDslSearchResponse.AggregationResult parseRangeAggResult(String aggName,
            co.elastic.clients.elasticsearch._types.aggregations.Aggregate esAgg) {
        try {
            List<TurDslSearchResponse.Bucket> buckets = new ArrayList<>();
            for (var bucket : esAgg.range().buckets().array()) {
                String from = bucket.fromAsString() != null ? bucket.fromAsString() : "*";
                String to = bucket.toAsString() != null ? bucket.toAsString() : "*";
                String key = bucket.key() != null ? bucket.key()
                        : "%s-%s".formatted(from, to);
                buckets.add(new TurDslSearchResponse.Bucket(key, bucket.docCount()));
            }
            return new TurDslSearchResponse.AggregationResult(buckets);
        } catch (Exception e) {
            log.debug("Could not parse range aggregation '{}': {}", aggName, e.getMessage());
            return null;
        }
    }

    private TurDslSearchResponse.AggregationResult parseMetricAggResult(String aggName,
            co.elastic.clients.elasticsearch._types.aggregations.Aggregate esAgg) {
        try {
            double value = esAgg._get() instanceof
                    co.elastic.clients.elasticsearch._types.aggregations.SingleMetricAggregateBase sm
                    ? sm.value() : 0.0;
            return new TurDslSearchResponse.AggregationResult(value);
        } catch (Exception e) {
            log.debug("Could not parse metric aggregation '{}': {}", aggName, e.getMessage());
            return null;
        }
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
                map.forEach((key, val) -> addMapSort(key, val, reqBuilder));
            }
        }
    }

    /** Appends one {@code {field: order}} sort to the request (default descending; {@code _score} aware). */
    private void addMapSort(Object key, Object val, SearchRequest.Builder reqBuilder) {
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
    }

    private void applySourceToRequest(Object source, SearchRequest.Builder reqBuilder) {
        if (source == null) return;
        if (Boolean.FALSE.equals(source)) {
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
                case TurDslAggregation.TermsAgg dsl -> esAggs.put(aggName, aggTerms(dsl));
                case TurDslAggregation.RangeAgg dsl -> esAggs.put(aggName, aggRange(dsl));
                case TurDslAggregation.DateHistogramAgg dsl -> esAggs.put(aggName, aggDateHistogram(dsl));
                case TurDslAggregation.AvgAgg(var field) ->
                        esAggs.put(aggName, Aggregation.of(a -> a.avg(av -> av.field(field))));
                case TurDslAggregation.SumAgg(var field) ->
                        esAggs.put(aggName, Aggregation.of(a -> a.sum(s -> s.field(field))));
                case TurDslAggregation.MinAgg(var field) ->
                        esAggs.put(aggName, Aggregation.of(a -> a.min(m -> m.field(field))));
                case TurDslAggregation.MaxAgg(var field) ->
                        esAggs.put(aggName, Aggregation.of(a -> a.max(m -> m.field(field))));
                case TurDslAggregation.CardinalityAgg dsl -> esAggs.put(aggName, aggCardinality(dsl));
                case TurDslAggregation.ValueCountAgg(var field) ->
                        esAggs.put(aggName, Aggregation.of(a -> a.valueCount(vc -> vc.field(field))));
                case TurDslAggregation.HistogramAgg dsl -> esAggs.put(aggName, aggHistogram(dsl));
                case TurDslAggregation.FilterAgg(var filter) ->
                        esAggs.put(aggName, Aggregation.of(a -> a.filter(translateQuery(filter))));
                case TurDslAggregation.StatsAgg(var field) ->
                        esAggs.put(aggName, Aggregation.of(a -> a.stats(s -> s.field(field))));
                case TurDslAggregation.ExtendedStatsAgg(var field) ->
                        esAggs.put(aggName, Aggregation.of(a -> a.extendedStats(es -> es.field(field))));
                case TurDslAggregation.PercentilesAgg dsl -> esAggs.put(aggName, aggPercentiles(dsl));
                case TurDslAggregation.TopHitsAgg dsl -> esAggs.put(aggName, aggTopHits(dsl));
                case TurDslAggregation.CompositeAgg dsl -> esAggs.put(aggName, aggComposite(dsl));
                case TurDslAggregation.FiltersAgg dsl -> esAggs.put(aggName, aggFilters(dsl));
                case TurDslAggregation.SignificantTermsAgg dsl -> esAggs.put(aggName, aggSignificantTerms(dsl));
                case TurDslAggregation.NestedAgg(var path) ->
                        esAggs.put(aggName, Aggregation.of(a -> a.nested(n -> n.path(path))));
                case TurDslAggregation.ReverseNestedAgg dsl -> esAggs.put(aggName, aggReverseNested(dsl));
                case TurDslAggregation.AutoDateHistogramAgg dsl -> esAggs.put(aggName, aggAutoDateHistogram(dsl));
                case TurDslAggregation.MultiTermsAgg dsl -> esAggs.put(aggName, aggMultiTerms(dsl));
                case TurDslAggregation.RareTermsAgg dsl -> esAggs.put(aggName, aggRareTerms(dsl));
                case TurDslAggregation.TopMetricsAgg dsl -> esAggs.put(aggName, aggTopMetrics(dsl));
                case TurDslAggregation.PercentileRanksAgg dsl -> esAggs.put(aggName, aggPercentileRanks(dsl));
                // Less-common aggregation families live in a sibling switch to
                // keep each below the case-count limit.
                default -> buildAggMapExtended(entry.getValue(), aggName, esAggs);
            }
        }
    }

    /** Second-tier dispatch for the rarer aggregation families — see {@link #buildAggMap}. */
    private void buildAggMapExtended(TurDslAggregation aggregation, String aggName,
                                     Map<String, Aggregation> esAggs) {
        switch (aggregation) {
            // --- Low-priority aggregations ---
            case TurDslAggregation.GeoDistanceAgg dsl -> esAggs.put(aggName, aggGeoDistance(dsl));
            case TurDslAggregation.GeoBoundsAgg(var field) ->
                    esAggs.put(aggName, Aggregation.of(a -> a.geoBounds(gb -> gb.field(field))));
            case TurDslAggregation.GeoCentroidAgg(var field) ->
                    esAggs.put(aggName, Aggregation.of(a -> a.geoCentroid(gc -> gc.field(field))));
            case TurDslAggregation.SamplerAgg dsl -> esAggs.put(aggName, aggSampler(dsl));
            case TurDslAggregation.DiversifiedSamplerAgg dsl -> esAggs.put(aggName, aggDiversifiedSampler(dsl));
            case TurDslAggregation.AdjacencyMatrixAgg dsl -> esAggs.put(aggName, aggAdjacencyMatrix(dsl));
            case TurDslAggregation.ScriptedMetricAgg dsl -> esAggs.put(aggName, aggScriptedMetric(dsl));
            case TurDslAggregation.MedianAbsoluteDeviationAgg(var field) ->
                    esAggs.put(aggName, Aggregation.of(a -> a.medianAbsoluteDeviation(
                            mad -> mad.field(field))));
            case TurDslAggregation.MatrixStatsAgg(var fields) ->
                    esAggs.put(aggName, Aggregation.of(a -> a.matrixStats(ms -> ms.fields(fields))));
            case TurDslAggregation.BoxplotAgg(var field) ->
                    esAggs.put(aggName, Aggregation.of(a -> a.boxplot(bp -> bp.field(field))));
            case TurDslAggregation.StringStatsAgg(var field) ->
                    esAggs.put(aggName, Aggregation.of(a -> a.stringStats(ss -> ss.field(field))));
            case TurDslAggregation.TTestAgg dsl -> esAggs.put(aggName, aggTTest(dsl));
            case TurDslAggregation.VariableWidthHistogramAgg dsl ->
                    esAggs.put(aggName, aggVariableWidthHistogram(dsl));
            case TurDslAggregation.RateAgg dsl -> esAggs.put(aggName, aggRate(dsl));
            case TurDslAggregation.WithSubAggs dsl -> putWithSubAggs(aggName, dsl, esAggs);
            default -> throw new IllegalStateException("Unexpected DSL type: " + aggregation);
        }
    }

    // ============ Per-aggregation-type builders (S3776: keep buildAggMap flat) ============

    private Aggregation aggTerms(TurDslAggregation.TermsAgg dsl) {
        return Aggregation.of(a -> a.terms(t -> {
            t.field(dsl.field());
            if (dsl.size() != null) t.size(dsl.size());
            return t;
        }));
    }

    private Aggregation aggRange(TurDslAggregation.RangeAgg dsl) {
        return Aggregation.of(a -> a.range(r -> {
            r.field(dsl.field());
            for (var range : dsl.ranges()) {
                r.ranges(rr -> applyAggRangeBucket(rr, range));
            }
            return r;
        }));
    }

    private co.elastic.clients.elasticsearch._types.aggregations.AggregationRange.Builder applyAggRangeBucket(
            co.elastic.clients.elasticsearch._types.aggregations.AggregationRange.Builder rr,
            TurDslAggregation.RangeBucket range) {
        if (range.key() != null) rr.key(range.key());
        if (range.from() != null) rr.from(toDouble(range.from()));
        if (range.to() != null) rr.to(toDouble(range.to()));
        return rr;
    }

    private Aggregation aggDateHistogram(TurDslAggregation.DateHistogramAgg dsl) {
        return Aggregation.of(a -> a.dateHistogram(dh -> {
            dh.field(dsl.field());
            if (dsl.calendarInterval() != null)
                dh.calendarInterval(co.elastic.clients.elasticsearch._types
                        .aggregations.CalendarInterval._DESERIALIZER
                        .parse(dsl.calendarInterval()));
            if (dsl.format() != null) dh.format(dsl.format());
            return dh;
        }));
    }

    private Aggregation aggCardinality(TurDslAggregation.CardinalityAgg dsl) {
        return Aggregation.of(a -> a.cardinality(c -> {
            c.field(dsl.field());
            if (dsl.precisionThreshold() != null) c.precisionThreshold(dsl.precisionThreshold());
            return c;
        }));
    }

    private Aggregation aggHistogram(TurDslAggregation.HistogramAgg dsl) {
        return Aggregation.of(a -> a.histogram(h -> {
            h.field(dsl.field());
            if (dsl.interval() != null) h.interval(dsl.interval());
            if (dsl.offset() != null) h.offset(dsl.offset());
            return h;
        }));
    }

    private Aggregation aggPercentiles(TurDslAggregation.PercentilesAgg dsl) {
        return Aggregation.of(a -> a.percentiles(pc -> {
            pc.field(dsl.field());
            if (dsl.percents() != null) pc.percents(dsl.percents());
            return pc;
        }));
    }

    private Aggregation aggTopHits(TurDslAggregation.TopHitsAgg dsl) {
        return Aggregation.of(a -> a.topHits(th -> {
            if (dsl.size() != null) th.size(dsl.size());
            return th;
        }));
    }

    private Aggregation aggComposite(TurDslAggregation.CompositeAgg dsl) {
        return Aggregation.of(a -> a.composite(c -> {
            if (dsl.size() != null) c.size(dsl.size());
            c.sources(buildCompositeSources(dsl.sources()));
            return c;
        }));
    }

    private List<co.elastic.clients.util.NamedValue<
            co.elastic.clients.elasticsearch._types.aggregations.CompositeAggregationSource>>
    buildCompositeSources(List<Map<String, TurDslAggregation.CompositeSource>> sources) {
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
        return esSources;
    }

    private Aggregation aggFilters(TurDslAggregation.FiltersAgg dsl) {
        return Aggregation.of(a -> a.filters(f -> {
            Map<String, Query> esFilters = new LinkedHashMap<>();
            dsl.filters().forEach((k, v) -> esFilters.put(k, translateQuery(v)));
            f.filters(fb -> fb.keyed(esFilters));
            return f;
        }));
    }

    private Aggregation aggSignificantTerms(TurDslAggregation.SignificantTermsAgg dsl) {
        return Aggregation.of(a -> a.significantTerms(st -> {
            st.field(dsl.field());
            if (dsl.size() != null) st.size(dsl.size());
            if (dsl.minDocCount() != null) st.minDocCount(dsl.minDocCount().longValue());
            return st;
        }));
    }

    private Aggregation aggReverseNested(TurDslAggregation.ReverseNestedAgg dsl) {
        return Aggregation.of(a -> a.reverseNested(rn -> {
            if (dsl.path() != null) rn.path(dsl.path());
            return rn;
        }));
    }

    private Aggregation aggAutoDateHistogram(TurDslAggregation.AutoDateHistogramAgg dsl) {
        return Aggregation.of(a -> a.autoDateHistogram(adh -> {
            adh.field(dsl.field());
            if (dsl.buckets() != null) adh.buckets(dsl.buckets());
            if (dsl.format() != null) adh.format(dsl.format());
            return adh;
        }));
    }

    private Aggregation aggMultiTerms(TurDslAggregation.MultiTermsAgg dsl) {
        return Aggregation.of(a -> a.multiTerms(mt -> {
            mt.terms(dsl.multiTermFields().stream()
                    .map(f -> co.elastic.clients.elasticsearch._types.aggregations
                            .MultiTermLookup.of(l -> l.field(f.field())))
                    .toList());
            if (dsl.size() != null) mt.size(dsl.size());
            return mt;
        }));
    }

    private Aggregation aggRareTerms(TurDslAggregation.RareTermsAgg dsl) {
        return Aggregation.of(a -> a.rareTerms(rt -> {
            rt.field(dsl.field());
            if (dsl.maxDocCount() != null) rt.maxDocCount(dsl.maxDocCount().longValue());
            return rt;
        }));
    }

    private Aggregation aggTopMetrics(TurDslAggregation.TopMetricsAgg dsl) {
        return Aggregation.of(a -> a.topMetrics(tm -> {
            tm.metrics(dsl.metrics().stream()
                    .map(m -> co.elastic.clients.elasticsearch._types.aggregations
                            .TopMetricsValue.of(v -> v.field(m)))
                    .toList());
            return tm;
        }));
    }

    private Aggregation aggPercentileRanks(TurDslAggregation.PercentileRanksAgg dsl) {
        return Aggregation.of(a -> a.percentileRanks(pr -> {
            pr.field(dsl.field());
            if (dsl.values() != null) pr.values(dsl.values());
            return pr;
        }));
    }

    private Aggregation aggGeoDistance(TurDslAggregation.GeoDistanceAgg dsl) {
        return Aggregation.of(a -> a.geoDistance(gd -> {
            gd.field(dsl.field());
            if (dsl.origin() != null) gd.origin(g -> g.latlon(ll ->
                    ll.lat(dsl.origin().lat()).lon(dsl.origin().lon())));
            if (dsl.ranges() != null) {
                for (var range : dsl.ranges()) {
                    gd.ranges(r -> applyGeoDistanceRange(r, range));
                }
            }
            return gd;
        }));
    }

    private co.elastic.clients.elasticsearch._types.aggregations.AggregationRange.Builder applyGeoDistanceRange(
            co.elastic.clients.elasticsearch._types.aggregations.AggregationRange.Builder r,
            TurDslAggregation.RangeBucket range) {
        if (range.from() != null) r.from(Double.parseDouble(range.from().toString()));
        if (range.to() != null) r.to(Double.parseDouble(range.to().toString()));
        if (range.key() != null) r.key(range.key());
        return r;
    }

    private Aggregation aggSampler(TurDslAggregation.SamplerAgg dsl) {
        return Aggregation.of(a -> a.sampler(s -> {
            if (dsl.shardSize() != null) s.shardSize(dsl.shardSize());
            return s;
        }));
    }

    private Aggregation aggDiversifiedSampler(TurDslAggregation.DiversifiedSamplerAgg dsl) {
        return Aggregation.of(a -> a.diversifiedSampler(ds -> {
            ds.field(dsl.field());
            if (dsl.shardSize() != null) ds.shardSize(dsl.shardSize());
            return ds;
        }));
    }

    private Aggregation aggAdjacencyMatrix(TurDslAggregation.AdjacencyMatrixAgg dsl) {
        return Aggregation.of(a -> a.adjacencyMatrix(am -> {
            Map<String, Query> ef = new LinkedHashMap<>();
            dsl.filters().forEach((k, v) -> ef.put(k, translateQuery(v)));
            am.filters(ef);
            return am;
        }));
    }

    private Aggregation aggScriptedMetric(TurDslAggregation.ScriptedMetricAgg dsl) {
        return Aggregation.of(a -> a.scriptedMetric(sm -> {
            if (dsl.initScript() != null) sm.initScript(s -> s.source(src -> src.scriptString(dsl.initScript())));
            if (dsl.mapScript() != null) sm.mapScript(s -> s.source(src -> src.scriptString(dsl.mapScript())));
            if (dsl.combineScript() != null) sm.combineScript(s -> s.source(src -> src.scriptString(dsl.combineScript())));
            if (dsl.reduceScript() != null) sm.reduceScript(s -> s.source(src -> src.scriptString(dsl.reduceScript())));
            return sm;
        }));
    }

    private Aggregation aggTTest(TurDslAggregation.TTestAgg dsl) {
        return Aggregation.of(a -> a.tTest(tt -> {
            if (dsl.a() != null) tt.a(pa -> applyTTestPopulation(pa, dsl.a()));
            if (dsl.b() != null) tt.b(pb -> applyTTestPopulation(pb, dsl.b()));
            return tt;
        }));
    }

    private co.elastic.clients.elasticsearch._types.aggregations.TestPopulation.Builder applyTTestPopulation(
            co.elastic.clients.elasticsearch._types.aggregations.TestPopulation.Builder p,
            TurDslAggregation.TTestPopulation pop) {
        if (pop.field() != null) p.field(pop.field());
        if (pop.filter() != null) p.filter(translateQuery(pop.filter()));
        return p;
    }

    private Aggregation aggVariableWidthHistogram(TurDslAggregation.VariableWidthHistogramAgg dsl) {
        return Aggregation.of(a -> a.variableWidthHistogram(vwh -> {
            vwh.field(dsl.field());
            if (dsl.buckets() != null) vwh.buckets(dsl.buckets());
            return vwh;
        }));
    }

    private Aggregation aggRate(TurDslAggregation.RateAgg dsl) {
        return Aggregation.of(a -> a.rate(r -> {
            if (dsl.field() != null) r.field(dsl.field());
            return r;
        }));
    }

    private void putWithSubAggs(String aggName, TurDslAggregation.WithSubAggs dsl,
            Map<String, Aggregation> esAggs) {
        TurDslAggregation parent = dsl.parent();
        Map<String, TurDslAggregation> parentWrapper = Map.of(aggName, parent);
        Map<String, Aggregation> builtParent = new LinkedHashMap<>();
        buildAggMap(parentWrapper, builtParent);
        Map<String, Aggregation> builtSubs = new LinkedHashMap<>();
        buildAggMap(dsl.subAggs(), builtSubs);
        if (builtParent.containsKey(aggName)) {
            esAggs.put(aggName, Aggregation.of(a -> {
                a.aggregations(builtSubs);
                applyParentAggType(a, parent);
                return a;
            }));
        }
    }

    private void applyParentAggType(Aggregation.Builder a, TurDslAggregation parent) {
        if (parent instanceof TurDslAggregation.TermsAgg(String field, Integer size)) {
            a.terms(t -> { t.field(field); if (size != null) { t.size(size); } return t; });
        } else if (parent instanceof TurDslAggregation.DateHistogramAgg dha) {
            a.dateHistogram(dh -> { dh.field(dha.field()); return dh; });
        } else if (parent instanceof TurDslAggregation.HistogramAgg ha) {
            a.histogram(h -> { h.field(ha.field()); if (ha.interval() != null) { h.interval(ha.interval()); } return h; });
        } else if (parent instanceof TurDslAggregation.FilterAgg(var filter)) {
            a.filter(translateQuery(filter));
        } else if (parent instanceof TurDslAggregation.FiltersAgg(var filters)) {
            Map<String, Query> ef = new LinkedHashMap<>();
            filters.forEach((k, v) -> ef.put(k, translateQuery(v)));
            a.filters(f -> { f.filters(fb -> fb.keyed(ef)); return f; });
        } else if (parent instanceof TurDslAggregation.RangeAgg ra) {
            a.range(r -> { r.field(ra.field()); return r; });
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
            suggests.forEach((name, suggest) -> s.suggesters(name, sg -> buildSuggester(sg, suggest)));
            return s;
        });
    }

    private co.elastic.clients.elasticsearch.core.search.FieldSuggester.Builder buildSuggester(
            co.elastic.clients.elasticsearch.core.search.FieldSuggester.Builder sg,
            TurDslQueryRequest.TurDslSuggest suggest) {
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
            sg.completion(comp -> buildCompletionSuggester(comp, suggest));
        }
        return sg;
    }

    private co.elastic.clients.elasticsearch.core.search.CompletionSuggester.Builder buildCompletionSuggester(
            co.elastic.clients.elasticsearch.core.search.CompletionSuggester.Builder comp,
            TurDslQueryRequest.TurDslSuggest suggest) {
        comp.field(suggest.completion().field());
        if (suggest.completion().size() != null) {
            comp.size(suggest.completion().size());
        }
        if (suggest.completion().skipDuplicates() != null) {
            comp.skipDuplicates(suggest.completion().skipDuplicates());
        }
        if (suggest.completion().fuzzy() != null) {
            comp.fuzzy(fz -> fz.fuzziness(
                    suggest.completion().fuzzy().fuzziness() != null
                            ? suggest.completion().fuzzy().fuzziness().toString()
                            : "AUTO"));
        }
        return comp;
    }

    @SuppressWarnings("unchecked")
    private TurDslSearchResponse attachSuggestResults(TurDslSearchResponse dslResponse,
                                                       SearchResponse<Map<String, Object>> response) {
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
                    r.query(rq -> buildRescoreQuery(rq, rescore));
                }
                return r;
            });
        }
    }

    private co.elastic.clients.elasticsearch.core.search.RescoreQuery.Builder buildRescoreQuery(
            co.elastic.clients.elasticsearch.core.search.RescoreQuery.Builder rq,
            TurDslQueryRequest.TurDslRescore rescore) {
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
    }

    // ==================== Script Fields ====================

    private void applyScriptFieldsToRequest(
            Map<String, TurDslQueryRequest.TurDslScriptField> scriptFields,
            SearchRequest.Builder reqBuilder) {
        Map<String, co.elastic.clients.elasticsearch._types.ScriptField> esFields = new LinkedHashMap<>();
        scriptFields.forEach((name, sf) -> {
            if (sf.script() != null && sf.script().source() != null) {
                esFields.put(name, co.elastic.clients.elasticsearch._types.ScriptField.of(f ->
                        f.script(s -> buildScriptFieldScript(s, sf))));
            }
        });
        reqBuilder.scriptFields(esFields);
    }

    private co.elastic.clients.elasticsearch._types.Script.Builder buildScriptFieldScript(
            co.elastic.clients.elasticsearch._types.Script.Builder s,
            TurDslQueryRequest.TurDslScriptField sf) {
        s.source(src -> src.scriptString(sf.script().source()));
        if (sf.script().lang() != null) s.lang(sf.script().lang());
        if (sf.script().params() != null) {
            Map<String, co.elastic.clients.json.JsonData> p = new LinkedHashMap<>();
            sf.script().params().forEach((k, v) ->
                    p.put(k, co.elastic.clients.json.JsonData.of(v)));
            s.params(p);
        }
        return s;
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
