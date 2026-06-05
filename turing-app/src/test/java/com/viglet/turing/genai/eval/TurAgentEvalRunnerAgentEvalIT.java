/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;

import com.viglet.turing.persistence.dto.agent.TurAgentEvalReportDto;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurAgentEvalCase;
import com.viglet.turing.persistence.model.agent.TurAgentEvalExpectedOutcome;
import com.viglet.turing.persistence.model.agent.TurAgentEvalSet;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.llm.TurLLMVendor;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.agent.TurAgentEvalReportRepository;
import com.viglet.turing.persistence.repository.agent.TurAgentEvalSetRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMVendorRepository;
import com.viglet.turing.system.security.TurSecretCryptoService;
import com.viglet.turing.testutil.AbstractTuringSpringIT;

/**
 * T288 / §XV.4 — Agent-CI integration test. Drives the full {@link
 * TurAgentEvalRunnerService} against a real OpenAI model: builds an agent with
 * an enabled LLM instance and a one-case golden set (rubric-only, so no flow
 * is required), runs the gate, and asserts a scored report comes back and is
 * persisted.
 *
 * <h2>Activation</h2>
 *
 * <pre>{@code
 * mvn verify -Pagent-eval -pl turing-app -Dskip.npm=true
 * }</pre>
 *
 * <p>Without {@code OPENAI_API_KEY} the class is short-circuited by JUnit's
 * {@link EnabledIfEnvironmentVariable} — zero API cost. The default failsafe
 * profile excludes {@code *AgentEvalIT.java} so PR / CI runs never load it.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class TurAgentEvalRunnerAgentEvalIT extends AbstractTuringSpringIT {

    private static final String OPENAI_BASE_URL = "https://api.openai.com/v1";
    private static final String OPENAI_MODEL = "gpt-4o-mini";

    @Autowired
    private TurAgentEvalRunnerService runnerService;
    @Autowired
    private TurAIAgentRepository agentRepository;
    @Autowired
    private TurLLMInstanceRepository llmInstanceRepository;
    @Autowired
    private TurLLMVendorRepository llmVendorRepository;
    @Autowired
    private TurAgentEvalSetRepository evalSetRepository;
    @Autowired
    private TurAgentEvalReportRepository reportRepository;
    @Autowired
    private TurSecretCryptoService secretCryptoService;

    @Test
    void runsGoldenSetAndPersistsReport() {
        TurLLMVendor openAi = findOpenAiVendor();
        assumeTrue(openAi != null, "No OpenAI vendor seeded — skipping");

        TurLLMInstance llm = new TurLLMInstance();
        llm.setTitle("eval-it-" + UUID.randomUUID().toString().substring(0, 8));
        llm.setEnabled(1);
        llm.setUrl(OPENAI_BASE_URL);
        llm.setModelName(OPENAI_MODEL);
        llm.setTurLLMVendor(openAi);
        llm.setApiKeyEncrypted(secretCryptoService.encrypt(System.getenv("OPENAI_API_KEY")));
        llm = llmInstanceRepository.save(llm);

        TurAIAgent agent = new TurAIAgent();
        agent.setTitle("eval-it-agent-" + UUID.randomUUID().toString().substring(0, 8));
        agent.setEnabled(1);
        agent.setSystemPrompt("You are a friendly, concise assistant.");
        agent.setLlmInstances(new HashSet<>(java.util.List.of(llm)));
        agent = agentRepository.save(agent);

        TurAgentEvalSet set = new TurAgentEvalSet();
        set.setName("smoke");
        set.setEnabled(1);
        set.setTurAIAgent(agent);
        TurAgentEvalCase c = new TurAgentEvalCase();
        c.setName("greeting stays helpful");
        c.setSeedTurnsJson("[\"Hi, can you help me with something?\"]");
        c.setExpectedOutcome(TurAgentEvalExpectedOutcome.ANY);
        c.setRubric("The assistant responds in a helpful, friendly way and offers to help.");
        c.setTurAgentEvalSet(set);
        set.getCases().add(c);
        evalSetRepository.save(set);

        TurAgentEvalReportDto report = runnerService.runAgent(agent.getId());

        assertThat(report.error()).as("run should not error: %s", report.error()).isNull();
        assertThat(report.caseCount()).isEqualTo(1);
        assertThat(report.results()).hasSize(1);
        assertThat(report.reportId()).isNotBlank();
        assertThat(reportRepository.findByTurAIAgent_IdOrderByCreatedAtDesc(agent.getId())).isNotEmpty();
    }

    private TurLLMVendor findOpenAiVendor() {
        Optional<TurLLMVendor> byId = llmVendorRepository.findById("OPENAI");
        if (byId.isPresent()) {
            return byId.get();
        }
        return llmVendorRepository.findAll().stream()
                .filter(v -> v.getPlugin() != null && v.getPlugin().toLowerCase().contains("openai"))
                .findFirst()
                .orElse(null);
    }
}
