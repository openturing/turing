package com.viglet.turing.persistence.model.se;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TurSEVendorTest {

    @Test
    void shouldStoreAndExposeAllFields() {
        TurSEVendor vendor = new TurSEVendor();
        vendor.setId("SOLR");
        vendor.setTitle("Apache Solr");
        vendor.setDescription("Search engine");
        vendor.setPlugin("plugin-se");
        vendor.setWebsite("https://solr.apache.org");

        assertEquals("SOLR", vendor.getId());
        assertEquals("Apache Solr", vendor.getTitle());
        assertEquals("Search engine", vendor.getDescription());
        assertEquals("plugin-se", vendor.getPlugin());
        assertEquals("https://solr.apache.org", vendor.getWebsite());
    }
}
