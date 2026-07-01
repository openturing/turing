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

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

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

    @Autowired
    private TurChatFlowStateRepository stateRepository;

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

        // Instrument stateRepository.save() to record the IDENTITY of the
        // transaction each save runs in. T13's contract is that all in-walk
        // saves (strategy mutation + persona/switch landing) coalesce into
        // advance()'s single @Transactional — so every save must observe the
        // SAME physical transaction.
        //
        // Why not count global afterCommit events (the previous approach)?
        // Read-only read-model loads on the turn path (T487) also open and
        // commit transactions, so "global commits < saves" broke without any
        // coalescing regression (it was seeing 4 commits for 3 saves). And
        // counting distinct transaction *names* wouldn't detect a regression
        // either: if the boundary reverted, each save would open its own
        // implicit transaction, all named after the same repository method.
        // So we tag each distinct *physical* transaction with a UUID bound as
        // a transaction-scoped resource (unbound on completion) and assert
        // exactly one tag was minted across all in-walk saves.
        java.util.Set<String> savingTxIds =
                java.util.Collections.synchronizedSet(new java.util.HashSet<>());
        java.util.concurrent.atomic.AtomicInteger savesOutsideTx =
                new java.util.concurrent.atomic.AtomicInteger();
        Object txTagKey = new Object();

        // Reset spy counter AFTER the seed save and BEFORE the runTurn so the
        // capture reflects just the executor turn.
        org.mockito.Mockito.clearInvocations(stateRepoSpy);
        // @MockitoSpyBean on a Spring Data repository is an INTERFACE spy: its
        // default answer delegates to the real bean (that's why unstubbed
        // saves persist), but invocation.callRealMethod() can't be used —
        // save() is abstract on the interface. Route through the spy's
        // delegating default answer so our side-effect wrapper still persists.
        org.mockito.stubbing.Answer<?> delegateToRealSave =
                org.mockito.Mockito.mockingDetails(stateRepoSpy)
                        .getMockCreationSettings().getDefaultAnswer();
        org.mockito.Mockito.doAnswer(invocation -> {
            if (org.springframework.transaction.support.TransactionSynchronizationManager
                            .isActualTransactionActive()
                    && org.springframework.transaction.support.TransactionSynchronizationManager
                            .isSynchronizationActive()) {
                if (!org.springframework.transaction.support.TransactionSynchronizationManager
                        .hasResource(txTagKey)) {
                    String tag = java.util.UUID.randomUUID().toString();
                    org.springframework.transaction.support.TransactionSynchronizationManager
                            .bindResource(txTagKey, tag);
                    savingTxIds.add(tag);
                    org.springframework.transaction.support.TransactionSynchronizationManager
                            .registerSynchronization(
                                    new org.springframework.transaction.support.TransactionSynchronization() {
                                        @Override
                                        public void afterCompletion(int status) {
                                            org.springframework.transaction.support
                                                    .TransactionSynchronizationManager
                                                    .unbindResourceIfPossible(txTagKey);
                                        }
                                    });
                }
            } else {
                savesOutsideTx.incrementAndGet();
            }
            return delegateToRealSave.answer(invocation);
        }).when(stateRepoSpy).save(org.mockito.ArgumentMatchers.any());
        harness().queueResponse("Qual é o seu objetivo de carreira?");

        runTurn(agent, llm,
                java.util.List.of(new TurAgentChatExecutor.ChatMessageItem("user", "Maria")),
                null, conv, imp.flow().getId());

        // 1. The strategy save + the in-walk saves (persona-lucas landing
        //    + switch-route landing) must each have hit stateRepository.save —
        //    at least TWO calls to prove the walk wasn't short-circuited.
        org.mockito.ArgumentCaptor<com.viglet.turing.persistence.model.agent.TurChatFlowState> captor =
                org.mockito.ArgumentCaptor.forClass(
                        com.viglet.turing.persistence.model.agent.TurChatFlowState.class);
        org.mockito.Mockito.verify(stateRepoSpy, org.mockito.Mockito.atLeast(2)).save(captor.capture());

        // 2. The COALESCE invariant: every in-walk save ran inside a
        //    transaction (never standalone) AND all of them shared ONE
        //    physical transaction — advance()'s @Transactional. If T13
        //    reverted, each save would open its own implicit transaction and
        //    savingTxIds would hold more than one tag.
        org.assertj.core.api.Assertions.assertThat(savesOutsideTx.get())
                .as("Every in-walk save must run inside advance()'s @Transactional, "
                        + "never in a standalone implicit transaction")
                .isZero();
        org.assertj.core.api.Assertions.assertThat(savingTxIds)
                .as("Post-T13 advance(...) must coalesce all in-walk saves into a SINGLE "
                        + "physical transaction; got %d distinct saving transactions",
                        savingTxIds.size())
                .hasSize(1);

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
    void cascadesAscentThroughChainOfTerminatedSubFlows_landsOnTopLevelNextNode() {
        // Build a depth-3 chain A → B → C where the two inner flows are
        // "pass-through" (start → … → end with no interactive node):
        //   C: startC → endC
        //   B: startB → subFlow(C) → endB
        //   A: startA → subFlow(B) → ai-confirma (aiQuestion) → endA
        //
        // A single loadOrInitState(A) descends A→B→C, hits C's end (parent
        // set) → ascends to B, B's next is endB → ascends AGAIN to A, whose
        // next node after the SubFlow node is the interactive ai-confirma,
        // where the walk parks. Two cascade ascents in one init, zero LLM
        // calls (the empty sub-flows need no guardrail decision). This locks
        // the MULTI-level ascent that TurChatFlowSubFlowEngineIT only covers
        // one level deep — the gap the original skeleton named.
        //
        // The skeleton originally scoped a JSON fixture + ChatFlowImportTestUtil
        // subFlowId remapping; the programmatic build below (mirroring
        // TurChatFlowSubFlowEngineIT's graph builders) reaches the same
        // invariant without either, and runs in the default mock-harness
        // suite (no OPENAI_API_KEY needed).
        com.viglet.turing.persistence.model.agent.TurAIAgent agent =
                createAgent("contract-cascade-ascent", null);

        com.viglet.turing.persistence.model.agent.TurChatFlow flowC = saveCascadeFlow(agent,
                "cascade-C — pass-through",
                """
                {"nodes":[
                  {"id":"startC","type":"start","data":{"label":"INÍCIO","type":"start"}},
                  {"id":"endC","type":"end","data":{"label":"FIM","type":"end"}}
                ],"edges":[{"id":"eC","source":"startC","target":"endC"}]}
                """);
        com.viglet.turing.persistence.model.agent.TurChatFlow flowB = saveCascadeFlow(agent,
                "cascade-B — subFlow(C) then end",
                """
                {"nodes":[
                  {"id":"startB","type":"start","data":{"label":"INÍCIO","type":"start"}},
                  {"id":"subB","type":"subFlow","data":{"label":"SUB FLOW","type":"subFlow","subFlowId":"%s","subFlowName":"C"}},
                  {"id":"endB","type":"end","data":{"label":"FIM","type":"end"}}
                ],"edges":[
                  {"id":"eB1","source":"startB","target":"subB"},
                  {"id":"eB2","source":"subB","target":"endB"}
                ]}
                """.formatted(flowC.getId()));
        com.viglet.turing.persistence.model.agent.TurChatFlow flowA = saveCascadeFlow(agent,
                "cascade-A — subFlow(B) then ai-confirma",
                """
                {"nodes":[
                  {"id":"startA","type":"start","data":{"label":"INÍCIO","type":"start"}},
                  {"id":"subA","type":"subFlow","data":{"label":"SUB FLOW","type":"subFlow","subFlowId":"%s","subFlowName":"B"}},
                  {"id":"ai-confirma","type":"aiQuestion","data":{"label":"CONFIRMA","type":"aiQuestion","aiInstruction":"Confirme os dados coletados.","outputVariable":"confirma"}},
                  {"id":"endA","type":"end","data":{"label":"FIM","type":"end"}}
                ],"edges":[
                  {"id":"eA1","source":"startA","target":"subA"},
                  {"id":"eA2","source":"subA","target":"ai-confirma"},
                  {"id":"eA3","source":"ai-confirma","target":"endA"}
                ]}
                """.formatted(flowB.getId()));

        String conv = "conv-cascade-ascent-" + java.util.UUID.randomUUID();
        com.viglet.turing.genai.flow.ChatFlowGraph graphA =
                chatFlowEngineService.parseGraph(flowA).orElseThrow();
        com.viglet.turing.persistence.model.agent.TurChatFlowState leaf =
                chatFlowEngineService.loadOrInitState(conv, flowA, graphA).orElseThrow();

        // 1. Final state landed back on the TOP-LEVEL flow A (sobe DUAS vezes).
        org.assertj.core.api.Assertions.assertThat(leaf.getFlow().getId())
                .as("cascade ascent must land back on the top-level flow A")
                .isEqualTo(flowA.getId());
        // 2. Resumed on A's node AFTER the SubFlow node — the interactive
        //    ai-confirma, NOT an end node.
        org.assertj.core.api.Assertions.assertThat(leaf.getCurrentNodeId())
                .as("must resume on A's node after the SubFlow node, not on an end")
                .isEqualTo("ai-confirma");
        // 3. The parent chain is fully drained (chain esvaziada).
        org.assertj.core.api.Assertions.assertThat(leaf.getParentStateId())
                .as("parent chain must be empty after cascading all the way up")
                .isNull();
        // 4. Both intermediate child states (B, C) were deleted on ascent — only
        //    the single A leaf row survives for the conversation.
        org.assertj.core.api.Assertions.assertThat(stateRepository.findByConversationId(conv))
                .as("both ascended sub-flow states must be deleted — one row remains")
                .hasSize(1)
                .first()
                .satisfies(s -> org.assertj.core.api.Assertions.assertThat(s.getId())
                        .isEqualTo(leaf.getId()));
    }

    /**
     * Persists a {@link com.viglet.turing.persistence.model.agent.TurChatFlow}
     * owned by {@code agent} with a HEURISTIC guardrail and a one-off name.
     * The cascade-ascent test never reaches an interactive node's advance —
     * it parks on init — so the guardrail method is immaterial; HEURISTIC
     * just avoids needing an LLM bean.
     */
    private com.viglet.turing.persistence.model.agent.TurChatFlow saveCascadeFlow(
            com.viglet.turing.persistence.model.agent.TurAIAgent agent,
            String name, String definitionJson) {
        com.viglet.turing.persistence.model.agent.TurChatFlow flow =
                new com.viglet.turing.persistence.model.agent.TurChatFlow();
        flow.setName(name + " — " + java.util.UUID.randomUUID().toString().substring(0, 6));
        flow.setEnabled(1);
        flow.setGuardrailMethod(
                com.viglet.turing.persistence.model.agent.TurChatFlowGuardrailMethod.HEURISTIC);
        flow.setDefinitionJson(definitionJson);
        flow.setTurAIAgent(agent);
        return chatFlowRepository.save(flow);
    }
}
