package com.viglet.turing.api.llm.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.tool.TurSemanticNavToolService;

@ExtendWith(MockitoExtension.class)
class TurSemanticNavToolAPITest {

    @Mock
    private TurSemanticNavToolService semanticNavToolService;

    private TurSemanticNavToolAPI api;

    @BeforeEach
    void setUp() {
        api = new TurSemanticNavToolAPI(semanticNavToolService);
    }

    @Test
    void shouldDelegateSearchSite() {
        when(semanticNavToolService.searchSite("MySite", "en", "query", 10, 1, null))
                .thenReturn("results");

        String result = api.searchSite("MySite", "en", "query", 10, 1, null);

        assertThat(result).isEqualTo("results");
        verify(semanticNavToolService).searchSite("MySite", "en", "query", 10, 1, null);
    }

    @Test
    void shouldDelegateSearchSiteWithFilterQueries() {
        when(semanticNavToolService.searchSite("MySite", "pt", "*", 5, 2, "type:news"))
                .thenReturn("filtered results");

        String result = api.searchSite("MySite", "pt", "*", 5, 2, "type:news");

        assertThat(result).isEqualTo("filtered results");
    }

    @Test
    void shouldDelegateListSites() {
        when(semanticNavToolService.listSites("")).thenReturn("site1;desc;en\n");

        String result = api.listSites("");

        assertThat(result).isEqualTo("site1;desc;en\n");
        verify(semanticNavToolService).listSites("");
    }

    @Test
    void shouldDelegateListSitesWithFilter() {
        when(semanticNavToolService.listSites("enterprise")).thenReturn("enterprise-site;desc;en\n");

        String result = api.listSites("enterprise");

        assertThat(result).isEqualTo("enterprise-site;desc;en\n");
    }

    @Test
    void shouldDelegateGetSiteFields() {
        when(semanticNavToolService.getSiteFields("MySite")).thenReturn("field_name;type;facet\n");

        String result = api.getSiteFields("MySite");

        assertThat(result).isEqualTo("field_name;type;facet\n");
        verify(semanticNavToolService).getSiteFields("MySite");
    }
}
