package com.viglet.turing.properties;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TurConfigPropertiesTest {

    // T642 / §XXXVII.4 — authn must not be implicitly super-admin.
    @Test
    void permissionsSecureByDefault() {
        assertTrue(new TurConfigProperties().isPermissions());
    }

    @Test
    void warnHookIsSafeInBothModes() {
        TurConfigProperties permissive = new TurConfigProperties();
        permissive.setPermissions(false);
        assertThatCode(permissive::warnIfPermissionsDisabled).doesNotThrowAnyException();
        assertThatCode(new TurConfigProperties()::warnIfPermissionsDisabled).doesNotThrowAnyException();
    }

    @Test
    void shouldStoreAndReturnAllConfiguredValues() {
        TurConfigProperties properties = new TurConfigProperties();
        TurSolrProperty turSolrProperty = new TurSolrProperty();
        turSolrProperty.setTimeout(2500);
        turSolrProperty.setCloud(true);

        properties.getTenancy().setEnabled(true);
        properties.setKeycloak(true);
        properties.setAllowedOrigins("http://localhost:5173,http://localhost:2700");
        properties.setSolr(turSolrProperty);

        assertTrue(properties.getTenancy().isEnabled());
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
