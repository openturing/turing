# Chat-Flow Execution Harness — Inventário de Patches

> Documento técnico para guiar refactor da camada `genai/` + `genai/flow/`.
> Gerado em 2026-05-20. Não é especificação de implementação — é mapa do que existe.

## 1. Hipótese central

O harness atual segue a ordem **"LLM call → engine advance → maybe regen"**: o executor primeiro pede ao chat-model uma resposta para a mensagem do usuário, *depois* roda `engine.advance()` (que pode mover o cursor por um ou mais nós transparentes — switch, condition, slot, persona, sub-flow), e por fim — se o cursor moveu — dispara uma **segunda chamada LLM** ("regen") para reescrever a resposta sob o ângulo do *novo* nó. A primeira resposta foi composta com o system prompt do nó *antigo*; ela está estruturalmente desalinhada com a UI que vamos renderizar (chips, próxima pergunta, persona ativa). Para esconder esse desalinhamento, o código acumula uma série de *patches empíricos*: stripping de toolset no initial call, override de máxima prioridade injetado depois do persona prompt, drop deliberado do base system prompt no regen, supressão condicional dos chips quando `textAlignedWithCurrentNode=false`, re-resolução da persona uma terceira vez, trim de história para 3 turnos, etc.

A hipótese é que invertendo para **"engine advance → 1× LLM call → publish"** — i.e., o engine *primeiro* observa a mensagem do usuário e decide o cursor (transparente em um único passe determinístico/judge), e *só depois* o executor monta UMA chamada LLM já com o nó correto, persona correta, tools certas, chips congruentes — a maior parte dos patches some por construção. O `freshly-triggered`/`regen`/`assistantOverride`/`textAlignedWithCurrentNode` viram artefatos do design antigo. Patches que sobrevivem são apenas os de robustez genuína (cycle detection, A/B variant sticky-hash, telemetria defensiva, anti prompt-injection) — esses ficam como middleware separado.

## 2. Resumo quantitativo

| Categoria | Patches | %   | Significado                                    |
| --------- | ------- | --- | ---------------------------------------------- |
| (A) RACE         | 11 | 50% | Some na inversão da ordem                  |
| (B) DECLARATIVE  | 6  | 27% | Vira campo de `ChatFlowNode`               |
| (C) MIDDLEWARE   | 5  | 23% | Continua como middleware separado          |
| **Total**        | **22** | 100% |                                          |

## 3. Inventário detalhado

### #1 — Re-resolve persona AFTER flow selection
- **Local:** [TurAgentChatExecutor.java:290-301](turing-app/src/main/java/com/viglet/turing/genai/TurAgentChatExecutor.java#L290-L301)
- **O que faz:** Chama `personaResolver.resolve(agent, conversationId)` uma segunda vez depois de `resolveFlowContext`, porque a primeira resolução (linha ~240) aconteceu antes do walk transparente que pode ter cruzado um `persona-*` node escrevendo `__activePersonaId`.
- **Por quê (citação literal):** > "The first call at line ~240 happened before any flow ran — on a freshly-triggered flow the engine's transparent walk traversed a `persona-*` node that wrote `__activePersonaId` onto the flow state, switching the active voice […]. Without this re-resolve the regen below still uses the agent's default persona (Marina) […]"
- **Categoria:** A
- **Como some/transforma:** Some. No modelo invertido, advance roda *antes* da LLM call, então quando o executor for resolver a persona ela já reflete o estado final pós-walk. Uma única resolução.

### #2 — Re-resolve persona a TERCEIRA vez para o regen
- **Local:** [TurAgentChatExecutor.java:451-461](turing-app/src/main/java/com/viglet/turing/genai/TurAgentChatExecutor.java#L451-L461)
- **O que faz:** Dentro do bloco regen, chama `personaResolver.resolve(...)` *de novo*, porque o `advance()` pode ter cruzado um `persona-*` durante o walk.
- **Por quê (citação literal):** > "The transparent walk inside `advance()` may have crossed a `persona-*` node that swapped the active voice […]. Using the same lookup as the initial post-flow resolve keeps both paths (fresh-trigger and mid-flow advance) consistent."
- **Categoria:** A
- **Como some/transforma:** Some. Mesmo motivo do #1 — uma única resolução de persona após advance é suficiente quando advance é a *primeira* coisa.

### #3 — Stripping de toolset no INITIAL call quando aiInstruction contém "não chame nenhuma tool"
- **Local:** [TurAgentChatExecutor.java:328-356](turing-app/src/main/java/com/viglet/turing/genai/TurAgentChatExecutor.java#L328-L356)
- **O que faz:** Detecta via substring matching (`forbidsToolCalls`) se o `aiInstruction` do nó ativo tem frases tipo "NÃO CHAME NENHUMA TOOL", e nesse caso troca as chat options para uma versão sem callbacks.
- **Por quê (citação literal):** > "Without this, gpt-4o-mini frequently called `compor_proposta_in_company` preemptively in the initial call BEFORE LlmJudgeGuardrailStrategy could capture the coupon code, generating a proposta without the coupon and publishing it via SSE. The downstream regen for `ai-deliver-proposta` then ran the tool AGAIN with the freshly-captured coupon, re-writing the slot — the visitor saw the card flash (no-coupon → with-coupon)."
- **Categoria:** B
- **Como some/transforma:** Vira campo declarativo `ChatFlowNode.toolsEnabled: boolean` (ou `toolsMode: "allow"|"deny"|"only:<list>"`). Author marca explicitamente que o nó é tool-free e o engine remove os callbacks da single LLM call. Notas: cycle race do flash também desaparece porque no design invertido só há *uma* chamada — não tem dupla execução.

### #4 — Stripping de toolset no REGEN call (mesma heurística)
- **Local:** [TurAgentChatExecutor.java:654-681](turing-app/src/main/java/com/viglet/turing/genai/TurAgentChatExecutor.java#L654-L681)
- **O que faz:** Duplica a lógica do #3 dentro do `regenerateReplyForNewNode`. Strips toolset quando `forbidsToolCalls(newNode.aiInstruction())` é verdade.
- **Por quê (citação literal):** > "we strip the toolset so an over-eager LLM can't preemptively call `compor_proposta_in_company` or `gerar_proposta_pdf` (which would generate the proposal before the slot is captured, then cache by hash and ignore the user's actual coupon code on the next turn). Detection is a substring scan of the aiInstruction — authors mark the node by writing 'NÃO CHAME NENHUMA TOOL' / 'do not call any tool' / 'no tool' anywhere in the instruction."
- **Categoria:** B
- **Como some/transforma:** Some junto com o #3 — uma chamada LLM com campo `toolsEnabled` decide a presença dos callbacks uma única vez.

### #5 — `forbidsToolCalls()`: substring matching bilíngue em texto livre
- **Local:** [TurAgentChatExecutor.java:709-716](turing-app/src/main/java/com/viglet/turing/genai/TurAgentChatExecutor.java#L709-L716)
- **O que faz:** `lower.contains("não chame nenhuma tool") || lower.contains("nao chame nenhuma tool") || lower.contains("do not call any tool") || lower.contains("zero tool calls")` — detecção heurística do "modo sem tools" via substring matching em texto humano.
- **Por quê (citação literal):** > "Bilingual on purpose: PT-BR flows use the upper-cased Portuguese directive; EN flows can use the literal English equivalent. The match is case-insensitive so authors don't have to remember a specific spelling."
- **Categoria:** B
- **Como some/transforma:** Vira `ChatFlowNode.toolsEnabled: boolean` declarativo. A função `forbidsToolCalls` deixa de existir.

### #6 — `freshlyTriggered`: skip do advance no turno de ativação
- **Local:** [TurAgentChatExecutor.java:399-420](turing-app/src/main/java/com/viglet/turing/genai/TurAgentChatExecutor.java#L399-L420)
- **O que faz:** Quando `flowContext.freshlyTriggered()` é true, pula `chatFlowEngineService.advance(...)` e força um regen para o nó de entrada.
- **Por quê (citação literal):** > "Trigger turn: user's msg is the activation signal, NOT input for the first interactive node. Skip the LlmJudge advance entirely (would force-capture the trigger text as the first slot value — e.g. name = 'Plano de carreira pessoal'). Force regen so the user sees the first node's question this very turn […]"
- **Categoria:** A
- **Como some/transforma:** Some. No modelo invertido, no turno de trigger o roteador escolhe o flow e posiciona o cursor no primeiro nó interativo; *depois* a única LLM call é montada com esse nó como contexto. Não há "primeira resposta antiga para reescrever".

### #7 — Regen full path: `regenerateReplyForNewNode`
- **Local:** [TurAgentChatExecutor.java:421-477, 566-693](turing-app/src/main/java/com/viglet/turing/genai/TurAgentChatExecutor.java#L421-L477)
- **O que faz:** Quando o engine advance moveu o cursor (`advanced=true`) e a strategy não emitiu override, dispara uma SEGUNDA chamada LLM com o system prompt já ajustado para o novo nó.
- **Por quê (citação literal):** > "the reply we just got from the LLM was generated with the OLD node's Goal — so the user sees an acknowledgment of their last input but never the new node's content (the 'vou montar um plano… um momento' demo bug). Regenerate the reply with the new node's Goal as the active step so the user sees the proper response on this very turn."
- **Categoria:** A
- **Como some/transforma:** Some inteiro. É o patch *canônico* da hipótese central: na ordem invertida, advance já aconteceu antes da LLM call, então a primeira (e única) resposta é composta com o nó correto. Sem segunda chamada, sem custo extra de latência+tokens.

### #8 — `textAlignedWithCurrentNode` flag + supressão de chips
- **Local:** [TurAgentChatExecutor.java:382-398, 469-476, 504-512](turing-app/src/main/java/com/viglet/turing/genai/TurAgentChatExecutor.java#L382-L398)
- **O que faz:** Booleano que rastreia se o texto final foi escrito para o nó atual (true) ou para um nó anterior (false). Quando false, os chips do nó atual são suprimidos para evitar mismatch visual.
- **Por quê (citação literal):** > "Suppressed when `text` was authored for a different node than the post-advance state — otherwise the user sees a question from node N with chips from node N+1 (e.g. the 'objetivo' question rendered with the 4 area chips below it). One-turn chip delay > permanent UX confusion."
- **Categoria:** A
- **Como some/transforma:** Some. Na ordem invertida, o texto é *sempre* composto para o nó atual pós-advance. Chips e texto sempre alinhados — não há flag para rastrear.

### #9 — Regen omite "Next step preview" hint
- **Local:** [TurAgentChatExecutor.java:593-602](turing-app/src/main/java/com/viglet/turing/genai/TurAgentChatExecutor.java#L593-L602) e [ChatFlowOps.java:125-167](turing-app/src/main/java/com/viglet/turing/genai/flow/strategy/ChatFlowOps.java#L125-L167)
- **O que faz:** Chama `buildBaseAddendum(node, state, graph, /*includeNextStepHint*/ false)` no regen, omitindo o hint sobre a próxima pergunta que o initial call recebe.
- **Por quê (citação literal):** > "In the regen path, the user's last message has already been captured by the judge and the flow is parked on the NEW current node — exposing the next node's instruction here makes the LLM ask one step ahead, producing the chip-vs-question mismatch we kept seeing in the in-company flow ('bot asks setor question, chips are num-pessoas', etc.)."
- **Categoria:** A
- **Como some/transforma:** Some. Sem regen, só há uma chamada e ela sempre tem o `next-step-hint` adequado ao seu único contexto. A bifurcação `includeNextStepHint=true/false` perde sentido.

### #10 — `HIGHEST-PRIORITY OVERRIDE` apendado depois do persona prompt
- **Local:** [TurAgentChatExecutor.java:604-632, 893-948](turing-app/src/main/java/com/viglet/turing/genai/TurAgentChatExecutor.java#L604-L632)
- **O que faz:** Constroi um bloco bilíngue "INSTRUÇÃO DE PRIORIDADE MÁXIMA" e o concatena *depois* do persona prompt no regen.
- **Por quê (citação literal):** > "Empirically the regen LLM tends to anchor on the conversational pattern in history […] and quietly ignore the new node's aiInstruction baked into newAddendum. Explicit directive — placed last so it's the freshest tokens before the user message — flips compliance in the cases we've seen fail."
- **Categoria:** A
- **Como some/transforma:** Some. O override existe puramente para "consertar" o anchoring que o regen acumula por estar tentando contradizer a primeira resposta. Sem regen, sem override.

### #11 — Drop deliberado do `baseSystemPrompt` no regen
- **Local:** [TurAgentChatExecutor.java:615-632](turing-app/src/main/java/com/viglet/turing/genai/TurAgentChatExecutor.java#L615-L632)
- **O que faz:** O regen é montado com `personaPromptComposer.compose(... newAddendum) + regenOverride` — *sem* incluir `baseSystemPrompt`. As regras de orquestração do agent são deliberadamente jogadas fora.
- **Por quê (citação literal):** > "Deliberately drop `baseSystemPrompt` from the regen call. That prompt contains the agent's orchestration rules ('show 3 caminhos welcome on first turn', 'ROUTER LLM decides flow', etc.) which are correct for the INITIAL LLM call (routing + persona-free greeting) but FIGHT the regen's narrow purpose: produce the current node's question. Observed in production with gpt-4o-mini: even with the regenOverride explicitly forbidding the welcome, the LLM kept emitting it on freshly-triggered flows […]"
- **Categoria:** A
- **Como some/transforma:** Some. Sem regen, não há dois prompts diferentes em conflito. O system prompt monta-se uma vez, com o conjunto certo de partes.

### #12 — Inline do `aiInstruction` literal dentro do override
- **Local:** [TurAgentChatExecutor.java:920-931](turing-app/src/main/java/com/viglet/turing/genai/TurAgentChatExecutor.java#L920-L931)
- **O que faz:** Re-inclui o `aiInstruction.trim()` *literal* dentro do bloco override, mesmo já estando no `newAddendum`.
- **Por quê (citação literal):** > "Without this the override only POINTED to a 'FLOW STATE / aiInstruction section above' — empirically gpt-4o-mini frequently ignored that pointer and recycled an instruction from a later step it had already seen via the conversational pattern […]. Re-stating the literal instruction next to the imperative wording flips compliance."
- **Categoria:** A
- **Como some/transforma:** Some junto com #10/#11. Patch puramente para combater anchoring no regen.

### #13 — Inline das chip-labels no override + "Your question MUST be congruent"
- **Local:** [TurAgentChatExecutor.java:932-945](turing-app/src/main/java/com/viglet/turing/genai/TurAgentChatExecutor.java#L932-L945)
- **O que faz:** Quando o novo nó tem `inlineOptions`, concatena os labels delas no override com "Your question MUST be congruent with these choices".
- **Por quê (citação literal):** > "When the destination node renders chip buttons in the UI, those choices are listed inline so the LLM cannot produce a question incongruent with them (observed gpt-4o-mini failure: a decision-role node with chips [RH/L&D, Gestor, C-level, Comprador] regenerated as 'Quantas pessoas participarão?' — the chips render separately, so the visitor saw a num_pessoas question with role chips below it)."
- **Categoria:** A
- **Como some/transforma:** Some. Na ordem invertida, o initial call já compõe com chips do nó atual no contexto — não há "regen tem que se lembrar dos chips do destino" porque não há regen.

### #14 — `trimToRecentTurns`: trim de history para 3 turnos no regen
- **Local:** [TurAgentChatExecutor.java:633-640, 882-979](turing-app/src/main/java/com/viglet/turing/genai/TurAgentChatExecutor.java#L633-L640)
- **O que faz:** O regen recebe `history` truncado para os últimos 3 user-turns, não a história completa.
- **Por quê (citação literal):** > "Sending the full conversation gave the LLM too many examples of the old-pattern Q&A and it kept re-asking the previous step's question despite the system prompt change. Recent context (3 turns = 6 messages) is enough for the model to know who the visitor is + what they just said, without anchoring on stale patterns."
- **Categoria:** A
- **Como some/transforma:** Some. Anchoring acontece porque o regen está tentando *contradizer* o que foi dito 1 mensagem atrás (a resposta antiga ao nó velho). Sem regen, history completa não causa anchoring problemático.

### #15 — Refresh de state do DB antes de strategy advance (defesa contra writes laterais por tools)
- **Local:** [TurChatFlowEngineService.java:227-237](turing-app/src/main/java/com/viglet/turing/genai/flow/TurChatFlowEngineService.java#L227-L237)
- **O que faz:** `state = stateRepository.findById(state.getId()).orElse(state)` no início do advance, recarregando do banco.
- **Por quê (citação literal):** > "Refresh from DB so we pick up any state mutation made during the chat call by side effects we can't see from here — primarily slot writes from Custom Tool callbacks (TurCustomToolSlotHelper saves via a separate findByConversationId + save round-trip, leaving the in-memory state parameter stale). Without this refresh the strategy's writeVariables at the end of advance silently overwrites those tool writes, wiping slots like `programas_match` that drive the UI."
- **Categoria:** A
- **Como some/transforma:** Some. Esse "side effects we can't see from here" é exatamente o problema da LLM call ter rodado *antes* do advance: tools chamados dentro dela escrevem no banco, então o state em memória ficou stale. Na ordem invertida, advance roda primeiro, *depois* a LLM call (com tools) — não há nada pra refrescar.

### #16 — Save explícito após walk transparente quando node id mudou in-place
- **Local:** [TurChatFlowEngineService.java:272-294](turing-app/src/main/java/com/viglet/turing/genai/flow/TurChatFlowEngineService.java#L272-L294)
- **O que faz:** Duplo `stateRepository.save(...)` no advance: uma vez após strategy, outra após `walkTransparentNodes` quando `currentNodeId` mudou in-place mas a row é a mesma.
- **Por quê (citação literal):** > "Without an explicit save the cursor stays at the value persisted by the line above — i.e. the switch/condition node — and the next turn's LlmJudge processes the user message AGAINST that intermediate node, rejecting valid input as off-topic. Visible symptom: visitor sees 'Beleza — qual é o código?' (regen for the landing aiQuestion) and types the coupon, only to be redirected with 'Poderia me informar se você tem um cupom?' because the engine state is still parked on switch-coupon-intent."
- **Categoria:** A
- **Como some/transforma:** Em um redesenho com advance/walk *atomicamente antes* da LLM call, o padrão "save → walk → save" fica explícito num único caminho (read state → resolve cursor → save cursor → call LLM). A patch sobrevive como uma única persistência clean (não dupla), mas a *natureza patch* dela some. Notas: a infraestrutura subjacente — "walks mutate state in-place" — pode ser mantida; só não precisa do save-defensive-extra.

### #17 — Cycle detection no walk transparente (TRANSPARENT_WALK_MAX=16)
- **Local:** [TurChatFlowEngineService.java:986-1099](turing-app/src/main/java/com/viglet/turing/genai/flow/TurChatFlowEngineService.java#L986-L1099)
- **O que faz:** Hard cap de 16 hops em `walkTransparentNodes`; análogo em ChatFlowOps para condition (8) e switch (8) walks.
- **Por quê (citação literal):** > "A flow that stacks more than this many sub-flows in one turn is almost certainly cyclic; the cap keeps a pathological author error non-fatal."
- **Categoria:** C
- **Como some/transforma:** Fica. É robustez genuína contra um erro de autor de flow (loop via sub-flow/condition). Continua como middleware/lifecycle do engine. Não tem nada a ver com a ordem race.

### #18 — Defense-in-depth: fall back to HEURISTIC quando strategy não bate o enum
- **Local:** [TurChatFlowEngineService.java:318-332](turing-app/src/main/java/com/viglet/turing/genai/flow/TurChatFlowEngineService.java#L318-L332)
- **O que faz:** Se o flow referencia um `guardrailMethod` sem bean correspondente, faz log + fallback para HEURISTIC.
- **Por quê (citação literal):** > "Defense in depth: if a flow somehow references a method with no bean (e.g. enum value added without a strategy), fall back to HEURISTIC instead of crashing the chat turn."
- **Categoria:** C
- **Como some/transforma:** Fica. É robustez de runtime contra config inválida. Continua como fallback explícito ou registry-style strategy resolver.

### #19 — `isInExperimentWindow`: A/B variant scheduling window
- **Local:** [TurChatFlowEngineService.java:765-779](turing-app/src/main/java/com/viglet/turing/genai/flow/TurChatFlowEngineService.java#L765-L779)
- **O que faz:** Bate `now` contra `experimentStartsAt`/`experimentEndsAt` antes de elegir variantes A/B.
- **Por quê:** > "Used to gate first-touch A/B assignment so flow authors can stage future variants and let past ones auto-retire without admin intervention."
- **Categoria:** C
- **Como some/transforma:** Fica. É lógica de produto (A/B com janela temporal). Continua como middleware do roteador.

### #20 — Defense in depth no `resolveForcedFlow`: flow só governa seu agent owner
- **Local:** [TurAgentChatExecutor.java:835-839](turing-app/src/main/java/com/viglet/turing/genai/TurAgentChatExecutor.java#L835-L839)
- **O que faz:** Checa que `flow.getTurAIAgent().getId().equals(agent.getId())` antes de aplicar um forced flow.
- **Por quê (citação literal):** > "Defense in depth: a flow only governs chats for its owning agent."
- **Categoria:** C
- **Como some/transforma:** Fica. Robustez/segurança. Continua como guard do resolver de flow.

### #21 — Second-opinion retry no LlmJudge para verdict "stay, no value"
- **Local:** [LlmJudgeGuardrailStrategy.java:108-193](turing-app/src/main/java/com/viglet/turing/genai/flow/strategy/LlmJudgeGuardrailStrategy.java#L108-L193)
- **O que faz:** Quando o primeiro verdict do judge rejeita uma mensagem ≥10 chars sem extrair valor, dispara uma SEGUNDA chamada de judge. Se ainda flake, aplica heurística `isNonBlankRedirect` para decidir entre force-capture vs stay.
- **Por quê (citação literal):** > "The judge call is non-deterministic even at temperature 0 (~5% flake observed in production), so one cheap retry recovers the turn without forcing the user to re-type."
- **Categoria:** B (com nuance C)
- **Como some/transforma:** O retry/heurística-do-redirect-substantivo são tentativas de adivinhar a *intenção* do autor do flow ("se ele marcou esse nó como crítico, NÃO force-capture; se é tolerante, capture"). Esse intent vira o campo declarativo `ChatFlowNode.onJudgeReject = "advance_with_literal"|"reprompt"|"block"` (que já existe! veja #22). Quando todo o intent estiver declarado, o retry pode ser substituído por uma única chamada de judge mais um path determinístico baseado no campo. Notas: o "5% flake" é um efeito da chamada LLM extra do judge — em um redesenho onde extraction acontece dentro da própria turn LLM call via tool calling (com schema enforcement do provider), o judge desaparece e o flake também.

### #22 — `onJudgeReject` already exists as declarative field (parcialmente B já feita)
- **Local:** [ChatFlowNode.java:165-193](turing-app/src/main/java/com/viglet/turing/genai/flow/ChatFlowNode.java#L165-L193), [LlmJudgeGuardrailStrategy.java:213-249, 406-420](turing-app/src/main/java/com/viglet/turing/genai/flow/strategy/LlmJudgeGuardrailStrategy.java#L213-L249)
- **O que faz:** Campo no nó com 3 políticas (`advance_with_literal`, `reprompt`, `block`). A strategy consulta esse valor para decidir force-capture em short messages e em mensagens ≥10 chars com ambos verdicts flake.
- **Por quê (citação literal):** > "Soft-fail policy for the LLM_JUDGE guardrail on this aiQuestion node. Controls what happens when the judge rejects the user's reply on a slot-collecting node […]"
- **Categoria:** B (já está B)
- **Como some/transforma:** Já está como campo declarativo — esse é o *modelo* de como outros patches B devem virar (#3/#4/#5 viram `toolsEnabled`; eventualmente surge `captureMode: "always"|"never"|"llm-judge"`, `inlineOptionsAuthority: "node"|"upstream"`, etc.). Sobrevive ao refactor.

## 4. Padrões observados

- **Persona resolvida 3 vezes por turno** (#1, #2, na linha ~240, e dentro do regen). Sempre por causa do walk transparente do engine ter potencialmente mudado `__activePersonaId` entre a primeira call e o regen. Vira *uma* resolução pós-advance.
- **Boolean "alignedWith…" flags propagadas para suprimir efeitos colaterais** (`textAlignedWithCurrentNode` em #8; `freshlyTriggered` em #6; o conceito de `assistantOverride` na própria `AdvanceResult`). Todas existem para compensar o fato do texto da resposta ter sido escrito num contexto diferente do que o engine acabou parando. Some quando texto sempre é escrito *no* contexto correto.
- **Save → walk → save** (#16, e linhas 270/291/293 em TurChatFlowEngineService.java) — dupla persistência por causa de mutações in-place no `currentNodeId` durante walks. Some quando o ciclo é "resolve cursor → persist → call LLM" em vez de "call → mutate → persist 1 → walk → persist 2".
- **Substring matching em texto livre** (#5: `aiInstruction.contains("não chame nenhuma tool")`; #21: `redirect.length() >= 8` como proxy de "substantivo"). Sempre signal de algo que deveria ser um campo declarativo.
- **"Highest-priority override" apendado depois do prompt principal pra lutar com anchoring** (#10, #12, #13). Tudo regen-specific. Some com regen.
- **Drop deliberado de partes do system prompt no regen path** (#11). É sintoma muito claro: o regen está com prompt diferente do initial pra "puxar" o LLM em direção oposta, e ainda assim falha — anchoring é forte demais. Solução é não ter regen.
- **Telemetria swallow-and-log** (#20, e em `recordSessionStartSafely`/`recordTurnTelemetry`). Esses são (C) middleware genuíno — ficam.

## 5. Recomendação de ordem de remoção

Lista ordenada (cada passo elimina um *batch* de patches):

1. **Inverter a ordem `advance → LLM call`.** Mover `chatFlowEngineService.advance(...)` para *antes* de `chatModel.call(prompt)`. A `advance()` precisa receber só `userMessage` (não `assistantMessage`); a strategy decide cursor sem ver resposta do bot. Remove patches #6, #7, #8, #11, #14, #15. (Patches mais impactantes — ~30% do total.)
2. **Resolver persona UMA vez por turno**, após o passo 1, com o cursor já final. Remove #1, #2.
3. **Introduzir `ChatFlowNode.toolsEnabled: Boolean` (ou `toolsMode`)** declarativo. Remove `forbidsToolCalls()`, dropa #3, #4, #5 e o `regenForbidsTools` que sobreviveria por inércia.
4. **Eliminar o `HIGHEST-PRIORITY OVERRIDE` block e `regenerateReplyForNewNode` inteiro.** Já são código morto depois do passo 1. Remove #9, #10, #12, #13 e o constante `REGEN_HISTORY_TURN_BUDGET`.
5. **Limpar o duplo-save no walk** (#16) — depois do passo 1, o ciclo vira "advance/walk dentro de uma transaction → flush → LLM call". Uma só persistência atômica.
6. **Re-avaliar o judge retry (#21)** à luz do passo 3. Provavelmente vira "uma judge call + comportamento decidido pelo `onJudgeReject` literal", sem heurística `isNonBlankRedirect`. Patch B (já parcial via #22) fica 100%.
7. **Manter middleware-shaped patches**: cycle caps (#17), strategy fallback (#18), A/B window (#19), agent-owner check (#20), telemetria silenciosa.
8. **Refactor terminal**: o método `execute()` deve caber em ~80-100 linhas (atualmente 290+). Os métodos `regenerateReplyForNewNode`, `buildRegenOverride`, `trimToRecentTurns`, `forbidsToolCalls` deixam de existir.

## 6. O que NÃO refatorar

- **Auto-trigger router** (`selectActiveFlow`) — é uma feature do produto (escolhe flow por descrição via LLM router). Não é patch; tem comentário "Resolution order" que documenta produto.
- **Continuation logic** (continuar flow em progresso quando há leaf state) — feature, não patch.
- **A/B variant assignment + sticky hash + scheduling window** (#19, `assignVariant`, `resolveAbVariant`) — produto, não patch.
- **Sub-flow descent/ascent** (`enterSubFlow`, `ascendFromSubFlow`, `chainContainsFlow`) — feature core do DSL. Cycle detection (#17) é robustez.
- **SSE event bus** (`slotEventBus.publish`) — produto. Continua disparando após mutações de slots.
- **Defense-in-depth checks** (#18, #20) — middleware genuíno.
- **Telemetria silenciosa** (`recordSessionStartSafely`, `recordTurnTelemetry`) — middleware C.
- **Anti prompt-injection / CPF/CNPJ checksum / yes-no normalization** em ChatFlowOps — robustez do parser, não patch da ordem.
- **TurToolCallbackPipeline.decorate(...)** — middleware obrigatório para descrição de tools + logging. Não tem nada a ver com a corrida.
- **`Already collected` truncation** (`summarizeVariables` com `MAX_INLINED_VARIABLE_LENGTH=120`) — defesa contra LLM ecoar 1.5KB JSON em chat. É middleware de prompt, continua útil.

## 8. Mapeamento Patches × Testes de Integração

### 8.1. Visão geral

Foram inspecionados **10 arquivos de teste** relevantes ao chat-flow: 1 IT customer-driven ([`ProgramaMatchEEFlowIT`](turing-app/src/test/java/com/viglet/turing/customers/education/flow/ProgramaMatchEEFlowIT.java)), 5 ITs de engine por feature ([`TurChatFlowSubFlowEngineIT`](turing-app/src/test/java/com/viglet/turing/genai/flow/TurChatFlowSubFlowEngineIT.java), [`TurChatFlowPersonaEngineIT`](turing-app/src/test/java/com/viglet/turing/genai/flow/TurChatFlowPersonaEngineIT.java), [`TurChatFlowSwitchEngineIT`](turing-app/src/test/java/com/viglet/turing/genai/flow/TurChatFlowSwitchEngineIT.java), [`TurChatFlowSlotEngineIT`](turing-app/src/test/java/com/viglet/turing/genai/flow/TurChatFlowSlotEngineIT.java), [`TurChatFlowSkipIfFilledEngineIT`](turing-app/src/test/java/com/viglet/turing/genai/flow/TurChatFlowSkipIfFilledEngineIT.java), [`TurChatFlowAlwaysRetriggerEngineIT`](turing-app/src/test/java/com/viglet/turing/genai/flow/TurChatFlowAlwaysRetriggerEngineIT.java)), 1 IT de endpoints adjacentes ([`ChatHandoffAndSlotExtractIT`](turing-app/src/test/java/com/viglet/turing/genai/flow/ChatHandoffAndSlotExtractIT.java)) e 3 unit-tests puros relevantes ([`TurChatFlowAbVariantAssignmentTest`](turing-app/src/test/java/com/viglet/turing/genai/flow/TurChatFlowAbVariantAssignmentTest.java), [`LlmJudgeGuardrailStrategyTest`](turing-app/src/test/java/com/viglet/turing/genai/flow/strategy/LlmJudgeGuardrailStrategyTest.java), [`ChatFlowOpsTest`](turing-app/src/test/java/com/viglet/turing/genai/flow/strategy/ChatFlowOpsTest.java)).

Dos 22 patches catalogados, **9 têm cobertura direta** (~41 %) e **13 estão sem cobertura** (~59 %). A categoria mais coberta é (C) MIDDLEWARE (4/5), seguida de (B) DECLARATIVE (3/6); a (A) RACE — o coração da hipótese central — só tem 2 patches com cobertura indireta de 11 totais. **A causa estrutural dessa lacuna é arquitetural**: os patches (A) vivem em `TurAgentChatExecutor` (a "primeira chamada LLM → engine.advance → regen") e não há nenhum IT do executor — os ITs param em `engine.advance()`, que é justamente o que NÃO carrega os patches. Os ITs de `ProgramaMatchEEFlowIT` chegam mais perto, mas mesmo o `turn4_realLlmResponseContainsThreeProgramsAndCta` *simula* o padrão de duas chamadas em vez de exercitar o executor real, então também não pega regressão dos patches (A).

Fixtures: dos 3 JSONs em `customers/education/chat-flow/`, apenas **`programa-match-ee.chat-flow.json` é exercitado de ponta a ponta**. `in-company-match.chat-flow.json` e `aula-relampago.chat-flow.json` existem como fixture mas só são lidos pelos unit-tests dos seus *tools* — o chat-flow propriamente nunca é rodado. Os ITs de engine usam grafos minimais montados in-line via builders Java.

### 8.2. Fixtures de flow usadas pelos ITs

- [`programa-match-ee.chat-flow.json`](turing-app/src/test/resources/customers/education/chat-flow/programa-match-ee.chat-flow.json) — bundle A/B com 2 flows (Marina e Lucas). Cada um é: `start → persona → 4 aiQuestion (name/cargo_atual/objetivo/area) → switch → 4 branches × 5 slot setters → ai-confirma-*`. Exercita: descent persona, LLM_JUDGE em 4 turnos consecutivos, switch com 4 opções + LLM classifier, slot chain transparente, A/B variant assignment com sticky hash.
- [`in-company-match.chat-flow.json`](turing-app/src/test/resources/customers/education/chat-flow/in-company-match.chat-flow.json) — **NÃO exercitado por nenhum IT.** Existe como fixture: flow B2B com captura de coupon code (cupom), nó tool-free com `aiInstruction="NÃO CHAME NENHUMA TOOL"`, e nó downstream que dispara `compor_proposta_in_company`. **Este é o flow que motivou os patches #3/#4/#5 (tool stripping bilíngue), mas o flow inteiro não tem IT — só o tool em [`ComporPropostaInCompanyTest`](turing-app/src/test/java/com/viglet/turing/customers/education/tool/ComporPropostaInCompanyTest.java).**
- [`aula-relampago.chat-flow.json`](turing-app/src/test/resources/customers/education/chat-flow/aula-relampago.chat-flow.json) — **NÃO exercitado por IT de flow.** Flow curto que dispara o tool `aula_relampago_dspace`. Tool isoladamente coberto por [`AulaRelampagoToolTest`](turing-app/src/test/java/com/viglet/turing/customers/education/tool/AulaRelampagoToolTest.java) e [`AulaRelampagoDspaceIT`](turing-app/src/test/java/com/viglet/turing/customers/education/aula/AulaRelampagoDspaceIT.java); a orquestração do flow não.

### 8.3. Cobertura por patch

| #  | Patch (slug)                                                  | Categoria | Teste(s)                                                                                                                                                                                                                                                                                                                                                                                          | Cenário                                                                                                                                                                                                                                                                                            |
| -- | ------------------------------------------------------------- | --------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1  | re-resolve persona AFTER flow selection                       | A         | — SEM COBERTURA DIRETA — (cobertura indireta parcial: [`TurChatFlowPersonaEngineIT.personaInsideSubFlow_propagatesBackToParentOnAscent`](turing-app/src/test/java/com/viglet/turing/genai/flow/TurChatFlowPersonaEngineIT.java#L260))                                                                                                                                                                | A IT só valida que `__activePersonaId` sobrevive ascent; nenhum teste cobre a 2ª resolução em `TurAgentChatExecutor` que recarrega persona após `resolveFlowContext`. Quebra silenciosamente se o re-resolve sumir.                                                                                  |
| 2  | re-resolve persona a TERCEIRA vez para o regen                | A         | — SEM COBERTURA —                                                                                                                                                                                                                                                                                                                                                                                 | Nenhum IT exerce o caminho regen. [`personaSwitchedMidFlow_overwritesActivePersona`](turing-app/src/test/java/com/viglet/turing/genai/flow/TurChatFlowPersonaEngineIT.java#L163) cobre a *mutação* do persona dentro do engine, mas nunca chega à 3ª resolução no executor.                            |
| 3  | tool-stripping no INITIAL call (forbidsToolCalls heurística)  | B         | — SEM COBERTURA —                                                                                                                                                                                                                                                                                                                                                                                 | O cenário "coupon capture" do flow `in-company-match` não tem IT. Tool composer testado isoladamente em [`ComporPropostaInCompanyTest`](turing-app/src/test/java/com/viglet/turing/customers/education/tool/ComporPropostaInCompanyTest.java); nenhum teste valida que o tool NÃO foi chamado no turn de captura. |
| 4  | tool-stripping no REGEN call (mesma heurística)               | B         | — SEM COBERTURA —                                                                                                                                                                                                                                                                                                                                                                                 | Idem #3. Patch existe puramente para o ai-deliver-proposta do in-company flow.                                                                                                                                                                                                                       |
| 5  | `forbidsToolCalls()` substring bilíngue                       | B         | — SEM COBERTURA —                                                                                                                                                                                                                                                                                                                                                                                 | Função privada em `TurAgentChatExecutor`, sem unit test. Detecção PT/EN/case-insensitive não tem nada provando que continua funcionando.                                                                                                                                                            |
| 6  | `freshlyTriggered`: skip advance no turno de ativação         | A         | [`ProgramaMatchEEFlowIT.autoTriggerRouter_rejectsGreeting_picksFlowOnExplicitIntent`](turing-app/src/test/java/com/viglet/turing/customers/education/flow/ProgramaMatchEEFlowIT.java#L245)                                                                                                                                                                                                          | Valida que o router posiciona o cursor em `ai-name` ao trigger (descida transparente atômica), mas NÃO valida que o executor pula o `advance()` no mesmo turno — a IT bate em `engine.selectActiveFlow`, antes do executor. Cobertura PARCIAL.                                                          |
| 7  | regen full path (`regenerateReplyForNewNode`)                 | A         | [`ProgramaMatchEEFlowIT.turn4_realLlmResponseContainsThreeProgramsAndCta`](turing-app/src/test/java/com/viglet/turing/customers/education/flow/ProgramaMatchEEFlowIT.java#L403)                                                                                                                                                                                                                    | **Cobertura SIMULADA, não exercitada**: o teste replica manualmente o pattern de 2 chamadas (1ª descartada, advance, 2ª usada) para validar que o reply final contém a fechamento correto. Não chama `TurAgentChatExecutor.execute()` em si — o teste *demonstra* a hipótese central, mas não pega regressão do código do executor. |
| 8  | `textAlignedWithCurrentNode` + supressão de chips             | A         | — SEM COBERTURA —                                                                                                                                                                                                                                                                                                                                                                                 | Flag interna do executor, nenhuma asserção sobre chips/text alignment.                                                                                                                                                                                                                            |
| 9  | regen omite Next-step-preview hint                            | A         | — SEM COBERTURA —                                                                                                                                                                                                                                                                                                                                                                                 | `buildBaseAddendum(..., includeNextStepHint=false)` no regen sem teste. `engine.buildSystemPromptAddendum` é exercitado por `turn4_realLlmResponseContains...`, mas com o overload SEM esse parâmetro.                                                                                                  |
| 10 | HIGHEST-PRIORITY OVERRIDE bloco                               | A         | — SEM COBERTURA —                                                                                                                                                                                                                                                                                                                                                                                 | Bloco bilíngue concatenado depois do persona prompt no regen, totalmente untested.                                                                                                                                                                                                                  |
| 11 | drop deliberado do `baseSystemPrompt` no regen                | A         | — SEM COBERTURA —                                                                                                                                                                                                                                                                                                                                                                                 | Comportamento empírico de gpt-4o-mini emitir welcome no regen — observado em produção, nunca pinado em teste.                                                                                                                                                                                       |
| 12 | inline do `aiInstruction` literal dentro do override          | A         | — SEM COBERTURA —                                                                                                                                                                                                                                                                                                                                                                                 | Mesma família do #10.                                                                                                                                                                                                                                                                              |
| 13 | inline das chip-labels no override + "must be congruent"      | A         | — SEM COBERTURA —                                                                                                                                                                                                                                                                                                                                                                                 | Mesma família do #10. O risco de regressão é o mais alto entre os anchoring patches (afeta UX visível: chips de N+1 com pergunta de N).                                                                                                                                                              |
| 14 | `trimToRecentTurns`: history truncado a 3 turnos no regen     | A         | — SEM COBERTURA —                                                                                                                                                                                                                                                                                                                                                                                 | Constante `REGEN_HISTORY_TURN_BUDGET=3` aplicada sem qualquer teste. Mudar para 5 ou 1 não quebra IT nenhum.                                                                                                                                                                                         |
| 15 | refresh de state do DB antes de strategy advance              | A         | — SEM COBERTURA —                                                                                                                                                                                                                                                                                                                                                                                 | Defesa contra writes laterais por Custom Tool callbacks (`TurCustomToolSlotHelper`). Nenhum IT exerce tool que escreve slot durante o turn do executor.                                                                                                                                              |
| 16 | save explícito após walk transparente (duplo-save)            | A         | [`TurChatFlowSwitchEngineIT.switchChained_walksThroughMultipleHops`](turing-app/src/test/java/com/viglet/turing/genai/flow/TurChatFlowSwitchEngineIT.java#L269), [`TurChatFlowSubFlowEngineIT.ascendsWhenSubFlowReachesEnd_andSharesVariablesBackToParent`](turing-app/src/test/java/com/viglet/turing/genai/flow/TurChatFlowSubFlowEngineIT.java#L211)                                                | Cobertura INDIRETA: os ITs validam que após walk transparente o `currentNodeId` final persiste corretamente — se o segundo save sumir, esses testes quebram porque o estado para no nó intermediário. Mas nenhum teste valida explicitamente o padrão "save → walk → save".                              |
| 17 | cycle detection no walk transparente (TRANSPARENT_WALK_MAX=16)| C         | [`TurChatFlowSubFlowEngineIT.refusesDirectRecursion_andTreatsSubFlowAsNoOp`](turing-app/src/test/java/com/viglet/turing/genai/flow/TurChatFlowSubFlowEngineIT.java#L334), [`TurChatFlowSubFlowEngineIT.refusesIndirectRecursion_throughSecondaryFlow`](turing-app/src/test/java/com/viglet/turing/genai/flow/TurChatFlowSubFlowEngineIT.java#L359)                                                  | Cobertura DIRETA: SubFlow A→A e A→B→A — engine detecta ciclo e trata SubFlow como no-op. Cobre o TRANSPARENT_WALK_MAX como mecanismo de safety. Robustez genuína (C) bem testada.                                                                                                                  |
| 18 | fallback HEURISTIC quando strategy não bate o enum            | C         | — SEM COBERTURA —                                                                                                                                                                                                                                                                                                                                                                                 | Linhas 318-332 de `TurChatFlowEngineService` nunca exercitadas: cenário "flow tem `guardrailMethod` para um bean que não existe" não é simulado.                                                                                                                                                     |
| 19 | `isInExperimentWindow`: A/B scheduling window                 | C         | [`TurChatFlowAbVariantAssignmentTest.isInExperimentWindow_nullBoundsMeanAlwaysOpen`](turing-app/src/test/java/com/viglet/turing/genai/flow/TurChatFlowAbVariantAssignmentTest.java#L199), [`...beforeStart_excluded`](turing-app/src/test/java/com/viglet/turing/genai/flow/TurChatFlowAbVariantAssignmentTest.java#L207), [`...afterEnd_excluded`](turing-app/src/test/java/com/viglet/turing/genai/flow/TurChatFlowAbVariantAssignmentTest.java#L215), [`...withinWindow_included`](turing-app/src/test/java/com/viglet/turing/genai/flow/TurChatFlowAbVariantAssignmentTest.java#L223) | Cobertura DIRETA EXAUSTIVA: 4 unit-tests pinam null bounds, before-start, after-end, within-window. Mais o IT [`abRouter_distributesAcrossVariants_andStaysStickyAcrossCalls`](turing-app/src/test/java/com/viglet/turing/customers/education/flow/ProgramaMatchEEFlowIT.java#L597) valida a distribuição binomial 50/50 e stickiness end-to-end. |
| 20 | resolveForcedFlow: flow só governa seu agent owner            | C         | — SEM COBERTURA —                                                                                                                                                                                                                                                                                                                                                                                 | Checagem `flow.getTurAIAgent().getId().equals(agent.getId())` em [`TurAgentChatExecutor.java:835-839`](turing-app/src/main/java/com/viglet/turing/genai/TurAgentChatExecutor.java#L835-L839). Nenhum IT injeta forced flow de outro agent.                                                            |
| 21 | second-opinion retry no LlmJudge ("stay, no value")           | B/C       | [`LlmJudgeGuardrailStrategyTest.isNonBlankRedirect_*`](turing-app/src/test/java/com/viglet/turing/genai/flow/strategy/LlmJudgeGuardrailStrategyTest.java#L43) (7 testes), [`ProgramaMatchEEFlowIT.unconventionalObjetivo_doesNotStallTheFlow`](turing-app/src/test/java/com/viglet/turing/customers/education/flow/ProgramaMatchEEFlowIT.java#L506)                                                  | Cobertura PARCIAL: a heurística `isNonBlankRedirect` (threshold 8 chars, trim) tem unit-tests exaustivos pinando redirects substantivos vs flake. A IT `unconventionalObjetivo_doesNotStallTheFlow` valida o caminho end-to-end "judge rejeita → segunda chamada → force-capture aceita 'jogador de futebol'". Falta cobertura do *retry call* propriamente (sem mock do judge não-determinístico). |
| 22 | `onJudgeReject` campo declarativo já existe                   | B (já B)  | [`LlmJudgeGuardrailStrategyTest.resolveRejectPolicy_*`](turing-app/src/test/java/com/viglet/turing/genai/flow/strategy/LlmJudgeGuardrailStrategyTest.java#L138) (6 testes)                                                                                                                                                                                                                            | Cobertura DIRETA EXAUSTIVA: null/blank/case/snake/SHOUTING_SNAKE/typo/3 valores válidos. Esse patch é o modelo de como (B) deve evoluir e é o único patch (B) totalmente coberto.                                                                                                                      |

### 8.4. Patches sem cobertura (risco de regressão silenciosa)

São 13 patches sem teste pegando regressão direta. Por categoria de risco:

**Alto risco (efeito UX-visível imediato):**

- **#13 — inline das chip-labels** → sugestão de IT: flow com `aiQuestion` + `inlineOptions` no nó N+1 dentro de switch; assertar que a pergunta gerada para o nó N+1 menciona os labels das chips, não a pergunta do nó anterior. (cenário real: o num_pessoas vs decision-role bug.)
- **#10 / #12 — HIGHEST-PRIORITY OVERRIDE + aiInstruction literal** → sugestão: snapshot test no system prompt do regen ao mover de `ai-cargo` para `ai-objetivo` no programa-match flow, validando que o bloco "INSTRUÇÃO DE PRIORIDADE MÁXIMA" está presente e contém o aiInstruction literal do nó destino.
- **#7 — regen full path** → sugestão crítica: IT real de `TurAgentChatExecutor.execute()` exercitando o ciclo completo turn-by-turn (não apenas o engine), assertando que `assistantMessage` recebida pelo SSE corresponde ao goal do nó pós-advance, não pré-advance. Hoje a IT mais próxima (`turn4_realLlmResponse...`) reconstrói o pattern manualmente.
- **#3 / #4 — tool stripping (initial + regen)** → sugestão: IT do in-company-match flow exercitando o turno "trigger msg dispara captura de coupon"; mockar o tool `compor_proposta_in_company` e assertar `verify(tool, never()).invoke(...)` no primeiro turn, depois `verify(tool, times(1)).invoke(eq(slotComCupom))` no segundo. Esse é literalmente o cenário "card flash" descrito no patch.

**Médio risco (anchoring/comportamento empírico):**

- **#8 — textAlignedWithCurrentNode** → sugestão: IT que dispara um advance que move o cursor através de switch+slot chain e valida que a SSE response não contém chips do nó destino se a flag for false. Hoje é confiar no comportamento empírico do gpt-4o-mini.
- **#9 — Next-step-preview omitido no regen** → sugestão: validar que `buildBaseAddendum(node, ..., includeNextStepHint=false)` NÃO contém o aiInstruction do próximo nó. Unit-test puro, baixo custo.
- **#11 — drop do baseSystemPrompt no regen** → sugestão: snapshot do system prompt do regen, assertando que NÃO contém "ROUTER LLM" ou "show 3 caminhos welcome on first turn".
- **#14 — trim de history para 3 turnos** → sugestão: unit-test passando 10 turns ao regen e assertando que apenas os últimos 3 chegam ao Prompt.

**Médio risco (race / persistência):**

- **#1 / #2 — re-resolve persona (2ª e 3ª vez)** → sugestão: IT exercitando agente com persona default, flow com `persona-X` node atravessado pelo walk. Capturar persona resolvida pelo executor e assertar `personaX`, não default. Hoje só validamos `__activePersonaId` no state, não o que o executor escolhe.
- **#15 — refresh de state do DB** → sugestão: IT com Custom Tool que escreve slot via `TurCustomToolSlotHelper.save` *durante* uma chamada do executor; assertar que após advance o slot escrito pelo tool sobrevive (i.e., `writeVariables` da strategy não sobrescreveu). Esse é exatamente o cenário `programas_match` do programa-match.

**Baixo risco (defesa em profundidade):**

- **#5 — forbidsToolCalls substring matching** → sugestão: unit-test puro com 6-8 strings (PT/EN/case mixed/com "no tool" embebido em outro contexto). Trivial de adicionar.
- **#18 — fallback HEURISTIC quando bean missing** → sugestão: registrar um flow com `guardrailMethod` para uma enum value adicionada mas sem `@Component` correspondente; assertar log + delegação para `HeuristicGuardrailStrategy` em vez de exception.
- **#20 — resolveForcedFlow agent owner check** → sugestão: criar dois agents A1/A2, atribuir forced flow para um flow de A1 mas chamar executor com A2; assertar que o forced flow é descartado e o auto-router roda.

### 8.5. Testes que validam comportamento NÃO catalogado como patch

Algumas asserções dos ITs cobrem comportamentos que merecem entrar como *features estruturais* (não patches), mas vale registrar porque vão sobreviver ao refactor:

- **Trigger contract pós-2026.2.x** ([`ProgramaMatchEEFlowIT.autoTriggerRouter_rejectsGreeting_picksFlowOnExplicitIntent`](turing-app/src/test/java/com/viglet/turing/customers/education/flow/ProgramaMatchEEFlowIT.java#L245)): o router DEVE rejeitar saudações genéricas e ativar SÓ em intent explícito. Esse comportamento não está catalogado como patch porque é *política de produto*, mas qualquer refactor do `selectActiveFlow` precisa preservar — o teste é a única especificação executável.
- **Import-bundle persona enum guard** ([`importBundle_createsPersonaSlotsAndFlowWithExpectedSettings`](turing-app/src/test/java/com/viglet/turing/customers/education/flow/ProgramaMatchEEFlowIT.java#L184)): valida que `TurPersonaTone.EXECUTIVE` é aceito e `tone=PROFESSIONAL` falharia. Defense in depth no import — não é patch do executor mas é guarda contra a classe de bug "opaque 400".
- **A/B variant sticky hash + stickiness across calls** ([`abRouter_distributesAcrossVariants_andStaysStickyAcrossCalls`](turing-app/src/test/java/com/viglet/turing/customers/education/flow/ProgramaMatchEEFlowIT.java#L597)): valida distribuição binomial 50/50 com N=20 e re-route sticky. O patch #19 é só o *window*; o teste cobre `assignVariant` + continuation path. Esse é o exemplo de IT completo que falta para os outros patches.
- **Cascade ascent (sub-flow end → parent end → submission)** ([`TurChatFlowSubFlowEngineIT.cascadeAscent_whenSubFlowEndsAndParentNextIsAlsoEnd_recordsRootSubmission`](turing-app/src/test/java/com/viglet/turing/genai/flow/TurChatFlowSubFlowEngineIT.java#L298)): garante que ascent em cascata num único turno é atômico e registra UMA submission. Não é patch — é o contrato de submission do engine.
- **Slot extraction guard rails** ([`ChatHandoffAndSlotExtractIT.slotExtract_agentWithoutLlmInstance_returnsEmptyResult`](turing-app/src/test/java/com/viglet/turing/genai/flow/ChatHandoffAndSlotExtractIT.java#L245)): agente sem LLM instance configurada não crasha o upload, retorna empty. Robustez genuína, sobrevive ao refactor.
- **BR document validation patterns** ([`ChatFlowOpsTest.extractValue_cpfChecksumRejectsInvalidNumber`](turing-app/src/test/java/com/viglet/turing/genai/flow/strategy/ChatFlowOpsTest.java#L130) e correlatos): CPF/CNPJ mod-11 + rejeição de dígitos repetidos. Middleware do parser, sobrevive.
- **Variable interpolation contract** ([`ChatFlowOpsTest.interpolate_*`](turing-app/src/test/java/com/viglet/turing/genai/flow/strategy/ChatFlowOpsTest.java#L40)): semântica single-pass + `Matcher.quoteReplacement`. Não é patch, é contrato do DSL.

## 9. Restrições e não-objetivos

- **Structured output (LLM → JSON) NÃO é caminho.** gpt-4o-mini falha em JSON consistentemente (testado anteriormente). Use **tool calling com schema enforcement do provider** quando precisar de output machine-readable — por exemplo, a captura de slot pode virar um tool `capture_slot(name, value)` que o modelo chama com argumentos schema-validados pelo provider. Não recomendar `BeanOutputConverter` / `ResponseFormat.JSON_SCHEMA`.
- **Não estamos reescrevendo o chat-flow DSL** (`ChatFlowGraph`/`ChatFlowNode` JSON shape). O refactor é da *execução*, não da *definição*. Novos campos declarativos (`toolsEnabled`, etc.) são *adições* ao `NodeData` record — backward-compatible com `null = default`.
- **Spring AI `ChatModel.call()` e `internalToolExecutionEnabled(true)` continuam** — não estamos trocando o framework. O loop interno de tool calling que o Spring AI gerencia é exatamente o que torna possível a inversão (uma só call resolve tools + produz reply).
- **Heuristic e LlmJudge strategies coexistem.** O LlmJudge fica útil para flows narrow-domain onde o autor quer validação semântica explícita; o Heuristic continua para slots regex-validáveis. A inversão da ordem afeta ambos do mesmo jeito.
- **Não criar middleware "auto"** que tente adivinhar quando um nó deveria ser tool-free, captureMode=never, etc. Esses sinais são *autoria do flow*, não inferência de runtime. Vão para campos declarativos do nó.
