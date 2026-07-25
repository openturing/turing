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
- [XII. Admin chat ↔ `@viglet/turing-react-sdk` convergence — shipped](#xii-admin-chat--vigletturing-react-sdk-convergence--shipped)
- [XIV. Multi-tenancy — one shared JVM, many isolated tenants](#xiv-multi-tenancy--one-shared-jvm-many-isolated-tenants)
- [XVI. Cost & token governance (Block L)](#xvi-cost--token-governance-block-l)
- [XVIII. Pluggable reranker — follow-up (Block N)](#xviii-pluggable-reranker--follow-up-block-n)
- [XX. Structured-source indexing — the cursarei / Dumont reference (Block R)](#xx-structured-source-indexing--the-cursarei--dumont-reference-block-r)
- [XXXIII. Evaluation platform — pluggable graders + datasets (Block AJ)](#xxxiii-evaluation-platform--pluggable-graders--datasets-block-aj)

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

#### IX.8.a Per-conversation tool-call trace (T427) — ✅ shipped

Shipped alongside Block W (T436) — see [CHANGELOG.md](CHANGELOG.md) §E.7.

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

### X.5 MCP federation — shipped (F.4)

> The F.4 MCP Connector block (T144 Anthropic connector, T145 cross-vendor
> federation, T146 public API-key-gated Turing-as-MCP-server) has shipped — see
> [CHANGELOG.md](CHANGELOG.md) §X.5. T146 already inverts the integration story:
> Turing's `/mcp` is a public consumption surface, so customer LLM clients
> integrate Turing rather than the reverse — Turing competes to be the search
> infrastructure *inside* other AI products, not only the customer-facing chat UI.
> The inter-tenant federation directory (`directory.turing.io`, the former T192
> bet) is **cancelled** — see STRATEGY §VIII.7.

### X.15 The bold creative bets — shipped

> T187 "Live answers (hybrid)", T188 "Headless MCP backbone", T189 "Real-time
> multilingual call center", T190 "Self-installing connectors", and T191
> "Self-improving agents overnight" shipped — see CHANGELOG §X.15.a … §X.15.e.
> The one remaining long-shot, T192 inter-tenant MCP federation, is **cancelled**
> (STRATEGY §VIII.7).

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
> source both the agent UI and the T186 instance × capability heatmap (shipped,
> CHANGELOG §X.14.f) render from. Design detail is recoverable from `git log`.

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

### XXII.6 Canonical AG-UI protocol adapter (T441, 💭 bet)

Optional, strategic: an endpoint that re-encodes Turing's chat stream into the
**canonical AG-UI event schema** (`RUN_STARTED`, `TEXT_MESSAGE_*`,
`TOOL_CALL_*`, `STATE_SNAPSHOT`/`STATE_DELTA`, `MESSAGES_SNAPSHOT`, …) so
CopilotKit's off-the-shelf React/Angular clients connect to a Turing agent with
zero Turing-SDK code. This is a distribution play (meet developers in the
ecosystem they already use), not core parity — depends on T436+T438 existing
first, and should only be promoted from 💭 if a named integration asks for it.

## XXVII. Public site & top-of-funnel conversion (Block AB)

> The marketing site (`turing.viglet.org`) as a conversion surface, not a
> brochure. Strategy/positioning is in [STRATEGY.md](STRATEGY.md) §XIV; this is
> the build rationale. Task status lives only in [ROADMAP.md](ROADMAP.md).

### XXVII.0 The gap & the framing

Benchmarking the site against Meilisearch, Supabase, Dify, PostHog, Deep Agents
and AG-UI showed the conversion blocker is **positioning → proof →
time-to-first-value**, not visual design (full analysis in §XIV). The build
answers the five 10-second questions a visitor asks, and turns artifacts we
already maintain (stars, CLI, ROADMAP/CHANGELOG) into trust signals.

The home of the site is `frontend/apps/site` (React 19 + Vite + Tailwind v4 +
shadcn, standalone, no SDK/backend dependency). Content lives in one place,
`src/lib/site-content.ts`; sections are thin renderers in
`src/components/sections/`. Deploy: `pnpm build:site` → `apps/site/dist/*` copied
to the Pages root by `publish-github-pages.yml`.

### XXVII.1 Repositioning & structure (shipped, T473–T476)

- **Hero** — launched analogy-first (*"the open-source alternative to Algolia"*);
  **revised platform-first by T630** to *"Enterprise search, cited RAG and AI
  agents — over your own content"* with the Algolia analogy demoted to a
  sub-line (§XIV.2). Leads with the differentiator, not the category anchor.
- **Search it → Ask it → Automate it → Run it** — four outcome pillars replace a
  flat feature grid, selling breadth as depth without diluting the message.
- **"Why Turing" comparison** — defensible, high-level table (Turing vs hosted
  SaaS vs DIY) with an explicit "as of 2026, verify vendor capabilities" caveat;
  attributes chosen to be true (license, hosting, data residency, unified
  agent+search) rather than feature trivia that ages or misleads.
- **In-page interactive search demo** — client-side filtering of a small sample
  corpus (`SEARCH_DOCS`) so the hero shows the product working with zero backend
  (the Meilisearch movie-demo pattern). It is explicitly labelled "sample data."
- **Near-free trust wins** — live GitHub-stars badge (`api.github.com`, graceful
  fallback), copy-paste quickstart (`npx @viglet/turing-cli init`), public
  Roadmap/Changelog links to the **canonical GitHub-rendered** markdown (not
  duplicated into the site — founding anti-duplication rule), and the CLI
  showcase section + the published `@viglet/turing-cli` README.

### XXVII.2 The live-demo linchpin (T477)

The defining bet (§XIV.3): a *truly* live demo. The hero search + cited-chat are
now live (T478, shipped — see CHANGELOG). Remaining under this section is only
the host bring-up and pointing the marketplace SPAs at the instance.

- **T477** — stand up a **public read-only Turing instance** with CORS + rate
  limiting, seeded with demo content; read-only/anonymous scope only, no write
  surface. **Realized in `viglet/cloud`** as a dedicated throwaway instance
  (`docker compose --profile demo`): a separate `turing-demo` + isolated
  `solr-demo`, `TURING_KEYCLOAK=false`/no-tenancy, **ephemeral tmpfs** store +
  index (wiped on recreate), `cap_drop: ALL`/`no-new-privileges`/mem-cpu-pids
  caps, **no `docker.sock`** (Code Interpreter DOCKER + skills off by
  construction), behind an nginx **deny-by-default allowlist** vhost
  (`turing-demo.viglet.org` → only `/api/sn/*`, `/csrf`, `…/semantic-chat`;
  admin/login/oauth/actuator/write → 404) with per-IP rate limits, plus a
  **systemd timer that recreates + re-seeds** periodically (the self-heal). Seed
  sources are committed (`demo-seed-src/mythical-creatures` + `turing-docs`);
  `docker/DEMO.md` documents the host go-live steps (DNS, real TLS, demo-agent
  budget cap). Pending = those host steps, not platform code.

### XXVII.3 Proof & deeper funnel (T479–T482)

- **T480** — hosted **"try it free"** path via Viglet Cloud (the dual-path CTA
  Meilisearch/Supabase use: self-host docs **and** a hosted sandbox), so visitors
  who won't install can still reach first value.
- **T481** — real **social proof** surface: adopters/logos, testimonials, and
  quantified case studies (the Dify model: "18,000 hours saved"). Gated on having
  real design partners — never fabricated.

**Non-goals:** do not duplicate ROADMAP/CHANGELOG content into the site (link to
GitHub); do not fabricate proof (logos/metrics) before real adopters exist; do
not over-claim in the comparison table (keep it defensible + dated).

### XXVII.4 Persona-adaptive & content-validation demo (T633–T638)

The PoC that won early interest highlighted two capabilities that are now deep in
Turing but invisible on the site: **ChatFlow** (declarative, deterministic
multi-step conversations) and **Personas**. A persona is a wildcard: as a SPEAKER
it fuses tone/verbosity/vocabulary/brand into the assistant's voice; as an
AUDIENCE it becomes a **spectator that validates content** — `TurPersonaContentFitEvaluator`
role-plays the persona as a reader and returns a structured verdict (fit %, what
does/doesn't fit, flagged spans + rewrites). "Cited RAG chat" is now commodity;
persona-as-content-validator is a genuinely uncommon, memorable differentiator.

**Positioning discipline:** these reinforce *Ask it / Automate it* — they are NOT
new top-level pillars (that would dilute the platform-first message and read as
complexity, per §XIV). The site sells the *outcome* ("same content, different
eyes"; "validate before you publish"), shown live in the playground, not the
machinery.

**Why these tasks exist (the API gap):** personas + content-fit are fully built
but only reachable **admin-secured**, and persona is bound per-agent / per-flow —
there is no per-request persona on the anonymous read-only path the demo uses, and
no anonymous content-fit endpoint. Rather than fake it with per-persona flows, we
evolve the product: a **catalog-restricted** per-request persona on `/api/sn/*/chat`
(T633) and a **rate-limited, input-capped** guest content-fit endpoint (T634) —
both safe because the persona must belong to the site agent's own catalog, and the
content-fit path is an LLM-cost surface that inherits the demo's nginx guard
(`docker/DEMO.md`). SDK (T635) + headless react-ui (T636) + demo seed (T637) then
let the playground (T638) dogfood it. The site becomes the forcing function that
turns two admin-only capabilities into public, embeddable product primitives.

## XXVIII. Provider coverage + cross-vendor capability exploitation (Block AD)

Turing already wires 6 chat provider types (`openai`, `anthropic`, `gemini`,
`gemini-openai`, `ollama`, `embedded`) through `TurGenAiLlmProvider` /
`TurGenAiLlmProviderFactory`, with embeddings on OpenAI/Azure/Ollama and Cohere
present **only** as a Block N reranker strategy. Block AD is deliberately *not*
"add provider X like the others." Block F.16 set the pattern: wiring a vendor is
table stakes; the value is in the **distinctive primitive each one brings** and
the **net-new product bridges** built on it. So this block has three layers —
the seams, the primitive-exploitation tasks, and the cross-vendor bridges — all
opt-in behind the existing two-level capability gate; empty/legacy config leaves
every current path byte-for-byte unchanged.

**Why these and not others:** the selection is driven by the product being an
*enterprise-search / RAG* platform — so the gaps that matter are an enterprise
gateway (Bedrock), a retrieval-specialist embed/rerank vendor (Voyage), a
GDPR-native European provider (Mistral), the GCP enterprise path (Vertex), and
the one-seam unlock of the entire OpenAI-compatible long tail. Pure-chat
novelty for its own sake is out of scope.

### Foundation — the provider seams

_Shipped (T505–T510) — see [CHANGELOG.md](CHANGELOG.md) Block AD._

### Exploit distinctive primitives — net-new retrieval capabilities

_Shipped (T511–T515) — see [CHANGELOG.md](CHANGELOG.md) Block AD._

### Cross-vendor bridges — the bold bets

_Shipped (T516–T522) — see [CHANGELOG.md](CHANGELOG.md) Block AD._

**Non-goals:** don't add a provider with no distinctive primitive or enterprise
reason just to lengthen the list (pure-chat clones belong behind T505's generic
seam, not as bespoke providers); don't make any new capability default-on (every
one is gated); don't let the managed-RAG seam (T520) fork the retrieval core —
it's an alternative backend, not a rewrite.

## XXIX. Documentation parity & didactic refresh — post-T410 (Block AE) — ✅ SHIPPED

> **Block AE (T524–T538) shipped 2026-06-30.** Per the founding rule, the detailed
> design rationale was removed once the block shipped. What shipped is indexed in
> [`CHANGELOG.md`](CHANGELOG.md) → Block AE; the living coverage gate is
> `docs-turing/COVERAGE.md` (every shipped block/feature → its doc page, or an
> explicit "deliberately internal" row). The house-style + didactic checklist that
> guided the round now lives on, applied, across the `docs-turing` pages themselves.

## XXX. Domain layer — bounded completion, stop-criteria & enforcement (Block AF)

> **Block AF (T540–T546) shipped 2026-07-02.** Per the founding rule, the design
> rationale was removed once the block shipped. The full decision — do NOT take
> the hexagonal migration to 100%; the value is the immutable/lazy-proxy-free/
> cacheable read model, so records + ports are bounded to hot / cached /
> cross-aggregate / LazyInit-prone reads while JPA repositories keep admin/CRUD
> and **all** writes — lives in the ADR:
> [`docs/adr/0001-domain-layer-bounded-completion.md`](adr/0001-domain-layer-bounded-completion.md),
> mirrored into `agents.md` and enforced by `TurDomainLayerConventionsTest`
> (T545). T546 collapsed the one pure-ceremony port (`TurCustomTool`, no mocking
> need) to a mapper-backed reader; the end state is **7 ports + 8 records + 1
> reader**. What shipped is indexed in [`CHANGELOG.md`](CHANGELOG.md) → Block AF.

## XXXI. Bento UI — complete the interface migration (Block AG)

> Design rationale for Block AG (T547–T572). The goal is to take the Bento
> redesign from a 3-screen `/bento` "Preview" to **100% of the admin
> interface**, then retire the legacy console. Guiding words from the ask:
> **fluido, conciso, amigável**.

### XXXI.0 Where we are

Bento is a **parallel** route tree mounted at `/bento` (see
`app/routes/bento.routes.tsx`), rendered inside `BentoRootPage` — a frosted-glass
shell with a sticky blurred header, ambient gradient blobs, iOS-style
route-hierarchy "up" navigation, a dark-mode toggle and a user menu. It carries a
"Preview" badge and does **not** replace the console. Migrated so far:

- `bento/home` — a launcher mosaic of feature tiles (privilege-gated), with an
  inline `ChatStarter` when a default agent + enabled LLM exist.
- `bento/ai-agent` — list (`bento-grid` of tiles) + detail dashboard.
- `bento/llm` — list + instance detail + form.

The **detail pattern is settled and worth codifying** because it's what makes the
rest cheap:

- `BentoHero` owns the entity identity — `BentoInlineEdit` title/description,
  a `BentoHeroIconPicker` (Iconify), and a status pill. In *existing* mode each
  field commits immediately via the update mutation (type → blur → saved); in
  *new* mode fields stage locally and the form's Save creates the entity.
- A scroll-linked **hero → sticky `BentoSaveBar` morph** driven by a single
  `--bento-fade` CSS custom property on `<html>` (rAF loop, zero React re-renders
  during scroll) — Save/Cancel live in the hero and fade into a fixed bar.
- Destructive actions live in a `⋮` `BentoActionsMenu`, never in the save bar.
- `BentoFormSection` groups the remaining fields under a tonal icon chip and
  **wraps the existing react-hook-form `FormField`/`Input`/`Select`/`Switch`
  unchanged** — so migrating a screen is re-skinning its layout, not rewriting
  its logic or validation.

The read paths already use React Query hooks (`api/queries/*.queries.ts`), which
the bento pages consume directly — no data-layer work per screen.

> **T547 shipped** — architecture & cutover decision now lives in ADR
> `docs/adr/0002-bento-ui-migration-cutover.md` (Bento-as-primary, route-swap +
> `<Navigate>` redirects, closed surface inventory). See CHANGELOG Block AG.

> **T548 shipped** — the shared scaffold (`BentoEntityShell` + `BentoListPage` +
> `BentoEntityTile`) is extracted and the LLM + AI-Agent surfaces re-skinned onto
> it as proof. Each Phase 1–4 screen is now a thin config + its `BentoFormSection`
> field groups. See CHANGELOG Block AG.

> **T549 shipped** — persistent frosted `BentoNavRail` (fixed left icon rail,
> desktop) over the migrated surfaces + a zero-dep `⌘K` `BentoCommandPalette`
> reaching every area (unmigrated ones link out to the console until they
> migrate). Both driven by a single `bento-nav.config.ts` registry that mirrors
> the console `AppSidebar` taxonomy. See CHANGELOG Block AG.

> **T550 shipped** — conventions codified in
> [`components/bento/CONVENTIONS.md`](../frontend/apps/turing-app/src/components/bento/CONVENTIONS.md)
> (i18n key reuse + object-form `t` defaults, a11y baseline, reduced-motion
> guard extended to the ambient blobs, responsive rules) + vitest coverage for
> all shared components under `components/bento/__tests__/`. **Phase 0 is
> complete** — Phases 1–4 now fan out against the scaffold. See CHANGELOG Block AG.

### XXXI.5 Phases 1 & the simple-CRUD tail (T551–T556, T562)

These are the BYO-infra and plain entity CRUDs — SE, Embedding Store, Embedding
Model, Token, Integration, MCP Server, Custom Tool, Routine, Chat Webhook,
Persona, Git, Page. Each is the LLM pattern: list of tiles → hero-driven detail →
`BentoFormSection`s wrapping the current fields. Grouped ~2 per task so each is a
shippable PR. Custom Tool and Skill keep their code editors inside a section (the
editor component is reused as-is). BYO-infra screens must keep the `T372`
GLOBAL-is-read-only behavior in the bento CRUD.

### XXXI.6 Semantic Navigation (T557–T559)

The heaviest area. Three tasks: the **instance** CRUD (identity + engine/store
bindings + config sections), the **fields** sub-CRUD (a nested editor — best
rendered as its own bento sub-surface, not a modal — plus the T388 coverage bars
and T386 manifest plan/diff), and the **onboarding wizard** re-skinned as a bento
stepper. Splitting keeps each PR reviewable.

### XXXI.7 Editors & browsers (T560–T561)

Skill (the T318 mini-VS-Code file-tree + editor) and Asset (file browser) aren't
form CRUDs — they're app-like surfaces. They mainly need to sit correctly inside
the bento shell (full-height layout inside the frosted chrome, the
`turing-resizable-panel` `overflowHidden` gotcha) rather than adopt the
hero/save-bar pattern. Reachable via T549 nav, not the home mosaic.

### XXXI.8 Functional & dashboard surfaces (T563–T566)

Chat, Conversation detail, the AI-Analytics dashboards (cost-governance,
chat-analytics, parked-conversations, capability-matrix), GraphQL explorer,
Dashboard, Marketplace, Voice Kiosk, Global Settings, Import/Exchange, Logging,
System Info. These are read-mostly / bespoke; the work is hosting them in the
bento shell + nav and applying the frosted-tile visual language to their existing
charts/panels — not the CRUD scaffold. Chart color rules follow the dataviz
guidance and the brand blue→indigo palette.

### XXXI.9 Admin & tenancy (T567–T568)

Admin settings (users/groups/roles/settings/tokens), Tenant settings, Tenant
admin, User account. The only subtlety is preserving the existing **Keycloak-vs-
JPA dependent-query pattern** (see `api/queries/admin.queries.ts`) — the bento
re-skin must not collapse the source discriminator that toggles cleanly between
backends.

## XXXIII. Evaluation platform — pluggable graders + datasets (Block AJ)

### XXXIII.0 The problem — eval is two hard-wired islands

Two independent eval subsystems shipped and both bake in *how* a case is scored:

- **Block K — Agent-CI eval** (T285–T288, `genai/eval/`). `TurAgentEvalRunnerService`
  replays each `TurAgentEvalCase` through the real executor and scores it on
  exactly **four baked-in dimensions** in one `scoreCase(...)` method: `slots`
  (deterministic diff), `outcome`, `node` (all deterministic), and `rubric` (one
  bilingual LLM judge, `TurEvalJudgePrompt`). You cannot add, remove, weight, or
  swap a grader without editing that method. There is **no human grader**. A
  "dataset" is golden cases typed inline into a single agent's set — not
  uploadable, not reusable across agents, not versioned. Aggregation is a hardcoded
  all-dimensions-must-match. Two offload paths already exist (T159 Batch judge,
  T168 OpenAI Evals) but only for the rubric judge, bolted on beside the runner.
- **Block R — NL→facet eval** (T385/T407, `sn/dsl/eval/`). A separate, pure
  deterministic scorer (`TurNLFacetEvalScorer`) with its own fixture record, its
  own report, its own API and Maven profile — a parallel universe.

The canonical framing (OpenAI Evals, Anthropic, Langfuse, promptfoo, DeepEval)
splits graders into **three kinds**: **model** (LLM-as-judge), **code**
(deterministic/programmatic) and **human** (review). Turing today has model + code
fused into one method and no human path, and its datasets are second-class.

The user's ask: (1) let us create **extensions of the eval for each grader kind**,
and (2) give us a way to **provide a dataset to be used and tested** — done in a
scalable, friendly, functional way.

### XXXIII.10–13 Datasets — first-class, provided, versioned (T595–T598)

The second half of the ask. Today a golden case is a child row of one agent's set.
T595 promotes it: `TurEvalDataset` + `TurEvalDatasetRow` is a reusable,
**agent-decoupled** collection (input turns + expected slots/outcome/node/reference
answer + tags/metadata); an eval set binds `dataset + grader stack`. Existing inline
cases migrate into a per-agent default dataset so nothing breaks. T596 is the
literal "provide a dataset to be tested": upload CSV / JSON / JSONL / OpenAI-Evals
JSONL, or import from anonymized production transcripts (T61) and Chat-Analytics
failures (reuse the T447 miner), through a column-mapping step; export JSONL for
round-tripping. T597 adds LLM-assisted generation/augmentation (synthesize
rows from the flow/system-prompt/T386 manifest, paraphrase seed turns — never
auto-trusted, always opens the review form, reusing the T387 pattern). T598 makes
datasets versioned: immutable snapshots, a run pins a version, and reports name the
version they ran against so regression comparisons are honest.

### XXXIII.14–18 UX, consolidation & scale (T599–T603)

T600 makes grader **stacks**
reusable named templates with weighted aggregation and per-grader thresholds
(retiring the hardcoded all-must-pass). T603 (§XXXIII.18)
extends grading from pre-publish to **online/continuous** — sampling live traffic
and alerting on drift (composing T87 sentiment, T155 citation-drift, T447 miner).

### XXXIII.18 Continuous / online eval (T603)

The whole block so far grades **golden** cases **before** publish. T603 closes
the loop on the other side: grade **real production traffic** **after** publish,
so quality regressions that a fixed golden set never anticipated (a drifted
knowledge base, a provider model swap, a new customer intent) surface on their
own. The key realisation is that the answer already happened — there is nothing
to replay. `TurOnlineEvalService` samples an agent's recent live sessions from
the T87 chat-analytics store (the same index `TurSelfTuningMiner` and the T596
transcript importer already read), reconstructs a `TurEvalGradingContext` from
each conversation's persisted turns (T61 chat memory), captured slots + final
node (T66 submission), session outcome, and T427 tool trace, and runs the same
pluggable grader stack the pre-publish runner uses — no new grader kinds, no
replay harness. Expectation-based CODE graders (slot / outcome / node) naturally
`appliesTo == false` on un-annotated live traffic, so the useful online stack is
content/quality graders (rubric, model-judge, contains/regex, tool-called) — a
choice the operator makes by binding an `onlineEvalGraderStackId` (blank ⇒
signal-only, no LLM cost).

Each sweep writes one `TurOnlineEvalSnapshot`: the sampled/graded counts, the
grader-stack mean score + pass-rate, and three **composed** post-hoc signals —
the negative-sentiment rate (T87), the stale-citation rate (T155, only when
citation-drift detection is itself on), and the failing-session rate (T447, the
miner's exact heuristic reused verbatim so the two subsystems can't disagree).
The first snapshot per agent is the healthy **baseline**; later windows are
compared to it and flagged `driftDetected` with a human-readable reason when the
mean score / pass-rate fall a configurable margin below baseline **or** any
absolute signal (sentiment / citation / failing) crosses its alarm threshold.
Drift below a minimum sample size is never flagged (too small to trust), and
"re-baseline after a fix" is an explicit operator action rather than an
auto-reset. Consistent with the block invariants it is opt-in twice (a global
`turing.genai.online-eval.enabled` switch **and** a per-agent `onlineEvalEnabled`
flag) and fail-open everywhere — a disabled analytics store, a missing LLM, or
any error yields a non-persisted "unavailable" snapshot, never an exception on
the nightly `@SchedulerLock` sweep. Alerting today is the persisted drift flag +
a `log.warn` line + the Eval Studio **Online** tab's drift timeline; wiring drift
into an email / webhook channel stays out of scope (no chat-analytics-native
notification seam exists yet — see the review-task / suggestion pattern for the
eventual home). This is deliberately **detection**, complementary to (not a
duplicate of) the T447 self-tuning loop that turns failing sessions into
suggested prompt changes.

**Design invariants for the whole block:** every layer is opt-in and fail-open —
empty/legacy config runs the exact current path; CODE graders stay LLM-free so PR
CI is zero-cost; MODEL graders never use free-text JSON; the grounding hard-fail
(Block R) and "propose, never auto-apply" (T447) postures carry over to dataset
generation and self-tuning.

## XXXIV. Live Preview as a per-turn prompt-diagnostics surface (Block AK)

### XXXIV.0 The problem — the preview reads, but doesn't diagnose

The System-Prompt **Live Preview** (`TurSystemPromptPreviewService` →
`SystemPromptLivePreview`) reverses the opaque assembled system message into
labeled provenance segments (PERSONA / AGENT / MCP / FLOW). A redesign made it a
readable, Medium-style flowing article (rendered Markdown, per-passage hover
provenance, origin legend) instead of one scrolling `<pre>` per fragment. That
alone turned it into a tool people actually read — and reading it surfaced real,
quantifiable problems on a live agent (the Insper multi-flow concierge):

- **~40–50% of an active-flow turn is inert or duplicated.** The agent prompt
  hard-codes a whole "first-interaction / welcome / 3-experiences" section that the
  flow addendum *explicitly orders the model to ignore* ("IGNORE TODO O RESTO …
  siga o Goal"). On every active-flow turn that is ~800 tokens of dead weight the
  model still has to attend to.
- **Rules are paid for 2–3×.** The forbidden-vocabulary list (`talvez`,
  `impossível`, `não sei`, …) appears in the Persona block *and* the agent "REGRAS
  DURAS"; "never ask for CPF/card" and "never cite competitors" likewise. Same
  rule, multiple segments.
- **The node label was pure cost.** The flow addendum emitted
  `=== ACTIVE FLOW STEP: <label> ===` — literal `===` (not Markdown) plus a
  Portuguese node label inside an English scaffold, when behavior is driven
  entirely by the `Goal:` line and the Hard rules already tell the model to ignore
  persona/role framing. (Fixed ahead of this block: the delimiter is now a Markdown
  `## ACTIVE FLOW STEP` heading and the label is dropped.)
- **The preview lies about the persona.** `buildPreviewInternal` hardcodes
  `agent.getDefaultPersona()`, but at runtime `TurAgentPersonaResolver.resolve()`
  reads a `__activePersonaId` set by a `persona`-type flow node — so a flow can
  switch Marina → Professor Insper and the preview would still show Marina.
  `TurSystemPromptValidatorService` has the same blind spot.

The insight: the Live Preview is the natural place to *optimize the turn*. It
should answer three questions per turn — **what does the model actually receive,
what does it cost, and what is wasted** — not just "what does a plain entry-turn
look like". This block builds that. All additions are read-only/opt-in and never
change the assembled prompt itself (that stays the faithful ground truth).

### XXXIV.7 Single source of truth — moved to Block AL

The preview's fidelity fix (render through the real assembler, retire the
reconstruction) is not a preview feature — it's the assembly pipeline itself.
It lives in **Block AL §XXXV** as **T613**, and every surface here (persona
T607, token metrics T608, inert T609, lint T610, replay T612) becomes a *view
over that one true assembly*.

**Design invariants for Block AK:** read-only and opt-in; the assembled prompt is
never mutated for display (the preview must stay a faithful mirror of what the
model receives — Block AL T613 makes that literal by reusing the runtime
assembler); token counting is best-effort/provider-approximate and must never
block the page; lint + inert-detection are advisory (they never rewrite the
prompt).

## XXXV. Prompt assembly as a composable, cache-friendly pipeline (Block AL)

### XXXV.0 The root cause behind every Block AK symptom

There are **two** prompt-builders today, and that is the bug factory:

- **Runtime** — `TurChatPromptAssembler.assemble(TurChatPromptRequest, files,
  selectedSkillId)`, the one method both executors call (`TurAgentChatExecutor`
  for the Spring-AI path, `TurNativeChatExecutor` for the native path). Via
  `TurPersonaPromptComposer.compose` it glues
  `persona-static + few-shot + "----" + (agent-base + mcp + flow-addendum)`,
  resolving persona (`TurAgentPersonaResolver`), flow context, MCP, skills,
  memory/compaction and few-shot — and returns the exact `List<Message>` sent to
  the LLM.
- **Preview** — `TurSystemPromptPreviewService.buildPreviewInternal` *rebuilds a
  lookalike* separately (`compose(defaultPersona, null, null,
  base + mcp + flowText)`). Every place the two disagree is a latent bug: the
  persona lie we hit, and every future one as the assembler grows.

The user's framing (verbatim intent): *somewhere Java assembles the whole turn and
sends it to the LLM — use that method as the evidence, drive it TDD-style by
injecting test parameters, validate the method + the final prompt, and show the
truly faithful prompt.* Exactly right — and the scalable form of it is a pipeline,
not a second call site.

> **T613 (SPI + single-pass assembler) and T614 (stability-classed ordering +
> cache breakpoint) shipped** — see CHANGELOG Block AL. The `TurPromptContributor`
> SPI, the single-pass `TurPromptAssemblyPipeline`, the six built-in contributors,
> the byte-identical legacy join, the preview rendering the same segments, and the
> opt-in STABLE-first reorder + `cacheBreakpointIndex` are all in. **T615 also
> shipped** — the pre-pipeline concatenation and its
> `turing.prompt.assembly.pipeline-enabled` escape hatch were deleted, so the
> pipeline is now the single system-prompt assembly path. The subsections that
> specified all three were removed per the "no shipped reports in IMPROVEMENTS"
> rule. **T616 also shipped** — the T115 memory summary and T30 relevance turns now
> assemble through a message-level SPI (`TurMessageContributor` /
> `TurMessageAssemblyPipeline`), so the whole message list has one assembly point.
> **T617 also shipped** — the `message-pipeline-enabled` escape hatch and the inline
> `buildMemoryPrefixLegacy` path were deleted (single history-prefix assembly path),
> and the Live Preview now mirrors the whole message list, not just the system
> message. Block AL (§XXXV) is now fully shipped and closed; see CHANGELOG Block AL.

## XXXVI. HuggingFace embedding provider + provider-aware model discovery (Block AM)

> **Block AM (T623–T629) is fully shipped and closed** — see CHANGELOG Block AM.
> **Follow-up T656 (resilient ONNX acquisition) is shipped** — see CHANGELOG
> Block AM (`TurOnnxModelDownloader` owns the download: retry/backoff, persistent
> cache, integrity check, diagnostics). Note: verified the stock Spring AI path
> already follows the Git-LFS 302 — the win is operational hardening, not
> redirect-following; a hard egress block to the CDN host still needs an infra
> allowlist or a pre-seeded cache volume.
> A third embedding `providerType` sentinel `HUGGINGFACE` reuses the in-process
> ONNX `TransformersEmbeddingModel` runtime but selects the model by picking an
> HF repo id from a list (stored in `modelReference`, no new columns) instead of
> typing `.onnx`/`tokenizer.json` URLs; two discovery seams (HF `/api/models` +
> ONNX-availability probe, and embedding-capable LLM models) feed one
> provider-aware picker (LLM combobox / HF searchable picker / manual local path)
> with on-select dimension + reindex warnings and an ONNX variant/quantization
> choice, and the default-RAG startup provisioning now seeds an HF repo id.

## XLIII. Persona Match — fit trend/history + adjustment suggestions (Block AT, T704)

> The Persona Match aggregate + N×N runner + studio + scheduled re-analysis
> shipped (T696–T703, see CHANGELOG Block AT). What remains is the tuning-loop
> payoff on top of the scheduling that already exists.

**T704 (💭) — fit trend/history + adjustment suggestions.** The scheduled runner
(T700) already re-extracts sources and recomputes changed cells on a MANUAL/DAILY/
WEEKLY cadence, but each run overwrites the cell in place — there is no memory of
how a `(content, persona)` pair's fit moved over time. T704 persists per-cell score
**history** across scheduled runs so the studio can surface *drift* ("this content
fell below fit for persona X since last run") and propose LLM-generated
persona/content adjustments — closing the bidirectional "adjust persona to content
and content to persona" loop the original request described. Builds on T698 (the
cell) + T700 (the schedule); the evaluator and grounding stay unchanged.

## XLV. POST-native search & link-free response contract (Block AV)

> Motivated by a production bug on the live demo: selecting a facet whose value
> contains `&` (e.g. `section:RAG & Chat`, `Deploy & Operate`, `SDKs & Tools`)
> returned zero results. Root cause was an *encoding* fault in the backend
> facet-link builder (fixed separately — `TurCommonsUtils.modifiedURI` left the
> `&` literal, so the value was truncated to `section:RAG ` on the wire). But the
> encoder fix treats a symptom of a deeper architectural coupling, which is what
> this block addresses.

### XLV.1 The coupling: the response hands the client GET URLs to re-parse

The search response (`TurSNSiteSearchBean`) does not just return data — it returns
**pre-serialized GET query-string links** the client is expected to navigate:

- facet item → `link` (`/api/sn/{site}/search?q=*&fq[]=category:RAG%20%26%20Chat`),
- facet group → `cleanUpLink`; widget → `cleanUpFacets`,
- pagination → `href`; spell-check → `corrected.link`; locale switch → a link.

The JS SDKs then **parse that href back into params** (`parseHrefToParams` via
`URLSearchParams`) and **re-serialize** it into a new request
(`buildQueryString`). So every filter click makes a value survive a four-hop
query-string round-trip:

```
backend href-build  →  SDK URLSearchParams parse  →  SDK query-string rebuild  →  server decode
```

Every hop is a chance for a reserved character (`&`, `=`, `+`, `#`, `%`, space) in
a *value* to be mis-read as query structure. The `&` bug is one instance of a
whole class; the encoder fix closes today's leak, but the architecture keeps the
risk alive because the contract is "here is a URL, go parse it." A **JSON request
body has no query-string grammar**, so the entire class of bug becomes impossible
by construction — this is the core argument for going POST-native.

### XLV.2 Current state — POST exists but doesn't solve it

`TurSNSiteSearchAPI` already has a `@PostMapping`, but it is not a usable public
contract:

1. it **401s anonymously** (`if (principal == null) return UNAUTHORIZED`) — the
   public demo and most embedded search are anonymous, so they can't use it;
2. it takes `TurSNSitePostParamsBean`, an **impersonation/admin-oriented** payload
   (`userId`, `populateMetrics`, `targetingRulesWithCondition*`) rather than a
   clean read-only search request;
3. it returns the **same href-laden bean** as GET — so even over POST the client
   still parses URLs. POST today changes the transport, not the contract.

### XLV.3 The key insight — the structured state already (almost) exists

The response is *already* carrying the structured data needed to reconstruct the
next request without touching a URL:

- `TurSNSiteSearchFacetItemBean` has `filterQuery` (the raw `field:value`) **and**
  `selected` **and** `count` — a client can toggle the `fq[]` array directly from
  these, no href parse;
- the widget has `selectedFilterQueries`; pagination has `page` and a `type`.

So the link-free response is mostly a *formalization + exposure* of state that is
already computed, not a new computation. The user's own framing applies here:
because a POST request is a distinct entry point, its **response payload may differ
from the GET default** — we can ship a cleaner v2 shape on the POST/opt-in path
without disturbing the GET response one byte.

### XLV.4 Design (phased, additive)

**Phase 1 — public POST contract (T707).** A read-only `TurSNSearchRequest` JSON
bean (`q`, `fq[]`, `fqAnd`, `fqOr`, operators, `page`, `rows`, `sort`, `locale`,
`group`, `fieldList`, `targetingRules`) that is **anonymous-capable** and decoupled
from the impersonation-only `TurSNSitePostParamsBean` (which stays for the
authenticated metrics/impersonation path). GET is untouched.

**Phase 2 — structured, link-free response (T708) behind format negotiation
(folded into T708).** Formalize facet items as `field` + `value` + `selected`
(+`count`) and add structured **pagination** (page/type), **sort**, **cleanup**
(clear-filters as an action, not an href) and **locale-switch** actions, so the
client reconstructs any next request from data alone. Emit this shape only when the
caller asks for it (a request flag / `responseFormat` / `Accept` negotiation),
**default = legacy `LINKS`**, so existing GET consumers get a byte-identical
response.

**Phase 3 — SDKs go 100% POST (T709 vanilla, T710 React + react-ui).** Move the
search/autocomplete/similar controllers to POST + JSON body; drive facet /
pagination / sort / cleanup toggles from the structured state; retire
`parseHrefToParams` / `buildQueryString` from the hot path; react-ui facet
components navigate via structured actions instead of `item.link`. POST is a state
change, so the SDKs prime CSRF via `/csrf` (already the established pattern —
`X-XSRF-TOKEN` header, cookie is HttpOnly). **T711** extends the T406 parity guard
to the POST/structured paths and confirms the anonymous demo's nginx allowlist +
CSRF priming cover POST search.

**Phase 4 — breadth & retirement (T712–T714, 💭).** POST variants for `/ac`
(autocomplete) and `/list`; once both SDKs are POST-native, mark the embedded
`link`/`href`/`cleanUpFacets`/spell-check-`link` fields legacy (kept — see
non-goals) and drop the query-string round-trip helpers from the SDKs; migrate
`rest-api.md` / `javascript-sdk.md`, the Atlas Store showcase and the EDS example
blocks to the POST-native flow.

### XLV.5 Non-goals & open questions

- **Do NOT remove GET or the server-rendered links.** They are valuable for
  deep-linkable / bookmarkable / shareable search URLs, SEO-crawlable facet pages,
  and no-JS / progressive-enhancement HTML consumers. This block is **purely
  additive** — POST + structured response is opt-in; the GET + href contract is
  frozen, not deprecated (Phase 4's "deprecate" means *flag as legacy for the JS
  SDK path*, not delete).
- **CSRF on anonymous POST (T709/T715 territory).** A search is a read, but POST
  triggers CSRF; the SDK must prime `/csrf` first, adding a round-trip on public
  embeds. Open question: exempt the read-only public search POST from CSRF, or
  accept the priming call. Decide in Phase 3; document either way.
- **Response-format negotiation mechanism (T708).** Request flag vs `Accept`
  header vs `responseFormat` field — pick one; a request field is simplest and
  works over both GET and POST for A/B testing the shape.
- The impersonation POST payload and the authenticated metrics path are **out of
  scope** — they keep their current bean and behavior.

## XLVI. Synthetic User Research — persona studies, interviews & continuous insight (Block AW)

> Persona work so far has treated a `TurPersona` as something you act on **one at a
> time**: a voice on an agent, a chat partner (T578), a fit lens over content
> (Block AA/AT), a speaker in a dialogue (Block AU). The maturity gap — and the
> thing **synth.users** (Synthetic Users) productized — is *research as a method*:
> define an audience, interview a whole cohort, and synthesize what they collectively
> tell you. This block adds that method, but the thesis is that **Turing should not
> ship a synth.users clone — it should ship the version only Turing can build**,
> because it already owns every primitive synth.users has to approximate.

### XLVI.0 What synth.users does, and the Turing primitive that already covers it

| synth.users concept | Turing primitive it maps onto |
| --- | --- |
| Audience definition (demographics + psychographics) | `TurPersona` + `personaKind` AUDIENCE facet (Block AA) |
| Multi-agent, OCEAN-profiled participants | personas + a new Big Five facet (T717) + T605 style→model calibration |
| RAG grounding (upload proprietary content) | the RAG/SN retrieval stack + Block AA notebook sources (T465) + T385 grounding |
| Dynamic / custom / concept-test interviews | persona-chat executor (T578) driven by an interviewer sub-loop (T720) |
| Multi-model "Big Shuffle" | the T517 per-stage model-lane seam (T728) |
| Insights report (themes, quotes, recs) | `TurLlmSummaryService` cached synthesis (T722) |
| Saturation scoring | a deterministic novelty scorer, T385/T466 pattern (T723) |
| Continuous validation | the Persona Match scheduler + ShedLock (T729) |
| PRISMA multi-study planner | the project-mosaic studio pattern (Block AT/AU) → T733 |
| Research Assistant | AI Authoring (T426) → T732 |

The point of the table is the design constraint: **almost nothing here is new
infrastructure.** The study aggregate, the interview engine, the synthesizer, and
the studio are thin compositions over shipped code — which is why the block can be
large without being risky, and why the two interconnection bets (§XLVI.4) are where
the actual product differentiation lives.

### XLVI.2 Phase 2 — the study model — ✅ shipped (see CHANGELOG Block AW: T719–T721)

### XLVI.3 Phase 3 — synthesis & sufficiency

- **T722 — insights report.** Synthesis runs through `TurLlmSummaryService` (cached,
  regenerable, already the home of SN-site + System-Info insights): executive
  summary, ranked themes each carrying **verbatim quotes traceable to (interview,
  persona)**, and recommendations, with *by-theme* / *by-persona* lenses like the
  Match report.
- **T723 — saturation scoring.** A **pure, LLM-free** novelty scorer (theme overlap
  via embeddings/keywords, the T385/T466 deterministic pattern so it runs in CI)
  that reports when marginal personas stop adding themes → "sample adequate at N."
  This is deliberately the antidote to the strongest criticism of synthetic research
  (NN/g): it makes the *sufficiency* claim measurable instead of asserted.
- **T724 (💭) — theme/affinity graph.** A concept↔problem↔persona graph over the
  synthesized themes; visualization only, no new inference.

### XLVI.4 Phase 4 — the interconnection (why this beats a standalone tool)

✅ **Shipped (T725–T729) — see CHANGELOG Block AW.** The part a dedicated research
SaaS cannot do, because it owns neither an agent runtime nor an eval platform:
studies promote into eval datasets, a study interviews your own deployed agent,
concept-tests route through the Persona Match content-fit evaluator, per-stage
"Big Shuffle" model lanes, and Continuous Insight (scheduled re-runs + drift).

### XLVI.5 Phase 5 — authoring, the studio & the developer surface

`/bento/persona/research` reuses the Match/Dialogue studio shape (T730) and is the
**frontend hub**: Phases 2–4 ship deliberately headless backends, and T730 renders
their surfaces (live SSE feed, report lenses, saturation gauge, target/lane pickers,
schedule + drift chart). Audience **cohort synthesis** (T731) generates a diverse
persona set from a one-paragraph brief via the T426 strict-schema authoring path +
T470 drafting, never auto-saved. The **Research Assistant** (T732) is a skippable
conversational guide over AI Authoring. **PRISMA** (T733, 💭) is the multi-study
planner + program rollup. **T734 (💭)** adds the *developer* surface — a `turing
research` CLI verb mirroring `turing eval` (fits because T725 already makes studies
eval-like) plus an optional programmatic client.

### XLVI.7 Frontend & SDK scope — the deliberate split

Two decisions shape where UI and SDK work land, so the block isn't padded with
speculative surface:

- **UI is explicit per task, but the studio is the hub.** Each task carries a `UI:`
  clause naming its visible surface. The persona-*form* additions (OCEAN sliders
  T717, knowledge binding T718) live on the existing `/bento/persona/:id` page —
  *not* the studio — because they are properties of a persona, usable outside any
  study. Everything study-scoped (interview feed, report, saturation, target/lane
  pickers, schedule/drift) renders in the T730 studio; the Phase 2–4 backends stay
  headless on purpose so there is one rendering surface, not many drifting ones —
  the same discipline Persona Match/Dialogue followed (backend tasks + one studio).
- **The embed SDKs are customer-facing; research is admin tooling.** `@viglet/turing-sdk`
  and `@viglet/turing-react-sdk` exist to embed **search and chat** into a
  customer's site. Running studies, synthesizing reports, and the eval bridge are
  internal research/admin capabilities with no embed use case, so they are **not**
  SDK work. The single embed-SDK touch is OCEAN metadata flowing through the
  persona surface that already exists (T635 vanilla / T579 react), so an embedded
  persona chat reflects personality. The *programmatic* surface for running a study
  from code/CI belongs to the **CLI** (`@viglet/turing-cli`, T734), mirroring
  `turing eval` — not the browser embed client. Adding study endpoints to the embed
  SDK would be scope with no consumer.

### XLVI.6 Non-goals & guardrails

- **Synthetic research does not replace real users.** The docs and UI must frame
  studies as a *discovery co-pilot* (front-load problem-space mapping, sharpen
  questions), not ground truth — exactly the NN/g caution. Saturation scoring (T723)
  and grounding (T718) exist to keep the output honest; do not add features that
  imply statistical validity Turing can't defend.
- **Default persona/agent behavior is untouched.** Every addition is opt-in; an
  empty OCEAN facet, no bound knowledge source, and no study leave the existing
  persona chat / Match / Dialogue paths byte-for-byte unchanged.
- **Reuse, don't fork.** The study runner reuses the Persona Match runner; answers
  reuse the T578 executor; synthesis reuses `TurLlmSummaryService`; concept-test
  reuses `TurPersonaContentFitEvaluator`. A new parallel engine for any of these is
  the anti-pattern this block exists to avoid.

## XLVII. Bento header consistency — the `BentoFormHero` morph everywhere (Block AX)

Bento detail pages settled on one header behavior (the LLM-instance surface): on
scroll the hero's Save/Cancel fade away and a fixed frosted save bar fades in (the
`--bento-fade` rAF morph), and the breadcrumb eyebrow leads with a `←` back-arrow.
Identity-CRUD pages inherit all of this from `BentoEntityShell`; **own-hero form
pages** (the persona editor, AI-agent sub-sections, the SN forms) had to reproduce
it by hand — a `bento-fade-out BentoSaveBar` plus a separate `BentoScrollSaveBar`
with duplicated props — and a few pages shipped a *static* (non-morphing) save row
instead. T735 collapses the hand-wired pair into a single `BentoFormHero`
(hero + scroll twin from one prop set, `backTo`/`backLabel` baking in the arrow) so
the pattern can't be half-implemented, and adopts it in the persona editor.

The rest of the block is bounded cleanup, sized by a **read-only audit**
(`scratchpad/bento-header-audit.md`), not a speculative rewrite:

- **T736 — the 3 real gaps.** `bento.sn.synonym`, `bento.se.core.new` (reused
  console page's bento `header` path), and `ai-agent.system-prompt.form` render a
  static Save row with no fade/stick. These are genuine UX inconsistencies and are
  the priority.
- **T737 — the back-arrow sweep.** ~16 own-hero forms use a plain `<Link>` eyebrow
  with no `IconArrowLeft`. The highest-leverage single edit is the shared
  `bento.ai-agent.sub-hero`, which covers every agent sub-page at once; the SN forms
  and `bento.persona.suggest` follow. Prefer migrating to `BentoFormHero.backTo`.
- **T738 (💭) — optional canonicalization.** The ~14 forms that already morph
  correctly by hand could drop their boilerplate for `BentoFormHero`. Pure
  consistency, zero visible change — worth doing only when those files are touched
  for another reason, to avoid churn for its own sake.

**Non-goal:** no behavior/logic change to any form. This block is header structure
only; the field logic, validation, and save mutations stay exactly as they are.

## XLIX. Governed LLM Gateway — OpenAI-compatible governed egress (Block AZ)

**Fully shipped (2026-07-20) — T740–T748.** The design rationale that lived here
has been removed now that the block is complete; see `CHANGELOG.md` → "Block AZ"
for what shipped and `git log` for the implementation history (per the
IMPROVEMENTS.md header rule: no shipped implementation reports). In short: an
OpenAI-compatible `/v1/*` inbound seam over the existing provider factory,
`sk-turing-…` virtual keys with per-key budget / rate-limit / spend, cross-cutting
cache + guardrails + RAG-as-a-header, `turing-agent:` / `turing-sn:` /
`turing-local:` / `turing-router:` model routing, `x-turing-tools` native tools,
per-key observability, a `/bento/gateway` admin, and a traffic → eval/distillation
bridge — all opt-in behind `turing.gateway.enabled` (default off).

## LI/LII. Model catalog API + regeneration pipeline (moved out)

> **Migrated 2026-07-21 to [`openviglet/model-catalog`](https://github.com/openviglet/model-catalog).**
> The public model catalog API (Block BC / former §LI) and its multi-source
> regeneration pipeline (Block BD / former §LII) now live in their own repo, with
> their own roadmap, design rationale and reference docs
> (`docs/references/api.md` + `pipeline.md` there). Turing consumes the published
> endpoint remotely (`turing.model-catalog.url`). The shipped Turing history stays
> in CHANGELOG Blocks BC/BD; design/backlog for the catalog now lives in that repo.

## LIII. Catalog-driven model intelligence (Block BE)

**The gap.** Turing depends on `io.github.openviglet:model-catalog-client` and
fetches a full `ModelEntry` per model — `pricing{inputPer1M,outputPer1M,currency,
indicative,source,lastVerified}`, `contextWindow`, `maxOutputTokens`,
`embeddingDimensions`, `capabilities`, `modalities{input,output}`,
`benchmarks{intelligenceIndex,arenaElo,scores}`, `performance{throughputTps,
latencyTtftSec}`, `knowledgeCutoff`, `releaseDate`, `deprecated`/`status`, plus the
client's `Classifier.classify` tier (Frontier/High/Mid/Light) and the
`providers()`/`plans()`/`changes()` registries. But `TurLlmModelCatalog.toCatalogMap`
collapses each entry to `id/label/kind` (its Javadoc admits "extras ignored"), so
every downstream feature re-enters the same facts by hand: prices live in a manual
`TurLLMPrice` table (unknown model → `$0`), `TurLLMInstance.contextWindow` is typed
in and barely enforced, modality/vision support is a hand-toggled native-capability
matrix, and nothing warns when an operator selects a model the vendor has already
deprecated. The data is already on the wire and thrown away at one method.

This block widens that funnel once and then turns the recovered data into product.

### LIII.1 Widen the funnel + auto-pricing (T776–T778)

`TurLlmModelOption` (and its frontend twin + `toCatalogMap`) is the lossy
three-field carrier; **T776** widens it to preserve the rich `ModelEntry` fields
(and the `Classifier` tier/tags) so the picker and services stop re-deriving what
the catalog states — including trusting catalog `kind` instead of the heuristic
classifier. **T777** is the headline the whole block hangs off: a scheduled
reconciler seeds/refreshes `TurLLMPrice` from catalog `pricing` with a
CATALOG-vs-MANUAL `source` column so token cost (`TurLLMTokenUsage.costUsd`,
`TurLLMPriceService.computeCost`, the cost dashboard, gateway per-key spend, budget
gates) is populated automatically for well-known models, while a customer's
negotiated/manual rate always wins and is never overwritten. Because catalog prices
are explicitly `indicative`, **T778** makes provenance honest in the UI: price
source, `lastVerified`, the indicative flag, and a "verify at vendor" deep link
(`providers()` `apiPricingUrl`), with a warning when a billed model is unpriced or
stale.

### LIII.2 Enriched selection & correctness (T779–T782)

Users pick models blind today. **T779** puts the recovered metadata where the
choice is made — price, limits, capability/modality, benchmark rank, TTFT, cutoff
and tier as badges/tooltips in the combobox, sortable by price/context/intelligence.
**T780** stops the manual re-entry of `contextWindow`/`maxOutputTokens`/
`embeddingDimensions` by pre-filling them from the catalog (override kept, hand
values validated). **T781** uses `capabilities`/`modalities` to gate features that
otherwise fail at runtime — no tool calling on a model without `tools`, no image
upload on a text-only model — and seeds the native-capability matrix defaults from
the catalog rather than hand-toggling. **T782** closes the lifecycle blind spot: a
scan flags configured models the vendor has `deprecated`/`RETIRED` and proposes a
GA replacement with matching capabilities.

### LIII.3 Intelligence, budgeting & optimization (T783–T786)

With prices trustworthy, cost becomes a first-class planning input. **T783** shows a
projected cost before an expensive run (eval dataset, research study, batch
re-embed). **T784** turns pricing/benchmarks/performance/tier into router policy on
the Block AZ gateway — cheapest-that-qualifies, highest-intelligence, or
lowest-latency selection instead of round-robin only. **T785** is the onboarding
lever: describe the constraints ("needs vision, ≥128k context, ≤ $X/1M, ≥ High
tier") and get a ranked recommendation with one-click instance creation, over
`fetchByCapability`/`fetchByModality`. **T786** runs the loop backwards — from
observed per-agent usage it proposes a cheaper/faster model that still covers what
the agent actually uses, with projected savings.

### LIII.4 Freshness & trust (T787–T789)

**T787** surfaces `knowledgeCutoff`/`releaseDate` so both operators and end users
know how stale an answer may be (optionally self-disclosed in the system prompt).
**T788** consumes the client `changes()` feed so admins are told when a model they
depend on is deprecated, repriced, or superseded — the catalog becomes a live
signal, not a startup snapshot. **T789** (💭) rounds it out with `providers()`
authoritative pricing links and an optional `plans()` consumer-plan reference.

**Library note (optional, not a blocker).** The 1.0.5 client already exposes
everything above; `providers()`/`plans()`/`changes()`/`aliases()`/`stats()`/
`coverage()` currently return untyped `Map<String,Object>`. The only refinement
worth filing in `openviglet/model-catalog` (`clients/java`) is *typed* records for
those registries plus an alias-resolution convenience (`get`/lookup that honors
`aliases.json` + `ModelEntry.aliases()`, so a stored alias id still resolves). Both
are ergonomic upgrades Turing can work around with raw-map reads until then.

## LIV. Vectorless (Structured-Data) RAG — name it, feed it, integrate it (Block BF)

**The realization.** Turing already ships a production-grade "talk to a structured
catalog, grounded, with citations, no embeddings" engine — it just isn't *named*
that way and isn't discoverable as its own capability. The T392
`TurCatalogCopilotService` (§XX.12) composes the Block R primitives into exactly
this: the latest user turn is parsed into a grounded `TurDslQueryRequest` over the
site's **declared** field schema (`TurSNSiteFieldExt`) by the T385
`TurNLFacetParser`/`TurLlmNLFacetParser` (the LLM may only reference declared
fields — hallucinated fields hard-fail), executed against the live index by
`TurDslSearchService` (Solr/ES/Lucene — **field/facet/range queries, zero
embeddings**), optionally reordered by objective signals via
`TurSNHybridRankingService`, then the default LLM answers **strictly** from those
cited rows, emitting `[n]` citations as `TurCatalogCitation`. It needs only a
default LLM — **no embedding model, no vector store** — which is precisely what
"vectorless" means. The whole path is fail-open (parse → free-text, hybrid →
lexical, LLM failure → still returns the matched citations). The word "vectorless"
appears nowhere in the codebase today (verified) — so the substance exists but the
*capability* is invisible to an integrator scanning for it.

**Why name it.** A vendor evaluating Turing for "LLM Q&A over our structured data
(product catalog, price list, model registry, spec sheet) without standing up a
vector database" has no way to find that Turing does this out of the box — they see
"RAG" and assume embeddings + a vector store + a chunking pipeline. Naming the mode
**Vectorless (Structured-Data) RAG**, giving it a knowledge-base *type* selector on
the SN-site AI form, a readiness path that advertises "needs only a default LLM",
and an end-user docs page turns a hidden strength into a positioning lever — and it
pairs directly with the public `openviglet/model-catalog` as the flagship live
example. This is the **structured-source-indexing** thesis (§XX) surfaced as a
product-level capability.

**The two real gaps (everything else exists).** The map is otherwise green: provider
factory + 12 LLMs (`TurGenAiLlmProviderFactory`, incl. `openai-compatible`),
structured index + DSL (`plugins/se/*`, `sn/dsl/*`), arbitrary-JSON-doc indexing via
`POST /api/sn/import` (`TurSNJobItems`), grounded cited Q&A (T392), NL→filter
grounding on a declared schema (T385). What is missing:
1. **Pull a remote JSON feed on a schedule.** Today content must be *pushed* by an
   external connector. A first-class vectorless KB fed from a URL (a catalog
   endpoint, a `catalog.ndjson`) needs a scheduled ingester that GETs the feed via
   the T739 `TurUrlFetcher`, declares/refreshes the field schema from a field
   manifest, maps records → `TurSNJobItems`, and POSTs to the existing import
   pipeline. This is the one genuinely new backend piece.
2. **A frontend widget + SDK hook bound to `/copilot`.** The existing chat widgets
   (`ai-mode-chat.tsx`, react-sdk `use-turing-chat`) target the vector-RAG
   `POST /api/sn/{site}/chat/conversation` SSE path; nothing targets
   `POST /api/sn/{siteName}/copilot` or renders `TurCatalogCitation[]` as linked
   sources. A `use-turing-copilot` hook + a thin embeddable component closes it so
   external sites (the model-catalog "Ask the catalog" box) can drop it in.

### LIV.1 Name & surface the capability (T790)
Promote the T392 retrieval path to an explicitly-named, first-class knowledge-base
**mode**: a Vector / **Vectorless-Structured** / Hybrid selector on the SN-site AI
form; a readiness path in `ChatDisabledReason` that recognizes "vectorless" needs
only a default LLM (not `MISSING_EMBEDDING`/`MISSING_STORE`); and an end-user docs
page. No new retrieval code — this is discoverability + config surface. The engine
already exists; the point is that an integrator can *find* and *turn it on*.

### LIV.2 Scheduled JSON-feed source (T791)
A `TurStructuredFeedIngestService`: a scheduled (TTL) ingester that GETs a remote
JSON feed — a plain array or a catalog envelope, natively consuming the model
catalog's `catalog.ndjson` — via the T739 `TurUrlFetcher`, declares/refreshes the
SN-site field schema (`TurSNSiteFieldExt`) from a **field manifest** (the model
catalog's emitted `query-manifest.json`: field name, type, facetable, enum sets,
numeric ranges), maps each record to `TurSNJobItems.attributes`, and POSTs to the
existing `/api/sn/import` pipeline (with de-index of vanished ids). Config under
`turing.genai.*`, opt-in per source. This is the missing "pull" side of ingestion.

### LIV.3 Copilot React widget + react-sdk hook (T792)
A `use-turing-copilot` react-sdk hook + a thin, embeddable Q&A component bound to
`POST /api/sn/{siteName}/copilot`, rendering the answer plus `TurCatalogCitation[]`
as linked sources (mirrors `ai-mode-chat.tsx` but for the vectorless/copilot path,
and multi-turn like `TurCatalogCopilotResult`). Closes the frontend gap so both the
admin app and external sites can consume vectorless Q&A with citations.

### LIV.4 Stuff-all mode for small datasets (T793)
For a dataset below a token threshold, skip DSL retrieval entirely and ground the
LLM on the **whole** context bundle (e.g. the model catalog's emitted `context.txt`,
sized ≤ ~40k tokens) — the extreme-vectorless path where a ~240-row catalog fits in
one prompt, cheaper to reason over and still cited + fail-open. A per-source toggle
on the copilot; retrieval stays the default above the threshold.

### LIV.5 Dogfood: model-catalog as the first vectorless KB on the demo (T794)
Provision an SN site + field schema from the model catalog's `query-manifest.json`,
wire the T791 feed to the published `openviglet.github.io/model-catalog` endpoint,
enable Vectorless RAG, and expose the copilot on `turing-demo.viglet.org` — the live
demo answering *"cheapest embedding model with ≥1M context?"* with cited model
links. Turns the capability into a public, clickable proof and the reference
integration for the model-catalog "Ask the catalog" widget (that repo's Block M).

### LIV.6 Vectorless RAG over the OpenAI-compatible gateway (T793 → 💭 T795)
Extend the Block AZ gateway (§XLIX) so a `turing-copilot:<site>` model route (or an
`x-turing-copilot-site` header) invokes the vectorless copilot path — letting any
OpenAI-style client get grounded, cited structured answers with zero Turing-SDK
code. The cleanest "separate backend" integration for an external static site, and
a natural extension of the T744 `turing-sn:<site>` / RAG-as-a-header routing.

## LVI. SN result & facet rendering robustness for feed/crawl-indexed sites (Block BH)

**The problem, from the same dogfood.** With the `openviglet/model-catalog` wired as
a vectorless KB (§LIV.5, T794) and indexing working, the SN search UI shows the
**correct result count** but every result's **title/description renders blank** and
every **facet renders with no group label**. Nothing errors — the data is indexed and
searchable; it just displays empty. The cause is that the search response layer does a
pure **name-lookup with no fallback**, and the T796 auto-provisioner never wires the
site's default-field columns or facet labels to the feed's actual field names:

- The response hands the client a `queryContext.defaultFields` name-map built straight
  from the site columns (`TurSNQueryContextBuilder.defaultFields`,
  `turSNSite.getDefaultTitleField()/getDefaultDescriptionField()/…`), and the client
  reads `doc.fields[defaultFields.title]`. If the column is null, or names a field the
  document doesn't have, the client renders `""` (`frontend/packages/sdk/src/resolve.ts`).
- A structured-feed document carries the feed's own field names — the model-catalog
  feed indexes `label`, `vendor`, `kind`, `contextWindow`, … — and has **no** `title`
  or `abstract` field, so the template defaults (`title`/`abstract`) point at fields
  that don't exist → both blank. `TurStructuredFeedProvisioningOnStartup` (T796)
  provisions the site with a minimal manifest and lets ingest converge the schema, but
  never sets the `defaultXField` mapping to the feed's real fields.
- A facet group-header label resolves to the locale-matched `TurSNSiteFieldExtFacet`
  row, else `fieldExtDto.getFacetName()` (`TurSNFacetRenderer.searchFacetBean`). An
  auto-provisioned facet field has `facetName = null` **and** zero locale-label rows
  (`TurSNFieldProvisioner.saveSiteFieldExt`/`saveFacetLocales` — the crawl/feed
  `TurSNJobAttributeSpec` carried no facet labels), so the header resolves to null and
  renders blank. There is no last-resort fallback to the field name.

The immediate unblock for the live dogfood is deployment configuration (point the SN
site's default fields at the feed's fields and give facet fields labels), but the
reason it broke **silently** is a real Turing robustness gap: the renderer has no
sensible defaults and the auto-provisioner leaves the mapping empty. This block closes
that so a feed/crawl-indexed site renders readably out of the box.

### LVI.1 Default-field resolution fallback (T808)
In `TurSNQueryContextBuilder.defaultFields`, when a site's `defaultTitleField` /
`defaultDescriptionField` / `defaultTextField` / `defaultURLField` /
`defaultImageField` / `defaultDateField` column is blank, fall back to the canonical
`TurSNFieldName` constant (`title` / `abstract` / `text` / `url` / `image` /
`publication_date`) instead of emitting a null field name. This is the safety net for
sites provisioned by a path that never ran `TurSNTemplate.defaultSNUI` — the exchange
import/restore path (`TurSNSiteImport.createSNSiteFromExchange` copies the columns
verbatim, null if the source had them null) and the T796 auto-provisioner. It does not
by itself fix a *name mismatch* (feed field `label` ≠ `title` — that's LVI.3), but it
guarantees the response always names the conventional fields rather than `null`.

### LVI.2 Facet group-label fallback to the field name (T809)
In `TurSNFacetRenderer.searchFacetBean` (and the parallel `facetLocalesFor` in
`TurSNSearchProcess`), when neither a locale-matched facet label nor `facetName` is
present, fall back to a **humanized field name** (e.g. `pricing_inputPer1M` →
"Pricing Input Per 1M", or at minimum the raw field name) so an auto-provisioned
crawl/feed facet field never renders a blank header. Facet *value* labels already fall
back to the raw attribute value (lines ~280-283), so this closes the remaining
label-less case — the group header. Locale-scoped labels still win when configured;
this is strictly a last resort.

### LVI.3 Auto-provisioning wires default fields + facet labels from the feed manifest (T810)
Extend `TurStructuredFeedProvisioningOnStartup` (T796) so the structured-feed source /
`VigletFieldManifest` can declare which feed fields map to title/description/text/url/
image/date and carry per-field facet labels, and have provisioning apply them —
setting `TurSNSite.defaultTitleField`/`defaultDescriptionField`/… and each faceted
field's `TurSNSiteFieldExt.facetName` + locale label rows. This is the direct,
zero-config fix for the dogfood: the model-catalog site provisions with
`defaultTitleField = label` (and a chosen description/text field) and readable facet
labels, so it renders correctly on first boot without post-deploy admin steps. The
concrete mapping lives in the deployment's manifest/env (viglet/cloud), never in
Turing — Turing only gains the ability to honor it. Builds on LVI.1's fallback (a site
that declares nothing still gets the canonical defaults).

## LVIII. Configurable, per-language NL ranking lexicon (Lucene analysis) (Block BJ)

> **On hold pending §LIX.** The LLM-assisted planning strategy (Block BK) handles
> languages natively, which may make a per-language *deterministic* lexicon
> unnecessary. This section remains the design for the deterministic i18n path; build
> it only if deterministic multilingual ranking is wanted *alongside* the LLM path
> (a cheap, CI-testable fast-path for non-English).

**The problem.** T811's `TurNLRankingPlanner` made ranking deterministic, but it did so
with a **hardcoded English word list** baked into the class: `ASC_WORDS`/`DESC_WORDS`
(the superlative→direction lexicon), `SYNONYMS` (adjective→concept-noun bridges like
`cheapest`→price), and `STOPWORDS`. Two limits follow:

- **Not configurable** — a deployment or a specific catalog can't add its own ranking
  vocabulary (a domain word, a house synonym) without editing Java and rebuilding.
- **English-only** — the words are English. A `pt_BR` user asking "modelo *mais
  barato*", "*maior* janela de contexto" or "*ordenar por* preço" gets no sort,
  because none of those tokens are in the sets. The copilot already knows the request
  locale (it is passed through `retrieve`), so the lexicon should follow it.

Enumerating every language × every inflection in Java would be unmaintainable
("barato/baratíssimo/mais barato", "cheap/cheaper/cheapest"). This is exactly the
problem **Lucene's analysis pipeline already solves** — and Lucene is already a Turing
dependency (embedded vector store + the Lucene SE plugin), so reusing it adds no weight.

**The design — data-driven lexicon + Lucene analysis, resolved per locale.**

- **LVIII.1 (T814) Externalize.** Lift the four hardcoded sets into a resource-backed
  `TurRankingLexicon` (a default `en` bundle under `resources/`) served by a
  `TurRankingLexiconProvider`. `TurNLRankingPlanner` consumes the lexicon instead of
  static fields — English parity, zero behaviour change, but now the vocabulary is
  data. This is the seam everything else hangs off.
- **LVIII.2 (T815) Lucene analysis backbone.** Replace the hand-rolled
  `tokenize`/`expand`/stopword code with a per-locale Lucene `Analyzer`:
  `StandardTokenizer` → `LowerCaseFilter` → per-language `StopFilter` (Lucene ships the
  stopword sets) → a Snowball/`*Stemmer` for the language → `SynonymGraphFilter` built
  from the T814 synonym map. Crucially the **same analyzer runs over both the query and
  each declared field's name + description**, so a query lemma matches a field lemma
  after stemming — "cheapest"/"cheaper"/"cheap" and "barato"/"baratíssimo" all collapse
  to one stem, and the synonym filter (not a hand map) does the adjective→concept
  bridge. New inflections stop being a maintenance burden.
- **LVIII.3 (T816) Per-language + external override.** A registry resolves the lexicon
  and analyzer by the copilot's request/site locale, falling back to `en`; ship `pt`
  (and `es`) bundles. `turing.genai.copilot.ranking.lexicon-dir` lets a deployment mount
  an external lexicon directory so a new language or a tuned synonym set ships as a
  config file, not a rebuild. Adding a language becomes: a stopword set (Lucene has it),
  a stemmer (Lucene has it), and a small direction+synonym resource.
- **LVIII.4 (T817, 💭) Per-site / admin overrides.** Optionally let a site or global
  settings extend the direction/synonym lexicon with domain terms, editable in admin and
  merged over the resource defaults — promote only if a deployment needs vocabulary
  beyond the shipped bundles.

**Invariants preserved.** The planner stays **deterministic and fail-open** — no LLM is
reintroduced; an unresolved locale falls back to `en`; a lexicon/analyzer failure
degrades to no-sort exactly as today. The engine (`TurDslLuceneExecutor.buildSort`) is
untouched. A possible future alignment (out of scope here) is to reuse the target SN
site's own index analyzers so ranking tokenization matches how the field was indexed.

## LIX. Configurable copilot query-planning strategy (deterministic / LLM-assisted / hybrid) (Block BK)

**The idea.** The copilot turns prose into a structured query in one LLM parse call,
with the T811 deterministic overlay bolted on for ranking. An alternative — surfaced in
design discussion — is to lean *into* the LLM but **decompose** the work into narrow,
verifiable steps instead of one "produce the whole DSL" call (which is what fails on a
weak model): **parse → judge → refine**. A judge pass is exactly what catches the bug's
failure mode (the parse came back empty, dropped the `kind` facet, or missed the sort)
and repairs it. The **depth of analysis is a parameter** (parse-only → +judge →
+refine), so a deployment dials cost against quality.

**Why it's not either/or with the deterministic planner.** The two approaches sit on
different axes:

- The **LLM multi-pass** path understands languages *natively* — "modelo mais barato",
  "ordenar por preço" work with no per-language resource, which is why it puts Block BJ
  (§LVIII) on hold. But it costs N× LLM calls per turn (latency + tokens) and is
  non-deterministic / not CI-testable without an LLM.
- The **deterministic** path (T811) is instant, free, and CI-testable, but English-only
  and needs the Block BJ lexicon work to scale to other languages.

So the design is a **configurable strategy**, opt-in per agent/site — idiomatic here
(choosable enum, `DETERMINISTIC` default = today's behaviour, so no existing site is
re-validated; cf. the project's opt-in-flag convention):

- **LIX.1 (T818) The seam.** A `TurCopilotPlanningStrategy` enum
  (`DETERMINISTIC` | `LLM_ASSISTED` | `HYBRID`) and a `TurCopilotQueryPlanner` interface
  that `retrieve()` delegates to; the current parse-plus-T811-overlay becomes the
  `DETERMINISTIC` implementation. Zero behaviour change at the default.
- **LIX.2 (T819) The LLM multi-pass.** `LLM_ASSISTED` = parse → a `TurLlmJudge`-style
  validation (dropped facet? wrong/missing sort? ungrounded field?) → refine, bounded by
  `turing.genai.copilot.planning.max-passes`. Provider tool calling / schema, never raw
  JSON (per the `gpt-4o-mini` structured-output caution in `agents.md`). This is where
  i18n and hard-question quality come from.
- **LIX.3 (T820) HYBRID — the recommended default to aim for.** Run the cheap
  deterministic fast-path first; escalate to the LLM judge/refine **only** when the
  result is empty or suspect (degenerate query, or zero hits when a superlative/facet
  was clearly present). Common questions stay instant and free; the LLM budget is spent
  only to rescue a failure — capturing most of the multi-pass quality at a fraction of
  the cost.
- **LIX.4 (T821, 💭) Admin + eval.** Expose the strategy + depth in the agent/site
  admin, and extend the NL→facet eval pack to score each strategy so the
  quality/latency/cost trade-off is measurable per catalog before switching.

**Invariants.** Fail-open throughout: an LLM/judge failure degrades to the deterministic
result (or to the citations-only path already there), never to an error. The default
strategy is `DETERMINISTIC`, so the block is purely additive.

## LX. Stuff-all at catalog scale — compact, size-adaptive projection (Block BL)

**The regression, from the live dogfood.** The model-catalog copilot answered open,
advisory questions strikingly well — "quero criar um chat para meu site, qual modelo
barato?" returned a real, reasoned shortlist. Then the catalog grew from ~200 to 800+
models and the same question started returning "no matching results" (with a nonsense
`pricing_currency in [usd, brl]` filter). Nothing in the *answering* logic changed — the
cause is a **silent mode switch**:

- The T793 **stuff-all** path grounds the LLM on the *entire* catalog when it fits
  `stuffAllTokenBudget` (default 40 000 tokens). That is why advisory questions worked:
  the model saw every row and could *reason* ("for a cheap chat model, consider …"), not
  just filter. The demo's compose even notes it: *"catalog is ~250 rows, so the copilot
  answers over the WHOLE catalog"*.
- The per-row context block is **verbose**: title + a snippet up to 320 chars + *every*
  short field labelled with its full T799 description. At ~250 rows that fit 40k tokens;
  at 800+ it is ~100–120k tokens, so stuff-all **silently falls back** to filtered
  NL→DSL retrieval. Filtered retrieval is fine for facet questions but brittle for
  advisory ones — the vague question has no clean facet, so the LLM planner invents a
  bad filter and returns nothing.

So the copilot didn't lose intelligence; it **quietly stopped grounding on the whole
catalog**. Simply raising the budget works but is expensive (~120k tokens/call, near the
`gpt-4o-mini` 128k context ceiling) — it doesn't scale.

**The fix — make the whole-catalog context cheap.**

- **LX.1 (T822) Compact, size-adaptive projection.** Most of the per-row cost is the
  snippet and the description-labelled fields — useful for *citation* grounding, wasteful
  for *whole-catalog reasoning*. Add a **compact** projection: one lean line per row —
  `label` + `kind`/`vendor` + the declared numeric/facet *key* fields — no snippet, no
  descriptions (~10× smaller). Make stuff-all **size-adaptive**: try the full projection;
  if over budget, retry compact; only if even the compact whole-catalog exceeds budget
  fall back to filtered retrieval. A catalog of thousands of rows then keeps the
  reason-over-everything behaviour at a fraction of the tokens. The compact key-field
  set is derived from the manifest (`sortable`/`facet` flags) rather than hardcoded. (An
  immediate, non-code mitigation exists — raise
  `turing.genai.copilot.stuffAllTokenBudget` / `stuffAllMaxDocs` via env — but it is a
  stopgap, not the scalable answer.)

(Retrieval-mode observability — logging which mode ran and, in the filtered path, the
exact query — shipped as T823; see CHANGELOG Block BL.)

**Relation to Block BK.** Advisory questions ("help me choose") are not facet retrieval —
they need the LLM reasoning over candidates. Compact stuff-all (this block) and the
LLM-assisted/HYBRID strategy (§LIX) are the two halves of answering them well at scale;
both stay fail-open and default to today's behaviour.
