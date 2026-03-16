package com.viglet.turing.plugins.se;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.se.TurSEVendor;
import com.viglet.turing.persistence.model.sn.TurSNSite;

class TurSearchEnginePluginFactoryTest {

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

        assertThrows(IllegalStateException.class, () -> factory.getPlugin("unknown"));
    }

    @Test
    void getPluginForSiteShouldResolveBySEVendor() {
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
}
