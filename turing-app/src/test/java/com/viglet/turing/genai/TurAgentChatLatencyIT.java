/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;

import com.viglet.turing.genai.TurAgentChatExecutor.ChatMessageItem;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatResponse;
import com.viglet.turing.observability.TurChatPipelineObservation;
import com.viglet.turing.observability.TurMeterNames;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.llm.TurLLMVendor;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMVendorRepository;
import com.viglet.turing.system.security.TurSecretCryptoService;
import com.viglet.turing.testutil.AbstractTuringSpringIT;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Latency benchmark and diagnostic for the agent chat pipeline. Sends a
 * trivial "oi tudo bem?" turn through the full {@link TurAgentChatExecutor}
 * (real OpenAI round-trip, no flow attached, no tools, no persona) and prints
 * a per-stage breakdown read from {@link TurChatPipelineObservation}:
 *
 * <pre>
 *   setup    — entity load, persona resolution, tool callback build,
 *              flow context resolution, prompt composition
 *   llm_call — the {@code ChatModel.call(prompt)} round-trip itself
 *   post     — flow advance, regen if any, token usage, analytics
 *   total    — sum of the above (matches wall time within ms)
 * </pre>
 *
 * <p>The breakdown lets us tell apart:
 * <ul>
 *   <li><b>JPA/cache regressions</b> — setup balloons when a {@code @Cacheable}
 *       repo gets bypassed or a LAZY association lights up a sleeping
 *       {@code N+1}.</li>
 *   <li><b>Provider latency</b> — llm_call carries the LLM and network round
 *       trip; tracked separately so a slow OpenAI day isn't read as a code
 *       regression.</li>
 *   <li><b>Post-processing overhead</b> — flow advance + regen + analytics
 *       writes blocking the response thread.</li>
 * </ul>
 *
 * <p>Guarded by {@code OPENAI_API_KEY} — without the key the test is skipped
 * so CI doesn't fail on machines without a real LLM provider configured. The
 * benchmark intentionally uses a real provider (not a mock) so the timing is
 * representative of production.
 *
 * <p>Extends {@link AbstractTuringSpringIT}: gets the ephemeral H2 datasource,
 * JMX-enabled context, per-JVM Artemis dir and proper cleanup — the same
 * pattern as every other {@code *IT.java}. Does NOT redeclare
 * {@code @SpringBootTest} (would override the parent's annotation and lose
 * the JMX property, causing context-load failure on
 * {@code TurQueueBrowserService}).
 *
 * @author Alexandre Oliveira
 * @since 2026.2.8
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class TurAgentChatLatencyIT extends AbstractTuringSpringIT {

    private static final String OPENAI_URL = "https://api.openai.com/v1";
    private static final String CHAT_MODEL = "gpt-4o-mini";

    /**
     * Soft ceiling for the pipeline {@code setup} stage. With everything
     * cached (agent, LLM instance, chat flow) this should stay under
     * 500 ms — when it doesn't, a {@code @Cacheable} got bypassed or a
     * LAZY association lit up an unexpected lazy load.
     */
    private static final long SETUP_MS_THRESHOLD = 500L;

    /**
     * Hard ceiling for total wall time on a trivial "hi" message. Anything
     * past 10 s on gpt-4o-mini is either a network blip or a real regression
     * (most likely a non-cached repo call or a double LLM round-trip in the
     * flow advance loop).
     */
    private static final long TOTAL_MS_THRESHOLD = 10_000L;

    @Autowired private TurAgentChatExecutor executor;
    @Autowired private TurAIAgentRepository agentRepository;
    @Autowired private TurLLMInstanceRepository llmRepository;
    @Autowired private TurLLMVendorRepository vendorRepository;
    @Autowired private TurSecretCryptoService cryptoService;
    @Autowired private TurChatPipelineObservation chatObservation;

    private TurAIAgent agent;
    private TurLLMInstance llm;

    @BeforeAll
    void seedAgentAndLlm() {
        // Pick the OpenAI vendor seeded by TurLLMVendorOnStartup. Filtering
        // on the plugin slug rather than the title because the title is i18n
        // and could shift between releases.
        TurLLMVendor openaiVendor = vendorRepository.findAll().stream()
                .filter(v -> "openai".equalsIgnoreCase(v.getPlugin()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "OpenAI vendor not seeded — check TurLLMVendorOnStartup"));

        TurLLMInstance newLlm = new TurLLMInstance();
        newLlm.setTitle("latency-it-llm-" + UUID.randomUUID().toString().substring(0, 8));
        newLlm.setEnabled(1);
        newLlm.setUrl(OPENAI_URL);
        newLlm.setModelName(CHAT_MODEL);
        newLlm.setTemperature(0.0);
        newLlm.setTurLLMVendor(openaiVendor);
        newLlm.setApiKeyEncrypted(cryptoService.encrypt(System.getenv("OPENAI_API_KEY")));
        this.llm = llmRepository.save(newLlm);

        TurAIAgent newAgent = new TurAIAgent();
        newAgent.setTitle("latency-it-agent-" + UUID.randomUUID().toString().substring(0, 8));
        newAgent.setEnabled(1);
        newAgent.setLlmInstances(Set.of(this.llm));
        this.agent = agentRepository.save(newAgent);
    }

    @Test
    void simpleHello_measuresPipelineBreakdown() {
        // "oi tudo bem?" — trivial Portuguese greeting. Chosen because:
        //   • short prompt (few tokens in, few tokens out)
        //   • no tool invocation expected
        //   • no flow attached (executor's simpler path)
        // → most of the wall time should be the LLM round-trip itself,
        //   making setup/post regressions easy to spot.
        List<ChatMessageItem> history = List.of(
                new ChatMessageItem("user", "oi tudo bem?"));

        long tWallStart = System.currentTimeMillis();
        List<ChatResponse> responses = executor
                .execute(agent, llm, history, null)
                .collectList()
                .block(Duration.ofSeconds(30));
        long wallMs = System.currentTimeMillis() - tWallStart;

        assertThat(responses)
                .as("Executor must emit at least one response chunk")
                .isNotNull()
                .isNotEmpty();
        assertThat(responses.get(0).content())
                .as("Response text must be non-blank")
                .isNotBlank();

        MeterRegistry mr = chatObservation.getMeterRegistry();
        long setupMs = timerTotalMillis(mr, TurMeterNames.STAGE_CHAT_SETUP);
        long llmMs = timerTotalMillis(mr, TurMeterNames.STAGE_CHAT_LLM_CALL);
        long postMs = timerTotalMillis(mr, TurMeterNames.STAGE_CHAT_POST);
        long pipelineMs = timerTotalMillis(mr, TurMeterNames.STAGE_CHAT_TOTAL);

        System.out.println();
        System.out.println("=== Chat Latency Breakdown ===");
        System.out.printf("  wall_ms:        %5d ms%n", wallMs);
        System.out.printf("  pipeline_ms:    %5d ms%n", pipelineMs);
        System.out.printf("    setup:        %5d ms%n", setupMs);
        System.out.printf("    llm_call:     %5d ms%n", llmMs);
        System.out.printf("    post:         %5d ms%n", postMs);
        System.out.println("  response: " + truncate(responses.get(0).content(), 120));
        System.out.println();

        assertThat(setupMs)
                .as("setup_ms (everything cached, no external calls) — regression if this grows. "
                        + "Threshold: %d ms", SETUP_MS_THRESHOLD)
                .isLessThan(SETUP_MS_THRESHOLD);
        assertThat(wallMs)
                .as("wall_ms (total turn) — regression if this passes %d ms on a trivial 'oi'",
                        TOTAL_MS_THRESHOLD)
                .isLessThan(TOTAL_MS_THRESHOLD);
    }

    // ---- Helpers ----------------------------------------------------------

    private static long timerTotalMillis(MeterRegistry mr, String stage) {
        Timer timer = mr.find(TurMeterNames.CHAT_PIPELINE)
                .tag(TurMeterNames.TAG_STAGE, stage)
                .timer();
        if (timer == null) {
            return 0L;
        }
        return (long) timer.totalTime(TimeUnit.MILLISECONDS);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "<null>";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
