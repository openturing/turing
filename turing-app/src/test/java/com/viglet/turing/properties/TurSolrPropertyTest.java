package com.viglet.turing.properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TurSolrPropertyTest {

    @Test
    void shouldStoreTimeoutAndCloudFlag() {
        TurSolrProperty property = new TurSolrProperty();
        property.setTimeout(1200);
        property.setCloud(true);

        assertEquals(1200, property.getTimeout());
        assertTrue(property.isCloud());
    }

    @Test
    void shouldAllowCloudFlagToggle() {
        TurSolrProperty property = new TurSolrProperty();
        property.setCloud(true);
        property.setCloud(false);

        assertFalse(property.isCloud());
    }
}
