/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.sn.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.sn.TurSNConfig;
import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.commons.sn.bean.TurSNSitePostParamsBean;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.persistence.dto.sn.field.TurSNSiteFieldExtDto;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.se.result.TurSEResult;
import com.viglet.turing.sn.spotlight.TurSNSpotlightProcess;
import com.viglet.turing.solr.TurSolrInstance;

/**
 * Unit tests for {@link TurSNDocumentResponse}, exercising both the full
 * document response (with spotlight gating) and the lightweight list response
 * (single-field vs. multi-field).
 */
@ExtendWith(MockitoExtension.class)
class TurSNDocumentResponseTest {

    @Mock
    private TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;

    @Mock
    private TurSNSpotlightProcess turSNSpotlightProcess;

    @InjectMocks
    private TurSNDocumentResponse documentResponse;

    private TurSNSite site;

    @BeforeEach
    void setUp() {
        site = new TurSNSite();
        site.setName("site");
        site.setSpotlightWithResults(0);
    }

    @Test
    void responseDocumentsReturnsEmptyDocumentListForEmptyResults() {
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(Collections.emptyList());

        var bean = documentResponse.responseDocuments(context(List.of()), null, site, Map.of(),
                Collections.emptyList());

        assertThat(bean).isNotNull();
        assertThat(bean.getDocument()).isEmpty();
    }

    @Test
    void responseDocumentsAddsOneBeanPerResult() {
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(Collections.emptyList());

        TurSEResult r1 = TurSEResult.builder().fields(java.util.Map.of("title", "Doc 1")).build();
        TurSEResult r2 = TurSEResult.builder().fields(java.util.Map.of("title", "Doc 2")).build();

        var bean = documentResponse.responseDocuments(context(List.of()), null, site, Map.of(),
                List.of(r1, r2));

        assertThat(bean.getDocument()).hasSize(2);
    }

    @Test
    void responseDocumentsSkipsSpotlightWhenSiteFlagDisabled() {
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(Collections.emptyList());
        site.setSpotlightWithResults(0);

        documentResponse.responseDocuments(context(List.of()),
                org.mockito.Mockito.mock(TurSolrInstance.class), site, Map.of(),
                Collections.emptyList());

        verify(turSNSpotlightProcess, never()).addSpotlightToResults(any(), any(), any(), any(),
                any(), any());
    }

    @Test
    void responseDocumentsSkipsSpotlightWhenSolrInstanceMissing() {
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(Collections.emptyList());
        site.setSpotlightWithResults(1);

        documentResponse.responseDocuments(context(List.of()), null, site, Map.of(),
                Collections.emptyList());

        verify(turSNSpotlightProcess, never()).addSpotlightToResults(any(), any(), any(), any(),
                any(), any());
    }

    @Test
    void responseDocumentsInvokesSpotlightWhenEnabledAndSolrInstancePresent() {
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1))
                .thenReturn(Collections.emptyList());
        site.setSpotlightWithResults(1);
        TurSolrInstance solr = org.mockito.Mockito.mock(TurSolrInstance.class);

        documentResponse.responseDocuments(context(List.of()), solr, site, Map.of(),
                Collections.emptyList());

        verify(turSNSpotlightProcess).addSpotlightToResults(any(), eq(solr), eq(site), any(),
                any(), any());
    }

    @Test
    void responseDocumentsTolleratesNullSite() {
        // Defensive: the previous inline implementation guarded the spotlight call with
        // Optional.ofNullable(site) — preserve that null-safety.
        var bean = documentResponse.responseDocuments(context(List.of()), null, null, Map.of(),
                Collections.emptyList());

        assertThat(bean).isNotNull();
        assertThat(bean.getDocument()).isEmpty();
        verify(turSNSpotlightProcess, never()).addSpotlightToResults(any(), any(), any(), any(),
                any(), any());
    }

    @Test
    void responseListUsesDefaultTitleFieldWhenNoFieldsRequested() {
        TurSEResult r = TurSEResult.builder().fields(Map.of("title", "OnlyTitle")).build();

        List<Object> list = documentResponse.responseList(context(null), List.of(r));

        assertThat(list).containsExactly("OnlyTitle");
    }

    @Test
    void responseListSkipsResultsMissingTheRequestedSingleField() {
        TurSEResult withTitle = TurSEResult.builder().fields(Map.of("title", "Has")).build();
        TurSEResult withoutTitle = TurSEResult.builder().fields(Map.of("url", "/x")).build();

        List<Object> list = documentResponse.responseList(context(null),
                List.of(withTitle, withoutTitle));

        assertThat(list).containsExactly("Has");
    }

    @Test
    void responseListReturnsRawValuesForSingleRequestedField() {
        TurSEResult r1 = TurSEResult.builder().fields(Map.of("url", "/a")).build();
        TurSEResult r2 = TurSEResult.builder().fields(Map.of("url", "/b")).build();

        List<Object> list = documentResponse.responseList(context(List.of("url")), List.of(r1, r2));

        assertThat(list).containsExactly("/a", "/b");
    }

    @Test
    void responseListReturnsMapsWhenMultipleFieldsRequested() {
        TurSEResult r = TurSEResult.builder()
                .fields(Map.of("title", "T", "url", "/u")).build();

        List<Object> list = documentResponse.responseList(context(List.of("title", "url")),
                List.of(r));

        assertThat(list).hasSize(1);
        assertThat(list.getFirst()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) list.getFirst();
        assertThat(first).containsEntry("title", "T").containsEntry("url", "/u");
    }

    @Test
    void responseListMapsOmitNullFieldValues() {
        TurSEResult r = TurSEResult.builder().fields(Map.of("title", "Only")).build();

        List<Object> list = documentResponse.responseList(context(List.of("title", "url")),
                List.of(r));

        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) list.getFirst();
        assertThat(first).containsKey("title").doesNotContainKey("url");
    }

    private TurSNSiteSearchContext context(List<String> fields) {
        TurSNSearchParams params = new TurSNSearchParams();
        params.setQ("java");
        params.setLocale(Locale.US);
        if (fields != null) {
            params.setFl(fields);
        }
        TurSNSitePostParamsBean post = new TurSNSitePostParamsBean();
        return new TurSNSiteSearchContext("site", new TurSNConfig(),
                new TurSEParameters(params, post), Locale.US,
                URI.create("http://localhost/search?q=java"), post);
    }
}
