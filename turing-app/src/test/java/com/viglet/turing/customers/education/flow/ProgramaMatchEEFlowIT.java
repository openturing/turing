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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.client.okhttp.OpenAIOkHttpClientAsync;
import com.viglet.turing.genai.flow.ChatFlowGraph;
import com.viglet.turing.genai.flow.TurChatFlowEngineService;
import com.viglet.turing.genai.flow.TurChatFlowEngineService.AdvanceResult;
import com.viglet.turing.genai.flow.TurChatFlowEngineService.FlowSelection;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurAIAgentSlot;
import com.viglet.turing.persistence.model.agent.TurAIAgentSlotType;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.model.agent.TurChatFlowGuardrailMethod;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;
import com.viglet.turing.persistence.model.agent.TurChatFlowTriggerMode;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.persona.TurPersonaLanguageStyle;
import com.viglet.turing.persistence.model.persona.TurPersonaTone;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.agent.TurAIAgentSlotRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowRepository;
import com.viglet.turing.persistence.repository.customtool.TurCustomToolRepository;
import com.viglet.turing.persistence.repository.persona.TurPersonaRepository;
import com.viglet.turing.testutil.AbstractTuringSpringIT;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Integration test for the {@code programa-match-ee} chat-flow that powers the
 * Executive Education demo.
 *
 * <p>The flow drives a 4-turn conversation that captures
 * {@code name → cargo_atual → objetivo → area}, then routes via a {@code switch} into
 * one of four area-specific branches. Each branch chains five {@code slot} SET nodes
 * (area_label, color, career_path, programas_match, cta_visible) before landing on
 * a final {@code aiQuestion} that closes with the consultor CTA.
 *
 * <p>Coverage:
 * <ul>
 *   <li>{@link #importBundle_createsPersonaSlotsAndFlowWithExpectedSettings}: structural
 *       guard against persona enum / slot type / trigger metadata bugs that would
 *       cause an opaque 400 on {@code /import-bundle}.</li>
 *   <li>{@link #autoTriggerRouter_rejectsGreeting_picksFlowOnExplicitIntent}:
 *       two-sided guard that bare greetings ("oi") do NOT trigger (the agent
 *       concierge owns the opener and shows 3-path chips), AND that explicit
 *       career-planning intent DOES trigger the flow.</li>
 *   <li>{@link #happyPath_walksAllSlotsAndLandsOnConfirma}: parameterized across the
 *       4 areas (Finanças, Liderança, Saúde, Tecnologia), running the full
 *       4-turn happy path against a real LLM and asserting the 5-slot transparent
 *       chain in each branch.</li>
 * </ul>
 *
 * <p>Drives the engine with real OpenAI {@code gpt-4o-mini} (temperature 0). Skipped
 * when {@code OPENAI_API_KEY} is not set.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class ProgramaMatchEEFlowIT extends AbstractTuringSpringIT {

    private static final String OPENAI_BASE_URL = "https://api.openai.com/v1";
    private static final String CHAT_MODEL = "gpt-4o-mini";
    private static final String FLOW_RESOURCE = "/customers/education/chat-flow/programa-match-ee.chat-flow.json";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private TurChatFlowEngineService engine;
    @Autowired
    private TurChatFlowRepository chatFlowRepository;
    @Autowired
    private TurAIAgentRepository agentRepository;
    @Autowired
    private TurAIAgentSlotRepository slotRepository;
    @Autowired
    private TurPersonaRepository personaRepository;
    @Autowired
    private TurCustomToolRepository customToolRepository;

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
                        .model(CHAT_MODEL)
                        .temperature(0.0)
                        .build())
                .build();
    }

    /**
     * Refresh the Custom Tool definition straight from the canonical script
     * bundled in {@code src/test/resources/tools/search-ee-programs.groovy}
     * before the first test method runs. The bundled H2 snapshot may carry
     * an OLDER version of the tool (or no tool at all when running off a
     * pristine DB) — this upsert guarantees the IT classpath script is the
     * one the tests exercise. Idempotent: re-running just rewrites the row.
     */
    @BeforeAll
    void seedCustomToolFromClasspath() {
        var tool = EducationEEFixtures.upsertSearchEeProgramsTool(customToolRepository);
        System.out.printf("[IT] Upserted custom tool '%s' id=%s (%d chars of Groovy)%n",
                tool.getTitle(), tool.getId(),
                tool.getGroovyScript() == null ? 0 : tool.getGroovyScript().length());
    }

    @BeforeEach
    void newAgent(TestInfo info) {
        TurAIAgent a = new TurAIAgent();
        a.setTitle("ee-it-" + UUID.randomUUID().toString().substring(0, 8));
        a.setEnabled(1);
        agent = agentRepository.save(a);
        System.out.println();
        System.out.println("=== " + info.getDisplayName() + " ===");
    }

    private static void trace(String fmt, Object... args) {
        System.out.println("  · " + String.format(fmt, args));
    }

    private static void traceState(String label, TurChatFlowState state) {
        System.out.printf("  · %-14s node=%s vars=%s%n",
                label, state.getCurrentNodeId(), state.getVariablesJson());
    }

    // ─────────────────────────── Tests ───────────────────────────

    /**
     * Validates that the published JSON imports cleanly into Turing — same logic
     * the {@code /import-bundle} endpoint runs. Acts as a regression guard against
     * the class of bug we hit with {@code tone=PROFESSIONAL} (an enum value the
     * backend didn't accept, which yielded an opaque 400).
     */
    @Test
    void importBundle_createsPersonaSlotsAndFlowWithExpectedSettings() throws IOException {
        ExportEntry entry = loadExport();
        Imported imp = importIntoAgent(entry);

        trace("Imported persona id=%s name=%s tone=%s style=%s",
                imp.persona.getId(), imp.persona.getName(),
                imp.persona.getTone(), imp.persona.getLanguageStyle());

        assertThat(imp.persona.getName()).isEqualTo("Marina — Consultora de Carreira EE");
        assertThat(imp.persona.getTone())
                .as("Marina persona must use a valid TurPersonaTone enum value")
                .isEqualTo(TurPersonaTone.EXECUTIVE);
        assertThat(imp.persona.getLanguageStyle()).isEqualTo(TurPersonaLanguageStyle.NEUTRAL);
        assertThat(imp.persona.getMandatoryTerms()).contains("Acme").contains("programa");
        assertThat(imp.persona.getForbiddenTerms()).contains("talvez").contains("impossível");
        assertThat(imp.persona.getEnabled()).isEqualTo(1);
        assertThat(imp.persona.getSystemInstruction())
                .as("Marina's system prompt is what makes her stay in voice when no node is active")
                .contains("Marina")
                .contains("Acme Education");

        assertThat(imp.flow.getName()).contains("Programa-Match");
        assertThat(imp.flow.getGuardrailMethod()).isEqualTo(TurChatFlowGuardrailMethod.LLM_JUDGE);
        assertThat(imp.flow.getTriggerMode()).isEqualTo(TurChatFlowTriggerMode.ALWAYS);
        // Trigger contract (post-2026.2.x tightening): bare greetings must
        // NOT auto-trigger the flow — they belong to the agent concierge,
        // which presents 3 path chips. The flow only activates on explicit
        // intent (chip click or career-coaching language). Two-sided assert
        // so a future loose rewording fails the build.
        assertThat(imp.flow.getTriggerDescription())
                .as("Trigger must explicitly reject greetings — concierge owns the opening turn now")
                .containsIgnoringCase("NÃO ATIVAR")
                .containsIgnoringCase("saudações genéricas isoladas")
                .doesNotContainIgnoringCase("incluindo saudações");

        List<TurAIAgentSlot> slots = slotRepository.findByTurAIAgent_IdOrderByNameAsc(agent.getId());
        assertThat(slots).extracting(TurAIAgentSlot::getName)
                .as("All 20 declared slots must be created on the agent — original 9 + lead-capture set (welcome/stat/pitch/share/cta/email/phone/status) + PDF set (proposta_pdf_url/filename/hash)")
                .containsExactlyInAnyOrder("name", "cargo_atual", "objetivo", "area",
                        "area_label", "color", "career_path", "programas_match", "cta_visible",
                        "welcome_personalized", "stat_pitch", "final_pitch", "share_url",
                        "cta_choice", "lead_email", "lead_phone", "lead_status",
                        "proposta_pdf_url", "proposta_pdf_filename", "proposta_pdf_hash");
        for (TurAIAgentSlot s : slots) {
            assertThat(s.getType()).as("slot %s type", s.getName()).isEqualTo(TurAIAgentSlotType.STRING);
        }
    }

    /**
     * Validates the post-2026.2.x trigger contract: the agent concierge owns
     * the opening turn (greeting + 3-path chip menu), the flow only auto-
     * activates on EXPLICIT intent to plan a career. Two-sided test —
     * bare "oi" must NOT trigger, an explicit career-coaching phrase MUST.
     *
     * <p>The earlier version of this test asserted the opposite ("oi" triggers
     * Programa-Match) which was the source of the demo cascade bug: the flow
     * grabbed the opening turn, force-captured the chip click as cargo_atual,
     * and corrupted every downstream slot. Locking the new contract here
     * prevents a regression.
     */
    @Test
    void autoTriggerRouter_rejectsGreeting_picksFlowOnExplicitIntent() throws IOException {
        ExportEntry entry = loadExport();
        Imported imp = importIntoAgent(entry);
        TurAIAgent reloaded = agentRepository.findById(agent.getId()).orElseThrow();

        // ── Negative case: bare greeting must NOT trigger ──
        String convGreet = "conv-greet-" + UUID.randomUUID();
        trace("Asking router for bare greeting 'oi' (should NOT trigger)");
        java.util.Optional<FlowSelection> greetSelection = engine.selectActiveFlow(
                reloaded, convGreet, "oi", chatModel);
        assertThat(greetSelection)
                .as("Router must NOT pick Programa-Match for 'oi' — concierge owns the opener")
                .isEmpty();

        // ── Positive case: explicit career-coaching intent must trigger ──
        String convIntent = "conv-intent-" + UUID.randomUUID();
        String explicitIntent = "Quero montar um plano de carreira pessoal para crescer profissionalmente";
        trace("Asking router for explicit intent '%s'", explicitIntent);
        java.util.Optional<FlowSelection> intentSelection = engine.selectActiveFlow(
                reloaded, convIntent, explicitIntent, chatModel);
        assertThat(intentSelection)
                .as("Router must pick Programa-Match for explicit career-planning intent")
                .isPresent();
        TurChatFlow picked = intentSelection.get().flow();
        trace("Router picked flow id=%s name=%s for explicit intent", picked.getId(), picked.getName());
        assertThat(picked.getId())
                .as("Router must pick the imported Programa-Match flow (only candidate)")
                .isEqualTo(imp.flow.getId());

        // After auto-trigger the engine's transparent walker descends
        // start → persona-marina, landing on ai-name as the first interactive node.
        assertThat(intentSelection.get().state().getCurrentNodeId())
                .as("Auto-trigger must land the new state on ai-name (first interactive node)")
                .isEqualTo("ai-name");
    }

    /**
     * Parameterized happy path: drives the full 4-turn conversation for each of
     * the four areas (Finanças, Liderança, Saúde, Tecnologia) and asserts:
     * <ul>
     *   <li>name → cargo_atual → objetivo → area captured turn-by-turn,</li>
     *   <li>after the switch + 5-slot transparent chain, the state lands on the
     *       expected {@code ai-confirma-*} node,</li>
     *   <li>the area-specific slot writes (color, area_label, cta_visible) are
     *       persisted,</li>
     *   <li>{@code programas_match} is a valid JSON array containing the three
     *       expected program names in order.</li>
     * </ul>
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("areaFixtures")
    void happyPath_walksAllSlotsAndLandsOnConfirma(AreaFixture fixture) throws IOException {
        ExportEntry entry = loadExport();
        Imported imp = importIntoAgent(entry);

        String conv = "conv-" + UUID.randomUUID();
        ChatFlowGraph graph = engine.parseGraph(imp.flow).orElseThrow();
        TurChatFlowState state = engine.loadOrInitState(conv, imp.flow, graph).orElseThrow();
        traceState("after init", state);

        assertThat(state.getCurrentNodeId())
                .as("Transparent walker must descend past start + persona on init")
                .isEqualTo("ai-name");

        // ─── Turn 1: name ────────────────────────────────────────────
        trace("turn 1: user='Alexandre' → ai-cargo");
        state = advance(imp.flow, state, graph,
                "Alexandre",
                "Olá! Sou a Marina, consultora de carreira de Executive Education. " +
                "Em 4 perguntas monto um plano com 2–3 programas pro seu próximo passo. " +
                "Qual é o seu primeiro nome?");
        assertThat(readVar(state, "name")).isEqualToIgnoringCase("Alexandre");
        assertThat(state.getCurrentNodeId()).isEqualTo("ai-cargo");

        // ─── Turn 2: cargo ───────────────────────────────────────────
        trace("turn 2: user='Gerente sênior numa fintech, há 4 anos' → ai-objetivo");
        state = advance(imp.flow, state, graph,
                "Gerente sênior numa fintech, há 4 anos",
                "Prazer, Alexandre! Conta um pouco: qual é o seu cargo atual e há quanto tempo está nele?");
        assertThat(readVar(state, "cargo_atual"))
                .as("Compound free-form cargo+tempo answer must be accepted")
                .isNotBlank()
                .containsIgnoringCase("gerente");
        assertThat(state.getCurrentNodeId()).isEqualTo("ai-objetivo");

        // ─── Turn 3: objetivo ────────────────────────────────────────
        // Use a CONCRETE career goal (specific cargo, no area-name overlap with
        // the switch options) so the judge can't mistake the answer for the
        // next question's value. "Crescer na área de Finanças & Investimentos"
        // would trip the judge into thinking the user already picked an area —
        // a real production behavior we WANT to keep (judge correctly defers to
        // the area question for that phrasing).
        String objetivo = fixture.targetObjective;
        trace("turn 3: user='%s' → ai-area", objetivo);
        state = advance(imp.flow, state, graph,
                objetivo,
                "Anotado. Onde você quer estar daqui a 3 anos, Alexandre?");
        assertThat(readVar(state, "objetivo"))
                .as("Free-form objective must be captured verbatim")
                .isNotBlank();
        assertThat(state.getCurrentNodeId()).isEqualTo("ai-area");

        // ─── Turn 4: area → switch → 5 slot setters → ai-confirma-* ──
        trace("turn 4: user='%s' → switch + 5 slots → %s", fixture.areaInput, fixture.expectedConfirmNode);
        state = advance(imp.flow, state, graph,
                fixture.areaInput,
                "Última pergunta antes do plano, Alexandre: em qual dessas áreas você quer aprofundar? " +
                "💰 Finanças & Investimentos · 👥 Liderança & Gestão · 🏥 Saúde · 💻 Tecnologia & Dados");
        traceState("after turn 4", state);

        assertThat(state.getCurrentNodeId())
                .as("Switch + 5-slot transparent chain must land on %s", fixture.expectedConfirmNode)
                .isEqualTo(fixture.expectedConfirmNode);

        Map<String, String> vars = readVars(state);
        assertThat(vars.get("area"))
                .as("Switch routing variable must be populated")
                .isEqualToIgnoringCase(fixture.areaInput);
        assertThat(vars).containsEntry("area_label", fixture.expectedAreaLabel);
        assertThat(vars).containsEntry("color", fixture.expectedColor);
        assertThat(vars).containsEntry("cta_visible", "true");
        assertThat(vars.get("career_path"))
                .as("career_path slot must hold a JSON array")
                .startsWith("[").endsWith("]");

        List<Map<String, Object>> programs = JSON.readValue(vars.get("programas_match"),
                new TypeReference<List<Map<String, Object>>>() {});
        assertThat(programs)
                .as("programas_match must hold exactly 3 program entries")
                .hasSize(3);
        assertThat(programs).extracting(p -> (String) p.get("name"))
                .as("Programs must match the area's hardcoded list in expected order")
                .containsExactly(fixture.expectedProgramNames);
        for (Map<String, Object> p : programs) {
            assertThat(p).containsKeys("name", "duration", "format", "priceFrom", "tag", "why");
        }
    }

    /**
     * Real-LLM-output test: simulates the executor's two-call architecture for
     * turn 4 (area → fechamento) and asserts on the LLM's text from the SECOND
     * call.
     *
     * <p>Background: in production, {@code TurAgentChatExecutor} calls the LLM
     * once with the current node's Goal (here, {@code ai-area} — "ask which
     * area"), then {@code engine.advance()} walks the switch + 5-slot chain and
     * parks the state on a new {@code aiQuestion} ({@code ai-confirma-financas}).
     * The first reply is just a vague acknowledgment because its Goal was
     * "ask for area", not "deliver the fechamento". The executor now does a
     * SECOND LLM call with the addendum for the new node, and that second
     * reply is what the user sees.
     *
     * <p>This test replays the same two-call pattern: build the prompt for
     * {@code ai-area} → 1st call (discarded), {@code engine.advance()} →
     * rebuild prompt for the new leaf → 2nd call. Asserts that the 2nd reply
     * carries the 3 hardcoded Finanças programs and the consultor CTA.
     */
    @Test
    void turn4_realLlmResponseContainsThreeProgramsAndCta() throws IOException {
        ExportEntry entry = loadExport();
        Imported imp = importIntoAgent(entry);

        String conv = "conv-" + UUID.randomUUID();
        ChatFlowGraph graph = engine.parseGraph(imp.flow).orElseThrow();
        TurChatFlowState state = engine.loadOrInitState(conv, imp.flow, graph).orElseThrow();

        // Walk through turns 1-3 the same way the parameterized happy path does.
        state = advance(imp.flow, state, graph,
                "Alexandre",
                "Olá! Sou a Marina, consultora de carreira de Executive Education. " +
                "Em 4 perguntas monto um plano com 2–3 programas pro seu próximo passo. " +
                "Qual é o seu primeiro nome?");
        state = advance(imp.flow, state, graph,
                "Gerente sênior numa fintech, há 4 anos",
                "Prazer, Alexandre! Conta um pouco: qual é o seu cargo atual e há quanto tempo está nele?");
        state = advance(imp.flow, state, graph,
                "Quero ser CFO de uma scale-up grande em até 3 anos",
                "Anotado. Onde você quer estar daqui a 3 anos, Alexandre?");
        assertThat(state.getCurrentNodeId()).isEqualTo("ai-area");

        // ─── Turn 4: 1st LLM call with ai-area's Goal ─────────────────
        // This is what TurAgentChatExecutor does BEFORE engine.advance().
        String preAddendum = engine.buildSystemPromptAddendum(imp.flow, state, graph);
        String preSystem = imp.persona.getSystemInstruction() + "\n\n" + preAddendum;
        List<org.springframework.ai.chat.messages.Message> preMessages = buildHistoryWithUser(preSystem,
                "Finanças & Investimentos");
        String firstReply = chatModel.call(
                new org.springframework.ai.chat.prompt.Prompt(preMessages))
                .getResult().getOutput().getText();
        System.out.println("=== 1st LLM CALL (ai-area Goal) ===");
        System.out.println(firstReply);

        // ─── Run engine.advance() — moves state through the slot chain ──
        state = advance(imp.flow, state, graph,
                "Finanças & Investimentos",
                firstReply);
        assertThat(state.getCurrentNodeId())
                .as("State must have advanced to ai-confirma-financas after transparent walk")
                .isEqualTo("ai-confirma-financas");

        // ─── 2nd LLM call with the NEW node's Goal ────────────────────
        // This is the regeneration step the executor now performs after
        // detecting the state transitioned through transparent nodes to a
        // new aiQuestion. The user sees THIS reply, not the first one.
        String postAddendum = engine.buildSystemPromptAddendum(imp.flow, state, graph);
        String postSystem = imp.persona.getSystemInstruction() + "\n\n" + postAddendum;
        List<org.springframework.ai.chat.messages.Message> postMessages = buildHistoryWithUser(postSystem,
                "Finanças & Investimentos");
        String secondReply = chatModel.call(
                new org.springframework.ai.chat.prompt.Prompt(postMessages))
                .getResult().getOutput().getText();
        System.out.println("=== 2nd LLM CALL (ai-confirma-financas Goal) ===");
        System.out.println(secondReply);
        System.out.println("=== END 2nd CALL ===");

        // The flow's ai-confirma-* aiInstruction now instructs the LLM to:
        //   1) call the search_ee_programs Custom Tool, which writes the 3
        //      programs into the `programas_match` slot (the React component
        //      ProgramCompareCards reads this slot and renders the cards)
        //   2) reply in a SHORT 1-2 sentence acknowledgment that does NOT
        //      repeat program names/prices — the cards do the visual work
        //   3) close with a question about consultor scheduling vs e-mail
        //
        // This test does NOT cadastra the Custom Tool in the IT's H2 DB, so
        // step 1 doesn't actually fire — but the LLM's reply still has to
        // honor the "short, CTA-closing, no forbidden filler" contract from
        // the aiInstruction. The earlier "must contain MBA Executivo / Valuation
        // / Advanced Finance Bootcamp" assertions are GONE because those names
        // no longer belong in the chat text — they live in the slot.
        assertThat(secondReply)
                .as("Reply must reference the cards UI (not repeat program names)")
                .containsIgnoringCase("card");
        assertThat(secondReply)
                .as("Reply must close with the scheduling/email CTA — accept both 'email' and 'e-mail'")
                .containsIgnoringCase("consultor");
        assertThat(secondReply.toLowerCase())
                .as("Reply must mention email handoff option in either spelling")
                .containsAnyOf("email", "e-mail");
        assertThat(secondReply.toLowerCase())
                .as("Reply must NOT use the forbidden vague phrasing")
                .doesNotContain("vou montar")
                .doesNotContain("um momento")
                .doesNotContain("aguarde");
    }

    /**
     * Unconventional-objetivo regression test. A real visitor reported that
     * answering {@code ai-objetivo} with a non-corporate goal ("Quero ser
     * jogador de futebol") got the flow stuck: the LLM judge repeatedly
     * rejected the input as off-topic, the visitor (frustrated) started
     * typing area names, and the force-capture fallback eventually captured
     * the area name as the {@code objetivo} slot — yielding a nonsensical
     * fechamento "com seu objetivo de Tecnologia & Dados".
     *
     * <p>This test pins the fix: {@code ai-objetivo} must accept ANY answer
     * (including hobbies / sports / non-corporate paths) on the very FIRST
     * turn, advance to {@code ai-area} cleanly, and persist the literal user
     * message as the {@code objetivo} slot. The fechamento later adapts via
     * the CASO B branch in each {@code ai-confirma-*} aiInstruction.
     */
    @Test
    void unconventionalObjetivo_doesNotStallTheFlow() throws IOException {
        ExportEntry entry = loadExport();
        Imported imp = importIntoAgent(entry);

        String conv = "conv-" + UUID.randomUUID();
        ChatFlowGraph graph = engine.parseGraph(imp.flow).orElseThrow();
        TurChatFlowState state = engine.loadOrInitState(conv, imp.flow, graph).orElseThrow();

        state = advance(imp.flow, state, graph,
                "Henrique",
                "Olá! Sou a Marina, consultora de carreira de Executive Education. " +
                "Em 4 perguntas monto um plano com 2–3 programas pro seu próximo passo. " +
                "Qual é o seu primeiro nome?");
        state = advance(imp.flow, state, graph,
                "Web designer há 4 anos",
                "Prazer, Henrique! Conta um pouco: qual é o seu cargo atual e há quanto tempo está nele?");
        assertThat(state.getCurrentNodeId()).isEqualTo("ai-objetivo");

        // The bug: this answer used to bounce off the LLM judge forever.
        trace("turn 3: user='Quero ser jogador de futebol' → should advance, no rejection loop");
        state = advance(imp.flow, state, graph,
                "Quero ser jogador de futebol",
                "Anotado. Onde você quer estar daqui a 3 anos, Henrique?");

        assertThat(readVar(state, "objetivo"))
                .as("Unconventional objetivo MUST be captured literally on the first try")
                .containsIgnoringCase("jogador")
                .containsIgnoringCase("futebol");
        assertThat(state.getCurrentNodeId())
                .as("Flow MUST advance to ai-area on the first turn, never stall on ai-objetivo")
                .isEqualTo("ai-area");
    }

    /**
     * Replays the 4 prior conversational turns of the happy path and appends
     * the supplied last user message. Used by the real-LLM test to give the
     * model identical context across both calls.
     */
    private static List<org.springframework.ai.chat.messages.Message> buildHistoryWithUser(
            String systemPrompt, String lastUserMessage) {
        List<org.springframework.ai.chat.messages.Message> messages = new java.util.ArrayList<>();
        messages.add(new org.springframework.ai.chat.messages.SystemMessage(systemPrompt));
        addUser(messages, "oi");
        addAssistant(messages, "Olá! Sou a Marina, consultora de carreira de Executive Education. " +
                "Em 4 perguntas monto um plano com 2–3 programas pro seu próximo passo. " +
                "Qual é o seu primeiro nome?");
        addUser(messages, "Alexandre");
        addAssistant(messages, "Prazer, Alexandre! Conta um pouco: qual é o seu cargo atual e há quanto tempo está nele?");
        addUser(messages, "Gerente sênior numa fintech, há 4 anos");
        addAssistant(messages, "Anotado. Onde você quer estar daqui a 3 anos, Alexandre?");
        addUser(messages, "Quero ser CFO de uma scale-up grande em até 3 anos");
        addAssistant(messages, "Última pergunta antes do plano, Alexandre: em qual dessas áreas você quer aprofundar? " +
                "💰 Finanças & Investimentos · 👥 Liderança & Gestão · 🏥 Saúde · 💻 Tecnologia & Dados");
        addUser(messages, lastUserMessage);
        return messages;
    }

    private static void addUser(List<org.springframework.ai.chat.messages.Message> msgs, String text) {
        msgs.add(new org.springframework.ai.chat.messages.UserMessage(text));
    }

    private static void addAssistant(List<org.springframework.ai.chat.messages.Message> msgs, String text) {
        msgs.add(new org.springframework.ai.chat.messages.AssistantMessage(text));
    }

    /**
     * End-to-end A/B router test — imports BOTH variants simultaneously,
     * drives the real LLM router across many distinct conversations, and
     * asserts:
     * <ol>
     *   <li><b>Distribution</b>: ~50/50 split across {@code marina-consultora}
     *       and {@code lucas-alumni} within statistical tolerance. With
     *       {@code N=20} and a fair 50/50 weight the binomial stddev is
     *       √(N·p·(1-p)) ≈ 2.24; we accept {@code [6, 14]} for each arm
     *       (well outside the 99% CI of 5-15 to keep the test rock-solid
     *       against flake).</li>
     *   <li><b>Stickiness</b>: re-routing the same conversation id returns
     *       the same variant. The continuation path inside
     *       {@code selectActiveFlow} catches the existing flow state on
     *       the second call — but this also pins the property end-to-end
     *       through Spring + JPA, not just the pure {@code assignVariant}
     *       unit.</li>
     * </ol>
     *
     * <p>Cost note: {@code N=20} + 5 stickiness checks = up to 25 LLM
     * router calls. With {@code gpt-4o-mini} at temperature 0 each call
     * is ~1s and ~$0.001 — total under $0.03 and ~30s wall time.
     *
     * @since 2026.2.7
     */
    @Test
    void abRouter_distributesAcrossVariants_andStaysStickyAcrossCalls() throws IOException {
        // Import BOTH variants — the agent now has two flows under the
        // same experimentKey 'programa-match-persona-2026q1'.
        importIntoAgent(loadExport(0));
        Imported lucas = importIntoAgent(loadExport(1));
        // Sanity: both variants must be live on the agent for A/B to fire.
        assertThat(chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc(agent.getId()))
                .as("Agent must have both Marina and Lucas variants imported")
                .hasSize(2);
        assertThat(lucas.flow.getExperimentKey())
                .as("Lucas variant must carry the shared experimentKey")
                .isEqualTo("programa-match-persona-2026q1");

        TurAIAgent reloaded = agentRepository.findById(agent.getId()).orElseThrow();
        final int N = 20;
        Map<String, Integer> counts = new HashMap<>();
        Map<String, String> assignments = new HashMap<>();

        // Use an EXPLICIT career-planning message (no longer "oi") — post-
        // 2026.2.x the bare-greeting trigger was removed; only explicit
        // intent activates this flow. The router's variant pick is
        // independent of the trigger message anyway (hash of conversationId
        // + experimentKey), so changing the message doesn't affect the
        // distribution we're testing.
        String triggerMsg = "Quero montar um plano de carreira pessoal";
        for (int i = 0; i < N; i++) {
            String conv = "conv-ab-" + UUID.randomUUID();
            Optional<FlowSelection> sel = engine.selectActiveFlow(reloaded, conv, triggerMsg, chatModel);
            assertThat(sel).as("Router must pick a flow for conv #%d", i).isPresent();
            String variant = sel.get().flow().getVariantLabel();
            assertThat(variant)
                    .as("Picked flow must carry a variantLabel — A/B routing didn't fire on conv %d", i)
                    .isIn("marina-consultora", "lucas-alumni");
            counts.merge(variant, 1, Integer::sum);
            assignments.put(conv, variant);
        }

        System.out.printf("[A/B IT] Distribution after %d conversations: marina=%d, lucas=%d%n",
                N, counts.getOrDefault("marina-consultora", 0),
                counts.getOrDefault("lucas-alumni", 0));

        // Distribution: 50/50 nominal with statistical slack. Both arms
        // must receive non-trivial traffic — a fully one-sided split would
        // mean assignVariant isn't actually mixing the experimentKey into
        // the hash.
        assertThat(counts.getOrDefault("marina-consultora", 0))
                .as("Marina arm must receive ~50%% of N=%d conversations", N)
                .isBetween(6, 14);
        assertThat(counts.getOrDefault("lucas-alumni", 0))
                .as("Lucas arm must receive ~50%% of N=%d conversations", N)
                .isBetween(6, 14);

        // Stickiness: re-route a sample of conversations and confirm they
        // land on the same variant. selectActiveFlow's continuation path
        // (existing non-terminal state) is the primary mechanism, but the
        // assertion also locks the variant identity end-to-end.
        int sample = Math.min(5, assignments.size());
        int checked = 0;
        for (Map.Entry<String, String> entry : assignments.entrySet()) {
            if (checked++ >= sample) break;
            // Re-route uses the same explicit intent — even though for the
            // continuation path (existing non-terminal state) the message
            // content is irrelevant, keeping it consistent with the original
            // trigger keeps the test readable.
            Optional<FlowSelection> sel = engine.selectActiveFlow(
                    reloaded, entry.getKey(), triggerMsg, chatModel);
            assertThat(sel).isPresent();
            assertThat(sel.get().flow().getVariantLabel())
                    .as("conv %s must stick to '%s' across calls", entry.getKey(), entry.getValue())
                    .isEqualTo(entry.getValue());
        }
        System.out.printf("[A/B IT] Stickiness check: %d/%d sticky on re-route%n", checked, sample);
    }

    /**
     * Variant of the happy path that exercises the Lucas (alumni peer-tone)
     * A/B arm. Validates that the LLM_JUDGE strategy accepts user input
     * across the 4 turns even with the more permissive {@code forbiddenTerms}
     * and the casual {@code NARRATIVE} persona style — the concern being
     * that the judge could become lax and accept off-topic answers, or
     * strict and reject valid ones, when the persona tone shifts.
     *
     * <p>Imports {@code bundle.get(1)} (Lucas) in isolation so no A/B
     * routing happens at the engine level — single candidate, deterministic
     * walk. Mirrors {@link #happyPath_walksAllSlotsAndLandsOnConfirma}
     * exactly, only differing on node ids (suffix {@code -lucas}) and the
     * assistant-context strings that simulate Lucas's peer voice.
     *
     * @since 2026.2.7
     */
    @ParameterizedTest(name = "Lucas/{0}")
    @MethodSource("areaFixturesLucas")
    void happyPathLucas_walksAllSlotsAndLandsOnConfirma(AreaFixture fixture) throws IOException {
        ExportEntry entry = loadExport(1);
        assertThat(entry.name).as("Bundle entry 1 must be the Lucas variant")
                .contains("Lucas");
        Imported imp = importIntoAgent(entry);

        String conv = "conv-" + UUID.randomUUID();
        ChatFlowGraph graph = engine.parseGraph(imp.flow).orElseThrow();
        TurChatFlowState state = engine.loadOrInitState(conv, imp.flow, graph).orElseThrow();
        traceState("after init (Lucas)", state);

        assertThat(state.getCurrentNodeId())
                .as("Transparent walker must descend past start + persona-lucas on init")
                .isEqualTo("ai-name-lucas");

        // ─── Turn 1: name ────────────────────────────────────────────
        trace("Lucas turn 1: user='Alexandre' → ai-cargo-lucas");
        state = advance(imp.flow, state, graph,
                "Alexandre",
                "Opa! Sou o Lucas, formei no MBA Executivo de Executive Education em 2024. " +
                "Quando entrei aqui no site eu também tava perdido entre os programas, " +
                "então deixa eu te ajudar a achar o seu. Como você se chama?");
        assertThat(readVar(state, "name")).isEqualToIgnoringCase("Alexandre");
        assertThat(state.getCurrentNodeId()).isEqualTo("ai-cargo-lucas");

        // ─── Turn 2: cargo ───────────────────────────────────────────
        trace("Lucas turn 2: peer-tone cargo question");
        state = advance(imp.flow, state, graph,
                "Gerente sênior numa fintech, há 4 anos",
                "Beleza, Alexandre. Conta aí: o que você faz hoje e há quanto tempo? " +
                "Qualquer cargo serve — não precisa achar que sua resposta é pequena demais.");
        assertThat(readVar(state, "cargo_atual"))
                .as("Lucas judge must accept the same compound cargo+tempo answer")
                .isNotBlank()
                .containsIgnoringCase("gerente");
        assertThat(state.getCurrentNodeId()).isEqualTo("ai-objetivo-lucas");

        // ─── Turn 3: objetivo ────────────────────────────────────────
        String objetivo = fixture.targetObjective;
        trace("Lucas turn 3: user='%s' → ai-area-lucas", objetivo);
        state = advance(imp.flow, state, graph,
                objetivo,
                "Quando eu comecei o programa, meu objetivo era virar Head de Estratégia — " +
                "hoje cheguei. E o seu, Alexandre? Daqui a 3 anos, onde você se vê?");
        assertThat(readVar(state, "objetivo"))
                .as("Lucas judge must capture free-form objective verbatim")
                .isNotBlank();
        assertThat(state.getCurrentNodeId()).isEqualTo("ai-area-lucas");

        // ─── Turn 4: area → switch → 5 slot setters → ai-confirma-*-lucas ──
        trace("Lucas turn 4: '%s' → switch + 5 slots → %s",
                fixture.areaInput, fixture.expectedConfirmNode);
        state = advance(imp.flow, state, graph,
                fixture.areaInput,
                "Última pergunta antes de eu te mostrar o que rolaria pro seu perfil, Alexandre: " +
                "qual dessas áreas te atrai mais agora? — " +
                "💰 Finanças & Investimentos · 👥 Liderança & Gestão · 🏥 Saúde · 💻 Tecnologia & Dados");
        traceState("after turn 4 (Lucas)", state);

        assertThat(state.getCurrentNodeId())
                .as("Switch + 5-slot transparent chain must land on %s", fixture.expectedConfirmNode)
                .isEqualTo(fixture.expectedConfirmNode);

        Map<String, String> vars = readVars(state);
        assertThat(vars.get("area"))
                .as("Switch routing variable must be populated")
                .isEqualToIgnoringCase(fixture.areaInput);
        assertThat(vars).containsEntry("area_label", fixture.expectedAreaLabel);
        assertThat(vars).containsEntry("color", fixture.expectedColor);
        assertThat(vars).containsEntry("cta_visible", "true");
        // Lucas-specific slot writes (stat_pitch and final_pitch carry peer voice)
        assertThat(vars.get("stat_pitch"))
                .as("Lucas stat_pitch must be a peer-tone quote signed '— Lucas'")
                .endsWith("— Lucas");
        assertThat(vars.get("final_pitch"))
                .as("final_pitch must have been interpolated (no remaining {{...}} placeholders)")
                .doesNotContain("{{").doesNotContain("}}");
        assertThat(vars.get("share_url"))
                .as("Lucas share_url must include the v=lucas marker for variant attribution")
                .contains("v=lucas");

        // Programs are the SAME across variants — the A/B is about voice
        // and framing, not catalog. Cross-validating ensures the area
        // branch in the Lucas graph wasn't accidentally edited to point
        // at different programs.
        List<Map<String, Object>> programs = JSON.readValue(vars.get("programas_match"),
                new TypeReference<List<Map<String, Object>>>() {});
        assertThat(programs).hasSize(3);
        assertThat(programs).extracting(p -> (String) p.get("name"))
                .containsExactly(fixture.expectedProgramNames);
    }

    /**
     * Regression test for the production drift reported on 2026-05-22:
     * the LLM at {@code ai-name-lucas} <i>used to</i> render the long Lucas
     * intro script ("Sou o Lucas, formei no MBA Executivo, quando entrei
     * aqui no site eu também tava perdido...") and FORGET to ask the name
     * at the end. The user then typed any reply expecting a name question,
     * and the judge captured that reply into the {@code name} slot
     * (HEURISTIC-style on the first turn).
     *
     * <p>The {@link #happyPathLucas_walksAllSlotsAndLandsOnConfirma} test
     * feeds an idealized {@code assistantMessage} to {@code engine.advance(...)}
     * — it never exercises what the LLM REALLY generates from the
     * {@code aiInstruction}. This test does, asserting two invariants on
     * the actual generation:
     *
     * <ol>
     *   <li>Lucas identifies himself ("lucas" appears in the reply).</li>
     *   <li>The reply ends with a name question (contains "chama" /
     *       "nome" — substring-loose to absorb wording variance).</li>
     * </ol>
     *
     * <p>If a future {@code aiInstruction} edit, persona drift, or
     * gpt-4o-mini upgrade breaks either invariant, this test catches the
     * UX regression immediately instead of waiting for a user report.
     *
     * @since 2026.2.7
     */
    @Test
    void aiNameLucas_realLlmReplyMustIdentifyLucasAndAskTheName() throws IOException {
        ExportEntry entry = loadExport(1);
        assertThat(entry.name).as("Bundle entry 1 must be the Lucas variant")
                .contains("Lucas");
        Imported imp = importIntoAgent(entry);

        String conv = "conv-" + UUID.randomUUID();
        ChatFlowGraph graph = engine.parseGraph(imp.flow).orElseThrow();
        TurChatFlowState state = engine.loadOrInitState(conv, imp.flow, graph).orElseThrow();
        assertThat(state.getCurrentNodeId())
                .as("Init walk must park on ai-name-lucas (the entry interactive node)")
                .isEqualTo("ai-name-lucas");

        // Compose the prompt the executor would build on turn 0 of a fresh
        // conversation and call the real LLM. No prior history — the
        // assertion targets the OPENING utterance, no anchoring carryover.
        String addendum = engine.buildSystemPromptAddendum(imp.flow, state, graph);
        String system = imp.persona.getSystemInstruction() + "\n\n" + addendum;
        List<org.springframework.ai.chat.messages.Message> messages = new java.util.ArrayList<>();
        messages.add(new org.springframework.ai.chat.messages.SystemMessage(system));
        // Production turn 0: the user has only just opened the chat — no
        // user message yet. But the OpenAI Chat Completions API rejects
        // prompts with no user message; the production executor injects a
        // sentinel empty user turn for this case. Mirror that here.
        messages.add(new org.springframework.ai.chat.messages.UserMessage(""));
        String reply = chatModel.call(new org.springframework.ai.chat.prompt.Prompt(messages))
                .getResult().getOutput().getText();
        System.out.println("=== ai-name-lucas turn 0 reply ===");
        System.out.println(reply);
        System.out.println("=== END ===");

        // Invariant 1: Lucas identifies himself.
        assertThat(reply.toLowerCase())
                .as("Opening reply must identify Lucas. Got: %s", reply)
                .contains("lucas");
        // Invariant 2: the reply asks the visitor's name. Loose substring
        // accepts: "Como você se chama?", "Qual é o seu nome?", "Qual
        // seu nome?", "Como te chamo?", etc.
        assertThat(reply.toLowerCase())
                .as("Opening reply must ask for the visitor's name "
                        + "(marker: 'chama' or 'nome'). Got: %s", reply)
                .containsAnyOf("chama", "nome");
    }

    static Stream<Arguments> areaFixturesLucas() {
        return areaFixtures().map(args -> {
            AreaFixture base = (AreaFixture) args.get()[0];
            // Same fixture, but the expectedConfirmNode is suffixed with
            // "-lucas" since the Lucas graph mirrors the Marina graph with
            // that suffix on every node id.
            return Arguments.of(new AreaFixture(
                    base.displayName,
                    base.areaInput,
                    base.expectedAreaLabel,
                    base.expectedColor,
                    base.expectedConfirmNode + "-lucas",
                    base.targetObjective,
                    base.expectedProgramNames));
        });
    }

    // ─────────────────────────── Parameter source ───────────────────────────

    /**
     * Per-area fixture for {@link #happyPath_walksAllSlotsAndLandsOnConfirma}.
     * Mirrors the four switch branches in
     * {@code chat-flow/programa-match-ee.chat-flow.json}.
     */
    private record AreaFixture(
            String displayName,
            String areaInput,
            String expectedAreaLabel,
            String expectedColor,
            String expectedConfirmNode,
            String targetObjective,
            String[] expectedProgramNames) {

        @Override
        public String toString() {
            return displayName;
        }
    }

    static Stream<Arguments> areaFixtures() {
        return Stream.of(
                Arguments.of(new AreaFixture(
                        "Finanças & Investimentos",
                        "Finanças & Investimentos",
                        "Finanças & Investimentos",
                        "navy-financas",
                        "ai-confirma-financas",
                        "Quero ser CFO de uma scale-up grande em até 3 anos",
                        new String[] {
                                "Especialização em Valuation",
                                "MBA Executivo — Trilha Finanças",
                                "Advanced Finance Bootcamp"
                        })),
                Arguments.of(new AreaFixture(
                        "Liderança & Gestão",
                        "Liderança & Gestão",
                        "Liderança & Gestão",
                        "graphite-lideranca",
                        "ai-confirma-lideranca",
                        "Quero virar Diretor de Operações de uma multinacional em 3 anos",
                        new String[] {
                                "Liderança de Alto Impacto",
                                "MBA Executivo",
                                "Board Practice"
                        })),
                Arguments.of(new AreaFixture(
                        "Saúde",
                        "Saúde",
                        "Saúde",
                        "green-saude",
                        "ai-confirma-saude",
                        "Quero ser Diretor Médico de um hospital de referência em 3 anos",
                        new String[] {
                                "Gestão Hospitalar",
                                "MBA em Saúde",
                                "Inovação em Saúde"
                        })),
                Arguments.of(new AreaFixture(
                        "Tecnologia & Dados",
                        "Tecnologia & Dados",
                        "Tecnologia & Dados",
                        "violet-tech",
                        "ai-confirma-tech",
                        "Quero virar CTO de uma fintech consolidada em 3 anos",
                        new String[] {
                                "IA para Negócios",
                                "Data Science Executivo",
                                "Transformação Digital"
                        })));
    }

    // ─────────────────────────── Import pipeline ───────────────────────────

    private record ExportEntry(
            String id,
            String name,
            String description,
            String triggerDescription,
            String triggerMode,
            String guardrailMethod,
            String experimentKey,
            String variantLabel,
            Integer trafficWeight,
            List<Map<String, Object>> personas,
            List<Map<String, Object>> slots,
            Map<String, Object> graph) {}

    private record Imported(TurChatFlow flow, TurPersona persona) {}

    /**
     * Loads the bundled JSON export and returns the FIRST entry (Marina variant,
     * the historical "control"). Pre-A/B refactor compatibility: existing tests
     * keep working without changes.
     */
    private ExportEntry loadExport() throws IOException {
        return loadExport(0);
    }

    /**
     * Loads the N-th flow from the bundle. The Programa-Match file ships
     * two flows since the A/B experiment landed:
     * <ul>
     *   <li>{@code index=0} → Marina (control, executive consultant tone)</li>
     *   <li>{@code index=1} → Lucas (treatment, alumni peer tone)</li>
     * </ul>
     * Use this to import a specific variant for tests that need to validate
     * the LLM_JUDGE strategy doesn't reject the variant's tone.
     *
     * @since 2026.2.7
     */
    private ExportEntry loadExport(int index) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(FLOW_RESOURCE)) {
            Objects.requireNonNull(in, "Missing classpath resource: " + FLOW_RESOURCE);
            List<Map<String, Object>> bundle = JSON.readValue(in,
                    new TypeReference<List<Map<String, Object>>>() {});
            assertThat(bundle).as("Export bundle must contain the EE flow").isNotEmpty();
            assertThat(index)
                    .as("Bundle has %d entries, index %d is out of range", bundle.size(), index)
                    .isBetween(0, bundle.size() - 1);
            Map<String, Object> raw = bundle.get(index);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> personas = (List<Map<String, Object>>) raw.get("personas");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> slots = (List<Map<String, Object>>) raw.get("slots");
            @SuppressWarnings("unchecked")
            Map<String, Object> graph = (Map<String, Object>) raw.get("graph");
            Object trafficWeightRaw = raw.get("trafficWeight");
            Integer trafficWeight = trafficWeightRaw instanceof Number n ? n.intValue() : null;
            return new ExportEntry(
                    (String) raw.get("id"),
                    (String) raw.get("name"),
                    (String) raw.get("description"),
                    (String) raw.get("triggerDescription"),
                    (String) raw.get("triggerMode"),
                    (String) raw.get("guardrailMethod"),
                    (String) raw.get("experimentKey"),
                    (String) raw.get("variantLabel"),
                    trafficWeight,
                    personas, slots, graph);
        }
    }

    /**
     * Replicates the {@code TurChatFlowAPI.turChatFlowImportBundle} pipeline at
     * the granularity this test needs: persona create-or-lookup with id remap,
     * slot auto-create on the agent, flow save with the graph JSON rewritten so
     * {@code personaId} nodes point at the persisted persona's UUID.
     */
    private Imported importIntoAgent(ExportEntry entry) {
        // Re-fetch the agent so its lazily-loaded `personas` collection is
        // in sync with the DB. The A/B router test imports two flows back
        // to back, and JPA's collection-tracking flushed the first import's
        // join-table inserts to the DB — but the in-memory list still
        // sees them as "new" on the second save, causing a duplicate
        // PK insert. Re-fetching is the cleanest reset.
        agent = agentRepository.findById(agent.getId()).orElseThrow();

        // 1) Personas — case-insensitive lookup, create if missing
        Map<String, String> personaIdRemap = new HashMap<>();
        TurPersona resolvedPersona = null;
        if (entry.personas != null) {
            for (Map<String, Object> p : entry.personas) {
                String name = (String) p.get("name");
                TurPersona existing = personaRepository.findByNameIgnoreCase(name).orElse(null);
                TurPersona persona = upsertPersonaFromExport(existing, p);
                String originalId = (String) p.get("id");
                if (originalId != null && !originalId.isBlank()) {
                    personaIdRemap.put(originalId, persona.getId());
                }
                // Idempotent linkage — tests that import multiple flows on
                // the SAME agent (A/B router test imports 2 variants) would
                // otherwise hit a unique-constraint violation on the
                // ai_agent_persona join table when re-adding a persona
                // already attached from a previous importIntoAgent call.
                if (!agent.getPersonas().contains(persona)) {
                    agent.getPersonas().add(persona);
                }
                resolvedPersona = persona;
            }
        }
        agentRepository.save(agent);

        // 2) Slots — auto-create per agent
        if (entry.slots != null) {
            for (Map<String, Object> s : entry.slots) {
                String name = (String) s.get("name");
                if (slotRepository.findByTurAIAgent_IdAndName(agent.getId(), name).isPresent()) {
                    continue;
                }
                TurAIAgentSlot slot = new TurAIAgentSlot();
                slot.setName(name);
                slot.setDescription((String) s.get("description"));
                String type = (String) s.get("type");
                slot.setType(type == null ? TurAIAgentSlotType.STRING : TurAIAgentSlotType.valueOf(type));
                slot.setTurAIAgent(agent);
                slotRepository.save(slot);
            }
        }

        // 3) Flow — definitionJson = stringified graph, with personaId rewritten
        Map<String, Object> rewrittenGraph = rewritePersonaIds(entry.graph, personaIdRemap);
        TurChatFlow flow = new TurChatFlow();
        flow.setName(entry.name);
        flow.setDescription(entry.description);
        flow.setDefinitionJson(JSON.writeValueAsString(rewrittenGraph));
        flow.setEnabled(1);
        flow.setGuardrailMethod(entry.guardrailMethod == null
                ? TurChatFlowGuardrailMethod.LLM_JUDGE
                : TurChatFlowGuardrailMethod.valueOf(entry.guardrailMethod));
        flow.setTriggerDescription(entry.triggerDescription);
        flow.setTriggerMode(entry.triggerMode == null
                ? TurChatFlowTriggerMode.ONCE
                : TurChatFlowTriggerMode.valueOf(entry.triggerMode));
        // A/B experiment metadata — when both variants of an experiment
        // land on the agent, selectActiveFlow.resolveAbVariant will route
        // by hash(conversationId + experimentKey). Tests that want to
        // exercise one variant in isolation should import just that entry.
        flow.setExperimentKey(entry.experimentKey);
        flow.setVariantLabel(entry.variantLabel);
        flow.setTrafficWeight(entry.trafficWeight);
        flow.setTurAIAgent(agent);
        flow = chatFlowRepository.save(flow);

        return new Imported(flow, resolvedPersona);
    }

    /**
     * Upsert: when {@code existing} is non-null, applies the export fields
     * onto it (so the IT validates the JSON data regardless of whether the
     * bundled H2 snapshot already carried a same-named persona); otherwise
     * creates a fresh row. Always persists.
     */
    private TurPersona upsertPersonaFromExport(TurPersona existing, Map<String, Object> p) {
        TurPersona persona = existing != null ? existing : new TurPersona();
        persona.setName((String) p.get("name"));
        persona.setDescription((String) p.get("description"));
        persona.setSystemInstruction((String) p.get("systemInstruction"));
        String tone = (String) p.get("tone");
        if (tone != null && !tone.isBlank()) {
            persona.setTone(TurPersonaTone.valueOf(tone));
        }
        Object verbosity = p.get("verbosity");
        if (verbosity instanceof Number n) {
            persona.setVerbosity(n.intValue() == 0 ? 3 : n.intValue());
        }
        String style = (String) p.get("languageStyle");
        if (style != null && !style.isBlank()) {
            persona.setLanguageStyle(TurPersonaLanguageStyle.valueOf(style));
        }
        persona.setMandatoryTerms((String) p.get("mandatoryTerms"));
        persona.setForbiddenTerms((String) p.get("forbiddenTerms"));
        Object enabled = p.get("enabled");
        persona.setEnabled(enabled instanceof Number n && n.intValue() != 0 ? n.intValue() : 1);
        return personaRepository.save(persona);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> rewritePersonaIds(Map<String, Object> graph,
            Map<String, String> personaIdRemap) {
        if (graph == null || personaIdRemap.isEmpty()) {
            return graph;
        }
        Map<String, Object> copy = new LinkedHashMap<>(graph);
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) copy.get("nodes");
        if (nodes == null) {
            return copy;
        }
        for (Map<String, Object> node : nodes) {
            Object dataObj = node.get("data");
            if (!(dataObj instanceof Map<?, ?>)) {
                continue;
            }
            Map<String, Object> data = (Map<String, Object>) dataObj;
            Object pid = data.get("personaId");
            if (pid instanceof String s) {
                String mapped = personaIdRemap.get(s);
                if (mapped != null) {
                    data.put("personaId", mapped);
                }
            }
        }
        return copy;
    }

    // ─────────────────────────── Helpers ───────────────────────────

    private TurChatFlowState advance(TurChatFlow flow, TurChatFlowState state,
            ChatFlowGraph graph, String userMessage, String assistantMessage) {
        AdvanceResult result = engine.advance(flow, state, graph, userMessage, assistantMessage, chatModel);
        return result.state();
    }

    private static String readVar(TurChatFlowState state, String key) {
        return readVars(state).get(key);
    }

    private static Map<String, String> readVars(TurChatFlowState state) {
        return JSON.readValue(
                state.getVariablesJson() == null ? "{}" : state.getVariablesJson(),
                new TypeReference<Map<String, String>>() {});
    }
}
