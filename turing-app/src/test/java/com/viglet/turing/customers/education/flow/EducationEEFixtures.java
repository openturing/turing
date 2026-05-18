/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.customers.education.flow;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import com.viglet.turing.persistence.model.customtool.TurCustomTool;
import com.viglet.turing.persistence.repository.customtool.TurCustomToolRepository;

/**
 * Idempotent fixture loader for the Executive Education demo. Re-applies
 * the canonical Custom Tool definition (script Groovy + LLM-facing metadata)
 * straight from the test classpath every time the IT class boots, so a stale
 * tool inside the bundled H2 snapshot ({@code /h2-snapshot/turingDB.mv.db})
 * never leaks an outdated script into a test run.
 *
 * <p>Pattern: any test that wants the {@code search_ee_programs} tool live in
 * its in-memory DB calls {@link #upsertSearchEeProgramsTool(TurCustomToolRepository)}
 * once in {@code @BeforeAll}. The method looks up the tool by title and either
 * creates a fresh row or updates the existing one in place — same operation
 * the admin UI does on Save.
 *
 * <p>Resources are loaded from {@code /customers/education/tools/} on the
 * classpath, checked into {@code src/test/resources/customers/education/tools}
 * (the {@code customers/} folder is gitignored — files live there only
 * for local test runs).
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
final class EducationEEFixtures {

    /** Tool {@code title} — also the sanitized name the LLM sees. */
    static final String CUSTOM_TOOL_TITLE = "search_ee_programs";
    static final String GROOVY_SCRIPT_RESOURCE = "/customers/education/tools/search-ee-programs.groovy";
    static final String PARAMETERS_JSON = "[{\"name\":\"query\",\"type\":\"string\"}]";
    static final String RETURN_TYPE = "markdown";

    static final String LLM_DESCRIPTION = "Busca em tempo real no catálogo Executive Education por "
            + "programas que casem com a área e o objetivo do visitante. SEMPRE chame esta ferramenta antes "
            + "de recomendar programas — nunca invente nomes. O parâmetro `query` deve juntar a área escolhida "
            + "pelo visitante com palavras-chave do objetivo (ex.: 'Finanças & Investimentos virar CFO scale-up', "
            + "'Tecnologia & Dados liderar transformação digital'). A tool ESCREVE os 3 programas escolhidos no "
            + "slot `programas_match` (o componente React `ProgramCompareCards` renderiza automaticamente em "
            + "'Programas recomendados pra você'). Retorna até 8 programas com nome, preço, datas e status de "
            + "inscrições.";

    private EducationEEFixtures() {
        // static factory; not instantiable
    }

    /**
     * Creates or updates the {@code search_ee_programs} custom tool to match
     * the canonical script + metadata bundled in this test module. Returns
     * the persisted entity so callers can bind it to an agent's
     * {@code customTools} set if they need to exercise the tool callback
     * pipeline end-to-end.
     */
    static TurCustomTool upsertSearchEeProgramsTool(TurCustomToolRepository repository) {
        String groovyScript = loadResource(GROOVY_SCRIPT_RESOURCE);
        TurCustomTool tool = repository.findAll().stream()
                .filter(t -> CUSTOM_TOOL_TITLE.equals(t.getTitle()))
                .findFirst()
                .orElseGet(TurCustomTool::new);
        tool.setTitle(CUSTOM_TOOL_TITLE);
        tool.setLlmDescription(LLM_DESCRIPTION);
        tool.setGroovyScript(groovyScript);
        tool.setParametersJson(PARAMETERS_JSON);
        tool.setReturnType(RETURN_TYPE);
        tool.setEnabled(1);
        return repository.save(tool);
    }

    /**
     * Reads a UTF-8 classpath resource. Wrap {@link IOException} into
     * {@link IllegalStateException} because callers are JUnit lifecycle
     * methods that can't recover from a missing fixture.
     */
    private static String loadResource(String classpath) {
        try (InputStream in = EducationEEFixtures.class.getResourceAsStream(classpath)) {
            Objects.requireNonNull(in, "Missing classpath resource: " + classpath);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read fixture: " + classpath, e);
        }
    }
}
