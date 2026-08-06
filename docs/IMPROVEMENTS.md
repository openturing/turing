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

#### III.1.1 Where a deterministic mechanism already shipped

| Subsystem | Class | Current mechanism | Opportunity |
|---|---|---|---|
| Chat-flow router | `TurChatFlowEngineService` | Token overlap (Lucene PT analyzer) + LLM fallback | ✅ Shipped (§II) |
| RAG retrieval | `TurRagSearchToolService`, `TurRagContextBuilder` | Vector + BM25 fused via Reciprocal Rank Fusion (T24) per-agent toggle | ✅ Single-collection shipped (§III.2); multi-locale (T24b) pending |
| Vector store | `TurLuceneVectorIndex`, `TurLuceneVectorStore` | Vector index + BM25 inverted index + `hybridSearch` (T24) | ✅ Shipped (§III.2, §III.3); multi-locale split pending (T24b) |
| Spotlights | `TurSNSpotlightProcess` | MemoryIndex + QueryParser (T21) — phrase / slop / fuzzy / boolean syntax via per-request MemoryIndex; AND-default keeps V1 precision | ✅ V1+V2 shipped (§III.4) |
| Persona tone validation | `TurPersonaToneValidator` | Multi-locale Lucene stem matching: BrazilianAnalyzer + EnglishAnalyzer + SpanishAnalyzer (T22) | ✅ Shipped (§IV.3) |

#### III.1.2 Where one is still worth adding

| Subsystem | Class | Current mechanism | Opportunity |
|---|---|---|---|
| Chat analytics enricher | `TurChatAnalyticsEnricher`, `TurChatAnalyticsEngine` | Pluggable interface; default no-op | **TF-IDF intent classifier** (§III.5) |
| Chat memory | `TurChatMemoryService` | Chronological FIFO of last N turns | Possible — **conversation-relevance retrieval** (§IV.4) |
| MCP/Custom tool selection | `TurAgentChatExecutor.resolveToolCallbacks` | All tools passed to LLM each turn | Possible — **tool pre-filtering** (§IV.2) |

#### III.1.3 Where the LLM, or a delegate, is already the right tool

| Subsystem | Class | Current mechanism | Opportunity |
|---|---|---|---|
| Slot extraction | `TurChatSlotExtractionService` | LLM-only with structured-output prompt | None — semantic extraction is LLM's strength |
| Persona resolution | `TurAgentPersonaResolver` | Exact `__activePersonaId` variable lookup | None — already deterministic |
| Persona few-shot | `TurPersonaFewShotRetriever` | Vector topK over persona's example pool | None — vector is right for this |
| Search rules | `TurSNSearchRuleEvaluator` | EQUALS/CONTAINS/STARTS_WITH on params | None — string matching is the contract |
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

#### V.1.1 Per-feature recommendations


| Feature | Backing store | Why |
|---|---|---|
| §I-II flow router | Embedded | Hot path, sub-ms required, small data |
| §III.2/§III.3 RAG hybrid | SE-backed (route via `TurSearchEnginePlugin`) | Large corpus, multi-instance |
| §III.4 Spotlights | Embedded | Hot path, tiny data, no clustering need |
| §III.5 Analytics intents | SE-backed (ES MLT) | Shared state, persistent, dashboards |
| §IV.2 Tool pre-filter | Embedded | Per-turn, ~50 tools max, sub-ms required |
| §IV.3 Persona tone | Embedded | Per-turn, in-process strings |
| §IV.4 Conv. memory retrieval | Embedded per-conversation | Tiny data, per-conversation |

#### V.1.2 When the second SE-backed feature ships

, extract a small
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

#### VI.3.2 Contract-only (middleware)
- #16 — explicit save after walk → redesigned as "all walk saves coalesce in one transaction" (T13)
- #17 — cycle cap (already covered by `TurChatFlowSubFlowEngineIT` — don't duplicate)
- #18 — fallback HEURISTIC
- #19 — A/B window (already covered by `TurChatFlowAbVariantAssignmentTest` — don't duplicate)
- #20 — agent-owner guard
- #21 — judge retry / `isNonBlankRedirect` — 🗑 deleted in T16; replaced entirely by #22
- #22 — `onJudgeReject` declarative (already covered by `LlmJudgeGuardrailStrategyTest` — don't duplicate)

#### VI.3.3 Special
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

### VII.3 Lessons from the engagement worth keeping

These survive customer rotation — patterns / pitfalls that should be
internalized in the next customer's design phase, not re-discovered.

#### VII.3.1 Determinism + LLM hybrid is the production pattern

Three Education-customer tools (`compor_proposta_in_company`, `gerar_proposta_pdf`,
`aula_relampago`) follow the same shape: Groovy composes structure deterministically
(pricing tables, layout, mindmap), LLM contributes only *content* (tone, narrative
voice). Pricing and references must audit; tone is a stylistic dial. Document this as
platform doctrine and ship `scaffold.markdown` / `scaffold.pickQuote` /
`scaffold.buildMindMap` helpers (T98 in §VII.4).

#### VII.3.2 Slot orthogonality enables multi-flow coexistence

B2B flow uses `razao_social`/`num_pessoas`/`tema`/`prazo`; B2C uses
`name`/`cargo_atual`/`objetivo`/`area`. Same session accumulates both without
corruption. LLM router decides at trigger; presence of a distinctive slot
(`proposta_in_company`) determines UI without needing an explicit mode flag.

#### VII.3.3 `triggerDescription` is a denylist, not an allowlist

Allow-list of keywords kills greetings. Denylist + specificity-tiebreak is what works in
production.

#### VII.3.4 `gpt-4o-mini` at temp=0 is deterministic but NOT robust to JSON

Structured-output as a guardrail is a dead-end with this model class. Use tool-calling
with provider-side schema or the bilingual `LlmJudge` strategy instead (already encoded
as §I.7 non-goal — verbatim in §0).

#### VII.3.5 DSpace / public-content grounding beats LLM-invented references

Pull the source + extract a verbatim quote ≥40 chars + hand it to the persona in the
scaffold. The LLM has no opportunity to hallucinate an author. Generalizable beyond
academia: any public-content corpus (papers, manuals, knowledge base) works with the
same pattern.

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

#### IX.1.1 Two operational features

- **Model-agnostic** — Turing has it ✅ (Spring AI + 6 providers).
- **SDK + CLI** — Turing has SDK ✅; **no CLI**.

#### IX.1.2 Bottom line

Turing is already a deep-agent-grade platform in
infrastructure terms. The gap is one missing primitive (workspace) and
two missing UX surfaces (CLI + eval).

### IX.2 Parity moves — close the gap with primitives, not features

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

#### IX.11.1 Group 1 — workspace foundation

(~3 weeks, gates everything else):
- T111 `TurAgentWorkspace` service (1 week, reuses `TurStorageService`)
- T112 `workspace` Groovy helper (3 days)
- T113 workspace SSE bus (3 days, reuses `TurChatSlotEventBus` pattern)
- T114 auto-offload large tool results (1 week, reuses
  `TurToolCallbackPipeline` middleware seam)

#### IX.11.2 Group 2 — planning + cross-agent

(~2 weeks, reuses chat-flow + Custom Tool DSL):
- T108 `planningStep` node type (1 week, similar surface to `writeSlot`)
- T109 `agent.invoke(...)` helper (1 week, reuses agent + executor + analytics)
- T110 sub-agent analytics rows (3 days, extends T84 scorecard schema)

#### IX.11.3 Group 3 — CLI + eval

(~3 weeks parallel track):
- T125 `turing` CLI scaffold + dev mode (2 weeks)
- T126 `turing eval` (1 week, generalizes §VI.4.3 pattern)

#### IX.11.4 Group 4 — long-running + HITL

(~2 weeks):
- T121 suspend/resume primitive (1 week)
- T119 `humanApproval` node type (1 week, layers on T54 SSE)

#### IX.11.5 Group 5 — context enforcement

(~1 week):
- T123 token-budget enforcement (1 week, plumbing through T31 + T30)
- T115 memory compression to workspace (3 days, depends on T111)

#### IX.11.6 Group 6 — interop + ecosystem

(~3 weeks):
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

#### X.1.1 Whole-new endpoints that didn't exist when Spring AI 1.x shipped

OpenAI Responses API, Realtime API, Batch APIs of both vendors, OpenAI Evals / Stored
Completions / Distillation, Anthropic MCP Connector / Files API / Token Counting. Spring
AI 2.0 milestones M3/M4 added some of these — most stay open issues.

#### X.1.2 Server-side built-in tools

every "the model executes a tool in the vendor's infrastructure with no client code"
feature (OpenAI `web_search` / `file_search` / `code_interpreter` / `computer_use` /
remote `mcp`; Anthropic `web_search` / `web_fetch` / `code_execution` / `bash` /
`text_editor` / `memory_20250818` / `computer_use_20251124`). Spring AI's tool surface
is client-side `MethodToolCallback`-only.

#### X.1.3 Provider-specific governance

`safety_identifier`, `omni-moderation-latest`, `service_tier`, `reasoning.summary`,
`predicted output`, Anthropic Citations with the Search Results content type, Admin API
/ Usage API for per-tenant cost tracking.

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

#### X.1.4 Migration philosophy

this is **not** "rip out Spring AI". It's "keep Spring AI as the default, opt into
native paths per capability per agent per LLM instance." The per-LLM-instance capability
matrix (T132) makes that opt-in explicit: an admin enabling `openai-web-search-tool` on
instance X turns on T133 for that instance only, every other instance keeps falling
through to the Spring AI executor. Block F has **no monolithic flag day** — features
ship one at a time, each guarded by its own capability row.

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

## XII. Admin chat ↔ `@viglet/turing-react-sdk` convergence — shipped

> Block H (T219–T232) is complete: SDK gained LLM listing/context-info, per-turn
> LLM override, real `AbortController`, multipart agent chat, plain-LLM hook, and
> the admin `chat.page.tsx` was migrated onto the SDK with a vitest smoke suite.
> T243 (shared `useStreamingChatCore`) has since shipped — see
> [CHANGELOG.md](CHANGELOG.md).

### XII.2 Headless UI library — `@viglet/turing-react-ui` (the what/why)

#### XII.2.1 Problem

Turing's chat surfaces now live in two apps that must stay
visually independent: the **admin console** (React 19 + Vite + shadcn/Tailwind +
`@viglet/viglet-design-system`) and **viglet.com** (Next.js 15, its own brand).
As features land in both — code-interpreter HTML/canvas previews, `sandbox:`
artifact URLs, rich-content segmentation — components get **copy-pasted**
between them (the `SandboxPlayer` ⇄ `HtmlSandbox` duplication that triggered
this block). Putting them in `@viglet/turing-react-sdk` is wrong: that package
carries the **axios/API hooks** layer, and viglet.com deliberately stays on the
zero-dep, fetch-based `@viglet/turing-sdk` core (it must not pull axios).

#### XII.2.2 The layering decision (binding)

Three packages, one direction of
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

#### XII.2.3 The headless contract (how we "don't commit to design")

A react-ui
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

#### XII.2.4 Packaging principles

`tsc` → ESM `dist` (`"type":"module"`,
`sideEffects:false` for tree-shaking), peer-dep React (never bundled), consumed
via `workspace:*` (admin) / `file:` (viglet.com) symlinks — **rebuild the dist**
for consumers to pick up changes. SSR-safe (Next RSC): client-only concerns
(`createPortal`, `document`) are guarded; consumers import into `"use client"`
boundaries. Keep the package **publishable** (npm, semver) so external embedders
can use it too.

#### XII.2.5 Roadmap shape (see ROADMAP.md D.12)

Migrate by **ROI and design-weight**,
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


#### XIV.8.7 XIV.8.7 — ship Cloud default config as a GLOBAL seed ZIP (T373)

The product ask: a fresh Viglet Cloud deployment should come with a default LLM, default
search engine, etc. already configured for *all* users, loadable purely from the Docker
image with no post-deploy clicking. `application.properties` is the wrong tool — each
instance has ~20 fields plus a `providerOptionsJson` blob and an encrypted API key. The
right tool already exists: `TurExportImportOnStartup` scans `export/*.zip` on first boot
and imports LLM/SE/Embedding/MCP/etc. through `TurImportExchange` (guarded once by the
`EXPORT_AUTO_IMPORT` config marker). The only new guarantee needed is that the seeded
BYO-infra lands in the GLOBAL pool: `TurInfraTenantScope.stampOnImport` already keeps
`tenantId = null` when no tenant is bound to the thread (the startup import case), and
the import code preserves whatever `tenantId` the bundle carries — so the work is to
ensure the *export* side emits `tenantId = null` for instances intended as platform
defaults, then bake the ZIP into the image (`COPY seed/defaults.zip /app/export/`). Two
operational caveats: (a) every container must share the same `TurSecretCryptoService`
key or the exported (encrypted) API keys won't decrypt; (b) the `EXPORT_AUTO_IMPORT`
marker makes this one-shot per database — changing the defaults later means clearing the
marker or a versioned re-seed, which is not yet modeled.  ---

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
### XX.14 Amplification (2026-06-16) — the reusable platform capability

> T381–T385 were scoped narrowly as the cursarei
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
### XXII.7 Non-goal clarification — Turing is the backend, not a protocol adapter

> Turing is **the backend** — it is *not* trying to
> become a protocol adapter that fronts LangGraph/CrewAI/Mastra (AG-UI's other
> selling point). The goal here is UI-observable parity for Turing's *own*
> agent runtime, not multi-runtime federation. The optional canonical-protocol
> adapter (§XXII.6) is a 💭 bet, deliberately separated.

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

#### XXXI.0.1 The settled detail pattern, and why it is worth codifying

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

## XXXIV. Live Preview as a per-turn prompt-diagnostics surface (Block AK)

### XXXIV.0 The problem — the preview reads, but doesn't diagnose

The System-Prompt **Live Preview** (`TurSystemPromptPreviewService` →
`SystemPromptLivePreview`) reverses the opaque assembled system message into
labeled provenance segments (PERSONA / AGENT / MCP / FLOW). A redesign made it a
readable, Medium-style flowing article (rendered Markdown, per-passage hover
provenance, origin legend) instead of one scrolling `<pre>` per fragment. That
alone turned it into a tool people actually read — and reading it surfaced real,
quantifiable problems on a live agent (the Insper multi-flow concierge):

#### XXXIV.0.1 ~40–50% of an active-flow turn is inert or duplicated

The agent prompt hard-codes a whole "first-interaction / welcome / 3-experiences"
section that the flow addendum *explicitly orders the model to ignore* ("IGNORE TODO O
RESTO … siga o Goal"). On every active-flow turn that is ~800 tokens of dead weight the
model still has to attend to.

#### XXXIV.0.2 Rules are paid for 2–3×

The forbidden-vocabulary list (`talvez`, `impossível`, `não sei`, …) appears in the
Persona block *and* the agent "REGRAS DURAS"; "never ask for CPF/card" and "never cite
competitors" likewise. Same rule, multiple segments.

#### XXXIV.0.3 The node label was pure cost

The flow addendum emitted `=== ACTIVE FLOW STEP: <label> ===` — literal `===` (not
Markdown) plus a Portuguese node label inside an English scaffold, when behavior is
driven entirely by the `Goal:` line and the Hard rules already tell the model to ignore
persona/role framing. (Fixed ahead of this block: the delimiter is now a Markdown
`## ACTIVE FLOW STEP` heading and the label is dropped.)

#### XXXIV.0.4 The preview lies about the persona

`buildPreviewInternal` hardcodes `agent.getDefaultPersona()`, but at runtime
`TurAgentPersonaResolver.resolve()` reads a `__activePersonaId` set by a `persona`-type
flow node — so a flow can switch Marina → Professor Insper and the preview would still
show Marina. `TurSystemPromptValidatorService` has the same blind spot.

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

### XLV.5 Non-goals & open questions

- **Do NOT remove GET or the server-rendered links.** They are valuable for
  deep-linkable / bookmarkable / shareable search URLs, SEO-crawlable facet pages,
  and no-JS / progressive-enhancement HTML consumers. This block is **purely
  additive** — POST + structured response is opt-in; the GET + href contract is
  frozen, not deprecated (Phase 4's "deprecate" means *flag as legacy for the JS
  SDK path*, not delete).
- **CSRF on anonymous POST — settled during T707.** `TurSecurityConfigProduction`
  already lists `/api/sn/**` under `csrf().ignoringRequestMatchers(…)` and
  `/api/sn/*/search/**` under `permitAll`, so the public search POST is
  CSRF-exempt and anonymous out of the box. The SDKs therefore need **no** `/csrf`
  priming round-trip for search (they still do for the endpoints outside that
  matcher). Phase 3 must not add one.
- **Response-format negotiation mechanism — settled in T708.** A request field
  (`responseFormat`), not `Accept`: it works identically over GET and POST, which
  is what A/B-ing the two shapes against one query requires, and it shows up in
  the request log next to the response it produced.
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

**Fully shipped (2026-08-02) — T735–T738.** The design rationale that lived here
has been removed now that the block is complete; see `CHANGELOG.md` -> "Block AX"
for what shipped and `git log` for the implementation history (per the
IMPROVEMENTS.md header rule: no shipped implementation reports). In short:
`BentoFormHero` is the single drop-in that gives an own-hero bento form page the
`BentoEntityShell` "hero -> sticky save bar" morph plus the `<-` `backTo`
breadcrumb; the 3 static non-morphing save rows were fixed, the back-arrow was
swept across every own-hero surface, and every hand-wired two-piece form was
collapsed onto the component (with an `actions` escape hatch for pages that save
imperatively). `BentoScrollSaveBar` is now internal to `BentoFormHero`. The
locked conventions live in `components/bento/CONVENTIONS.md` and the
`turing-bento-ui` skill.

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

## LIV. Vectorless (Structured-Data) RAG — name it, feed it, integrate it (Block BF)

### LIV.1 The realization

Turing already ships a production-grade "talk to a structured
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

### LIV.2 Why name it

A vendor evaluating Turing for "LLM Q&A over our structured data
(product catalog, price list, model registry, spec sheet) without standing up a
vector database" has no way to find that Turing does this out of the box — they see
"RAG" and assume embeddings + a vector store + a chunking pipeline. Naming the mode
**Vectorless (Structured-Data) RAG**, giving it a knowledge-base *type* selector on
the SN-site AI form, a readiness path that advertises "needs only a default LLM",
and an end-user docs page turns a hidden strength into a positioning lever — and it
pairs directly with the public `openviglet/model-catalog` as the flagship live
example. This is the **structured-source-indexing** thesis (§XX) surfaced as a
product-level capability.

### LIV.3 The two real gaps (everything else exists)

The map is otherwise green: provider
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

## LVIII. Configurable, per-language NL ranking lexicon (Lucene analysis) (Block BJ)

> **On hold — §LIX has shipped.** `LLM_ASSISTED`/`HYBRID` (Block BK) handle other
> languages natively, so a per-language *deterministic* lexicon is no longer needed
> for i18n. This section remains the design for the deterministic multilingual path;
> build it only if that is wanted *alongside* the LLM path — the free, CI-testable
> `DETERMINISTIC` planner is also what `HYBRID` runs first, so a non-English lexicon
> would reduce how often `HYBRID` has to pay for an escalation.

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


### LVIII.1 LVIII.1 (T814) Externalize

Lift the four hardcoded sets into a resource-backed `TurRankingLexicon` (a default `en`
bundle under `resources/`) served by a `TurRankingLexiconProvider`.
`TurNLRankingPlanner` consumes the lexicon instead of static fields — English parity,
zero behaviour change, but now the vocabulary is data. This is the seam everything else
hangs off.

### LVIII.2 LVIII.2 (T815) Lucene analysis backbone

Replace the hand-rolled `tokenize`/`expand`/stopword code with a per-locale Lucene
`Analyzer`: `StandardTokenizer` → `LowerCaseFilter` → per-language `StopFilter` (Lucene
ships the stopword sets) → a Snowball/`*Stemmer` for the language → `SynonymGraphFilter`
built from the T814 synonym map. Crucially the **same analyzer runs over both the query
and each declared field's name + description**, so a query lemma matches a field lemma
after stemming — "cheapest"/"cheaper"/"cheap" and "barato"/"baratíssimo" all collapse to
one stem, and the synonym filter (not a hand map) does the adjective→concept bridge. New
inflections stop being a maintenance burden.

### LVIII.3 LVIII.3 (T816) Per-language + external override

A registry resolves the lexicon and analyzer by the copilot's request/site locale,
falling back to `en`; ship `pt` (and `es`) bundles.
`turing.genai.copilot.ranking.lexicon-dir` lets a deployment mount an external lexicon
directory so a new language or a tuned synonym set ships as a config file, not a
rebuild. Adding a language becomes: a stopword set (Lucene has it), a stemmer (Lucene
has it), and a small direction+synonym resource.

### LVIII.4 LVIII.4 (T817, 💭) Per-site / admin overrides

Optionally let a site or global settings extend the direction/synonym lexicon with
domain terms, editable in admin and merged over the resource defaults — promote only if
a deployment needs vocabulary beyond the shipped bundles.  **Invariants preserved.** The
planner stays **deterministic and fail-open** — no LLM is reintroduced; an unresolved
locale falls back to `en`; a lexicon/analyzer failure degrades to no-sort exactly as
today. The engine (`TurDslLuceneExecutor.buildSort`) is untouched. A possible future
alignment (out of scope here) is to reuse the target SN site's own index analyzers so
ranking tokenization matches how the field was indexed.

## LXI. Content-fit tuning loop — model-aware, enforceable, and it tells you (Block BN)

> Motivated by building T704 (Block AT's closer). The loop the original Persona
> Match ask described — *"adjust the persona to the content and the content to the
> persona, continuously"* — now has its memory (per-cell history), its signal
> (drift) and its advice (adjustment suggestions); since T825 the score honours the
> project's chosen model, since T826 the "good fit" bar is the project's own number
> rather than a literal duplicated across the stack, and since T827 a scheduled run
> that regresses a project can say so over a webhook instead of waiting for someone
> to open the studio, since T828 a pipeline can fail a build on it
> (`turing match run … --min-fit --fail-on-drop`), and since T829 both verdicts are
> bound to a provider-enforced schema with their degradations counted instead of
> silent, and since T830 both the studio and the exported PDFs report movement per
> content and per persona rather than only for the project as a whole. What remains
> is the 💭 apply action (T831).

### LXI.7 Advice that must be retyped (T831 — 💭)

T704 is advisory by construction, and that invariant is right for content: Turing
does not own the CMS a page lives in, so "apply" there would mean writing into
someone else's system. But **persona** adjustments target a Turing entity, and
today an operator who agrees with "the reading level is set too high" leaves the
studio, opens the persona form, and retypes it — with no record that the change came
from a fit suggestion, which makes the next run's drift unattributable.

The bet is an opt-in *Apply* on PERSONA items only, presented as a **field-level
diff** over the audience facet (`readingLevel`, `domainExpertise`,
`vocabularyCeiling`, `comprehensionNotes`) that the operator confirms field by field
— never a bulk accept, never automatic, because an LLM suggestion rewriting an
audience definition unattended is exactly the failure mode the advisory-only rule
exists to prevent. Stamping the applied suggestion is what turns the loop from
"scores that move" into "changes with causes". Kept at 💭 until the trust surfaces
(T825, T829) are in: applying a suggestion derived from a verdict produced by an
unknown model is not something to build first.

## LXII. Answer-first search — the steerable, auditable answer engine (Block BO)

_Fully shipped — Block BO (T832–T845). See [CHANGELOG.md](CHANGELOG.md) and `git log`._

## LXIV. The answer engine as a first-class search lens (`/answer`) (Block BQ)

### LXIV.0 The problem — one engine, one surface, and it's the wrong audience

Block BO
shipped the whole answer engine: a streaming `GET /api/sn/{siteName}/answer` (T833),
numbered citations (T832/T836), steerable filters (T838), provenance (T839),
freshness (T840), artifacts (T841), a research trace (T843), permalinks (T844) and
signal capture (T845). It reached exactly **one** surface — an expand/collapse panel
inside the *public* search page (`_aimode`, T835). The admin console never got it:
`SnLaunchBar` still renders two launcher tiles, Open Search (`/sn/{name}`) and ANN
(`/ann/{name}`). Three gaps follow.

#### LXIV.0.1 The three gaps that follow

- **The gates disagree, and the narrower one wins.** The ANN tile requires
  `annEnabled` — an agent that is enabled *and* `ragEnabled`, *and* a site not in
  `VECTORLESS_STRUCTURED` (T790) — re-derived in the frontend. The answer endpoint
  requires far less: GenAI on and an effective agent with an LLM
  (`TurSNAnswerService`), which a vectorless catalog site satisfies. So the sites
  Block BF deliberately made *vector-free* are precisely the sites whose console
  shows **no AI lens at all**, even though they answer.
- **No operator view of the answer.** The visitor panel is tuned for a reader:
  prose first, provenance collapsed, trace optional, no raw document. The operator
  question is the opposite one — *why did it say that?* — and its evidence (KB mode,
  rerank strategy, per-source `rankingExplanation`, the tool trace, the raw hit) is
  exactly what the reader view hides.
- **No way to localize a bad answer.** A wrong answer has three possible causes:
  retrieval missed the document (recall), it was retrieved but ranked away (rerank),
  or the evidence was right and the synthesis wasn't. `/sn` and `/ann` already expose
  the first two lenses; without the third, the comparison can't be made in the
  console.

#### LXIV.0.2 The design — `/answer` as a peer route, not a query parameter

`/ann` is the
precedent: it exists because "raw vector hits with scores and a JSON sheet" is an
*operator* view of retrieval that no visitor wants. `/answer` is the same move one
layer up. Deep-linking `/sn/{name}?_aimode=true` would be cheaper, but it is a link
into lens #1 with a panel open — it inherits the reader-tuned chrome, and it still
leaves vectorless sites without a tile. The block adds **no backend**.


## LXV. Block AV follow-through — the gaps the POST-native migration left (Block BR)

> Block AV (§XLV) closed the query-string bug class: search is POST-native, the
> response can be link-free, and both JS SDKs drive it. None of the items below
> are part of that contract — they are things the migration *exposed*, either
> because a design note promised something the code doesn't do, or because a
> primitive that was fine for GET-only turned out to invite a bug once a second
> transport existed.


## LXVIII. The search cache is off by a thousand and never evicted on write (Block BU)

> Read while deriving the T858 cache key from the search context. All three are
> latent because `turing.search.cache.enabled` defaults to `false` — which is
> also the reason they have survived: the feature is opt-in and nobody who opted
> in has reported it.

## LXIX. The quality gate stops at the package boundary (Block BV)

> Block BS fixed the gate for one package. Adopting the same two changes across
> the workspace — declare the runner, then *confirm CI runs it* — turned up the
> same shape five more times, in packages nobody had checked because
> `@viglet/turing-sdk` was the one that happened to be under the microscope. The
> pattern is consistent and worth naming: **every gate here was written per
> package, so a package added later inherits nothing.** `validate-js.yml`'s
> matrix is a hand-maintained list; `prepublishOnly` is per package; the publish
> workflows were copied from each other and then diverged. Nothing enumerates the
> workspace and asserts each publishable package is covered.

### LXIX.6 A total-only baseline cannot localize a regression (T878)

T863 gave react-sdk a delta guard, but `strict: false` means the baseline is a
single `total`, because per-file baselines over ~100 `tsc` outputs would be an
unreviewable manifest. So the guard can now say *"the package grew 4 kB"* and
never *"which file"* — and it has already had to: the 7.8 kB that put react-sdk
over its old ceiling is still unattributed.

A middle ground exists: baseline per **directory** (`core/`, `ui/`, `hooks/`)
rather than per file. Roughly a dozen numbers, reviewable, and enough to point at
the change.

### LXIX.7 A Java enum and its TypeScript mirror drift silently

`ChatDisabledReason` exists twice: as a Java enum in `TurSNSiteGenAiAPI` and as a
string-union mirror in `@viglet/turing-sdk`, with a `CHAT_DISABLED_HINTS` record keyed
by it. T790 added `MISSING_DEFAULT_LLM` to the Java side and the mirror was not updated,
so for eight months the vectorless readiness reason narrowed to `undefined` in every TS
consumer — found only because T853 built a UI on that reason. The record's
exhaustiveness gives no protection: it constrains the mirror to itself, never to the
enum. The gate that is missing is one test that parses the declared Java enum constants
and asserts set equality with the union, run in the same job as the rest of the package
suites. The same shape guards `TurSEFieldType`, `TurAnswerSignalKind` and every other
enum the SDKs re-declare, so make the check take the pair list rather than hard-code
one.

### LXIX.8 Two governed prose files declare the same 13 section anchors (T902)

`STRATEGY.md` and `IMPROVEMENTS.md` both number their sections from the same outline,
and 13 anchors collide: §VIII, §IX, §IX.1–§IX.4, §X, §X.1, §X.5, §XII, §XII.2, §XIV,
§XVI. An anchor names one section, so a pointer to any of them resolves to two — and
every verb that reads one refuses rather than guessing.

Nothing surfaced this until the lint learned to check it, which is the part worth
noting: the collision has been latent for as long as both files have been governed, and
it was invisible precisely because no *pointer* happened to aim at a doubled anchor. The
condition was always there; only the gate is new. A rule enforced from the pointer end
alone reports a defect when someone stumbles into it, not when it is introduced.

The fix is configuration, not rewriting: `[refs] <role> = "<prefix>"` in `roadkeep.toml`
gives one of the two files its own namespace, after which its addresses are written
`§<prefix>:<x.y>` and the two outlines stop competing. Choosing which file takes the
prefix is the only real decision — `STRATEGY.md` is the smaller surface and the one
whose anchors are cited least, so it is the cheaper side to renumber.

Filed here because Block BV's thesis is gates that reach one surface and not the next.
This is the same shape one layer up: the gate over the governed docs was not checking
what the docs were free to get wrong.

## LXX. What the link-free response and its consumers still lack (Block BW)

> Block BT migrated the three `turing-app` consumers off `store.data`. What it did
> not do is finish the **shape** they were reading it for, or extend the fix past
> the one app. Every item below was read out of the code while shipping T865–T869.

### LXX.1 A site's icon is silently dropped POST-native (T879)

`TurSNSiteSearchWidgetBean` carries `icon` — a plain Iconify name, no href, no
round-trip — and `TurSNStructuredSearchBean` has no field for it. So T865 had to
write `siteIcon: null` on the structured branch and let the page fall back to the
Turing logo. That is a user-visible regression on every site that configured an
icon, and it is invisible in review because the fallback looks deliberate.

It is also the cheapest kind of gap to close: the value is already a scalar with
nothing URL-shaped about it. Carry it on the structured bean and drop the
hardcoded `null`.

### LXX.2 `similar` and `spotlights` are written and undeclared (T880)

`toStructured` ends with `.setSimilar(widget.getSimilar())` and
`.setSpotlights(widget.getSpotlights())`, so both are on the wire. Neither is
usable:

- `TurStructuredSearchResponse` (owned by `@viglet/turing-sdk`, re-exported by the
  React SDK) declares **no `similar` field at all**;
- `spotlights` is declared `unknown[]`, which a typed consumer cannot read without
  casting.

The asymmetry is the bug: the server pays to compute and serialize both, and the
only clients that can use them are the legacy ones. Declare `similar`, and give
the spotlight entry a real type rather than `unknown`.

### LXX.3 The projection belongs in the SDK, not in one app (T881)

`search/lib/search-view.ts` exists because three components each reached into
`store.data` and each got it wrong the same way (§LXVII). It answers "read a
response without caring which shape it is" — and it answers it for exactly one
app, because that is where it lives.

The other consumers have the same question and no answer. The vanilla SDK's
`SearchController` fills both `data` and `structured` for the EDS blocks;
`@viglet/turing-react-ui` re-declares a structural `TuringRawDocument` precisely
so it can stay dependency-free, and renders whichever chip shape it gets by
ignoring the difference rather than resolving it. `toSearchView` was deliberately
exported as a pure function of the store, so lifting it next to
`resolveDocuments` / `resolveGroups` — the two functions that already do exactly
this for documents and groups — is a move, not a rewrite. The app then imports it
and keeps only its own React-flavoured `useSearchView` wrapper.

### LXX.4 `TuringResultList`'s chip is the T867 bug, still shipping (T882)

`TuringRawDocument.metadata` is typed as the union of both chip shapes and the
docblock even says "narrow on `"href" in chip` if you act on it" — but the
component never acts on it: it renders `text` and nothing else. A viglet.com
consumer therefore gets exactly the dead badge T867 removed from the admin app.

The fix has the same shape as T867 and the same constraint as the rest of
react-ui: an optional `onToggleFacet` prop (design-agnostic, no SDK import — the
package must stay zero-runtime-dependency), narrowing on the union, so the
consumer decides whether a chip is actionable.

### LXX.5 The README still teaches the shape that broke (T883)

`packages/react-sdk/README.md` documents facet rendering twice — once as
`data.widget.facet.map(…)`, once as `turing.data?.widget.facet` — and both are
`null` in the default mode. The README is the first thing a new consumer reads, so
it currently instructs them to write T865 on their first page, and the second form
(`?.`) will do it *silently*: no crash, just an empty rail.

Rewrite both examples on `useTuringFacets`, which has resolved the two shapes
since T710, and say plainly that `data` is the legacy transport.

### LXX.6 `toggle_facet` cannot reach a secondary facet (T884)

`findStructuredFacetItem` (T713) looks the target up in `store.structured?.facets`
and stops there. A field configured as a *secondary* facet lives in
`structured.secondaryFacets`, so the co-browse client tool returns
`Facet not found: field=value` for it — then falls through to `findFacetLink`,
which reads `store.data?.widget?.facet` and is primary-only as well, so the legacy
transport has the same hole.

T869's `ChipState` had to solve the identical problem for document chips and reads
both lists; this should do the same. Worth checking whether any other consumer
enumerates `facets` without `secondaryFacets` in the same pass.

### LXX.7 Nothing stops the fourth consumer (T885)

T865–T867 were one mistake made three times, in three files, by three edits that
each looked locally reasonable. T868 makes *that page* catchable. It does not make
the *class* catchable: a new component can still write `store.data?.widget?.…`
today and pass every suite, because the value is legitimately non-null in legacy
mode and the type system cannot tell the difference.

The backend already has the pattern for this — `TurDomainLayerConventionsTest`
asserts an architectural rule as a test rather than a review habit. The frontend
equivalent is a convention test (or an ESLint `no-restricted-syntax` rule) over
`frontend/apps/**` that fails on a `store.data` read outside the projection
module. Kept as 💭 because the boundary needs stating precisely first: the SDK's
own hooks read `data` on purpose, and the legacy fallback inside the projection
must stay allowed.

## XXXVII Security hardening (post-audit)

Block AN's standing home. The audit that opened it is shipped and its rationale sections
went with it; the heading is reopened here because the same file keeps producing the
same class of finding. `TurSecurityConfigProduction` states its permitAll list **twice**
— once for the Keycloak branch, once for local auth — and the two copies are maintained
by hand. Every drift filed below is a path present in one copy and absent from the
other, or absent from both because a shipped endpoint was never added.

### XXXVII.17 The public search page is unreachable under Keycloak

`mvc.matcher("/sn/**")` appears in only the local-auth branch. On a Keycloak deployment
the flagship anonymous surface — the public faceted search page — therefore redirects a
visitor to the SSO login, while `/ann/**` and `/pages/**` beside it are permitted in
both branches. Every API the page calls (`/api/sn/*/search`, `/ac`, `/click`) is already
anonymous in both, so the gap is the SPA route alone: the data is public and the page
serving it is not. Confirm against a Keycloak-profile deployment before assuming a
visitor hits the login — a reverse proxy or static-asset rule may mask it in the current
hosting — and if it reproduces, add the one matcher to the Keycloak branch rather than
widening anything else.

### XXXVII.18 One permitAll list, written twice, drifts

Roughly forty matchers are spelled twice, once per auth branch, with no mechanism
holding the copies equal. That duplication is not a style problem: it is the direct
cause of §XXXVII.17 (a path in one copy only) and of T889 (a shipped endpoint in
neither), and T889's guard test had to assert a count of two precisely because the list
is doubled. Extract the anonymous surface to one `private static final String[]` — or a
small builder both branches call — so a new public endpoint is added in one place and
cannot be half-added. The two branches genuinely differ in their login and logout
wiring, so this is about the shared matcher list only, not about merging the branches.
The T889 source-level guard should then assert the shared constant rather than counting
occurrences, which is a weaker check that a refactor would silently satisfy.

## LXXI The no-entity-caching rule is enforced only where it was first broken (Block AC)

> Found while enumerating the explicitly-keyed caches for T895. Block AC's rule is
> unqualified — never cache a JPA entity — but its guard walks repositories only, so the
> hottest service-level cache in the product holds six entity types and passes the lint
> for the policy it violates. The evidence that this is not theoretical is in that cache's
> own javadoc: it already works around the exact failure, one association at a time.

### LXXI.1 The search snapshot caches six kinds of JPA entity, which T486 forbids (T898)

Block AC's rule is stated without qualification: cache an immutable read-model or DTO,
**never a JPA entity**, because a cached entity carries lazy proxies that throw
`LazyInitializationException` the moment a hot path dereferences one outside a
transaction. T486 removed the whole class of bug — at the repository layer.

`TurSNSiteSearchSnapshot` is a `@Cacheable` record whose six components are `TurSNSite`,
`List<TurSNSiteFieldExt>`, `Map<…, List<TurSNSiteCustomFacet>>`, `Map<…,
Set<TurSNSiteFieldExtFacet>>`, `List<TurSNSiteLocale>` and `List<TurSNSiteCustomSort>` —
entities, all of them, on the hottest read in the product.

It has already been bitten. `getSnapshot` deliberately calls
`findByNameIgnoreCaseWithGenAi` rather than the plain finder, and says why: *"The
snapshot caches the entity, so the T383 hybrid-ranking path that reads `turSNSiteGenAi`
on a cache hit would otherwise hit a detached LAZY proxy."* That is the T486 failure,
diagnosed and worked around one association at a time — the treadmill the policy exists
to end. The next association added to any of the six repeats it.

`TurCacheAnnotationConventionsTest` cannot see this: it filters to types named
`*Repository` or under `persistence.repository`, so a service cache is invisible to the
guard for the rule it enforces. Two things are wanted, and the second is the durable
one: project the snapshot into a read-model of records, and widen the lint past the
repository boundary so the policy is checked wherever caching happens.
