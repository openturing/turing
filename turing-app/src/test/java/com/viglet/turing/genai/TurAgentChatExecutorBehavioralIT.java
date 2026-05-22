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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.client.okhttp.OpenAIOkHttpClientAsync;
import com.viglet.turing.genai.flow.ChatFlowGraph;
import com.viglet.turing.genai.flow.TurChatFlowEngineService;
import com.viglet.turing.genai.testsupport.ChatFlowImportTestUtil;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.agent.TurAIAgentSlotRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowStateRepository;
import com.viglet.turing.persistence.repository.persona.TurPersonaRepository;
import com.viglet.turing.testutil.AbstractTuringSpringIT;

/**
 * Behavioral integration tests for {@link TurAgentChatExecutor} — paired
 * skeletons for the critical patches in {@code docs/IMPROVEMENTS.md}
 * §VI.3 pairing matrix that need <i>real</i> LLM behavior to lock the
 * contract (anchoring, persona voice, chip congruence).
 *
 * <h2>Scope after T16</h2>
 *
 * The original Block A roadmap (§VI.6 Phase 5) planned 6 paired behavioral
 * skeletons (#1, #3, #7, #10, #12, #13). After the T16 terminal refactor
 * that deleted the regen path + OVERRIDE block, three of those pair to
 * code that no longer exists or are tautological given the structural pair:
 *
 * <ul>
 *   <li><b>#3</b> — tool-strip: deleted because the structural test already
 *       proves {@code toolCallbacks} is an empty list in the captured
 *       prompt. With an empty callback list, Spring AI's tool execution
 *       loop has nothing to invoke — the behavioral assertion would be
 *       tautological (covered by construction).</li>
 *   <li><b>#10</b> — HIGHEST-PRIORITY OVERRIDE block: deleted in T16
 *       along with {@code regenerateReplyForNewNode}. No code path remains
 *       to verify.</li>
 *   <li><b>#12</b> — aiInstruction re-inline inside OVERRIDE: same as #10.
 *       The post-T16 single-prompt path composes against the
 *       post-advance node's {@code aiInstruction} by construction, with
 *       no override block to anchor against.</li>
 * </ul>
 *
 * <p>Surviving skeletons (#1, #7, #13) lock contracts that DO survive the
 * inversion verbatim — only the route to the assertion changed.
 *
 * <h2>Activation</h2>
 *
 * <pre>{@code
 * mvn verify -Pllm-it -pl turing-app -Dskip.npm=true
 * }</pre>
 *
 * <p>Without {@code OPENAI_API_KEY} the class is short-circuited by
 * JUnit's {@link EnabledIfEnvironmentVariable} — zero API cost, zero
 * round-trips. The default failsafe profile excludes
 * {@code *BehavioralIT.java} entirely so PR / CI runs don't load this class.
 *
 * <h2>Test pattern</h2>
 *
 * Drives the {@link TurChatFlowEngineService engine} directly (not the
 * full {@code TurAgentChatExecutor}) — same shape as
 * {@code ProgramaMatchEEFlowIT}. Each test:
 *
 * <ol>
 *   <li>Imports a harness fixture and seeds state at a known cursor.</li>
 *   <li>Calls {@code engine.advance(...)} to crystallize the post-advance
 *       cursor + variables (this is what the executor does in setup post-T9).</li>
 *   <li>Composes the prompt as {@code persona.systemInstruction +
 *       engine.buildSystemPromptAddendum(post-advance state)} — same shape
 *       the executor builds.</li>
 *   <li>Calls {@code chatModel.call(prompt)} against real OpenAI and asserts
 *       the reply matches the behavioral contract (loose regex per §VI.4.3).</li>
 * </ol>
 *
 * <p><b>Why not autowire the executor?</b> The
 * {@code TurAgentChatExecutorMockSupport @TestConfiguration} (used by
 * Structural/Contract ITs) registers a {@code @Primary TurLlmModelFactory}
 * mock. When loaded into the same Maven JVM, that bean wins even for
 * classes that don't {@code @Import} it — the executor would then call
 * the mocked chat model instead of real OpenAI. Driving the engine + a
 * locally-built {@link ChatModel} sidesteps that interference cleanly.
 *
 * <p>Tolerance strategy (per §VI.4.3): retry zero. Flake → adjust regex /
 * synonyms before relaxing. {@code gpt-4o-mini} flake-rate at temp 0
 * with a deterministic prompt is &lt; 5% — loose regex absorbs.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class TurAgentChatExecutorBehavioralIT extends AbstractTuringSpringIT {

    private static final String OPENAI_BASE_URL = "https://api.openai.com/v1";
    private static final String OPENAI_MODEL = "gpt-4o-mini";

    @Autowired
    private TurChatFlowEngineService engine;
    @Autowired
    private TurAIAgentRepository agentRepository;
    @Autowired
    private TurChatFlowRepository chatFlowRepository;
    @Autowired
    private TurAIAgentSlotRepository slotRepository;
    @Autowired
    private TurPersonaRepository personaRepository;
    @Autowired
    private TurChatFlowStateRepository stateRepository;

    private ChatModel chatModel;
    private TurAIAgent agent;

    @BeforeAll
    void buildChatModel() {
        String apiKey = System.getenv("OPENAI_API_KEY");
        OpenAIClient sync = OpenAIOkHttpClient.builder()
                .baseUrl(OPENAI_BASE_URL).apiKey(apiKey).build();
        OpenAIClientAsync async = OpenAIOkHttpClientAsync.builder()
                .baseUrl(OPENAI_BASE_URL).apiKey(apiKey).build();
        chatModel = OpenAiChatModel.builder()
                .openAiClient(sync)
                .openAiClientAsync(async)
                .options(OpenAiChatOptions.builder()
                        .model(OPENAI_MODEL)
                        .temperature(0.0)
                        .build())
                .build();
    }

    @BeforeEach
    void freshAgent() {
        TurAIAgent a = new TurAIAgent();
        a.setTitle("behavioral-" + UUID.randomUUID().toString().substring(0, 8));
        a.setEnabled(1);
        agent = agentRepository.save(a);
    }

    private ChatFlowImportTestUtil.Repos importRepos() {
        return new ChatFlowImportTestUtil.Repos(
                agentRepository, chatFlowRepository, slotRepository, personaRepository);
    }

    /**
     * Looks up the post-walk persona by reading the engine-controlled
     * {@code __activePersonaId} variable from the persisted flow state and
     * resolving it against the agent's persona catalog. Mirrors what
     * {@link com.viglet.turing.genai.persona.TurAgentPersonaResolver} does
     * — by reaching for it directly here we avoid coupling to the executor
     * (and the mock factory that comes with autowiring it).
     */
    private TurPersona resolvePostWalkPersona(TurAIAgent a, TurChatFlowState state) {
        String json = state.getVariablesJson();
        if (json == null || json.isBlank()) {
            return a.getDefaultPersona();
        }
        int idx = json.indexOf("\"__activePersonaId\"");
        if (idx < 0) {
            return a.getDefaultPersona();
        }
        // Cheap JSON sniff — variablesJson is small + flat enough that this
        // is fine for tests; production code uses ObjectMapper through
        // TurAgentPersonaResolver.readActivePersonaId.
        int colon = json.indexOf(':', idx);
        int q1 = json.indexOf('"', colon + 1);
        int q2 = json.indexOf('"', q1 + 1);
        if (colon < 0 || q1 < 0 || q2 < 0) {
            return a.getDefaultPersona();
        }
        String activeId = json.substring(q1 + 1, q2);
        return a.getPersonas().stream()
                .filter(p -> activeId.equals(p.getId()))
                .findFirst()
                .orElse(a.getDefaultPersona());
    }

    /**
     * Composes the prompt the executor would build for the supplied
     * post-advance state and persona, then calls the real {@link #chatModel}
     * once and returns the assistant text. Throws if the model returns no
     * text — that's a fail-fast for a misconfigured fixture, not a flake.
     */
    private String composeAndCall(TurChatFlow flow, TurChatFlowState state, ChatFlowGraph graph,
            TurPersona persona, String userMessage) {
        String addendum = engine.buildSystemPromptAddendum(flow, state, graph);
        String system = (persona == null ? "" : persona.getSystemInstruction() + "\n\n") + addendum;
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(system));
        // No prior history — the user message is the ENTIRE conversation
        // from the LLM's POV. Keeps the assertion isolated: any "anchoring"
        // failure has to come from the system prompt's composition, not
        // from accumulated history.
        messages.add(new UserMessage(userMessage));
        Prompt prompt = new Prompt(messages);
        var response = chatModel.call(prompt);
        if (response.getResult() == null
                || response.getResult().getOutput() == null
                || response.getResult().getOutput().getText() == null) {
            throw new AssertionError("ChatModel returned no text for prompt: " + prompt);
        }
        String text = response.getResult().getOutput().getText();
        System.out.println("=== Behavioral reply (" + userMessage + ") ===");
        System.out.println(text);
        System.out.println("=== END ===");
        return text;
    }

    /** Synthetic assistant turn used to seed the engine's advance call. The text
     *  is irrelevant to the strategy under HEURISTIC; the engine just needs a
     *  non-null assistantMessage to mark the prior turn as completed. */
    private static final String SYNTHETIC_ASSISTANT_REPLY = "ok";

    /**
     * Patch #1 — persona post-flow voice (real LLM pairing of the structural #1 test)
     * Pairing: {@link TurAgentChatExecutorStructuralIT#resolvesPersonaAfterFlowSelection_picksWalkWrittenPersonaForInitialCall}
     * Fixture: {@code harness-it/persona-switch-mid-flow.chat-flow.json}
     * Pinning: gpt-4o-mini @ 2026-05
     *
     * <p>Cenário behavioral: o walk transparente cruza {@code persona-lucas}
     * via {@code __force_route = "lucas"}. O reply DEVE compor em VOZ DE
     * LUCAS (peer/alumni), não em voz de Marina (consultora formal).
     * Pre-T10 regression signal: reply contém "consultora" + assina como
     * "Marina"; pós-T10 reply contém marcadores de Lucas.
     */
    @Test
    void postWalkPersonaSwap_initialReplyComesInLucasVoiceNotMarina() throws IOException {
        ChatFlowImportTestUtil.ExportEntry entry = ChatFlowImportTestUtil.loadFromClasspath(
                "/harness-it/persona-switch-mid-flow.chat-flow.json");
        ChatFlowImportTestUtil.Imported imp = ChatFlowImportTestUtil.importIntoAgent(
                agent, entry, importRepos());

        String conv = "behavioral-patch1-" + UUID.randomUUID();
        TurChatFlowState seeded = new TurChatFlowState();
        seeded.setConversationId(conv);
        seeded.setFlow(imp.flow());
        seeded.setCurrentNodeId("ai-name");
        seeded.setVariablesJson("{\"__force_route\":\"lucas\"}");
        seeded = stateRepository.save(seeded);

        ChatFlowGraph graph = engine.parseGraph(imp.flow()).orElseThrow();
        TurChatFlowState postAdvance = engine.advance(imp.flow(), seeded, graph,
                "Maria", SYNTHETIC_ASSISTANT_REPLY, chatModel).state();

        // The post-walk cursor must have landed on ai-objetivo (via
        // switch-route → persona-lucas → ai-objetivo). If the seed didn't
        // route correctly, the assertion below would test the wrong node.
        assertThat(postAdvance.getCurrentNodeId())
                .as("Walk must land on ai-objetivo after persona-lucas")
                .isEqualTo("ai-objetivo");

        TurAIAgent reloaded = agentRepository.findById(agent.getId()).orElseThrow();
        TurPersona resolved = resolvePostWalkPersona(reloaded, postAdvance);
        assertThat(resolved)
                .as("Post-walk persona resolution must yield Lucas")
                .isNotNull();
        assertThat(resolved.getName()).contains("Lucas");

        String reply = composeAndCall(imp.flow(), postAdvance, graph, resolved, "Maria");

        // Loose voice-marker assertion (per §VI.4.3). Lucas's systemInstruction
        // teaches peer/alumni speak: "Beleza", "Conta aí" — OR he signs with
        // his own name. Any ONE of these signals proves the post-walk persona
        // wired correctly. Observed gpt-4o-mini renders include:
        //   "E aí, Maria! Conta aí, qual é o seu objetivo..."
        //   "Beleza, Maria — qual é o seu objetivo de carreira?"
        //   "Conta aí: qual é o seu objetivo? — Lucas"
        // A Marina reply ("Prezada Maria, qual seria seu objetivo...") matches
        // NONE of these, so the disjunction discriminates cleanly.
        assertThat(reply.toLowerCase())
                .as("Reply must carry at least one Lucas voice marker "
                        + "(peer greeting, casual imperative, or literal name). Got: %s", reply)
                .containsAnyOf("lucas", "beleza", "conta aí", "conta ai", "e aí", "e ai");
        // Negative: Marina formal markers must NOT appear. Three distinctive
        // tokens — "consultora" is in her systemInstruction, "prezad" and
        // "cordialmente" are the canonical PT formal markers her tone implies.
        assertThat(reply.toLowerCase())
                .as("Marina formal markers must NOT appear when post-walk persona is Lucas. Got: %s", reply)
                .doesNotContain("consultora")
                .doesNotContain("prezad")
                .doesNotContain("cordialmente");
    }

    /**
     * Patch #7 — reply IS the question for the post-advance node.
     * Structural pair was DELETED in T16 (regen path gone). This behavioral
     * test survives because the OBSERVABLE contract is identical post-T16:
     * after advance crosses from {@code ai-name} to {@code ai-objetivo}, the
     * LLM reply asks the objetivo question, not a re-ask of name.
     * Fixture: {@code harness-it/persona-switch-mid-flow.chat-flow.json}
     * Pinning: gpt-4o-mini @ 2026-05
     */
    @Test
    void postAdvance_replyIsQuestionForNewNode_notReAskOfPreviousNode() throws IOException {
        ChatFlowImportTestUtil.ExportEntry entry = ChatFlowImportTestUtil.loadFromClasspath(
                "/harness-it/persona-switch-mid-flow.chat-flow.json");
        ChatFlowImportTestUtil.Imported imp = ChatFlowImportTestUtil.importIntoAgent(
                agent, entry, importRepos());

        // Route through marina (not lucas) to isolate this assertion from
        // the persona-voice one in patch #1.
        String conv = "behavioral-patch7-" + UUID.randomUUID();
        TurChatFlowState seeded = new TurChatFlowState();
        seeded.setConversationId(conv);
        seeded.setFlow(imp.flow());
        seeded.setCurrentNodeId("ai-name");
        seeded.setVariablesJson("{\"__force_route\":\"marina\"}");
        seeded = stateRepository.save(seeded);

        ChatFlowGraph graph = engine.parseGraph(imp.flow()).orElseThrow();
        TurChatFlowState postAdvance = engine.advance(imp.flow(), seeded, graph,
                "Maria", SYNTHETIC_ASSISTANT_REPLY, chatModel).state();
        assertThat(postAdvance.getCurrentNodeId())
                .as("Walk must land on ai-objetivo via the marina branch")
                .isEqualTo("ai-objetivo");

        TurAIAgent reloaded = agentRepository.findById(agent.getId()).orElseThrow();
        TurPersona resolved = resolvePostWalkPersona(reloaded, postAdvance);
        String reply = composeAndCall(imp.flow(), postAdvance, graph, resolved, "Maria");

        // Positive: reply asks the objetivo question (distinctive markers
        // come from the aiInstruction "qual é o objetivo de carreira pros
        // próximos 3 anos").
        assertThat(reply.toLowerCase())
                .as("Reply must be the ai-objetivo question. Got: %s", reply)
                .containsAnyOf("objetivo", "3 anos", "carreira", "próximos");
        // Negative: must NOT re-ask the visitor's name — that's the
        // ai-name aiInstruction. Re-asking means anchoring regressed.
        assertThat(reply.toLowerCase())
                .as("Reply must NOT re-ask the visitor's name. Got: %s", reply)
                .doesNotContain("primeiro nome")
                .doesNotContain("qual é o seu nome");
    }

    /**
     * Patch #13 — chip congruence. Structural pair was DELETED in T16 (chip
     * inlining lived inside the regen OVERRIDE block). The behavioral test
     * survives because chips now flow into the single prompt by construction;
     * the LLM's question must admit the 4 chip labels as natural answers.
     * Fixture: {@code harness-it/regen-with-chips.chat-flow.json}
     * Pinning: gpt-4o-mini @ 2026-05
     */
    @Test
    void chipCongruence_postAdvanceQuestionAdmitsAllChipLabelsAsNaturalAnswers() throws IOException {
        ChatFlowImportTestUtil.ExportEntry entry = ChatFlowImportTestUtil.loadFromClasspath(
                "/harness-it/regen-with-chips.chat-flow.json");
        ChatFlowImportTestUtil.Imported imp = ChatFlowImportTestUtil.importIntoAgent(
                agent, entry, importRepos());

        // Seed at ai-cargo so the user reply flows through switch-area
        // into ai-decision-role (which carries the 4 inlineOptions chips).
        String conv = "behavioral-patch13-" + UUID.randomUUID();
        TurChatFlowState seeded = new TurChatFlowState();
        seeded.setConversationId(conv);
        seeded.setFlow(imp.flow());
        seeded.setCurrentNodeId("ai-cargo");
        seeded.setVariablesJson("{}");
        seeded = stateRepository.save(seeded);

        String userMsg = "Gerente de produto numa fintech, com time pequeno";
        ChatFlowGraph graph = engine.parseGraph(imp.flow()).orElseThrow();
        TurChatFlowState postAdvance = engine.advance(imp.flow(), seeded, graph,
                userMsg, SYNTHETIC_ASSISTANT_REPLY, chatModel).state();
        assertThat(postAdvance.getCurrentNodeId())
                .as("Walk must land on ai-decision-role after switch-area")
                .isEqualTo("ai-decision-role");

        TurAIAgent reloaded = agentRepository.findById(agent.getId()).orElseThrow();
        TurPersona resolved = resolvePostWalkPersona(reloaded, postAdvance);
        String reply = composeAndCall(imp.flow(), postAdvance, graph, resolved, userMsg);

        // Positive: question must be about the visitor's ROLE in the
        // purchase decision (admits the 4 chip labels RH/L&D, Gestor,
        // C-level, Comprador as natural answers). Loose regex to absorb
        // gpt-4o-mini's wording variation.
        assertThat(reply.toLowerCase())
                .as("Question must admit the 4 chip labels as natural answers. "
                        + "Marker: papel/decisão/função/atuação. Got: %s", reply)
                .matches("(?si).*(papel|decis[aã]o|fun[çc][aã]o|atua[çc][aã]o).*");
        // Negative: the recurring failure mode pre-T16 — asking about
        // team size when the chips are about role-in-decision. If this
        // fires, the chip-inline regressed.
        assertThat(reply.toLowerCase())
                .as("Reply must NOT ask about team size (the incongruent failure mode). Got: %s", reply)
                .doesNotContain("quantas pessoas")
                .doesNotContain("tamanho da equipe")
                .doesNotContain("número de funcionários");
    }
}
