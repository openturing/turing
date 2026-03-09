package com.viglet.turing.exchange.sn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class TurSNSiteGenAiExchangeTest {

    @Test
    void shouldStoreAndReturnAllFields() {
        TurSNSiteGenAiExchange exchange = new TurSNSiteGenAiExchange();
        exchange.setId("genai-id");
        exchange.setEnabled(false);
        exchange.setSystemPrompt("prompt");
        exchange.setTurLLMInstance("llm-1");
        exchange.setTurStoreInstance("store-1");

        assertEquals("genai-id", exchange.getId());
        assertFalse(exchange.isEnabled());
        assertEquals("prompt", exchange.getSystemPrompt());
        assertEquals("llm-1", exchange.getTurLLMInstance());
        assertEquals("store-1", exchange.getTurStoreInstance());
    }
}
