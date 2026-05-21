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
}
