package com.viglet.turing.properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TurConfigPropertiesTest {

    @Test
    void shouldStoreAndReturnAllConfiguredValues() {
        TurConfigProperties properties = new TurConfigProperties();
        TurSolrProperty turSolrProperty = new TurSolrProperty();
        turSolrProperty.setTimeout(2500);
        turSolrProperty.setCloud(true);

        properties.setMultiTenant(true);
        properties.setKeycloak(true);
        properties.setAllowedOrigins("http://localhost:5173,http://localhost:2700");
        properties.setSolr(turSolrProperty);

        assertTrue(properties.isMultiTenant());
        assertTrue(properties.isKeycloak());
        assertEquals("http://localhost:5173,http://localhost:2700", properties.getAllowedOrigins());
        assertSame(turSolrProperty, properties.getSolr());
        assertEquals(2500, properties.getSolr().getTimeout());
        assertTrue(properties.getSolr().isCloud());
    }

    @Test
    void shouldAllowUpdatingNestedSolrPropertiesIndependently() {
        TurConfigProperties properties = new TurConfigProperties();
        TurSolrProperty turSolrProperty = new TurSolrProperty();
        properties.setSolr(turSolrProperty);

        properties.getSolr().setCloud(false);
        properties.getSolr().setTimeout(1000);

        assertFalse(properties.getSolr().isCloud());
        assertEquals(1000, properties.getSolr().getTimeout());
    }
}
