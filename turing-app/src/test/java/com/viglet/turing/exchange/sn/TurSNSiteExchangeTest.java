package com.viglet.turing.exchange.sn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;

class TurSNSiteExchangeTest {

    @Test
    void shouldMapTurSNSiteWithGenAiAndSeReference() {
        TurSNSite site = new TurSNSite();
        site.setId("site-1");
        site.setName("Main Site");
        site.setDescription("desc");
        site.setFacet(1);
        site.setHl(1);
        site.setMlt(0);
        site.setThesaurus(1);
        site.setRowsPerPage(25);

        TurSEInstance seInstance = new TurSEInstance();
        seInstance.setId("se-1");
        site.setTurSEInstance(seInstance);

        TurAIAgent agent = new TurAIAgent();
        agent.setId("agent-1");

        TurSNSiteGenAi genAi = new TurSNSiteGenAi();
        genAi.setId("genai-1");
        genAi.setSitePrompt("site description");
        genAi.setTurAIAgent(agent);
        site.setTurSNSiteGenAi(genAi);

        TurSNSiteExchange exchange = new TurSNSiteExchange(site);

        assertEquals("site-1", exchange.getId());
        assertEquals("Main Site", exchange.getName());
        assertTrue(exchange.isFacet());
        assertTrue(exchange.getHl());
        assertFalse(exchange.isMlt());
        assertTrue(exchange.isThesaurus());
        assertEquals("se-1", exchange.getTurSEInstance());
        assertNotNull(exchange.getTurSNSiteGenAi());
        assertEquals("genai-1", exchange.getTurSNSiteGenAi().getId());
        assertEquals("agent-1", exchange.getTurSNSiteGenAi().getTurAIAgent());
    }

    @Test
    void shouldHandleNullGenAiAndSeReference() {
        TurSNSite site = new TurSNSite();
        site.setId("site-2");
        site.setFacet(0);
        site.setHl(0);
        site.setMlt(1);
        site.setThesaurus(0);
        site.setTurSEInstance(null);
        site.setTurSNSiteGenAi(null);

        TurSNSiteExchange exchange = new TurSNSiteExchange(site);

        assertFalse(exchange.isFacet());
        assertFalse(exchange.getHl());
        assertTrue(exchange.isMlt());
        assertFalse(exchange.isThesaurus());
        assertNull(exchange.getTurSEInstance());
        assertNull(exchange.getTurSNSiteGenAi());
    }
}
