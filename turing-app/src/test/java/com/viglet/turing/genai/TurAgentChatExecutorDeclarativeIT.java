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
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Esqueletos para os patches da categoria (B) DECLARATIVE listados em
 * {@code docs/chat-flow/HARNESS_INVENTORY.md} §8.4. Cada {@code @Test} é
 * um contrato que o refactor deve preservar — esses patches sobrevivem
 * ao refactor virando campos declarativos no {@code ChatFlowNode}
 * (ex.: {@code toolsEnabled}, {@code toolsMode}), MAS o comportamento
 * observável (tool não é chamado quando o autor marcou o nó como
 * tool-free) deve continuar idêntico.
 *
 * <p>Implementar os bodies usando o padrão de mocking de
 * {@link com.viglet.turing.customers.education.flow.ProgramaMatchEEFlowIT}
 * (mesmo setup {@code @SpringBootTest} + {@code importIntoAgent} + loadOrInitState).
 * Especificamente, estes testes precisam de:
 * <ul>
 *   <li>mock de {@link org.springframework.ai.chat.model.ChatModel} via Mockito
 *       (não OpenAI real — comportamento é determinístico e independente do modelo);</li>
 *   <li>captura do {@link org.springframework.ai.chat.prompt.Prompt} via
 *       {@code ArgumentCaptor} para inspecionar {@code ChatOptions.getToolCallbacks()};</li>
 *   <li>fixture do flow {@code in-company-match.chat-flow.json}
 *       (já existe em {@code customers/education/chat-flow/}) ou fixture
 *       nova com nó marcado "NÃO CHAME NENHUMA TOOL".</li>
 * </ul>
 *
 * <p>Cobertura dos patches: #3, #4, #5.
 * Patch (B) restante já coberto: #22 (onJudgeReject — modelo do que
 * #3/#4/#5 devem virar).
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@SpringBootTest
class TurAgentChatExecutorDeclarativeIT {

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
     * Cenário:
     *   Flow:    in-company-match.chat-flow.json (já existe como fixture
     *            mas não tem IT). Nó alvo: aiQuestion de captura de coupon
     *            code com aiInstruction "NÃO CHAME NENHUMA TOOL". Custom
     *            tool {@code compor_proposta_in_company} configurado no agent.
     *   Estado:  parked no nó tool-free de captura de coupon.
     *   Entrada: user msg = "MEU-CUPOM-123"
     *   Mocks:   ChatModel mockado retorna texto qualquer; ArgumentCaptor<Prompt>
     *            captura o initial call.
     *            Tool {@code compor_proposta_in_company} mockado (spy/mock) para
     *            registrar invocações.
     *
     * Asserts:
     *   1. {@code Prompt.getOptions()} cast para ToolCallingChatOptions —
     *      {@code getToolCallbacks()} retorna lista VAZIA (callbacks
     *      stripped).
     *   2. O tool {@code compor_proposta_in_company} foi invocado ZERO vezes
     *      no turn: {@code verify(tool, never()).invoke(any())}.
     *   3. (Sanity inverso) — em um nó SEM "NÃO CHAME NENHUMA TOOL" no
     *      aiInstruction, o mesmo cenário invoca o tool normalmente.
     *
     * Nota refactor:
     *   Vira o campo declarativo {@code ChatFlowNode.toolsEnabled: boolean}
     *   (ou {@code toolsMode}). Este teste passa a verificar
     *   "quando {@code node.toolsEnabled() == false}, options.toolCallbacks
     *   está vazio" — o invariante observável (tool não chamado) é idêntico.
     *   A heurística de substring matching some; o campo aparece. O teste
     *   continua válido com mudança trivial no setup da fixture (declarar
     *   o campo em vez de adicionar a frase ao aiInstruction).
     */
    @Test
    @Disabled("Skeleton — pendente implementação. Inventário §8.4, patch #3.")
    void stripsToolsOnInitialCallWhenActiveNodeForbidsToolCalls() {
        fail("Not implemented — skeleton from HARNESS_INVENTORY.md §8.4");
    }

    /**
     * Patch #4 — tool-stripping no REGEN call (mesma heurística)
     * Categoria: B
     * Localização do patch: TurAgentChatExecutor.java:654-681
     *
     * Comportamento a travar:
     *   No regen, quando o NOVO nó (destino do advance) tem aiInstruction
     *   marcando "NÃO CHAME NENHUMA TOOL", o regen prompt é montado SEM
     *   tool callbacks — mesma blindagem do #3 mas no segundo call.
     *
     * Cenário:
     *   Flow:    in-company-match. Cadeia: aiQuestion captura-coupon
     *            (tool-free, dispara regen ao avançar) → aiQuestion
     *            ai-deliver-proposta (também tool-free, é onde queremos
     *            que regen rode SEM tools).
     *   Estado:  parked em N1, user manda coupon.
     *   Entrada: user msg = "CUPOM-XYZ"
     *   Mocks:   ChatModel-1 (initial) retorna ack qualquer; ChatModel-2
     *            (regen) capturado via ArgumentCaptor.
     *            Tool {@code compor_proposta_in_company} mockado.
     *
     * Asserts:
     *   1. O 2º Prompt capturado tem {@code options.getToolCallbacks()}
     *      VAZIO (stripped no regen).
     *   2. O tool {@code compor_proposta_in_company} foi invocado ZERO vezes
     *      durante o regen (não há card-flash com proposta sem coupon).
     *   3. O reply final SSE corresponde à 2ª chamada (regen) — confirmando
     *      que o caminho regen é o exercitado.
     *
     * Nota refactor:
     *   Sem regen, este teste é redundante com o #3 — o initial call único
     *   já cobre o invariante. Teste removido; o campo {@code toolsEnabled}
     *   é consultado uma única vez no novo design.
     */
    @Test
    @Disabled("Skeleton — pendente implementação. Inventário §8.4, patch #4.")
    void stripsToolsOnRegenCallWhenPostAdvanceNodeForbidsToolCalls() {
        fail("Not implemented — skeleton from HARNESS_INVENTORY.md §8.4");
    }

    /**
     * Patch #5 — forbidsToolCalls() substring matching bilíngue
     * Categoria: B
     * Localização do patch: TurAgentChatExecutor.java:709-716
     *
     * Comportamento a travar:
     *   A função {@code forbidsToolCalls(String aiInstruction)} retorna
     *   true se a string (lowercased) contém QUALQUER das 4 sentinelas:
     *   "não chame nenhuma tool", "nao chame nenhuma tool" (sem acento),
     *   "do not call any tool", "zero tool calls". Matching é case-insensitive
     *   e funciona com a sentinela embebida em texto livre.
     *
     * Cenário:
     *   Este patch é uma função estática privada — o teste pode ser unit-style
     *   (sem @SpringBootTest, sem fixture de flow), mas mantemos aqui por
     *   coesão com a categoria B e porque {@code forbidsToolCalls} é
     *   package-private (acessível do mesmo package).
     *   Mocks: nenhum.
     *
     * Asserts (parameterized recomendado):
     *   1. {@code forbidsToolCalls(null)} → false (defensivo).
     *   2. {@code forbidsToolCalls("")} → false.
     *   3. {@code forbidsToolCalls("NÃO CHAME NENHUMA TOOL")} → true (PT upper).
     *   4. {@code forbidsToolCalls("não chame nenhuma tool")} → true (PT lower).
     *   5. {@code forbidsToolCalls("nao chame nenhuma tool")} → true (PT sem acento).
     *   6. {@code forbidsToolCalls("Do Not Call Any Tool")} → true (EN mixed case).
     *   7. {@code forbidsToolCalls("zero tool calls neste turno")} → true (EN curto).
     *   8. {@code forbidsToolCalls("Pergunte o nome. ZERO TOOL CALLS. Aguarde.")}
     *      → true (sentinela embebida em texto livre).
     *   9. {@code forbidsToolCalls("Pergunte o nome ao usuário.")} → false (sem sentinela).
     *   10. {@code forbidsToolCalls("Use the search tool freely.")} → false
     *       (palavra "tool" presente mas sem sentinela completa).
     *
     * Nota refactor:
     *   Vira o campo declarativo — a função {@code forbidsToolCalls()} deixa
     *   de existir. Estes 10 casos viram um único teste positivo do campo
     *   ("quando toolsEnabled=false, options sem callbacks") + a remoção
     *   da função. Esse teste deve ser EXCLUÍDO após o refactor, não migrado.
     */
    @Test
    @Disabled("Skeleton — pendente implementação. Inventário §8.4, patch #5.")
    void forbidsToolCalls_detectsBilingualSentinelsCaseInsensitivelyInFreeText() {
        fail("Not implemented — skeleton from HARNESS_INVENTORY.md §8.4");
    }
}
