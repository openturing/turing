/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.sn.bean.TurSNFilterParams;
import com.viglet.turing.commons.sn.search.TurSNFilterQueryOperator;
import com.viglet.turing.lucene.TurLuceneCustomFacets;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.sort.TurSNSiteCustomSortMissingEnum;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;

/**
 * T1106 — the Elasticsearch engine reads a request's facet filters the way the
 * Lucene engine does (T1077), and a custom sort level's missing placement.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.5
 */
class TurElasticsearchFilterQueriesTest {

    private static final Instant NOW = Instant.parse("2026-10-23T12:00:00Z");

    private static TurSNSiteFieldExt field(String name, TurSEFieldType type) {
        TurSNSiteFieldExt ext = new TurSNSiteFieldExt();
        ext.setName(name);
        ext.setType(type);
        return ext;
    }

    private static final Map<String, TurSNSiteFieldExt> FIELDS = Map.of(
            "sector", field("sector", TurSEFieldType.STRING),
            "salary", field("salary", TurSEFieldType.INT),
            "publishedAt", field("publishedAt", TurSEFieldType.DATE));

    private static Optional<Query> build(TurSNFilterParams params) {
        return TurElasticsearchFilterQueries.build(new TurSNSite(), params, FIELDS,
                (facet, label) -> Optional.empty(), NOW);
    }

    @Test
    void noSelectionIsNoFilter() {
        assertThat(build(TurSNFilterParams.builder().build())).isEmpty();
        assertThat(build(null)).isEmpty();
    }

    @Test
    void aStringSelectionMatchesTheKeywordSubFieldTheFacetCounts() {
        Query query = build(TurSNFilterParams.builder().defaultValues(List.of("sector:\"educacao\"")).build())
                .orElseThrow();

        Query term = query.bool().filter().getFirst();
        assertThat(term.term().field()).isEqualTo("sector.keyword");
        assertThat(term.term().value().stringValue()).isEqualTo("educacao");
    }

    @Test
    void twoValuesOfOneFieldIntersectByDefaultAndUnionUnderAnOrItemOperator() {
        TurSNFilterParams intersect = TurSNFilterParams.builder()
                .defaultValues(List.of("sector:educacao", "sector:saude")).build();
        TurSNFilterParams union = TurSNFilterParams.builder()
                .defaultValues(List.of("sector:educacao", "sector:saude"))
                .itemOperator(TurSNFilterQueryOperator.OR).build();

        assertThat(build(intersect).orElseThrow().bool().filter().getFirst().bool().filter()).hasSize(2);
        Query unionGroup = build(union).orElseThrow().bool().filter().getFirst();
        assertThat(unionGroup.bool().should()).hasSize(2);
        assertThat(unionGroup.bool().minimumShouldMatch()).isEqualTo("1");
    }

    @Test
    void aMultiSelectFieldUnionsItsValuesWithoutAnOperator() {
        TurSNSiteFieldExt sector = field("sector", TurSEFieldType.STRING);
        sector.setFacetItemType(TurSNSiteFacetFieldEnum.OR);

        Query query = TurElasticsearchFilterQueries.build(new TurSNSite(),
                TurSNFilterParams.builder().defaultValues(List.of("sector:educacao", "sector:saude")).build(),
                Map.of("sector", sector), (facet, label) -> Optional.empty(), NOW).orElseThrow();

        assertThat(query.bool().filter().getFirst().bool().should()).hasSize(2);
    }

    @Test
    void fqOrIsOrdWithTheIntersectedFilters() {
        Query query = build(TurSNFilterParams.builder()
                .defaultValues(List.of("sector:educacao"))
                .or(List.of("salary:5000")).build()).orElseThrow();

        // Solr's shape: (sector) OR (salary)
        assertThat(query.bool().should()).hasSize(2);
        assertThat(query.bool().minimumShouldMatch()).isEqualTo("1");
        assertThat(query.bool().should().getFirst().bool().filter().getFirst().term().field())
                .isEqualTo("sector.keyword");
        assertThat(query.bool().should().get(1).term().field()).isEqualTo("salary");
    }

    @Test
    void aDateRangeWithDateMathBecomesAnIsoRange() {
        Query query = build(TurSNFilterParams.builder()
                .defaultValues(List.of("publishedAt:[NOW-7DAYS TO *]")).build()).orElseThrow();

        var range = query.bool().filter().getFirst().range().untyped();
        assertThat(range.field()).isEqualTo("publishedAt");
        assertThat(range.gte().to(String.class)).isEqualTo("2026-10-16T12:00:00Z");
        assertThat(range.lt()).isNull();
        assertThat(range.lte()).isNull();
    }

    @Test
    void anExclusiveNumericBoundStaysExclusive() {
        var range = build(TurSNFilterParams.builder()
                .defaultValues(List.of("salary:{1000 TO 5000]")).build()).orElseThrow()
                .bool().filter().getFirst().range().untyped();

        assertThat(range.gt().to(String.class)).isEqualTo("1000");
        assertThat(range.lte().to(String.class)).isEqualTo("5000");
    }

    @Test
    void aCustomFacetSelectionStandsForItsItemRange() {
        Query query = TurElasticsearchFilterQueries.build(new TurSNSite(),
                TurSNFilterParams.builder().defaultValues(List.of("postedSince:Last 7 days")).build(), FIELDS,
                (facet, label) -> "postedSince".equals(facet) && "Last 7 days".equals(label)
                        ? Optional.of(new TurLuceneCustomFacets.ItemFilter("publishedAt", TurSEFieldType.DATE,
                                "[NOW-7DAYS TO *]"))
                        : Optional.empty(),
                NOW).orElseThrow();

        assertThat(query.bool().filter().getFirst().range().untyped().field()).isEqualTo("publishedAt");
    }

    @Test
    void aCustomSortLevelCarriesItsMissingPlacement() {
        var sort = TurElasticsearch.sortOption("expiresAt", TurSEFieldType.DATE, SortOrder.Asc,
                TurSNSiteCustomSortMissingEnum.LAST).field();

        assertThat(sort.field()).isEqualTo("expiresAt");
        assertThat(sort.order()).isEqualTo(SortOrder.Asc);
        assertThat(sort.missing().stringValue()).isEqualTo("_last");
    }

    @Test
    void aStringSortLevelSortsTheKeywordSubFieldAndKeepsTheDefaultPlacement() {
        var sort = TurElasticsearch.sortOption("title", TurSEFieldType.STRING, SortOrder.Desc, null).field();

        assertThat(sort.field()).isEqualTo("title.keyword");
        assertThat(sort.missing()).isNull();
    }
}
