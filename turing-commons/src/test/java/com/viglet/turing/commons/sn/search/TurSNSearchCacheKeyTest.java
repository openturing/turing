/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.commons.sn.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.sn.TurSNConfig;
import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.commons.sn.bean.TurSNSitePostParamsBean;

/**
 * T858 / §LXV.3 — the cache key must be a function of what was asked, never of
 * how it arrived.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurSNSearchCacheKeyTest {

    private static TurSNSiteSearchContext context(TurSNSearchParams params,
            TurSNSitePostParamsBean postParams, URI uri) {
        return new TurSNSiteSearchContext("site", new TurSNConfig(),
                new TurSEParameters(params, postParams), params.getLocale(), uri, postParams);
    }

    private static TurSNSearchParams params(String query) {
        TurSNSearchParams params = new TurSNSearchParams();
        params.setQ(query);
        params.setLocale(Locale.US);
        return params;
    }

    @Test
    void testTheSameRequestAlwaysProducesTheSameKey() {
        assertThat(TurSNSearchCacheKey.of(context(params("chat"), null, null)))
                .isEqualTo(TurSNSearchCacheKey.of(context(params("chat"), null, null)));
    }

    /**
     * The bug the primitive invited: a POST has no query string, so the old key
     * collapsed every distinct body onto {@code <site>_null}.
     */
    @Test
    void testTwoDistinctBodiesWithNoQueryStringGetDistinctKeys() {
        String first = TurSNSearchCacheKey.of(context(params("chat"), null, null));
        String second = TurSNSearchCacheKey.of(context(params("search"), null, null));

        assertThat(first).isNotEqualTo(second);
    }

    /** The transport is not part of the key: the URI it arrived on is ignored. */
    @Test
    void testTheRequestUriDoesNotAffectTheKey() {
        TurSNSearchParams params = params("chat");
        String get = TurSNSearchCacheKey
                .of(context(params, null, URI.create("/api/sn/site/search?q=chat")));
        String post = TurSNSearchCacheKey
                .of(context(params, null, URI.create("/api/sn/site/search/query")));

        assertThat(get).isEqualTo(post);
    }

    @Test
    void testEveryFilterQueryGroupChangesTheKey() {
        TurSNSearchParams base = params("chat");
        TurSNSearchParams withFq = params("chat");
        withFq.setFq(List.of("section:RAG"));
        TurSNSearchParams withFqAnd = params("chat");
        withFqAnd.setFqAnd(List.of("section:RAG"));
        TurSNSearchParams withFqOr = params("chat");
        withFqOr.setFqOr(List.of("section:RAG"));

        assertThat(List.of(TurSNSearchCacheKey.of(context(base, null, null)),
                TurSNSearchCacheKey.of(context(withFq, null, null)),
                TurSNSearchCacheKey.of(context(withFqAnd, null, null)),
                TurSNSearchCacheKey.of(context(withFqOr, null, null))))
                        .doesNotHaveDuplicates();
    }

    /**
     * The separators are escaped, so no combination of values can spell out
     * another combination — the property the query-string key lacked.
     */
    @Test
    void testValuesContainingSeparatorsCannotForgeAnotherKey() {
        TurSNSearchParams twoValues = params("chat");
        twoValues.setFq(List.of("a", "b"));
        TurSNSearchParams oneValueWithComma = params("chat");
        oneValueWithComma.setFq(List.of("a,b"));
        TurSNSearchParams oneValueWithPipe = params("chat");
        oneValueWithPipe.setFq(List.of("a|fq=b"));

        assertThat(List.of(TurSNSearchCacheKey.of(context(twoValues, null, null)),
                TurSNSearchCacheKey.of(context(oneValueWithComma, null, null)),
                TurSNSearchCacheKey.of(context(oneValueWithPipe, null, null))))
                        .doesNotHaveDuplicates();
    }

    /** Targeting rules change which documents match, so they are part of the key. */
    @Test
    void testTargetingRulesChangeTheKey() {
        TurSNSitePostParamsBean admin = new TurSNSitePostParamsBean();
        admin.setTargetingRules(List.of("role:admin"));
        TurSNSitePostParamsBean guest = new TurSNSitePostParamsBean();
        guest.setTargetingRules(List.of("role:guest"));

        assertThat(TurSNSearchCacheKey.of(context(params("chat"), admin, null)))
                .isNotEqualTo(TurSNSearchCacheKey.of(context(params("chat"), guest, null)));
    }

    /**
     * {@code userId} is a metrics/impersonation concern, not a matching one —
     * keying on it would fragment the cache per user for no gain.
     */
    @Test
    void testUserIdDoesNotFragmentTheCache() {
        TurSNSitePostParamsBean first = new TurSNSitePostParamsBean();
        first.setUserId("alice");
        TurSNSitePostParamsBean second = new TurSNSitePostParamsBean();
        second.setUserId("bob");

        assertThat(TurSNSearchCacheKey.of(context(params("chat"), first, null)))
                .isEqualTo(TurSNSearchCacheKey.of(context(params("chat"), second, null)));
    }

    /** Highlighting changes the response, so it is part of the key. */
    @Test
    void testHighlightingChangesTheKey() {
        TurSNSearchParams params = params("chat");
        TurSNConfig highlighted = new TurSNConfig();
        highlighted.setHlEnabled(true);
        TurSNSiteSearchContext withHl = new TurSNSiteSearchContext("site", highlighted,
                new TurSEParameters(params), params.getLocale(), null);

        assertThat(TurSNSearchCacheKey.of(withHl))
                .isNotEqualTo(TurSNSearchCacheKey.of(context(params, null, null)));
    }

    /** Facet sets have no iteration order, so they are sorted before encoding. */
    @Test
    void testFacetSetsAreOrderIndependent() {
        TurSNSiteSearchContext first = context(params("chat"), null, null);
        first.getTurSEParameters().setAddedFacets(new java.util.LinkedHashSet<>(List.of("a", "b")));
        TurSNSiteSearchContext second = context(params("chat"), null, null);
        second.getTurSEParameters().setAddedFacets(new java.util.LinkedHashSet<>(List.of("b", "a")));

        assertThat(TurSNSearchCacheKey.of(first)).isEqualTo(TurSNSearchCacheKey.of(second));
    }

    @Test
    void testPaginationAndSortAreInTheKey() {
        TurSNSearchParams page2 = params("chat");
        page2.setP(2);
        TurSNSearchParams sorted = params("chat");
        sorted.setSort("newest");
        TurSNSearchParams narrowed = params("chat");
        narrowed.setRows(5);

        assertThat(List.of(TurSNSearchCacheKey.of(context(params("chat"), null, null)),
                TurSNSearchCacheKey.of(context(page2, null, null)),
                TurSNSearchCacheKey.of(context(sorted, null, null)),
                TurSNSearchCacheKey.of(context(narrowed, null, null))))
                        .doesNotHaveDuplicates();
    }

    @Test
    void testAllowedFacetsAreInTheKey() {
        TurSNSiteSearchContext restricted = context(params("chat"), null, null);
        restricted.getTurSEParameters().setAllowedFacets(Set.of("section"));

        assertThat(TurSNSearchCacheKey.of(restricted))
                .isNotEqualTo(TurSNSearchCacheKey.of(context(params("chat"), null, null)));
    }

    @Test
    void testTheLocaleIsInTheKey() {
        TurSNSearchParams english = params("chat");
        TurSNSearchParams portuguese = params("chat");
        portuguese.setLocale(Locale.of("pt", "BR"));

        assertThat(TurSNSearchCacheKey.of(context(english, null, null)))
                .isNotEqualTo(TurSNSearchCacheKey.of(context(portuguese, null, null)));
    }
}
