/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.mcp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.tool.TurDslToolService;

/**
 * Unit tests for {@link TurMcpSearchToolService} — the T247 MCP read surface.
 * Verifies each friendly-named tool delegates to the proven
 * {@link TurDslToolService} and that the full-text / facet tools build the
 * expected Elasticsearch-DSL body.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@ExtendWith(MockitoExtension.class)
class TurMcpSearchToolServiceTest {

    @Mock
    private TurDslToolService dslToolService;

    @InjectMocks
    private TurMcpSearchToolService service;

    @Test
    void listSites_delegatesToListIndices() {
        when(dslToolService.listIndices("prod*")).thenReturn("ok");
        assertEquals("ok", service.listSites("prod*"));
        verify(dslToolService).listIndices("prod*");
    }

    @Test
    void getSiteFields_delegatesToGetMappings() {
        when(dslToolService.getMappings("wiki")).thenReturn("fields");
        assertEquals("fields", service.getSiteFields("wiki"));
        verify(dslToolService).getMappings("wiki");
    }

    @Test
    void getDocument_delegates() {
        when(dslToolService.getDocument("wiki", "en_US", "42")).thenReturn("doc");
        assertEquals("doc", service.getDocument("wiki", "en_US", "42"));
        verify(dslToolService).getDocument("wiki", "en_US", "42");
    }

    @Test
    void autocomplete_delegatesToSuggest() {
        when(dslToolService.suggest("wiki", "en_US", "sea")).thenReturn("sug");
        assertEquals("sug", service.autocomplete("wiki", "en_US", "sea"));
        verify(dslToolService).suggest("wiki", "en_US", "sea");
    }

    @Test
    void searchSite_buildsMatchQueryWithSize() {
        when(dslToolService.search(eq("wiki"), eq("en_US"), anyJson())).thenReturn("results");
        service.searchSite("wiki", "en_US", "auth refresh", 5);

        String body = captureBody("wiki", "en_US");
        assertTrue(body.contains("\"match\""), body);
        assertTrue(body.contains("_text_"), body);
        assertTrue(body.contains("auth refresh"), body);
        assertTrue(body.contains("\"size\":5"), body);
    }

    @Test
    void searchSite_emptyQuery_matchesAll() {
        when(dslToolService.search(eq("wiki"), eq("en_US"), anyJson())).thenReturn("results");
        service.searchSite("wiki", "en_US", "  ", null);

        String body = captureBody("wiki", "en_US");
        assertTrue(body.contains("match_all"), body);
        // null maxResults → default 10
        assertTrue(body.contains("\"size\":10"), body);
    }

    @Test
    void searchSite_clampsMaxResults() {
        when(dslToolService.search(eq("wiki"), eq("en_US"), anyJson())).thenReturn("results");
        service.searchSite("wiki", "en_US", "q", 999);

        String body = captureBody("wiki", "en_US");
        assertTrue(body.contains("\"size\":50"), body);
    }

    @Test
    void searchSite_missingSite_returnsError() {
        String result = service.searchSite("", "en_US", "q", 5);
        assertTrue(result.startsWith("Error"), result);
        verify(dslToolService, never()).search(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void facetSearch_buildsTermsAggregation() {
        when(dslToolService.search(eq("wiki"), eq("en_US"), anyJson())).thenReturn("facets");
        service.facetSearch("wiki", "en_US", "guide", "category");

        String body = captureBody("wiki", "en_US");
        assertTrue(body.contains("\"aggs\""), body);
        assertTrue(body.contains("\"terms\""), body);
        assertTrue(body.contains("category"), body);
        assertTrue(body.contains("\"size\":0"), body);
    }

    @Test
    void facetSearch_missingFacetField_returnsError() {
        String result = service.facetSearch("wiki", "en_US", "q", " ");
        assertTrue(result.startsWith("Error"), result);
        verify(dslToolService, never()).search(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void similarDocuments_buildsMatchOnReferenceText() {
        when(dslToolService.search(eq("wiki"), eq("en_US"), anyJson())).thenReturn("similar");
        service.similarDocuments("wiki", "en_US", "a paragraph about dragons", 3);

        String body = captureBody("wiki", "en_US");
        assertTrue(body.contains("dragons"), body);
        assertTrue(body.contains("\"size\":3"), body);
    }

    @Test
    void similarDocuments_missingReferenceText_returnsError() {
        String result = service.similarDocuments("wiki", "en_US", "", 3);
        assertTrue(result.startsWith("Error"), result);
        verify(dslToolService, never()).search(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    private String anyJson() {
        return org.mockito.ArgumentMatchers.anyString();
    }

    private String captureBody(String site, String locale) {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(dslToolService).search(eq(site), eq(locale), captor.capture());
        return captor.getValue();
    }
}
