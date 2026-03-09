package com.viglet.turing.plugins.se.solr;

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
import com.viglet.turing.se.result.TurSEResults;
import com.viglet.turing.solr.TurSolr;
import com.viglet.turing.solr.TurSolrInstance;
import com.viglet.turing.solr.TurSolrInstanceProcess;

@ExtendWith(MockitoExtension.class)
class TurSolrSearchEnginePluginTest {

    @Mock
    private TurSolr turSolr;
    @Mock
    private TurSolrInstanceProcess turSolrInstanceProcess;

    @InjectMocks
    private TurSolrSearchEnginePlugin plugin;

    private TurSNSiteSearchContext context;

    @BeforeEach
    void setUp() {
        TurSNConfig config = new TurSNConfig();
        TurSEParameters parameters = new TurSEParameters(new TurSNSearchParams());
        context = new TurSNSiteSearchContext("site-a", config, parameters, Locale.ENGLISH,
                URI.create("http://localhost"));
    }

    @Test
    void shouldRetrieveSearchResultsFromSolrPlugin() {
        TurSolrInstance instance = org.mockito.Mockito.mock(TurSolrInstance.class);
        TurSEResults results = TurSEResults.builder().queryString("q").build();

        when(turSolrInstanceProcess.initSolrInstance("site-a", Locale.ENGLISH)).thenReturn(Optional.of(instance));
        when(turSolr.retrieveSolrFromSN(instance, context)).thenReturn(Optional.of(results));

        Optional<TurSEResults> response = plugin.retrieveSearchResults(context);

        assertTrue(response.isPresent());
        assertEquals("q", response.get().getQueryString());
        verify(turSolrInstanceProcess).initSolrInstance("site-a", Locale.ENGLISH);
        verify(turSolr).retrieveSolrFromSN(instance, context);
    }

    @Test
    void shouldReturnEmptyWhenSolrInstanceCannotBeInitialized() {
        when(turSolrInstanceProcess.initSolrInstance("site-a", Locale.ENGLISH)).thenReturn(Optional.empty());

        Optional<TurSEResults> response = plugin.retrieveSearchResults(context);

        assertFalse(response.isPresent());
    }

    @Test
    void shouldRetrieveFacetResultsPassingFacetName() {
        TurSolrInstance instance = org.mockito.Mockito.mock(TurSolrInstance.class);
        TurSEResults results = TurSEResults.builder().queryString("facet").build();

        when(turSolrInstanceProcess.initSolrInstance("site-a", Locale.ENGLISH)).thenReturn(Optional.of(instance));
        when(turSolr.retrieveFacetSolrFromSN(instance, context, "category")).thenReturn(Optional.of(results));

        Optional<TurSEResults> response = plugin.retrieveFacetResults(context, "category");

        assertTrue(response.isPresent());
        assertEquals("facet", response.get().getQueryString());
        verify(turSolr).retrieveFacetSolrFromSN(instance, context, "category");
    }

    @Test
    void shouldExposePluginTypeAsSolr() {
        assertEquals("solr", plugin.getPluginType());
    }
}
