package com.viglet.turing.exchange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

/**
 * T373 / §XIV.8.7 — guards {@code seed/defaults/export.json}, the payload the
 * Docker build zips into {@code /app/export/defaults.zip} and
 * {@code TurExportImportOnStartup} imports on the first boot of a fresh
 * database. A hand-edit that adds a {@code tenantId}, or that breaks the shape
 * the importer needs, would otherwise only be found in a deployed container.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurSeedBundleTest {

    /** {@code turing-app/} at test time; the seed lives at the repo root. */
    private static Path seedFile() {
        return Paths.get(System.getProperty("user.dir"))
                .resolve("..").resolve("seed").resolve("defaults").resolve("export.json")
                .normalize();
    }

    private static String seedJson() throws Exception {
        Path seed = seedFile();
        assertTrue(Files.isRegularFile(seed), () -> "seed bundle missing at " + seed);
        return Files.readString(seed, StandardCharsets.UTF_8);
    }

    @Test
    void seedCarriesNoTenantIdSoItLandsGlobal() throws Exception {
        String json = seedJson();

        assertFalse(json.contains("tenantId"),
                "the seed must own no tenant: a non-null tenantId survives import verbatim "
                        + "and the instance ends up invisible to every tenant");
    }

    @Test
    void seedParsesIntoAnEnvelopeTheImporterCanUse() throws Exception {
        TurExchange envelope = JsonMapper.builder().build()
                .readValue(seedJson(), TurExchange.class);

        // No site and no agent: the branch TurImportExchange routes to
        // importInfraOnly. If this ever gains a site the routing changes.
        assertNull(envelope.getSnSites(), "seed is infra-only by design");
        assertNull(envelope.getAgents(), "seed is infra-only by design");

        assertNotNull(envelope.getSe());
        assertEquals(1, envelope.getSe().size());
        var se = envelope.getSe().getFirst();
        assertNull(se.getTenantId(), "seeded SE instance must be GLOBAL");
        // resolveOrCreateSEInstance skips creation without all three.
        assertNotNull(se.getId());
        assertNotNull(se.getTitle());
        assertNotNull(se.getEndpointUrl());
        assertNotNull(se.getTurSEVendor(), "SE vendor is required — creation is skipped without it");
        assertEquals("LUCENE", se.getTurSEVendor().getId(), "seed must need no external search engine");

        assertNotNull(envelope.getStore());
        assertEquals(1, envelope.getStore().size());
        var store = envelope.getStore().getFirst();
        assertNull(store.getTenantId(), "seeded store instance must be GLOBAL");
        // resolveOrCreateStoreInstance skips creation without all three.
        assertNotNull(store.getId());
        assertNotNull(store.getTitle());
        assertNotNull(store.getUrl());
        assertNotNull(store.getTurStoreVendor());
        assertEquals("LUCENE", store.getTurStoreVendor().getId());
    }

    @Test
    void seedShipsNoLlmBecauseItsApiKeyCouldNotDecryptAnywhereElse() throws Exception {
        TurExchange envelope = JsonMapper.builder().build()
                .readValue(seedJson(), TurExchange.class);

        assertNull(envelope.getLlm(),
                "an exported LLM carries its key encrypted with the source install's "
                        + "TurSecretCryptoService key; the default LLM comes from the environment");
    }
}
