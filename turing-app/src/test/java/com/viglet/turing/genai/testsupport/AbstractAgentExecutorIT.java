/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.testsupport;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.viglet.testsupport.genai.executor.TurAgentChatExecutorMockSupport;
import com.viglet.turing.genai.TurAgentChatExecutor;
import com.viglet.turing.genai.TurAgentChatRequest;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatMessageItem;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatResponse;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.llm.TurLLMVendor;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.agent.TurAIAgentSlotRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMVendorRepository;
import com.viglet.turing.persistence.repository.persona.TurPersonaRepository;
import com.viglet.turing.system.security.TurSecretCryptoService;
import com.viglet.turing.testutil.AbstractTuringSpringIT;

/**
 * Base class for Structural and Contract integration tests of
 * {@link TurAgentChatExecutor}.
 *
 * <p>Extends {@link AbstractTuringSpringIT} (ephemeral H2 + per-JVM Artemis
 * data dir + JMX) and adds:
 *
 * <ul>
 *   <li>Convenient handles into {@link TurAgentChatExecutorMockSupport} —
 *       {@link #harness()} for staging responses, {@link #firstPrompt()} /
 *       {@link #regenPrompt()} as zero-ceremony accessors over the captured
 *       prompt list.</li>
 *   <li>Factories for the minimal entity graph an executor test needs:
 *       {@link #createAgent(String, TurPersona)} and
 *       {@link #createMockableLlmInstance()}. Tests that need a fuller graph
 *       (slots, flows, custom tools) compose on top of these.</li>
 *   <li>{@link #runTurn(TurAIAgent, TurLLMInstance, List, String, String, String)} —
 *       drives a single executor turn end-to-end and returns the captured
 *       SSE event list, with a 5-second timeout matching the
 *       {@code TurAgentChatLatencyIT} contract.</li>
 *   <li>{@link #systemTextOf(Prompt)} — pulls the SystemMessage text out of
 *       a captured prompt for the bread-and-butter assertion of these
 *       structural tests.</li>
 * </ul>
 *
 * <p>Subclasses must {@code @Import(TurAgentChatExecutorMockSupport.class)}
 * themselves. Sharing the {@code @Import} on the base would override Spring's
 * context cache key and silently produce duplicate contexts.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
public abstract class AbstractAgentExecutorIT extends AbstractTuringSpringIT {

    /**
     * Spring's MBean exporter normally registers Hikari's pool MBean under
     * {@code com.zaxxer.hikari:name=dataSource,type=HikariDataSource}. When
     * an IT class is loaded with extra {@code @Import(...)} bindings, its
     * Spring context cache key differs from the base — so Spring boots a
     * SECOND context and tries to register a SECOND MBean with the same name.
     * The JMX server throws {@code InstanceAlreadyExistsException} during
     * {@code afterSingletonsInstantiated}, the context fails to load, and
     * EVERY test in this IT fails with "Failed to load ApplicationContext".
     *
     * <p>{@code spring.jmx.unique-names=true} makes Spring suffix the MBean
     * name with the bean id (e.g. {@code …,name=dataSource,id=0x…}), so
     * coexisting test contexts no longer collide. Cost is zero at runtime
     * (only affects MBean naming) and is opt-in via this base class so the
     * production app's JMX surface is unchanged.
     */
    @DynamicPropertySource
    static void uniqueJmxNamesAcrossTestContexts(DynamicPropertyRegistry registry) {
        registry.add("spring.jmx.unique-names", () -> "true");
    }

    @Autowired
    protected TurAgentChatExecutor executor;

    @Autowired
    protected TurAgentChatExecutorMockSupport.ChatModelHarness harness;

    @Autowired
    protected TurAIAgentRepository agentRepository;

    @Autowired
    protected TurLLMInstanceRepository llmInstanceRepository;

    @Autowired
    protected TurPersonaRepository personaRepository;

    @Autowired
    protected TurLLMVendorRepository llmVendorRepository;

    @Autowired
    protected TurSecretCryptoService cryptoService;

    @Autowired
    protected TurChatFlowRepository chatFlowRepository;

    @Autowired
    protected TurAIAgentSlotRepository slotRepository;

    /**
     * Bundle of repositories that {@link ChatFlowImportTestUtil} needs to
     * upsert a flow + persona + slots into the test database. Centralised
     * here so tests don't have to re-construct the record at every call
     * site.
     */
    protected ChatFlowImportTestUtil.Repos importRepos() {
        return new ChatFlowImportTestUtil.Repos(
                agentRepository, chatFlowRepository, slotRepository, personaRepository);
    }

    /**
     * Reset the mock harness before every test method. Without this, queued
     * responses or captured prompts from the previous test would leak into
     * the next (Spring context is shared across the class for performance).
     */
    @BeforeEach
    void resetHarness() {
        harness.reset();
    }

    /** Live ChatModel harness shortcut — same instance as {@link #harness}. */
    protected TurAgentChatExecutorMockSupport.ChatModelHarness harness() {
        return harness;
    }

    /**
     * Persist a fresh {@link TurAIAgent} with a one-off title to avoid
     * collisions across test methods. The {@code defaultPersona} parameter
     * is optional — pass {@code null} when the test doesn't need a persona
     * resolved.
     */
    protected TurAIAgent createAgent(String title, TurPersona defaultPersona) {
        TurAIAgent agent = new TurAIAgent();
        agent.setTitle(title + "-" + UUID.randomUUID().toString().substring(0, 8));
        agent.setEnabled(1);
        if (defaultPersona != null) {
            agent.getPersonas().add(defaultPersona);
            agent.setDefaultPersona(defaultPersona);
        }
        return agentRepository.save(agent);
    }

    /**
     * Persist a {@link TurLLMInstance} pointed at a fake API key. The
     * {@code TurLlmModelFactory} {@code @Primary} mock from
     * {@link TurAgentChatExecutorMockSupport} ignores the LLM identity and
     * always returns the harness mock — this entity exists only so the
     * executor's repository lookups + schema NOT NULL constraints are
     * satisfied. We:
     *
     * <ul>
     *   <li>resolve the {@code openai} vendor seeded by
     *       {@code TurLLMVendorOnStartup} (no vendor row → schema constraint
     *       on {@code llm_vendor_id} trips);</li>
     *   <li>set a placeholder URL so the {@code url} NOT NULL column passes;</li>
     *   <li>encrypt a literal {@code "mock-key"} via the real
     *       {@link TurSecretCryptoService} so the executor's
     *       {@code decrypt(apiKeyEncrypted)} round-trips without a
     *       Base64 / cipher-text format error.</li>
     * </ul>
     */
    protected TurLLMInstance createMockableLlmInstance() {
        TurLLMVendor openaiVendor = llmVendorRepository.findAll().stream()
                .filter(v -> "openai".equalsIgnoreCase(v.getPlugin()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "OpenAI vendor not seeded — TurLLMVendorOnStartup must run "
                                + "before this IT (default profile does)"));
        TurLLMInstance instance = new TurLLMInstance();
        instance.setTitle("mock-llm-" + UUID.randomUUID().toString().substring(0, 8));
        instance.setModelName("mock-model");
        instance.setUrl("https://mock.invalid/v1");
        instance.setEnabled(1);
        instance.setTurLLMVendor(openaiVendor);
        instance.setApiKeyEncrypted(cryptoService.encrypt("mock-key"));
        return llmInstanceRepository.save(instance);
    }

    /**
     * Drives {@code executor.execute(...)} synchronously to completion and
     * returns the captured SSE events. Defaults to a single user turn with
     * no system override, no conversation id, no flow id — subclasses pass
     * the optional arguments when they need them.
     */
    protected List<ChatResponse> runTurn(TurAIAgent agent,
            TurLLMInstance llmInstance,
            List<ChatMessageItem> history,
            String systemPromptOverride,
            String conversationId,
            String flowId) {
        return executor.execute(new TurAgentChatRequest(agent, llmInstance, history,
                        systemPromptOverride, conversationId, flowId, null, null))
                .collectList()
                .block(Duration.ofSeconds(5));
    }

    /** Shortcut: single user message, no system override, no flow. */
    protected List<ChatResponse> runTurn(TurAIAgent agent,
            TurLLMInstance llmInstance,
            String userMessage) {
        return runTurn(agent, llmInstance,
                List.of(new ChatMessageItem("user", userMessage)),
                null, null, null);
    }

    /** First captured prompt — the "initial" LLM call in the harness order. */
    protected Prompt firstPrompt() {
        List<Prompt> all = harness.capturedPrompts();
        if (all.isEmpty()) {
            throw new IllegalStateException(
                    "No prompts captured yet — call runTurn(...) first");
        }
        return all.get(0);
    }

    /**
     * Second captured prompt — semantically the regen call in today's
     * executor. Will become a structural failure once the §I.5 inversion
     * lands and {@code regenerateReplyForNewNode} is deleted; tests that
     * call this past the refactor should be deleted too (they're locking
     * patches #7-#14, which the refactor removes).
     */
    protected Prompt regenPrompt() {
        List<Prompt> all = harness.capturedPrompts();
        if (all.size() < 2) {
            throw new IllegalStateException(
                    "Expected a regen prompt but only " + all.size() + " were captured");
        }
        return all.get(1);
    }

    /** Pulls the first {@link SystemMessage} text out of a {@link Prompt}. */
    protected static String systemTextOf(Prompt prompt) {
        return prompt.getInstructions().stream()
                .filter(SystemMessage.class::isInstance)
                .map(Message::getText)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Prompt has no SystemMessage — this is unexpected"));
    }

    /**
     * Joins all message texts of the prompt for substring assertions that
     * don't care which role carried the text (e.g. "INSTRUÇÃO DE PRIORIDADE"
     * appears once in the system message; we just want to assert it's
     * somewhere in the composed call).
     */
    protected static String allTextOf(Prompt prompt) {
        StringBuilder sb = new StringBuilder();
        for (Message m : prompt.getInstructions()) {
            sb.append(m.getMessageType()).append(": ").append(m.getText()).append('\n');
        }
        return sb.toString();
    }

    /**
     * Counts non-overlapping occurrences of {@code needle} in {@code haystack}.
     * Mirrors Apache Commons {@code countMatches} — extracted so structural
     * tests don't drag in commons-lang3 just for this.
     */
    protected static int countMatches(String haystack, String needle) {
        if (haystack == null || needle == null || needle.isEmpty()) return 0;
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    /**
     * Convenience: build a {@code Map.of(...)}-style history list quickly.
     * Pairs alternate role / content. Odd-length input throws so typos are
     * caught at write time.
     */
    protected static List<ChatMessageItem> history(String... rolesAndContents) {
        if (rolesAndContents.length % 2 != 0) {
            throw new IllegalArgumentException(
                    "history(...) needs an even number of arguments (role, content, role, content, ...)");
        }
        List<ChatMessageItem> out = new java.util.ArrayList<>(rolesAndContents.length / 2);
        for (int i = 0; i < rolesAndContents.length; i += 2) {
            out.add(new ChatMessageItem(rolesAndContents[i], rolesAndContents[i + 1]));
        }
        return out;
    }

    /** Convenience: convert a {@link Map} into a chat history (deterministic order). */
    protected static List<ChatMessageItem> historyFromMap(Map<String, String> roleToContent) {
        return roleToContent.entrySet().stream()
                .map(e -> new ChatMessageItem(e.getKey(), e.getValue()))
                .toList();
    }
}
