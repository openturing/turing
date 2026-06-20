package com.viglet.turing.sn.dsl;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.provider.Arguments;

import com.viglet.turing.sn.dsl.TurDslQuery.GeoPoint;
import com.viglet.turing.sn.dsl.TurDslQuery.IntervalsRule;
import com.viglet.turing.sn.dsl.TurDslQuery.MatchAll;
import com.viglet.turing.sn.dsl.TurDslQuery.ScoreFunction;
import com.viglet.turing.sn.dsl.TurDslQuery.Script;
import com.viglet.turing.sn.dsl.TurDslQuery.SpanTerm;
import com.viglet.turing.sn.dsl.TurDslQuery.Term;

/**
 * Shared one-of-each-type {@link TurDslQuery} fixtures for the per-engine
 * translation tests (Lucene / Solr / Elasticsearch) so the full query-family
 * matrix is defined once.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
final class TurDslQueryFixtures {

    private TurDslQueryFixtures() {
    }

    /** One representative instance of every supported {@link TurDslQuery} family. */
    static Stream<Arguments> named() {
        return Stream.of(
                arg("match_all", new MatchAll()),
                arg("match", new TurDslQuery.Match("title", "hello", null)),
                arg("multi_match", new TurDslQuery.MultiMatch("hello", List.of("title", "body"), null)),
                arg("term", new Term("status", "active")),
                arg("terms", new TurDslQuery.Terms("tag", List.of("a", "b"))),
                arg("bool", new TurDslQuery.Bool(List.of(new MatchAll()), List.of(new Term("a", "b")),
                        List.of(new Term("c", "d")), List.of(new TurDslQuery.Exists("e")), 1)),
                arg("range", new TurDslQuery.Range("age", 18, null, 65, null)),
                arg("wildcard", new TurDslQuery.Wildcard("name", "jo*")),
                arg("prefix", new TurDslQuery.Prefix("name", "jo")),
                arg("exists", new TurDslQuery.Exists("email")),
                arg("query_string", new TurDslQuery.QueryString("hello world", "title", null)),
                arg("fuzzy", new TurDslQuery.Fuzzy("name", "jon", 2)),
                arg("ids", new TurDslQuery.Ids(List.of("1", "2"))),
                arg("match_phrase", new TurDslQuery.MatchPhrase("body", "quick brown", 1)),
                arg("match_phrase_prefix", new TurDslQuery.MatchPhrasePrefix("body", "quick bro", 10)),
                arg("simple_query_string", new TurDslQuery.SimpleQueryString("hello", List.of("title"), null)),
                arg("regexp", new TurDslQuery.Regexp("name", "jo.*", null)),
                arg("constant_score", new TurDslQuery.ConstantScore(new MatchAll(), 2.0)),
                arg("dis_max", new TurDslQuery.DisMax(List.of(new MatchAll(), new Term("a", "b")), 0.3)),
                arg("boosting", new TurDslQuery.Boosting(new MatchAll(), new Term("a", "b"), 0.5)),
                arg("nested", new TurDslQuery.Nested("path", new MatchAll(), "avg")),
                arg("function_score", new TurDslQuery.FunctionScore(new MatchAll(),
                        List.of(new ScoreFunction(null, 1.0, null, null, null, null, null)), null, null, null, null)),
                arg("script_score", new TurDslQuery.ScriptScore(new MatchAll(), new Script("_score", null, null), null)),
                arg("knn", new TurDslQuery.Knn("vec", List.of(0.1, 0.2), 5, 50, null, null)),
                arg("more_like_this", new TurDslQuery.MoreLikeThis(List.of("body"), "like text", List.of(), 1, 1, 10)),
                arg("combined_fields", new TurDslQuery.CombinedFields("hello", List.of("title", "body"), null)),
                arg("match_bool_prefix", new TurDslQuery.MatchBoolPrefix("body", "quick bro")),
                arg("pinned", new TurDslQuery.Pinned(List.of("1"), new MatchAll())),
                arg("geo_distance", new TurDslQuery.GeoDistance("loc", "10km", new GeoPoint(1.0, 2.0))),
                arg("geo_bounding_box", new TurDslQuery.GeoBoundingBox("loc", new GeoPoint(2.0, 1.0), new GeoPoint(1.0, 2.0))),
                arg("geo_shape", new TurDslQuery.GeoShape("loc", Map.of("type", "envelope"), "within")),
                arg("has_child", new TurDslQuery.HasChild("ct", new MatchAll(), "max", null, null)),
                arg("has_parent", new TurDslQuery.HasParent("pt", new MatchAll(), true)),
                arg("intervals", new TurDslQuery.Intervals("body", new IntervalsRule("match", "quick", null, null, null, null))),
                arg("span_term", new SpanTerm("body", "quick")),
                arg("span_near", new TurDslQuery.SpanNear(List.of(new SpanTerm("body", "quick"), new SpanTerm("body", "brown")), 1, true)),
                arg("span_or", new TurDslQuery.SpanOr(List.of(new SpanTerm("body", "quick")))),
                arg("span_not", new TurDslQuery.SpanNot(new SpanTerm("body", "quick"), new SpanTerm("body", "slow"))),
                arg("span_first", new TurDslQuery.SpanFirst(new SpanTerm("body", "quick"), 3)),
                arg("rank_feature", new TurDslQuery.RankFeature("pagerank", 2.0, null, null, null)),
                arg("distance_feature", new TurDslQuery.DistanceFeature("date", "now", "7d")),
                arg("wrapper", new TurDslQuery.Wrapper("eyJ9")),
                arg("percolate", new TurDslQuery.Percolate("q", Map.of("title", "x"))),
                arg("terms_set", new TurDslQuery.TermsSet("tag", List.of("a", "b"), "msm", null)));
    }

    private static Arguments arg(String name, TurDslQuery query) {
        return Arguments.of(Named.of(name, query));
    }
}
