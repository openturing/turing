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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Pure-Groovy regression tests for the Executive Education
 * {@code aula_relampago} Custom Tool. This test boots a
 * {@link GroovyShell} with a {@link Binding} that mocks the
 * three helpers the script uses ({@code args}, {@code http}, {@code slots})
 * — same shape {@link com.viglet.turing.genai.tool.TurCustomToolCallbackService}
 * exposes in production, minus the live HTTP and DB.
 *
 * <p>No Spring boot, no DB. Each test runs in ~50 ms, so the suite is
 * green-friendly and runs on every {@code mvn test} without
 * {@code OPENAI_API_KEY} or a database container.
 *
 * <h2>What gets pinned here</h2>
 * <ul>
 *   <li><b>DSpace response shape</b> — the script reads
 *       {@code _embedded.searchResult._embedded.objects[N]._embedded.indexableObject.metadata}
 *       on every hit. Schema changes upstream would silently produce
 *       blank slots; tests pin the parse path.</li>
 *   <li><b>Quote heuristic</b> — picks the first sentence ≥40 chars
 *       containing the search term (case-insensitive), falls back to
 *       the longest sentence under 220 chars. Pins both branches.</li>
 *   <li><b>Mind-map composition</b> — Title-Case word frequency on
 *       the abstracts, drops stop-words + the search term itself,
 *       returns top-4 in Mermaid {@code mindmap} format. Pins
 *       deterministic behavior.</li>
 *   <li><b>Cargo bucketing</b> — regex-based difficulty hint
 *       (STRATEGIC / MANAGERIAL / FOUNDATIONAL / TACTICAL / GENERIC).
 *       Pins all 5 branches.</li>
 *   <li><b>Sector cascade</b> — {@code args.sector} →
 *       {@code area_label} slot → {@code area} slot → empty.</li>
 *   <li><b>Slot writes</b> — exactly 5 slots when DSpace returns hits
 *       ({@code aula_term}, {@code aula_sector}, {@code aula_lesson_pack},
 *       {@code aula_followups}, {@code aula_mindmap}). Mindmap slot
 *       skipped when there aren't ≥2 co-occurring concepts.</li>
 *   <li><b>Graceful degradation</b> — HTTP throws → degraded message,
 *       zero slot writes.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
class AulaRelampagoToolTest {

    private static final String SCRIPT_RESOURCE = "/customers/education/tools/aula-relampago.groovy";
    private static final String SCRIPT_SOURCE = loadResource();
    private static final ObjectMapper JSON = new ObjectMapper();

    private MockSlots slots;
    private MockHttp http;

    @BeforeEach
    void setUp() {
        slots = new MockSlots();
        http = new MockHttp();
    }

    // ─────────────────────── Happy path ───────────────────────

    @Test
    void happyPath_realOptions_writesAllFiveSlots() {
        slots.put("cargo_atual", "Gerente sênior numa fintech, há 4 anos");
        slots.put("area_label", "Finanças & Investimentos");
        http.cannedResponse = dspaceResponse(403, List.of(
                Map.of("title", "Determinantes do valor de opções de crescimento",
                        "author", "Almeida, Fábio Eirado De", "year", "2009-05-01",
                        "type", "master thesis", "subject", "Real options",
                        "abstract", "A teoria de opções reais tem recebido atenção. " +
                                "Os modelos de Real Options permitem decisões em cenários de incerteza. " +
                                "Aplicações em projetos industriais brasileiros são exploradas."),
                Map.of("title", "Análise de Investimento sob Incerteza",
                        "author", "Silva, Maria", "year", "2015-03",
                        "type", "thesis", "subject", "Investimento, decisão",
                        "abstract", "Este trabalho investiga Decisões de Investimento em Cenários incertos."),
                Map.of("title", "Avaliação de Empresas",
                        "author", "Costa, João", "year", "2018",
                        "type", "monograph", "subject", "Valuation",
                        "abstract", "Métodos de Valuation aplicados ao Mercado brasileiro.")));

        String markdown = runScript(Map.of("term", "Real Options"));

        // Five slot writes
        assertThat(slots.snapshot()).containsKeys(
                "aula_term", "aula_sector", "aula_lesson_pack",
                "aula_followups", "aula_mindmap");

        // Slot values
        assertThat(slots.get("aula_term")).isEqualTo("Real Options");
        assertThat(slots.get("aula_sector")).isEqualTo("Finanças & Investimentos");

        Map<String, Object> pack = parseJson(slots.get("aula_lesson_pack"));
        assertThat(pack)
                .containsEntry("term", "Real Options")
                .containsEntry("sector", "Finanças & Investimentos")
                .containsEntry("totalDspaceHits", 403)
                .containsEntry("difficultyLevel", "MANAGERIAL");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> refs = (List<Map<String, Object>>) pack.get("references");
        // Sort contract: abstract-bearing first (all 3 have abstracts here),
        // then year desc. Pinning the deterministic order — Costa 2018 →
        // Silva 2015 → Almeida 2009.
        assertThat(refs).hasSize(3);
        assertThat(refs).extracting(r -> r.get("author"))
                .containsExactly("Costa, João", "Silva, Maria", "Almeida, Fábio Eirado De");
        assertThat(refs).extracting(r -> r.get("year"))
                .containsExactly("2018", "2015", "2009");

        // Markdown body
        assertThat(markdown)
                .contains("Aula-relâmpago de 90 segundos: Real Options")
                .contains("Finanças & Investimentos")
                .contains("MANAGERIAL")
                .contains("403 referências");
    }

    // ─────────────────────── Cargo bucketing ───────────────────────

    @ParameterizedTest
    @CsvSource({
            "'CFO há 6 anos',                STRATEGIC",
            "'Diretora de Marketing',        STRATEGIC",
            "'sócio da consultoria',         STRATEGIC",
            "'Gerente sênior em fintech',    MANAGERIAL",
            "'Coordenadora de RH',           MANAGERIAL",
            "'Head de Estratégia',           MANAGERIAL",
            "'Analista Júnior',              FOUNDATIONAL",
            "'estagiário no jurídico',       FOUNDATIONAL",
            "'Trainee de produto',           FOUNDATIONAL",
            "'engenheira de software',       TACTICAL",
            "'professor adjunto',            TACTICAL",
            "'',                             GENERIC"
    })
    void cargoBucketing_appliesExpectedDifficulty(String cargo, String expectedLevel) {
        if (!cargo.isEmpty()) slots.put("cargo_atual", cargo);
        http.cannedResponse = dspaceResponse(10, List.of(
                Map.of("title", "Sample", "abstract", "An abstract.")));

        runScript(Map.of("term", "Six Sigma"));

        Map<String, Object> pack = parseJson(slots.get("aula_lesson_pack"));
        assertThat(pack).containsEntry("difficultyLevel", expectedLevel);
    }

    // ─────────────────────── Sector cascade ───────────────────────

    @Test
    void sectorCascade_argWins_overSlot() {
        slots.put("area_label", "Saúde");
        http.cannedResponse = dspaceResponse(5, List.of(
                Map.of("title", "Sample", "abstract", "Test.")));

        runScript(Map.of("term", "OKR", "sector", "Liderança & Gestão"));

        assertThat(slots.get("aula_sector")).isEqualTo("Liderança & Gestão");
    }

    @Test
    void sectorCascade_areaLabelSlot_winsOverAreaSlot() {
        slots.put("area_label", "Tecnologia & Dados");
        slots.put("area", "tecnologia"); // key — should lose to area_label
        http.cannedResponse = dspaceResponse(5, List.of(
                Map.of("title", "Sample", "abstract", "Test.")));

        runScript(Map.of("term", "RAG"));

        assertThat(slots.get("aula_sector")).isEqualTo("Tecnologia & Dados");
    }

    @Test
    void sectorCascade_fallsThroughToAreaKey_whenLabelMissing() {
        slots.put("area", "saude");
        http.cannedResponse = dspaceResponse(5, List.of(
                Map.of("title", "Sample", "abstract", "Test.")));

        runScript(Map.of("term", "HL7"));

        assertThat(slots.get("aula_sector")).isEqualTo("saude");
    }

    @Test
    void sectorCascade_emptyWhenNoSourceAvailable() {
        http.cannedResponse = dspaceResponse(5, List.of(
                Map.of("title", "Sample", "abstract", "Test.")));

        runScript(Map.of("term", "Generic Term"));

        // No aula_sector slot written when no source resolved
        assertThat(slots.snapshot()).doesNotContainKey("aula_sector");
    }

    // ─────────────────────── Quote heuristic ───────────────────────

    @Test
    void quoteHeuristic_picksSentenceMentioningTerm_caseInsensitive() {
        String abstractText =
                "Short intro. " +
                "The application of real options to capital budgeting decisions remains a controversial topic in corporate finance. " +
                "Another generic sentence here. " +
                "End.";
        http.cannedResponse = dspaceResponse(1, List.of(
                Map.of("title", "Paper", "abstract", abstractText)));

        runScript(Map.of("term", "Real Options"));

        Map<String, Object> pack = parseJson(slots.get("aula_lesson_pack"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> refs = (List<Map<String, Object>>) pack.get("references");
        assertThat((String) refs.get(0).get("quote"))
                .as("Quote must be the sentence that mentions the term (case-insensitive)")
                .containsIgnoringCase("real options")
                .startsWith("The application");
    }

    @Test
    void quoteHeuristic_fallsBackToLongestSentence_whenNoneContainsTerm() {
        String abstractText =
                "Short. " +
                "This is a moderately long sentence about valuation that doesn't quite reach the threshold of the longest one in this paragraph. " +
                "Brief end.";
        http.cannedResponse = dspaceResponse(1, List.of(
                Map.of("title", "Paper", "abstract", abstractText)));

        runScript(Map.of("term", "Real Options"));

        Map<String, Object> pack = parseJson(slots.get("aula_lesson_pack"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> refs = (List<Map<String, Object>>) pack.get("references");
        assertThat((String) refs.get(0).get("quote"))
                .as("Should fall back to the longest 60-220 char sentence")
                .startsWith("This is a moderately long");
    }

    // ─────────────────────── Mind-map composition ───────────────────────

    @Test
    void mindmap_dropsSearchTerm_andStopWords_topFourFrequency() {
        // Abstracts mention "Real Options" (search term, must drop), "the" (stop-word),
        // and 5 Title-Case concepts at varying frequencies. Top 4 should win.
        String abstract1 = "Real Options theory studies Investimento Decisão Estratégia. " +
                "The Decisão making process involves Real Options analysis.";
        String abstract2 = "Investimento decisions under Incerteza require Real Options framing. " +
                "Decisão criteria depend on Mercado conditions.";
        String abstract3 = "Mercado dynamics affect Decisão under Incerteza. " +
                "Investimento timing matters in Estratégia.";
        http.cannedResponse = dspaceResponse(3, List.of(
                Map.of("title", "Paper 1", "abstract", abstract1),
                Map.of("title", "Paper 2", "abstract", abstract2),
                Map.of("title", "Paper 3", "abstract", abstract3)));

        runScript(Map.of("term", "Real Options"));

        String mindmap = slots.get("aula_mindmap");
        assertThat(mindmap)
                .as("Must be Mermaid mindmap syntax")
                .startsWith("mindmap")
                .contains("root((Real Options))")
                // Search term and stop-words MUST NOT appear as branches
                .as("Search-term tokens excluded from branches")
                .doesNotContain("    Real\n")
                .doesNotContain("    Options\n")
                .doesNotContain("    the\n")
                // Top-4 frequent Title-Case concepts SHOULD appear (Decisão 4x, Investimento 3x, Mercado 2x, Estratégia 2x, Incerteza 2x)
                .as("Most frequent concepts appear as branches")
                .contains("Decisão")
                .contains("Investimento");
        // At most 4 branches under root
        long branchLines = mindmap.lines().filter(l -> l.startsWith("    ") && !l.contains("root(")).count();
        assertThat(branchLines)
                .as("Mindmap should have at most 4 branches")
                .isLessThanOrEqualTo(4);
    }

    @Test
    void mindmap_skipsSlot_whenFewerThanTwoConcepts() {
        // Abstract has zero Title-Case words (everything lowercased) — the
        // bag-of-nouns regex `[A-ZÁ-Ú][a-zá-ú]{3,}.*` rejects it all, so the
        // mindmap builder returns null and the slot stays unwritten.
        http.cannedResponse = dspaceResponse(1, List.of(
                Map.of("title", "Paper",
                       "abstract", "a very short abstract with only lowercase words and nothing capitalized.")));

        runScript(Map.of("term", "Six Sigma"));

        assertThat(slots.snapshot())
                .as("aula_mindmap slot not written when <2 distinct Title-Case concepts co-occur")
                .doesNotContainKey("aula_mindmap");
    }

    // ─────────────────────── References filtering ───────────────────────

    @Test
    void references_skipsHitsWithoutTitle_andPrefersAbstractBearing() {
        http.cannedResponse = dspaceResponse(20, List.of(
                Map.of("title", "(sem título)"), // gets filtered by the title guard
                Map.of("title", "Old paper, no abstract", "year", "1995"),
                Map.of("title", "Recent with abstract",
                       "year", "2024", "abstract", "Real Options applied. More text here makes the sentence longer than forty chars."),
                Map.of("title", "2020 paper with abstract",
                       "year", "2020", "abstract", "Another good Real Options abstract sentence longer than forty chars to qualify."),
                Map.of("title", "Older with abstract",
                       "year", "2015", "abstract", "Older Real Options abstract sentence longer than forty chars to qualify.")));

        runScript(Map.of("term", "Real Options"));

        Map<String, Object> pack = parseJson(slots.get("aula_lesson_pack"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> refs = (List<Map<String, Object>>) pack.get("references");

        assertThat(refs)
                .as("Top 3 references kept; '(sem título)' excluded")
                .hasSize(3)
                .extracting(r -> r.get("title"))
                .doesNotContain("(sem título)")
                .containsExactly("Recent with abstract", "2020 paper with abstract", "Older with abstract");
    }

    // ─────────────────────── Follow-up chips ───────────────────────

    @Test
    void followups_alwaysThreeChips_crossSectorChip_adaptsToSector() {
        slots.put("area_label", "Saúde");
        http.cannedResponse = dspaceResponse(5, List.of(
                Map.of("title", "Sample", "abstract", "Test.")));

        runScript(Map.of("term", "Lean Six Sigma"));

        List<String> followups = parseJsonAsList(slots.get("aula_followups"));
        assertThat(followups).hasSize(3);
        assertThat(followups.get(0))
                .as("Cross-sector chip swaps to a different sector (Saúde → Finanças by the static map)")
                .contains("Lean Six Sigma")
                .containsIgnoringCase("finanças");
        assertThat(followups.get(1)).contains("armadilha");
        assertThat(followups.get(2)).contains("caso brasileiro");
    }

    @Test
    void followups_fallbackChip_whenNoSector() {
        http.cannedResponse = dspaceResponse(5, List.of(
                Map.of("title", "Sample", "abstract", "Test.")));

        runScript(Map.of("term", "OKR"));

        List<String> followups = parseJsonAsList(slots.get("aula_followups"));
        assertThat(followups.get(0))
                .as("Cross-sector chip defaults to 'finanças' when sector is unknown")
                .contains("OKR")
                .containsIgnoringCase("finanças");
    }

    // ─────────────────────── Graceful degradation ───────────────────────

    @Test
    void dspaceUnavailable_returnsDegradedMessage_writesNoSlots() {
        http.throwOnCall = new RuntimeException("Connection refused");

        String markdown = runScript(Map.of("term", "Real Options"));

        assertThat(markdown)
                .as("Tool surfaces a clear degradation message instead of crashing the LLM turn")
                .contains("Acervo Education indisponível");
        assertThat(slots.snapshot())
                .as("Zero slot writes on degradation — caller sees no false success")
                .isEmpty();
    }

    @Test
    void emptyTerm_returnsEarlyMessage_writesNoSlots() {
        String markdown = runScript(Map.of("term", "  "));

        assertThat(markdown).contains("Sem termo");
        assertThat(slots.snapshot()).isEmpty();
    }

    @Test
    void noDspaceResults_emitsScaffoldWithoutReferences() {
        http.cannedResponse = dspaceResponse(0, List.of());

        String markdown = runScript(Map.of("term", "Obscure Term"));

        assertThat(markdown)
                .contains("Aula-relâmpago de 90 segundos: Obscure Term")
                .contains("acervo não retornou referências");
        // lesson_pack still written, but with empty references
        Map<String, Object> pack = parseJson(slots.get("aula_lesson_pack"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> refs = (List<Map<String, Object>>) pack.get("references");
        assertThat(refs).isEmpty();
    }

    // ─────────────────────── Helpers ───────────────────────

    private String runScript(Map<String, Object> args) {
        Binding binding = new Binding();
        binding.setVariable("args", args);
        binding.setVariable("slots", slots);
        binding.setVariable("http", http);
        // turingSearch is not used by aula_relampago but the production
        // binding always provides it — include a stub so the script's
        // implicit-reference path stays identical to runtime.
        binding.setVariable("turingSearch", new Object());
        GroovyShell shell = new GroovyShell(binding);
        Object result = shell.evaluate(SCRIPT_SOURCE);
        return result == null ? "" : result.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseJson(String src) {
        try {
            return JSON.readValue(src, new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            throw new AssertionError("Failed to parse JSON: " + src, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> parseJsonAsList(String src) {
        try {
            return JSON.readValue(src, new TypeReference<List<String>>() { });
        } catch (Exception e) {
            throw new AssertionError("Failed to parse JSON list: " + src, e);
        }
    }

    /**
     * Builds a canned DSpace {@code /discover/search/objects} response in
     * exactly the shape the live API returns — see
     * {@code https://repositorio-api.education.example.com/server/api/discover/search/objects?query=Real+Options}
     * for a sample. The script's parse path keys on
     * {@code _embedded.searchResult._embedded.objects[N]._embedded.indexableObject.metadata.dc.*[0].value}.
     */
    private static Map<String, Object> dspaceResponse(int totalElements,
            List<Map<String, String>> hits) {
        List<Object> objects = new ArrayList<>();
        for (Map<String, String> h : hits) {
            Map<String, Object> meta = new LinkedHashMap<>();
            putMeta(meta, "dc.title", h.get("title"));
            putMeta(meta, "dc.contributor.author", h.get("author"));
            putMeta(meta, "dc.date.issued", h.get("year"));
            putMeta(meta, "dc.type", h.get("type"));
            putMeta(meta, "dc.description.abstract", h.get("abstract"));
            putMeta(meta, "dc.subject", h.get("subject"));
            Map<String, Object> indexable = Map.of("metadata", meta);
            Map<String, Object> embedded = Map.of("indexableObject", indexable);
            objects.add(Map.of("_embedded", embedded));
        }
        Map<String, Object> pageInfo = Map.of("totalElements", totalElements);
        Map<String, Object> objectsEmbedded = Map.of("objects", objects);
        Map<String, Object> searchResult = Map.of(
                "page", pageInfo,
                "_embedded", objectsEmbedded);
        Map<String, Object> outerEmbedded = Map.of("searchResult", searchResult);
        return Map.of("_embedded", outerEmbedded);
    }

    private static void putMeta(Map<String, Object> meta, String key, String value) {
        if (value == null) return;
        meta.put(key, List.of(Map.of("value", value)));
    }

    private static String loadResource() {
        try (InputStream in = AulaRelampagoToolTest.class.getResourceAsStream(SCRIPT_RESOURCE)) {
            Objects.requireNonNull(in, "Missing classpath resource: " + SCRIPT_RESOURCE);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read fixture: " + SCRIPT_RESOURCE, e);
        }
    }

    /** Map-backed slot helper — mirrors {@code TurCustomToolSlotHelper}'s surface. */
    static class MockSlots {
        private final Map<String, String> map = new LinkedHashMap<>();

        public String get(String name) {
            return map.get(name);
        }

        public void set(String name, String value) {
            map.put(name, value);
        }

        // For test setup — pre-seed a slot value before invoking the script.
        void put(String name, String value) {
            map.put(name, value);
        }

        Map<String, String> snapshot() {
            return Map.copyOf(map);
        }
    }

    /** HTTP helper double — single canned response (or thrown exception). */
    static class MockHttp {
        Object cannedResponse;
        RuntimeException throwOnCall;

        public Object getJson(String url) {
            if (throwOnCall != null) throw throwOnCall;
            return cannedResponse;
        }

        public Object getJson(String url, Map<String, String> headers) {
            return getJson(url);
        }
    }
}
