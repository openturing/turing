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

package com.viglet.turing.sn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.se.result.spellcheck.TurSESpellCheckResult;
import com.viglet.turing.commons.sn.TurSNConfig;
import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.commons.sn.bean.TurSNSitePostParamsBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchDocumentBean;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.persistence.dto.sn.field.TurSNSiteFieldExtDto;
import com.viglet.turing.persistence.dto.sn.field.TurSNSiteFieldExtFacetDto;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.se.result.TurSEResult;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Unit tests for TurSNUtils.
 *
 * @author Alexandre Oliveira
 * @since 2025.1.10
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TurSNUtilsTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private TurSNConfig turSNConfig;

    @Mock
    private TurSNSite turSNSite;

    private TurSNSearchParams turSNSearchParams;

    @BeforeEach
    void setUp() {
        turSNSearchParams = new TurSNSearchParams();
        turSNSearchParams.setLocale(java.util.Locale.US);
        lenient().when(request.getRequestURL()).thenReturn(new StringBuffer("http://example.com/api/search"));
        lenient().when(request.getQueryString()).thenReturn("q=test&page=1");
        lenient().when(request.getHeaderNames()).thenReturn(java.util.Collections.emptyEnumeration());
    }

    @Test
    void testConstructorThrowsException() throws NoSuchMethodException {
        var constructor = TurSNUtils.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThatThrownBy(constructor::newInstance)
                .cause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SN Utility class");
    }

    @Test
    void testIsTrueWithOne() {
        boolean result = TurSNUtils.isTrue(1);
        assertThat(result).isTrue();
    }

    @Test
    void testIsTrueWithZero() {
        boolean result = TurSNUtils.isTrue(0);
        assertThat(result).isFalse();
    }

    @Test
    void testIsTrueWithNull() {
        boolean result = TurSNUtils.isTrue(null);
        assertThat(result).isFalse();
    }

    @Test
    void testIsTrueWithOtherNumber() {
        boolean result = TurSNUtils.isTrue(42);
        assertThat(result).isFalse();
    }

    @Test
    void testGetCacheKey() {
        String siteName = "testSite";

        String result = TurSNUtils.getCacheKey(siteName, request);

        assertThat(result).isEqualTo("testSite_q=test&page=1");
    }

    @Test
    void testGetCacheKeyWithNullQueryString() {
        when(request.getQueryString()).thenReturn(null);
        String siteName = "testSite";

        String result = TurSNUtils.getCacheKey(siteName, request);

        assertThat(result).isEqualTo("testSite_null");
    }

    @Test
    void testGetTurSNSiteSearchContext() {
        String siteName = "testSite";

        TurSNSiteSearchContext result = TurSNUtils.getTurSNSiteSearchContext(
                turSNConfig, siteName, turSNSearchParams, request);

        assertThat(result).isNotNull();
        assertThat(result.getSiteName()).isEqualTo(siteName);
        assertThat(result.getLocale()).hasToString("en_US");
    }

    @Test
    void testGetTurSNSiteSearchContextWithPostParams() {
        String siteName = "testSite";
        TurSNSitePostParamsBean postParams = new TurSNSitePostParamsBean();

        TurSNSiteSearchContext result = TurSNUtils.getTurSNSiteSearchContext(
                turSNConfig, siteName, turSNSearchParams, postParams, request);

        assertThat(result).isNotNull();
        assertThat(result.getSiteName()).isEqualTo(siteName);
    }

    @Test
    void testHasCorrectedTextWithCorrectedText() {
        TurSESpellCheckResult spellCheckResult = new TurSESpellCheckResult();
        spellCheckResult.setCorrected(true);
        spellCheckResult.setCorrectedText("corrected query");

        boolean result = TurSNUtils.hasCorrectedText(spellCheckResult);

        assertThat(result).isTrue();
    }

    @Test
    void testHasCorrectedTextWithEmptyText() {
        TurSESpellCheckResult spellCheckResult = new TurSESpellCheckResult();
        spellCheckResult.setCorrected(true);
        spellCheckResult.setCorrectedText("");

        boolean result = TurSNUtils.hasCorrectedText(spellCheckResult);

        assertThat(result).isFalse();
    }

    @Test
    void testHasCorrectedTextNotCorrected() {
        TurSESpellCheckResult spellCheckResult = new TurSESpellCheckResult();
        spellCheckResult.setCorrected(false);
        spellCheckResult.setCorrectedText("text");

        boolean result = TurSNUtils.hasCorrectedText(spellCheckResult);

        assertThat(result).isFalse();
    }

    @Test
    void testIsAutoCorrectionEnabledWhenEnabled() {
        TurSNSiteSearchContext context = mock(TurSNSiteSearchContext.class);
        TurSEParameters params = mock(TurSEParameters.class);

        when(context.getTurSEParameters()).thenReturn(params);
        when(params.getAutoCorrectionDisabled()).thenReturn(0);
        when(params.getCurrentPage()).thenReturn(1);
        when(turSNSite.getSpellCheck()).thenReturn(1);
        when(turSNSite.getSpellCheckFixes()).thenReturn(1);

        boolean result = TurSNUtils.isAutoCorrectionEnabled(context, turSNSite);

        assertThat(result).isTrue();
    }

    @Test
    void testIsAutoCorrectionEnabledWhenDisabled() {
        TurSNSiteSearchContext context = mock(TurSNSiteSearchContext.class);
        TurSEParameters params = mock(TurSEParameters.class);

        when(context.getTurSEParameters()).thenReturn(params);
        when(params.getAutoCorrectionDisabled()).thenReturn(1);

        boolean result = TurSNUtils.isAutoCorrectionEnabled(context, turSNSite);

        assertThat(result).isFalse();
    }

    @Test
    void testIsAutoCorrectionEnabledWhenNotFirstPage() {
        TurSNSiteSearchContext context = mock(TurSNSiteSearchContext.class);
        TurSEParameters params = mock(TurSEParameters.class);

        when(context.getTurSEParameters()).thenReturn(params);
        when(params.getAutoCorrectionDisabled()).thenReturn(0);
        when(params.getCurrentPage()).thenReturn(2);

        boolean result = TurSNUtils.isAutoCorrectionEnabled(context, turSNSite);

        assertThat(result).isFalse();
    }

    @Test
    void testAddFilterQuery() {
        URI uri = URI.create("http://example.com/search?q=test");
        String fq = "category:books";

        URI result = TurSNUtils.addFilterQuery(uri, fq);

        assertThat(result.toString()).contains("fq");
        assertThat(result.toString()).contains("category");
        assertThat(result.toString()).contains("q=test");
    }

    @Test
    void testAddFilterQueryWhenAlreadyExists() {
        URI uri = URI.create("http://example.com/search?q=test&fq=category:books");
        String fq = "category:books";

        URI result = TurSNUtils.addFilterQuery(uri, fq);

        assertThat(result.toString()).contains("fq");
        assertThat(result.toString()).contains("category");
    }

    @Test
    void testRemoveFilterQuery() {
        URI uri = URI.create("http://example.com/search?q=test&fq=category:books");
        String fq = "category:books";

        URI result = TurSNUtils.removeFilterQuery(uri, fq);

        assertThat(result.toString()).contains("q=test");
    }

    @Test
    void testRemoveFilterQueryByFieldName() {
        URI uri = URI.create("http://example.com/search?q=test&fq=category:books");

        URI result = TurSNUtils.removeFilterQueryByFieldName(uri, "category");

        assertThat(result.toString()).contains("q=test");
    }

    @Test
    void testFilterQueryByFieldName() {
        URI uri = URI.create("http://example.com/search?fq[]=category:books&fq[]=author:smith");

        List<String> result = TurSNUtils.filterQueryByFieldName(uri, "category");

        assertThat(result)
                .hasSize(1)
                .contains("category:books")
                .doesNotContain("author:smith");
    }

    @Test
    void testRemoveQueryStringParameter() {
        URI uri = URI.create("http://example.com/search?q=test&page=2&sort=date");

        URI result = TurSNUtils.removeQueryStringParameter(uri, "page");

        assertThat(result.toString()).doesNotContain("page=");
        assertThat(result.toString()).contains("q=test");
        assertThat(result.toString()).contains("sort=date");
    }

    @Test
    void testAddSNDocument() {
        URI uri = URI.create("http://example.com/search");
        Map<String, TurSNSiteFieldExtDto> fieldExtMap = new HashMap<>();
        Map<String, TurSNSiteFieldExtDto> facetMap = new HashMap<>();
        List<TurSNSiteSearchDocumentBean> documents = new ArrayList<>();

        TurSEResult result = TurSEResult.builder()
                .fields(new HashMap<>())
                .build();
        result.getFields().put("title", "Test Title");
        result.getFields().put("url", "http://example.com/doc");

        TurSNUtils.addSNDocument(uri, fieldExtMap, facetMap, documents, result, false);

        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).isElevate()).isFalse();
    }

    @Test
    void testAddSNDocumentWithPosition() {
        URI uri = URI.create("http://example.com/search");
        Map<String, TurSNSiteFieldExtDto> fieldExtMap = new HashMap<>();
        Map<String, TurSNSiteFieldExtDto> facetMap = new HashMap<>();
        List<TurSNSiteSearchDocumentBean> documents = new ArrayList<>();
        documents.add(TurSNSiteSearchDocumentBean.builder().build());

        TurSEResult result = TurSEResult.builder()
                .fields(new HashMap<>())
                .build();
        result.getFields().put("title", "New Title");

        TurSNUtils.addSNDocumentWithPosition(uri, fieldExtMap, facetMap, documents, result, true, 0);

        assertThat(documents).hasSize(2);
        assertThat(documents.get(0).isElevate()).isTrue();
    }

    @Test
    void testConstantsAreCorrect() {
        assertThat(TurSNUtils.TURING_ENTITY).isEqualTo("turing_entity");
        assertThat(TurSNUtils.DEFAULT_LANGUAGE).isEqualTo("en");
        assertThat(TurSNUtils.URL).isEqualTo("url");
    }

    @Test
    void testRemoveFilterQueryByFieldNames() {
        URI uri = URI.create("http://example.com/search?fq=cat:books&fq=author:smith&q=test");

        URI result = TurSNUtils.removeFilterQueryByFieldNames(uri, List.of("cat", "author"));

        assertThat(result.toString()).contains("q=test");
    }

    @Test
    void testFilterQueryByFieldNames() {
        URI uri = URI.create("http://example.com/search?fq[]=category:books&fq[]=author:smith&fq[]=year:2024");

        List<String> result = TurSNUtils.filterQueryByFieldNames(uri, List.of("category", "year"));

        assertThat(result)
                .hasSize(2)
                .contains("category:books", "year:2024")
                .doesNotContain("author:smith");
    }

    @Test
    void testAddFilterQueryResetsPageToOne() {
        URI uri = URI.create("http://example.com/search?q=test&p=5");

        URI result = TurSNUtils.addFilterQuery(uri, "category:books");

        assertThat(result.toString()).contains("p=1");
        assertThat(result.toString()).contains("q=test");
    }

    @Test
    void testAddSNDocumentWithDuplicateMappedFieldNamesAndMetadataArray() {
        URI uri = URI.create("http://example.com/search?q=test");
        Map<String, TurSNSiteFieldExtDto> fieldExtMap = new HashMap<>();
        fieldExtMap.put("first_attr", TurSNSiteFieldExtDto.builder().name("merged")
                .facetLocales(Set.of(new TurSNSiteFieldExtFacetDto())).build());
        fieldExtMap.put("second_attr", TurSNSiteFieldExtDto.builder().name("merged").build());

        Map<String, TurSNSiteFieldExtDto> facetMap = new HashMap<>();
        facetMap.put("category", TurSNSiteFieldExtDto.builder().name("category").build());

        List<TurSNSiteSearchDocumentBean> documents = new ArrayList<>();

        Map<String, Object> fields = new HashMap<>();
        fields.put("first_attr", "A");
        fields.put("second_attr", "B");
        fields.put("category", new ArrayList<>(List.of("books", "tech")));
        fields.put("turing_entity_hidden", "ignore");
        TurSEResult result = TurSEResult.builder().fields(fields).build();

        TurSNUtils.addSNDocument(uri, fieldExtMap, facetMap, documents, result, true);

        assertThat(documents).hasSize(1);
        TurSNSiteSearchDocumentBean document = documents.get(0);
        assertThat(document.isElevate()).isTrue();
        assertThat(document.getFields()).containsKey("merged");
        assertThat(document.getFields().get("merged")).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Object> merged = (List<Object>) document.getFields().get("merged");
        assertThat(merged).containsExactly("A", "B");
        assertThat(document.getFields().keySet()).noneMatch(k -> k.startsWith("turing_entity"));
        assertThat(document.getMetadata()).hasSize(2);
    }

    @Test
    void testAddSNDocumentWithoutUrlSetsNullSource() {
        URI uri = URI.create("http://example.com/search");
        List<TurSNSiteSearchDocumentBean> documents = new ArrayList<>();
        TurSEResult result = TurSEResult.builder().fields(Map.of("title", "No URL")).build();

        TurSNUtils.addSNDocument(uri, new HashMap<>(), new HashMap<>(), documents, result, false);

        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).getSource()).isNull();
    }
}
