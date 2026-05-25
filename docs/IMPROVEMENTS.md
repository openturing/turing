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
- [VII. Education customer engagement — platform synthesis](#vii-education-customer-engagement--platform-synthesis)
- [VIII. Strategic moves — repositioning beyond features](#viii-strategic-moves--repositioning-beyond-features)
- [IX. Deep Agents — competitive response](#ix-deep-agents--competitive-response)

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
> **Status snapshot (2026-05-25, post-T31)**:
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
> - **Block C** — ✅ T17–T33 all shipped 2026.2.7 / 2026.3.1. T32 closed
>   the observability gap (Hazelcast cache gauges + T30/T29 per-turn
>   timings + chat-setup sub-stage breakdown) so the wins claimed by
>   T28-T31 are now measurable on `/actuator/prometheus`. T33 (shipped
>   2026.3.1) decomposed `TurAgentChatExecutor.execute()` into three
>   collaborator beans (`TurChatToolResolver`, `TurChatPromptAssembler`,
>   `TurChatStreamingDispatcher`) plus a top-level `TurAgentChatFlowContext`
>   record — orchestrator method shrunk to 147 lines; every public API
>   and every `STAGE_CHAT_*` meter preserved bit-for-bit. T34 (cache
>   conventions docs + lint test — §IV.8) shipped 2026.3.1 — new
>   "Adding a `@Cacheable` query" subsection in `agents.md` + new
>   `TurCacheAnnotationConventionsTest` walks `target/classes` via
>   Spring's `MetadataReader` to fail when a `@Cacheable` cache name is
>   never paired with a `@CacheEvict`. Caught `turSNSiteSearchSnapshot`
>   on first run (evicted programmatically by `TurSNSiteSnapshotEvictionListener`
>   rather than declaratively); added to `EVICTION_EXEMPT` with a
>   justifying comment.
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
| T22 | ✅ | — | `TurPersonaToneValidator` replaces whole-word regex with multi-locale Lucene stem matching: `BrazilianAnalyzer` (PT, aggressive Snowball stemmer — chosen over the default `PortugueseAnalyzer` which uses Light stemming and would still miss "competitivo→competitividade") + `EnglishAnalyzer` (Porter) + `SpanishAnalyzer`. Reply is tokenized ONCE with offset tracking; each forbidden term is tokenized through all three analyzers, intersection of stem sets ⇒ hit. Masking uses `OffsetAttribute` so the actual word in the reply gets replaced with `[***]` — overlapping/adjacent ranges from different analyzers merge so a word gets one mask, not three. Extracted `collectHitRanges` helper to keep `validate(...)` under the Sonar cognitive-complexity threshold. 14 unit tests pin: exact-word backwards-compat, the §IV.3 headline ("competitivo" → "competitividade", "competitiva", "competitivamente"), EN Porter ("running" → "runs"), ES stemming ("corredor" → "corredores"), cross-language stem alignment (PT/ES "carro"), stopword-only term skip, multi-term + multi-hit masking, no-double-mask invariant. | §IV.3 |
| T23 | ✅ | T11 | New `ChatFlowOps.canonicalizeAgainstInlineOptions(value, options)` with a 4-tier match cascade — exact (case-fold) → substring containment (longest option wins) → Lucene `LevenshteinAutomata` edit-distance 1 → distance 2. Transpositions enabled (`Sêinor`/`Sênior` counts as 1 edit, not 2). Wired into both guardrail strategies (`LlmJudgeGuardrailStrategy` after `normalizeYesNo` + force-capture, `HeuristicGuardrailStrategy` right after `extractValue`) so downstream switch nodes / SpEL conditions see the canonical chip label regardless of which path captured the slot. Headline regressions fixed: "Gerente Senior" → "Gerente Sênior" (diacritic, 1 edit), "Sr Comprador" → "Comprador" (prefix noise, substring), "Compradoor" → "Comprador" (typo, 1 edit), "Gerente Senir" → "Gerente Sênior" (double typo, 2 edits). 9 new unit tests pin the cascade + edge cases (no match passes through, blank/null inputs no-op, longest-option-wins on ambiguous substring matches, exact match short-circuits before fuzzy). | §IV.6 |
| T24 | ✅ | T19 | `TurLuceneVectorStore.hybridSearch(query, topK, threshold)` runs vector KNN + BM25 over `FIELD_CONTENT` in parallel, fuses via Reciprocal Rank Fusion (`k=60`, sum of `1/(k+rank_i)` across both lists). Per-doc tag: `METADATA_KEYWORD_ONLY_FALLBACK` removed when doc appeared in vector pass (vector confirmed → trust), kept when only BM25 found it. Per-context flags `ragBm25Fallback` + `ragHybridSearch` live on `TurSNSiteGenAi` (Liquibase `v2026.2.7.11.yaml` — see "Architectural reposition" note below). `TurRagSearchToolService` looks them up via the agent id in `ToolContext` → `snSiteGenAiRepository.findByTurAIAgent_Id`. Frontend toggles in the SN site GenAi form (`sn.site.genai.form.tsx`) under a new "Hybrid Retrieval (RAG)" SectionCard with EN/PT i18n; toggles disabled when bound agent has RAG off. 3 new unit tests pin: doc-in-both ranks first + tag stripped, dedup invariant, fused score equals `2/(60+1) ≈ 0.0328`. **Multi-language collections (per-locale BM25 cores in Solr/ES with `_bm25` suffix) tracked as T24b** below. | §III.2 |
| | | | **Architectural reposition (2026.2.7)** — `ragBm25Fallback` (T19) and `ragHybridSearch` (T24) initially shipped as columns on `TurAIAgent` were moved to `TurSNSiteGenAi` after a design Q&A clarified: RAG only fires through the SN site chat API, locale-aware retrieval depends on `TurSNSiteLocale` which the SN site owns, and agents have no native notion of locales. Migration `v2026.2.7.11.yaml`: adds `ragBm25Fallback` + `ragHybridSearch` columns to `sn_site_genai` (both default TRUE → existing bindings auto-upgrade to hybrid), then drops the same columns from `ai_agent`. API PUT for the agent no longer propagates the flags; the SN site GenAi update path now carries them. Frontend toggles moved out of `ai-agent.settings.form.tsx` and added to `sn.site.genai.form.tsx`. Same i18n key namespace switch: `forms.agentSettings.ragBm25Fallback*` → `forms.snGenai.bm25Fallback*`. | |
| T24b | ✅ | T24 | **Production hybrid RAG via dedicated BM25 cores in Solr/ES (admin-facing zero-config)**. Refined architecture after design Q&A: RAG runs ONLY through the SN site chat API (the only invocation path that needs RAG); the API request carries the locale → BM25 routes to the matching dedicated core. No language detection. ID conflict resolved: vector store chunks (N per asset) get parallel-indexed into RAG-dedicated cores in the SE (NOT reusing SN site search cores — those are 1 doc per source). Cores follow auto-generated naming `rag_<storeInstanceId>_<locale>`, auto-provisioned via Solr `CoreAdmin` / ES `PUT /<index>` with BM25 similarity (`k1=1.2`, `b=0.75`) + per-locale analyzer chain. Per-agent toggle in `ai-agent.settings.form.tsx`: Embedded (zero-config, default — current T24) vs Production (multi-language Solr/ES). Switch to Production → explicit "Reindex all" button with async progress bar. RAG-dedicated cores never appear in `TurSNSiteSearchAPI` (filtered by `rag_` prefix). 8-step plan: (1) `TurRagBm25Core` entity + Liquibase, (2) `TurRagBm25CoreProvisioner` (idempotent core/index creation per locale), (3) hook indexer to push chunks to the matching core after vector store add, (4) `hybridSearch(query, locale, topK)` routes BM25 pass via `TurSearchEnginePlugin` to `rag_<storeId>_<locale>`, (5) per-agent UI toggle + reindex button, (6) status cards per core (docCount, provisioning state), (7) async reindex job + progress endpoint, (8) `docs/RAG_HYBRID_CONFIG.md` with Solr/ES schema templates. **Estimate ~22h focused work** (language detect removed). Doesn't disrupt T24 embedded path — admins opt in. **Shipped 2026.3.1** as 5 phases: (1) `TurRagBm25Core` entity + `TurRagBm25CoreRepository` + Liquibase `v2026.2.7.12.yaml` + `TurRagBm25CoreProvisioner` (idempotent provision/deprovision/lookup, naming `rag_<storeShortId>_<localeTag>`); (2) `TurSearchEnginePlugin` extended with `indexStandaloneDocument(s)` / `deIndexStandalone(ByField)` / `commitStandalone` / `retrieveStandalone` — implemented in Solr (`SolrClient.add/deleteById/deleteByQuery`) and ES (`IndexRequest`/`BulkRequest`/`deleteByQuery`) plugins; `TurRagBm25Indexer` pushes Spring AI `Document` chunks into the matching core; (3) `TurRagBm25Source` enum + `ragBm25Source`/`ragSeInstance` on `TurSNSiteGenAi` + Liquibase `v2026.2.7.13.yaml` + `TurRagRrf` utility (extracted RRF from `TurLuceneVectorStore.hybridSearch` for reuse) + `TurRagSearchToolService.seHybridSearch` routing on `SE_INSTANCE` source with locale from `TOOL_CONTEXT_LOCALE`, degrades to vector-only on missing core / SE failure; (4) `TurRagBm25CoreAPI` REST (`GET/POST/DELETE /api/sn/{siteId}/rag/cores`) + frontend `sn.site.genai.form.tsx` extended with `Embedded`/`Search Engine` radio + SE-instance selector + per-locale status table; persisted in `TurSNSiteAPI` PUT (also fixed pre-existing bug: `ragBm25Fallback`/`ragHybridSearch` were not being saved on update); (5) [docs/RAG_HYBRID_CONFIG.md](RAG_HYBRID_CONFIG.md). 85 tests across 6 suites (`TurRagRrfTest`, `TurRagBm25CoreProvisionerTest`, `TurRagBm25IndexerTest`, `TurRagSearchToolServiceTest` extended +6 SE_INSTANCE tests, `TurRagBm25CoreAPITest`, latency IT also fixed — `TurAgentChatExecutorMockSupport` had leaked into every `@SpringBootTest` because it lived under `com.viglet.turing.*` scan; moved to `com.viglet.testsupport.genai.executor` so only `@Import` consumers get it). | §III.2 (production multi-language) |
| T25 | ✅ | — | Router language-aware: each flow's `triggerDescription` now tokenizes through the matching `Analyzer` in [TurChatFlowEngineService.analyzerFor](../turing-app/src/main/java/com/viglet/turing/genai/flow/TurChatFlowEngineService.java) — `PortugueseAnalyzer` for PT (default), `EnglishAnalyzer` for EN (Porter + EN stopwords). Selection via new `TurChatFlow.triggerLanguage` enum `AUTO / PT / EN` (Liquibase `v2026.3.1.0.yaml`, default `AUTO` for backwards-compat). `AUTO` runs a lightweight static stopword heuristic on the description per route — no Tika, no model load, zero allocation in steady state. `tryProceduralRoute` was refactored to score each candidate in ITS language (user-message tokens memoized per analyzer so N PT flows + 1 EN flow only tokenize twice). 14 unit tests pin: explicit override (PT/EN/null→AUTO), AUTO heuristic on PT-dominant and EN-dominant text, tie/no-signal defaults to PT, blank input defaults to PT, accent/case tolerance, stemmer behavior per analyzer (PT light: `planos/plano` collapse, `planejar` distinct; EN Porter: `running/runs` collapse, irregular `ran` distinct), and the crosswise failure mode T25 fixes (PT analyzer on EN text leaves running/runs distinct; EN analyzer on PT text leaves planos/plano distinct). Frontend: `chat-flow.settings-tab.tsx` gets a "Trigger language" Select after Trigger Mode, with PT/EN i18n. | §II.2.1 |
| T26 | ✅ | T25 | Router scoring: overlap counter replaced with Lucene `MoreLikeThis` over tiny in-memory indexes of `triggerDescription`s. T25 language routing is preserved by bucketing candidates by effective analyzer (`PT`/`EN`/`AUTO`) before indexing. MLT uses `minTermFreq=1`, `minDocFreq=1`, `minWordLen=2`, `maxQueryTerms=25` so short user messages still produce signal; the existing dominance ratio remains the commit gate until production logs justify a percentile threshold. New `lucene-queries` dependency plus 3 unit tests pin single rare-term routing, ambiguous equal-score fallback, and EN stemming through the T25 analyzer path. | §II.2.2 |
| T27 | ✅ | T26 | Router scaling: per-agent `SearcherManager` cache in `TurChatFlowEngineService.routerIndexByAgent` (one `AnalyzerBucket` per PT/EN bucket, `IndexWriterConfig.setSimilarity(new BM25Similarity())`); built lazily on first procedural route per agent, reused on subsequent turns. Index covers every enabled flow on the agent regardless of per-conversation eligibility — the eligibility mask is applied after MLT scoring, so the cache survives across conversations. Eviction wired alongside the existing `turChatFlowRouterDecision` hook through a JPA `@EntityListeners(TurChatFlowRouterEvictionListener.class)` on `TurChatFlow` (`@PostPersist/@PostUpdate/@PostRemove` → `engine.evictRouterIndexes()`), mirroring the `TurSNSiteSnapshotEvictionListener` pattern; the one bulk-DML delete path in `TurChatFlowAPI.turChatFlowDelete` (bypasses JPA callbacks) calls `evictRouterIndexes()` explicitly. Local-only by design: `SearcherManager` + `ByteBuffersDirectory` are non-serializable live resources, so each JVM holds its own copy and rebuilds on its next turn after a save/delete (the JPA listener fires per-SessionFactory). T26's static `tryProceduralRoute(List, String)` kept intact as the on-demand fallback + unit-test entry point. 5 new unit tests pin: index built once per agent (hit-rate verified via Mockito `times(1)` on `findByTurAIAgent_IdOrderByNameAsc`), `evictRouterIndexes()` forces rebuild on next call, eligibility mask excludes completed flows even when they would score top in the cached bucket, empty candidates short-circuit without building, null/blank agentId falls back to the T26 static path. | §II.2.3 |
| T28 | ✅ | — | **Phases A+B+C shipped 2026.3.1** — pluggable chat-analytics classifier backbone: new entity `TurAnalyticsIntent` (per-agent catalog, `label` + newline-separated `samples` + `enabled` + `description`, Liquibase `v2026.3.1.1.yaml` with unique `(agent_id, label)` constraint), Spring-cached `TurAnalyticsIntentRepository` (`turAnalyticsIntentFindByAgentId` + `turAnalyticsIntentMLTIndex`). Introduced `TurIntentClassifierStrategy` interface; the legacy LLM classifier moved to `TurLlmIntentClassifier` (unchanged behavior, plus an `agentId` parameter that the LLM strategy ignores). New `TurLuceneIntentClassifier` runs embedded Lucene `MoreLikeThis` over the per-agent catalog with the same per-agent `ConcurrentMap` cache pattern T27 uses (BM25 similarity, PT/EN analyzer pick via the same stopword heuristic as T25, `MIN_SCORE_FLOOR=0.5f` rejects incidental overlaps, `extractKeyTerms` matches the LLM strategy's 5-term cap). `TurChatIntentClassifier` rewritten as an orchestrator that picks one strategy per session per `turing.chat.analytics.classifier.mode` (`auto`/`llm`/`lucene`/`search-engine-mlt`; aliases `solr-mlt`/`elasticsearch-mlt`/`se-mlt`/`es`/`solr` all map to the same mode since the SE-backed strategy runs through `TurSearchEnginePlugin` and supports both Solr `MoreLikeThisHandler` and ES `more_like_this`); `auto` cascades LLM → SE-MLT → Lucene and falls through when a strategy returns unclassified. `TurChatAnalyticsEnricher` threads `agentId` from the session row into `classify(...)`. CRUD API at `/api/ai-agent/{agentId}/analytics-intent` (list/get/create/update/delete). JPA `@EntityListeners(TurAnalyticsIntentEvictionListener.class)` wipes the Lucene per-agent index on every catalog write, same hook pattern T27 uses for the chat-flow router. 12 unit tests pin: real catalog match (transcript → label), per-agent index built-once-per-cycle, `evictAll()` forces rebuild, below-floor score returns unclassified, null agent id short-circuits; orchestrator cascade LLM → Lucene, short-circuit on LLM hit, mode pinning, unavailable-strategy unclassified, backwards-compat shim. **Phase B shipped 2026.3.1** — search-engine-backed strategy live. `TurSearchEnginePlugin` gained `moreLikeThisStandalone(seInstance, indexName, field, text, topK)` (default `UnsupportedOperationException` for plugins that don't support MLT — single-JVM admins on the Lucene-embedded SE keep falling through to Phase A's `TurLuceneIntentClassifier`). Solr override hits the `/mlt` request handler via `qt=/mlt` + `stream.body=<transcript>` (`SolrQuery.setRequestHandler` deprecated in SolrJ 9 — switched to `set("qt", ...)`); falls back to a regular BM25 search against the samples field when the handler isn't reachable so installs that haven't opted into `enableStreamBody=true` still get a usable signal. ES override uses the native `more_like_this` query (`fields=samples`, `like=<text>`, `min_term_freq=1`, `min_doc_freq=1`, `min_word_length=3`, `max_query_terms=40`). New `TurSeMltIntentClassifier @Component` (`type="search-engine-mlt"`) wires those plugin methods into the orchestrator's `auto` cascade: LLM → SE-MLT → Lucene-embedded; floor `MIN_SCORE_FLOOR=0.5f` rejects incidental matches. `TurAnalyticsIntentIndexer @Service` keeps the per-agent SE index in sync — one doc per catalog row (`id`/`agentId`/`label`/`samples`), index name `intent_<agentShortId>` (matches T24b's `rag_` prefix convention so SN search APIs filter the internal indexes). The shared `TurAnalyticsIntentEvictionListener` fans out catalog mutations to both classifiers: wipes the Phase A in-memory index and upserts/removes the row in the SE-side index. `POST /api/ai-agent/{agentId}/analytics-intent/reindex` bootstrap path wipes the agent's slice via `deIndexStandaloneByField` then batch-pushes through `indexStandaloneDocuments`. SE binding (Phase B) uses property `turing.chat.analytics.classifier.se-instance-id` with fallback to the first registered SE instance — per-agent binding (new column on `TurAIAgent`) is a Phase C concern. 17 new unit tests (`TurSeMltIntentClassifierTest` 9, `TurAnalyticsIntentIndexerTest` 8) pin: availability gating (agent id + SE binding + non-empty catalog), top-hit → goalSummary mapping, below-floor unclassified, `UnsupportedOperationException` swallowed, index name normalization (UUID dashes stripped, capped at 12 chars), enabled rows upsert / disabled rows deindex, no-SE silent skip, full reindex wipes-then-pushes, configured property wins over `findAll` fallback. **Phase C shipped 2026.3.1** — frontend admin page and per-agent SE binding completed. New column `TurAIAgent.analyticsSeInstance` (`@ManyToOne(fetch=LAZY)`, nullable, FK to `se_instance(id)`, Liquibase `v2026.3.1.2.yaml`). `TurAnalyticsIntentIndexer.resolveSeInstance(agentId)` resolution order changed to: per-agent binding → property override → first registered SE; the backwards-compat no-arg overload delegates with `null`. `TurAIAgentAPI` PUT/POST propagate `analyticsSeInstance` via `resolveSingleRelation`. Frontend: new module under `frontend/apps/turing-app/src/app/console/analytics-intent/` — single `analytics-intent.list.page.tsx` with inline modal dialog for create/edit (heavier list+settings split kept for the legacy `intent/` module where actions/icon/sortOrder justify it; analytics intents have only label+samples+enabled+description so the compact form ships fine). Reindex button in the page header wires to the Phase B `POST /reindex` endpoint. New model (`analytics-intent.model.ts`), service (`analytics-intent.service.ts`), React Query hooks (`analytics-intent.queries.ts` with new `analyticsIntents` query key family), route registration (`/ai-agent/instance/:id/analytics-intent` lazy-loaded), sidebar nav entry under the chat group (`IconChartBar`), PT/EN i18n (`analyticsIntent.json` + `aiAgent.nav.analyticsIntent` + `forms.agentSettings.analyticsSeInstance*` keys). Agent settings form gains a `Select` for the SE binding (label + global-default fallback option). 31 backend tests still pass (added 2 new orchestrator tests for the per-agent SE binding fallback chain). | §III.5 |
| T29 | ✅ | 2026.3.1 | Tool pre-filtering for large catalogs: `TurToolPreFilterService` does BM25 / MoreLikeThis over `prompts/tools/**/*.md` descriptions in an in-memory `ByteBuffersDirectory`, keeps top-K configured via `turing.genai.tool-prefilter.{top-k,min-tools-threshold,enabled}`. Always-kept set covers agent `nativeTools` + the active node's new `ChatFlowNode.requiredTools` list. Bypasses for blank user message / pool below threshold / cap already covers the pool. | §IV.2 |
| T30 | ✅ | 2026.3.1 | Conversation-relevance retrieval for chat memory: new `TurChatMemoryRelevanceRetriever` reads the persisted history, splits at the recent-N FIFO window, builds a per-call `ByteBuffersDirectory` + `BM25Similarity` index over the older pool, scores against the latest user message via `MoreLikeThis`, and prepends the top-K hits (chronologically re-sorted, prefixed with `[earlier turn #N at <ts>]` markers) to the history that flows into `TurAgentChatExecutor.execute()`. Three new opt-in fields on `TurAIAgent`: `chatMemoryRelevanceEnabled` (default false), `chatMemoryRelevanceTopK` (default 5, capped at 20 in code), `chatMemoryRecentN` (default 10) — Liquibase `v2026.3.1.3.yaml`. Exchange round-trip extended so import/export carries the new fields. 18 new unit tests pin: bypass when chat memory / relevance / store / convId / userMsg are off, bypass when persisted ≤ recent-N, no-overlap returns history unchanged, BM25 surfaces the matching older turn with position+ISO timestamp marker, multiple hits sorted by original position (not score), top-K cap honored + hard-capped at 20 on misconfig, role preserved on retrieved turns, null history → empty list, store I/O failure falls back to original history, malformed persisted rows (null/blank role/content) skipped, marker helpers (no-ts fallback, ISO normalization, epoch-millis path). | §IV.4 |
| T31 | ✅ | 2026.3.1 | System-prompt fragment caching: new `TurPersonaStaticPromptCache` `@Cacheable("turPersonaStaticPrompt")` keyed by `personaId` for the deterministic persona block (instruction + style + mandatory/forbidden vocab + brand placeholder); few-shot block stays uncached because it depends on `userQuery`. New `TurChatFlowStaticPromptCache` exposes `staticHead` (`@Cacheable("turChatFlowStaticAddendumHead")` keyed by `flowId:nodeId`) + `staticTail` (`@Cacheable("turChatFlowStaticAddendumTail")` keyed by `flowId:nodeId:includeNextStepHint`); the dynamic "Already collected" line is built per turn. `ChatFlowOps.buildBaseAddendum` refactored into three pure helpers (`buildStaticAddendumHead` / `buildVariablesLine` / `buildStaticAddendumTail`) so the cache bean and any legacy static caller share one source of truth. Strategy interface gained a `TurChatFlow flow` parameter so the cache key can be derived from the live flow. Eviction extends the existing `@CacheEvict` chains: `TurPersonaRepository.save/delete` now also wipes `turPersonaStaticPrompt`; `TurChatFlowRepository.save/delete` adds `turChatFlowStaticAddendumHead` + `turChatFlowStaticAddendumTail` to the existing router-decision eviction set. Self-injection (`@Lazy` self ref) in `TurChatFlowStaticPromptCache` routes intra-class calls through the Spring proxy so the `@Cacheable` advice actually fires. 24 new unit tests across `TurPersonaStaticPromptCacheTest` (12: null/blank persona, instruction/style/vocab/brand block presence, verbosity clamping, determinism, output excludes few-shot + base-separator boundaries) and `TurChatFlowStaticPromptCacheTest` (12: orchestrator null/terminal short-circuit, head+vars+tail concatenation, dynamic line omission on blank state, null-flow fallback, head/tail content split, hint-toggle, `buildBaseAddendum` equals head+vars+tail invariant). All smoke ITs (`HarnessFixtureSmokeIT` 3/3, `TestSupportSmokeIT` 1/1) + impacted strategy tests (`LlmJudgeGuardrailStrategyTest` 7/7, `ChatFlowOpsTest` + `ChatFlowOpsBrDocUnitTest`) green. | §IV.5 |
| T32 | ✅ | 2026.3.1 | Observability for the cache + retrieval features shipped in 2026.3.1: new `TurChatCacheStatsRegistrar` registers Micrometer gauges (`turing.chat.cache.{hits,misses,size}` tagged by `cache`) for the 9 Hazelcast `IMap`s the chat pipeline reads — gauges resolve via `ObjectProvider<HazelcastInstance>` so dev mode (no clustered cache) degrades silently instead of flat-zeroing. Misses derived as `getOperationCount − getHits` since `LocalMapStats` doesn't expose a direct miss counter, clamped at 0. `TurChatPipelineObservation` gains `recordMemoryRetrieval(poolSize, hits, ms, outcome)` and `recordToolPrefilter(in, out, ms, reason)` helpers; T30 retriever wires every branch (5 bypass paths → `outcome=bypass`, BM25 zero-match → `no-hits`, prepended turns → `hits`) and T29 pre-filter wires its 6 reasons (`disabled`, `below-threshold`, `blank-query`, `already-covered`, `cap-covers-pool`, `engaged`) on a bypass counter alongside the timer outcome (`filtered` / `passthrough`). 6 new sub-stage breakdowns of `chat.setup` (`decrypt`/`model`/`flow`/`persona`/`tools`/`compose`) recorded from the existing timing locals in `TurAgentChatExecutor.execute()` — backwards-compatible: the parent `chat.setup` aggregate stays. 12 new unit tests (`TurChatPipelineObservationT32Test` 7, `TurChatCacheStatsRegistrarTest` 5) pin: hits/no-hits/bypass outcomes register the right timer+distribution combinations, distributions skipped on bypass to avoid zero-pollution, all 6 prefilter reasons get independent counters; per-cache gauges resolve to the expected derived values (hits=100 / misses=50 / size=42 round-trip), miss gauge clamps at 0 on stat regression, gauge supplier swallows Hazelcast exceptions so a cluster failure can't take down the Prometheus scrape, no-Hazelcast mode registers zero gauges (not flat-zero ones). All smoke ITs (`HarnessFixtureSmokeIT` 3/3, `TestSupportSmokeIT` 1/1) + impacted existing tests green (`TurChatMemoryRelevanceRetrieverTest` 18/18, `TurToolPreFilterServiceTest` 9/9). Metrics exposed via the existing `/actuator/prometheus` endpoint — no new dependency. | §IV.7 |
| T33 | ✅ | — | Decomposed `TurAgentChatExecutor.execute()` (was 1074 lines / 13 timing substages) into 3 collaborator beans + a top-level `TurAgentChatFlowContext` record: `TurChatToolResolver` (native + MCP + custom + persona-brand callbacks → pipeline-decorate → T29 BM25 pre-filter; static `forbidsToolCalls(ChatFlowNode)` lives here, with the executor exposing a thin static façade for back-compat with `TurAgentChatExecutorStructuralIT`), `TurChatPromptAssembler` (T31-cached flow addendum + persona compose + T30 conversation-relevance enrichment + `List<Message>` build), `TurChatStreamingDispatcher` (CALL vs STREAM branch, tone validation, telemetry, suggested options + SSE fan-out via the moved `AssistantTurn` + `emitTurn` helpers). Executor shrunk to 573 lines total; the 6-arg `execute()` body is 147 lines of pure orchestration. Observability shape preserved exactly — every `STAGE_CHAT_*` meter still fires (DECRYPT/MODEL/FLOW/PERSONA in the executor; TOOLS in the resolver; COMPOSE in the assembler; LLM_CALL/POST/TOTAL/ADVANCE/REGEN in the dispatcher) and the three `[AgentExec][timing]` log lines (setup_ms / llm_ms / total_ms / STREAM total_ms) keep their original shape. Public API untouched: `Flux<ChatResponse> execute(...)` 4-arg + 6-arg overloads, `resolveToolCallbacks(...)` overloads, `ChatMessageItem` / `ChatResponse` records, `FLOW_ID_NONE`, `forbidsToolCalls(ChatFlowNode)` all still on `TurAgentChatExecutor` so `TurAIAgentChatAPI`, `TurSNGenAi`, `TurSNSiteGenAiAPI`, `TurLLMSemanticChatAPI` and every test pass through with zero diff. Verified: backend compile clean (853 source files), 121/121 impacted unit tests green (`TurAIAgentChatAPITest` 16, `ChatFlowOpsBrDocUnitTest` 26, `ChatFlowOpsTest` 28, `LlmJudgeGuardrailStrategyTest` 7, `TurSNGenAiTest` 26, `TurChatMemoryRelevanceRetrieverTest` 18); 16/16 ITs green (`HarnessFixtureSmokeIT` 3, `TestSupportSmokeIT` 1, `TurAgentChatExecutorContractIT` 3 + 1 skipped, `TurAgentChatExecutorStructuralIT$ForbidsToolCallsUnitTests` 8). | §I.8 |
| T34 | ✅ | — | Codified the caching conventions agents kept re-deriving. (a) New "Adding a `@Cacheable` query" subsection under "Cache, Scheduling & Serialization Notes" in [agents.md](../agents.md) covering the four rules: pair every cache name with a `@CacheEvict` (now lint-enforced), `@Cacheable` is proxy-based so never call via `this` ([`TurChatFlowStaticPromptCache`](../turing-app/src/main/java/com/viglet/turing/genai/flow/TurChatFlowStaticPromptCache.java) cited as the self-injection example), lowerCamelCase cache-name convention, Hazelcast-vs-in-process is a deploy-time concern not a `@Cacheable`-site concern. (b) New [`TurCacheAnnotationConventionsTest`](../turing-app/src/test/java/com/viglet/turing/db/TurCacheAnnotationConventionsTest.java) mirrors `TurLiquibaseChangelogAgnosticTest`'s file-tree-walk style: walks `target/classes`, reads `@Cacheable` / `@CacheEvict` / `@Caching` annotation metadata via Spring's bytecode-level `MetadataReader` (no class loading, no Spring context), unions cache names from both sides, fails when a `@Cacheable` name is never evicted. Caught one real case on first run — `turSNSiteSearchSnapshot` is evicted programmatically by `TurSNSiteSnapshotEvictionListener` (JPA `@PostPersist/@PostUpdate/@PostRemove` → `cacheManager.getCache(name).clear()`), not declaratively; added to `EVICTION_EXEMPT` with a comment so the lint stays honest. Test runs in ~0.8s. | §IV.8 |

### Block D — Education customer engagement — platform-level expansion

> Backlog distilled from the Education customer × Turing engagement (see
> §VII for the full synthesis). Many items below were prototyped or
> shipped as customer-side Custom Tools / portal code that should be
> promoted to platform citizens. Status reflects **what is in the Turing
> platform**, not what exists in the customer repo. The customer repo
> doc is the authoritative source for the customer-side artifacts; this
> section tracks what graduates into the Turing platform proper.

#### D.1 — Custom Tool helpers & DX

| #   | Status | Depends on | Task | Detail in |
|-----|--------|------------|------|-----------|
| T35 | ✅ | — | `http`, `turingSearch` (with doc-level dedup), `slots` helpers exposed to Custom Tool Groovy scripts + `ToolContext` threading via `ChatOptions.toolContext` propagating `conversationId` end-to-end. | §VII.1.a |
| T36 | ✅ | T35 | `code.executePython(...)` binding — `TurCodeInterpreterToolService` exposed to Custom Tools Groovy via `TurCustomToolCodeHelper`. Enables deterministic `Groovy compose → Python sandbox` pattern (PDF generation, charts, data transforms) without LLM in the critical path. | §VII.1.b |
| T37 | ✅ | T35 | Hot-reload endpoint `POST /api/custom-tool/{id}/execute` for live Groovy testing without restart (`TurCustomToolCallbackService.executeForPreview`). Per-invocation latency tracking via `TurChatLoggingToolCallback` flushed in `recordSessionEnd`. | §VII.1.c |
| T38 | ✅ | T35 | `turingSearch.sn(...)` helper — in-process call to `TurSNSearchProcess.search`, no HTTP self-call. Aliases for `query`/`q`/`rows`/`page`/`sort`/`fq[*]`/`fl`/`templateName`; return shape mirrors `ann()` (flat fields + `id`/`source`/`elevate`). | §VII.1.d |
| T39 | 📋 | T35 | `http.bearer(token).postJson(...)` + `http.basic(user, pass)` chainable auth helpers — eliminates boilerplate when Custom Tools call authenticated APIs. Slot prefix `__` reserved for tokens (`__bearer_token` → auto `Authorization` header). | §VII.2.a |
| T40 | 💭 | — | Groovy editor with auto-complete: descriptor JSON shipped to Monaco listing all helpers (`http`, `code`, `slots`, `turingSearch`) + signature hints. Lowers the bar for non-dev authors. | §VII.2.b |
| T41 | 💭 | T40 | Hot-reload of Groovy via WebSocket — admin saves Groovy → WS pushes reload to backend → next invocation uses new script without restart. | §VII.2.c |

#### D.2 — Engine declarative additions (new node types & policies)

| #   | Status | Depends on | Task | Detail in |
|-----|--------|------------|------|-----------|
| T42 | ✅ | — | `writeSlot` node type — slot SET with server-side `{{variable}}` interpolation via `ChatFlowOps.applyWriteSlotNode`. Editor palette item with `IconPencilPlus` (teal). | §VII.3.a |
| T43 | ✅ | — | `formCapture` node type — like `aiQuestion` but with strict regex validation (CPF, CNPJ, email, phone, CEP). `ChatFlowOps.VALIDATION_PATTERNS` + canonical extractor cascade. Editor renders with `IconForms` (indigo) + rule-name badge. | §VII.3.b |
| T44 | ✅ | — | CPF/CNPJ digit-check (mod-11) on `formCapture` patterns — strict regex format + checksum validation. | §VII.3.b |
| T45 | ✅ | — | `onJudgeReject` declarative policy (already documented as patch #22) — `advance_with_literal` / `reprompt` / `block` per `aiQuestion`. Removes the need for `LlmJudgeGuardrailStrategy` retry heuristic. | §I.3 patch #22 |
| T46 | 📋 | T45 | `functionCall` node runtime — type already exists in `ChatFlowNodeGeneration.ts` but the engine does not execute it. Decision needed: implement (deterministic tool invocation from the flow, not the LLM) OR remove from the DSL. **Vapor features are worse than absences.** | §VII.4.a |
| T47 | 📋 | — | `subFlowSwitch` node — switch that chooses a sub-flow by slot value (vs routing edges). Composes reusable flows; pairs with cross-flow slot inheritance (T58). | §VII.4.b |
| T48 | 💭 | — | `scheduleAgent` integration node — fires a routine remote and waits for its slot-bus response. Long-running tools without blocking the chat turn. | §VII.4.c |
| T49 | 💭 | — | `continueOnFailure: true` flag on nodes — authors mark a node where a tool/LLM failure falls into an alternative path instead of stalling. | §VII.4.d |
| T50 | 💭 | T49 | `onError` edges in editor visual — analogous to try/catch; red arrow that fires when the node fails. | §VII.4.d |
| T51 | 💭 | — | Capture-first inversion — restructure engine to **always** persist the user message into the slot BEFORE the judge runs. Judge becomes advisory (`slot_confidence: low/high` in a parallel slot); the flow never stalls with `slot=null`. Architectural — kills the whole "judge rejects → flow stuck" class at the root (vs. T45's per-node mitigation). Affects all existing flows; requires full IT revalidation. | §VII.4.e |
| T52 | 💭 | — | Few-shot baked into platform-level judge system prompt — 2-3 positive examples (compound `cargo+tempo` → capture; informal `objetivo` → capture) + 1 negative (clicked chip routed to wrong node → reject with redirect). Reduces stochastic rejects without architecture change. | §VII.4.f |
| T53 | 💭 | — | `abandoned` auto-escalation — when judge detects abandonment, offer human consultant via handoff instead of just closing the session. | §VII.4.g |

#### D.3 — Slot infrastructure & compliance

| #   | Status | Depends on | Task | Detail in |
|-----|--------|------------|------|-----------|
| T54 | ✅ | T42 | SSE for slot updates — `TurChatSlotEventBus` in-memory bus + `GET /chat/slots/stream` (`Flux<TurChatSessionSlotsDto>`); three write paths (node, `slots.set`, `POST /chat/slots`) instrumented. **Heartbeat 25s comment-only** keeps the channel alive through corporate proxies. Latency slot→UI: <100ms (was ~1.5s polling). | §VII.5.a |
| T55 | ✅ | — | `POST /chat/handoff` — channel-agnostic handoff with transcript snapshot. 5 channels live: WhatsApp / Email / SMS / Telegram / Slack. Slot whitelist for transcript + custom intro; result includes `handoff_status` slot for analytics segmentation. | §VII.5.b |
| T56 | ✅ | — | `GET /chat/share/og` — server-side rendered Open Graph card. Slot values via query params drive `og:*` + `twitter:*` tags; humans get meta-refresh redirect. Personalized link previews on WhatsApp / Slack / LinkedIn / X. | §VII.5.c |
| T57 | ✅ | — | `GET /chat/state` — snapshot of active flow (`flowId`/`flowName`/`currentNodeId`/`guardrailMethod`/`experimentKey`/`variantLabel`). Powers SDK hooks `useTuringFlowState` + `useTuringExperiment` without polluting slots. | §VII.5.d |
| T58 | ✅ | — | `POST /chat/slot-extract` — multipart file → Tika → LLM structured output → write per slot. Same `TurFileUtils.documentToText()` as RAG indexing; tolerant JSON parser (code-fence + drift whitelist). | §VII.5.e |
| T59 | ✅ | — | `TurLlmSummaryService.generate(cacheKey, data, systemPrompt, regenerate)` — reusable LLM summary with cache (TTL from Global Settings) + regenerate. Powers `GET /api/sn/{id}/summary` + `GET /api/system/info/insights`. | §VII.5.f |
| T60 | 📋 | T54 | **Slot audit log** — every slot write registers origin (`node:`/`tool:`/`endpoint:`/`extract:`) in `chat_slot_write_log` (conversationId, slot, oldValue, newValue, source, ts). LGPD audit + debug forensics. SlotInspector / SDK renders as timeline with source badge. | §VII.6.a |
| T61 | 📋 | — | **PII slot prefix** `pii_*` — engine redacts in logs/analytics, encrypts at rest, TTL configurable (default 24h). LGPD/GDPR-ready compliance pitch. | §VII.6.b |
| T62 | 💭 | T54 | Webhook handoff channel — `POST` transcript to a configured CRM endpoint (Salesforce / HubSpot / Pipedrive). Slot subscription wider: arbitrary slot writes can fire webhooks declared in admin. | §VII.6.c |
| T63 | 💭 | T54 | Slot delta SSE — emit only `{added, removed, updated}` deltas instead of full snapshot. 10× fewer bytes on conversations with 20+ slots. | §VII.6.d |
| T64 | 💭 | — | Multi-modal slots (`IMAGE`/`AUDIO`/`FILE` types) — slot value is URL to MinIO/S3 binary; Custom Tools resolve via Vision/Audio APIs. Photo-of-CV → Vision LLM → slots. | §VII.6.e |
| T65 | 💭 | — | Snapshot export of conversation state — admin button "Export conversation" returns JSON of `chat_flow_state` + slots + transcript for debug / regression. | §VII.6.f |
| T66 | 💭 | — | `SubmissionRetention` config (`RETAIN_FOREVER` / `RETAIN_DAYS=N` / `DELETE_AFTER_EXPORT`) per agent — compliance hook tied to PII expiration. | §VII.6.g |

#### D.4 — A/B testing evolution

| #   | Status | Depends on | Task | Detail in |
|-----|--------|------------|------|-----------|
| T67 | ✅ | — | A/B testing native: 5 columns on `TurChatFlow` (`experimentKey`, `variantLabel`, `trafficWeight`, `experimentStartsAt`, `experimentEndsAt`) + `assignVariant` deterministic hash + sticky continuation + `isInExperimentWindow` scheduling filter. | §VII.7.a |
| T68 | ✅ | T67 | A/B significance endpoint `/experiment/{key}/significance` — two-proportion z-test with Wald CI. Tells operator when to retire variant. | §VII.7.b |
| T69 | ✅ | T67 | A/B analytics dimensions — `experimentKey` + `variantLabel` persisted in `TurChatSessionEvent`; scorecard supports 4 dimensions (agent / persona / experiment / variant). | §VII.7.c |
| T70 | 📋 | T67 | **A/B multi-armed bandit** — auto-adjust `trafficWeight` based on observed conversion in last N sessions. Replaces manual fixed split + scheduling window with bandit math (Thompson sampling / UCB). Better optimization, less operator work. | §VII.8.a |
| T71 | 💭 | T68, T70 | **Champion-challenger auto-promotion** — when significance endpoint detects a winner, auto-promote `trafficWeight=100` on the winner and archive the loser. | §VII.8.b |
| T72 | 💭 | T67 | Per-node A/B (`ChatFlowNode.nodeExperimentKey`) — variants swap a single node, not the whole flow. Granular optimization without duplicating 50 nodes. | §VII.8.c |
| T73 | 💭 | T67 | Forced variant via URL param (`?_ab_variant=lucas-alumni`) — useful for QA + sales demo. | §VII.8.d |
| T74 | 💭 | T69 | Cohort filters in scorecard — filter by timezone / locale / user-agent (mobile vs desktop) to see if a variant wins in a sub-population. | §VII.8.e |
| T75 | 💭 | T69 | Variant trace logging — line per turn `experimentKey:variantLabel:nodeId:userMessage` for exact path reconstruction in production. | §VII.8.f |

#### D.5 — Code Interpreter hardening (the missing security layer)

| #   | Status | Depends on | Task | Detail in |
|-----|--------|------------|------|-----------|
| T76 | ✅ | — | Date-bucketed sessions + cleanup task — disk-leak fix (was accumulating ~50MB/day on a moderate site). | §VII.9.a |
| T77 | ✅ | — | Per-agent Python deps auto-install (global + per-agent UNION) — admin declares `pip install reportlab matplotlib qrcode` in a Custom Tool textarea; engine ensures deps before `executePython`. | §VII.9.b |
| T78 | ✅ | — | Multi-tenant path isolation — session dirs scoped as `tenants/{agentId}/{convId}/...`. Eliminates cross-tenant filesystem accidents (`TurCodeInterpreterToolService.executePythonForTenant`). | §VII.9.c |
| T79 | ✅ | — | HMAC-signed URLs + cookie-binding for `/api/v2/code-interpreter/{session}/{file}` — prevents URL replay / cross-browser leakage (`TurCodeInterpreterUrlSigner`). | §VII.9.d |
| T80 | 📋 | T78 | ⭐ **Container sandbox (Docker / gVisor / Firecracker)** — the ONLY remaining gap in the Code Interpreter threat model. Today a malicious Python script can `open("../../../etc/passwd")` and read any host file the JVM user can; path isolation + URL hardening close HTTP / operational vectors but not the runtime escape. Containerization isolates syscalls, filesystem, network. Estimated ~2 weeks: base image with `python+reportlab+matplotlib` pre-installed + bind-mount only on session dir + drop capabilities + seccomp filter; gVisor for defense-in-depth. **Gate item for multi-tenant SaaS pitch with customer-authored Custom Tools.** | §VII.9.e |
| T81 | 💭 | T80 | Resource limits per execution (cgroups) — CPU/RAM cap. Today only the 30s timeout controls runaway scripts (`[x*x for x in range(10**9)]` still trashes the JVM). | §VII.9.f |
| T82 | 💭 | T76 | Pre-warmed Python interpreter pool — first call pays ~200ms startup. Keeping 2-3 warm cuts ~70% latency for frequently-invoked tools. | §VII.9.g |
| T83 | 💭 | T36 | Structured output capture — return `{stdout, files: [...], duration_ms, exit_code}` JSON instead of markdown-with-links so callers don't regex-parse. | §VII.9.h |

#### D.6 — Analytics & observability gaps

| #   | Status | Depends on | Task | Detail in |
|-----|--------|------------|------|-----------|
| T84 | ✅ | — | Chat session analytics — 9 timeseries metrics (sessions, goal-achievement-rate, negative-sentiment-rate, avg-duration-ms, avg-tokens-out, tool-calls-per-session, tool-errors-per-session, avg-tool-latency-ms, tool-error-rate-pct) + 4 scorecard dimensions (agent/persona/experiment/variant). Mongo + Redis stores. | §VII.10.a |
| T85 | 📋 | T84 | **Funnel visualization** — turn-by-turn abandonment heatmap over the flow graph. "60% drop between `ai-cargo` and `ai-objetivo`." Drives surgical UX fixes vs whole-flow rewrites. | §VII.10.b |
| T86 | 📋 | T84 | **Conversation replay UI** — click `conversationId` in scorecard → transcript + slot timeline side-by-side, scrubber temporal, prompt+response artifacts per turn. Investigation-grade. | §VII.10.c |
| T87 | 💭 | T84 | Sentiment trajectory per turn (line chart 1..N) — identifies where the conversation soured. | §VII.10.d |
| T88 | 💭 | T84 | Tool execution latency p95 per tool (disaggregated) — today aggregated as `avg_tool_latency_ms`. Surfaces "this one tool is a p95 outlier." | §VII.10.e |
| T89 | 💭 | — | Flow router decision logs — structured log per LLM router call: candidate flows + score + winner. Captures "why did B2B lose to B2C on this message?" — needed when a customer complains. | §VII.10.f |
| T90 | 💭 | T54 | SSE channel debug surface in admin — live panel showing open channels + refcount (consuming the SDK's `_slotsSseOpenChannelCount`). Operator validates "12 portal tabs, 4 channels, refcount sane." | §VII.10.g |

#### D.7 — Multi-flow architecture & flow editor DX

| #   | Status | Depends on | Task | Detail in |
|-----|--------|------------|------|-----------|
| T91 | ✅ | — | **Trigger conflict resolver UI** — `TurTriggerConflictService` runs Jaccard over analyzer-stemmed trigger descriptions on each agent and emits HIGH/WARNING pairs via `GET /chat-flow/trigger-conflicts`. Editor + list pages render `ChatFlowTriggerConflictsPanel` (rose card for HIGH, amber for WARNING) with overlapping stems and a PT/EN disambiguation hint. Refreshes automatically on chat-flow save/delete via the existing `chatFlows.all()` invalidation. | §VII.11.a |
| T92 | ✅ | — | **Flow chooser endpoint** `POST /api/sn/{siteName}/chat/flow-select` — `{conversationId, flow}` pins a specific flow; `flow` resolves by UUID first, then by case-insensitive name (deep-link friendly: `?flow=in-company`). `TurChatFlowEngineService.pinFlowForConversation` snapshots existing slots, resets every prior in-progress state on the agent, pre-creates the chosen flow's state with the snapshot seeded in, and walks transparent + satisfied-question nodes so the next chat turn lands on the right cursor. Engine continuation rule + the slot inheritance combo means subsequent chat calls need no `flowId` plumbing. | §VII.11.b |
| T93 | ✅ | T92 | **Cross-flow slot inheritance** — `TurChatFlow.slotInheritanceJson` (longtext) holds `{receivingSlot:sourceSlot}` map; `{"*":"*"}` is the wildcard "bring everything". Engine helper `applySlotInheritance` projects the conversation's snapshot through the map and seeds the new state BEFORE `walkThroughSatisfiedQuestions` runs (so already-filled questions auto-skip). Wired into both the manual pin (T92) and the auto-router fresh-route branch. Editor exposes a "Preserve all session slots" toggle + advanced JSON textarea. | §VII.11.c |
| T94 | ✅ | — | **Chat-flow lint** — `TurChatFlowLinterService.lint(agentId, flow)` returns `[{nodeId, edgeId, severity, code, message, hint}]` covering `ai_instruction_too_long` (500-char soft cap), `unused_output_variable` (cross-flow scan: counts inheritance sources + interpolation + switch / slot reads), `dead_end_node` (ERROR — non-end node with no outgoing edge), `trigger_conflict` (delegates to T91), `edge_without_label` (condition/switch edge missing both sourceHandle and label). `GET /api/ai-agent/{agentId}/chat-flow/{id}/lint` powers the `ChatFlowLintPanel` sidebar (rose on ERROR, amber on WARNING, hidden when clean). | §VII.11.d |
| T95 | ✅ | — | **Live preview of flow in editor** — `ChatFlowLivePreviewPanel` (toggled by the `IconBolt` action) shows the selected node rendered as the visitor would see it, with `{{slot}}` references interpolated from a `key=value` mock textarea persisted in `localStorage` per flow id. Per-node rendering covers every node type (aiQuestion/formCapture/start/end/condition/switch/slot/writeSlot/subFlow/persona/functionCall); unresolved slots highlight in amber so authors notice missing mocks. All client-side — instant updates, no LLM round-trip. | §VII.11.e |
| T96 | ✅ | — | **Flow Recipes — unified marketplace** — recipes are bundled JSON in `resources/flow-recipes/*.json` (3 shipped: `lead-capture-b2c`, `in-company-quote-b2b`, `micro-lesson-educational`) carrying flow(s)+personas+slots; reuses `/import-bundle` machinery. Catalog at `GET /api/chat-flow-recipes`, full payload at `/{id}`, install at `POST /ai-agent/{agentId}/chat-flow/install-recipe/{id}` with `installedFromRecipe` + `installedRecipeVersion` audit columns on `chat_flow`. Marketplace UI **unified** with Sites: filter chips for `kind` (All / Sites / Flow Recipes — extensible for future Skills) and `vertical` (Education / Finance / Healthcare / General); FlowRecipeCard shows vertical chip + flow/slot/persona counts; install dialog has agent picker + preview of bundled assets + post-install navigation to the editor. The B2B recipe pre-wires T93 inheritance from the B2C lead-capture so chained installs "just work". | §VII.11.f |
| T97 | 💭 | — | **Variant generator via LLM** — author writes flow A; clicks "generate variant B with more casual tone, 30% shorter" → LLM proposes ready-to-review variant. 1 flow + 5 variants in 10min vs 5 hours. | §VII.11.g |
| T98 | 💭 | — | **TypeScript DSL** for chat-flow (`@viglet/turing-flow-dsl`) — `flows/foo.ts` with types imported from backend enums; transpiles to JSON at build time; back-compat reading raw JSON. Catches `tone=PROFESSIONAL` typos at compile time. | §VII.11.h |

#### D.8 — Document extraction & document AI

| #   | Status | Depends on | Task | Detail in |
|-----|--------|------------|------|-----------|
| T99  | 💭 | T58 | OCR de CV (image) — phone-photo of CV bypasses Tika text extraction; integrate Tesseract (already a Tika parser dep) or Vision API. | §VII.12.a |
| T100 | 💭 | T58 | Per-agent extraction schema — agent declares `documentSchema` in admin (slot names + validation patterns) instead of caller passing slot names per request. | §VII.12.b |
| T101 | 💭 | T58 | Multi-doc context — upload CV + JD together → LLM identifies skill gap → writes `skill_gap` slot. | §VII.12.c |
| T102 | 💭 | T58 | Extraction confidence per field — LLM returns `{value, confidence: 0-1}`; UI shows warning when `confidence < 0.7`. | §VII.12.d |
| T103 | 💭 | T58 | Anthropic Files API / OpenAI Files — pass binary PDF/DOCX directly to the LLM (preserves tables/layout) instead of Tika→text. | §VII.12.e |

#### D.9 — SDK & frontend surface

The Education customer engagement shipped 10 React hooks. Most are
already in `@viglet/turing-react-sdk` (versions 2026.2.21 → .29).
Remaining platform-relevant promotions:

| #   | Status | Depends on | Task | Detail in |
|-----|--------|------------|------|-----------|
| T104 | ✅ | — | 10 hooks in `@viglet/turing-react-sdk`: `useTuringSlotWriter` · `useTuringSlotExtract` · `useTuringHandoff` · SSE in `useTuringSlots` · `useTuringVoice` · `useTuringFlowState` · `useTuringExperiment` · `useTuringSlot<T>` generic with auto JSON.parse · SSE multiplexer (N→1 EventSource via refcount module-level). Storybook 100% coverage. | §VII.13.a |
| T105 | 💭 | — | Promote `CompanyProposalCard` shape (header + pills + pricing hero + modules + timeline + similar cases) to SDK as a generic `ProposalCard<T>` taking a typed proposal payload. Reusable across customers (education / consulting / training). | §VII.13.b |
| T106 | 💭 | T92 | `useTuringFlowChooser` hook — programmatically force a specific flow (pairs with T92 endpoint). | §VII.13.c |
| T107 | 💭 | — | `formCapture` form-event SSE — emit structured event so SDK renders a native form (multi-field capture) instead of single-turn validation. | §VII.13.d |

#### D.10 — Kill list (deprecate or remove)

| #    | Status | Task | Rationale |
|------|--------|------|-----------|
| TK1 | 📋 | Deprecate `HeuristicGuardrailStrategy` (default → `LLM_JUDGE` for new flows; kept for back-compat) | Regex-only. `LLM_JUDGE` + tool-calling with provider schema does the job better. Third option that nobody picks pollutes the UI. |
| TK2 | 📋 | Remove slot types `INTEGER` / `BOOLEAN` from the DSL | Client-side parser via `useTuringSlot<T>` does the coercion better. Schema-level types only generate coercion bugs. |
| TK3 | 📋 | Remove `forbidsToolCalls(String)` substring overload (T16 left only the node-aware path; the bilingual substring matcher is still inside `@Nested` unit tests but not in production) | Already 95% gone post-T16. Finish the job. |
| TK4 | 💭 | Audit `functionCall` node type — implement (T46) OR remove from the schema | Vapor features are worse than absences. Decision needed. |
| TK5 | 💭 | Replace Brevo email integration with Resend or Postmark | Modern provider, smaller footprint, better deliverability. Email-out is the feature; the provider is a swappable detail. |
| TK6 | 💭 | Audit Custom Solr admin UI usage; if zero customers depend on it, fold into Solr native admin URL | TurSN legacy. Maintaining two admin surfaces dilutes. |

### Block E — Deep Agents competitive parity (and beyond)

> Triggered by analysis of LangChain Deep Agents (`langchain.com/deep-agents`).
> Each task below either (a) reaches parity with a Deep-Agents capability
> Turing currently lacks, or (b) differentiates with something Deep
> Agents *doesn't* have. Full competitive analysis in §IX.
>
> **Strategic frame**: Deep Agents is a Python SDK with middleware
> (planning tool, virtual filesystem, sub-agent spawner, context
> compression). Turing already has the *infrastructure* analog of every
> one of those (chat-flow = planning graph; `TurStorageService` = pluggable
> filesystem; sub-flows = sub-agents; T30/T31 = context engineering).
> What Turing lacks is the **per-conversation workspace primitive** that
> ties them into a "deep" workflow. Block E closes that gap and adds
> three moves where Turing leads.

#### E.1 — Planning & decomposition (parity)

| #    | Status | Depends on | Task | Detail in |
|------|--------|------------|------|-----------|
| T108 | 📋 | — | **`planningStep` node type** — LLM emits a typed TODO list into a designated slot (`__plan` by convention), downstream `iteratePlan` nodes consume items one by one and mark complete. Authorable planning step, NOT always-on middleware overhead. Visible in the chat-flow graph, auditable, replayable. | §IX.2.a |
| T109 | 📋 | — | **`agent.invoke(agentId, message, opts)` Custom Tool helper** — spawns another Turing agent in an isolated conversation, awaits final answer, returns it as a tool result. Cross-agent delegation. Each invocation gets a child `conversationId` linked to parent for trace reconstruction. | §IX.2.b |
| T110 | 📋 | T109 | **Sub-agent scorecard rows** — each `agent.invoke(...)` call records a separate `TurChatSessionEvent` tagged `parentConversationId`. Scorecard supports drill-down: "orchestrator agent X invoked specialist Y in N sessions, average sub-task duration M ms." | §IX.2.c |

#### E.2 — Agent Workspace (the key differentiator)

| #    | Status | Depends on | Task | Detail in |
|------|--------|------------|------|-----------|
| T111 | 📋 | — | **`TurAgentWorkspace` service** — per-conversation key-value-blob store backed by `TurStorageService` (already pluggable: NoOp / filesystem / MinIO). API: `put(key, bytes, contentType)`, `get(key)`, `list(prefix)`, `delete(key)`, `signedUrl(key)`. Scoped path `tenants/{agentId}/{conversationId}/workspace/<key>` — reuses the multi-tenant isolation pattern from T78 verbatim. Distinct from slots: slots are flat scalars/JSON; workspace is byte-blobs with content type + signed URLs. | §IX.3.a |
| T112 | 📋 | T111 | **`workspace` helper for Custom Tool Groovy** — `workspace.put(key, value)`, `workspace.get(key)`, `workspace.list("drafts/")`, `workspace.url(key)` (returns HMAC-signed URL using the T79 signer). Tools write drafts, partial results, intermediate artifacts; downstream tools read them; LLM references them by URL in chat. | §IX.3.b |
| T113 | 📋 | T111 | **Workspace SSE bus** — `GET /chat/workspace/stream?conversationId=X` emits `{event: put/delete, key, contentType, size, signedUrl}` events. Mirrors `TurChatSlotEventBus`. SDK hook `useTuringWorkspace<T>` consumes; UI renders the active artifact list as a sidebar. | §IX.3.c |
| T114 | 📋 | T111 | **Auto-offload large tool results** — when a tool returns >`TOOL_RESULT_INLINE_MAX` chars (default 4096), `TurToolCallbackPipeline` stores the full payload in workspace at `tool-results/{toolName}-{ts}.json`, replaces inline text with `Stored at workspace://tool-results/foo-1234.json (15KB) — call workspace.read(key) for full content.` Marker tells the LLM it can re-fetch. Cuts prompt-token bloat dramatically on tools that return JSON catalogs. | §IX.3.d |
| T115 | 📋 | T111 | **Memory compression to workspace** — when persisted history exceeds `chatMemoryCompressionThresholdTokens` (default 8k), the oldest turns past the T30 recent-N window get summarized via a single dedicated LLM call → stored as `memory/summary-{from}-{to}.md` in workspace → the summary URL replaces those turns in the prompt context. Pairs with T30 BM25 relevance (relevance still surfaces the original turns when needed; summary is the default cheap path). | §IX.3.e |

#### E.3 — Skills as first-class (differentiator)

| #    | Status | Depends on | Task | Detail in |
|------|--------|------------|------|-----------|
| T116 | 📋 | — | **`TurSkill` entity** — composable bundle of `(personaFragment + flowSnippet + tools + workspaceDefaults + dependencies)`. SemVer + author + signature. Skills are persisted in a `tur_skill` table + portable JSON manifests. New agent can install skills like npm packages: `agent.install("scaffold-markdown@^1.2.0")`. The `scaffold.markdown` / `scaffold.pickQuote` / `scaffold.buildMindMap` helpers from §VII.4 become the first three platform skills. | §IX.4.a |
| T117 | 📋 | T116 | **Skills marketplace UI** in admin — browse, search, version-pin, vendor-filter, install. Same shape as a code-package manager. Internal-only at v1; opens to public registry once T118 ships. | §IX.4.b |
| T118 | 💭 | T116 | **Public skill registry** (`skills.turing.io`) — community-maintained, vendor-curated tiers, like crates.io / pypi. Authenticated signing. Skill consumption pays bandwidth + optional license fees back to authors. **This is the marketplace moat Deep Agents doesn't have.** | §IX.4.c |

#### E.4 — Human-in-the-loop (parity, made graph-visible)

| #    | Status | Depends on | Task | Detail in |
|------|--------|------------|------|-----------|
| T119 | 📋 | — | **`humanApproval` node type** — pauses the flow, sends notification to a configured channel (Slack/Email/webhook), persists a pending-approval record, returns a resume URL. Operator clicks Approve/Reject/Edit; operator decision becomes a slot value; engine advances. Distinct from generic chat — this is a graph-visible "this turn requires a human" step. | §IX.5.a |
| T120 | 💭 | T119 | **Spectator + Co-pilot mode** — operator opens `/conversation/{id}?spectate=true`, sees live chat + slots + workspace, can take the wheel by toggling `manual=true` (LLM step is skipped on that turn; operator's text becomes the assistant message but persona/guardrail still apply). Already sketched in §VIII as a moat-builder — Block E formalizes the work. | §IX.5.b |

#### E.5 — Long-horizon task primitives

| #    | Status | Depends on | Task | Detail in |
|------|--------|------------|------|-----------|
| T121 | 📋 | — | **`flow.suspend(reason)` + `POST /chat/resume/{conversationId}`** — node marks conversation as parked with a reason slot. External system (webhook callback, scheduled job, human approval) calls resume endpoint with optional slot updates. Flow continues from the suspended node. Enables long-running tasks that pause hours/days for external completion. | §IX.6.a |
| T122 | 💭 | T121 | **Parked-conversations dashboard** — admin panel listing all suspended conversations + reasons + waiting time + unblock action. Operator visibility into asynchronous workloads. | §IX.6.b |

#### E.6 — Context engineering enforcement

| #    | Status | Depends on | Task | Detail in |
|------|--------|------------|------|-----------|
| T123 | 📋 | T31 | **Token-budget enforcement per turn** — agent setting `maxPromptTokens`; engine refuses to call LLM if predicted prompt > budget. Failure mode is operator-configurable: `compact` (auto-trigger T115 summarization) / `error` (return error to caller) / `warn` (log + proceed). Pairs with cost predictor (§VIII T101 sketch). | §IX.7.a |
| T124 | 💭 | T32 | **Workspace + memory + tool-offload metrics** — extend `TurChatPipelineObservation` with `turing.chat.workspace.{puts,gets,bytes_stored}` + `turing.chat.memory.compression_ratio` + `turing.chat.tools.offloaded_bytes`. Tells the operator "compression saved 40% prompt tokens last 24h." | §IX.7.b |

#### E.7 — Developer experience (CLI + eval)

| #    | Status | Depends on | Task | Detail in |
|------|--------|------------|------|-----------|
| T125 | 📋 | — | **`turing` CLI** — `init` (scaffold new agent JSON), `dev` (spin up full local stack with hot reload), `deploy` (push agent + flows + tools + skills to remote instance), `logs` (tail chat events from remote). Built as a Node.js package shipped under `@viglet/turing-cli`. The `turing dev` subcommand uses Docker Compose to start Postgres + Solr + MinIO + the Spring Boot JAR with the file watcher pointed at the agent JSON. | §IX.8.a |
| T126 | 📋 | T125 | **`turing eval` — YAML-defined regression suite** — agent authors write `evals/onboarding-flow.eval.yaml` with N test conversations + per-turn assertions (slot values, persona invariants, tool-call expected/forbidden, response regex). `turing eval --watch` runs locally; CI runs on every PR. The Behavioral IT pattern (§VI.4.3) generalized into a customer-facing tool. | §IX.8.b |
| T127 | 💭 | T126 | **`turing eval` replay-as-test** — pull a production conversationId from the scorecard, freeze it as an eval fixture with all slot/turn snapshots. One-click "save as regression test." A production bug becomes a permanent eval line. | §IX.8.c |

#### E.8 — Observability interop

| #    | Status | Depends on | Task | Detail in |
|------|--------|------------|------|-----------|
| T128 | 💭 | T32 | **LangSmith-compatible export** — `TurChatPipelineObservation` events can be optionally mirrored to a LangSmith project via the public LangSmith API. Customers already invested in LangSmith dashboards see Turing traces without giving up Turing's auditability. Interop, not lock-out. | §IX.9.a |
| T129 | 💭 | T32 | **OpenTelemetry trace export** — wrap `STAGE_CHAT_*` timers as OTel spans; emit to any OTLP-compatible backend (Honeycomb, Datadog, Jaeger). Standard transport instead of provider-specific. | §IX.9.b |

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

### I.8 ✅ Decompose `TurAgentChatExecutor` (post-Block-B god-class) — shipped 2026.3.1

**Context (historical, kept for the next time it grows back)**: Block
B (T9-T16) collapsed the harness's pre-LLM / post-LLM split into a
single inverted path, which was the right move — but the
consolidation pushed everything into one method.
[`TurAgentChatExecutor.execute(...)`](../turing-app/src/main/java/com/viglet/turing/genai/TurAgentChatExecutor.java)
was 1074 lines and threaded 13 timing substages: decrypt → chat model
→ flow context → early advance → persona → tool callbacks → tool
context → flow addendum → persona compose → history enrichment (T30)
→ messages build → CALL/STREAM dispatch → post-LLM tone validation.

Each substage shipped one at a time and each one was reasonable in
isolation. The accumulated weight wasn't. Adding T30 + T31 required
both reading and editing this file at line ranges 280-510 — far from
each other, near other unrelated concerns. The decomposition below
killed that archeology cost.

**Shipped design** — three collaborator beans + a top-level
`TurAgentChatFlowContext` record, called in order by a slim
orchestrator method:

| Bean | Owns | Inputs | Outputs |
|---|---|---|---|
| [`TurChatToolResolver`](../turing-app/src/main/java/com/viglet/turing/genai/TurChatToolResolver.java) | Native + MCP + custom + persona-brand callbacks → pipeline decoration → T29 BM25 pre-filter. Also owns the static `forbidsToolCalls(ChatFlowNode)` (T11 declarative check) — the executor exposes a thin static façade delegating here for back-compat with `TurAgentChatExecutorStructuralIT`. Records `STAGE_CHAT_SETUP_TOOLS`. | `agent`, `activePersona`, `userMessage`, `activeNode` | `ToolCallback[]` |
| [`TurChatPromptAssembler`](../turing-app/src/main/java/com/viglet/turing/genai/TurChatPromptAssembler.java) | T31-cached flow addendum + persona prompt composition + T30 conversation-relevance history enrichment → `List<Message>` build. Records `STAGE_CHAT_SETUP_COMPOSE`. | `agent`, `history`, `conversationId`, `flowContext`, `effectivePersona`, `lastUserMessage`, `baseSystemPrompt` | `List<Message>` |
| [`TurChatStreamingDispatcher`](../turing-app/src/main/java/com/viglet/turing/genai/TurChatStreamingDispatcher.java) | CALL vs STREAM branch selection (STREAM gated on `flowContext == null` per the pre-T33 invariant), tone validation, telemetry (token usage + chat memory + analytics), suggested-options derivation + SSE fan-out (the `AssistantTurn` + `emitTurn` helpers moved here). Records `STAGE_CHAT_LLM_CALL`, `STAGE_CHAT_POST`, `STAGE_CHAT_TOTAL`, `STAGE_CHAT_ADVANCE` (always 0 post-T16), `STAGE_CHAT_REGEN` (always 0 post-T16). | `agent`, `llmInstance`, `decryptedApiKey`, `chatModel`, `messages`, `initialOptions`, `flowContext`, `effectivePersona`, `conversationId`, `lastUserMessage`, `username`, `tStart`, `tSetupEnd` | `Flux<ChatResponse>` |

After the split the 6-arg `execute()` body is 147 lines of pure
orchestration: decrypt → model → flow context (with early advance) →
persona → active node → `toolResolver.resolve(...)` →
`buildChatOptions(...)` + tool-strip decision →
`promptAssembler.assemble(...)` → `recordSessionStartSafely(...)` →
`dispatcher.dispatch(...)`. Setup-phase substages owned by the
executor (`STAGE_CHAT_SETUP_DECRYPT`, `_MODEL`, `_FLOW`, `_PERSONA`,
plus the parent `STAGE_CHAT_SETUP` aggregate) recorded directly;
TOOLS and COMPOSE recorded by their respective collaborators. The
`[AgentExec][timing] setup_ms=...` log line preserves its original
breakdown shape using wall-clock around the collaborator calls — so
the legacy human-readable trace still works.

**Risks mitigated**:
- The public `Flux<ChatResponse> execute(...)` signature stayed
  exact — `TurAIAgentChatAPI`, `TurSNGenAi`, `TurSNSiteGenAiAPI`,
  `TurLLMSemanticChatAPI` all kept passing through with zero diff.
- The `ChatMessageItem` and `ChatResponse` records, `FLOW_ID_NONE`
  constant, `resolveToolCallbacks(...)` overloads, and the static
  `forbidsToolCalls(ChatFlowNode)` all stayed on
  `TurAgentChatExecutor` — every test reference still resolves.
- The 13-step timing sequence kept the same `MeterName`s — the
  [`TurAgentChatLatencyIT`](../turing-app/src/test/java/com/viglet/turing/genai/TurAgentChatLatencyIT.java)
  contract reads `STAGE_CHAT_SETUP / LLM_CALL / POST / TOTAL` and
  all four are still emitted by exactly one collaborator each per
  turn (single-recording — no double-count).
- The `TurAgentChatExecutorMockSupport` `@Primary` overrides
  intercept `TurLlmModelFactory` at the executor injection point;
  the collaborators inherit the mocked `ChatModel` transitively
  through the executor's `chatModel` local that the dispatcher
  receives as a parameter, so no test-support changes were
  required.
- T15's audit items (cycle cap, strategy fallback, A/B window,
  agent-owner check, silent telemetry) live in
  `TurChatFlowEngineService` and the executor's `resolveForcedFlow`
  — the decomposition didn't touch any of them.

**Verification (2026.3.1)**: 121/121 impacted unit tests green;
16/16 ITs green (`HarnessFixtureSmokeIT` 3, `TestSupportSmokeIT` 1,
`TurAgentChatExecutorContractIT` 3 + 1 skipped,
`TurAgentChatExecutorStructuralIT$ForbidsToolCallsUnitTests` 8).

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

### III.2 ⏳ RAG hybrid search — BM25 re-rank over vector topK
Single-collection hybrid (T24) shipped 2026.2.7. Multi-language collection separation (T24b) pending.

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

### IV.2 ✅ Tool pre-filtering for large tool catalogs (shipped 2026.3.1 as T29)

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

### IV.3 ✅ Persona tone validation via Lucene stem matching (shipped 2026.2.7 as T22)

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

### IV.4 ✅ Conversation-relevance retrieval for chat memory (shipped 2026.3.1 as T30)

**Context**: [`TurChatMemoryService`](../turing-app/src/main/java/com/viglet/turing/service/chatmemory/TurChatMemoryService.java)
kept last N turns chronologically. For long conversations, important
context from turn 3 was dropped by turn 25.

**Shipped**: [`TurChatMemoryRelevanceRetriever`](../turing-app/src/main/java/com/viglet/turing/service/chatmemory/TurChatMemoryRelevanceRetriever.java)
sits between the call site (`TurAgentChatExecutor.execute()`) and the
prompt-message iteration. On each turn:

1. Reads the persisted history via the existing `TurChatMemoryStore`
   (no schema change — same `findMessages(conversationId, limit)`
   contract the analytics enricher already used).
2. Splits the list at `chatMemoryRecentN` (default 10) — anything
   inside the window is left for the existing FIFO injection;
   anything older becomes the BM25 candidate pool.
3. Builds a per-call `ByteBuffersDirectory` with `BM25Similarity`
   (matching the `TurToolPreFilterService` pattern — sub-millisecond
   for typical pools, no cross-request cache to invalidate).
4. Scores the latest user message via `MoreLikeThis(FIELD_CONTENT)`
   and keeps the top `chatMemoryRelevanceTopK` (default 5, hard-capped
   at 20 in code to guard against misconfig).
5. Re-sorts the retrieved set in chronological order (so the LLM sees
   the rescued turns in the order they originally happened) and
   prefixes each one with `[earlier turn #N at <ISO timestamp>]` —
   the position+timestamp marker the original sketch called for, with
   a `[earlier turn #N]` fallback when the persisted row has no
   timestamp.
6. Returns `[retrieved-with-markers] ++ history` — never modifies the
   recent window itself; the existing tail-N stays verbatim.

**Bypass conditions**: agent has `chatMemoryEnabled=false`, agent has
`chatMemoryRelevanceEnabled=false`, store reports disabled (NoOp
backend), conversationId blank, latest user message blank, persisted
history ≤ recent-N (nothing to rescue), BM25 returns zero hits. Each
short-circuits to returning the input list unchanged.

**Persistence**: three new columns on `ai_agent` via Liquibase
`v2026.3.1.3.yaml` — `chat_memory_relevance_enabled` (boolean, default
false), `chat_memory_relevance_top_k` (INT, default 5),
`chat_memory_recent_n` (INT, default 10). All three round-trip through
`TurAIAgentExchange` so export/import preserves them.

**Tests**: [`TurChatMemoryRelevanceRetrieverTest`](../turing-app/src/test/java/com/viglet/turing/service/chatmemory/TurChatMemoryRelevanceRetrieverTest.java)
— 18 unit tests pin every bypass branch, BM25 surfacing of a target
turn, chronological re-sort, top-K cap + hard-cap at 20, role
preservation on retrieved turns, store I/O failure → return original,
malformed-row skip, and the three marker formatting paths
(ISO/epoch-millis/no-timestamp).

### IV.5 ✅ System-prompt fragment caching (shipped 2026.3.1 as T31)

**Context**: each turn composed a system prompt from base + persona +
flow addendum + RAG context + few-shot examples. Each fragment is
deterministic given its inputs, but they were being recomputed every
turn — the persona block alone is typically 1-3 KB of formatted text,
and a flow node addendum carries another ~600-2000 B of `HARD_RULES`
plus the active-step header.

**Shipped — two cache surfaces** (both backed by the project's existing
Hazelcast cache manager, both wired into the same `@CacheEvict` chains
the catalog reads already use):

- **Persona block** — [`TurPersonaStaticPromptCache.composeStaticBlock(persona)`](../turing-app/src/main/java/com/viglet/turing/genai/persona/TurPersonaStaticPromptCache.java)
  is `@Cacheable("turPersonaStaticPrompt", key="#persona.id")`. The
  block covers `systemInstruction` + style guidelines + required /
  forbidden vocabulary + brand-context placeholder. The few-shot
  examples are appended by [`TurPersonaPromptComposer`](../turing-app/src/main/java/com/viglet/turing/genai/persona/TurPersonaPromptComposer.java)
  AFTER the cached call — they depend on `userQuery` (vector store
  retrieval) and stay uncached. The agent base prompt (which may carry
  per-turn RAG context) is also concatenated after.

- **Flow addendum** — [`TurChatFlowStaticPromptCache`](../turing-app/src/main/java/com/viglet/turing/genai/flow/TurChatFlowStaticPromptCache.java)
  splits the addendum into three pieces: a static **head**
  (`@Cacheable("turChatFlowStaticAddendumHead", key="#flowId+':'+#nodeId")`
  — active-step header, Goal, Collect, Validate), a per-turn
  **variables line** (built inline, never cached — depends on
  `state.variablesJson` which mutates as slots fill), and a static
  **tail** (`@Cacheable("turChatFlowStaticAddendumTail",
  key="#flowId+':'+#nodeId+':'+#includeNextStepHint")` — next-step
  preview hint + `HARD_RULES`). Splitting around the dynamic line
  preserves the exact prompt structure the LLM was already seeing
  while keeping the cache hit rate near 100% on the static portions
  — keying on `hash(variablesJson)` would have collapsed the hit rate
  every turn a slot was captured.

**Why the suggested `(personaId, agentId)` key didn't hold**: the
persona composer also takes a `userQuery` argument that drives few-shot
retrieval. Including `userQuery` in the key would collapse the hit
rate; folding the few-shot block OUT of the cached surface (it's
already a separate vector-store lookup, with its own cost profile)
keeps the deterministic block reusable across turns.

**Why the suggested `(flowId, currentNodeId)` key didn't hold either**:
`state.getVariablesJson()` mutates each time a slot is collected, and
the addendum embeds an `Already collected: {...}` line in the middle
of the static text. Splitting the addendum around that line — head
above, tail below — turned the same "key on `(flowId, nodeId)`" plan
into a correct one: now each cached fragment is genuinely a function of
its key.

**Eviction**: the existing `@CacheEvict` blocks on
[`TurPersonaRepository.save/delete`](../turing-app/src/main/java/com/viglet/turing/persistence/repository/persona/TurPersonaRepository.java)
gained `turPersonaStaticPrompt`;
[`TurChatFlowRepository.save/delete`](../turing-app/src/main/java/com/viglet/turing/persistence/repository/agent/TurChatFlowRepository.java)
gained `turChatFlowStaticAddendumHead` + `turChatFlowStaticAddendumTail`
alongside the existing `turChatFlowRouterDecision` eviction. Operator
edits to persona instruction / tone / vocab — or to a flow node's
label / aiInstruction / outputVariable / validationRule / edges —
propagate on the next chat turn without restart, same as every other
cached catalog read in the project.

**Tests**: [`TurPersonaStaticPromptCacheTest`](../turing-app/src/test/java/com/viglet/turing/genai/persona/TurPersonaStaticPromptCacheTest.java)
(12) + [`TurChatFlowStaticPromptCacheTest`](../turing-app/src/test/java/com/viglet/turing/genai/flow/TurChatFlowStaticPromptCacheTest.java)
(12). Spring-context smoke ITs (`HarnessFixtureSmokeIT` 3/3,
`TestSupportSmokeIT` 1/1) plus impacted strategy tests
(`LlmJudgeGuardrailStrategyTest`, `ChatFlowOpsTest`,
`ChatFlowOpsBrDocUnitTest`) all green.

### IV.6 ✅ Slot value fuzzy matching (shipped 2026.2.7 as T23)

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

### IV.7 ✅ Observability for the 2026.3.1 cache + retrieval features (shipped 2026.3.1 as T32)

**Context**: T28 (intent classifier), T29 (tool pre-filter), T30
(relevance retrieval) and T31 (prompt cache) all claimed per-turn
performance wins — "shaves 10-30 ms", "skips ~50 tool descriptions",
"prepends top-K older turns" — but the project had **zero
instrumentation** on any of them. The only existing chat-pipeline
observability was
[`TurChatPipelineObservation`](../turing-app/src/main/java/com/viglet/turing/observability/TurChatPipelineObservation.java)
aggregating `setup_ms` / `llm_ms` / `post_ms` per turn — coarse,
silent on cache outcomes. Any claim about whether the caches were
actually winning was faith-based.

**Shipped — three classes of metric**:

1. **Cache hit-rate gauges** ([`TurChatCacheStatsRegistrar`](../turing-app/src/main/java/com/viglet/turing/observability/TurChatCacheStatsRegistrar.java))
   pulled from Hazelcast `IMap.getLocalMapStats()` for every
   `@Cacheable` the chat pipeline reads — 9 caches total covering
   catalog reads (agent, flow, persona, analytics intent), the T26
   router-decision cache, and T31's three prompt-fragment caches.
   Surfaced as `turing.chat.cache.{hits,misses,size}` gauges tagged
   by `cache`. Misses are derived as `getOperationCount − getHits`
   because the Hazelcast 5.x `LocalMapStats` interface doesn't expose
   a direct miss counter; clamped at 0 so a stat reset can't surface a
   negative value. Bean degrades silently when no `HazelcastInstance`
   is present (dev mode falls back to `ConcurrentMapCacheManager` —
   logging an INFO is more honest than registering flat-zero gauges).

2. **Per-turn retrieval timings** — `TurChatPipelineObservation`
   gained `recordMemoryRetrieval(poolSize, hits, ms, outcome)` and
   `recordToolPrefilter(in, out, ms, reason)`. T30 wires every branch:
   5 bypass paths emit a fast timer sample tagged
   `outcome=bypass` (no pool/hits distribution to avoid
   zero-pollution); BM25 zero-match emits `no-hits` (the cost was
   paid, signal value just was nil); successful retrieval emits
   `hits` along with the pool size + hits count distributions. T29
   wires its 6 reasons (`disabled`, `below-threshold`,
   `blank-query`, `already-covered`, `cap-covers-pool`, `engaged`) on
   the bypass counter, with the timer outcome split into `filtered`
   vs `passthrough`.

3. **Sub-stage timing breakdown** — the 6 existing timing locals in
   `TurAgentChatExecutor.execute()` (decrypt / model / flow /
   persona / tools / compose) now push to
   `TurChatPipelineObservation` as separate `stage` tag values on the
   `turing.chat.pipeline` timer. The parent `chat.setup` aggregate
   stays so existing dashboards keep working; new dashboards can drill
   in to tell apart "everything cached ~100ms" from "cold-start MCP
   handshake / first-time flow load".

**Tag cardinality discipline**: every new tag is from a bounded enum
(cache name from the static catalog, T29 reason from 6 values, T30
outcome from 3) — no `agentId` / `flowId` / `nodeId` tags that would
explode the Prometheus storage. Per-feature aggregates only.

**Tests**: [`TurChatPipelineObservationT32Test`](../turing-app/src/test/java/com/viglet/turing/observability/TurChatPipelineObservationT32Test.java)
(7) covers all hit/no-hits/bypass + reason combinations, distribution
skipping on bypass, and counter independence across all 6 reasons.
[`TurChatCacheStatsRegistrarTest`](../turing-app/src/test/java/com/viglet/turing/observability/TurChatCacheStatsRegistrarTest.java)
(5) pins gauge registration shape, miss-clamping at 0, exception
swallowing on the supplier (so a Hazelcast outage can't crash the
Prometheus scrape), no-Hazelcast mode skipping registration, and the
cache-catalog inventory (any new cache added to the chat pipeline
should also be added to the registrar list — the test fails when the
catalog drifts).

**Exposure**: metrics flow through the existing `/actuator/prometheus`
endpoint — no new dependency, no Hazelcast Management Center required.

### IV.8 📋 Codify the caching conventions (docs + lint test)

**Context**: shipping T31 required me to re-derive two conventions
that already exist tacitly in this codebase but aren't in any
canonical place:

1. **`@Cacheable` is proxy-based**: a `this.cacheableMethod()` call
   from the same class bypasses the Spring proxy and silently no-ops
   the cache. T31's `TurChatFlowStaticPromptCache` solves this with
   `@Lazy` self-injection; future contributors will hit the same wall.
2. **Every `@Cacheable` query needs a corresponding `@CacheEvict`** in
   the repository's save/delete methods — otherwise stale catalog
   data is served indefinitely. The convention is enforced by
   convention (i.e. memory) today; one missed eviction = a class of
   bug that's already in our memory log
   ([`feedback_sn_repository_cache.md`](../../../C:/Users/alexa/.claude/projects/d--Git-viglet-turing-2026-1/memory/)).

**Idea**: pay it forward with two artifacts:

- **`agents.md` block** — short subsection ("Adding a @Cacheable
  query") covering: where the eviction goes, the self-injection rule,
  the lowercase-cache-name convention, and a pointer to the
  `TurChatFlowStaticPromptCache` self-injection example.

- **`TurCacheAnnotationConventionsTest`** — static-analysis unit test
  (no Spring context, just a class-loader walk over the compiled
  `target/classes`) that:
  - lists every `@Cacheable` method's cache name in the project;
  - lists every cache name mentioned in any `@CacheEvict`;
  - fails when a `@Cacheable` name is never evicted anywhere
    (other than caches explicitly tagged with an annotation like
    `@TurEvictExempt` for read-only stuff).

  Mirrors the existing `TurLiquibaseChangelogAgnosticTest` which
  greps the changelog tree for forbidden tokens.

**Risk**: low. The lint test runs in seconds, no flake surface (pure
classpath scan).

**When**: any time. Especially valuable before the project takes on
more contributors / agents — every new `@Cacheable` shipped without
the right eviction is a future incident.

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

---

## VII. Education customer engagement — platform synthesis

> Consolidation of the platform-level discoveries from the Education
> customer × Turing engagement. The customer repo's `docs/IMPROVEMENTS.md`
> is exhaustive on the *customer side* (portal, 3 chat-flows, 4 Custom
> Tools, 17 slots, Storybook stories). This section keeps only the
> **Turing platform** projection: what shipped *here*, what should ship
> *here*, and which lessons survive customer rotation.

### VII.1 What shipped on the Turing platform during the engagement

These were built or hardened inside `turing-app` while solving an
Education customer need; they are platform citizens now and should not
be re-derived by the next customer. T-numbered entries are tracked in
§0 Block D for the audit trail.

#### VII.1.a Custom Tool helper backbone (T35)
[`TurCustomToolHttpHelper`](../turing-app/src/main/java/com/viglet/turing/genai/tool/TurCustomToolHttpHelper.java)
(`http.postJson` / `http.getJson`),
[`TurCustomToolSearchHelper`](../turing-app/src/main/java/com/viglet/turing/genai/tool/TurCustomToolSearchHelper.java)
(`turingSearch.ann` with collapse-by-document dedup),
[`TurCustomToolSlotHelper`](../turing-app/src/main/java/com/viglet/turing/genai/tool/TurCustomToolSlotHelper.java)
(`slots.set` / `slots.get` / `slots.all`, also publishes to the SSE bus).
`ToolContext` propagates `conversationId` end-to-end so helpers resolve
the right state row without re-fetching by user message.

#### VII.1.b `code.executePython(...)` for Custom Tools (T36)
[`TurCustomToolCodeHelper`](../turing-app/src/main/java/com/viglet/turing/genai/tool/TurCustomToolCodeHelper.java)
wraps `TurCodeInterpreterToolService.executePython(...)` as a Groovy
binding. Unlocks the **deterministic-compose + sandbox-execute pattern**:
Groovy composes a Python script from slots (zero LLM in the critical
path), the Python sandbox renders the artifact, the URL is parsed from
the markdown response and written back to a slot. Auditable, reproducible,
not subject to LLM stochasticity. Proven in the Education engagement
for PDF generation (reportlab 4-page proposal in ~3s) but applicable to
any heavy-render output (charts, CSV transforms, vector graphics).

#### VII.1.c Hot-reload + per-tool analytics (T37)
`POST /api/custom-tool/{id}/execute` runs the Groovy script with mock
args from admin without restart. `TurChatLoggingToolCallback` records
per-invocation latency + success; flushed in `recordSessionEnd` as
`totalToolCalls` / `toolErrors` / `totalToolLatencyMs` on the session
event so the scorecard can answer "which tool is the p95 outlier?"

#### VII.1.d `turingSearch.sn(...)` in-process (T38)
Calls `TurSNSearchProcess.search` directly (no HTTP self-call). Aliases
`query`/`q`/`rows`/`page`/`sort`/`fq[*]`/`fl`/`templateName`. Return
shape mirrors `ann()` so Custom Tools can swap between vector and
classic search without changing parsing. Five tests cover flatten,
id-alias, null fields, reserved-key precedence.

#### VII.3.a `writeSlot` node type (T42)
Server-side `{{variable}}` interpolation via
`ChatFlowOps.applyWriteSlotNode`. Consumers read resolved values; the
template never leaks to the client. Editor palette item with
`IconPencilPlus` (teal).

#### VII.3.b `formCapture` node type with CPF/CNPJ checksum (T43, T44)
Like `aiQuestion` but stricter — regex validation against the named
pattern (CPF, CNPJ, email, phone, CEP). Mod-11 checksum for CPF / CNPJ.
Editor renders with `IconForms` (indigo) + rule-name badge. The
Education customer's B2B flow uses this for `razao_social` capture and
the lead-capture branches.

#### VII.5.a SSE slot bus + 25s heartbeat (T54)
[`TurChatSlotEventBus`](../turing-app/src/main/java/com/viglet/turing/service/chatslots/TurChatSlotEventBus.java)
in-memory bus + `GET /chat/slots/stream` (`Flux<TurChatSessionSlotsDto>`).
Three write paths instrumented: slot node, `slots.set`, `POST /chat/slots`
endpoint. Initial snapshot + delta events. 25s comment-only heartbeat
keeps the channel alive through corporate proxies. Latency slot→UI
collapsed from ~1.5s (polling) to <100ms.

#### VII.5.b Multi-channel handoff (T55)
[`TurChatHandoffService`](../turing-app/src/main/java/com/viglet/turing/service/chatslots/TurChatHandoffService.java)
exposes 5 channels: WhatsApp / Email / SMS / Telegram / Slack. Slot
whitelist for transcript composition + custom intro; tracking via
`handoff_status` slot for analytics segmentation. Composed deep link
returned for `window.open` / `<a href>`; SDK hook `useTuringHandoff`
wraps the composition.

#### VII.5.c OG share endpoint (T56)
`GET /chat/share/og` renders a server-side Open Graph card. Caller
passes slot values as query params (`?name=…&area=…&dest=…`); scrapers
(WhatsApp / Slack / LinkedIn / X / Discord) parse `og:*` + `twitter:*`
tags; humans get a meta-refresh redirect to the `dest` URL. Personalized
preview drives viral B2C compared to generic landing-page preview.

#### VII.5.d `GET /chat/state` (T57)
DTO with `flowId` / `flowName` / `currentNodeId` / `guardrailMethod` /
`experimentKey` / `variantLabel` (all-null when no flow active).
Powers `useTuringFlowState` + `useTuringExperiment` SDK hooks for
debug surfaces and variant-aware UI without polluting slots.

#### VII.5.e `POST /chat/slot-extract` (T58)
Multipart file → Tika → LLM structured output → `writeSlot` per field.
Same `TurFileUtils.documentToText()` as RAG indexing (no duplication).
Tolerant JSON parser absorbs code-fence wrapping and field drift via
whitelist. Generic: caller passes slot names + file; same path serves
CV, invoice, form, contract.

#### VII.5.f `TurLlmSummaryService` (T59)
`generate(cacheKey, data, systemPrompt, regenerate) → SummaryResult`.
In-memory cache via `TurLlmCacheService` (TTL from Global Settings)
respecting `llmCacheRegenerate` toggle. Used by SN Site Insights
(`GET /api/sn/{id}/summary`) and System Info Insights
(`GET /api/system/info/insights`).

#### VII.7.a A/B testing native (T67)
5 columns on `TurChatFlow` (`experimentKey`, `variantLabel`,
`trafficWeight`, `experimentStartsAt`, `experimentEndsAt`) +
`assignVariant` deterministic hash (`Math.floorMod` for negative-hash
safety) + sticky continuation via state row + `isInExperimentWindow`
scheduling filter. Admin card in `chat-flow.settings-tab.tsx`.
13 unit tests pin distribution / stickiness / ratio normalize /
zero-weight exclude / scheduling boundaries.

#### VII.7.b A/B significance endpoint (T68)
`/experiment/{key}/significance` runs two-proportion z-test with Wald CI.
Tells the operator when to retire a variant with statistical justification.

#### VII.7.c A/B scorecard (T69)
`experimentKey` + `variantLabel` persisted on `TurChatSessionEvent` +
Mongo + Redis. Scorecard supports 4 dimensions (agent / persona /
experiment / variant). Education-customer exemplar:
`programa-match-persona-2026q1` — control persona (authority voice) vs
challenger persona (peer voice) with 50/50 split.

#### VII.9 Code Interpreter hardening (T76–T79 shipped)
| Layer | Vector closed | Status |
|---|---|---|
| Date-bucketed sessions + cleanup task | Disk leak (~50MB/day) | ✅ T76 |
| Python deps auto-install (global ∪ per-agent) | Operator handoff of `pip install` between envs | ✅ T77 |
| Multi-tenant path isolation (`tenants/{agentId}/{convId}/...`) | Cross-tenant accidental cleanup; per-agent search scoping | ✅ T78 |
| HMAC-signed URLs + cookie-binding | URL guessing / cross-browser leakage | ✅ T79 |
| Container sandbox (Docker / gVisor / Firecracker) | Runtime escape — malicious Python reading `/etc/passwd` or cross-tenant files | ❌ T80 — see VII.2.h |

#### VII.10.a Chat analytics (T84)
9 timeseries metrics + 4 scorecard dimensions exposed at
`/api/chat-analytics`. Frontend dashboard with `TimeseriesPanel` (line
chart) + `ScorecardPanel` (dimension dropdown). Mongo + Redis stores.

#### VII.13.a SDK hooks (T104)
`@viglet/turing-react-sdk` 2026.2.21 → .29 shipped 10 hooks:
`useTuringSlotWriter` · `useTuringSlotExtract` · `useTuringHandoff` ·
SSE transport in `useTuringSlots` · `useTuringVoice` ·
`useTuringFlowState` · `useTuringExperiment` · `useTuringSlot<T>`
generic with `parse: "auto" | "json" | "raw" | (raw) => T` ·
**SSE multiplexer** (N→1 EventSource via refcount module-level +
late-subscriber snapshot replay through `queueMicrotask`) ·
Storybook 100% coverage across the 10 hooks.

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

#### VII.2.b Groovy editor with auto-complete (T40)
Descriptor JSON shipped to Monaco listing helpers + signature hints +
return shapes. Today scripts are a textarea; typo on `slots.set`
discovered at runtime. Lowers the bar for non-dev authors.

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

#### VII.2.f Platform-level judge few-shot (T52)
Bake 2-3 positive examples (compound `cargo+tempo`, informal `objetivo`)
+ 1 negative (chip-routed-to-wrong-node) into the **platform-level**
judge system prompt, not into per-customer `aiInstruction` strings.
Reduces stochastic rejects across all flows without each customer
re-discovering. `gpt-4o-mini` improves dramatically with few-shot.

#### VII.2.g Slot audit log + PII prefix (T60, T61)
- **Audit log**: `chat_slot_write_log` (conversationId, slot, oldValue,
  newValue, source ∈ {`node`, `tool`, `endpoint`, `extract`}, ts). LGPD
  audit + debug forensics. SlotInspector renders as timeline with source
  badge.
- **PII prefix `pii_*`**: engine redacts in logs/analytics, encrypts at
  rest, TTL configurable (default 24h). Compliance pitch for regulated
  customers (banking, healthcare, B2C with strong privacy reqs).

#### VII.2.h Code Interpreter container sandbox (T80) ⭐
The single largest remaining gap in the Code Interpreter threat model.
Today a malicious Python script runs as the JVM's OS user and can
`open("../../../etc/passwd")` or read any file the JVM can. Path
isolation (T78) + URL hardening (T79) closed the HTTP / operational
vectors but **not** the runtime escape. Without containerization, any
SaaS pitch with customer-authored Custom Tools is at risk of
cross-tenant exfiltration. Estimated ~2 weeks: base Docker image with
`python+reportlab+matplotlib+qrcode` pre-installed, bind-mount only the
session dir, drop capabilities, seccomp filter. gVisor optional for
defense in depth. **Gate item — this opens Turing Cloud (see §VIII).**

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

> The Education customer engagement proved the product. This section is
> about **positioning** — what to start, what to deprecate, where the
> moat deepens. Not a roadmap; a strategy memo. Items here are scoped at
> *months*, not *PRs*. Tactical entries live in §0 Block D and §IV.

### VIII.1 The pricing-tiers question — what to charge for

Today everything is bundled. Three observable buyer profiles:

| Profile | Buys | Pricing pressure |
|---|---|---|
| **Self-host enterprise** (education, banking) | Full source + on-prem deploy + support contract | Custom enterprise license — high willingness-to-pay, low volume |
| **Mid-market SaaS** (would-be enterprise customers if smaller) | Hosted Turing without operating Spring Boot + Solr + JPA + Code Interpreter | Usage-based $0.01-0.05/conv; doesn't exist today, full mid-market lost to OpenAI Assistants |
| **Code Interpreter only** (devs who want Python sandbox + URL signing) | Standalone Modal / E2B competitor | Pure usage-based; the *sandbox hardening* (T76–T80) is differentiator |

**The strategic bet**: **separate Turing into 3 SKUs** with independent
pricing and marketing:

1. **Turing Search** — TurSN heritage (Solr + facets + ANN). Open
   source + paid support. Mature, slow-growth, paying for itself.
2. **Turing Agents** — chat-flow engine + Custom Tools + LLM-judge +
   analytics + admin. Open source (self-host) + cloud usage-based.
   Where the engagement work concentrates.
3. **Turing Sandbox** — Code Interpreter multi-tenant standalone, after
   T80 lands. Direct Modal / E2B competitor. Pure SaaS usage-based.

Each has a different buyer, different pricing, different lifecycle.
Bundling them dilutes the message — "is Turing a search engine or an
agent platform or a code sandbox?" today gets a yes on all three and a
buying decision on none.

### VIII.2 The Cloud bet — paralleling open source

**Tese**: SMB and mid-market will not operate Spring Boot + Solr +
Code Interpreter + Liquibase + multi-LLM config. Today 100% of that
segment goes to OpenAI Assistants / Copilot Studio. Self-host stays
free + open; cloud charges per use. Supabase / Posthog / Sentry pattern.

- **MVP shape**: signup → wizard creates agent → picks flow template
  ("Educational Lead Capture" / "B2B Pricing Quote" / "Knowledge Q&A")
  → WhatsApp integration → live in 10 min.
- **Stack**: managed containers (Fly.io / Cloud Run / Modal) — do not
  reinvent K8s.
- **Pricing**: $0.01/conv with 1k free; Code Interpreter $0.05/min
  additional; premium support in tiers.
- **Risks**: distracts from OSS; operations cost real money; competes
  with potential integrator partners.
- **Why it matters**: SaaS valuation multiple is 8-15× revenue vs open
  source 0-3×. Even if Cloud stays a small slice, it professionalizes
  the product (because customers complain) and unlocks the multi-tenant
  pitch that T80 will support.

### VIII.3 Verticalize in 2-3 markets

**Tese**: today Turing is "agent platform for anything." Wins no
category. Sierra ($4.5B) is vertical customer-service; Decagon idem;
Hippocratic AI is vertical healthcare. **Horizontal-positioned losers**:
LangChain (commodity), Botpress (struggling), Copilot Studio
(Microsoft-only). Verticalize on **Education + Financial Services**
where the engagement opened doors:

- Pre-built personas tone-calibrated per vertical
- Pre-built Custom Tools (DSpace adapter for academia, Bloomberg / Quandl
  for finance, FHIR for healthcare if added)
- Pre-built flow templates (Lead Capture EE, Mortgage Pre-Qualification,
  Patient Intake)
- Compliance defaults (LGPD aggressive, PII redaction by default,
  SOC2 audit trail)
- Public case studies per vertical (Education customer as anchor, plus
  1 bank + 1 hospital network)

Vertical is where 2026 venture dollars sit. Horizontal is where they
left.

### VIII.4 The MCP-first transition

**Tese**: Model Context Protocol is becoming the de facto standard for
agent tools (Anthropic, Cursor, Claude Desktop, Glama, OpenTools).
**Custom Tools Groovy is Turing-only lock-in**. Move Custom Tools to be
**MCP servers by default**, with Groovy demoted to legacy mode.

- **Ganho 1**: Custom Tools written for Turing become portable to any
  MCP client.
- **Ganho 2**: the MCP marketplace ecosystem becomes available *to*
  Turing customers without writing new code (third-party tools listed
  in the admin gallery).
- **Trade-off**: existing Groovy customers need a migration path.
- **Sequencing**: ship a one-shot Groovy → MCP exporter, then make MCP
  the default for new tools while keeping Groovy on the docs as
  "advanced" for power users.

### VIII.5 The Spring AI question — fork or live with the lag

**Tese**: Spring AI is the abstraction layer between Turing and
underlying LLMs. **Every new feature ships in Spring AI weeks or months
after the upstream SDKs.** Spring AI 2.0.0-M6's broken streaming (T17,
T18) was a concrete example.

**Two options**:
- (A) **Keep Spring AI**, ship workarounds (T18: hand-rolled WebClient
  for OpenAI streaming) as needed. Maintenance keeps growing.
- (B) **Hybrid: direct SDKs for fast-moving features, Spring AI for
  long-tail providers**. `turing-llm` thin abstraction layer routes by
  provider type. Higher initial cost (~4-6 weeks senior dev); buys
  feature parity at SDK release day.

Recommend (B) before customer commitments require day-1 feature parity
(vision, code-execution tools, prompt caching v2, etc.). The engagement
already paid the price for this on T18.

### VIII.6 What to deprecate (loud, with deadlines)

Cutting saves ciclo. Hesitation costs forever-maintenance.

| Feature | Why deprecate | Sunset window |
|---|---|---|
| `HeuristicGuardrailStrategy` as default for new flows | Regex-only; LLM_JUDGE + tool-calling do better. Third option pollutes the UI (TK1). | 6 months — flag-flip default; remove enum entry 12 months |
| Slot types `INTEGER` / `BOOLEAN` (TK2) | `useTuringSlot<number>` does coercion correctly; schema types only generate bugs. | 12 months — silently migrate to `string`, remove enum 18 months |
| `functionCall` node type (TK4) | Vapor — engine doesn't execute it. Either ship T46 or kill the type. | Decision in 1 sprint |
| Brevo integration (TK5) | Modern alternatives (Resend, Postmark) have better deliverability + simpler API. | Replace within 6 months |
| Custom Solr admin UI (TK6) | TurSN heritage; Solr has native admin. | Audit usage, kill if zero customers depend |
| `forbidsToolCalls(String)` substring overload (TK3) | T16 left only node-aware path; finish the job. | 1 sprint |

### VIII.7 Sequencing — what to start this quarter

**Convicted choices, not exhaustive list**:

1. ⭐ **T80 — Container sandbox for Code Interpreter** (~2 weeks). The
   one feature that gates multi-tenant SaaS and the Turing Sandbox SKU.
   No clean shortcut exists.
2. **T60 + T61 — Slot audit log + PII prefix** (~2 weeks). Unlocks the
   compliance pitch for regulated verticals. Required before any bank /
   hospital conversation.
3. **T94 — Chat-flow lint** (~1 week). Drives perceived editor
   maturity; first thing CTOs see in a demo.
4. **T70 — Multi-armed bandit for A/B** (~1 week). Replaces fixed-split
   pain we already feel internally.
5. **T46 OR TK4 — `functionCall` decision** (~1-3 days for decision +
   1-2 weeks if implemented). Vapor cleanup. Either ship it or kill it.

**Defer**: T80 cascades unblock T81 (resource limits) and T82
(pre-warmed pool). Most Block D items wait on a customer to ask for them
twice before scheduling.

### VIII.8 The pergunta hard

**Today's positioning** (~35 words; buyer zones out at the first comma):
> "Open source platform for search + AI agents with chat-flows and
> Custom Tools running on Spring Boot, supporting A/B testing and
> multi-tenant Code Interpreter."

**12 months from now, if VII.2.h + VIII.1 + VIII.3 ship** (~28 words;
buyer knows in 5s if it's for them):
> "Turing is the open-source conversational AI platform for Education
> and Financial Services — self-host or cloud, end-to-end auditable,
> with the best LLM-as-judge in the market."

Difference is not features. It is **focus, clarity, and the courage to
deprecate what does not pay**.

---

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

#### IX.3.d Auto-offload large tool results (T114)

When `TurToolCallbackPipeline.decorate(...)` wraps a `ToolCallback`,
the wrapper intercepts the result. If the returned string > 4096 chars,
the pipeline:
1. Generates a key `tool-results/{toolName}-{timestamp}.json`
2. Writes the full payload to `workspace.put(key, ...)`
3. Replaces the returned content with:
   `Stored at workspace://${key} (${size} KB). Call workspace.read(key) when needed.`

The LLM sees the reference; if it needs the data, it calls a
`workspace.read` tool (new platform-level callback, always present).
Cuts prompt-token bloat dramatically on tools that return large
catalogs / search results / JSON arrays — addresses the same problem
Deep Agents' "offload large tool results" middleware solves, but with
**audit trail** (workspace stores the artifact; T60 audit log tracks
the write).

#### IX.3.e Memory compression to workspace (T115)

When the persisted history for a conversation exceeds
`chatMemoryCompressionThresholdTokens` (default 8k tokens), the oldest
turns past the T30 recent-N window get summarized via a dedicated LLM
call → stored as `memory/summary-{from}-{to}.md` in workspace → the
summary URL replaces those turns in the prompt context. **Composes
with T30 relevance retrieval**: relevance still surfaces the original
turns when the BM25 score matches a current query (high-signal
recovery); summary is the cheap default for low-signal context.

```yaml
# Per-agent settings (Liquibase migration in T115):
chatMemoryCompressionEnabled: true
chatMemoryCompressionThresholdTokens: 8192
chatMemoryCompressionModel: gpt-4o-mini    # explicitly cheap; summary doesn't need flagship model
chatMemoryCompressionInterval: PT1H        # don't re-compress more than once per hour
```

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
    "instruction": "When asked to compose narrative content, structure as beats with timestamps...",
    "vocab": { "mandatory": ["beat"], "forbidden": [] }
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
**distributable, versioned package format with vendor signatures**.
Once `skills.turing.io` (T118) lives, the network effect is one-way —
authors publish there because customers are there; customers go there
because skills are there. LangChain has community packages but no
deterministic catalog with vendor signing.

### IX.5 Human-in-the-loop — make it a graph node, not magic

Deep Agents has HITL as middleware (the harness pauses for you when
appropriate). **Turing's better move**: `humanApproval` is a node
type, visible in the editor, observable in the analytics, auditable
in the slot audit log.

```jsonc
{
  "type": "humanApproval",
  "data": {
    "notificationChannel": "slack",
    "notificationTarget": "#sales-approvals",
    "notificationTemplate": "Lead {{name}} from {{company}} ready to convert. Review: {{transcript_url}}",
    "approvalSlot": "operator_decision",       // 'approve' | 'reject' | 'edit:<text>'
    "timeoutSeconds": 3600,
    "timeoutBehavior": "auto_reject"
  }
}
```

This is **graph-visible** — every flow's audit trail shows where humans
intervened. Spectator + Co-pilot mode (T120) layers in for live
co-driving.

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

### IX.7 Context engineering — enforceable, not advisory

Deep Agents says it compresses / offloads / isolates / caches. All four
are advisory ("the framework helps"). Turing's competitive move:
**enforceable token budgets** (T123).

```yaml
# Per-agent setting:
maxPromptTokens: 16000
overBudgetBehavior: compact     # auto-trigger T115 summarization
# alternatives: error | warn
```

If the predicted prompt exceeds budget, engine routes through
compression *before* calling LLM. If compression doesn't bring it
under, the configured failure mode kicks in. **Cost is a first-class
constraint, not a metric you discover after the fact.**

### IX.8 Developer experience — CLI + eval as table stakes

#### IX.8.a `turing` CLI (T125)

Deep Agents has a CLI. Turing's CLI must be **strictly better** because
it spans the whole stack:

```bash
$ turing init my-agent              # scaffolds agent JSON + flow + tools dir
$ cd my-agent
$ turing dev                        # docker-compose up: pg + solr + minio + jar
                                    # file watcher → hot reload of flow + tools
$ turing eval                       # run YAML eval suite
$ turing deploy --env=staging       # push agent + flows + tools + skills
$ turing logs --tail                # stream chat events from remote
```

The differentiator: `turing dev` boots the **full Turing stack** locally
in one command. LangChain's CLI is just the SDK harness — you still
need your own infrastructure. Turing's CLI is closer to Supabase's CLI
(supabase + auth + postgres + storage in one) than to LangChain's.

#### IX.8.b `turing eval` (T126)

YAML-defined regression suite. Generalizes the BehavioralIT pattern
(§VI.4.3) into a customer-facing tool:

```yaml
# evals/lead-capture.eval.yaml
fixtures:
  - id: happy-path-cfo
    conversation:
      - user: "Quero saber dos seus programas"
      - assert.assistant.matches: ".*cargo.*"      # expected to ask cargo
      - user: "Sou CFO numa fintech"
      - assert.slot.cargo_atual: "CFO"             # judge captured it
      - assert.tool_called: search_ee_programs     # tool fired
      - assert.persona_invariants:                 # tone validator passed
          forbidden: ["talvez", "não posso"]
  - id: rejection-path-junior
    ...
```

Customers run `turing eval --watch` locally; CI runs on every PR;
production conversations get one-click "save as eval fixture" (T127).
LangSmith has eval but provider-locked; Turing's eval works across all
6 LLM providers because Spring AI abstracts them.

### IX.9 Observability interop — don't fight LangSmith, federate

Customers who already invested in LangSmith dashboards / OpenTelemetry
backends should not have to abandon them to adopt Turing.

- **T128 LangSmith mirror**: optional `turing.observability.langsmith.enabled=true` + API key → every chat-pipeline event also pushed to LangSmith via their public ingest API. Customers see Turing traces in LangSmith side-by-side with their LangChain agents.
- **T129 OTel export**: wrap `STAGE_CHAT_*` timers as OTel spans; emit to any OTLP backend. Standard transport, no provider lock-in.

**Strategic principle**: lock customers in via *capability*, never via
*format*. The Turing scorecard is irreplaceable; the trace export
shouldn't be.

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
harness). T118 public skills registry (`skills.turing.io`) is a
separate product launch, not a feature.

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
