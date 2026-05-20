# Plano de Implementação — ITs do Execution Harness

> Plano para preencher os bodies dos 15 esqueletos em `TurAgentChatExecutor*IT.java`.
> Cada `@Test` é o contrato que o refactor "advance → 1× LLM call" precisa preservar.

Arquivos-alvo:

- [`turing-app/src/test/java/com/viglet/turing/genai/TurAgentChatExecutorRaceIT.java`](../../turing-app/src/test/java/com/viglet/turing/genai/TurAgentChatExecutorRaceIT.java) — 10 esqueletos (A)
- [`turing-app/src/test/java/com/viglet/turing/genai/TurAgentChatExecutorDeclarativeIT.java`](../../turing-app/src/test/java/com/viglet/turing/genai/TurAgentChatExecutorDeclarativeIT.java) — 3 esqueletos (B)
- [`turing-app/src/test/java/com/viglet/turing/genai/TurAgentChatExecutorMiddlewareIT.java`](../../turing-app/src/test/java/com/viglet/turing/genai/TurAgentChatExecutorMiddlewareIT.java) — 2 esqueletos (C)

Referências de patches: [`HARNESS_INVENTORY.md`](HARNESS_INVENTORY.md) §8.4.

## 1. Estratégia geral

**Por que mock de `ChatModel` em vez de OpenAI real.** O IT canônico [`ProgramaMatchEEFlowIT`](../../turing-app/src/test/java/com/viglet/turing/customers/education/flow/ProgramaMatchEEFlowIT.java) usa OpenAI real (`gpt-4o-mini`, temperature 0) precisamente porque seu propósito é validar o motor end-to-end, incluindo o comportamento real do judge contra a Marina/Lucas. Para os 15 esqueletos o objetivo é o oposto: pinar **invariantes estruturais do executor** (qual prompt foi montado, quantas chamadas saíram, quais `toolCallbacks` estavam ligados, qual persona foi composta, qual `history` foi enviado). O conteúdo da resposta do LLM é irrelevante e introduzir não-determinismo + custo + `@EnabledIfEnvironmentVariable` no harness de race elimina justamente a propriedade que faz esses testes valerem: **rodam em todo `mvn verify`, sem env-var, em segundos**.

**Por que o ponto de mock é `TurLlmModelFactory.createChatModel(...)`, não `ChatModel` direto.** O executor obtém o `ChatModel` em runtime via injeção do `TurLlmModelFactory` ([`TurAgentChatExecutor.java:121-151`](../../turing-app/src/main/java/com/viglet/turing/genai/TurAgentChatExecutor.java#L121-L151)) — a factory descriptografa a API key e enrola o provider em [`TurResilientChatModel`](../../turing-app/src/main/java/com/viglet/turing/resilience/llm/TurLlmModelFactory.java#L61-L65). Não há `ChatModel` injetado diretamente no executor. O ponto de mock natural é portanto **a factory**: substituí-la por um spy/mock que retorna um `ChatModel` Mockito controlado pelo teste. Isso também evita ter de mexer no `TurSecretCryptoService` (a key descriptografada nunca chega ao mock).

**Por que `@SpringBootTest` e não unit-test puro do executor.** O `TurAgentChatExecutor` tem 14 colaboradores no construtor: `TurLlmModelFactory`, `TurSecretCryptoService`, `TurNativeToolService`, `TurMcpToolCallbackService`, `TurCustomToolCallbackService`, `TurToolCallbackPipeline`, `TurLLMTokenUsageService`, `TurChatFlowRepository`, `TurChatFlowEngineService`, `TurPersonaPromptComposer`, `TurPersonaToneValidator`, `TurPersonaBrandContextProvider`, `TurAgentPersonaResolver`, `TurChatAnalyticsService`, `TurChatMemoryService`. Stubar todos por unit-test reescreveria metade do produto. Reusar o context Spring + base [`AbstractTuringSpringIT`](../../turing-app/src/test/java/com/viglet/turing/testutil/AbstractTuringSpringIT.java) (H2 efêmero por classe, snapshot opcional) custa ~30s de boot uma vez e dá grafo de objetos real.

**Quais beans ficam reais:** `TurChatFlowEngineService`, `TurAgentPersonaResolver`, `TurPersonaPromptComposer`, `TurChatFlowRepository`, `TurAIAgentRepository`, `TurPersonaRepository`, `TurAIAgentSlotRepository`, `TurChatFlowStateRepository`, todas as `*GuardrailStrategy`, todas as `ChatFlowOps` puras. **Quais ficam mockados:** `TurLlmModelFactory` (substituído via `@TestConfiguration`/`@Primary`), e opcionalmente `TurChatAnalyticsService` + `TurChatMemoryService` (para evitar IO desnecessário no H2 e ruído no log — ambos já têm `try/catch` defensivo, mas trocá-los por NoOp deixa os asserts mais limpos). `TurCustomToolCallbackService` e `TurMcpToolCallbackService` podem ficar reais — quando o agent não tem custom-tools nem MCP servers anexados, retornam array vazio e não disparam IO.

## 2. Infraestrutura compartilhada

### 2.1. `@TestConfiguration` "TurAgentChatExecutorTestSupport"

Localização: `turing-app/src/test/java/com/viglet/turing/genai/testsupport/TurAgentChatExecutorTestSupport.java` (novo).

Responsabilidades:

1. Substituir o bean `TurLlmModelFactory` por um wrapper que retorna o mesmo `ChatModel` Mockito por instância de teste (compartilhado entre initial call e regen — quem queue as respostas é o teste).
2. Expor um helper `mockNextChatCalls(ChatResponse...)` que enfileira `ChatResponse`s para `chatModel.call(any(Prompt.class))` em ordem. Implementado via `Mockito.when(...).thenReturn(first).thenReturn(second)...`.
3. Expor um `ArgumentCaptor<Prompt>` global, recuperável via `getCapturedPrompts()` — usado nos asserts de "1º prompt vs 2º prompt".
4. Substituir `TurChatAnalyticsService` por um spy que mantém a interface mas no-op'a `recordSessionStart` e `recordTurn` para evitar persistência.
5. Substituir `TurChatMemoryService` por um spy análogo — `recordTurn` retorna sem escrever vector store (que pode não estar configurado no H2 efêmero).

Forma resumida (sem código completo):

```java
@TestConfiguration
public class TurAgentChatExecutorTestSupport {
    public static class ChatModelHarness { /* enqueue + captor */ }
    @Bean ChatModelHarness chatModelHarness() { ... }
    @Bean @Primary TurLlmModelFactory mockLlmModelFactory(ChatModelHarness h) { ... }
    @Bean @Primary TurChatAnalyticsService noopChatAnalyticsService() { ... }
    @Bean @Primary TurChatMemoryService noopChatMemoryService() { ... }
}
```

A `ChatModelHarness` encapsula:

- `ChatModel chatModel` (Mockito mock criado uma vez)
- `ArgumentCaptor<Prompt> promptCaptor` (idem)
- `void enqueue(ChatResponse... responses)` — re-stub `when(chatModel.call(promptCaptor.capture())).thenReturn(first, rest)`
- `List<Prompt> capturedPrompts()` — `promptCaptor.getAllValues()`
- `void reset()` — `Mockito.reset(chatModel)` + nova lista interna (chamado em `@BeforeEach`)

Estratégia de construir `ChatResponse` de teste: usar `org.springframework.ai.chat.model.ChatResponse.builder().generations(List.of(new Generation(new AssistantMessage("ok"))))` — Spring AI fornece a API direta; basta um helper `static ChatResponse okText(String text)`.

### 2.2. Base class `AbstractAgentExecutorIT`

**Decisão: SIM**, vale uma base class. Os 15 esqueletos compartilham 80% do setup (criar `TurAIAgent`, criar `TurLLMInstance`, criar `TurPersona`, importar flow JSON, posicionar state em nó N, chamar `executor.execute(...)`, esperar Flux). Sem base class cada `@Test` repete ~40 linhas de boilerplate. Helpers propostos:

- `TurAIAgent createAgent(String title, TurPersona defaultPersona)` — persiste e retorna.
- `TurLLMInstance createMockableLlmInstance()` — cria um `TurLLMInstance` minimal (provider type qualquer, apiKey encriptada placeholder — a factory mockada ignora).
- `TurChatFlow importFlowFromClasspath(String resourcePath, int bundleIndex, TurAIAgent agent)` — replica `ProgramaMatchEEFlowIT.importIntoAgent`, mas reusando a fixture-loader como utility (extrair para `EducationEEFixtures` ou novo `ChatFlowImportTestUtil`).
- `TurChatFlowState seedStateAt(String conversationId, TurChatFlow flow, String nodeId, Map<String,String> vars)` — útil para os ITs que precisam "começar parked no nó X" sem rodar turnos prévios.
- `List<ChatResponse> runTurn(TurAIAgent agent, TurLLMInstance llm, List<ChatMessageItem> history, String conversationId, String flowId)` — chama `executor.execute(...)` e `.collectList().block(Duration.ofSeconds(5))`.
- `TurChatFlowState reloadState(String conversationId)` — `stateRepository.findByConversationId(...).orElseThrow()` para asserts pós-turn.
- `Prompt firstPrompt()` / `Prompt regenPrompt()` — atalhos pra `harness.capturedPrompts().get(0)` / `.get(1)`.
- `String systemTextOf(Prompt p)` — extrai o `SystemMessage.text()` do prompt.

A base class é `abstract`, sem `@SpringBootTest` ela mesma (cada IT herda já tem `@SpringBootTest @Import(TurAgentChatExecutorTestSupport.class)` no top). Estende `AbstractTuringSpringIT` para reaproveitar o H2 efêmero.

### 2.3. Fixtures novas necessárias

Avaliação por patch:

- **`programa-match-ee.chat-flow.json`** (existente) cobre patches **#1 parcial, #6 sim, #9, #10, #11, #12, #14, #15 indiretamente, #18, #20**. Tem agent owner + persona Marina/Lucas + nós aiQuestion encadeados (`ai-name → ai-cargo → ai-objetivo → ai-area`) que dão o "advance que move o cursor por transparente walk" necessário para os patches de regen. Cobre também o trigger contract para #6.
- **`in-company-match.chat-flow.json`** (existente, sem IT) tem o nó `ai-coupon-code` e `ai-deliver-proposta` que motivaram os patches **#3/#4/#5**. Porém o `aiInstruction` atual NÃO contém a sentinela `"NÃO CHAME NENHUMA TOOL"` — o tool stripping nunca dispararia rodando essa fixture as-is. Duas opções: (a) mutar o `aiInstruction` em runtime no teste via JSON patch, (b) criar fixture nova com a sentinela. A opção (a) é frágil (acopla o teste à estrutura interna do JSON). **Recomendação: fixture nova baseada no in-company.**

Fixtures novas a criar:

**Fixture 1: `harness-it/tool-free-node.chat-flow.json`**
Estrutura: `start → ai-coupon-code (aiInstruction contém "NÃO CHAME NENHUMA TOOL") → switch-cupom-ok → ai-deliver (aiInstruction contém "Chame compor_proposta_in_company") → end`
Patches que serve: **#3, #4, #5** (sentinela bilíngue em PT-BR upper).
Notas: o agent associado precisa ter o custom-tool `compor_proposta_in_company` registrado (pode ser stub que registra a chamada). A fixture é a mínima necessária para exercer o stripping no initial call (#3) e no regen (#4).

**Fixture 2: `harness-it/persona-switch-mid-flow.chat-flow.json`**
Estrutura: `start → persona-marina → ai-name → switch-route → persona-lucas → ai-objetivo → end`
Patches que serve: **#1** (persona walk-written na descida transparente do trigger turn) e **#2** (persona switch no meio do flow disparando regen com persona nova).
Variação ou nova: nova — o `programa-match-ee` tem persona-marina e persona-lucas em flows SEPARADOS (A/B), não no MESMO flow com switch entre eles. Esta fixture força a "transparent walk cruza persona node entre user input e nó destino" exigida pelos asserts.

**Fixture 3: `harness-it/regen-with-chips.chat-flow.json`**
Estrutura: `start → ai-cargo (aiInstruction "Pergunte cargo") → switch-area → ai-decision-role (aiInstruction "Pergunte papel na decisão", inlineOptions=["RH/L&D", "Gestor", "C-level", "Comprador"]) → end`
Patches que serve: **#13** (chips inline no override + "MUST be congruent") e **#8** (suppressão de chips quando `textAlignedWithCurrentNode=false`). Reproduz literalmente o cenário descrito no patch #13.
Variação ou nova: nova — necessária porque o programa-match não tem `inlineOptions` num nó alvo de regen com `aiInstruction` descrevendo um assunto diferente.

**Fixture 4 (opcional): `harness-it/tool-write-during-call.chat-flow.json`**
Estrutura: `start → ai-area (aiInstruction força chamar tool search_ee_programs) → ai-confirma → end`
Patches que serve: **#15** (refresh do state pre-advance). Pode reusar o `programa-match-ee` com um custom-tool stub que escreve no slot `programas_match` via `TurCustomToolSlotHelper.save(...)` mid-turn. **Verificar primeiro se a complexidade de simular tool-call lifecycle do Spring AI compensa** — se o stub for muito ad-hoc, marcar como TODO e cobrir patch #15 indiretamente (vide §6).

Total: **3 fixtures novas firme + 1 condicional**, todas sob `turing-app/src/test/resources/harness-it/`.

## 3. Fases de implementação

### Fase 0 — Infraestrutura (dia 0, sem dependências)

1. Criar pacote `turing-app/src/test/java/com/viglet/turing/genai/testsupport/`.
2. Implementar `TurAgentChatExecutorTestSupport` (§2.1) + `ChatModelHarness`.
3. Implementar `AbstractAgentExecutorIT` (§2.2) com helpers `createAgent`, `createMockableLlmInstance`, `importFlowFromClasspath`, `seedStateAt`, `runTurn`, `reloadState`, `firstPrompt`, `regenPrompt`, `systemTextOf`.
4. Extrair `ChatFlowImportTestUtil.importIntoAgent(...)` a partir de `ProgramaMatchEEFlowIT.importIntoAgent` para reuso. Original mantém método privado que delega para o util.
5. Smoke test interno (deletar antes de fechar a fase): `_smokeContextLoadsAndChatModelMockReceivesCall` — `@Test` que roda `executor.execute(agentSimples, llmMock, listOf(user("oi")), null, null)`, espera `harness.capturedPrompts()` tamanho 1.

Critério de pronto: smoke test verde + `mvn verify -pl turing-app -Dskip.npm=true` sem regressões nos ITs existentes (`@Primary` overrides não devem poluir o context de outros ITs porque o `@TestConfiguration` só é importado quando explicitamente declarado).

Estimativa: **0.5–1 dia**.

### Fase 1 — Tests sem fixture nova (dia 1–2)

Esqueletos exercitáveis APENAS com `programa-match-ee.chat-flow.json` (após mutações mínimas de state). Total: **8 dos 15**.

1. **`appendsBilingualHighestPriorityOverrideOnRegen`** (#10): seed state em `ai-cargo` com vars `{name: "X"}`, user msg "Gerente sênior", 2 chat responses. Asserta `systemTextOf(regenPrompt())` contém `"INSTRUÇÃO DE PRIORIDADE MÁXIMA"` E `"HIGHEST-PRIORITY OVERRIDE"`, e que `systemTextOf(firstPrompt())` NÃO contém nenhum dos dois. Bloqueio: nenhum.
2. **`dropsBaseSystemPromptInRegenToAvoidOrchestrationConflict`** (#11): configurar `agent.setSystemPrompt("ROUTER LLM decides flow. Show 3 caminhos welcome on first turn.")`, seed em `ai-cargo`. Asserta marker em `firstPrompt` e ausência em `regenPrompt`. Bloqueio: nenhum.
3. **`omitsNextStepPreviewHintInRegenAddendum`** (#9): seed em `ai-cargo` (next = `ai-objetivo` cujo aiInstruction começa com "Pergunte onde o visitante quer estar"). Asserta substring de `ai-objetivo.aiInstruction` em `firstPrompt` (next-step hint) e que `regenPrompt` NÃO contém o aiInstruction de `ai-area` (o "nó depois do destino do regen"). Bloqueio: confirmar que `ChatFlowOps.buildBaseAddendum` realmente emite a aiInstruction do nó next-step no initial — ler a função antes de implementar.
4. **`inlinesAiInstructionLiteralInsideOverrideBlock`** (#12): seed em `ai-cargo`. Esperar 2 ocorrências da substring distintiva do `ai-objetivo.aiInstruction` (ex.: "daqui a 3 anos") em `regenPrompt` — uma no `newAddendum` (`FLOW STATE`), outra no override (`CURRENT STEP'S INSTRUCTION (literal — follow this, not any earlier step's)`). Asserta com `StringUtils.countMatches(systemText, "daqui a 3 anos") == 2`. Bloqueio: nenhum.
5. **`trimsHistoryToBudgetedRecentTurnsInRegenCall`** (#14): construir `history` com 5 user turns + 5 assistant turns = 10 mensagens, mais o user atual. Asserta `firstPrompt.getInstructions()` filtrado por `UserMessage.class` tem >= 6 (todo o history + current); `regenPrompt` tem `<= 3` UserMessages (REGEN_HISTORY_TURN_BUDGET); última UserMessage do regen == user atual. Bloqueio: nenhum.
6. **`resolvesPersonaAfterFlowSelection_picksWalkWrittenPersonaForInitialCall`** (#1, parcial): no programa-match, o trigger turn faz descent transparente por `start → persona-marina → ai-name`. Esse caminho NÃO troca a persona (Marina é a default). Para validar **a chamada extra** sem fixture nova, podemos: (a) usar `@SpyBean` no `TurAgentPersonaResolver` e contar invocations — sem precisar de persona switch real; o invariante a travar é "resolver chamado >= 2 vezes no trigger turn". Asserta `verify(personaResolver, atLeast(2)).resolve(...)`. Bloqueio: `@SpyBean` em service real precisa de proxy CGLIB — Spring Boot 4 com Java 21 + `--add-opens` é checagem rápida. Se falhar, fallback é `@MockBean` + delegação manual.
7. **`fallsBackToHeuristicStrategyWhenGuardrailMethodHasNoRegisteredBean`** (#18): mais limpo via reflection no `strategyByMethod` Map para REMOVER a entrada de `LLM_JUDGE`, flow configurado com `LLM_JUDGE`. Asserta turno completa sem exception + `HeuristicGuardrailStrategy.advance(...)` invocado uma vez (spy) + log captado contém "fallback" ou "HEURISTIC". Bloqueio: capturar logs em Spring Boot 4 — usar `OutputCaptureExtension` do Spring Boot Test (`spring-boot-test-junit-jupiter`). Verificar disponibilidade.
8. **`discardsForcedFlowWhenItDoesNotBelongToCallingAgent`** (#20): criar `agentA1` com flow `flowA1`, `agentA2` separado. `runTurn(agentA2, llm, history, conv, flowA1.getId())`. Asserta: `engine.selectActiveFlow(eq(agentA2), ...)` foi invocado pelo menos 1 vez (spy do engine); `state.getFlow().getId()` != `flowA1.getId()` (ou null se router também não pegou); variante positiva (sanity) com `flowA2` pertencendo a `agentA2` invoca o forced sem cair no router. Bloqueio: nenhum.

Critério de pronto: 8 `@Disabled` removidos, asserts implementados, build verde sem env-var `OPENAI_API_KEY`.

Estimativa: **0.5–1 dia por test** quando a infra existe ≈ **2 dias** para os 8. Real-world: 10–30min por teste once infra está pronta, mas reserva 4x para debug.

### Fase 2 — Criação das fixtures novas (dia 3)

Para CADA fixture identificada em §2.3:

1. **`harness-it/tool-free-node.chat-flow.json`** — começar do `in-company-match.chat-flow.json`, podar para 3 nós (start, ai-coupon-code, ai-deliver-proposta, end + edges). Adicionar a sentinela literal `"NÃO CHAME NENHUMA TOOL — capture o código e siga."` ao `aiInstruction` do `ai-coupon-code`. Adicionar `"NÃO CHAME NENHUMA TOOL"` ao `ai-deliver-proposta.aiInstruction` (para servir o patch #4 que strips no regen). Persona única (qualquer — Camila do in-company já existe na fixture, copiar).
2. **`harness-it/persona-switch-mid-flow.chat-flow.json`** — bundle com 1 flow + 2 personas (Marina + Lucas). Grafo: 5 nós + 4 edges. O `switch-route` decide pelo valor da var `__force_route` (escrito manualmente pelo teste via `seedStateAt` vars).
3. **`harness-it/regen-with-chips.chat-flow.json`** — bundle com 1 flow + 1 persona. Grafo: 4 nós + 3 edges. O `ai-decision-role` tem `inlineOptions` LITERAL com os 4 labels do patch #13.
4. (Opcional) **`harness-it/tool-write-during-call.chat-flow.json`** — se decidirmos cobrir #15. Avaliar custo de stubbar tool-call lifecycle do Spring AI: se o stub crescer >50 linhas, pular e marcar #15 como "covered indirectly via existing slot snapshot diff in TurChatFlowEngineService".

Validação de cada fixture: implementar 1 `@Test` "smoke" por fixture que carrega via `chatFlowImportTestUtil.importIntoAgent(...)`, chama `engine.parseGraph(...)` e loga o grafo. Falha rápida se shape do JSON tiver typo.

Estimativa: **0.5 dia**.

### Fase 3 — Tests com fixtures novas (dia 4–5)

7 esqueletos restantes:

9. **`reResolvesPersonaInsideRegen_whenAdvanceCrossedPersonaNode`** (#2): usa Fixture 2. Seed em `ai-name`, user input dispara switch que cruza `persona-lucas`. Asserta `personaResolver.resolve(...)` invocado 3x (linha ~240 + pós resolveFlowContext + dentro regen); `systemTextOf(regenPrompt())` contém o `systemInstruction` da persona Lucas; reply final == 2ª resposta. Bloqueio: spy do resolver (vide #1).
10. **`stripsToolsOnInitialCallWhenActiveNodeForbidsToolCalls`** (#3): usa Fixture 1. Seed em `ai-coupon-code` (que tem a sentinela). Asserta `((ToolCallingChatOptions) firstPrompt.getOptions()).getToolCallbacks()` é vazio; o custom-tool spy NUNCA foi invocado; variante positiva (nó sem sentinela) NÃO strips. Bloqueio: garantir que o agent tem custom-tool registrado no setUp (via spy/mock — IT não usa OpenAI então o tool nunca dispara realmente, mas o callback PRECISA estar no array antes do strip).
11. **`stripsToolsOnRegenCallWhenPostAdvanceNodeForbidsToolCalls`** (#4): usa Fixture 1. Seed em estado pré-advance que avança para `ai-deliver-proposta` (sentinela). Asserta `((ToolCallingChatOptions) regenPrompt.getOptions()).getToolCallbacks()` vazio; tool nunca invocado; reply final == 2ª resposta. Bloqueio: nenhum se #3 funcionar.
12. **`forbidsToolCalls_detectsBilingualSentinelsCaseInsensitivelyInFreeText`** (#5): **NÃO precisa de fixture nem `@SpringBootTest`**. É unit test puro do método estático package-private `TurAgentChatExecutor.forbidsToolCalls(String)`. Pode ficar no `*IT.java` por coesão mas marcar com comentário. 10 casos via `@ParameterizedTest` + `@CsvSource` (atenção: strings com vírgula em CSV precisam de aspas duplas envoltas).
13. **`suppressesChipsWhenAssistantOverrideDoesNotMatchPostAdvanceNode`** (#8): usa Fixture 3 OU programa-match adaptado. Strategy mockada/spy para retornar `AdvanceResult` com `assistantOverride="literal text"`. Esperar SSE não contendo o array de chips do nó destino. **Bloqueio importante:** capturar o `Flux<ChatResponse>` SSE — o stream emite tokens E options separadamente. Asserta que `responses.stream().filter(r -> "options".equals(r.type())).count() == 0` quando override + advance, e `> 0` no turno seguinte sem override.
14. **`inlinesChipLabelsAndCongruencyImperativeInOverrideWhenDestNodeHasInlineOptions`** (#13): usa Fixture 3. Seed em `ai-cargo`, user input avança para `ai-decision-role`. Asserta `systemTextOf(regenPrompt())` contém os 4 labels literais + a string "MUST be congruent"; variante negativa: trocar para nó SEM `inlineOptions` e assertar bloco ausente.
15. **`refreshesStateFromDbBeforeStrategyAdvance_preservingSlotWritesFromToolCallbacks`** (#15): Fixture 4 (se criada) OU spy do `TurChatFlowStateRepository`. Caminho simulado: usar `@SpyBean(TurChatFlowStateRepository)` para contar `findById(stateId)` invocations DENTRO de `advance(...)`. Mid-turn, simular write-by-tool reescrevendo `state.variablesJson` no DB diretamente via repository.save numa thread paralela OU via callback do Mockito no first chatModel.call. Asserta: pós-turn, `programas_match` no DB == valor escrito pelo "tool"; `findById` invocado pelo menos 1x dentro do advance. Bloqueio sério: difícil de simular determinístico. **Pode ficar como TODO se complexidade explodir.**

Estimativa: **2 dias**.

### Fase 4 — Trigger contract & comportamentos extras (dia 6)

Reviewar §8.5 do inventário — 6 comportamentos cobertos por ITs existentes mas não catalogados como patches:

- Trigger contract pós-2026.2.x — coberto em `ProgramaMatchEEFlowIT.autoTriggerRouter_rejectsGreeting_picksFlowOnExplicitIntent`.
- Import-bundle persona enum guard — coberto em `importBundle_createsPersonaSlotsAndFlowWithExpectedSettings`.
- Cascade ascent — coberto em `TurChatFlowSubFlowEngineIT.cascadeAscent_*`.
- Slot extraction sem LLM — coberto em `ChatHandoffAndSlotExtractIT`.
- BR document validation patterns — coberto em `ChatFlowOpsTest`.
- Variable interpolation contract — coberto em `ChatFlowOpsTest.interpolate_*`.

**Decisão: não criar ITs novas para esses comportamentos.** Já têm cobertura em outros lugares (alguns com OpenAI real, mas o lookback é menor — comportamento estrutural não muda). Registrar isso no plano evita esforço duplicado.

Estimativa: **0** (já coberto). Linha registrada por completude.

## 4. Tabela de implementação detalhada

Ordem de implementação ≠ ordem dos esqueletos no arquivo. Ordenado por dependência (fase + custo crescente):

| Ordem | Fase | Test method | Patch | Cat | Fixture | Mocks chave | Asserts chave |
|-------|------|-------------|-------|-----|---------|-------------|---------------|
| 1 | 1 | `appendsBilingualHighestPriorityOverrideOnRegen` | #10 | A | programa-match-ee | LlmModelFactory→mock; 2 ChatResponse "ok" | `systemTextOf(regenPrompt()).contains("INSTRUÇÃO DE PRIORIDADE MÁXIMA")`; first prompt NÃO contém |
| 2 | 1 | `dropsBaseSystemPromptInRegenToAvoidOrchestrationConflict` | #11 | A | programa-match-ee + `agent.setSystemPrompt("ROUTER LLM ...")` | LlmModelFactory→mock; 2 ChatResponse | `firstPrompt` contém "ROUTER LLM"; `regenPrompt` NÃO contém |
| 3 | 1 | `omitsNextStepPreviewHintInRegenAddendum` | #9 | A | programa-match-ee | LlmModelFactory→mock; 2 ChatResponse | `firstPrompt` contém substring de `ai-objetivo.aiInstruction` (next-step hint); `regenPrompt` NÃO contém aiInstruction de `ai-area` |
| 4 | 1 | `inlinesAiInstructionLiteralInsideOverrideBlock` | #12 | A | programa-match-ee | LlmModelFactory→mock; 2 ChatResponse | `StringUtils.countMatches(systemTextOf(regenPrompt()), "daqui a 3 anos") == 2` |
| 5 | 1 | `trimsHistoryToBudgetedRecentTurnsInRegenCall` | #14 | A | programa-match-ee + history de 5+ user turns | LlmModelFactory→mock; 2 ChatResponse | `firstPrompt` `UserMessage` count >= 6; `regenPrompt` `UserMessage` count <= 3; última == atual |
| 6 | 1 | `resolvesPersonaAfterFlowSelection_picksWalkWrittenPersonaForInitialCall` | #1 | A | programa-match-ee (trigger turn) | LlmModelFactory→mock; SpyBean `TurAgentPersonaResolver` | `verify(resolver, atLeast(2)).resolve(...)` no trigger turn |
| 7 | 1 | `fallsBackToHeuristicStrategyWhenGuardrailMethodHasNoRegisteredBean` | #18 | C | programa-match-ee | Reflection remove LLM_JUDGE de `strategyByMethod`; SpyBean `HeuristicGuardrailStrategy`; OutputCaptureExtension | Sem exception; `Heuristic.advance` invocado 1x; log contém "fallback" + "HEURISTIC" |
| 8 | 1 | `discardsForcedFlowWhenItDoesNotBelongToCallingAgent` | #20 | C | programa-match-ee (2 agents) | LlmModelFactory→mock; SpyBean `TurChatFlowEngineService.selectActiveFlow` | flowAtivo != flowDeA1; `selectActiveFlow(A2,...)` invocado >=1; sanity positivo: forced de A2 não passa pelo router |
| 9 | 2 | (smoke) `loadsToolFreeNodeFixture` | n/a | n/a | Fixture 1 (nova) | — | `engine.parseGraph(...).isPresent()` |
| 10 | 2 | (smoke) `loadsPersonaSwitchMidFlowFixture` | n/a | n/a | Fixture 2 (nova) | — | idem |
| 11 | 2 | (smoke) `loadsRegenWithChipsFixture` | n/a | n/a | Fixture 3 (nova) | — | idem |
| 12 | 3 | `forbidsToolCalls_detectsBilingualSentinelsCaseInsensitivelyInFreeText` | #5 | B | — (unit puro) | — | 10 casos `@ParameterizedTest`; null/empty/PT-upper/PT-lower/PT-no-accent/EN-mixed/EN-short/embedded/no-sentinel/"tool" sem sentinela |
| 13 | 3 | `stripsToolsOnInitialCallWhenActiveNodeForbidsToolCalls` | #3 | B | Fixture 1 | LlmModelFactory→mock; tool spy `compor_proposta_in_company` | `((ToolCallingChatOptions) firstPrompt.getOptions()).getToolCallbacks().isEmpty()`; `verify(tool, never()).invoke(...)`; variante negativa em nó sem sentinela |
| 14 | 3 | `stripsToolsOnRegenCallWhenPostAdvanceNodeForbidsToolCalls` | #4 | B | Fixture 1 | LlmModelFactory→mock; 2 ChatResponse; tool spy | `regenPrompt.getOptions().toolCallbacks` vazio; tool nunca invocado; reply final == 2ª |
| 15 | 3 | `reResolvesPersonaInsideRegen_whenAdvanceCrossedPersonaNode` | #2 | A | Fixture 2 | LlmModelFactory→mock; 2 ChatResponse; SpyBean resolver | `verify(resolver, times(3)).resolve(...)`; `regenPrompt` contém Lucas systemInstruction; reply == 2ª |
| 16 | 3 | `inlinesChipLabelsAndCongruencyImperativeInOverrideWhenDestNodeHasInlineOptions` | #13 | A | Fixture 3 | LlmModelFactory→mock; 2 ChatResponse | `regenPrompt` contém 4 labels literais + "MUST be congruent"; variante negativa sem `inlineOptions` |
| 17 | 3 | `suppressesChipsWhenAssistantOverrideDoesNotMatchPostAdvanceNode` | #8 | A | Fixture 3 OU programa-match | LlmModelFactory→mock; Strategy mockada para retornar `assistantOverride != null` | SSE: 0 events com `type="options"` quando override+advance; turno seguinte > 0 |
| 18 | 3 | `refreshesStateFromDbBeforeStrategyAdvance_preservingSlotWritesFromToolCallbacks` | #15 | A | Fixture 4 (cond.) OU programa-match + repo spy | SpyBean `TurChatFlowStateRepository`; callback no chatModel mid-turn que escreve slot via repository | Pós-turn: slot escrito sobrevive; `findById(stateId)` invocado >=1 dentro de `advance` |

**Total: 15 esqueletos + 3 smokes de fixture.**

## 5. Padrões de mock comuns

### 5.1. Mockar `ChatModel.call(Prompt)` single vs multi-call

Padrão central — usado em 13 dos 15 esqueletos. Não fazemos streaming (`ChatModel.stream(...)`) porque o executor usa `chatModel.call(prompt)` síncrono dentro do `Mono.fromCallable`. Para múltiplas chamadas (initial + regen):

```java
when(harness.chatModel().call(harness.promptCaptor().capture()))
    .thenReturn(okText("initial-ack"))
    .thenReturn(okText("regen-reply"));
```

`harness.capturedPrompts()` retorna `List<Prompt>` na ordem das chamadas. `firstPrompt()` = `.get(0)`, `regenPrompt()` = `.get(1)`. Quando o teste espera apenas 1 chamada (ex.: trigger turn force-regen), o `times(1)` no `verify` cobre.

### 5.2. Capturar `Prompt` para inspecionar `SystemMessage`

```java
String systemTextOf(Prompt p) {
    return p.getInstructions().stream()
        .filter(m -> m instanceof SystemMessage)
        .map(Message::getText)
        .findFirst().orElseThrow();
}
```

`UserMessage` count:

```java
long userCount = p.getInstructions().stream()
    .filter(m -> m instanceof UserMessage)
    .count();
```

Para asserts de "contém substring", `assertThat(systemTextOf(p)).contains("X")` direto. Para "exatamente 2 ocorrências", `StringUtils.countMatches`.

### 5.3. Inspecionar `toolCallbacks` no `DefaultToolCallingChatOptions`

```java
var opts = (ToolCallingChatOptions) prompt.getOptions();
assertThat(opts.getToolCallbacks()).isEmpty();
```

Para o caminho oposto (variante negativa em #3), assertar `assertThat(opts.getToolCallbacks()).isNotEmpty()` e contém o callback do tool spy pelo `getToolDefinition().name()`.

### 5.4. Verificar `TurChatFlowState` pós-turn

```java
runTurn(agent, llm, history, conversationId, null);
TurChatFlowState state = stateRepository.findByConversationId(conversationId).orElseThrow();
assertThat(state.getCurrentNodeId()).isEqualTo("ai-objetivo");
Map<String,String> vars = JSON.readValue(state.getVariablesJson(), new TypeReference<>() {});
assertThat(vars).containsEntry("cargo_atual", "Gerente sênior");
```

Para asserts de SSE (chips suppression #8), coletar a lista de `ChatResponse` do Flux:

```java
List<ChatResponse> emitted = executor.execute(...).collectList().block(Duration.ofSeconds(5));
long optionEvents = emitted.stream().filter(r -> "options".equals(r.type())).count();
assertThat(optionEvents).isZero();
```

## 6. Riscos e bloqueios

1. **`TurLlmModelFactory` enrola o `ChatModel` em `TurResilientChatModel`** ([linha 64](../../turing-app/src/main/java/com/viglet/turing/resilience/llm/TurLlmModelFactory.java#L64)) com retry + circuit breaker + observation. Substituir a factory inteira via `@Primary` bypassa esse wrapping — preserva o invariante "uma chamada do executor = uma chamada Mockito" sem retry mascarando. Verificar que a factory mockada não dispara `TurResilienceExecutor` em paralelo.
2. **Custom Tool callbacks Groovy executariam scripts reais** se o agent tiver custom-tools registrados no H2 snapshot (vide `seedCustomToolFromClasspath` em `ProgramaMatchEEFlowIT`). Risco: testes ficam dependentes do snapshot. Estratégia: ITs do harness criam agent FRESH no `@BeforeEach` SEM custom-tools anexados; quando precisar testar tool-stripping (#3, #4), anexar UM custom-tool mockado via repository + spy. O snapshot pode até existir mas o teste não consome a config dele.
3. **`TurChatMemoryService` pode persistir no vector store** quando configurado. Se o snapshot tiver um `TurStoreInstance` ativado, `recordTurn` tenta escrever em Lucene/Chroma. Mitigação: o `@TestConfiguration` substitui por NoOp via `@Primary`. Sanity check: rodar smoke test com snapshot presente E ausente.
4. **SSE Flux assíncrono.** `executor.execute(...)` retorna `Flux<ChatResponse>` que faz `.subscribeOn(Schedulers.boundedElastic())`. Asserts precisam `.collectList().block(Duration.ofSeconds(5))`. Atenção: timeout muito curto em CI lento pode flake; 5s é seguro para chamadas mockadas (que retornam síncrono).
5. **`@SpringBootTest` context startup ~30s.** Cada IT class re-inicia se o `@TestConfiguration` ou `@Import` diferir. Mitigação: TODOS os 3 IT classes usam EXATAMENTE o mesmo `@Import(TurAgentChatExecutorTestSupport.class)` — Spring Boot reusa context entre elas. Confirmar com `--info` que só há 1 context init no logs.
6. **`ProgramaMatchEEFlowIT` coexiste com OpenAI real e usa `@EnabledIfEnvironmentVariable`.** Os novos ITs do harness NÃO usam essa anotação (rodam sempre). Os ITs do harness e o IT real precisam de strategies de import distintos: o IT real importa SEM TestConfiguration; os novos importam COM. Garantir que `@TestConfiguration` está em pacote DIFERENTE de `customers/education/flow/` para evitar auto-discovery acidental.
7. **`@SpyBean` em `TurAgentPersonaResolver` (necessário para #1 e #2).** Spring Boot 4 mantém `@SpyBean` mas com CGLIB; service final ou com `final` em métodos pode falhar. Verificar que o resolver NÃO tem métodos final. Backup: `@MockBean` que delega manualmente para o real (verbose mas funciona).
8. **OutputCaptureExtension para #18** (capturar log "fallback"): a extensão é de `spring-boot-test`. Confirmar import: `import org.springframework.boot.test.system.OutputCaptureExtension;` e `@ExtendWith(OutputCaptureExtension.class)`. Se não funcionar, usar `LogCaptor` (lib externa) ou Mockito spy do `Logger` do `TurChatFlowEngineService` via reflection. Backup low-tech: marcar o assert do log como soft (TODO comment).
9. **Reflection no `strategyByMethod` (Map privada do engine service para #18)** acopla teste ao internal. Risco baixo (o teste documenta o invariante "se método não estiver no map, fallback") mas se o refactor passar a usar registry-style com `Optional<TurChatFlowGuardrailStrategy>`, o setup do teste muda. Documentar isso na "Nota refactor" do esqueleto — já está.
10. **Patch #15 (refresh do state) é o mais difícil de simular.** O fluxo "tool escreve slot durante chatModel.call → engine.advance refreshes do DB" exige interceptar o Mockito `chatModel.call` para, no answer, fazer um write no `stateRepository` em transação separada — o que requer `@Transactional(propagation = REQUIRES_NEW)` ou similar para o write ser visível dentro do mesmo turno. **Recomendação: começar marcando #15 como TODO se a complexidade do setup passar de 30 min.** Cobertura indireta já existe via `findByIdNoCache` no engine service.

## 7. Critério de pronto do plano

- Todos os 15 `@Disabled` removidos dos 3 arquivos.
- `mvn verify -pl turing-app -Dskip.npm=true` passa localmente sem env var `OPENAI_API_KEY`.
- 0 referências a `fail("Not implemented...")` nos 3 arquivos `TurAgentChatExecutor*IT.java`.
- Asserts capturam EXATAMENTE os invariantes descritos nos Javadocs dos esqueletos — não os relaxam para passar. Se um assert se mostrar fragil/flaky, ajustar o Javadoc PRIMEIRO (e justificar no inventário) antes de relaxar o assert.
- Após o refactor de inversão de ordem, a aplicação de cada "Nota refactor" se materializa como mudança nos asserts do teste (mudança real de código), não como apagar o `@Disabled` de volta.
- 3 (ou 4) fixtures novas em `turing-app/src/test/resources/harness-it/` exercitadas por pelo menos 1 IT cada.
- 1 `@TestConfiguration` + 1 base class compartilhados, ambos package-private fora de `customers/education/flow/`.

## 8. O que este plano NÃO cobre

- Implementação do refactor "advance → 1× LLM call → publish" em si — escopo separado, conduzido após os 15 testes implementados servirem de rede de segurança.
- Reescrita de [`ProgramaMatchEEFlowIT`](../../turing-app/src/test/java/com/viglet/turing/customers/education/flow/ProgramaMatchEEFlowIT.java) — fica como está. Teste end-to-end com OpenAI real, conteúdo determinístico (temperature 0), valor preservado.
- Performance tuning dos novos ITs (boot do context já tem 30s; otimizar fica para depois).
- Mudanças em `LlmJudgeGuardrailStrategy` / `HeuristicGuardrailStrategy` / `StructuredOutput*` — refactor da camada de strategies cobre essas separadamente.
- Migração Spring AI 2.0.0-M2 → 2.x quando lançar — testes podem precisar ajustes nos mocks de `ChatResponse.builder()` se a API mudar.
- ITs novas para os 6 comportamentos do §8.5 do inventário (trigger contract, persona enum guard, cascade ascent, slot extract no-LLM, BR mod-11, interpolation) — já cobertos em outros ITs, registrado em §3 Fase 4.
- Patches **#6, #7, #16** da categoria (A) — explicitamente fora do escopo dos 15 esqueletos por instrução do header de `TurAgentChatExecutorRaceIT.java` ("Patches (A) restantes com cobertura parcial ou indireta — #6, #7, #16 — permanecem no inventário"). #7 em particular é o patch CANÔNICO (regen full path) — qualquer dos 10 testes (A) já implementados quebra implicitamente se o regen sumir, então cobertura indireta é forte.
