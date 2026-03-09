package com.viglet.turing.exchange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.turing.exchange.sn.TurSNSiteExchange;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.store.TurStoreInstance;

class TurExchangeTest {

    @Test
    void shouldSetAndGetAllCollections() {
        TurExchange exchange = new TurExchange();

        List<TurSNSiteExchange> snSites = List.of(new TurSNSiteExchange());
        List<TurLLMInstance> llm = List.of(new TurLLMInstance());
        List<TurStoreInstance> store = List.of(new TurStoreInstance());
        List<TurSEInstance> se = List.of(new TurSEInstance());

        exchange.setSnSites(snSites);
        exchange.setLlm(llm);
        exchange.setStore(store);
        exchange.setSe(se);

        assertSame(snSites, exchange.getSnSites());
        assertSame(llm, exchange.getLlm());
        assertSame(store, exchange.getStore());
        assertSame(se, exchange.getSe());
        assertEquals(1, exchange.getSnSites().size());
    }
}
