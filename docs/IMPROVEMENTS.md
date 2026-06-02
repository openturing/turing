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
- [X. Native LLM SDK exploitation — beyond Spring AI 2.0](#x-native-llm-sdk-exploitation--beyond-spring-ai-20)
- [XI. Embedded LLM via Jlama — pure-Java in-process inference](#xi-embedded-llm-via-jlama--pure-java-in-process-inference)
- [XII. Admin chat ↔ `@viglet/turing-react-sdk` convergence](#xii-admin-chat--vigletturing-react-sdk-convergence)

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
> - **Block D** — Education customer engagement promotions (T35–T107).
>   T35–T59, T67–T69, T76–T79, T84, T91–T97, T104 all ✅ (see §VII).
>   Remaining T-codes are 📋/💭 backlog of platform-level features
>   distilled from the customer engagement.
> - **Block E** — Deep Agents competitive parity (T108–T129) — mostly
>   📋/💭. **T109 (`agent.invoke(...)` Custom Tool helper) ✅ shipped
>   2026-05-26** — cross-agent delegation surface for Groovy scripts;
>   recursion-capped at depth 3 via a new `TOOL_CONTEXT_AGENT_INVOKE_DEPTH`
>   tool-context key + 8-arg executor overload, parent conversationId
>   linkage carried inline on the child's first user message.
>   **T110 (Sub-agent scorecard) ✅ shipped 2026-05-26** — backend
>   data capture + UI dimension picker. Child sessions record a
>   structured `parentConversationId` column on `TurChatSessionEvent`
>   (Mongo + Redis); the `[__parentConversationId=…]` prefix T109
>   writes is parsed + stripped server-side via new
>   `extractParentConversationId` / `stripParentConversationIdPrefix`
>   helpers; `parentConversationId` is a valid scorecard group-by
>   dimension on both store impls + on the admin
>   `chat-analytics.page.tsx` `DIMENSION_OPTIONS` picker (with
>   parent-conv-specific description + empty-state copy). Session
>   model carries the new field for future detail-pane surfacing.
>   What remains: session-detail "spawned by orchestrator" chip +
>   child-list panel on parent session view — deferred to a polish
>   pass, doesn't gate any Block E work. Workspace primitive
>   (T111–T115) still gates the rest of Block E. See §IX.
> - **Block F** — **NEW (2026-05-25)**: native LLM SDK exploitation
>   beyond Spring AI 2.0 (T130–T192). T130–T132 are the foundation
>   seam; downstream tasks ship one-at-a-time guarded by per-LLM
>   capability flags. **18 weeks total** for committed (📋) work; 17
>   long-shot (💭) tasks following on opportunity-cost basis. See §X
>   for the strategic frame and X.16 for sequencing.
> - **Block G** — **NEW (2026-05-25)**: embedded LLM via **Jlama**
>   (`com.github.tjake:jlama-core:0.8.4+`, T193–T218). Pure-Java
>   in-process inference — no Ollama server, no cloud account, no
>   JNI native libs to extract. Lets an admin import a model into
>   asset storage and create a `TurLLMInstance` that serves chat /
>   embedding from the same JVM. Three non-trivial dependencies:
>   per-instance model cache (§XI.3), `TurStorageService.resolveLocalPath`
>   (§XI.3), and JVM bootstrap with `--add-modules jdk.incubator.vector
>   --enable-preview` for Project Panama (§XI.5). Major upside vs the
>   earlier llama.cpp draft: **tool calling works** via
>   `dev.langchain4j:langchain4j-jlama` for Llama 3.1 / Mistral /
>   Mixtral (§XI.6); no JNI shutdown issues; clean Windows support
>   out of the box. Tradeoff: SafeTensors format only (GGUF requires
>   conversion via `jlama-cli`, addressed by T214).
> - **Block H** — **NEW (2026-05-26)**: admin chat ↔
>   `@viglet/turing-react-sdk` convergence (T219–T232). Today
>   [chat.page.tsx](../frontend/apps/turing-app/src/app/console/chat/chat.page.tsx)
>   reimplements the SSE plumbing, conversation-id minting, chip
>   options handling, and `resetFlow` calls that the SDK's
>   `useTuringChat` / `useTuringSlots` / `useTuringFlowState` already
>   ship — drift that's already cost us one bug (admin chat missed
>   the `disabledReason` surface and a streaming-status corner case
>   the SDK had fixed). The block closes 6 gaps the SDK doesn't cover
>   yet (multipart uploads on the agent endpoint, LLM listing,
>   context-info, per-turn LLM override, real `AbortController`,
>   plain-LLM mode for the title generator + sandbox), wires the
>   admin page to the SDK as the canonical caller, and keeps the
>   admin-only chrome (multi-agent tabs, IndexedDB session sidebar,
>   AI-titled history, Markdown/highlight rendering, sandbox player,
>   scheduleAgent "Aguardando rotina…" banner) as host code. ~10–14
>   days focused work; T231 (the actual `chat.page.tsx` rewrite) is
>   gated on T219–T225. See §XII.
>
>   **Status snapshot (2026-05-26 PM)**: SDK plumbing — T219 (LLM
>   listing + context-info API), T220 (`useTuringLlmInstances`),
>   T222 (per-turn LLM override on `useTuringChat`), T223 (real
>   `AbortController` for `stop()`), T225 (`useTuringScheduleAgentWaiting`)
>   all ✅. Admin migration — T226 (admin's local `useLlmInstances`
>   replaced with the SDK hook) ✅. Two tasks deferred with documented
>   prereqs: **T221** (multipart agent chat) blocked on **T221a** —
>   backend prereq adding a multipart variant to `TurAIAgentChatAPI`
>   `/chat` + threading `MultipartFile` through `TurAgentChatExecutor.execute`;
>   **T228** (admin `useScheduleAgentWaiting` swap) blocked on
>   **T228a** — the SDK's `useTuringSlots` calls `useTuringContext()`
>   unconditionally, but the admin console doesn't mount a
>   `<TuringProvider>`, so adopting the SDK hook crashes at runtime
>   (resolution: either mount a placeholder provider at the admin
>   App root, or drop the context dependency for agent-mode hooks).
>   T224 (`useTuringLlmChat`) ✅ shipped 2026-05-26 — `postLlmChat()`
>   in `core/api.ts` + new hook in `hooks/use-turing-llm-chat.ts`,
>   covers the plain-LLM `POST /v2/llm/{id}/chat` endpoint with optional
>   `files?: File[]` multipart attachments and real `AbortController`
>   plumbing. Cross-hook `useStreamingChatCore` extraction deferred
>   (deeply intertwined with `useTuringChat`'s site-vs-agent bifurcation;
>   not worth the refactor cost at one consumer). T229 (AI title
>   generator migration to `postLlmChat`) ✅ shipped 2026-05-26 —
>   `useChatSession.generateAITitle` now calls the SDK directly behind a
>   shared `AbortController` timeout; the now-unused `turChatService`
>   param dropped from the hook signature. T230 cross-module prep ✅
>   shipped 2026-05-26 — the 4 admin component-level `TurChatService`
>   consumers outside `chat.page.tsx` (`icon-picker-dialog.tsx`,
>   `smart-description.tsx`, `meta-prompt-sheet.tsx`,
>   `sn.site.genai.form.tsx`) now talk to the SDK's `postLlmChat`
>   directly. To unblock the migration, `TurChatConversationMessage.role`
>   was widened in the SDK from `"user" | "assistant"` to
>   `"user" | "assistant" | "system"` so callers that needed a
>   per-request `system` directive (e.g. `smart-description`'s
>   field-level instruction, `meta-prompt-sheet`'s "vibe" briefs) keep
>   working. Hook code paths (`useTuringChat`, `useTuringLlmChat`) still
>   only produce `user`/`assistant` so no observable change for chat UIs.
>   5 of 5 admin-side `TurChatService.sendStream` callers retired; the
>   service still survives for `chat.page.tsx`'s `sendAgentStream` ×2 +
>   `resetAgentFlowState` (covered by T227 + T230). T228a (architectural
>   prereq) + T228 ✅ shipped 2026-05-26 — new `useOptionalTuringContext`
>   helper lets `useTuringSlots` skip the provider requirement in agent
>   mode (site mode still throws with a guiding error), then admin
>   swapped `useScheduleAgentWaiting` for the SDK alias via a one-line
>   import + deleted the local 95-line file. Side benefit: the admin's
>   local hook was hitting `/agent/{agentId}/chat-slots` (404 — backend
>   exposes only `/api/v2/ai-agent/...`); SDK path is the real endpoint.
>   **T227a (architectural prereq) ✅ shipped 2026-05-26** — same shape
>   as T228a but applied to `useTuringChat`: `useOptionalTuringContext()`
>   + site-mode throw-guard, every `config.site` / `config.locale` read
>   routed through `ctx?.config.*` so an admin host with no
>   `<TuringProvider>` can mount the hook in agent mode. Public-site
>   consumers (mounted under `<TuringProvider>`) get identical
>   behaviour. Verified: SDK `tsc --noEmit` clean, full build emits new
>   `dist/hooks/use-turing-chat.{js,d.ts}`, admin
>   `tsc --noEmit -p tsconfig.app.json` clean against the rebuilt SDK.
>   **T221a + T221 ✅ shipped 2026-05-26** — agent chat now accepts
>   `multipart/form-data` uploads end-to-end. New
>   `TurAIAgentChatAPI.chatMultipart` endpoint mirrors the JSON variant
>   via a shared `doChat(...)` helper; `TurAgentChatExecutor.execute`
>   gained a 7-arg overload that threads `List<MultipartFile>` into a
>   new `TurChatPromptAssembler.assemble(..., files)` overload, which
>   attaches images + Tika-extracted text to the LAST user message via
>   the new shared `TurChatAttachmentService` (extracted from
>   `TurLLMChatAPI`'s previous in-place helpers — both APIs now share
>   one attachment pipeline). On the SDK side, `postAgentChat`
>   `PostAgentChatOptions.files?: ReadonlyArray<File>` switches the
>   request to multipart when present, plain JSON otherwise — same
>   pattern T224's `postLlmChat` already uses. 9 reflection-based file
>   tests migrated from `TurLLMChatAPITest` to a clean
>   `TurChatAttachmentServiceTest` (real-Tika, no reflection); 2
>   delegation tests stayed on the API test. 43/43 backend tests green
>   on direct surface; pre-existing IT failure (`TurAbBanditService` —
>   "No default constructor found" on a clean tree, unrelated to this
>   work) tracked separately.
>   **T227 ✅ shipped 2026-05-27** — `chat.page.tsx`'s `sendAgentStream`
>   ×2 + `resetAgentFlowState` retired; a new `AgentChatTab` child
>   owns one tab's `useTuringChat` (agent mode) and the page drops
>   `TurChatService` entirely. The three 2026-05-26 design points landed
>   per their recommendations: (1) session restore via two additive SDK
>   options `initialMessages` + controlled `conversationId`, re-seeded
>   through a `chatEpoch` remount key (cleaner than an imperative
>   `restore()` since the controlled id makes remount lossless); (2)
>   compaction stays admin-only — a thin direct `postAgentChat` summary
>   reseeds the child with `[summary]` on a bumped epoch (no
>   `replaceMessages` added to the SDK); (3) only the active tab mounts,
>   completed turns persist via the unchanged `saveAfterResponse`
>   (behaviour change: switching agent tabs drops the other tab's
>   in-memory transcript to the sidebar). The page owns the conversation
>   id (`ensureSessionId` minted eagerly, passed as controlled
>   `conversationId`) so backend conversation == IndexedDB session.
>   Verified SDK + admin `tsc` clean; runtime unproven — T232 smoke ITs
>   are the regression gate.
>   **T230 ✅ shipped 2026-05-27** — `TurChatService` + `chat.service.ts`
>   deleted (-368 lines). A frontend-wide sweep confirmed no remaining
>   importer of the class and that `sendSemanticStream` was dead code;
>   the lone surviving export (the `ChatMessageItem` type) was repointed
>   to the SDK's identical `TurChatConversationMessage` in `chat.types.ts`
>   + `chat-session.service.ts` (IndexedDB session shape unchanged).
>   **T231 ✅ shipped 2026-05-27** — child renamed `AgentChatTab` and now
>   owns its full hook trio (`useTuringScheduleAgentWaiting` moved out of
>   the page into the tab); admin↔SDK message mappers centralized in
>   `chat.types.ts`; no-LLM placeholder extracted to `AgentNoLlmFallback`.
>   `chat.page.tsx` 571 → 400 lines.
>   **T232 ✅ shipped 2026-05-27** — bootstrapped the frontend's first test
>   runner (vitest 3.2.4 + RTL 16 + jsdom + fake-indexeddb) and shipped the
>   three chat smoke ITs (send+stream+chips, session restore, scheduleAgent
>   banner). 4/4 green; `pnpm install` clean (no EBUSY); `tsc -b` exit 0.
>   Block H (T219–T232) is now complete.
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
| T39 | ✅ | T35 | `http.bearer(token).postJson(...)` + `http.basic(user, pass)` chainable auth helpers on [`TurCustomToolHttpHelper`](../turing-app/src/main/java/com/viglet/turing/genai/tool/TurCustomToolHttpHelper.java) — immutable: each call returns a new helper that shares the underlying `RestClient` but pins `Authorization`, so a script can interleave authenticated and unauth calls without leaking credentials. Reserved slot prefix `__` for auto-auth — `TurCustomToolCallbackService.executeGroovy` now constructs the slot helper first, then wires it into the `http` binding (`new TurCustomToolHttpHelper(slotHelper)`); on every `http.*` call the helper resolves auth in order **explicit chain > `__bearer_token` slot > `__basic_user`+`__basic_password` slot > none**, with caller-supplied `Authorization` headers always winning over both. Constants `SLOT_BEARER_TOKEN`/`SLOT_BASIC_USER`/`SLOT_BASIC_PASSWORD` are public so flow authors can grep the reserved names. Log-safety: `toString()` only reports `auth=REDACTED`/`auth=none` + `slots=bound`/`slots=none` — never echoes the token, even via accidental `log.info(http)` in a customer script; slot-based credentials are resolved lazily per-request and never serialised. New [`TurCustomToolHttpHelperAuthTest`](../turing-app/src/test/java/com/viglet/turing/genai/tool/TurCustomToolHttpHelperAuthTest.java) (15 tests) uses a throwaway JDK `HttpServer` (same pattern as `TurSEStopWordTest`, no MockWebServer dependency) to capture the actual `Authorization` header for bearer chain, basic chain, immutability (`http.bearer(t)` doesn't mutate `http`), null-token clearing, slot auto-attach for both schemes, chain-wins-over-slot precedence, caller-header-wins-over-everything, bearer-slot beats basic-slot, blank token treated as unset, and three `toString()` redaction assertions. Verified: backend compile clean; 15/15 new tests green; 132/132 related custom-tool tests (`AulaRelampagoToolTest` + `ComporPropostaInCompanyTest` + `GerarPropostaPdfTest` + `TurCustomToolGroovyParserBugTest`) still green; `TurCacheAnnotationConventionsTest` + `TurLiquibaseChangelogAgnosticTest` still green. | §VII.2.a |
| T40 | ✅ | — | Groovy editor with auto-complete via [`TurCustomToolDescriptorService`](../turing-app/src/main/java/com/viglet/turing/genai/tool/TurCustomToolDescriptorService.java) + `GET /api/custom-tool/descriptor` (new endpoint on [`TurCustomToolAPI`](../turing-app/src/main/java/com/viglet/turing/api/customtool/TurCustomToolAPI.java)). **Scope adjustment** — spec said "Monaco" but the project already uses CodeMirror v6 for the Custom Tool editor (project standard); wiring CodeMirror's `@codemirror/autocomplete` (already a transitive dep via `@codemirror/language` 6.10.5) instead of swapping editors keeps the diff aligned with the convention while shipping the user-facing value. Descriptor shape is editor-agnostic (`helpers[].name/className/description/methods[]` + `globals[]`) so a future Monaco / LSP path can consume the same payload. Hand-written method signatures rather than reflected — captures Groovy named-arg conventions and default values (`ann(site:, query:, locale: null, topK: 8, ...)`, `executePython(namedArgs Map [script:, inputs:])`) that reflection can't see. Covers all five bindings injected by `TurCustomToolCallbackService.executeGroovy`: **http** (`getJson`, `postJson`, `bearer`, `basic` — incl. the T39 chainable auth + `__bearer_token` slot prefix hint in the helper-level description), **slots** (`set`, `get`, `all`), **turingSearch** (`ann`, `sn`), **code** (`executePython` × 2 overloads), and the `args` global. Frontend: new model `custom-tool-descriptor.model.ts`, service method `TurCustomToolService.getDescriptor`, query hook `useCustomToolDescriptor` (`staleTime: Infinity` — static catalogue, never re-fetched mid-edit), CodeMirror completion-source factory at [`groovy-autocomplete.ts`](../frontend/apps/turing-app/src/components/custom-tool/groovy-autocomplete.ts) wired into the editor's `extensions` via a `useMemo`. Suggestions trigger on (a) top-level identifier typing with descriptor blurb + class name shown in the popover, and (b) member access `<helper>.` showing the method name + signature + description. New [`TurCustomToolDescriptorServiceTest`](../turing-app/src/test/java/com/viglet/turing/genai/tool/TurCustomToolDescriptorServiceTest.java) (8 tests) pins the binding names against `executeGroovy`'s injection contract — if a helper is renamed/dropped backend-side but not mirrored in the descriptor, the editor would silently stop suggesting it; these tests fail loudly. Verified: 8/8 new tests green; 88/88 `TurCustomTool*` tests green (including the T39 `TurCustomToolHttpHelperAuthTest`); backend compile clean. | §VII.2.b |
| T41 | ✅ | T40 | Live unsaved-script preview via per-admin draft overlay. **Scope adjustment** — the original spec's "no restart needed for the persisted form" is *already* delivered by T37 + the existing `@Cacheable("turCustomToolfindAll"/"turCustomToolfindById")` on `TurCustomToolRepository` (Hazelcast-backed → cluster-wide eviction on save) + the hash-based `scriptCache` recompile inside `TurCustomToolCallbackService.scriptFor`. T41's real value-add is therefore the *unsaved* case: admin iterates against real LLM + RAG + chat flow without persisting half-baked scripts that would affect every visitor. **Transport** is REST PUT/DELETE/GET rather than WebSocket — the codebase has no `spring-boot-starter-websocket` on the classpath and standardises on Reactor SSE (`TurChatSlotEventBus`, `TurSNSiteGenAiAPI` `streamSlots`); a one-way client→server push doesn't need duplex, so plain REST matches the convention while delivering the same UX. Implementation: new [`TurCustomToolDraftRegistry`](../turing-app/src/main/java/com/viglet/turing/genai/tool/TurCustomToolDraftRegistry.java) — thread-safe `ConcurrentHashMap<(username, toolId), DraftEntry>` storing the compiled `Class<? extends Script>` upfront so chat invocations skip re-parsing per call; 30-min idle TTL with `@Scheduled(fixedRate=5min)` sweep + touch-on-find so an active editor keeps the draft alive past nominal TTL; reuses `TurCustomToolCallbackService.sanitizeGroovySource` so heredoc-with-`\U` sources (Python regex embeds, the Groovy ANTLR bug from `TurCustomToolGroovyParserBugTest`) compile identically to the persisted path. `TurCustomToolCallbackService.executeGroovy` resolves `username` from the new `TOOL_CONTEXT_USERNAME` (`turing.username`) key, queries the registry first, falls through to the regular hash-keyed `scriptFor(tool)` cache when no draft exists — visitors and other admins see zero behavioural change. `TurAgentChatExecutor.buildChatOptions` publishes the username from `SecurityContextHolder` on the HTTP thread (before the reactive Flux starts) so the value is visible inside the tool-call lambda; `"anonymous"` sessions are skipped. New endpoints on [`TurCustomToolAPI`](../turing-app/src/main/java/com/viglet/turing/api/customtool/TurCustomToolAPI.java): `PUT /api/custom-tool/{id}/draft` (compiles + stores, returns line/column on syntax error), `DELETE /api/custom-tool/{id}/draft`, `GET /api/custom-tool/{id}/draft` (status for page-reload + active-banner). Username extracted via `SecurityContextHolder` + filter of `"anonymousUser"`. Frontend: 3 service methods on `TurCustomToolService`, 3 query/mutation hooks (`useCustomToolDraftStatus`, `usePushCustomToolDraft`, `useClearCustomToolDraft`), and two new buttons next to Validate/Vibe-Coding in [custom-tool.form.tsx](../frontend/apps/turing-app/src/components/custom-tool/custom-tool.form.tsx) — "Push live draft" (IconBolt) and "Clear draft" (IconBoltOff, conditional on `draftStatus.active`), an amber banner while a draft is active, and an inline error pane with line/column when the pushed draft fails to compile. New tools (no id yet) hide the controls. New [`TurCustomToolDraftRegistryTest`](../turing-app/src/test/java/com/viglet/turing/genai/tool/TurCustomToolDraftRegistryTest.java) (11 tests) using a `MutableClock` to drive TTL deterministically: compile-on-put + line/column extraction, per-(username, toolId) isolation (Alice's draft invisible to Bob), idempotent clear, TTL expiry + lazy eviction on find, touch-on-find keeps active drafts alive past nominal TTL, blank-username/blank-toolId rejection. Verified: 11/11 new tests green; 210/210 across full custom-tool + customer tool + lint sweep (`TurCustomTool*` + `AulaRelampagoToolTest` + `ComporPropostaInCompanyTest` + `GerarPropostaPdfTest` + `TurCacheAnnotationConventionsTest` + `TurLiquibaseChangelogAgnosticTest`); 15/15 `TurAgentChatExecutor*` tests still green (executor changes are pure additions to `buildChatOptions` toolContext map); backend compile clean. | §VII.2.c |

#### D.2 — Engine declarative additions (new node types & policies)

| #   | Status | Depends on | Task | Detail in |
|-----|--------|------------|------|-----------|
| T42 | ✅ | — | `writeSlot` node type — slot SET with server-side `{{variable}}` interpolation via `ChatFlowOps.applyWriteSlotNode`. Editor palette item with `IconPencilPlus` (teal). | §VII.3.a |
| T43 | ✅ | — | `formCapture` node type — like `aiQuestion` but with strict regex validation (CPF, CNPJ, email, phone, CEP). `ChatFlowOps.VALIDATION_PATTERNS` + canonical extractor cascade. Editor renders with `IconForms` (indigo) + rule-name badge. | §VII.3.b |
| T44 | ✅ | — | CPF/CNPJ digit-check (mod-11) on `formCapture` patterns — strict regex format + checksum validation. | §VII.3.b |
| T45 | ✅ | — | `onJudgeReject` declarative policy (already documented as patch #22) — `advance_with_literal` / `reprompt` / `block` per `aiQuestion`. Removes the need for `LlmJudgeGuardrailStrategy` retry heuristic. | §I.3 patch #22 |
| T46 | ✅ | T45 | `functionCall` node runtime — type is no longer vapor. New [`TurFunctionCallNodeExecutor`](../turing-app/src/main/java/com/viglet/turing/genai/flow/TurFunctionCallNodeExecutor.java) invokes a tool deterministically from the flow (not from the LLM), wired into [`TurChatFlowEngineService.walkTransparentNodes`](../turing-app/src/main/java/com/viglet/turing/genai/flow/TurChatFlowEngineService.java) alongside the existing `writeSlot` dispatch: execute → `advanceToFirstEdge` → save → SSE → continue. **No schema additions** — reuses existing `ChatFlowNode` fields the editor already writes: `toolSource` ∈ `{NATIVE, MCP}`, `functionName` (the Spring AI `ToolDefinition.name()`, e.g. `search_site`/`get_current_time`/sanitised Custom Tool title), `aiInstruction` repurposed as the tool input JSON template with `{{slot}}` interpolation via the now-public `ChatFlowOps.interpolateVariables` (blank → `"{}"`), and `outputVariable` for the result slot (optional → side-effect only when blank). Lookup uses `TurNativeToolService.getToolCallbacks(Set.of(functionName))` so Custom Tools registered via T35+ ride the same path. **v1 scope guards**: `toolSource=MCP` logs "not yet supported in v1" and lets the engine advance — `TK4` row updated to point at MCP follow-up rather than `functionCall` itself being vapor; unknown `toolSource` strings, missing `functionName`, missing callback, and callback exceptions all return `ExecutionResult.failure(...)` with a one-line reason, the engine logs + advances (stricter `continueOnFailure`/`onError` deferred to T49/T50). Null `toolSource` defaults to `NATIVE` for backward-compat with older flows. New [`TurFunctionCallNodeExecutorTest`](../turing-app/src/test/java/com/viglet/turing/genai/flow/TurFunctionCallNodeExecutorTest.java) (10 tests, pure-Mockito) covers: NATIVE happy path (slot interpolation, callback invocation, slot write); blank `aiInstruction` defaults to `{}`; side-effect-only execution (no `outputVariable`); missing functionName; tool not found; MCP source acknowledged + skipped; callback throws; unknown toolSource; null inputs; null toolSource defaults to NATIVE. Three existing tests (`TurChatFlowLinterServiceTest`, `TurChatFlowPinFlowTest`, `TurChatFlowRouterIndexCacheTest`) updated to pass a Mockito-mocked `TurFunctionCallNodeExecutor` to the engine constructor — no behaviour change. Verified: 10/10 new tests green; 195/195 across full flow + executor sweep (`TurChatFlow*` + `ChatFlowOpsTest` + `TurAgentChatExecutor*`); 111/111 across custom-tool + lint sweep; backend compile clean. | §VII.4.a |
| T47 | ✅ | — | `subFlowSwitch` node — both backend runtime and frontend editor block shipped. Reuses the existing T46-touched `subFlow` descent via [`TurChatFlowEngineService.enterSubFlowById`](../turing-app/src/main/java/com/viglet/turing/genai/flow/TurChatFlowEngineService.java) (refactored out of `enterSubFlow` so both the plain `subFlow` node and the new `subFlowSwitch` share descent semantics, incl. cycle protection via `chainContainsFlow`). Dispatch in [`walkTransparentNodes`](../turing-app/src/main/java/com/viglet/turing/genai/flow/TurChatFlowEngineService.java) sits next to the existing `subFlow` case: read switch variable from state, call the new public [`ChatFlowOps.resolveSwitchOption`](../turing-app/src/main/java/com/viglet/turing/genai/flow/strategy/ChatFlowOps.java) (extracted from the previously-private `resolveSwitchHandle` — same exact/substring/LLM-classify cascade, now returning the matched `SwitchOption` so callers can read fields beyond `id`), pull `option.subFlowId()`, descend; on no-match or blank `subFlowId` fall back to `advanceToFirstEdge` so a wildcard outgoing edge can catch the case (mirrors the `switch` wildcard contract — authors should always keep one wired). **Schema**: extended `ChatFlowNode.SwitchOption` from `(id, label)` to `(id, label, subFlowId)` with a 2-arg back-compat constructor so existing JSON deserialises unchanged (`@JsonIgnoreProperties(ignoreUnknown=true)` already absorbs the new field on old data). The existing `resolveSwitchHandle` is now a one-liner that delegates to `resolveSwitchOption(...).map(SwitchOption::id).orElse(null)` — pure refactor, no behavioural change. New [`ResolveSwitchOptionTest`](../turing-app/src/test/java/com/viglet/turing/genai/flow/strategy/ResolveSwitchOptionTest.java) (7 tests): exact match preserves `subFlowId`, substring "Beach Getaway" beats "Beach" (longest wins), case-insensitive, blank/missing variable → empty, empty options list → empty, no aux model + no cheap match → empty, 2-arg back-compat constructor still works. Verified: 7/7 new tests green; 238/238 across full flow + executor + custom-tool + lint sweep — `switch` IT (6 tests with real OpenAI) still green, confirming the `resolveSwitchHandle` → `resolveSwitchOption` refactor is behavior-preserving; sub-flow descent IT (12 tests) still green; backend compile clean. **Frontend** (same turn): new `"subFlowSwitch"` member added to [`FlowNodeType`](../frontend/apps/turing-app/src/app/console/chat-flow/types.ts) discriminated union + entry in `NODE_COLORS` (purple palette — sits between `switch` cyan and `subFlow` violet to signal "switch semantics, sub-flow consequence"); `FlowSwitchOption` extended with optional `subFlowId` + cached `subFlowName` so the canvas reads well even before the flow list loads. Palette block in [`flow-sidebar.tsx`](../frontend/apps/turing-app/src/app/console/chat-flow/components/flow-sidebar.tsx) with `IconRouteAltLeft`. New [`SubFlowSwitchNode`](../frontend/apps/turing-app/src/app/console/chat-flow/components/flow-nodes.tsx) renderer mirrors `SwitchNode`'s per-option row layout but shows the linked sub-flow name (or "—") instead of an outgoing-edge arrow, with a single source handle on the right for the wildcard fallback edge. [`SwitchOptionsEditor`](../frontend/apps/turing-app/src/app/console/chat-flow/components/flow-properties.tsx) reused with a new optional `subFlowOptions` prop — when present, each option row gets a sub-flow picker underneath the label input; the existing `switch` node passes `undefined` and renders unchanged. `FlowProperties` collapses the `switch` and `subFlowSwitch` sections into one block — same `switchVariable` slot picker, same `overrideExistingValue` checkbox, same options editor; only the hint text and the per-row picker differ. `buildDefaultNodeData` seeds two empty options on both kinds. `typeLabelKey()` + `DEFAULT_LABEL` extended. `chat-preview.tsx` (mermaid-like transcript preview) and `chat-flow.live-preview-panel.tsx` (live test preview) both render `subFlowSwitch` summary rows. i18n keys added to `en/chatFlow.json` and `pt/chatFlow.json`: `palette.subFlowSwitch`, `properties.subFlowSwitchOptionsHint`, `properties.subFlowSwitchOptionPlaceholder`. `chat-flow.ai-chat.page.tsx`'s narrower `ChatFlowNodeType` (5 LLM-only types used by the authoring skill API) is intentionally untouched — that's the LLM-generation surface, not the editor's exhaustive node-type catalogue. | §VII.4.b |
| T48 | ✅ | — | `scheduleAgent` integration node — fires a routine remote and waits for its slot-bus response. Long-running tools without blocking the chat turn. Backend in [`TurScheduleAgentNodeExecutor`](../turing-app/src/main/java/com/viglet/turing/genai/flow/routine/TurScheduleAgentNodeExecutor.java) (fire/poll lifecycle with `__scheduleAgent_pending_<nodeId>` + `__scheduleAgent_startedAt_<nodeId>` markers) wired into [`TurChatFlowEngineService.walkTransparentNodes`](../turing-app/src/main/java/com/viglet/turing/genai/flow/TurChatFlowEngineService.java) alongside `functionCall` — five outcomes: `WAITING_FIRED` (publishes SSE so the chat UI's banner picks up the pending marker, no further walking), `WAITING_POLL` (markers already set; no republish to avoid the auto-resume → walk → publish loop), `COMPLETED` (advance to first edge), `TIMEOUT` (route via `timeout` sourceHandle, falls back to first edge), `FAILED` (legacy behavior: advance + log; T49 makes this configurable via `continueOnFailure`). Async dispatch through `JmsMessagingTemplate.convertAndSend(TurSNConstants.ROUTINE_QUEUE, TurScheduleAgentMessage)`; payload includes the routineId + conversationId + nodeId + outputSlot + `aiInstruction`-interpolated JSON input. The companion `TurChatFlowAutoResumeService` subscribes to the slot bus and walks the parked flow forward when the routine writes the output slot. Routine CRUD lives at `/api/routine` ([`TurRoutineAPI`](../turing-app/src/main/java/com/viglet/turing/api/agent/TurRoutineAPI.java)) with `TurRoutine` entity (`TurRoutineKind` enum: `NATIVE_TOOL` / `GROOVY`, with `TurRoutineGroovyExecutor` mirroring the Custom Tool sandbox), Liquibase `v2026.3.1.6.yaml` (tur_routine + tur_routine_queue) + `v2026.3.1.7.yaml` (TurRoutineDto export). Frontend: new `scheduleAgent` palette block (lime) with `IconClock`, two outgoing handles on the canvas (right = normal completion, bottom = `timeout`), properties panel exposes routine picker + payload-template textarea + output-slot input + per-node timeout-ms override; routine list/edit pages at `/console/routine/*`. SDK adds `useTuringScheduleAgentWaiting({ agentId })` (T225 dep) polling `__scheduleAgent_pending_*` markers; admin chat shows an "Aguardando rotina…" banner via the same hook. 16 backend tests across [`TurScheduleAgentNodeExecutorTest`](../turing-app/src/test/java/com/viglet/turing/genai/flow/routine/TurScheduleAgentNodeExecutorTest.java) (8 — fire/poll/timeout/missing-routine/disabled-routine/null-inputs) + [`TurRoutineGroovyExecutorTest`](../turing-app/src/test/java/com/viglet/turing/genai/flow/routine/TurRoutineGroovyExecutorTest.java) + [`TurRoutineAPITest`](../turing-app/src/test/java/com/viglet/turing/api/agent/TurRoutineAPITest.java); the full-stack IT [`TurScheduleAgentEngineIT`](../turing-app/src/test/java/com/viglet/turing/genai/flow/routine/TurScheduleAgentEngineIT.java) (boots Spring + embedded Artemis + chat flow) verifies the fire → JMS dispatch → routine completion → slot write → auto-resume cycle for both NATIVE_TOOL and GROOVY routine kinds, plus the disabled-routine FAILED path. Verified: backend compile clean; all 16 routine tests green; existing engine/flow tests (`TurChatFlowEngineService` + `TurFunctionCallNodeExecutor` + `ChatFlowOps` + lint tests) untouched. Shipped via commits `5b8ada1c11` (backend) + `31d9038fd5` (frontend routine management). | §VII.4.c |
| T49 | ✅ | — | `continueOnFailure: true` flag on nodes — authors mark a `functionCall` or `scheduleAgent` node where a tool/routine failure routes the flow along an outgoing edge whose `sourceHandle` is `"failure"` instead of advancing silently. New `Boolean continueOnFailure` field added to [`ChatFlowNode.NodeData`](../turing-app/src/main/java/com/viglet/turing/genai/flow/ChatFlowNode.java) (25 fields) with a back-compat 24-arg constructor delegating to `null` so existing JSON deserialization, ChatFlowGraph fixtures, and pre-T49 tests keep compiling unchanged. New helper [`ChatFlowOps.advanceOnFailure(state, graph, current)`](../turing-app/src/main/java/com/viglet/turing/genai/flow/strategy/ChatFlowOps.java) picks the outgoing edge whose `sourceHandle` equals `FAILURE_HANDLE = "failure"` (case-insensitive), falls back to `advanceToFirstEdge` when no failure edge is wired or the wired target is missing — same lenient default the rest of the flow ops use (subFlowSwitch wildcard, scheduleAgent timeout fallback). Engine wiring in [`TurChatFlowEngineService.walkTransparentNodes`](../turing-app/src/main/java/com/viglet/turing/genai/flow/TurChatFlowEngineService.java): `functionCall` now consumes the `TurFunctionCallNodeExecutor.ExecutionResult` (previously the void-receiving call discarded it) — when `!result.ok() && Boolean.TRUE.equals(continueOnFailure)` routes via `advanceOnFailure`, else legacy `advanceToFirstEdge`; the `scheduleAgent` FAILED branch (resolution/dispatch errors) gets the same conditional dispatch. TIMEOUT keeps its existing `timeout` sourceHandle path — `failure` is distinct so authors can wire "deadline expired" and "routine errored" to different recovery flows. Frontend: new `continueOnFailure?: boolean` field on [`FlowNodeData`](../frontend/apps/turing-app/src/app/console/chat-flow/types.ts), a shared `ContinueOnFailureField` checkbox component in [`flow-properties.tsx`](../frontend/apps/turing-app/src/app/console/chat-flow/components/flow-properties.tsx) rendered at the bottom of both `functionCall` and `scheduleAgent` property panes, conditional `Handle` (id=`"failure"`, red-rose) on [`FunctionCallNode`](../frontend/apps/turing-app/src/app/console/chat-flow/components/flow-nodes.tsx) + [`ScheduleAgentNode`](../frontend/apps/turing-app/src/app/console/chat-flow/components/flow-nodes.tsx) that only mounts when the flag is checked so authors don't see noise on default nodes. i18n EN/PT keys `chatFlow.properties.continueOnFailure` + `chatFlow.properties.continueOnFailureHint`. New [`AdvanceOnFailureTest`](../turing-app/src/test/java/com/viglet/turing/genai/flow/strategy/AdvanceOnFailureTest.java) (9 tests): failure handle wired → routes to failure target, case-insensitive match (`"FAILURE"`), no-failure-handle falls back to first edge, failure target points at missing node falls back to first edge, no outgoing edges stays put, `continueOnFailure()` defaults to null on legacy nodes, round-trips both `Boolean.TRUE` and `Boolean.FALSE`, 24-arg back-compat constructor leaves `continueOnFailure=null`, `FAILURE_HANDLE` literal pinned to `"failure"` (JSON wire contract for T50's red-edge styling). T50 (visual red try/catch edge in the editor) gated on this — the data-model + engine routing are done; T50 wires `edgeTypes` + CSS so the existing red handle gets a styled connection. Verified: 9/9 new tests green; 98/98 across the impacted unit-test sweep (`TurFunctionCallNodeExecutorTest` 10, `TurScheduleAgentNodeExecutorTest` 8, `ChatFlowOpsTest` 28, `ResolveSwitchOptionTest` 7, `TurChatFlowStaticPromptCacheTest` 12, `TurChatFlowPinFlowTest` 11, `TurChatFlowLinterServiceTest` 8, `TurChatFlowRouterIndexCacheTest` 5); `TurCacheAnnotationConventionsTest` + `TurLiquibaseChangelogAgnosticTest` still green; backend compile clean. | §VII.4.d |
| T50 | ✅ | T49 | `onError` edges in editor visual — analogous to try/catch; red arrow that fires when the node fails. T49 wired the routing + the conditional red Handle on `functionCall`/`scheduleAgent`; T50 styles the outgoing connection so authors spot the recovery path at a glance. New [`flow-edges.tsx`](../frontend/apps/turing-app/src/app/console/chat-flow/components/flow-edges.tsx) exposes the `FAILURE_HANDLE = "failure"` JSON-wire mirror (matches `ChatFlowOps.FAILURE_HANDLE` pinned by `AdvanceOnFailureTest`), the `failureEdgeOverlay(sourceHandle)` helper, a `FailureEdge` renderer (`BaseEdge` + `getSmoothStepPath`, red `#e11d48` stroke, `strokeDasharray: "6 4"`), and the `edgeTypes` map registered as `{ failure: FailureEdge }`. The overlay returns `{ type: "failure", animated: true, markerEnd: { type: ArrowClosed, color: "#e11d48", width: 18, height: 18 } }` for failure edges and `{ type: "smoothstep", animated: false, markerEnd: undefined }` for the non-failure branch — the explicit reset on the non-failure path matters for `onReconnect`, where moving an edge from the failure handle back to a regular handle would otherwise keep the red `markerEnd` and `animated: true` from its previous incarnation. Wired in three places: [`chat-flow.page.tsx`](../frontend/apps/turing-app/src/app/console/chat-flow/chat-flow.page.tsx) `onConnect` spreads the overlay over the `addEdge` payload so freshly-dragged failure edges render red immediately; `onReconnect` re-derives the overlay after `reconnectEdge` so flipping the source handle flips the style; [`chat-flow.serialize.ts`](../frontend/apps/turing-app/src/app/console/chat-flow/chat-flow.serialize.ts) `deserializeGraph` calls the overlay per edge so pre-T50 persisted graphs hydrate with the red look without a migration. [`chat-flow.diagram-tab.tsx`](../frontend/apps/turing-app/src/app/console/chat-flow/components/chat-flow.diagram-tab.tsx) passes `edgeTypes={edgeTypes}` to `<ReactFlow>`. **No schema change**: only `sourceHandle` (already persisted) drives the visual; nothing new lands in `definitionJson`. Verified: full frontend type-check clean (`pnpm exec tsc -b --noEmit` → exit 0); backend compile clean — pure frontend change, no Java touched. | §VII.4.d |
| T51 | ✅ | — | Capture-first inversion — persists the user message into the slot BEFORE the judge runs; the judge becomes advisory (writes a `<slot>__confidence` low/high grade in a parallel slot) so the flow never stalls with `slot=null`. **Shipped as an opt-in flow-level mode** (per the engagement decision: authors choose the behaviour per flow rather than a global engine rewrite) via a new `TurChatFlowCaptureMode` enum on `TurChatFlow.captureMode`: `VALIDATE_THEN_CAPTURE` (legacy default — judge gates, capture only on accept; existing flows unchanged, **zero IT revalidation needed**), `CAPTURE_THEN_GATE` (capture-first, but a failing deterministic `validationRule` still parks the cursor for a re-prompt with the value captured + `confidence=low`), `CAPTURE_THEN_GRADE` (capture-first, always advance unless abandoned; validation + judge are purely advisory). Only the `LLM_JUDGE` guardrail honours the mode — the other methods have no judge to invert. **Backend**: new enum [`TurChatFlowCaptureMode`](../turing-app/src/main/java/com/viglet/turing/persistence/model/agent/TurChatFlowCaptureMode.java); `captureMode` column on [`TurChatFlow`](../turing-app/src/main/java/com/viglet/turing/persistence/model/agent/TurChatFlow.java) (`@Enumerated(STRING)`, default `VALIDATE_THEN_CAPTURE`) + Liquibase [`v2026.3.1.11.yaml`](../turing-app/src/main/resources/db/changelog/v2026.3.1.11.yaml) (idempotent `addColumn`, `varchar(32)` default `VALIDATE_THEN_CAPTURE`); [`AdvanceContext`](../turing-app/src/main/java/com/viglet/turing/genai/flow/strategy/AdvanceContext.java) gains a `flow` field (engine passes it at the single construction site) so the strategy reads `captureMode` without a lazy-load. The inversion lives in [`LlmJudgeGuardrailStrategy.advanceCaptureFirst`](../turing-app/src/main/java/com/viglet/turing/genai/flow/strategy/LlmJudgeGuardrailStrategy.java): (1) provisional raw write of the user reply to the slot (write-eligibility decided ONCE on the original slot state — empty / `overrideExistingValue` / prior `confidence=low` provisional are writable, a genuine out-of-band high-confidence value e.g. CV-extracted `name` is protected); (2) advisory judge call; abandon still ends the flow; (3) refine the slot with the judge's normalized/canonicalized extraction; (4) grade `<slot>__confidence` = `high` iff `valid && judge.onTopic && judge.readyToAdvance` else `low`; (5) advance per mode (GRADE always; GATE iff `validationRule` passes; informational no-output-var nodes keep the legacy "advance when the judge says the goal is addressed"). A blank reply on a slot node stays put (waiting for an answer); no judge model still captures raw + advances (graded low). The legacy `VALIDATE_THEN_CAPTURE` body is byte-for-byte unchanged — the dispatch returns before it. **Plumbing**: `TurChatFlowAPI` update copies `captureMode` (null-guarded like `guardrailMethod`); create + MapStruct mapper auto-map it (DTO extends entity, default protects the common case); export/import carry it via [`TurChatFlowExchange`](../turing-app/src/main/java/com/viglet/turing/exchange/agent/TurChatFlowExchange.java) + [`TurAIAgentImportService`](../turing-app/src/main/java/com/viglet/turing/exchange/agent/TurAIAgentImportService.java) (null-guarded so pre-T51 exports keep the default). **Frontend**: `TurChatFlowCaptureMode` type + `CHAT_FLOW_CAPTURE_MODES` + `captureMode` field on [`chat-flow.model.ts`](../frontend/apps/turing-app/src/models/agent/chat-flow.model.ts); a "Capture Mode" `Select` in [`chat-flow.settings-tab.tsx`](../frontend/apps/turing-app/src/app/console/chat-flow/components/chat-flow.settings-tab.tsx) (disabled + explained when guardrail ≠ LLM_JUDGE), wired through [`chat-flow.page.tsx`](../frontend/apps/turing-app/src/app/console/chat-flow/chat-flow.page.tsx) state + `onSave`; EN/PT i18n `chatFlow.fields.captureMode` + `chatFlow.captureModes.*`. New [`LlmJudgeCaptureFirstTest`](../turing-app/src/test/java/com/viglet/turing/genai/flow/strategy/LlmJudgeCaptureFirstTest.java) (11 tests, mocked judge ChatModel): GATE judge-rejects-but-valid → capture+advance+low; GATE validation-fails → capture+stay+reprompt; GATE judge-endorses → refine+advance+high; GATE protected out-of-band value preserved; GATE prior-low-confidence replaceable on re-prompt; GATE blank reply stays; GATE no-model still captures+advances; GRADE invalid+rejected still advances; GRADE abandon ends with farewell; `resolveCaptureMode` null-flow / null-column default. Verified: 55/55 across the capture-first + judge + ops + failure sweep; 63/63 across flow + export + domain + Liquibase-agnostic + cache-conventions lints; backend compile clean; frontend `tsc -b --noEmit` exit 0. | §VII.4.e |
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
| T60 | ✅ | T54 | **Slot audit log** — every slot write registers origin (`NODE`/`TOOL`/`ENDPOINT`/`EXTRACT`) in `chat_slot_write_log` (id, conversationId, slotName, oldValue, newValue, source, originDetail, ts). New `TurChatSlotAuditEntry` JPA entity + `TurChatSlotAuditRepository` + Liquibase `v2026.3.1.8.yaml` (two indexes: `(conversationId, ts)` and `(slotName, ts)`). New `TurChatSlotAuditService.record(...)` + `recordDiff(before, after, ...)` swallows persistence failures so the audit layer never breaks a chat turn; pure-function `diff(Map, Map)` helper exposed for tests. **Instrumented all 4 write paths**: (1) `TurChatFlowEngineService.advance()` captures previous/current `variablesJson` and records each diff as `NODE` with `originDetail="node=<previousNode>[->newNode]"`; (2) `TurChatFlowEngineService.writeSlot(...)` gained a source-aware overload `(conversationId, name, value, source, originDetail)` — old 3-arg overload defaults to `ENDPOINT` for back-compat; (3) `TurCustomToolSlotHelper` gained a new audit-aware constructor (back-compat constructor kept) wired through `TurCustomToolCallbackService.executeGroovy` with `originDetail="tool=<toolTitle>"`; (4) `TurChatSlotExtractionService` passes `EXTRACT` source + `originDetail="agent=<id>"`. REST: new `GET /api/chat/sessions/{conversationId}/slot-audit` on `TurChatSessionAPI` (secured `ROLE_ADMIN`/`AI_AGENT_VIEW`) returning `TurChatSlotAuditDto(conversationId, List<Entry>)`. **Frontend**: new `chat-slot-audit.service.ts` + `useChatSlotAudit` React Query hook (5s polling when sheet open, 0 staleTime) + new query key `queryKeys.chatSessions.slotAudit(conversationId)`. New `SlotAuditRow` component rendered as a timeline below the existing slot map inside `ChatSessionInfoSheet` — each row shows a colour-coded source badge (NODE blue / TOOL amber / ENDPOINT emerald / EXTRACT violet), the slot name, the `(old → new)` delta with `(empty)`/`(cleared)` fallbacks, the localised timestamp, and the `originDetail` subtitle. EN/PT i18n keys for badge labels + empty/error/cleared states. 14 new unit tests (`TurChatSlotAuditServiceTest`) pin: one row per `record`, blank-conversation/blank-slot/null-source no-ops, persistence failure swallowed, originDetail 256-char truncation with `...` suffix, `list()` empty-input guard, diff add/update/remove/no-change/null-input, `recordDiff` skips unchanged slots. 3 existing engine tests updated to pass the new `slotAuditService` mock to the constructor; 40/40 across `TurChatFlowPinFlowTest`+`TurChatFlowRouterIndexCacheTest`+`TurChatFlowLinterServiceTest`+`TurChatSlotAuditServiceTest`+`TurLiquibaseChangelogAgnosticTest`+`TurCacheAnnotationConventionsTest` green. Backend compile clean (883 sources). Frontend `tsc -b` green. **Learning**: the engine's transparent-walk path mutates `variablesJson` in bulk inside a single `advance()` round — wrapping each individual `ChatFlowOps.writeVariables` caller would have meant editing dozens of strategy paths; capturing the previous JSON + diffing once at the end of `advance()` records every NODE slot mutation regardless of which strategy / sub-walker produced it. | §VII.6.a |
| T61 | ✅ | — | **PII slot prefix `pii_*`** — three guarantees layered on the existing slot infrastructure. (1) **Encryption at rest**: new `TurPiiSlotService` (`PII_PREFIX="pii_"`, envelope marker `pii_enc:v1:<aes-gcm-base64>`) reuses `TurSecretCryptoService` (AES/GCM). `ChatFlowOps.writeVariables/readVariables` apply `encryptInPlace` / `decryptInPlace` transparently via a Spring-managed `INSTANCE` singleton (`@PostConstruct` register / `@PreDestroy` unregister) — pure unit tests that don't bootstrap Spring see `INSTANCE=null` and the helpers degrade to identity functions so existing test fixtures still work unchanged. Idempotent: encrypt skips already-enveloped values; decrypt only touches values bearing the marker (legacy pre-T61 plain rows stay legible). Crypto failures swallowed + logged (best-effort, never breaks a chat turn). (2) **Redaction in logs/audit**: `TurPiiSlotService.redact(Map)` returns a defensive copy with `pii_*` values replaced by `[redacted]`; static helper `redactValue(slotName, value)` for one-off log lines. `TurChatSlotAuditService.record(...)` redacts old/new values for `pii_*` slots before persisting — preserves "a write happened at T" forensics without storing the plaintext that the encrypt-at-rest layer protects. The pre-existing `[FlowEngine] advance` log already serialized `state.getVariablesJson()`, which after T61 is the encrypted envelope form for PII slots, so log aggregators see `pii_enc:v1:<base64>` rather than plaintext for free. (3) **Configurable TTL**: new Global Setting `GLOBAL_PII_SLOT_TTL_HOURS` (default 24h, clamped `[0, 8760]` server-side; 0 disables enforcement). New `TurPiiSlotTtlCleanupJob` runs hourly (cron `0 23 * * * *`, configurable via `turing.chat.pii.cleanup.cron`) with ShedLock (`piiSlotTtlCleanup`, `lockAtMostFor=PT15M`, `lockAtLeastFor=PT1S`) so multi-node deploys don't race. Walks every `chat_flow_state` whose `updatedAt < now-TTL`, strips `pii_*` keys via `ChatFlowOps.readVariables/writeVariables`, persists. Backend: new `TurGlobalSettingsService.getPiiSlotTtlHours()` / `updatePiiSlotTtlHours(int)` + bean field + API GET/PUT round-trip. **Frontend**: new `piiSlotTtlHours` field on `TurGlobalSettings` model + new amber `SectionCard` ("PII Slot Retention") inside `global-settings.page.tsx` with a number input bounded `[0, 8760]` + EN/PT i18n strings + an out-of-range validation message. 13 new unit tests (`TurPiiSlotServiceTest`) pin: `isPiiSlot` prefix detection (case-sensitive, null-safe), envelope marker recognition, encrypt skips non-PII / already-enveloped / empty values, encrypt swallows crypto failures keeping plaintext, decrypt only touches enveloped values (legacy pre-T61 plain rows pass through untouched), decrypt swallows failures keeping envelope, redact returns immutable copy + leaves empty values empty, `redactValue` helper, TTL pulled from Global Settings, INSTANCE register/unregister lifecycle. 83/83 across `TurPiiSlotServiceTest`+`TurChatSlotAuditServiceTest`+`TurChatFlowPinFlowTest`+`TurChatFlowRouterIndexCacheTest`+`TurChatFlowLinterServiceTest`+`ChatFlowOpsTest`+`TurLiquibaseChangelogAgnosticTest`+`TurCacheAnnotationConventionsTest`+`TurGlobalSettingsBeanTest` green. Backend compile clean; frontend `tsc -b` green. **Learning**: making `ChatFlowOps` (a stateless utility) Spring-aware via dependency injection would have meant promoting it to a service and editing dozens of call sites; the Spring-managed static `INSTANCE` pattern keeps the helper signature stable and lets the encrypt/decrypt feature degrade gracefully to identity in unit tests that don't carry a Spring context. | §VII.6.b |
| T62 | ✅ | T54 | **Webhook handoff channel + admin-declared slot-write webhooks.** New `TurChatWebhook` entity (`tur_chat_webhook`, Liquibase [`v2026.3.1.13.yaml`](../turing-app/src/main/resources/db/changelog/v2026.3.1.13.yaml), camelCase columns + `slotTrigger` index) carrying `name` (unique, doubles as the handoff destination), `targetUrl`, `slotTrigger`, `includeSlots` (csv whitelist), `authHeader` (encrypted at rest via `TurSecretCryptoService`), `enabled`. Repository follows the cache convention (`turChatWebhookfindAll`/`findById`/`findByName` + paired `@CacheEvict` — lint-clean) so the dispatcher's per-write `findAll()` stays off the DB. **Two firing modes, one entity:** (1) **Handoff channel** — new `WEBHOOK` case on `TurChatHandoffService.Channel`; unlike the deep-link channels it does a *server-side* POST of the rendered transcript + filtered slots to the configured CRM endpoint (Salesforce / HubSpot / Pipedrive / Zapier-Make) and writes `handoff_status=webhook_requested` only on success — the existing `POST /chat/handoff` endpoint routes to it automatically via `Channel.valueOf`. (2) **Wider slot subscription** — new `TurChatWebhookDispatcher` subscribes once to `TurChatSlotEventBus.subscribeAll()` and edge-detects which slots actually changed per conversation (bounded LRU last-seen map; the bus carries full snapshots, not deltas — T63 territory), firing each enabled webhook whose `slotTrigger` matches the changed slot name or the wildcard `*`. The outbound POST is handed to a 2-thread daemon pool so a slow CRM never stalls a chat turn; slot-write failures are swallowed + logged, handoff failures are surfaced to the visitor. Payload: `{event: handoff|slot_write, webhook, conversationId, slot?, transcript?, slots}`. The `authHeader` is write-only end-to-end — `TurChatWebhookDto.from` emits `hasAuthHeader` only; a blank value on update preserves the stored cipher (admin edits URL without re-typing the secret). Admin CRUD at `/api/genai/webhook` ([`TurChatWebhookAPI`](../turing-app/src/main/java/com/viglet/turing/api/agent/TurChatWebhookAPI.java), `@Secured` ROLE_ADMIN/AI_AGENT_*) with name + absolute-http(s)-URL validation. **Frontend**: deployment-wide admin UI mirroring the routine pattern — `webhook.model.ts` + `webhook.service.ts` + React-Query hooks (`useChatWebhooks`/`useChatWebhook` + create/update/delete, `chatWebhooks` query keys) + lazy routes (`ChatWebhookRoutes`, `CHAT_WEBHOOK_*` consts) + root/list/edit pages + `WebhookForm` (details / trigger / security `SectionCard`s, password-masked auth field showing a "credential stored" hint when `hasAuthHeader`) + sidebar item (`IconWebhook`) + EN/PT i18n (`webhook.json` + `home.features.chatWebhook`). **Tests**: 18 `TurChatWebhookServiceTest` (normalizeTrigger, matchesSlot wildcard/exact/disabled/blank/null, applyWhitelist passthrough/filter/empty, buildPayload handoff vs slot_write shape, save encrypt/keep-existing/clear, dispatchHandoff blank/unknown/disabled pre-flight, findSlotTriggerWebhooks filtering) + 7 `TurChatWebhookDispatcherTest` (fire-once-on-appear, no-refire-on-unchanged, refire-on-value-change, wildcard-per-changed-slot, non-matching-never-fires, no-backfire-for-already-seen, null-event-ignored) — 27/27 green alongside `TurCacheAnnotationConventionsTest` + `TurLiquibaseChangelogAgnosticTest`. Backend compile clean; frontend `tsc -b --noEmit` exit 0. **Note**: webhooks are deployment-specific endpoints/credentials, intentionally NOT round-tripped through the import/export exchange. **Follow-up (same engagement):** (a) **`payloadTemplate`** column ([`v2026.3.1.14.yaml`](../turing-app/src/main/resources/db/changelog/v2026.3.1.14.yaml)) — optional JSON body template with `{{slot}}` placeholders (JSON-escaped via `renderTemplate`/`jsonEscape` so quotes/newlines can't corrupt the JSON) so the POST matches the CRM's own schema (HubSpot `{"properties":{...}}` etc.); blank = default envelope. `resolveBody` sends the rendered String verbatim (`application/json`, no double-encoding) or the Map. (b) **`httpMethod`** (POST/PUT/PATCH/GET/DELETE — GET/DELETE send no body) + **`headersJson`** (custom request headers as a JSON object; a custom `Authorization` wins over the encrypted `authHeader`) — both on the same `.14` changelog, validated in the API, applied in `post()`. (c) **`webhook` chat-flow node** — new `TurChatWebhookNodeExecutor` fires a named webhook deterministically mid-flow (reuses `functionName` for the webhook name + `continueOnFailure`/`failure` edge, **no `NodeData` schema change** — same T46 philosophy); wired into `walkTransparentNodes` next to `functionCall`; frontend adds the `webhook` member to `FlowNodeType`/`NODE_COLORS`/`DEFAULT_LABEL`/`nodeTypes`/palette (pink, `IconWebhook`) + `WebhookNode` renderer + properties webhook-picker (`useChatWebhooks`) + `typeLabelKey` + EN/PT `chatFlow.json` keys. Engine constructor gained the executor param (3 engine tests updated). +8 tests (`TurChatWebhookNodeExecutorTest` 4 + service renderTemplate/jsonEscape/resolveBody/parseHeaders/flow-node-dispatch 8) — 63/63 green across the webhook + engine + lint sweep; backend compile clean; frontend `tsc -b` exit 0. | §VII.6.c |
| T63 | ✅ | T54 | **Slot delta SSE** — opt-in `GET /chat/slots/stream/delta` emits only `{added, updated, removed}` deltas instead of re-serializing the full map per write (~10× fewer bytes on 20+ slot conversations). New [`TurChatSessionSlotsDeltaDto`](../turing-app/src/main/java/com/viglet/turing/persistence/dto/agent/TurChatSessionSlotsDeltaDto.java) (`snapshot` flag + `added`/`updated`/`removed`; static `snapshot(...)` + pure `diff(before, after)` + `isEmpty()`); new [`TurChatSlotEventBus.subscribeDelta(conversationId, initialSnapshot)`](../turing-app/src/main/java/com/viglet/turing/service/chatslots/TurChatSlotEventBus.java) prepends one `snapshot:true` event (whole map in `added`, resets the client on reconnect) then maps each full-snapshot bus event to an incremental delta vs a per-subscription `AtomicReference` previous, filtering empty deltas so an idle write never hits the wire. Endpoint mirrors `streamSlots` (same 25s comment heartbeat, single-node caveat); **the legacy `/slots/stream` full-snapshot stream is untouched** (non-breaking). **SDK**: the shared SSE multiplexer ([`slots-sse.ts`](../frontend/packages/sdk/src/slots-sse.ts)) gained a `SlotsSseOptions { delta }` arg — in delta mode it opens the delta endpoint, maintains a per-channel running map (snapshot resets it; added/updated upsert, removed delete) and **still surfaces full `TurChatSessionSlots` snapshots to subscribers**, so the `onMessage` contract is identical — only the wire shrinks. Channel key includes the delta flag so delta/snapshot subscribers don't collide. Threaded through the vanilla `createSlotsController` (`sseDelta` option) + React `useTuringSlots` (`sseDelta` option) + re-exports + new `TurChatSessionSlotsDelta` type. 7 backend tests (`TurChatSessionSlotsDeltaDtoTest` 4: snapshot/diff add+update+remove/identical-empty/null-maps; `TurChatSlotEventBusDeltaTest` 3: initial-snapshot-then-incremental-deltas, no-delta-on-unchanged-republish, other-conversations-isolated — driven synchronously off the multicast sink, no StepVerifier needed). Backend compile clean; vanilla SDK + react-sdk + turing-app `tsc` all exit 0. | §VII.6.d |
| T64 | ✅ | T58 | **Multi-modal slots (`IMAGE`/`AUDIO`/`FILE`).** Three new members on [`TurAIAgentSlotType`](../turing-app/src/main/java/com/viglet/turing/persistence/model/agent/TurAIAgentSlotType.java) (+ `isMultiModal()` helper) whose runtime value is a **URL to a binary in object storage** rather than an inline scalar — the `ai_agent_slot.type` column is already `varchar(16)` so no Liquibase change was needed. New [`TurChatMultiModalSlotService`](../turing-app/src/main/java/com/viglet/turing/service/chatslots/TurChatMultiModalSlotService.java) (sibling of the T58 document path): (1) persists the upload via [`TurStorageService`](../turing-app/src/main/java/com/viglet/turing/service/storage/TurStorageService.java) (`uploadStream`) under a conversation-scoped prefix `chat-slots/{conversationId}/{slot}/{filename}` — filename + path segments sanitised against traversal; (2) writes the resolved **preview URL** (`/api/asset/preview?objectName=…`, the storage-agnostic `TurAssetAPI` proxy that already serves MinIO/filesystem binaries inline) into the slot via `TurChatFlowEngineService.writeSlot(..., TurChatSlotAuditSource.UPLOAD, ...)` so the React UI renders straight from the slot value through the T54 SSE channel; (3) **optionally**, for `IMAGE` slots with `vision=true`, hands the bytes to the agent's vision LLM as a Spring AI `Media` block (mirroring `TurChatAttachmentService`) and fills the agent's **scalar** slots — the headline "photo of a CV → name / cargo / objetivo slots" use case — tagged `EXTRACT` like the document path. Vision failures are swallowed so a miss never undoes the successful binary write. New `TurChatSlotAuditSource.UPLOAD`. The T58 `buildSystemPrompt` + tolerant `parseJsonReply` were promoted to package-private static and reused (no duplication of the structured-output contract). REST: `POST /api/sn/{site}/chat/slot-upload` on [`TurSNSiteGenAiAPI`](../turing-app/src/main/java/com/viglet/turing/api/sn/genai/TurSNSiteGenAiAPI.java) (multipart `file` + `conversationId` + `slotName` + `vision` + optional `visionSlotNames` whitelist) → `ChatSlotUploadResponse(slotName, slotType, objectName, url, contentType, size, visionExtracted, visionSlotsWritten, error)`; unauthenticated like the rest of `/chat/*`; errors cleanly when storage is disabled / slot unknown / slot not multi-modal. **Frontend**: `IMAGE`/`AUDIO`/`FILE` added to `TurAIAgentSlotType` + `TUR_AI_AGENT_SLOT_TYPES` + new `TUR_MULTI_MODAL_SLOT_TYPES`/`isMultiModalSlotType()` helpers in [`ai-agent-slot.model.ts`](../frontend/apps/turing-app/src/models/agent/ai-agent-slot.model.ts) (slot CRUD page picks them up automatically); EN/PT `aiAgent.slot.types.*` i18n. 11 unit tests ([`TurChatMultiModalSlotServiceTest`](../turing-app/src/test/java/com/viglet/turing/service/chatslots/TurChatMultiModalSlotServiceTest.java)): upload happy path (URL slot + UPLOAD audit + storage call), storage-disabled / unknown-slot / non-multi-modal / empty-file / null-agent / blank-conv / blank-slot errors, vision pass fills scalar slots (real `ChatResponse`/`Generation`/`AssistantMessage`), vision skipped when not requested + skipped for AUDIO even when requested, object-name path-traversal stripping + blank-filename default. Verified: 11/11 green; `TurCacheAnnotationConventionsTest` + `TurLiquibaseChangelogAgnosticTest` + `TurChatSlotAuditServiceTest` + `TurChatFlowLinterServiceTest` (24/24) green; backend compile clean; frontend `tsc -b --noEmit` exit 0. | §VII.6.e |
| T65 | ✅ | — | **Snapshot export of conversation state.** New [`TurChatSessionExportDto`](../turing-app/src/main/java/com/viglet/turing/persistence/dto/agent/TurChatSessionExportDto.java) bundles everything a debugger / regression harness needs to reconstruct a conversation into one JSON document: `exportedAt` stamp + the raw `chat_flow_state` rows (nested `FlowState` record — `stateId`/`flowId`/`flowName`/`currentNodeId`/`parentStateId`/parsed `variables` map/`updatedAt`, newest-first) + finished `chat_flow_submission` runs + the merged conversation-scoped `slots` map + the chat-memory `transcript` (with `transcriptEngine`/`transcriptEnabled` reporting the backing store) + the T60 slot-write audit trail. Two new `@Transactional(readOnly = true)` engine reads on [`TurChatFlowEngineService`](../turing-app/src/main/java/com/viglet/turing/genai/flow/TurChatFlowEngineService.java): `exportFlowStates(conversationId)` (maps the raw state rows through `ChatFlowOps.readVariablesJson`) and `listSubmissionsForConversation(conversationId)` (conversation-scoped sibling of `listSubmissions(flow)`); `slots`/`transcript`/`slotAudit` reuse the existing `listSlotsForConversation` / `chatMemoryService.listMessages` / `slotAuditService.list`. REST: new `GET /api/chat/sessions/{conversationId}/export?transcriptLimit=1000` on [`TurChatSessionAPI`](../turing-app/src/main/java/com/viglet/turing/api/chat/TurChatSessionAPI.java) (`@Secured` ROLE_ADMIN/AI_AGENT_VIEW), served as a **`Content-Disposition: attachment`** JSON download (`conversation-<sanitized-id>.json`; filename sanitiser replaces anything outside `[A-Za-z0-9._-]` with `_` + caps at 80 chars to block header/path injection from opaque cookie ids). `pii_*` values stay encrypted-at-rest / redacted exactly as the T60/T61 layers stored them. **Frontend**: new `TurChatSessionExportService.download()` ([`chat-session-export.service.ts`](../frontend/apps/turing-app/src/services/chat/chat-session-export.service.ts)) GETs the blob and triggers a browser download; an "Export conversation" download-icon button next to the copy-id button inside [`ChatSessionInfoSheet`](../frontend/apps/turing-app/src/app/console/chat/components/chat-session-info-sheet.tsx) (disabled while in-flight, success/error toasts) + EN/PT `chat.sessionInfo.export*` i18n. 3 unit tests ([`TurChatSessionExportTest`](../turing-app/src/test/java/com/viglet/turing/api/chat/TurChatSessionExportTest.java)): bundles all four sources into the DTO, serves a JSON attachment with a sanitised filename (path separators / spaces stripped), and propagates `transcriptLimit` to the memory service — 3/3 green; backend compile clean; frontend `tsc -b --noEmit` exit 0. | §VII.6.f |
| T66 | ✅ | — | **Per-agent submission retention policy** (LGPD / GDPR compliance hook). New [`TurSubmissionRetention`](../turing-app/src/main/java/com/viglet/turing/persistence/model/agent/TurSubmissionRetention.java) enum (`RETAIN_FOREVER` / `RETAIN_DAYS` / `DELETE_AFTER_EXPORT`, stored as a string column to avoid ordinal drift) + two fields on [`TurAIAgent`](../turing-app/src/main/java/com/viglet/turing/persistence/model/agent/TurAIAgent.java): `submissionRetention` (`@Enumerated(STRING)`, `varchar(20)`, default `RETAIN_FOREVER` — preserves pre-T66 behaviour) and nullable `submissionRetentionDays` (`int`), via idempotent Liquibase [`v2026.3.1.15.yaml`](../turing-app/src/main/resources/db/changelog/v2026.3.1.15.yaml) (two `columnExists`-gated `addColumn`s). **Scope boundary**: this governs only the completed `chat_flow_submission` archive — live `pii_*` slot expiry stays the separate concern of the T61 `TurPiiSlotTtlCleanupJob`; the two layers compose (T61 strips PII from in-flight conversations on a global hourly TTL, T66 purges the per-agent completed-submission record). New [`TurSubmissionRetentionService`](../turing-app/src/main/java/com/viglet/turing/service/chatslots/TurSubmissionRetentionService.java) with two entry points: (1) `purgeExpiredSubmissions()` — iterates agents, and for each in `RETAIN_DAYS` mode with a positive day count deletes submissions via the new derived query `TurChatFlowSubmissionRepository.findByFlow_TurAIAgent_IdAndCompletedAtBefore(agentId, cutoff)` (`cutoff = now - days`); `RETAIN_FOREVER`, `DELETE_AFTER_EXPORT`, and `RETAIN_DAYS`-with-null/non-positive-days are skipped so a half-configured policy never silently deletes; (2) `applyPostExportRetention(conversationId)` — loads the conversation's submissions and deletes only those whose owning agent (`flow.turAIAgent`) is in `DELETE_AFTER_EXPORT` mode (handles multi-agent conversations row-by-row), best-effort with `RuntimeException` swallowed so a retention hiccup never breaks the export download. The daily sweep is the cron `@SchedulerLock`'d [`TurSubmissionRetentionCleanupJob`](../turing-app/src/main/java/com/viglet/turing/service/chatslots/TurSubmissionRetentionCleanupJob.java) (`0 41 3 * * *`, overridable via `turing.chat.submission.cleanup.cron`; ShedLock `submissionRetentionCleanup`, `lockAtMostFor=PT30M`). The `DELETE_AFTER_EXPORT` path is wired into the T65 export endpoint — [`TurChatSessionAPI.exportConversation`](../turing-app/src/main/java/com/viglet/turing/api/chat/TurChatSessionAPI.java) builds the response, then calls `submissionRetentionService.applyPostExportRetention(conversationId)` before returning (the export is treated as the system-of-record archive). **Plumbing**: `TurAIAgentAPI.turAIAgentUpdate` copies both fields field-by-field (null-guarded enum like `chatMode`; days copied verbatim since null is valid); create auto-maps via MapStruct; export/import carry them through [`TurAIAgentExchange`](../turing-app/src/main/java/com/viglet/turing/exchange/agent/TurAIAgentExchange.java) `fromEntity` + [`TurAIAgentImportService.applyAgentScalars`](../turing-app/src/main/java/com/viglet/turing/exchange/agent/TurAIAgentImportService.java) (null-guarded so pre-T66 exports keep the `RETAIN_FOREVER` default). **Frontend**: `TurSubmissionRetention` type + two fields on [`ai-agent.model.ts`](../frontend/apps/turing-app/src/models/agent/ai-agent.model.ts); a new amber "Submission retention" `SectionCard` (`IconShieldLock`) in [`ai-agent.settings.form.tsx`](../frontend/apps/turing-app/src/components/agent/ai-agent.settings.form.tsx) with a policy `<select>` + a conditional days `Input` (shown only for `RETAIN_DAYS`, bounded `[1, 3650]`); EN/PT `forms.agentSettings.submissionRetention*` i18n. **Tests**: 13 `TurSubmissionRetentionServiceTest` (purge: RETAIN_DAYS deletes + cutoff math, RETAIN_FOREVER/DELETE_AFTER_EXPORT no-op, null/zero/negative days skipped, empty list no delete, mixed agents; post-export: deletes DELETE_AFTER_EXPORT rows, keeps other modes, mixed conversation row-by-row, blank conversation no-op, null flow/agent safe, persistence failure swallowed) + `TurChatSessionExportTest` extended (+1: export fires the post-export hook; constructor updated for the new dep). 19/19 green across the new + export + `TurLiquibaseChangelogAgnosticTest` + `TurCacheAnnotationConventionsTest` sweep; 22/22 across `TurAIAgentAPITest` + `TurAIAgentExportServiceTest` + `TurAIAgentMapperTest` + `TurAIAgentDomainTest`; backend compile clean; frontend `tsc -b --noEmit` exit 0. | §VII.6.g |
| T233 | 💭 | — | **API-Key auth layer for the public chat/search surface (reusing Turing-registered API keys).** Today the visitor-facing endpoints — `/api/sn/*/search`, `/api/sn/*/ac`, `/api/sn/*/query/**`, `/api/sn/*/click`, `/api/sn/*/chat`, `/api/sn/*/chat/**` (incl. the new T64 `slot-upload` + T58 `slot-extract` + T55 `handoff` + T62 webhook-backed handoff) — are wired `permitAll()` in both branches of [`TurSecurityConfigProduction`](../turing-app/src/main/java/com/viglet/turing/spring/security/TurSecurityConfigProduction.java) (the Keycloak branch and the JPA/basic branch), so anyone who can reach the host can drive an agent, burn LLM tokens, and write slots/upload binaries anonymously. **Reuse the existing key infrastructure instead of inventing a new one**: Turing already has admin-registered API keys via [`TurDevToken`](../turing-app/src/main/java/com/viglet/turing/domain/dev/TurDevTokenDomain.java) (CRUD + `TurDevTokenRepository.findByToken`) and the [`TurAuthTokenHeaderFilter`](../turing-app/src/main/java/com/viglet/turing/spring/security/auth/TurAuthTokenHeaderFilter.java) that validates the `Key` HTTP header and authenticates the request as the token's creator user (already on the chain via `http.addFilterBefore(turAuthTokenHeaderFilter, BasicAuthenticationFilter.class)`). **Scope**: (1) introduce an **opt-in** enforcement flag so existing open-by-default embeds don't break — a global setting `GLOBAL_CHAT_REQUIRE_API_KEY` (Global Settings, default `false`) and/or a per-`TurSNSite` boolean `requireApiKey` so a single deployment can keep a public search site open while gating a lead-capture agent; (2) when enforced, the public matchers move from `permitAll()` to `.authenticated()` (or a custom `AuthorizationManager` that checks for a valid `TurDevToken`-derived authentication) so a missing/invalid key returns 401 via the existing `TurAuthenticationEntryPoint` (`{"error":"Unauthorized"}`, `application/json`); (3) accept the key the way browser/SDK embeds can actually send it — the `Key` header (server-to-server), `Authorization: Bearer <key>` (align with the T39 `http.bearer` convention + T146 MCP API-key gating), and optionally a signed/short-lived query param for `<script>`-tag widgets that can't set headers; (4) **scope a token to specific sites/agents** (new join or a CSV column on `TurDevToken`) so a leaked widget key can't be replayed against every site, and so per-key rate limits / analytics segmentation become possible; (5) thread the resolved key identity into the T60 slot audit (`originDetail="key=<tokenTitle>"`) and the chat-session analytics so writes are attributable. **SDK + frontend**: the vanilla `@viglet/turing-sdk` `createTuringClient` and the React SDK gain an `apiKey` option that sets the header on every request (same place `ensureXsrfToken` lives — the SDK already primes CSRF via `GET /csrf`); admin gets a "Require API Key" toggle on the SN-site security tab + a per-key site/agent scope picker on the existing Dev Token page. **Tests**: filter-level (valid key → authenticated, missing/invalid → 401, scope mismatch → 403), security-config slice (enforced vs open matchers), and an SDK contract IT extension (the T-`TurSdkContractIT` Lucene pattern) asserting a keyed request succeeds and an unkeyed one is rejected when enforcement is on. **Non-breaking**: default `false` preserves today's anonymous posture; the gate is a deploy-time/admin decision, mirroring the storage-type and permissions toggles. | §VII.6.h |

#### D.4 — A/B testing evolution

| #   | Status | Depends on | Task | Detail in |
|-----|--------|------------|------|-----------|
| T67 | ✅ | — | A/B testing native: 5 columns on `TurChatFlow` (`experimentKey`, `variantLabel`, `trafficWeight`, `experimentStartsAt`, `experimentEndsAt`) + `assignVariant` deterministic hash + sticky continuation + `isInExperimentWindow` scheduling filter. | §VII.7.a |
| T68 | ✅ | T67 | A/B significance endpoint `/experiment/{key}/significance` — two-proportion z-test with Wald CI. Tells operator when to retire variant. | §VII.7.b |
| T69 | ✅ | T67 | A/B analytics dimensions — `experimentKey` + `variantLabel` persisted in `TurChatSessionEvent`; scorecard supports 4 dimensions (agent / persona / experiment / variant). | §VII.7.c |
| T70 | ✅ | T67 | **A/B multi-armed bandit (Thompson sampling)** — new per-flow `banditEnabled` Boolean column on `TurChatFlow` (Liquibase `v2026.3.1.9.yaml`); when at least one variant in an experimentKey has it set, the engine swaps the deterministic weighted-hash `assignVariant(...)` for Thompson sampling on the Beta-Bernoulli posterior. New `TurAbBanditService` reuses `TurExperimentSignificanceService.collectVariantStats(...)` (made `public` from `private` so the bandit and the significance test share ONE definition of "success") to pull (sessions, successes) per variant over the last 30 days; for each variant samples from `Beta(successes+1, failures+1)` with the conventional Laplace prior, picks the highest sample. **Pure-Java sampler** — Marsaglia & Tsang's acceptance-rejection Gamma sampler plus the `Beta(α,β) ≡ X/(X+Y)` identity, no commons-math dep (the project doesn't ship one). Cold start safety: when no analytics data is reachable (Mongo/Redis disabled or experiment freshly created) every variant defaults to `Beta(1,1)=Uniform(0,1)` so the picker degrades to uniform random; analytics failures (`RuntimeException` from the store) are caught + logged and the picker proceeds as if no data was found. RNG seam: constructor accepts a `Random` for test determinism; production wires `ThreadLocalRandom.current()`. The engine's `resolveAbVariant` branches on the new static `TurAbBanditService.isBanditExperiment(variants)` so plain weighted-hash experiments incur zero overhead. Round-tripped through the import/export exchange (`TurChatFlowExchange.banditEnabled`) and the chat-flow REST update (`TurChatFlowAPI` PUT). **Frontend**: new `banditEnabled?: boolean` field on `TurChatFlow` model; chat-flow editor's settings tab gains a `Switch` ("Bandit (Thompson sampling)") below the traffic-weight input — the traffic-weight input is disabled while bandit mode is on (visually telegraphs that weight is ignored). Both fields are gated on a non-blank `experimentKey` (no A/B → both controls disabled). EN/PT inline defaults via `t(...defaultValue)`. 10 new unit tests (`TurAbBanditServiceTest`) pin: empty/null input rejection; single-variant returns without sampling; deterministic-seeded convergence on a wide separation (200 trials, 80% vs 20%, A wins >95%); cold-start uniform distribution (binomial CI ±60 around 500 over 1000 trials); analytics failure swallowed; `isBanditExperiment` true on any banditEnabled=true variant; `Beta` samples stay in `[0,1]`; mean of `Beta(5,15)` over 20k samples lands within `±1.5%` of the analytic `α/(α+β)=0.25`; Gamma samples positive across shape values from 0.1 to 100; partial stats (one variant analytics-tracked, one cold) still distributes traffic between both. 66/66 across T60+T61+T70 audit/PII/bandit + flow-engine related tests green. Three engine tests updated to pass the new `abBanditService` mock to the constructor. Backend compile clean; frontend `tsc -b` green. **Learning**: extracting Marsaglia-Tsang inline keeps the diff self-contained (no new Maven dep), the `Random` seam keeps tests deterministic, and reusing `collectVariantStats` from the significance service means bandit + z-test agree on what counts as a success — a future per-experiment success metric override (handoff, lead-capture) plugs in by passing the metric enum through `pickVariant`. | §VII.8.a |
| T71 | ✅ | T68, T70 | **Champion-challenger auto-promotion** — closes the A/B loop: T68 says *whether* a variant won, T71 *acts* on it by pinning the winner to 100% traffic and archiving the losers. New `TurChampionChallengerService` (`service/chatanalytics/`) with one promotion routine shared by two entry points: (1) `promote(experimentKey, metric, alpha, dryRun)` re-runs `TurExperimentSignificanceService.evaluate(...)` — so promotion uses the **exact same** two-proportion z-test + `MIN_TOTAL_SAMPLE` gate the dashboard banner shows — and, only when `sig.winner() != null`, loads the arms via the new (non-cached, rare-admin) `TurChatFlowRepository.findByExperimentKey(key)`, pins the winner (`trafficWeight=100`, `banditEnabled=false`, schedule window untouched) and **archives** each loser (`trafficWeight=0` → excluded from fresh assignment but past sessions stay analyzable, `banditEnabled=false`, `experimentEndsAt=now` → `isInExperimentWindow` now returns false so the engine reassigns its traffic to the champion); underpowered / not-yet-significant / unknown-winner-label / no-flows-for-key all return `applied=false` with the significance recommendation as the reason; `dryRun=true` computes the winner+archived-labels plan without persisting (UI preview). (2) `runScheduledPromotions()` scans `findAll()`, collects experimentKeys where **any** variant opted in (same "any-variant-flips-the-group" semantics as bandit), and promotes each — one experiment's analytics hiccup is caught + logged so the sweep continues. Daily ShedLock job `TurChampionChallengerPromotionJob` (`0 47 3 * * *`, overridable `turing.chat.champion.promote.cron`; lock `championChallengerPromote`, `lockAtMostFor=PT30M`). **Opt-in per project convention** (behaviour changes ship as a choosable per-entity flag, default=legacy): new `autoPromote` Boolean column on `TurChatFlow` (idempotent Liquibase `v2026.3.1.16.yaml`, `columnExists`-gated); null/false → manual control only, and the **manual endpoint ignores the flag** (an operator click is itself the opt-in). REST: `POST /api/system/chat-analytics/experiment/{key}/promote?successMetric=&alpha=&dryRun=` on `TurChatAnalyticsAPI` (`@Secured` ROLE_ADMIN/AI_AGENT_VIEW), returning a `PromotionResult` record (`applied`, `dryRun`, `winner`, `winnerFlowId`, `pValue`, `archivedVariants`, `reason`). Round-tripped through `TurChatFlowExchange.autoPromote` + `TurAIAgentImportService` (null-guarded so pre-T71 exports stay manual) + `TurChatFlowAPI` PUT. **Frontend**: `autoPromote?: boolean` on the `TurChatFlow` model; a "Auto-promote winner (champion-challenger)" `Switch` below the bandit toggle in the chat-flow editor's A/B card (gated on a non-blank `experimentKey`, inline EN/PT `defaultValue`); page state coupled to `experimentKey` (clearing the key nulls it, same as bandit). 6 unit tests (`TurChampionChallengerServiceTest`): promotes winner + archives loser (weights, bandit-cleared, window-closed-on-loser/open-on-winner, both saved); no-op on no-winner (no repo touch); no-op + nothing-mutated on winner-label mismatch; no-op on empty flow set; dry-run computes plan but never saves; scheduled sweep promotes only the opted-in experimentKey and skips the rest. 32/32 green across the new test + `TurAbBanditServiceTest` + `TurChatFlowAbVariantAssignmentTest` + `TurCacheAnnotationConventionsTest` + `TurLiquibaseChangelogAgnosticTest`; backend compile clean. **Learning**: reusing `evaluate(...)` (not just `collectVariantStats`) keeps promotion and the operator-facing significance banner in lockstep — they can never disagree on "is there a winner". "Archive" = weight 0 **plus** `experimentEndsAt=now` rather than disabling the flow, so the loser keeps running as a normal (non-experiment) flow if the router still picks it, while its A/B traffic drains to the champion. | §VII.8.b |
| T72 | 💭 | T67 | Per-node A/B (`ChatFlowNode.nodeExperimentKey`) — variants swap a single node, not the whole flow. Granular optimization without duplicating 50 nodes. | §VII.8.c |
| T73 | 💭 | T67 | Forced variant via URL param (`?_ab_variant=lucas-alumni`) — useful for QA + sales demo. | §VII.8.d |
| T74 | 💭 | T69 | Cohort filters in scorecard — filter by timezone / locale / user-agent (mobile vs desktop) to see if a variant wins in a sub-population. | §VII.8.e |
| T75 | ✅ | T69 | **Variant trace logging** — `TurChatFlowEngineService.advance(...)` now emits one structured `[A/B Trace] experimentKey:variantLabel:nodeId:userMessage` log line per turn whenever the active flow has a non-blank `experimentKey`; plain (non-experiment) flows are silent. The formatter is extracted as a static helper `formatVariantTrace(TurChatFlow, String nodeId, String userMessage)` so the wire format is pinned by a pure-function unit test (`TurChatFlowVariantTraceTest`, 7 cases — null flow / blank or whitespace key skipped, canonical four-field shape, `(unset)` substitution when `variantLabel` missing, 200-char message truncation with ellipsis, CR/LF collapsed to spaces so one turn = one line, null nodeId + null userMessage tolerated). The trace fires at the end of `advance()` AFTER `walkTransparentNodes` so the `nodeId` field reflects the post-walk position the next turn will actually consume — gives operators the same forensic ground truth the engine itself uses. 20/20 green across `TurChatFlowVariantTraceTest` + `TurChatFlowAbVariantAssignmentTest`; 107/107 green across the broader engine + chat-flow test sweep. Backend compile clean. **Learning**: a package-private static formatter is the cheapest seam for "log this structured line per turn" — keeps the `advance(...)` call site to a one-liner (`formatVariantTrace(...).ifPresent(t -> log.info("[A/B Trace] {}", t))`), zero overhead for non-experiment flows (one null/blank check), and the test surface is a pure-function table without any Spring context or `ListAppender` plumbing. Pairs with T70 (Thompson sampling) and T69 (scorecard) by giving a path-level forensic source when an aggregate metric surfaces a regression — operators grep `[A/B Trace]` in the production log to reconstruct the exact node sequence for a given variant. | §VII.8.f |

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
| T85 | ✅ | T84 | **Funnel visualization (V1)** — backend service + REST + sidebar panel. **Pragmatic scope**: without a per-turn node visit log (would need a new `chat_node_visit` table), the funnel reports what existing tables already track — `chat_flow_state.currentNodeId` ("currently parked here") + `chat_flow_submission.endNodeId` ("ended here"). Drop-off is read visually by comparing the cursor counts of consecutive interactive nodes in graph order; a full path-aware funnel is tracked as a T85 follow-up. New `TurChatFlowFunnelService.computeFunnel(flowId)` returns `FunnelReport(flowId, flowName, totalStates, totalSubmissions, abandonedAtFlow, List<FunnelNode>)`. FunnelNode = `(nodeId, label, type, cursorCount, completedCount, abandonedCount)`. Transparent nodes (`start`/`condition`/`switch`/`persona`/`subFlow`/`subFlowSwitch`/`writeSlot`/`functionCall`/`scheduleAgent`) are excluded so the funnel reads as user-facing steps only. New repository methods `TurChatFlowStateRepository.countByCurrentNodeForFlow(flowId)` + `TurChatFlowSubmissionRepository.countByEndNodeForFlow(flowId)` use `@Query` JPQL aggregations (group-by node id) — single round-trip per call, no per-row scan. REST: new `GET /api/ai-agent/{agentId}/chat-flow/{id}/funnel` on `TurChatFlowAPI` (secured `ROLE_ADMIN`/`AI_AGENT_VIEW`), 404 when the flow id is unknown / doesn't belong to the agent in the path. **Frontend**: new `TurChatFlowFunnelNode` + `TurChatFlowFunnelReport` models, `funnel(agentId, flowId)` method on `TurChatFlowService`, `useChatFlowFunnel` React Query hook (30s `refetchInterval` so an admin tweaking the graph watches the cursor counts shift live), new query key `queryKeys.chatFlows.funnel(...)`. New `ChatFlowFunnelPanel` component renders a vertical stack of nodes inside the editor's settings tab (positioned right under the existing lint panel): each row shows the type badge, label, parked count `●N` (amber) + completed count `✓N` (emerald), and a proportional bar (blue→indigo gradient) sized by `(cursor+completed)/maxCount`. Hidden when no states/submissions are captured yet — keeps a brand-new flow's editor uncluttered. 6 new unit tests (`TurChatFlowFunnelServiceTest`) pin: blank/null flow id returns empty, missing flow returns empty, unparseable graph returns empty, transparent nodes excluded with start/condition stripped + end kept as terminal anchor, empty stats tables return zero counters, malformed count rows (null id / short row / non-numeric count) tolerated. 6/6 green. Backend compile clean; frontend `tsc -b` green. **Learning**: shipping a useful V1 without the proper traversal log buys real value today (operator sees "12 conversations parked on ai-cargo" immediately) and leaves the door open for a future per-turn log to power the full path-aware version without changing the API surface — the report's `abandonedCount` field is already in the wire format ready for the follow-up. | §VII.10.b |
| T86 | ✅ | T84 | **Conversation replay UI (V1)** — operators click `conversationId` in the scorecard and the existing chat-analytics `DetailPanel` now renders a unified **Replay timeline** card that interleaves chat-memory messages with T60 slot-audit entries by timestamp. Each row carries an absolute time, a source badge (user / assistant / NODE / TOOL / ENDPOINT / EXTRACT) and the payload — for messages the text, for slot writes a `(oldValue → newValue)` delta with `(empty)`/`—` fallbacks. **No new endpoints** — reuses the existing `useChatAnalyticsTranscript` hook for messages and the T60 `useChatSlotAudit` hook (polling disabled in the detail context — historical sessions don't move, no point keeping traffic alive). Sort is stable: timestamps parse to ms-epoch, messages with no timestamp degrade to insertion order at the tail. The pre-T86 standalone transcript card was replaced by the new unified `ReplayTimeline` component. Sheet width bumped from `sm:max-w-2xl` to `sm:max-w-3xl` so the slot delta `pii_enc:v1:...` envelopes don't wrap awkwardly. **Scope-deliberate omission**: no scrubber (yet) — the brief mentioned "scrubber temporal" but events are already chronologically rendered top-to-bottom and the scroll-position-driven discovery covers ~95% of the forensic value. A scrubber that filters events around a target ts is a clean follow-up once V1 reveals which scrub semantics the operators actually want (time-window vs current-turn-focused). Frontend `tsc -b` green. **Learning**: many "UI feature" tickets are really "render existing endpoint data in a new shape" — T60+T86 together took the same conversationId lookup the operator was already running and made the slot-write attribution visually adjacent to the messages that produced it, without any new backend surface. | §VII.10.c |
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
| T97 | ✅ | — | **Variant generator via LLM** — `TurChatFlowVariantService` parses the source `definitionJson`, extracts a node-by-node copy table (`label` / `aiInstruction` / `conditionExpression` / `slotValue`) and asks the default LLM (via `TurLlmSummaryService`) to rewrite it per a free-text directive (`"more casual tone, 30% shorter"`, `"translate to Portuguese"`). Structural fields (ids, edges, types, `outputVariable`, `validationRule`, tool wiring, slot ops) round-trip verbatim. `POST /api/ai-agent/{agentId}/chat-flow/{id}/variant` returns an UNPERSISTED candidate (`id == null`, `enabled = 0`) so the author can iterate without polluting the flow list; the editor's `IconSparkles` action opens `ChatFlowVariantDialog` (instructions + optional name → generate → review summary + rewritten-node count → save via standard create endpoint and navigate to the new flow). Recipe lineage is wiped on the candidate. | §VII.11.g |
| T98 | ✅ | — | **TypeScript DSL** for chat-flow — new monorepo package [`@viglet/turing-flow-dsl`](../frontend/packages/flow-dsl/) with literal-union types mirroring every backend enum (`GuardrailMethod`, `TriggerMode`/`Language`, `NodeType`, `ToolSource`, `SlotOperation`, `OnJudgeReject`, `ValidationRule`) plus a discriminated `FlowNode` union covering all 11 node types. `defineFlow()` is an identity helper preserving literal inference so `triggerMode: "ONCEE"` fails at `tsc` time; `transpileFlow()` validates structure (single start, no dup ids, reachable nodes, condition has yes/no, switch handles match `switchOptions.id`), auto-fills `data.label` defaults, lays nodes in a vertical waterfall, synthesizes `e1/e2/…` edge ids, and emits the editor's wire shape (matches `ChatFlowExportEntry` consumed by `buildImportPayloadFromExport`). Zero-dep CLI `turing-flow-dsl build <dir> --out <dir>` ingests both `*.flow.mjs/.js` (dynamic-imported compiled DSL modules) and `*.flow.json` (raw editor exports, normalized via `reverseTranspile` → `transpileFlow`) — the JSON path satisfies the "back-compat reading raw JSON" requirement so a team can mix hand-authored JSON and typed DSL in one `flows/` directory. 10 `node:test` tests; canonical lead-capture example mirrors the shipped `lead-capture-b2c.json` recipe. | §VII.11.h |

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
| T106 | ✅ | T92 | **`useTuringFlowChooser` hook** in `@viglet/turing-react-sdk` — imperative `chooseFlow(flow?)` pins a flow by UUID or case-insensitive name through the T92 `POST /chat/flow-select` endpoint (new `postSiteFlowSelect` API helper + `TurChatFlowSelectResponse` type). Exposes `status` (`idle/selecting/success/error`), `error`, `lastResult`, `pinnedFlowId/Name`. `mintSession` (default on) proactively mints the `TUR_SESSION` cookie so a deep-link landing pins before the visitor types — the chat hook reuses the same cookie. Opt-in `autoSelectFromParam` reads a URL query param once on mount (`?flow=in-company`), pinning the deep-linked persona; falls back to `options.flow` when the param is absent. Storybook story (track-picker simulation, latency/error variants). | §VII.13.c |
| T107 | 💭 | — | `formCapture` form-event SSE — emit structured event so SDK renders a native form (multi-field capture) instead of single-turn validation. | §VII.13.d |

#### D.10 — Kill list (deprecate or remove)

| #    | Status | Task | Rationale |
|------|--------|------|-----------|
| TK1 | 📋 | Deprecate `HeuristicGuardrailStrategy` (default → `LLM_JUDGE` for new flows; kept for back-compat) | Regex-only. `LLM_JUDGE` + tool-calling with provider schema does the job better. Third option that nobody picks pollutes the UI. |
| TK2 | 📋 | Remove slot types `INTEGER` / `BOOLEAN` from the DSL | Client-side parser via `useTuringSlot<T>` does the coercion better. Schema-level types only generate coercion bugs. |
| TK3 | ✅ | Remove `forbidsToolCalls(String)` substring overload | Already shipped — T16 deleted the bilingual-substring overload alongside the regen helpers (`forbidsToolCalls(String)`, `regenerateReplyForNewNode`, `buildRegenOverride`, `trimToRecentTurns`, `REGEN_HISTORY_TURN_BUDGET`). Verified 2026-05-26: only `forbidsToolCalls(ChatFlowNode)` survives in [`TurChatToolResolver.java:160`](../turing-app/src/main/java/com/viglet/turing/genai/TurChatToolResolver.java#L160), reading `node.toolsEnabled()` declaratively. The `@Nested ForbidsToolCallsUnitTests` block in [`TurAgentChatExecutorStructuralIT`](../turing-app/src/test/java/com/viglet/turing/genai/TurAgentChatExecutorStructuralIT.java) only exercises the surviving node-aware overload (4 tests pin: null node → false; `toolsEnabled=TRUE` overrides legacy sentinel; `toolsEnabled=FALSE` strips tools; null + sentinel → false, asserting the substring fallback is gone). No production code path scans `aiInstruction` for bilingual sentinels anymore. |
| TK4 | 📋 | Extend `functionCall` node runtime to support `toolSource=MCP` | T46 shipped the NATIVE path; MCP is currently acknowledged + skipped with a warning. Either wire `TurMcpToolCallbackService.getToolCallbacks(...)` into `TurFunctionCallNodeExecutor` (needs an MCP-server resolution by `mcpServerId`) OR drop `"MCP"` from the schema enum. |
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
| T109 | ✅ | — | **`agent.invoke(agentId, message, opts)` Custom Tool helper** — new [`TurCustomToolAgentHelper`](../turing-app/src/main/java/com/viglet/turing/genai/tool/TurCustomToolAgentHelper.java) bound to Groovy scripts as the `agent` variable. Two-arg form `invoke(agentId, message)` uses defaults; three-arg form accepts `opts = [timeout: ms, llmInstanceId: String]`. Spawns a fresh child conversation under the target agent's first enabled LLM (or the explicitly chosen one when supplied via `opts.llmInstanceId`); the parent `conversationId` is captured as a `[__parentConversationId=...]` prefix on the child's first user turn so T110 can reconstruct the call tree from the analytics log without a DB schema change. **Recursion cap** — `DEFAULT_DEPTH_CAP=3`, propagated through a new `TOOL_CONTEXT_AGENT_INVOKE_DEPTH` Spring AI tool-context key + a new 8-arg `TurAgentChatExecutor.execute(..., int agentInvokeDepth)` overload that re-publishes the depth into the child turn's `ChatOptions.toolContext`. A call that would exceed the cap returns the literal `[agent.invoke depth cap reached]` rather than throwing, matching every other failure mode (agent missing, agent disabled, no LLM configured, LLM not allowed, timeout, runtime error) — all returned as bracketed sentinel strings so the orchestrator LLM sees a stable failure shape. **Cycle-safe DI** — `TurCustomToolCallbackService` injects `TurAgentChatExecutor` with `@Lazy` to break the import cycle `TurAgentChatExecutor → TurChatToolResolver → TurCustomToolCallbackService → TurAgentChatExecutor`. **Blocking semantics** — the helper subscribes to `executor.execute(...).collectList()` and blocks up to `timeout` ms (default 30s, capped at 300s); tokens with `type == "token"` are concatenated, non-token events ignored. **Frontend autocomplete** — [`TurCustomToolDescriptorService`](../turing-app/src/main/java/com/viglet/turing/genai/tool/TurCustomToolDescriptorService.java) gains an `agentHelper()` descriptor so the CodeMirror autocomplete (T40) suggests `agent.invoke(...)` with the right signatures and docstrings. **Tests** — 16 new unit tests in [`TurCustomToolAgentHelperTest`](../turing-app/src/test/java/com/viglet/turing/genai/tool/TurCustomToolAgentHelperTest.java) pin: depth cap short-circuit (executor never invoked), all 4 input-validation sentinels (blank id / blank msg / missing agent / disabled agent / no LLM), llm-not-allowed for unattached llmInstanceId, token-only concatenation (option events ignored), depth propagation into executor call, parent-conversationId embedded in child's first user msg + omitted when null, custom-timeout / over-cap / unparseable timeout fallbacks, depth+cap accessor invariants. Descriptor test extended to assert the 5th helper + its `invoke` overloads. Verified: backend compile clean; 147/147 across the impacted custom-tool surface (`TurCustomToolDescriptorServiceTest` + `TurCustomToolGroovyParserBugTest` + `TurCustomToolAgentHelperTest` + customer-side `AulaRelampagoToolTest` + `ComporPropostaInCompanyTest`); `TurCacheAnnotationConventionsTest` + `TurLiquibaseChangelogAgnosticTest` lints green. **T110 unblocked** — the parent linkage is now in the child's first user message; T110's scorecard work is just to parse + persist that prefix as a structured `parentConversationId` column on `TurChatSessionEvent`. | §IX.2.b |
| T110 | ✅ | T109 | **Sub-agent scorecard rows ✅ shipped 2026-05-26 (backend + UI dimension picker)**. Each `agent.invoke(...)` child session now records a separate `TurChatSessionEvent` tagged with `parentConversationId` so the scorecard can drill down ("orchestrator agent X invoked specialist Y in N sessions, average sub-task duration M ms"). **Implementation** — new nullable `parentConversationId` field on the [`TurChatSessionEvent`](../turing-app/src/main/java/com/viglet/turing/service/chatanalytics/TurChatSessionEvent.java) record; new `start(...)` overload accepting the parent id (the no-parent overload delegates with `null` for backwards-compat). [`TurChatAnalyticsService.recordSessionStart`](../turing-app/src/main/java/com/viglet/turing/service/chatanalytics/TurChatAnalyticsService.java) now extracts the `[__parentConversationId=…]` prefix that T109's `TurCustomToolAgentHelper` writes onto the child's first user message via two new package-private helpers (`extractParentConversationId(firstUserMessage)` + `stripParentConversationIdPrefix(firstUserMessage)`) — the structured field is persisted, the visible `firstUserMessage` keeps the audit marker out of dashboards. The `InFlight` cache holds the parent id alongside the other sticky-per-session fields so `recordSessionEnd` rolls it into the upserted end record without re-parsing. **Stores** — Mongo (`TurMongoChatAnalyticsStore.upsertSession` + `toRow`) and Redis (`TurRedisChatAnalyticsStore.upsertSession` + `toRow`) both persist + project the new field. **Scorecard whitelist** — `dimension == "parentConversationId"` is now a valid group-by key on both store impls' `scorecard(...)` methods so future drill-down dashboards can pivot on it without another backend ship. **Tests** — new [`TurChatAnalyticsServiceTest`](../turing-app/src/test/java/com/viglet/turing/service/chatanalytics/TurChatAnalyticsServiceTest.java) pins the parser + strip helpers (11 cases — prefixed/blank/absent/null/missing-close-bracket inputs, trailing-newline drop, malformed-prefix passthrough, record-shape sanity, legacy-overload null-parent invariant). Verified: backend compile clean; 30/30 across the new test + lint sweep (`TurCacheAnnotationConventionsTest`, `TurLiquibaseChangelogAgnosticTest`); 12/12 `TurAbBanditServiceTest` still green (consumes the unchanged `variantLabel`/`experimentKey` columns alongside the new field). **UI dimension picker ✅ shipped 2026-05-26 (same day)**: [`chat-analytics.page.tsx`](../frontend/apps/turing-app/src/app/console/chat-analytics/chat-analytics.page.tsx) `DIMENSION_OPTIONS` gained a fifth entry (`"parentConversationId"` → "Parent conversation"); description text + empty-state message both branch for the new dimension so admins see context-appropriate copy ("Orchestrator agents grouped by parent conversation — each row is one orchestrator + its sub-task sessions") and ("No orchestrator sessions yet — child sessions get this dimension populated only when a Custom Tool calls agent.invoke(...)") instead of the generic version. SDK-shape types updated in lockstep: [`TurChatAnalyticsScorecardDimension`](../frontend/apps/turing-app/src/models/chat-analytics/chat-analytics.model.ts) union extended; new `parentConversationId?: string \| null` field on `TurChatAnalyticsSession` so future session-detail panels can show the linkage without another model bump. **What remains 📋 (deferred to a polish pass)**: the session-detail surfaces — when a user clicks into a child session row, show a "Spawned by orchestrator: <parentId>" chip linking back to the parent; when viewing an orchestrator session, list its direct children with their per-task summaries. Backend data is fully captured + projected; this is pure presentation glue that doesn't gate any other Block E work. Verified: admin `tsc --noEmit -p tsconfig.app.json` clean against the updated model + page. | §IX.2.c |

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
| T121 | ✅ | — | **`suspend` node + `POST /chat/resume`** — new chat-flow node type `suspend` parks the cursor indefinitely; the engine's `walkTransparentNodes` recognises the type and returns the state unchanged (cursor stays on the suspend node turn after turn). New `TurChatFlowEngineService.resumeSuspendedFlow(conversationId, slotUpdates, resumeReason)` is the entry point external systems hit to advance the flow — applies the optional `slotUpdates` map FIRST (so downstream condition/switch nodes branch on the freshest values) via the source-aware `writeSlot(..., ENDPOINT, "resume=<reason>")` overload from T60, then walks each parked state past its suspend node via `advanceToFirstEdge` + `walkTransparentNodes`. Returns `ResumeResult(resumed, nothingToResume)`. `findSuspendedReason(conversationId)` helper exposes the suspend node's label as the parked reason. **REST**: new `POST /api/sn/{site}/chat/resume` on `TurSNSiteGenAiAPI` with `ChatResumeRequest(conversationId, slotUpdates, resumeReason)` body and `ChatResumeResponse(resumed, wasParked, error)` reply. The existing `GET /chat/state` (`ConversationStateDto`) gained a new `suspendedReason` field — non-null when the leaf cursor sits on a suspend node — so the SDK / portal can render a "Waiting…" banner without learning about node types. **Frontend**: new `"suspend"` member added to `FlowNodeType` discriminated union + entry in `NODE_COLORS` (slate palette — visually distinct from `scheduleAgent`'s lime "active waiting on a routine"). Palette block in `flow-sidebar.tsx` with `IconHandStop`. New `SuspendNode` renderer in `flow-nodes.tsx` shows the slate banner + "Conversation parked here until POST /chat/resume" hint. `nodeTypes` registry, `DEFAULT_LABEL`, `typeLabelKey()` exhaustive switch, palette EN/PT i18n all extended. No special properties form needed — the suspend node carries only `label` (the reason text the operator types on the canvas), already editable via the standard label field. **Scope-deliberate omission**: a dashboard listing all parked conversations is tracked as T122 (💭) — V1 ships only the primitive so flows can use it; the operator queries the parked state via the existing `GET /chat/state` endpoint per-conversation. Verified: backend compile clean; all 60+ impacted tests green (`TurChatFlowPinFlowTest`, `TurChatFlowRouterIndexCacheTest`, `TurChatFlowLinterServiceTest`, `TurAbBanditServiceTest`, `TurChatSlotAuditServiceTest`, `TurPiiSlotServiceTest`, `TurChatFlowFunnelServiceTest`, lint conventions). Frontend `tsc -b` green. **Learning**: turning a suspend node into a "park indefinitely" walker hook in the engine is just adding ONE conditional inside `walkTransparentNodes` because the walker's existing fallback (return state unchanged) is already the parked behaviour — the cursor sticks on the node, every chat turn since just lands on the same place. The resume endpoint is the "exit ramp" that converts a parked state back to a walking state. | §IX.6.a |
| T122 | 💭 | T121 | **Parked-conversations dashboard** — admin panel listing all suspended conversations + reasons + waiting time + unblock action. Operator visibility into asynchronous workloads. | §IX.6.b |

#### E.6 — Context engineering enforcement

| #    | Status | Depends on | Task | Detail in |
|------|--------|------------|------|-----------|
| T123 | ⏳ | T31 | **Token-budget enforcement per turn (V1: WARN/ERROR; COMPACT degrades to WARN until T115 ships)**. Two new columns on `TurAIAgent` (Liquibase `v2026.3.1.10.yaml`): `max_prompt_tokens` (int, default 0 = disabled) and `over_budget_behavior` (`TurAgentOverBudgetBehavior` enum: `WARN` | `ERROR` | `COMPACT`, default `WARN`). New `TurTokenBudgetService.check(agent, messages)` returns `CheckResult(Decision, estimatedTokens, budget)` where Decision is `WITHIN_BUDGET` / `WARN` / `ERROR`. Estimator: pure-function `estimateTokens(List<Message>)` sums message char counts and divides by 4 with ceiling (so a 1-char message counts as 1 token, not 0) — the OpenAI `cl100k_base` average is ~4 chars/token across English+code; Anthropic and Gemini are within ±15%, plenty good for a budget check where the decision boundary is "16k ± 1k". **No new Maven dep**: Spring AI 2.0.0-M2 only ships `JTokkitTokenCountEstimator` coupled to `MessageWindowChatMemory`, and pulling that just for the budget check would couple the layer to chat-memory's lifecycle. Wired into `TurAgentChatExecutor.execute` right after `promptAssembler.assemble(...)` and BEFORE `streamingDispatcher.dispatch(...)`: `Decision.ERROR` returns `Flux.just(new ChatResponse("assistant", "Prompt over budget..."))` so the SSE consumer sees a structured failure instead of a dangling stream; `Decision.WARN` logs and falls through to the dispatcher. `COMPACT` mode logs a "T115 not yet shipped, behaving as WARN" notice and proceeds — flow authors can pre-pick the COMPACT mode they'll want when T115 lands and the upgrade just flips the runtime behaviour. **Frontend**: new `maxPromptTokens` + `overBudgetBehavior` fields on `TurAIAgent` model, two new form rows inside the existing chat-memory `SectionCard` of `ai-agent.settings.form.tsx` (a number input for the budget, clamped `[0, 1_000_000]` with step 500; a `<select>` for the behaviour with `aria-label` so the screen reader picks up the field name). The behaviour select is disabled while `maxPromptTokens=0` so authors get a visual cue that "no budget = no behaviour to pick". EN/PT i18n via inline `defaultValue` (same convention as the surrounding T70 fields). 11 new unit tests (`TurTokenBudgetServiceTest`): budget=0 skips estimation, exact budget hits the within-budget branch, char/4 ceiling for tiny prompts, sum-across-messages, multi-message system+user mix, ERROR returns Error decision with the right estimate echoed back, WARN proceeds, COMPACT degrades to WARN with the V1 notice, null agent skips check, null behaviour defaults to WARN. 80/80 across `TurTokenBudgetServiceTest` + the existing T60/T61/T70/T85/T121 sweep + Liquibase + cache-annotations lints. Backend compile clean; frontend `tsc -b` green. **Status ⏳ (partial)**: COMPACT mode is wired through the data model + UI but degrades to WARN until T115 ships the workspace-backed history compression. **Learning**: shipping a provider-agnostic 4-chars/token heuristic over `cl100k_base` precision lets the budget check stay zero-allocation per Message and removes a transitive dep on Spring AI's `MessageWindowChatMemory` internals — for a decision that gates "should we call the LLM at all?", ±15% precision is below the noise floor anyway. The COMPACT slot in the enum lets the future T115 flip be a runtime-only change with no schema migration. | §IX.7.a |
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

### Block F — Native LLM SDK exploitation (beyond Spring AI 2.0)

> Triggered by a 2026-05-25 audit of OpenAI Java SDK + Anthropic Java SDK
> vs. Spring AI 2.0.0-M2/M3/M4 (full mapping in §X). Spring AI 2.0 covers
> chat / tool calling / structured output / vector stores / observability
> very well; what it does **not** cover (no public ETA inside 2.0) is the
> entire post-Chat-Completions stack: OpenAI Responses API + built-in
> server-side tools (`web_search`, `file_search`, `code_interpreter`,
> `computer_use`, `image_generation`, remote `mcp`), the Realtime API,
> the Batch APIs of both vendors, OpenAI Predicted Outputs / Stored
> Completions / Evals / Distillation / Vector Stores hospedados / safety
> identifier, Anthropic Citations + Search Results content type + Memory
> tool + Context editing + Compaction + MCP Connector + Files API +
> Token Counting + server-side `bash`/`text_editor`/`web_search`/
> `web_fetch`/`code_execution`/`advisor` tools.
>
> **Strategic frame**: keep Spring AI as the portable layer for the
> features it ships well; introduce a `TurNativeProviderClient` seam
> that exposes the official SDK clients (`openai-java`, `anthropic-java`)
> per `TurLLMInstance`, gated by a per-agent capability matrix. The
> existing `TurGenAiLlmProvider` factory grows two parallel paths
> (`createNativeOpenAiClient()`, `createNativeAnthropicClient()`)
> alongside `createChatModel()` so individual features migrate without
> ripping the platform off Spring AI.
>
> Block F is **the largest single capability bet** on this roadmap.
> It is structured so each subsection is independently shippable —
> any single T-task delivers customer-visible value without waiting
> for the rest. Dependency anchor: T130 (the native seam) and T131
> (executor variant that consumes it) are the only true prerequisites
> for everything downstream.

#### F.1 — Foundation (the seam itself)

| #    | Status | Depends on | Task | Detail in |
|------|--------|------------|------|-----------|
| T130 | 📋 | — | **`TurNativeProviderClient` seam** — interface exposing per-`TurLLMInstance` access to the official OpenAI (`com.openai:openai-java`) and Anthropic (`com.anthropic:anthropic-java`) SDK clients. `TurGenAiLlmProvider` gains `createNativeOpenAiClient()` / `createNativeAnthropicClient()` defaulting to `Optional.empty()`. Per-agent capability matrix on `TurAIAgent.nativeCapabilitiesJson` declares which native features the agent opts into. Decryption / resilience / pipeline decoration reuse `TurLlmModelFactory` so audit trail stays unified. | §X.2 |
| T131 | 📋 | T130 | **`TurNativeChatExecutor` variant** — sibling to `TurAgentChatExecutor` (post-T33 decomposition). Routes a turn through Responses API (OpenAI) or native Messages API with full feature set (Anthropic) when the active LLM + agent capability matrix declares native support; falls back to the Spring-AI executor otherwise. Reuses `TurChatToolResolver` / `TurChatPromptAssembler` / `TurChatStreamingDispatcher` collaborators verbatim — only the LLM dispatch leg differs. `STAGE_CHAT_*` meter shape preserved, with a new `[Native]` tag on every emission so dashboards can A/B native-vs-spring at observation time. | §X.2 |
| T132 | 📋 | T131 | **Per-LLM-instance feature flags table** — `tur_llm_instance_capability` (instanceId, capabilityKey, enabled, configJson). Capability keys: `openai-responses`, `openai-web-search-tool`, `openai-file-search-tool`, `openai-code-interpreter-tool`, `openai-computer-use`, `openai-image-gen-tool`, `openai-realtime`, `openai-mcp-connector`, `openai-predicted-outputs`, `openai-stored-completions`, `openai-evals`, `openai-safety-identifier`, `openai-omni-moderation`, `openai-batch`, `openai-service-tier`, `anthropic-web-search`, `anthropic-web-fetch`, `anthropic-code-execution`, `anthropic-bash-tool`, `anthropic-text-editor-tool`, `anthropic-computer-use`, `anthropic-mcp-connector`, `anthropic-memory-tool`, `anthropic-context-editing`, `anthropic-compaction`, `anthropic-citations`, `anthropic-search-results`, `anthropic-files-api`, `anthropic-token-counting`, `anthropic-batch`, `anthropic-service-tier`. Liquibase `v2026.3.2.0.yaml`. Frontend: per-LLM instance settings page gains a capability matrix grid (one row per key, toggle + config drawer). | §X.2 |

#### F.2 — OpenAI Responses API + built-in tools

| #    | Status | Depends on | Task | Detail in |
|------|--------|------------|------|-----------|
| T133 | 📋 | T131 | **OpenAI `web_search` built-in tool** — wires the `web_search_preview` tool into the Responses path. Per-agent toggle `webSearchEnabled` + `webSearchAllowedDomains` (allowlist) + `webSearchBlockedDomains` (denylist). UI: "Live answers" mode in the chat — answer mixes SN index hits with fresh web context, each web claim carrying a citation chip linked back to the source URL. **Differentiation**: enterprise-search platforms typically can't answer "what changed since indexing?" — this closes the gap inside the same answer. | §X.3.a |
| T134 | 📋 | T131 | **OpenAI `file_search` built-in tool** — provisions OpenAI-hosted vector stores per agent/tenant. New `TurOpenAiVectorStoreSync` mirrors `TurStorageService` blobs into per-tenant vector stores with attribute filters (`tenantId`, `agentId`, `kind`, `locale`) so retrieval is sandboxed. Complementary tier to T19/T24/T24b — admins choose embedded Lucene, dedicated Solr/ES BM25 cores, OR OpenAI-hosted vector store per `TurSNSiteGenAi`. Useful for ad-hoc per-user knowledge ("uploaded a CV mid-chat, search just within it") without round-tripping Solr. | §X.3.b |
| T135 | 📋 | T131, T80 | **OpenAI `code_interpreter` built-in tool** — alternative path to T80's in-process Code Interpreter for installs that prefer OpenAI's managed Container sandbox. Per-agent toggle `codeInterpreterRuntime` (`embedded` / `openai-container`); embedded keeps the customer-authored Custom Tool Python ecosystem (matplotlib + reportlab + the per-agent `pip install` list from T77), OpenAI Container runs `gpt-image-1` / `gpt-4o` against the model's own sandbox. Charged at $0.03/container. Pairs naturally with T80 — `embedded` for tools with deps, `openai-container` for one-shot analytical answers in chat. | §X.3.c |
| T136 | 💭 | T131 | **OpenAI `computer_use` built-in tool** — agentic browser. Two concrete enterprise-search uses worth pitching: (1) **connector-setup wizard** — admin says "connect my Confluence" → agent navigates to Confluence admin UI, generates an API token, pastes it into Turing; (2) **crawler debugging** — admin says "why didn't this site indexed?" → agent opens the URL, inspects rendered DOM, identifies the JS gate / paywall. Sandbox runs in a managed Container. **High wow-factor for demos**, low to mid leverage for steady-state use. | §X.3.d |
| T137 | 📋 | T131 | **OpenAI `image_generation` as inline tool** — `gpt-image-1`/`gpt-image-1.5` invoked inside the Responses turn. Wired into the marketing / site-authoring Custom Tools — the chat asks "rewrite this hero text and generate a matching banner" and the response embeds both. Auto-stored in `TurAgentWorkspace` (T111) as `images/{ts}-{hash}.png` with HMAC-signed URL surfaced in the SDK workspace bus (T113). | §X.3.e |
| T138 | 💭 | T131 | **OpenAI `mcp` tool (remote MCP connector)** — Responses API gains a first-class `mcp` tool that points at a remote MCP server URL (Streamable HTTP / SSE). Turing's chat exposes the existing Turing MCP server **to its own LLM** through this entry, eliminating the duplicate `MethodToolCallbackProvider` wiring on `/api/v2/llm/{id}/semantic-chat`. Allow-list per agent (`mcpServerAllowlist`) of (a) the local Turing MCP, (b) customer-configured third-party MCP servers (Confluence, Jira, GitHub, Notion). Federated enterprise search across systems **without** writing a Java tool callback per source. | §X.3.f |

#### F.3 — Anthropic server-side tools

| #    | Status | Depends on | Task | Detail in |
|------|--------|------------|------|-----------|
| T139 | 📋 | T131 | **Anthropic `web_search_20250305` server tool** — analog to T133 but on Claude models. Spring AI 1.1.7 / M4 added the option for the new module, but the platform is on 2.0.0-M2 — short of upgrading Spring AI, the native SDK is the path. Same UX as T133 (per-agent toggle + allow/deny domains + citation chips). At $10 / 1k searches, surface usage gauge in System Info AI Spend (T172). | §X.4.a |
| T140 | 📋 | T131 | **Anthropic `web_fetch` server tool** — Claude pulls a URL or PDF directly (no Turing crawler needed). Two concrete uses: (1) **ad-hoc URL in chat** ("summarize this page: <url>"), (2) **drift detection** ("is the Wikipedia article about X consistent with our indexed copy?"). ZDR-eligible. Domain allow/deny inherited from T139 settings. | §X.4.b |
| T141 | 📋 | T131 | **Anthropic `code_execution` server tool** — Python sandbox, **free** when paired with web_search/web_fetch. Enables "ask a question of the SN corpus, get a chart back" — combine with T133/T139 web search + T134/T165 file search, Claude composes Python that reads the retrieved JSON / CSV and emits a matplotlib chart. Result file URL routed into `TurAgentWorkspace` (T111). Eliminates the Custom-Tool Groovy-→-Python plumbing for one-shot analytics. | §X.4.c |
| T142 | 💭 | T131 | **Anthropic `bash_20250124` + `text_editor_20250728` tools** — Claude edits markdown files persisted in `TurAgentWorkspace` (T111). Concrete uses: (1) **agentic content authoring** — chat says "draft a 10-page playbook" → Claude `text_editor.create("playbook.md")` then `str_replace` over multiple turns, (2) **indexer maintenance** — admin says "regenerate the synonym dictionary from these conversations" → Claude `bash` runs a script in a tenant-isolated container. Pairs with T80 container sandbox so `bash` doesn't have host access. | §X.4.d |
| T143 | 💭 | T131 | **Anthropic `computer_use_20251124` tool with built-in prompt-injection classifier** — same use cases as T136 but on Claude with the server-side screenshot-level prompt-injection classifier that ships with the tool. Higher-trust path for production computer-use deployment than the OpenAI variant. | §X.4.e |

#### F.4 — MCP Connector (both vendors)

| #    | Status | Depends on | Task | Detail in |
|------|--------|------------|------|-----------|
| T144 | 📋 | T131 | **Anthropic MCP Connector** (`mcp-client-2025-11-20` beta) — `messages.create(mcp_servers=[…])` lets Claude call any remote MCP server with no client code. Per-agent allow-list with OAuth bearer tokens. Same federation story as T138 (OpenAI side) but on Claude. **Not ZDR-eligible** — surface that constraint in the UI when enabling. | §X.5.a |
| T145 | 💭 | T138, T144 | **Cross-vendor MCP federation layer** — a thin `TurMcpFederationDirectory` keeps a single per-agent allow-list of MCP server URLs; the `TurNativeChatExecutor` passes the matching subset to whichever vendor handles the turn (OpenAI via T138 `mcp` tool entry, Anthropic via T144 connector). Customer authors the list once; works across LLM swaps. | §X.5.b |
| T146 | 💭 | T144 | **Turing-as-MCP-Server for Claude Desktop / Cursor / OpenAI Responses** — promote the existing Turing MCP server from "internal feature" to a **public consumption surface**: API-key-gated, OAuth optional, hosted at `https://<turing-host>/mcp` with the standard MCP discovery manifest. A customer's Claude Desktop sees Turing's SN search as a first-class tool. Inverts the integration — instead of customers integrating Turing into their app, their LLM clients integrate Turing themselves. | §X.5.c |

#### F.5 — Voice agents (Realtime API)

| #    | Status | Depends on | Task | Detail in |
|------|--------|------------|------|-----------|
| T147 | 📋 | T131 | **`TurRealtimeVoiceProvider`** — WebSocket transport against `gpt-realtime` / `gpt-realtime-mini` / `gpt-realtime-1.5`. Server-side bridge so the browser doesn't carry the OpenAI key. Same `TurChatToolResolver` pipeline injects the SN search tools into the Realtime session — voice queries hit the index live. Per-LLM-instance capability gated by T132. | §X.6.a |
| T148 | 📋 | T147 | **`/chat/voice` endpoint + React hook `useTuringVoice`** — already shipped as a stub in T104; this expands it to full duplex. Mic → WebSocket → Realtime → audio playback. Falls back to text TTS (T156) when Realtime cap is off. | §X.6.b |
| T149 | 💭 | T147 | **Voice-first kiosk mode** — `/console/voice-kiosk?agent=X` — full-screen voice UI for trade-show booths, lobby kiosks, accessibility deployments. Background brand colors, large captions of what the user/agent just said, no keyboard required. **Demo-heavy differentiator**: enterprise-search platforms are typed-input-only. | §X.6.c |
| T150 | 💭 | T147 | **`gpt-realtime-translate` simultaneous interpretation** — visitor speaks Portuguese, agent answers Portuguese AND the operator sees a live English translation in spectator mode (T120). Or vice versa for multilingual customer support. | §X.6.d |
| T151 | 💭 | T147 | **Voice authoring of chat-flows** — admin says "add an `aiQuestion` node asking for the lead's cargo, then route by role" → Realtime parses → emits chat-flow JSON patch → applies via the editor's existing event bus. Pairs with the existing chat-flow live preview (T95) for "voice → graph appears" demo flow. | §X.6.e |

#### F.6 — Citations & verifiable RAG

| #    | Status | Depends on | Task | Detail in |
|------|--------|------------|------|-----------|
| T152 | 📋 | T131 | **Anthropic Citations (`citations: enabled`)** — every passage in `TurSNDocument` payload added to the prompt as a `document` content block; Claude responses come back with per-sentence citation arrays pointing to `(document_id, char_start, char_end)` for text and `(page)` for PDFs. Backend persists the citation map in `chat_message.citationsJson`. | §X.7.a |
| T153 | 📋 | T152 | **Anthropic Search Results content type** — Turing's vector / hybrid retrieval (T19/T24/T24b) wraps its hits in the new `search_result` content block (encyclopedia-quality citation format) instead of the older `document` block. Cleaner UI mapping than free-form documents — Claude treats them as "this is web-search-tier provenance" by default. | §X.7.b |
| T154 | 📋 | T152 | **Citation-aware chat UI** — React message renderer underlines the exact span Claude derived each claim from, with a hover popover showing the source chunk + deep link `/sn/{site}/document/{id}#chunk={index}`. PDF citations show `(p. 3)` chips. Backend exposes `GET /chat/message/{id}/citation/{i}` to resolve a single citation lazily. Eliminates the "the LLM said this but where did it come from?" trust gap. | §X.7.c |
| T155 | 💭 | T154 | **Citation drift detection** — async job re-resolves recent citations; flags messages where the cited chunk has been re-indexed since the answer (`citationStale=true`). Operator gets a "this answer references outdated content" warning in the conversation replay UI (T86). LGPD / compliance angle: provable provenance. | §X.7.d |

#### F.7 — Batch + token economics

| #    | Status | Depends on | Task | Detail in |
|------|--------|------------|------|-----------|
| T156 | 📋 | T131 | **`TurBatchInferenceService`** — unified abstraction over OpenAI Batch API (`client.batches()`) and Anthropic Message Batches (`POST /v1/messages/batches`). Submit a `BatchJob(jobId, kind, payload, callback)` → service writes JSONL → submits → polls for completion → routes results back to the originating service. Persists job state in `tur_batch_job` (Liquibase `v2026.3.2.1.yaml`). | §X.8.a |
| T157 | 📋 | T156 | **Nightly batch summarization** — the per-site AI Insights cron + per-SN-document summary now run through `TurBatchInferenceService` at the **50% Batch discount** instead of synchronous Spring AI calls. Cache TTL extended to 24h to match the batch latency budget. Cost-cutting move on the bulk insight workload. | §X.8.b |
| T158 | 📋 | T156 | **Bulk re-embedding via Batch** — when an admin switches the embedding model on a site (`text-embedding-3-small` → `text-embedding-3-large`), Turing now schedules the re-embedding as a Batch job (50% off, 24h SLA) instead of a synchronous re-index that locks the indexer thread pool. Progress visible in the existing reindex UI (T24b Phase 4). | §X.8.c |
| T159 | 📋 | T156 | **LLM Judge eval at batch tier** — the LLM Judge bilingual prompt over a regression set (T126 eval suite, when present) runs as Batch by default — eval CI pipelines finish overnight at half cost. | §X.8.d |
| T160 | 📋 | T131 | **Service tier routing** — per-stage `service_tier` selection (`flex` / `priority` / `default` / `auto`). Mapping: `chat.live` → `priority` (SLA-protected, interactive), `chat.background` (AI Insights, summaries, classifier reindex) → `flex` (near-batch rates, sync), unknown / fallback → `auto`. Wired through `TurNativeChatExecutor` and `TurBatchInferenceService`. Per-LLM cap from T132. | §X.8.e |
| T161 | 📋 | T131 | **Anthropic token counting (`/v1/messages/count_tokens`)** — pre-flight token estimator. Wired into `TurChatPromptAssembler` so the executor can refuse to call Claude when predicted prompt would exceed the agent's `maxPromptTokens` (T123) — replaces the current `cl100k_base`-tiktoken heuristic that misestimates Claude tokens. Cached for 60s per prompt fingerprint to avoid per-turn round-trips on identical-prefix conversations. | §X.8.f |
| T162 | 💭 | T161 | **Pre-flight cost quote in chat UI** — when token-counting estimate × model price exceeds `softCostBudgetPerTurnUsd`, the SDK shows a "this turn will cost $X.XX, confirm?" gate. Per-agent off by default; enabled by enterprise customers who want hard cost ceilings on agent answers (research / report-generation use cases). | §X.8.g |

#### F.8 — Memory tool + context editing

| #    | Status | Depends on | Task | Detail in |
|------|--------|------------|------|-----------|
| T163 | 📋 | T131, T111 | **Anthropic Memory tool (`memory_20250818`)** — Claude gets six file commands (`view`/`create`/`str_replace`/`insert`/`delete`/`rename`); the implementation is backed by `TurAgentWorkspace` at path `memory/` (per-conversation by default; opt-in per-user via `memoryScope=user` flag). Persistent cross-session knowledge. Beta header `context-management-2025-06-27`. | §X.9.a |
| T164 | 📋 | T131 | **Context editing (`clear_tool_uses_20250919`)** — Claude server-side auto-evicts old tool results when the window saturates, replacing them with a placeholder. Up to 84% token savings on long agent loops. Pairs with T29 tool pre-filter and T30 BM25 memory retrieval — Anthropic handles the trim of inline tool outputs, Turing handles the retrieve-the-right-old-turn problem. | §X.9.b |
| T165 | 📋 | T131 | **Anthropic Compaction** (beta) — server-side summarization of older turns when the window fills. Per-agent toggle `compactionEnabled`; threshold inherited from `chatMemoryCompressionThresholdTokens` (T115). When both Turing-side compression (T115) and Anthropic compaction are on, Turing wins precedence (artifact stored in workspace), Anthropic disabled to prevent double-summarization. | §X.9.c |
| T166 | 💭 | T163 | **Per-user persistent persona memory** — `memoryScope=user` + the Memory tool gives Claude a per-Keycloak-`sub` file area where it stores stable user preferences ("Alex prefers terse answers", "Alex's preferred locale is pt-BR"). Surfaced in the chat UI as a "What this agent remembers about you" disclosure, with a "Forget all" button (LGPD ready). | §X.9.d |

#### F.9 — Stored Completions + Evals + Distillation flywheel

| #    | Status | Depends on | Task | Detail in |
|------|--------|------------|------|-----------|
| T167 | 📋 | T131 | **`TurStoredCompletionsService`** — when an agent has `storedCompletions=true`, every Responses API call sets `store: true` + structured `metadata` (`agentId`, `conversationId`, `flowId`, `nodeId`, `experimentKey`, `variantLabel`). OpenAI retains the request/response pair under that metadata, queryable from the OpenAI dashboard or Evals API. Per-agent opt-in (privacy default off); LGPD review hook tied to the existing `SubmissionRetention` config (T66). | §X.10.a |
| T168 | 📋 | T167 | **OpenAI Evals API integration** — Turing's eval suite (T126) optionally pushes fixtures to OpenAI Evals + runs server-side graders. Result: customer eval CI runs on every PR but the grading uses OpenAI's harness, freeing the customer from building their own LLM-judge stack. Per-eval choice (`localGrader` vs `openaiGrader`). | §X.10.b |
| T169 | 💭 | T167, T168 | **Distillation pipeline** — admin clicks "Distill last 30 days" on an agent → Turing exports Stored Completions for that agent → submits as a fine-tuning job against `gpt-4o-mini` → swaps the `TurLLMInstance.modelId` after the eval passes. Net effect: gpt-4o-quality answers at gpt-4o-mini cost on this customer's specific traffic. **Production flywheel that compounds with usage.** | §X.10.c |
| T170 | 💭 | T169 | **DPO loop from operator feedback** — operators thumb-up / thumb-down assistant turns; the DPO dataset is built incrementally from the binary preferences. Quarterly fine-tune pulls in the latest preferences. Turning everyday conversational moderation into model improvement. | §X.10.d |

#### F.10 — Predicted Outputs + strict structured outputs

| #    | Status | Depends on | Task | Detail in |
|------|--------|------------|------|-----------|
| T171 | 📋 | T131 | **OpenAI Predicted Outputs (`prediction`)** — 3-5× latency drop when the output mostly overlaps an input string. Wired into the chat-flow editor's variant generator (T97 — the original definitionJson IS the prediction), the persona-prompt editor, the locale-string editor. Symptom: regenerate-with-tweak feels instant instead of 3-5 seconds. | §X.11.a |
| T172 | 📋 | T171 | **Predicted Outputs in T19/T24 RAG answer re-rank** — when the LLM answers an SN question and the user clicks "rephrase shorter", the previous answer is the `prediction`. Massive latency win on the conversational refinement loop. | §X.11.b |
| T173 | 📋 | T131 | **OpenAI Strict Structured Outputs (`response_format: json_schema, strict: true`)** — replaces the gpt-4o-mini JSON-truncation workarounds documented in `feedback_gpt4o_mini_json.md`. Every Turing path that parses LLM JSON (LLM Judge, T28 LLM intent classifier, T97 variant generator, chat-flow router decision) switches to strict schemas. Per-LLM cap from T132 toggles whether the strict path or the legacy json_object path is used. | §X.11.c |
| T174 | 📋 | T131 | **Anthropic structured outputs (ZDR-cached schemas)** — analog to T173 on Claude. Schemas cached server-side 24h → second use is free. Wire `LlmJudge` + classifier + variant generator + router decision; switch via `structuredOutputProvider=anthropic` on `TurLLMInstance`. | §X.11.d |

#### F.11 — Files API + native PDF

| #    | Status | Depends on | Task | Detail in |
|------|--------|------------|------|-----------|
| T175 | 📋 | T131 | **`TurFilesApiBridge`** — on the indexer write path, when a file is queued for an OpenAI- or Anthropic-backed agent, the binary is also uploaded to the vendor's Files API and the returned `file_id` cached in `TurStorageObjectStat` metadata. Spring AI inlines media bytes per request — this bypasses that for repeat references. | §X.12.a |
| T176 | 📋 | T175 | **Crawler / Connector integration** — Tika-extracted text **stops being the only input**: the vendor `file_id` is passed alongside as a citation-bearing PDF/DOCX. Claude's native PDF support (preserves tables, layout, figures) is now Turing's default for any document indexed against a Claude agent. | §X.12.b |
| T177 | 💭 | T175 | **Vendor-side chunk reuse across agents in the same tenant** — when N agents on the same tenant cite the same PDF (e.g. company handbook), one `file_id` is shared (single upload, multiple references). Track via `TurStorageObjectStat.metadata.openaiFileId` / `.anthropicFileId`. | §X.12.c |

#### F.12 — Reasoning summary + observability hardening

| #    | Status | Depends on | Task | Detail in |
|------|--------|------------|------|-----------|
| T178 | 📋 | T131 | **Reasoning summary surfacing** — for o1 / o3 / o4-mini / GPT-5 reasoning models, capture `reasoning.summary` from the Responses API and render as a collapsible "Why this answer" panel in the chat UI. Elevates trust ("here's how I got there") without exposing raw chain-of-thought. Per-agent toggle `showReasoning`. | §X.13.a |
| T179 | 💭 | T131 | **Reasoning effort budget** — `reasoning.effort` (`minimal` / `low` / `medium` / `high`) plumbed per-stage. `chat.live` → `low` (latency); `chat.background` for AI Insights → `high` (quality matters more than latency); `LlmJudge` → `medium`. Default policy editable per agent. | §X.13.b |
| T180 | 📋 | T131 | **Logprobs in T19/T24/T28 reranker as confidence signal** — capture `logprobs` + `top_logprobs` on Responses calls; reranker / classifier mixes the LLM token-level confidence into the existing BM25 / vector scores. Fixes the long-standing "the LLM hedged but we ranked it #1" failure mode. | §X.13.c |

#### F.13 — Safety, compliance, multi-tenant cost

| #    | Status | Depends on | Task | Detail in |
|------|--------|------------|------|-----------|
| T181 | 📋 | T131 | **`safety_identifier` propagation** — hashed Keycloak `sub` (`SHA-256(sub + tenant-salt)`) sent on every OpenAI Responses call as `safety_identifier`. OpenAI absorbs abuse-detection for the customer's end users instead of Turing carrying the burden. Per-tenant salt makes the identifier non-reversible across customers. | §X.14.a |
| T182 | 📋 | T131 | **OpenAI `omni-moderation-latest` pre-filter** — every user query (and every crawler-discovered document chunk over a configurable size) passes through `omni-moderation` before reaching the LLM / index. Per-agent toggle + per-category sensitivity (sexual, violence, hate, self-harm, illegal). Free at the API tier. Pairs with the multimodal moderation path so image attachments are filtered too. | §X.14.b |
| T183 | 📋 | T131 | **Anthropic Admin + Usage API integration** — `TurAnthropicUsageImporter` runs nightly, pulls per-workspace / per-model / per-service-tier token consumption + USD cost from the Anthropic Admin/Usage API, persists to `tur_ai_usage_daily` (Liquibase `v2026.3.2.2.yaml`). Same shape for OpenAI via `client.organizations().usage()`. | §X.14.c |
| T184 | 📋 | T183 | **Per-tenant AI Spend widget** — System Info gains an "AI Spend" card showing 30-day spend per LLM / per agent / per stage (chat.live vs chat.background) with budget alert thresholds. Drill-down: which conversationId burned the most tokens. | §X.14.d |
| T185 | 💭 | T183, T184 | **Soft budget caps per agent** — agent setting `monthlyBudgetUsd`; when projected spend (based on rolling 7-day average) would breach the cap, the engine downgrades to a cheaper tier (e.g. gpt-4o → gpt-4o-mini) or routes through Batch (T156). Operator gets a "budget guardrail tripped" notification. | §X.14.e |
| T186 | 💭 | T132 | **Capability matrix UI heatmap** — admin dashboard heatmap of (LLM instance × capability) showing which natives are wired and which are pending. Visual aid for the "how full is my Block F?" question. | §X.14.f |

#### F.14 — Bold creative bets (the long shots)

| #    | Status | Depends on | Task | Detail in |
|------|--------|------------|------|-----------|
| T187 | 💭 | T133, T139, T141 | **"Live answers" hybrid mode** — single chat answer fuses (a) SN index hits, (b) web_search citations, (c) live data via web_fetch, (d) computed analytics via code_execution — composed in one Responses turn. Each fact carries its origin chip (`📚 indexed`, `🌐 web`, `🧮 computed`). The category killer for "ask one question, get the full picture" enterprise search. | §X.15.a |
| T188 | 💭 | T146, T138, T144 | **Turing as the "headless" enterprise-search MCP backbone** — abandon the chat UI as the only consumption surface. Turing's MCP server (T146) becomes the canonical way for Claude Desktop / Cursor / OpenAI Responses / any MCP-compatible client to search and reason over the customer's index. Customer-facing chat UI continues; but the **bigger market** is "Turing is the search infrastructure inside other AI products." | §X.15.b |
| T189 | 💭 | T147, T150, T55 | **Real-time multilingual call center augmentation** — operator's headset audio goes through `gpt-realtime-translate` (T150), agent's screen shows live cited answers (T154) sourced from the customer's index, escalation paths via T55 handoff. Replaces the "expensive multilingual support staff" line item for customers with global presence. Voice + citations + handoff in one product. | §X.15.c |
| T190 | 💭 | T136, T142, T78 | **"Self-installing" enterprise connectors via computer_use** — customer says "connect my company's Notion" → Turing's computer-use agent navigates to Notion → guides them through API token generation → tests the connection → enables the connector. **Zero engineer hours per integration**, infinite-tail SaaS connector library on demand. The acquisition story for the SMB tier of the market. | §X.15.d |
| T191 | 💭 | T169, T126 | **Agent that improves itself overnight** — combine T169 distillation + T126 eval suite + T127 production-replay-as-test: every night, the agent's prod traffic feeds a distillation candidate, the candidate runs against the eval suite, and if it beats the current model on every assertion it's auto-promoted. The customer's agent gets cheaper and better every week with zero engineering work. | §X.15.e |
| T192 | 💭 | T138, T144, T146 | **Inter-tenant MCP federation** — opt-in directory at `directory.turing.io` where tenants can publish a redacted MCP server exposing **only public knowledge** (their docs, their public catalog). Other tenants subscribe and add it to their agent's MCP allowlist. Network effect: every Turing customer that publishes makes every other customer's agents more useful. Long-tail moat. | §X.15.f |

### Block G — Embedded LLM via Jlama (pure-Java in-process inference)

> Triggered by a 2026-05-25 evaluation of
> [`com.github.tjake:jlama-core`](https://github.com/tjake/Jlama),
> the pure-Java LLM inference engine using Project Panama Vector
> API. Goal: an admin imports a model into asset storage and the
> resulting `TurLLMInstance` serves chat / embedding **in-process**
> — no Ollama, no cloud API account, no JNI native libs to extract,
> no second container. **Strategic value**: (a) **air-gapped /
> regulated deployments** that forbid outbound LLM calls,
> (b) **demo/dev installs** that don't need API credit,
> (c) **local LlmJudge / local embedding** stages to cut cloud cost
> on high-volume guardrail and indexing paths, (d) **clean Windows
> deployment** — no native CUDA missing, no `dll` extraction, no
> path-of-libs surprises.
>
> **Two structural blockers** that don't exist yet:
> (1) [`TurLlmModelFactory`](../turing-app/src/main/java/com/viglet/turing/resilience/llm/TurLlmModelFactory.java)
> creates a fresh `ChatModel` per request — fine for HTTP-stateless
> providers, fatal for a provider where "instantiate" means "load
> SafeTensors weights and allocate the KV cache". T194 introduces a
> per-`TurLLMInstance` model cache.
> (2) [`TurStorageService`](../turing-app/src/main/java/com/viglet/turing/service/storage/TurStorageService.java)
> exposes `downloadObject(key): InputStream` but no local-file path —
> Jlama reads `config.json`, `tokenizer.json`, and `*.safetensors`
> from a real directory on disk. T195 adds a cross-backend
> `resolveLocalPath(key): Path`.
>
> **Critical operational requirement**: Jlama depends on **Project
> Panama Vector API** (still incubator in JDK 21). The Turing JVM
> must launch with `--add-modules jdk.incubator.vector
> --enable-preview` — a single missing flag and inference fails at
> first call. T216 enforces this at boot.
>
> **Critical format note**: Jlama reads **HuggingFace SafeTensors
> directories** (containing `config.json` + `tokenizer.json` +
> `model.safetensors`), **not GGUF directly**. Two acceptable
> ingestion paths: (a) admin imports an HF model directory (the
> normal path — `huggingface.co/tjake/*` ships pre-quantized models
> ready for Jlama), or (b) admin uploads a `.gguf` and Turing runs
> `jlama-cli convert` to produce the Jlama-friendly directory
> (T214). See §XI.2 for the format reality.

#### G.1 — Foundation (dependency + provider + cache + storage path)

| #    | Status | Depends on | Task | Detail in |
|------|--------|------------|------|-----------|
| T193 | 📋 | — | **`com.github.tjake:jlama-core:0.8.4+` dependency + `TurEmbeddedLlmProvider`** — new `@Component` implementing `TurGenAiLlmProvider` with `getPluginType()=="embedded-jlama"`. Reads `TurLLMInstance.providerOptionsJson` for `{modelAssetKey, workingQuant, modelQuant, role, contextLength}`. `createChatModel` / `createEmbeddingModel` lookup the cached `AbstractModel` (T194) by `instance.id`, never construct on the hot path. Optional dependency on `com.github.tjake:jlama-native:0.8.4+` (SIMD acceleration via JNI; 2–3× speedup; classifier per OS+arch) controlled by a Maven profile so installs without the native jar still work pure-Java. | §XI.2, §XI.3 |
| T194 | 📋 | T193 | **`TurEmbeddedModelCache`** — per-`TurLLMInstance.id` cache of loaded `AbstractModel`s. Unlike kherud's `LlamaModel`, Jlama's `AbstractModel` **is thread-safe** (stateless per request; KV-cache is per-`UUID` of session) — so the cache holds one shared instance per `TurLLMInstance` and serves concurrent requests directly. LRU eviction with `maxLoadedModels` (default 2) only fires on RAM pressure / explicit reconfiguration. Eviction triggers on `LlmInstanceUpdatedEvent` (published in [TurLLMInstanceAPI.java:126-128](../turing-app/src/main/java/com/viglet/turing/api/llm/TurLLMInstanceAPI.java#L126-L128)). `@PreDestroy` walks the cache and releases each `AbstractModel` — **clean JVM shutdown**, no JNI thread quirks. | §XI.3 |
| T195 | 📋 | — | **`TurStorageService.resolveLocalPath(String key): Path`** — new method on the storage interface. Filesystem: returns `basePath.resolve(key)` directly. MinIO/S3: idempotent download to `./store/cache/llm-instances/{sha256(key)}/{filename}` (skips download when the cache file matches the remote `etag` / size + last-modified). For Jlama the `key` points at a *directory* (the SafeTensors model dir), so the backend recursively syncs `config.json` + `tokenizer.json` + every `*.safetensors` shard before returning the local dir path. NoOp: throws `UnsupportedOperationException("Storage type 'none' does not support local file resolution")`. | §XI.3 |
| T196 | 📋 | T193 | **`EMBEDDED_JLAMA` vendor row** — extend [TurLLMVendorOnStartup.java](../turing-app/src/main/java/com/viglet/turing/onstartup/llm/TurLLMVendorOnStartup.java) with `vendor(id="EMBEDDED_JLAMA", plugin="embedded-jlama", title="Embedded (Jlama)", description="Pure-Java in-process inference, no external service required", website="https://github.com/tjake/Jlama")`. Idempotent insert (existing pattern). No Liquibase column changes — `TurLLMInstance` already has `providerOptionsJson` to carry `modelAssetKey`. | §XI.3 |
| T197 | 📋 | T193, T194, T195 | **`TurEmbeddedChatModel` + `TurEmbeddedStreamingChatModel`** — Spring AI `ChatModel` / `StreamingChatModel` adapters. Implementation choice: either (a) call Jlama's `AbstractModel.generate(UUID, PromptContext, temperature, maxTokens, onToken)` directly and convert to `Flux<ChatResponse>`, or (b) reuse `dev.langchain4j:langchain4j-jlama:1.15.0-beta25+` (`JlamaChatModel` / `JlamaStreamingChatModel`) and bridge LangChain4j → Spring AI via a thin adapter. Option (b) is preferred — pulls in tool calling for free (T207). Per-conversation `UUID` is mapped to `conversationId` so the KV cache reuses across turns within one session. **No semaphore needed** — Jlama serves concurrent UUIDs natively. | §XI.3 |
| T198 | 📋 | T193, T194, T195 | **`TurEmbeddedEmbeddingModel`** — separate Spring AI `EmbeddingModel` adapter via `JlamaEmbeddingModel`. Jlama supports BERT-family encoders (BGE, sentence-transformers, MiniLM) — the indexer specifies an encoder-only model for `role=embedding`. A single `TurLLMInstance` declares its role (`chat` / `embedding`) — combining requires two instances (Jlama loads encoder vs decoder weights differently). UI must surface this explicitly. | §XI.7 |

#### G.2 — Frontend UX (model picker + import)

| #    | Status | Depends on | Task | Detail in |
|------|--------|------------|------|-----------|
| T199 | 📋 | T193, T195 | **`JlamaModelPicker` React component** — modal/dropdown that lists candidate model directories under `models/` in `TurStorageService`. Calls a new `GET /api/llm/embedded/models` endpoint that recursively scans for directories containing both `config.json` AND at least one `*.safetensors` shard. Returns `[{key, name, sizeBytes, modelType?, paramCount?, quantization?}]` (last three derived from `config.json` when present). Each row shows directory name + total size + architecture chip (`llama`, `mistral`, `bert`, ...). Empty-state CTA: "No Jlama models yet — import one in `/console/llm-models/import`". | §XI.4 |
| T200 | 📋 | T199 | **`applyVendorDefaults("EMBEDDED_JLAMA")`** in [llm.instance.form.tsx:139-195](../frontend/apps/turing-app/src/components/llm/llm.instance.form.tsx) — sets `apiKey=""` (hidden), `url=""` (hidden), conditional fields revealed: `modelAssetKey` (the `JlamaModelPicker`), `role` (chat/embedding radio), `workingQuant` (F16/I8 — default I8 for chat/embedding both, balances quality vs RAM), `modelQuant` (auto-detected from `config.json`), `contextLength` (default = config's `max_position_embeddings`, capped per agent), `eagerLoad` (checkbox), `useNativeSimd` (checkbox — only enabled if `jlama-native` is on the classpath). i18n EN/PT for every label. | §XI.4 |
| T201 | 📋 | T199 | **Model metadata pre-flight on selection** — when the picker selects a model dir, frontend calls `GET /api/llm/embedded/model-info?key=...` → reads `config.json` and returns `{architecture, paramCount, hiddenSize, contextLength, vocabSize, quantization, estimatedRamBytes}`. UI displays a chip row: `Llama 3.1 · 8B · I8 · 8k ctx · ~4.5 GB RAM`. Warns when `estimatedRamBytes > 0.7 * Runtime.getRuntime().maxMemory()`. Also warns when `architecture` is **not in the Jlama-supported list** (`llama`, `llama2`, `llama3`, `llama3.1`, `mistral`, `mixtral`, `gemma`, `qwen2`, `gpt2`, `bert`) — refuses to save with a list of supported families. | §XI.4, §XI.7 |
| T202 | 📋 | T193 | **Pre-flight RAM check on save** — `TurLLMInstanceAPI` PUT/POST validates that `estimatedModelRam + currentLoadedTotal < 0.7 * Runtime.maxMemory()`; rejects with 422 + actionable message ("Loading this model would push memory beyond 70% — evict an active embedded instance first or increase JVM heap"). Prevents the JVM-OOM-mid-warmup failure mode. | §XI.5 |

#### G.3 — Operational realities (Panama, lifecycle, observability)

| #    | Status | Depends on | Task | Detail in |
|------|--------|------------|------|-----------|
| T203 | 📋 | T194 | **Per-conversation session UUID lifecycle** — Jlama's KV-cache is keyed by `UUID` per `AbstractModel.generate(...)` call. `TurEmbeddedChatModel` maintains a `Cache<String, UUID>` mapping `conversationId → sessionUuid` so KV-cache reuse survives across turns; eviction policy tied to chat memory retention + an idle TTL (default 30 min, configurable). Stale UUIDs trigger Jlama's `releaseSession(UUID)` to free KV memory. Surfaced in T205 metrics as `turing.embedded.sessionsActive`. | §XI.5 |
| T204 | 📋 | T194 | **Eager-load on startup** — when `eagerLoad=true` on a `TurLLMInstance`, a Spring `ApplicationReadyEvent` listener walks the embedded instances and pre-loads each into the cache. First chat turn skips the SafeTensors load. Trade-off: longer Spring boot, locked RAM at startup. Per-instance opt-in. | §XI.5 |
| T205 | 📋 | T193 | **Memory + load observability** — extend `TurChatPipelineObservation` (post-T32) with `turing.embedded.{modelLoadMs, modelEvictions, loadedModelCount, residentBytes, sessionsActive, tokensPerSecond}` Micrometer gauges + timers, tagged by `instanceId`. Exposes the difference between cold-start latency (~5–15s for 7B-I8) and warm-call latency (~50–200ms TTFT) in `/actuator/prometheus`. | §XI.5 |
| T206 | 📋 | T193 | **Status panel in LLM Instance details page** — per-embedded-instance card showing: load state (`unloaded` / `loading` / `ready` / `evicted`), resident RAM, active session count, last-used timestamp, total inferences served, recent average tok/s, whether `jlama-native` SIMD is active. Operator can manually `Load` / `Evict` / `Drop sessions` from the UI without restart. | §XI.5 |

#### G.4 — Capability completion (tool calling, judge, rerank, embedding)

| #    | Status | Depends on | Task | Detail in |
|------|--------|------------|------|-----------|
| T207 | 📋 | T193 | **Tool calling via LangChain4j-Jlama** — wire `JlamaChatModel`'s native tool-calling support (works with **Llama 3.1**, **Mistral**, **Mixtral** today; **JSON-mode not yet supported** — that's the LangChain4j gap, T173 strict JSON schemas don't apply to embedded yet). `TurEmbeddedChatModel` exposes a `LangChain4jToolCallback` bridge: Spring AI `ToolCallback[]` → LangChain4j `ToolSpecification[]` → `JlamaChatModel` request → response markers parsed → fed back to Spring AI's tool execution loop. UI surfaces a model-compatibility chip on T201 metadata ("tool calling: ✓ supported" only for the three families above). | §XI.6 |
| T208 | 📋 | T193 | **Local LlmJudge** — route the guardrail's bilingual judge (today defaults to gpt-4o-mini, $0.15/1M in) to a local 3B I8 Jlama model (~2–3 GB RAM, 5–15 tok/s pure-Java, ~15–30 tok/s with `jlama-native`). On high-volume Education-customer traffic the judge fires every turn; locally that's literally free after the model import. Per-agent toggle `guardrailLocalEmbedded` + `guardrailLocalInstanceId`. Quality validation step: run the existing LlmJudge test suite against the local model before flipping. **Note**: LlmJudge today returns structured JSON; until LangChain4j-Jlama adds JSON mode, the local judge uses prompt-engineered parsing with the existing tolerant JSON extractor (`feedback_gpt4o_mini_json.md` extractor pattern is reusable). | §XI.6 |
| T209 | 📋 | T198 | **Local embedding for `TurLuceneVectorStore` + RAG indexer** — when an agent's embedding provider is an embedded instance with `role=embedding`, the indexer pipes through the local Jlama BGE / MiniLM model instead of OpenAI / Anthropic / Gemini. Avoids cloud bills on bulk re-embedding (where T158 Batch API isn't enough). Indexer batches into N-document chunks per call. Recommended models: `BAAI/bge-small-en-v1.5`, `BAAI/bge-large-en-v1.5`, `sentence-transformers/all-MiniLM-L6-v2` (verified to work with Jlama). | §XI.6 |
| T210 | 💭 | T193, T209 | **Local rerank stage in T24 hybrid RAG** — Jlama has **no native rerank API** (unlike kherud 4.1). Workaround: load a cross-encoder (e.g. `BAAI/bge-reranker-base`) as a BERT-family `AbstractModel` and score `(query, doc)` pairs via a custom adapter that pools the `[CLS]` token. Significant integration work (~1 week) vs zero-effort cloud rerank. Per-`TurSNSiteGenAi` toggle `rerankLocalEmbedded`. **Decision pending**: may be cheaper to stick with OpenAI-Cohere rerank for now and revisit if Jlama gains a rerank API upstream. | §XI.7 |

#### G.5 — Bold creative bets

| #    | Status | Depends on | Task | Detail in |
|------|--------|------------|------|-----------|
| T211 | 💭 | T193, T207 | **Zero-egress certification mode** — global flag `turing.network.outbound-llm=forbidden`. When set, the engine refuses to call any cloud LLM provider and asserts that every active agent's chat/embedding `TurLLMInstance` is `EMBEDDED_JLAMA` (or another embedded vendor when added). Boot-time fails if not. Compliance pitch: "this Turing install can answer every customer query with zero external network calls — auditable in the startup log". For regulated industries (gov, defense, hospitals, banks). | §XI.7 |
| T212 | 💭 | T193, T208 | **Hybrid orchestration — cheap-by-default, escalate when needed** — chat-flow node-level setting `llmTier`: `embedded` (default for `aiQuestion` / `formCapture` / `writeSlot`), `cloud` (for the final answer composition or complex RAG synthesis). Engine routes each turn through the appropriate `TurLLMInstance`. Material cost reduction at the per-turn level — heuristic-style nodes burn local tokens; only the high-value nodes touch cloud. Pairs naturally with T132 capability matrix. | §XI.7 |
| T213 | 💭 | T193 | **Recommended Jlama model gallery** — curated list of `huggingface.co/tjake/*` pre-quantized models (and known-compatible community releases) at `/console/llm-models/recommended`. One-click "Import to assets" downloads the SafeTensors dir into `TurStorageService` and seeds a `TurLLMInstance` row. Models grouped by role (chat / embedding) and size (1B / 3B / 7B / 13B). Quality chip per model (`good` / `degraded` / `experimental`). | §XI.4 |
| T214 | 💭 | T193 | **`.gguf` → Jlama converter UI** — addresses the user's original "drop a GGUF" intent. Admin uploads `.gguf` to assets; a "Convert to Jlama format" action shells out to `jlama-cli convert` (bundled via `com.github.tjake:jlama-cli:0.8.4+`) → output `model.safetensors` + `config.json` + `tokenizer.json` written back to assets in a new subdirectory. Async job with progress bar. Result is immediately pickable in T199's `JlamaModelPicker`. **Operationally important**: keeps the user-facing UX of "I have a GGUF, just use it" working despite the underlying engine swap. | §XI.4 |
| T215 | 💭 | T193 | **Custom-Tool `embedded` helper** — Custom Tool Groovy scripts get a new helper `embedded.complete(instanceId, prompt, opts?)` / `embedded.embed(instanceId, text)`. Authors can compose multi-step local pipelines (extract entities locally → embed locally → semantic similarity → only then escalate to cloud for synthesis). Stitches naturally with `code`, `workspace`, `slots`, `turingSearch` helpers (T35–T38, T112). | §XI.7 |
| T216 | 📋 | T193 | **JVM Panama Vector flag enforcement** — boot-time check that JVM was launched with `--add-modules jdk.incubator.vector --enable-preview`. If absent, log a precise error pointing at the documented startup snippet + the Docker image variants that ship the flags by default. Spring Boot launcher updated to include the flags in the executable jar's `Main-Class` wrapper + the Docker `ENTRYPOINT`. Required because Jlama's vector ops fail at first call without the modules — caught at boot, not at chat-turn time. | §XI.5 |
| T217 | 💭 | T216 | **`jlama-native` SIMD opt-in** — per-deployment property `turing.embedded.jlama.native-simd=true` (default false). When enabled, the `com.github.tjake:jlama-native` jar (with OS/arch classifier) is loaded and Jlama's `OptimisedOperationsType` routes through hand-tuned SIMD kernels (AVX2 / AVX-512 / NEON). 2–3× speedup at the cost of a native dependency. Documented per platform: Linux/Mac x86_64+arm64 ship binaries; Windows x86_64 ships binary; Windows ARM falls through to pure-Java. T206 status panel surfaces whether SIMD is active for the loaded model. | §XI.5 |
| T218 | 💭 | T193, T208 | **"This conversation cost $0.00" badge** — when every LLM call in a conversation went through embedded instances, the chat UI surfaces a green "$0 — fully local" badge. Operator-visible proof that the local path is being used. Small UX touch, big perceived-trust win for regulated-industry pitches. | §XI.7 |

### Block H — Admin chat ↔ react-sdk convergence (T219–T232)

> Today the admin chat at [chat.page.tsx](../frontend/apps/turing-app/src/app/console/chat/chat.page.tsx)
> + `chat.service.ts`
> reimplements the same SSE transport, conversation-id minting, chip-options
> parsing, `resetFlow` calls, and CSRF cookie reading that
> [`useTuringChat`](../frontend/packages/react-sdk/src/hooks/use-turing-chat.ts)
> in `@viglet/turing-react-sdk` already ships for the public SN chat
> widget. The SDK *already* supports agent mode (`UseTuringChatAgent`
> since 2026.2.16), per-turn options (`onOptions` since 2026.2.17),
> slot reading (`useTuringSlots`), flow state (`useTuringFlowState`)
> and `resetFlow()`. The gap is six features that exist only on the
> admin side, plus the admin page never adopted the SDK after it
> shipped because the admin code predated it. The fix is bidirectional:
> close the 6 SDK gaps, then rewrite the admin page to delegate
> protocol concerns to the SDK while keeping the admin-only chrome
> (multi-agent tabs, IndexedDB session list, AI titles, Markdown +
> SandboxPlayer, scheduleAgent banner, file uploads).
>
> **Why this matters now**: every protocol-level fix (streaming-buffer
> handling, `disabledReason` surfacing, options chip dispatch,
> conversation-id stability across reloads) currently has to be
> patched twice — once in the SDK and once in `chat.service.ts`. Bug
> reports against the admin chat have already drifted from the SDK's
> behavior twice in the last two minor versions (the `disabledReason`
> hint was missing from the admin tooltip until 2026.2.31; the
> options chip race condition fixed in `useTuringChat` 2026.2.17 is
> still latent in [chat.page.tsx:317-334](../frontend/apps/turing-app/src/app/console/chat/chat.page.tsx#L317-L334),
> where the admin reads `options` from the `onOptions` callback after
> the stream closes rather than treating it as part of the assistant
> message's `options` field).
>
> **What stays in the admin app** (not SDK material):
> - Multi-agent tab strip + per-agent state (admin-only — public site
>   chat is single-agent)
> - IndexedDB-backed session sidebar with cross-tab history (admin's
>   `chat-session.service.ts` — public chat persists per-tab via
>   `sessionStorage`)
> - AI-generated session titles (admin uses a second LLM call;
>   could live in the SDK as a one-shot helper, but the trigger is
>   admin-specific)
> - Markdown + `rehype-highlight` rendering + `SandboxPlayer` for
>   inline HTML blocks (`@viglet/viglet-design-system` material, not
>   SDK)
> - Multi-agent tab forwarding from the home page via `sessionStorage`
>   (admin nav glue)
> - Context-bar / token estimator with one-click compaction
>   (admin-only feature surfaced inside the composer)
>
> **What the SDK needs to gain** (T219–T225):
> 1. **`fetchLlmInstances`** + `fetchAgentContextInfo` /
>    `fetchLlmContextInfo` — today only admin's `chat.service.ts`
>    knows how to call them.
> 2. **Multipart upload support on `postAgentChat`** — agent chat
>    endpoint accepts `multipart/form-data` with `request` + `files`
>    parts (admin's `sendAgentStream` doesn't actually use this yet
>    for the agent route, only for the plain `/v2/llm/{id}/chat`
>    route, but the backend already supports files on `aiAgent` per
>    `TurAgentChatExecutor`; closing it in the SDK unlocks file-aware
>    agent chats for SDK consumers too).
> 3. **Per-turn LLM override on `useTuringChat`** — today the
>    `agent.llmInstanceId` is pinned at hook init. Admin lets the
>    user switch LLM dropdown mid-conversation; the SDK has to either
>    accept a per-`send` option or re-fetch the agent's allowed
>    `llmInstances` list and accept dynamic re-binding without
>    losing message state.
> 4. **Real `AbortController` for `stop()`** — today
>    `useTuringChat.stop()` just bumps a `requestRef` counter that
>    suppresses late chunks but doesn't cancel the network call.
>    Admin chat has the same defect; both should pass
>    `AbortController.signal` through to `fetch`.
> 5. **Plain-LLM mode (`useTuringLlmChat` or `mode: "llm"` on
>    `useTuringChat`)** — admin's title-generator + sandbox iframe +
>    "no flow context, just talk to the model" path hits
>    `POST /v2/llm/{id}/chat`, which the SDK doesn't expose at all
>    today. Need a hook that takes `llmInstanceId` (not agent),
>    sends/streams JSON messages, and exposes the same `onToken` /
>    `onOptions` shape.
> 6. **`useTuringScheduleAgentWaiting`** — admin polls
>    `GET /agent/{id}/chat-slots` every 2s for
>    `__scheduleAgent_pending_*` markers (the T48 banner). This is a
>    trivial 30-line wrapper over `useTuringSlots`; lifting it into
>    the SDK lets external chat hosts render the same banner without
>    re-deriving the slot-key prefix convention.
>
> **Admin-side rewrite** (T226–T231): replace the imperative
> `TurChatService.sendAgentStream` flow with `useTuringChat({ agent
> })`; keep the multi-agent tab state but feed each tab's chat hook
> the right `agent.id`; drop the per-message `requestRef`/`abortRef`
> manual state (SDK owns it); read `messages` / `isStreaming` / `send`
> / `stop` / `reset` / `resetFlow` from the hook. Net code delta:
> -350 lines from `chat.page.tsx`, -200 lines from `chat.service.ts`
> (drop `sendAgentStream`, `sendStream`, `sendSemanticStream`,
> `handleSseLine`, `ensureCsrfToken` — all of which the SDK has).
>
> **Testing surface**: SDK gains 4 new hook tests (title generation
> path, per-turn LLM override, multipart agent chat, schedule-agent
> waiting). Admin gains 3 IT-level smoke tests proving the rewritten
> page still passes today's manual checklist (send/receive/options
> click, session restore, scheduleAgent banner appears and clears).

#### H.1 — SDK gaps (close before admin migration can start)

| #    | Status | Depends on | Task | Detail in |
|------|--------|------------|------|-----------|
| T219 | ✅ | — | **`fetchLlmInstances()` + `fetchLlmContextInfo(llmId)` + `fetchAgentContextInfo(agentId, llmId)` in `core/api.ts`** — three low-level API functions wrapping `GET /llm`, `GET /v2/llm/{id}/chat/context-info`, and `GET /v2/ai-agent/{agentId}/chat/context-info?llmInstanceId=...`. Exported from `index.ts`. New SDK types `TurLlmInstance` (id/title/description/enabled/contextWindow/modelName/icon/turLLMVendor — picker-relevant subset of the admin entity), `TurLlmVendor` (id/title/description), and `TurLlmContextInfo` ({ contextWindow, source }) added to `core/types.ts` and re-exported alongside the existing `TurChat*` types. Drop-in replacement for admin's `chat.service.ts:172-182, 344-350` (now deleted) — admin currently owns these because the SDK never needed them on the public path. Backend endpoints unchanged; SDK paths use the existing `/api` baseURL convention (same pattern as `postChatConversation` / `postAgentChat`). Verified: `node node_modules/typescript/lib/tsc.js --noEmit` clean (exit 0) on `frontend/packages/react-sdk`. T220 (`useTuringLlmInstances` hook) and T226 (admin migration) consume these on top. | §XII.1 |
| T220 | ✅ | T219 | **`useTuringLlmInstances()` hook** — new [use-turing-llm-instances.ts](../frontend/packages/react-sdk/src/hooks/use-turing-llm-instances.ts) wraps `fetchLlmInstances()` + `fetchLlmContextInfo()` / `fetchAgentContextInfo()` (when `agentId` is set) and exposes `{ instances, loaded, selectedId, selectedInstance, contextWindow, select, error }`. Module-level `contextWindowCache` `Map` (keyed by `agentId:llmId` so per-agent overrides don't collide with the plain probe) dedupes repeated mounts. Persistence is opt-in via `options.persistKey` — admin keeps its `LLM_STORAGE_KEY` localStorage behaviour as a one-line `useTuringLlmInstances({ persistKey: LLM_STORAGE_KEY })`; public consumers stay in-memory. Default context-window fallback is `128_000` (matches admin's `DEFAULT_CONTEXT_WINDOW`). Auto-picks the first enabled instance when the persisted id is gone (avoids the 400 the admin used to hit with stale ids pointing at deleted instances). `filterDisabled` toggle (default `true`) mirrors admin's `enabled === 1` filter. Exported from `index.ts`. Verified: `tsc --noEmit` clean. T226 closes the migration on the admin side. | §XII.1 |
| T221 | ✅ | T221a | **Multipart agent chat in `postAgentChat`** — `PostAgentChatOptions` extended with `files?: ReadonlyArray<File>`. When non-empty the SDK switches the agent chat request to `multipart/form-data` (a `request` part carrying the existing JSON body as an `application/json` blob + N `files` parts), letting the browser set the multipart `Content-Type` + boundary; without files, the request stays as plain `application/json`. Mirrors the structure already used by `postLlmChat` (T224). Drop-in replacement for the admin's `chat.service.ts:106-118` (now deleted) pattern. Verified: SDK `tsc --noEmit` clean, SDK full build emits the new `dist/core/api.{js,d.ts}`, admin app `tsc --noEmit -p tsconfig.app.json` clean against the rebuilt SDK. | §XII.1 |
| T221a | ✅ | — | **Backend multipart variant of the agent chat endpoint** — added [`TurAIAgentChatAPI.chatMultipart`](../turing-app/src/main/java/com/viglet/turing/api/agent/TurAIAgentChatAPI.java) (`@PostMapping(consumes = MULTIPART_FORM_DATA_VALUE)`) alongside the existing JSON `chat` variant; both delegate to a shared private `doChat(...)` helper that runs all the existing agent/LLM allow-list checks + code-interpreter cookie binding before calling the executor. New constructor overload `TurAgentChatExecutor.execute(agent, llm, history, systemPromptOverride, conversationId, flowId, files)` threads `List<MultipartFile>` into `TurChatPromptAssembler.assemble(..., files)` (also a new overload). The assembler's user-message build loop locates the LAST user message (the visitor's current turn) and routes it through the shared `TurChatAttachmentService.buildUserMessageWithFiles(text, files)` — the rest of the history (older user turns / assistant turns) stays as plain `UserMessage`/`AssistantMessage`. Backwards-compatible: every existing caller (4-arg / 6-arg `execute`, 7-arg `assemble`) keeps working via convenience overloads that pass `null` for `files`. **Refactor win**: the file-handling helpers (`buildUserMessageWithFiles`, `extractTextFromFile`, `IMAGE_MIME_TYPES`) moved out of `TurLLMChatAPI` into a new `@Service` [TurChatAttachmentService](../turing-app/src/main/java/com/viglet/turing/genai/TurChatAttachmentService.java) so the plain-LLM endpoint and the AI Agent endpoint attach attachments through one canonical implementation. The 9 reflection-based file-handling tests in `TurLLMChatAPITest` moved to a clean [TurChatAttachmentServiceTest](../turing-app/src/test/java/com/viglet/turing/genai/TurChatAttachmentServiceTest.java) (real-Tika, no reflection); 2 delegation tests stayed on the API test (one stubs the mock + asserts the service is called for the last user msg, one asserts the service is NOT invoked when the last msg is `assistant`). Verified: backend compile clean (854 source files); `TurAIAgentChatAPITest` 16/16 + `TurLLMChatAPITest` 17/17 + `TurChatAttachmentServiceTest` 10/10 — 43/43 green. Pre-existing failure unrelated to this work: `TurAgentChatExecutorStructuralIT$ForbidsToolCallsUnitTests` errors with `TurAbBanditService: No default constructor found` on a clean tree too (confirmed via `git stash` + re-run); to be tracked separately. | §XII.1 |
| T222 | ✅ | — | **Per-turn LLM override on `useTuringChat`** — `UseTuringChatAgent` extended with `allowOverride?: boolean` (documentary flag for host UIs); `send(content, overrides?: SendOverrides)` takes an optional `{ llmInstanceId? }` bag honoured only in agent mode (site mode silently ignores it — the site picks its agent + LLM server-side). When the override is set the `postAgentChat` call uses it; the hook never mutates `agent.llmInstanceId` so the caller decides whether the swap is "sticky" (re-render with a new pinned model) or "per-turn" (one-shot picker). New `SendOverrides` type exported from `index.ts`; existing single-arg `send(content)` callers stay green. Verified: `tsc --noEmit` clean. T227 wires admin's per-turn LLM picker into this shim. | §XII.1 |
| T223 | ✅ | — | **Real `AbortController` for `stop()`** — new `abortControllerRef = useRef<AbortController \| null>(null)` is minted at the top of every `send` (prior controller `.abort()`'d first so chained sends cancel cleanly), the controller's `signal` is plumbed into `postAgentChat` / `postChatConversation` via the existing `signal` option, and both `stop()` and `reset()` now call `.abort()` then null out the ref. Caught `AbortError` (DOMException + Error name fallback for Node test envs) is treated as an intentional stop — extracted into a top-level `isAbortError(err)` helper plus a hook-level `handleSendError` callback so the streaming `send` body stays under the cognitive-complexity ceiling. The `requestRef` counter + `abortedRef` flag stay as the cross-send/late-chunk guard so any pre-existing fetch responses dropped after `stop()` don't race the new request. Net: stopping a turn now actually cancels the in-flight `fetch` (and the backend stops emitting tokens we'd throw away). Verified: `tsc --noEmit` clean on the SDK. | §XII.1 |
| T224 | ✅ | T219 | **`useTuringLlmChat({ llmInstanceId })`** — new [postLlmChat()](../frontend/packages/react-sdk/src/core/api.ts) API + [use-turing-llm-chat.ts](../frontend/packages/react-sdk/src/hooks/use-turing-llm-chat.ts) hook for the plain-LLM endpoint `POST /v2/llm/{id}/chat` (no agent, no site, no flow). Hook shape mirrors the admin-relevant subset of `useTuringChat`: `{ messages, status, error, isStreaming, send, stop, reset }` — dropped `enabled`/`disabledReason` (no `/chat/enabled` probe), `conversationId` (endpoint is stateless), `resetFlow` (no flow runtime). `send(content, { files? })` accepts optional `File[]`; with files the request switches to `multipart/form-data` (`request` part + N `files` parts) matching the backend's [TurLLMChatAPI.chat](../turing-app/src/main/java/com/viglet/turing/api/llm/chat/TurLLMChatAPI.java#L177-L183) multipart variant; without files it stays on the JSON variant. Real `AbortController` plumbed through `stop()`/`reset()`/`send`-chaining (same pattern as T223). New `PostLlmChatOptions`, `UseTuringLlmChatOptions`, `UseTuringLlmChatReturn`, `LlmSendOverrides` types exported. **Deferred**: shared `useStreamingChatCore` private helper — `useTuringChat`'s streaming/bubble pattern is intertwined with site-mode bifurcation (enabled probe, conversationId minting, options chips, resetFlow), so the cross-hook extraction was deferred until `useTuringChat` itself is refactored. The two hooks share the API layer (`consumeAssistantStream` + `postLlmChat`/`postAgentChat`/`postChatConversation`); the per-hook `send`/`stop`/`reset` state machine is intentionally duplicated for now. Verified: `tsc --noEmit` clean on the SDK; full `tsc` build emits `dist/hooks/use-turing-llm-chat.{js,d.ts}`; admin app `tsc --noEmit -p tsconfig.app.json` against the rebuilt SDK exit 0. T229 (title generator migration) and T230 (delete `TurChatService`) unblock on this. | §XII.1 |
| T225 | ✅ | — | **`useTuringScheduleAgentWaiting({ agentId })`** — admin-flavoured alias of the existing `useTuringRoutineWaiting` hook (which already wraps `useTuringSlots` + filters the `__scheduleAgent_pending_` markers — see [use-turing-routine-waiting.ts](../frontend/packages/react-sdk/src/hooks/use-turing-routine-waiting.ts)). New file [use-turing-schedule-agent-waiting.ts](../frontend/packages/react-sdk/src/hooks/use-turing-schedule-agent-waiting.ts) accepts the bare `agentId` (instead of `agent: { id }`) and defaults `intervalMs` to 2000 to match the admin code's cadence. Auto-disables the underlying polling when `agentId` is undefined so the hook is safe to mount before the active-agent state lands. Exported from `index.ts` next to the parent hook. Verified: `tsc --noEmit` clean. Admin migration is a one-line import swap (covered by T228). | §XII.1 |

#### H.2 — Admin migration (drop the duplicated transport)

| #    | Status | Depends on | Task | Detail in |
|------|--------|------------|------|-----------|
| T226 | ✅ | T219, T220 | **Replace admin's `useLlmInstances` with `useTuringLlmInstances`** — local hook file [use-llm-instances.ts] deleted; `chat.page.tsx` now imports `useTuringLlmInstances` from `@viglet/turing-react-sdk` with `persistKey: LLM_STORAGE_KEY` + `defaultContextWindow: DEFAULT_CONTEXT_WINDOW` so the localStorage persistence + 128k fallback are unchanged. Field-name remapping done in the destructure (`instances → llmInstances`, `selectedId → selectedLlmId`, `select → selectLlm`) so the rest of the page stays diff-free. `turChatService` (previously returned by the local hook as a passthrough) is now instantiated once at module scope alongside `turAIAgentService` / `turGlobalSettingsService`. `ChatEmptyState` + `ChatHeader` props widened to structural-subset types (`DisplayInstance` / `PickerInstance` with only the fields actually read) so they accept both the admin's `TurLLMInstance` and the SDK's `TurLlmInstance` without a cast. Agent-filtered `visibleLlmInstances` `useMemo` stays — that's admin business logic joining the LLM list with `activeAgent.llmInstances`. Verified: `tsc --noEmit -p tsconfig.app.json` clean on `frontend/apps/turing-app`; SDK rebuilt so `dist/` carries the new exports. | §XII.2 |
| T227 | ✅ | T222, T223, T224, T227a | **Replaced `TurChatService.sendAgentStream` call sites with `useTuringChat({ agent })`** — new [AgentChatTab](../frontend/apps/turing-app/src/app/console/chat/components/agent-chat-tab.tsx) child owns one tab's `useTuringChat` (agent mode): messages, streaming/loading, abort, and chip `options` now come from the hook, not the page's per-token `setAgentStates`. [chat.page.tsx](../frontend/apps/turing-app/src/app/console/chat/chat.page.tsx) drops `TurChatService` entirely (the `sendAgentStream` ×2 + `resetAgentFlowState` callers are gone — flow reset routes through the hook's `resetFlow()` → `deleteAgentFlowState`); the `agentStates` map collapses to the child's local `input` + a lifted `hasMessages` flag. **SDK additions (additive, backward-compatible, no existing consumer touched — only `ai-mode-chat.tsx` uses the hook, in site mode):** `UseTuringChatOptions.conversationId` (controlled backend id — wins over cookie minting; a sync effect adopts it into state+ref so `resetFlow()` works before the first send) + `UseTuringChatOptions.initialMessages` (seed transcript at mount). **Three scoping design points resolved per the documented recommendations:** (1) *session restore* — used `initialMessages` (recommendation a's core) re-seeded via a `chatEpoch` remount key rather than an imperative `restore()` handle (cleaner, since the controlled `conversationId` makes remount lossless — no re-mint); (2) *compaction* — kept admin-only, a thin direct `postAgentChat` call summarizes then reseeds the child with `[summary]` on a bumped epoch, same conversation id (recommendation a — no `replaceMessages` added to the SDK); (3) *tab-state retention* — recommendation (b): only the active agent's tab is mounted, completed turns persist to IndexedDB via the unchanged `saveAfterResponse`; **behaviour change** — switching agent tabs no longer keeps the other tab's in-memory transcript (it lives in the sidebar instead). **Conversation-id ownership** (the implicit 4th decision): the page owns the id — `useChatSession.ensureSessionId()` mints it eagerly when a tab activates, and it is passed down as the controlled `conversationId`, keeping the backend conversation and the IndexedDB session on one id (so flow routing, restore, and "New chat" all stay coherent). Initial-prompt auto-send, flow selector + reset, scheduleAgent "Aguardando rotina…" banner, context bar, and the no-LLM disabled fallback are all preserved. Verified: SDK `tsc --noEmit` + full build clean (dist `use-turing-chat.{js,d.ts}` carry the new options); admin app `tsc --noEmit -p tsconfig.app.json` against the rebuilt SDK exit 0. **Residual risk** (no runtime/e2e run): the per-tab streaming, restore, compaction-reseed, and tab-switch paths are type-safe but unproven at runtime — T232's three smoke ITs are the intended regression gate. T231 (further extraction + `useTuringSlots` trio + line-count reduction) is now largely realized by this change; T230 can delete `chat.service.ts` once no other page imports it. | §XII.2 |
| T227a | ✅ | — | **`useTuringChat` context dependency optional in agent mode** — same shape as T228a but applied to [useTuringChat](../frontend/packages/react-sdk/src/hooks/use-turing-chat.ts): swapped `useTuringContext()` for `useOptionalTuringContext()` (line 287), added the site-mode guard (`if (!ctx && !isAgentMode) throw …`), captured `siteName = ctx?.config.site ?? ""` once and threaded it through `persistKey` derivation, the chat-enabled probe, `postChatConversation`, and `deleteSiteConversationState`. Same applies to `effectiveLocale = localeOverride ?? ctx?.config.locale` — agent-mode callers can omit both `<TuringProvider>` and `locale`. The chat-enabled probe is short-circuited when `siteName` is empty (the agent-mode early-return already handled this; the new guard makes the no-provider site-name-empty case equivalent). `useEffect` deps updated to `[siteName, isAgentMode]` and `[siteName, effectiveLocale, maxMessages, flowId, agentId, agentLlmInstanceId, handleSendError]` so the dependency graph stays accurate. Net: an admin/console app with no `<TuringProvider>` can call `useTuringChat({ agent: { id, llmInstanceId } })` and gets the agent-mode pipeline. Public-site consumers (mounted under `<TuringProvider>`) get identical behaviour because `ctx` is always non-null in that path. Verified: `tsc --noEmit` clean on the SDK; `tsc` full build emits the new `dist/hooks/use-turing-chat.{js,d.ts}`; admin app `tsc --noEmit -p tsconfig.app.json` against the rebuilt SDK exit 0. T227 (per-tab `useTuringChat` wiring) consumes this. | §XII.2 |
| T228a | ✅ | — | **`useTuringSlots` context dependency optional in agent mode** — new [useOptionalTuringContext()](../frontend/packages/react-sdk/src/core/use-turing-context.ts) helper (non-throwing variant of `useTuringContext`) plus a runtime guard inside [useTuringSlots](../frontend/packages/react-sdk/src/hooks/use-turing-slots.ts#L115-L140): site mode still requires the provider (throws with a guiding error if missing); agent mode reads `agentId` from `options.agent` and skips the provider entirely. `config.site` accesses replaced with a captured `siteName` resolved from the optional context. Net: an admin/console app with no `<TuringProvider>` can call `useTuringSlots({ agent: { id } })` (or any hook that delegates to it — `useTuringRoutineWaiting`, `useTuringScheduleAgentWaiting`) without wrapping the world in a placeholder provider. Verified: `tsc --noEmit` clean on the SDK; existing site-mode consumers (public search chat, marketplace apps) get the same behaviour because they're still mounted under `<TuringProvider>`. | §XII.2 |
| T228 | ✅ | T225, T228a | **Replaced admin's `useScheduleAgentWaiting` with the SDK's `useTuringScheduleAgentWaiting`** — local hook file deleted; one-line import swap in [chat.page.tsx:35](../frontend/apps/turing-app/src/app/console/chat/chat.page.tsx#L35) (`import { useTuringScheduleAgentWaiting as useScheduleAgentWaiting } from "@viglet/turing-react-sdk"`) leaves every call site untouched. T228a unblocked the swap by relaxing `useTuringSlots`'s context dependency in agent mode. Bonus fix: the admin's local hook was polling `/agent/{agentId}/chat-slots` (a path that doesn't exist on the backend — only `/api/v2/ai-agent/{agentId}/chat-slots` does), so the banner was always reading 404s and the `catch` swallowed them silently. The SDK hook routes through `fetchAgentChatSlots` which hits the real endpoint; the "Aguardando rotina…" banner now actually responds to live `__scheduleAgent_pending_*` markers. Verified: `tsc --noEmit -p tsconfig.app.json` clean on the admin. | §XII.2 |
| T229 | ✅ | T224 | **Migrate admin's AI title generator to `postLlmChat`** — [use-chat-session.ts:40-58](../frontend/apps/turing-app/src/app/console/chat/hooks/use-chat-session.ts#L40-L58) now calls the SDK's `postLlmChat()` directly (one-shot is the right shape for title generation — no need to spin up the full `useTuringLlmChat` hook for a non-streamed single utterance). The new implementation races an `AbortController.abort()` triggered by `TITLE_TIMEOUT_MS` against the LLM response: success returns the cleaned title; any branch (timeout, abort, network error, malformed response) falls through `catch` to `generateFallbackTitle(msgs)`. Dropped the `turChatService` parameter on `useChatSession` since that was the only caller; updated [chat.page.tsx:181](../frontend/apps/turing-app/src/app/console/chat/chat.page.tsx#L181) accordingly. Verified: `tsc --noEmit -p tsconfig.app.json` clean. T230 (`TurChatService` deletion) still has 4 admin call sites to migrate (`sendAgentStream` × 2, `resetAgentFlowState`, the lingering reference for `sendSemanticStream`); this commit removes 1 of the 5 dependents. | §XII.2 |
| T230 | ✅ | T219 | **Deleted `TurChatService` and `chat.service.ts`** — after T227 retired the last call sites, a frontend-wide sweep confirmed nothing imported the `TurChatService` class anymore and `sendSemanticStream` was dead code (defined, never called — no residual service needed). The only surviving export was the `ChatMessageItem` type (`{ role: "user"|"assistant"|"system"; content }`), consumed by [chat.types.ts](../frontend/apps/turing-app/src/app/console/chat/chat.types.ts) (`ChatMessage extends …`) and [chat-session.service.ts](../frontend/apps/turing-app/src/services/chat/chat-session.service.ts) (`ChatSession.messages`); both now extend the SDK's byte-identical [`TurChatConversationMessage`](../frontend/packages/react-sdk/src/core/types.ts) instead, so the IndexedDB session shape is unchanged. File deleted (-368 lines); stale `TurChatService.sendAgentStream` JSDoc reference in `AgentChatTab` cleaned up. Method→SDK equivalence for the record: `sendStream`→`postLlmChat` (T224), `sendAgentStream`→`postAgentChat` (T221+T222), `queryLLMInstances`→`fetchLlmInstances` (T219), `fetchContextInfo`→`fetchLlmContextInfo` (T219), `fetchAgentContextInfo`→`fetchAgentContextInfo` (T219), `resetAgentFlowState`→`deleteAgentFlowState` (via `useTuringChat.resetFlow()`). Verified: admin app `tsc --noEmit -p tsconfig.app.json` exit 0; frontend-wide grep for `chat.service` / `TurChatService` / `ChatMessageItem` / `sendSemanticStream` returns zero matches. | §XII.2 |
| T231 | ✅ | T226–T230 | **`chat.page.tsx` extraction** — T227 already extracted the per-agent tab content; T231 completed the structural goals. The child was renamed [AgentChatTab](../frontend/apps/turing-app/src/app/console/chat/components/agent-chat-tab.tsx) (matching this spec) and now owns its **full SDK hook trio**: `useTuringChat` (conversation) + `useTuringScheduleAgentWaiting` (the "Aguardando rotina…" banner, which transitively wraps `useTuringSlots`) — `useTuringScheduleAgentWaiting` moved out of the page into the tab, so the parent no longer threads a `routineWaiting` prop and each tab polls its own conversation's slots. The duplicated `toTuringMessages`/`toAdminMessages` admin↔SDK mappers were centralized into [chat.types.ts](../frontend/apps/turing-app/src/app/console/chat/chat.types.ts) (one definition, imported by both page and tab). The no-LLM disabled placeholder was extracted to a co-located `AgentNoLlmFallback` (the chat hook can't mount in agent mode without a valid LLM id, so it stays a sibling, not a branch inside the tab). **Top-level `chat.page.tsx` keeps** exactly what the spec lists: agent loading + default-agent resolution, multi-tab header + LLM/flow selectors (driving the per-tab `agent`/`flowId` props), session sidebar + restore (IndexedDB service unchanged), and compaction orchestration (admin-only, thin direct `postAgentChat`). Net delta: `chat.page.tsx` 571 → 400 lines; `agent-chat-tab.tsx` ~322 lines (incl. the fallback); `chat.service.ts` gone (-368, T230). Verified: admin `tsc --noEmit -p tsconfig.app.json` exit 0; frontend grep for `AgentTabContent`/`agent-tab-content` returns zero matches. Runtime still unproven — T232 smoke ITs remain the regression gate. | §XII.2 |
| T232 | ✅ | T231 | **Admin smoke ITs (RTL)** — bootstrapped the **frontend's first test runner** (none existed in the monorepo): vitest 3.2.4 + @testing-library/react 16 + jsdom + fake-indexeddb, a standalone [vitest.config.ts](../frontend/apps/turing-app/vitest.config.ts) (deliberately NOT extending vite.config — drops Module Federation / Tailwind / boot-loader for jsdom), and [src/test/setup.ts](../frontend/apps/turing-app/src/test/setup.ts) with global module mocks (react-i18next passthrough, react-markdown→raw text, `useCurrentUser` stub, the shared `axios` singleton) + jsdom polyfills (`scrollIntoView`/`ResizeObserver`/`matchMedia`). Three tests in [chat/__tests__/](../frontend/apps/turing-app/src/app/console/chat/__tests__/): (1) **send+stream+chips** mounts `AgentChatTab`, stubs `fetch` with an SSE stream (`data: {type:token…}` / `{type:options…}`), asserts the streamed bubble + chip buttons, then clicks a chip and asserts the 2nd `fetch` body carries the chip label; (2) **session restore** seeds IndexedDB via the real `saveSession`, mounts the full `ChatPage` (services + `/llm` + context-info mocked, wrapped in a fresh `QueryClientProvider` for the header's session-info `useQuery`), asserts the session lists in the sidebar, clicks it, asserts the restored transcript renders (validates the controlled `conversationId` + `initialMessages` re-seed path from T227); (3) **scheduleAgent banner** stubs the `chat-slots` poll to inject a `__scheduleAgent_pending_*` marker, asserts "Aguardando rotina…" shows, clears the marker, asserts the next 2s poll removes it. They double as the regression suite for SDK↔admin protocol drift. Verified: `pnpm install` clean (no Windows EBUSY), **4/4 tests pass** (`pnpm --filter turing-app test`), `tsc -b` (build gate) exit 0 with test files included. Note: vitest 3.2.4 runs fine against Vite 8 here; bump to vitest 4 if a future Vite upgrade needs it. | §XII.2 |

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

#### VII.13.c Flow chooser hook (T106)
`useTuringFlowChooser` is the SDK pairing for the T92 flow-select
endpoint (`POST /api/sn/{site}/chat/flow-select`). It lets a portal
force a specific persona instead of leaving the choice to the LLM
trigger router — the two production triggers being deep links
(`?flow=in-company` lands directly on the B2B account-exec) and explicit
track pickers ("Falar com Vendas" vs "Cursos para mim") rendered before
the conversation starts.

- **API layer**: `postSiteFlowSelect(site, conversationId, flow)` +
  `TurChatFlowSelectResponse` (`{success, pinnedFlowId, pinnedFlowName,
  reason}`). `flow` resolves by UUID first, then case-insensitive name —
  same contract as the endpoint.
- **Imperative core**: `chooseFlow(flow?)` falls back to `options.flow`;
  resolves with the server response, rejects on transport error so a
  caller can `try/catch`. `status`/`error`/`lastResult` +
  `pinnedFlowId`/`pinnedFlowName` mirror the established
  `useTuringHandoff`/`useTuringSlotWriter` shape.
- **Deep-link readiness**: `mintSession` (default `true`) proactively
  mints the `TUR_SESSION` cookie via `getOrCreateTurSession` so the pin
  pre-creates the chosen flow's state *before* the visitor types; the
  chat hook later reuses the same cookie, so the first turn already runs
  the pinned flow. Set `false` to require an existing conversation.
- **Auto-pin**: opt-in `autoSelectFromParam: "flow"` reads the URL query
  param once on mount (guarded by a ref so it fires a single time),
  pinning the deep-linked flow; absent param falls back to `options.flow`.
- Pairs with T93 — slots already captured carry into the pinned flow via
  the engine's `applySlotInheritance`. Storybook story simulates the
  track-picker round-trip (latency + failure variants).

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
| ~~`forbidsToolCalls(String)` substring overload (TK3)~~ | ✅ Shipped in T16 — only the node-aware overload survives, reading `ChatFlowNode.toolsEnabled` declaratively. | Done |

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
providers only. A sibling executor `TurNativeChatExecutor` (T131)
routes a turn through the native client when both `TurLLMInstance.vendor`
and `TurAIAgent.nativeCapabilitiesJson` agree on a feature; otherwise
it delegates to the post-T33 collaborator graph (resolver / assembler
/ dispatcher) verbatim, with one `[Native]` tag added to every
`STAGE_CHAT_*` meter so dashboards can A/B the two paths at observation
time.

**Migration philosophy**: this is **not** "rip out Spring AI". It's
"keep Spring AI as the default, opt into native paths per capability
per agent per LLM instance." The per-LLM-instance capability matrix
(T132) makes that opt-in explicit: an admin enabling `openai-web-search-tool`
on instance X turns on T133 for that instance only, every other
instance keeps falling through to the Spring AI executor. Block F has
**no monolithic flag day** — features ship one at a time, each guarded
by its own capability row.

### X.2 The seam — `TurNativeProviderClient` (T130, T131, T132)

```java
public interface TurNativeProviderClient {
    Optional<OpenAIClient> openAi();           // com.openai:openai-java
    Optional<AnthropicClient> anthropic();     // com.anthropic:anthropic-java

    /** Capability lookup for the agent + instance pair. */
    boolean hasCapability(String agentId, String instanceId, String key);

    /** Decrypted runtime credentials, audit-trail-logged. */
    NativeCredentials credentials(String instanceId);
}
```

- Decryption / resilience / pipeline decoration reuse
  `TurLlmModelFactory` (no parallel security path).
- Capability lookups are `@Cacheable("turNativeCapability")` with
  eviction tied to `TurLLMInstance.save/delete` and
  `TurAIAgent.save/delete` — keeps the per-turn lookup ~free.
- `TurNativeChatExecutor` is **a thin variant of `TurAgentChatExecutor`,
  not a replacement** — the three collaborators
  (`TurChatToolResolver` / `TurChatPromptAssembler` /
  `TurChatStreamingDispatcher`, all post-T33) are reused; only the
  LLM dispatch leg differs. Token observability, slot/workspace SSE,
  chat memory, tool pre-filter, prompt caching all stay identical.

### X.3 OpenAI Responses API + built-in tools (T133–T138)

The Responses API (`/v1/responses`) is OpenAI's new primary endpoint.
Built-in tools (`web_search`, `file_search`, `code_interpreter`,
`computer_use`, `image_generation`, remote `mcp`) execute server-side
— Turing pays for compute via OpenAI but writes **zero** Java tool
callbacks for any of them.

**Killer use cases**:

- **T133 `web_search` — "Live answers" mode** (foundational for T187).
  Today an enterprise-search platform can only answer questions where
  the index already has the answer. With `web_search` enabled, the
  chat fuses SN hits with fresh web context **inside one answer**, each
  web claim carrying a clickable citation chip. Solves the long-standing
  "what about news after the last reindex?" gap that drives users back
  to Google.

- **T134 `file_search` — managed vector store tier**. Alongside the
  existing embedded Lucene (T19/T24) and dedicated Solr/ES BM25 cores
  (T24b), OpenAI-hosted vector stores become a third option, selected
  per `TurSNSiteGenAi`. Ideal for ad-hoc per-user data that doesn't
  warrant a permanent indexer pipeline: a CV uploaded mid-chat, an
  RFP document in a sales conversation, an attached PDF in an email
  thread. Per-tenant attribute filters guarantee isolation.

- **T135 `code_interpreter` — managed analytics sandbox**. Today's
  T80 container path keeps the customer-authored Python ecosystem
  (T77 per-agent `pip install`, matplotlib, reportlab) — that
  ecosystem matters and isn't going away. But for one-shot "give me
  a chart of these numbers" answers in chat, the OpenAI Container
  path is one less moving part. Admins choose per agent.

- **T136 `computer_use` — connector self-installation** (T190). The
  market-changing application: "Connect my Confluence" → an agent
  navigates the customer's Confluence admin UI, generates a token,
  configures Turing's connector. Per integration the engineering
  cost drops from "days" to "zero." Long-tail SaaS connector library
  on demand. **High-leverage demo material; production-grade reliability
  is medium-term.**

- **T138 remote `mcp` tool** — Responses calls a Streamable HTTP / SSE
  MCP server URL directly. Turing's own MCP server becomes a first-class
  consumer (no more `MethodToolCallbackProvider` duplication on
  `/api/v2/llm/{id}/semantic-chat`). Customer-facing payoff: add
  third-party MCP servers (Confluence, Jira, GitHub, Notion) to the
  agent's allow-list and **the agent searches them all in one turn,
  no Java written**. The federation story (T145) generalizes this
  across vendors.

### X.4 Anthropic server-side tools (T139–T143)

Same shape, different vendor. Important wins specific to Anthropic:

- **T141 `code_execution` is FREE when paired with web_search /
  web_fetch.** Anthropic's pricing has a deliberate carrot for the
  retrieve-then-compute pattern. Combine with T139 / T140 and Claude
  composes Python that reads retrieved JSON / CSV → emits a chart →
  Turing stores it in `TurAgentWorkspace` (T111) and surfaces via
  the workspace SSE bus (T113). All in a single Anthropic turn.
- **T143 `computer_use_20251124` ships a server-side
  prompt-injection classifier** that scores every screenshot
  before passing it back to the model. Higher trust than T136
  (OpenAI variant has no equivalent classifier). For customers
  with stringent security review, this is the production-grade
  computer-use path.
- **T142 `bash` + `text_editor` enable agentic content authoring.**
  Pair with `TurAgentWorkspace` (T111) and Claude can iteratively
  edit a markdown playbook across multiple turns (`text_editor.create`
  → `text_editor.str_replace` → `text_editor.view`), with the workspace
  surfacing the live draft to the operator (T120 spectator mode).

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

### X.11 Predicted Outputs + strict structured outputs (T171–T174)

T171 + T172 make the "regenerate-with-tweak" loop in the chat-flow
editor (T97 variant generator), persona-prompt editor, locale-string
editor, and the user-facing "rephrase shorter" feel **instant** instead
of 3–5 seconds. Wherever the output mostly overlaps an input string,
the speedup is 3–5×.

T173 + T174 replace the legacy `json_object` JSON-mode workarounds
(remembered explicitly as `feedback_gpt4o_mini_json.md` in agent
memory) with **strict schema-validated structured outputs**. Every
Turing path that parses LLM JSON — `LlmJudge`, T28 LLM intent
classifier, T97 variant generator, chat-flow router decision —
becomes deterministic by construction. The class of "the model
returned truncated JSON, the parser exploded" bugs is gone.

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

---

## XI. Embedded LLM via Jlama — pure-Java in-process inference

> Triggered by a 2026-05-25 evaluation of
> [`com.github.tjake:jlama-core`](https://github.com/tjake/Jlama),
> the pure-Java LLM inference engine using Project Panama Vector
> API. Goal: an admin imports a model into asset storage, picks it
> in the LLM Instance form, and the resulting `TurLLMInstance` serves
> chat / embedding **in-process** — no Ollama server, no cloud API
> account, no JNI native libs to extract, no second container.

### XI.1 Why this matters — three deployments it unlocks

1. **Air-gapped / regulated deployments**. Government, defense,
   hospitals, banks that **cannot** call any cloud LLM. Today Turing's
   only real option is Ollama-as-sidecar; that's an extra service to
   sell, deploy, monitor, and certify. Embedded Jlama turns
   "deploy a separate inference server" into "import a model into the
   storage UI" — pure-Java, no JNI, no sidecar.
2. **Demo / dev installs**. The number of times a prospective customer
   bounces at "you need an OpenAI API key to try it" is non-trivial.
   With T193, a one-command `docker run openviglet/turing` and a
   pre-bundled tiny model (e.g. `tjake/TinyLlama-1.1B-Chat-v1.0-Jlama-I8`)
   gives a working chat agent in 60 seconds.
3. **Cost arbitrage on high-frequency stages**. The LlmJudge
   (T208) fires every turn under `LLM_JUDGE` guardrails; embedding
   generation (T209) fires every indexed chunk. Routing those to a
   local 3B I8 model is **literally free** after the model import.
   T212 layers the chat-flow tier so cheap nodes go local, hard nodes
   escalate to cloud — material cost reduction at the per-turn level.

### XI.2 The version + format reality — what to pin and what to feed it

**Dependency**:

```xml
<dependency>
  <groupId>com.github.tjake</groupId>
  <artifactId>jlama-core</artifactId>
  <version>0.8.4</version>  <!-- verify latest at integration time -->
</dependency>
<!-- Optional native SIMD acceleration (T217): -->
<dependency>
  <groupId>com.github.tjake</groupId>
  <artifactId>jlama-native</artifactId>
  <version>0.8.4</version>
  <classifier>${os.detected.classifier}</classifier>
  <optional>true</optional>
</dependency>
<!-- For tool calling via LangChain4j bridge (T207): -->
<dependency>
  <groupId>dev.langchain4j</groupId>
  <artifactId>langchain4j-jlama</artifactId>
  <version>1.15.0-beta25</version>
</dependency>
```

The 0.x line is still current as of 2026-05 — no 1.x yet. Re-check
Maven Central at integration time.

**Format reality — Jlama is NOT a GGUF runtime**. Jlama reads
**HuggingFace SafeTensors directories**, structured as:

```
<model-dir>/
  config.json           # architecture metadata
  tokenizer.json        # HF tokenizer
  model.safetensors     # quantized weights (or shards model-00001-of-N.safetensors)
```

Quantizations supported internally: **F32 / F16 / BF16 / Q8 / Q4**.
Two acceptable ingestion paths in Turing:

1. **Direct import of HF SafeTensors** — admin clones a Jlama-ready
   model into `models/` in asset storage. The canonical source is
   [`huggingface.co/tjake/*`](https://huggingface.co/tjake) where the
   maintainer ships pre-quantized models that load cleanly.
2. **GGUF → Jlama conversion via `jlama-cli`** (T214) — admin uploads
   a `.gguf`, Turing runs `jlama-cli convert` (bundled via
   `com.github.tjake:jlama-cli`) → output SafeTensors dir → result
   immediately pickable in the LLM Instance form. **This preserves
   the user's original "drop a GGUF" intent** despite the engine
   under the hood not consuming GGUF natively.

**Architecture support is restricted.** Jlama supports
**Llama / Llama2 / Llama3 / Llama3.1, Mistral, Mixtral, Gemma,
Qwen2, GPT-2, BERT** — and that's it. Phi-3 mini variants,
DeepSeek-V3 MoE, Mamba / state-space models, exotic vision-language
models do **not** load. T201's metadata pre-flight reads
`config.json`, asserts the `model_type` is in the supported list,
and refuses to save with a helpful list of supported families.

**Runtime requirement**: Jlama uses **Project Panama Vector API**
which is still incubator in JDK 21. The JVM must launch with:

```
--add-modules jdk.incubator.vector --enable-preview
```

A single missing flag → inference fails at first call with a
cryptic `NoClassDefFoundError`. T216 enforces this at boot — Spring
Boot launcher updated, Docker `ENTRYPOINT` updated, boot-time check
runs before any cache is touched.

> **JDK version decision (2026-05-27) — why Turing stays on Java 21,
> and what the flags become on 22+/25.** The `--enable-preview` above
> is required **only on JDK 21**. Jlama leans on two Panama APIs with
> different maturity:
>
> | Panama API Jlama uses | JDK 21 | JDK 22+ / 25 |
> |---|---|---|
> | **Foreign Function & Memory** (off-heap, native SIMD) | preview → needs `--enable-preview` | **final since JDK 22 (JEP 454)** → flag drops |
> | **Vector API** (SIMD kernels) | incubator → `--add-modules jdk.incubator.vector` | **still incubator even in JDK 25 (JEP 508)** → flag stays |
> | Native downcalls | — | warning on JDK 24+ → add `--enable-native-access=ALL-UNNAMED` |
>
> **The real cost of `--enable-preview`**: preview-compiled `.class`
> files are stamped with minor version `0xFFFF` and load **only** on a
> JVM of the exact same major version running in preview mode. On JDK
> 21 that pins the **entire `turing-app`** to Java 21 + preview — which
> is precisely why embedded Jlama *server-wide* is awkward today, and
> why moving to **JDK 25 (LTS)** is the real unlock: `--enable-preview`
> disappears, leaving only the two innocuous launch flags (which do
> *not* pin the bytecode to a JDK version).
>
> **Why we are NOT on JDK 25 yet — and the blocker is *not* Jlama.**
> Spring Boot 4.0.6's managed BOM pins two deps that break on Java 25:
>
> - **Artemis `2.43.0`** → hard crash at broker init on Java 25:
>   `UnsupportedOperationException: getSubject is not supported`
>   (`Subject.getSubject` removed alongside the SecurityManager). Fixed
>   in **Artemis 2.44.0** (multi-release SecurityManager/Subject shim).
>   Spring Boot issue **#48344** was closed *"not planned /
>   external-project"* — Boot will not bump it for us on the 4.0 line.
> - **Hazelcast `5.5.0`** → Java 25 friction; **5.7.0** ships certified
>   JDK 25 support (Docker tag `5.7.0-jdk25`).
>
> Both are fixable today by overriding the managed versions
> (`<artemis.version>2.44.0</artemis.version>`,
> `<hazelcast.version>5.7.0</hazelcast.version>`) plus the Hazelcast
> `--add-opens` set. **Decision (2026-05-27): stay on JDK 21 and wait
> for Spring Boot to bump the managed Hazelcast/Artemis versions**
> rather than carry manual overrides ahead of the BOM. Re-evaluate
> embedded-Jlama-on-the-server once the project is on JDK 25 — validate
> with `mvn verify -pl turing-app` (the IT suite boots embedded Artemis
> + Hazelcast via `AbstractTuringSpringIT`, so it is the real Java-25
> smoke test).
>
> **Consequence for this block while on JDK 21**: keep embedded Jlama
> strictly opt-in (`embedded-jlama` plugin type, off by default) and
> run it with `--enable-preview`; the flag lines in XI.2 / XI.5 #1 are
> correct for JDK 21. The **CLI** use case (single binary, no external
> LLM) can carry its own launcher with these flags independently of the
> server's JDK story.

### XI.3 Architecture — provider + model cache + storage resolver

The three structural pieces this whole block hinges on.

**`TurEmbeddedLlmProvider`** (T193) is small — Jlama's
thread-safety lets us hand out the cached model directly:

```java
@Component
public class TurEmbeddedLlmProvider implements TurGenAiLlmProvider {
    @Override public String getPluginType() { return "embedded-jlama"; }

    @Override public ChatModel createChatModel(TurLLMInstance i, String _ignored) {
        // _ignored = decrypted apiKey: always null for embedded; never used
        return new TurEmbeddedChatModel(modelCache.getOrLoad(i));
    }

    @Override public EmbeddingModel createEmbeddingModel(TurLLMInstance i, String _ignored) {
        return new TurEmbeddedEmbeddingModel(modelCache.getOrLoad(i));
    }
}
```

**`TurEmbeddedModelCache`** (T194) is simpler than the kherud draft —
no semaphores, no concurrency contortions:

```java
@Service
public class TurEmbeddedModelCache {
    private final Map<String, CachedModel> cache;        // instanceId → loaded AbstractModel
    private final TurStorageService storage;             // T195
    private final int maxLoadedModels;                   // default 2, RAM-pressure eviction

    public CachedModel getOrLoad(TurLLMInstance i) {
        return cache.computeIfAbsent(i.getId(), id -> {
            evictLruIfNeeded();
            Path modelDir = storage.resolveLocalPath(modelAssetKey(i));   // T195
            AbstractModel model = ModelSupport.loadModel(
                modelDir.toFile(),
                workingQuant(i),    // DType.F32 default
                modelQuant(i));     // DType.I8 default
            return new CachedModel(model, i);
        });
    }

    @EventListener public void onUpdate(LlmInstanceUpdatedEvent ev) {
        evict(ev.instanceId());
    }

    @PreDestroy public void shutdown() {
        // Clean — Jlama doesn't have kherud's non-daemon thread quirk
        cache.values().forEach(CachedModel::close);
    }
}
```

**`TurStorageService.resolveLocalPath(key)`** (T195) is unchanged
from the earlier draft and is the only change to the storage
interface:

```java
public interface TurStorageService {
    // ...existing methods...
    Path resolveLocalPath(String key);
}
```

For Jlama specifically the `key` points at a *directory* — the
backend must recursively sync `config.json` + `tokenizer.json` +
every `*.safetensors` shard before returning the local dir path.
MinIO/S3 caches the synced tree under `./store/cache/llm-instances/`.

### XI.4 Frontend UX — model picker + import paths

The user's framing was "escolhendo o arquivo gguf em assets". With
Jlama as the engine, that single GGUF file becomes a model
*directory* (SafeTensors + config + tokenizer). The shipping-grade
UX preserves the spirit of "pick from assets":

1. **Path A — import HF SafeTensors directly.** Admin uses
   `/console/llm-models/import` (new page, T213) to clone a model
   from HuggingFace. Picks from a curated gallery of `tjake/*`
   pre-quantized models or pastes a custom HF model ID. Backend
   verifies the architecture is supported and downloads to
   `models/<name>/` in `TurStorageService`.
2. **Path B — convert a GGUF (T214).** Admin uploads `.gguf` to
   `/console/assets/models/`. Selecting it triggers a "Convert to
   Jlama format" action that shells out to `jlama-cli convert` →
   produces SafeTensors in a new subdirectory → result is
   immediately pickable in T199's `JlamaModelPicker`.
3. **Path C — pre-staged directory.** Ops drops a SafeTensors model
   dir directly into the filesystem storage backend (no UI needed).
   The picker finds it on next list call.

Then the LLM Instance form flow:

1. Admin opens **LLM Instances → New** and picks vendor
   `Embedded (Jlama)`.
2. The form replaces the API-key field with a `JlamaModelPicker`
   (T199) listing every supported model directory.
3. On selection, the form **fetches model metadata** from
   `config.json` (T201) and shows a chip row: `Llama 3.1 · 8B · I8 ·
   8k ctx · ~4.5 GB RAM · tools: ✓`.
4. Form fields `role` (chat/embedding radio), `workingQuant` (F32/I8),
   `modelQuant` (auto-detected), `contextLength`, `eagerLoad`,
   `useNativeSimd` get sensible defaults from
   `applyVendorDefaults("EMBEDDED_JLAMA")` (T200).
5. Save triggers a **pre-flight RAM check** (T202).
6. First chat turn (or boot, if `eagerLoad=true`) loads the model.
   The instance details page (T206) shows live `load state` /
   `resident RAM` / `tok/s` / `SIMD active`.

Empty-state guidance: when the picker has no models, modal links to
T213's recommended gallery + T214's GGUF converter.

### XI.5 Operational realities — what breaks if you don't plan for it

Four failure modes the block design must cover (different from the
earlier kherud draft):

1. **Missing JVM flags.** Without `--add-modules jdk.incubator.vector
   --enable-preview`, Jlama crashes at first vector op with a
   confusing `NoClassDefFoundError`. T216 enforces at boot — Spring
   `ApplicationReadyEvent` listener checks `ModuleLayer.boot()` for
   `jdk.incubator.vector` and fails fast with a precise error message.
   Spring Boot launcher updated to include the flags in the executable
   jar's `Main-Class` wrapper + Docker `ENTRYPOINT` updated.
2. **JVM OOM mid-warmup.** A 13B I8 model is ~7 GB resident; the
   default JVM heap is ~4 GB. Loading without a pre-flight check
   crashes the whole server. T202 + T201 prevent this; T204 lets
   admins choose lazy load if startup memory is tight.
3. **Unsupported model architecture.** Admin imports a Phi-3 or
   Mamba SafeTensors. T201 reads `config.json` *before* selection is
   committed — refuses with a list of supported families. **Catch at
   import time, not at first chat turn.**
4. **The model directory changed under us.** Admin re-imports a model
   with the same name (different weights). T194 listens to
   `LlmInstanceUpdatedEvent`; if the storage event bus (not yet built)
   ever surfaces "directory updated", evict then too. Today: rely on
   the explicit "Reload" button in T206's status panel.

**What is NOT a concern any more (vs the kherud draft):**

- **JVM shutdown hang.** Jlama is pure Java; there's no non-daemon
  JNI thread to block exit. `@PreDestroy` cleanly releases models.
- **Per-instance concurrency lock.** Jlama's `AbstractModel` is
  documented thread-safe (stateless per request, KV-cache keyed by
  `UUID`). The semaphore from the kherud draft goes away — the cache
  hands out the single shared model and concurrent requests just
  pass different UUIDs. T203 is now about **session UUID
  lifecycle**, not concurrency throttling.
- **Windows CUDA missing.** No JNI — no native lib mismatch story.
  `jlama-native` (T217) is opt-in SIMD on top of pure-Java baseline;
  Windows ARM falls back to pure Java gracefully.

T205 wires all of this into `TurChatPipelineObservation` so
`/actuator/prometheus` shows `loadedModelCount`, `residentBytes`,
`sessionsActive`, `tokensPerSecond` per instance.

### XI.6 Tool calling — via LangChain4j-Jlama bridge

Biggest single improvement vs the earlier kherud draft: **tool
calling works** through
`dev.langchain4j:langchain4j-jlama:1.15.0-beta25+`. T207 is a 📋
committed task, not a 💭 long-shot.

```java
// T197/T207 bridge sketch (illustrative):
public class TurEmbeddedChatModel implements ChatModel {
    private final JlamaChatModel lc4j;  // wraps AbstractModel from cache

    @Override public ChatResponse call(Prompt prompt) {
        List<ToolSpecification> tools = bridgeTools(prompt.getOptions());
        var lc4jResponse = lc4j.chat(
            ChatRequest.builder()
                .messages(bridgeMessages(prompt))
                .toolSpecifications(tools)
                .build());
        return bridgeResponse(lc4jResponse);
    }
}
```

**Caveats** — model-family-specific:

- **Llama 3.1, Mistral, Mixtral**: tool calling works reliably.
- **Llama 3 (pre-3.1), Gemma, Qwen2**: tool calling not supported by
  Jlama (no upstream template). Force `toolsEnabled=false` in
  T201's metadata pre-flight UI.
- **JSON-mode (structured output)**: **not yet supported** in
  LangChain4j-Jlama. T173/T174's strict JSON schemas don't apply to
  embedded yet. Fall back to the existing tolerant-JSON extractor
  pattern from `feedback_gpt4o_mini_json.md`.

The model-family compatibility chip rendered by T201 (`tools: ✓` /
`tools: ✗`) sets expectations at selection time.

### XI.7 When NOT to use Jlama — `de.kherud:llama` as the alternative

Jlama is the right primary choice for Turing's "drop-in install on
any OS, zero engineering hours per deploy" pitch. But for a small
class of use cases, **kherud/llama (`de.kherud:llama:4.1.0+`)** wins:

| Dimension | Jlama (`com.github.tjake`) | kherud/llama (llama.cpp JNI) |
|---|---|---|
| Performance (CPU, 7B-Q4) | 5–10 tok/s pure Java, 10–20 with `jlama-native` | 6–12 tok/s |
| GPU | Experimental WebGPU; no production path | CUDA on Linux x86_64 (3.4+) |
| Native libs | Optional (Panama Vector default; SIMD opt-in via `jlama-native`) | Required (extracted automatically; Windows-CUDA missing) |
| Tool calling | Works (T207, LangChain4j) | Not exposed (grammar-shim required) |
| GGUF native | No (SafeTensors only; convert via `jlama-cli`) | Yes (direct `mmap`) |
| Model coverage | Llama/Mistral/Mixtral/Gemma/Qwen2/GPT-2/BERT only | Everything llama.cpp supports (broader) |
| JVM shutdown | Clean | Non-daemon thread (issue #116) |
| Rerank API | Not built-in (T210 needs custom adapter) | Native `rerank(...)` since 4.1 |
| JDK requirement | Java 21 + Panama Vector flags | Java 17+, no special flags |
| Maintenance velocity | Slow (~yearly release on Central) | Fast (~monthly release) |

**Decision rule for Turing**:

- **Air-gapped Windows / Mac deployments where ops simplicity wins**: Jlama.
- **Linux x86_64 servers with GPU and tool-heavy agents**: consider kherud.
- **Customer wants to "drop a GGUF" and use exotic models**: kherud or convert via T214.

T210's local rerank caveat (Jlama has no native rerank API) is the
clearest case for routing some workloads through kherud anyway. A
future `TurEmbeddedRuntime` abstraction with both implementations
keeps the door open — but Block G ships Jlama first since the
operational simplicity is the primary win.

### XI.8 Bold creative bets

Eight long-shot tasks that extend the block's reach well beyond
"another LLM provider":

1. **T211 — Zero-egress certification mode.** A boot-time check that
   refuses to start if any active agent has a cloud LLM bound. Single
   line in the admin log: "All N agents resolve to embedded
   instances. Outbound LLM calls blocked." For regulated industries,
   this is sales-grade proof.
2. **T212 — Hybrid orchestration by chat-flow tier.** `llmTier:
   embedded` on cheap nodes (`aiQuestion`, `formCapture`, `writeSlot`);
   `cloud` only on synthesis-heavy nodes. Per-turn cost falls
   substantially without quality drop on the boring nodes.
3. **T207 — Tool calling via LangChain4j-Jlama.** No longer a long
   shot — committed and on the foundation track. Llama 3.1 / Mistral /
   Mixtral give Turing local tool-calling agents out of the gate.
4. **T208 — Local LlmJudge.** Routes the bilingual guardrail judge
   to a local 3B I8. Free per turn. Quality validation step required
   before flipping (run the LlmJudge suite against the local model).
5. **T209 — Local embedding for `TurLuceneVectorStore`.** Removes
   cloud bills from bulk re-embedding workloads where T158 Batch
   isn't enough. Recommended models: `BAAI/bge-small-en-v1.5`,
   `sentence-transformers/all-MiniLM-L6-v2`.
6. **T214 — GGUF→Jlama converter UI.** Preserves the user's original
   "drop a GGUF" UX intent. `jlama-cli convert` bundled in the Turing
   container; admin's upload becomes a usable model in minutes.
7. **T217 — `jlama-native` SIMD opt-in.** Drops a single-line
   property + a native classifier jar to unlock 2–3× speedup. No code
   change for the admin; clear opt-in for the deployment ops team.
8. **T218 — "$0 conversation" badge.** Surfaces a green chip in the
   chat UI when every call in the conversation went through embedded
   instances. Tiny touch, big perceived-trust win.

### XI.9 Sequencing — what to ship first

**Group 1 — Foundation (T193–T198, T216, ~3 weeks)**. Nothing else
ships without these:

- T216 JVM Panama Vector flag enforcement + Docker baseline (1 day)
- T193 provider skeleton + Maven dependencies (2 days)
- T194 model cache (3 days)
- T195 `TurStorageService.resolveLocalPath` directory-aware (3 days)
- T196 vendor row (half day)
- T197 chat model + streaming adapter via LangChain4j-Jlama (4 days)
- T198 embedding adapter (2 days)

**Group 2 — UX completion (T199–T202, T213, T214, ~2.5 weeks)**.
Makes the foundation usable + preserves "drop a GGUF" UX:

- T199 `JlamaModelPicker` + scan endpoint (3 days)
- T200 `applyVendorDefaults` (1 day)
- T201 model metadata + architecture-support pre-flight (3 days)
- T202 RAM check on save (1 day)
- T213 recommended model gallery + HF import (3 days)
- T214 GGUF→Jlama converter UI + bundled `jlama-cli` (4 days)

**Group 3 — Operational hardening (T203–T206, ~1.5 weeks)**.
Production-grade observability + session lifecycle:

- T203 per-conversation session UUID lifecycle (2 days)
- T204 eager-load (1 day)
- T205 observability metrics (2 days)
- T206 status panel (3 days)

**Group 4 — Capability completion (T207–T209, ~1 week)**. Tool
calling + cost-cutting wins on the chat + retrieval pipeline:

- T207 tool calling via LangChain4j-Jlama bridge (3 days)
- T208 local LlmJudge with quality validation (2 days)
- T209 local embedding wired into `TurLuceneVectorStore` (2 days)

**Group 5 — Zero-egress + bold bets (T210–T212, T215, T217, T218,
~3 weeks parallel)**. Independent shipping items, mix of 📋 + 💭.

**Total Block G: ~8–10 weeks of focused work for committed (📋)
tasks; 8 long-shot (💭) tasks following on opportunity-cost basis.**
Faster than the earlier kherud draft (~10–12 weeks) because Jlama's
thread-safety + working tool-calling kills two whole capability
hardening tracks.

### XI.10 The narrative — one paragraph

> "Turing's GenAI architecture treats LLM providers as plugins —
> OpenAI, Anthropic, Ollama, Gemini, Azure are all just rows in
> `tur_llm_vendor`. **Embedded Jlama** adds a sixth, but with a
> qualitatively different posture: **no external service required**,
> not even a sidecar; no JNI native libs to extract; no Windows-CUDA
> missing; **pure Java**. The customer imports a SafeTensors model
> into asset storage (or uploads a `.gguf` and runs the bundled
> converter), picks it in the LLM Instance form, and a chat agent
> with **working tool calling** starts answering. That is the
> air-gapped-deployment story, the demo-without-API-keys story, the
> cost-arbitrage story, the regulated-industry story — packaged as a
> single dropdown choice in the LLM Instance form. The two
> structural investments (per-instance model cache, cross-backend
> local-path resolver) pay forward into Code Interpreter container
> bind-mounts, future asset-driven providers, and the kherud
> fallback path. The work is contained — three weeks of foundation,
> five weeks of capability hardening — but the strategic surface it
> unlocks is broad. The first Turing customer that wins a defense
> or hospital contract because we can ship zero-egress is the bet."

---

## XII. Admin chat ↔ `@viglet/turing-react-sdk` convergence

> The admin chat at [chat.page.tsx](../frontend/apps/turing-app/src/app/console/chat/chat.page.tsx)
> + `chat.service.ts`
> ships its own SSE plumbing, conversation-id minting, chip parsing,
> CSRF handling, and `resetFlow` calls — every one of which already
> exists in `@viglet/turing-react-sdk` and is consumed by the public
> SN search chat at
> [ai-mode-chat.tsx](../frontend/apps/turing-app/src/search/components/ai-mode-chat.tsx).
> The duplication exists because the admin chat predates the SDK's
> agent-mode support (`UseTuringChatAgent` shipped in 2026.2.16) and
> no one circled back to migrate. This section captures the
> migration plan: close the 6 SDK gaps that block the move, then
> rewrite the admin page to delegate protocol concerns to the SDK.

### XII.1 What the SDK is missing (Block H part 1 — T219–T225)

The SDK already covers everything the admin chat does **at the
protocol layer** except these six items. Each is a small, additive
change with no risk to existing SDK consumers (the marketplace apps
and the public SN chat widget).

#### Capability matrix — admin vs SDK today

| Concern | Admin chat today | SDK today | Gap closed by |
|---|---|---|---|
| Site RAG chat (anonymous) | n/a — admin doesn't use it | ✅ `postChatConversation` | — |
| Explicit agent chat | ✅ `sendAgentStream` | ✅ `postAgentChat` | — |
| Token-by-token streaming | ✅ manual SSE reader | ✅ `consumeAssistantStream` | — |
| Chip options (`type:"options"`) | ✅ `onOptions` callback | ✅ `onOptions` + `messages[i].options` | — (admin uses callback shape, see migration note) |
| `conversationId` minting | ✅ `useChatSession.ensureSessionId` | ✅ `TUR_SESSION` cookie | — |
| `resetFlow` server-side | ✅ `resetAgentFlowState` | ✅ `deleteAgentFlowState` + `resetFlow()` | — |
| CSRF cookie reading | ✅ `ensureCsrfToken` | ✅ `readXsrfCookie` | — |
| Slot read (`/chat-slots`) | ✅ inline poll | ✅ `useTuringSlots({ agent })` | — |
| Flow state read | n/a in admin chat | ✅ `useTuringFlowState` | — |
| Disabled-reason hint | ❌ missing | ✅ `disabledReason` + `CHAT_DISABLED_HINTS` | — (admin should adopt) |
| **LLM instance listing** | ✅ `queryLLMInstances` | ❌ | **T219** |
| **Context-info probe** | ✅ `fetchContextInfo` / `fetchAgentContextInfo` | ❌ | **T219** |
| **`useLlmInstances` hook (state + cache)** | ✅ | ❌ | **T220** |
| **Multipart file uploads** | ✅ on `/v2/llm/{id}/chat` only | ❌ | **T221** (+ backend verification for agent endpoint) |
| **Per-turn LLM switch on agent chat** | ✅ user picks LLM mid-conversation | ❌ pinned at hook init | **T222** |
| **Real `AbortController` for `stop()`** | ❌ also a `requestRef` no-op | ❌ same defect | **T223** |
| **Plain-LLM chat (no agent, no site)** | ✅ `sendStream` to `/v2/llm/{id}/chat` | ❌ | **T224** |
| **`__scheduleAgent_pending_*` banner** | ✅ `useScheduleAgentWaiting` | ❌ | **T225** |

### XII.2 What the admin migration looks like (Block H part 2 — T226–T232)

Once T219–T225 land in the SDK, the admin migration is mostly
**deletion**:

```
chat.page.tsx    571 → ~350 lines  (extract AgentChatTab, drop manual SSE state)
chat.service.ts  368 → 0    lines  (every method replaced by SDK equivalents)
use-llm-instances.ts        77 → 0    lines  (replaced by T220 useTuringLlmInstances)
use-schedule-agent-waiting  95 → 0    lines  (replaced by T225 useTuringScheduleAgentWaiting)
use-chat-session.ts        145 → ~145 lines  (stays — IndexedDB sessions are admin-only)
  AgentChatTab (new)         0 → ~150 lines  (per-tab SDK hook wrapper)
---
Net delta: ~ -430 lines for the chat module.
```

**The admin-only chrome stays in admin:**

- Multi-agent tab strip (the SDK targets one agent at a time per
  `useTuringChat` instance; admin renders N hooks, one per tab)
- IndexedDB session sidebar with cross-tab history and AI-generated
  titles — this is admin's `chat-session.service.ts` and is
  intentionally different from the SDK's `sessionStorage`
  per-tab persistence (public sites typically don't want a multi-tab
  session sidebar)
- Markdown + `rehype-highlight` rendering — host concern, not SDK
  (SDK is headless by design)
- `SandboxPlayer` for inline HTML blocks — admin/console feature
- Compact-context button + token estimator — admin-only feature
- Flow selector dropdown ("Auto-detect" sentinel + per-flow list) —
  admin/console-only authoring tool
- Forward-from-home-page handoff via `sessionStorage` — admin nav glue

### XII.3 Risk + sequencing

#### Risks worth calling out

1. **Per-turn LLM override semantics (T222)** — the SDK is
   opinionated that `agent.llmInstanceId` is pinned for the
   conversation. Letting it change mid-conversation introduces a
   "what counts as a new conversation?" question that the public SN
   chat doesn't have. Resolution: the SDK never auto-mutates;
   callers explicitly pass a per-`send` override and own the policy.
2. **Multipart on the agent endpoint (T221)** — `TurAgentChatExecutor`
   has historically accepted `application/json` only on the agent
   chat route. If files-on-agent isn't actually wired backend-side,
   T221 spawns a backend prereq (T221a) to add the multipart variant.
   Verify against the latest `TurAgentChatAPI` before estimating.
3. **`/semantic-chat` users (T230)** — `chat.service.ts.sendSemanticStream`
   isn't called from the admin chat page, but a grep is required
   before deletion. If a different admin page still uses it, the
   migration leaves a residual slim service or moves that endpoint
   into the SDK too.
4. **Tests as the contract** — without T232's smoke ITs, the
   rewrite is an article of faith. The three ITs are the cheapest
   insurance against admin/SDK protocol drift going forward.

#### Sequencing

1. **Week 1**: T219–T223 (SDK plumbing — independent, can run in
   parallel between 2 developers).
2. **Week 2**: T224 + T225 (depends on T219; small).
3. **Week 2 (parallel)**: T226 + T228 (1-line migrations once T220 +
   T225 land).
4. **Week 3**: T227 + T229 (per-tab hook wiring + title generator).
5. **Week 3**: T230 (delete the dead service after every other
   migration commit is green).
6. **Week 3-4**: T231 (the actual `chat.page.tsx` rewrite + extract
   `AgentChatTab`) + T232 (smoke ITs).

**Total Block H: ~10–14 days focused work.** Net result: one chat
protocol implementation in the codebase, one set of SDK consumers
(public chat + admin chat + future marketplace apps), and every
future protocol fix lands in exactly one place.

### XII.4 The narrative — one paragraph

> "The admin chat predates the SDK's agent-mode support; we built it
> when the only thing the SDK could talk to was the site RAG
> endpoint. Three years and two minor versions later, the SDK now
> covers every chat surface the admin uses — agent chat with
> streaming, chip options, slot polling, flow-state read, resetFlow,
> CSRF, conversation-id management — and the admin's parallel
> implementation has already drifted twice. Block H closes the 6
> protocol gaps in the SDK (LLM listing, context-info, multipart,
> per-turn LLM, real abort, plain-LLM chat, scheduleAgent banner) so
> that **one transport implementation** serves the public chat, the
> admin chat, and every future SDK consumer. Net code delta: -430
> lines from the admin chat module, one canonical place to fix the
> next protocol bug, and three smoke ITs that catch the drift before
> it ships."

---
