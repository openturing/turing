# Turing — Improvements & Roadmap

> Single source of truth for proposed improvements, architectural refactors,
> and the integration-test investments that go with them. Each entry is
> self-contained: a contributor (human or agent) can pick one without
> re-deriving context. Entries are removed once shipped — `git log` is the
> authoritative history of what's actually in the codebase.

## How to read this doc

**Status legend**

| Symbol | Meaning |
|---|---|
| ✅ | Shipped (in `git log`; will be removed from this doc next pass) |
| ⏳ | Partial — direction is right, more work remains |
| 📋 | Designed but not started |
| 💭 | Idea worth exploring; needs design |

**Categories**

- **A. Architectural** — large refactors that change how the runtime is shaped
- **B. Performance** — measurable wins (latency / memory / cost)
- **C. Capability** — new features or hardening of existing ones
- **D. Testing** — coverage / harness investments that unlock the above

**Cross-references** — every entry uses absolute paths from the repo root.
Linked code may have moved; verify against current `git ls-files` before
acting.

## Table of contents

- [0. Execution order — single ordered task list](#0-execution-order--single-ordered-task-list)
- [I. Chat-flow execution harness — the big architectural refactor](#i-chat-flow-execution-harness--the-big-architectural-refactor)
- [II. Chat-flow router (already shipped + backlog)](#ii-chat-flow-router-already-shipped--backlog)
- [III. Lucene & Search Engine across the GenAI surface](#iii-lucene--search-engine-across-the-genai-surface)
- [IV. New ideas from the latest session](#iv-new-ideas-from-the-latest-session)
- [V. Cross-cutting decisions](#v-cross-cutting-decisions)
- [VI. Integration test plan for the harness refactor](#vi-integration-test-plan-for-the-harness-refactor)

---

## 0. Execution order — single ordered task list

> Flat, top-to-bottom backlog. Pick the lowest-numbered task whose
> dependencies are ✅ done. Sections §I-§VI below remain the source of
> truth for *what* each task means and *why* — the table here is the
> executable view.
>
> **Legend** (same as the global one): ✅ shipped · 🛠 in-progress ·
> 📋 designed, not started · 💭 idea, needs design · ⏳ partial.
>
> **Status snapshot (2026-05-22 end-of-day, post-deferred-skeletons sweep)**:
> - **Block A** — infrastructure ✅; **9 tests now real**. Post-T16 audit
>   deleted **11 obsolete skeletons**; then 2026-05-22 PM implemented all
>   4 remaining deferred bodies: ✅ **#3** (tool-strip via declarative
>   `toolsEnabled=false`), ✅ **#6** (freshlyTriggered skips earlyAdvance),
>   ✅ **#1** (persona resolved exactly 1× post-advance), ✅ **#16**
>   (saves coalesce into one commit via T13 `@Transactional`). Only
>   **cascade ascent** remains deferred (needs new `nested-sub-flow`
>   fixture + `subFlowId` remap in `ChatFlowImportTestUtil`).
>   Verified: `mvn verify -Dit.test=…Structural…,…Contract…,Harness…` →
>   **15 ran / 0 failed / 1 skipped** in 3:43.
> - **Block B** — ✅ COMPLETE. Inverted harness shipped, all legacy code
>   paths deleted, 17 of 22 patches from §I.3 are gone.
> - **Block C** — ✅ T17; everything else 📋, fully unblocked since T16
>   landed (no more dependency on Block B).
> - **Test infra fix (2026-05-22)**: `AbstractTuringSpringIT` now sets
>   `spring.artemis.embedded.persistent=false` to keep multiple Spring
>   contexts in a single Maven fork from deadlocking on Artemis
>   `FileLockNodeManager.lock` (each cached context spawned its own
>   `EmbeddedActiveMQ` and they all targeted the same per-JVM `DB_DIR`).
>   Before: running 2+ IT classes back-to-back hung indefinitely on
>   Spring context startup with no error logged. After: 3 IT classes
>   coexist cleanly.
>
> **Dependency rule of thumb** *(historical — kept for context):* "Safety
> net" was supposed to land before §I.5 — in practice we shipped Block B
> against the smoke ITs (T1+T5) and impacted unit tests, with the
> structural/behavioral bodies (T3, T4, T6, T7) still open. The smoke
> + impacted-unit coverage caught zero regressions through T9-T16, but
> the structural bodies remain a useful investment for future refactors.

### Block A — Safety net (test scaffolding + fixtures)

> **Status (post-T16):** scaffolding is shipped and green; **test bodies
> still pending.** T1+T2+T5+T8 are ✅ (infra, migration, fixtures, cross-link).
> T3+T4+T6+T7 stay ⏳ — the skeletons exist with detailed Javadoc contracts
> but their bodies are still `fail("Not implemented")`. The Block B harness
> refactor (T9-T16) ran AHEAD of these bodies — Block B's success is
> measured by smoke ITs + impacted unit tests staying green; the more
> rigorous structural/behavioral assertions remain on the to-do list.

| #  | Status | Depends on | Task | Detail in |
|----|--------|------------|------|-----------|
| T1 | ✅ | — | Test infra: `TurAgentChatExecutorMockSupport` + `ChatModelHarness` + `AbstractAgentExecutorIT` + `llm-it` Maven profile | §VI.4, §VI.6 Phase 0 |
| T2 | ✅ | T1 | Migrated 15 existing skeletons into Structural (12) + Contract (2) + Behavioral shell; 3 old files deleted | §VI.6 Phase 0.5 |
| T3 | ✅ | T2 | Post-T16 audit deleted 6 obsolete skeletons (#7, #9, #10, #11, #12, #14 — all regen-related). 2 survivors implemented 2026-05-22: ✅ **#1** `resolvesPersonaAfterFlowSelection_picksWalkWrittenPersonaForInitialCall` (`@MockitoSpyBean TurAgentPersonaResolver` + persona-switch fixture with seeded `__force_route=lucas`; asserts `resolve(agent,conv)` called exactly 1× and prompt's SystemMessage carries Lucas voice not Marina), ✅ **#6** `freshlyTriggeredTurn_skipsEarlyAdvance_preservesEntryNodeCursor` (procedural Tier-1 router via Portuguese-overlap triggerDescription; asserts advance invocations = 0 + cursor stays on `ai-cargo` + no `cargo` slot captured + `callCount==1`). | §VI.6 Phase 1 |
| T4 | ✅ | T2 | **5/5 implemented:** ✅ **#18** fallsBackToHeuristic, ✅ **#20** discardsForcedFlow, ✅ **BR-doc CPF** moved to `ChatFlowOpsBrDocUnitTest`, ✅ **#16** `persistsStateInsideTransparentWalkAndAgainAfterAdvance_doubleSavePattern` (`@MockitoSpyBean TurChatFlowStateRepository` for save count + `TransactionExecutionListener` attached to `ConfigurableTransactionManager` for afterCommit count; asserts `commitCount < saveCount` proving T13 @Transactional coalesces walk saves). Only **cascade ascent** still deferred (needs `ChatFlowImportTestUtil` extension for `subFlowId` remap across nested-fixture bundle). | §VI.6 Phase 2 |
| T5 | ✅ | T2 | 3 harness fixtures + `HarnessFixtureSmokeIT` (3 real `@Test`s passing; substring/structural invariant guards) | §VI.5, §VI.6 Phase 3 |
| T6 | ✅ | T5 | Post-T16 audit deleted 5 obsolete skeletons (#2, #4, #8, #13, #15). **#5** already implemented (T2/T11). ✅ **persona-tone import guard** implemented (round-trips `mandatoryTerms`/`forbiddenTerms`/`tone`/`languageStyle` across all 3 fixtures). ✅ **#3** `stripsToolsOnInitialCallWhenActiveNodeForbidsToolCalls` implemented 2026-05-22 (tool-free fixture pre-declares `toolsEnabled=false` on both `ai-coupon-code` AND `ai-deliver` to survive HEURISTIC earlyAdvance; assertion filters captured prompts for empty `toolCallbacks` list — the tool-strip branch signature). | §VI.6 Phase 4 |
| T7 | ✅ | T1 | Post-T16 triage: **deleted 3** skeletons paired with removed code (#3 tautological with structural pair / #10 OVERRIDE block gone / #12 re-inline gone). **Implemented 3** bodies driving `engine.advance(...)` + locally-built `OpenAiChatModel` (gpt-4o-mini @ temp 0) — sidesteps the `TurAgentChatExecutorMockSupport @Primary` factory that wins via component-scan even without `@Import`: ✅ **#1** post-walk persona voice (Lucas peer markers vs Marina formal), ✅ **#7** post-advance reply is question-for-new-node (no anchoring on previous node), ✅ **#13** chip congruence (question admits all 4 inlineOptions). 3/3 green at `mvn verify -Pllm-it` (~37s real LLM calls). | §VI.6 Phase 5 |
| T8 | ✅ | T3..T7 | Cross-link pairing done; pairing matrix in §VI.3 annotated with T16 status (🗑 deleted / ⏳ redesign). §VI.8 done-criteria items that depend on body implementations (no `@Disabled`, no `fail()`) remain open in surviving T3/T4/T6 skeletons. | §VI.6 Phase 6, §VI.8 |

### Block B — Harness inversion (§I.5, COMPLETE)

> **Block B shipped 2026-05-22.** T9-T16 all ✅. The executor now runs
> `engine.advance(...)` BEFORE the LLM call, composes ONE prompt with the
> post-advance node already in context, makes ONE LLM call, and emits.
> No regen, no retry, no `forbidsToolCalls(String)` substring fallback, no
> feature flags — the inverted path is the ONLY path. Patches #1, #2, #3,
> #4, #5, #6, #7, #8, #9, #10, #11, #12, #13, #14, #15, #16, #21 are gone
> from the codebase. Middleware patches #17, #18, #19, #20 + silent
> telemetry + #22 (`onJudgeReject` declarative) survived intact per §I.6.

| #   | Status | Depends on | Task | Detail in |
|-----|--------|------------|------|-----------|
| T9  | ✅ | — | Inverted `advance → LLM call`: `chatFlowEngineService.advance(...)` runs in setup BEFORE `chatModel.call(prompt)`. Strategy decides cursor from user message alone (`assistantMessage=null`); LLM composes a single reply with the post-advance node in its prompt. **Removed patches #6, #7, #8, #11, #14, #15.** | §I.5 step 1 |
| T10 | ✅ | T9 | Persona resolved ONCE per turn (post-flow-context, post-early-advance). 1st pre-flow resolution + 2nd post-flow conditional both gone; `activePersona` and `effectivePersona` collapse into a single value. Tool callbacks now built from the (post-walk) persona. **Removed #1, #2.** | §I.5 step 2 |
| T11 | ✅ | T10 | Declarative `ChatFlowNode.toolsEnabled` field added (Boolean: TRUE/null = allowed, FALSE = stripped). Backend reads via `forbidsToolCalls(ChatFlowNode)`; legacy substring matcher fallback removed in T16. Frontend chat-flow editor exposes the toggle (Checkbox) + i18n EN/PT. **Removed #3, #4, #5** + retired `forbidsToolCalls(String)`. | §I.5 step 3 |
| T12 | ✅ | T11 | `regenerateReplyForNewNode`, `buildRegenOverride`, `trimToRecentTurns`, `REGEN_HISTORY_TURN_BUDGET` were `@Deprecated(forRemoval=true)` in T12, then DELETED in T16 along with the legacy `else if (flowContext != null)` block in `execute()`. **Removed #9, #10, #12, #13.** | §I.5 step 4 |
| T13 | ✅ | T12 | `TurChatFlowEngineService.advance(...)` now `@Transactional` — strategy mutation + intermediate walk saves + post-walk save coalesce into ONE commit per turn (was N+1 implicit commits). Intermediate `save()` calls kept for in-memory persistence-context updates within the transaction. | §I.5 step 5 |
| T14 | ✅ | T13 | Second-opinion judge retry block was gated by `turing.chat.judge-retry.enabled` in T14, then DELETED in T16. `onJudgeReject` (#22, shipped 2026.2.31) is now the SOLE reject driver: `advance_with_literal` / `reprompt` / `block`. `isNonBlankRedirect`, `shouldRetryVerdict`, `RETRY_MIN_USER_MESSAGE_LENGTH` deleted. **Removed #21.** | §I.5 step 6 |
| T15 | ✅ | T14 | Audited: #17 cycle cap (`TRANSPARENT_WALK_MAX=16` @ engine:1244), #18 strategy fallback (engine:418), #19 A/B window (`isInExperimentWindow` @ engine:881), #20 agent-owner check (executor:1223), silent telemetry (`recordSessionStartSafely` + `recordTurnTelemetry`) — all intact post-T9..T14. Zero code change. T16 explicitly preserved these. | §I.5 step 7 |
| T16 | ✅ | T15 | Terminal refactor — all feature flags + all `@Deprecated(forRemoval=true)` symbols + the legacy `execute()` branch DELETED. The inverted harness is the ONLY path. Impacted tests cleaned (10 substring `@ParameterizedTest` cases + 9 `isNonBlankRedirect_*` tests + helpers removed). Smoke + impacted unit tests verified green: HarnessFixtureSmokeIT 3/3, TestSupportSmokeIT 1/1, TurAgentChatExecutorStructuralIT 4 pass + 15 still @Disabled, LlmJudgeGuardrailStrategyTest 7/7, ChatFlowOpsTest 19/19. | §I.5 step 8 |

### Block C — Post-refactor capabilities (Block B is shipped — all of these unblocked)

> **Block B is complete** — every Block C task that gated on T16 (or any
> earlier Block B step) is now unblocked at the prerequisite level. Test
> bodies in T3/T4/T6/T7 stay open but do NOT gate Block C tasks since
> Block B already shipped and is smoke-green.

| #   | Status | Depends on | Task | Detail in |
|-----|--------|------------|------|-----------|
| T17 | ✅ | — | Documented the Spring AI 2.0.0-M6 OpenAI streaming-buffer limitation on `TurAgentChatMode` Javadoc + i18n on the agent settings form. | §IV.1.a |
| T18 | ✅ | T17 | Hand-rolled `TurOpenAiStreamingChatModel` (`WebClient` → `/v1/chat/completions` with `stream:true` + SSE parser for `data: {...}` lines emitting one `ChatResponse` per `delta.content`). New `TurGenAiLlmProvider#createStreamingChatModel` default delegates to `createChatModel`; `TurOpenAiLlmProvider` overrides to return the streaming model. `TurLlmModelFactory#createStreamingChatModel` wraps in same resilience pipeline. `TurAgentChatExecutor` STREAM branch calls `createStreamingChatModel(...)` only — CALL keeps the SDK path. Validated by `TurOpenAiStreamingChatModelBehavioralIT`: **123 chunks** per turn vs ~1 on the SDK path (chunks count is the streaming witness; first-chunk latency is too variable for a stable bound). i18n + `TurAgentChatMode` Javadoc updated to drop the limitation warning. | §IV.1.b |
| T19 | ✅ | — | `FIELD_CONTENT` now indexed as `TextField` (was `StoredField` — invisible to BM25). New `TurLuceneVectorStore.bm25Fallback(query, topK)` parses via classic `QueryParser` on `StandardAnalyzer`, OR-default operator, escapes special chars; tags every hit with `_keyword_only_fallback=true`. `TurRagSearchToolService.searchKnowledgeBase` drops the legacy `similarityThreshold=0.5` request filter, fetches all topK, and falls back to BM25 when vector returned 0 hits OR top-1 cosine score < `MIN_VECTOR_CONFIDENCE=0.5`. Tool result prepends `⚠️ KEYWORD-ONLY MATCH` header; `prompts/tools/rag/search_knowledge_base.md` instructs the LLM to soften its answer when present. **Per-agent toggle**: new `TurAIAgent.ragBm25Fallback` boolean (default `true`, Liquibase `v2026.2.7.9.yaml`) exposed via the agent settings form (toggle inside the RAG SectionCard, EN/PT i18n) — flipped through to the tool via `ToolContext` lookup. **Limitation**: chunks indexed before T19 used `StoredField` (no inverted index) → invisible to fallback; vector still finds them. Re-index makes them keyword-searchable. 3 new unit tests pass (literal-term match, blank-query guard, special-char escaping). | §III.3 |
| T20 | ✅ | — | `TurSNSpotlightProcess.getSpotlightsFromQuery` substring `.contains(...)` replaced with locale-aware stem-overlap. Static `SPOTLIGHT_ANALYZERS` map (`pt → PortugueseAnalyzer`, `en → EnglishAnalyzer`, `es → SpanishAnalyzer`, fallback `PortugueseAnalyzer`); `tokenize` + `matchesByStemOverlap` helpers compute `userStems` once per request then check `userStems.containsAll(termStems)` per spotlight. Regressions fixed: "carros" ↔ "carro" (plural/singular bidirectional), "planos para carreira" ↔ "plano de carreira" (filler-word drop). Behavior change: stopword-only terms ("de para com") no longer match every query — deliberate; legacy substring matched empty-string spotlights. 6 new test cases pin the contract across PT/EN/ES (plural, filler words, no-overlap, partial-overlap, stopword-only). | §III.4 (V1) |
| T21 | ✅ | T20 | `TurSNSpotlightProcess.matchesByLuceneQuery(MemoryIndex, term, Analyzer)` replaces V1's stem-overlap. Per-request `MemoryIndex` over the user query, `QueryParser` per spotlight term with **AND default operator** (preserves V1's "all stems must match" precision). Admins can now write `"plano de carreira"` (phrase), `"plano carreira"~2` (slop), `carreira~1` (fuzzy, ≤1 edit), `carro OR veiculo OR automovel` (boolean). Malformed input is caught + falls back to `QueryParser.escape` so a typo can't take down search. Frontend [sn.site.spotlight.form.tsx](frontend/apps/turing-app/src/components/sn/spotlight/sn.site.spotlight.form.tsx) ganhou um collapsible "Sintaxe avançada" com exemplos (PT/EN i18n). 12 unit tests pin both V1-equivalent cases (plural, filler-word drop, AND-default precision, no-overlap, EN stemming) and V2-only syntax (phrase order, slop, fuzzy edit distance, boolean OR, malformed fallback, blank/null). lucene-memory dep added to root + turing-app POMs. | §III.4 (V2) |
| T22 | 📋 | — | Persona tone validation via `PortugueseAnalyzer` stems in `TurPersonaToneValidator` — "competitivo" must catch "competitividade". Same morphology pattern as T20. | §IV.3 |
| T23 | 📋 | T11 | Slot value fuzzy matching in `LlmJudgeGuardrailStrategy`: match extracted value against `inlineOptions` via Lucene `LevenshteinAutomata` (edit distance 2) so "Gerente Senior" matches "Gerente Sênior". | §IV.6 |
| T24 | 📋 | T19 | RAG hybrid retrieval: vector topK + BM25 topK + Reciprocal Rank Fusion (`k=60`). Native Solr 9 / ES 8 hybrid query; in-process RRF for embedded Lucene. New interface method `hybridSearch` on `TurSearchEnginePlugin`. | §III.2 |
| T25 | 📋 | — | Router language-aware: `PerFieldAnalyzerWrapper` so EN trigger descriptions tokenize through `EnglishAnalyzer`. Detection via static heuristic / Tika / explicit `triggerLanguage` enum on `TurChatFlow`. | §II.2.1 |
| T26 | 📋 | T25 | Router scoring: replace overlap counter with Lucene `MoreLikeThis` over an in-memory index of trigger descriptions; dominance ratio becomes a percentile threshold once production logs feed a histogram. | §II.2.2 |
| T27 | 📋 | T26 | Router scaling: pre-built `SearcherManager` per agent with `BM25Similarity`; evict on `TurChatFlow.save/delete` via the existing `turChatFlowRouterDecision` cache hook. | §II.2.3 |
| T28 | 📋 | — | Chat analytics intent classifier: ship `TurEsMltAnalyticsEngine @Component` so the "Top Intents" panel stops being empty. Embedded Lucene `MoreLikeThis` for the dev/no-ES fallback. | §III.5 |
| T29 | 📋 | — | Tool pre-filtering for large catalogs: Lucene-similarity over `prompts/tools/**/*.md` descriptions; keep top-K (~10) for the LLM, always keep tools the flow node explicitly requires (`requiredTools` field). **Unblocked** — T16 shipped. | §IV.2 |
| T30 | 📋 | — | Conversation-relevance retrieval for chat memory: per-conversation `MemoryIndex` + BM25 of past turns concatenated with the recent-N window; prefix retrieved turns with timestamp/position marker so the LLM keeps coherence. | §IV.4 |
| T31 | 📋 | — | System prompt fragment caching: `@Cacheable` the composed persona-prompt by `(personaId, agentId)` and the flow addendum by `(flowId, currentNodeId)`. Evict on persona/flow/agent edit via existing hooks. RAG fragment stays uncached. | §IV.5 |

### Non-goals (do NOT add as tasks)

Locked-in by §I.6 and §I.7 — preserved verbatim, not subject to the
ordering above:

- Auto-trigger router, continuation logic, A/B variant assignment +
  sticky hash + scheduling window, sub-flow descent/ascent, SSE event
  bus, defense-in-depth checks, silent telemetry,
  anti-prompt-injection / CPF-CNPJ checksum / yes-no normalization,
  `TurToolCallbackPipeline.decorate(...)`, `summarizeVariables` cap —
  all stay as-is.
- Structured output (LLM → JSON) is NOT a path forward; use tool
  calling with provider-side schemas instead.
- The chat-flow DSL (`ChatFlowGraph` / `ChatFlowNode` JSON shape) is
  NOT being rewritten — only execution is.
- Spring AI `ChatModel.call()` + `internalToolExecutionEnabled(true)`
  stay (no framework swap).
- Heuristic and LlmJudge strategies coexist.
- No "auto-detect" middleware that guesses node-level intent
  (toolFree, captureMode, etc.) — those are *authorship*, not runtime.

---

## I. Chat-flow execution harness — the big architectural refactor

> Merges and supersedes the previous `docs/chat-flow/HARNESS_INVENTORY.md` and
> `docs/chat-flow/HARNESS_IT_IMPLEMENTATION_PLAN.md`. Those files are gone;
> their content lives here.

### I.1 Background: the 22-patch harness

[`TurAgentChatExecutor`](../turing-app/src/main/java/com/viglet/turing/genai/TurAgentChatExecutor.java)
runs the order **"LLM call → engine advance → maybe regen"**. The executor
first asks the chat model for a reply to the user message, *then* calls
`engine.advance()` (which may walk transparent nodes — switch, condition,
slot, persona, sub-flow), and *if* the cursor moved, fires a **second LLM
call (regen)** to rewrite the reply for the *new* node. The first reply was
composed with the *old* node's system prompt; it's structurally
misaligned with the UI we're about to render (chips, next question,
active persona). To hide that misalignment the code accumulates 22
empirical patches: tool-stripping on initial call, "HIGHEST-PRIORITY
OVERRIDE" injected after the persona prompt, deliberate drop of the base
system prompt in regen, conditional chip suppression when
`textAlignedWithCurrentNode=false`, persona resolved three times,
history trimmed to 3 turns, etc.

**Central hypothesis**: inverting to **"engine advance → 1× LLM call →
publish"** — the engine first observes the user message and decides the
cursor (transparent in a single deterministic/judge pass), and *then* the
executor composes ONE LLM call already with the correct node, persona,
tools and chips congruent — most patches disappear by construction.
`freshlyTriggered`/`regen`/`assistantOverride`/`textAlignedWithCurrentNode`
become artifacts of the old design. The patches that survive are
robustness-grade only (cycle detection, A/B sticky-hash, defensive
telemetry, anti-prompt-injection) and stay as separate middleware.

#### Quantitative summary

| Category | Patches | % | Meaning |
|---|---|---|---|
| (A) RACE | 11 | 50% | Disappear on order inversion |
| (B) DECLARATIVE | 6 | 27% | Become `ChatFlowNode` fields |
| (C) MIDDLEWARE | 5 | 23% | Stay as separate middleware |
| **Total** | **22** | 100% | |

### I.2 Already shipped this round (partial mitigations)

Three changes that landed in `2026.2.7-2026.2.8` move the needle on the
refactor without committing to it yet:

| Shipped | Maps to patch | Effect |
|---|---|---|
| ✅ JPA EAGER→LAZY on `TurAIAgent`, `TurSNSiteGenAi`, `TurSNSite.turSNSiteGenAi` + `JOIN FETCH` on `TurAIAgentRepository.findById` | Reduces side-effects from #15 (state writes during eager fetches); cuts setup time by ~5s on first agent-configured chat | Setup phase cleaner |
| ✅ Procedural flow router via Lucene `PortugueseAnalyzer` + cached LLM router fallback (`turChatFlowRouterDecision`) | Net new — saves ~2.5s on every freshly-triggered turn | Setup time 2.7s → ~50ms when keyword-match hits |
| ✅ Skip initial LLM call when `flowContext.freshlyTriggered() == true` ([`TurAgentChatExecutor:368-394`](../turing-app/src/main/java/com/viglet/turing/genai/TurAgentChatExecutor.java#L368-L394)) | **Partial fix for #7 (regen full path)** — initial call was being discarded by regen anyway; now skipped entirely | Saves ~1.5-2s per freshly-triggered turn |
| ✅ Per-stage timing observability ([`TurChatPipelineObservation`](../turing-app/src/main/java/com/viglet/turing/observability/TurChatPipelineObservation.java)) + benchmark IT ([`TurAgentChatLatencyIT`](../turing-app/src/test/java/com/viglet/turing/genai/TurAgentChatLatencyIT.java)) | Enables data-driven prioritization of the remaining patches | Setup / LLM / advance / regen / total broken out per turn |

The skip-initial-LLM fix is **on the path** to the full inversion: it
proves the architectural insight (initial reply discarded → don't make
the call) without yet moving `advance()` before the LLM. That's the next
domino.

### I.3 The 22 patches — current status

Patch text below is compressed from the original `HARNESS_INVENTORY.md`.
Every patch links its code location. Full historical quotations are
recoverable from `git log -p docs/chat-flow/HARNESS_INVENTORY.md` (file
deleted as part of this consolidation).

#### Race patches (A) — 11

| # | Location | What | Status | How it sums |
|---|---|---|---|---|
| 1 | [`TurAgentChatExecutor:290-301`](../turing-app/src/main/java/com/viglet/turing/genai/TurAgentChatExecutor.java#L290-L301) | Re-resolve persona AFTER flow selection (the walk may have crossed `persona-*`) | 📋 | Disappears — single resolution post-advance |
| 2 | [`TurAgentChatExecutor:451-461`](../turing-app/src/main/java/com/viglet/turing/genai/TurAgentChatExecutor.java#L451-L461) | Re-resolve persona a THIRD time for regen | 📋 | Disappears — no regen |
| 6 | [`TurAgentChatExecutor:399-420`](../turing-app/src/main/java/com/viglet/turing/genai/TurAgentChatExecutor.java#L399-L420) | `freshlyTriggered`: skip advance on activation turn | ⏳ Skip of initial LLM landed; advance-skip remains | Disappears — router places cursor; LLM composes once |
| 7 | [`TurAgentChatExecutor:421-477,566-693`](../turing-app/src/main/java/com/viglet/turing/genai/TurAgentChatExecutor.java#L421-L477) | Full regen path: 2nd LLM call after `advanced=true` | ⏳ Initial-call skip partial mitigation shipped; regen path itself remains | Disappears — single composition with correct node |
| 8 | [`TurAgentChatExecutor:382-398,469-476,504-512`](../turing-app/src/main/java/com/viglet/turing/genai/TurAgentChatExecutor.java#L382-L398) | `textAlignedWithCurrentNode` flag + chip suppression | 📋 | Disappears — text always for current node |
| 9 | [`TurAgentChatExecutor:593-602`](../turing-app/src/main/java/com/viglet/turing/genai/TurAgentChatExecutor.java#L593-L602) | Regen omits "Next-step preview" hint | 📋 | Disappears — `includeNextStepHint` bifurcation moot |
| 10 | [`TurAgentChatExecutor:604-632,893-948`](../turing-app/src/main/java/com/viglet/turing/genai/TurAgentChatExecutor.java#L604-L632) | "HIGHEST-PRIORITY OVERRIDE" appended after persona prompt | 📋 | Disappears — anchoring is regen-specific |
| 11 | [`TurAgentChatExecutor:615-632`](../turing-app/src/main/java/com/viglet/turing/genai/TurAgentChatExecutor.java#L615-L632) | Drop `baseSystemPrompt` in regen | 📋 | Disappears — single prompt, no conflict |
| 12 | [`TurAgentChatExecutor:920-931`](../turing-app/src/main/java/com/viglet/turing/genai/TurAgentChatExecutor.java#L920-L931) | Inline `aiInstruction` literal in override | 📋 | Disappears with #10 |
| 13 | [`TurAgentChatExecutor:932-945`](../turing-app/src/main/java/com/viglet/turing/genai/TurAgentChatExecutor.java#L932-L945) | Inline chip-labels in override + "MUST be congruent" | 📋 | Disappears — chips of current node in single prompt |
| 14 | [`TurAgentChatExecutor:633-640,882-979`](../turing-app/src/main/java/com/viglet/turing/genai/TurAgentChatExecutor.java#L633-L640) | `trimToRecentTurns`: history truncated to 3 turns in regen | 📋 | Disappears — no regen, no anchoring |
| 15 | [`TurChatFlowEngineService:227-237`](../turing-app/src/main/java/com/viglet/turing/genai/flow/TurChatFlowEngineService.java#L227-L237) | Refresh state from DB before strategy advance (defense against side-effect writes by tools called during the LLM) | 📋 | Disappears — advance runs first; tools fire later in the single LLM call |
| 16 | [`TurChatFlowEngineService:272-294`](../turing-app/src/main/java/com/viglet/turing/genai/flow/TurChatFlowEngineService.java#L272-L294) | Explicit save after transparent walk (double-save) | 📋 | Reduces to single clean persistence |

#### Declarative patches (B) — 6

| # | Location | What | Status | Becomes |
|---|---|---|---|---|
| 3 | [`TurAgentChatExecutor:328-356`](../turing-app/src/main/java/com/viglet/turing/genai/TurAgentChatExecutor.java#L328-L356) | Tool-stripping on initial call (substring match in `aiInstruction`) | 📋 | `ChatFlowNode.toolsEnabled: Boolean` |
| 4 | [`TurAgentChatExecutor:654-681`](../turing-app/src/main/java/com/viglet/turing/genai/TurAgentChatExecutor.java#L654-L681) | Tool-stripping in regen | 📋 | Same as #3; single check |
| 5 | [`TurAgentChatExecutor:709-716`](../turing-app/src/main/java/com/viglet/turing/genai/TurAgentChatExecutor.java#L709-L716) | `forbidsToolCalls()` bilingual substring matcher | 📋 | Function deleted once #3+#4 land |
| 21 | [`LlmJudgeGuardrailStrategy:108-193`](../turing-app/src/main/java/com/viglet/turing/genai/flow/strategy/LlmJudgeGuardrailStrategy.java#L108-L193) | Second-opinion retry for "stay, no value" verdict + `isNonBlankRedirect` heuristic | 📋 | Behavior decided by `onJudgeReject` declarative field once that's wired everywhere |
| 22 | [`ChatFlowNode:165-193`](../turing-app/src/main/java/com/viglet/turing/genai/flow/ChatFlowNode.java#L165-L193) | `onJudgeReject` declarative field with 3 policies | ✅ Already declarative | Model for how (B) should evolve |

#### Middleware patches (C) — 5

| # | Location | What | Status | Stays as |
|---|---|---|---|---|
| 17 | [`TurChatFlowEngineService:986-1099`](../turing-app/src/main/java/com/viglet/turing/genai/flow/TurChatFlowEngineService.java#L986-L1099) | Cycle detection in transparent walk (`TRANSPARENT_WALK_MAX=16`) | ✅ Already correct | Engine middleware |
| 18 | [`TurChatFlowEngineService:318-332`](../turing-app/src/main/java/com/viglet/turing/genai/flow/TurChatFlowEngineService.java#L318-L332) | Defense-in-depth: fall back to HEURISTIC when strategy bean missing | ✅ Already correct | Strategy resolver |
| 19 | [`TurChatFlowEngineService:765-779`](../turing-app/src/main/java/com/viglet/turing/genai/flow/TurChatFlowEngineService.java#L765-L779) | `isInExperimentWindow` A/B scheduling window | ✅ Already correct | Router middleware |
| 20 | [`TurAgentChatExecutor:835-839`](../turing-app/src/main/java/com/viglet/turing/genai/TurAgentChatExecutor.java#L835-L839) | `resolveForcedFlow`: flow only governs its owner agent | ✅ Already correct | Resolver guard |
| (telemetry) | various | Telemetry swallow-and-log around sessions, turns | ✅ Already correct | Stays |

### I.4 Patterns observed across the patches

- **Persona resolved 3× per turn** (#1, #2, plus the initial `~240` call).
  Every time because the walk may have changed `__activePersonaId`.
  Becomes **one** post-advance resolution.
- **Boolean `alignedWith…` flags propagated to suppress side-effects**
  (#8 `textAlignedWithCurrentNode`, #6 `freshlyTriggered`,
  `assistantOverride` on `AdvanceResult`). All exist to compensate for
  the answer being written in a different context than where the engine
  parked. Vanish when text is always written *in* the correct context.
- **Save → walk → save** (#16). Double persistence because in-place
  `currentNodeId` mutations during walks. Becomes single clean
  persistence when the cycle is "resolve cursor → persist → call LLM".
- **Substring matching in free text** (#5 `aiInstruction.contains("não
  chame nenhuma tool")`, #21 `redirect.length() >= 8` as "substantive
  proxy"). Always a signal that a declarative field is missing.
- **"Highest-priority override" appended after the main prompt to fight
  anchoring** (#10, #12, #13). All regen-specific. Vanishes with regen.
- **Deliberate drop of parts of the system prompt in regen** (#11). The
  symptom is clear: regen has a different prompt than initial to pull
  the LLM the other way, and still fails — anchoring is too strong. The
  solution is to not have regen.
- **Telemetry swallow-and-log** (#20, `recordSessionStartSafely`,
  `recordTurnTelemetry`). Genuine (C) middleware — stays.

### I.5 Recommended order of removal

Each step removes a *batch* of patches. The skip-initial-LLM ship gives
us a partial of step 1's value already.

1. **Invert `advance → LLM call`** — move
   `chatFlowEngineService.advance(...)` to *before* `chatModel.call(prompt)`.
   `advance()` only sees `userMessage` (no `assistantMessage`); strategy
   decides cursor without seeing the bot's reply. **Removes #6, #7, #8,
   #11, #14, #15.**
2. **Resolve persona ONCE per turn** after step 1, with cursor already
   final. **Removes #1, #2.**
3. **Introduce `ChatFlowNode.toolsEnabled` (or `toolsMode`) declarative.**
   **Removes #3, #4, #5** + the `regenForbidsTools` that would survive
   by inertia.
4. **Eliminate the `HIGHEST-PRIORITY OVERRIDE` block and
   `regenerateReplyForNewNode` entirely.** Dead code after step 1.
   **Removes #9, #10, #12, #13** and the `REGEN_HISTORY_TURN_BUDGET`
   constant.
5. **Clean the double-save in the walk** (#16). The cycle becomes
   "advance/walk inside a transaction → flush → LLM call". One atomic
   persistence.
6. **Re-evaluate the judge retry (#21)** in light of step 3. Likely
   becomes "one judge call + behavior driven by `onJudgeReject`
   literal", no `isNonBlankRedirect` heuristic.
7. **Keep middleware-shaped patches**: cycle caps (#17), strategy
   fallback (#18), A/B window (#19), agent-owner check (#20),
   silent telemetry.
8. **Terminal refactor**: `execute()` should fit in ~80-100 lines
   (currently 290+). `regenerateReplyForNewNode`, `buildRegenOverride`,
   `trimToRecentTurns`, `forbidsToolCalls` cease to exist.

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

## II. Chat-flow router (already shipped + backlog)

### II.1 ✅ Shipped this session

Procedural router + LLM cache pipeline lives in
[`TurChatFlowEngineService.selectActiveFlow`](../turing-app/src/main/java/com/viglet/turing/genai/flow/TurChatFlowEngineService.java).
Three phases:

1. **`tryProceduralRoute`** — Lucene `PortugueseAnalyzer` tokenizes the
   user message and each candidate's `triggerDescription`; overlap of
   stems picks the best fit when it dominates the runner-up by ≥1.5×.
2. **`askRouterCached`** — Spring cache keyed by
   `agentId:::sha256(normalized(userMessage))`; same wording returns the
   prior LLM decision without a round-trip.
3. **`askRouter`** — full LLM call (the original behavior; only fires on
   procedural-ambiguous + cache-miss).

Observability: `[FlowEngine] Procedural router picked '…' (LLM skipped)`,
`Router cache HIT/MISS`, `Procedural router undecided (best=X, second=Y)`
(debug). Cache evicted on `TurChatFlow.save/delete` via
`turChatFlowRouterDecision` in
[`TurChatFlowRepository`](../turing-app/src/main/java/com/viglet/turing/persistence/repository/agent/TurChatFlowRepository.java).

### II.2 📋 Backlog — sharpen the procedural pass

#### II.2.1 `PerFieldAnalyzerWrapper` — language-specific stems

**Problem**: `FLOW_ROUTER_ANALYZER` is a single `PortugueseAnalyzer`. EN
trigger descriptions tokenize without EN stopword filtering and without
EN stemming — overlap quality drops.

**Idea**: route each `triggerDescription` through the matching language's
analyzer.

```java
private static final Analyzer ROUTER_ANALYZER = new PerFieldAnalyzerWrapper(
        new PortugueseAnalyzer(), // default
        Map.of("en", new EnglishAnalyzer()));
```

Language detection options:
- Static heuristic (stopword count per language) — ~10 lines
- Apache Tika `LanguageDetector` (transitive dep already)
- Explicit `TurChatFlow.triggerLanguage` enum (`PT`/`EN`/`AUTO`) +
  default `AUTO` → detection

**When**: procedural hit-rate < 70% AND log inspection shows EN-trigger
misses.

#### II.2.2 Lucene `MoreLikeThis` — TF-IDF scoring

**Problem**: overlap count ignores rarity (`carreira` should weigh more
than `programa`) and document length.

**Idea**: replace overlap counter with `MoreLikeThis` query against a
small in-memory index of trigger descriptions. Returns calibrated
similarity scores — dominance ratio can become a percentile threshold
once a histogram from production logs exists.

```java
try (Directory dir = new ByteBuffersDirectory()) {
    IndexWriter writer = new IndexWriter(dir,
            new IndexWriterConfig(FLOW_ROUTER_ANALYZER));
    for (RouterCandidate c : candidates) {
        Document doc = new Document();
        doc.add(new StringField("flowId", c.flow().getId(), Field.Store.YES));
        doc.add(new TextField("trigger", c.flow().getTriggerDescription(),
                Field.Store.NO));
        writer.addDocument(doc);
    }
    writer.close();

    try (IndexReader reader = DirectoryReader.open(dir)) {
        MoreLikeThis mlt = new MoreLikeThis(reader);
        mlt.setAnalyzer(FLOW_ROUTER_ANALYZER);
        mlt.setFieldNames(new String[]{"trigger"});
        Query mltQuery = mlt.like("trigger", new StringReader(userMessage));
        TopDocs hits = new IndexSearcher(reader).search(mltQuery, 2);
        // dominance check on hits[0]/[1]
    }
}
```

**When**: visible mismatches in the log (procedural picks the wrong flow
OR misses obvious matches).

#### II.2.3 Cached `SearcherManager` + BM25 — scale to dozens of flows

**Problem**: the procedural loop is `O(candidates × tokens)` per turn.
For 50+ flows per agent it adds up and tie-breaking gets coarser.

**Idea**: pre-build a Lucene index of trigger descriptions per agent,
held in a `SearcherManager`, queried with `BM25Similarity`. Evict on
`TurChatFlow.save/delete` via the same hook that already wipes
`turChatFlowRouterDecision`.

```java
private final ConcurrentMap<String, SearcherManager> indexByAgent =
        new ConcurrentHashMap<>();

private SearcherManager indexFor(TurAIAgent agent) {
    return indexByAgent.computeIfAbsent(agent.getId(), id -> {
        Directory dir = new ByteBuffersDirectory();
        IndexWriterConfig cfg = new IndexWriterConfig(FLOW_ROUTER_ANALYZER)
                .setSimilarity(new BM25Similarity());
        try (IndexWriter writer = new IndexWriter(dir, cfg)) {
            for (TurChatFlow f : chatFlowRepository
                    .findByTurAIAgent_IdOrderByNameAsc(id)) {
                if (StringUtils.isBlank(f.getTriggerDescription())) continue;
                Document doc = new Document();
                doc.add(new StringField("flowId", f.getId(), Field.Store.YES));
                doc.add(new TextField("trigger", f.getTriggerDescription(),
                        Field.Store.NO));
                writer.addDocument(doc);
            }
        }
        return new SearcherManager(dir, null);
    });
}
```

**When**: single agent has ≥20 flows AND procedural hit-rate < 85%.

---

## III. Lucene & Search Engine across the GenAI surface

### III.1 Subsystem inventory

| Subsystem | Class | Current mechanism | Opportunity |
|---|---|---|---|
| Chat-flow router | `TurChatFlowEngineService` | Token overlap (Lucene PT analyzer) + LLM fallback | ✅ Shipped (§II) |
| RAG retrieval | `TurRagSearchToolService`, `TurRagContextBuilder` | Vector cosine KNN only | **Hybrid BM25 + vector** (§III.2) |
| Vector store | `TurLuceneVectorIndex`, `TurLuceneVectorStore` | Vector index + BM25 fallback on `FIELD_CONTENT` (T19) | ✅ Shipped (§III.3); full hybrid pending (§III.2) |
| Spotlights | `TurSNSpotlightProcess` | MemoryIndex + QueryParser (T21) — phrase / slop / fuzzy / boolean syntax via per-request MemoryIndex; AND-default keeps V1 precision | ✅ V1+V2 shipped (§III.4) |
| Chat analytics enricher | `TurChatAnalyticsEnricher`, `TurChatAnalyticsEngine` | Pluggable interface; default no-op | **TF-IDF intent classifier** (§III.5) |
| Chat memory | `TurChatMemoryService` | Chronological FIFO of last N turns | Possible — **conversation-relevance retrieval** (§IV.4) |
| Slot extraction | `TurChatSlotExtractionService` | LLM-only with structured-output prompt | None — semantic extraction is LLM's strength |
| Persona resolution | `TurAgentPersonaResolver` | Exact `__activePersonaId` variable lookup | None — already deterministic |
| Persona tone validation | `TurPersonaToneValidator` | Substring match on forbidden/mandatory term list | **Lucene stem matching** (§IV.3) |
| Persona few-shot | `TurPersonaFewShotRetriever` | Vector topK over persona's example pool | None — vector is right for this |
| Search rules | `TurSNSearchRuleEvaluator` | EQUALS/CONTAINS/STARTS_WITH on params | None — string matching is the contract |
| MCP/Custom tool selection | `TurAgentChatExecutor.resolveToolCallbacks` | All tools passed to LLM each turn | Possible — **tool pre-filtering** (§IV.2) |
| Spell check | `TurSNSiteSpellCheckAPI` | Delegated to Solr (Lucene under the hood) | None |
| Autocomplete | `TurSNSiteAutoCompleteAPI` | Delegated to SE plugin | None |
| Token usage | `TurLLMTokenUsageService` | Counter aggregation | None |

### III.2 📋 RAG hybrid search — BM25 re-rank over vector topK

**Where**: [`TurLuceneVectorIndex`](../turing-app/src/main/java/com/viglet/turing/genai/provider/store/lucene/TurLuceneVectorIndex.java)
exposes `KnnFloatVectorQuery` only. Callers go through
[`TurRagSearchToolService`](../turing-app/src/main/java/com/viglet/turing/genai/tool/TurRagSearchToolService.java)
(LLM tool: `search_knowledge_base`).

**Problem**: pure vector retrieval misses queries with **rare or
out-of-distribution terms** — proper nouns, internal codes, version
numbers, product names the embedding model never saw. Cosine similarity
over a token the model treats as `<UNK>` is noise. BM25 nails these.

**Idea**: hybrid retrieval — vector topK + BM25 topK + Reciprocal Rank
Fusion (RRF, `k=60` canonical default).

**Backing store decision** (cluster-aware): push to the configured SE
via [`TurSearchEnginePlugin`](../turing-app/src/main/java/com/viglet/turing/plugins/se/TurSearchEnginePlugin.java).
Solr 9 and Elasticsearch 8 have native hybrid query support; embedded
Lucene falls back to in-process RRF for dev/single-instance.

```java
// New interface method
Optional<TurSEResults> hybridSearch(TurSearchEngineHybridContext ctx,
        String queryText, float[] queryEmbedding, int topK);

// TurSolrSearchEnginePlugin — Solr 9: {!hybrid v=$kQuery v2=$bm25Query}
// TurElasticsearchSearchEnginePlugin — ES 8: { "knn": {...}, "query": {...}, "rank": { "rrf": {} } }
// TurLuceneSearchEnginePlugin — in-process RRF as fallback
```

**Trade-offs**: index size grows ~20-40% (`FIELD_CONTENT` becomes
tokenized + indexed). Query time roughly doubles (two passes) but stays
<50ms vs the ~2s LLM call that follows.

**When**: RAG quality complaints mention "the model didn't find the doc
that has my exact term/version/SKU". Complementarity is biggest on
technical/SKU-heavy corpora.

### III.3 ✅ Vector → BM25 fallback (shipped 2026.2.7 as T19)

**Problem**: low-confidence vector results == "no signal". Returning
empty when keyword match exists is missed information.

**Idea**: when vector top-1 < `MIN_VECTOR_CONFIDENCE`, fall back to pure
BM25 on `FIELD_CONTENT`. Mark the result so the prompt warns the LLM
("keyword match only").

Cheaper than §III.2 (only fires on vector failure), easier to roll back.
Good intermediate step before committing to full RRF.

**When**: ship this first. Roll into §III.2 once corpus characteristics
reveal whether full hybrid is warranted.

### III.4 ✅ Spotlights via Lucene — stop the brittle `contains`
V1 (locale-aware stem overlap) shipped 2026.2.7 as T20. V2 (MemoryIndex + QueryParser, phrase/fuzzy/boolean syntax) shipped 2026.2.7 as T21.

**Where**: [`TurSNSpotlightProcess`](../turing-app/src/main/java/com/viglet/turing/sn/spotlight/TurSNSpotlightProcess.java)
matches by `userQuery.toLowerCase().contains(spotlightTerm.toLowerCase())`.

**Problem**: `contains` is brittle. "carro" doesn't match "carros".
"Plano de carreira" doesn't match "planos para carreira". Admins end up
configuring every morphological variant.

**Idea V1** — token overlap with `PortugueseAnalyzer` (5 lines, immediate
morphology fix):

```java
private static final Analyzer SPOTLIGHT_ANALYZER = new PortugueseAnalyzer();

private boolean matches(String userQuery, String spotlightTerm) {
    Set<String> userStems = tokenize(SPOTLIGHT_ANALYZER, userQuery);
    Set<String> termStems = tokenize(SPOTLIGHT_ANALYZER, spotlightTerm);
    return !termStems.isEmpty() && userStems.containsAll(termStems);
}
```

**Idea V2** — `MemoryIndex` + `QueryParser` per query (supports phrase
queries, fuzzy operators, synonym files):

```java
MemoryIndex idx = new MemoryIndex();
idx.addField("q", userQuery, SPOTLIGHT_ANALYZER);
QueryParser qp = new QueryParser("q", SPOTLIGHT_ANALYZER);
float score = idx.search(qp.parse(QueryParser.escape(spotlightTerm)));
return score > 0f;
```

V2 lets admins write `"plano de carreira"~2` (phrase with slop) or use a
`SynonymGraphFilter`-loaded list ("carro, veiculo, automovel").

**Backing store**: stays **embedded** — spotlights are tiny (hundreds
per site), hot path (every search query), 5-50ms HTTP to SE would be a
regression.

**When**: admins complain about "spotlight not firing" for queries that
obviously should match. V1 first; V2 only if there's real demand for
phrase/fuzzy.

### III.5 📋 Chat analytics intent classifier — fill the no-op engine

**Where**: [`TurChatAnalyticsEnricher`](../turing-app/src/main/java/com/viglet/turing/service/chatanalytics/TurChatAnalyticsEnricher.java)
calls `TurChatAnalyticsEngine.classify(...)`. Default implementation is a
no-op — intent/category/sentiment fields stay null.

**Problem**: dashboards have empty "Top intents" / "Category mix"
panels.

**Idea**: ship a `TurEsMltAnalyticsEngine` `@Component` that classifies
each conversation against a per-agent **intent catalog** via
Elasticsearch's `more_like_this` query.

**Backing store decision**: **SE-backed (ES)**. Analytics data is
shared across pods by definition; intent catalog + conversation history
want persistent storage with point-in-time queries.

```java
@Component
@ConditionalOnProperty(name = "turing.chat.analytics.engine",
        havingValue = "elasticsearch-mlt")
class TurEsMltAnalyticsEngine implements TurChatAnalyticsEngine {
    @Override
    public ClassificationResult classify(TurChatSessionRecord session) {
        // POST /turing_intents_{agentId}/_search
        // {
        //   "query": { "more_like_this": {
        //       "fields": ["samples"],
        //       "like": "<concatenated user turns>",
        //       "min_term_freq": 1, "min_doc_freq": 1 } },
        //   "size": 1
        // }
    }
}
```

Embedded Lucene `MoreLikeThis` remains the "dev / single-instance /
no-ES-available" fallback.

**When**: product asks "why is the Top Intents panel empty?" and there's
no budget for batch LLM enrichment.

---

## IV. New ideas from the latest session

Emerged from connecting the chat-flow timing data, the Lucene survey,
and the harness refactor. None designed in depth yet — sketches only.

### IV.1 ✅ Streaming initial LLM (shipped 2026.2.7)

**Context**: even with the inversion (§I.5 step 1), individual turns
take ~2s because the LLM round-trip dominates. Until streaming reaches
all the way to the SSE pipeline, users see a frozen UI for the full
turn.

#### IV.1.a ✅ Persistent toggle + documented limitation (shipped 2026.2.7)

Added [`TurAgentChatMode`](../turing-app/src/main/java/com/viglet/turing/persistence/model/agent/TurAgentChatMode.java)
enum (`CALL` / `STREAM`), persisted on `TurAIAgent.chatMode`
(Liquibase `v2026.2.7.8.yaml`, default `CALL`). UI in
[`ai-agent.settings.form.tsx`](../frontend/apps/turing-app/src/components/agent/ai-agent.settings.form.tsx)
exposes the select with both PT and EN copy that calls out the
limitation. The PUT endpoint propagates `chatMode` field-by-field
(same dropped-on-save bug pattern the `chatMemory` block already
warned about).

[`TurAgentChatExecutor.execute(...)`](../turing-app/src/main/java/com/viglet/turing/genai/TurAgentChatExecutor.java)
branches when `chatMode=STREAM AND flowContext == null` and uses
`chatModel.stream(prompt)`; per-turn tools are stripped on this path
(observed: `DefaultToolCallingChatOptions` + `internalToolExecutionEnabled(true)`
activate the Spring AI tool-aware aggregator, defeating streaming).

**Confirmed empirically** that Spring AI 2.0.0-M6's
`OpenAiChatModel.stream(prompt)` still buffers internally even with
the tool-aware path disabled — `first_raw_ms ≈ llm_ms` (all 500+
deltas drain in the last ~150ms of the round-trip). Root cause: M6
migrated from the WebClient-based `OpenAiApi` to the official OpenAI
Java SDK (`OpenAIOkHttpClientAsync`), whose iterator-based
`StreamResponse<ChatCompletionChunk>` is wrapped via
`Flux.fromStream(...)` — the iterator gets drained before publishing.
The Turing pipeline cannot fix this in-place.

Net user-facing effect today: the setting persists and the executor
takes the STREAM branch, but on OpenAI-backed agents the bubble still
fills in one burst. Documented on the enum Javadoc and i18n form copy
so operators understand the trade-off before flipping the toggle.

#### IV.1.b ✅ Real streaming via WebClient bypass (shipped 2026.2.7 as T18)

**Plan** (executes as task T18 after T17 + T16):
- In [`TurOpenAiLlmProvider`](../turing-app/src/main/java/com/viglet/turing/genai/provider/llm/TurOpenAiLlmProvider.java),
  add a second factory method (e.g. `createStreamingChatModel(...)`)
  that constructs the legacy WebClient-based `OpenAiApi` /
  `OpenAiChatModel` instead of the SDK-based one. Spring AI 2.0.0-M6
  retains the alternate builder for compat; if it doesn't, drop
  through to a hand-rolled `WebClient` posting to `/v1/chat/completions`
  with `stream:true` and parsing `data: ...` SSE lines into a
  `Flux<ChatResponse>`.
- The executor's STREAM branch calls
  `llmModelFactory.createStreamingChatModel(...)` only when
  `chatMode=STREAM`; CALL keeps the SDK-based path.
- Once the harness refactor lands (§I.5 ⇒ regen is gone), the
  branching simplifies further: a single `chatModel.stream(prompt)`
  feeds the SSE pipeline for every non-flow turn and the
  late-regen-as-`type="regenerate"` event becomes moot.
- Update i18n on the form to drop the limitation warning once green.

**Risks**: maintaining the OpenAI provider against two transports
(SDK for tool calling, WebClient for streaming) until upstream Spring
AI fixes M6's streaming. Tool-calling-during-stream remains a
separate open problem (see the original §IV.1 sketch — mid-stream
tool deltas, manual tool-execution loop, etc.); for now the path
stays tool-free.

**When**: unblocked. T16 shipped 2026-05-22; the harness now has a
single LLM call per turn with no regen, so the WebClient branch slots
in without conflicting with anything. Pick this up next time someone
asks "why isn't streaming actually streaming?".

### IV.2 💭 Tool pre-filtering for large tool catalogs

**Context**: when an agent has dozens of MCP tools, the LLM sees *all
of them* every turn ([`TurAgentChatExecutor.resolveToolCallbacks`](../turing-app/src/main/java/com/viglet/turing/genai/TurAgentChatExecutor.java)).
Each tool description costs input tokens; the LLM tool-selection
quality also degrades with cardinality.

**Idea**: before composing the prompt, run a **fast Lucene similarity
match** between the user's message and each tool's `description` (the
`prompts/tools/**/*.md` content). Take top-K (say 10) by score; the
rest are dropped from the `toolCallbacks` array sent to the LLM. The
description for each tool is already markdown that Lucene can tokenize
naturally.

**Caveat**: never drop tools that the *flow node* explicitly requires
(would need a node-level `requiredTools` field, or a sentinel in the
tool description). Belt-and-suspenders: also keep tools mentioned in
the agent's `nativeTools` configuration.

**When**: an agent has >20 active tools AND token usage shows
description bloat dominating input cost.

### IV.3 💭 Persona tone validation via Lucene stem matching

**Where**: `TurPersonaToneValidator` checks forbidden/mandatory term
lists against the assistant reply via substring match.

**Problem**: same morphology problem as spotlights — "competitivo" in
the forbidden list doesn't catch "competitiva", "competitivamente",
"competitividade".

**Idea**: tokenize the reply AND each forbidden/mandatory term through
`PortugueseAnalyzer`. Compare on stems. "competitiv-" matches all the
above.

```java
private static final Analyzer PERSONA_TONE_ANALYZER = new PortugueseAnalyzer();

private boolean containsForbiddenStem(String reply, List<String> forbidden) {
    Set<String> replyStems = tokenize(PERSONA_TONE_ANALYZER, reply);
    return forbidden.stream()
            .flatMap(t -> tokenize(PERSONA_TONE_ANALYZER, t).stream())
            .anyMatch(replyStems::contains);
}
```

**When**: persona authors report "I added 'competitivo' to forbidden but
the bot still says 'competitividade'" — same UX bug class as
spotlights.

### IV.4 💭 Conversation-relevance retrieval for chat memory

**Context**: [`TurChatMemoryService`](../turing-app/src/main/java/com/viglet/turing/service/chatmemory/TurChatMemoryService.java)
keeps last N turns chronologically. For long conversations, important
context from turn 3 gets dropped by turn 25.

**Idea**: index conversation turns into a tiny per-conversation Lucene
`MemoryIndex`. Before composing the system prompt for a new turn,
retrieve the top-K turns by BM25 similarity to the current user
message. Concatenate them with the most-recent N turns to form the
history payload. Keeps short-term continuity AND long-term relevance.

**Risk**: turn ordering matters for the LLM's coherence — feeding
out-of-order turns may confuse the model. Mitigation: prefix retrieved
turns with their timestamp / position marker.

**When**: long-conversation use cases emerge (support chat, sales
qualification). Not before — adds latency without payoff for short
chats.

### IV.5 💭 System prompt fragment caching

**Context**: each turn composes a system prompt from base + persona +
flow addendum + RAG context + few-shot examples. Each fragment is
deterministic given its inputs. Today they're recomputed every turn.

**Idea**: cache the **composed** persona-prompt and flow-addendum
fragments (Spring `@Cacheable`) keyed by their input identifiers.
Persona fragment by `(personaId, agentId)`; flow addendum by
`(flowId, currentNodeId)`. Evict on persona / flow / agent edit (same
hooks that already invalidate `turAIAgentfindById`).

**Caveat**: RAG context is per-turn (depends on user message) — that
fragment stays uncached.

**Expected win**: ~10-30ms per turn (string concatenation isn't free
when prompts are 2-5KB), more important as agents grow more complex.

**When**: a profiling pass on the `setup_ms` substages (now exposed by
`TurChatPipelineObservation`) shows persona/flow composition taking
≥50ms per turn.

### IV.6 💭 Slot value fuzzy matching

**Context**: `LlmJudgeGuardrailStrategy` extracts slot values. For
slots with `inlineOptions`, the extracted value is matched against the
option labels via case-insensitive equals. "Gerente Senior" doesn't
match "Gerente Sênior". "Sr Comprador" doesn't match "Comprador".

**Idea**: when matching the extracted value against inlineOptions, use
Lucene's `LevenshteinAutomata` (FuzzyQuery's underlying primitive)
with edit distance 2. The match is bounded and deterministic.

```java
LevenshteinAutomata la = new LevenshteinAutomata(optionLabel.toLowerCase(), 2, true);
ByteRunAutomaton matcher = new ByteRunAutomaton(la.toAutomaton(2));
byte[] bytes = extractedValue.toLowerCase().getBytes(StandardCharsets.UTF_8);
boolean matches = matcher.run(bytes, 0, bytes.length);
```

**When**: customer flows show recurring "user typed almost-but-not-quite
the chip label and the judge rejects it" reports.

---

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
- (Follow-up, out of scope) CI nightly pipeline for `-Pllm-it` with the
  `OPENAI_API_KEY` secret
