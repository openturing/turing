package com.viglet.turing.persistence.bean.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TurIntegrationVendorTest {

    @Test
    void shouldCreateVendorUsingConstructorAndAllowSetterUpdates() {
        TurIntegrationVendor vendor = new TurIntegrationVendor("AEM", "Adobe Experience Manager");

        assertEquals("AEM", vendor.getId());
        assertEquals("Adobe Experience Manager", vendor.getTitle());

        vendor.setId("WP");
        vendor.setTitle("WordPress");

        assertEquals("WP", vendor.getId());
        assertEquals("WordPress", vendor.getTitle());
    }
}
