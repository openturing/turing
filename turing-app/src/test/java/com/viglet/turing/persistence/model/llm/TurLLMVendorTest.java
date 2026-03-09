package com.viglet.turing.persistence.model.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TurLLMVendorTest {

    @Test
    void shouldStoreAndExposeAllFields() {
        TurLLMVendor vendor = new TurLLMVendor();
        vendor.setId("OPENAI");
        vendor.setTitle("Open AI");
        vendor.setDescription("LLM Vendor");
        vendor.setPlugin("plugin-llm");
        vendor.setWebsite("https://openai.com");

        assertEquals("OPENAI", vendor.getId());
        assertEquals("Open AI", vendor.getTitle());
        assertEquals("LLM Vendor", vendor.getDescription());
        assertEquals("plugin-llm", vendor.getPlugin());
        assertEquals("https://openai.com", vendor.getWebsite());
    }
}
