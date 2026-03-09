/*
 * Copyright (C) 2016-2025 the original author or authors.
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

package com.viglet.turing.solr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.Map;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpJdkSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.SpellCheckResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.sn.TurSNConfig;
import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.ranking.TurSNRankingConditionRepository;
import com.viglet.turing.persistence.repository.sn.ranking.TurSNRankingExpressionRepository;
import com.viglet.turing.se.result.TurSEResult;
import com.viglet.turing.sn.TurSNFieldProcess;
import com.viglet.turing.sn.field.TurSNSiteFieldService;
import com.viglet.turing.sn.tr.TurSNTargetingRules;

/**
 * Unit tests for TurSolr.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.10
 */
@ExtendWith(MockitoExtension.class)
class TurSolrTest {

    @Mock
    private TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;

    @Mock
    private TurSNTargetingRules turSNTargetingRules;

    @Mock
    private TurSNSiteFieldService turSNSiteFieldService;

    @Mock
    private TurSNRankingExpressionRepository turSNRankingExpressionRepository;

    @Mock
    private TurSNRankingConditionRepository turSNRankingConditionRepository;

    @Mock
    private TurSNSiteRepository turSNSiteRepository;

    @Mock
    private TurSNFieldProcess turSNFieldProcess;

    @Mock
    private TurDecimalFieldNormalizer turDecimalFieldNormalizer;

    @Mock
    private HttpJdkSolrClient httpJdkSolrClient;

    @Mock
    private SolrClient solrClient;

    private TurSolrInstance turSolrInstance;

    @BeforeEach
    void setUp() throws MalformedURLException {
        turSolrInstance = new TurSolrInstance(httpJdkSolrClient, URI.create("http://localhost:8983/solr").toURL(),
                "core");
        turSolrInstance.setSolrClient(solrClient);
    }

    @Test
    void testAddAWildcardInQuery() {
        SolrQuery query = new SolrQuery().setQuery("test");

        TurSolr.addAWildcardInQuery(query);

        assertThat(query.getQuery()).isEqualTo("test*");
    }

    @Test
    void testEnabledWildcardNoResults() {
        TurSNSite turSNSite = mock(TurSNSite.class);
        when(turSNSite.getWildcardNoResults()).thenReturn(1);

        assertThat(TurSolr.enabledWildcardNoResults(turSNSite)).isTrue();

        when(turSNSite.getWildcardNoResults()).thenReturn(null);
        assertThat(TurSolr.enabledWildcardNoResults(turSNSite)).isFalse();
    }

    @Test
    void testIsNotQueryExpressionReturnsTrueForSimpleQuery() {
        SolrQuery query = new SolrQuery().setQuery("simple");

        assertThat(TurSolr.isNotQueryExpression(query)).isTrue();
    }

    @Test
    void testCreateTurSEResultFromDocument() {
        SolrDocument document = new SolrDocument();
        document.addField("id", "100");
        document.addField("title", "Sample");

        TurSEResult result = TurSolr.createTurSEResultFromDocument(document);

        assertThat(result.getFields())
                .containsEntry("id", "100")
                .containsEntry("title", "Sample");
    }

    @Test
    void testCommitSkippedWhenDisabled() throws Exception {
        TurSolr turSolr = buildTurSolr(false);

        turSolr.commit(turSolrInstance);

        verify(solrClient, never()).commit("core");
    }

    @Test
    void testCommitInvokedWhenEnabled() throws Exception {
        TurSolr turSolr = buildTurSolr(true);

        turSolr.commit(turSolrInstance);

        verify(solrClient, times(1)).commit("core");
    }

    @Test
    void testCommitReturnsTrueWhenCommitThrows() throws Exception {
        TurSolr turSolr = buildTurSolr(true);
        org.mockito.Mockito.doThrow(new SolrServerException("fail")).when(solrClient).commit("core");

        boolean result = turSolr.commit(turSolrInstance);

        assertThat(result).isTrue();
    }

    @Test
    void testExecuteSolrQueryReturnsEmptyOnException() throws Exception {
        when(solrClient.query(eq("core"), any(SolrQuery.class))).thenThrow(new SolrServerException("error"));

        var result = TurSolr.executeSolrQuery(turSolrInstance, new SolrQuery().setQuery("test"));

        assertThat(result).isEmpty();
    }

    @Test
    void testGetDocumentTotalReturnsNumFound() throws Exception {
        QueryResponse response = mock(QueryResponse.class);
        SolrDocumentList docs = new SolrDocumentList();
        docs.setNumFound(42);
        when(response.getResults()).thenReturn(docs);
        when(solrClient.query(eq("core"), any(SolrQuery.class))).thenReturn(response);

        TurSolr turSolr = buildTurSolr(false);

        assertThat(turSolr.getDocumentTotal(turSolrInstance)).isEqualTo(42L);
    }

    @Test
    void testSolrResultAndReturnsResults() throws Exception {
        QueryResponse response = mock(QueryResponse.class);
        SolrDocumentList docs = new SolrDocumentList();
        SolrDocument document = new SolrDocument();
        document.addField("id", "1");
        docs.add(document);
        when(response.getResults()).thenReturn(docs);
        when(solrClient.query(eq("core"), any(SolrQuery.class))).thenReturn(response);

        TurSolr turSolr = buildTurSolr(false);
        SolrDocumentList result = turSolr.solrResultAnd(turSolrInstance, Map.of("id", "1"));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getFieldValue("id")).isEqualTo("1");
    }

    @Test
    void testSpellCheckTermCorrectedAndDefault() throws Exception {
        TurSolr turSolr = buildTurSolr(false);

        QueryResponse correctedResponse = mock(QueryResponse.class);
        SpellCheckResponse correctedSpell = mock(SpellCheckResponse.class);
        when(correctedSpell.getCollatedResult()).thenReturn("hello");
        when(correctedResponse.getSpellCheckResponse()).thenReturn(correctedSpell);
        when(solrClient.query(eq("core"), any(SolrQuery.class))).thenReturn(correctedResponse);

        var corrected = turSolr.spellCheckTerm(turSolrInstance, "helo");
        assertThat(corrected.isCorrected()).isTrue();
        assertThat(corrected.getCorrectedText()).isEqualTo("hello");

        QueryResponse defaultResponse = mock(QueryResponse.class);
        SpellCheckResponse defaultSpell = mock(SpellCheckResponse.class);
        when(defaultSpell.getCollatedResult()).thenReturn("");
        when(defaultResponse.getSpellCheckResponse()).thenReturn(defaultSpell);
        when(solrClient.query(eq("core"), any(SolrQuery.class))).thenReturn(defaultResponse);

        var notCorrected = turSolr.spellCheckTerm(turSolrInstance, "helo");
        assertThat(notCorrected.isCorrected()).isFalse();
    }

    @Test
    void testFirstRowPositionFromCurrentPage() {
        TurSNSearchParams searchParams = new TurSNSearchParams();
        searchParams.setP(3);
        searchParams.setRows(10);
        TurSEParameters parameters = new TurSEParameters(searchParams);

        assertThat(TurSolr.firstRowPositionFromCurrentPage(parameters)).isEqualTo(20);
    }

    @Test
    void testRetrieveSolrAppliesSortAndFilterQueries() throws Exception {
        TurSolr turSolr = buildTurSolr(false);

        TurSNSearchParams searchParams = new TurSNSearchParams();
        searchParams.setQ("java");
        searchParams.setRows(10);
        searchParams.setP(1);
        searchParams.setSort("newest");
        searchParams.setFq(java.util.List.of("type:article"));
        TurSEParameters parameters = new TurSEParameters(searchParams);

        QueryResponse response = mock(QueryResponse.class);
        SolrDocumentList docs = new SolrDocumentList();
        docs.setNumFound(1);
        SolrDocument doc = new SolrDocument();
        doc.addField("id", "10");
        docs.add(doc);
        when(response.getResults()).thenReturn(docs);
        when(response.getElapsedTime()).thenReturn(11L);
        when(response.getQTime()).thenReturn(7);
        when(solrClient.query(eq("core"), any(SolrQuery.class))).thenReturn(response);

        var results = turSolr.retrieveSolr(turSolrInstance, parameters, "publishedDate");

        assertThat(results.getResults()).hasSize(1);
        assertThat(results.getResults().getFirst().getFields()).containsEntry("id", "10");

        ArgumentCaptor<SolrQuery> queryCaptor = ArgumentCaptor.forClass(SolrQuery.class);
        verify(solrClient, times(2)).query(eq("core"), queryCaptor.capture());
        SolrQuery executed = queryCaptor.getAllValues().stream()
                .filter(q -> q.get("qt") == null)
                .findFirst()
                .orElse(queryCaptor.getAllValues().getFirst());
        assertThat(executed.getSortField()).contains("publishedDate desc");
        assertThat(executed.getFilterQueries()).containsExactly("type:article");
    }

    @Test
    void testFindByIdBuildsResultFromDocument() throws Exception {
        TurSolr turSolr = buildTurSolr(false);
        TurSNSite site = new TurSNSite();
        site.setHl(1);
        site.setHlPre("<em>");
        site.setHlPost("</em>");

        TurSNSiteFieldExt hlField = TurSNSiteFieldExt.builder().name("title")
                .type(com.viglet.turing.commons.se.field.TurSEFieldType.TEXT).build();
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndHlAndEnabled(site, 1, 1))
                .thenReturn(java.util.List.of(hlField));
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(java.util.List.of(hlField));
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndRequiredAndEnabled(site, 1, 1))
                .thenReturn(java.util.List.of());

        QueryResponse response = mock(QueryResponse.class);
        SolrDocumentList docs = new SolrDocumentList();
        SolrDocument doc = new SolrDocument();
        doc.addField("id", "abc");
        doc.addField("title", "plain");
        docs.add(doc);
        when(response.getResults()).thenReturn(docs);
        when(response.getHighlighting()).thenReturn(java.util.Map.of("abc", java.util.Map.of("title",
                java.util.List.of("<em>highlighted</em>"))));
        when(solrClient.query(eq("core"), any(SolrQuery.class))).thenReturn(response);

        TurSNSearchParams searchParams = new TurSNSearchParams();
        searchParams.setQ("abc");
        TurSEParameters parameters = new TurSEParameters(searchParams);
        TurSNSiteSearchContext context = new TurSNSiteSearchContext("site",
                new TurSNConfig(), parameters, java.util.Locale.US, URI.create("http://localhost/search"));

        TurSEResult result = turSolr.findById(turSolrInstance, site, "abc", context);

        assertThat(result.getFields()).containsEntry("id", "abc");
        assertThat(result.getFields()).containsEntry("title", "<em>highlighted</em>");
    }

    @Test
    void testAutoCompleteReturnsNullWhenQueryFails() throws Exception {
        TurSolr turSolr = buildTurSolr(false);
        when(solrClient.query(eq("core"), any(SolrQuery.class))).thenThrow(new SolrServerException("error"));

        assertThat(turSolr.autoComplete(turSolrInstance, "te")).isNull();
    }

    private TurSolr buildTurSolr(boolean commitEnabled) {
        return new TurSolr(commitEnabled, 500,
                turSNSiteFieldExtRepository,
                turSNTargetingRules,
                turSNSiteFieldService,
                turSNRankingExpressionRepository,
                turSNRankingConditionRepository,
                turSNSiteRepository,
                turSNFieldProcess,
                turDecimalFieldNormalizer);
    }
}
