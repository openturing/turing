# Turing — Improvements & Roadmap (design rationale)

> **What this file is:** the *design rationale* (the what & why) for
> **unshipped** work — the spec behind each backlog item. It is one of four
> roadmap documents; keep them in sync (see `agents.md` → "Roadmap & docs maintenance"):
>
> | File | Holds | One line per |
> |---|---|---|
> | [ROADMAP.md](ROADMAP.md) | **Task status** (single source of truth) — active backlog | task |
> | [CHANGELOG.md](CHANGELOG.md) | What has **shipped** (git is authoritative) | shipped task |
> | [STRATEGY.md](STRATEGY.md) | Business/positioning decisions (pricing, markets, bets) | — |
> | **IMPROVEMENTS.md** (this file) | Design rationale for active sections | — |
>
> **Founding rule (now enforced):** once a task ships, its detail is removed from
> here and reduced to a one-line entry in CHANGELOG.md — `git log` is the
> authoritative history. Do not re-accrete shipped implementation reports into
> this file.

**Status legend** (status itself lives in ROADMAP.md): ⏳ partial · 📋 designed · 💭 idea.

**Cross-references** use absolute paths from the repo root; linked code may have
moved — verify against `git ls-files` before acting.

## Table of contents

- [I. Chat-flow execution harness — binding constraints](#i-chat-flow-execution-harness--binding-constraints)
- [II. Chat-flow router](#ii-chat-flow-router)
- [III. Lucene & Search Engine across the GenAI surface](#iii-lucene--search-engine-across-the-genai-surface)
- [IV. New ideas from the latest session — shipped](#iv-new-ideas-from-the-latest-session--shipped)
- [V. Cross-cutting decisions](#v-cross-cutting-decisions)
- [VI. Integration test plan for the harness refactor](#vi-integration-test-plan-for-the-harness-refactor)
- [VII. Education customer engagement — platform synthesis](#vii-education-customer-engagement--platform-synthesis)
- [VIII. Strategic moves — repositioning beyond features](#viii-strategic-moves--repositioning-beyond-features)
- [IX. Deep Agents — competitive response](#ix-deep-agents--competitive-response)
- [X. Native LLM SDK exploitation — beyond Spring AI 2.0](#x-native-llm-sdk-exploitation--beyond-spring-ai-20)
- [XI. Embedded LLM (Jlama) — downgraded to a spike](#xi-embedded-llm-jlama--downgraded-to-a-spike)
- [XII. Admin chat ↔ `@viglet/turing-react-sdk` convergence — shipped](#xii-admin-chat--vigletturing-react-sdk-convergence--shipped)
- [XIII. Turing as an MCP Server — let Claude (and any agent) drive the platform](#xiii-turing-as-an-mcp-server--let-claude-and-any-agent-drive-the-platform)
- [XIV. Multi-tenancy — one shared JVM, many isolated tenants](#xiv-multi-tenancy--one-shared-jvm-many-isolated-tenants)
- [XVI. Cost & token governance (Block L)](#xvi-cost--token-governance-block-l)
- [XVIII. Pluggable reranker — follow-up (Block N)](#xviii-pluggable-reranker--follow-up-block-n)
- [XX. Structured-source indexing — the cursarei / Dumont reference (Block R)](#xx-structured-source-indexing--the-cursarei--dumont-reference-block-r)

---

## I. Chat-flow execution harness — binding constraints

> The big harness refactor (22-patch *LLM→advance→regen* → inverted
> *advance→1×LLM→publish*) **shipped** as Block B (T9–T16) plus the
> `TurAgentChatExecutor` decomposition (T33). The historical anatomy of the 22
> patches lived here and is now in `git log` / [CHANGELOG.md](CHANGELOG.md). What
> remains below are the **non-goals and restrictions that are still in force** —
> they constrain every future chat-flow change.

### I.6 What NOT to refactor

These are product, not patches — preserve verbatim:

- Auto-trigger router (`selectActiveFlow`) — picks a flow per
  description via the LLM router.
- Continuation logic (continue in-progress flow when a leaf state
  exists).
- A/B variant assignment + sticky hash + scheduling window (#19,
  `assignVariant`, `resolveAbVariant`).
- Sub-flow descent/ascent (`enterSubFlow`, `ascendFromSubFlow`,
  `chainContainsFlow`). Cycle detection (#17) is robustness.
- SSE event bus (`slotEventBus.publish`).
- Defense-in-depth checks (#18, #20).
- Silent telemetry (`recordSessionStartSafely`, `recordTurnTelemetry`).
- Anti-prompt-injection / CPF-CNPJ checksum / yes-no normalization in
  `ChatFlowOps` — parser robustness.
- `TurToolCallbackPipeline.decorate(...)` — mandatory middleware for
  tool description + logging.
- `summarizeVariables` with `MAX_INLINED_VARIABLE_LENGTH=120` —
  protection against the LLM echoing a 1.5KB JSON in chat.

### I.7 Restrictions and non-goals

- **Structured output (LLM → JSON) is NOT the path.** gpt-4o-mini fails
  on JSON consistently (empirical finding, multiple production tests).
  Use **tool calling with provider-side schema enforcement** when
  machine-readable output is needed.
- **Not rewriting the chat-flow DSL** (`ChatFlowGraph`/`ChatFlowNode`
  JSON shape). The refactor is of *execution*, not *definition*. New
  declarative fields (`toolsEnabled`, etc.) are *additions* to the
  `NodeData` record — backward-compatible with `null = default`.
- **Spring AI `ChatModel.call()` and `internalToolExecutionEnabled(true)`
  stay** — not swapping frameworks. The internal tool-calling loop
  Spring AI manages is exactly what makes the inversion possible (one
  call resolves tools + produces reply).
- **Heuristic and LlmJudge strategies coexist.** LlmJudge stays useful
  for narrow-domain flows where authors want explicit semantic
  validation; Heuristic stays for regex-validatable slots.
- **No "auto-detect" middleware** that tries to guess when a node should
  be tool-free, captureMode=never, etc. Those signals are *flow
  authorship*, not runtime inference. They become declarative node
  fields.

---

## II. Chat-flow router

> Shipped (Block C): language-aware procedural routing (T25), Lucene
> `MoreLikeThis` scoring (T26), cached per-agent `SearcherManager` + BM25 (T27).
> See [CHANGELOG.md](CHANGELOG.md). No active backlog.

## III. Lucene & Search Engine across the GenAI surface

### III.1 Subsystem inventory

| Subsystem | Class | Current mechanism | Opportunity |
|---|---|---|---|
| Chat-flow router | `TurChatFlowEngineService` | Token overlap (Lucene PT analyzer) + LLM fallback | ✅ Shipped (§II) |
| RAG retrieval | `TurRagSearchToolService`, `TurRagContextBuilder` | Vector + BM25 fused via Reciprocal Rank Fusion (T24) per-agent toggle | ✅ Single-collection shipped (§III.2); multi-locale (T24b) pending |
| Vector store | `TurLuceneVectorIndex`, `TurLuceneVectorStore` | Vector index + BM25 inverted index + `hybridSearch` (T24) | ✅ Shipped (§III.2, §III.3); multi-locale split pending (T24b) |
| Spotlights | `TurSNSpotlightProcess` | MemoryIndex + QueryParser (T21) — phrase / slop / fuzzy / boolean syntax via per-request MemoryIndex; AND-default keeps V1 precision | ✅ V1+V2 shipped (§III.4) |
| Chat analytics enricher | `TurChatAnalyticsEnricher`, `TurChatAnalyticsEngine` | Pluggable interface; default no-op | **TF-IDF intent classifier** (§III.5) |
| Chat memory | `TurChatMemoryService` | Chronological FIFO of last N turns | Possible — **conversation-relevance retrieval** (§IV.4) |
| Slot extraction | `TurChatSlotExtractionService` | LLM-only with structured-output prompt | None — semantic extraction is LLM's strength |
| Persona resolution | `TurAgentPersonaResolver` | Exact `__activePersonaId` variable lookup | None — already deterministic |
| Persona tone validation | `TurPersonaToneValidator` | Multi-locale Lucene stem matching: BrazilianAnalyzer + EnglishAnalyzer + SpanishAnalyzer (T22) | ✅ Shipped (§IV.3) |
| Persona few-shot | `TurPersonaFewShotRetriever` | Vector topK over persona's example pool | None — vector is right for this |
| Search rules | `TurSNSearchRuleEvaluator` | EQUALS/CONTAINS/STARTS_WITH on params | None — string matching is the contract |
| MCP/Custom tool selection | `TurAgentChatExecutor.resolveToolCallbacks` | All tools passed to LLM each turn | Possible — **tool pre-filtering** (§IV.2) |
| Spell check | `TurSNSiteSpellCheckAPI` | Delegated to Solr (Lucene under the hood) | None |
| Autocomplete | `TurSNSiteAutoCompleteAPI` | Delegated to SE plugin | None |
| Token usage | `TurLLMTokenUsageService` | Counter aggregation | None |

### III.2–III.5 — shipped

> Hybrid RAG (vector + BM25 RRF, T24) and its production multi-language cores
> (T24b), plus the pluggable chat-analytics intent classifier (T28) shipped in
> 2026.3.1. See [CHANGELOG.md](CHANGELOG.md). The subsystem inventory above (§III.1)
> stays as evergreen reference.

## IV. New ideas from the latest session — shipped

> Everything in this section shipped (T18, T22, T23, T29–T34): streaming
> initial LLM, persona tone validation, slot fuzzy-matching, tool pre-filtering,
> conversation-relevance retrieval, system-prompt fragment caching, observability,
> and the cache-conventions lint. See [CHANGELOG.md](CHANGELOG.md).

## V. Cross-cutting decisions

### V.1 Embedded Lucene vs external SE — when to pick what

[`TurSearchEnginePlugin`](../turing-app/src/main/java/com/viglet/turing/plugins/se/TurSearchEnginePlugin.java)
already abstracts Solr/ES/Lucene-embedded. Every Lucene-backed
improvement has a dual path. The trade-off table:

| Dimension | Embedded Lucene | External SE (Solr/ES) |
|---|---|---|
| Latency per query | ~0.1-2 ms (in-process) | ~5-50 ms (HTTP, same DC) |
| Clustering | Heap-local per pod | Shared cluster, all pods see same view |
| Operational surface | None — just code | Cluster lifecycle, monitoring, backups |
| Index freshness | Wherever the writer was; no replication | Near-real-time across pods |
| Memory budget | App heap | External cluster's RAM |
| Best fit | Hot path, small data, per-instance | Large corpus, multi-instance, persistent |
| Recovery on restart | Cold-start rebuild needed | Already there |

**Per-feature recommendations**:

| Feature | Backing store | Why |
|---|---|---|
| §I-II flow router | Embedded | Hot path, sub-ms required, small data |
| §III.2/§III.3 RAG hybrid | SE-backed (route via `TurSearchEnginePlugin`) | Large corpus, multi-instance |
| §III.4 Spotlights | Embedded | Hot path, tiny data, no clustering need |
| §III.5 Analytics intents | SE-backed (ES MLT) | Shared state, persistent, dashboards |
| §IV.2 Tool pre-filter | Embedded | Per-turn, ~50 tools max, sub-ms required |
| §IV.3 Persona tone | Embedded | Per-turn, in-process strings |
| §IV.4 Conv. memory retrieval | Embedded per-conversation | Tiny data, per-conversation |

**When the second SE-backed feature ships**, extract a small
`TurSeBackedFeatureIndex<K>` helper:
- Owns per-feature index name convention
- Routes through the active `TurSearchEnginePlugin`
- Falls back to embedded Lucene when the SE is unavailable (dev mode)

Premature today (only §III.5 would use it).

### V.2 Where new caches live

| Cache type | Backend today | When to keep / change |
|---|---|---|
| Repository `@Cacheable` | Spring `ConcurrentMap` (default) or Hazelcast if `com.hazelcast:hazelcast-spring` on classpath | Hazelcast for multi-pod consistency — Spring Boot 4 needs the spring integration jar explicitly or the cache silently falls back to in-process |
| Snapshot caches (`turSNSiteSearchSnapshot`) | Same as above | JPA `@EntityListeners` for eviction (e.g. [`TurSNSiteSnapshotEvictionListener`](../turing-app/src/main/java/com/viglet/turing/sn/snapshot/TurSNSiteSnapshotEvictionListener.java)) |
| LLM router decisions | Spring `@Cacheable("turChatFlowRouterDecision")` | Evicted on `TurChatFlow.save/delete` |
| Per-feature SE indexes | Soon (§V.1) | `TurSearchEnginePlugin` + per-agent eviction |

### V.3 Adding a SE-backed feature — the pattern

1. Define the search method on `TurSearchEnginePlugin` interface
2. Implement in `TurSolr*Plugin`, `TurElasticsearch*Plugin`,
   `TurLucene*Plugin` (the last is the embedded fallback)
3. Caller code injects `TurSearchEnginePluginFactory` and calls
   `factory.getPlugin(...).<newMethod>(...)`
4. Eviction hook: `@CacheEvict` on the entity's repository
   `save`/`delete`, listing the new feature's cache name
5. New index name convention: `turing_<feature>_<scope>` (e.g.
   `turing_intents_${agentId}`, `turing_rag_${agentId}`)

---

## VI. Integration test plan for the harness refactor

> Supersedes `docs/chat-flow/HARNESS_IT_IMPLEMENTATION_PLAN.md` (deleted).
> Implementation guide for the IT investments that need to land **before**
> the refactor of §I.5 begins — the tests become the safety net.

### VI.1 Principle: predictability without surprise

The 22 patches (§I.3) exist for reasons that determine *which kind of
test* can lock the regression. Two orthogonal axes — most critical
patches touch both, and covering only one leaves a blind spot.

**Wiring (structural).** The harness *composes the right prompt*, *builds
the right `ChatOptions`*, *persists in the right order*, *calls the
`personaResolver` N times*. Mock the `ChatModel`: enqueue responses,
capture the `Prompt`, count invocations. Runs on every `mvn verify`,
< 10 min, no env var.

**Behavior (real LLM).** Several patches exist because gpt-4o-mini
*responded a certain way* to a certain prompt — anchoring (#10),
preemptive tool call (#3), incongruent chip question (#13), persona
drift (#1/#2). Mock can't assert behavior. Real LLM at `temperature=0`,
loose regex assertions. `@EnabledIfEnvironmentVariable("OPENAI_API_KEY")`,
runs under `-Pllm-it` (nightly / pre-release).

### VI.2 Three IT files, axis per assertion type

Under `turing-app/src/test/java/com/viglet/turing/genai/`:

| File | Type | LLM | Profile | When |
|---|---|---|---|---|
| `TurAgentChatExecutorStructuralIT.java` | Wiring | Mocked via `@Primary TurLlmModelFactory` | default | Every PR / CI |
| `TurAgentChatExecutorBehavioralIT.java` | Real LLM | gpt-4o-mini, gated by `OPENAI_API_KEY` | `-Pllm-it` | Nightly + pre-release |
| `TurAgentChatExecutorContractIT.java` | Middleware (cycle cap, A/B window, agent-owner, abandon) | Mocked | default | Every PR / CI |

**Split axis**: not by patch category (A/B/C). By *which assertion type
breaks first*. Critical patches get **paired** Structural + Behavioral
tests; the Javadocs reference each other so drift is visible.

### VI.3 Pairing matrix

> **Status post-T16:** the pairing matrix below is the **historical**
> design that captured 22 patches. Most of those patches no longer exist
> in the codebase — T16 deleted the regen path, the OVERRIDE block, the
> retry heuristic, and the substring matcher. The Structural skeletons
> that pinned the deleted patches have been removed from
> `TurAgentChatExecutorStructuralIT` (tombstones left in their place);
> the corresponding Behavioral skeletons in
> `TurAgentChatExecutorBehavioralIT` remain `@Disabled` and would have
> to be deleted next time someone fills bodies in. Surviving pairs
> below:
>
> | Patch | Structural (mocked) | Behavioral (real LLM) | Status |
> |---|---|---|---|
> | #1  | `resolvesPersonaAfterFlowSelection_picksWalkWrittenPersonaForInitialCall` | `postWalkPersonaSwap_initialReplyComesInLucasVoiceNotMarina` | ⏳ Survived T16 (redesign needed: assert resolves==1 post-advance, not ≥2) |
> | #3  | `stripsToolsOnInitialCallWhenActiveNodeForbidsToolCalls` | `toolFreeNode_llmDoesNotPreemptivelyInvokeCustomTool` | ⏳ Survived T16 (redesign: assert via `node.toolsEnabled()==false`) |
>
> Deleted pairs (no longer applicable): #7, #10, #12, #13 — all four
> tested the regen / OVERRIDE path that T16 removed.

> **Historical inventory** (kept for context — the categories below
> describe what the 22 patches WERE pre-refactor). Use this only when
> reading old PRs; the active contract is the survivors table above.

| Patch | Cat | Structural IT (mock) | Behavioral IT (gpt-4o-mini real) | Fixture | T16 status |
|---|---|---|---|---|---|
| #1 — persona post-flow | A | `resolver` called ≥2×; 1st prompt has post-walk persona | reply in post-walk persona voice (Lucas), not default (Marina) | persona-switch-mid-flow | ⏳ redesign |
| #2 — persona in regen | A | `resolver` 3×; 2nd prompt has post-walk persona | 2nd turn in post-walk voice after crossing `persona-*` | persona-switch-mid-flow | 🗑 deleted |
| #3 — tool strip initial | B | `ToolCallingChatOptions.getToolCallbacks()` empty when node forbids | LLM does NOT invoke `compor_proposta_in_company` on capture turn | tool-free-node | ⏳ redesign |
| #4 — tool strip regen | B | idem #3 on 2nd call | idem #3 on 2nd call | tool-free-node | 🗑 deleted |
| #7 — regen full path | A | `chatModel.call()` invoked 2×; 2nd prompt has post-advance addendum | question generated is from node B (regex), not A | programa-match-ee | 🗑 deleted |
| #10 — OVERRIDE block | A | substrings `INSTRUÇÃO DE PRIORIDADE MÁXIMA` + `HIGHEST-PRIORITY OVERRIDE` in regen system text | with OVERRIDE: question from node B; without (mock-control): regresses to node A | programa-match-ee | 🗑 deleted |
| #12 — aiInstruction literal | A | `countMatches(systemText, snippet) == 2` in regen | reply mentions distinctive term from `aiInstruction` | programa-match-ee | 🗑 deleted |
| #13 — chips congruency | A | 4 labels literal + "MUST be congruent" in system text | generated question admits all 4 chips as natural answers | regen-with-chips | 🗑 deleted |

**Structural-only — all DELETED in T16** (regen path gone):
- #8 — `textAlignedWithCurrentNode` + chip suppression
- #9 — regen omits next-step preview
- #11 — drop `baseSystemPrompt` in regen
- #14 — trim history to 3 turns in regen
- #15 — refresh state before strategy advance (T13 `@Transactional` replaces it)

**Contract-only** (middleware):
- #16 — explicit save after walk → redesigned as "all walk saves coalesce in one transaction" (T13)
- #17 — cycle cap (already covered by `TurChatFlowSubFlowEngineIT` — don't duplicate)
- #18 — fallback HEURISTIC
- #19 — A/B window (already covered by `TurChatFlowAbVariantAssignmentTest` — don't duplicate)
- #20 — agent-owner guard
- #21 — judge retry / `isNonBlankRedirect` — 🗑 deleted in T16; replaced entirely by #22
- #22 — `onJudgeReject` declarative (already covered by `LlmJudgeGuardrailStrategyTest` — don't duplicate)

**Special**:
- #5 — `forbidsToolCalls` substring overload 🗑 deleted in T16; `@Nested` unit class now only exercises the node-aware overload (`ChatFlowNode.toolsEnabled`)
- #6 — `freshlyTriggered` skip: strong indirect coverage via `autoTriggerRouter_rejectsGreeting_picksFlowOnExplicitIntent`. Post-T16 the contract narrowed to "skip earlyAdvance on the trigger turn"; the structural skeleton survives and needs a body.

### VI.4 Shared infrastructure

#### VI.4.1 `@TestConfiguration` (Structural / Contract)

Location: `turing-app/src/test/java/com/viglet/turing/genai/testsupport/TurAgentChatExecutorMockSupport.java`

```java
@TestConfiguration
public class TurAgentChatExecutorMockSupport {
    public static class ChatModelHarness {
        ChatModel chatModel();                  // Mockito mock
        ArgumentCaptor<Prompt> promptCaptor();
        void queueResponse(String text);        // enqueue plain-text response
        void queueToolCallResponse(/* ... */);  // for tool-strip inverse sanity
        List<Prompt> capturedPrompts();
        void reset();                           // @BeforeEach
    }
    @Bean ChatModelHarness chatModelHarness();
    @Bean @Primary TurLlmModelFactory mockLlmModelFactory(ChatModelHarness h);
    @Bean @Primary TurChatAnalyticsService noopChatAnalyticsService();
    @Bean @Primary TurChatMemoryService noopChatMemoryService();
}
```

Helpers:

```java
static String systemTextOf(Prompt p) {
    return p.getInstructions().stream()
            .filter(m -> m instanceof SystemMessage)
            .map(Message::getText)
            .findFirst().orElseThrow();
}
```

Substituting `TurLlmModelFactory` via `@Primary` bypasses
`TurResilientChatModel` retry/circuit-breaker — preserves the invariant
"1 executor call = 1 Mockito call" without retry masking.

#### VI.4.2 Base class `AbstractAgentExecutorIT`

Extends [`AbstractTuringSpringIT`](../turing-app/src/test/java/com/viglet/turing/testutil/AbstractTuringSpringIT.java)
(ephemeral H2 per JVM, JMX, Artemis isolated). Helpers:

- `TurAIAgent createAgent(String title, TurPersona defaultPersona)`
- `TurLLMInstance createMockableLlmInstance()`
- `TurChatFlow importFlowFromClasspath(String resource, TurAIAgent agent)`
- `TurChatFlowState seedStateAt(String conversationId, TurChatFlow flow, String nodeId, Map<String,String> vars)`
- `List<ChatResponse> runTurn(...)` — `executor.execute(...).collectList().block(Duration.ofSeconds(5))`
- `TurChatFlowState reloadState(String conversationId)`
- `Prompt firstPrompt()` / `Prompt regenPrompt()` — shortcuts to
  `capturedPrompts().get(0/1)`

#### VI.4.3 BehavioralIT pattern (real LLM)

Inherited from
[`ProgramaMatchEEFlowIT`](../turing-app/src/test/java/com/viglet/turing/customers/education/flow/ProgramaMatchEEFlowIT.java):

- `@SpringBootTest` + `@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")`
- `@BeforeAll buildChatModel()` constructs `OpenAiChatModel` with
  `gpt-4o-mini`, `temperature=0`
- Each test method's Javadoc includes `Pinning: gpt-4o-mini @ 2026-05`
  — when OpenAI retires the model, the test breaking IS the migration
  documentation (drift is a feature)

Loose assertions:

```java
// #10 — node B question, not node A:
assertThat(reply).matches("(?i).*(objetivo|onde\\s+você\\s+quer\\s+estar|carreira).*\\?");

// #1 — Lucas peer-tone, not Marina formal:
assertThat(reply).containsAnyOf("Oi", "Olá", "Beleza");
assertThat(reply.toLowerCase()).doesNotContain("prezad");

// #13 — question admits ALL 4 chips:
assertThat(reply.toLowerCase()).matches(".*(papel|decisão|atuação).*\\?");
```

Tolerance strategy: retry zero. Flake → adjust regex/synonyms BEFORE
relaxing. gpt-4o-mini flake-rate at temp 0 with deterministic prompt is
< 5% — loose regex absorbs.

### VI.5 New fixtures

Under `turing-app/src/test/resources/harness-it/`:

**1. `tool-free-node.chat-flow.json`** *(firm)*
Graph: `start → ai-coupon-code (aiInstruction "NÃO CHAME NENHUMA TOOL")
→ switch-cupom-ok → ai-deliver (aiInstruction "Chame
compor_proposta_in_company") → end`. Single persona (Camila copied from
in-company). Agent has `compor_proposta_in_company` as a stub custom
tool that records invocations. Serves #3, #4, #5.

**2. `persona-switch-mid-flow.chat-flow.json`** *(firm)*
Bundle: 1 flow + 2 personas (Marina, Lucas). Graph: `start →
persona-marina → ai-name → switch-route (decides via `__force_route`
seeded by test) → persona-lucas → ai-objetivo → end`. 5 nodes + 4 edges.
Serves #1, #2. (programa-match-ee has Marina/Lucas in *separate* A/B
flows — this fixture forces the walk crossing a persona node.)

**3. `regen-with-chips.chat-flow.json`** *(firm)*
Bundle: 1 flow + 1 persona. Graph: `start → ai-cargo (aiInstruction
"Pergunte cargo") → switch-area → ai-decision-role (aiInstruction
"Pergunte papel na decisão", inlineOptions=["RH/L&D", "Gestor",
"C-level", "Comprador"]) → end`. 4 nodes + 3 edges. Reproduces the
patch #13 scenario verbatim.

**4. `tool-write-during-call.chat-flow.json`** *(conditional)*
For patch #15. Spy of `TurChatFlowStateRepository` + Mockito callback
in `chatModel.call` is **preferred** over a real Custom Tool simulation.
Only build the JSON if the spy fails to cover the scenario.

### VI.6 Implementation phases

**Phase 0 — Infrastructure (0.5-1 day)**
- Create `genai/testsupport/`
- Implement `TurAgentChatExecutorMockSupport` + `ChatModelHarness`
- Implement `AbstractAgentExecutorIT`
- Extract `ChatFlowImportTestUtil.importIntoAgent` from
  `ProgramaMatchEEFlowIT`
- Add `llm-it` Maven profile to `turing-app/pom.xml`
- Smoke test (delete before closing phase)

**Phase 0.5 — Migrate 15 existing skeletons (0.5 day)**
The Javadocs of the existing skeletons (in `TurAgentChatExecutorRaceIT`,
`TurAgentChatExecutorDeclarativeIT`, `TurAgentChatExecutorMiddlewareIT`)
**are the contract** — preserve literally. Migration is:

- `package` change
- Add `@Import(TurAgentChatExecutorMockSupport.class)` on Structural/Contract
- Rename to destination file as per the patch table (§VI.3)
- **Delete** the 3 old files after migration

**Phase 1 — Structural skeletons reusing existing fixture (1 day)**
Tests that run with `programa-match-ee.chat-flow.json` (existing):
#10, #11, #9, #12, #14, #1, #7 (new), #6 (new).

**Phase 2 — Contract skeletons (0.5 day)**
#18, #20, #16 (new double-save), cascade ascent, BR-doc CPF.

**Phase 3 — New fixtures (0.5 day)**
`harness-it/tool-free-node.chat-flow.json`,
`harness-it/persona-switch-mid-flow.chat-flow.json`,
`harness-it/regen-with-chips.chat-flow.json`. Smoke `@Test` per fixture
that imports + parses + logs the graph.

**Phase 4 — Structural skeletons with new fixtures (1 day)**
#3, #4, #5 (`@Nested` unit), #2, #13, #8, #15 (spy approach),
persona-tone import guard.

**Phase 5 — Behavioral skeletons (1-1.5 days)**
All in `TurAgentChatExecutorBehavioralIT.java`. Six tests pairing
structurally with the critical patches: #1, #3, #7, #10, #12, #13.

**Phase 6 — Cleanup (0.5 day)**
- Remove `TODO` from migrated skeletons
- Update Javadocs cross-references
- Note in this doc that pairing exists

**Total estimate: 4.5-6 working days.**

### VI.7 Risks

1. **`TurLlmModelFactory` wraps `ChatModel` in `TurResilientChatModel`**
   — `@Primary` substitution bypasses, preserving the invariant.
2. **Custom Tool Groovy scripts** would run if registered — ITs create
   a fresh agent per `@BeforeEach` with no custom tools, except Fixture 1.
3. **`TurChatMemoryService` could persist to vector store** — replaced by
   NoOp via `@Primary`.
4. **Context startup ~30s** — all three IT classes use the same
   `@Import` so Spring reuses the context. BehavioralIT inherits
   `AbstractTuringSpringIT` without the mock support — separate ~30s
   context (acceptable, only with profile).
5. **`@SpyBean TurAgentPersonaResolver` on Java 21 + CGLIB** — confirm
   `resolve(...)` is not `final`. Fallback: `@MockBean` + manual
   delegation.
6. **BehavioralIT depends on gpt-4o-mini stability** — treat model
   drift as feature: Javadoc pins the model version, breakage forces
   conscious migration.
7. **`OPENAI_API_KEY` in CI** — GitHub Actions secrets, out of this
   doc's scope.
8. **BehavioralIT cost** — ~$0.01 per full suite run @ gpt-4o-mini
   pricing. Only nightly.
9. **Pairing drift** — Javadocs reference the pair by exact method
   name; refactor search picks them up together.
10. **Real LLM flakiness** — loose regex / synonyms; retry zero.
11. **Patch #15 is the hardest to simulate** — spy + Mockito callback
    preferred over Fixture 4. If setup grows beyond 1h, mark TODO and
    open a separate issue; don't block the other 21 lines.
12. **`OutputCaptureExtension` for #18** — from `spring-boot-test`,
    available; fallback to Mockito spy of `Logger` via reflection.

### VI.8 Done criteria

- All ~22 `@Disabled` removed across the three new files
- `mvn verify -pl turing-app -Dskip.npm=true` (no profile) green in
  < 10 min
- `mvn verify -Pllm-it -pl turing-app -Dskip.npm=true` green locally
  with `OPENAI_API_KEY`
- Zero `fail("Not implemented…")` in any of the three new files
- Every critical patch in the pairing matrix (§VI.3) has both
  Structural + Behavioral, Javadocs cross-referenced by method name
- The three old files (`...RaceIT`, `...DeclarativeIT`,
  `...MiddlewareIT`) **deleted** after Phase 0.5
- Three new firm fixtures under `turing-app/src/test/resources/harness-it/`,
  each exercised by ≥1 IT
- One `@TestConfiguration` (`TurAgentChatExecutorMockSupport`) + one
  base class (`AbstractAgentExecutorIT`) shared, both package-private
  outside `customers/education/flow/`
- `llm-it` profile in `turing-app/pom.xml` works locally
- CI nightly pipeline for `-Pllm-it` (+ `-Pagent-eval` / `-Pnl-facet-eval`)
  with the `OPENAI_API_KEY` secret — shipped, see
  `.github/workflows/nightly-llm-it.yml` (T242)

---

## VII. Education customer engagement — platform synthesis

> Consolidation of the platform-level discoveries from the Education
> customer × Turing engagement. The customer repo's `docs/IMPROVEMENTS.md`
> is exhaustive on the *customer side* (portal, 3 chat-flows, 4 Custom
> Tools, 17 slots, Storybook stories). This section keeps only the
> **Turing platform** projection: what shipped *here*, what should ship
> *here*, and which lessons survive customer rotation.

### VII.2 Platform-pending items the engagement surfaced

These are the items that *should* live in the Turing platform but
currently exist only as Education customer-side patterns, sketches or
explicit gaps. Each maps to a T-task in §0 Block D.

#### VII.2.a HTTP auth chainable helpers (T39)
Custom Tools today re-implement bearer / basic auth headers per call.
Goal: `http.bearer(token).postJson(url, body)`, `http.basic(user, pw)`.
Pairs with slot prefix `__` reserved for credentials (`__bearer_token`
→ auto `Authorization` header on every `http.*` call, never echoed in
logs).

#### VII.2.c Groovy hot-reload via WebSocket (T41)
Admin edits → save → WS pushes reload notice to backend → next
invocation uses the new script without restart. Cuts ~3min wait per
iteration.

#### VII.2.d `subFlowSwitch` + `functionCall` runtime (T46, T47)
- `subFlowSwitch`: switch chooses a sub-flow by slot value (not edges).
  Composes reusable mini-flows (lead-capture / handoff selection /
  pricing tier flows).
- `functionCall`: the type exists in `ChatFlowNodeGeneration.ts` but the
  engine does not execute it. **Decision needed: implement OR remove
  from the DSL.** Vapor types are worse than absences.

#### VII.2.e Capture-first inversion (T51)
Restructures the engine so the user message is **always** persisted into
the active slot BEFORE the judge runs. Judge becomes advisory (writes
`slot_confidence: low/high` into a parallel slot); the flow never stalls
with `slot=null`. Architectural — kills the whole "judge rejects → flow
stuck" class at the root (vs. T45's per-node `onJudgeReject` mitigation).
Risk: affects every existing flow, requires full IT revalidation.
Filosofia change: "validate-then-capture" → "capture-then-grade."

#### VII.2.g Slot audit log + PII prefix (T60, T61)
- **Audit log**: `chat_slot_write_log` (conversationId, slot, oldValue,
  newValue, source ∈ {`node`, `tool`, `endpoint`, `extract`}, ts). LGPD
  audit + debug forensics. SlotInspector renders as timeline with source
  badge.
- **PII prefix `pii_*`**: engine redacts in logs/analytics, encrypts at
  rest, TTL configurable (default 24h). Compliance pitch for regulated
  customers (banking, healthcare, B2C with strong privacy reqs).

#### VII.2.i A/B multi-armed bandit (T70) + champion-challenger (T71)
Current A/B is fixed split + scheduling window. Bandit math (Thompson
sampling / UCB) auto-adjusts `trafficWeight` based on observed conversion
in the last N sessions. Champion-challenger auto-promotes a statistically
significant winner to `trafficWeight=100` and archives the loser. Less
operator vigilance; better optimization on slow-moving conversions.

#### VII.2.j Funnel + conversation replay (T85, T86)
- **Funnel viz**: turn-by-turn abandonment heatmap over the flow graph.
  "60% drop between `ai-cargo` and `ai-objetivo`" enables surgical UX
  fixes vs whole-flow rewrites.
- **Conversation replay**: click `conversationId` in scorecard → see
  transcript + slot timeline side-by-side, scrubber temporal, per-turn
  prompt + response artifacts. Investigation-grade DX for operators.

#### VII.2.k Flow editor capabilities (T94, T95, T98)
- **Chat-flow lint** (`TurChatFlowLinter`): static analyzer flags
  `aiInstruction > 500 chars`, dead-end node, orphan node, edge without
  label, ambiguous trigger across flows, `outputVariable` never read.
  Sidebar warnings panel. Analogue of TypeScript for chat-flows.
- **Live preview** of the active node in editor — second panel renders
  "what the visitor sees" with mocked slots, updates <300ms on edit.
  Iteration speed: 10× vs running real turns.
- **TypeScript DSL** (`@viglet/turing-flow-dsl`) — `flows/foo.ts` with
  backend enums imported, transpiles to JSON at build time. Catches
  `tone=PROFESSIONAL` typos at compile time; full IDE auto-complete.

#### VII.2.l Multi-flow architecture (T91, T92, T93)
With the Education customer running 3 coexisting flows (lead-capture
B2C + in-company quote B2B + micro-lesson educational), three new
platform needs emerged:
- **Trigger conflict resolver UI**: admin flags "this triggerDescription
  has overlap with flow X" + suggests disambiguation. Today specificity
  decides silently.
- **Flow chooser endpoint** `POST /chat/flow-select`: portal forces a
  specific flow (override LLM router). Useful for deep links
  (`?flow=in-company` lands on the right persona).
- **Cross-flow slot inheritance**: when the engine mid-flow-routes
  ("oh this is actually B2B"), preserve already-captured slots
  (`name`/`cargo_atual`) via a declared mapping in the receiving flow.

### VII.3 Lessons from the engagement worth keeping

These survive customer rotation — patterns / pitfalls that should be
internalized in the next customer's design phase, not re-discovered.

1. **Determinism + LLM hybrid is the production pattern.** Three
   Education-customer tools (`compor_proposta_in_company`,
   `gerar_proposta_pdf`, `aula_relampago`) follow the same shape:
   Groovy composes structure deterministically (pricing tables, layout,
   mindmap), LLM contributes only *content* (tone, narrative voice).
   Pricing and references must audit; tone is a stylistic dial.
   Document this as platform doctrine and ship `scaffold.markdown` /
   `scaffold.pickQuote` / `scaffold.buildMindMap` helpers (T98 in §VII.4).

2. **Slot orthogonality enables multi-flow coexistence.** B2B flow uses
   `razao_social`/`num_pessoas`/`tema`/`prazo`; B2C uses
   `name`/`cargo_atual`/`objetivo`/`area`. Same session accumulates both
   without corruption. LLM router decides at trigger; presence of a
   distinctive slot (`proposta_in_company`) determines UI without
   needing an explicit mode flag.

3. **`triggerDescription` is a denylist, not an allowlist.** Allow-list
   of keywords kills greetings. Denylist + specificity-tiebreak is what
   works in production.

4. **`gpt-4o-mini` at temp=0 is deterministic but NOT robust to JSON.**
   Structured-output as a guardrail is a dead-end with this model class.
   Use tool-calling with provider-side schema or the bilingual `LlmJudge`
   strategy instead (already encoded as §I.7 non-goal — verbatim in §0).

5. **DSpace / public-content grounding beats LLM-invented references.**
   Pull the source + extract a verbatim quote ≥40 chars + hand it to the
   persona in the scaffold. The LLM has no opportunity to hallucinate
   an author. Generalizable beyond academia: any public-content corpus
   (papers, manuals, knowledge base) works with the same pattern.

6. **The persona should NOT use a first name in B2B.** "Olá, Maria"
   sounds invasive in account-exec contexts; the B2B persona speaks to
   the *company* through an interlocutor. Detail with outsized
   credibility impact. Add to persona authoring guide.

7. **Decision-role-aware closing branches one node, not the flow.**
   Same proposal + 4 narratives (RH hears logistics, C-level hears ROI,
   manager hears hands-on, buyer hears terms). `aiInstruction` ramifies
   by `decision_role` slot. Cheaper than duplicating nodes.

8. **SSE multiplexer pattern is generic.** N hook instances → 1
   EventSource via refcount module-level + late-subscriber snapshot
   replay via `queueMicrotask`. Applicable to any SSE multi-consumer
   surface (chat tokens, analytics, code-interpreter logs). Shipped at
   the SDK level (T104); reuse the same pattern for other streams.

### VII.4 Helper APIs to extract from Education-customer tools

The Education-customer tools repeated three primitives that should
graduate to platform-level Groovy helpers:

```groovy
// Pattern observed in compor_proposta_in_company, aula_relampago,
// gerar_proposta_pdf

scaffold.markdown(
    beats: [
        [t: 0,  label: "Hook"],
        [t: 22, label: "Conceito"],
        [t: 44, label: "Caso brasileiro"],
        [t: 66, label: "Aplicação imediata"],
    ],
    references: [
        [author: "...", year: 2009, title: "...", quote: "..."]
    ],
    cargoHint: cargoRegisterHint  // "STRATEGIC" / "MANAGERIAL" / "FOUNDATIONAL"
)  // returns a string scaffold for the persona to fill

scaffold.pickQuote(abstractText, term, minLength = 40)
// heuristic: longest sentence ≥40 chars containing the term;
// fallback to the longest sentence overall

scaffold.buildMindMap(abstracts, term, stopWords = "pt+en")
// returns Mermaid mindmap source via title-case + stop-word filter
// + drop-of-term + top-N frequency
```

Each helper is ~20-30 lines of Groovy; bundling into the SDK eliminates
the Custom Tool copy-paste tax and gives a discoverable contract.

---

## VIII. Strategic moves — repositioning beyond features

> Moved to [STRATEGY.md](STRATEGY.md) — pricing, market focus, the Cloud bet,
> MCP-first transition, the Spring-AI fork question, deprecations, sequencing.

## IX. Deep Agents — competitive response

> **Date of analysis**: 2026-05-25.
> **Subject**: LangChain Deep Agents (`langchain.com/deep-agents`), the
> Python harness LangChain ships for long-horizon autonomous agents.
> **Verdict**: Turing has 80% of the underlying mechanics already, but
> packages them as a different category (chat-flow platform, not deep
> agent harness). This section maps the gap and proposes Turing's
> response — three parity moves, three differentiation moves, two
> interop moves. Tactical work is in §0 Block E (T108–T129).

### IX.1 Feature-by-feature mapping — where Turing stands today

The Deep Agents pitch reduces to five capabilities. Mapping each to
what Turing already has:

| Deep Agents capability | Quote from langchain.com/deep-agents | Turing today | Gap |
|---|---|---|---|
| **Planning tools** | "Planning tools let agents decompose tasks, track progress, and adapt as they learn" | Chat-flow graph (authored, deterministic); LLM-router for flow selection | No **LLM-emitted plan as runtime artifact**. Plans are author-time in Turing |
| **Sub-agents with isolated context** | "Spawn subagents for independent subtasks, each with isolated context" | Sub-flow descent (`enterSubFlow`/`ascendFromSubFlow`); A/B variants; chat-flow router | Sub-flows are *graphs inside the same agent*. **No cross-agent invocation primitive.** |
| **Virtual filesystem** | "Virtual filesystem stores system prompts, skills, and long-term memory" | Persona prompts (DB) + RAG vector store + chat memory FIFO + T30 relevance retrieval | No **per-conversation scratchpad** for arbitrary blobs. Slots are scalars/JSON, not files. |
| **Context engineering middleware** | "Compress conversation history, offload large tool results, isolate context with subagents, use prompt caching" | T30 relevance retrieval (compress), `summarizeVariables` cap (partial offload), sub-flows (isolate), T31 prompt caching (caching) | **No automatic summarization** of compressed memory. **No artifact-store offload** for large tool results. |
| **Human-in-the-loop** | "Supports human intervention when needed" | None directly; sketched as Spectator mode in §VIII | **No `humanApproval` node type.** |

**Two operational features**:
- **Model-agnostic** — Turing has it ✅ (Spring AI + 6 providers).
- **SDK + CLI** — Turing has SDK ✅; **no CLI**.

**Bottom line**: Turing is already a deep-agent-grade platform in
infrastructure terms. The gap is one missing primitive (workspace) and
two missing UX surfaces (CLI + eval).

### IX.2 Parity moves — close the gap with primitives, not features

#### IX.2.a Planning as a flow node (T108)

Deep Agents bakes planning into the harness — every agent gets a
planning tool by default. **Turing's better move**: make planning an
**authorable node type**, not implicit middleware.

```jsonc
// Inside a chat-flow:
{
  "type": "planningStep",
  "data": {
    "outputVariable": "__plan",                  // slot receiving the JSON plan
    "aiInstruction": "Break the user's goal into 3-6 concrete steps...",
    "schema": "List<{id: string, title: string, status: 'pending'|'done'}>"
  }
}

// followed by an iterator:
{
  "type": "iteratePlan",
  "data": {
    "planSlot": "__plan",
    "bodyFlowId": "step-executor-subflow",      // sub-flow runs once per pending step
    "completionMode": "mark_done"
  }
}
```

**Why this is better than Deep Agents' approach**:
- Planning is **visible in the editor** — operators see "this flow plans before executing." Deep Agents hides it as middleware.
- **Selective** — admins decide when planning is worth the LLM call. Deep Agents always plans.
- **Auditable** — the plan IS a slot value; T54 SSE bus already emits it; T60 audit log already captures origin.
- **Replayable** — exact plan from prod becomes a regression test fixture (T127).

#### IX.2.b Cross-agent invocation (T109)

Deep Agents spawns subagents inside the same harness. Turing should
expose **agent-as-tool** at the platform level:

```groovy
// Inside a Custom Tool Groovy script:
def specialist_answer = agent.invoke(
    "specialist-legal-agent",                    // target agent
    "Is this contract clause enforceable? ${slots.contract_excerpt}",
    [timeout: 30_000, isolated: true]
)
slots.set("legal_review", specialist_answer)
```

Each invocation spawns a child conversation, links to parent via
`parentConversationId`, gets its own scorecard row (T110). Recursion
allowed up to a configurable depth (default 3) — mirrors the
`TRANSPARENT_WALK_MAX` cycle cap pattern.

**Why this beats Deep Agents subagents**:
- Sub-agents in Turing are **persisted, named, reusable** entities — not anonymous spawns.
- Each can have its own LLM, persona, flow, tools — true specialization.
- **Multi-tenant by construction** — workspace + path isolation (T78) extend to child conversations automatically.
- **Observable** — drill-down dashboard "orchestrator X calls specialist Y on average 2.3 times per conversation, costing $0.04 each."

#### IX.2.c Sub-agent analytics (T110)

Every `agent.invoke(...)` recorded as a separate session event with
`parentConversationId` foreign key. Scorecard supports parent/child
filter. Operator can answer "is the orchestrator overspending on the
specialist?" — a question Deep Agents users have no built-in way to
answer.

### IX.3 The workspace primitive — Turing's defensible move

This is the *one* primitive Deep Agents has that Turing doesn't, and
also the one where Turing can ship a **strictly better** version
because of pre-existing infrastructure.

#### IX.3.0 What "workspace" means in Turing — concrete walkthrough

Before the API design (§IX.3.a), make the concept tangible. The
**Agent Workspace** is a per-conversation, persistent file area that
Custom Tools and the LLM share. It's a *fourth* type of state in
Turing, distinct from the three that already exist:

| State surface | Today | Shape | Lifetime | Visible to LLM | Visible to portal |
|---|---|---|---|---|---|
| **Slots** | ✅ | Flat scalars / small JSON (~1KB) | Per conversation | Yes — embedded in every system prompt as `Already collected: {...}` | Yes — SSE bus (T54) |
| **Chat memory** | ✅ | Ordered list of text turns | Per conversation, FIFO of last N | Yes — flows into prompt history | No |
| **RAG vector store** | ✅ | Indexed chunks of corpus docs | Static, design-time | Yes — retrieved by `search_knowledge_base` tool | No (admin only) |
| **Workspace** | ❌ (T111) | Binary blobs of arbitrary size, content-typed, key-addressable | Per conversation, persistent across turns and sessions | Yes — as URL references, not inlined content | Yes — SSE bus (T113) + signed URLs |

Slots, memory and RAG cover the *small, structured, ephemeral, or
shared-static* states. Workspace covers the gap they all leave:
**large, unstructured, conversation-private, user-co-authored
artifacts**.

#### Why slots aren't enough

Three concrete reasons Turing today can't just "use a bigger slot":

1. **Slots embed in every system prompt.** The engine renders
   `Already collected: { ... }` on every turn. Putting a 50KB CSV
   there destroys cost economics — `gpt-4o-mini` at $0.15 / 1M tokens
   means every turn pays $0.0075 just to re-send the same CSV. Across
   a 20-turn conversation, **$0.15 in token bloat for one slot**.

2. **Slots are scalars + JSON.** No content-type semantics; no binary
   support. A PDF in a slot would mean base64-encoded text (33% size
   penalty + impossible to render natively in the portal).

3. **Slot SSE emits full snapshots.** The `TurChatSlotEventBus`
   broadcasts every mutation as the full map. With a 50KB blob in one
   slot, every other slot change re-pushes 50KB to every connected
   client. Workspace SSE (T113) emits only metadata
   (`{key, contentType, size, signedUrl}`), not content.

#### Three concrete scenarios

**Scenario 1 — B2B proposal flow (today's pattern, refactored)**

The Education customer's `compor_proposta_in_company` tool today
writes a ~1.5KB JSON to slot `proposta_in_company`, then
`gerar_proposta_pdf` reads the slot and renders a PDF via Code
Interpreter — the PDF URL goes into slot `proposta_pdf_url` and lives
in the date-bucketed Code Interpreter session dir.

With workspace:

```groovy
// compor_proposta_in_company:
def proposalData = composePricingAndModules(slots)  // 50KB rich object
workspace.put(
    "proposals/main.json",
    JsonOutput.toJson(proposalData).bytes,
    "application/json"
)
slots.set("proposal_artifact", "workspace://proposals/main.json")

// gerar_proposta_pdf, next turn:
def proposalBytes = workspace.get("proposals/main.json")
def pdfResult = code.executePython("""
    import json
    from reportlab.pdfgen import canvas
    proposal = json.loads('''${new String(proposalBytes)}''')
    # ... 4-page reportlab rendering ...
""")
def pdfBytes = http.getBytes("${pdfResult.fileUrl}")
workspace.put("proposals/main.pdf", pdfBytes, "application/pdf")
slots.set("proposal_pdf_artifact", "workspace://proposals/main.pdf")
```

**What this buys**:
- The 50KB rich proposal stays *outside* the prompt — LLM sees only
  `workspace://proposals/main.json (52KB)` reference (40 chars).
- The PDF survives the Code Interpreter date-bucket cleanup —
  workspace is durable storage, sandbox dir is ephemeral.
- Tomorrow's conversation resume (T121) still has the PDF.
- Portal sidebar renders: **📄 main.json (52KB) · 📑 main.pdf (340KB)**
  with direct download links via HMAC-signed URLs.

**Scenario 2 — Multi-document research session**

User uploads CV + job description + reference letter across 3 turns.
Today: each upload triggers `POST /chat/slot-extract` and writes a
handful of flat slots (`cv_name`, `cv_experience_years`, `jd_role`,
etc.) — losing the original files entirely.

With workspace:

```groovy
// Custom Tool handling each upload:
def fileBytes = http.getBytes("${args.uploadUrl}")
workspace.put("uploads/cv.pdf", fileBytes, "application/pdf")

// Later turn, "analyze_skill_gap" tool reads all three uploads:
def cvBytes  = workspace.get("uploads/cv.pdf")
def jdBytes  = workspace.get("uploads/jd.pdf")
def refBytes = workspace.get("uploads/ref.pdf")
def cvText  = tika.extract(cvBytes)
def jdText  = tika.extract(jdBytes)
def refText = tika.extract(refBytes)
// ... LLM call comparing the 3 texts ...
def analysisMd = "## Skills you have\n- ...\n\n## Skills the JD asks for\n- ..."
workspace.put("analysis/skill-gap.md", analysisMd.bytes, "text/markdown")
slots.set("analysis_url", workspace.url("analysis/skill-gap.md"))
```

**What this buys**:
- Originals preserved for audit / re-analysis with a different prompt.
- Analysis output is a real downloadable markdown file the user can
  share with a mentor / send to a coach, not just text in a chat
  bubble.
- The LLM can reference the analysis file by name in subsequent turns
  ("conforme analisado em `analysis/skill-gap.md`...") and the link
  resolves to a signed URL the user can open.

**Scenario 3 — Iterative content drafting**

Marketing agent helps a user iterate on a blog post over 5 turns:

```
Turn 1: User → "Draft a post about hybrid retrieval RAG"
        Tool → workspace.put("drafts/post-v1.md", "# Hybrid Retrieval\n...", "text/markdown")
        LLM → "Draft v1 ready: workspace://drafts/post-v1.md (2.1KB)"

Turn 2: User → "Make the intro stronger"
        Tool → workspace.get("drafts/post-v1.md") → revise → put("drafts/post-v2.md", ...)
        LLM → "Updated. v2: workspace://drafts/post-v2.md (2.3KB)"

Turn 3: User → "Add a comparison table"
        Tool → workspace.put("drafts/post-v3.md", ...)
        ...

Turn 5: User → "Publish this"
        Tool → workspace.get("drafts/post-v5.md") → POST to CMS endpoint
```

**What this buys**:
- Full version history of the draft — audit trail of every revision.
- User can download any prior version from the portal sidebar.
- The LLM's prompt stays clean: it sees only `workspace://drafts/`
  listing + the *latest* file content, never all 5 versions stuffed
  into the context window.
- If conversation is suspended and resumed days later, drafts are
  still there.

#### API surface — concrete

```java
public interface TurAgentWorkspace {
    /** Store bytes with content type. Overwrites if key exists. */
    void put(String key, byte[] content, String contentType);

    /** Convenience overloads. */
    void putText(String key, String content);                 // UTF-8 + text/plain
    void putJson(String key, Object obj);                     // serialized + application/json

    Optional<byte[]> get(String key);
    Optional<String> getText(String key);

    /** List entries under a prefix ("drafts/", "uploads/", "" = all). */
    List<WorkspaceEntry> list(String prefix);

    void delete(String key);
    void deletePrefix(String prefix);

    /** HMAC-signed URL with TTL; reuses TurCodeInterpreterUrlSigner. */
    String signedUrl(String key, Duration ttl);

    /** Total storage used by this conversation, in bytes. */
    long totalSize();

    record WorkspaceEntry(
            String key,
            String contentType,
            long sizeBytes,
            Instant lastModifiedAt,
            String writtenBy           // "tool:<name>" / "node:<id>" / "endpoint" / "user-upload"
    ) {}
}
```

From a Custom Tool Groovy script:

```groovy
workspace.put("reports/leads.csv", csvBytes, "text/csv")
def text   = workspace.getText("drafts/v3.md")
def url    = workspace.url("reports/leads.csv")   // signed URL, default 1h TTL
def drafts = workspace.list("drafts/")            // returns WorkspaceEntry[]
workspace.deletePrefix("drafts/")                 // cleanup older drafts
```

REST API (for portal + external integrations):

```
GET    /api/chat/workspace/{conversationId}                  → entries[]
GET    /api/chat/workspace/{conversationId}/stream           → SSE bus
GET    /api/chat/workspace/{conversationId}/files/{key+}     → signed redirect
PUT    /api/chat/workspace/{conversationId}/files/{key+}     → upload (admin / authorized tool)
DELETE /api/chat/workspace/{conversationId}/files/{key+}
POST   /api/chat/workspace/{conversationId}/snapshot         → returns ZIP of all artifacts
```

SDK hook (would land in `@viglet/turing-react-sdk@2026.3.x`):

```typescript
const { entries, signedUrl, isLoading } = useTuringWorkspace({
    conversationId,
    transport: "sse",          // matches useTuringSlots pattern
    prefix: "drafts/"          // optional filter
});

// Render the sidebar:
{entries.map(e => (
    <a href={signedUrl(e.key)} key={e.key} download>
        {iconFor(e.contentType)} {e.key} ({fmtSize(e.sizeBytes)})
    </a>
))}
```

#### How the LLM sees the workspace

Workspace contents are **NOT** dumped into the prompt. Instead, the
system prompt gets a single appended block listing what's available:

```
## Available workspace artifacts (read with the workspace.read tool)

- workspace://uploads/cv.pdf (application/pdf, 124KB, written by user-upload at 14:32)
- workspace://uploads/jd.pdf (application/pdf, 18KB, written by user-upload at 14:35)
- workspace://analysis/skill-gap.md (text/markdown, 4KB, written by tool:analyze_skill_gap at 14:38)
```

A new platform-level tool `workspace.read(key)` is always available;
the LLM calls it when it needs to read content. This is the
**offload pattern** — large content stays out of the prompt unless
explicitly requested. Pairs directly with T114 (auto-offload large
tool results) and T115 (memory compression to workspace summaries).

#### Path scheme — the multi-tenant story

Storage backend (NoOp / Filesystem / MinIO / S3) writes to a path that
reuses T78's tenant isolation verbatim:

```
{turing.storage.path-root}/tenants/{agentId}/{conversationId}/workspace/<key>
```

- Two conversations of the same agent never see each other's workspace.
- Two agents within the same Turing instance never see each other's
  workspace.
- Path traversal protected by `resolveSafe()` (already in
  `TurFilesystemStorageService`).
- HMAC URL signing (T79 `TurCodeInterpreterUrlSigner`) means stolen
  URLs can't be replayed beyond TTL.

Once T80 (container sandbox for Code Interpreter) ships, the same
container can bind-mount only its own conversation's workspace —
sealing the last leak vector.

#### IX.3.a `TurAgentWorkspace` service (T111)

Per-conversation, key-value-blob store. **Critical insight**: Turing
already has the storage abstraction it needs — `TurStorageService`
(NoOp / Filesystem / MinIO, configured via `turing.storage.type`).
Workspace is a thin façade over that:

```java
public interface TurAgentWorkspace {
    void put(String key, byte[] content, String contentType);
    Optional<byte[]> get(String key);
    List<WorkspaceEntry> list(String prefix);
    void delete(String key);
    String signedUrl(String key);  // uses TurCodeInterpreterUrlSigner pattern (T79)
}

// Scoped path: tenants/{agentId}/{conversationId}/workspace/<key>
// Matches T78 multi-tenant isolation verbatim.
```

**Why this is strictly better than Deep Agents' virtual filesystem**:

| Capability | Deep Agents virtual FS | Turing workspace |
|---|---|---|
| Storage backend | In-memory or implementation detail | **Pluggable** — NoOp/FS/MinIO/S3 per deployment |
| Multi-tenant | Implicit / unclear | **Path-isolated** by agent + conversation (reuses T78) |
| URL access | Not exposed | **HMAC-signed URLs** with cookie binding (reuses T79) |
| Content types | Strings | Binary blobs with MIME type — PDFs, images, CSVs |
| Eviction / TTL | Manual | Tied to `SubmissionRetention` config (T66) |
| External serving | None | Files directly servable from the deployed instance |

#### IX.3.b `workspace` Custom Tool helper (T112)

```groovy
// Custom Tool script can mix workspace with existing helpers:
def report = code.executePython("""
    import json, csv
    data = json.loads('''${slots.dataset}''')
    with open('report.csv', 'w') as f:
        writer = csv.writer(f)
        for row in data: writer.writerow(row)
""")
// code helper saved the file inside the sandbox; copy to workspace:
def csvBytes = http.getBytes("${report.fileUrl}")
workspace.put("reports/lead-export-${slots.export_date}.csv", csvBytes, "text/csv")

// Later turn, another tool reads the same workspace entry:
def previous = workspace.get("reports/lead-export-${slots.export_date}.csv")
```

This composes naturally with the existing `code`, `http`, `slots`,
`turingSearch` helpers — same `ToolContext` threading, same per-call
analytics.

#### IX.3.c Workspace SSE bus (T113)

Mirrors `TurChatSlotEventBus` (T54). Endpoint
`GET /chat/workspace/stream?conversationId=X` emits
`{event, key, contentType, size, signedUrl}`. SDK hook
`useTuringWorkspace<T>` consumes; portals render the active artifact
list as a sidebar — "files this agent built for you." Sub-100ms
latency, multiplexer pattern reused from T104.

### IX.4 Skills as first-class — the marketplace moat

Deep Agents mentions "skills" in passing. Turing makes them **typed,
versioned, marketplace-distributable units**:

```json5
// skill.manifest.json
{
  "name": "scaffold-markdown",
  "version": "1.2.0",
  "author": "viglet",
  "signature": "sha256:...",
  "description": "Generate beat-by-beat markdown scaffolds for narrative content",
  "personaFragment": {
    "instruction": "When asked to compose narrative content, structure as beats with timestamps..."
  },
  "tools": ["scaffold.markdown", "scaffold.pickQuote"],
  "workspaceDefaults": {
    "draft.md": "# {{title}}\n\n## Hook\n..."
  },
  "dependencies": ["http@^1.0.0"]
}
```

**Three platform skills ship in v1 — the helpers extracted in §VII.4**:
- `scaffold-markdown` — beat-structured markdown scaffolds
- `scaffold-quote-extraction` — `pickQuote(text, term)` heuristic
- `scaffold-mindmap` — `buildMindMap(abstracts, term)` for Mermaid trees

**The moat**: Deep Agents has skills as a *concept*; Turing makes them a
**portable, ZIP-distributable folder format** that round-trips with
Anthropic's own skill container (see §IX.4.d, which supersedes the
decomposed-manifest framing above).

> **A hosted public registry was dropped.** An earlier idea (a hosted
> `skills.turing.io` community/vendor catalog) was considered and is **not**
> a product direction — import/export ZIP (T319/T320) is the interop boundary;
> distribution beyond that is out of scope.

#### IX.4.d The pivot — Skill = Anthropic-standard folder, run in Docker (T317–T325)

> **Direction locked 2026-06-10.** After working the model end to end we
> reframed what a Skill *is*. The earlier design (T310–T315: attach a
> decomposed `TurSkill` to an agent, inject `personaFragment`, register
> `tools`, seed `workspaceDefaults`, persona-by-FK) is **superseded** and
> those task numbers are retired. The shipped T116 decomposed-manifest
> model (JSON columns for persona/tools/workspace/deps) is likewise
> **superseded by T317** — kept in CHANGELOG as history.

**The new definition.** A Skill is an **Anthropic-compatible folder** —
`SKILL.md` at the root plus `references/`, `scripts/`, `assets/` — stored
in object storage, edited like a mini-VS-Code, and **executed in a Docker
sandbox compatible with Anthropic's skill container**. Turing does **not**
port the skill into its own entities; it treats the folder as the truth
and runs it to the standard. Reference bundle (a deliberately complex
example) lives at `turing-marketplace/anthropic-skills/brand-content-studio/`.

**Why this shape wins.** It resolves every tension we hit:
- *No porting.* Scripts run as-is in Docker; no "script → Custom Tool"
  translation, no persona-FK, no decomposed manifest.
- *Compatibility is the moat.* The same folder runs in Claude and in
  Turing → Turing becomes an **entry/exit door** for capabilities
  (import any Anthropic skill ZIP; export Turing folders as Anthropic
  skills). Differentiation = following the market standard, not inventing.
- *Files-as-truth* reuses `TurStorageService` (a skill is just objects in
  storage) and Turing's existing ZIP export/import muscle.

**Hard requirement.** The feature is gated on **Docker** being configured
(Code Interpreter `DOCKER` mode, T80) — analogous to how `storageEnabled`
gates `/admin/assets`. NATIVE-only deployments don't get skills.

**The goal state.** The *same* skill runs in **chat** and in **semantic
navigation**, and a single imported **ZIP may carry many skills**, each an
independently-activatable unit.

**Tasks (dependency-ordered, each executable on its own):**

- **T323 — Use skill in chat. (shipped)** A session's available skills
  activate by description, but with a key design rule: **a skill always
  runs on the Global Settings Default LLM, never the caller's own model**,
  so a portable skill behaves identically regardless of which agent (or SN
  site) triggers it. Since a tool cannot switch the model that drives it,
  this is realised as a *delegated sub-loop*: the parent turn sees only the
  cheap name+description block + a single `run_skill(skill, task)` tool;
  invoking it spins a separate agentic loop on the Default LLM (reusing the
  T322 harness for the sub-loop's prompt + `load_skill`/`skill_bash` tools)
  that operates the skill end-to-end and returns the result. T324/T325
  inherit this rule.
- **T324 — Use skill in semantic navigation. (shipped)** Two parts. (1)
  *Activation* came free: the SN AI chat shares `TurAgentChatExecutor`, so
  T323's per-agent toggle already activates skills in SN mode (on the
  Default LLM, per the rule above). (2) *Turing search as a skill tool*:
  the skill's Default-LLM sub-loop now also gets the Semantic Navigation
  DSL toolset (`TurDslToolService` — list indices / get mappings / search /
  …, the same surface the semantic chat uses) alongside
  `load_skill`/`skill_bash`. The tools run in-process; the model bridges
  results into the sandbox `/workspace` via `skill_bash`. This is the first
  place a skill leverages Turing's own data — the v2 "integrate, via
  standard tools" step.
- **T325 — Multi-skill ZIP as independent units. (shipped)** Each skill
  in an imported ZIP is independently activatable (already true via T319,
  which splits every `SKILL.md`-bearing folder into its own indexed
  `TurSkill`) and now optionally selectable in chat as a distinct
  mode/"flow": an optional `selectedSkillId` rides the chat request and,
  when set, narrows the turn to that one skill (prompt block + `run_skill`
  tool); blank keeps the legacy all-skills progressive disclosure.

**Sequencing.** The whole authoring + interop layer has shipped: T326
(rollback), T317 (storage/index shape), T318 (the mini-VS-Code folder
editor at `/admin/skill`), T319 (import ZIP — entry door) and T320
(export ZIP — exit door). **T321 — the engine — has shipped**
(`TurSkillSandbox*`: a persistent `bash`-driven Docker session with the
skill folder mounted read-only and a workspace that survives across
turns, gated on `DOCKER` mode). **T322 — the activation harness — has
shipped** (`TurSkillActivationHarness`: progressive-disclosure prompt
block + `load_skill` / `skill_bash` tools over the T321 sandbox, gated on
storage + `DOCKER`). **T323 — use skill in chat — has shipped**
(`TurSkillRunnerService` + `run_skill` delegation, per-agent `skillsEnabled`
toggle, skill always driven by the Default LLM). **T324 — use skill in
semantic navigation — has shipped** (skill activation is free via the
shared executor; the skill sub-loop also gets the `TurDslToolService`
Turing-search tools). **T325 — multi-skill ZIP as independent units — has
shipped** (independent units via T319 import; per-conversation skill-mode
selection via an optional `selectedSkillId` on the chat request). The
whole §IX.4.d skill block is now complete. (A hosted public registry was
considered and dropped — not a product direction; ZIP import/export is the
interop boundary.)

### IX.5 Human-in-the-loop — make it a graph node, not magic

Deep Agents has HITL as middleware (the harness pauses for you when
appropriate). **Turing's better move**: HITL is a node type, visible in
the editor, observable in the analytics, auditable in the slot audit
log. Both HITL tasks — the async `humanApproval` node (T119) and the
live Spectator + Co-pilot mode (T120) — have shipped; see CHANGELOG §E.4.

### IX.6 Long-horizon tasks — explicit suspend / resume

Deep Agents pitches "long-running tasks" as runtime characteristic.
Turing's move: **explicit suspend/resume primitive** (T121).

```java
// Inside a Custom Tool or flow node:
flow.suspend("waiting_for_webhook:approval-system");
// → conversation row marked suspended; engine stops processing turns
// → REST endpoint enabled: POST /chat/resume/{conversationId}
//    body: { slotUpdates: { ... }, resumeReason: "webhook_received" }
```

External systems (Salesforce webhook, scheduled job, human approval
click) trigger the resume; flow advances from the suspend point with
the new slot values. Deep Agents implies you build this yourself with
generic state machines. Turing makes it a one-line authoring primitive.

### IX.8 Developer experience — CLI follow-ups

The `turing` CLI + `turing eval` shipped (T125–T127); these close the
honest gaps left in the first cut, all of which need a small **backend**
surface the CLI can read — they can't be fixed in the CLI alone.

#### IX.8.a Per-conversation tool-call trace (T427)

`turing eval`'s `assert.tool_called` is currently *best-effort*: it
infers a tool fired by scanning the T60 slot-audit for `source=TOOL`
writes, so a tool that runs without writing a slot is invisible and a
slot written by two paths is ambiguous. The fix is a read-only
`GET /api/chat/sessions/{id}/tool-calls` returning the tools that
actually executed per turn (name, arg digest, latency, ok/err). The data
already flows through `TurLoggingToolCallback`; this persists/exposes it
per conversation. Doubles as an observability surface (per-conversation
tool timeline) independent of the eval use.

#### IX.8.b `assert.outcome` terminal-state assertion (T428)

The CLI can assert slots and cursor node, but not the conversation's
*end state* (`CAPTURED` / `ABANDONED` / `HANDOFF`). That label is already
computed inside `TurAgentEvalRunnerService`; T428 lifts it onto a session
read endpoint (or the existing `/state` DTO) so a fixture can assert
`assert.outcome: CAPTURED`. Without it, "did the lead-capture flow
actually complete?" can only be approximated via slot presence.

#### IX.8.c Global chat-event tail (T429)

`turing logs` rides the T120 spectator SSE stream, which is
conversation-scoped — you must already know the `conversationId`. A
`turing logs` with no id should tail a firehose of recent chat events
across all conversations. T429 adds an admin SSE endpoint backed by a
bounded in-memory ring buffer (single-node caveat shared with the slot/
message buses) that fans recent turns out to the CLI.

#### IX.8.d `turing eval` CI gate (T430)

A reusable composite GitHub Action wrapping `turing eval` against a
deployed instance as a PR gate, plus an `npx @viglet/turing-cli`
quickstart in the docs. Pure packaging/docs on top of T126 — the
"every PR runs the eval suite" story from the original design.

### IX.10 What we explicitly do NOT copy from Deep Agents

- **"Skills" as undefined filesystem files** — Turing makes skills a
  typed, versioned, signed package format. Looser is worse here.
- **Always-on planning middleware** — Turing makes planning a node
  authors add when they want it. Always-on overhead is opinionated
  in the wrong direction.
- **Python-only SDK** — Turing's SDK is React-first
  (`@viglet/turing-react-sdk`), with backend served by Spring Boot Java
  21. The CLI (T125) is Node.js. **No Python lock-in** — every
  capability is REST-accessible.
- **LangGraph-style explicit state machine on top of an LLM** — Turing
  has the chat-flow DSL, which is a state machine but graphical and
  authorable by non-devs. We don't need a second layer.

### IX.11 Sequencing — what to ship first

Three groups, ranked by ratio of differentiation × Turing-infra reuse:

**Group 1 — workspace foundation** (~3 weeks, gates everything else):
- T111 `TurAgentWorkspace` service (1 week, reuses `TurStorageService`)
- T112 `workspace` Groovy helper (3 days)
- T113 workspace SSE bus (3 days, reuses `TurChatSlotEventBus` pattern)
- T114 auto-offload large tool results (1 week, reuses
  `TurToolCallbackPipeline` middleware seam)

**Group 2 — planning + cross-agent** (~2 weeks, reuses chat-flow + Custom Tool DSL):
- T108 `planningStep` node type (1 week, similar surface to `writeSlot`)
- T109 `agent.invoke(...)` helper (1 week, reuses agent + executor + analytics)
- T110 sub-agent analytics rows (3 days, extends T84 scorecard schema)

**Group 3 — CLI + eval** (~3 weeks parallel track):
- T125 `turing` CLI scaffold + dev mode (2 weeks)
- T126 `turing eval` (1 week, generalizes §VI.4.3 pattern)

**Group 4 — long-running + HITL** (~2 weeks):
- T121 suspend/resume primitive (1 week)
- T119 `humanApproval` node type (1 week, layers on T54 SSE)

**Group 5 — context enforcement** (~1 week):
- T123 token-budget enforcement (1 week, plumbing through T31 + T30)
- T115 memory compression to workspace (3 days, depends on T111)

**Group 6 — interop + ecosystem** (~3 weeks):
- T128 LangSmith export (1 week)
- T129 OTel export (1 week)
- T116 `TurSkill` entity + manifest format (2 weeks)
- T117 skills marketplace UI (1 week, internal-only v1)

**Total: ~14 weeks of focused work to reach Deep Agents parity +
overtake on three dimensions** (workspace, skills marketplace, eval
harness). (A hosted public skills registry, once floated as `skills.turing.io`,
was dropped — not a product direction.)

### IX.12 The competitive narrative — one paragraph

> "Deep Agents is an SDK harness for engineers writing autonomous agents
> in Python. Turing is the same capability surface — planning, virtual
> filesystem, sub-agents, context engineering, human-in-the-loop — but
> as a **platform** with multi-tenant deployment, graphical flow
> authoring, multi-LLM support, audit-grade observability, and a
> versioned skills marketplace. Self-host or cloud. Engineer or
> marketer can author. The customer's ops team can read every trace.
> Deep Agents is what you write; Turing is what you run."

---

## X. Native LLM SDK exploitation — beyond Spring AI 2.0

> Triggered by a 2026-05-25 audit (see Block F at §0) of the official
> **OpenAI Java SDK** (`com.openai:openai-java`, 4.36.0 series) and
> **Anthropic Java SDK** (`com.anthropic:anthropic-java`, 2.34.0
> series) against the surface area Spring AI 2.0 exposes (M2 in
> production today, M3/M4 on the branch). Block F is the executable
> task list; §X is the narrative companion that explains *what each
> SDK feature is, why Spring AI 2.0 doesn't ship it, and how Turing
> can exploit it for differentiation*.

### X.1 Why bypass Spring AI for some features (and not others)

Spring AI is a great portable layer for the **stable middle 60%** of
the LLM API surface: chat completions, streaming, tool calling,
structured output (basic), embeddings, vector stores, RAG advisors,
chat memory, observability via Micrometer. Turing should keep that —
all the existing `ChatModel` plumbing, `TurToolCallbackPipeline`,
`TurChatPromptAssembler`, `TurAgentChatExecutor` keep working
unchanged.

The audit found three classes of features Spring AI 2.0 does **not**
cover and where the official Java SDKs ship richer surface:

1. **Whole-new endpoints that didn't exist when Spring AI 1.x
   shipped**: OpenAI Responses API, Realtime API, Batch APIs of both
   vendors, OpenAI Evals / Stored Completions / Distillation, Anthropic
   MCP Connector / Files API / Token Counting. Spring AI 2.0
   milestones M3/M4 added some of these — most stay open issues.
2. **Server-side built-in tools**: every "the model executes a tool
   in the vendor's infrastructure with no client code" feature
   (OpenAI `web_search` / `file_search` / `code_interpreter` /
   `computer_use` / remote `mcp`; Anthropic `web_search` /
   `web_fetch` / `code_execution` / `bash` / `text_editor` /
   `memory_20250818` / `computer_use_20251124`). Spring AI's tool
   surface is client-side `MethodToolCallback`-only.
3. **Provider-specific governance**: `safety_identifier`,
   `omni-moderation-latest`, `service_tier`, `reasoning.summary`,
   `predicted output`, Anthropic Citations with the Search Results
   content type, Admin API / Usage API for per-tenant cost tracking.

For these three classes Turing introduces a **`TurNativeProviderClient`**
seam (T130). The existing `TurGenAiLlmProvider` gains two parallel
factory methods — `createNativeOpenAiClient()` and
`createNativeAnthropicClient()` — returning `Optional<NativeClient>`
that defaults to `empty()` and gets populated by the OpenAI / Anthropic
providers only. A sibling executor `TurNativeChatExecutor` routes a turn
through the native client when both `TurLLMInstance.vendor` and the
per-instance capability matrix agree on a feature; otherwise it returns
empty and the caller delegates to the Spring AI collaborator graph
verbatim.

**Migration philosophy**: this is **not** "rip out Spring AI". It's
"keep Spring AI as the default, opt into native paths per capability
per agent per LLM instance." The per-LLM-instance capability matrix
(T132) makes that opt-in explicit: an admin enabling `openai-web-search-tool`
on instance X turns on T133 for that instance only, every other
instance keeps falling through to the Spring AI executor. Block F has
**no monolithic flag day** — features ship one at a time, each guarded
by its own capability row.

### X.5 MCP Connector — both vendors (T144–T146)

OpenAI's remote `mcp` tool (T138) and Anthropic's MCP Connector (T144)
both let the model call MCP servers directly. **T146 inverts the
integration story** — Turing's MCP server, hosted at
`https://<turing-host>/mcp`, becomes a **public consumption surface**
for any MCP-compatible client (Claude Desktop, Cursor, OpenAI
Responses agents, future ones). Instead of customers integrating
Turing's API into their app, their LLM clients integrate Turing.

The strategic shift: **Turing competes for being the search
infrastructure inside other AI products**, not only for being the
customer-facing chat UI. Both markets coexist; the MCP route adds a
second.

**T192 inter-tenant federation directory** at `directory.turing.io`
is the long-shot network-effect bet. Tenants opt-in publish a
redacted MCP server (public docs, public catalog). Subscribers add
it to their agent's allow-list. Every customer that publishes makes
every other customer's agents more useful. Long-tail moat.

### X.6 Voice agents (T147–T151)

OpenAI Realtime is bidirectional speech with native tool calling and
reasoning. The hosted infrastructure handles the audio; Turing
provides the tool callbacks (the same SN search tools the text chat
uses) and the conversation orchestration. **T149 voice-first kiosk
mode** is a high-impact demo for trade shows, lobby kiosks,
accessibility deployments — Turing differentiates with "voice +
enterprise search + persona-tone + citations" in one product.

**T150 `gpt-realtime-translate`** is the asymmetric win: a single
operator can serve multilingual customers in real time. Visitor speaks
PT, agent answers PT, operator's spectator panel shows live EN
translation. Cuts the "multilingual support staff" line item for
customers with global footprints.

**T151 voice authoring of chat-flows** ties Realtime to the existing
chat-flow live preview (T95): admin says "add an `aiQuestion` for
cargo, then route by role" → Realtime parses → emits JSON patch →
graph updates live in the editor. **Authoring with voice is a
demo-grade differentiator** in a market where everyone authors with
forms.

### X.7 Citations & verifiable RAG (T152–T155)

Anthropic Citations + the new Search Results content type close the
biggest single trust gap in enterprise RAG: **provability**. T154's
chat UI underlines the exact span Claude derived each claim from,
with a hover popover linking to `TurSNDocument` and a deep-link to
the chunk position. PDF citations show `(p. 3)` chips. Combine with
T155 citation drift detection and Turing answers the compliance
question "can you prove what document this answer came from, and
whether that document has changed since the answer?" with a recorded
yes.

This is **the single highest-leverage UX move in Block F** for
enterprise-search customers. Every other RAG platform shows source
docs separately; Turing underlines the sentence.

### X.8 Batch processing + token economics (T156–T162)

Both vendors offer Batch APIs at **50% off**. The candidates inside
Turing are obvious — every workload that's bulk + non-interactive
should go batch:

- T157 nightly AI Insights generation (already shipped via
  `TurLlmSummaryService`, just needs Batch routing).
- T158 bulk re-embedding on model upgrade.
- T159 eval runs (T126 regression suites).

T160 service tier routing maps stages: `chat.live` → `priority`
(interactive SLA), `chat.background` → `flex` (near-batch sync),
default `auto`. T161 Anthropic token counting replaces the
`cl100k_base` heuristic with the vendor's exact count, plumbed into
the T123 budget-enforcement check. T162 surfaces a pre-flight cost
quote in the UI for cost-sensitive deployments.

**Economic impact** of Block F's batch path alone: the AI Insights
+ classification + LLM-Judge eval workloads roughly halve in cost at
zero quality loss. The customer doesn't notice; the bill drops.

### X.9 Memory tool + context editing (T163–T166)

Anthropic ships three independent context-management features that
each solve a different long-conversation pain:

- **Memory tool (T163)** — Claude gets a six-command file API
  (view/create/str_replace/insert/delete/rename) backed by
  `TurAgentWorkspace`. Persistent **per-conversation** memory by
  default; per-user via `memoryScope=user` (T166). The agent learns
  stable preferences ("Alex prefers terse answers") and surfaces them
  via "What this agent remembers about you" disclosure with a "Forget
  all" button (LGPD ready).
- **Context editing (T164)** — server-side auto-evicts old tool
  results when the window saturates, replacing them with placeholders.
  84% token savings on long agent loops. Composes with T29
  (Turing-side tool pre-filter) and T30 (BM25 memory retrieval) —
  Anthropic trims inline tool outputs, Turing surfaces the right
  retrievable older turn.
- **Compaction (T165)** — server-side summarization of older turns.
  Coordinates with Turing's T115 (workspace-stored compression):
  when both are enabled, Turing wins precedence (audit trail), Anthropic
  disabled to avoid double-summarization.

### X.10 Stored Completions + Evals + Distillation flywheel (T167–T170)

This is the **production self-improvement loop**:

1. `storedCompletions=true` per agent → every call stored on OpenAI
   side with structured metadata (T167).
2. T126 eval suite + T168 server-side OpenAI graders catch regressions
   automatically.
3. T169 distillation: monthly export → fine-tune `gpt-4o-mini` against
   prod-traffic-quality answers → swap `TurLLMInstance.modelId` if
   evals pass.
4. T170 closes the loop: operator thumb-up/down feedback feeds a
   DPO dataset → next fine-tune incorporates the preferences.

**Compounding effect**: the agent gets cheaper and better every week
with zero engineering work. T191 explicitly wires the three together
into "self-improving agent overnight" — the long-shot bet that's a
genuine moat.

### X.11 Predicted Outputs + strict structured outputs (T171–T174, T426)

T171 + T172 make the "regenerate-with-tweak" loop in the chat-flow
editor (T97 variant generator), persona-prompt editor, locale-string
editor, and the user-facing "rephrase shorter" feel **instant** instead
of 3–5 seconds. Wherever the output mostly overlaps an input string,
the speedup is 3–5×.

T173 + T174 replace the legacy `json_object` JSON-mode workarounds
(remembered explicitly as `feedback_gpt4o_mini_json.md` in agent
memory) with **strict schema-validated structured outputs**. Every
Turing path that parses LLM JSON — `LlmJudge`, T28 LLM intent
classifier, T97 variant generator, chat-flow router decision, and the
**AI Authoring chat** (`TurAiAuthoringService`, T426) — becomes
deterministic by construction. The class of "the model returned
truncated JSON / prose-around-JSON / an empty turn, the parser
exploded" bugs is gone. T426 specifically retires the
`BeanOutputConverter`-over-free-text path that drives the Intent /
Agent / Custom-Tool authoring forms onto a provider tool / strict
`json_schema`; the brace-scan-extract + graceful-fallback recovery
currently in that service is a band-aid until T173/T174 land.

### X.12 Files API + native PDF (T175–T177)

T175 mirrors `TurStorageService` entries into vendor Files APIs and
caches `file_id`s. The crawler integration (T176) makes Claude's
native PDF understanding the default for any PDF indexed against a
Claude agent — tables, layout, figures preserved. Pair with T152
citations and you get page-level provenance on PDF answers
out-of-the-box. T177 dedupes across agents in the same tenant —
one upload, many references.

### X.13 Reasoning surfacing + logprobs (T178–T180)

T178's "Why this answer" panel surfaces `reasoning.summary` (not raw
chain-of-thought) for o1 / o3 / o4-mini / GPT-5 reasoning models.
Elevates trust without exposing reasoning internals. T179 plumbs
`reasoning.effort` per-stage (live=low, background=high, judge=medium)
as a per-agent policy. T180 mixes `logprobs` into the T19/T24/T28
reranker confidence score — fixes the "the LLM hedged but we still
ranked it #1" failure mode.

### X.14 Safety, compliance, multi-tenant cost (T181–T186)

`safety_identifier` (T181) outsources abuse detection to OpenAI's
edge — Turing carries less compliance burden per tenant. T182
omni-moderation pre-filter blocks bad queries / bad indexed content
at the multimodal level (text + image), **free at the API tier**.
T183 + T184 + T185 build the per-tenant AI Spend dashboard backed
by both vendors' Usage APIs, with soft budget caps that
auto-downgrade to cheaper tiers when projections breach. **Compliance
+ FinOps + governance, packaged as part of the chat platform** —
the kind of "platform" attribute Deep Agents fundamentally can't
match because it's an SDK harness.

### X.15 The bold creative bets (T187–T192)

Six long-shot tasks that, taken together, redefine what an
enterprise-search-meets-AI platform looks like:

1. **T187 Live answers (hybrid)** — every answer can fuse indexed,
   web, fetched, and computed sources in one turn. Category killer.
2. **T188 Headless MCP backbone** — Turing as the search
   infrastructure inside other AI products. Doubles the market.
3. **T189 Real-time multilingual call center** — voice + citations +
   handoff. Replaces multilingual support staff.
4. **T190 Self-installing connectors** — `computer_use` agent does
   the integration setup. Long-tail SaaS connectors on demand.
5. **T191 Self-improving agents overnight** — distillation + evals +
   prod replay. Compounds with usage.
6. **T192 Inter-tenant MCP federation** — opt-in directory at
   `directory.turing.io`. Network effect moat.

### X.16 Sequencing — what to ship first

Block F orders into seven groups by leverage × infrastructure reuse.
**Group 1 gates everything else**; the rest can ship in parallel
streams.

**Group 1 — Foundation (T130–T132, ~3 weeks)**

- T130 `TurNativeProviderClient` seam (1.5 wk, reuses
  `TurLlmModelFactory` decryption/resilience)
- T131 `TurNativeChatExecutor` variant (1 wk, reuses post-T33
  collaborators)
- T132 capability matrix table + UI (3 days)

**Group 2 — Citations + RAG verifiability (T152–T155, ~2 weeks)**

Highest customer-visible UX win for enterprise-search positioning.
Anthropic-only initially (OpenAI doesn't have an equivalent yet);
generalizable later if OpenAI ships citations.

**Group 3 — Built-in server tools (T133–T138, T139–T143, ~4 weeks
parallel)**

T133/T139 (web_search both vendors), T141 (Anthropic code_execution
— FREE with web_search), T134 (OpenAI file_search). Drives T187
"Live answers" mode. T136/T142/T143 (computer_use + bash + text_editor)
later — high demo value, medium production priority.

**Group 4 — MCP federation (T138, T144, T146, ~3 weeks)**

T146 publishes Turing as MCP server (smallest, biggest market
expansion). T138 + T144 wire vendors to call any MCP. T145 unifies
the allow-list across vendors.

**Group 5 — Batch + cost economics (T156–T162, ~2 weeks)**

Pure cost-cutting; no UX surface. Roughly halves the bulk-AI bill.

**Group 6 — Memory + structured outputs + predicted outputs
(T163–T174, ~3 weeks)**

Foundational for the long-running chat experience + replaces JSON
guardrails.

**Group 7 — Stored completions + distillation flywheel (T167–T170,
~3 weeks)**

Production self-improvement loop. Wait until ~6 months of stored
traffic exists before triggering the first distillation cycle.

**Voice (T147–T151, ~3 weeks)** runs in a parallel track —
demo-heavy, ships best alongside Group 2 citations for the marquee
demo: "the agent answers your voice question with cited search
results."

**Total Block F: ~18 weeks focused work to ship every committed
(📋) task**, with 17 💭 long-shot tasks following on opportunity-cost
basis. Block F is structured so any single shipped task delivers
customer value — there is no monolithic "Block F is done" gate.

### X.17 The narrative — one paragraph

> "Spring AI is what Turing uses when the standard works. The official
> OpenAI and Anthropic Java SDKs are what Turing uses when the standard
> doesn't ship the feature yet. The seam between the two
> (`TurNativeProviderClient`) is small, observable, and per-LLM-per-agent
> opt-in. Block F is not a bet on bypassing Spring AI — it's a bet
> that the **next two years of LLM differentiation happens at the
> vendor surface**, not at the abstraction layer, and Turing is the
> first enterprise-search platform that exposes that surface to
> non-engineer authors through the chat-flow DSL, the agent settings
> UI, and the per-LLM capability matrix. The customer flips a toggle;
> Claude starts citing; OpenAI starts batching; the bill drops 50%.
> That is the platform pitch."

### X.18 Capability taxonomy & UX surfacing (T432–T435) — shipped

> **Shipped (2026-06-26).** The unified capability registry/taxonomy (T432),
> per-agent selection + union execution (T433), the capability-first agent
> picker (T434), and the registry-driven REQUEST_OPTION settings (T435) all
> landed — see [CHANGELOG.md](CHANGELOG.md) §X.18. The registry is the single
> source both the agent UI and the future T186 (instance × capability heatmap,
> §X.14.f) render from. Design detail is recoverable from `git log`.

---

## XI. Embedded LLM (Jlama) — downgraded to a spike

> **Status (2026-06-03): cut from a committed program to a 2-3 day spike
> (ROADMAP Block G, T193-spike).** The original §XI was a 26-task pure-Java
> in-process inference plan (provider + per-instance model cache + Panama vector
> bootstrap + tool-calling bridge). It is shelved because:
>
> - Jlama upstream is stagnant — last release 2025-01-01, single maintainer.
> - **Ollama-as-sidecar already delivers the same outcomes** (air-gapped, no API
>   key, working tool calling) in the same docker-compose, with far less risk and
>   clean Windows/CUDA support — and it is already a supported provider.
> - Committing 8 weeks to it contradicted the "courage to deprecate" principle
>   (STRATEGY §VIII.6/§VIII.7).
>
> **The spike's job** (T193-spike): benchmark Jlama vs Ollama-sidecar vs
> `de.kherud:llama` for the zero-egress story (tool calling, RAM, tok/s, Windows,
> ops burden) and pick **one** engine — only then consider re-opening a build,
> and only for a *named* customer requirement. Default assumption: Ollama-sidecar.
> The detailed Jlama design (former §XI.1-§XI.10) is recoverable from `git log`.

## XII. Admin chat ↔ `@viglet/turing-react-sdk` convergence — shipped

> Block H (T219–T232) is complete: SDK gained LLM listing/context-info, per-turn
> LLM override, real `AbortController`, multipart agent chat, plain-LLM hook, and
> the admin `chat.page.tsx` was migrated onto the SDK with a vitest smoke suite.
> T243 (shared `useStreamingChatCore`) has since shipped — see
> [CHANGELOG.md](CHANGELOG.md).

### XII.2 Headless UI library — `@viglet/turing-react-ui` (the what/why)

**Problem.** Turing's chat surfaces now live in two apps that must stay
visually independent: the **admin console** (React 19 + Vite + shadcn/Tailwind +
`@viglet/viglet-design-system`) and **viglet.com** (Next.js 15, its own brand).
As features land in both — code-interpreter HTML/canvas previews, `sandbox:`
artifact URLs, rich-content segmentation — components get **copy-pasted**
between them (the `SandboxPlayer` ⇄ `HtmlSandbox` duplication that triggered
this block). Putting them in `@viglet/turing-react-sdk` is wrong: that package
carries the **axios/API hooks** layer, and viglet.com deliberately stays on the
zero-dep, fetch-based `@viglet/turing-sdk` core (it must not pull axios).

**The layering decision (binding).** Three packages, one direction of
dependency:

| Package | Holds | Deps | Consumers |
|---|---|---|---|
| `@viglet/turing-sdk` | core logic — client, controllers, **pure helpers** (`splitRichContent`, `resolveSandboxUrl`, `absolutizeArtifactUrls`) | zero runtime deps | viglet.com, admin (via react-sdk), EDS |
| **`@viglet/turing-react-ui`** | **headless React components** | peer `react`/`react-dom` only — **no axios, no API** | viglet.com, admin |
| `@viglet/turing-react-sdk` | API hooks + observable stores | axios + the SDK core | admin (re-exports react-ui as a single import surface, T300) |

Pure, framework-agnostic logic belongs in the **core SDK** (tested there,
reused by every surface). React rendering belongs in **react-ui**. API/transport
stays in **react-sdk**. viglet.com composes core + react-ui + its own Next BFF
transport; it never sees axios.

**The headless contract (how we "don't commit to design").** A react-ui
component renders **structure only** — no colors, borders, or spacing of its
own (only functional layout, e.g. an iframe's `width:100%`/`border:0`). The host
app supplies the look through three injection points:

- `classNames` — a **per-slot** class map (`{ root, header, label, button,
  iframe, fullscreenRoot, … }`). The app passes its own Tailwind / CSS-module /
  vanilla classes. Crucially the classes are **literal in the app's source**, so
  each app's Tailwind scan resolves them and the **library stays Tailwind-free**
  (no shared config, no `content` globbing into `node_modules`).
- `labels` — localized strings (the app owns i18n).
- `icons` — `ReactNode` slots that **default to the label text**, so the library
  has **no icon-library dependency**; the app plugs in @tabler / lucide / svg.

This is why the admin (shadcn look) and viglet.com (its brand) share one
`TuringHtmlSandbox` implementation while keeping distinct appearances — each
app's `*-sandbox.tsx` is now a **thin skin adapter** (props only, zero logic).

**Packaging principles.** `tsc` → ESM `dist` (`"type":"module"`,
`sideEffects:false` for tree-shaking), peer-dep React (never bundled), consumed
via `workspace:*` (admin) / `file:` (viglet.com) symlinks — **rebuild the dist**
for consumers to pick up changes. SSR-safe (Next RSC): client-only concerns
(`createPortal`, `document`) are guarded; consumers import into `"use client"`
boundaries. Keep the package **publishable** (npm, semver) so external embedders
can use it too.

**Roadmap shape (see ROADMAP.md D.12).** Migrate by **ROI and design-weight**,
not all at once: design-less primitives first (sandbox preview ✅, rich-content
segmentation, markdown wrapper, D2 diagrams, code-block/loading atoms), then a
slot-driven message shell only if duplication keeps hurting. The fully-branded
**app shell** (layout, input bar, suggestion chips) stays per-app — extracting
it early produces an abstraction that fights both designs. DX backstops:
Storybook as the living "two-skin" design contract, jsdom render tests for the
headless contract, and packaging hardening (`exports` subpaths, a11y, SSR,
bundle-size budget) before a public release.

## XIII. Turing as an MCP Server — let Claude (and any agent) drive the platform

> **The pitch in one line**: stop asking customers to integrate Turing
> into their apps — let the AI client they *already pay for* (Claude
> Desktop, Claude Code, Cursor, ChatGPT, internal agents) connect to
> Turing in 30 seconds and search, answer, and act on their enterprise
> content. Turing stops being "a chat UI you have to adopt" and becomes
> **the grounding layer that lives inside whatever agent the customer
> already uses.**

> **This is the _server_ half of the unified MCP pillar** (STRATEGY §VIII.4).
> The _client_ half — Custom Tools as portable MCP servers + a Groovy->MCP
> exporter — is the same protocol bet in the other direction. Sell them as one
> line: *"Turing speaks MCP both ways — your tools go out, the world's agents
> come in."*

### XIII.1 The thesis — two markets, one endpoint

Turing today is consumed two ways: the public SN search/chat widget,
and the admin console. Both require the customer to **come to Turing**.
MCP inverts that: a `https://<host>/mcp` endpoint means **Turing goes
to wherever the customer's intelligence already lives**.

This is the §X.5 / T146 idea ("Turing's MCP server as a public
consumption surface") promoted from a paragraph to a real plan, because
the build cost just collapsed — see §XIII.2.

- **Ganho 1 (sell to the buyer)**: "Your team already has Claude. Point
  it at Turing and every employee can ask your intranet, your policies,
  your product catalog — *with citations* — without you building a
  single screen."
- **Ganho 2 (sell to the architect)**: one MCP integration replaces N
  bespoke REST integrations. Turing becomes one well-behaved node in a
  multi-tool agent graph (Turing search + Jira + GitHub + Google Drive),
  not a walled garden.
- **Ganho 3 (sell to the CFO)**: zero net-new seats, zero net-new UI to
  maintain. The interface is the LLM client the company already
  standardized on. Turing monetizes the **grounding**, not the chrome.
- **Strategic shift**: Turing competes to be the *search infrastructure
  inside other AI products*, not only the customer-facing chat. Both
  markets coexist; MCP adds the second one for almost free.

### XIII.2 What exists today — why this is mostly transport, not a rewrite

The senior-architect read: **the expensive 80% is already built.**

- **The tool catalog already exists** as Spring AI `@Tool` callbacks:
  `search_site` / `get_site_fields` / `list_sites`
  ([`TurLLMSemanticChatAPI`](../turing-app/src/main/java/com/viglet/turing/api/llm/chat/TurLLMSemanticChatAPI.java)),
  `search_knowledge_base` / `knowledge_base_stats` /
  `list_knowledge_base_files` / `get_file_from_knowledge_base`
  ([`TurRagSearchToolService`](../turing-app/src/main/java/com/viglet/turing/genai/tool/TurRagSearchToolService.java)),
  plus integration-monitoring, logging, and system-info tool services.
  These are the *same* tools the internal Semantic-Navigation agent uses
  — they're production-tested.
- **Descriptions are already curated** — real tool docs live in
  `prompts/tools/**/*.md` and are attached at runtime via
  `TurToolDescriptionService` + `TurToolCallbackPipeline.decorate(...)`
  (the `@Tool(description=".")` placeholders are intentional). MCP needs
  exactly these descriptions, so we get them for free.
- **The Spring AI MCP dependency is already on the classpath**
  (`spring-ai-mcp` in [`turing-app/pom.xml`](../turing-app/pom.xml)) —
  today wired only for the **consumer** path (`TurMcpServer` +
  [`TurMcpToolCallbackService`](../turing-app/src/main/java/com/viglet/turing/genai/tool/TurMcpToolCallbackService.java),
  where Turing calls *out* to external MCP servers). The server starter
  is the missing half.

**Therefore**: Block I is (1) add the server transport, (2) bolt on
auth + tenant/site scoping, (3) register the already-decorated callbacks
as the MCP tool surface, (4) add resources/prompts. We are **not**
re-implementing search, RAG, or the agent runtime. That's the whole
reason this is worth doing now rather than "someday".

> ⚠️ **The one hard part is not the tools — it's the trust boundary.**
> The internal agent runs the tool catalog *server-side, on behalf of a
> known agent*. An MCP server hands that catalog to an *external,
> partially-trusted client*. T246 (auth + scoping) is therefore the
> real engineering, and it must land before anything leaves loopback.
> Everything else is plumbing.

### XIII.3 The surface — tools, resources, prompts

MCP has three primitives; Turing should expose all three (most products
ship only tools and leave value on the table).

**Tools** (verbs the client can call):

| Tool | What it does | Backed by | Scope |
|---|---|---|---|
| `list_sites` | Discover SN sites + locales | `TurLLMSemanticChatAPI` | read |
| `get_site_fields` | Field map + which are facets | existing | read |
| `search_site` | Semantic / hybrid search, faceted | existing | read |
| `autocomplete` | Type-ahead suggestions | SN autocomplete | read |
| `get_document` | Fetch one indexed doc by id | SN store | read |
| `similar_documents` | KNN vector neighbors | §III vector store | read |
| `facet_search` | Faceted drill-down | existing | read |
| `rag_answer` (T248) | Hybrid search → grounded, **cited** answer in one call | §III RAG pipeline | read |
| `invoke_agent` / `list_agents` (T249) | Run a whole chat-flow headlessly, return answer + slots | `TurAgentChatExecutor` | read |
| `top_failed_searches` / `content_gaps` (T252) | "What are users asking that we can't answer?" | chat/search analytics | read |
| `index_document` / `reindex_site` (T253) | Push/refresh content | SN indexing | **write (gated)** |

**Resources** (nouns the client can read/attach — T250): expose indexed
documents and site schemas as `turing://site/{id}/doc/{docId}` and
`turing://site/{id}/schema`. This lets a client *attach* the source
document to its context window, not just receive a search snippet — the
difference between "here's a summary" and "here's the actual policy PDF,
reason over it."

**Prompts** (reusable templates — T251): publish each Persona and the
composed system-prompt fragments as MCP prompts. One pick in Claude's
prompt menu and the customer's brand voice + guardrails come along.

### XIII.4 The scenarios — productivity gains by persona (the sales engine)

Each row is a demo you can run on a customer call. The common shape:
*the customer keeps their tool, Turing supplies the truth.*

1. **Developer in Claude Code / Cursor** — "how does our auth refresh
   flow work?" Claude calls `search_site` over the company's
   Turing-indexed wiki + ADRs + code docs, answers *with links*, never
   leaving the editor. **Gain**: kills the context-switch to Confluence;
   answers are grounded in *current* internal docs, not the model's
   stale training data.
2. **Every employee via Claude Desktop** — the customer buys Claude for
   the org, points it at Turing, and the whole intranet/policies/HR-FAQ
   becomes ask-able with citations. **Gain**: enterprise search adoption
   with *zero* UI build and zero new login — the chat the company
   already uses *is* the search front-end.
3. **Content / editorial ops** — "list every page that still mentions
   the old product name, with URLs." `facet_search` + `search_site` →
   instant content audit. **Gain**: a half-day manual crawl becomes a
   sentence.
4. **Bulk ingestion by agent (T253)** — "index this folder of policy
   PDFs into the `compliance` site." Claude reads the files and calls
   `index_document`. **Gain**: content onboarding without the admin
   console or a connector project.
5. **Multi-tool orchestration** — "find the incident runbook in our KB,
   open a Jira ticket, and link the doc." Turing MCP (search) + Atlassian
   MCP (Jira) in one Claude flow. **Gain**: Turing becomes a *composable*
   node, not an integration dead-end.
6. **Answer grounding / anti-hallucination** — any agent calls
   `rag_answer` (T248) to ground a draft in enterprise truth before
   replying. **Gain**: the citation trail is the auditable defense for
   regulated answers.
7. **Self-service content-gap analytics (T252)** — "what did users
   search last week that returned nothing?" `content_gaps` → the product
   team sees exactly what content to write next. **Gain**: turns search
   logs into a backlog without a BI project.
8. **Admin introspection** — "which fields on the Products site are
   faceted?" `get_site_fields`. **Gain**: configure/debug from the same
   chat, no console hunting.
9. **Delegated guided flows (T249)** — an external orchestrator hands a
   whole lead-capture / triage chat-flow to Turing via `invoke_agent`
   and gets back the captured slots. **Gain**: reuse the customer's
   carefully-authored flows from *outside* the Turing chat widget.
10. **Turing-inside-an-ISV-product (B2B2C)** — a software vendor builds
    their own AI product and uses Turing MCP as the enterprise-search
    backend. **Gain**: Turing earns infrastructure revenue inside
    products it doesn't have to build (the §XIII.1 strategic shift).

### XIII.5 Architecture — how to build it (senior architect)

- **Transport (T245)**: add `spring-ai-starter-mcp-server-webmvc`,
  serve **Streamable HTTP** at `/mcp`. Prefer Streamable HTTP over the
  deprecated HTTP+SSE transport from the MCP spec; keep an SSE fallback
  only if a target client still needs it.
- **Tool registration (T245)**: build the server's `ToolCallbackProvider`
  from `TurToolCallbackPipeline.decorate(callbacks)` — the *same*
  decoration path the internal agent uses, so descriptions
  (`prompts/tools/**/*.md`), naming, and the placeholder-stripping all
  come along. **Never** register raw `@Tool` callbacks with the `"."`
  descriptions. This is a project invariant (see `agents.md`).
- **One catalog, two consumers**: the internal Semantic-Nav agent and
  the external MCP server should resolve tools through the *same*
  factory so a new tool shows up in both surfaces. Avoid a parallel
  hand-maintained MCP tool list — that's how the surfaces drift.
- **Large catalogs (T254)**: reuse **tool pre-filtering (T29)** so a
  client connecting to a tenant with dozens of tools gets a relevant
  subset, not a 60-tool wall that blows the client's tool budget.
- **Observability (T254)**: emit per-tool MCP call metrics by reusing
  the **T88 tool-latency** accumulator and **T32 meters** — the MCP
  surface must be as observable as the internal agent (p95 per tool,
  error rate, calls per tenant).
- **Resources/prompts (T250/T251)**: back resources with the SN store +
  schema introspection already used by `get_site_fields`; back prompts
  with the persona/system-prompt composition from §IV.5 + the persona
  domain.
- **Reuse, don't fork**: `invoke_agent` (T249) routes through
  `TurAgentChatExecutor` (do not duplicate the flow runtime); `rag_answer`
  (T248) routes through the §III RAG pipeline.

### XIII.6 Security, multi-tenancy & governance (the part that earns the enterprise check)

This is where the deal is won or lost in an enterprise review.

- **Auth (T246)**: implement the **MCP authorization spec** — Turing is
  an OAuth 2.1 **resource server** validating Keycloak-issued bearer
  tokens on `/mcp`. No anonymous access to a non-loopback endpoint, ever.
- **Scoping**: token → tenant → role → **allowed sites** + **allowed
  tools**. A marketing token sees the public catalog + read tools; an
  ops token additionally sees ingestion tools. Scoping is enforced
  server-side *inside* each tool callback, not just at the endpoint —
  defense in depth.
- **Read-only by default**: write/ingestion tools (T253) require an
  explicit write scope and stay off until a customer opts in. Same
  posture as the storage + Code-Interpreter sandbox lessons (§VII).
- **Per-tenant endpoints (T254)**: SaaS gets
  `https://<tenant>.turing.cloud/mcp`; the token decides which sites are
  even *visible*, so a misconfigured client can't enumerate another
  tenant's content.
- **Rate limiting & cost**: a public MCP surface is a new attack/cost
  surface. Per-token rate limits + the existing multi-tenant cost
  controls (§X.14) apply here too.
- **Auditability**: every MCP tool call is attributable to a token →
  user → tenant, and lands in the same audit trail as internal tool
  calls. This is the line that closes regulated verticals.

### XIII.7 DX & distribution — remove the last 30 seconds of friction

- **One-click client config (T255)**: the admin generates ready-to-paste
  `mcp.json` for Claude Desktop / Claude Code / Cursor — endpoint, auth
  hint, recommended tool subset. The gap between "we bought Turing" and
  "my Claude can search it" should be a copy-paste, not a docs safari.
- **Federation directory (T256, xref §X.5 T192)**: tenants opt-in to
  publish a *redacted* public MCP server (public docs, public catalog)
  to `directory.turing.io`; subscribers add it to their agent's
  allow-list. Every publisher makes every subscriber's agents more
  useful — a long-tail network-effect moat that pure-API competitors
  can't replicate.

### XIII.8 Sequencing — what to ship first

1. **T245** — transport + register the decorated catalog, **bound to
   loopback**. Demo-able same week against a local Claude Desktop /
   Claude Code. Proves the catalog reuse with near-zero new code.
2. **T246** — auth + scoping. The gate. Nothing reaches a non-loopback
   address before this is green.
3. **T247 + T248** — the read search surface + `rag_answer`. This is the
   "point Claude at your intranet, get cited answers" demo — the core
   sales moment.
4. **T249 + T250 + T251** — agents, resources, prompts. Depth that
   separates Turing from a thin search API.
5. **T252** — analytics tools. Easy, high-perceived-value, content-team
   love.
6. **T253 + T254** — gated writes + per-tenant SaaS. The monetizable,
   enterprise-grade surface.
7. **T255 + T256** — DX polish + the federation long-shot.

**Convicted first move**: ship **T245 against loopback** to prove the
"the tools we already have just *become* an MCP server" thesis in a
single demo, then immediately fund **T246** because every dollar of
this only unlocks once the trust boundary is real.

### XIII.9 The narrative — one paragraph

> "Turing already speaks MCP — as a client. It calls out to external
> tool servers today through `TurMcpToolCallbackService`. Block I flips
> the arrow: the same production-tested tool catalog the internal agent
> uses — `search_site`, `get_site_fields`, `search_knowledge_base`, the
> integration/logging/system-info tools, all already decorated with
> curated descriptions through `TurToolCallbackPipeline` — gets
> re-exposed at `https://<host>/mcp` so the AI client the customer
> already pays for can drive Turing directly. No new chat UI to build,
> no new seats to buy, one MCP integration instead of N REST ones. The
> hard 80% (the tools, the descriptions, the RAG pipeline, the agent
> runtime) is done; the work is transport, an OAuth-2.1 trust boundary
> with per-tenant/per-site scoping, and the resources/prompts that turn
> a search API into a first-class agent surface. The strategic payoff:
> Turing stops competing only to be the customer's chat window and
> starts competing to be the **grounding layer inside every other AI
> product** — a second market, opened for the cost of plumbing we mostly
> already own."

---

## XIV. Multi-tenancy — SHIPPED (Block J, T257–T284)

> Shipped 2026-06-11. The design rationale that lived here is preserved in git
> history; the operator + developer guide is [MULTI_TENANCY.md](MULTI_TENANCY.md)
> and the shipped task index is [CHANGELOG.md](CHANGELOG.md) → Block J.

### XIV.8 Cloud SaaS integration — one tenant per user (T333, T334, T335, T336, T365, T366 shipped; T373 open)

Block J gives *isolation*; the Viglet Cloud product needs *one environment per
signed-up user* on the shared turing/solr/postgres infra. The model: every Cloud
user becomes the OWNER of their own personal tenant, so isolation is automatic
and there is nothing to provision by hand.

**T333 (shipped 2026-06-12)** closes the only structural gap: the resolution
filter (T259) used to drop an authenticated principal with no explicit tenant
onto `DEFAULT` (shared). With the opt-in `turing.tenancy.auto-provision` flag on,
that principal instead gets a personal tenant + OWNER membership minted on first
request (slug derived from the username, race-safe reuse if a concurrent request
won), and resolves to it via membership on every later request. No Keycloak SPI,
no JWT `tenant` claim, and no console orchestration are required for the
single-Turing flow — Cloud just sets `TURING_TENANCY_ENABLED=true` +
`TURING_TENANCY_AUTOPROVISION=true`. Default off preserves the legacy
invite-based, many-users-per-tenant posture.

The remaining follow-ups are about making the *multi-product* Cloud seamless,
not about correctness of the single-service flow:

- **XIV.8.7 — ship Cloud default config as a GLOBAL seed ZIP (T373).** The
  product ask: a fresh Viglet Cloud deployment should come with a default LLM,
  default search engine, etc. already configured for *all* users, loadable purely
  from the Docker image with no post-deploy clicking. `application.properties` is
  the wrong tool — each instance has ~20 fields plus a `providerOptionsJson` blob
  and an encrypted API key. The right tool already exists: `TurExportImportOnStartup`
  scans `export/*.zip` on first boot and imports LLM/SE/Embedding/MCP/etc. through
  `TurImportExchange` (guarded once by the `EXPORT_AUTO_IMPORT` config marker). The
  only new guarantee needed is that the seeded BYO-infra lands in the GLOBAL pool:
  `TurInfraTenantScope.stampOnImport` already keeps `tenantId = null` when no tenant
  is bound to the thread (the startup import case), and the import code preserves
  whatever `tenantId` the bundle carries — so the work is to ensure the *export*
  side emits `tenantId = null` for instances intended as platform defaults, then
  bake the ZIP into the image (`COPY seed/defaults.zip /app/export/`). Two
  operational caveats: (a) every container must share the same
  `TurSecretCryptoService` key or the exported (encrypted) API keys won't decrypt;
  (b) the `EXPORT_AUTO_IMPORT` marker makes this one-shot per database — changing
  the defaults later means clearing the marker or a versioned re-seed, which is not
  yet modeled.

---

## XVI. Cost & token governance (Block L)

> **Shipped 2026-06-18** (T289–T291). Design realized; see
> [CHANGELOG.md](CHANGELOG.md) → Block L for the as-built summary. One deliberate
> deviation from the original sketch: the per-turn cost is captured on the
> always-on relational `llm_token_usage` table (extended with `agent_id` /
> `stage` / `cost_usd`) instead of a separate `TurChatCostSample` in the
> Mongo/Redis chat-analytics store — the relational path is always present, so
> cost data survives `turing.logging.engine=none`, which the "always works / $0
> local" goal requires. No active backlog remains here.

## XIX. Open-core publish mechanics (Block P)

> Added 2026-06-15. The *how* for the §XI source split (private `turing` →
> public `turing-ce`). Engineering design only; business/license rationale lives
> in [STRATEGY.md](STRATEGY.md) §XI.

### XIX.1 Source of truth + direction

Private `turing` is the **only** place development happens. `turing-ce` is a
generated artifact — never accept commits there as truth. The flow is strictly
one-way (private → public); community PRs are *backported* into private `main`
(§XIX.5), never merged as the public repo's own history.

### XIX.2 The denylist — the single rule

The checked-in [`ce-exclude.txt`](../ce-exclude.txt) at the private repo root is
the authoritative boundary (T356 ✅), reviewed on every release. It is the single
source of the public/private rule; the publish script (T357) applies it and the
secret-scan gate (T358) is only the backstop. Highlights:

- `docs/STRATEGY.md`, `docs/IMPROVEMENTS.md`, `docs/ROADMAP.md`,
  `docs/products/**` — the thinking docs (§XI.1 test #1).
- Internal AI/contributor instructions (`agents.md`, `CLAUDE.md`,
  `.claude-instructions`, copilot-instructions), internal publish workflows +
  the GitHub Pages site, any secret/credential/`.env.local`/keystore, scratch
  dirs.

It is gitignore-style: default-**deny** all of `docs/**` design content with
explicit re-includes for the user-facing docs (CHANGELOG, RAG_HYBRID_CONFIG,
MULTI_TENANCY, chat-flow samples), default-**allow** for code. The public README
is a *separate curated file* (`README.ce.adoc`), not the internal `README.adoc`.

### XIX.3 Squash-snapshot, not history mirror

Implemented in [`scripts/publish-ce.sh`](../scripts/publish-ce.sh) (T357 ✅).
The publish builds a clean tree at the release ref (`git archive`), applies the
denylist extracted from that same ref, and **appends a single squashed commit**
("Release vX.Y.Z") onto turing-ce's default branch — discarding private history
(§XI.2). Each release == exactly one public commit, leak-proof by construction.
`git filter-repo --invert-paths` is the fallback if a full filtered history is
ever wanted, but the snapshot model is simpler and cannot leak a commit title.

The curated public README ([`README.ce.adoc`](../README.ce.adoc), T359 ✅) is
promoted by the script to `README.adoc` in the public tree; the internal
`README.adoc` is excluded by the denylist.

### XIX.4 Secret-scan gate (safety net)

Implemented in [`scripts/publish-ce.sh`](../scripts/publish-ce.sh) `run_secret_scan`
(T358 ✅). Two layers run over the *assembled* tree (after the denylist has
already removed the known-private paths) and the publish **fails closed** on any
hit:

1. **Built-in pattern scan** (always on, no external dep) — internal hostnames,
   customer names, and high-signal secret markers from the itself-denylisted
   [`scripts/ce-deny-patterns.txt`](../scripts/ce-deny-patterns.txt). This is the
   layer the entropy scanners can't provide; the pattern file is where the
   maintainer curates real customer/host names *without* committing PII to the
   public tree.
2. **gitleaks / trufflehog** for entropy + known credential formats; a real run
   of one of these is mandatory before `--push` (override: `--allow-no-scan`).
   gitleaks runs with [`scripts/gitleaks.toml`](../scripts/gitleaks.toml) (built-in
   ruleset + a tight allowlist for known false positives — H2/Liquibase
   auto-generated `CONSTRAINT_*` PK names and Hibernate `FK*` names that the
   `generic-api-key` rule would otherwise flag across `db/changelog/**`).

Matches are redacted in the output so a secret can't re-leak through a CI log.
The denylist is the policy; the scanner is the backstop — a missed path must
never reach a public, indexed, un-deletable mirror.

### XIX.5 Contribution inflow + CLA

Implemented (T361 ✅): [`CONTRIBUTING.md`](../CONTRIBUTING.md) + the verbatim
[`DCO`](../DCO) 1.1 text + a [`PULL_REQUEST_TEMPLATE.md`](../.github/PULL_REQUEST_TEMPLATE.md)
checklist all live in the private repo root and are **not** denylisted, so they
flow into the `turing-ce` snapshot. CONTRIBUTING documents the snapshot/backport
flow from the contributor's POV (open PRs on `turing-ce`; a maintainer integrates
accepted changes and they ship in a following release) and requires a **DCO
sign-off** (`git commit -s`) on every commit instead of a separate CLA. The DCO
keeps contributions under Apache 2.0 and, with the single copyright owner,
preserves the relicensing option (§XI.4) — the *rationale* stays private; the
public doc only states the sign-off requirement. Enforcement can be added later
via the DCO GitHub App on `turing-ce` (UI-installed, no in-repo workflow).

### XIX.6 CI wiring

Implemented in [`publish-ce.yml`](../.github/workflows/publish-ce.yml) (T360 ✅),
beside [`publish-docker.yml`](../.github/workflows/publish-docker.yml) (which
already names `openviglet/turing-ce` for the *image*). The source publish is the
same idea for the *tree*: it installs gitleaks (making the T358 scan real),
resolves the release ref, and runs `scripts/publish-ce.sh --push`.

Trigger is **`workflow_dispatch`** only (with a `push` boolean defaulting to a
dry run). There is deliberately **no on-tag trigger on the private repo**:
release tags/Releases are created on `turing-ce` itself by
[`publish.yml`](../.github/workflows/publish.yml)
(`gh release create --repo openviglet/turing-ce`), not on `turing` — so a private
on-tag hook would never fire. The source publish is therefore the manual/dry-run
entry point and the building block the release pipeline calls; at release time it
must run **before** `gh release create` so the public tag points at the snapshot.

The snapshot lands on a `turing-ce` branch whose name **mirrors the branch the
run was dispatched from** (`github.ref_name`): a run on `2026.3` publishes to a
`2026.3` branch on `turing-ce`, **created as an orphan** if it does not exist so
it never inherits another branch's history.

Push reuses the existing **`GH_PKG_TOKEN`** secret: a cross-repo write credential
is **required** because the default `GITHUB_TOKEN` is scoped to the private
`turing` repo and cannot push to `turing-ce` — even within the same org.
`GH_PKG_TOKEN` already pushes git content to `turing-ce` (the github-pages branch
and releases), so it has the needed `contents: write` scope. The token is
injected via git's `insteadOf` rewrite so it never reaches a log; an SSH deploy
key on `turing-ce` is the equivalent alternative.

---

## XX. Structured-source indexing — the cursarei / Dumont reference (Block R)

> **Origin.** The [cursarei](https://cursarei.com.br) project independently built a
> Next.js course aggregator with its own crawler, lexical+semantic search, NL filters
> and a grounded LLM copilot — i.e. it reimplemented, by hand, the four things the
> Viglet stack already does as products (collect ↔ Dumont; search/semantic/NL ↔
> Turing SN; RAG ↔ Turing GenAI; editorial content ↔ Shio). Its `V##` track migrates
> those backends onto Viglet. This section is the **Turing side**: what the platform
> needs so a structured, public-data catalog (~4,000 courses, 120+ sources) is a
> first-class SN citizen for faceted search, similar-items and grounded chat. The
> collection side lives in Dumont (`dumont/docs`, tracks A–C); the two move together.
>
> **Two invariants run through every task here** and are the reason they're grouped:
> 1. **Conservative grounding.** The catalog is built from official public sources;
>    a field the source doesn't declare must be *absent* in the index, not `""`.
>    Anything that coerces absent→empty silently corrupts faceting and `exists`
>    filters. This is a correctness contract shared with Dumont **D03**.
> 2. **Objective ranking.** Public-data aggregator → no "preferred/partner" boost,
>    ever. Hybrid/semantic ranking must order by relevance and neutral signals only.
>
> **Amplification (2026-06-16).** T381–T385 were scoped narrowly as the cursarei
> migration's indexing half. Read together they are the foundation of a reusable
> platform capability: **Turing as a declarative, self-hosted structured-search engine**
> for public and enterprise catalogs — the open-core Algolia/Coveo posture in
> [STRATEGY.md](STRATEGY.md) §XIII. T386–T393 (XX.6–13) are that productization, each
> built on an existing Turing seam so the marginal build cost is low: schema-as-code
> (T386), LLM-assisted onboarding (T387/T391), data-quality observability (T388), a
> pluggable ranking pipeline reusing the Block N reranker (T389), cross-source dedup
> (T390), a grounded catalog copilot over the GenAI stack (T392), and an eventual
> `viglet-core` lift (T393). All keep both invariants above. cursarei stays the first
> reference customer, not the ceiling.

### Sequencing

T381 first (contract + test, unblocks the rest), then T382 (provisioning) since both
Dumont **D10** and cursarei **V05.0/V01** need a schema to target. **T386–T388** harden
the foundation cheaply on top of T381/T382 (schema-as-code, derivation, observability) and
are the highest leverage-to-cost of the amplification. T383/T384/T385 are the
user-visible search wins and can follow independently as cursarei `V02–V04` ask for them;
**T389/T390** layer onto T383/T384 once those land. **T391** (wizard) and **T392**
(copilot) are capstones — schedule them only after their dependencies are real, since they
are integration seams, not new primitives. **T393** shipped the `viglet-core` lift (the
neutral grounding + manifest kernel is now `viglet-core-manifest`; see CHANGELOG). None of
these block the Dumont collection work — a sidecar
(D10) can index into a hand-provisioned site before T382 lands.

## XXII. Agent↔UI transparency & client tools — AG-UI parity (Block W)

> **Why now.** A capability review against CopilotKit's **AG-UI** protocol
> (the bi-directional agent↔frontend event standard) found Turing's JS SDK at
> near-parity on the common copilot surface — token streaming, bi-directional
> shared state (**Slots** ≈ `STATE_SNAPSHOT`/`STATE_DELTA`, with the T63 delta
> SSE), generative-ish UI (rich content + native forms T107 + option chips),
> multimodal upload (T401), and per-conversation artifacts (**Workspace** T113,
> which AG-UI has no equivalent for). Two AG-UI capabilities are genuinely
> **absent**, and they are exactly the ones that make an agent feel like a
> transparent collaborator rather than a black box:
>
> 1. **Live tool-call visibility** — AG-UI streams `TOOL_CALL_START / _ARGS / _END`
>    so the UI can show "the agent is calling `search_flights`…". Turing runs the
>    whole tool loop **server-side** (`TurToolExecutionLoop`, see
>    [project_rc1_tool_execution_loop]) and the client only ever sees the final
>    text. The data exists server-side (T427 already computes a per-conversation
>    tool-call trace); it is simply never put on the live SSE.
> 2. **Frontend-defined ("client") tools** — AG-UI's `useCopilotAction` lets a
>    tool live in the **frontend**: the agent requests it, the browser executes
>    it (read the DOM, call a first-party API the server can't reach, render a
>    component), and the result is fed back into the loop. Turing has **no**
>    mechanism for the agent to invoke client code; Slots + forms are a
>    data-driven contour, not a tool-call round-trip.
>
> **Non-goal clarification.** Turing is **the backend** — it is *not* trying to
> become a protocol adapter that fronts LangGraph/CrewAI/Mastra (AG-UI's other
> selling point). The goal here is UI-observable parity for Turing's *own*
> agent runtime, not multi-runtime federation. The optional canonical-protocol
> adapter (§XXII.6) is a 💭 bet, deliberately separated.

### XXII.1 Live tool-call events on the chat SSE (T436)

The chat SSE today carries `token` / `options` / `form` / `sources` event
types (`TurChatStreamEvent`). Add a **`tool_call`** event carrying a small
lifecycle payload: `{ callId, name, phase: "start"|"result"|"end", argsSummary?,
status?, durationMs? }`. The emit point is the existing server-side tool loop
(`TurToolExecutionLoop` / `TurNativeChatExecutor`) — wrap the per-tool
invocation with before/after callbacks that publish onto the same SSE the
pipeline already owns. **Opt-in per-agent** (`toolCallEventsEnabled`, default
off) so existing streams stay byte-identical and the IT corpus needs no
revalidation — consistent with [feedback_behavior_changes_optin_flag]. Args are
**summarized/redacted**, never the raw secret-bearing payload. Reuses the
trace-shaping logic from T427 so the live event and the read-only trace agree.

### XXII.2 SDK + headless UI consume tool-call events (T437)

Vanilla SDK: the chat controller parses `tool_call` and exposes a
`toolCalls: TurChatToolCall[]` array on the in-flight assistant message (and on
controller state for the active turn). React SDK: `useTuringChat` surfaces the
same on `ChatMessage.toolCalls`. `@viglet/turing-react-ui`: a new **headless**
`TuringToolActivity` atom (same skinning contract as `TuringThinkingDots` /
`TuringSourceChips` — `classNames`/`labels`/`icons`, zero styling) that renders
the running/finished tool list. Admin chat + viglet.com skin it.

### XXII.3 Client-tool protocol — backend round-trip (T438)

The agent requests a frontend tool: the loop emits a **`client_tool_call`** SSE
event `{ callId, name, args }` and **parks** the turn awaiting a result (same
suspend/resume machinery as the T119 `humanApproval` node — a parked turn keyed
by `(conversationId, callId)`). New endpoint **`POST /chat/client-tool-result`**
`{ conversationId, callId, result|error }` feeds the value back and the tool
loop resumes streaming. Allowed client-tool **names + JSON schemas are declared
per-agent** (so the model is told they exist and the backend can validate the
returned shape); an unknown/disallowed name is rejected. Timeout + abort path so
a never-answered client call can't wedge the conversation. Gated per-agent
(`clientToolsEnabled`).

### XXII.4 SDK client-tool registration (T439)

Vanilla SDK: `chatController.registerClientTool(name, async (args) => result)`;
on a `client_tool_call` event the controller runs the handler, POSTs to
`/chat/client-tool-result`, and the stream continues. React SDK: a
`clientTools` option on `useTuringChat` (`{ [name]: { schema, handler } }`).
Mirrors the `useCopilotAction` ergonomics so the migration story from CopilotKit
is a thin shim. Handler errors are returned to the agent as a tool error (the
loop decides whether to retry/abandon), not thrown into the UI.

### XXII.5 Generative UI via client tools (T440)

A client tool whose "result" is a **rendered component** rather than a data
value — AG-UI's tool-based generative UI. Built entirely on T438/T439: register
a tool whose handler renders a host-supplied React component (looked up in a
small component registry by name) with the agent-provided props, optionally
returning a structured value back to the loop when the user interacts (e.g. a
custom picker). Reuses the `TuringRichContent` segment-dispatch model (T295) so
generative segments and markdown/html/d2 segments share one render path. No new
backend beyond T438.

### XXII.6 Canonical AG-UI protocol adapter (T441, 💭 bet)

Optional, strategic: an endpoint that re-encodes Turing's chat stream into the
**canonical AG-UI event schema** (`RUN_STARTED`, `TEXT_MESSAGE_*`,
`TOOL_CALL_*`, `STATE_SNAPSHOT`/`STATE_DELTA`, `MESSAGES_SNAPSHOT`, …) so
CopilotKit's off-the-shelf React/Angular clients connect to a Turing agent with
zero Turing-SDK code. This is a distribution play (meet developers in the
ecosystem they already use), not core parity — depends on T436+T438 existing
first, and should only be promoted from 💭 if a named integration asks for it.

## XXIII. Creative experiences at the search × agent intersection (Block X)

> **The thesis.** Once Block W lands (live tool calls + client tools + generative
> UI), Turing holds a combination **neither side of the market has together**:
> agent frameworks (CopilotKit/LangGraph) have no enterprise search engine, and
> search engines have no agent runtime with generative UI. Every idea below is a
> bet that *only pays off because Turing owns both*. They are deliberately built
> as thin compositions of shipped primitives — none is a new subsystem.

### XXIII.1 Answer-as-an-app — search-grounded generative UI (T442)

The agent stops returning a paragraph and assembles a **live mini-app from the
indexed result set**: a comparison table, a configurator, a spec card, a
ranked-options widget. The `manifest` (T386) makes this tractable — the agent
knows `price` is `CURRENCY` and renders a slider, `releaseDate` is a date and
renders a timeline. Implemented as a generative-UI client tool (T440) whose
props are populated from a real SN search. Impossible in a pure-agent framework:
it has no typed index to draw from. → builds on T440, T386, T383.

### XXIII.2 Co-browse the search UI — the agent drives the real page (T443)

Slots already sync agent↔UI state; client tools (T438) let the agent mutate the
**host page's actual filters**. "filtra pros vermelhos abaixo de R$50" flips the
real facet checkboxes and updates the URL (`useTuringUrlSearch`). The chat stops
being a parallel box and becomes a remote control for the page the user is
already looking at — a demo that instantly explains why search+agent together
beats either alone. → builds on T438/T439, `useTuringUrlSearch`.

### XXIII.3 Conversational glass-box ranking (T444)

Ranking explainability (T389) exists as data; make it dialogable. "por que esse
resultado é o #1?" → the agent narrates the real signals (BM25 vs vector in the
T383 HYBRID_RRF blend, the Block N reranker score, field boosts). A trust
feature that sells in regulated/enterprise procurement where "the AI said so" is
not an acceptable answer. → builds on T389, T383, Block N.

### XXIII.4 Ambient / proactive copilot (T445)

The inverse direction of slots: user interactions (clicks via
`useTuringClickTracking`, dwell, opened facets) are written as slots, the agent
watches the slot bus (T63) and **proactively offers help** without being
prompted — "vi que você abriu 5 políticas de devolução, quer que eu inicie
uma?". Throttled and opt-in (a proactive agent that interrupts constantly is
worse than none). → builds on T63, click tracking, slots.

### XXIII.5 Cross-conversation personal memory (T446)

The memory tool (F.8 / T163) is per-conversation. Add a **per-user, long-horizon
memory** ("voa econômica", "prefere docs em PT", "trabalha no time fiscal") that
survives across sessions, tenant-scoped for isolation, with a user-visible
"what the assistant remembers about me" panel + delete (privacy/GDPR posture is
a feature, not an afterthought). → builds on T163/T164, multi-tenancy.

### XXIII.6 Self-tuning agent — eval as continuous improvement (T447)

The highest leverage-to-cost bet: it is **almost entirely composition of shipped
parts**. A scheduled routine mines real conversations (via the T427 tool-call
trace + chat analytics) for failure signatures, drafts a prompt/flow change,
runs it against the Block K golden set, and — only if it scores better — opens a
**"suggested change" in the admin** (PR-style, human-approved). The eval loop
stops being a CI gate and becomes a self-improvement flywheel. → builds on
Block K (T285–T288), T427, routines.

### XXIII.7 Agent-to-agent handoff — a visible team (T448)

Today handoff is to a human, multi-channel (T286). Add **agent→agent** handoff: a
router agent delegates to specialists (each with its own skills/tools), and the
UI shows the conversation visibly "passing" between them — the Turing analog of
AG-UI's subgraph support. The T436 tool-call events already supply the
visibility surface. → builds on T436, T286, chatflow router.

### XXIII.8 Skills that ship their own UI (T449)

With generative UI (T440), an Anthropic-standard skill folder can embed its own
React components: the "travel-booking" skill ships its date-picker, the
"returns" skill ships its RMA form. Skills graduate from prompt+scripts to
**full vertical apps**, reinforcing the marketplace moat. A `ui/` convention in
the skill folder + a registration step in the activation harness. → builds on
T440, T323/T322 (skill activation).

### XXIII.9 Embeddable action widget — Turing as an action layer (T450)

The vanilla SDK already runs in EDS / plain `<script>`. With client tools
(T438/T439), the embedded widget **acts on the host site** — add-to-cart, fill a
form, navigate. Turing stops being "a search box you embed" and becomes an
**action layer over any website**, an entirely new sales proposition that
competes with browser-agent products without a browser extension. → builds on
T438/T439, vanilla SDK.

## XXIV. Reference showcase app — stress every feature (Block Y)

> **Goal.** One installable reference app that **exercises the maximum surface of
> the platform** — a living demo, an integration test of the SDKs against a real
> backend, and the canonical "this is what Turing can do" artifact for sales and
> docs. It lives in the monorepo at `frontend/apps/showcase`, seeds its own data,
> and is the kitchen sink: if a feature isn't visible here, it isn't real to a
> prospect.
>
> **Chosen domain: a commerce-catalog copilot** (working title "Atlas Store").
> Rationale — it is the most feature-dense domain: a typed catalog (price as
> `CURRENCY`, ratings, availability, images) stresses the manifest + hybrid
> ranking + facets; "compare these three" stresses answer-as-app; "add the red
> one to my cart" stresses client tools + co-browse; "why is this first?"
> stresses glass-box ranking; product images stress multimodal; a returns flow
> stresses HITL + handoff + skills. A single domain that naturally pulls in
> nearly every primitive beats a contrived feature-tour.
>
> **Coverage discipline.** Each showcase task names the exact features it must
> light up; a feature with no home in the matrix is a gap to flag, not to skip.

### XXIV.1 Coverage matrix — what the sample must stress

| Area | Features the showcase must exercise |
|---|---|
| **Indexing/schema** | manifest schema-as-code (T386), field provisioning (T382), LLM-assisted derivation from sample docs (T387), per-field coverage page (T388), import/export ZIP |
| **Search** | faceted search, URL-synced search (`useTuringUrlSearch`), autocomplete, spell-check, tabs (T247), sort options, similar/"more like this" (T400), click tracking + CTR, hybrid RRF ranking (T383), pluggable reranker (Block N) |
| **RAG chat** | token streaming SSE, RAG sources/citations (T152/T292), `TuringSourceChips`, option chips, native forms (T107), slots read/subscribe/write (delta SSE T63), multimodal slot upload + vision (T401), workspace panel (T113), rich content markdown/`html` sandbox/`d2` diagrams (T295–T298) |
| **Agent power** | skills (Anthropic folder, run in chat T323 + semantic nav T324, multi-skill ZIP T325), code interpreter (T80, structured output T83, warm pool T82), **live tool-call activity (T436)**, **client tools (T438/T439)**, **answer-as-app generative UI (T440/T442)** |
| **Creative bets** | co-browse the search UI (T443), glass-box ranking (T444), ambient copilot (T445), cross-conversation memory (T446), agent→agent handoff (T448), skills-ship-UI (T449), embeddable action widget (T450) |
| **Voice/multimodal** | voice agent (T147), search-by-image (multimodal upload as a search entry point) |
| **Ops/admin** | chat analytics (sentiment trajectory T87, tool latency p95 T88, SSE channel debug T90), agent eval + golden sets (Block K), self-tuning suggestions (T447), cost/token governance (Block L), token-budget COMPACT (T123) |
| **Platform** | multi-tenancy (each demo visitor in an auto-provisioned tenant T333), MCP server exposure (Block I — "drive Atlas Store from Claude Desktop"), webhooks (T62), routines/cron |
| **SDK surfaces** | vanilla `@viglet/turing-sdk` (an EDS-style embeddable widget build), React SDK hooks, headless `@viglet/turing-react-ui` skinned to the Atlas brand, CLI `turing dev`/`eval` (E.7) wiring the project |

### XXIV.2 Build tasks (T451–T457)

- **T451 — scaffold + seed.** New `frontend/apps/showcase` app in the Turborepo; a commerce dataset (≈200 products with typed fields + images); provision the SN site from a manifest (T382/T386), optionally demo derivation from sample docs (T387); ship an import ZIP + the `turing init`/`dev` flow so a clone-to-running path exists. Includes the brand skin of `@viglet/turing-react-ui` (T305 tokens).
- **T452 — search surface.** Full faceted + URL-synced search, autocomplete, spell-check, tabs, sort, similar docs, click tracking, with HYBRID_RRF on and the reranker selectable — the "search half" lit end to end.
- **T453 — chat surface.** RAG agent chat exercising streaming + sources/citations + `TuringSourceChips` + option chips + native forms (T107) + slots (read/write/delta) + multimodal upload + workspace panel + rich content (md/html/d2).
- **T454 — agent power.** A bundled skill (returns/RMA), code interpreter (a "compute my cart total / chart price history" tool), **live tool-call activity (T436)**, a couple of **client tools (T438/T439)**, and the **answer-as-app comparison widget (T442)** — the Block W + skills surface lit.
- **T455 — creative bets wiring.** Co-browse filters (T443), glass-box "why #1" (T444), ambient proactive copilot (T445), agent→agent handoff returns↔sales (T448), and an **embeddable action widget (T450)** demonstrated on a mock host page (add-to-cart from outside the app).
- **T456 — voice, memory & ops tour.** Voice agent (T147) + search-by-image; cross-conversation personal memory panel (T446); an admin "ops tour" route surfacing analytics (T87/T88/T90), eval + self-tuning suggestions (T447), and cost governance (Block L). Also expose the catalog over the MCP server (Block I) for the "drive it from Claude Desktop" demo.
- **T457 — guided tour, tests & docs.** An in-app guided walkthrough that narrates each capability as it's demonstrated; an e2e smoke test (the SDK-contract IT pattern — boot backend, import the showcase ZIP, drive search/chat/SSE/client-tool round-trip); a `docs-turing` page + README link so the showcase is discoverable. This task is also the **coverage audit**: reconcile the app against §XXIV.1 and file any uncovered feature as a follow-up.

## XXV. Client-side conversion analytics & the Google Analytics bridge (Block Z)

### XXV.0 The gap & the architecture decision

Turing already measures the funnel **server-side**: `TurChatSessionEvent` persists
`outcome` (`CAPTURED`/`ABANDONED`/`HANDOFF`), `experimentKey`, `variantLabel`,
`personaId`, cohort dims (T74), and the A/B stack (T67–T75) + the planned funnel
viz/replay (T85/T86) consume it. The CTR path (`postClick`) records search clicks.

What's missing is **client-side, marketing-owned** measurement. Customers run **GA4
in the browser**, where conversion lives next to acquisition source, campaign, media
spend and audiences. Three concrete questions the customer asks in *their* GA, not
ours: (1) **lead conversion** in chat & semantic navigation, (2) **drop-off /
abandonment**, (3) **which persona A/B variation was preferred**. These must be
emitted in the browser at the right lifecycle moments — critically, **abandonment**:
the server only infers it from a TTL, while the browser actually sees the tab close.

**Decision — do NOT hard-code GA into the SDK.** That would break the zero-dep /
framework-agnostic / EDS-friendly contract. Instead: a **canonical analytics event
bus** (zero-dep emitter) that the chat/search/slots controllers feed, plus
**pluggable sinks** — GA4 is the *first adapter*, not a dependency. This is the same
adapter-over-a-canonical-stream shape as the AG-UI bet (T441) and the slot SSE sinks,
so the infrastructure is reusable. The bus is no-op when no `analytics` option is
passed, so every existing embed is byte-for-byte unchanged.

### XXV.1 Canonical event bus (T458)

`createTuringAnalytics({ sinks, context })` returns a zero-dep emitter with a fixed,
provider-agnostic **event taxonomy** so dashboards/funnels are portable across sinks:

- Chat: `turing_chat_start`, `turing_chat_message_sent`, `turing_chat_step`,
  `turing_chat_lead_captured`, `turing_chat_abandoned`, `turing_chat_handoff`.
- A/B: `turing_ab_variant_assigned`.
- Search/SN: `turing_search`, `turing_search_no_results`,
  `turing_search_result_click`, `turing_search_refined`.

Controllers accept an `analytics` option and emit at their existing lifecycle points
(`createChatController`, `createSearchController`, `createSlotsController`). Every
event carries a common context envelope (`conversationId`, `agentId`/`site`,
`session_id` from `TUR_SESSION`) so sinks don't re-derive it.

### XXV.2 Pluggable sinks + GA4/GTM adapter (T459)

- `googleAnalyticsSink({ measurementId?, transport? })` — maps each canonical event
  to GA4 `gtag('event', name, params)` **and/or** a GTM `dataLayer.push`. It
  **auto-detects** an existing `window.gtag` / `window.dataLayer`: on an Adobe EDS or
  GTM-tagged host the bridge lights up with **zero extra config**.
- `onEventSink(fn)` — generic escape hatch (Segment/Matomo/Plausible later).
- `debugSink()` — console tracing for integration.

GA4 param names are snake_cased to GA convention; reserved GA params are avoided.

### XXV.3 Client-side abandonment & engagement (T460)

A small lifecycle watcher: once a conversation has ≥1 user message and **no**
conversion has fired, a `visibilitychange→hidden` / `beforeunload` / idle-timeout
flushes `turing_chat_abandoned` via `navigator.sendBeacon` (survives unload),
carrying `last_node_id`/`last_step`, `turns`, and elapsed time. This is the signal
the server cannot capture reliably and is the heart of "did the lead give up, and
where". Idle threshold and whether unload counts as abandonment are configurable.

### XXV.4 Conversion, funnel steps & A/B attribution (T461)

- **Funnel steps** — `turing_chat_step` per flow node reached (from the controller's
  view of flow progression / `currentNodeId`), the client-side mirror of the T85
  funnel so the customer builds a turn-by-turn drop-off funnel in GA4.
- **Conversion** — `turing_chat_lead_captured` fires on any of: a configurable
  **goal slot** being written (e.g. `email`/`phone`), a native form submit (T107),
  or the server reporting `outcome=CAPTURED`. The goal-slot definition is shared with
  the server funnel concept.
- **A/B attribution** — backend addition: expose `personaId` (and active persona
  name) on `GET /chat/state` alongside the already-present `experimentKey` /
  `variantLabel`. The SDK emits `turing_ab_variant_assigned` once resolved and
  **stamps `experiment_key` / `variant_label` / `persona_id` on every subsequent
  event**. That stamping is what lets the customer answer *"which persona variation
  was preferred"* with native GA4 comparisons/audiences — complementing the server
  scorecard (T69) and significance test (T68) rather than duplicating them.

### XXV.5 Search / semantic-navigation conversion (T462)

`turing_search` (query + result count), `turing_search_no_results` (the client-side
**content-gap** signal that also feeds T252), `turing_search_result_click` (mirrors
the existing `postClick` CTR — one call emits both server + GA), and
`turing_search_refined` (query reformulation). **Cross-surface stitching:** the
shared `TUR_SESSION` cookie ties a later chat lead back to the originating search
query, so GA shows the full *search → chat → lead* journey, not two disjoint funnels.

### XXV.6 React hook, showcase/EDS wiring & docs (T463)

`useTuringAnalytics()` in `@viglet/turing-react-sdk` (wires the bus into the existing
chat/search hooks) + a react-ui passthrough. Demonstrated end-to-end in the Block Y
showcase (T455/T456) as a GA4 search→chat→lead funnel, plus an EDS example block that
auto-detects the host `dataLayer`. A `docs-turing` page documents the event taxonomy
and GA4 funnel/audience recipes.

**Combines with:** A/B (T67–T75, the client attribution they lacked), funnel/replay
(T85/T86, shared step signal), content-gap (T252, the no-results event), self-tuning
(T447, conversion/abandonment as another failure-signal source), cohort dims (T74,
aligned GA dimensions), Block W (T436 `tool_call` SSE → a `turing_tool_call` event
for free), and the AG-UI adapter (T441, shared sink architecture). Non-goal: shipping
our own dashboards — GA4 (and any sink) owns visualization; we only emit clean events.
