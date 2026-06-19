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
 * Pure-Groovy regression tests for the Executive Education B2B
 * {@code compor_proposta_in_company} Custom Tool — the deterministic
 * pricing-and-modules composer behind the {@code in-company-match}
 * chat-flow. This test boots a {@link GroovyShell} with a {@link Binding} that mocks
 * {@code slots} + {@code turingSearch} (the script's only two helpers)
 * — same shape
 * {@link com.viglet.turing.genai.tool.TurCustomToolCallbackService} exposes
 * in production.
 *
 * <p>No Spring boot, no DB, no LLM. Each test runs in ~30 ms — the
 * tool is 100% deterministic by design (pricing tables, module catalogs,
 * cronograma profiles) and the test pins that contract.
 *
 * <h2>What gets pinned here</h2>
 * <ul>
 *   <li><b>4-tier pricing table</b> — small (≤14) · medium (≤49) · large
 *       (≤99) · enterprise (100+). Pricing per head + discount % + carga
 *       horária + formato sugerido per tier. Test boundaries (14↔15,
 *       49↔50, 99↔100) since they're the most likely regression points.</li>
 *   <li><b>Tolerant `num_pessoas` parsing</b> — accepts "15", "15 pessoas",
 *       "~60", "30-40" (takes lower bound), blank (default 15).</li>
 *   <li><b>Tema normalization</b> — regex on the free-text tema slot
 *       picks one of 5 catalog keys (financas / lideranca / saude /
 *       tecnologia / outros). Pins the regex against representative
 *       inputs per branch.</li>
 *   <li><b>Module catalog</b> — 5 trilhas × 3 modules each. Enterprise
 *       tier adds a 4th "Projeto Aplicado" module; small tier drops the
 *       3rd executive module. Carga total derived from the sum.</li>
 *   <li><b>Cronograma adaptativo</b> — 4 schedule profiles based on
 *       prazo: urgente (fast-track flag, 4 day-based marcos) · trimestre
 *       (4 week-based marcos) · semestre (4 month-based marcos + pré-
 *       trabalho) · flex (2 scenarios).</li>
 *   <li><b>Casos similares</b> — `turingSearch.ann` happy path, empty
 *       result fallback to static cases by setor, exception fallback to
 *       static cases by setor (the helper is best-effort and must not
 *       block the proposal).</li>
 *   <li><b>Share URL composition</b> — empresa + tema + pessoas params,
 *       URL-encoded.</li>
 *   <li><b>Slot writes</b> — exactly 7 slots ({@code proposta_in_company},
 *       {@code tier}, {@code tier_label}, {@code tema_normalized},
 *       {@code prazo_label}, {@code share_url_b2b}, {@code cta_visible}).</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
class ComporPropostaInCompanyTest {

    private static final String SCRIPT_RESOURCE = "/customers/education/tools/compor-proposta-in-company.groovy";
    private static final String SCRIPT_SOURCE = loadResource();
    private static final ObjectMapper JSON = new ObjectMapper();

    private MockSlots slots;
    private MockTuringSearch turingSearch;

    @BeforeEach
    void setUp() {
        slots = new MockSlots();
        turingSearch = new MockTuringSearch();
        // Default: turingSearch returns empty (no casos in the index yet).
        // Tests override this when they want to exercise the index-hit path.
        turingSearch.cannedHits = List.of();
    }

    // ─────────────────────── Tier bucketing — boundaries ───────────────────────

    @ParameterizedTest
    @CsvSource({
            // numPessoas | expectedTier | tierLabel                | basePrice | discount
            "5,            small,         'Workshop turma aberta',   2500,       0",
            "14,           small,         'Workshop turma aberta',   2500,       0",
            "15,           medium,        'Programa customizado',    2000,       20",
            "30,           medium,        'Programa customizado',    2000,       20",
            "49,           medium,        'Programa customizado',    2000,       20",
            "50,           large,         'In-company dedicado',     1625,       35",
            "75,           large,         'In-company dedicado',     1625,       35",
            "99,           large,         'In-company dedicado',     1625,       35",
            "100,          enterprise,    'Programa estruturado',    1300,       48",
            "500,          enterprise,    'Programa estruturado',    1300,       48",
    })
    void tierBucketing_pricingTable_pinsAllFourBuckets(
            int numPessoas, String tier, String tierLabel, int basePrice, int discount) {
        seedBriefing(numPessoas, "Liderança", "trimestre");

        runScript();

        Map<String, Object> proposta = parseJson(slots.get("proposta_in_company"));
        assertThat(proposta)
                .containsEntry("tier", tier)
                .containsEntry("tierLabel", tierLabel)
                .containsEntry("precoPorPessoa", basePrice)
                .containsEntry("descontoPercentual", discount)
                .containsEntry("numPessoas", numPessoas);

        // precoTotal = numPessoas × basePrice (verified manually)
        int expectedTotal = numPessoas * basePrice;
        assertThat(proposta).containsEntry("precoTotal", expectedTotal);
        assertThat((String) proposta.get("precoTotalLabel"))
                .as("Price formatted in pt-BR locale (R$ X.XXX,XX)")
                .startsWith("R$ ");
    }

    @Test
    void priceFormatting_brLocale_thirtyFivePeopleMediumTier() {
        seedBriefing(35, "IA generativa", "trimestre");

        runScript();

        Map<String, Object> proposta = parseJson(slots.get("proposta_in_company"));
        // 35 × R$ 2.000 = R$ 70.000,00
        assertThat(proposta).containsEntry("precoTotal", 70_000);
        assertThat((String) proposta.get("precoTotalLabel"))
                .matches("R\\$ 70\\.000,00");
    }

    @Test
    void priceFormatting_brLocale_enterpriseTier() {
        seedBriefing(120, "Transformação digital", "semestre");

        runScript();

        Map<String, Object> proposta = parseJson(slots.get("proposta_in_company"));
        // 120 × R$ 1.300 = R$ 156.000,00
        assertThat(proposta).containsEntry("precoTotal", 156_000);
        assertThat((String) proposta.get("precoTotalLabel"))
                .matches("R\\$ 156\\.000,00");
    }

    // ─────────────────────── Tolerant num_pessoas parsing ───────────────────────

    @ParameterizedTest
    @CsvSource({
            "'35',                  35",   // bare integer
            "'35 pessoas',          35",   // suffix
            "'cerca de 60',         60",   // prefix prose
            "'~60',                 60",   // tilde
            "'30-40',               30",   // range → lower bound
            "'uns 20',              20",   // colloquial
            "'',                    15",   // empty → safe default (medium tier)
            "'mais de 100',        100",   // first match wins
            "'15-49 pessoas',       15",   // first int
    })
    void numPessoasParsing_acceptsAllToleratedShapes(String input, int expected) {
        slots.put("razao_social", "Acme S.A.");
        slots.put("num_pessoas", input);
        slots.put("tema", "Liderança");
        slots.put("prazo", "trimestre");

        runScript();

        Map<String, Object> proposta = parseJson(slots.get("proposta_in_company"));
        assertThat(proposta).containsEntry("numPessoas", expected);
    }

    // ─────────────────────── Tema normalization (regex per branch) ───────────────────────

    @ParameterizedTest
    @CsvSource({
            // The script's regex bank is `financas → lideranca → saude → tecnologia`
            // (first match wins). Diacritics are stripped before matching,
            // so accented Portuguese inputs hit the same prefix patterns
            // as unaccented ones — "Finanças" / "Financeiro", "Gestão" /
            // "gestao", "Saúde" / "saude" all converge.
            //
            // Short ambiguous tokens (`\bia\b`, `\bai\b`, `\bml\b`, `\bdata\b`)
            // get word boundaries to prevent false positives — "auditoria"
            // and "compliance" used to wrongly land in tecnologia because
            // they contain the substring "ia"; with boundaries they fall
            // through to outros as expected.
            //
            // Free-text tema | expected key in catalog
            "'Valuation e M&A',                          financas",  // valuat
            "'Finanças corporativas',                    financas",  // financ via accent-stripped "financas"
            "'Análise de investimento',                  financas",  // invest
            "'controladoria',                            financas",  // controlador
            "'CFO development',                          financas",  // cfo
            "'Liderança Servidora',                      lideranca", // lider
            "'gestão de equipes',                        lideranca", // gestao via accent-stripped
            "'People management',                        lideranca", // people
            "'cultura organizacional',                   lideranca", // cultura
            "'coaching executivo',                       lideranca", // coach
            "'Gestão hospitalar',                        lideranca", // gestao wins BEFORE saude (first-match)
            "'hospital terciário',                       saude",     // hospital (no lideranca trigger)
            "'clínica oncológica',                       saude",     // clinic via accent-stripped
            "'farmacologia aplicada',                    saude",     // farmac
            "'Saúde mental no trabalho',                 saude",     // saud via accent-stripped
            "'IA generativa',                            tecnologia", // \bia\b — standalone "ia"
            "'Tecnologia & Dados',                       tecnologia", // tecnologi
            "'machine learning',                         tecnologia", // machine
            "'Cloud computing',                          tecnologia", // cloud
            "'Transformação digital',                    tecnologia", // digital
            // GOTCHA PINS — these used to fail before the regex fix (\b on ia/ai/ml/data)
            "'auditoria fiscal',                         outros",    // 'ia' inside "auditoria" must NOT trigger tecnologia
            "'Compliance regulatório',                   outros",    // 'ia' inside "compliance" must NOT trigger tecnologia
            "'país natal',                               outros",    // 'ai' inside "pais" must NOT trigger tecnologia
            "'atualização de processos',                 outros",    // 'data' inside "atualizacao" (after accent strip) must NOT trigger
            "'ESG corporativo',                          outros",    // plain outros — no regex catch
            "'',                                         outros",    // empty short-circuit
    })
    void temaNormalization_regexHitsExpectedCatalogKey(String tema, String expectedKey) {
        seedBriefing(20, tema, "trimestre");

        runScript();

        Map<String, Object> proposta = parseJson(slots.get("proposta_in_company"));
        assertThat(proposta).containsEntry("temaKey", expectedKey);
        assertThat(slots.get("tema_normalized")).isEqualTo(expectedKey);
    }

    // ─────────────────────── Module catalog & overrides ───────────────────────

    @Test
    void modules_tecnologiaCatalog_hasThreeStandardModules() {
        seedBriefing(30, "IA generativa", "trimestre");

        runScript();

        Map<String, Object> proposta = parseJson(slots.get("proposta_in_company"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> mods = (List<Map<String, Object>>) proposta.get("modulos");
        assertThat(mods).hasSize(3);
        assertThat(mods).extracting(m -> m.get("nome"))
                .containsExactly(
                        "IA Generativa para Negócios",
                        "Workshop Hands-on (dados da empresa)",
                        "Roadmap de Adoção de IA");
        // Carga total = 16+12+4 = 32h
        assertThat(proposta).containsEntry("cargaTotal", "32h");
    }

    @Test
    void modules_enterpriseTierAddsProjectoAplicado() {
        seedBriefing(150, "Liderança", "trimestre");

        runScript();

        Map<String, Object> proposta = parseJson(slots.get("proposta_in_company"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> mods = (List<Map<String, Object>>) proposta.get("modulos");
        assertThat(mods)
                .as("Enterprise tier adds a 4th 'Projeto Aplicado com mentoria' module")
                .hasSize(4);
        assertThat(mods.get(3))
                .containsEntry("nome", "Projeto Aplicado com mentoria")
                .containsEntry("carga", "8h");
        // Carga total = 16+12+8+8 = 44h
        assertThat(proposta).containsEntry("cargaTotal", "44h");
    }

    @Test
    void modules_smallTierDropsExecutiveModule() {
        seedBriefing(10, "Saúde", "trimestre");

        runScript();

        Map<String, Object> proposta = parseJson(slots.get("proposta_in_company"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> mods = (List<Map<String, Object>>) proposta.get("modulos");
        assertThat(mods)
                .as("Small tier (workshop turma aberta) drops the 3rd executive module")
                .hasSize(2);
        // Carga total = 16+12 = 28h (no executive bloc)
        assertThat(proposta).containsEntry("cargaTotal", "28h");
    }

    @Test
    void modules_outrosTema_fallsBackToCustomizado() {
        seedBriefing(25, "Compliance regulatório", "trimestre");

        runScript();

        Map<String, Object> proposta = parseJson(slots.get("proposta_in_company"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> mods = (List<Map<String, Object>>) proposta.get("modulos");
        assertThat(mods).hasSize(3);
        assertThat(mods).extracting(m -> m.get("nome"))
                .as("'outros' tema falls back to the generic customizado catalog")
                .containsExactly(
                        "Trilha customizada — Bloco 1",
                        "Trilha customizada — Bloco 2",
                        "Sessão de fechamento executivo");
    }

    // ─────────────────────── Cronograma adaptativo (4 profiles) ───────────────────────

    @ParameterizedTest
    @CsvSource({
            "urgente,                ≤ 30 dias (Fast-track),  true",
            "'30 dias',              ≤ 30 dias (Fast-track),  true",
            "trimestre,              ≤ 3 meses,                false",
            "'90',                   ≤ 3 meses,                false",
            "semestre,               ≤ 6 meses,                false",
            "'6 meses',              ≤ 6 meses,                false",
            "'180',                  ≤ 6 meses,                false",
            "flex,                   Flexível,                 false",
            "'qualquer outra coisa', Flexível,                 false",
    })
    void cronograma_prazoBucketing_pinsAllFourProfiles(
            String prazoInput, String expectedLabel, boolean expectedFastTrack) {
        seedBriefing(40, "Liderança", prazoInput);

        runScript();

        Map<String, Object> proposta = parseJson(slots.get("proposta_in_company"));
        assertThat(proposta)
                .containsEntry("prazoLabel", expectedLabel)
                .containsEntry("fastTrack", expectedFastTrack);
    }

    @Test
    void cronograma_urgente_hasFourDayBasedMarcos() {
        seedBriefing(20, "Liderança", "urgente");

        runScript();

        Map<String, Object> proposta = parseJson(slots.get("proposta_in_company"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> crono = (List<Map<String, Object>>) proposta.get("cronograma");
        assertThat(crono).hasSize(4);
        assertThat(crono).extracting(m -> m.get("quando"))
                .as("Urgente schedule uses day-based windows for paralelism")
                .containsExactly("Dias 1-3", "Dias 5-12", "Dias 13-25", "Dias 26-30");
    }

    @Test
    void cronograma_semestre_includesPreTrabalho() {
        seedBriefing(60, "Tecnologia", "semestre");

        runScript();

        Map<String, Object> proposta = parseJson(slots.get("proposta_in_company"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> crono = (List<Map<String, Object>>) proposta.get("cronograma");
        assertThat(crono).hasSize(4);
        assertThat((String) crono.get(0).get("marco"))
                .as("Semestre opens with a pre-trabalho diagnóstico (longer runway)")
                .containsIgnoringCase("Pré-trabalho");
    }

    @Test
    void cronograma_flex_offersTwoScenarios() {
        seedBriefing(30, "Saúde", "flex");

        runScript();

        Map<String, Object> proposta = parseJson(slots.get("proposta_in_company"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> crono = (List<Map<String, Object>>) proposta.get("cronograma");
        assertThat(crono).hasSize(2);
        assertThat(crono).extracting(m -> m.get("marco"))
                .as("Flex prazo presents 2 scenarios for the buyer to choose between")
                .anyMatch(m -> ((String) m).contains("Rápido"))
                .anyMatch(m -> ((String) m).contains("Estruturado"));
    }

    // ─────────────────────── Casos similares (turingSearch + fallback) ───────────────────────

    @Test
    void casosSimilares_turingSearchHits_usedDirectly() {
        turingSearch.cannedHits = List.of(
                Map.of("title", "Banco XYZ", "numPessoas", "85", "tema", "IA"),
                Map.of("title", "Varejo ABC", "numPessoas", "120", "tema", "Dados"));
        seedBriefing(40, "Tecnologia", "trimestre");
        slots.put("setor_empresa", "Financeiro");

        runScript();

        Map<String, Object> proposta = parseJson(slots.get("proposta_in_company"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> casos = (List<Map<String, Object>>) proposta.get("casosSimilares");
        assertThat(casos).hasSize(2);
        assertThat(casos.get(0))
                .containsEntry("empresa", "Banco XYZ")
                .containsEntry("pessoas", "85")
                .containsEntry("tema", "IA");
    }

    @Test
    void casosSimilares_emptyResult_fallsBackToStaticBySector() {
        turingSearch.cannedHits = List.of(); // empty
        seedBriefing(40, "Tecnologia", "trimestre");

        runScript();

        Map<String, Object> proposta = parseJson(slots.get("proposta_in_company"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> casos = (List<Map<String, Object>>) proposta.get("casosSimilares");
        assertThat(casos)
                .as("Fallback static cases by tema_normalized key (tecnologia → 3 references)")
                .isNotEmpty();
        // The fallback for tecnologia contains "Banco digital referência"
        assertThat(casos).extracting(c -> c.get("empresa"))
                .anyMatch(e -> ((String) e).contains("digital"));
    }

    @Test
    void casosSimilares_turingSearchThrows_fallsBackGracefully() {
        turingSearch.throwOnCall = new RuntimeException("ANN index unavailable");
        seedBriefing(40, "Liderança", "trimestre");

        runScript();

        Map<String, Object> proposta = parseJson(slots.get("proposta_in_company"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> casos = (List<Map<String, Object>>) proposta.get("casosSimilares");
        assertThat(casos)
                .as("turingSearch exception MUST NOT block the proposal — fallback to static cases")
                .isNotEmpty();
    }

    // ─────────────────────── Share URL ───────────────────────

    @Test
    void shareUrl_b2b_carriesEmpresaTemaPessoas() {
        seedBriefing(35, "IA generativa", "trimestre");
        slots.put("razao_social", "Acme S.A.");

        runScript();

        String url = slots.get("share_url_b2b");
        assertThat(url)
                .startsWith("https://ee.education.example.com/in-company?")
                .contains("empresa=Acme")
                .contains("tema=IA")
                .contains("pessoas=35");
    }

    @Test
    void shareUrl_urlEncodesSpecialChars() {
        seedBriefing(50, "Finanças & M&A", "trimestre");
        slots.put("razao_social", "Empresa com Acentos S/A");

        runScript();

        String url = slots.get("share_url_b2b");
        assertThat(url)
                .as("Special chars (spaces, ampersand, slashes) URL-encoded — never raw in the link")
                .doesNotContain(" ")
                .doesNotContain("S/A");
    }

    // ─────────────────────── Slot writes ───────────────────────

    @Test
    void slotWrites_exactlySevenSlots_writtenOnEveryCall() {
        seedBriefing(35, "IA generativa", "trimestre");

        runScript();

        // 7 mandatory slot writes per call:
        // proposta_in_company, tier, tier_label, tema_normalized,
        // prazo_label, share_url_b2b, cta_visible
        assertThat(slots.snapshot().keySet())
                .as("Every successful call writes exactly these 7 slots")
                .containsAll(List.of(
                        "proposta_in_company",
                        "tier", "tier_label",
                        "tema_normalized",
                        "prazo_label",
                        "share_url_b2b",
                        "cta_visible"));
        assertThat(slots.get("cta_visible"))
                .as("cta_visible flips to 'true' so the portal renders CompanyProposalCard")
                .isEqualTo("true");
    }

    // ─────────────────────── Markdown body ───────────────────────

    @Test
    void markdown_includesEmpresaTierAndPricingHighlights() {
        seedBriefing(35, "IA generativa", "trimestre");
        slots.put("razao_social", "Acme S.A.");

        String markdown = runScript();

        assertThat(markdown)
                .contains("Acme S.A.")
                .contains("Programa customizado") // tier_label
                .contains("35 pessoas")
                .contains("R$ 70.000,00") // total with formatted price
                .contains("desconto")
                .contains("20%"); // medium tier discount
    }

    @Test
    void markdown_fastTrackHighlight_onUrgentePrazo() {
        seedBriefing(15, "Liderança", "urgente");

        String markdown = runScript();

        assertThat(markdown)
                .as("Fast-track flag surfaces visibly in the persona-facing markdown")
                .contains("fast-track");
    }

    // ─────────────────────── Defaults / safe fallbacks ───────────────────────

    @Test
    void defaults_emptyBriefing_stillProducesValidProposal() {
        // No slots seeded except a minimal stub — the tool should still
        // produce a valid (if generic) proposal with the safe defaults:
        // razao_social="Sua empresa", numPessoas=15 (medium tier).
        runScript();

        Map<String, Object> proposta = parseJson(slots.get("proposta_in_company"));
        assertThat(proposta)
                .containsEntry("empresa", "Sua empresa")
                .containsEntry("numPessoas", 15)
                .containsEntry("tier", "medium");
    }

    // ─────────────────────── Helpers ───────────────────────

    private String runScript() {
        Binding binding = new Binding();
        // args is empty by design — the tool reads everything from slots.
        binding.setVariable("args", Map.of());
        binding.setVariable("slots", slots);
        binding.setVariable("turingSearch", turingSearch);
        // http is unused by this tool but the production binding always
        // provides it — include a stub so the script's implicit-reference
        // path stays identical to runtime.
        binding.setVariable("http", new Object());
        GroovyShell shell = new GroovyShell(binding);
        Object result = shell.evaluate(SCRIPT_SOURCE);
        return result == null ? "" : result.toString();
    }

    /**
     * Pre-seeds the 3 most-used briefing slots — leaves room for tests to
     * override individual ones. Each test typically calls this then sets
     * 1-2 extras (razao_social, setor_empresa, …).
     */
    private void seedBriefing(int numPessoas, String tema, String prazo) {
        slots.put("razao_social", "TestCo S.A.");
        slots.put("num_pessoas", String.valueOf(numPessoas));
        slots.put("tema", tema);
        slots.put("prazo", prazo);
        slots.put("formato", "Híbrido");
        slots.put("decision_role", "RH / L&D");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseJson(String src) {
        try {
            return JSON.readValue(src, new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            throw new AssertionError("Failed to parse JSON: " + src, e);
        }
    }

    private static String loadResource() {
        try (InputStream in = ComporPropostaInCompanyTest.class.getResourceAsStream(SCRIPT_RESOURCE)) {
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

        void put(String name, String value) {
            map.put(name, value);
        }

        Map<String, String> snapshot() {
            return Map.copyOf(map);
        }
    }

    /**
     * {@code turingSearch.ann(...)} stub — returns the cannedHits list when
     * configured, throws when the test set {@link #throwOnCall}, otherwise
     * empty. Mirrors the production helper's API.
     */
    static class MockTuringSearch {
        List<Map<String, Object>> cannedHits = new ArrayList<>();
        RuntimeException throwOnCall;

        public List<Map<String, Object>> ann(Map<String, Object> args) {
            if (throwOnCall != null) throw throwOnCall;
            return cannedHits;
        }
    }
}
