/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.spring.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.turing.spring.security.abuse.TurAnonymousAbuseRateLimitFilter;
import com.viglet.turing.spring.security.auth.TurSNSiteApiKeyGateFilter;

/**
 * T966 / §LXXV — the anonymous LLM-cost surface is guarded by two filters that
 * each name their paths in a regex, and both lists had drifted behind the
 * endpoints. This is the gate that makes the next drift a failing build.
 *
 * <p>The rule has two halves, and the second is the one that matters. Asserting
 * only "these paths are covered" would pass forever while a new endpoint is
 * added beside them; so the test also asserts that the two classification lists
 * <b>partition</b> the public {@code /api/sn/} surface, which means a new public
 * path cannot be added without declaring what a call to it costs.
 *
 * <p>What went wrong without it: T889 made {@code /api/sn/*&#47;answer} anonymous on
 * the stated grounds that it was gated exactly like site chat, and it was in
 * neither filter — a claim in a comment, load-bearing and false.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurLlmCostSurfaceCoverageTest {

    /** Every LLM-cost path must be reached by the per-site API-key gate. */
    @Test
    void everyCostlyPublicPathIsApiKeyGated() {
        for (String matcher : TurSecurityConfigProduction.publicSnLlmCostMatchers()) {
            String uri = sampleUri(matcher);
            assertThat(TurSNSiteApiKeyGateFilter.gates(uri))
                    .withFailMessage("%s (%s) is declared LLM-cost but TurSNSiteApiKeyGateFilter "
                            + "does not gate it — an API_KEY-mode site would answer anyone.",
                            matcher, uri)
                    .isTrue();
        }
    }

    /** …and by the anonymous throttle, which is the app-layer spend backstop. */
    @Test
    void everyCostlyPublicPathIsRateLimited() {
        for (String matcher : TurSecurityConfigProduction.publicSnLlmCostMatchers()) {
            String uri = sampleUri(matcher);
            assertThat(TurAnonymousAbuseRateLimitFilter.limits(uri))
                    .withFailMessage("%s (%s) is declared LLM-cost but "
                            + "TurAnonymousAbuseRateLimitFilter does not throttle it — an "
                            + "anonymous loop spends without bound.", matcher, uri)
                    .isTrue();
        }
    }

    /**
     * The half that catches the endpoint nobody thought about: every anonymous
     * {@code /api/sn/} path has to be classified, so adding one to
     * {@code PUBLIC_MATCHERS} and nothing else fails here.
     */
    @Test
    void thePublicSnSurfaceIsFullyClassified() {
        List<String> declared = concat(TurSecurityConfigProduction.publicSnLlmCostMatchers(),
                TurSecurityConfigProduction.publicSnCheapMatchers());
        List<String> publicSn = TurSecurityConfigProduction.publicMatchers().stream()
                .filter(matcher -> matcher.startsWith("/api/sn/"))
                .toList();

        assertThat(publicSn)
                .withFailMessage("Every anonymous /api/sn path must be declared either LLM-cost "
                        + "or cheap. Unclassified: %s",
                        publicSn.stream().filter(p -> !declared.contains(p)).toList())
                .allMatch(declared::contains);
        assertThat(declared)
                .withFailMessage("These are classified but not actually public: %s",
                        declared.stream().filter(p -> !publicSn.contains(p)).toList())
                .allMatch(publicSn::contains);
    }

    /** A path is one or the other, never both — the classification has to decide. */
    @Test
    void noPathIsBothCostlyAndCheap() {
        assertThat(TurSecurityConfigProduction.publicSnLlmCostMatchers())
                .doesNotContainAnyElementsOf(TurSecurityConfigProduction.publicSnCheapMatchers());
    }

    /**
     * The regression itself, spelled out: the answer stream and the catalog
     * copilot reach both filters. Kept as its own case so a future edit that
     * empties the declared list cannot make the loops above vacuously green.
     */
    @Test
    void theAnswerStreamAndCopilotAreCovered() {
        for (String uri : List.of("/api/sn/mysite/answer", "/api/sn/mysite/copilot")) {
            assertThat(TurSNSiteApiKeyGateFilter.gates(uri)).as("api-key gate: %s", uri).isTrue();
            assertThat(TurAnonymousAbuseRateLimitFilter.limits(uri)).as("throttle: %s", uri).isTrue();
        }
    }

    /** Cheap reads stay unthrottled — per-keystroke autocomplete must not 429. */
    @Test
    void cheapReadsAreNotThrottled() {
        for (String matcher : TurSecurityConfigProduction.publicSnCheapMatchers()) {
            String uri = sampleUri(matcher);
            assertThat(TurAnonymousAbuseRateLimitFilter.limits(uri))
                    .withFailMessage("%s (%s) is declared cheap but is throttled — the abuse "
                            + "filter would break normal search UX.", matcher, uri)
                    .isFalse();
        }
    }

    /**
     * Turns a Spring path pattern into a concrete URI the filters' regexes can be
     * asked about: {@code *} becomes a segment, a trailing {@code /**} becomes one
     * more. Deliberately dumb — a pattern this cannot render is a pattern too
     * clever for a security allowlist.
     */
    private static String sampleUri(String matcher) {
        return matcher.replace("/**", "/x").replace("*", "mysite");
    }

    private static List<String> concat(List<String> first, List<String> second) {
        return java.util.stream.Stream.concat(first.stream(), second.stream()).toList();
    }
}
