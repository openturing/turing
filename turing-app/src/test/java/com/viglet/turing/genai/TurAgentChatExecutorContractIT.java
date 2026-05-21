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
import org.springframework.context.annotation.Import;

import com.viglet.turing.genai.testsupport.AbstractAgentExecutorIT;
import com.viglet.turing.genai.testsupport.TurAgentChatExecutorMockSupport;

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
    @Disabled("Skeleton — pendente implementação. Inventário §8.4, patch #18.")
    void fallsBackToHeuristicStrategyWhenGuardrailMethodHasNoRegisteredBean() {
        fail("Not implemented — skeleton from IMPROVEMENTS.md §I.3");
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
    @Disabled("Skeleton — pendente implementação. Inventário §8.4, patch #20.")
    void discardsForcedFlowWhenItDoesNotBelongToCallingAgent() {
        fail("Not implemented — skeleton from IMPROVEMENTS.md §I.3");
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
    @Disabled("Skeleton — pendente implementação. Inventário §8.4, patch #16 (new in T4).")
    void persistsStateInsideTransparentWalkAndAgainAfterAdvance_doubleSavePattern() {
        fail("Not implemented — skeleton from IMPROVEMENTS.md §I.3");
    }

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
    @Disabled("Skeleton — pendente implementação. Inventário §8.4, cascade ascent (new in T4).")
    void cascadesAscentThroughChainOfTerminatedSubFlows_landsOnTopLevelNextNode() {
        fail("Not implemented — skeleton from IMPROVEMENTS.md §I.3");
    }

    /**
     * BR-doc CPF/CNPJ checksum validation — {@code ChatFlowOps.isValidCpfChecksum} /
     * {@code isValidCnpjChecksum}
     * Categoria: C (middleware — input validation primitive)
     * Localização: ChatFlowOps.java:1217-1251 (CPF + CNPJ mod-11 checks)
     *
     * Comportamento a travar:
     *   {@code isValidCpfChecksum(String cpf)} returns true ⇔ the 11-digit
     *   normalized string passes the Receita Federal mod-11 algorithm AND
     *   is NOT a repeated-digit sequence (111.111.111-11, 000.000.000-00,
     *   etc.). {@code isValidCnpjChecksum(String cnpj)} idem for 14-digit
     *   CNPJ with the cycle {6,5,4,3,2,9,8,7,6,5,4,3,2}.
     *
     *   These methods sit on the hot path: the LLM_JUDGE strategy extracts
     *   a candidate CPF/CNPJ string via regex, then routes it through these
     *   checksums BEFORE force-capturing the slot. A buggy checksum either
     *   accepts malformed docs (breaks downstream API calls) or rejects
     *   valid ones (the visitor types it again and again — UX nightmare).
     *
     * Cenário:
     *   Pure unit-style tests, no Spring context. The static methods are
     *   PACKAGE-PRIVATE on {@code ChatFlowOps} in
     *   {@code com.viglet.turing.genai.flow.strategy} — this skeleton lives
     *   in the wrong package to call them directly.
     *
     *   Implementer should EITHER:
     *   <ul>
     *     <li>Move the {@code @Nested} class into a new file at
     *         {@code com/viglet/turing/genai/flow/strategy/ChatFlowOpsBrDocUnitTest.java}
     *         (preferred — keeps tests next to the implementation), OR</li>
     *     <li>Escalate {@code isValidCpfChecksum} / {@code isValidCnpjChecksum}
     *         visibility to {@code public} and keep the test here (only do
     *         this if a real caller outside {@code strategy/} ever needs
     *         the primitives — currently nobody does).</li>
     *   </ul>
     *
     * Asserts (parameterized recommended, ~10-15 cases):
     *   CPF valid:
     *     - 11144477735 (canonical valid CPF used by Receita Federal docs)
     *     - 390.533.447-05 (real, formatted — strip dots/dash before check)
     *   CPF invalid:
     *     - 11111111111 (repeated digits — rejected by isAllSameDigit)
     *     - 00000000000, 99999999999 (same)
     *     - 12345678900 (mod-11 fails)
     *     - "12345" (wrong length)
     *     - null, "" (defensive)
     *   CNPJ valid:
     *     - 11.222.333/0001-81 (canonical real CNPJ)
     *     - 12.345.678/0001-95 (real, formatted)
     *   CNPJ invalid:
     *     - 11111111111111 (repeated)
     *     - 12345678901234 (mod-11 fails)
     *     - wrong length, null, ""
     *
     * Nota refactor:
     *   Primitives de validação não mudam com o §I.5 refactor — sobrevive
     *   inalterado. Pode até virar parte de uma biblioteca shared de
     *   validation futura sem afetar nada do executor.
     */
    @Test
    @Disabled("Skeleton — pendente implementação. Inventário §8.4, BR-doc CPF (new in T4); see Javadoc — methods are package-private, this skeleton needs to move to ChatFlowOpsBrDocUnitTest.java in com.viglet.turing.genai.flow.strategy.")
    void brDocCpfCnpjChecksumValidation_acceptsValidRejectsInvalidRejectsRepeatedDigits() {
        fail("Not implemented — skeleton from IMPROVEMENTS.md §I.3");
    }
}
