package com.viglet.turing.plugins.se.elasticsearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.sn.TurSNConfig;
import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.elasticsearch.TurElasticsearch;
import com.viglet.turing.elasticsearch.TurElasticsearchInstance;
import com.viglet.turing.elasticsearch.TurElasticsearchInstanceProcess;
import com.viglet.turing.se.result.TurSEResults;

@ExtendWith(MockitoExtension.class)
class TurElasticsearchSearchEnginePluginTest {

    @Mock
    private TurElasticsearch turElasticsearch;
    @Mock
    private TurElasticsearchInstanceProcess turElasticsearchInstanceProcess;

    @InjectMocks
    private TurElasticsearchSearchEnginePlugin plugin;

    private TurSNSiteSearchContext context;

    @BeforeEach
    void setUp() {
        TurSNConfig config = new TurSNConfig();
        TurSEParameters parameters = new TurSEParameters(new TurSNSearchParams());
        context = new TurSNSiteSearchContext("site-es", config, parameters, Locale.ENGLISH,
                URI.create("http://localhost"));
    }

    @Test
    void shouldRetrieveSearchResultsFromElasticsearchPlugin() {
        TurElasticsearchInstance instance = org.mockito.Mockito.mock(TurElasticsearchInstance.class);
        TurSEResults results = TurSEResults.builder().queryString("q-es").build();

        when(turElasticsearchInstanceProcess.initElasticsearchInstance("site-es", Locale.ENGLISH))
                .thenReturn(Optional.of(instance));
        when(turElasticsearch.retrieveElasticsearchFromSN(instance, context)).thenReturn(Optional.of(results));

        Optional<TurSEResults> response = plugin.retrieveSearchResults(context);

        assertTrue(response.isPresent());
        assertEquals("q-es", response.get().getQueryString());
        verify(turElasticsearch).retrieveElasticsearchFromSN(instance, context);
    }

    @Test
    void shouldRetrieveFacetResultsFromElasticsearchPlugin() {
        TurElasticsearchInstance instance = org.mockito.Mockito.mock(TurElasticsearchInstance.class);
        TurSEResults results = TurSEResults.builder().queryString("facet-es").build();

        when(turElasticsearchInstanceProcess.initElasticsearchInstance("site-es", Locale.ENGLISH))
                .thenReturn(Optional.of(instance));
        when(turElasticsearch.retrieveFacetElasticsearchFromSN(instance, context)).thenReturn(Optional.of(results));

        Optional<TurSEResults> response = plugin.retrieveFacetResults(context, "author");

        assertTrue(response.isPresent());
        assertEquals("facet-es", response.get().getQueryString());
        verify(turElasticsearch).retrieveFacetElasticsearchFromSN(instance, context);
    }

    @Test
    void shouldReturnEmptyWhenElasticsearchInstanceCannotBeInitialized() {
        when(turElasticsearchInstanceProcess.initElasticsearchInstance("site-es", Locale.ENGLISH))
                .thenReturn(Optional.empty());

        Optional<TurSEResults> response = plugin.retrieveSearchResults(context);

        assertFalse(response.isPresent());
    }

    @Test
    void shouldExposePluginTypeAsElasticsearch() {
        assertEquals("elasticsearch", plugin.getPluginType());
    }
}
