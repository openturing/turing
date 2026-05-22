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
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.viglet.turing.testutil.AbstractTuringSpringIT;

/**
 * Behavioral integration tests for {@link TurAgentChatExecutor} — paired
 * skeletons for the critical patches in {@code docs/IMPROVEMENTS.md}
 * §VI.3 pairing matrix that need <i>real</i> LLM behavior to lock the
 * contract (anchoring, drift, persona voice, tool preemption,
 * chip congruence).
 *
 * <p><b>Empty under T2.</b> This file ships as a shell so the {@code -Pllm-it}
 * Maven profile (see {@code turing-app/pom.xml}) has a class to bind to.
 * Task T7 (§VI.6 Phase 5) populates six paired skeletons: #1, #3, #7,
 * #10, #12, #13. Bodies must follow the
 * {@code ProgramaMatchEEFlowIT.buildChatModel()} pattern — real
 * {@code OpenAiChatModel} backed by {@code gpt-4o-mini} at temperature
 * {@code 0}, each test pinned to {@code gpt-4o-mini @ 2026-05} in its
 * Javadoc so model drift becomes visible at the test layer.
 *
 * <p>Activation:
 *
 * <pre>{@code
 * mvn verify -Pllm-it -pl turing-app -Dskip.npm=true
 * }</pre>
 *
 * <p>Without the {@code OPENAI_API_KEY} environment variable the class
 * is short-circuited by JUnit's
 * {@link EnabledIfEnvironmentVariable} — zero API cost, zero round-trips.
 * The default failsafe profile excludes {@code *BehavioralIT.java}
 * entirely (see the {@code maven-failsafe-plugin} {@code <excludes>}
 * block in the POM), so PR / CI runs don't even load this class.
 *
 * <p><b>Cost discipline:</b> the full suite per §VI.7 risk #8 is
 * ~$0.01 per nightly run at {@code gpt-4o-mini} pricing — cheap, but
 * still real money. Don't add tests here for assertions a Structural
 * test could lock. Use {@link TurAgentChatExecutorStructuralIT} when a
 * mocked {@code ChatModel} can verify the invariant.
 *
 * <h2>Implementation recipe (every test below assumes this scaffolding)</h2>
 *
 * Mirror {@code ProgramaMatchEEFlowIT.buildChatModel} verbatim. The
 * implementer must add to this class:
 *
 * <pre>{@code
 * @TestInstance(TestInstance.Lifecycle.PER_CLASS)  // required for non-static @BeforeAll
 * class TurAgentChatExecutorBehavioralIT {
 *     private static final String OPENAI_BASE_URL = "https://api.openai.com/v1";
 *     private static final String CHAT_MODEL = "gpt-4o-mini";
 *     private ChatModel chatModel;
 *
 *     @BeforeAll
 *     void buildChatModel() {
 *         String apiKey = System.getenv("OPENAI_API_KEY");
 *         OpenAIClient sync = OpenAIOkHttpClient.builder()
 *                 .baseUrl(OPENAI_BASE_URL).apiKey(apiKey).build();
 *         OpenAIClientAsync async = OpenAIOkHttpClientAsync.builder()
 *                 .baseUrl(OPENAI_BASE_URL).apiKey(apiKey).build();
 *         chatModel = OpenAiChatModel.builder()
 *                 .openAiClient(sync).openAiClientAsync(async)
 *                 .options(OpenAiChatOptions.builder()
 *                         .model(CHAT_MODEL).temperature(0.0).build())
 *                 .build();
 *     }
 * }
 * }</pre>
 *
 * <p>The flow is then driven via {@code engine.advance(flow, state, graph,
 * userMsg, assistantMsg, chatModel)} — the same shape ProgramaMatchEEFlowIT
 * uses. Fixtures live under {@code harness-it/} (created in T5); load them
 * via {@code com.viglet.turing.genai.testsupport.ChatFlowImportTestUtil}
 * and import onto a fresh agent per {@code @BeforeEach}.
 *
 * <p>Tolerance strategy (per §VI.4.3): retry zero. Flake → adjust regex /
 * synonyms before relaxing. gpt-4o-mini flake-rate at temp 0 with
 * deterministic prompt is &lt; 5% — loose regex absorbs.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class TurAgentChatExecutorBehavioralIT extends AbstractTuringSpringIT {

    /**
     * Patch #1 — persona post-flow voice (real LLM pairing of the structural #1 test)
     * Pairing: {@link TurAgentChatExecutorStructuralIT#resolvesPersonaAfterFlowSelection_picksWalkWrittenPersonaForInitialCall}
     * Fixture: {@code harness-it/persona-switch-mid-flow.chat-flow.json}
     * Pinning: gpt-4o-mini @ 2026-05
     *
     * <p>Cenário behavioral: o walk transparente cruza {@code persona-lucas}
     * via {@code __force_route = "lucas"}. O initial LLM call DEVE compor o
     * reply em VOZ DE LUCAS (peer/alumni), não em voz de Marina (consultora
     * formal — a default). Sem a re-resolução pós-walk (patch #1), o reply
     * ainda viria como Marina.
     *
     * <p>Loose regex assertions (per §VI.4.3):
     * <pre>{@code
     * assertThat(reply).containsAnyOf("Oi", "Olá", "Beleza", "Conta aí");
     * assertThat(reply.toLowerCase())
     *         .as("Marina formal markers must NOT appear when post-walk persona is Lucas")
     *         .doesNotContain("prezad")
     *         .doesNotContain("cordialmente");
     * // Lucas signs as Lucas at the end:
     * assertThat(reply).matches("(?si).*lucas.*");
     * }</pre>
     *
     * <p>Refactor note: pós-§I.5 inversion, advance roda antes do initial LLM call;
     * persona resolution acontece uma vez com cursor final. O teste sobrevive
     * com asserts idênticos — a OBSERVABILIDADE não muda, só a quantidade de
     * resoluções (3× → 1×). Pode ser mantido pós-refactor como guard duradouro.
     */
    @Test
    @Disabled("Behavioral skeleton — pendente implementação. §VI.3 patch #1.")
    void postWalkPersonaSwap_initialReplyComesInLucasVoiceNotMarina() {
        fail("Not implemented — Behavioral skeleton from IMPROVEMENTS.md §VI.3");
    }

    /**
     * Patch #3 — initial-call tool strip (real LLM pairing of the structural #3 test)
     * Pairing: {@link TurAgentChatExecutorStructuralIT#stripsToolsOnInitialCallWhenActiveNodeForbidsToolCalls}
     * Fixture: {@code harness-it/tool-free-node.chat-flow.json}
     * Pinning: gpt-4o-mini @ 2026-05
     *
     * <p>Cenário behavioral: parked em {@code ai-coupon-code} (aiInstruction
     * contém "NÃO CHAME NENHUMA TOOL"); o agent tem o Custom Tool stub
     * {@code compor_proposta_in_company} attached. SEM o tool-strip do
     * patch #3, gpt-4o-mini frequentemente chama a tool preemptivamente
     * ("vou já gerar uma proposta") — gerando proposta SEM cupom capturado.
     * COM o strip, o LLM tem que se contentar em pedir o cupom.
     *
     * <p>Assertion: o stub do tool registra invocações; depois do turno,
     * {@code verify(tool, never()).invoke(any())}. Asserção atravessa o
     * full stack — Spring AI tool execution loop, o decorator do
     * {@link com.viglet.turing.genai.tool.TurToolCallbackPipeline}, e o
     * próprio Mockito spy.
     *
     * <p>Refactor note: o tool-strip vira o campo declarativo
     * {@code ChatFlowNode.toolsEnabled} (§I.5 step 3). Behavioral fica
     * idêntico: tool não invocada quando o nó autor marcou tool-free.
     */
    @Test
    @Disabled("Behavioral skeleton — pendente implementação. §VI.3 patch #3.")
    void toolFreeNode_llmDoesNotPreemptivelyInvokeCustomTool() {
        fail("Not implemented — Behavioral skeleton from IMPROVEMENTS.md §VI.3");
    }

    /**
     * Patch #7 — regen full path (real LLM pairing of the structural #7 test)
     * Pairing: {@link TurAgentChatExecutorStructuralIT#firesSecondLlmCallAfterTransparentAdvanceToNewAiQuestion}
     * Fixture: {@code programa-match-ee.chat-flow.json} (in
     * {@code src/test/resources/customers/education/chat-flow/})
     * Pinning: gpt-4o-mini @ 2026-05
     *
     * <p>Cenário behavioral: usuário responde {@code ai-cargo} ("Gerente
     * sênior numa fintech, há 4 anos"); engine.advance() move cursor para
     * {@code ai-objetivo}; o regen DEVE gerar a pergunta de objetivo, não
     * uma re-pergunta de cargo. Validate via regex sobre o reply final:
     *
     * <pre>{@code
     * assertThat(reply)
     *         .as("Reply must be the ai-objetivo question, not a re-ask of cargo")
     *         .matches("(?i).*(objetivo|onde\\s+você\\s+quer\\s+estar|carreira).*\\?")
     *         .doesNotMatch("(?i).*qual\\s+é\\s+o\\s+seu\\s+cargo.*");
     * }</pre>
     *
     * <p>Refactor note: pós-inversion, não existe "regen" — advance roda
     * antes, prompt único compõe a pergunta do nó pós-advance. Asserção
     * sobrevive verbatim; a coisa que mudou é a forma de chegar lá.
     */
    @Test
    @Disabled("Behavioral skeleton — pendente implementação. §VI.3 patch #7.")
    void regenFullPath_replyIsQuestionForPostAdvanceNode_notReAskOfPreviousNode() {
        fail("Not implemented — Behavioral skeleton from IMPROVEMENTS.md §VI.3");
    }

    /**
     * Patch #10 — HIGHEST-PRIORITY OVERRIDE effective (real LLM pairing of #10)
     * Pairing: {@link TurAgentChatExecutorStructuralIT#appendsBilingualHighestPriorityOverrideOnRegen}
     * Fixture: {@code programa-match-ee.chat-flow.json}
     * Pinning: gpt-4o-mini @ 2026-05
     *
     * <p>Cenário behavioral em DUAS sub-tests (control + treatment) para
     * EVIDENCIAR o efeito do override:
     *
     * <ul>
     *   <li><b>Treatment</b> (override-on, default): após advance ai-cargo →
     *       ai-objetivo, o reply final é a pergunta de objetivo
     *       (regex: {@code (?i).*(objetivo|onde\s+você\s+quer\s+estar).*?\\?}).</li>
     *   <li><b>Control</b> (override-off, simulado por subclasse que
     *       sobrescreve {@code buildRegenOverride()} pra retornar ""): o
     *       reply REGRIDE para uma re-pergunta de cargo. Sem o
     *       OVERRIDE, gpt-4o-mini anchorea no padrão Q&A da conversa anterior.</li>
     * </ul>
     *
     * <p>Esta é a única test no set que prova VALOR do override em vez de só
     * sua presença estrutural — porque "string está no prompt" não prova que
     * mudou behavior. A subclasse de control é setup-pesado mas é o que dá
     * confiança que o patch #10 não é cosmético.
     *
     * <p>Refactor note: pós-inversion não há override (não há regen).
     * Test deve ser DELETADO pós-refactor — o cenário "anchoring na
     * conversa anterior" desaparece por construção.
     */
    @Test
    @Disabled("Behavioral skeleton — pendente implementação. §VI.3 patch #10.")
    void highestPriorityOverride_proventsRegressionToPreviousNodeQuestion() {
        fail("Not implemented — Behavioral skeleton from IMPROVEMENTS.md §VI.3");
    }

    /**
     * Patch #12 — aiInstruction literal echoed in override (real LLM pairing of #12)
     * Pairing: {@link TurAgentChatExecutorStructuralIT#inlinesAiInstructionLiteralInsideOverrideBlock}
     * Fixture: {@code programa-match-ee.chat-flow.json}
     * Pinning: gpt-4o-mini @ 2026-05
     *
     * <p>Cenário behavioral: após advance ai-cargo → ai-objetivo, o regen
     * reply contém um TERMO DISTINTIVO do {@code aiInstruction} de
     * ai-objetivo (ex.: "daqui a 3 anos"). Confirma que o re-inline literal
     * (linhas 920-931 do executor) não é só decoração — gpt-4o-mini
     * tipicamente ignora "pointer to section above" e recicla instrução
     * antiga; o re-inline força a recuperação literal.
     *
     * <pre>{@code
     * assertThat(reply)
     *         .as("Regen reply must echo the distinctive '3 anos' span from aiInstruction")
     *         .containsIgnoringCase("3 anos");
     * }</pre>
     *
     * <p>Refactor note: pós-inversion, não há override block separado —
     * o prompt único já é composto com o aiInstruction do nó atual.
     * Asserção sobrevive, mas pelo prompt único, não pelo re-inline.
     */
    @Test
    @Disabled("Behavioral skeleton — pendente implementação. §VI.3 patch #12.")
    void aiInstructionLiteralEcho_regenReplyContainsDistinctiveSpanFromAddendum() {
        fail("Not implemented — Behavioral skeleton from IMPROVEMENTS.md §VI.3");
    }

    /**
     * Patch #13 — chip congruence (real LLM pairing of #13)
     * Pairing: {@link TurAgentChatExecutorStructuralIT#inlinesChipLabelsAndCongruencyImperativeInOverrideWhenDestNodeHasInlineOptions}
     * Fixture: {@code harness-it/regen-with-chips.chat-flow.json}
     * Pinning: gpt-4o-mini @ 2026-05
     *
     * <p>Cenário behavioral: após advance ai-cargo → ai-decision-role
     * (chips: RH/L&D, Gestor, C-level, Comprador), o regen reply é uma
     * pergunta CUJAS RESPOSTAS NATURAIS incluem todos os 4 chips
     * (papel/decisão/atuação). SEM o inline de chips + "MUST be congruent"
     * (linha 932-945 do executor), gpt-4o-mini frequentemente regenera
     * uma pergunta sobre num_pessoas com role chips abaixo — a falha
     * literal do bug original.
     *
     * <pre>{@code
     * assertThat(reply.toLowerCase())
     *         .as("Question must admit all 4 chip labels as natural answers")
     *         .matches(".*(papel|decisão|atuação).*\\?");
     * // Negative: must NOT be about org size (the recurring failure mode):
     * assertThat(reply.toLowerCase()).doesNotContain("quantas pessoas");
     * assertThat(reply.toLowerCase()).doesNotContain("tamanho da equipe");
     * }</pre>
     *
     * <p>Refactor note: pós-inversion, o prompt único já é composto com os
     * chips do nó atual em contexto. Asserção sobrevive — chips e pergunta
     * sempre alinhados por construção.
     */
    @Test
    @Disabled("Behavioral skeleton — pendente implementação. §VI.3 patch #13.")
    void chipCongruence_regenQuestionAdmitsAllChipLabelsAsNaturalAnswers() {
        fail("Not implemented — Behavioral skeleton from IMPROVEMENTS.md §VI.3");
    }
}
