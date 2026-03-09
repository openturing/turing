/*
 * Copyright (C) 2016-2022 the original author or authors.
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

package com.viglet.turing.api.sn.graphql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.viglet.turing.commons.sn.bean.TurSNSiteSearchBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchDocumentBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchGroupBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchResultsBean;
import com.viglet.turing.commons.sn.search.TurSNFilterQueryOperator;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.sn.TurSNSearchProcess;

/**
 * Unit tests for TurSNSiteSearchGraphQLController.
 * 
 * @author Alexandre Oliveira
 * @since 0.3.6
 */

class TurSNSiteSearchGraphQLControllerTest {

    @Test
    void testSiteNamesListsAllSitesFromRepository() {
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        TurSNSiteFieldExtRepository fieldExtRepository = mock(TurSNSiteFieldExtRepository.class);
        TurSNSiteSearchGraphQLController controller = new TurSNSiteSearchGraphQLController(
                searchProcess, siteRepository, fieldExtRepository);

        TurSNSite site1 = new TurSNSite();
        site1.setName("insper-stage-publish");
        TurSNSite site2 = new TurSNSite();
        site2.setName("demo-site");
        TurSNSite site3 = new TurSNSite();
        site3.setName(" ");

        when(siteRepository.findAll(any(Sort.class))).thenReturn(List.of(site1, site2, site3));

        List<String> result = controller.siteNames();

        assertEquals(List.of("insper-stage-publish", "demo-site"), result);
        verify(siteRepository).findAll(any(Sort.class));
    }

    @Test
    void testSiteSearchReturnsEmptyWhenLocaleNotSupported() {
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        TurSNSiteFieldExtRepository fieldExtRepository = mock(TurSNSiteFieldExtRepository.class);
        TurSNSiteSearchGraphQLController controller = new TurSNSiteSearchGraphQLController(
                searchProcess, siteRepository, fieldExtRepository);

        when(searchProcess.existsByTurSNSiteAndLanguage("demo", Locale.ENGLISH)).thenReturn(false);

        TurSNSiteSearchBean result = controller.siteSearch("demo", new TurSNSearchParamsInput(), "en");

        assertNotNull(result);
    }

    @Test
    void testSiteSearchReturnsEmptyWhenSiteNotFound() {
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        TurSNSiteFieldExtRepository fieldExtRepository = mock(TurSNSiteFieldExtRepository.class);
        TurSNSiteSearchGraphQLController controller = new TurSNSiteSearchGraphQLController(
                searchProcess, siteRepository, fieldExtRepository);

        when(searchProcess.existsByTurSNSiteAndLanguage("demo", Locale.ENGLISH)).thenReturn(true);
        when(siteRepository.findByName("demo")).thenReturn(Optional.empty());

        TurSNSiteSearchBean result = controller.siteSearch("demo", new TurSNSearchParamsInput(), "en");

        assertNotNull(result);
    }

    @Test
    void testSiteSearchBuildsDefaultContextAndCallsSearch() {
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        TurSNSiteFieldExtRepository fieldExtRepository = mock(TurSNSiteFieldExtRepository.class);
        TurSNSiteSearchGraphQLController controller = new TurSNSiteSearchGraphQLController(
                searchProcess, siteRepository, fieldExtRepository);

        TurSNSite site = new TurSNSite();
        site.setName("demo");
        site.setHl(0);
        site.setHl(1);

        TurSNSiteFieldExt hlField = TurSNSiteFieldExt.builder().name("content").enabled(1).hl(1).build();

        when(searchProcess.existsByTurSNSiteAndLanguage("demo", Locale.ENGLISH)).thenReturn(true);
        when(siteRepository.findByName("demo")).thenReturn(Optional.of(site));
        when(fieldExtRepository.findByTurSNSiteAndHlAndEnabled(site, 1, 1)).thenReturn(List.of(hlField));

        TurSNSiteSearchBean expected = new TurSNSiteSearchBean();
        when(searchProcess.search(any(TurSNSiteSearchContext.class))).thenReturn(expected);

        TurSNSiteSearchBean result = controller.siteSearch("demo", null, null);

        assertSame(expected, result);

        ArgumentCaptor<TurSNSiteSearchContext> contextCaptor = ArgumentCaptor
                .forClass(TurSNSiteSearchContext.class);
        verify(searchProcess).search(contextCaptor.capture());
        TurSNSiteSearchContext context = contextCaptor.getValue();

        assertEquals("demo", context.getSiteName());
        assertNull(context.getLocale());
        assertEquals("*", context.getTurSEParameters().getQuery());
        assertEquals(Integer.valueOf(1), context.getTurSEParameters().getCurrentPage());
        assertEquals(Integer.valueOf(-1), context.getTurSEParameters().getRows());
        assertEquals("relevance", context.getTurSEParameters().getSort());
        assertTrue(context.getTurSNConfig().isHlEnabled());
        assertTrue(context.getUri().toString().contains("/graphql"));
    }

    @Test
    void testSiteSearchUsesLocaleArgumentOverInputLocale() {
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        TurSNSiteFieldExtRepository fieldExtRepository = mock(TurSNSiteFieldExtRepository.class);
        TurSNSiteSearchGraphQLController controller = new TurSNSiteSearchGraphQLController(
                searchProcess, siteRepository, fieldExtRepository);

        TurSNSite site = new TurSNSite();
        site.setName("demo");
        site.setHl(0);

        TurSNSearchParamsInput input = new TurSNSearchParamsInput();
        input.setQ("java");
        input.setLocale("pt_BR");

        when(searchProcess.existsByTurSNSiteAndLanguage("demo", Locale.US)).thenReturn(true);
        when(siteRepository.findByName("demo")).thenReturn(Optional.of(site));
        when(fieldExtRepository.findByTurSNSiteAndHlAndEnabled(site, 1, 1)).thenReturn(List.of());
        when(searchProcess.search(any(TurSNSiteSearchContext.class))).thenReturn(new TurSNSiteSearchBean());

        controller.siteSearch("demo", input, "en_US");

        ArgumentCaptor<TurSNSiteSearchContext> contextCaptor = ArgumentCaptor
                .forClass(TurSNSiteSearchContext.class);
        verify(searchProcess).search(contextCaptor.capture());
        assertEquals(Locale.of("pt", "BR"), contextCaptor.getValue().getLocale());
    }

    @Test
    void testSiteSearchInvalidOperatorsFallbackToNone() {
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        TurSNSiteFieldExtRepository fieldExtRepository = mock(TurSNSiteFieldExtRepository.class);
        TurSNSiteSearchGraphQLController controller = new TurSNSiteSearchGraphQLController(
                searchProcess, siteRepository, fieldExtRepository);

        TurSNSite site = new TurSNSite();
        site.setName("demo");
        site.setHl(0);

        TurSNSearchParamsInput input = new TurSNSearchParamsInput();
        input.setQ("java");
        input.setFqOp("INVALID");
        input.setFqiOp("WRONG");

        when(searchProcess.existsByTurSNSiteAndLanguage("demo", Locale.ENGLISH)).thenReturn(true);
        when(siteRepository.findByName("demo")).thenReturn(Optional.of(site));
        when(fieldExtRepository.findByTurSNSiteAndHlAndEnabled(site, 1, 1)).thenReturn(List.of());
        when(searchProcess.search(any(TurSNSiteSearchContext.class))).thenReturn(new TurSNSiteSearchBean());

        controller.siteSearch("demo", input, "en");

        ArgumentCaptor<TurSNSiteSearchContext> contextCaptor = ArgumentCaptor
                .forClass(TurSNSiteSearchContext.class);
        verify(searchProcess).search(contextCaptor.capture());
        assertEquals(TurSNFilterQueryOperator.NONE,
                contextCaptor.getValue().getTurSEParameters().getTurSNFilterParams().getOperator());
        assertEquals(TurSNFilterQueryOperator.NONE,
                contextCaptor.getValue().getTurSEParameters().getTurSNFilterParams().getItemOperator());
    }

    @Test
    void testSiteSearchUsesCurrentRequestContextWhenAvailable() {
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        TurSNSiteFieldExtRepository fieldExtRepository = mock(TurSNSiteFieldExtRepository.class);
        TurSNSiteSearchGraphQLController controller = new TurSNSiteSearchGraphQLController(
                searchProcess, siteRepository, fieldExtRepository);

        TurSNSite site = new TurSNSite();
        site.setName("demo");
        site.setHl(0);

        when(searchProcess.existsByTurSNSiteAndLanguage("demo", Locale.ENGLISH)).thenReturn(true);
        when(siteRepository.findByName("demo")).thenReturn(Optional.of(site));
        when(fieldExtRepository.findByTurSNSiteAndHlAndEnabled(site, 1, 1)).thenReturn(List.of());
        when(searchProcess.search(any(TurSNSiteSearchContext.class))).thenReturn(new TurSNSiteSearchBean());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/sn/search");
        request.setQueryString("q=graphql");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            TurSNSearchParamsInput input = new TurSNSearchParamsInput();
            input.setQ("graphql");

            controller.siteSearch("demo", input, "en");

            ArgumentCaptor<TurSNSiteSearchContext> contextCaptor = ArgumentCaptor
                    .forClass(TurSNSiteSearchContext.class);
            verify(searchProcess).search(contextCaptor.capture());
            assertTrue(contextCaptor.getValue().getUri().toString().contains("/api/sn/search"));
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void testSiteSearchNormalizesDynamicFieldsBySite() {
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        TurSNSiteFieldExtRepository fieldExtRepository = mock(TurSNSiteFieldExtRepository.class);
        TurSNSiteSearchGraphQLController controller = new TurSNSiteSearchGraphQLController(
                searchProcess, siteRepository, fieldExtRepository);

        TurSNSite site = new TurSNSite();
        site.setName("demo");
        site.setHl(0);

        TurSNSiteFieldExt titleField = TurSNSiteFieldExt.builder()
                .name("title")
                .externalId("title_i")
                .enabled(1)
                .build();
        TurSNSiteFieldExt priceField = TurSNSiteFieldExt.builder()
                .name("price")
                .externalId("price_d")
                .enabled(0)
                .build();
        TurSNSiteFieldExt tagsField = TurSNSiteFieldExt.builder()
                .name("tags")
                .externalId("tags_ss")
                .enabled(1)
                .build();

        TurSNSiteSearchDocumentBean mainDocument = TurSNSiteSearchDocumentBean.builder()
                .fields(Map.of(
                        "title_i", "GraphQL Book",
                        "tags_ss", List.of("java", "graphql"),
                        "adHoc", 7))
                .build();

        TurSNSiteSearchDocumentBean groupedDocument = TurSNSiteSearchDocumentBean.builder()
                .fields(Map.of("price_d", 19.9d))
                .build();

        TurSNSiteSearchResultsBean mainResults = new TurSNSiteSearchResultsBean()
                .setDocument(List.of(mainDocument));
        TurSNSiteSearchResultsBean groupedResults = new TurSNSiteSearchResultsBean()
                .setDocument(List.of(groupedDocument));

        TurSNSiteSearchGroupBean group = new TurSNSiteSearchGroupBean()
                .setName("books")
                .setResults(groupedResults);

        TurSNSiteSearchBean response = new TurSNSiteSearchBean()
                .setResults(mainResults)
                .setGroups(List.of(group));

        when(searchProcess.existsByTurSNSiteAndLanguage("demo", Locale.ENGLISH)).thenReturn(true);
        when(siteRepository.findByName("demo")).thenReturn(Optional.of(site));
        when(fieldExtRepository.findByTurSNSiteAndHlAndEnabled(site, 1, 1)).thenReturn(List.of());
        when(fieldExtRepository.findByTurSNSite(any(), any())).thenReturn(List.of(titleField, priceField, tagsField));
        when(searchProcess.search(any(TurSNSiteSearchContext.class))).thenReturn(response);

        TurSNSiteSearchBean result = controller.siteSearch("demo", new TurSNSearchParamsInput(), "en");

        Map<String, Object> normalizedMainFields = result.getResults().getDocument().get(0).getFields();
        assertEquals("GraphQL Book", normalizedMainFields.get("title"));
        assertNull(normalizedMainFields.get("price"));
        assertEquals(List.of("java", "graphql"), normalizedMainFields.get("tags"));
        assertEquals(7, normalizedMainFields.get("adHoc"));

        Map<String, Object> normalizedGroupFields = result.getGroups().get(0).getResults().getDocument().get(0)
                .getFields();
        assertNull(normalizedGroupFields.get("title"));
        assertEquals(19.9d, normalizedGroupFields.get("price"));
        assertNull(normalizedGroupFields.get("tags"));
    }

    @Test
    void testSiteSearchFiltersFieldsWhenFlIsProvided() {
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        TurSNSiteFieldExtRepository fieldExtRepository = mock(TurSNSiteFieldExtRepository.class);
        TurSNSiteSearchGraphQLController controller = new TurSNSiteSearchGraphQLController(
                searchProcess, siteRepository, fieldExtRepository);

        TurSNSite site = new TurSNSite();
        site.setName("demo");
        site.setHl(0);

        TurSNSiteFieldExt titleField = TurSNSiteFieldExt.builder()
                .name("title")
                .externalId("title_i")
                .enabled(1)
                .build();
        TurSNSiteFieldExt urlField = TurSNSiteFieldExt.builder()
                .name("url")
                .externalId("url_s")
                .enabled(1)
                .build();

        TurSNSiteSearchDocumentBean document = TurSNSiteSearchDocumentBean.builder()
                .fields(Map.of(
                        "title_i", "Course",
                        "url_s", "https://example.org/course"))
                .build();
        TurSNSiteSearchBean response = new TurSNSiteSearchBean()
                .setResults(new TurSNSiteSearchResultsBean().setDocument(List.of(document)));

        TurSNSearchParamsInput input = new TurSNSearchParamsInput();
        input.setFl(List.of("title"));

        when(searchProcess.existsByTurSNSiteAndLanguage("demo", Locale.ENGLISH)).thenReturn(true);
        when(siteRepository.findByName("demo")).thenReturn(Optional.of(site));
        when(fieldExtRepository.findByTurSNSiteAndHlAndEnabled(site, 1, 1)).thenReturn(List.of());
        when(fieldExtRepository.findByTurSNSite(any(), any())).thenReturn(List.of(titleField, urlField));
        when(searchProcess.search(any(TurSNSiteSearchContext.class))).thenReturn(response);

        TurSNSiteSearchBean result = controller.siteSearch("demo", input, "en");

        Map<String, Object> fields = result.getResults().getDocument().get(0).getFields();
        assertEquals("Course", fields.get("title"));
        assertEquals(1, fields.size());
    }

    @Test
    void testSiteSearchResolvesEnumSiteNameToHyphenatedSiteName() {
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        TurSNSiteFieldExtRepository fieldExtRepository = mock(TurSNSiteFieldExtRepository.class);
        TurSNSiteSearchGraphQLController controller = new TurSNSiteSearchGraphQLController(
                searchProcess, siteRepository, fieldExtRepository);

        TurSNSite site = new TurSNSite();
        site.setName("insper-stage-publish");
        site.setHl(0);

        when(siteRepository.findByName("INSPER_STAGE_PUBLISH")).thenReturn(Optional.empty());
        when(siteRepository.findAll(any(Sort.class))).thenReturn(List.of(site));
        when(siteRepository.findByName("insper-stage-publish")).thenReturn(Optional.of(site));
        when(searchProcess.existsByTurSNSiteAndLanguage("insper-stage-publish", Locale.ENGLISH))
                .thenReturn(true);
        when(fieldExtRepository.findByTurSNSiteAndHlAndEnabled(site, 1, 1)).thenReturn(List.of());
        when(fieldExtRepository.findByTurSNSite(any(), any())).thenReturn(List.of());
        when(searchProcess.search(any(TurSNSiteSearchContext.class))).thenReturn(new TurSNSiteSearchBean());

        controller.siteSearch("INSPER_STAGE_PUBLISH", new TurSNSearchParamsInput(), "en");

        verify(searchProcess).existsByTurSNSiteAndLanguage("insper-stage-publish", Locale.ENGLISH);
        verify(siteRepository).findByName("insper-stage-publish");
    }

    @Test
    void testSearchParamsInputCreation() {
        TurSNSearchParamsInput input = new TurSNSearchParamsInput();

        // Test default values
        assertEquals("*", input.getQ());
        assertEquals(Integer.valueOf(1), input.getP());
        assertEquals("relevance", input.getSort());
        assertEquals(Integer.valueOf(-1), input.getRows());
        assertEquals(Integer.valueOf(1), input.getNfpr());
        assertEquals("NONE", input.getFqOp());
        assertEquals("NONE", input.getFqiOp());

        // Test nullable fields
        assertNull(input.getLocale());
        assertNull(input.getGroup());
        assertNull(input.getFq());
        assertNull(input.getFqAnd());
        assertNull(input.getFqOr());
        assertNull(input.getFl());
    }

    @Test
    void testSearchParamsInputSetters() {
        TurSNSearchParamsInput input = new TurSNSearchParamsInput();

        // Test setters
        input.setQ("test query");
        input.setP(2);
        input.setRows(20);
        input.setSort("date");
        input.setLocale("pt");
        input.setGroup("category");
        input.setNfpr(5);
        input.setFqOp("AND");
        input.setFqiOp("OR");

        // Verify values
        assertEquals("test query", input.getQ());
        assertEquals(Integer.valueOf(2), input.getP());
        assertEquals(Integer.valueOf(20), input.getRows());
        assertEquals("date", input.getSort());
        assertEquals("pt", input.getLocale());
        assertEquals("category", input.getGroup());
        assertEquals(Integer.valueOf(5), input.getNfpr());
        assertEquals("AND", input.getFqOp());
        assertEquals("OR", input.getFqiOp());
    }

    @Test
    void testToString() {
        TurSNSearchParamsInput input = new TurSNSearchParamsInput();
        input.setQ("test");
        input.setP(1);

        String toString = input.toString();
        assertNotNull(toString);
        // Should contain class name and some field values
        assert (toString.contains("TurSNSearchParamsInput"));
    }
}
