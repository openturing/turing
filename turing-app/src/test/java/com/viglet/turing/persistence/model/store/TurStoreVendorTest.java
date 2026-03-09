package com.viglet.turing.persistence.model.store;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TurStoreVendorTest {

    @Test
    void shouldStoreAndExposeAllFields() {
        TurStoreVendor vendor = new TurStoreVendor();
        vendor.setId("CHROMA");
        vendor.setTitle("Chroma");
        vendor.setDescription("Vector DB");
        vendor.setPlugin("plugin-a");
        vendor.setWebsite("https://example.com");

        assertEquals("CHROMA", vendor.getId());
        assertEquals("Chroma", vendor.getTitle());
        assertEquals("Vector DB", vendor.getDescription());
        assertEquals("plugin-a", vendor.getPlugin());
        assertEquals("https://example.com", vendor.getWebsite());
    }
}
