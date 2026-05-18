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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.viglet.turing.commons.sn.bean.TurSNSiteSearchBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchDocumentBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchGroupBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchResultsBean;
import com.viglet.turing.commons.sn.search.TurSNFilterQueryOperator;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.domain.sn.TurSNSiteLocaleRepositoryPort;
import com.viglet.turing.domain.sn.TurSNSiteRepositoryPort;
import com.viglet.turing.domain.sn.TurSNSiteFieldExtDomain;
import com.viglet.turing.domain.sn.TurSNSiteFieldExtRepositoryPort;
import com.viglet.turing.sn.TurSNSearchProcess;

/**
 * Unit tests for TurSNSiteSearchGraphQLController.
 * 
 * @author Alexandre Oliveira
 * @since 0.3.6
 */

class TurSNSiteSearchGraphQLControllerTest {

    /** Minimal site domain record for tests that pass a site to siteSearch. */
    private static com.viglet.turing.domain.sn.TurSNSiteDomain siteDomain(String name, Integer hl) {
        return new com.viglet.turing.domain.sn.TurSNSiteDomain(name + "-id", name, null, null,
                null, null, null, null, null, null, hl, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null);
    }

    /** Minimal field-ext domain record carrying the two values the controller projects. */
    private static TurSNSiteFieldExtDomain fieldDomain(String name, String externalId) {
        return new TurSNSiteFieldExtDomain(
                "id-" + name, externalId, name, null, null, null, null, null, null,
                null, null, null, null, null, 0, 0, 0, 0, 1, 0, null, "site-id",
                java.util.Set.of(), java.util.Set.of());
    }

    @Test
    void testSiteNamesListsAllSitesFromRepository() {
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteRepositoryPort siteRepositoryPort = mock(TurSNSiteRepositoryPort.class);
        TurSNSiteFieldExtRepositoryPort fieldExtRepository = mock(TurSNSiteFieldExtRepositoryPort.class);
        TurSNSiteLocaleRepositoryPort localeRepositoryPort = mock(TurSNSiteLocaleRepositoryPort.class);
        TurSNSiteSearchGraphQLController controller = new TurSNSiteSearchGraphQLController(
                searchProcess, siteRepositoryPort, fieldExtRepository, localeRepositoryPort);

        // Names list (with the blank-name filter) is now resolved by the
        // port — adapter test pins the projection / filtering. Controller
        // test just asserts the controller delegates and forwards the list.
        when(siteRepositoryPort.findAllNamesOrderedByNameIgnoreCase())
                .thenReturn(List.of("education-stage-publish", "demo-site"));

        List<String> result = controller.siteNames();

        assertEquals(List.of("education-stage-publish", "demo-site"), result);
        verify(siteRepositoryPort).findAllNamesOrderedByNameIgnoreCase();
    }

    @Test
    void testSiteSearchReturnsEmptyWhenLocaleNotSupported() {
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteRepositoryPort siteRepositoryPort = mock(TurSNSiteRepositoryPort.class);
        TurSNSiteFieldExtRepositoryPort fieldExtRepository = mock(TurSNSiteFieldExtRepositoryPort.class);
        TurSNSiteLocaleRepositoryPort localeRepositoryPort = mock(TurSNSiteLocaleRepositoryPort.class);
        TurSNSiteSearchGraphQLController controller = new TurSNSiteSearchGraphQLController(
                searchProcess, siteRepositoryPort, fieldExtRepository, localeRepositoryPort);

        when(searchProcess.existsByTurSNSiteAndLanguage("demo", Locale.ENGLISH)).thenReturn(false);

        TurSNSiteSearchBean result = controller.siteSearch("demo", new TurSNSearchParamsInput(), "en");

        assertNotNull(result);
    }

    @Test
    void testSiteSearchReturnsEmptyWhenSiteNotFound() {
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteRepositoryPort siteRepositoryPort = mock(TurSNSiteRepositoryPort.class);
        TurSNSiteFieldExtRepositoryPort fieldExtRepository = mock(TurSNSiteFieldExtRepositoryPort.class);
        TurSNSiteLocaleRepositoryPort localeRepositoryPort = mock(TurSNSiteLocaleRepositoryPort.class);
        TurSNSiteSearchGraphQLController controller = new TurSNSiteSearchGraphQLController(
                searchProcess, siteRepositoryPort, fieldExtRepository, localeRepositoryPort);

        when(searchProcess.existsByTurSNSiteAndLanguage("demo", Locale.ENGLISH)).thenReturn(true);
        when(siteRepositoryPort.findByNameIgnoreCase("demo")).thenReturn(Optional.empty());

        TurSNSiteSearchBean result = controller.siteSearch("demo", new TurSNSearchParamsInput(), "en");

        assertNotNull(result);
    }

    @Test
    void testSiteSearchBuildsDefaultContextAndCallsSearch() {
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteRepositoryPort siteRepositoryPort = mock(TurSNSiteRepositoryPort.class);
        TurSNSiteFieldExtRepositoryPort fieldExtRepository = mock(TurSNSiteFieldExtRepositoryPort.class);
        TurSNSiteLocaleRepositoryPort localeRepositoryPort = mock(TurSNSiteLocaleRepositoryPort.class);
        TurSNSiteSearchGraphQLController controller = new TurSNSiteSearchGraphQLController(
                searchProcess, siteRepositoryPort, fieldExtRepository, localeRepositoryPort);

        com.viglet.turing.domain.sn.TurSNSiteDomain site = siteDomain("demo", 1);

        when(searchProcess.existsByTurSNSiteAndLanguage("demo", Locale.ENGLISH)).thenReturn(true);
        when(siteRepositoryPort.findByNameIgnoreCase("demo")).thenReturn(Optional.of(site));
        when(fieldExtRepository.existsBySnSiteIdAndHlAndEnabled(any(), eq(1), eq(1)))
                .thenReturn(true);

        TurSNSiteSearchBean expected = new TurSNSiteSearchBean();
        when(searchProcess.search(any(TurSNSiteSearchContext.class))).thenReturn(expected);

        MockHttpServletRequest mockRequest = new MockHttpServletRequest("POST", "/graphql");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(mockRequest));
        try {
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
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void testSiteSearchUsesLocaleArgumentOverInputLocale() {
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteRepositoryPort siteRepositoryPort = mock(TurSNSiteRepositoryPort.class);
        TurSNSiteFieldExtRepositoryPort fieldExtRepository = mock(TurSNSiteFieldExtRepositoryPort.class);
        TurSNSiteLocaleRepositoryPort localeRepositoryPort = mock(TurSNSiteLocaleRepositoryPort.class);
        TurSNSiteSearchGraphQLController controller = new TurSNSiteSearchGraphQLController(
                searchProcess, siteRepositoryPort, fieldExtRepository, localeRepositoryPort);

        com.viglet.turing.domain.sn.TurSNSiteDomain site = siteDomain("demo", 0);

        TurSNSearchParamsInput input = new TurSNSearchParamsInput();
        input.setQ("java");
        input.setLocale("pt_BR");

        when(searchProcess.existsByTurSNSiteAndLanguage("demo", Locale.US)).thenReturn(true);
        when(siteRepositoryPort.findByNameIgnoreCase("demo")).thenReturn(Optional.of(site));
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
        TurSNSiteRepositoryPort siteRepositoryPort = mock(TurSNSiteRepositoryPort.class);
        TurSNSiteFieldExtRepositoryPort fieldExtRepository = mock(TurSNSiteFieldExtRepositoryPort.class);
        TurSNSiteLocaleRepositoryPort localeRepositoryPort = mock(TurSNSiteLocaleRepositoryPort.class);
        TurSNSiteSearchGraphQLController controller = new TurSNSiteSearchGraphQLController(
                searchProcess, siteRepositoryPort, fieldExtRepository, localeRepositoryPort);

        com.viglet.turing.domain.sn.TurSNSiteDomain site = siteDomain("demo", 0);

        TurSNSearchParamsInput input = new TurSNSearchParamsInput();
        input.setQ("java");
        input.setFqOp("INVALID");
        input.setFqiOp("WRONG");

        when(searchProcess.existsByTurSNSiteAndLanguage("demo", Locale.ENGLISH)).thenReturn(true);
        when(siteRepositoryPort.findByNameIgnoreCase("demo")).thenReturn(Optional.of(site));
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
        TurSNSiteRepositoryPort siteRepositoryPort = mock(TurSNSiteRepositoryPort.class);
        TurSNSiteFieldExtRepositoryPort fieldExtRepository = mock(TurSNSiteFieldExtRepositoryPort.class);
        TurSNSiteLocaleRepositoryPort localeRepositoryPort = mock(TurSNSiteLocaleRepositoryPort.class);
        TurSNSiteSearchGraphQLController controller = new TurSNSiteSearchGraphQLController(
                searchProcess, siteRepositoryPort, fieldExtRepository, localeRepositoryPort);

        com.viglet.turing.domain.sn.TurSNSiteDomain site = siteDomain("demo", 0);

        when(searchProcess.existsByTurSNSiteAndLanguage("demo", Locale.ENGLISH)).thenReturn(true);
        when(siteRepositoryPort.findByNameIgnoreCase("demo")).thenReturn(Optional.of(site));
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
        TurSNSiteRepositoryPort siteRepositoryPort = mock(TurSNSiteRepositoryPort.class);
        TurSNSiteFieldExtRepositoryPort fieldExtRepository = mock(TurSNSiteFieldExtRepositoryPort.class);
        TurSNSiteLocaleRepositoryPort localeRepositoryPort = mock(TurSNSiteLocaleRepositoryPort.class);
        TurSNSiteSearchGraphQLController controller = new TurSNSiteSearchGraphQLController(
                searchProcess, siteRepositoryPort, fieldExtRepository, localeRepositoryPort);

        com.viglet.turing.domain.sn.TurSNSiteDomain site = siteDomain("demo", 0);

        TurSNSiteFieldExtDomain titleField = fieldDomain("title", "title_i");
        TurSNSiteFieldExtDomain priceField = fieldDomain("price", "price_d");
        TurSNSiteFieldExtDomain tagsField = fieldDomain("tags", "tags_ss");

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
        when(siteRepositoryPort.findByNameIgnoreCase("demo")).thenReturn(Optional.of(site));
        when(fieldExtRepository.findBySnSiteId(any())).thenReturn(List.of(titleField, priceField, tagsField));
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
        TurSNSiteRepositoryPort siteRepositoryPort = mock(TurSNSiteRepositoryPort.class);
        TurSNSiteFieldExtRepositoryPort fieldExtRepository = mock(TurSNSiteFieldExtRepositoryPort.class);
        TurSNSiteLocaleRepositoryPort localeRepositoryPort = mock(TurSNSiteLocaleRepositoryPort.class);
        TurSNSiteSearchGraphQLController controller = new TurSNSiteSearchGraphQLController(
                searchProcess, siteRepositoryPort, fieldExtRepository, localeRepositoryPort);

        com.viglet.turing.domain.sn.TurSNSiteDomain site = siteDomain("demo", 0);

        TurSNSiteFieldExtDomain titleField = fieldDomain("title", "title_i");
        TurSNSiteFieldExtDomain urlField = fieldDomain("url", "url_s");

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
        when(siteRepositoryPort.findByNameIgnoreCase("demo")).thenReturn(Optional.of(site));
        when(fieldExtRepository.findBySnSiteId(any())).thenReturn(List.of(titleField, urlField));
        when(searchProcess.search(any(TurSNSiteSearchContext.class))).thenReturn(response);

        TurSNSiteSearchBean result = controller.siteSearch("demo", input, "en");

        Map<String, Object> fields = result.getResults().getDocument().get(0).getFields();
        assertEquals("Course", fields.get("title"));
        assertEquals(1, fields.size());
    }

    @Test
    void testSiteSearchResolvesEnumSiteNameToHyphenatedSiteName() {
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteRepositoryPort siteRepositoryPort = mock(TurSNSiteRepositoryPort.class);
        TurSNSiteFieldExtRepositoryPort fieldExtRepository = mock(TurSNSiteFieldExtRepositoryPort.class);
        TurSNSiteLocaleRepositoryPort localeRepositoryPort = mock(TurSNSiteLocaleRepositoryPort.class);
        TurSNSiteSearchGraphQLController controller = new TurSNSiteSearchGraphQLController(
                searchProcess, siteRepositoryPort, fieldExtRepository, localeRepositoryPort);

        com.viglet.turing.domain.sn.TurSNSiteDomain site = siteDomain("education-stage-publish", 0);

        // resolveSiteName now goes through the port: enum-style → hyphenated.
        when(siteRepositoryPort.findByNameIgnoreCase("EDUCATION_STAGE_PUBLISH"))
                .thenReturn(Optional.empty());
        when(siteRepositoryPort.findAllNamesOrderedByNameIgnoreCase())
                .thenReturn(List.of("education-stage-publish"));
        // The siteSearch lookup also goes through the port now.
        when(siteRepositoryPort.findByNameIgnoreCase("education-stage-publish")).thenReturn(Optional.of(site));
        when(searchProcess.existsByTurSNSiteAndLanguage("education-stage-publish", Locale.ENGLISH))
                .thenReturn(true);
        when(fieldExtRepository.findBySnSiteId(any())).thenReturn(List.of());
        when(searchProcess.search(any(TurSNSiteSearchContext.class))).thenReturn(new TurSNSiteSearchBean());

        controller.siteSearch("EDUCATION_STAGE_PUBLISH", new TurSNSearchParamsInput(), "en");

        verify(searchProcess).existsByTurSNSiteAndLanguage("education-stage-publish", Locale.ENGLISH);
        verify(siteRepositoryPort).findByNameIgnoreCase("education-stage-publish");
    }

    @Test
    void testSiteSearchConvertsHyphenatedFieldNamesToDoubleUnderscore() {
        TurSNSearchProcess searchProcess = mock(TurSNSearchProcess.class);
        TurSNSiteRepositoryPort siteRepositoryPort = mock(TurSNSiteRepositoryPort.class);
        TurSNSiteFieldExtRepositoryPort fieldExtRepository = mock(TurSNSiteFieldExtRepositoryPort.class);
        TurSNSiteLocaleRepositoryPort localeRepositoryPort = mock(TurSNSiteLocaleRepositoryPort.class);
        TurSNSiteSearchGraphQLController controller = new TurSNSiteSearchGraphQLController(
                searchProcess, siteRepositoryPort, fieldExtRepository, localeRepositoryPort);

        com.viglet.turing.domain.sn.TurSNSiteDomain site = siteDomain("demo", 0);

        TurSNSiteFieldExtDomain hyphenField = fieldDomain("content-type", "content-type_s");
        TurSNSiteFieldExtDomain normalField = fieldDomain("author", "author_s");

        TurSNSiteSearchDocumentBean document = TurSNSiteSearchDocumentBean.builder()
                .fields(Map.of(
                        "content-type_s", "article",
                        "author_s", "John"))
                .build();
        TurSNSiteSearchBean response = new TurSNSiteSearchBean()
                .setResults(new TurSNSiteSearchResultsBean().setDocument(List.of(document)));

        when(searchProcess.existsByTurSNSiteAndLanguage("demo", Locale.ENGLISH)).thenReturn(true);
        when(siteRepositoryPort.findByNameIgnoreCase("demo")).thenReturn(Optional.of(site));
        when(fieldExtRepository.findBySnSiteId(any())).thenReturn(List.of(hyphenField, normalField));
        when(searchProcess.search(any(TurSNSiteSearchContext.class))).thenReturn(response);

        TurSNSiteSearchBean result = controller.siteSearch("demo", new TurSNSearchParamsInput(), "en");

        Map<String, Object> fields = result.getResults().getDocument().get(0).getFields();
        // Hyphenated field name should be converted to double underscore
        assertEquals("article", fields.get("content__type"));
        assertEquals("John", fields.get("author"));
        // Original hyphenated key should not be present
        assertNull(fields.get("content-type"));
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
