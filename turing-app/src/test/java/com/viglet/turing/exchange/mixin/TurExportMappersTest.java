package com.viglet.turing.exchange.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.turing.exchange.TurExchange;
import com.viglet.turing.persistence.model.embedding.TurEmbeddingModel;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.mcp.TurMcpServer;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.store.TurStoreInstance;

import tools.jackson.databind.json.JsonMapper;

/**
 * T373 / §XIV.8.7 — an export bundle must never carry the owning
 * {@code tenantId} of a BYO-infra instance.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurExportMappersTest {

    private static final String OWNER = "tenant-a";

    private static TurExchange tenantOwnedEnvelope() {
        TurSEInstance se = new TurSEInstance();
        se.setId("se-1");
        se.setTitle("SE");
        se.setTenantId(OWNER);

        TurLLMInstance llm = new TurLLMInstance();
        llm.setId("llm-1");
        llm.setTitle("LLM");
        llm.setTenantId(OWNER);

        TurStoreInstance store = new TurStoreInstance();
        store.setId("store-1");
        store.setTitle("Store");
        store.setTenantId(OWNER);

        TurEmbeddingModel embedding = new TurEmbeddingModel();
        embedding.setId("emb-1");
        embedding.setModelName("m");
        embedding.setTenantId(OWNER);

        TurMcpServer mcp = new TurMcpServer();
        mcp.setId("mcp-1");
        mcp.setTenantId(OWNER);

        TurExchange envelope = new TurExchange();
        envelope.setSe(List.of(se));
        envelope.setLlm(List.of(llm));
        envelope.setStore(List.of(store));
        envelope.setEmbeddingModels(List.of(embedding));
        envelope.setMcpServers(List.of(mcp));
        return envelope;
    }

    @Test
    void exportMapperDropsTenantIdFromEveryByoInfraType() {
        String json = TurExportMappers.exportMapperBuilder().build()
                .writeValueAsString(tenantOwnedEnvelope());

        assertFalse(json.contains("tenantId"),
                () -> "export bundle leaked a tenantId:\n" + json);
        assertFalse(json.contains(OWNER),
                () -> "export bundle leaked the owning tenant's id:\n" + json);
    }

    /**
     * The negative control that gives the assertion above its teeth: the very
     * same envelope through a plain mapper <em>does</em> emit {@code tenantId},
     * so a green first test proves the mixin is doing the work and not that the
     * field was absent all along.
     */
    @Test
    void plainMapperStillLeaksTenantId_soTheAssertionAboveIsNotVacuous() {
        String json = JsonMapper.builder().build().writeValueAsString(tenantOwnedEnvelope());

        assertTrue(json.contains("tenantId"), "fixture no longer carries a tenantId at all");
        assertTrue(json.contains(OWNER), "fixture no longer carries the owning tenant's id");
    }

    @Test
    void exportMapperStillEmitsTheRestOfTheInstance() {
        String json = TurExportMappers.exportMapperBuilder().build()
                .writeValueAsString(tenantOwnedEnvelope());

        assertTrue(json.contains("\"se-1\""), json);
        assertTrue(json.contains("\"llm-1\""), json);
        assertTrue(json.contains("\"store-1\""), json);
        assertTrue(json.contains("\"emb-1\""), json);
        assertTrue(json.contains("\"mcp-1\""), json);
    }

    @Test
    void coversAllSixTenantOwnedInfraTypes() {
        assertEquals(6, TurExportMappers.tenantOwnedInfraTypes().length,
                "VigletTenantOwnedInfra gained or lost an implementation — "
                        + "the export mapper must be kept in step");
    }
}
