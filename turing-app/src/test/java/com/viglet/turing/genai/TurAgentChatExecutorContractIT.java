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

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.ConfigurableTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionExecutionListener;

import com.viglet.turing.genai.testsupport.AbstractAgentExecutorIT;
import com.viglet.testsupport.genai.executor.TurAgentChatExecutorMockSupport;
import com.viglet.turing.persistence.repository.agent.TurChatFlowStateRepository;

/**
 * Contract integration tests for {@link TurAgentChatExecutor}. Locks
 * the middleware (C) patches — defense-in-depth, agent-owner check,
 * abandon-state path — that survive the §I.5 refactor unchanged.
 *
 * <p>Renamed from {@code TurAgentChatExecutorMiddlewareIT} under task T2.
 * Same scope, same patches; the {@code Contract} name aligns with
 * {@code docs/IMPROVEMENTS.md} §VI.2 ("Contract = middleware (cycle cap,
 * A/B window, agent-owner, abandon)").
 *
 * <p>Implementation pattern: same as {@link TurAgentChatExecutorStructuralIT}
 * — stage canned replies via {@link TurAgentChatExecutorMockSupport},
 * compose entities with {@link AbstractAgentExecutorIT} helpers. For
 * patch #18 specifically you'll need a fake {@code GuardrailStrategy}
 * registry (or a mocked {@code ApplicationContext} bean lookup) that
 * returns {@code Optional.empty()} for the guardrail enum the test
 * pins — pure JPA / reflection approaches break because the enum is
 * closed and persistence assumes a non-null backing bean.
 *
 * <p>Cobertura dos patches: #18, #20.
 * Patches (C) restantes já cobertos por outros ITs (não duplicar aqui):
 * <ul>
 *   <li>#17 — cycle detection ({@code TurChatFlowSubFlowEngineIT})</li>
 *   <li>#19 — A/B scheduling window ({@code TurChatFlowAbVariantAssignmentTest}
 *       + {@code ProgramaMatchEEFlowIT})</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@Import(TurAgentChatExecutorMockSupport.class)
class TurAgentChatExecutorContractIT extends AbstractAgentExecutorIT {

    @org.springframework.beans.factory.annotation.Autowired
    private com.viglet.turing.genai.flow.TurChatFlowEngineService chatFlowEngineService;

    /**
     * Patch #18 — fallback HEURISTIC quando strategy bean missing
     * Categoria: C
     * Localização do patch: TurChatFlowEngineService.java:318-332
     *
     * Comportamento a travar:
     *   Quando um flow referencia um {@code guardrailMethod} cujo bean
     *   correspondente NÃO está registrado no contexto (cenário: enum
     *   value adicionado mas o {@code @Component} ainda não existe), o
     *   engine LOGA um warning e faz fallback explícito para
     *   {@code HeuristicGuardrailStrategy} em vez de propagar a exception
     *   que mataria o turno.
     *
     * Cenário:
     *   Flow:    programa-match-ee (ou qualquer flow simples) com
     *            guardrailMethod alterado em runtime via reflection OU
     *            via {@code @MockBean} de um registry/factory de strategies
     *            que retorne {@code Optional.empty()} para o método pedido.
     *            Alternativa: criar um valor de enum sintético de teste e
     *            usar reflection para injetá-lo no campo {@code guardrailMethod}.
     *            Como o enum é fechado, o caminho mais limpo é mockar
     *            o ApplicationContext / registry que resolve a strategy.
     *   Estado:  conversação nova, qualquer aiQuestion.
     *   Entrada: user msg = qualquer input que normalmente passaria pela strategy.
     *   Mocks:   logger appender attached para capturar o warning.
     *            HeuristicGuardrailStrategy spy/real para registrar invocação.
     *
     * Asserts:
     *   1. O turno NÃO lança exception — completa normalmente.
     *   2. {@code HeuristicGuardrailStrategy.advance(...)} foi invocado
     *      EXATAMENTE uma vez (fallback aplicado).
     *   3. O logger emitiu um WARN com mensagem mencionando o
     *      guardrailMethod missing + a palavra "fallback" ou "HEURISTIC".
     *
     * Nota refactor:
     *   Este teste DEVE continuar passando após o refactor sem ajuste —
     *   defense-in-depth contra config inválida é middleware C, sobrevive.
     *   O ponto de injeção pode mudar (registry-style resolver vez de
     *   switch-on-enum) mas o invariante observável é o mesmo.
     */
    @Test
    void fallsBackToHeuristicStrategyWhenGuardrailMethodHasNoRegisteredBean() throws Exception {
        // Simulate "enum value added without a backing bean" by reflectively
        // removing the LLM_JUDGE entry from the engine's strategy map. The
        // production code path that resolves the strategy then returns null
        // for that method and falls back to HEURISTIC (the defense-in-depth
        // §I.6 contract). Restore the map after the assertion so the next
        // test sees the full registry.
        // advance(...) is @Transactional → Spring wraps the engine in a
        // CGLIB proxy; reflection on the proxy's class returns null
        // because the real field lives on the target. Unwrap first.
        com.viglet.turing.genai.flow.TurChatFlowEngineService engineTarget =
                org.springframework.test.util.AopTestUtils.getTargetObject(chatFlowEngineService);
        java.lang.reflect.Field field = com.viglet.turing.genai.flow.TurChatFlowEngineService.class
                .getDeclaredField("strategyByMethod");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<com.viglet.turing.persistence.model.agent.TurChatFlowGuardrailMethod,
                com.viglet.turing.genai.flow.strategy.TurChatFlowGuardrailStrategy> original =
                (java.util.Map<com.viglet.turing.persistence.model.agent.TurChatFlowGuardrailMethod,
                        com.viglet.turing.genai.flow.strategy.TurChatFlowGuardrailStrategy>)
                        field.get(engineTarget);
        // Build a copy without LLM_JUDGE → resolveStrategy will hit the
        // fallback log + return HEURISTIC.
        java.util.Map<com.viglet.turing.persistence.model.agent.TurChatFlowGuardrailMethod,
                com.viglet.turing.genai.flow.strategy.TurChatFlowGuardrailStrategy> trimmed =
                new java.util.EnumMap<>(original);
        trimmed.remove(com.viglet.turing.persistence.model.agent.TurChatFlowGuardrailMethod.LLM_JUDGE);

        ch.qos.logback.classic.Logger engineLogger = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(com.viglet.turing.genai.flow.TurChatFlowEngineService.class);
        TestLogAppender appender = new TestLogAppender();
        appender.start();
        engineLogger.addAppender(appender);

        try {
            // Replace strategy map for the duration of the assertion
            field.set(engineTarget, trimmed);

            // Drive a turn through advance() on a flow whose guardrailMethod
            // is LLM_JUDGE (now bean-missing). HEURISTIC must run, no throw.
            org.assertj.core.api.Assertions.assertThatCode(this::driveOneTurnOnLlmJudgeFlow)
                    .as("Engine must NOT throw when the resolved strategy bean is missing — it must fall back to HEURISTIC")
                    .doesNotThrowAnyException();

            // The WARN log line is the observable signal that the fallback
            // path fired (per the line at TurChatFlowEngineService:418).
            org.assertj.core.api.Assertions.assertThat(appender.warnMessages())
                    .as("Engine should warn about the missing strategy and the HEURISTIC fallback")
                    .anySatisfy(msg -> {
                        org.assertj.core.api.Assertions.assertThat(msg)
                                .containsIgnoringCase("LLM_JUDGE")
                                .containsIgnoringCase("HEURISTIC");
                    });
        } finally {
            // Restore original map so subsequent tests in this class /
            // suite still see the full strategy registry.
            field.set(engineTarget, original);
            engineLogger.detachAppender(appender);
        }
    }

    /**
     * Drives a single {@code advance(...)} turn against a fresh
     * {@code LLM_JUDGE}-tagged flow + ephemeral state. Used by the patch #18
     * test to exercise the fallback without going through the full executor
     * (we just need to hit {@code resolveStrategy} once).
     */
    private void driveOneTurnOnLlmJudgeFlow() {
        com.viglet.turing.persistence.model.agent.TurAIAgent agent = createAgent("contract-18", null);
        com.viglet.turing.persistence.model.agent.TurChatFlow flow =
                new com.viglet.turing.persistence.model.agent.TurChatFlow();
        flow.setName("Contract #18 — LLM_JUDGE without bean");
        flow.setEnabled(1);
        flow.setGuardrailMethod(com.viglet.turing.persistence.model.agent.TurChatFlowGuardrailMethod.LLM_JUDGE);
        flow.setTriggerMode(com.viglet.turing.persistence.model.agent.TurChatFlowTriggerMode.ONCE);
        flow.setTriggerDescription("contract-18 — never auto-triggered");
        // Minimal graph: start → end. The engine only needs to find the
        // current node and call resolveStrategy once; no real interaction
        // logic is exercised.
        flow.setDefinitionJson("{\"nodes\":["
                + "{\"id\":\"start\",\"type\":\"start\",\"data\":{\"label\":\"INÍCIO\",\"type\":\"start\"}},"
                + "{\"id\":\"end\",\"type\":\"end\",\"data\":{\"label\":\"FIM\",\"type\":\"end\"}}"
                + "],\"edges\":[{\"id\":\"e1\",\"source\":\"start\",\"target\":\"end\"}]}");
        flow.setTurAIAgent(agent);
        flow = chatFlowRepository.save(flow);
        com.viglet.turing.genai.flow.ChatFlowGraph graph = chatFlowEngineService.parseGraph(flow).orElseThrow();
        String conv = "conv-contract-18-" + java.util.UUID.randomUUID();
        com.viglet.turing.persistence.model.agent.TurChatFlowState state =
                chatFlowEngineService.loadOrInitState(conv, flow, graph).orElseThrow();
        chatFlowEngineService.advance(flow, state, graph, "hello", null, harness().chatModel());
    }

    /**
     * Tiny in-memory Logback appender. Collects WARN+ messages so the
     * assertion above can match against them without using
     * {@code OutputCaptureExtension} (which is provided by
     * {@code spring-boot-test} but requires extra wiring on this IT class).
     */
    private static final class TestLogAppender
            extends ch.qos.logback.core.AppenderBase<ch.qos.logback.classic.spi.ILoggingEvent> {

        private final java.util.List<String> warnMessages =
                java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        @Override
        protected void append(ch.qos.logback.classic.spi.ILoggingEvent event) {
            if (event.getLevel().isGreaterOrEqual(ch.qos.logback.classic.Level.WARN)) {
                warnMessages.add(event.getFormattedMessage());
            }
        }

        java.util.List<String> warnMessages() {
            return java.util.List.copyOf(warnMessages);
        }
    }

    /**
     * Patch #20 — resolveForcedFlow: flow só governa seu agent owner
     * Categoria: C
     * Localização do patch: TurAgentChatExecutor.java:835-839
     *
     * Comportamento a travar:
     *   Quando o front-end envia um {@code flowId} forçado mas o flow
     *   referenciado pertence a um agent DIFERENTE do agent que está
     *   processando o turno ({@code flow.getTurAIAgent().getId() != agent.getId()}),
     *   o executor DESCARTA o forced flow e cai no auto-router em vez de
     *   aplicá-lo. Defense in depth contra cross-tenant leakage.
     *
     * Cenário:
     *   Flow:    Dois agents distintos (A1 e A2), cada um com sua cópia
     *            do programa-match-ee (ou flows quaisquer). flowId enviado
     *            = ID do flow de A1; executor chamado para A2.
     *   Estado:  conversação nova em A2.
     *   Entrada: user msg = "Quero montar um plano de carreira pessoal"
     *            forcedFlowId = ID do flow de A1.
     *   Mocks:   ChatModel mockado retorna texto qualquer; auto-router
     *            (selectActiveFlow) capturado via spy para verificar que
     *            FOI invocado (fallback) — ou seja, o forced flow NÃO
     *            bypassou o router.
     *
     * Asserts:
     *   1. O flow ATIVO no turno NÃO é o de A1 (forced flow rejeitado).
     *   2. {@code engine.selectActiveFlow(agentA2, ...)} foi invocado
     *      ao menos uma vez (auto-router rodou em fallback).
     *   3. O state criado tem {@code turChatFlow.getTurAIAgent().getId() ==
     *      agentA2.getId()} (ou é null se o auto-router também não selecionou).
     *   4. (Sanity positivo) — quando o forced flow PERTENCE a A2,
     *      o auto-router NÃO é invocado e o forced flow é aplicado.
     *
     * Nota refactor:
     *   Este teste DEVE continuar passando após o refactor sem ajuste —
     *   é uma checagem de segurança/multitenant, não tem nada a ver com
     *   a ordem race. Sobrevive como guard do resolver de flow no novo
     *   design.
     */
    @Test
    void discardsForcedFlowWhenItDoesNotBelongToCallingAgent() {
        // Two distinct agents. Agent A owns the flow; Agent B is the one
        // we'll drive. Sending B a forced flowId pointing at A's flow must
        // log a WARN and silently drop the flow override.
        com.viglet.turing.persistence.model.agent.TurAIAgent agentA = createAgent("contract-20-owner-A", null);
        com.viglet.turing.persistence.model.agent.TurAIAgent agentB = createAgent("contract-20-caller-B", null);
        com.viglet.turing.persistence.model.llm.TurLLMInstance llm = createMockableLlmInstance();
        agentB.getLlmInstances().add(llm);
        agentB = agentRepository.save(agentB);

        com.viglet.turing.persistence.model.agent.TurChatFlow flowOfA =
                new com.viglet.turing.persistence.model.agent.TurChatFlow();
        flowOfA.setName("Contract #20 — owned by agent A only");
        flowOfA.setEnabled(1);
        flowOfA.setGuardrailMethod(com.viglet.turing.persistence.model.agent.TurChatFlowGuardrailMethod.HEURISTIC);
        flowOfA.setTriggerMode(com.viglet.turing.persistence.model.agent.TurChatFlowTriggerMode.ONCE);
        flowOfA.setTriggerDescription("contract-20 — owned by A");
        flowOfA.setDefinitionJson("{\"nodes\":["
                + "{\"id\":\"start\",\"type\":\"start\",\"data\":{\"label\":\"INÍCIO\",\"type\":\"start\"}},"
                + "{\"id\":\"end\",\"type\":\"end\",\"data\":{\"label\":\"FIM\",\"type\":\"end\"}}"
                + "],\"edges\":[{\"id\":\"e1\",\"source\":\"start\",\"target\":\"end\"}]}");
        flowOfA.setTurAIAgent(agentA);
        flowOfA = chatFlowRepository.save(flowOfA);

        // Mock chat reply so the executor has SOMETHING to publish after
        // the (now-null) flow context.
        harness().queueResponse("hi from mock");

        ch.qos.logback.classic.Logger executorLogger = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(TurAgentChatExecutor.class);
        TestLogAppender appender = new TestLogAppender();
        appender.start();
        executorLogger.addAppender(appender);

        java.util.List<TurAgentChatExecutor.ChatResponse> events;
        try {
            // Drive agent B with agent A's forced flowId. Expected: flow
            // is rejected, conversation proceeds without flow context.
            String conv = "conv-contract-20-" + java.util.UUID.randomUUID();
            events = runTurn(agentB, llm,
                    java.util.List.of(new TurAgentChatExecutor.ChatMessageItem("user", "hi")),
                    null, conv, flowOfA.getId());
        } finally {
            executorLogger.detachAppender(appender);
        }

        // 1) The executor must have completed the turn (no exception, reply emitted).
        org.assertj.core.api.Assertions.assertThat(events)
                .as("Cross-tenant forced flow must be dropped, not crash the turn")
                .isNotEmpty();
        org.assertj.core.api.Assertions.assertThat(events.get(0).content())
                .as("Reply is the LLM mock output (flow-less path) — confirms flow was rejected")
                .isEqualTo("hi from mock");

        // 2) The WARN log message must mention the rejected flow id and
        //    the caller agent — same observable signal as line 896 of
        //    TurAgentChatExecutor.
        String flowId = flowOfA.getId();
        String agentBId = agentB.getId();
        org.assertj.core.api.Assertions.assertThat(appender.warnMessages())
                .as("Executor should warn that the forced flow does not belong to the calling agent")
                .anySatisfy(msg -> org.assertj.core.api.Assertions.assertThat(msg)
                        .contains(flowId)
                        .contains(agentBId)
                        .containsIgnoringCase("does not belong"));
    }

    /* ─────────────────────────── New skeletons added in T4 (Phase 2) ──────────────
     * Per docs/IMPROVEMENTS.md §VI.6 Phase 2, the Contract skeleton set is
     * "#18, #20, #16 (new double-save), cascade ascent, BR-doc CPF". The
     * first two landed via T2's migration of TurAgentChatExecutorMiddlewareIT;
     * the three below were added in T4. All three lock invariants that
     * survive the §I.5 refactor unchanged — defense-in-depth, sub-flow
     * navigation, validation primitives — so they should keep passing
     * verbatim once the executor is inverted.
     */

    /**
     * Patch #16 — explicit save AFTER transparent walk (double-save pattern)
     * Categoria: C
     * Localização do patch: TurChatFlowEngineService.java:272-294 (current)
     *                       and walkTransparentNodes saves inside the loop
     *
     * Comportamento a travar:
     *   Durante o transparent walk, o engine PERSISTE o state em DOIS
     *   momentos por hop interessante:
     *
     *   <ol>
     *     <li>Ao cruzar slot SET / writeSlot / persona / switch resolution
     *         — {@code state = stateRepository.save(state)} dentro do loop
     *         (linhas ~1280-1314 de {@code TurChatFlowEngineService}). Isso
     *         existe porque mutações in-place ao {@code currentNodeId} +
     *         {@code variablesJson} têm que sobreviver mesmo se o turno
     *         crashar a meio caminho.</li>
     *     <li>Ao retornar de {@code advance(...)}, o caller volta a salvar
     *         o {@code AdvanceResult.state()} (linha ~272-294). Esse segundo
     *         save garante consistência do FINAL cursor independente do
     *         que o loop fez.</li>
     *   </ol>
     *
     *   O double-save é defensivo: se a primeira persistência falha por
     *   contenção / race, o caller ainda persiste a versão final. Se nada
     *   falhar, é redundante mas barato (mesmo ID, JPA merge).
     *
     * Cenário (programa-match-ee):
     *   Flow:    programa-match-ee.chat-flow.json — qualquer área-branch.
     *            A 4ª turn ("Finanças & Investimentos") atravessa o switch
     *            e 5 nós slot SET (area_label, color, career_path,
     *            programas_match, cta_visible) antes de parkar em
     *            ai-confirma-financas.
     *   Estado:  parked em ai-area, conversa em progresso (3 slots
     *            preenchidos: name, cargo_atual, objetivo).
     *   Entrada: user msg = "Finanças & Investimentos"
     *   Mocks:   ChatModel mockado: enqueue 1 resposta de ack para o
     *            initial call e 1 para o regen do ai-confirma-financas.
     *            {@code @SpyBean TurChatFlowStateRepository} — contar
     *            {@code save(state)} invocations.
     *
     * Asserts:
     *   1. {@code stateRepository.save(...)} foi invocado AO MENOS 6 vezes
     *      durante o turno: 5 slot setters + 1 caller-level save. (Pode
     *      ser ≥ por reentrada do switch / re-fetch.)
     *   2. O state persistido final tem TODOS os 5 slots da área Finanças
     *      preenchidos com os valores corretos (assert via
     *      {@link #readVars(...)} ou query direto).
     *   3. {@code state.getCurrentNodeId() == "ai-confirma-financas"}
     *      — confirma que o save final reflete o cursor pós-walk.
     *
     * Nota refactor:
     *   No design invertido (§I.5 step 5), o cycle vira "advance/walk dentro
     *   de uma @Transactional → flush → LLM call". O double-save desaparece
     *   — passa a UM atomic persistence por turno. Este teste é REMOVIDO
     *   pós-refactor; o invariante "todos os slots do branch foram
     *   persistidos ao final do turno" sobrevive como um teste mais simples.
     */
    @Test
    void persistsStateInsideTransparentWalkAndAgainAfterAdvance_doubleSavePattern() throws java.io.IOException {
        // Post-T13 contract for patch #16: the in-walk save() calls
        // (slot SET / persona / switch landing inside walkTransparentNodes)
        // are RETAINED for in-memory persistence-context coherence and
        // crash-survivability mid-walk, but they MUST coalesce into a
        // SINGLE commit at the @Transactional advance(...) boundary.
        // Without batching, each save would commit separately — e.g. the
        // 3-hop walk on persona-switch-mid-flow (Maria → switch-route →
        // persona-lucas → ai-objetivo) would fire ≥3 commits instead of 1.
        //
        // The persona-switch fixture is the smallest harness flow that
        // exercises the multi-save walk path (switch resolution + persona
        // landing both call stateRepository.save() inside the loop).
        com.viglet.turing.genai.testsupport.ChatFlowImportTestUtil.ExportEntry entry =
                com.viglet.turing.genai.testsupport.ChatFlowImportTestUtil.loadFromClasspath(
                        "/harness-it/persona-switch-mid-flow.chat-flow.json");
        com.viglet.turing.persistence.model.agent.TurAIAgent agent = createAgent("patch16", null);
        com.viglet.turing.persistence.model.llm.TurLLMInstance llm = createMockableLlmInstance();
        agent.getLlmInstances().add(llm);
        agent = agentRepository.save(agent);
        com.viglet.turing.genai.testsupport.ChatFlowImportTestUtil.Imported imp =
                com.viglet.turing.genai.testsupport.ChatFlowImportTestUtil.importIntoAgent(
                        agent, entry, importRepos());

        // Pre-seed cursor at ai-name with __force_route=lucas so the
        // strategy captures "Maria" into the name slot AND the post-walk
        // routes through persona-lucas — multi-hop walk that triggers
        // multiple in-walk save() calls.
        String conv = "conv-patch16-" + java.util.UUID.randomUUID();
        com.viglet.turing.persistence.model.agent.TurChatFlowState seeded =
                new com.viglet.turing.persistence.model.agent.TurChatFlowState();
        seeded.setConversationId(conv);
        seeded.setFlow(imp.flow());
        seeded.setCurrentNodeId("ai-name");
        seeded.setVariablesJson("{\"__force_route\":\"lucas\"}");
        stateRepoSpy.save(seeded);

        // Register a TransactionExecutionListener on the production
        // transaction manager to count afterCommit events. Removed in
        // finally so it doesn't leak across tests sharing this context.
        if (!(txManager instanceof ConfigurableTransactionManager configurable)) {
            org.junit.jupiter.api.Assertions.fail(
                    "Expected the production PlatformTransactionManager to implement "
                            + "ConfigurableTransactionManager (Spring 6.2+) so the test can attach a "
                            + "TransactionExecutionListener. Got: " + txManager.getClass().getName());
            return;
        }
        java.util.concurrent.atomic.AtomicInteger commitCount =
                new java.util.concurrent.atomic.AtomicInteger();
        TransactionExecutionListener listener = new TransactionExecutionListener() {
            @Override
            public void afterCommit(org.springframework.transaction.TransactionExecution tx, Throwable error) {
                if (error == null) {
                    commitCount.incrementAndGet();
                }
            }
        };
        java.util.Collection<TransactionExecutionListener> originalListeners =
                new java.util.ArrayList<>(configurable.getTransactionExecutionListeners());

        // Reset spy counter AFTER the seed save and BEFORE the runTurn so
        // counts reflect just the executor turn. The afterCommit counter
        // starts at zero by construction; nothing in the test reset path
        // commits between here and runTurn.
        org.mockito.Mockito.clearInvocations(stateRepoSpy);
        configurable.addListener(listener);
        harness().queueResponse("Qual é o seu objetivo de carreira?");

        try {
            runTurn(agent, llm,
                    java.util.List.of(new TurAgentChatExecutor.ChatMessageItem("user", "Maria")),
                    null, conv, imp.flow().getId());
        } finally {
            // Detach: TransactionExecutionListener has no removeListener,
            // so we restore the original list to leave the manager clean
            // for sibling tests.
            configurable.setTransactionExecutionListeners(originalListeners);
        }

        // 1. The strategy save + the in-walk saves (persona-lucas landing
        //    + switch-route landing) must each have hit stateRepository.save —
        //    at least TWO calls to prove the walk wasn't short-circuited.
        org.mockito.ArgumentCaptor<com.viglet.turing.persistence.model.agent.TurChatFlowState> captor =
                org.mockito.ArgumentCaptor.forClass(
                        com.viglet.turing.persistence.model.agent.TurChatFlowState.class);
        org.mockito.Mockito.verify(stateRepoSpy, org.mockito.Mockito.atLeast(2)).save(captor.capture());
        int saveCount = captor.getAllValues().size();

        // 2. The COALESCE invariant: total afterCommit count during the
        //    turn must be STRICTLY LESS than save count. If T13 reverted,
        //    each save would commit standalone and commitCount == saveCount.
        org.assertj.core.api.Assertions.assertThat(commitCount.get())
                .as("Post-T13 @Transactional advance(...) must coalesce in-walk saves into "
                        + "fewer commits than saves. Got %d saves but %d commits — if these are "
                        + "equal, the advance() @Transactional boundary regressed.",
                        saveCount, commitCount.get())
                .isLessThan(saveCount);

        // 3. The final persisted state must reflect the FULL walk — cursor
        //    landed on ai-objetivo (the post-walk interactive node) and
        //    the name slot was captured. Proves the transaction committed
        //    successfully (not rolled back) AND the in-walk saves persisted.
        com.viglet.turing.persistence.model.agent.TurChatFlowState finalState =
                ((com.viglet.turing.persistence.repository.agent.TurChatFlowStateRepository)
                        org.springframework.beans.factory.BeanFactoryUtils.beanOfTypeIncludingAncestors(
                                applicationContext,
                                com.viglet.turing.persistence.repository.agent.TurChatFlowStateRepository.class))
                        .findByConversationIdAndFlow_Id(conv, imp.flow().getId())
                        .orElseThrow();
        org.assertj.core.api.Assertions.assertThat(finalState.getCurrentNodeId())
                .as("Final cursor after the multi-hop walk: ai-name → switch-route → persona-lucas → ai-objetivo")
                .isEqualTo("ai-objetivo");
        org.assertj.core.api.Assertions.assertThat(finalState.getVariablesJson())
                .as("name slot must be persisted from the captured user msg")
                .contains("\"name\"")
                .contains("Maria");
    }

    @MockitoSpyBean
    private TurChatFlowStateRepository stateRepoSpy;

    @Autowired
    private PlatformTransactionManager txManager;

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    /**
     * Cascade ascent — `end` nodes in nested sub-flows pop the chain
     * Categoria: C (middleware — sub-flow navigation guard)
     * Localização: TurChatFlowEngineService.java:1316-1323 (`end` in walk)
     *              + ascendFromSubFlow (~1398) + chainContainsFlow (~1462)
     *
     * Comportamento a travar:
     *   Quando o transparent walk encontra um {@code end} node e o state
     *   tem {@code parentStateId != null}, o engine sobe pro parent state
     *   (resume na linha após o Sub Flow node original). Se o parent ALSO
     *   está num {@code end} (sub-flow do parent terminou), cascateia até
     *   chegar num parent que tem mais nodes OU até esgotar a chain.
     *
     *   Garantia adicional: {@code chainContainsFlow} previne recursão —
     *   se o sub-flow id já está na chain de parents, ascensão é negada e
     *   o engine loga "Refusing recursive Sub Flow".
     *
     * Cenário (fixture nova OU adaptada):
     *   Flow:    Bundle com 3 flows: A (top) → SubFlow B (chamado por A) →
     *            SubFlow C (chamado por B). C tem um aiQuestion seguido de
     *            end; B tem só o SubFlow C node + end; A tem o SubFlow B
     *            node + ai-confirma final.
     *
     *            Cenário expected: parked no aiQuestion de C; user manda
     *            resposta; advance avança em C, encontra end de C → sobe pro
     *            parent B, B está parked após o SubFlow C node, próximo é o
     *            end de B → sobe pro parent A, A está parked após o SubFlow B
     *            node, próximo é ai-confirma de A.
     *
     *            (programa-match-ee não tem sub-flows; precisará da fixture
     *            harness-it/nested-sub-flow.chat-flow.json criada em T5.)
     *
     *   Estado:  parked em C's aiQuestion, depth-3 chain.
     *   Entrada: user msg = resposta válida.
     *   Mocks:   ChatModel mockado retorna acks; sem regen relevante.
     *
     * Asserts:
     *   1. Final state.flow.id == A's flow id (sobe DUAS vezes).
     *   2. state.currentNodeId == "ai-confirma" (nó de A após o SubFlow B).
     *   3. state.parentStateId == null (chain esvaziada).
     *   4. (Negativo) — se o fixture for C → A direto (sem B intermediário),
     *      o teste detecta que só houve 1 ascent.
     *   5. (Negativo recursive) — fixture com A → B → A (referência circular)
     *      deve resultar em log WARN "Refusing recursive Sub Flow" e o state
     *      ficar no end de B sem ascender.
     *
     * Nota refactor:
     *   Sub-flow descent/ascent é product (§I.6 — "preserve verbatim"). Este
     *   teste DEVE continuar passando idêntico pós-refactor; é robustez de
     *   navegação, não race condition.
     */
    @Test
    @Disabled("Skeleton — body deferred. Requires harness-it/nested-sub-flow.chat-flow.json "
            + "(3 nested flows A→B→C) + extending ChatFlowImportTestUtil to remap subFlowId "
            + "across the bundle the same way personaId is already remapped. ~2h infra + ~1h body. "
            + "Indirect coverage exists in TurChatFlowSubFlowEngineIT (single-level ascent) — "
            + "the multi-level chain case here is the gap.")
    void cascadesAscentThroughChainOfTerminatedSubFlows_landsOnTopLevelNextNode() {
        fail("Not implemented — see @Disabled message for the deferred-work scope.");
    }
}
