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

package com.viglet.turing.sn.nl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.api.exception.TurNotFoundException;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.sn.dsl.TurDslQueryRequest;
import com.viglet.turing.sn.dsl.TurDslSearchResponse;
import com.viglet.turing.sn.dsl.TurDslSearchService;
import com.viglet.turing.sn.dsl.eval.TurNLFacetParser;

import tools.jackson.databind.json.JsonMapper;

/**
 * T1085 — a description becomes the q and fq of the site's own search, every value
 * spelled as the site holds it.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurSNNLFilterServiceTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final String TEXT = "trabalho de meio periodo em sao paulo";

    @Mock
    private TurSNSiteRepository turSNSiteRepository;
    @Mock
    private TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
    @Mock
    private TurSNSiteLocaleRepository turSNSiteLocaleRepository;
    @Mock
    private TurNLFacetParser parser;
    @Mock
    private TurDslSearchService dslSearchService;
    @InjectMocks
    private TurSNNLFilterService service;

    private TurSNSite site;

    @BeforeEach
    void setUp() {
        site = new TurSNSite();
        site.setName("vagas");
    }

    private static TurSNSiteFieldExt field(String name, TurSEFieldType type, boolean facet) {
        TurSNSiteFieldExt ext = new TurSNSiteFieldExt();
        ext.setName(name);
        ext.setType(type);
        ext.setFacet(facet ? 1 : 0);
        return ext;
    }

    private void siteWithFields() {
        when(turSNSiteRepository.findByNameIgnoreCase("vagas")).thenReturn(Optional.of(site));
        when(parser.isAvailable()).thenReturn(true);
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1)).thenReturn(List.of(
                field("city", TurSEFieldType.STRING, true),
                field("contract", TurSEFieldType.STRING, true),
                field("salary", TurSEFieldType.FLOAT, true)));
    }

    private void parserAnswers(String json) {
        when(parser.parse(any())).thenReturn(MAPPER.readValue(json, TurDslQueryRequest.class));
    }

    private void siteHolds(Map<String, List<String>> bucketsByAgg) {
        Map<String, TurDslSearchResponse.AggregationResult> aggs = new java.util.LinkedHashMap<>();
        bucketsByAgg.forEach((agg, keys) -> aggs.put(agg, new TurDslSearchResponse.AggregationResult(
                keys.stream().map(k -> new TurDslSearchResponse.Bucket(k, 3)).toList())));
        when(dslSearchService.search(eq("vagas"), eq("pt_BR"), any()))
                .thenReturn(Optional.of(new TurDslSearchResponse(1, false, null, aggs)));
    }

    @Test
    @DisplayName("A value without accents is spelled as the site holds it, read from the words that named it")
    void groundsAValueInTheSiteSpelling() {
        siteWithFields();
        parserAnswers("""
                {"query":{"bool":{
                  "filter":[{"term":{"city":"sao paulo"}}],
                  "must":[{"match":{"_text_":"trabalho de meio periodo"}}]}}}""");
        siteHolds(Map.of("f0", List.of("São Paulo", "Campinas")));

        TurSNNLFilters result = service.parse("vagas", "pt_BR", TEXT);

        assertThat(result.available()).isTrue();
        assertThat(result.q()).isEqualTo("trabalho de meio periodo");
        assertThat(result.fq()).containsExactly("city:São Paulo");
        assertThat(result.filters()).singleElement().satisfies(filter -> {
            assertThat(filter.values()).containsExactly("São Paulo");
            assertThat(filter.sourceWords()).containsExactly("sao paulo");
            assertThat(filter.confidence()).isEqualTo(TurSNNLFilterService.LITERAL);
            assertThat(filter.grounded()).isTrue();
        });
        assertThat(result.dropped()).isEmpty();
    }

    @Test
    @DisplayName("A value the site does not hold, or a field it does not have, is dropped with the reason")
    void dropsWhatTheSiteDoesNotHold() {
        siteWithFields();
        parserAnswers("""
                {"query":{"bool":{"filter":[
                  {"terms":{"city":["sao paulo","Atlantis"]}},
                  {"term":{"metro":"perto"}}]}}}""");
        siteHolds(Map.of("f0", List.of("São Paulo")));

        TurSNNLFilters result = service.parse("vagas", "pt_BR", TEXT);

        assertThat(result.fq()).containsExactly("city:São Paulo");
        assertThat(result.dropped()).containsExactlyInAnyOrder(
                new TurSNNLFilters.Dropped("city", "Atlantis", TurSNNLFilterService.NOT_IN_SITE),
                new TurSNNLFilters.Dropped("metro", "perto", TurSNNLFilterService.UNKNOWN_FIELD));
    }

    @Test
    @DisplayName("A value the parser inferred, absent from the text, is kept with a lower confidence")
    void inferredValueHasLowerConfidence() {
        siteWithFields();
        parserAnswers("""
                {"query":{"bool":{"filter":[{"term":{"contract":"clt"}}]}}}""");
        siteHolds(Map.of("f0", List.of("CLT", "PJ")));

        TurSNNLFilters result = service.parse("vagas", "pt_BR", "vaga com carteira assinada");

        assertThat(result.fq()).containsExactly("contract:CLT");
        assertThat(result.filters().get(0).confidence()).isEqualTo(TurSNNLFilterService.INFERRED);
        assertThat(result.filters().get(0).sourceWords()).isEmpty();
        assertThat(result.q()).isEqualTo("vaga com carteira assinada");
    }

    @Test
    @DisplayName("A range becomes a range filter query, open where the request names no bound")
    void rangeBecomesARangeFilterQuery() {
        siteWithFields();
        parserAnswers("""
                {"query":{"bool":{"filter":[{"range":{"salary":{"gte":3000}}}]}}}""");

        TurSNNLFilters result = service.parse("vagas", "pt_BR", "salario acima de 3000 reais");

        assertThat(result.fq()).containsExactly("salary:[3000 TO *]");
        assertThat(result.filters().get(0).isRange()).isTrue();
        assertThat(result.filters().get(0).sourceWords()).containsExactly("3000");
        assertThat(result.q()).isEqualTo("salario acima de reais");
        verify(dslSearchService, never()).search(any(), any(), any());
    }

    @Test
    @DisplayName("When the engine lists no values, the parsed values pass unchecked and say so")
    void valuesPassUncheckedWhenTheEngineListsNone() {
        siteWithFields();
        parserAnswers("""
                {"query":{"bool":{"filter":[{"term":{"city":"sao paulo"}}]}}}""");
        when(dslSearchService.search(eq("vagas"), eq("pt_BR"), any())).thenReturn(Optional.empty());

        TurSNNLFilters result = service.parse("vagas", "pt_BR", TEXT);

        assertThat(result.fq()).containsExactly("city:sao paulo");
        assertThat(result.filters().get(0).grounded()).isFalse();
        assertThat(result.filters().get(0).confidence()).isEqualTo(TurSNNLFilterService.UNCHECKED);
        assertThat(result.q()).isEqualTo("trabalho de meio periodo em");
    }

    @Test
    @DisplayName("Without a locale, the site's first locale parses and searches")
    void defaultsToTheSitesFirstLocale() {
        siteWithFields();
        TurSNSiteLocale ptBr = new TurSNSiteLocale();
        ptBr.setLanguage(Locale.of("pt", "BR"));
        when(turSNSiteLocaleRepository.findByTurSNSiteOrderByPositionAsc(site)).thenReturn(List.of(ptBr));
        parserAnswers("""
                {"query":{"bool":{"filter":[{"term":{"city":"sao paulo"}}]}}}""");
        siteHolds(Map.of("f0", List.of("São Paulo")));

        service.parse("vagas", null, TEXT);

        ArgumentCaptor<TurNLFacetParser.ParseRequest> request = ArgumentCaptor
                .forClass(TurNLFacetParser.ParseRequest.class);
        verify(parser).parse(request.capture());
        assertThat(request.getValue().locale()).isEqualTo("pt_BR");
    }

    @Test
    @DisplayName("No LLM means nothing is filtered and the text stays the query")
    void unavailableWithoutAParser() {
        when(turSNSiteRepository.findByNameIgnoreCase("vagas")).thenReturn(Optional.of(site));
        when(parser.isAvailable()).thenReturn(false);

        TurSNNLFilters result = service.parse("vagas", "pt_BR", TEXT);

        assertThat(result.available()).isFalse();
        assertThat(result.q()).isEqualTo(TEXT);
        assertThat(result.fq()).isEmpty();
    }

    @Test
    @DisplayName("A blank text is refused, and an unknown site is not found")
    void refusesBlankTextAndUnknownSite() {
        assertThatThrownBy(() -> service.parse("vagas", null, " "))
                .isInstanceOf(IllegalArgumentException.class);
        when(turSNSiteRepository.findByNameIgnoreCase("nope")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.parse("nope", null, TEXT))
                .isInstanceOf(TurNotFoundException.class);
    }

    @Test
    @DisplayName("A field that is not a facet has no value list, so its value passes unchecked without a search")
    void nonFacetFieldPassesUnchecked() {
        when(turSNSiteRepository.findByNameIgnoreCase("vagas")).thenReturn(Optional.of(site));
        when(parser.isAvailable()).thenReturn(true);
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(List.of(field("company", TurSEFieldType.STRING, false)));
        parserAnswers("""
                {"query":{"bool":{"filter":[{"term":{"company":"acme"}}]}}}""");

        TurSNNLFilters result = service.parse("vagas", "pt_BR", "vagas na acme");

        assertThat(result.fq()).containsExactly("company:acme");
        assertThat(result.filters().get(0).grounded()).isFalse();
        verify(dslSearchService, never()).search(any(), any(), any());
    }

    @Test
    @DisplayName("Grounded filters go back to a query: terms, ranges as numbers, text fields as matches, exclusions kept")
    void groundedFiltersGoBackToAQuery() {
        var schema = List.of(
                new com.viglet.turing.sn.dsl.eval.TurNLFacetField("city", TurSEFieldType.STRING, true, null),
                new com.viglet.turing.sn.dsl.eval.TurNLFacetField("title", TurSEFieldType.TEXT, false, null));
        TurSNNLFilters filters = new TurSNNLFilters(true, "t", "estagio", List.of(
                new TurSNNLFilters.Filter("city", List.of("São Paulo", "Campinas"), null, null, List.of(), 1, true),
                new TurSNNLFilters.Filter("title", List.of("analista"), null, null, List.of(), 1, false),
                new TurSNNLFilters.Filter("salary", List.of(), "3000", null, List.of(), 1, false)),
                List.of(), List.of());
        var exclusion = new com.viglet.turing.sn.dsl.TurDslQuery.Term("contract", "PJ");
        var parsed = new com.viglet.turing.sn.dsl.TurDslQuery.Bool(List.of(), List.of(), List.of(exclusion),
                List.of(), null);

        var query = (com.viglet.turing.sn.dsl.TurDslQuery.Bool) TurSNNLFilterService.toQuery(filters, schema,
                parsed);

        assertThat(query.filter()).containsExactly(
                new com.viglet.turing.sn.dsl.TurDslQuery.Terms("city", List.of("São Paulo", "Campinas")),
                new com.viglet.turing.sn.dsl.TurDslQuery.Range("salary", 3000L, null, null, null));
        assertThat(query.must()).containsExactly(
                new com.viglet.turing.sn.dsl.TurDslQuery.Match("title", "analista", null),
                new com.viglet.turing.sn.dsl.TurDslQuery.Match(TurSNNLFilterService.TEXT_FIELD, "estagio", null));
        assertThat(query.mustNot()).containsExactly(exclusion);
    }

    @Test
    @DisplayName("Source words match on word boundaries, without case or accents, as the text spells them")
    void sourceWordsKeepTheTextSpelling() {
        assertThat(TurSNNLFilterService.sourceWords("Vagas em SÃO PAULO hoje", "sao paulo"))
                .contains("SÃO PAULO");
        assertThat(TurSNNLFilterService.sourceWords("salvador", "salva")).isEmpty();
        assertThat(TurSNNLFilterService.canonical("CAMPINAS", java.util.Set.of("Campinas")))
                .isEqualTo("Campinas");
    }
}
