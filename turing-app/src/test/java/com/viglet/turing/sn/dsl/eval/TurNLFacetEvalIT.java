/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.sn.dsl.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;

import com.viglet.core.manifest.VigletFieldManifest;
import com.viglet.core.manifest.VigletFieldSpec;
import com.viglet.core.manifest.VigletManifestResult;
import com.viglet.turing.exchange.sn.TurSNSiteContentExchangeService;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.llm.TurLLMVendor;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.se.TurSEVendor;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMVendorRepository;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.persistence.repository.se.TurSEVendorRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.sn.dsl.TurDslQueryRequest;
import com.viglet.turing.sn.dsl.TurDslSearchResponse;
import com.viglet.turing.sn.dsl.TurDslSearchService;
import com.viglet.turing.sn.dsl.eval.TurNLFacetParser.ParseRequest;
import com.viglet.turing.sn.manifest.TurSNManifestMapper;
import com.viglet.turing.sn.manifest.TurSNSiteManifestService;
import com.viglet.turing.system.TurGlobalSettingsService;
import com.viglet.turing.system.security.TurSecretCryptoService;
import com.viglet.turing.testutil.AbstractTuringSpringIT;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * T407 / §XX.14 — End-to-end NL→facet eval integration test against a <b>real</b>
 * LLM and a <b>live</b> index. The T385 unit tests already pin the scorer and the
 * service wiring with a deterministic fake parser; this IT closes the gap that
 * actually breaks in production: the real {@link TurLlmNLFacetParser} turning prose
 * into a grounded query, and that query being a <i>valid, executable</i> search
 * against a live index.
 *
 * <p>Setup mirrors a structured-source onboarding: a Lucene SE instance is created
 * against a throwaway temp directory, a small catalog site + field schema is
 * provisioned through the <b>T382 manifest</b> ({@link TurSNSiteManifestService}),
 * and a handful of fixture courses are indexed synchronously. The test then:
 *
 * <ol>
 *   <li><b>Live-index sanity (no LLM)</b> — hand-built DSL term queries prove the
 *       manifest-provisioned schema and the indexed documents are queryable by
 *       field (match-all, single-term, multi-term {@code bool/filter}).</li>
 *   <li><b>Grounded &amp; scored</b> — {@link TurNLFacetEvalService#run} drives the
 *       real parser over every prose case and asserts the run completes, scores,
 *       and — the Block&nbsp;R invariant — never filters on a field outside the
 *       declared schema.</li>
 *   <li><b>Executable</b> — each parsed {@link TurDslQueryRequest} is run through
 *       {@link TurDslSearchService} so DSL that "scores well but fails to execute"
 *       is caught.</li>
 *   <li><b>Returns the expected documents</b> — the parsed query for the
 *       "online graduate" case is executed and asserted to return <i>only</i>
 *       online courses, proving the parsed filter was applied to the live index.</li>
 * </ol>
 *
 * <h2>Activation</h2>
 *
 * <pre>{@code
 * mvn verify -Pnl-facet-eval -pl turing-app -Dskip.npm=true
 * }</pre>
 *
 * <p>Without {@code OPENAI_API_KEY} the class is short-circuited by JUnit's
 * {@link EnabledIfEnvironmentVariable} — zero API cost. The default failsafe
 * profile excludes {@code *NLFacetEvalIT.java} so PR / CI runs never load it
 * (mirrors {@code TurAgentEvalRunnerAgentEvalIT}).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class TurNLFacetEvalIT extends AbstractTuringSpringIT {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private static final String SITE = "courses-nlfaceteval-it";
    private static final String LOCALE = "pt_BR";
    private static final String OPENAI_BASE_URL = "https://api.openai.com/v1";
    private static final String OPENAI_MODEL = "gpt-4o-mini";

    @Autowired
    private TurSEVendorRepository seVendorRepository;
    @Autowired
    private TurSEInstanceRepository seInstanceRepository;
    @Autowired
    private TurLLMVendorRepository llmVendorRepository;
    @Autowired
    private TurLLMInstanceRepository llmInstanceRepository;
    @Autowired
    private TurSNSiteRepository snSiteRepository;
    @Autowired
    private TurSNSiteManifestService manifestService;
    @Autowired
    private TurSNSiteContentExchangeService contentExchangeService;
    @Autowired
    private TurNLFacetEvalService evalService;
    @Autowired
    private TurNLFacetParser parser;
    @Autowired
    private TurDslSearchService dslSearchService;
    @Autowired
    private TurGlobalSettingsService globalSettingsService;
    @Autowired
    private TurSecretCryptoService secretCryptoService;

    private static Path luceneDir;
    private TurNLFacetEvalPack pack;

    @BeforeAll
    void setUp() throws IOException {
        luceneDir = Files.createTempDirectory("turing-nlfacet-eval-it-lucene");

        configureDefaultOpenAiLlm();
        TurSEInstance seInstance = createLuceneInstance(luceneDir);
        pack = packForSite(loadCoursesPack(), SITE);

        provisionSchemaFromManifest(seInstance, pack.fields());
        indexFixtureCourses();
    }

    @AfterAll
    void cleanupLuceneDir() {
        if (luceneDir == null || !Files.exists(luceneDir)) {
            return;
        }
        try (var walk = Files.walk(luceneDir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // write.lock may linger on Windows — OS reaps the temp dir later.
                }
            });
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }

    // ─────────────── 1. Live-index sanity (no LLM involved) ───────────────

    @Test
    void manifestSchemaAndIndexedDocsAreQueryableByField() {
        // match-all: the whole fixture corpus is live and queryable.
        assertThat(idsOf(search(dsl("{\"query\":{\"match_all\":{}}}"))))
                .as("match-all must return every indexed course")
                .containsExactlyInAnyOrder("c1", "c2", "c3", "c4", "c5", "c6");

        // single term filter on a manifest-provisioned facet field.
        assertThat(idsOf(search(dsl(
                "{\"query\":{\"bool\":{\"filter\":[{\"term\":{\"modality\":\"online\"}}]}}}"))))
                .as("term modality=online must return exactly the online courses")
                .containsExactlyInAnyOrder("c1", "c3", "c4");

        // multi-term bool/filter: online AND post-graduate.
        assertThat(idsOf(search(dsl(
                "{\"query\":{\"bool\":{\"filter\":["
                        + "{\"term\":{\"modality\":\"online\"}},"
                        + "{\"term\":{\"degree\":\"pos\"}}]}}}"))))
                .as("modality=online AND degree=pos must return exactly c1 and c4")
                .containsExactlyInAnyOrder("c1", "c4");
    }

    // ─────────────── 2. Real LLM: grounded & scored ───────────────

    @Test
    void realLlmParsesEveryCaseGroundedAndScored() {
        assertThat(parser.isAvailable())
                .as("default OpenAI LLM must be configured for the parser")
                .isTrue();

        TurNLFacetEvalReport report = evalService.run(pack);

        assertThat(report.error()).as("run should not error: %s", report.error()).isNull();
        assertThat(report.caseCount()).isEqualTo(pack.cases().size());
        assertThat(report.results()).hasSize(pack.cases().size());

        // The Block R invariant the prompt pins: the parser must never filter on a
        // field outside the declared schema. This is the hard, stable guarantee.
        assertThat(report.results())
                .as("no case may reference a field outside the declared schema")
                .allSatisfy(r -> assertThat(r.ungroundedFields()).isEmpty());

        // A competent model should match most golden clauses on the curated pack.
        assertThat(report.score())
                .as("aggregate score across the golden pack")
                .isGreaterThanOrEqualTo(0.5d);
    }

    // ─────────────── 3. Real LLM: parsed DSL is executable ───────────────

    @Test
    void everyParsedQueryIsExecutableAgainstTheLiveIndex() {
        for (TurNLFacetEvalCase evalCase : pack.cases()) {
            TurDslQueryRequest parsed = parser.parse(
                    new ParseRequest(SITE, LOCALE, evalCase.query(), pack.fields()));
            Optional<TurDslSearchResponse> response = dslSearchService.search(SITE, LOCALE, parsed);
            assertThat(response)
                    .as("parsed DSL for case '%s' must execute against the live index", evalCase.name())
                    .isPresent();
        }
    }

    // ─────────────── 4. Real LLM: query returns the expected documents ───────────────

    @Test
    void onlineGraduateQueryReturnsOnlyOnlineCourses() {
        TurNLFacetEvalCase graduate = pack.cases().stream()
                .filter(c -> c.query().toLowerCase().contains("online")
                        && c.query().toLowerCase().contains("graduate"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("fixture must carry the online-graduate case"));

        TurDslQueryRequest parsed = parser.parse(
                new ParseRequest(SITE, LOCALE, graduate.query(), pack.fields()));
        TurDslSearchResponse response = dslSearchService.search(SITE, LOCALE, parsed)
                .orElseThrow(() -> new AssertionError("online-graduate query must execute"));

        List<TurDslSearchResponse.Hit> hits = response.hits().hits();
        assertThat(hits)
                .as("an 'online' query must return at least one online course")
                .isNotEmpty()
                // "online" is an enumerated modality value in the schema description, so the
                // parser reliably emits modality=online — every returned doc must be online,
                // proving the parsed filter was applied to the live index.
                .allSatisfy(h -> assertThat(h.source()).containsEntry("modality", "online"));
    }

    // ─────────────── Setup helpers ───────────────

    private void configureDefaultOpenAiLlm() {
        TurLLMVendor openAi = llmVendorRepository.findById("OPENAI").orElseGet(() -> {
            TurLLMVendor v = new TurLLMVendor();
            v.setId("OPENAI");
            v.setTitle("OpenAI");
            v.setDescription("OpenAI");
            v.setPlugin("openai");
            return llmVendorRepository.save(v);
        });

        TurLLMInstance llm = new TurLLMInstance();
        llm.setTitle("nlfacet-eval-it-" + UUID.randomUUID().toString().substring(0, 8));
        llm.setEnabled(1);
        llm.setUrl(OPENAI_BASE_URL);
        llm.setModelName(OPENAI_MODEL);
        llm.setTurLLMVendor(openAi);
        llm.setApiKeyEncrypted(secretCryptoService.encrypt(System.getenv("OPENAI_API_KEY")));
        llm = llmInstanceRepository.save(llm);

        globalSettingsService.updateDefaultLlmId(llm.getId());
    }

    private TurSEInstance createLuceneInstance(Path indexDir) {
        TurSEVendor lucene = seVendorRepository.findById("LUCENE").orElseGet(() -> {
            TurSEVendor v = new TurSEVendor();
            v.setId("LUCENE");
            v.setTitle("Lucene");
            v.setDescription("Apache Lucene (embedded)");
            v.setPlugin("lucene");
            return seVendorRepository.save(v);
        });

        TurSEInstance instance = new TurSEInstance();
        instance.setTitle("nlfacet-eval-it-lucene");
        instance.setEnabled(1);
        instance.setEndpointUrl(indexDir.toString().replace('\\', '/'));
        instance.setTurSEVendor(lucene);
        return seInstanceRepository.save(instance);
    }

    private void provisionSchemaFromManifest(TurSEInstance seInstance, List<TurNLFacetField> fields) {
        List<VigletFieldSpec> specs = fields.stream()
                .map(f -> VigletFieldSpec.builder()
                        .name(f.name())
                        .type(TurSNManifestMapper.toCoreType(f.type()))
                        .facet(f.facet())
                        .description(f.description())
                        .build())
                .toList();
        VigletFieldManifest manifest = new VigletFieldManifest(
                SITE, "T407 NL→facet eval IT catalog", seInstance.getId(), List.of(LOCALE), specs);

        VigletManifestResult result = manifestService.provision(manifest);
        assertThat(result.siteName()).isEqualTo(SITE);
        assertThat(snSiteRepository.findByNameIgnoreCase(SITE)).isPresent();
    }

    private void indexFixtureCourses() {
        List<Map<String, Object>> docs = List.of(
                course("c1", "Pós em Ciência de Dados Online", "pos", "online", "data science",
                        "Sao Paulo", "Insper", 1500, 18),
                course("c2", "MBA Presencial em São Paulo", "pos", "presencial", "business",
                        "Sao Paulo", "FGV", 2500, 24),
                course("c3", "Graduação Online em Administração", "graduacao", "online", "business",
                        "Rio de Janeiro", "Estácio", 900, 48),
                course("c4", "Pós Online em Inteligência Artificial", "pos", "online", "data science",
                        "Sao Paulo", "USP", 1800, 12),
                course("c5", "Técnico Presencial em Redes", "tecnico", "presencial", "it",
                        "Campinas", "Senac", 600, 18),
                course("c6", "Doutorado Presencial em Economia", "doutorado", "presencial", "economics",
                        "Sao Paulo", "USP", 3000, 48));

        var site = snSiteRepository.findByNameIgnoreCase(SITE).orElseThrow();
        int indexed = contentExchangeService.importContent(site,
                Map.of(SITE, Map.of(LOCALE, docs)), "nlfacet-eval-it-" + site.getId());
        assertThat(indexed).as("all fixture courses must index").isEqualTo(docs.size());
    }

    private static Map<String, Object> course(String id, String title, String degree, String modality,
            String area, String city, String institution, int tuition, int durationMonths) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("id", id);
        d.put("type", "CourseIT");
        d.put("title", title);
        d.put("text", title);
        d.put("degree", degree);
        d.put("modality", modality);
        d.put("area", area);
        d.put("city", city);
        d.put("institution", institution);
        d.put("tuition", tuition);
        d.put("durationMonths", durationMonths);
        return d;
    }

    // ─────────────── Fixture + query helpers ───────────────

    private TurNLFacetEvalPack loadCoursesPack() {
        try (InputStream in = getClass().getResourceAsStream(
                "/nl-facet-eval/courses.nl-facet-eval.json")) {
            assertThat(in).as("fixture present on classpath").isNotNull();
            return MAPPER.readValue(in, TurNLFacetEvalPack.class);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Re-point the committed fixture at the IT's provisioned site (scoring uses the pack's own schema). */
    private static TurNLFacetEvalPack packForSite(TurNLFacetEvalPack base, String site) {
        return new TurNLFacetEvalPack(base.name(), site, LOCALE, base.fields(), base.cases());
    }

    private static TurDslQueryRequest dsl(String json) {
        return MAPPER.readValue(json, TurDslQueryRequest.class);
    }

    private TurDslSearchResponse search(TurDslQueryRequest request) {
        // The fixture corpus is 6 docs, well under the executor's default page size,
        // so the request is run as-is — no need to widen `size`.
        return dslSearchService.search(SITE, LOCALE, request)
                .orElseThrow(() -> new AssertionError("DSL search must execute against the live index"));
    }

    private static Set<String> idsOf(TurDslSearchResponse response) {
        return response.hits().hits().stream()
                .map(TurDslSearchResponse.Hit::id)
                .collect(Collectors.toSet());
    }
}
