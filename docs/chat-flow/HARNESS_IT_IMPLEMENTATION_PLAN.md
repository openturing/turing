# Plano de Implementação — ITs do Execution Harness (v2)

> Substitui o plano v1 (eixo de divisão errado: por categoria A/B/C do inventário,
> que só captura wiring e deixa o comportamento do gpt-4o-mini como ponto cego).
> **Princípio diretor: previsibilidade sem surpresa.** Cada patch crítico tem dois
> ângulos de regressão — wiring estrutural + resultado contra LLM real — cobertos
> por testes pareados que se referenciam mutuamente.

Arquivos-alvo (novos) sob `turing-app/src/test/java/com/viglet/turing/genai/`:

- [`TurAgentChatExecutorStructuralIT.java`](../../turing-app/src/test/java/com/viglet/turing/genai/TurAgentChatExecutorStructuralIT.java) — wiring (mock)
- [`TurAgentChatExecutorBehavioralIT.java`](../../turing-app/src/test/java/com/viglet/turing/genai/TurAgentChatExecutorBehavioralIT.java) — resultado contra LLM real
- [`TurAgentChatExecutorContractIT.java`](../../turing-app/src/test/java/com/viglet/turing/genai/TurAgentChatExecutorContractIT.java) — middleware (mock)

Os 3 arquivos atuais ([`...RaceIT.java`](../../turing-app/src/test/java/com/viglet/turing/genai/TurAgentChatExecutorRaceIT.java),
[`...DeclarativeIT.java`](../../turing-app/src/test/java/com/viglet/turing/genai/TurAgentChatExecutorDeclarativeIT.java),
[`...MiddlewareIT.java`](../../turing-app/src/test/java/com/viglet/turing/genai/TurAgentChatExecutorMiddlewareIT.java))
serão **migrados e deletados** na Fase 0.5.

Referências de patches: [`HARNESS_INVENTORY.md`](HARNESS_INVENTORY.md) §3 e §8.

---

## Decisões-chave

Antes de mergulhar no plano: quatro decisões que valem ter explícitas. Cada uma
se desenvolve em sua seção; este sumário existe pra que um leitor não precise
montar o quadro a partir de §3.4 + risco #11 + tabela §6.

1. **Patch #15 cobre via spy de repositório, não via Fixture 4.** Simular um
   Custom Tool Groovy escrevendo slot durante `chatModel.call` é mais ruído de
   setup que sinal de invariante. Caminho preferido: `@SpyBean
   TurChatFlowStateRepository` + callback Mockito que dispara a escrita no
   momento certo. Fixture 4 vira **condicional** — só criar se o spy não cobrir
   o cenário. Detalhes em §3.4 (Fixture 4), risco #11, tabela §6 linha 20.

2. **Pinning explícito do `gpt-4o-mini` nos Javadocs Behavioral.** O eixo
   comportamental depende de um modelo específico (decisão tomada porque
   `gpt-4o-mini` falha em produzir JSON consistente, descartando structured
   output como caminho de guardrail). Cada esqueleto Behavioral inclui o ID da
   versão do modelo no Javadoc (formato `Pinning: gpt-4o-mini @ 2026-05`). Quando
   o modelo for retirado ou silenciosamente atualizado, a quebra do teste é a
   documentação da migração — drift do modelo é **feature do teste**, não bug.
   Detalhes em risco #6 e §3.3.

3. **Migração dos 15 esqueletos existentes é Fase 0.5 explícita, não trabalho
   de bastidor.** Cada test method tem destino arquivo-por-arquivo em §5 Fase
   0.5. Os 3 arquivos antigos ([`...RaceIT`](../../turing-app/src/test/java/com/viglet/turing/genai/TurAgentChatExecutorRaceIT.java),
   [`...DeclarativeIT`](../../turing-app/src/test/java/com/viglet/turing/genai/TurAgentChatExecutorDeclarativeIT.java),
   [`...MiddlewareIT`](../../turing-app/src/test/java/com/viglet/turing/genai/TurAgentChatExecutorMiddlewareIT.java))
   são **deletados** após migração — sem coexistência dupla, sem confusão de
   "qual IT cobre o quê". Os Javadocs (que são o contrato) são preservados
   literalmente; só pacote, anotação e referência de inventário mudam.

4. **Anti-padrão de mock-tautológico documentado em §7.3.** Smell-check durante
   review: se um Structural test reduz a "se eu retornar X, vejo X no SSE",
   virou no-op e o par com Behavioral perdeu sentido. §7.3 lista os 4 sinais
   mais comuns; ler antes de habilitar qualquer esqueleto e antes de revisar
   PR que toque os 3 arquivos de teste novos.

---

## 1. Princípio: previsibilidade sem surpresa

O harness atual carrega 22 patches catalogados (`HARNESS_INVENTORY.md`). Cada
patch existe por um motivo, e esse motivo determina **que tipo de teste** consegue
travar a regressão. Há dois eixos ortogonais e a maioria dos patches críticos
toca os dois — cobrir só um deixa ponto cego onde regressão silenciosa passa
no CI verde e quebra em produção.

**Wiring (estrutural).** O harness *compõe o prompt certo*, *monta as
`ChatOptions` certas*, *persiste na ordem certa*, *chama o `personaResolver`
N vezes*. Esses invariantes são determinados pelo código do executor, não pelo
modelo. Mock do `ChatModel` é a ferramenta exata: enfileira respostas,
captura o `Prompt`, conta invocações. O LLM real não muda o assert — se o teste
estrutural passa, o wiring está certo. Roda em todo `mvn verify`, < 10 minutos,
sem env var.

**Resultado (comportamental).** Vários patches existem porque o gpt-4o-mini
respondia *de um certo jeito* a um certo prompt: anchoring no padrão antigo
(#10), tool call preemptivo antes do judge capturar coupon (#3), pergunta
incongruente com chips (#13), persona drift no regen (#1/#2). Verificar que
*esse comportamento* persiste depois do refactor exige rodar o modelo real
contra o flow real e ler a resposta. **Mock não consegue avaliar isso**: a
única coisa que o teste poderia afirmar é "se eu disser ao mock que retornou X,
o harness usa X" — tautologia. O comportamental usa OpenAI real
(`gpt-4o-mini`, temperature 0) — herda o padrão de [`ProgramaMatchEEFlowIT`](../../turing-app/src/test/java/com/viglet/turing/customers/education/flow/ProgramaMatchEEFlowIT.java)
e roda só com `-Pllm-it` (nightly / pre-release).

**Por que cobrir só um eixo deixa ponto cego.** Se cobrirmos só estrutural,
um patch que sumir silenciosamente do `aiInstruction` literal (#12) ainda
passa nos asserts ("`countMatches == 2`"), mas o gpt-4o-mini volta a reciclar
instrução antiga porque o pointer não basta. Se cobrirmos só comportamental,
quando o OpenAI muda o modelo (gpt-4o-mini → gpt-4o-mini-2026-09) e a resposta
muda de forma cosmética, o teste flake sem que tenha havido regressão real
de código. **Os dois pareados eliminam a ambiguidade**: regressão estrutural
quebra estrutural primeiro; mudança de modelo quebra só o comportamental e o
diagnóstico fica óbvio.

---

## 2. Arquitetura

### 2.1. Três arquivos de teste, eixo por tipo de assert

| Arquivo (sob `turing-app/src/test/java/com/viglet/turing/genai/`) | Tipo | Stub LLM | Profile Maven | Quando roda |
|---|---|---|---|---|
| [`TurAgentChatExecutorStructuralIT.java`](../../turing-app/src/test/java/com/viglet/turing/genai/TurAgentChatExecutorStructuralIT.java) | Wiring | Mock via `TurLlmModelFactory` substituído por `@TestConfiguration` `@Primary` | default | Toda PR / CI |
| [`TurAgentChatExecutorBehavioralIT.java`](../../turing-app/src/test/java/com/viglet/turing/genai/TurAgentChatExecutorBehavioralIT.java) | Resultado vs LLM real | gpt-4o-mini real, `@EnabledIfEnvironmentVariable("OPENAI_API_KEY")` | `-Pllm-it` | Nightly + pre-release |
| [`TurAgentChatExecutorContractIT.java`](../../turing-app/src/test/java/com/viglet/turing/genai/TurAgentChatExecutorContractIT.java) | Middleware (cycle cap, A/B window, abandon, agent-owner guard) | Mock | default | Toda PR / CI |

**Eixo de divisão.** Não por categoria do patch (A/B/C). Por *que tipo de
assert quebra primeiro*. Patches A (race) podem ter ambos; patches B
(declarative) costumam ter ambos quando envolvem tool stripping observável
(LLM real evita chamar tool removida); patches C (middleware) ficam quase
sempre só no Contract.

### 2.2. Pareamento patch → testes

Patches **críticos** ganham par estrutural + comportamental. Os Javadocs dos
dois esqueletos referenciam-se mutuamente ("par estrutural de `xxx` em
`BehavioralIT`") — drift dos dois fica documentado.

| Patch | Categoria | Structural IT (asserção) | Behavioral IT (asserção contra gpt-4o-mini real) | Fixture |
|---|---|---|---|---|
| #1 — persona resolve pós-flow | A | `resolver` chamado ≥ 2×; 1º prompt traz a persona pós-walk | resposta tem voz/marca da persona pós-walk (Lucas), não da default (Marina) | persona-switch-mid-flow |
| #2 — persona resolve no regen | A | `resolver` chamado 3×; 2º prompt traz persona pós-walk | 2º turn produz resposta na voz pós-walk após cruzar `persona-*` | persona-switch-mid-flow |
| #3 — tool stripping initial | B | `((ToolCallingChatOptions) prompt.options).toolCallbacks` vazio quando nó marca proibição | gpt-4o-mini real NÃO chama `compor_proposta_in_company` no turno de captura (lê `tool_calls` no response metadata) | tool-free-node |
| #4 — tool stripping regen | B | idem #3 no 2º call | idem #3 no 2º call | tool-free-node |
| #7 — regen full path | A | `chatModel.call()` invocado 2×; 2º prompt tem addendum do nó pós-advance | pergunta gerada é do nó B (regex `(?i).*objetivo.*\?`), não do A | programa-match-ee |
| #10 — OVERRIDE bilíngue | A | substrings literais `INSTRUÇÃO DE PRIORIDADE MÁXIMA` + `HIGHEST-PRIORITY OVERRIDE` no system text do regen | com OVERRIDE: pergunta do nó B; sem OVERRIDE (mock-control): pergunta volta a recair no nó A — pinning empírico | programa-match-ee |
| #12 — aiInstruction literal inline | A | `countMatches(systemText, aiInstructionSnippet) == 2` no regen | resposta menciona o termo distintivo do `aiInstruction` (ex.: "daqui a 3 anos") | programa-match-ee |
| #13 — chips congruency | A | os 4 labels aparecem literais + frase "MUST be congruent" no system text | pergunta gerada admite TODAS as 4 chips como resposta natural (regex de OR + verb "papel/decisão/comprar") | regen-with-chips |

Patches **só estruturais** (LLM real não acrescenta sinal):

- #8 — `textAlignedWithCurrentNode` + supressão de chips
- #9 — regen omite next-step preview
- #11 — drop do `baseSystemPrompt` no regen
- #14 — trim de history para 3 turnos
- #15 — refresh do state pré-advance

Patches **só contrato / middleware** (`ContractIT`):

- #16 — save explícito após walk (cobertura indireta via #17 já existe; novo IT só do invariante double-save)
- #17 — cycle cap (já coberto por [`TurChatFlowSubFlowEngineIT`](../../turing-app/src/test/java/com/viglet/turing/genai/flow/TurChatFlowSubFlowEngineIT.java) — não duplicar)
- #18 — fallback HEURISTIC
- #19 — A/B window (já coberto por [`TurChatFlowAbVariantAssignmentTest`](../../turing-app/src/test/java/com/viglet/turing/genai/flow/TurChatFlowAbVariantAssignmentTest.java) — não duplicar)
- #20 — agent-owner guard
- #21 — judge retry / `isNonBlankRedirect` (já coberto por [`LlmJudgeGuardrailStrategyTest`](../../turing-app/src/test/java/com/viglet/turing/genai/flow/strategy/LlmJudgeGuardrailStrategyTest.java) — não duplicar)
- #22 — `onJudgeReject` declarativo (já coberto pelo mesmo unit-test — não duplicar)

Patches **fora deste escopo** ou já cobertos por indireta forte:

- #5 — `forbidsToolCalls` bilíngue: vira unit-test puro paramétrico (10 casos), entra no `StructuralIT` por coesão.
- #6 — `freshlyTriggered` skip: indireta forte via [`autoTriggerRouter_rejectsGreeting_picksFlowOnExplicitIntent`](../../turing-app/src/test/java/com/viglet/turing/customers/education/flow/ProgramaMatchEEFlowIT.java) — adicionar 1 esqueleto estrutural ("freshlyTriggered=true ⇒ advance NÃO foi invocado neste turno").

**Total de esqueletos esperado:**

- Structural: **12** (8 estruturais "puros" + 4 estruturais pareados com behavioral)
- Behavioral: **6** (#1, #3, #7, #10, #12, #13 — não #2 pois compartilha fixture com #1 e a assertion comportamental é equivalente)
- Contract: **4** (#16, #18, #20, e 1 do features implícitas §4)
- Features implícitas extras (§4): **3** (cascade ascent, persona enum guard, BR-doc mod-11 — promotion formal)

**Total: ~22 esqueletos novos + migração de 15 esqueletos existentes.**

### 2.3. Maven profile `-Pllm-it`

O `failsafe-plugin` já está configurado em [`turing-app/pom.xml:628-644`](../../turing-app/pom.xml#L628-L644)
com defaults para `*IT.java`. Vamos adicionar um profile que, por default,
**exclui** os ITs comportamentais (que precisam de OPENAI_API_KEY) e os
**inclui** quando `-Pllm-it` é passado:

```xml
<profiles>
  <profile>
    <id>llm-it</id>
    <build><plugins>
      <plugin>
        <artifactId>maven-failsafe-plugin</artifactId>
        <configuration>
          <includes>
            <include>**/*IT.java</include>
            <include>**/*BehavioralIT.java</include>
          </includes>
        </configuration>
      </plugin>
    </plugins></build>
  </profile>
</profiles>
```

**Default (sem profile):** `BehavioralIT` é pulado por causa do
`@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")` — não
precisa exclude explícito; é a mesma estratégia que `ProgramaMatchEEFlowIT` já
usa. O profile existe para tornar a execução *intencional* em CI nightly:
quando alguém roda `mvn verify -Pllm-it -DOPENAI_API_KEY=...`, fica explícito
que a intenção é exercitar o eixo comportamental.

`ProgramaMatchEEFlowIT` continua respondendo à variável de ambiente (sem
mudança) — o profile não muda o comportamento dele, só sinaliza intenção.

---

## 3. Infraestrutura compartilhada

### 3.1. `@TestConfiguration` para Structural/ContractIT (mock)

Localização: `turing-app/src/test/java/com/viglet/turing/genai/testsupport/TurAgentChatExecutorMockSupport.java`

Responsabilidades:

1. Substituir o bean `TurLlmModelFactory` por `@Bean @Primary` que retorna
   um `ChatModel` Mockito controlado. Bypass do `TurResilientChatModel`
   wrapper para preservar o invariante "1 chamada do executor = 1 chamada
   Mockito" sem retry mascarando.
2. Helper `mockNextChatCall(String)` enfileira respostas em ordem (initial,
   regen, ...). Implementado com `Mockito.when(...).thenReturn(first, rest)`.
3. `ArgumentCaptor<Prompt>` exposto via getter (`capturedPrompts()`) — usado
   para inspecionar 1º vs 2º prompt.
4. Spy opcional em `TurAgentPersonaResolver` para contar invocações (#1, #2).
5. NoOp para `TurChatAnalyticsService` e `TurChatMemoryService` (evita IO no
   H2 efêmero, deixa asserts limpos).

Assinaturas:

```java
@TestConfiguration
public class TurAgentChatExecutorMockSupport {
    public static class ChatModelHarness {
        ChatModel chatModel();                  // Mockito mock
        ArgumentCaptor<Prompt> promptCaptor();
        void queueResponse(String text);        // enqueue "ok"-style ChatResponse
        void queueToolCallResponse(/* … */);    // for #3/#4 sanity inverse
        List<Prompt> capturedPrompts();
        void reset();                           // @BeforeEach
    }
    @Bean ChatModelHarness chatModelHarness();
    @Bean @Primary TurLlmModelFactory mockLlmModelFactory(ChatModelHarness h);
    @Bean @Primary TurChatAnalyticsService noopChatAnalyticsService();
    @Bean @Primary TurChatMemoryService noopChatMemoryService();
}
```

Helper para construir `ChatResponse` de teste: `static ChatResponse okText(String)`
usando `ChatResponse.builder().generations(List.of(new Generation(new AssistantMessage(text))))`.

Helper para extrair system text:

```java
String systemTextOf(Prompt p) {
    return p.getInstructions().stream()
            .filter(m -> m instanceof SystemMessage)
            .map(Message::getText)
            .findFirst().orElseThrow();
}
```

### 3.2. Base class `AbstractAgentExecutorIT`

Estende [`AbstractTuringSpringIT`](../../turing-app/src/test/java/com/viglet/turing/testutil/AbstractTuringSpringIT.java)
(reuso de H2 efêmero por classe). Helpers:

- `TurAIAgent createAgent(String title, TurPersona defaultPersona)`
- `TurLLMInstance createMockableLlmInstance()` — provider type qualquer,
  API key encriptada placeholder (factory mockada ignora)
- `TurChatFlow importFlowFromClasspath(String resourcePath, TurAIAgent agent)`
  — reusa `EducationEEFixtures.importIntoAgent(...)` via `ChatFlowImportTestUtil`
- `TurChatFlowState seedStateAt(String conversationId, TurChatFlow flow, String nodeId, Map<String,String> vars)`
- `List<ChatResponse> runTurn(...)` — chama `executor.execute(...)` + `.collectList().block(Duration.ofSeconds(5))`
- `TurChatFlowState reloadState(String conversationId)`
- `Prompt firstPrompt()` / `Prompt regenPrompt()` — atalhos para `capturedPrompts().get(0/1)`

### 3.3. Padrão de BehavioralIT (real LLM)

Herdado de [`ProgramaMatchEEFlowIT`](../../turing-app/src/test/java/com/viglet/turing/customers/education/flow/ProgramaMatchEEFlowIT.java):

- `@SpringBootTest` + `@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")`
- `@BeforeAll buildChatModel()` constrói `OpenAiChatModel` com `gpt-4o-mini`,
  temperature 0
- Setup de `TurLLMInstance` apontando para o mesmo modelo
- Helper `assertResponseRelatesTo(String reply, String... synonyms)` — match
  laxo (regex case-insensitive de OR), nunca exact match

Como evitar asserts frágeis:

```java
// #10 — pergunta do nó B (objetivo), não do A (cargo):
assertThat(reply).matches("(?i).*(objetivo|onde\\s+você\\s+quer\\s+estar|carreira).*\\?");

// #1 — voz da persona Lucas (peer-tone), não Marina default:
assertThat(reply).containsAnyOf("Oi", "Olá", "Beleza");
assertThat(reply.toLowerCase()).doesNotContain("prezad"); // Marina formal

// #13 — pergunta admite TODAS as 4 chips como resposta natural:
String q = reply.toLowerCase();
assertThat(q).matches(".*(papel|decisão|comprar|atuação|área de atuação).*\\?");
// 4 chips = {RH/L&D, Gestor, C-level, Comprador} → pergunta sobre PAPEL DE DECISÃO
```

Estratégia para tolerância: cada `BehavioralIT` retry 0 (sem retry); se um
teste flake, ajustar o regex / synonyms PRIMEIRO antes de relaxar. O flake-rate
de gpt-4o-mini a temperature 0 com prompt determinístico é < 5% (medido no
inventário §3 #21); regex laxo absorve.

### 3.4. Fixtures novas necessárias

Releitura do plano v1 §2.3, validada. Fixtures sob
`turing-app/src/test/resources/harness-it/`:

**Fixture 1 — `harness-it/tool-free-node.chat-flow.json`** *(firme)*
Estrutura: `start → ai-coupon-code (aiInstruction contém "NÃO CHAME NENHUMA TOOL")
→ switch-cupom-ok → ai-deliver (aiInstruction contém "Chame compor_proposta_in_company")
→ end`. Persona única (Camila do in-company, copiada). O agent associado precisa
ter `compor_proposta_in_company` registrado como custom tool (stub que registra
invocações). Serve patches #3, #4, #5 (sentinela PT-BR upper) — tanto Structural
(asserta `toolCallbacks` vazio) quanto Behavioral (asserta tool não invocada via
metadata da response).

**Fixture 2 — `harness-it/persona-switch-mid-flow.chat-flow.json`** *(firme)*
Bundle com 1 flow + 2 personas (Marina + Lucas). Grafo: `start → persona-marina
→ ai-name → switch-route (decide por `__force_route` setada via `seedStateAt`)
→ persona-lucas → ai-objetivo → end`. 5 nós + 4 edges. Serve patches #1 e #2.
Programa-match-ee tem Marina/Lucas em flows SEPARADOS (A/B), não no MESMO flow
com switch — esta fixture força o walk transparente cruzando persona.

**Fixture 3 — `harness-it/regen-with-chips.chat-flow.json`** *(firme)*
Bundle com 1 flow + 1 persona. Grafo: `start → ai-cargo (aiInstruction
"Pergunte cargo") → switch-area → ai-decision-role (aiInstruction "Pergunte
papel na decisão", inlineOptions=["RH/L&D", "Gestor", "C-level", "Comprador"])
→ end`. 4 nós + 3 edges. Reproduz literalmente o cenário do patch #13.

**Fixture 4 — `harness-it/tool-write-during-call.chat-flow.json`** *(condicional)*
Estrutura: `start → ai-area (aiInstruction força chamar tool search_ee_programs)
→ ai-confirma → end`. Serve patch #15 (refresh do state pre-advance). Avaliar
custo de simular tool-call lifecycle do Spring AI — se o stub crescer > 50
linhas, pular esta fixture e cobrir #15 via spy de `TurChatFlowStateRepository`
+ callback Mockito no `chatModel.call` que escreve no state em transação
paralela. **Decisão preliminar: condicional; preferir spy.**

**Total: 3 fixtures firmes + 1 condicional.**

---

## 4. Features implícitas a promover (do inventário §8.5)

A §8.5 lista 6 comportamentos cobertos por ITs existentes mas não catalogados
como patches. Dos 6, **3 merecem promoção formal a esqueleto IT no novo arquivo**
para deixar o invariante visível no eixo "harness behaviour":

1. **Trigger contract pós-2026.2.x** — já coberto por
   [`ProgramaMatchEEFlowIT.autoTriggerRouter_rejectsGreeting_picksFlowOnExplicitIntent`](../../turing-app/src/test/java/com/viglet/turing/customers/education/flow/ProgramaMatchEEFlowIT.java#L245).
   Está sob `@EnabledIfEnvironmentVariable` e usa LLM real — duplicar como
   esqueleto Behavioral seria redundante. **Não adicionar esqueleto novo.**
2. **Import-bundle persona enum guard (`TurPersonaTone.EXECUTIVE`)** — já
   coberto por [`importBundle_createsPersonaSlotsAndFlowWithExpectedSettings`](../../turing-app/src/test/java/com/viglet/turing/customers/education/flow/ProgramaMatchEEFlowIT.java#L184).
   **Promover** para `StructuralIT` (não precisa LLM real): novo esqueleto
   `importBundle_rejectsInvalidPersonaTone_yields400WithDescriptiveMessage`.
3. **Cascade ascent (sub-flow end → parent end → submission atômica)** — já
   coberto por [`TurChatFlowSubFlowEngineIT.cascadeAscent_whenSubFlowEndsAndParentNextIsAlsoEnd_recordsRootSubmission`](../../turing-app/src/test/java/com/viglet/turing/genai/flow/TurChatFlowSubFlowEngineIT.java#L298).
   **Não duplicar**, mas adicionar 1 esqueleto Contract espelhando o invariante
   no nível do executor: `cascadeAscent_recordsSingleSubmissionEvenWhenMultipleEndsCascade`.
4. **Slot extraction sem LLM** — já coberto por
   [`ChatHandoffAndSlotExtractIT.slotExtract_agentWithoutLlmInstance_returnsEmptyResult`](../../turing-app/src/test/java/com/viglet/turing/genai/flow/ChatHandoffAndSlotExtractIT.java#L245). **Não duplicar.**
5. **BR-document mod-11 (CPF/CNPJ)** — já coberto por
   [`ChatFlowOpsTest`](../../turing-app/src/test/java/com/viglet/turing/genai/flow/strategy/ChatFlowOpsTest.java).
   **Promover** para `ContractIT` no eixo executor (não engine puro): novo
   esqueleto `rejectsInvalidCpfWithoutInvokingLlm_whenSlotTypeIsCpf`.
6. **Variable interpolation contract** — coberto em `ChatFlowOpsTest`. **Não
   duplicar.**

**Total adicional: +3 esqueletos** (1 Structural + 2 Contract).

---

## 5. Fases de implementação

### Fase 0 — Infraestrutura (0.5–1 dia)

1. Criar pacote `turing-app/src/test/java/com/viglet/turing/genai/testsupport/`
2. Implementar `TurAgentChatExecutorMockSupport` (§3.1) + `ChatModelHarness`
3. Implementar `AbstractAgentExecutorIT` (§3.2) com helpers
4. Extrair `ChatFlowImportTestUtil.importIntoAgent(...)` a partir de
   `ProgramaMatchEEFlowIT.importIntoAgent` para reuso (mantém método privado
   delegando para o util)
5. Adicionar profile `llm-it` ao [`turing-app/pom.xml`](../../turing-app/pom.xml)
   (§2.3)
6. Smoke test interno (deletar antes de fechar a fase):
   `_smokeContextLoadsAndChatModelMockReceivesCall` — verifica `harness.capturedPrompts()` tamanho 1

**Critério de pronto:** smoke verde + `mvn verify -pl turing-app -Dskip.npm=true`
sem regressão nos ITs existentes (`@Primary` overrides só pegam quando
`@TestConfiguration` é explicitamente `@Import`-ado).

### Fase 0.5 — Migração dos 15 esqueletos existentes (0.5 dia)

Os Javadocs dos esqueletos atuais são o contrato real — **preservar literalmente**.
Apenas ajustar `package`, anotação de classe (`@Import(TurAgentChatExecutorMockSupport.class)`
nos novos arquivos), e renomear conforme necessário.

**De [`TurAgentChatExecutorRaceIT.java`](../../turing-app/src/test/java/com/viglet/turing/genai/TurAgentChatExecutorRaceIT.java) (10 testes) →**

| Test method existente | Destino | Justificativa |
|---|---|---|
| `resolvesPersonaAfterFlowSelection_picksWalkWrittenPersonaForInitialCall` (#1) | StructuralIT | wiring: contagem de `personaResolver.resolve()` invocations |
| `reResolvesPersonaInsideRegen_whenAdvanceCrossedPersonaNode` (#2) | StructuralIT | idem: 3× invocations |
| `suppressesChipsWhenAssistantOverrideDoesNotMatchPostAdvanceNode` (#8) | StructuralIT | SSE event count — wiring |
| `omitsNextStepPreviewHintInRegenAddendum` (#9) | StructuralIT | substring presence/absence — wiring |
| `appendsBilingualHighestPriorityOverrideOnRegen` (#10) | StructuralIT | substring literal — wiring |
| `dropsBaseSystemPromptInRegenToAvoidOrchestrationConflict` (#11) | StructuralIT | substring absence — wiring |
| `inlinesAiInstructionLiteralInsideOverrideBlock` (#12) | StructuralIT | `countMatches == 2` — wiring |
| `inlinesChipLabelsAndCongruencyImperativeInOverrideWhenDestNodeHasInlineOptions` (#13) | StructuralIT | substring literal — wiring |
| `trimsHistoryToBudgetedRecentTurnsInRegenCall` (#14) | StructuralIT | `UserMessage` count — wiring |
| `refreshesStateFromDbBeforeStrategyAdvance_preservingSlotWritesFromToolCallbacks` (#15) | StructuralIT | spy contagem de `findById` — wiring |

**De [`TurAgentChatExecutorDeclarativeIT.java`](../../turing-app/src/test/java/com/viglet/turing/genai/TurAgentChatExecutorDeclarativeIT.java) (3 testes) →**

| Test method existente | Destino | Justificativa |
|---|---|---|
| `stripsToolsOnInitialCallWhenActiveNodeForbidsToolCalls` (#3) | StructuralIT | `toolCallbacks.isEmpty()` — wiring |
| `stripsToolsOnRegenCallWhenPostAdvanceNodeForbidsToolCalls` (#4) | StructuralIT | idem no 2º call |
| `forbidsToolCalls_detectsBilingualSentinelsCaseInsensitivelyInFreeText` (#5) | StructuralIT | unit-test paramétrico, marcar como `@Nested` |

**De [`TurAgentChatExecutorMiddlewareIT.java`](../../turing-app/src/test/java/com/viglet/turing/genai/TurAgentChatExecutorMiddlewareIT.java) (2 testes) →**

| Test method existente | Destino | Justificativa |
|---|---|---|
| `fallsBackToHeuristicStrategyWhenGuardrailMethodHasNoRegisteredBean` (#18) | ContractIT | middleware C |
| `discardsForcedFlowWhenItDoesNotBelongToCallingAgent` (#20) | ContractIT | middleware C (defense-in-depth) |

**Após migração:** **deletar** os 3 arquivos antigos. Atualizar referências
"§8.4" nos Javadocs migrados para apontar para `HARNESS_INVENTORY.md` §3
(número do patch direto, sem categoria).

### Fase 1 — Esqueletos Structural sem fixture nova (1 dia)

Esqueletos que rodam apenas com `programa-match-ee.chat-flow.json` (já existente):

1. `appendsBilingualHighestPriorityOverrideOnRegen` (#10) — migrado da Fase 0.5
2. `dropsBaseSystemPromptInRegenToAvoidOrchestrationConflict` (#11)
3. `omitsNextStepPreviewHintInRegenAddendum` (#9)
4. `inlinesAiInstructionLiteralInsideOverrideBlock` (#12)
5. `trimsHistoryToBudgetedRecentTurnsInRegenCall` (#14)
6. `resolvesPersonaAfterFlowSelection_picksWalkWrittenPersonaForInitialCall` (#1)
7. `regenFullPath_invokesChatModelTwiceWhenAdvanceMovedCursor` (#7) — **NOVO**
8. `freshlyTriggered_skipsEngineAdvance` (#6) — **NOVO**

**Critério de pronto:** 8 `@Disabled` removidos, asserts implementados,
`mvn verify -pl turing-app -Dskip.npm=true` verde.

### Fase 2 — Esqueletos Contract (0.5 dia)

9. `fallsBackToHeuristicStrategyWhenGuardrailMethodHasNoRegisteredBean` (#18)
10. `discardsForcedFlowWhenItDoesNotBelongToCallingAgent` (#20)
11. `doubleSaveAroundTransparentWalk_persistsFinalCursor` (#16) — **NOVO**
12. `cascadeAscent_recordsSingleSubmissionEvenWhenMultipleEndsCascade` (features §4)
13. `rejectsInvalidCpfWithoutInvokingLlm_whenSlotTypeIsCpf` (features §4)

### Fase 3 — Criação das fixtures novas (0.5 dia)

Para cada fixture firme (§3.4):

1. `harness-it/tool-free-node.chat-flow.json` (#3/#4/#5)
2. `harness-it/persona-switch-mid-flow.chat-flow.json` (#1/#2)
3. `harness-it/regen-with-chips.chat-flow.json` (#13)

Validação: 1 `@Test` "smoke" por fixture que carrega via `chatFlowImportTestUtil`,
chama `engine.parseGraph(...)` e loga o grafo. Falha rápida se shape do JSON
tiver typo.

### Fase 4 — Esqueletos Structural com fixture nova (1 dia)

14. `stripsToolsOnInitialCallWhenActiveNodeForbidsToolCalls` (#3) — Fixture 1
15. `stripsToolsOnRegenCallWhenPostAdvanceNodeForbidsToolCalls` (#4) — Fixture 1
16. `forbidsToolCalls_detectsBilingualSentinelsCaseInsensitivelyInFreeText` (#5) — `@Nested`, unit
17. `reResolvesPersonaInsideRegen_whenAdvanceCrossedPersonaNode` (#2) — Fixture 2
18. `inlinesChipLabelsAndCongruencyImperativeInOverrideWhenDestNodeHasInlineOptions` (#13) — Fixture 3
19. `suppressesChipsWhenAssistantOverrideDoesNotMatchPostAdvanceNode` (#8) — Fixture 3
20. `refreshesStateFromDbBeforeStrategyAdvance_preservingSlotWritesFromToolCallbacks` (#15) — spy de repo (Fixture 4 condicional)
21. `importBundle_rejectsInvalidPersonaTone_yields400WithDescriptiveMessage` (features §4)

### Fase 5 — Esqueletos Behavioral (1–1.5 dias)

Todos no `TurAgentChatExecutorBehavioralIT.java`. Setup `@BeforeAll buildChatModel()`
herdado de `ProgramaMatchEEFlowIT`:

22. `personaPostWalk_realLlmRepliesInLucasVoice` (#1 — par estrutural de
    `resolvesPersonaAfterFlowSelection_*`)
23. `toolStripped_realLlmDoesNotInvokeRemovedTool_capturesCouponCleanly` (#3 — par estrutural de
    `stripsToolsOnInitialCall_*`)
24. `regenFullPath_realLlmAsksQuestionOfPostAdvanceNode_notPriorNode` (#7 — par estrutural de
    `regenFullPath_invokesChatModelTwice_*`)
25. `overrideBlock_realLlmFollowsCurrentStep_doesNotAnchorOnHistoryPattern` (#10 — par estrutural de
    `appendsBilingualHighestPriorityOverride_*`)
26. `aiInstructionLiteral_realLlmAsksQuestionWithDistinctiveTerm` (#12 — par estrutural de
    `inlinesAiInstructionLiteral_*`)
27. `chipsCongruency_realLlmGeneratesQuestionWhereAllChipsAreValidAnswers` (#13 — par estrutural de
    `inlinesChipLabelsAndCongruency_*`)

**Critério de pronto:** `mvn verify -Pllm-it -DOPENAI_API_KEY=...` verde local
em < 3 minutos; cada esqueleto Javadoc referencia o par estrutural por nome.

### Fase 6 — Limpeza e fechamento (0.5 dia)

- Remover comentários `TODO` deixados pelos esqueletos
- Atualizar referências `§8.4` → `§3 #N` nos Javadocs
- Adicionar uma linha em `HARNESS_INVENTORY.md` §8.3 indicando que pareamento
  estrutural/comportamental existe (follow-up no inventário, fora deste plano)

**Estimativa total: 4.5–6 dias úteis.**

---

## 6. Tabela mestra de implementação

Ordem por fase. **22 linhas, total esperado.**

| # | Fase | Arquivo | Test method | Patch | Tipo | Fixture | Mocks/setup chave | Asserts chave |
|---|---|---|---|---|---|---|---|---|
| 1 | 1 | Structural | `appendsBilingualHighestPriorityOverrideOnRegen` | #10 | wiring | programa-match-ee | LlmModelFactory→mock; 2× `okText` | `systemTextOf(regenPrompt()).contains("INSTRUÇÃO DE PRIORIDADE MÁXIMA")` E `"HIGHEST-PRIORITY OVERRIDE"`; firstPrompt NÃO contém *(par comportamental: #25)* |
| 2 | 1 | Structural | `dropsBaseSystemPromptInRegenToAvoidOrchestrationConflict` | #11 | wiring | programa-match-ee + `agent.systemPrompt="ROUTER LLM ..."` | LlmModelFactory→mock; 2× `okText` | firstPrompt contém "ROUTER LLM"; regenPrompt NÃO contém |
| 3 | 1 | Structural | `omitsNextStepPreviewHintInRegenAddendum` | #9 | wiring | programa-match-ee | LlmModelFactory→mock; 2× `okText` | firstPrompt contém aiInstruction de `ai-objetivo`; regenPrompt NÃO contém aiInstruction de `ai-area` |
| 4 | 1 | Structural | `inlinesAiInstructionLiteralInsideOverrideBlock` | #12 | wiring | programa-match-ee | LlmModelFactory→mock; 2× `okText` | `StringUtils.countMatches(systemTextOf(regenPrompt()), "daqui a 3 anos") == 2` *(par comportamental: #26)* |
| 5 | 1 | Structural | `trimsHistoryToBudgetedRecentTurnsInRegenCall` | #14 | wiring | programa-match-ee + 5 user turns | LlmModelFactory→mock; 2× `okText` | firstPrompt UserMessage count ≥ 6; regenPrompt ≤ 3; última == atual |
| 6 | 1 | Structural | `resolvesPersonaAfterFlowSelection_picksWalkWrittenPersonaForInitialCall` | #1 | wiring | programa-match-ee | LlmModelFactory→mock; `@SpyBean TurAgentPersonaResolver` | `verify(resolver, atLeast(2)).resolve(...)` *(par comportamental: #22)* |
| 7 | 1 | Structural | `regenFullPath_invokesChatModelTwiceWhenAdvanceMovedCursor` | #7 | wiring | programa-match-ee | LlmModelFactory→mock; 2× `okText` | `verify(chatModel, times(2)).call(any())`; regenPrompt addendum tem nó pós-advance *(par comportamental: #24)* |
| 8 | 1 | Structural | `freshlyTriggered_skipsEngineAdvance` | #6 | wiring | programa-match-ee | `@SpyBean TurChatFlowEngineService` | `verify(engine, never()).advance(any(), any(), any(), any())` no trigger turn |
| 9 | 2 | Contract | `fallsBackToHeuristicStrategyWhenGuardrailMethodHasNoRegisteredBean` | #18 | middleware | programa-match-ee | reflection remove `LLM_JUDGE` de `strategyByMethod`; `OutputCaptureExtension` | sem exception; `Heuristic.advance(...)` 1×; log contém "fallback" |
| 10 | 2 | Contract | `discardsForcedFlowWhenItDoesNotBelongToCallingAgent` | #20 | middleware | programa-match-ee × 2 agents | LlmModelFactory→mock; spy `engine.selectActiveFlow` | flow ativo ≠ flow de A1; selectActiveFlow(A2,...) ≥ 1×; sanity positivo |
| 11 | 2 | Contract | `doubleSaveAroundTransparentWalk_persistsFinalCursor` | #16 | middleware | programa-match-ee | `@SpyBean TurChatFlowStateRepository` | `InOrder` valida `save→walk→save`; estado final em nó pós-walk |
| 12 | 2 | Contract | `cascadeAscent_recordsSingleSubmissionEvenWhenMultipleEndsCascade` | §4 | feature impl. | sub-flow fixture | spy `submission` repo | exactly 1 submission gravada |
| 13 | 2 | Contract | `rejectsInvalidCpfWithoutInvokingLlm_whenSlotTypeIsCpf` | §4 | feature impl. | programa-match-ee + slot CPF | LlmModelFactory→mock | `verify(chatModel, never()).call(any())`; reject-message → user |
| 14 | 4 | Structural | `stripsToolsOnInitialCallWhenActiveNodeForbidsToolCalls` | #3 | wiring | tool-free-node | LlmModelFactory→mock; spy `compor_proposta_in_company` | `((ToolCallingChatOptions) firstPrompt.getOptions()).getToolCallbacks()` vazio; tool nunca invocada; sanity negativo *(par comportamental: #23)* |
| 15 | 4 | Structural | `stripsToolsOnRegenCallWhenPostAdvanceNodeForbidsToolCalls` | #4 | wiring | tool-free-node | idem; 2× `okText` | regenPrompt toolCallbacks vazio; tool nunca invocada |
| 16 | 4 | Structural (`@Nested`) | `forbidsToolCalls_detectsBilingualSentinelsCaseInsensitivelyInFreeText` | #5 | unit | — | — | 10 casos `@ParameterizedTest` (null/empty/PT-upper/PT-lower/PT-no-accent/EN-mixed/EN-short/embedded/no-sentinel/"tool" sem sentinela) |
| 17 | 4 | Structural | `reResolvesPersonaInsideRegen_whenAdvanceCrossedPersonaNode` | #2 | wiring | persona-switch-mid-flow | LlmModelFactory→mock; 2× `okText`; SpyBean resolver | `verify(resolver, times(3)).resolve(...)`; regenPrompt contém Lucas systemInstruction |
| 18 | 4 | Structural | `inlinesChipLabelsAndCongruencyImperativeInOverrideWhenDestNodeHasInlineOptions` | #13 | wiring | regen-with-chips | LlmModelFactory→mock; 2× `okText` | regenPrompt contém os 4 labels literais + `"MUST be congruent"`; sanity negativo *(par comportamental: #27)* |
| 19 | 4 | Structural | `suppressesChipsWhenAssistantOverrideDoesNotMatchPostAdvanceNode` | #8 | wiring | regen-with-chips OU programa-match | Strategy mock retornando `assistantOverride != null` | SSE: 0 events com `type="options"` quando override+advance; turno seguinte > 0 |
| 20 | 4 | Structural | `refreshesStateFromDbBeforeStrategyAdvance_preservingSlotWritesFromToolCallbacks` | #15 | wiring | programa-match-ee + repo spy | SpyBean `TurChatFlowStateRepository`; callback Mockito no `chatModel.call` que escreve slot | pós-turn: slot escrito sobrevive; `findById(stateId)` ≥ 1× dentro de advance |
| 21 | 4 | Structural | `importBundle_rejectsInvalidPersonaTone_yields400WithDescriptiveMessage` | §4 | feature impl. | bundle JSON inválido inline | — | HTTP 400; mensagem cita "tone" + enum válidos |
| 22 | 5 | Behavioral | `personaPostWalk_realLlmRepliesInLucasVoice` | #1 | comportamental | persona-switch-mid-flow | OpenAI real gpt-4o-mini | `reply.containsAnyOf("Oi","Olá","Beleza")` + `!contains("prezad")` *(par estrutural: #6)* |
| 23 | 5 | Behavioral | `toolStripped_realLlmDoesNotInvokeRemovedTool_capturesCouponCleanly` | #3 | comportamental | tool-free-node | OpenAI real | `response.metadata.toolCalls.isEmpty()`; slot `cupom` capturado *(par estrutural: #14)* |
| 24 | 5 | Behavioral | `regenFullPath_realLlmAsksQuestionOfPostAdvanceNode_notPriorNode` | #7 | comportamental | programa-match-ee | OpenAI real | `reply.matches("(?i).*(objetivo\|onde\\s+você\\s+quer\\s+estar).*\\?")` *(par estrutural: #7)* |
| 25 | 5 | Behavioral | `overrideBlock_realLlmFollowsCurrentStep_doesNotAnchorOnHistoryPattern` | #10 | comportamental | programa-match-ee | OpenAI real | mesma assertion de #24 *(par estrutural: #1)* |
| 26 | 5 | Behavioral | `aiInstructionLiteral_realLlmAsksQuestionWithDistinctiveTerm` | #12 | comportamental | programa-match-ee | OpenAI real | `reply.toLowerCase().contains("3 anos")` OU `"daqui a"` *(par estrutural: #4)* |
| 27 | 5 | Behavioral | `chipsCongruency_realLlmGeneratesQuestionWhereAllChipsAreValidAnswers` | #13 | comportamental | regen-with-chips | OpenAI real | `reply.toLowerCase().matches(".*(papel\|decisão\|atuação).*\\?")` *(par estrutural: #18)* |

---

## 7. Padrões de assert

### 7.1. Structural — exemplos canônicos

**Capturar Prompt e inspecionar SystemMessage:**

```java
List<Prompt> prompts = harness.capturedPrompts();
String firstSys = systemTextOf(prompts.get(0));
String regenSys = systemTextOf(prompts.get(1));
assertThat(regenSys).contains("INSTRUÇÃO DE PRIORIDADE MÁXIMA");
assertThat(firstSys).doesNotContain("HIGHEST-PRIORITY OVERRIDE");
```

**Inspecionar `ToolCallingChatOptions.getToolCallbacks()`:**

```java
ToolCallingChatOptions opts = (ToolCallingChatOptions) firstPrompt().getOptions();
assertThat(opts.getToolCallbacks()).isEmpty();
verify(composerToolSpy, never()).call(any());
```

**Contar invocações em spy de `TurAgentPersonaResolver`:**

```java
verify(personaResolver, atLeast(2)).resolve(eq(agent), eq(conversationId));
```

**Ler `TurChatFlowState` pós-turn:**

```java
TurChatFlowState state = stateRepository.findByConversationId(conv).orElseThrow();
assertThat(state.getCurrentNodeId()).isEqualTo("ai-objetivo");
Map<String,String> vars = JSON.readValue(state.getVariablesJson(), new TypeReference<>() {});
assertThat(vars).containsEntry("cargo_atual", "Gerente sênior");
```

**Verificar ordem com `InOrder`:**

```java
InOrder inOrder = inOrder(stateRepository, engine);
inOrder.verify(stateRepository).save(any());
inOrder.verify(engine).walkTransparentNodes(any(), any(), any());
inOrder.verify(stateRepository).save(any());
```

### 7.2. Behavioral — exemplos canônicos

**Regex laxo, nunca exact match:**

```java
String reply = lastTokenEventContent(emitted);
assertThat(reply).matches("(?i).*(objetivo|onde\\s+você\\s+quer\\s+estar|carreira).*\\?");
```

**"Contains any of" para tolerância de paráfrase:**

```java
assertThat(reply).containsAnyOf("Oi", "Olá", "Beleza");
assertThat(reply.toLowerCase()).doesNotContain("prezad"); // Marina formal
```

**Verificar `tool_calls` no response metadata (sem usar ChatResponse mockado):**

```java
List<ChatResponse> emitted = runTurnReal(agent, llm, history, conv, null);
// real LLM emits tokens; if it called a tool, an extra event type="tool_call" exists
long toolEvents = emitted.stream().filter(r -> "tool_call".equals(r.type())).count();
assertThat(toolEvents).as("tool was stripped — should not have been called").isZero();
```

**Tolerância: sem retry automático.** Se um BehavioralIT flake, ajustar o
regex / synonyms PRIMEIRO. Re-rodar 1× manualmente para confirmar não-flake;
nunca colocar retry loop no teste.

### 7.3. Anti-padrão: quando NÃO mockar

Smells que indicam um Structural test virou tautológico:

- O assert é "o texto que mockei aparece no Flux" → no-op
- O assert é "ChatModel foi chamado" sem inspecionar `Prompt` capturado → no-op
- O assert ignora o `ArgumentCaptor` → estrutura provavelmente errada
- O assert é "se eu retornar resposta X, vejo X no SSE" → tautologia, virar
  Behavioral ou eliminar

---

## 8. Riscos e bloqueios

Riscos reusados do plano v1:

1. **`TurLlmModelFactory` enrola o `ChatModel` em `TurResilientChatModel`**
   ([linha 64](../../turing-app/src/main/java/com/viglet/turing/resilience/llm/TurLlmModelFactory.java#L64))
   com retry + circuit breaker + observation. Substituir a factory inteira via
   `@Primary` bypassa esse wrapping — confirma o invariante "1 chamada do
   executor = 1 chamada Mockito" sem retry mascarando.
2. **Custom Tool callbacks Groovy executariam scripts reais** se o agent tiver
   custom-tools registrados no H2 snapshot. Mitigação: ITs do harness criam
   agent FRESH no `@BeforeEach` SEM custom-tools, exceto onde explicitamente
   necessário (Fixture 1).
3. **`TurChatMemoryService` pode persistir no vector store** quando configurado.
   Mitigação: substituído por NoOp via `@Primary` na `MockSupport` config.
4. **`@SpringBootTest` context startup ~30s.** Todos os 3 IT classes usam
   EXATAMENTE o mesmo `@Import(TurAgentChatExecutorMockSupport.class)` para
   Spring reusar context entre eles. BehavioralIT herda de `AbstractTuringSpringIT`
   sem o mock support — context separado de ~30s adicional (aceitável,
   roda só com profile).
5. **`@SpyBean TurAgentPersonaResolver`** em Spring Boot 4 + Java 21 com
   CGLIB: confirmar resolver não tem métodos `final`. Backup: `@MockBean` +
   delegação manual.

Riscos novos da reescrita:

6. **Behavioral IT depende de gpt-4o-mini estável.** Modelo pode mudar
   (deprecation, novo training cut) e quebrar testes. Tratar como **feature,
   não bug**: cada commit de Behavioral inclui o ID do modelo no Javadoc
   ("Pinning: gpt-4o-mini @ 2026-05"). Quando o modelo retirar, atualizar
   conscientemente.
7. **`OPENAI_API_KEY` em CI.** Variável é segredo — configurar via GitHub
   Actions secrets quando CI nightly subir. Fora deste plano (follow-up),
   mas o profile `-Pllm-it` já está preparado para isso.
8. **Custo do BehavioralIT.** ~$0.01 por suite full run (6 testes × ~1500
   tokens / teste @ gpt-4o-mini pricing). Aceitável; auditável via OpenAI
   dashboard. Não rodar em todo PR — só nightly (default sem profile pula
   por causa do `@EnabledIfEnvironmentVariable`).
9. **Pareamento pode criar drift.** Se um patch evoluir, dois testes precisam
   ser atualizados juntos. Mitigação: cada Javadoc referencia o par pelo nome
   exato do método ("par comportamental: `xxx` em `BehavioralIT`") — buscas
   de refactor pegam o par.
10. **Flakiness do real LLM.** Assert deve ser laxo (regex / synonyms),
    nunca exact match. Já documentado em §7.2. Cap de retries no teste: zero
    (não escondemos flake; ajustamos regex ou modelo de pinning).
11. **Patch #15 (refresh state) é o mais difícil de simular** — escalou em
    prioridade desde plano v1. Spy + callback Mockito é o caminho preferido
    sobre Fixture 4. Se a complexidade do setup passar de 1h, marcar como
    TODO e abrir issue separada — não bloquear as outras 21 linhas.
12. **OutputCaptureExtension para #18.** A extensão é de `spring-boot-test`
    (`org.springframework.boot.test.system.OutputCaptureExtension`). Confirmar
    disponibilidade; backup: Mockito spy do `Logger` via reflection.

---

## 9. Critério de pronto

- Todos os ~22 `@Disabled` removidos nos 3 arquivos novos
- `mvn verify -pl turing-app -Dskip.npm=true` (sem profile) verde em < 10min
- `mvn verify -Pllm-it -pl turing-app -Dskip.npm=true` verde local com `OPENAI_API_KEY`
- 0 referências a `fail("Not implemented...")` em qualquer dos 3 arquivos
- Cada patch crítico (linhas 1, 4, 6, 7, 14, 18 da tabela §6) tem **par estrutural + comportamental** com Javadocs que se referenciam mutuamente pelo nome do método
- Os 3 arquivos antigos (`...RaceIT`, `...DeclarativeIT`, `...MiddlewareIT`) **deletados** após Fase 0.5
- 3 fixtures novas firmes em `turing-app/src/test/resources/harness-it/`, exercitadas por ≥ 1 IT cada
- 1 `@TestConfiguration` (`TurAgentChatExecutorMockSupport`) + 1 base class (`AbstractAgentExecutorIT`) compartilhados, ambos package-private fora de `customers/education/flow/`
- Profile `llm-it` em [`turing-app/pom.xml`](../../turing-app/pom.xml) funciona local
- (Follow-up registrado, fora deste plano) Pipeline CI nightly configurado para rodar `-Pllm-it` separado com segredo `OPENAI_API_KEY`
- (Follow-up registrado, fora deste plano) §8.3 do inventário ganha coluna "Par estrutural / comportamental" indicando os pareamentos

---

## 10. O que NÃO faz parte deste plano

- Implementação do refactor "advance → 1× LLM call" — escopo separado, após
  os ITs estarem verdes e servindo de rede de segurança
- Reescrita de [`ProgramaMatchEEFlowIT`](../../turing-app/src/test/java/com/viglet/turing/customers/education/flow/ProgramaMatchEEFlowIT.java)
  — mantém valor como teste customer-driven end-to-end
- Configuração de CI nightly para `-Pllm-it` — follow-up
- Atualização de §8.3 do `HARNESS_INVENTORY.md` refletindo pareamento — follow-up
- Performance tuning dos novos ITs (boot do context já é ~30s; otimização
  fica para depois)
- Migração Spring AI 2.0.0-M6 → 2.x quando lançar; mocks de `ChatResponse.builder()`
  podem precisar ajuste quando API mudar
- ITs novas para features implícitas §8.5 já cobertas indiretamente (trigger
  contract, slot-extract no-LLM, interpolation contract, BR-doc no engine puro)
  — registrado em §4 quais foram promovidas (3) e quais não foram (3)
