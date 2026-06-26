# Last task number — `T472`

> **Single source of truth for the next free task number.** The next new task is
> `T473`; after assigning it, bump the number above and the log line below.
>
> **Note (2026-06-16):** `T380` is an intentional **free gap** — Block R was
> created as `T381–T385` without bumping this counter (the exact hazard this file
> guards against), so the counter was reconciled to the real max when Block R's
> amplification (`T386–T393`) landed. Don't reuse `T380` unless you deliberately
> want to fill the gap.

## Why this file exists

T-numbers are **non-contiguous across blocks** — a block's header range (e.g.
"Block J follow-ups (T334–T373)") is *not* a reliable guide to what's free,
because a later block can consume numbers inside an earlier block's band. Real
example that caused a collision (2026-06-16): Block N (reranker) had already
shipped as **T337–T341**, sitting inside the Block J band, so "the next Block J
number" looked free but wasn't. Grepping three files (`ROADMAP.md`,
`CHANGELOG.md`, `IMPROVEMENTS.md`) is error-prone; this counter is authoritative.

## Rule

1. **Before creating a task**, read the number above and use `T<n+1>`.
2. **After creating it**, increment the number above and append a log line below.
3. Never infer the next number from a block header range or a `git log` scan.

## Log (most recent first)

- **T464–T472** — Block AA: Synthetic personas — audience-fit content evaluation + persona synthesis from audio. Reframes `TurPersona` from a speaker-only contract to also model an *audience/reader* (opt-in `personaKind` SPEAKER|AUDIENCE|BOTH + audience facet, default SPEAKER = legacy untouched). T464 audience-profile facet (readingLevel/education, domainExpertise, vocabularyCeiling, comprehension/accessibility, primaryLanguage) + Liquibase; T465 persona "notebook" source set (SN doc / uploaded asset / URL via `TurFileUtils`+OCR SSRF guard, cached extracted text); T466 pure multilingual readability/complexity scorer (Flesch/Fog adapted PT/ES/EN, sentence length, complex-word ratio vs vocabularyCeiling, passive heuristic — deterministic CI, no LLM, T385 pattern); T467 content-fit evaluator (deterministic T466 score ⊕ LLM persona-as-reader *grounded* judge → fit %, condiz/não-condiz, flagged spans + rewrite suggestions; reuses `TurLlmSummaryService` cache + LlmJudge structured-output + T385 grounding hard-fail); T468 fit report API + admin page (red/amber/green bars à la T388, regenerate); T469 💭 `TurTranscriptionProvider` seam (default OpenAI Whisper/provider-native, pluggable for air-gapped; wired to the AUDIO slot, unblocks F.5); T470 persona-from-audio authoring (upload→transcribe→LLM behavioral analysis→draft persona, never auto-saved, opens existing AI-authoring review form via T426 strict-schema); T471 💭 auto-suggest best persona variant for a piece of content (batch T467, feeds Block Z A/B persona selection); T472 💭 content-fit as an index-time SN signal (per-doc readability/fit field, surfaces "too complex for audience" in the content-gap dashboard). deps: existing Persona stack, T64, T385, T388, T252, T426, T69. → IMPROVEMENTS §XXVI — 2026-06-26
- **T458–T463** — Block Z: Client-side conversion analytics & the Google Analytics bridge. Zero-dep canonical analytics event bus in the vanilla SDK + pluggable sinks (GA4/GTM first adapter, not a dep) so customers measure lead conversion / abandonment / "which persona A/B variation was preferred" in their own GA4. T458 event bus + taxonomy (controllers emit via `analytics` option, no-op when absent); T459 GA4/GTM `googleAnalyticsSink` w/ `window.gtag`/`dataLayer` auto-detect (EDS zero-config) + generic `onEvent` + debug sinks; T460 client-side abandonment/idle/unload detection via sendBeacon w/ last-step; T461 funnel-step + conversion (goal slots / form / CAPTURED) events + expose `personaId` on `/chat/state` + stamp experiment/variant/persona on every event; T462 search/SN events (search/no-results/result-click/refined) + cross-surface `TUR_SESSION` stitching; T463 `useTuringAnalytics()` + Block Y showcase/EDS wiring + docs. deps: T404, T405, T69, T67–T75, T85, T252, T441, Block W/Y. → IMPROVEMENTS §XXV — 2026-06-26
- **T451–T457** — Block Y: Reference showcase app ("Atlas Store" commerce-catalog copilot at `frontend/apps/showcase`) that stresses the maximum platform surface — a living demo + SDK-vs-real-backend integration test + canonical sales/docs artifact. T451 scaffold+seed (manifest-provisioned typed catalog, import ZIP, `turing init/dev`, branded react-ui); T452 full search surface (facets/URL/autocomplete/spell/tabs/similar/CTR/HYBRID_RRF/reranker); T453 chat surface (streaming/citations/forms/slots-delta/multimodal/workspace/rich-content); T454 agent power (skill+code-interpreter+T436 tool activity+T438 client tools+T442 answer-as-app); T455 creative-bets wiring (T443/T444/T445/T448/T450); T456 voice+image+memory(T446)+ops tour(analytics/eval/self-tune/cost)+MCP-server exposure; T457 guided tour + e2e smoke + docs + coverage audit vs the §XXIV.1 matrix. deps: T382, T386, Block W, Block X, T147, T80, T323, Block I/K/L/N. → IMPROVEMENTS §XXIV — 2026-06-26
- **T442–T450** — Block X: Creative experiences at the search × agent intersection — thin compositions of shipped primitives that only pay off because Turing owns both halves (agent runtime + enterprise search). T442 answer-as-an-app (search-grounded generative UI off the typed manifest); T443 co-browse the real search UI via client tools; T444 conversational glass-box ranking; T445 ambient/proactive copilot off the slot bus; T446 cross-conversation personal memory; T447 self-tuning agent (mine failures→eval→suggested change, near-pure composition); T448 agent-to-agent visible handoff; T449 skills that ship their own UI; T450 embeddable action widget (Turing as an action layer over any site). All 💭 ideas. deps: Block W (T438–T440), T386, T383, T389, T63, T163, T285/T427, T286, T323, T147. → IMPROVEMENTS §XXIII — 2026-06-26
- **T436–T441** — Block W: Agent↔UI transparency & client tools (AG-UI parity). Closes the two gaps a CopilotKit/AG-UI review surfaced in the JS SDK: T436 live `tool_call` events on the chat SSE (wraps the server-side tool loop; opt-in `toolCallEventsEnabled`); T437 SDK/`useTuringChat` `toolCalls` + headless `TuringToolActivity`; T438 client-tool protocol (`client_tool_call` event parks the turn like T119 humanApproval + `POST /chat/client-tool-result` resumes; per-agent declared+validated; `clientToolsEnabled`); T439 SDK `registerClientTool`/`clientTools` (≈ `useCopilotAction`); T440 generative UI via client tools (host component registry, reuses TuringRichContent dispatch); T441 💭 canonical AG-UI protocol adapter (distribution bet, not core parity). Note: Slots already ≈ AG-UI state sync, forms T107/multimodal T401/Workspace T113 already at/over parity. deps: T427, T295, T119. → IMPROVEMENTS §XXII — 2026-06-26
- **T432–T435** — Block F.15: Capability surfacing & UX. T432 capability registry/taxonomy (`kind`/`function`/`provider`/`category`/`ownsTurn`, tags Turing native-tool groups too, `GET /api/capability/registry`); T433 per-agent capability selection (`agentSelected ∩ instanceCapable`) + union execution on the native path ("capacidade é mutex, resto coexiste" — provider server tools + non-conflicting Turing/MCP/custom tools in one native tool-loop; `ownsTurn` still owns the turn); T434 agent "Tools & Capabilities" picker (capability-first, radio per `function`, instance-vendor gating); T435 REQUEST_OPTION knobs as registry-driven settings. Motivated by: no UI for the T132 matrix today + native turn silently bypasses all Turing/MCP/custom tools. deps: T132, T136. → IMPROVEMENTS §X.18 — 2026-06-26
- **T431** — Per-LLM-instance file-upload capability flag (D.8 / T103 follow-up): `fileUploadEnabled` boolean on `TurLLMInstance` (Liquibase v2026.3.1.46) so the admin decides which providers may receive raw document binaries; the T103 binary passthrough now fires when the request opts in OR the agent's resolved LLM has the flag on. Admin + bento LLM forms gain the switch (en/pt). deps: T103. — 2026-06-26
- **T430** — `turing eval` CI gate (E.7 follow-up): reusable GitHub composite action running `turing eval` as a PR gate + `npx @viglet/turing-cli` quickstart docs. deps: T126. → IMPROVEMENTS §IX.8.d — 2026-06-26
- **T429** — Global chat-event tail for `turing logs` (E.7 follow-up): admin SSE firehose of recent chat events across conversations (bounded ring buffer) so `logs` works without a conversationId. deps: T125. → IMPROVEMENTS §IX.8.c — 2026-06-26
- **T428** — `assert.outcome` in `turing eval` (E.7 follow-up): expose terminal outcome (CAPTURED/ABANDONED/HANDOFF) the TurAgentEvalRunnerService already computes so fixtures can assert end state. deps: T126. → IMPROVEMENTS §IX.8.b — 2026-06-26
- **T427** — Per-conversation tool-call trace endpoint (E.7 follow-up): read-only GET /api/chat/sessions/{id}/tool-calls so `assert.tool_called` is exact (not inferred from slot-audit TOOL writes) + observability surface. deps: T126. → IMPROVEMENTS §IX.8.a — 2026-06-26
- **T426** — AI Authoring on provider tool-calling/strict schema (F.10): retire `BeanOutputConverter`-over-free-text in `TurAiAuthoringService` (it 500s on prose-around-JSON / empty turns from gpt-4o-mini in the Intent authoring chat) in favor of a provider tool / strict `json_schema` so the response is validated JSON by construction. Band-aid recovery (brace-scan extract + graceful fallback) shipped first. deps: T173, T174. → IMPROVEMENTS §X.11 — 2026-06-26
- **T425** — `mcp` binding for Custom Tools (E.9): Groovy scripts can call an attached MCP server's tools via `mcp.call("dspace_search_items", [...])`, reusing the agent's MCP integration instead of a parallel direct-REST path. Motivated by the Aula-Relâmpago tool hitting the Insper Akamai WAF with the JDK `Java/<v>` UA (403) while the MCP server's `python-httpx` UA is allowlisted. → IMPROVEMENTS §XXI — 2026-06-25
- **T424** — Trigger/namespace-aware tool prioritization (E.9): boost an attached MCP server's tools when its `llmInstructions` triggers match the message + reserve a pre-filter quota so always-keep native tools can't starve attached MCP/custom tools to zero. Surfaced by a real incident where `dspace_*` was dropped (24 natives ≥ top-K) and gpt-4o-mini looped on `get_current_time`. → IMPROVEMENTS §XXI — 2026-06-25
- **T411–T423** — Block U: documentation refresh for `docs-turing` (webhooks, routines, cost-governance new pages; chat-flow/chat-analytics rewrites; experiments + human-in-the-loop split-outs; agent-eval/mcp-server/tool-calling/ai-agents/config-reference/token-usage updates). Plan: `docs-turing/_documentation-update-plan.md` — 2026-06-25
- **T410** — Chaotic chatflow HITL coverage: `humanApproval` node type in `flow-dsl` + dedicated approval sub-flow in the chaotic fixture + park/decision-resume IT (also fixed a leaking test cache-manager that broke every full-context test) — 2026-06-18
- **T409** — Lucene DSL executor: type-aware numeric/currency range + sort (Block R) — 2026-06-17
- **T407** — End-to-end NL→facet eval integration test (Block R) — 2026-06-16
- **T406** — SDK parity guard (CI contract test) (Block S) — 2026-06-16
- **T405** — SDK platform discovery helpers (Block S) — 2026-06-16
- **T404** — SDK semantic-chat client (Block S) — 2026-06-16
- **T403** — SDK DSL search client (Block S) — 2026-06-16
- **T402** — SDK spell-check client (Block S) — 2026-06-16
- **T401** — SDK multimodal slot-upload + flow resume (Block S) — 2026-06-16
- **T400** — SDK public "similar documents" client (Block S) — 2026-06-16
- **T399** — Cross-product tenancy conformance test (Block Q / parity) — 2026-06-16
- **T398** — Dumont: audit + adopt the shared tenancy (Block Q / parity) — 2026-06-16
- **T397** — Shio: adopt the full shared tenancy (Block Q / parity) — 2026-06-16
- **T396** — Turing: align to the expanded tenancy kernel (Block Q / parity) — 2026-06-16
- **T408** — Admin wizard: generate SN fields from sample documents (frontend for T387) — 2026-06-17
- **T395** — Expand `viglet-core-tenancy` to the full shared feature set (Block Q / parity) — 2026-06-16
- **T394** — Audit + design the canonical shared tenancy surface (Block Q / parity) — 2026-06-16
- **T393** — Lift manifest + grounding contract into `viglet-core` (Block R) — 2026-06-16
- **T392** — Grounded conversational catalog copilot (Block R) — 2026-06-16 (shipped 2026-06-17)
- **T391** — Structured-source onboarding wizard (Block R) — 2026-06-16
- **T390** — Multi-source duplicate clustering (Block R) — 2026-06-16 (shipped 2026-06-17)
- **T389** — Pluggable ranking pipeline + ranking explainability (Block R) — 2026-06-16 (shipped 2026-06-17)
- **T388** — Per-field coverage / completeness observability (Block R) — 2026-06-16 (shipped 2026-06-17)
- **T387** — LLM-assisted manifest derivation from sample documents (Block R) — 2026-06-16 (shipped 2026-06-17)
- **T386** — Manifest as versioned schema-as-code (Block R) — 2026-06-16
- _(T380 left as a free gap — see note above; T381–T385 are Block R, created earlier without a log entry)_
- **T379** — `viglet-core` HTTP/RestClient builder (Block Q) — 2026-06-16
- **T378** — `viglet-core` outbound-webhook + HMAC signing (Block Q) — 2026-06-16
- **T377** — `viglet-core` messaging + persistent-logging module (Block Q) — 2026-06-16
- **T376** — `viglet-core` config-var / global-settings service (Block Q) — 2026-06-16
- **T375** — `viglet-core` commons module (Block Q) — 2026-06-16
- **T374** — `viglet-core` content-extraction module (Block Q) — 2026-06-16
- **T373** — Ship Cloud default config as a GLOBAL seed ZIP (Block J follow-ups) — 2026-06-16
- **T372** — Platform-global BYO-infra is read-only in the CRUD (Block J follow-ups) — 2026-06-16
- **T371** — `viglet-core` Phase 2: promote to a public shared library (Block Q) — 2026-06-16
- **T370** — `viglet-core` tenancy module (Block Q) — 2026-06-16
- **T369** — `viglet-core` security module (Block Q) — 2026-06-16
- **T368** — `viglet-core` crypto + storage modules (Block Q) — 2026-06-16
- **T367** — `viglet-core` Turing submodule skeleton (Block Q) — 2026-06-16
