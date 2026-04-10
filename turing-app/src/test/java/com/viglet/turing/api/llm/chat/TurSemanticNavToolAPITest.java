package com.viglet.turing.api.llm.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.tool.TurDslToolService;

@ExtendWith(MockitoExtension.class)
class TurSemanticNavToolAPITest {

    @Mock
    private TurDslToolService dslToolService;

    private TurSemanticNavToolAPI api;

    @BeforeEach
    void setUp() {
        api = new TurSemanticNavToolAPI(dslToolService);
    }

    @Test
    void shouldDelegateSearch() {
        String body = "{\"query\":{\"match_all\":{}}}";
        when(dslToolService.search("MySite", "en", body)).thenReturn("results");

        String result = api.searchSite("MySite", "en", body);

        assertThat(result).isEqualTo("results");
        verify(dslToolService).search("MySite", "en", body);
    }

    @Test
    void shouldDelegateListIndices() {
        when(dslToolService.listIndices("*")).thenReturn("indices");

        String result = api.listSites("*");

        assertThat(result).isEqualTo("indices");
        verify(dslToolService).listIndices("*");
    }

    @Test
    void shouldDelegateGetMappings() {
        when(dslToolService.getMappings("MySite")).thenReturn("mappings");

        String result = api.getSiteFields("MySite");

        assertThat(result).isEqualTo("mappings");
        verify(dslToolService).getMappings("MySite");
    }

    @Test
    void shouldDelegateGetDocument() {
        when(dslToolService.getDocument("MySite", "en", "doc1")).thenReturn("doc");

        String result = api.getDocument("MySite", "en", "doc1");

        assertThat(result).isEqualTo("doc");
        verify(dslToolService).getDocument("MySite", "en", "doc1");
    }

    @Test
    void shouldDelegateSuggest() {
        when(dslToolService.suggest("MySite", "en", "test")).thenReturn("suggestions");

        String result = api.suggest("MySite", "en", "test");

        assertThat(result).isEqualTo("suggestions");
        verify(dslToolService).suggest("MySite", "en", "test");
    }
}
