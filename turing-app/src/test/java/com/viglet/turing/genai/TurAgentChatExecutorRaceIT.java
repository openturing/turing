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

import com.viglet.turing.testutil.AbstractTuringSpringIT;

/**
 * Esqueletos para os patches da categoria (A) RACE listados em
 * {@code docs/IMPROVEMENTS.md} §I.3. Cada {@code @Test}
 * é um contrato que o refactor da camada {@code genai/} (inversão para
 * "advance → 1× LLM call → publish") deve preservar ou substituir por
 * uma asserção declarativamente equivalente.
 *
 * <p>Implementar os bodies usando o padrão de mocking de
 * {@link com.viglet.turing.customers.education.flow.ProgramaMatchEEFlowIT}
 * (mesma estratégia de {@code importIntoAgent}, {@code loadOrInitState},
 * {@code engine.advance(...)}) antes de iniciar o refactor — a IT de
 * referência usa OpenAI real porque foi pensada para o motor end-to-end;
 * estes skeletons devem mockar {@link org.springframework.ai.chat.model.ChatModel}
 * via Mockito para serem determinísticos e capturarem invariantes do executor,
 * não do modelo. Veja {@code LlmJudgeGuardrailStrategyTest} para o padrão de
 * stub de {@code ChatModel.call(Prompt)}.
 *
 * <p>Todos os métodos estão {@code @Disabled} para manter o build verde
 * enquanto a implementação não acompanha. Body é literalmente
 * {@code fail("Not implemented...")} para garantir que ninguém habilite
 * o teste sem implementar — o Javadoc é o contrato, não o código.
 *
 * <p>Cobertura dos patches: #1, #2, #8, #9, #10, #11, #12, #13, #14, #15.
 * Patches (A) restantes com cobertura parcial ou indireta — #6, #7, #16 —
 * permanecem no inventário; os esqueletos abaixo não tentam re-cobri-los.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
class TurAgentChatExecutorRaceIT extends AbstractTuringSpringIT {

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
     */
    @Test
    @Disabled("Skeleton — pendente implementação. Inventário §8.4, patch #1.")
    void resolvesPersonaAfterFlowSelection_picksWalkWrittenPersonaForInitialCall() {
        fail("Not implemented — skeleton from IMPROVEMENTS.md §I.3");
    }

    /**
     * Patch #2 — re-resolve persona a TERCEIRA vez para o regen
     * Categoria: A
     * Localização do patch: TurAgentChatExecutor.java:451-461
     *
     * Comportamento a travar:
     *   Quando o {@code advance()} pós-initial-call atravessa um nó
     *   {@code persona-*} que troca {@code __activePersonaId}, o regen
     *   resolve a persona de novo e o segundo prompt sai com a persona
     *   recém-ativa, não com a do início do turno.
     *
     * Cenário:
     *   Flow:    Nova fixture: um aiQuestion N1 → switch que decide rota →
     *            persona-X (transparente) → aiQuestion N2 (target do regen).
     *            programa-match-ee.chat-flow.json não tem persona switch
     *            no meio do flow; fica como fixture nova OU "programa-match
     *            + nó persona-lucas inserido entre switch e ai-confirma-*".
     *   Estado:  conversação em progresso, parked em N1.
     *   Entrada: user msg = "Tecnologia & Dados" (ou input que dispare o switch).
     *   Mocks:   ChatModel-1 (initial, persona inicial) retorna "ok-1".
     *            ChatModel-2 (regen, persona pós-walk) retorna "ok-2".
     *            personaResolver pode ser spy do bean real para contar invocações.
     *
     * Asserts:
     *   1. {@code personaResolver.resolve(...)} foi invocado 3 vezes
     *      (linha ~240 inicial + pós-flow + dentro do regen).
     *   2. O SystemMessage do 2º Prompt capturado contém o systemInstruction
     *      da persona X (ativada pelo walk), NÃO da persona inicial.
     *   3. O reply final que o SSE publica == "ok-2".
     *
     * Nota refactor:
     *   No modelo invertido NÃO há regen — o teste passa a NÃO existir como
     *   asserção sobre regen; deve ser substituído por uma versão que valida
     *   uma única resolução de persona pós-advance. Marcar como
     *   {@code @Deprecated/remove-on-refactor} no Javadoc da implementação.
     */
    @Test
    @Disabled("Skeleton — pendente implementação. Inventário §8.4, patch #2.")
    void reResolvesPersonaInsideRegen_whenAdvanceCrossedPersonaNode() {
        fail("Not implemented — skeleton from IMPROVEMENTS.md §I.3");
    }

    /**
     * Patch #8 — textAlignedWithCurrentNode flag + supressão de chips
     * Categoria: A
     * Localização do patch: TurAgentChatExecutor.java:382-398, 469-476, 504-512
     *
     * Comportamento a travar:
     *   Quando o initial LLM call respondeu para o nó N (pré-advance), depois
     *   o advance cruzou nós transparentes e a strategy emitiu um
     *   {@code assistantOverride} (texto fixo, sem regen), o executor zera
     *   {@code textAlignedWithCurrentNode=false} e SUPRIME os chips do nó
     *   atual no payload SSE — porque os chips do nó N+1 não casam com o
     *   texto do nó N que será exibido.
     *
     * Cenário:
     *   Flow:    Nova fixture OU programa-match adaptado: aiQuestion N1 →
     *            switch → aiQuestion N2 com {@code inlineOptions} (chips).
     *            Strategy precisa retornar AdvanceResult com assistantOverride
     *            não-nulo para evitar o regen (que zeraria a flag de volta).
     *   Estado:  parked em N1; usuário acabou de mandar input válido.
     *   Entrada: user msg = "Tecnologia & Dados"
     *   Mocks:   ChatModel retorna texto para N1.
     *            Strategy mockada para retornar assistantOverride="literal text"
     *            (sem regen path).
     *
     * Asserts:
     *   1. O reply final SSE = "literal text" (assistantOverride venceu).
     *   2. O payload SSE NÃO contém o array de chips do N2
     *      (chips são suprimidos quando textAlignedWithCurrentNode=false).
     *   3. No turno SEGUINTE (sem advance), a flag volta a true e os chips
     *      do nó parked aparecem normalmente.
     *
     * Nota refactor:
     *   Esta flag deixa de existir na ordem invertida. O refactor remove o
     *   teste — texto e chips são sempre compostos para o nó atual pós-advance,
     *   por construção. Marcar a substituição: "no novo design, chips e texto
     *   estão SEMPRE alinhados, não há cenário onde precisem ser dessincronizados".
     */
    @Test
    @Disabled("Skeleton — pendente implementação. Inventário §8.4, patch #8.")
    void suppressesChipsWhenAssistantOverrideDoesNotMatchPostAdvanceNode() {
        fail("Not implemented — skeleton from IMPROVEMENTS.md §I.3");
    }

    /**
     * Patch #9 — regen omite "Next step preview" hint
     * Categoria: A
     * Localização do patch: TurAgentChatExecutor.java:593-602
     *                       + ChatFlowOps.java:125-167
     *
     * Comportamento a travar:
     *   No regen, o executor chama
     *   {@code buildBaseAddendum(node, state, graph, includeNextStepHint=false)}
     *   — diferentemente do initial call onde o flag é {@code true}. O resultado
     *   é que o system prompt do regen NÃO descreve o nó N+1 (próximo step),
     *   evitando que o LLM pergunte uma etapa adiante.
     *
     * Cenário:
     *   Flow:    programa-match-ee — qualquer cadeia aiQuestion N → aiQuestion N+1
     *            onde N+1 tem aiInstruction distintiva.
     *   Estado:  parked em N pré-advance; user acabou de responder N.
     *   Entrada: user msg = resposta válida para N.
     *   Mocks:   ChatModel-1 (initial) retorna texto qualquer.
     *            ChatModel-2 (regen) capturado via ArgumentCaptor para
     *            inspecionar o SystemMessage.
     *
     * Asserts:
     *   1. O system prompt do PRIMEIRO Prompt capturado CONTÉM o
     *      aiInstruction de N+1 (next-step-hint=true).
     *   2. O system prompt do SEGUNDO Prompt capturado NÃO contém o
     *      aiInstruction do nó depois do destino do regen (i.e., não
     *      vaza N+2). Pode usar o marcador textual do hint
     *      ("Próximo passo" / "Next step" / "Coming up") como assertion.
     *
     * Nota refactor:
     *   Sem regen, há apenas um {@code buildBaseAddendum} por turno, sempre
     *   com o flag adequado ao nó atual pós-advance — a bifurcação
     *   true/false desaparece. Teste vira: "system prompt do único call
     *   contém o hint do nó atual e NÃO do nó futuro".
     */
    @Test
    @Disabled("Skeleton — pendente implementação. Inventário §8.4, patch #9.")
    void omitsNextStepPreviewHintInRegenAddendum() {
        fail("Not implemented — skeleton from IMPROVEMENTS.md §I.3");
    }

    /**
     * Patch #10 — HIGHEST-PRIORITY OVERRIDE bloco apendado depois do persona prompt
     * Categoria: A
     * Localização do patch: TurAgentChatExecutor.java:604-632, 893-948
     *
     * Comportamento a travar:
     *   O system prompt do regen recebe, APÓS o persona prompt, um bloco
     *   bilíngue "INSTRUÇÃO DE PRIORIDADE MÁXIMA / HIGHEST-PRIORITY OVERRIDE"
     *   colocado como os tokens MAIS RECENTES antes da user message — para
     *   combater o anchoring do regen no padrão da conversa anterior.
     *
     * Cenário:
     *   Flow:    programa-match-ee, transição ai-cargo → ai-objetivo (regen path).
     *   Estado:  parked em ai-cargo; user acabou de responder "Gerente sênior".
     *   Entrada: user msg = "Gerente sênior numa fintech, há 4 anos"
     *   Mocks:   ChatModel-1 retorna ack qualquer.
     *            ChatModel-2 (regen) capturado via ArgumentCaptor para
     *            inspecionar o último SystemMessage do Prompt.
     *
     * Asserts:
     *   1. O SystemMessage do PRIMEIRO Prompt NÃO contém
     *      "INSTRUÇÃO DE PRIORIDADE MÁXIMA" nem "HIGHEST-PRIORITY OVERRIDE".
     *   2. O SystemMessage do SEGUNDO Prompt (regen) CONTÉM
     *      "INSTRUÇÃO DE PRIORIDADE MÁXIMA" E "HIGHEST-PRIORITY OVERRIDE"
     *      (bloco bilíngue obrigatório).
     *   3. O bloco override aparece DEPOIS do persona prompt — i.e., o
     *      offset de "INSTRUÇÃO" é > offset do nome da persona ("Marina").
     *
     * Nota refactor:
     *   Sem regen, não há prompt diferente para combater anchoring — o teste
     *   deixa de fazer sentido após o refactor. Marcar como "tombstone teste
     *   #10 — bloco override removido por construção".
     */
    @Test
    @Disabled("Skeleton — pendente implementação. Inventário §8.4, patch #10.")
    void appendsBilingualHighestPriorityOverrideOnRegen() {
        fail("Not implemented — skeleton from IMPROVEMENTS.md §I.3");
    }

    /**
     * Patch #11 — drop deliberado do baseSystemPrompt no regen
     * Categoria: A
     * Localização do patch: TurAgentChatExecutor.java:615-632
     *
     * Comportamento a travar:
     *   No regen, o system prompt é montado SEM o {@code baseSystemPrompt}
     *   (regras de orquestração do agent: "ROUTER LLM decides flow",
     *   "show 3 caminhos welcome on first turn", etc.) — porque esse prompt
     *   contradiz o objetivo estreito do regen (responder a UMA pergunta
     *   do nó atual).
     *
     * Cenário:
     *   Flow:    programa-match-ee, qualquer transição que dispare regen.
     *            O agent precisa ter um baseSystemPrompt distintivo configurado
     *            (frases tipo "ROUTER LLM" ou "3 caminhos") para podermos
     *            assertar ausência.
     *   Estado:  parked em N pré-advance.
     *   Entrada: user msg = resposta válida.
     *   Mocks:   ChatModel-1 retorna ack.
     *            ChatModel-2 (regen) capturado via ArgumentCaptor.
     *
     * Asserts:
     *   1. O SystemMessage do PRIMEIRO Prompt CONTÉM marcadores distintivos
     *      do baseSystemPrompt (ex.: "ROUTER LLM", "3 caminhos", "welcome on first turn").
     *   2. O SystemMessage do SEGUNDO Prompt (regen) NÃO contém NENHUM desses
     *      marcadores — o baseSystemPrompt foi totalmente removido.
     *   3. O 2º prompt ainda contém o persona prompt (não é nulo) e o
     *      newAddendum do nó destino.
     *
     * Nota refactor:
     *   Sem regen, não há "system prompt diferente do initial". O teste
     *   é removido — o prompt único monta-se uma vez com o conjunto certo
     *   de partes para o nó pós-advance.
     */
    @Test
    @Disabled("Skeleton — pendente implementação. Inventário §8.4, patch #11.")
    void dropsBaseSystemPromptInRegenToAvoidOrchestrationConflict() {
        fail("Not implemented — skeleton from IMPROVEMENTS.md §I.3");
    }

    /**
     * Patch #12 — inline do aiInstruction literal dentro do override
     * Categoria: A
     * Localização do patch: TurAgentChatExecutor.java:920-931
     *
     * Comportamento a travar:
     *   O override bloco do regen RE-INCLUI o {@code aiInstruction.trim()}
     *   literal do nó destino — mesmo que ele já apareça no {@code newAddendum}
     *   acima. Sem esse re-inline o LLM ignora o "pointer" ao aiInstruction
     *   e recicla uma instrução antiga de outro step.
     *
     * Cenário:
     *   Flow:    programa-match-ee, transição com aiInstruction distintiva no
     *            nó destino — ex.: ai-objetivo cujo aiInstruction começa com
     *            "Pergunte onde o visitante quer estar daqui a 3 anos".
     *   Estado:  parked em ai-cargo.
     *   Entrada: user msg = "Gerente sênior, há 4 anos"
     *   Mocks:   ChatModel-1 retorna ack.
     *            ChatModel-2 capturado.
     *
     * Asserts:
     *   1. O aiInstruction literal do nó destino aparece EXATAMENTE 2 vezes
     *      no SystemMessage do regen — uma no newAddendum (FLOW STATE) e
     *      uma no bloco override (linha 920-931).
     *   2. Ambas ocorrências contêm a mesma substring distintiva
     *      (ex.: "daqui a 3 anos") — confirma que é literal, não paráfrase.
     *
     * Nota refactor:
     *   Sem regen, não há override. Teste removido.
     */
    @Test
    @Disabled("Skeleton — pendente implementação. Inventário §8.4, patch #12.")
    void inlinesAiInstructionLiteralInsideOverrideBlock() {
        fail("Not implemented — skeleton from IMPROVEMENTS.md §I.3");
    }

    /**
     * Patch #13 — inline das chip-labels no override + "MUST be congruent"
     * Categoria: A
     * Localização do patch: TurAgentChatExecutor.java:932-945
     *
     * Comportamento a travar:
     *   Quando o nó destino do regen tem {@code inlineOptions} (chips),
     *   o override lista os labels delas inline + a frase imperativa
     *   "Your question MUST be congruent with these choices". Sem isso,
     *   gpt-4o-mini gera perguntas incongruentes com os chips renderizados
     *   na UI (ex.: pergunta sobre num_pessoas com chips de role).
     *
     * Cenário:
     *   Flow:    Nova fixture OU in-company-match.chat-flow.json — um
     *            aiQuestion destino com inlineOptions=[RH/L&D, Gestor, C-level,
     *            Comprador] (cenário literal do patch).
     *   Estado:  parked em N pré-advance; user respondeu N.
     *   Entrada: user msg = qualquer trigger que mova para o nó com chips.
     *   Mocks:   ChatModel-1 retorna ack.
     *            ChatModel-2 (regen) capturado via ArgumentCaptor.
     *
     * Asserts:
     *   1. O SystemMessage do regen CONTÉM cada label de chip do nó destino
     *      como substring (todos os 4 labels do exemplo).
     *   2. O SystemMessage do regen CONTÉM a frase
     *      "MUST be congruent" (ou tradução equivalente).
     *   3. Quando o nó destino NÃO tem inlineOptions, o bloco de chips
     *      NÃO aparece no override (asserção de variante negativa).
     *
     * Nota refactor:
     *   Sem regen, o initial call já compõe com chips do nó atual no contexto.
     *   Não há "regen tem que lembrar dos chips do destino". Teste removido,
     *   mas o contrato "perguntas congruentes com chips do nó atual" vira
     *   parte do prompt builder único e merece um teste positivo nesse novo
     *   ponto.
     */
    @Test
    @Disabled("Skeleton — pendente implementação. Inventário §8.4, patch #13.")
    void inlinesChipLabelsAndCongruencyImperativeInOverrideWhenDestNodeHasInlineOptions() {
        fail("Not implemented — skeleton from IMPROVEMENTS.md §I.3");
    }

    /**
     * Patch #14 — trimToRecentTurns: history truncado a 3 turnos no regen
     * Categoria: A
     * Localização do patch: TurAgentChatExecutor.java:633-640, 882-979
     *
     * Comportamento a travar:
     *   O regen recebe history truncado para os últimos 3 user-turns
     *   ({@code REGEN_HISTORY_TURN_BUDGET=3}) em vez da história completa,
     *   porque conversation completa dá ao LLM exemplos demais do padrão
     *   antigo (que o regen está tentando contradizer), gerando re-asking
     *   da pergunta anterior.
     *
     * Cenário:
     *   Flow:    programa-match-ee. Forçar uma conversação com >= 5 user-turns
     *            anteriores (ex.: trigger + ai-name + ai-cargo + ai-objetivo
     *            + ai-area = 5 turns) antes da transição que dispara regen.
     *   Estado:  parked em ai-area; histórico completo carregado.
     *   Entrada: user msg = "Finanças & Investimentos"
     *   Mocks:   ChatModel-1 retorna ack.
     *            ChatModel-2 (regen) capturado via ArgumentCaptor; conta
     *            quantos UserMessage existem no Prompt capturado.
     *
     * Asserts:
     *   1. O PRIMEIRO Prompt capturado contém TODAS as UserMessage da história
     *      (>= 5 user turns + a atual = >= 6).
     *   2. O SEGUNDO Prompt (regen) contém NO MÁXIMO 3 UserMessage
     *      (REGEN_HISTORY_TURN_BUDGET) — i.e., 3 user-turns × 2 mensagens
     *      (user + assistant) = 6 mensagens não-system no máximo.
     *   3. A última UserMessage do regen é a atual (não foi descartada na trim).
     *
     * Nota refactor:
     *   Sem regen, anchoring é não-issue — não há resposta antiga a contradizer.
     *   History completa vai como única call. Teste removido. Pode renascer
     *   como cap geral de history (ex.: budget de tokens), mas não como
     *   "trim só no regen".
     */
    @Test
    @Disabled("Skeleton — pendente implementação. Inventário §8.4, patch #14.")
    void trimsHistoryToBudgetedRecentTurnsInRegenCall() {
        fail("Not implemented — skeleton from IMPROVEMENTS.md §I.3");
    }

    /**
     * Patch #15 — refresh de state do DB antes de strategy advance
     * Categoria: A
     * Localização do patch: TurChatFlowEngineService.java:227-237
     *
     * Comportamento a travar:
     *   No início do {@code advance(...)}, o engine recarrega o state do banco
     *   ({@code stateRepository.findById(state.getId()).orElse(state)}) para
     *   capturar slot writes feitos por Custom Tool callbacks durante o initial
     *   LLM call. Sem isso, a strategy.writeVariables ao final do advance
     *   sobrescreveria os slots que o tool acabou de escrever.
     *
     * Cenário:
     *   Flow:    Nova fixture: aiQuestion → Custom Tool callback que escreve
     *            slot {@code programas_match} via
     *            {@code TurCustomToolSlotHelper.save(...)} →
     *            advance que normalmente também escreveria slots.
     *            programa-match-ee tem o tool search_ee_programs disponível, mas
     *            o cenário precisa forçar tool-call dentro do initial call ANTES
     *            do advance — verificar se já existe nó adequado.
     *   Estado:  parked num aiQuestion que dispara tool no LLM call.
     *   Entrada: user msg = qualquer trigger que faça o LLM chamar o tool.
     *   Mocks:   ChatModel real OU mock que simula tool-call lifecycle do Spring AI.
     *            Pode-se usar Spring AI {@code ToolCallback} mockado que escreve
     *            no DB diretamente via TurCustomToolSlotHelper para simular
     *            o efeito lateral.
     *            stateRepository spy para contar findById invocations.
     *
     * Asserts:
     *   1. Após o turn completo, o slot {@code programas_match} contém o
     *      valor que o tool escreveu — NÃO foi sobrescrito/zerado pelo
     *      writeVariables da strategy.
     *   2. {@code stateRepository.findById(state.getId())} foi chamado pelo
     *      menos UMA VEZ dentro do advance (linha 227-237) — evidência do refresh.
     *   3. (Negativo, sanity) — se o refresh for desabilitado (skipped),
     *      o slot fica vazio/sobrescrito (este assert pode ficar como TODO
     *      condicional para regression-witness).
     *
     * Nota refactor:
     *   Na ordem invertida, advance roda ANTES do LLM call → não há tool
     *   write durante o advance. A LLM call posterior pode chamar tools que
     *   escrevem slots, mas como o advance já terminou, não há writeVariables
     *   pendente para sobrescrever. Teste removido; o write-by-tool sobrevive
     *   sem necessidade de refresh defensivo.
     */
    @Test
    @Disabled("Skeleton — pendente implementação. Inventário §8.4, patch #15.")
    void refreshesStateFromDbBeforeStrategyAdvance_preservingSlotWritesFromToolCallbacks() {
        fail("Not implemented — skeleton from IMPROVEMENTS.md §I.3");
    }
}
