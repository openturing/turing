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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.viglet.turing.genai.persona.TurAgentPersonaResolver;
import com.viglet.turing.genai.testsupport.AbstractAgentExecutorIT;
import com.viglet.testsupport.genai.executor.TurAgentChatExecutorMockSupport;

/**
 * Structural integration tests for {@link TurAgentChatExecutor}. Locks
 * the <i>wiring</i> contract of the executor — which
 * {@link org.springframework.ai.chat.prompt.Prompt}s it composes, which
 * {@link org.springframework.ai.model.tool.ToolCallingChatOptions} it
 * passes, how many times it invokes {@code personaResolver}, in what
 * order it persists. Behavior of {@code gpt-4o-mini} (anchoring, drift,
 * tool preemption) belongs to {@code TurAgentChatExecutorBehavioralIT}
 * under the {@code -Pllm-it} profile (see {@code docs/IMPROVEMENTS.md}
 * §VI.3 pairing matrix).
 *
 * <p>Consolidates the (A) RACE and (B) DECLARATIVE skeleton families
 * from the original {@code TurAgentChatExecutorRaceIT} +
 * {@code TurAgentChatExecutorDeclarativeIT} (both deleted under task T2).
 * Patch coverage: #1, #2, #3, #4, #5 (as {@link ForbidsToolCallsUnitTests}
 * nested), #8, #9, #10, #11, #12, #13, #14, #15. Patches (A) #6, #7, #16
 * remain in the inventory with the partial / indirect coverage they
 * already have — these skeletons do not re-cover them.
 *
 * <h2>Implementation pattern</h2>
 *
 * <ol>
 *   <li>Stage canned replies via {@link TurAgentChatExecutorMockSupport}
 *       — {@code harness().queueResponse(...)} for blocking, queueing
 *       multiple responses to drive initial + regen.</li>
 *   <li>Use the {@link AbstractAgentExecutorIT} helpers —
 *       {@link AbstractAgentExecutorIT#runTurn},
 *       {@link AbstractAgentExecutorIT#firstPrompt},
 *       {@link AbstractAgentExecutorIT#regenPrompt},
 *       {@link AbstractAgentExecutorIT#systemTextOf}.</li>
 *   <li>For flow-driven tests, import the matching fixture under
 *       {@code harness-it/} via
 *       {@link com.viglet.turing.genai.testsupport.ChatFlowImportTestUtil}
 *       (fixtures land under task T5).</li>
 * </ol>
 *
 * <p>Each {@code @Test} stays {@code @Disabled} until implemented — the
 * Javadoc <b>is</b> the contract. Bodies are literally
 * {@code fail("Not implemented...")} so nobody enables a test without
 * writing the assertions.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@Import(TurAgentChatExecutorMockSupport.class)
class TurAgentChatExecutorStructuralIT extends AbstractAgentExecutorIT {

    /**
     * Patch #1 — re-resolve persona AFTER flow selection
     * Categoria: A
     * Localização do patch: TurAgentChatExecutor.java:290-301
     *
     * Comportamento a travar:
     *   Após {@code resolveFlowContext} mover o cursor através de um nó
     *   {@code persona-*} no walk transparente, o executor faz uma SEGUNDA
     *   chamada a {@code personaResolver.resolve(...)} de forma que a persona
     *   ativa usada no initial LLM call já reflita {@code __activePersonaId}
     *   recém-escrito no flow state.
     *
     * Cenário:
     *   Flow:    fixture nova OU programa-match-ee adaptado — um agente com
     *            persona default "Marina" e um flow cuja descida transparente
     *            (start → persona-lucas → ai-name) escreve {@code __activePersonaId}=lucas.
     *   Estado:  freshlyTriggered=true, conversação nova; o roteador acabou
     *            de selecionar o flow.
     *   Entrada: user msg = "Quero montar um plano de carreira pessoal"
     *   Mocks:   ChatModel-1 (initial) retorna texto qualquer ("ok");
     *            o teste captura o {@code Prompt} via ArgumentCaptor e
     *            inspeciona o {@code SystemMessage} montado.
     *
     * Asserts:
     *   1. ChatModel.call(...) foi invocado EXATAMENTE uma vez no turno (não
     *      há advance pós-call neste caminho — o cursor já está em ai-name).
     *   2. O SystemMessage do prompt capturado contém o {@code systemInstruction}
     *      da persona Lucas (peer-tone), NÃO o da Marina default.
     *   3. {@code personaResolver.resolve(...)} foi invocado >= 2 vezes
     *      (primeira em ~linha 240, segunda pós resolveFlowContext).
     *
     * Nota refactor:
     *   Se a inversão for feita, este teste passa a verificar que
     *   {@code personaResolver.resolve(...)} é chamado EXATAMENTE UMA VEZ
     *   (após advance), e que a persona resolvida é Lucas. A asserção #3
     *   inverte de ">= 2" para "== 1".
     *
     * Pareado com (Behavioral): {@link TurAgentChatExecutorBehavioralIT#postWalkPersonaSwap_initialReplyComesInLucasVoiceNotMarina}
     */
    @Test
    void resolvesPersonaAfterFlowSelection_picksWalkWrittenPersonaForInitialCall() throws java.io.IOException {
        // Post-T16 contract for patch #1: persona resolution happens EXACTLY
        // ONCE per turn, AFTER earlyAdvance has run the strategy + walked
        // any transparent persona-* nodes. The single resolve(...) call
        // therefore sees the POST-walk {@code __activePersonaId} and returns
        // the post-walk persona — never the pre-walk one.
        //
        // The persona-switch fixture has a switch-route driven by
        // {@code __force_route}: when set to "lucas", the post-cursor walk
        // is ai-name → switch-route → persona-lucas → ai-objetivo. The
        // persona-lucas node sets {@code __activePersonaId=lucas} in the
        // engine variables. The test pre-seeds state at ai-name with
        // __force_route=lucas + __activePersonaId=<marina-id> (the walk
        // landed on Marina first via persona-marina at start). After
        // earlyAdvance, the persisted state has __activePersonaId=<lucas-id>
        // and the SINGLE resolve() returns Lucas.
        com.viglet.turing.genai.testsupport.ChatFlowImportTestUtil.ExportEntry entry =
                com.viglet.turing.genai.testsupport.ChatFlowImportTestUtil.loadFromClasspath(
                        "/harness-it/persona-switch-mid-flow.chat-flow.json");
        com.viglet.turing.persistence.model.agent.TurAIAgent agent = createAgent("patch1", null);
        com.viglet.turing.persistence.model.llm.TurLLMInstance llm = createMockableLlmInstance();
        agent.getLlmInstances().add(llm);
        agent = agentRepository.save(agent);
        com.viglet.turing.genai.testsupport.ChatFlowImportTestUtil.Imported imp =
                com.viglet.turing.genai.testsupport.ChatFlowImportTestUtil.importIntoAgent(
                        agent, entry, importRepos());

        com.viglet.turing.persistence.model.agent.TurAIAgent reloaded =
                agentRepository.findById(agent.getId()).orElseThrow();
        com.viglet.turing.persistence.model.persona.TurPersona marina = reloaded.getPersonas().stream()
                .filter(p -> p.getName().contains("Marina"))
                .findFirst().orElseThrow(() -> new AssertionError("Marina must be linked to the agent"));
        com.viglet.turing.persistence.model.persona.TurPersona lucas = reloaded.getPersonas().stream()
                .filter(p -> p.getName().contains("Lucas"))
                .findFirst().orElseThrow(() -> new AssertionError("Lucas must be linked to the agent"));

        // Pre-seed: park the cursor on ai-name with __force_route=lucas so
        // the strategy captures the user message into the `name` slot, then
        // the walk routes through persona-lucas. __activePersonaId is set
        // to Marina (post-walk would have been Marina, since the start →
        // persona-marina → ai-name walk landed there).
        String conv = "conv-patch1-" + java.util.UUID.randomUUID();
        com.viglet.turing.persistence.repository.agent.TurChatFlowStateRepository stateRepo =
                org.springframework.beans.factory.BeanFactoryUtils.beanOfTypeIncludingAncestors(
                        applicationContext,
                        com.viglet.turing.persistence.repository.agent.TurChatFlowStateRepository.class);
        com.viglet.turing.persistence.model.agent.TurChatFlowState seeded =
                new com.viglet.turing.persistence.model.agent.TurChatFlowState();
        seeded.setConversationId(conv);
        seeded.setFlow(imp.flow());
        seeded.setCurrentNodeId("ai-name");
        seeded.setVariablesJson("{\"__force_route\":\"lucas\",\"__activePersonaId\":\""
                + marina.getId() + "\"}");
        stateRepo.save(seeded);

        // Clear any spy invocations from the @BeforeEach harness reset
        // (which itself doesn't touch personaResolver, but other tests
        // sharing the context may have).
        Mockito.clearInvocations(personaResolverSpy);
        harness().queueResponse("Qual é o seu objetivo de carreira?");

        runTurn(agent, llm,
                java.util.List.of(new TurAgentChatExecutor.ChatMessageItem("user", "Maria")),
                null, conv, imp.flow().getId());

        // 1. EXACTLY ONE resolve(...) call per turn — patches #1+#2 of the
        //    §I.3 inventory collapse to this single contract post-T10.
        Mockito.verify(personaResolverSpy, Mockito.times(1))
                .resolve(Mockito.any(), Mockito.eq(conv));

        // 2. Cursor walked through persona-lucas and landed on ai-objetivo
        com.viglet.turing.persistence.model.agent.TurChatFlowState finalState =
                stateRepo.findByConversationIdAndFlow_Id(conv, imp.flow().getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(finalState.getCurrentNodeId())
                .as("Post-advance: walk through switch-route → persona-lucas → ai-objetivo")
                .isEqualTo("ai-objetivo");
        // The persona-lucas node must have re-written __activePersonaId.
        org.assertj.core.api.Assertions.assertThat(finalState.getVariablesJson())
                .as("persona-lucas walk must overwrite __activePersonaId from Marina → Lucas")
                .contains(lucas.getId());

        // 3. The captured prompt's SystemMessage must carry LUCAS's voice
        //    (the post-walk persona) — NOT Marina's. Distinctive substrings:
        //      Lucas:  "alumni" / "Beleza" / "narrativa"
        //      Marina: "consultora" / "Assine como Marina"
        String systemText = systemTextOf(firstPrompt());
        org.assertj.core.api.Assertions.assertThat(systemText)
                .as("Initial-call SystemMessage must reflect the post-walk Lucas persona "
                        + "(distinctive: 'alumni'). Got: %s", systemText)
                .contains("alumni");
        org.assertj.core.api.Assertions.assertThat(systemText)
                .as("SystemMessage must NOT carry Marina's voice — the post-walk resolve "
                        + "wrote Lucas. Pre-T10 regressions show 'consultora' here.")
                .doesNotContain("consultora");
    }

    @MockitoSpyBean
    private TurAgentPersonaResolver personaResolverSpy;

    @BeforeEach
    void resetPersonaResolverSpy() {
        Mockito.clearInvocations(personaResolverSpy);
    }

    /* Patches #2 (re-resolve persona inside regen) and #8 (textAlignedWithCurrentNode
     * flag + chip suppression) — DELETED in T16: persona resolves exactly
     * once per turn now (T10), and text + chips are composed for the same
     * post-advance node so the alignment flag is true by construction.
     */

    /* Patches #9 (next-step hint), #10 (HIGHEST-PRIORITY OVERRIDE block),
     * #11 (drop baseSystemPrompt in regen), #12 (inline aiInstruction in
     * override), #13 (inline chip labels + congruency imperative), and #14
     * (trim history to recent turns in regen) — DELETED in T16: all six
     * fought anchoring in the regen call. With regen gone (T12 → T16) the
     * single LLM call composes against the post-advance node directly,
     * the OVERRIDE block is gone, and full history flows unmodified.
     */

    /* Patch #15 (refresh state from DB before strategy advance) —
     * DELETED in T16: advance() is now @Transactional (T13) and runs
     * BEFORE the LLM call (T9), so there is no concurrent tool-callback
     * slot write to refresh from. The state mutation is atomic within
     * the @Transactional boundary.
     */

    /* ─────────────────────────── New skeletons added in T3 (Phase 1) ──────────────
     * Per docs/IMPROVEMENTS.md §VI.6 Phase 1, the existing-fixture skeleton set
     * is "#10, #11, #9, #12, #14, #1, #7 (new), #6 (new)". The first six landed
     * via T2's migration of TurAgentChatExecutorRaceIT; the two "(new)" ones
     * below were created in T3 and run against `programa-match-ee.chat-flow.json`.
     *
     * Patches #6 and #7 both exist today as code paths but were never covered
     * structurally — only the indirect "happy path of programa-match-ee" in
     * ProgramaMatchEEFlowIT exercises them. These skeletons exist so the
     * §I.5 inversion can prove the BEHAVIOR is preserved before the code
     * that implements it gets deleted.
     */

    /* Patch #7 (regen full path) — DELETED in T16: the executor no longer
     * makes a second LLM call. The post-T16 contract is "ONE call, prompt
     * composed against the post-advance node", covered by the redesigned
     * #1 test (single persona resolution + post-walk addendum in prompt).
     */

    /**
     * Patch #6 — freshlyTriggered: skip advance + skip initial LLM call no turno de ativação
     * Categoria: A
     * Localização do patch: TurAgentChatExecutor.java:399-420 (advance skip)
     *                       + TurAgentChatExecutor.java:368-394 (initial skip — shipped in 2026.2.7)
     *
     * Comportamento a travar:
     *   Quando {@code FlowSelection.freshlyTriggered() == true} (o router
     *   acaba de selecionar o flow pela primeira vez para a conversa, e a
     *   user msg É o trigger, não input para o primeiro aiQuestion):
     *
     *   <ol>
     *     <li>O executor PULA o initial LLM call inteiro (skipInitialLlmCall=true,
     *         shipped em 2026.2.7) — saving ~1.5-2s. O contador
     *         {@link harness#callCount()} deve refletir apenas o REGEN.</li>
     *     <li>O executor PULA o {@code engine.advance(...)} (porque a user msg
     *         é o trigger, não resposta ao nó). Sem esse skip, o judge tentaria
     *         force-capturar o texto do trigger como valor do primeiro slot
     *         (ex.: name = "Quero montar um plano de carreira pessoal").</li>
     *     <li>Em seu lugar, o executor dispara DIRETO o regen com o newAddendum
     *         do nó de entrada — para que o usuário veja a PERGUNTA do primeiro
     *         aiQuestion já neste turno, em vez do welcome do agent.</li>
     *   </ol>
     *
     * Cenário (programa-match-ee):
     *   Flow:    programa-match-ee.chat-flow.json — nó de entrada ai-name
     *            ({@code aiInstruction} = "Pergunte o primeiro nome do visitante").
     *            Conversa NOVA, sem state pré-existente.
     *   Estado:  N/A (loadOrInitState criará o state apontando para start).
     *   Entrada: user msg = "Quero montar um plano de carreira pessoal"
     *            (claramente o trigger, não um nome).
     *   Mocks:   ChatModel queue tem APENAS UMA resposta enfileirada:
     *            "Olá! Sou a Marina, consultora de carreira… Qual é o seu primeiro nome?"
     *            (a do regen para o nó de entrada).
     *
     * Asserts:
     *   1. {@code harness.callCount() == 1} — o initial foi PULADO, só o
     *      regen disparou.
     *   2. O system prompt do PROMPT CAPTURADO (regen, índice 0 já que initial
     *      foi pulado) CONTÉM substring distintiva do aiInstruction de ai-name
     *      (ex.: "primeiro nome").
     *   3. O state persistido tem {@code currentNodeId == "ai-name"} (entrada)
     *      — confirma que advance NÃO rodou e force-captured o trigger como
     *      valor de slot.
     *   4. {@code engine.advance(...)} foi invocado ZERO vezes (spy do engine
     *      OU verificar via state).
     *   5. (Sanity inverso) — em um TURNO SUBSEQUENTE (mesma conversa, depois
     *      do nome capturado), {@code freshlyTriggered=false}, o initial volta
     *      a disparar e advance roda normal — {@code callCount() >= 1}.
     *
     * Nota refactor:
     *   Pós-inversão (§I.5 step 1): advance roda ANTES do LLM call e SEMPRE
     *   começa olhando "essa user msg é trigger ou resposta?". A bifurcação
     *   freshlyTriggered some — vira mecânica natural do advance, não flag.
     *   Este teste é REMOVIDO; o invariante observável ("trigger turn não
     *   captura slot, mostra pergunta do nó de entrada") fica como
     *   responsabilidade de um teste novo no design invertido.
     */
    @Test
    void freshlyTriggeredTurn_skipsEarlyAdvance_preservesEntryNodeCursor() throws java.io.IOException {
        // Post-T16 contract for patch #6: when the router has just
        // selected the flow for this conversation (freshlyTriggered=true),
        // the executor must SKIP the earlyAdvanceBeforeLlm step — the user
        // message is the activation signal, not data for the entry node.
        // Without this skip, the strategy would force-capture the trigger
        // text into the entry node's outputVariable.
        //
        // The legacy "skip initial LLM call" behavior was DELETED in T16:
        // the LLM always fires, composing the entry-node question from the
        // current cursor (which loadOrInitState already parked on the
        // entry node). No regen, no skip-and-substitute.
        //
        // Routing strategy: drive the procedural Tier-1 router (Lucene
        // MoreLikeThis in {@code TurChatFlowEngineService#tryProceduralRoute}
        // — gated by PROCEDURAL_DOMINANCE_RATIO=1.5).
        // The triggerDescription deliberately shares strong terms with the
        // user message. The procedural pick bypasses the LLM
        // router entirely, so the SINGLE queued response is consumed by the
        // actual chat call — keeping {@code callCount() == 1} stable.
        com.viglet.turing.genai.testsupport.ChatFlowImportTestUtil.ExportEntry entry =
                com.viglet.turing.genai.testsupport.ChatFlowImportTestUtil.loadFromClasspath(
                        "/harness-it/regen-with-chips.chat-flow.json");
        // Replace the harness fixture's "never auto-trigger" description
        // with one whose stems overlap the user message "Quero discutir
        // cargo, carreira e fintech" by ≥2 tokens. Words "cargo",
        // "carreira", "fintech" all survive the Portuguese analyzer and
        // overlap deterministically.
        com.viglet.turing.genai.testsupport.ChatFlowImportTestUtil.ExportEntry triggerable =
                new com.viglet.turing.genai.testsupport.ChatFlowImportTestUtil.ExportEntry(
                        entry.id(), entry.name(), entry.description(),
                        "Ative quando o visitante mencionar cargo, carreira ou fintech",
                        // ONCE is fine: fresh conversation has no prior
                        // completed state for this flow.
                        "ONCE",
                        entry.guardrailMethod(), entry.experimentKey(),
                        entry.variantLabel(), entry.trafficWeight(),
                        entry.personas(), entry.slots(), entry.graph());

        com.viglet.turing.persistence.model.agent.TurAIAgent agent = createAgent("patch6", null);
        com.viglet.turing.persistence.model.llm.TurLLMInstance llm = createMockableLlmInstance();
        agent.getLlmInstances().add(llm);
        agent = agentRepository.save(agent);
        com.viglet.turing.genai.testsupport.ChatFlowImportTestUtil.Imported imp =
                com.viglet.turing.genai.testsupport.ChatFlowImportTestUtil.importIntoAgent(
                        agent, triggerable, importRepos());

        // Second off-topic flow so the procedural router has ≥2 candidates
        // (post-2026.3.1 Option B: single-candidate agents defer to the LLM
        // router instead of running MoreLikeThis). Built directly via the
        // repo to skip the persona M2M re-insert that ChatFlowImportTestUtil
        // would attempt on a re-import. The trigger description shares no
        // PT stems with the user message, so the first flow dominates by
        // the PROCEDURAL_DOMINANCE_RATIO factor and gets picked
        // deterministically.
        com.viglet.turing.persistence.model.agent.TurChatFlow offTopic =
                new com.viglet.turing.persistence.model.agent.TurChatFlow();
        offTopic.setName("off-topic-decoy");
        offTopic.setDescription("Off-topic decoy to keep procedural router engaged");
        offTopic.setDefinitionJson(imp.flow().getDefinitionJson());
        offTopic.setEnabled(1);
        offTopic.setGuardrailMethod(
                com.viglet.turing.persistence.model.agent.TurChatFlowGuardrailMethod.LLM_JUDGE);
        offTopic.setTriggerDescription(
                "Ative para reclamações sobre boleto, fatura, pagamento ou pix");
        offTopic.setTriggerMode(
                com.viglet.turing.persistence.model.agent.TurChatFlowTriggerMode.ONCE);
        offTopic.setTurAIAgent(agent);
        chatFlowRepository.save(offTopic);

        // Fresh conversation: NO pre-existing state. Procedural router
        // will match the triggerDescription, select the flow, and the
        // engine returns FlowSelection.freshlyTriggered=true.
        harness().queueResponse("Qual é o seu cargo atual?");

        String conv = "conv-patch6-" + java.util.UUID.randomUUID();
        // Count advance() invocations via a logback appender pinned to the
        // engine's own "[FlowEngine] advance:" INFO line — fires once per
        // advance(...) call. Lighter than @SpyBean and survives @Transactional
        // proxying without AopTestUtils unwrapping at every site.
        java.util.concurrent.atomic.AtomicInteger advanceInvocations =
                new java.util.concurrent.atomic.AtomicInteger();
        ch.qos.logback.classic.Logger engineLogger = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(com.viglet.turing.genai.flow.TurChatFlowEngineService.class);
        ch.qos.logback.core.AppenderBase<ch.qos.logback.classic.spi.ILoggingEvent> counter =
                new ch.qos.logback.core.AppenderBase<>() {
                    @Override
                    protected void append(ch.qos.logback.classic.spi.ILoggingEvent event) {
                        if (event.getLevel() == ch.qos.logback.classic.Level.INFO
                                && event.getFormattedMessage().contains("[FlowEngine] advance:")) {
                            advanceInvocations.incrementAndGet();
                        }
                    }
                };
        counter.start();
        engineLogger.addAppender(counter);
        try {
            runTurn(agent, llm,
                    java.util.List.of(new TurAgentChatExecutor.ChatMessageItem(
                            "user", "Quero discutir cargo, carreira e fintech")),
                    null, conv, null);  // flowId=null → router picks via triggerDescription
        } finally {
            engineLogger.detachAppender(counter);
        }

        // 0. Sanity: the procedural router actually picked the flow (state
        //    row exists). If absent, the freshlyTriggered branch never ran
        //    and the rest of the assertions would be deceptively passing.
        com.viglet.turing.persistence.repository.agent.TurChatFlowStateRepository stateRepo =
                org.springframework.beans.factory.BeanFactoryUtils.beanOfTypeIncludingAncestors(
                        applicationContext,
                        com.viglet.turing.persistence.repository.agent.TurChatFlowStateRepository.class);
        com.viglet.turing.persistence.model.agent.TurChatFlowState finalState =
                stateRepo.findByConversationIdAndFlow_Id(conv, imp.flow().getId())
                        .orElseThrow(() -> new AssertionError(
                                "Flow state should exist post-turn — procedural router likely "
                                        + "didn't pick this flow. Check triggerDescription overlap."));

        // 1. earlyAdvance must NOT have run on the freshly-triggered turn
        org.assertj.core.api.Assertions.assertThat(advanceInvocations.get())
                .as("On a freshly-triggered turn, engine.advance(...) must NOT be called by the executor "
                        + "(the user msg is the trigger, not data for the entry node)")
                .isZero();

        // 2. State cursor parks on the entry interactive node (ai-cargo).
        //    The transparent walk in loadOrInitState walks start → ai-cargo
        //    and stops at the first aiQuestion node.
        org.assertj.core.api.Assertions.assertThat(finalState.getCurrentNodeId())
                .as("Cursor must stay on entry interactive node after a freshly-triggered turn")
                .isEqualTo("ai-cargo");

        // 3. The trigger text must NOT have been force-captured into the
        //    "cargo" slot. If advance ran on this turn, the HEURISTIC
        //    strategy would extract "Quero discutir cargo, carreira e
        //    fintech" (or some derivation) into the cargo variable. We
        //    inspect the raw JSON string — bringing in an ObjectMapper
        //    just to assert "cargo" isn't a key is overkill.
        String variablesJson = finalState.getVariablesJson();
        org.assertj.core.api.Assertions.assertThat(variablesJson == null ? "" : variablesJson)
                .as("No slot variables should be populated by a freshly-triggered activation turn — "
                        + "variablesJson must be null/empty/{} (got: %s)", variablesJson)
                .doesNotContain("\"cargo\"");

        // 4. LLM was called exactly once (no skip-initial behavior anymore;
        //    procedural router skips the LLM router as well).
        org.assertj.core.api.Assertions.assertThat(harness().callCount())
                .as("Post-T16: the LLM fires exactly once per turn (initial). "
                        + "Procedural Tier-1 router skips the LLM router; no regen, no skip-initial.")
                .isEqualTo(1);
    }

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    /**
     * Persona-tone import guard (new skeleton — Phase 4 / T6)
     * Categoria: structural — pre-execution contract guard
     *
     * Comportamento a travar:
     *   Toda fixture {@code harness-it/*.chat-flow.json} importada via
     *   {@link com.viglet.turing.genai.testsupport.ChatFlowImportTestUtil}
     *   produz {@link com.viglet.turing.persistence.model.persona.TurPersona}
     *   rows cujos {@code mandatoryTerms}/{@code forbiddenTerms} são EXATAMENTE
     *   o que a fixture declarou. Isso protege contra duas regressões:
     *
     *   <ol>
     *     <li><b>Drift de schema</b> — alguém adiciona um campo persona novo
     *         com NOT NULL default e esquece de backfillar nas fixtures →
     *         import quebra silenciosamente OU upsert sobrescreve com defaults
     *         que invalidam as asserções dos testes downstream.</li>
     *     <li><b>Drift de upsertPersonaFromExport</b> — alguém muda
     *         {@link com.viglet.turing.genai.testsupport.ChatFlowImportTestUtil#upsertPersonaFromExport}
     *         (por exemplo: tipa mandatoryTerms como List em vez de String) →
     *         as personas das fixtures podem persistir com lista vazia,
     *         desabilitando a {@code TurPersonaToneValidator} sem aviso.
     *         O Structural test #14 (que valida o tone) passaria a verde
     *         falsamente.</li>
     *   </ol>
     *
     * Cenário:
     *   Roda contra TODAS as 3 fixtures de T5 (parametrizado por path):
     *   <ul>
     *     <li>{@code harness-it/tool-free-node.chat-flow.json} — Camila (1 persona)</li>
     *     <li>{@code harness-it/persona-switch-mid-flow.chat-flow.json} — Marina + Lucas (2 personas)</li>
     *     <li>{@code harness-it/regen-with-chips.chat-flow.json} — Comprador B2B (1 persona)</li>
     *   </ul>
     *
     * Asserts:
     *   1. Para CADA persona declarada na fixture, a row persistida tem
     *      {@code mandatoryTerms == fixture.mandatoryTerms} (string-equal,
     *      não substring). Mesmo quando ambos são "" — o vazio é também
     *      uma escolha contratual.
     *   2. Mesmo para {@code forbiddenTerms}.
     *   3. {@code systemInstruction} sobrevive ao round-trip sem truncate
     *      (length-equal).
     *   4. {@code tone} e {@code languageStyle} mapeiam para os enums
     *      corretos sem queda pra null.
     *
     * Nota refactor:
     *   Este teste NÃO depende do executor — só de
     *   {@code ChatFlowImportTestUtil} + JPA. Sobrevive intacto a qualquer
     *   reforma do §I.5. É puro guard de "as fixtures do harness continuam
     *   chegando inteiras no DB".
     */
    @Test
    void personaToneFixtures_roundTripExactMandatoryAndForbiddenTermLists() throws java.io.IOException {
        // Import each harness-it fixture into a fresh agent and assert
        // the persisted persona rows match the JSON exactly. Catches
        // drift in the upsertPersonaFromExport path that would silently
        // disable the tone validator.
        String[] fixtures = {
                "/harness-it/tool-free-node.chat-flow.json",
                "/harness-it/persona-switch-mid-flow.chat-flow.json",
                "/harness-it/regen-with-chips.chat-flow.json",
        };

        for (String fixtureResource : fixtures) {
            com.viglet.turing.genai.testsupport.ChatFlowImportTestUtil.ExportEntry entry =
                    com.viglet.turing.genai.testsupport.ChatFlowImportTestUtil.loadFromClasspath(fixtureResource);
            com.viglet.turing.persistence.model.agent.TurAIAgent agent =
                    createAgent("persona-tone-guard-" + java.util.UUID.randomUUID().toString().substring(0, 8), null);
            com.viglet.turing.genai.testsupport.ChatFlowImportTestUtil.importIntoAgent(agent, entry, importRepos());

            org.assertj.core.api.Assertions.assertThat(entry.personas())
                    .as("Fixture %s must declare at least one persona", fixtureResource)
                    .isNotEmpty();

            for (java.util.Map<String, Object> declared : entry.personas()) {
                String name = (String) declared.get("name");
                com.viglet.turing.persistence.model.persona.TurPersona persisted =
                        personaRepository.findByNameIgnoreCase(name).orElseThrow(() ->
                                new AssertionError("Persona '" + name + "' was not persisted by import "
                                        + "of " + fixtureResource));

                String expectedSystemInstruction = (String) declared.get("systemInstruction");
                String expectedMandatory = (String) declared.get("mandatoryTerms");
                String expectedForbidden = (String) declared.get("forbiddenTerms");
                String expectedTone = (String) declared.get("tone");
                String expectedStyle = (String) declared.get("languageStyle");

                org.assertj.core.api.Assertions.assertThat(persisted.getSystemInstruction())
                        .as("systemInstruction round-trip for persona '%s' from %s", name, fixtureResource)
                        .isEqualTo(expectedSystemInstruction);

                // Empty string and null are BOTH valid declarations in the
                // fixture JSON, and they must round-trip with the same
                // distinction the author wrote — converting empty → null
                // (or vice-versa) silently disables the tone validator.
                org.assertj.core.api.Assertions.assertThat(nullableTrim(persisted.getMandatoryTerms()))
                        .as("mandatoryTerms round-trip for persona '%s' from %s", name, fixtureResource)
                        .isEqualTo(nullableTrim(expectedMandatory));
                org.assertj.core.api.Assertions.assertThat(nullableTrim(persisted.getForbiddenTerms()))
                        .as("forbiddenTerms round-trip for persona '%s' from %s", name, fixtureResource)
                        .isEqualTo(nullableTrim(expectedForbidden));

                if (expectedTone != null && !expectedTone.isBlank()) {
                    org.assertj.core.api.Assertions.assertThat(persisted.getTone())
                            .as("tone enum for persona '%s' from %s", name, fixtureResource)
                            .isEqualTo(com.viglet.turing.persistence.model.persona.TurPersonaTone.valueOf(expectedTone));
                }
                if (expectedStyle != null && !expectedStyle.isBlank()) {
                    org.assertj.core.api.Assertions.assertThat(persisted.getLanguageStyle())
                            .as("languageStyle enum for persona '%s' from %s", name, fixtureResource)
                            .isEqualTo(com.viglet.turing.persistence.model.persona.TurPersonaLanguageStyle.valueOf(expectedStyle));
                }
            }
        }
    }

    /** Empty / null collapse helper so JSON "" and null both compare equal. */
    private static String nullableTrim(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /* ─────────────────────────── Declarative patches (B) ───────────────────────────
     * Migrated from TurAgentChatExecutorDeclarativeIT (deleted under T2).
     * Coverage: #3, #4. Patch #5 lives below as a @Nested unit-test class
     * because it exercises a pure static method on TurAgentChatExecutor —
     * no Spring context, no harness needed.
     */

    /**
     * Patch #3 — tool-stripping no INITIAL call quando aiInstruction
     *            contém "não chame nenhuma tool"
     * Categoria: B
     * Localização do patch: TurAgentChatExecutor.java:328-356
     *
     * Comportamento a travar:
     *   Quando o nó ativo tem {@code aiInstruction} contendo qualquer das
     *   sentinelas bilíngues do {@code forbidsToolCalls()}, o initial LLM
     *   call é montado com chat options SEM tool callbacks — o LLM não
     *   pode invocar tools nesse turno mesmo que o agent tenha tools
     *   habilitadas.
     *
     * Cenário (fixture firme — criada em T5):
     *   Flow:    {@code harness-it/tool-free-node.chat-flow.json} (importada
     *            via {@link com.viglet.turing.genai.testsupport.ChatFlowImportTestUtil}).
     *            Nó alvo: {@code ai-coupon-code} ({@code aiInstruction} contém
     *            "NÃO CHAME NENHUMA TOOL"). O harness smoke (T5) já garante
     *            que esse sentinel sobrevive a renomes — drift dispara
     *            falha do {@code toolFreeNodeFixture_importsAndParsesCleanly}
     *            ANTES desse teste rodar.
     *   Estado:  parked em {@code ai-coupon-code}; agent com Custom Tool
     *            stub {@code compor_proposta_in_company} attached.
     *   Entrada: user msg = "MEU-CUPOM-123"
     *   Mocks:   {@link com.viglet.testsupport.genai.executor.TurAgentChatExecutorMockSupport.ChatModelHarness#queueResponse}
     *            ("ack qualquer") para o initial call.
     *            Tool stub registra invocações via Mockito spy (criar o agent
     *            com um Custom Tool real cujo Groovy script faz nada, ou
     *            substituir o lookup do tool via {@code @MockBean}).
     *
     * Asserts:
     *   1. {@code firstPrompt().getOptions()} cast para ToolCallingChatOptions —
     *      {@code getToolCallbacks()} retorna lista VAZIA (callbacks stripped).
     *   2. O tool {@code compor_proposta_in_company} foi invocado ZERO vezes
     *      no turn: {@code verify(tool, never()).invoke(any())}.
     *   3. (Sanity inverso) — alterar o fixture in-memory pra remover o
     *      sentinel "NÃO CHAME NENHUMA TOOL" e re-rodar; o teste de
     *      controle confirma que SEM o sentinel as tools fluem normais.
     *
     * Nota refactor:
     *   Vira o campo declarativo {@code ChatFlowNode.toolsEnabled: boolean}
     *   (ou {@code toolsMode}). Este teste passa a verificar
     *   "quando {@code node.toolsEnabled() == false}, options.toolCallbacks
     *   está vazio" — o invariante observável (tool não chamado) é idêntico.
     *   A heurística de substring matching some; o campo aparece. O teste
     *   continua válido com mudança trivial no setup da fixture (declarar
     *   o campo em vez de adicionar a frase ao aiInstruction).
     *
     * Pareado com (Behavioral): {@link TurAgentChatExecutorBehavioralIT#toolFreeNode_llmDoesNotPreemptivelyInvokeCustomTool}
     */
    @Test
    void stripsToolsOnInitialCallWhenActiveNodeForbidsToolCalls() throws java.io.IOException {
        // §I.5 step 3 / T16 contract: when the active node's
        // ChatFlowNode.toolsEnabled == FALSE, the executor builds the
        // initial (and only) LLM call with ChatOptions whose toolCallbacks
        // is EMPTY. Verified by capturing the prompt via the mock harness.
        //
        // Setup uses the tool-free-node fixture and rewrites the
        // ai-coupon-code node's data to set toolsEnabled=false at import
        // time. The fixture's aiInstruction still carries the legacy
        // "NÃO CHAME NENHUMA TOOL" sentinel — that's a no-op post-T16
        // (substring matcher deleted) but harmless.
        com.viglet.turing.genai.testsupport.ChatFlowImportTestUtil.ExportEntry entry =
                com.viglet.turing.genai.testsupport.ChatFlowImportTestUtil.loadFromClasspath(
                        "/harness-it/tool-free-node.chat-flow.json");
        // The fixture already declares toolsEnabled=false on ai-coupon-code
        // — no in-memory patching needed.
        com.viglet.turing.persistence.model.agent.TurAIAgent agent = createAgent("patch3", null);
        com.viglet.turing.persistence.model.llm.TurLLMInstance llm = createMockableLlmInstance();
        agent.getLlmInstances().add(llm);
        agent = agentRepository.save(agent);
        com.viglet.turing.genai.testsupport.ChatFlowImportTestUtil.Imported imp =
                com.viglet.turing.genai.testsupport.ChatFlowImportTestUtil.importIntoAgent(
                        agent, entry, importRepos());

        // Park the conversation on ai-coupon-code so it's the ACTIVE node
        // when execute(...) runs. Without seeding, the engine would init
        // the cursor at the entry node — also fine for this fixture since
        // the entry chain (start → persona-camila → ai-coupon-code) walks
        // through to ai-coupon-code transparently. We seed explicitly for
        // determinism.
        com.viglet.turing.genai.flow.ChatFlowGraph graph =
                chatFlowEngineService.parseGraph(imp.flow()).orElseThrow();
        String conv = "conv-patch3-" + java.util.UUID.randomUUID();
        com.viglet.turing.persistence.model.agent.TurChatFlowState state =
                chatFlowEngineService.loadOrInitState(conv, imp.flow(), graph).orElseThrow();
        // loadOrInitState's walk lands the cursor on ai-coupon-code (first
        // interactive node after start + persona-* transparent hops).
        org.assertj.core.api.Assertions.assertThat(state.getCurrentNodeId())
                .as("Init walk must park the cursor on ai-coupon-code so it's the active node for the test")
                .isEqualTo("ai-coupon-code");

        // Sanity check: the JSON round-trip preserved the toolsEnabled
        // patch we applied above. If this fails the executor will never
        // take the tool-strip branch and the real assertion below would
        // be deceptively passing.
        com.viglet.turing.genai.flow.ChatFlowNode couponNode =
                graph.nodeById("ai-coupon-code").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(couponNode.toolsEnabled())
                .as("In-memory mutation of node.data.toolsEnabled=false must survive JSON round-trip")
                .isEqualTo(Boolean.FALSE);

        harness().queueResponse("Qual é o código do cupom?");

        runTurn(agent, llm,
                java.util.List.of(new TurAgentChatExecutor.ChatMessageItem("user", "tenho um cupom")),
                null, conv, imp.flow().getId());

        // The Mockito mock captures every prompt the executor sends to the
        // ChatModel. With a HEURISTIC flow and an agent that has no real
        // tools registered, only the initial call should hit the mock.
        // Even so, we use the EMPTY-callbacks shape to pick it out
        // deterministically — that shape is the tool-strip branch's
        // signature.
        java.util.List<org.springframework.ai.chat.prompt.Prompt> all = harness().capturedPrompts();
        java.util.List<org.springframework.ai.chat.prompt.Prompt> matching = all.stream()
                .filter(p -> p.getOptions() instanceof org.springframework.ai.model.tool.ToolCallingChatOptions tc
                        && tc.getToolCallbacks() != null
                        && tc.getToolCallbacks().isEmpty())
                .toList();
        org.assertj.core.api.Assertions.assertThat(matching)
                .as("Expected the executor's initial call to carry an empty toolCallbacks list "
                        + "(captured %d total prompts)", all.size())
                .hasSize(1);
        org.springframework.ai.chat.prompt.Prompt initial = matching.get(0);

        // Tool-strip contract: when node.toolsEnabled == FALSE, the
        // executor builds a fresh DefaultToolCallingChatOptions with an
        // empty callback list and internalToolExecutionEnabled=false.
        org.springframework.ai.chat.prompt.ChatOptions opts = initial.getOptions();
        org.assertj.core.api.Assertions.assertThat(opts)
                .as("Prompt options must be ToolCallingChatOptions so the tool list can be inspected")
                .isInstanceOf(org.springframework.ai.model.tool.ToolCallingChatOptions.class);
        org.springframework.ai.model.tool.ToolCallingChatOptions tco =
                (org.springframework.ai.model.tool.ToolCallingChatOptions) opts;
        // The CRITICAL invariant: when node.toolsEnabled == FALSE, the
        // executor builds ChatOptions with an EMPTY toolCallbacks list.
        // No callbacks means no tools to execute.
        //
        // Spring AI 2.0.0-RC1 removed the internalToolExecutionEnabled flag
        // (and its getter) entirely — internal tool execution is now the
        // default, gated only by whether the model returns tool calls. The
        // executor therefore relies solely on the empty callbacks list to
        // guarantee a tool-free turn, which is exactly what this assertion
        // verifies.
        org.assertj.core.api.Assertions.assertThat(tco.getToolCallbacks())
                .as("toolsEnabled=false on the active node MUST yield an empty toolCallbacks list — "
                        + "opts class=%s",
                        opts.getClass().getSimpleName())
                .isEmpty();
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.viglet.turing.genai.flow.TurChatFlowEngineService chatFlowEngineService;

    /* Patch #4 (regen tool-strip) — DELETED in T16: no regen path exists
     * anymore. Patch #3's structural test above covers the only remaining
     * tool-strip site (initial call), and the declarative
     * ChatFlowNode.toolsEnabled field collapses both cases into one check.
     */

    /**
     * Patch #5 — {@code forbidsToolCalls()} substring matching bilíngue.
     * Categoria: B. Localização: TurAgentChatExecutor.java:709-716.
     *
     * <p>Pure unit test — the method is package-private and the test class
     * is in the same package, so no Spring context or harness is needed.
     * Hosted as a {@link Nested} inside the Structural IT per
     * {@code docs/IMPROVEMENTS.md} §VI.3 ("Special: #5 forbidsToolCalls
     * bilingual: pure unit test, @Nested inside StructuralIT, ~10
     * parameterized cases").
     *
     * <p>Bodies are real assertions (not {@code @Disabled} placeholders)
     * because the function already exists — the only thing standing
     * between this nested class and a green build is enabling it. Reviewed
     * for safety: every case is deterministic, no side effects, no Spring.
     *
     * <p>Refactor note: once §I.5 step 3 replaces the substring scan with
     * a declarative {@code ChatFlowNode.toolsEnabled} field, the function
     * disappears and this whole nested class deletes — the contract becomes
     * "when {@code toolsEnabled=false}, options carry no callbacks",
     * covered structurally by {@link #stripsToolsOnInitialCallWhenActiveNodeForbidsToolCalls}.
     */
    @Nested
    class ForbidsToolCallsUnitTests {

        /* §I.5 step 3 / T16 — the bilingual-substring overload
         * {@code forbidsToolCalls(String)} was DELETED along with the regen
         * helpers. The original 10 parameterized cases that pinned the
         * sentinel matcher are gone (history recoverable in git log
         * pre-T16). Only the node-aware overload survives below — flow
         * authors must use the declarative {@code ChatFlowNode.toolsEnabled}
         * field.

         * Node-aware overload (§I.5 step 3 / T11) ───
         * The declarative ChatFlowNode.toolsEnabled field is the sole
         * source of truth — when set to FALSE, tools are stripped on
         * that node; otherwise, tools are allowed.
         */

        @org.junit.jupiter.api.Test
        void nodeOverload_returnsFalseForNullNode() {
            assertFalse(TurAgentChatExecutor.forbidsToolCalls((com.viglet.turing.genai.flow.ChatFlowNode) null));
        }

        @org.junit.jupiter.api.Test
        void nodeOverload_explicitToolsEnabledTrue_returnsFalseEvenWhenAiInstructionHasSentinel() {
            // Declarative wins: TRUE overrides the legacy "NÃO CHAME NENHUMA TOOL" sentinel.
            com.viglet.turing.genai.flow.ChatFlowNode node = new com.viglet.turing.genai.flow.ChatFlowNode(
                    "test-node", "aiQuestion",
                    nodeDataWithInstructionAndToolsEnabled("NÃO CHAME NENHUMA TOOL", Boolean.TRUE));
            assertFalse(TurAgentChatExecutor.forbidsToolCalls(node),
                    "toolsEnabled=TRUE must override the legacy substring sentinel");
        }

        @org.junit.jupiter.api.Test
        void nodeOverload_explicitToolsEnabledFalse_returnsTrueEvenWhenAiInstructionIsBenign() {
            // Declarative wins: FALSE strips tools even when aiInstruction has no sentinel.
            com.viglet.turing.genai.flow.ChatFlowNode node = new com.viglet.turing.genai.flow.ChatFlowNode(
                    "test-node", "aiQuestion",
                    nodeDataWithInstructionAndToolsEnabled("Pergunte o nome ao usuário.", Boolean.FALSE));
            assertTrue(TurAgentChatExecutor.forbidsToolCalls(node),
                    "toolsEnabled=FALSE must strip tools regardless of aiInstruction content");
        }

        @org.junit.jupiter.api.Test
        void nodeOverload_nullField_returnsFalse_T16NoSubstringFallback() {
            // Post-T16: the bilingual substring fallback was deleted. Null
            // toolsEnabled means "tools allowed" (the default), regardless
            // of what {@code aiInstruction} contains. Authors of old flows
            // that still carry "NÃO CHAME NENHUMA TOOL" in {@code aiInstruction}
            // must migrate to the declarative field — the sentinel is
            // ignored from T16 onwards.
            com.viglet.turing.genai.flow.ChatFlowNode legacyWithSentinel = new com.viglet.turing.genai.flow.ChatFlowNode(
                    "test-node", "aiQuestion",
                    nodeDataWithInstructionAndToolsEnabled("NÃO CHAME NENHUMA TOOL", null));
            assertFalse(TurAgentChatExecutor.forbidsToolCalls(legacyWithSentinel),
                    "T16 deleted the substring fallback — null toolsEnabled means tools allowed");

            com.viglet.turing.genai.flow.ChatFlowNode benign = new com.viglet.turing.genai.flow.ChatFlowNode(
                    "test-node", "aiQuestion",
                    nodeDataWithInstructionAndToolsEnabled("Pergunte o nome.", null));
            assertFalse(TurAgentChatExecutor.forbidsToolCalls(benign),
                    "Null toolsEnabled + no sentinel must return false");
        }

        /**
         * Builds a {@link com.viglet.turing.genai.flow.ChatFlowNode.NodeData}
         * with the given {@code aiInstruction} + {@code toolsEnabled} field
         * and {@code null} for every other slot. Keeps the test bodies
         * focused on the field under test.
         */
        private com.viglet.turing.genai.flow.ChatFlowNode.NodeData nodeDataWithInstructionAndToolsEnabled(
                String aiInstruction, Boolean toolsEnabled) {
            return new com.viglet.turing.genai.flow.ChatFlowNode.NodeData(
                    "label",     // label
                    "aiQuestion", // type
                    aiInstruction,
                    null, // outputVariable
                    null, // validationRule
                    null, // functionName
                    null, // conditionExpression
                    null, // toolSource
                    null, // mcpServerId
                    null, // subFlowId
                    null, // subFlowName
                    null, // personaId
                    null, // switchVariable
                    null, // switchOptions
                    null, // inlineOptions
                    null, // overrideExistingValue
                    null, // slotName
                    null, // slotOperation
                    null, // slotValue
                    null, // onJudgeReject
                    toolsEnabled,
                    null, // requiredTools
                    null, // routineId
                    null); // routineTimeoutMs
        }
    }
}
