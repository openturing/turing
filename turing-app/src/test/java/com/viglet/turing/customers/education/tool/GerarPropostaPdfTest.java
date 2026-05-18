/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.customers.education.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;

/**
 * Pure-Groovy regression tests for the Executive Education
 * {@code gerar_proposta_pdf} Custom Tool — primarily pinning the
 * idempotency-by-hash short-circuit added in 2026.2.7. This test boots
 * a {@link GroovyShell} with a {@link Binding} that mocks {@code slots}
 * + {@code code} (the Python sandbox helper).
 *
 * <p>No Spring boot, no real Python execution, no DB. Each test runs
 * in ~30 ms — the test pins the Groovy-side caching behavior without
 * the cost of actually invoking the platform's
 * {@link com.viglet.turing.genai.tool.TurCodeInterpreterToolService}.
 *
 * <h2>What gets pinned here</h2>
 * <ul>
 *   <li><b>First call generates fresh</b> — invokes
 *       {@code code.executePython}, writes 3 slots
 *       ({@code proposta_pdf_url}, {@code proposta_pdf_filename},
 *       {@code proposta_pdf_hash}).</li>
 *   <li><b>Second call with identical slots short-circuits</b> — does
 *       NOT call {@code code.executePython}, returns the cached
 *       message, slot values stay intact.</li>
 *   <li><b>Slot change invalidates the cache</b> — any input slot
 *       difference produces a new hash, so the script re-runs.</li>
 *   <li><b>Failed Python run does NOT write the hash</b> — next call
 *       still re-runs, since the hash slot stayed empty.</li>
 *   <li><b>Cached hash without URL doesn't short-circuit</b> —
 *       defensive guard for the rare case where a previous run wrote
 *       the hash but the URL slot was cleared (e.g. by admin reset).</li>
 *   <li><b>Hash format is MD5 hex (32 chars)</b> — deterministic
 *       fingerprint, pin the contract for future tooling that might
 *       inspect the slot value.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
class GerarPropostaPdfTest {

    private static final String SCRIPT_RESOURCE = "/customers/education/tools/gerar-proposta-pdf.groovy";
    private static final String SCRIPT_SOURCE = loadResource();

    /**
     * Canned markdown response the platform's
     * {@link com.viglet.turing.genai.tool.TurCodeInterpreterToolService}
     * emits when a Python script writes a non-image file. The regex in
     * the tool's success path keys on
     * {@code [Download {filename}]({url}.pdf)} — match this shape
     * exactly so the parse path is identical to production.
     */
    private static final String CANNED_PYTHON_OK_OUTPUT =
            "PDF gerado: proposta-carreira-alexandre.pdf\n" +
            "Tamanho: 78924 bytes\n" +
            "\n" +
            "--- Generated Files ---\n" +
            "[Download proposta-carreira-alexandre.pdf](/api/v2/code-interpreter/abc123/proposta-carreira-alexandre.pdf)\n";

    private MockSlots slots;
    private MockCode code;

    @BeforeEach
    void setUp() {
        slots = new MockSlots();
        code = new MockCode();
        code.cannedOutput = CANNED_PYTHON_OK_OUTPUT;
        // Realistic slot snapshot: visitor finished the 4-turn flow,
        // both Marina template slots are populated, share_url is set.
        slots.put("name", "Alexandre");
        slots.put("cargo_atual", "Gerente sênior numa fintech, há 4 anos");
        slots.put("objetivo", "Virar CFO em 3 anos");
        slots.put("area", "financas");
        slots.put("area_label", "Finanças & Investimentos");
        slots.put("final_pitch", "Alexandre, com seu perfil...");
        slots.put("stat_pitch", "78% dos alunos da trilha...");
        slots.put("share_url", "https://ee.education.example.com/programa-match?ref=Alexandre&area=financas");
        slots.put("programas_match", "[{\"name\":\"Especialização em Valuation\",\"priceFrom\":\"R$ 18.900\"}]");
        slots.put("career_path", "[{\"step\":\"HOJE\",\"label\":\"Gerente sênior\"}]");
    }

    // ─────────────────────── First-call generation ───────────────────────

    @Test
    void firstCall_runsPython_writesAllThreeSlots() {
        String markdown = runScript();

        assertThat(code.invocationCount)
                .as("First call must hit the Python sandbox")
                .isEqualTo(1);
        assertThat(slots.get("proposta_pdf_url"))
                .isEqualTo("/api/v2/code-interpreter/abc123/proposta-carreira-alexandre.pdf");
        assertThat(slots.get("proposta_pdf_filename"))
                .isEqualTo("proposta-carreira-alexandre.pdf");
        assertThat(slots.get("proposta_pdf_hash"))
                .as("Hash slot persisted after successful run — primes cache for next call")
                .isNotBlank();
        assertThat(markdown)
                .contains("Alexandre")
                .contains("Plano de Carreira")
                .doesNotContain("já está pronto") // first-call wording
                .doesNotContain("anteriormente");
    }

    @Test
    void firstCall_hashSlot_isMd5HexFormat() {
        runScript();

        String hash = slots.get("proposta_pdf_hash");
        assertThat(hash)
                .as("MD5 hex digest must be 32 lowercase hex chars")
                .hasSize(32)
                .matches("[0-9a-f]{32}");
    }

    // ─────────────────────── Cache hit (idempotency) ───────────────────────

    @Test
    void secondCall_sameSlots_shortCircuits_doesNotInvokePython() {
        // First run primes the cache.
        runScript();
        assertThat(code.invocationCount).isEqualTo(1);
        String firstHash = slots.get("proposta_pdf_hash");
        String firstUrl = slots.get("proposta_pdf_url");

        // Second run — same slots; must NOT call the sandbox.
        String markdown = runScript();

        assertThat(code.invocationCount)
                .as("Cache hit MUST skip the Python sandbox")
                .isEqualTo(1);
        assertThat(slots.get("proposta_pdf_hash"))
                .as("Hash slot unchanged on cache hit")
                .isEqualTo(firstHash);
        assertThat(slots.get("proposta_pdf_url"))
                .as("URL slot unchanged on cache hit (portal keeps showing the same button)")
                .isEqualTo(firstUrl);
        assertThat(markdown)
                .as("Cache-hit message hints that the PDF was generated previously")
                .contains("já está pronto")
                .contains("anteriormente");
    }

    @Test
    void thirdAndFourthCalls_stillHitCache_zeroPythonInvocations() {
        runScript();
        runScript();
        runScript();
        runScript();

        assertThat(code.invocationCount)
                .as("Repeated identical calls hit cache after the first — Python is invoked exactly once")
                .isEqualTo(1);
    }

    // ─────────────────────── Cache invalidation ───────────────────────

    @Test
    void slotChange_invalidatesCache_reRunsPython() {
        runScript(); // primes cache with cargo_atual = "Gerente sênior..."
        assertThat(code.invocationCount).isEqualTo(1);
        String oldHash = slots.get("proposta_pdf_hash");

        // Visitor edits a slot mid-flight (e.g. corrects their cargo via
        // the SlotInspector debug panel, or chips re-fire). The new hash
        // must invalidate the cache.
        slots.put("cargo_atual", "Diretora de Finanças (revised)");

        runScript();

        assertThat(code.invocationCount)
                .as("Slot change → new hash → cache miss → fresh Python run")
                .isEqualTo(2);
        assertThat(slots.get("proposta_pdf_hash"))
                .as("Hash slot updated to reflect new inputs")
                .isNotEqualTo(oldHash);
    }

    @Test
    void areaSlotChange_alsoInvalidates_evenIfOtherSlotsStable() {
        runScript();
        slots.put("area", "tecnologia");
        slots.put("area_label", "Tecnologia & Dados");

        runScript();

        assertThat(code.invocationCount).isEqualTo(2);
    }

    @Test
    void programsMatchSlotChange_invalidates_evenIfVisualUnchanged() {
        // JSON slot subtly differs (different price formatting) → hash
        // must catch it even though the cards look the same to a human.
        runScript();
        slots.put("programas_match", "[{\"name\":\"Especialização em Valuation\",\"priceFrom\":\"R$ 19.500\"}]");

        runScript();

        assertThat(code.invocationCount).isEqualTo(2);
    }

    // ─────────────────────── Failure does NOT poison cache ───────────────────────

    @Test
    void pythonFailure_doesNotWriteHash_nextCallRetriesFresh() {
        // First call: simulate a Python crash (script returned no
        // "[Download ...]" marker).
        code.cannedOutput = "Error: reportlab not installed\nImportError: No module named 'reportlab'";

        String firstResult = runScript();

        assertThat(code.invocationCount).isEqualTo(1);
        assertThat(firstResult).contains("Não consegui gerar o PDF");
        assertThat(slots.get("proposta_pdf_url"))
                .as("Failed run clears the URL slot defensively")
                .isEmpty();
        assertThat(slots.get("proposta_pdf_hash"))
                .as("Hash slot stays unset on failure — next call MUST re-attempt")
                .isNull();

        // Second call: env fixed, Python now works → still re-runs because
        // the hash wasn't poisoned by the failure.
        code.cannedOutput = CANNED_PYTHON_OK_OUTPUT;
        String secondResult = runScript();

        assertThat(code.invocationCount).isEqualTo(2);
        assertThat(secondResult).contains("Plano de Carreira").contains("está pronto");
        assertThat(slots.get("proposta_pdf_hash"))
                .as("Hash slot finally populated after the successful retry")
                .isNotBlank();
    }

    // ─────────────────────── Defensive cache-hit guards ───────────────────────

    @Test
    void cacheGuard_hashWithoutUrl_treatsAsMiss() {
        // Edge case: somehow the hash slot was set but URL was cleared
        // (e.g. admin reset the proposta_pdf_url manually to invalidate
        // the link). The script must NOT trust the lonely hash — it
        // should re-generate.
        runScript(); // primes both hash + url
        slots.put("proposta_pdf_url", ""); // simulate URL-only reset

        runScript();

        assertThat(code.invocationCount)
                .as("Hash without URL is treated as cache miss — script re-runs")
                .isEqualTo(2);
    }

    @Test
    void cacheGuard_noPriorHash_treatsAsMiss() {
        // Fresh conversation — neither slot set. First call must run.
        assertThat(slots.get("proposta_pdf_hash")).isNull();

        runScript();

        assertThat(code.invocationCount).isEqualTo(1);
    }

    // ─────────────────────── Helpers ───────────────────────

    private String runScript() {
        Binding binding = new Binding();
        binding.setVariable("args", Map.of());
        binding.setVariable("slots", slots);
        binding.setVariable("code", code);
        // http + turingSearch unused by this tool but production binding
        // always provides them — include stubs so the script's implicit
        // reference path stays identical to runtime.
        binding.setVariable("http", new Object());
        binding.setVariable("turingSearch", new Object());
        GroovyShell shell = new GroovyShell(binding);
        Object result = shell.evaluate(SCRIPT_SOURCE);
        return result == null ? "" : result.toString();
    }

    private static String loadResource() {
        try (InputStream in = GerarPropostaPdfTest.class.getResourceAsStream(SCRIPT_RESOURCE)) {
            Objects.requireNonNull(in, "Missing classpath resource: " + SCRIPT_RESOURCE);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read fixture: " + SCRIPT_RESOURCE, e);
        }
    }

    /** Map-backed slot helper — mirrors {@code TurCustomToolSlotHelper}. */
    static class MockSlots {
        private final Map<String, String> map = new LinkedHashMap<>();

        public String get(String name) {
            return map.get(name);
        }

        public void set(String name, String value) {
            map.put(name, value == null ? "" : value);
        }

        void put(String name, String value) {
            map.put(name, value);
        }
    }

    /**
     * {@code code.executePython(...)} stub. Counts invocations so tests
     * can assert short-circuit behavior; returns canned markdown output
     * matching what the platform's
     * {@link com.viglet.turing.genai.tool.TurCodeInterpreterToolService}
     * emits in production.
     */
    static class MockCode {
        String cannedOutput;
        int invocationCount;
        List<String> capturedScripts = new ArrayList<>();

        public String executePython(String script) {
            invocationCount++;
            capturedScripts.add(script);
            return cannedOutput;
        }
    }
}
