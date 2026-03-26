package com.viglet.turing.plugins.se;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.se.TurSEVendor;
import com.viglet.turing.persistence.model.sn.TurSNSite;

/**
 * Tests for TurSearchEnginePluginFactory.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
class TurSearchEnginePluginFactoryTest {

    // --- getPlugin ---

    @Test
    void getPluginShouldReturnRegisteredPlugin() {
        TurSearchEnginePlugin solr = mock(TurSearchEnginePlugin.class);
        when(solr.getPluginType()).thenReturn("solr");
        TurSearchEnginePluginFactory factory = new TurSearchEnginePluginFactory(List.of(solr));

        assertSame(solr, factory.getPlugin("solr"));
        assertSame(solr, factory.getPlugin("SOLR"));
    }

    @Test
    void getPluginShouldFallbackToSolrForUnknown() {
        TurSearchEnginePlugin solr = mock(TurSearchEnginePlugin.class);
        when(solr.getPluginType()).thenReturn("solr");
        TurSearchEnginePluginFactory factory = new TurSearchEnginePluginFactory(List.of(solr));

        assertSame(solr, factory.getPlugin("unknown"));
    }

    @Test
    void getPluginShouldThrowWhenNoSolrFallback() {
        TurSearchEnginePlugin lucene = mock(TurSearchEnginePlugin.class);
        when(lucene.getPluginType()).thenReturn("lucene");
        TurSearchEnginePluginFactory factory = new TurSearchEnginePluginFactory(List.of(lucene));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> factory.getPlugin("unknown"));
        assertTrue(ex.getMessage().contains("unknown"));
        assertTrue(ex.getMessage().contains("solr"));
    }

    @Test
    void getPluginShouldSelectCorrectPluginFromMultiple() {
        TurSearchEnginePlugin solr = mock(TurSearchEnginePlugin.class);
        when(solr.getPluginType()).thenReturn("solr");
        TurSearchEnginePlugin lucene = mock(TurSearchEnginePlugin.class);
        when(lucene.getPluginType()).thenReturn("lucene");
        TurSearchEnginePlugin es = mock(TurSearchEnginePlugin.class);
        when(es.getPluginType()).thenReturn("elasticsearch");

        TurSearchEnginePluginFactory factory = new TurSearchEnginePluginFactory(
                List.of(solr, lucene, es));

        assertSame(solr, factory.getPlugin("solr"));
        assertSame(lucene, factory.getPlugin("lucene"));
        assertSame(es, factory.getPlugin("elasticsearch"));
    }

    @Test
    void getPluginShouldBeCaseInsensitive() {
        TurSearchEnginePlugin es = mock(TurSearchEnginePlugin.class);
        when(es.getPluginType()).thenReturn("ElasticSearch");
        TurSearchEnginePlugin solr = mock(TurSearchEnginePlugin.class);
        when(solr.getPluginType()).thenReturn("solr");

        TurSearchEnginePluginFactory factory = new TurSearchEnginePluginFactory(List.of(es, solr));

        assertSame(es, factory.getPlugin("elasticsearch"));
        assertSame(es, factory.getPlugin("ELASTICSEARCH"));
    }

    // --- getPluginForSite ---

    @Test
    void getPluginForSiteShouldResolveBySEVendorPlugin() {
        TurSearchEnginePlugin lucene = mock(TurSearchEnginePlugin.class);
        when(lucene.getPluginType()).thenReturn("lucene");
        TurSearchEnginePlugin solr = mock(TurSearchEnginePlugin.class);
        when(solr.getPluginType()).thenReturn("solr");
        TurSearchEnginePluginFactory factory = new TurSearchEnginePluginFactory(List.of(lucene, solr));

        TurSEVendor vendor = mock(TurSEVendor.class);
        when(vendor.getPlugin()).thenReturn("lucene");
        TurSEInstance seInstance = mock(TurSEInstance.class);
        when(seInstance.getTurSEVendor()).thenReturn(vendor);
        TurSNSite site = mock(TurSNSite.class);
        when(site.getTurSEInstance()).thenReturn(seInstance);

        assertSame(lucene, factory.getPluginForSite(site));
    }

    @Test
    void getPluginForSiteShouldFallbackToSolrForNullSite() {
        TurSearchEnginePlugin solr = mock(TurSearchEnginePlugin.class);
        when(solr.getPluginType()).thenReturn("solr");
        TurSearchEnginePluginFactory factory = new TurSearchEnginePluginFactory(List.of(solr));

        assertSame(solr, factory.getPluginForSite(null));
    }

    @Test
    void getPluginForSiteShouldFallbackToSolrWhenNoSEInstance() {
        TurSearchEnginePlugin solr = mock(TurSearchEnginePlugin.class);
        when(solr.getPluginType()).thenReturn("solr");
        TurSearchEnginePluginFactory factory = new TurSearchEnginePluginFactory(List.of(solr));

        TurSNSite site = mock(TurSNSite.class);
        when(site.getTurSEInstance()).thenReturn(null);

        assertSame(solr, factory.getPluginForSite(site));
    }

    @Test
    void getPluginForSiteShouldFallbackToSolrWhenNoVendor() {
        TurSearchEnginePlugin solr = mock(TurSearchEnginePlugin.class);
        when(solr.getPluginType()).thenReturn("solr");
        TurSearchEnginePluginFactory factory = new TurSearchEnginePluginFactory(List.of(solr));

        TurSEInstance seInstance = mock(TurSEInstance.class);
        when(seInstance.getTurSEVendor()).thenReturn(null);
        TurSNSite site = mock(TurSNSite.class);
        when(site.getTurSEInstance()).thenReturn(seInstance);

        assertSame(solr, factory.getPluginForSite(site));
    }

    @Test
    void getPluginForSiteShouldUseVendorIdWhenPluginBlank() {
        TurSearchEnginePlugin solr = mock(TurSearchEnginePlugin.class);
        when(solr.getPluginType()).thenReturn("solr");
        TurSearchEnginePluginFactory factory = new TurSearchEnginePluginFactory(List.of(solr));

        TurSEVendor vendor = mock(TurSEVendor.class);
        when(vendor.getPlugin()).thenReturn("");
        when(vendor.getId()).thenReturn("solr");
        TurSEInstance seInstance = mock(TurSEInstance.class);
        when(seInstance.getTurSEVendor()).thenReturn(vendor);
        TurSNSite site = mock(TurSNSite.class);
        when(site.getTurSEInstance()).thenReturn(seInstance);

        assertSame(solr, factory.getPluginForSite(site));
    }

    @Test
    void getPluginForSiteShouldUseVendorIdWhenPluginNull() {
        TurSearchEnginePlugin solr = mock(TurSearchEnginePlugin.class);
        when(solr.getPluginType()).thenReturn("solr");
        TurSearchEnginePluginFactory factory = new TurSearchEnginePluginFactory(List.of(solr));

        TurSEVendor vendor = mock(TurSEVendor.class);
        when(vendor.getPlugin()).thenReturn(null);
        when(vendor.getId()).thenReturn("solr");
        TurSEInstance seInstance = mock(TurSEInstance.class);
        when(seInstance.getTurSEVendor()).thenReturn(vendor);
        TurSNSite site = mock(TurSNSite.class);
        when(site.getTurSEInstance()).thenReturn(seInstance);

        assertSame(solr, factory.getPluginForSite(site));
    }

    // --- getPluginForInstance ---

    @Test
    void getPluginForInstanceShouldResolveByVendor() {
        TurSearchEnginePlugin es = mock(TurSearchEnginePlugin.class);
        when(es.getPluginType()).thenReturn("elasticsearch");
        TurSearchEnginePlugin solr = mock(TurSearchEnginePlugin.class);
        when(solr.getPluginType()).thenReturn("solr");
        TurSearchEnginePluginFactory factory = new TurSearchEnginePluginFactory(List.of(es, solr));

        TurSEVendor vendor = mock(TurSEVendor.class);
        when(vendor.getPlugin()).thenReturn("elasticsearch");
        TurSEInstance instance = mock(TurSEInstance.class);
        when(instance.getTurSEVendor()).thenReturn(vendor);

        assertSame(es, factory.getPluginForInstance(instance));
    }

    @Test
    void getPluginForInstanceShouldUseVendorIdWhenPluginBlank() {
        TurSearchEnginePlugin solr = mock(TurSearchEnginePlugin.class);
        when(solr.getPluginType()).thenReturn("solr");
        TurSearchEnginePluginFactory factory = new TurSearchEnginePluginFactory(List.of(solr));

        TurSEVendor vendor = mock(TurSEVendor.class);
        when(vendor.getPlugin()).thenReturn("");
        when(vendor.getId()).thenReturn("solr");
        TurSEInstance instance = mock(TurSEInstance.class);
        when(instance.getTurSEVendor()).thenReturn(vendor);

        assertSame(solr, factory.getPluginForInstance(instance));
    }

    @Test
    void getPluginForInstanceShouldFallbackToSolrForNull() {
        TurSearchEnginePlugin solr = mock(TurSearchEnginePlugin.class);
        when(solr.getPluginType()).thenReturn("solr");
        TurSearchEnginePluginFactory factory = new TurSearchEnginePluginFactory(List.of(solr));

        assertSame(solr, factory.getPluginForInstance(null));
    }

    @Test
    void getPluginForInstanceShouldFallbackToSolrWhenNoVendor() {
        TurSearchEnginePlugin solr = mock(TurSearchEnginePlugin.class);
        when(solr.getPluginType()).thenReturn("solr");
        TurSearchEnginePluginFactory factory = new TurSearchEnginePluginFactory(List.of(solr));

        TurSEInstance instance = mock(TurSEInstance.class);
        when(instance.getTurSEVendor()).thenReturn(null);

        assertSame(solr, factory.getPluginForInstance(instance));
    }
}
