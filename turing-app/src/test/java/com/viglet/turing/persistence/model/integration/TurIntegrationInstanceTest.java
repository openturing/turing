package com.viglet.turing.persistence.model.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TurIntegrationInstanceTest {

    @Test
    void shouldStoreAndExposeAllFields() {
        TurIntegrationInstance instance = new TurIntegrationInstance();
        instance.setId("int-1");
        instance.setTitle("CMS Integration");
        instance.setDescription("Integration with CMS");
        instance.setEnabled(1);
        instance.setEndpoint("https://cms.example.com/api");
        instance.setVendor("AEM");

        assertEquals("int-1", instance.getId());
        assertEquals("CMS Integration", instance.getTitle());
        assertEquals("Integration with CMS", instance.getDescription());
        assertEquals(1, instance.getEnabled());
        assertEquals("https://cms.example.com/api", instance.getEndpoint());
        assertEquals("AEM", instance.getVendor());
    }
}
