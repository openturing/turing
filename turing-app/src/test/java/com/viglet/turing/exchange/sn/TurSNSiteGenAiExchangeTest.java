package com.viglet.turing.exchange.sn;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TurSNSiteGenAiExchangeTest {

    @Test
    void shouldStoreAndReturnAllFields() {
        TurSNSiteGenAiExchange exchange = new TurSNSiteGenAiExchange();
        exchange.setId("genai-id");
        exchange.setSitePrompt("site");
        exchange.setTurAIAgent("agent-1");

        assertEquals("genai-id", exchange.getId());
        assertEquals("site", exchange.getSitePrompt());
        assertEquals("agent-1", exchange.getTurAIAgent());
    }
}
