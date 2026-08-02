/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.viglet.turing.api.sn.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.commons.sn.bean.TurSNSearchRequest;
import com.viglet.turing.commons.sn.bean.TurSNSitePostParamsBean;
import com.viglet.turing.commons.sn.search.TurSNFilterQueryOperator;

/**
 * T707 / §XLV — tests for the public POST search request adapter.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurSNSearchRequestAdapterTest {

    private static final URI POST_URI = URI.create("/api/sn/wknd/search/query");

    @Test
    void testEmptyRequestKeepsGetDefaults() {
        TurSNSearchParams params = TurSNSearchRequestAdapter
                .toSearchParams(new TurSNSearchRequest(), Locale.US);
        TurSNSearchParams getDefaults = new TurSNSearchParams();

        assertThat(params.getQ()).isEqualTo(getDefaults.getQ());
        assertThat(params.getP()).isEqualTo(getDefaults.getP());
        assertThat(params.getSort()).isEqualTo(getDefaults.getSort());
        assertThat(params.getRows()).isEqualTo(getDefaults.getRows());
        assertThat(params.getNfpr()).isEqualTo(getDefaults.getNfpr());
        assertThat(params.getFqOp()).isEqualTo(TurSNFilterQueryOperator.NONE);
        assertThat(params.getFqiOp()).isEqualTo(TurSNFilterQueryOperator.NONE);
        assertThat(params.getFq()).isNull();
        assertThat(params.getFqAnd()).isNull();
        assertThat(params.getFqOr()).isNull();
        assertThat(params.getFl()).isNull();
        assertThat(params.getLocale()).isEqualTo(Locale.US);
    }

    @Test
    void testRequestFieldsMapOntoSearchParams() {
        TurSNSearchRequest request = new TurSNSearchRequest();
        request.setQ("solr");
        request.setFq(List.of("section:RAG & Chat"));
        request.setFqAnd(List.of("type:page"));
        request.setFqOr(List.of("tag:a", "tag:b"));
        request.setFqOperator(TurSNFilterQueryOperator.AND);
        request.setFqItemOperator(TurSNFilterQueryOperator.OR);
        request.setPage(3);
        request.setRows(20);
        request.setSort("newest");
        request.setGroup("type");
        request.setFieldList(List.of("id", "title"));
        request.setAutoCorrectionDisabled(false);

        TurSNSearchParams params = TurSNSearchRequestAdapter.toSearchParams(request, Locale.US);

        assertThat(params.getQ()).isEqualTo("solr");
        assertThat(params.getFq()).containsExactly("section:RAG & Chat");
        assertThat(params.getFqAnd()).containsExactly("type:page");
        assertThat(params.getFqOr()).containsExactly("tag:a", "tag:b");
        assertThat(params.getFqOp()).isEqualTo(TurSNFilterQueryOperator.AND);
        assertThat(params.getFqiOp()).isEqualTo(TurSNFilterQueryOperator.OR);
        assertThat(params.getP()).isEqualTo(3);
        assertThat(params.getRows()).isEqualTo(20);
        assertThat(params.getSort()).isEqualTo("newest");
        assertThat(params.getGroup()).isEqualTo("type");
        assertThat(params.getFl()).containsExactly("id", "title");
        assertThat(params.getNfpr()).isZero();
    }

    /**
     * The whole point of the block: a facet value carrying reserved characters
     * arrives verbatim because the JSON body has no query-string grammar.
     */
    @Test
    void testReservedCharactersInFilterValueSurviveVerbatim() {
        TurSNSearchRequest request = new TurSNSearchRequest();
        request.setFq(List.of("section:RAG & Chat", "path:a=b+c#d%e", "title:Deploy & Operate"));

        TurSNSearchParams params = TurSNSearchRequestAdapter.toSearchParams(request, Locale.US);

        assertThat(params.getFq()).containsExactly(
                "section:RAG & Chat", "path:a=b+c#d%e", "title:Deploy & Operate");
    }

    @Test
    void testCanonicalUriDropsTheQuerySegmentAndEncodesValues() {
        TurSNSearchRequest request = new TurSNSearchRequest();
        request.setQ("chat");
        request.setFq(List.of("section:RAG & Chat"));

        URI canonical = TurSNSearchRequestAdapter.canonicalUri(POST_URI, request, Locale.US);

        assertThat(canonical.getRawPath()).isEqualTo("/api/sn/wknd/search");
        assertThat(canonical.getRawQuery())
                .contains("q=chat")
                .contains("fq[]=section:RAG%20%26%20Chat")
                .contains("_setlocale=en_US");
    }

    /**
     * Re-parsing the canonical URI must give back the original value — that is the
     * contract the legacy link-bearing response still depends on.
     */
    @Test
    void testCanonicalUriRoundTripsTheReservedValue() {
        TurSNSearchRequest request = new TurSNSearchRequest();
        request.setFq(List.of("section:RAG & Chat"));

        URI canonical = TurSNSearchRequestAdapter.canonicalUri(POST_URI, request, null);
        String fqValue = java.net.URLDecoder.decode(
                canonical.getRawQuery().split("fq\\[\\]=")[1].split("&")[0],
                java.nio.charset.StandardCharsets.UTF_8);

        assertThat(fqValue).isEqualTo("section:RAG & Chat");
    }

    /**
     * T712 / §XLV — the other POST variants ({@code /ac/query} and
     * {@code /search/list/query}) must also canonicalise onto their GET
     * counterpart, so a link rendered from one of them stays deep-linkable.
     */
    @Test
    void testCanonicalUriStripsTheQuerySegmentOfEveryPostVariant() {
        TurSNSearchRequest request = new TurSNSearchRequest();

        assertThat(TurSNSearchRequestAdapter
                .canonicalUri(URI.create("/api/sn/wknd/ac/query"), request, null).getRawPath())
                        .isEqualTo("/api/sn/wknd/ac");
        assertThat(TurSNSearchRequestAdapter
                .canonicalUri(URI.create("/api/sn/wknd/search/list/query"), request, null)
                .getRawPath()).isEqualTo("/api/sn/wknd/search/list");
    }

    @Test
    void testCanonicalUriOmitsAbsentOptionalParameters() {
        URI canonical = TurSNSearchRequestAdapter.canonicalUri(POST_URI,
                new TurSNSearchRequest(), null);

        assertThat(canonical.getRawQuery())
                .isEqualTo("q=*&p=1")
                .doesNotContain("rows=")
                .doesNotContain("sort=")
                .doesNotContain("_setlocale=");
    }

    @Test
    void testNoneOperatorsAreNotEmittedInTheCanonicalUri() {
        TurSNSearchRequest request = new TurSNSearchRequest();
        request.setFqOperator(TurSNFilterQueryOperator.NONE);
        request.setFqItemOperator(TurSNFilterQueryOperator.NONE);

        URI canonical = TurSNSearchRequestAdapter.canonicalUri(POST_URI, request, null);

        assertThat(canonical.getRawQuery()).doesNotContain("fq.op").doesNotContain("fqi.op");
    }

    /**
     * The anonymous path must not be able to reach the impersonation surface.
     */
    @Test
    void testPostParamsNeverCarryImpersonationFields() {
        TurSNSearchRequest request = new TurSNSearchRequest();
        request.setTargetingRules(List.of("rule-a"));

        TurSNSitePostParamsBean postParams = TurSNSearchRequestAdapter.toPostParams(request);

        assertThat(postParams.getTargetingRules()).containsExactly("rule-a");
        assertThat(postParams.getUserId()).isNull();
        assertThat(postParams.getTargetingRulesWithCondition()).isEmpty();
        assertThat(postParams.getTargetingRulesWithConditionAND()).isEmpty();
        assertThat(postParams.getTargetingRulesWithConditionOR()).isEmpty();
    }

    @Test
    void testLocaleResolution() {
        TurSNSearchRequest request = new TurSNSearchRequest();
        assertThat(TurSNSearchRequestAdapter.resolveLocale(request)).isNull();

        request.setLocale("pt_BR");
        assertThat(TurSNSearchRequestAdapter.resolveLocale(request))
                .isEqualTo(Locale.of("pt", "BR"));
    }
}
