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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.solr.client.solrj.request.SolrQuery;
import org.apache.solr.common.params.GroupParams;
import org.apache.solr.common.params.HighlightParams;
import org.apache.solr.common.params.MoreLikeThisParams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.se.result.spellcheck.TurSESpellCheckResult;
import com.viglet.turing.commons.sn.TurSNConfig;
import com.viglet.turing.commons.sn.bean.TurSNFilterParams;
import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.commons.sn.bean.TurSNSitePostParamsBean;
import com.viglet.turing.commons.sn.search.TurSNFilterQueryOperator;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.TurSNSiteFacetRangeEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacet;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacetItem;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldSortEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.ranking.TurSNRankingCondition;
import com.viglet.turing.persistence.model.sn.ranking.TurSNRankingExpression;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.ranking.TurSNRankingConditionRepository;
import com.viglet.turing.persistence.repository.sn.ranking.TurSNRankingExpressionRepository;
import com.viglet.turing.sn.TurSNFieldType;
import com.viglet.turing.sn.facet.TurSNFacetTypeContext;
import com.viglet.turing.persistence.repository.sn.sort.TurSNSiteCustomSortRepository;
import com.viglet.turing.sn.tr.TurSNTargetingRules;

/**
 * Tests for TurSolrQueryBuilder.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@ExtendWith(MockitoExtension.class)
class TurSolrQueryBuilderTest {

        @Mock
        private TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;

        @Mock
        private TurSNRankingExpressionRepository turSNRankingExpressionRepository;

        @Mock
        private TurSNRankingConditionRepository turSNRankingConditionRepository;

        @Mock
        private TurSNTargetingRules turSNTargetingRules;

        @Mock
        private com.viglet.turing.persistence.repository.sn.field.TurSNSiteCustomFacetRepository turSNSiteCustomFacetRepository;

        @Mock
        private TurSNSiteCustomSortRepository turSNSiteCustomSortRepository;

        @Mock
        private com.viglet.turing.sn.searchrule.TurSNSearchRuleEvaluator turSNSearchRuleEvaluator;

        @Mock
        private com.viglet.turing.sn.snapshot.TurSNSiteSearchSnapshotService snapshotService;

        private TurSolrQueryBuilder builder() {
                return new TurSolrQueryBuilder(turSNSiteFieldExtRepository,
                                turSNSiteCustomFacetRepository,
                                turSNSiteCustomSortRepository,
                                turSNRankingExpressionRepository, turSNRankingConditionRepository,
                                turSNTargetingRules, turSNSearchRuleEvaluator, snapshotService);
        }

        private TurSNSiteSearchContext contextFrom(TurSEParameters parameters,
                        TurSNSitePostParamsBean post) {
                TurSNConfig config = new TurSNConfig();
                config.setHlEnabled(true);
                return new TurSNSiteSearchContext("site", config, parameters, Locale.US,
                                URI.create("http://localhost/search"), post);
        }

        @Test
        void testHasGroupWhenGroupIsPresent() {
                TurSolrQueryBuilder builder = new TurSolrQueryBuilder(turSNSiteFieldExtRepository, turSNSiteCustomFacetRepository,
                                turSNSiteCustomSortRepository, turSNRankingExpressionRepository, turSNRankingConditionRepository, turSNTargetingRules, turSNSearchRuleEvaluator, snapshotService);
                TurSNSearchParams searchParams = new TurSNSearchParams();
                searchParams.setGroup("group");
                TurSEParameters parameters = new TurSEParameters(
                                searchParams);

                assertThat(builder.hasGroup(parameters)).isTrue();
        }

        @Test
        void testHasGroupWhenGroupIsMissing() {
                TurSolrQueryBuilder builder = new TurSolrQueryBuilder(turSNSiteFieldExtRepository, turSNSiteCustomFacetRepository,
                                turSNSiteCustomSortRepository, turSNRankingExpressionRepository, turSNRankingConditionRepository, turSNTargetingRules, turSNSearchRuleEvaluator, snapshotService);
                TurSNSearchParams searchParams = new TurSNSearchParams();
                TurSEParameters parameters = new TurSEParameters(
                                searchParams);

                assertThat(builder.hasGroup(parameters)).isFalse();
        }

        @Test
        void testGetFqFieldsExtractsKeys() {
                TurSolrQueryBuilder builder = new TurSolrQueryBuilder(turSNSiteFieldExtRepository, turSNSiteCustomFacetRepository,
                                turSNSiteCustomSortRepository, turSNRankingExpressionRepository, turSNRankingConditionRepository, turSNTargetingRules, turSNSearchRuleEvaluator, snapshotService);
                TurSNFilterParams params = TurSNFilterParams.builder()
                                .defaultValues(List.of("category:books", "type:article"))
                                .and(List.of("author:john"))
                                .or(List.of("format:pdf"))
                                .build();

                List<String> keys = builder.getFqFields(params);

                assertThat(keys).containsExactly("category", "type", "author", "format");
        }

        @Test
        void testGetFacetTypeAndFacetItemTypeValuesUsesSiteDefaults() {
                TurSNSite site = new TurSNSite();
                site.setFacetType(TurSNSiteFacetFieldEnum.AND);
                site.setFacetItemType(TurSNSiteFacetFieldEnum.OR);
                TurSNFilterParams params = TurSNFilterParams.builder()
                                .operator(TurSNFilterQueryOperator.NONE)
                                .itemOperator(TurSNFilterQueryOperator.NONE)
                                .build();

                TurSNFacetTypeContext context = new TurSNFacetTypeContext(site, params);

                assertThat(TurSolrQueryBuilder.getFacetTypeAndFacetItemTypeValues(context))
                                .isEqualTo("AND-OR");
        }

        @Test
        void testGetFacetFieldsInFilterQueryFiltersEnabledFacets() {
                TurSNSite site = new TurSNSite();
                TurSNFilterParams params = TurSNFilterParams.builder()
                                .defaultValues(List.of("category:books", "other:1"))
                                .build();
                TurSNSiteFieldExt categoryFacet = TurSNSiteFieldExt.builder().name("category").facet(1).enabled(1)
                                .build();
                TurSNSiteFieldExt typeFacet = TurSNSiteFieldExt.builder().name("type").facet(1).enabled(1)
                                .build();
                when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                                .thenReturn(List.of(categoryFacet, typeFacet));

                TurSolrQueryBuilder builder = new TurSolrQueryBuilder(turSNSiteFieldExtRepository, turSNSiteCustomFacetRepository,
                                turSNSiteCustomSortRepository, turSNRankingExpressionRepository, turSNRankingConditionRepository, turSNTargetingRules, turSNSearchRuleEvaluator, snapshotService);
                TurSNFacetTypeContext context = new TurSNFacetTypeContext(site, params);

                List<String> fields = builder.getFacetFieldsInFilterQuery(context);

                assertThat(fields).containsExactly("category");
        }

        @Test
        void testGetFacetFieldsInFilterQueryIncludesCustomFacetNames() {
                TurSNSite site = new TurSNSite();
                TurSNFilterParams params = TurSNFilterParams.builder()
                                .defaultValues(List.of("price_range:101 - 500", "other:1"))
                                .build();

                TurSNSiteFieldExt idFacet = TurSNSiteFieldExt.builder()
                                .id("field-1")
                                .name("id")
                                .build();
                TurSNSiteCustomFacet customFacet = TurSNSiteCustomFacet.builder()
                                .name("price_range").turSNSiteFieldExt(idFacet).build();

                when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                                .thenReturn(List.of(idFacet));
                when(turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(any()))
                                .thenReturn(List.of(customFacet));

                List<String> fields = builder().getFacetFieldsInFilterQuery(new TurSNFacetTypeContext(site, params));

                assertThat(fields).containsExactly("price_range");
        }

        @Test
        void testGetFacetFieldsInFilterQueryIncludesCustomFacetWhenBaseFieldIsNotFacet() {
                TurSNSite site = new TurSNSite();
                TurSNFilterParams params = TurSNFilterParams.builder()
                                .defaultValues(List.of("price_range:101 - 500"))
                                .build();

                TurSNSiteFieldExt nonFacetField = TurSNSiteFieldExt.builder()
                                .id("field-1")
                                .name("id")
                                .facet(0)
                                .enabled(1)
                                .build();
                TurSNSiteCustomFacet customFacet = TurSNSiteCustomFacet.builder()
                                .name("price_range").turSNSiteFieldExt(nonFacetField).build();

                when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                                .thenReturn(List.of(nonFacetField));
                when(turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(any()))
                                .thenReturn(List.of(customFacet));

                List<String> fields = builder().getFacetFieldsInFilterQuery(new TurSNFacetTypeContext(site, params));

                assertThat(fields).containsExactly("price_range");
        }

        @Test
        void testPrepareQueryMLTConfiguresQueryWhenEnabled() {
                TurSNSite site = new TurSNSite();
                site.setMlt(1);
                TurSNSiteFieldExt mltField = TurSNSiteFieldExt.builder().name("body").build();
                when(turSNSiteFieldExtRepository.findByTurSNSiteAndMltAndEnabled(site, 1, 1))
                                .thenReturn(List.of(mltField));

                TurSolrQueryBuilder builder = new TurSolrQueryBuilder(turSNSiteFieldExtRepository, turSNSiteCustomFacetRepository,
                                turSNSiteCustomSortRepository, turSNRankingExpressionRepository, turSNRankingConditionRepository, turSNTargetingRules, turSNSearchRuleEvaluator, snapshotService);
                SolrQuery query = new SolrQuery();

                List<TurSNSiteFieldExt> result = builder.prepareQueryMLT(site, query);

                assertThat(result).containsExactly(mltField);
                assertThat(query.getBool(MoreLikeThisParams.MLT)).isTrue();
                assertThat(query.get(MoreLikeThisParams.SIMILARITY_FIELDS)).isEqualTo("body");
        }

        @Test
        void testPrepareQueryMLTDoesNothingWhenDisabledOrNoFields() {
                TurSNSite site = new TurSNSite();
                site.setMlt(0);
                when(turSNSiteFieldExtRepository.findByTurSNSiteAndMltAndEnabled(site, 1, 1))
                                .thenReturn(Collections.emptyList());

                SolrQuery query = new SolrQuery();
                List<TurSNSiteFieldExt> result = builder().prepareQueryMLT(site, query);

                assertThat(result).isEmpty();
                assertThat(query.get(MoreLikeThisParams.MLT)).isNull();
        }

        @Test
        void testPrepareQueryHLEnabledAndDisabled() {
                TurSNSite site = new TurSNSite();
                site.setHlPre("<mark>");
                site.setHlPost("</mark>");
                TurSNSiteFieldExt hlField = TurSNSiteFieldExt.builder().name("content").build();
                when(turSNSiteFieldExtRepository.findByTurSNSiteAndHlAndEnabled(site, 1, 1))
                                .thenReturn(List.of(hlField));

                TurSNSearchParams searchParams = new TurSNSearchParams();
                TurSEParameters parameters = new TurSEParameters(searchParams);
                TurSNConfig config = new TurSNConfig();
                config.setHlEnabled(true);
                TurSNSiteSearchContext enabledContext = new TurSNSiteSearchContext("site", config,
                                parameters, Locale.US, URI.create("http://localhost/search"));

                SolrQuery enabledQuery = new SolrQuery();
                builder().prepareQueryHL(site, enabledQuery, enabledContext);

                assertThat(enabledQuery.getBool(HighlightParams.HIGHLIGHT)).isTrue();
                assertThat(enabledQuery.get(HighlightParams.FIELDS)).isEqualTo("content");

                config.setHlEnabled(false);
                TurSNSiteSearchContext disabledContext = new TurSNSiteSearchContext("site", config,
                                parameters, Locale.US, URI.create("http://localhost/search"));
                SolrQuery disabledQuery = new SolrQuery();
                builder().prepareQueryHL(site, disabledQuery, disabledContext);
                assertThat(disabledQuery.get(HighlightParams.FIELDS)).isNull();
        }

        @Test
        void testPrepareQueryFacetConfiguresFacetFields() {
                TurSNSite site = new TurSNSite();
                site.setFacet(1);
                site.setItemsPerFacet(5);
                site.setFacetSort(null);
                site.setFacetType(TurSNSiteFacetFieldEnum.AND);
                site.setFacetItemType(TurSNSiteFacetFieldEnum.AND);

                TurSNSiteFieldExt facet = TurSNSiteFieldExt.builder()
                                .name("category")
                                .facet(1)
                                .enabled(1)
                                .type(com.viglet.turing.commons.se.field.TurSEFieldType.STRING)
                                .facetSort(com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldSortEnum.DEFAULT)
                                .build();

                when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                                .thenReturn(List.of(facet));

                SolrQuery query = new SolrQuery();
                TurSNFilterParams params = TurSNFilterParams.builder().build();
                List<TurSNSiteFieldExt> facets = builder().prepareQueryFacet(site, query, params, (com.viglet.turing.commons.se.TurSEParameters) null);

                assertThat(facets).hasSize(1);
                assertThat(query.getBool("facet")).isTrue();
                assertThat(query.get("facet.limit")).isEqualTo("5");
                assertThat(query.getFacetFields()).isNotEmpty();
        }

        @Test
        void testPrepareQueryFacetIncludesCustomFacetWhenBaseFieldIsNotFacet() {
                TurSNSite site = new TurSNSite();
                site.setFacet(1);
                site.setItemsPerFacet(5);
                site.setFacetSort(null);

                TurSNSiteCustomFacetItem item = TurSNSiteCustomFacetItem.builder()
                                .label("101 - 500")
                                .rangeStart(new BigDecimal("101"))
                                .rangeEnd(new BigDecimal("500"))
                                .build();
                TurSNSiteCustomFacet customFacet = TurSNSiteCustomFacet.builder()
                                .name("price_range")
                                .items(new java.util.HashSet<>(List.of(item)))
                                .build();

                TurSNSiteFieldExt nonFacetField = TurSNSiteFieldExt.builder()
                                .id("field-1")
                                .name("id")
                                .facet(0)
                                .type(com.viglet.turing.commons.se.field.TurSEFieldType.STRING)
                                .build();
                customFacet.setTurSNSiteFieldExt(nonFacetField);

                when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                                .thenReturn(List.of(nonFacetField));
                when(turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(any()))
                                .thenReturn(List.of(customFacet));

                SolrQuery query = new SolrQuery();
                List<TurSNSiteFieldExt> facets = builder().prepareQueryFacet(site, query,
                                TurSNFilterParams.builder().build(), (com.viglet.turing.commons.se.TurSEParameters) null);

                assertThat(facets).hasSize(1);
                assertThat(query.getFacetQuery()).isNotEmpty();
        }

        @Test
        void testPrepareQueryFacetSelectedCustomFacetUsesItsOwnNameForOrAndExclusion() {
                TurSNSite site = new TurSNSite();
                site.setFacet(1);
                site.setItemsPerFacet(5);
                site.setFacetSort(null);
                site.setFacetType(TurSNSiteFacetFieldEnum.OR);
                site.setFacetItemType(TurSNSiteFacetFieldEnum.AND);

                TurSNSiteCustomFacetItem item = TurSNSiteCustomFacetItem.builder()
                                .label("101 - 500")
                                .rangeStart(new BigDecimal("101"))
                                .rangeEnd(new BigDecimal("500"))
                                .build();

                TurSNSiteFieldExt nonFacetField = TurSNSiteFieldExt.builder()
                                .id("field-1")
                                .name("id")
                                .facet(0)
                                .type(com.viglet.turing.commons.se.field.TurSEFieldType.STRING)
                                .build();
                TurSNSiteCustomFacet customFacet = TurSNSiteCustomFacet.builder()
                                .name("price_range")
                                .items(new java.util.HashSet<>(List.of(item)))
                                .turSNSiteFieldExt(nonFacetField)
                                .build();

                when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                                .thenReturn(List.of(nonFacetField));
                when(turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(any()))
                                .thenReturn(List.of(customFacet));

                SolrQuery query = new SolrQuery();
                TurSNFilterParams params = TurSNFilterParams.builder()
                                .defaultValues(List.of("price_range:101 - 500"))
                                .operator(TurSNFilterQueryOperator.NONE)
                                .itemOperator(TurSNFilterQueryOperator.NONE)
                                .build();

                builder().prepareQueryFacet(site, query, params, (com.viglet.turing.commons.se.TurSEParameters) null);

                assertThat(query.getFacetQuery()).isNotEmpty();
                assertThat(List.of(query.getFacetQuery()))
                                .allMatch(facetQuery -> !facetQuery.contains("ex=_all_"));
        }

        @Test
        void testPrepareSolrQueryUsesCustomFacetSpecificOperatorsInsteadOfFieldOrGlobal() {
                TurSNSite site = new TurSNSite();
                site.setFacetType(TurSNSiteFacetFieldEnum.AND);
                site.setFacetItemType(TurSNSiteFacetFieldEnum.AND);
                site.setRowsPerPage(10);
                site.setSpellCheck(0);
                site.setSpellCheckFixes(0);

                TurSNSiteCustomFacetItem item1 = TurSNSiteCustomFacetItem.builder()
                                .label("1 - 10")
                                .rangeStart(new BigDecimal("1"))
                                .rangeEnd(new BigDecimal("10"))
                                .build();
                TurSNSiteCustomFacetItem item2 = TurSNSiteCustomFacetItem.builder()
                                .label("11 - 20")
                                .rangeStart(new BigDecimal("11"))
                                .rangeEnd(new BigDecimal("20"))
                                .build();

                TurSNSiteCustomFacet customFacet = TurSNSiteCustomFacet.builder()
                                .name("price_range")
                                .facetType(TurSNSiteFacetFieldEnum.OR)
                                .facetItemType(TurSNSiteFacetFieldEnum.OR)
                                .items(new java.util.HashSet<>(List.of(item1, item2)))
                                .build();

                TurSNSiteFieldExt field = TurSNSiteFieldExt.builder()
                                .id("field-1")
                                .name("id")
                                .facetType(TurSNSiteFacetFieldEnum.AND)
                                .facetItemType(TurSNSiteFacetFieldEnum.AND)
                                .type(com.viglet.turing.commons.se.field.TurSEFieldType.STRING)
                                .build();
                customFacet.setTurSNSiteFieldExt(field);

                when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                                .thenReturn(List.of(field));
                when(turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(any()))
                                .thenReturn(List.of(customFacet));
                when(turSNSiteFieldExtRepository.findByTurSNSiteAndFacetAndEnabledAndType(site, 1, 1,
                                com.viglet.turing.commons.se.field.TurSEFieldType.DATE))
                                .thenReturn(Collections.emptyList());
                when(turSNSiteFieldExtRepository.findByTurSNSite(any(), eq(site)))
                                .thenReturn(Collections.emptyList());
                when(turSNRankingExpressionRepository.findByTurSNSite(any(), eq(site)))
                                .thenReturn(Collections.emptySet());

                TurSNSearchParams params = new TurSNSearchParams();
                params.setQ("query");
                params.setRows(10);
                params.setFq(List.of("price_range:1 - 10", "price_range:11 - 20"));
                TurSEParameters seParameters = new TurSEParameters(params, new TurSNSitePostParamsBean());

                SolrQuery query = builder().prepareSolrQuery(
                                contextFrom(seParameters, new TurSNSitePostParamsBean()), site,
                                seParameters, new TurSESpellCheckResult(false, ""));

                assertThat(query.getFilterQueries())
                                .hasSize(1)
                                .as("Filter query should contain OR operator")
                                .allMatch(fq -> fq.contains(" OR "))
                                .as("Filter query should contain range 1 TO 10")
                                .allMatch(fq -> fq.contains("id:[1 TO 10]"))
                                .as("Filter query should contain range 11 TO 20")
                                .allMatch(fq -> fq.contains("id:[11 TO 20]"));
        }

        @Test
        void testPrepareSolrQueryAppliesExactMatchAndTargetingRulesAndGroup() {
                TurSNSite site = new TurSNSite();
                site.setRowsPerPage(20);
                site.setExactMatch(1);
                site.setExactMatchField("title_exact");
                site.setSpellCheck(0);
                site.setSpellCheckFixes(0);

                when(turSNSiteFieldExtRepository.findByTurSNSite(any(), eq(site)))
                                .thenReturn(Collections.emptyList());
                when(turSNRankingExpressionRepository.findByTurSNSite(any(), eq(site)))
                                .thenReturn(Collections.<TurSNRankingExpression>emptySet());

                TurSNSearchParams searchParams = new TurSNSearchParams();
                searchParams.setQ("\"hello world\"");
                searchParams.setRows(-1);
                searchParams.setGroup("type");
                TurSNSitePostParamsBean post = new TurSNSitePostParamsBean();
                post.setTargetingRules(List.of("segment:vip"));

                TurSEParameters parameters = new TurSEParameters(searchParams, post);
                TurSESpellCheckResult spell = new TurSESpellCheckResult(false, "");

                when(turSNTargetingRules.ruleExpression(com.viglet.turing.sn.tr.TurSNTargetingRuleMethod.AND,
                                List.of("segment:vip")))
                                .thenReturn("segment:\"vip\"");

                SolrQuery query = builder().prepareSolrQuery(contextFrom(parameters, post), site,
                                parameters, spell);

                assertThat(query.getQuery()).isEqualTo("title_exact:\"hello world\"");
                assertThat(query.get(GroupParams.GROUP)).isEqualTo("true");
                assertThat(query.get(GroupParams.GROUP_FIELD)).isEqualTo("type");
                assertThat(query.getFilterQueries()).contains("segment:\"vip\"");
                verify(turSNTargetingRules).ruleExpression(
                                com.viglet.turing.sn.tr.TurSNTargetingRuleMethod.AND,
                                List.of("segment:vip"));
        }

        @Test
        void testPrepareSolrQueryUsesCorrectedTextWhenAutoCorrectionEnabled() {
                TurSNSite site = new TurSNSite();
                site.setRowsPerPage(10);
                site.setSpellCheck(1);
                site.setSpellCheckFixes(1);
                site.setExactMatch(0);

                when(turSNSiteFieldExtRepository.findByTurSNSite(any(), eq(site)))
                                .thenReturn(Collections.emptyList());
                when(turSNRankingExpressionRepository.findByTurSNSite(any(), eq(site)))
                                .thenReturn(Collections.<TurSNRankingExpression>emptySet());

                TurSNSearchParams searchParams = new TurSNSearchParams();
                searchParams.setQ("helo");
                searchParams.setP(1);
                searchParams.setRows(10);
                searchParams.setNfpr(0);
                TurSEParameters parameters = new TurSEParameters(searchParams, new TurSNSitePostParamsBean());

                TurSESpellCheckResult spell = new TurSESpellCheckResult(true, "hello");

                SolrQuery query = builder().prepareSolrQuery(
                                contextFrom(parameters, new TurSNSitePostParamsBean()), site,
                                parameters, spell);

                assertThat(query.getQuery()).isEqualTo("hello");
        }

        @Test
        void testPrepareSolrQueryTargetingRulesWithConditionBuildsFilterQuery() {
                TurSNSite site = new TurSNSite();
                site.setRowsPerPage(10);
                site.setSpellCheck(0);
                site.setSpellCheckFixes(0);
                site.setExactMatch(0);

                when(turSNSiteFieldExtRepository.findByTurSNSite(any(), eq(site)))
                                .thenReturn(Collections.emptyList());
                when(turSNRankingExpressionRepository.findByTurSNSite(any(), eq(site)))
                                .thenReturn(Collections.<TurSNRankingExpression>emptySet());
                when(turSNTargetingRules.andMethod(List.of("ruleA"))).thenReturn("ruleA");
                when(turSNTargetingRules.orMethod(List.of("ruleB"))).thenReturn("ruleB");

                TurSNSearchParams searchParams = new TurSNSearchParams();
                searchParams.setQ("news");
                searchParams.setRows(10);
                TurSNSitePostParamsBean post = new TurSNSitePostParamsBean();
                post.setTargetingRulesWithCondition(new HashMap<>() {
                        {
                                put("user:1", List.of("ruleA"));
                        }
                });
                post.setTargetingRulesWithConditionOR(new HashMap<>() {
                        {
                                put("user:1", List.of("ruleB"));
                        }
                });

                TurSEParameters parameters = new TurSEParameters(searchParams, post);

                SolrQuery query = builder().prepareSolrQuery(contextFrom(parameters, post), site,
                                parameters, new TurSESpellCheckResult(false, ""));

                assertThat(query.getFilterQueries()).isNotEmpty();
                assertThat(String.join(" ", query.getFilterQueries())).contains("user:1");
        }

        @Test
        void testPrepareSolrQueryAppliesSortAndDefaultRowsWhenNegative() {
                TurSNSite site = new TurSNSite();
                site.setRowsPerPage(0);
                site.setDefaultDateField("publishedDate");
                site.setSpellCheck(0);
                site.setSpellCheckFixes(0);

                when(turSNSiteFieldExtRepository.findByTurSNSite(any(), eq(site)))
                                .thenReturn(Collections.emptyList());
                when(turSNRankingExpressionRepository.findByTurSNSite(any(), eq(site)))
                                .thenReturn(Collections.emptySet());

                TurSNSearchParams firstParams = new TurSNSearchParams();
                firstParams.setQ("java");
                firstParams.setRows(-1);
                firstParams.setSort("createdAt:asc");
                TurSEParameters first = new TurSEParameters(firstParams, new TurSNSitePostParamsBean());

                SolrQuery firstQuery = builder().prepareSolrQuery(
                                contextFrom(first, new TurSNSitePostParamsBean()), site,
                                first, new TurSESpellCheckResult(false, ""));

                assertThat(first.getRows()).isEqualTo(10);
                assertThat(firstQuery.getSortField()).contains("createdAt asc");

                TurSNSearchParams newestParams = new TurSNSearchParams();
                newestParams.setQ("java");
                newestParams.setRows(10);
                newestParams.setSort("newest");
                TurSEParameters newest = new TurSEParameters(newestParams, new TurSNSitePostParamsBean());

                SolrQuery newestQuery = builder().prepareSolrQuery(
                                contextFrom(newest, new TurSNSitePostParamsBean()), site,
                                newest, new TurSESpellCheckResult(false, ""));

                assertThat(newestQuery.getSortField()).contains("publishedDate desc");

                TurSNSearchParams oldestParams = new TurSNSearchParams();
                oldestParams.setQ("java");
                oldestParams.setRows(10);
                oldestParams.setSort("oldest");
                TurSEParameters oldest = new TurSEParameters(oldestParams, new TurSNSitePostParamsBean());

                SolrQuery oldestQuery = builder().prepareSolrQuery(
                                contextFrom(oldest, new TurSNSitePostParamsBean()), site,
                                oldest, new TurSESpellCheckResult(false, ""));

                assertThat(oldestQuery.getSortField()).contains("publishedDate asc");
        }

        @Test
        void testPrepareSolrQueryFormatsDateRangeAndUnknownFacetInFilterQuery() {
                TurSNSite site = new TurSNSite();
                site.setFacetType(TurSNSiteFacetFieldEnum.AND);
                site.setFacetItemType(TurSNSiteFacetFieldEnum.AND);
                site.setRowsPerPage(10);
                site.setSpellCheck(0);
                site.setSpellCheckFixes(0);

                TurSNSiteFieldExt enabledDate = TurSNSiteFieldExt.builder()
                                .name("publishDate")
                                .type(com.viglet.turing.commons.se.field.TurSEFieldType.DATE)
                                .facetRange(TurSNSiteFacetRangeEnum.MONTH)
                                .build();
                TurSNSiteFieldExt enabledCategory = TurSNSiteFieldExt.builder()
                                .name("category")
                                .type(com.viglet.turing.commons.se.field.TurSEFieldType.STRING)
                                .build();

                when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                                .thenReturn(List.of(enabledDate, enabledCategory));
                when(turSNSiteFieldExtRepository.findByTurSNSiteAndFacetAndEnabledAndType(site, 1, 1,
                                com.viglet.turing.commons.se.field.TurSEFieldType.DATE))
                                .thenReturn(List.of(enabledDate));
                when(turSNSiteFieldExtRepository.findByTurSNSite(any(), eq(site)))
                                .thenReturn(Collections.emptyList());
                when(turSNRankingExpressionRepository.findByTurSNSite(any(), eq(site)))
                                .thenReturn(Collections.emptySet());

                TurSNSearchParams params = new TurSNSearchParams();
                params.setQ("query");
                params.setRows(10);
                params.setFq(List.of("publishDate:2024-01-01T00:00:00Z", "category:books", "other:value"));
                TurSEParameters seParameters = new TurSEParameters(params, new TurSNSitePostParamsBean());

                SolrQuery query = builder().prepareSolrQuery(
                                contextFrom(seParameters, new TurSNSitePostParamsBean()), site,
                                seParameters, new TurSESpellCheckResult(false, ""));

                assertThat(query.getFilterQueries()).hasSize(1);
                String fq = query.getFilterQueries()[0];
                assertThat(fq)
                                .contains("publishDate:[ 2024-01-01T00:00:00Z TO ")
                                .contains("category:\"books\"")
                                .contains("other:\"value\"");
        }

        @Test
        void testPrepareSolrQueryAddsBoostQueryIncludingRecentDatesExpression() {
                TurSNSite site = new TurSNSite();
                site.setRowsPerPage(10);
                site.setSpellCheck(0);
                site.setSpellCheckFixes(0);

                TurSNSiteFieldExt dateField = TurSNSiteFieldExt.builder()
                                .name("published")
                                .type(com.viglet.turing.commons.se.field.TurSEFieldType.DATE)
                                .build();
                TurSNSiteFieldExt typeField = TurSNSiteFieldExt.builder()
                                .name("type")
                                .type(com.viglet.turing.commons.se.field.TurSEFieldType.STRING)
                                .build();

                TurSNRankingExpression expression = new TurSNRankingExpression();
                expression.setId("expr-1");
                expression.setWeight(5.0f);

                TurSNRankingCondition dateCondition = new TurSNRankingCondition();
                dateCondition.setAttribute("published");
                dateCondition.setValue("asc");
                TurSNRankingCondition typeCondition = new TurSNRankingCondition();
                typeCondition.setAttribute("type");
                typeCondition.setValue("article");

                when(turSNSiteFieldExtRepository.findByTurSNSite(any(), eq(site)))
                                .thenReturn(List.of(dateField, typeField));
                when(turSNRankingExpressionRepository.findByTurSNSite(any(), eq(site)))
                                .thenReturn(Set.of(expression));
                when(turSNRankingConditionRepository.findByTurSNRankingExpression(expression))
                                .thenReturn(Set.of(dateCondition, typeCondition));

                TurSNSearchParams params = new TurSNSearchParams();
                params.setQ("query");
                params.setRows(10);
                TurSEParameters seParameters = new TurSEParameters(params, new TurSNSitePostParamsBean());

                SolrQuery query = builder().prepareSolrQuery(
                                contextFrom(seParameters, new TurSNSitePostParamsBean()), site,
                                seParameters, new TurSESpellCheckResult(false, ""));

                assertThat(query.getParams("bq")).isNotNull();
                assertThat(String.join(" ", query.getParams("bq"))).contains("_query_:");
                assertThat(String.join(" ", query.getParams("bq"))).contains("type:\"article\"");
        }

        @Test
        void testPrepareQueryFacetWithOneFacetHandlesDateRangeAndEntityPrefix() {
                TurSNSite site = new TurSNSite();
                site.setFacet(1);
                site.setItemsPerFacet(8);
                site.setFacetSort(null);
                site.setFacetType(TurSNSiteFacetFieldEnum.OR);
                site.setFacetItemType(TurSNSiteFacetFieldEnum.OR);

                TurSNSiteFieldExt dateFacet = TurSNSiteFieldExt.builder()
                                .name("publishDate")
                                .snType(TurSNFieldType.SE)
                                .facet(1)
                                .enabled(1)
                                .type(com.viglet.turing.commons.se.field.TurSEFieldType.DATE)
                                .facetRange(TurSNSiteFacetRangeEnum.YEAR)
                                .facetSort(TurSNSiteFacetFieldSortEnum.COUNT)
                                .build();

                TurSNSiteFieldExt entityFacet = TurSNSiteFieldExt.builder()
                                .name("person")
                                .snType(TurSNFieldType.NER)
                                .facet(1)
                                .enabled(1)
                                .type(com.viglet.turing.commons.se.field.TurSEFieldType.STRING)
                                .facetSort(TurSNSiteFacetFieldSortEnum.ALPHABETICAL)
                                .build();

                when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                                .thenReturn(List.of(dateFacet, entityFacet));

                SolrQuery dateQuery = new SolrQuery();
                builder().prepareQueryFacetWithOneFacet(site, dateQuery, TurSNFilterParams.builder().build(),
                                "publishDate");
                assertThat(dateQuery.getParams("facet.range")).isNotNull();

                SolrQuery entityQuery = new SolrQuery();
                builder().prepareQueryFacetWithOneFacet(site, entityQuery, TurSNFilterParams.builder().build(),
                                "person");
                assertThat(entityQuery.getFacetFields()).isNotEmpty();
                assertThat(String.join(",", entityQuery.getFacetFields())).contains("turing_entity_person");
                assertThat(entityQuery.get("f.person.facet.sort")).isEqualTo("index");
        }

        @Test
        void testHasGroupWithBlankStringReturnsFalse() {
                TurSNSearchParams searchParams = new TurSNSearchParams();
                searchParams.setGroup("   ");
                TurSEParameters parameters = new TurSEParameters(searchParams);

                // StringUtils.hasText returns false for blank strings
                assertThat(builder().hasGroup(parameters)).isFalse();
        }

        @Test
        void testGetFqFieldsReturnsEmptyForNullParams() {
                TurSNFilterParams params = TurSNFilterParams.builder().build();
                List<String> keys = builder().getFqFields(params);
                assertThat(keys).isEmpty();
        }

        @Test
        void testGetFqFieldsExtractsKeysFromAllSources() {
                TurSNFilterParams params = TurSNFilterParams.builder()
                                .defaultValues(List.of("category:books"))
                                .and(List.of("author:john"))
                                .or(List.of("format:pdf"))
                                .build();

                List<String> keys = builder().getFqFields(params);

                assertThat(keys).containsExactly("category", "author", "format");
        }

        @Test
        void testGetFqFieldsHandlesEmptyLists() {
                TurSNFilterParams params = TurSNFilterParams.builder()
                                .defaultValues(Collections.emptyList())
                                .and(Collections.emptyList())
                                .or(Collections.emptyList())
                                .build();

                List<String> keys = builder().getFqFields(params);
                assertThat(keys).isEmpty();
        }

        @Test
        void testCustomFacetQuerySeparatorConstant() {
                assertThat(TurSolrQueryBuilder.CUSTOM_FACET_QUERY_SEPARATOR).isEqualTo("::");
        }

        @Test
        void testGetFacetTypeAndFacetItemTypeValuesWithParamOverrides() {
                TurSNSite site = new TurSNSite();
                site.setFacetType(TurSNSiteFacetFieldEnum.AND);
                site.setFacetItemType(TurSNSiteFacetFieldEnum.AND);
                TurSNFilterParams params = TurSNFilterParams.builder()
                                .operator(TurSNFilterQueryOperator.AND)
                                .itemOperator(TurSNFilterQueryOperator.OR)
                                .build();

                TurSNFacetTypeContext context = new TurSNFacetTypeContext(site, params);

                assertThat(TurSolrQueryBuilder.getFacetTypeAndFacetItemTypeValues(context))
                                .isEqualTo("AND-OR");
        }

        @Test
        void testGetFacetTypeAndFacetItemTypeValuesOrOr() {
                TurSNSite site = new TurSNSite();
                site.setFacetType(TurSNSiteFacetFieldEnum.OR);
                site.setFacetItemType(TurSNSiteFacetFieldEnum.OR);
                TurSNFilterParams params = TurSNFilterParams.builder()
                                .operator(TurSNFilterQueryOperator.NONE)
                                .itemOperator(TurSNFilterQueryOperator.NONE)
                                .build();

                TurSNFacetTypeContext context = new TurSNFacetTypeContext(site, params);

                assertThat(TurSolrQueryBuilder.getFacetTypeAndFacetItemTypeValues(context))
                                .isEqualTo("OR-OR");
        }

        @Test
        void testPrepareQueryFacetReturnsFacetFields() {
                TurSNSite site = new TurSNSite();
                site.setFacet(1);
                site.setItemsPerFacet(10);
                site.setFacetSort(null);
                site.setFacetType(TurSNSiteFacetFieldEnum.AND);
                site.setFacetItemType(TurSNSiteFacetFieldEnum.AND);

                TurSNSiteFieldExt f1 = TurSNSiteFieldExt.builder()
                                .name("category")
                                .facet(1)
                                .enabled(1)
                                .facetPosition(2)
                                .type(com.viglet.turing.commons.se.field.TurSEFieldType.STRING)
                                .facetSort(TurSNSiteFacetFieldSortEnum.DEFAULT)
                                .build();
                TurSNSiteFieldExt f2 = TurSNSiteFieldExt.builder()
                                .name("type")
                                .facet(1)
                                .enabled(1)
                                .facetPosition(1)
                                .type(com.viglet.turing.commons.se.field.TurSEFieldType.STRING)
                                .facetSort(TurSNSiteFacetFieldSortEnum.DEFAULT)
                                .build();

                when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                                .thenReturn(List.of(f1, f2));

                SolrQuery query = new SolrQuery();
                List<TurSNSiteFieldExt> facets = builder().prepareQueryFacet(site, query,
                                TurSNFilterParams.builder().build(), (com.viglet.turing.commons.se.TurSEParameters) null);

                assertThat(facets).hasSize(2);
                assertThat(facets).extracting(TurSNSiteFieldExt::getName)
                                .containsExactlyInAnyOrder("category", "type");
                assertThat(query.getBool("facet")).isTrue();
        }

        @Test
        void testPrepareQueryFacetWithDisabledFacetReturnsEmpty() {
                TurSNSite site = new TurSNSite();
                site.setFacet(0);

                SolrQuery query = new SolrQuery();
                List<TurSNSiteFieldExt> facets = builder().prepareQueryFacet(site, query,
                                TurSNFilterParams.builder().build(), (com.viglet.turing.commons.se.TurSEParameters) null);

                assertThat(facets).isEmpty();
        }

        @Test
        void testPrepareSolrQueryWithNullSortUsesDefault() {
                TurSNSite site = new TurSNSite();
                site.setRowsPerPage(10);
                site.setSpellCheck(0);
                site.setSpellCheckFixes(0);

                when(turSNSiteFieldExtRepository.findByTurSNSite(any(), eq(site)))
                                .thenReturn(Collections.emptyList());
                when(turSNRankingExpressionRepository.findByTurSNSite(any(), eq(site)))
                                .thenReturn(Collections.emptySet());

                TurSNSearchParams searchParams = new TurSNSearchParams();
                searchParams.setQ("test");
                searchParams.setRows(10);
                searchParams.setSort(null);
                TurSEParameters parameters = new TurSEParameters(searchParams, new TurSNSitePostParamsBean());

                SolrQuery query = builder().prepareSolrQuery(
                                contextFrom(parameters, new TurSNSitePostParamsBean()), site,
                                parameters, new TurSESpellCheckResult(false, ""));

                assertThat(query.getQuery()).isEqualTo("test");
        }

        @Test
        void testPrepareQueryMLTWithMltDisabledReturnsFieldsButDoesNotConfigureQuery() {
                TurSNSite site = new TurSNSite();
                site.setMlt(0);
                TurSNSiteFieldExt mltField = TurSNSiteFieldExt.builder().name("body").build();
                when(turSNSiteFieldExtRepository.findByTurSNSiteAndMltAndEnabled(site, 1, 1))
                                .thenReturn(List.of(mltField));

                SolrQuery query = new SolrQuery();
                List<TurSNSiteFieldExt> result = builder().prepareQueryMLT(site, query);

                // Fields are always returned from the repository
                assertThat(result).containsExactly(mltField);
                // But the query is NOT configured with MLT params when mlt is disabled
                assertThat(query.get(MoreLikeThisParams.MLT)).isNull();
        }

        @Test
        void testPrepareSolrQueryWithoutExactMatchDoesNotModifyQuery() {
                TurSNSite site = new TurSNSite();
                site.setRowsPerPage(10);
                site.setExactMatch(0);
                site.setSpellCheck(0);
                site.setSpellCheckFixes(0);

                when(turSNSiteFieldExtRepository.findByTurSNSite(any(), eq(site)))
                                .thenReturn(Collections.emptyList());
                when(turSNRankingExpressionRepository.findByTurSNSite(any(), eq(site)))
                                .thenReturn(Collections.emptySet());

                TurSNSearchParams searchParams = new TurSNSearchParams();
                searchParams.setQ("\"quoted query\"");
                searchParams.setRows(10);
                TurSEParameters parameters = new TurSEParameters(searchParams, new TurSNSitePostParamsBean());

                SolrQuery query = builder().prepareSolrQuery(
                                contextFrom(parameters, new TurSNSitePostParamsBean()), site,
                                parameters, new TurSESpellCheckResult(false, ""));

                // Without exact match, the query should not be prefixed
                assertThat(query.getQuery()).isEqualTo("\"quoted query\"");
        }

        @Test
        void testPrepareSolrQueryExactMatchWithBlankFieldDoesNotModify() {
                TurSNSite site = new TurSNSite();
                site.setRowsPerPage(10);
                site.setExactMatch(1);
                site.setExactMatchField("");
                site.setSpellCheck(0);
                site.setSpellCheckFixes(0);

                when(turSNSiteFieldExtRepository.findByTurSNSite(any(), eq(site)))
                                .thenReturn(Collections.emptyList());
                when(turSNRankingExpressionRepository.findByTurSNSite(any(), eq(site)))
                                .thenReturn(Collections.emptySet());

                TurSNSearchParams searchParams = new TurSNSearchParams();
                searchParams.setQ("\"test\"");
                searchParams.setRows(10);
                TurSEParameters parameters = new TurSEParameters(searchParams, new TurSNSitePostParamsBean());

                SolrQuery query = builder().prepareSolrQuery(
                                contextFrom(parameters, new TurSNSitePostParamsBean()), site,
                                parameters, new TurSESpellCheckResult(false, ""));

                // ExactMatchField is blank so exact match should NOT apply
                assertThat(query.getQuery()).isEqualTo("\"test\"");
        }

        @Test
        void testPrepareSolrQueryWithFacetSortCount() {
                TurSNSite site = new TurSNSite();
                site.setFacet(1);
                site.setItemsPerFacet(10);
                site.setFacetSort(com.viglet.turing.persistence.model.sn.TurSNSiteFacetSortEnum.COUNT);
                site.setFacetType(TurSNSiteFacetFieldEnum.AND);
                site.setFacetItemType(TurSNSiteFacetFieldEnum.AND);

                TurSNSiteFieldExt facet = TurSNSiteFieldExt.builder()
                                .name("category")
                                .facet(1)
                                .enabled(1)
                                .type(com.viglet.turing.commons.se.field.TurSEFieldType.STRING)
                                .facetSort(TurSNSiteFacetFieldSortEnum.DEFAULT)
                                .build();

                when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                                .thenReturn(List.of(facet));

                SolrQuery query = new SolrQuery();
                builder().prepareQueryFacet(site, query, TurSNFilterParams.builder().build(), null);

                assertThat(query.get("facet.sort")).isEqualTo("count");
        }

        @Test
        void testPrepareSolrQueryWithFacetSortAlphabetical() {
                TurSNSite site = new TurSNSite();
                site.setFacet(1);
                site.setItemsPerFacet(10);
                site.setFacetSort(com.viglet.turing.persistence.model.sn.TurSNSiteFacetSortEnum.ALPHABETICAL);
                site.setFacetType(TurSNSiteFacetFieldEnum.AND);
                site.setFacetItemType(TurSNSiteFacetFieldEnum.AND);

                TurSNSiteFieldExt facet = TurSNSiteFieldExt.builder()
                                .name("category")
                                .facet(1)
                                .enabled(1)
                                .type(com.viglet.turing.commons.se.field.TurSEFieldType.STRING)
                                .facetSort(TurSNSiteFacetFieldSortEnum.DEFAULT)
                                .build();

                when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                                .thenReturn(List.of(facet));

                SolrQuery query = new SolrQuery();
                builder().prepareQueryFacet(site, query, TurSNFilterParams.builder().build(), null);

                assertThat(query.get("facet.sort")).isEqualTo("index");
        }

        @Test
        void testPrepareQueryHLWithMultipleFields() {
                TurSNSite site = new TurSNSite();
                site.setHlPre("<b>");
                site.setHlPost("</b>");

                TurSNSiteFieldExt hlField1 = TurSNSiteFieldExt.builder().name("title").build();
                TurSNSiteFieldExt hlField2 = TurSNSiteFieldExt.builder().name("body").build();
                when(turSNSiteFieldExtRepository.findByTurSNSiteAndHlAndEnabled(site, 1, 1))
                                .thenReturn(List.of(hlField1, hlField2));

                TurSNSearchParams searchParams = new TurSNSearchParams();
                TurSEParameters parameters = new TurSEParameters(searchParams);
                TurSNConfig config = new TurSNConfig();
                config.setHlEnabled(true);
                TurSNSiteSearchContext context = new TurSNSiteSearchContext("site", config,
                                parameters, Locale.US, URI.create("http://localhost/search"));

                SolrQuery query = new SolrQuery();
                List<TurSNSiteFieldExt> result = builder().prepareQueryHL(site, query, context);

                assertThat(result).hasSize(2);
                assertThat(query.get(HighlightParams.FIELDS)).isEqualTo("title,body");
                assertThat(query.get(HighlightParams.SIMPLE_PRE)).isEqualTo("<b>");
                assertThat(query.get(HighlightParams.SIMPLE_POST)).isEqualTo("</b>");
        }

        @Test
        void testPrepareQueryFacetWithNullItemsPerFacetDoesNotSetFacet() {
                TurSNSite site = new TurSNSite();
                site.setFacet(1);
                site.setItemsPerFacet(null);

                TurSNSiteFieldExt facet = TurSNSiteFieldExt.builder()
                                .name("category")
                                .facet(1)
                                .enabled(1)
                                .build();

                when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                                .thenReturn(List.of(facet));

                SolrQuery query = new SolrQuery();
                builder().prepareQueryFacet(site, query,
                                TurSNFilterParams.builder().build(), (com.viglet.turing.commons.se.TurSEParameters) null);

                // itemsPerFacet is null so wasFacetConfigured returns false
                assertThat(query.get("facet")).isNull();
        }

        @Test
        void testPrepareSolrQueryWithNonExactMatchQuotedQuery() {
                TurSNSite site = new TurSNSite();
                site.setRowsPerPage(10);
                site.setExactMatch(1);
                site.setExactMatchField("title_exact");
                site.setSpellCheck(0);
                site.setSpellCheckFixes(0);

                when(turSNSiteFieldExtRepository.findByTurSNSite(any(), eq(site)))
                                .thenReturn(Collections.emptyList());
                when(turSNRankingExpressionRepository.findByTurSNSite(any(), eq(site)))
                                .thenReturn(Collections.emptySet());

                // Non-quoted query should NOT trigger exact match
                TurSNSearchParams searchParams = new TurSNSearchParams();
                searchParams.setQ("hello world");
                searchParams.setRows(10);
                TurSEParameters parameters = new TurSEParameters(searchParams, new TurSNSitePostParamsBean());

                SolrQuery query = builder().prepareSolrQuery(
                                contextFrom(parameters, new TurSNSitePostParamsBean()), site,
                                parameters, new TurSESpellCheckResult(false, ""));

                assertThat(query.getQuery()).isEqualTo("hello world");
        }

        @Test
        void testGetFqFieldsHandlesValuesWithoutColons() {
                TurSNFilterParams params = TurSNFilterParams.builder()
                                .defaultValues(List.of("nocolon"))
                                .build();

                List<String> keys = builder().getFqFields(params);
                // No colon means getKeyValueFromColon returns empty Optional -> null key
                assertThat(keys).hasSize(1);
        }

        @Test
        void testPrepareSolrQueryWithPositiveRowsPerPageSetsRows() {
                TurSNSite site = new TurSNSite();
                site.setRowsPerPage(25);
                site.setSpellCheck(0);
                site.setSpellCheckFixes(0);

                when(turSNSiteFieldExtRepository.findByTurSNSite(any(), eq(site)))
                                .thenReturn(Collections.emptyList());
                when(turSNRankingExpressionRepository.findByTurSNSite(any(), eq(site)))
                                .thenReturn(Collections.emptySet());

                TurSNSearchParams searchParams = new TurSNSearchParams();
                searchParams.setQ("test");
                searchParams.setRows(-1);
                TurSEParameters parameters = new TurSEParameters(searchParams, new TurSNSitePostParamsBean());

                builder().prepareSolrQuery(
                                contextFrom(parameters, new TurSNSitePostParamsBean()), site,
                                parameters, new TurSESpellCheckResult(false, ""));

                assertThat(parameters.getRows()).isEqualTo(25);
        }

        @Test
        void testPrepareSolrQueryWithAndFilterQueries() {
                TurSNSite site = new TurSNSite();
                site.setFacetType(TurSNSiteFacetFieldEnum.AND);
                site.setFacetItemType(TurSNSiteFacetFieldEnum.AND);
                site.setRowsPerPage(10);
                site.setSpellCheck(0);
                site.setSpellCheckFixes(0);

                TurSNSiteFieldExt categoryField = TurSNSiteFieldExt.builder()
                                .name("category")
                                .type(com.viglet.turing.commons.se.field.TurSEFieldType.STRING)
                                .build();
                when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                                .thenReturn(List.of(categoryField));
                when(turSNSiteFieldExtRepository.findByTurSNSiteAndFacetAndEnabledAndType(site, 1, 1,
                                com.viglet.turing.commons.se.field.TurSEFieldType.DATE))
                                .thenReturn(Collections.emptyList());
                when(turSNSiteFieldExtRepository.findByTurSNSite(any(), eq(site)))
                                .thenReturn(Collections.emptyList());
                when(turSNRankingExpressionRepository.findByTurSNSite(any(), eq(site)))
                                .thenReturn(Collections.emptySet());

                TurSNSearchParams params = new TurSNSearchParams();
                params.setQ("query");
                params.setRows(10);
                params.setFq(List.of("category:books"));
                TurSEParameters seParameters = new TurSEParameters(params, new TurSNSitePostParamsBean());

                SolrQuery query = builder().prepareSolrQuery(
                                contextFrom(seParameters, new TurSNSitePostParamsBean()), site,
                                seParameters, new TurSESpellCheckResult(false, ""));

                assertThat(query.getFilterQueries()).isNotEmpty();
                assertThat(query.getFilterQueries()[0]).contains("category:\"books\"");
        }

        @Test
        void testPrepareSolrQueryWrapsNegationFilterQueryWithMatchAll() {
                TurSNSite site = new TurSNSite();
                site.setFacetType(TurSNSiteFacetFieldEnum.AND);
                site.setFacetItemType(TurSNSiteFacetFieldEnum.AND);
                site.setRowsPerPage(10);
                site.setSpellCheck(0);
                site.setSpellCheckFixes(0);

                TurSNSiteFieldExt topicField = TurSNSiteFieldExt.builder()
                                .name("temas-secundarios")
                                .type(com.viglet.turing.commons.se.field.TurSEFieldType.STRING)
                                .build();
                when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                                .thenReturn(List.of(topicField));
                when(turSNSiteFieldExtRepository.findByTurSNSiteAndFacetAndEnabledAndType(site, 1, 1,
                                com.viglet.turing.commons.se.field.TurSEFieldType.DATE))
                                .thenReturn(Collections.emptyList());
                when(turSNSiteFieldExtRepository.findByTurSNSite(any(), eq(site)))
                                .thenReturn(Collections.emptyList());
                when(turSNRankingExpressionRepository.findByTurSNSite(any(), eq(site)))
                                .thenReturn(Collections.emptySet());

                TurSNSearchParams params = new TurSNSearchParams();
                params.setQ("query");
                params.setRows(10);
                params.setFq(List.of("temas-secundarios:Políticas Públicas",
                                "-id:/content/excluded-page"));
                TurSEParameters seParameters = new TurSEParameters(params, new TurSNSitePostParamsBean());

                SolrQuery query = builder().prepareSolrQuery(
                                contextFrom(seParameters, new TurSNSitePostParamsBean()), site,
                                seParameters, new TurSESpellCheckResult(false, ""));

                assertThat(query.getFilterQueries()).hasSize(1);
                String fq = query.getFilterQueries()[0];
                assertThat(fq)
                                .contains("temas-secundarios:\"Políticas Públicas\"")
                                .contains("*:* -id:\"/content/excluded-page\"")
                                .doesNotContain("(-id:");
        }

        // --- buildFilterQueryByOperator (via reflection) ---

        private static String invokeBuildFilterQueryByOperator(
                        com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacetOperatorEnum op,
                        String field, String start, String end) throws Exception {
                Method method = TurSolrQueryBuilder.class.getDeclaredMethod(
                                "buildFilterQueryByOperator", String.class,
                                com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacetOperatorEnum.class,
                                String.class, String.class);
                method.setAccessible(true);
                return (String) method.invoke(null, field, op, start, end);
        }

        @Test
        void testBuildFilterQueryBetweenInclusive() throws Exception {
                assertThat(invokeBuildFilterQueryByOperator(
                                com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacetOperatorEnum.BETWEEN_INCLUSIVE,
                                "price", "10", "100"))
                                .isEqualTo("price:[10 TO 100]");
        }

        @Test
        void testBuildFilterQueryBetweenInclusiveWithNullStart() throws Exception {
                assertThat(invokeBuildFilterQueryByOperator(
                                com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacetOperatorEnum.BETWEEN_INCLUSIVE,
                                "price", null, "100"))
                                .isEqualTo("price:[* TO 100]");
        }

        @Test
        void testBuildFilterQueryBetweenInclusiveWithNullEnd() throws Exception {
                assertThat(invokeBuildFilterQueryByOperator(
                                com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacetOperatorEnum.BETWEEN_INCLUSIVE,
                                "price", "10", null))
                                .isEqualTo("price:[10 TO *]");
        }

        @Test
        void testBuildFilterQueryBetweenExclusive() throws Exception {
                assertThat(invokeBuildFilterQueryByOperator(
                                com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacetOperatorEnum.BETWEEN_EXCLUSIVE,
                                "price", "10", "100"))
                                .isEqualTo("price:{10 TO 100}");
        }

        @Test
        void testBuildFilterQueryEqual() throws Exception {
                assertThat(invokeBuildFilterQueryByOperator(
                                com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacetOperatorEnum.EQUAL,
                                "status", "active", null))
                                .isEqualTo("status:active");
        }

        @Test
        void testBuildFilterQueryEqualUsesEndWhenStartNull() throws Exception {
                assertThat(invokeBuildFilterQueryByOperator(
                                com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacetOperatorEnum.EQUAL,
                                "status", null, "inactive"))
                                .isEqualTo("status:inactive");
        }

        @Test
        void testBuildFilterQueryEqualBothNullUsesWildcard() throws Exception {
                assertThat(invokeBuildFilterQueryByOperator(
                                com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacetOperatorEnum.EQUAL,
                                "status", null, null))
                                .isEqualTo("status:*");
        }

        @Test
        void testBuildFilterQueryGreaterThanStrict() throws Exception {
                assertThat(invokeBuildFilterQueryByOperator(
                                com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacetOperatorEnum.GREATER_THAN,
                                "price", "50", null))
                                .isEqualTo("price:{50 TO *]");
        }

        @Test
        void testBuildFilterQueryGreaterThanOrEqual() throws Exception {
                assertThat(invokeBuildFilterQueryByOperator(
                                com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacetOperatorEnum.GREATER_THAN_OR_EQUAL,
                                "price", "50", null))
                                .isEqualTo("price:[50 TO *]");
        }

        @Test
        void testBuildFilterQueryLessThanStrict() throws Exception {
                assertThat(invokeBuildFilterQueryByOperator(
                                com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacetOperatorEnum.LESS_THAN,
                                "price", null, "100"))
                                .isEqualTo("price:[* TO 100}");
        }

        @Test
        void testBuildFilterQueryLessThanOrEqual() throws Exception {
                assertThat(invokeBuildFilterQueryByOperator(
                                com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacetOperatorEnum.LESS_THAN_OR_EQUAL,
                                "price", null, "100"))
                                .isEqualTo("price:[* TO 100]");
        }

        // --- escapeLocalParamValue (via reflection) ---

        @Test
        void testEscapeLocalParamValueEscapesBackslashAndQuote() throws Exception {
                Method method = TurSolrQueryBuilder.class.getDeclaredMethod(
                                "escapeLocalParamValue", String.class);
                method.setAccessible(true);

                assertThat((String) method.invoke(null, "normal")).isEqualTo("normal");
                assertThat((String) method.invoke(null, "it's")).isEqualTo("it\\'s");
                assertThat((String) method.invoke(null, "back\\slash")).isEqualTo("back\\\\slash");
                assertThat((String) method.invoke(null, "both\\and'quote")).isEqualTo("both\\\\and\\'quote");
        }

        // --- addDoubleQuotesToValue (via reflection) ---

        @Test
        void testAddDoubleQuotesToValueWithKeyValue() throws Exception {
                Method method = TurSolrQueryBuilder.class.getDeclaredMethod(
                                "addDoubleQuotesToValue", String.class);
                method.setAccessible(true);

                assertThat((String) method.invoke(null, "category:books")).isEqualTo("category:\"books\"");
        }

        @Test
        void testAddDoubleQuotesToValueWithoutColon() throws Exception {
                Method method = TurSolrQueryBuilder.class.getDeclaredMethod(
                                "addDoubleQuotesToValue", String.class);
                method.setAccessible(true);

                assertThat((String) method.invoke(null, "plainvalue")).isEqualTo("\"plainvalue\"");
        }

        // --- queryWithoutExpression / withoutExpression (via reflection) ---

        @Test
        void testWithoutExpression() throws Exception {
                Method method = TurSolrQueryBuilder.class.getDeclaredMethod(
                                "withoutExpression", String.class);
                method.setAccessible(true);

                assertThat((boolean) method.invoke(null, "simple")).isTrue();
                assertThat((boolean) method.invoke(null, "[1 TO 10]")).isFalse();
                assertThat((boolean) method.invoke(null, "(nested)")).isFalse();
                assertThat((boolean) method.invoke(null, "wild*")).isFalse();
                assertThat((boolean) method.invoke(null, "normal text")).isTrue();
        }

        @Test
        void testQueryWithoutExpression() throws Exception {
                Method method = TurSolrQueryBuilder.class.getDeclaredMethod(
                                "queryWithoutExpression", String.class);
                method.setAccessible(true);

                assertThat((boolean) method.invoke(null, "field:value")).isTrue();
                assertThat((boolean) method.invoke(null, "(field:value)")).isFalse();
                assertThat((boolean) method.invoke(null, "field:[1 TO 10]")).isFalse();
                assertThat((boolean) method.invoke(null, "field:wild*")).isFalse();
        }

        // --- betweenSpaces (via reflection) ---

        @Test
        void testBetweenSpaces() throws Exception {
                Method method = TurSolrQueryBuilder.class.getDeclaredMethod(
                                "betweenSpaces", String.class);
                method.setAccessible(true);

                assertThat((String) method.invoke(null, "AND")).isEqualTo(" AND ");
                assertThat((String) method.invoke(null, "OR")).isEqualTo(" OR ");
        }

        // --- getCustomFacetLocalParams (via reflection) ---

        @Test
        void testGetCustomFacetLocalParamsWithExclude() throws Exception {
                Method method = TurSolrQueryBuilder.class.getDeclaredMethod(
                                "getCustomFacetLocalParams", String.class, String.class, boolean.class);
                method.setAccessible(true);

                String result = (String) method.invoke(null, "price_range", "0-100", true);
                assertThat(result)
                                .contains("key='price_range::0-100'")
                                .contains("ex=_all_");
        }

        @Test
        void testGetCustomFacetLocalParamsWithoutExclude() throws Exception {
                Method method = TurSolrQueryBuilder.class.getDeclaredMethod(
                                "getCustomFacetLocalParams", String.class, String.class, boolean.class);
                method.setAccessible(true);

                String result = (String) method.invoke(null, "price_range", "0-100", false);
                assertThat(result)
                                .contains("key='price_range::0-100'")
                                .doesNotContain("ex=_all_");
        }

        // --- isFacetTypeDefault (via reflection) ---

        @Test
        void testIsFacetTypeDefault() throws Exception {
                Method method = TurSolrQueryBuilder.class.getDeclaredMethod(
                                "isFacetTypeDefault", TurSNSiteFacetFieldEnum.class);
                method.setAccessible(true);

                assertThat((boolean) method.invoke(null, TurSNSiteFacetFieldEnum.DEFAULT)).isTrue();
                assertThat((boolean) method.invoke(null, (Object) null)).isTrue();
                assertThat((boolean) method.invoke(null, TurSNSiteFacetFieldEnum.AND)).isFalse();
                assertThat((boolean) method.invoke(null, TurSNSiteFacetFieldEnum.OR)).isFalse();
        }

        // --- operatorIsNotEmpty (via reflection) ---

        @Test
        void testOperatorIsNotEmpty() throws Exception {
                Method method = TurSolrQueryBuilder.class.getDeclaredMethod(
                                "operatorIsNotEmpty", TurSNFilterQueryOperator.class);
                method.setAccessible(true);

                assertThat((boolean) method.invoke(null, TurSNFilterQueryOperator.AND)).isTrue();
                assertThat((boolean) method.invoke(null, TurSNFilterQueryOperator.OR)).isTrue();
                assertThat((boolean) method.invoke(null, TurSNFilterQueryOperator.NONE)).isFalse();
                assertThat((boolean) method.invoke(null, (Object) null)).isFalse();
        }

        // --- getFacetTypeFromSite / getFacetItemTypeFromSite (via reflection) ---

        @Test
        void testGetFacetTypeFromSite() throws Exception {
                Method method = TurSolrQueryBuilder.class.getDeclaredMethod(
                                "getFacetTypeFromSite", TurSNSite.class);
                method.setAccessible(true);

                TurSNSite orSite = new TurSNSite();
                orSite.setFacetType(TurSNSiteFacetFieldEnum.OR);
                assertThat(method.invoke(null, orSite)).isEqualTo(TurSNSiteFacetFieldEnum.OR);

                TurSNSite andSite = new TurSNSite();
                andSite.setFacetType(TurSNSiteFacetFieldEnum.AND);
                assertThat(method.invoke(null, andSite)).isEqualTo(TurSNSiteFacetFieldEnum.AND);

                TurSNSite defaultSite = new TurSNSite();
                defaultSite.setFacetType(TurSNSiteFacetFieldEnum.DEFAULT);
                assertThat(method.invoke(null, defaultSite)).isEqualTo(TurSNSiteFacetFieldEnum.AND);
        }

        @Test
        void testGetFacetItemTypeFromSite() throws Exception {
                Method method = TurSolrQueryBuilder.class.getDeclaredMethod(
                                "getFacetItemTypeFromSite", TurSNSite.class);
                method.setAccessible(true);

                TurSNSite orSite = new TurSNSite();
                orSite.setFacetItemType(TurSNSiteFacetFieldEnum.OR);
                assertThat(method.invoke(null, orSite)).isEqualTo(TurSNSiteFacetFieldEnum.OR);

                TurSNSite andSite = new TurSNSite();
                andSite.setFacetItemType(TurSNSiteFacetFieldEnum.AND);
                assertThat(method.invoke(null, andSite)).isEqualTo(TurSNSiteFacetFieldEnum.AND);

                TurSNSite defaultSite = new TurSNSite();
                defaultSite.setFacetItemType(TurSNSiteFacetFieldEnum.DEFAULT);
                assertThat(method.invoke(null, defaultSite)).isEqualTo(TurSNSiteFacetFieldEnum.AND);
        }

        // --- facetSortIsEmptyOrCount (via reflection) ---

        @Test
        void testFacetSortIsEmptyOrCount() throws Exception {
                Method method = TurSolrQueryBuilder.class.getDeclaredMethod(
                                "facetSortIsEmptyOrCount", TurSNSite.class);
                method.setAccessible(true);

                TurSNSite nullSort = new TurSNSite();
                nullSort.setFacetSort(null);
                assertThat((boolean) method.invoke(null, nullSort)).isTrue();

                TurSNSite countSort = new TurSNSite();
                countSort.setFacetSort(com.viglet.turing.persistence.model.sn.TurSNSiteFacetSortEnum.COUNT);
                assertThat((boolean) method.invoke(null, countSort)).isTrue();

                TurSNSite alphaSort = new TurSNSite();
                alphaSort.setFacetSort(com.viglet.turing.persistence.model.sn.TurSNSiteFacetSortEnum.ALPHABETICAL);
                assertThat((boolean) method.invoke(null, alphaSort)).isFalse();
        }

        // --- isNerOrThesaurus (via reflection) ---

        @Test
        void testIsNerOrThesaurus() throws Exception {
                Method method = TurSolrQueryBuilder.class.getDeclaredMethod(
                                "isNerOrThesaurus", TurSNFieldType.class);
                method.setAccessible(true);

                assertThat((boolean) method.invoke(null, TurSNFieldType.NER)).isTrue();
                assertThat((boolean) method.invoke(null, TurSNFieldType.THESAURUS)).isTrue();
                assertThat((boolean) method.invoke(null, TurSNFieldType.SE)).isFalse();
        }

        // --- isDateRangeFacet (via reflection) ---

        @Test
        void testIsDateRangeFacet() throws Exception {
                Method method = TurSolrQueryBuilder.class.getDeclaredMethod(
                                "isDateRangeFacet", TurSNSiteFieldExt.class);
                method.setAccessible(true);

                TurSNSiteFieldExt dateRangeField = TurSNSiteFieldExt.builder()
                                .type(com.viglet.turing.commons.se.field.TurSEFieldType.DATE)
                                .facetRange(TurSNSiteFacetRangeEnum.MONTH)
                                .build();
                assertThat((boolean) method.invoke(null, dateRangeField)).isTrue();

                TurSNSiteFieldExt dateDisabledField = TurSNSiteFieldExt.builder()
                                .type(com.viglet.turing.commons.se.field.TurSEFieldType.DATE)
                                .facetRange(TurSNSiteFacetRangeEnum.DISABLED)
                                .build();
                assertThat((boolean) method.invoke(null, dateDisabledField)).isFalse();

                TurSNSiteFieldExt dateNullRange = TurSNSiteFieldExt.builder()
                                .type(com.viglet.turing.commons.se.field.TurSEFieldType.DATE)
                                .facetRange(null)
                                .build();
                assertThat((boolean) method.invoke(null, dateNullRange)).isFalse();

                TurSNSiteFieldExt stringField = TurSNSiteFieldExt.builder()
                                .type(com.viglet.turing.commons.se.field.TurSEFieldType.STRING)
                                .facetRange(TurSNSiteFacetRangeEnum.MONTH)
                                .build();
                assertThat((boolean) method.invoke(null, stringField)).isFalse();
        }

        // --- isFacetEnabled (via reflection) ---

        @Test
        void testIsFacetEnabled() throws Exception {
                Method method = TurSolrQueryBuilder.class.getDeclaredMethod(
                                "isFacetEnabled", TurSNSiteFieldExt.class);
                method.setAccessible(true);

                TurSNSiteFieldExt enabled = TurSNSiteFieldExt.builder().facet(1).build();
                assertThat((boolean) method.invoke(null, enabled)).isTrue();

                TurSNSiteFieldExt disabled = TurSNSiteFieldExt.builder().facet(0).build();
                assertThat((boolean) method.invoke(null, disabled)).isFalse();
        }

        // --- getFaceTypeFromOperator (via reflection) ---

        @Test
        void testGetFaceTypeFromOperator() throws Exception {
                Method method = TurSolrQueryBuilder.class.getDeclaredMethod(
                                "getFaceTypeFromOperator", TurSNFilterQueryOperator.class);
                method.setAccessible(true);

                assertThat(method.invoke(null, TurSNFilterQueryOperator.OR))
                                .isEqualTo(TurSNSiteFacetFieldEnum.OR);
                assertThat(method.invoke(null, TurSNFilterQueryOperator.AND))
                                .isEqualTo(TurSNSiteFacetFieldEnum.AND);
        }
}
