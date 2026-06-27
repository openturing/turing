# Turing — Roadmap (active backlog)

> **Single source of truth for task status.** Flat, one line per task.
> Only **unshipped** work lives here (📋 designed · 💭 idea · ⏳ partial · 🛠 in-progress).
> Shipped work is moved to [CHANGELOG.md](CHANGELOG.md); design rationale (the
> *what/why* per task) lives in [IMPROVEMENTS.md](IMPROVEMENTS.md); strategy/positioning
> lives in [STRATEGY.md](STRATEGY.md).
>
> **How to pick work:** lowest-numbered task in a block whose `deps` are all shipped.
> The `→` pointer is the section in IMPROVEMENTS.md with the full design.

| Symbol | Meaning |
|---|---|
| 📋 | Designed but not started |
| 💭 | Idea worth exploring; needs design |
| ⏳ | Partial — direction is right, more work remains |
| 🛠 | In progress |

## D.12 — Headless React UI library (`@viglet/turing-react-ui`)

> New package for **design-agnostic** React chat/rich-content components shared
> by the admin console and viglet.com **without** viglet.com depending on
> `@viglet/turing-react-sdk` (axios). Skin via `classNames`/`labels`/`icons`;
> pure logic stays in `@viglet/turing-sdk`. Foundation (`TuringHtmlSandbox` +
> the SDK helpers) shipped — see [CHANGELOG.md](CHANGELOG.md). Rationale → §XII.2.

## E.2 — Agent Workspace (the key differentiator)


## E.3 — Skills (Anthropic-standard, portable) (differentiator)

> **Direction (locked 2026-06-10).** A Skill is an **Anthropic-compatible folder**
> (`SKILL.md` + `references/` + `scripts/` + `assets/`), stored in object storage,
> edited like a mini-VS-Code, and **executed in a Docker sandbox compatible with
> Anthropic's skill container** — Turing does **not** port skills into its own
> entities. Goal: the *same* skill runs in **chat** and **semantic navigation**, and
> is ZIP import/export-able (one ZIP may carry many skills, each independently
> activatable). **Docker is a hard requirement** for the feature. This **supersedes**
> the shipped T116 decomposed-manifest model (→ T317) and **retires** the old
> T310–T315 attach-to-agent design. Reference bundle: `turing-marketplace/anthropic-skills/`.
> Rationale → §IX.4.d.

> **Block complete (2026-06-10).** All skill tasks (T316–T326) have shipped;
> there is no remaining skills backlog. A hosted public registry was considered
> and **dropped** — it is not a product direction.

## E.4 — Human-in-the-loop (parity, made graph-visible)

> **Block complete.** Both HITL tasks have shipped — the async, graph-declared
> `humanApproval` node (T119) and the live, in-the-loop Spectator + Co-pilot mode
> (T120). See [CHANGELOG.md](CHANGELOG.md) §E.4.

## E.6 — Context engineering enforcement

> **Block complete.** Both tasks have shipped — enforceable per-turn token
> budgets with a working `COMPACT` mode (T123) and the workspace / memory /
> tool-offload context-engineering metrics (T124). See [CHANGELOG.md](CHANGELOG.md) §E.6.

## E.7 — Developer experience (CLI + eval)

> **Block complete.** All three tasks have shipped — the `turing` CLI
> (`init`/`dev`/`deploy`/`logs`, T125), the YAML eval regression suite
> (`turing eval`, T126), and replay-as-test (`turing eval record`, T127) — as
> the zero-dep `@viglet/turing-cli` package. See [CHANGELOG.md](CHANGELOG.md) §E.7.

## E.8 — Observability interop

> **Block complete.** Both tasks have shipped — the opt-in LangSmith run mirror
> (T128) and chat-pipeline OpenTelemetry span export (T129) — as the fail-open
> `turing.observability.*` exports. See [CHANGELOG.md](CHANGELOG.md) §E.8.

## E.9 — Tool-selection quality (MCP / native disambiguation)

> **Block complete.** Both tasks have shipped — trigger/namespace-aware tool
> prioritization with an anti-starvation quota floor (T424) and the `mcp` binding
> for Custom Tools (T425). See [CHANGELOG.md](CHANGELOG.md) §E.9.

## F.2 — OpenAI Responses API + built-in tools

> **Block complete.** All built-ins shipped — `web_search` (T133),
> `file_search` (T134), `code_interpreter` (T135), `image_generation` (T137),
> remote `mcp` (T138) as one-shot Responses tools, and `computer_use` (T136) as
> the multi-turn screenshot/action loop over a pluggable `TurComputerUseDriver`.
> See [CHANGELOG.md](CHANGELOG.md) §X.3.

## F.3 — Anthropic server-side tools

> **Block complete.** All five shipped — one-shot server tools `web_search`
> (T139), `web_fetch` (T140), `code_execution` (T141) via `TurAnthropicMessagesService`,
> plus the multi-turn loops `computer_use` (T143, reusing the `TurComputerUseDriver`
> seam) and `bash` + `text_editor` (T142, against `TurAgentWorkspace`).
> See [CHANGELOG.md](CHANGELOG.md) §X.4.

## F.4 — MCP Connector (both vendors)

> **Block complete.** All three tasks have shipped — the Anthropic MCP
> Connector (T144), the cross-vendor MCP federation directory (T145), and the
> public API-key-gated Turing-as-MCP-Server consumption surface (T146). See
> [CHANGELOG.md](CHANGELOG.md) §X.5.

## F.5 — Voice agents (Realtime API)

> **Block complete.** All five tasks have shipped — the `TurRealtimeVoiceProvider`
> seam + OpenAI ephemeral session minting (T147), the `/voice/session` +
> `/voice/available` endpoints and the full-duplex `useTuringRealtimeVoice` SDK
> hook (T148), the full-screen voice kiosk (T149), operator spectator
> translation (T150), and voice authoring of chat-flows (T151). See
> [CHANGELOG.md](CHANGELOG.md) §X.6.

## F.7 — Batch + token economics

- 📋 **T158** (deps: T156) **Bulk re-embedding via Batch** — when an admin switches the embedding model on a site (`text-embedding-3-small` → `text-embedding-3-large`), Turing now schedules the re-embedding as a Batch job (50% off, 24h SLA) instead of a synchronous r… → §X.8.c
- 📋 **T159** (deps: T156) **LLM Judge eval at batch tier** — the LLM Judge bilingual prompt over a regression set (T126 eval suite, when present) runs as Batch by default — eval CI pipelines finish overnight at half cost. → §X.8.d
- 📋 **T160** (deps: T131) **Service tier routing** — per-stage `service_tier` selection (`flex` / `priority` / `default` / `auto`). → §X.8.e
- 📋 **T161** (deps: T131) **Anthropic token counting (`/v1/messages/count_tokens`)** — pre-flight token estimator. → §X.8.f
- 💭 **T162** (deps: T161) **Pre-flight cost quote in chat UI** — when token-counting estimate × model price exceeds `softCostBudgetPerTurnUsd`, the SDK shows a "this turn will cost $X.XX, confirm?" gate. → §X.8.g

## F.8 — Memory tool + context editing

- 📋 **T163** (deps: T131, T111) **Anthropic Memory tool (`memory_20250818`)** — Claude gets six file commands (`view`/`create`/`str_replace`/`insert`/`delete`/`rename`); the implementation is backed by `TurAgentWorkspace` at path `memory/` (per-conversation by default; op… → §X.9.a
- 📋 **T164** (deps: T131) **Context editing (`clear_tool_uses_20250919`)** — Claude server-side auto-evicts old tool results when the window saturates, replacing them with a placeholder. → §X.9.b
- 📋 **T165** (deps: T131) **Anthropic Compaction** (beta) — server-side summarization of older turns when the window fills. → §X.9.c
- 💭 **T166** (deps: T163) **Per-user persistent persona memory** — `memoryScope=user` + the Memory tool gives Claude a per-Keycloak-`sub` file area where it stores stable user preferences ("Alex prefers terse answers", "Alex's preferred locale is pt-BR"). → §X.9.d

## F.9 — Stored Completions + Evals + Distillation flywheel

- 📋 **T167** (deps: T131) **`TurStoredCompletionsService`** — when an agent has `storedCompletions=true`, every Responses API call sets `store: true` + structured `metadata` (`agentId`, `conversationId`, `flowId`, `nodeId`, `experimentKey`, `variantLabel`). → §X.10.a
- 📋 **T168** (deps: T167) **OpenAI Evals API integration** — Turing's eval suite (T126) optionally pushes fixtures to OpenAI Evals + runs server-side graders. → §X.10.b
- 💭 **T169** (deps: T167, T168) **Distillation pipeline** — admin clicks "Distill last 30 days" on an agent → Turing exports Stored Completions for that agent → submits as a fine-tuning job against `gpt-4o-mini` → swaps the `TurLLMInstance.modelId` after the eval passes. → §X.10.c
- 💭 **T170** (deps: T169) **DPO loop from operator feedback** — operators thumb-up / thumb-down assistant turns; the DPO dataset is built incrementally from the binary preferences. → §X.10.d

## F.10 — Predicted Outputs + strict structured outputs

- 📋 **T171** (deps: T131) **OpenAI Predicted Outputs (`prediction`)** — 3-5× latency drop when the output mostly overlaps an input string. → §X.11.a
- 📋 **T172** (deps: T171) **Predicted Outputs in T19/T24 RAG answer re-rank** — when the LLM answers an SN question and the user clicks "rephrase shorter", the previous answer is the `prediction`. → §X.11.b
- 📋 **T173** (deps: T131) **OpenAI Strict Structured Outputs (`response_format: json_schema, strict: true`)** — replaces the gpt-4o-mini JSON-truncation workarounds documented in `feedback_gpt4o_mini_json.md`. → §X.11.c
- 📋 **T174** (deps: T131) **Anthropic structured outputs (ZDR-cached schemas)** — analog to T173 on Claude. → §X.11.d
- 📋 **T426** (deps: T173, T174) **AI Authoring on provider tool-calling/strict schema (retire `BeanOutputConverter`)** — `TurAiAuthoringService` parses free text into `AiAuthoringResponse<T>` via `BeanOutputConverter`, which 500s when the model emits prose-around-JSON or an empty turn (observed on gpt-4o-mini for the Intent authoring chat). Recovery (brace-scan extract + graceful fallback) was added as a band-aid; the durable fix is to bind the response shape as a provider tool / strict `json_schema` so the model returns validated JSON by construction. → §X.11.c

## F.11 — Files API + native PDF

- 📋 **T175** (deps: T131) **`TurFilesApiBridge`** — on the indexer write path, when a file is queued for an OpenAI- or Anthropic-backed agent, the binary is also uploaded to the vendor's Files API and the returned `file_id` cached in `TurStorageObjectStat` metadata.… → §X.12.a
- 📋 **T176** (deps: T175) **Crawler / Connector integration** — Tika-extracted text **stops being the only input**: the vendor `file_id` is passed alongside as a citation-bearing PDF/DOCX. → §X.12.b
- 💭 **T177** (deps: T175) **Vendor-side chunk reuse across agents in the same tenant** — when N agents on the same tenant cite the same PDF (e.g. → §X.12.c

## F.12 — Reasoning summary + observability hardening

- 📋 **T178** (deps: T131) **Reasoning summary surfacing** — for o1 / o3 / o4-mini / GPT-5 reasoning models, capture `reasoning.summary` from the Responses API and render as a collapsible "Why this answer" panel in the chat UI. → §X.13.a
- 💭 **T179** (deps: T131) **Reasoning effort budget** — `reasoning.effort` (`minimal` / `low` / `medium` / `high`) plumbed per-stage. → §X.13.b
- 📋 **T180** (deps: T131) **Logprobs in T19/T24/T28 reranker as confidence signal** — capture `logprobs` + `top_logprobs` on Responses calls; reranker / classifier mixes the LLM token-level confidence into the existing BM25 / vector scores. → §X.13.c

## F.13 — Safety, compliance, multi-tenant cost

- 📋 **T181** (deps: T131) **`safety_identifier` propagation** — hashed Keycloak `sub` (`SHA-256(sub + tenant-salt)`) sent on every OpenAI Responses call as `safety_identifier`. → §X.14.a
- 📋 **T182** (deps: T131) **OpenAI `omni-moderation-latest` pre-filter** — every user query (and every crawler-discovered document chunk over a configurable size) passes through `omni-moderation` before reaching the LLM / index. → §X.14.b
- 📋 **T183** (deps: T131) **Anthropic Admin + Usage API integration** — `TurAnthropicUsageImporter` runs nightly, pulls per-workspace / per-model / per-service-tier token consumption + USD cost from the Anthropic Admin/Usage API, persists to `tur_ai_usage_daily` (Li… → §X.14.c
- 📋 **T184** (deps: T183) **Per-tenant AI Spend widget** — System Info gains an "AI Spend" card showing 30-day spend per LLM / per agent / per stage (chat.live vs chat.background) with budget alert thresholds. → §X.14.d
- 💭 **T185** (deps: T183, T184) **Soft budget caps per agent** — agent setting `monthlyBudgetUsd`; when projected spend (based on rolling 7-day average) would breach the cap, the engine downgrades to a cheaper tier (e.g. → §X.14.e
- 💭 **T186** (deps: T132, T432) **Capability matrix UI heatmap** — admin dashboard heatmap of (LLM instance × capability) showing which natives are wired and which are pending; renders from the T432 registry grouped by `provider` × `category`. → §X.14.f

## F.14 — Bold creative bets (the long shots)

- 💭 **T187** (deps: T133, T139, T141) **"Live answers" hybrid mode** — single chat answer fuses (a) SN index hits, (b) web_search citations, (c) live data via web_fetch, (d) computed analytics via code_execution — composed in one Responses turn. → §X.15.a
- 💭 **T188** (deps: T146, T138, T144) **Turing as the "headless" enterprise-search MCP backbone** — abandon the chat UI as the only consumption surface. → §X.15.b
- 💭 **T189** (deps: T147, T150, T55) **Real-time multilingual call center augmentation** — operator's headset audio goes through `gpt-realtime-translate` (T150), agent's screen shows live cited answers (T154) sourced from the customer's index, escalation paths via T55 handoff.… → §X.15.c
- 💭 **T190** (deps: T136, T142, T78) **"Self-installing" enterprise connectors via computer_use** — customer says "connect my company's Notion" → Turing's computer-use agent navigates to Notion → guides them through API token generation → tests the connection → enables the con… → §X.15.d
- 💭 **T191** (deps: T169, T126) **Agent that improves itself overnight** — combine T169 distillation + T126 eval suite + T127 production-replay-as-test: every night, the agent's prod traffic feeds a distillation candidate, the candidate runs against the eval suite, and if… → §X.15.e
- 💭 **T192** (deps: T138, T144, T146) **Inter-tenant MCP federation** — opt-in directory at `directory.turing.io` where tenants can publish a redacted MCP server exposing **only public knowledge** (their docs, their public catalog). → §X.15.f

## F.15 — Capability surfacing & UX (the layer that makes the whole §X usable)

> **Block complete (2026-06-26).** All four tasks shipped — the unified
> capability registry/taxonomy (T432), the per-agent selection + union execution
> "capacidade é mutex, resto coexiste" (T433), the capability-first agent picker
> (T434), and the registry-driven REQUEST_OPTION settings (T435). §X tasks now
> slot into a consistent home; T186 (heatmap) reads the same registry. See
> [CHANGELOG.md](CHANGELOG.md) §X.18.

## Block W — Agent↔UI transparency & client tools (AG-UI parity)

> **Why now:** a capability review against CopilotKit's **AG-UI** protocol found
> Turing's JS SDK at near-parity (token streaming, bi-directional **Slots** ≈
> state sync, native forms T107, multimodal T401, Workspace T113), with **two**
> genuine gaps: (1) the agent's tool calls are never put on the live SSE — they
> run server-side and the client only sees final text; (2) there is no
> frontend-defined ("client") tool the agent can invoke. This block closes both
> for Turing's *own* agent runtime (it is **not** a multi-runtime protocol
> adapter — that stays a 💭 bet, T441). Rationale → §XXII.

- 📋 **T436** (deps: T427) **Live tool-call events on the chat SSE** — add a `tool_call` event type to `TurChatStreamEvent` (`{callId, name, phase: start|result|end, argsSummary?, status?, durationMs?}`), emitted by wrapping the server-side `TurToolExecutionLoop` / `TurNativeChatExecutor` per-tool invocation; args summarized/redacted (never raw secrets). Opt-in per-agent (`toolCallEventsEnabled`, default off) so existing streams stay byte-identical; reuses T427's trace shaping so live event and read-only trace agree. → §XXII.1
- 📋 **T437** (deps: T436) **SDK + headless UI consume tool-call events** — vanilla chat controller + `useTuringChat` surface `toolCalls` on the in-flight assistant message; new headless `TuringToolActivity` atom in `@viglet/turing-react-ui` (same skinning contract as `TuringThinkingDots`); admin chat + viglet.com skin it. → §XXII.2
- 📋 **T438** (deps: T436) **Client-tool protocol (backend round-trip)** — agent emits a `client_tool_call` SSE event and the turn **parks** awaiting a result (same suspend/resume as the T119 `humanApproval` node, keyed by `(conversationId, callId)`); new `POST /chat/client-tool-result` resumes the loop. Allowed client-tool names + JSON schemas declared per-agent + validated; timeout/abort so an unanswered call can't wedge the conversation. Gated `clientToolsEnabled`. → §XXII.3
- 📋 **T439** (deps: T438) **SDK client-tool registration** — `chatController.registerClientTool(name, handler)` + a `clientTools` option on `useTuringChat` (`{[name]: {schema, handler}}`); on `client_tool_call` the SDK runs the handler, POSTs the result, and the stream continues. Mirrors `useCopilotAction` ergonomics (thin CopilotKit migration shim); handler errors flow back to the agent as a tool error, not into the UI. → §XXII.4
- 📋 **T440** (deps: T439, T295) **Generative UI via client tools** — a client tool whose handler renders a host-registered React component (looked up by name) with agent-provided props, optionally returning a value when the user interacts; reuses the `TuringRichContent` segment-dispatch model so generative + markdown/html/d2 segments share one render path. No backend beyond T438. → §XXII.5
- 💭 **T441** (deps: T436, T438) **Canonical AG-UI protocol adapter** — optional endpoint that re-encodes Turing's stream into the canonical AG-UI event schema (`RUN_STARTED`, `TEXT_MESSAGE_*`, `TOOL_CALL_*`, `STATE_SNAPSHOT`/`STATE_DELTA`, …) so CopilotKit's off-the-shelf React/Angular clients connect with zero Turing-SDK code. Distribution play, not core parity — promote from 💭 only if a named integration asks. → §XXII.6

## Block X — Creative experiences at the search × agent intersection

> **Why now:** with Block W (live tool calls + client tools + generative UI),
> Turing owns a combination neither side of the market has together — agent
> frameworks have no enterprise search engine, search engines have no agent
> runtime with generative UI. Every task here is a thin composition of shipped
> primitives that *only pays off because Turing owns both halves*. Rationale → §XXIII.

- 💭 **T442** (deps: T440, T386) **Answer-as-an-app (search-grounded generative UI)** — the agent assembles a live mini-app (comparison table / configurator / spec card) from the typed indexed result set instead of returning prose; the manifest tells it `price` is currency → renders a slider, etc. A generative-UI client tool whose props come from a real SN search. → §XXIII.1
- 💭 **T443** (deps: T438, T439) **Co-browse the search UI** — client tools let the agent mutate the host page's *real* facets + URL (`useTuringUrlSearch`); "filter to red ones under R$50" flips the actual checkboxes. Chat becomes a remote control for the page, not a parallel box. → §XXIII.2
- 💭 **T444** (deps: T389, T383) **Conversational glass-box ranking** — "why is this result #1?" → the agent narrates the real signals (BM25 vs vector in HYBRID_RRF, reranker score, field boosts). Trust feature for regulated procurement. → §XXIII.3
- 💭 **T445** (deps: T63) **Ambient / proactive copilot** — user interactions (clicks, dwell, opened facets) become slots; the agent watches the slot bus and proactively offers help, throttled + opt-in. → §XXIII.4
- 💭 **T446** (deps: T163) **Cross-conversation personal memory** — per-user long-horizon memory (preferences, role) surviving across sessions, tenant-scoped, with a user-visible "what the assistant remembers" panel + delete (GDPR posture as a feature). → §XXIII.5
- 💭 **T447** (deps: T285, T427) **Self-tuning agent (eval as continuous improvement)** — a routine mines real conversations for failure signatures, drafts a prompt/flow change, scores it against the Block K golden set, and opens a human-approved "suggested change" in the admin only if it improves. Highest leverage-to-cost — almost pure composition. → §XXIII.6
- 💭 **T448** (deps: T436, T286) **Agent-to-agent handoff (a visible team)** — a router agent delegates to specialists (own skills/tools); the UI shows the conversation visibly passing between them (Turing analog of AG-UI subgraphs), using the T436 tool-call events as the visibility surface. → §XXIII.7
- 💭 **T449** (deps: T440, T323) **Skills that ship their own UI** — an Anthropic-standard skill folder embeds its own React components (a `ui/` convention + activation-harness registration); skills graduate from prompt+scripts to full vertical apps. → §XXIII.8
- 💭 **T450** (deps: T438, T439) **Embeddable action widget (Turing as an action layer)** — the vanilla SDK widget acts on the host site (add-to-cart, fill form, navigate) via client tools; Turing stops being "a search box you embed" and becomes an action layer over any website, no browser extension. → §XXIII.9

## Block Y — Reference showcase app (stress every feature)

> **Why now:** one installable reference app that exercises the **maximum surface
> of the platform** — a living demo, an SDK-vs-real-backend integration test, and
> the canonical "this is what Turing can do" artifact for sales/docs. Domain: a
> commerce-catalog copilot ("Atlas Store") — the most feature-dense domain, where
> a typed catalog + compare + add-to-cart + "why #1?" + returns flow naturally
> pull in nearly every primitive. Lives at `frontend/apps/showcase`, seeds its
> own data. **Coverage matrix + per-task feature lists → §XXIV.**

- 💭 **T451** (deps: T382, T386) **Scaffold + seed** — new `frontend/apps/showcase` Turborepo app; ~200-product typed catalog with images; SN site provisioned from a manifest (optionally demo derivation T387); import ZIP + `turing init`/`dev` clone-to-running path; branded skin of `@viglet/turing-react-ui` (T305). → §XXIV.2
- 💭 **T452** (deps: T451) **Search surface** — faceted + URL-synced search, autocomplete, spell-check, tabs (T247), sort, similar docs (T400), click tracking, HYBRID_RRF (T383) on + selectable reranker (Block N). The search half lit end to end. → §XXIV.2
- 💭 **T453** (deps: T451) **Chat surface** — RAG chat exercising streaming + sources/citations (T152/T292) + `TuringSourceChips` + option chips + native forms (T107) + slots read/write/delta (T63) + multimodal upload (T401) + workspace panel (T113) + rich content md/`html`/`d2` (T295–T298). → §XXIV.2
- 💭 **T454** (deps: T453, T436, T438, T442, T323, T80) **Agent power** — a bundled returns/RMA skill, code interpreter ("compute cart total / chart price history"), live tool-call activity (T436), client tools (T438/T439), and the answer-as-app comparison widget (T442). Block W + skills surface lit. → §XXIV.2
- 💭 **T455** (deps: T454, T443, T444, T445, T448, T450) **Creative-bets wiring** — co-browse filters (T443), glass-box "why #1" (T444), ambient proactive copilot (T445), agent→agent returns↔sales handoff (T448), and an embeddable action widget (T450) on a mock host page. → §XXIV.2
- 💭 **T456** (deps: T454, T446, T147) **Voice, memory & ops tour** — voice agent (T147) + search-by-image; cross-conversation memory panel (T446); admin ops route surfacing analytics (T87/T88/T90), eval + self-tuning (T447), cost governance (Block L); catalog exposed over the MCP server (Block I) for the "drive it from Claude Desktop" demo. → §XXIV.2
- 💭 **T457** (deps: T455, T456) **Guided tour, tests & docs** — in-app walkthrough narrating each capability; e2e smoke test (SDK-contract IT pattern: boot backend, import showcase ZIP, drive search/chat/SSE/client-tool round-trip); `docs-turing` page + README link. Also the **coverage audit** reconciling the app against the §XXIV.1 matrix. → §XXIV.2

## Block Z — Client-side conversion analytics & the Google Analytics bridge (T458–T463)

> **Why:** the platform measures conversion/abandonment/A/B **server-side**
> (`TurChatSessionEvent` outcome + experimentKey/variantLabel/personaId, T67–T75,
> T85/T86). But customers run **GA4 in the browser**, where they already own
> acquisition, media and audiences. Lead conversion in chat & semantic navigation,
> drop-off/abandonment, and *which persona A/B variation was preferred* must be
> emitted **client-side at the right lifecycle moments** (the server can't see a
> tab close). Architecture: a zero-dep **canonical analytics event bus** in the
> vanilla SDK + **pluggable sinks** (GA4 is the first adapter, not a hard dep) —
> same shape as the AG-UI adapter bet (T441). Keeps the SDK zero-dep / EDS-friendly.
> → §XXV

- 💭 **T458** (deps: T404) **SDK analytics event bus** — zero-dep `createTuringAnalytics()` emitter + canonical event taxonomy (`turing_chat_start`/`_message_sent`/`_step`/`_lead_captured`/`_abandoned`/`_handoff`, `turing_ab_variant_assigned`, `turing_search`/`_no_results`/`_result_click`); chat/search/slots controllers feed it via an injected `analytics` option. No-op when absent → existing embeds unchanged. → §XXV.1
- 💭 **T459** (deps: T458) **Pluggable sinks + GA4/GTM adapter** — `googleAnalyticsSink()` mapping canonical events → GA4 `gtag('event', …)` **and** GTM `dataLayer.push`, auto-detecting an existing `window.gtag`/`window.dataLayer` (EDS sites light up with zero config); plus a generic `onEvent` sink (Segment/Matomo/Plausible later) and a `debug` console sink. → §XXV.2
- 💭 **T460** (deps: T458) **Client-side abandonment & engagement detection** — `visibilitychange`/`beforeunload`/idle-timer + `navigator.sendBeacon`: a started conversation (≥1 user msg) that ends without a conversion emits `turing_chat_abandoned` carrying the **last node/step reached** and turn count — the browser truth the server only infers via TTL. → §XXV.3
- 💭 **T461** (deps: T458, T69) **Conversion + funnel-step events & A/B attribution** — `turing_chat_step` per flow node reached (client mirror of the T85 funnel); `turing_chat_lead_captured` from configurable **goal slots** + form submit (T107) + server `outcome=CAPTURED`; **expose `personaId`** (and active persona name) on `GET /chat/state` and **stamp `experiment_key`/`variant_label`/`persona_id` on every event** → "which persona variation converted" answered natively in GA4. → §XXV.4
- 💭 **T462** (deps: T458, T405) **Search / semantic-navigation conversion events** — `turing_search`, `turing_search_no_results` (= client-side content-gap signal, feeds T252), `turing_search_result_click` (mirrors the existing `postClick` CTR in one call), `turing_search_refined`; **cross-surface session stitching** via the `TUR_SESSION` cookie so a chat lead attributes back to the originating search query. → §XXV.5
- 💭 **T463** (deps: T459, T461, T462) **React hook + showcase/EDS wiring + docs** — `useTuringAnalytics()` in the react-sdk + react-ui passthrough; wire the GA4 search→chat→lead funnel into the Block Y showcase (T455/T456) and an EDS example block (auto-detecting the host `dataLayer`); `docs-turing` page on the event taxonomy + GA4 funnel/audience recipes. → §XXV.6

## Block AA — Synthetic personas: audience-fit content evaluation & persona synthesis from audio (T464–T472)

> **Why:** today `TurPersona` is a **speaker** contract — it controls how the
> assistant *talks* (tone/verbosity/languageStyle/mandatory+forbidden terms). It
> models nothing about the *reader*. This block adds the missing half: a persona
> as an **audience proxy**, so the platform can answer *"is this indexed page / PDF /
> URL appropriate for this audience?"* (NotebookLM-style condiz/não-condiz report —
> e.g. a low-education persona rejects dense, jargon-heavy text), and can **synthesize
> a new persona from a real recorded conversation** (audio → transcript → behavioral
> traits). Pure composition of shipped primitives: content extraction (`TurFileUtils`/
> `VigletContentExtractor`), cached LLM reports (`TurLlmSummaryService`), the LlmJudge
> structured-verdict path, and the T385 *deterministic-scorer + LLM-seam* shape. The
> whole block is **opt-in** behind a `personaKind` discriminator (default `SPEAKER` =
> existing voice personas byte-for-byte unchanged). Rationale → §XXVI.

- 📋 **T464** (deps: existing Persona stack) **Audience-profile facet on `TurPersona`** — extend, don't fork: opt-in `personaKind` discriminator (SPEAKER \| AUDIENCE \| BOTH, default SPEAKER) + a cohesive `TurPersonaAudience` `@Embeddable` (`readingLevel`/education, `domainExpertise`, `vocabularyCeiling`, comprehension + accessibility notes, `primaryLanguage`) — not loose columns. Guard so AUDIENCE-only never resolves as an agent's voice. Additive Liquibase; legacy voice personas untouched; admin "Audience" section gated on kind. → §XXVI.1
- 📋 **T465** (deps: T464) **Persona "notebook" source set** — attach evaluation sources to a persona: an indexed SN document (by id/query), an uploaded asset (PDF/DOC via `TurFileUtils`), or a URL (OCR url path with the existing SSRF guard). New `TurPersonaSource` (type SN_DOC\|ASSET\|URL, ref, cached extracted text) + CRUD + admin "Sources" tab. → §XXVI.2
- 📋 **T466** (deps: T464) **Pure multilingual readability/complexity scorer** — deterministic `TurReadabilityScorer` (Flesch Reading Ease / FK-grade adapted PT/ES/EN, avg sentence length, complex-word ratio against `vocabularyCeiling`, passive-voice heuristic) → numeric fit sub-score with **no LLM**, so CI tests are deterministic (the T385 pure-scorer pattern). → §XXVI.3
- 📋 **T467** (deps: T465, T466, T385) **Content-fit evaluator** — the LLM **role-plays the persona as a reader** and, *grounded in the source text* (grounding hard-fail à la T385), returns a structured verdict: overall fit %, what condiz / what não condiz, flagged spans (too-complex term, jargon, tone/context mismatch) each with a suggested rewrite. Fuses the deterministic T466 score (hard signal) with the LLM judgment; cached via `TurLlmSummaryService`, structured output via the LlmJudge path. → §XXVI.4
- 📋 **T468** (deps: T467, T388) **Persona-fit report API + admin page** — `POST /api/persona/{id}/content-fit` (one source or the whole notebook) → aggregated report; admin page with per-source red/amber/green fit bars (à la T388 field coverage), flagged spans, rewrite suggestions, and regenerate support. → §XXVI.5
- 💭 **T469** (deps: T64) **Transcription seam (`TurTranscriptionProvider`)** — a pluggable speech-to-text interface (default: OpenAI Whisper / provider-native via an existing LLM instance; air-gapped installs plug a local STT) wired to the existing `AUDIO` slot upload path. Provider choice open; also partially unblocks F.5 voice agents. → §XXVI.6
- 📋 **T470** (deps: T469, T426) **Persona-from-audio authoring** — `POST /api/persona/derive-from-audio`: upload audio → transcribe (T469) → LLM behavioral analysis extracts voice traits (tone/verbosity/languageStyle/vocabulary) **and** audience descriptors → **draft** persona that is never auto-saved; it opens the existing AI-authoring review form (T426 strict-schema path) for human approval. → §XXVI.7
- 💭 **T471** (deps: T467, T69) **Auto-suggest the best persona variant for a piece of content** — given content + a set of audience personas, batch the T467 evaluator and rank which persona it best fits; feeds Block Z A/B persona selection and editorial routing. → §XXVI.8
- 💭 **T472** (deps: T467, T388) **Content-fit as an index-time SN signal** — run the evaluator at index time against a configured target-audience persona and store a per-document readability/fit score as an SN field; surface "content too complex for its audience" in the content-gap dashboard (cousin of T252/T388). → §XXVI.9

## Block G — Embedded LLM (Jlama) — DOWNGRADED TO SPIKE (2026-06-03)

> **Decision:** the 26-task committed Jlama program (was T193–T218) is cut to a
> single time-boxed spike. Rationale: Jlama upstream is stagnant (last release
> 2025-01-01, single maintainer), and **Ollama-as-sidecar already delivers the
> air-gapped + tool-calling story** in the same docker-compose with far less risk.
> The committed 8-week build was the roadmap violating its own "courage to
> deprecate" principle. Detailed Jlama design is in `git log` (former §XI).

- 💭 **T193 (spike)** (deps: —) **Embedded inference engine evaluation** — 2–3 day spike: benchmark Jlama (pure-Java, Panama) vs Ollama-sidecar vs `de.kherud:llama` for the zero-egress deployment story (tool calling, RAM, tok/s, Windows, ops). Decide ONE engine before committing any build. Default assumption: **Ollama-sidecar**. Promote to a real block only if the spike beats that default for a named customer requirement. → STRATEGY §VIII (deprecations)

## Block I — Turing as an MCP Server (T245–T256)

- 💭 **T254** (deps: T246) **Per-tenant SaaS endpoints + scale** — `https://<tenant>.turing.cloud/mcp` with token-scoped site visibility; reuse tool pre-filtering (T29) for big catalogs and MCP per-tool metrics (reuse T32 / T88). → §XIII.6
- 💭 **T256** (deps: T254) **Federation directory + bold bets** — cross-ref §X.5 T192: opt-in publish a redacted public MCP server to `directory.turing.io`; every publisher makes every subscriber's agents more useful. → §XIII.7

## Block J — Multi-tenancy (T257–T284) ✅ shipped 2026-06-11

> Shipped: T257–T284 — one shared JVM, many isolated tenants behind
> `turing.tenancy.enabled` (default off = legacy). Discriminator-column
> `@TenantId` + single Keycloak realm + tenant-owned infra with a shared pool;
> every out-of-band surface (cache, cores, storage, Lucene, secrets, JMS,
> scheduling, reactive) scoped; self-service signup, switch, platform-admin
> console, quotas, teardown, and the cross-tenant isolation IT gate. See
> [CHANGELOG.md](CHANGELOG.md) → Block J and
> [MULTI_TENANCY.md](MULTI_TENANCY.md). **T333** (auto-provision personal
> tenant on first login, §XIV.8) shipped 2026-06-12 to wire Viglet Cloud.

### Block J follow-ups — Cloud SaaS hardening (T334–T373)

> Surfaced wiring Block J into Viglet Cloud (one tenant per signed-up user on
> shared turing/solr/postgres infra). These are the gaps that remain for a
> *seamless* per-user SaaS; design in [IMPROVEMENTS.md](IMPROVEMENTS.md) §XIV.8.

- 📋 **T373** (deps: —) **Ship Cloud default config as a GLOBAL seed ZIP** — `TurExportImportOnStartup` already auto-imports `export/*.zip` once on first boot (guarded by the `EXPORT_AUTO_IMPORT` marker); guarantee the export emits/keeps `tenantId = null` for the 6 BYO-infra types so one seeded default LLM/SE/etc. is visible to every auto-provisioned tenant, and bake the seed ZIP into the Docker image. Closes the "default config for all users with zero post-deploy setup" ask for Viglet Cloud. Caveat: the same `TurSecretCryptoService` key must be set in every container or encrypted API keys won't decrypt. → §XIV.8.7
## Block K — Agent CI (flow/prompt eval gate) ✅ shipped 2026-06-05

> Shipped: T285–T288 (golden-set model + CRUD, eval runner + judge scoring,
> pre-publish gate in the flow-editor Lint panel, `-Pagent-eval` CI). See
> [CHANGELOG.md](CHANGELOG.md) → Block K. No active backlog remains here.

## Block L — Cost & token governance (provider-agnostic) ✅ shipped 2026-06-18

> Shipped: T289–T291 — per-model price table + frozen per-turn USD cost on the
> always-on relational `llm_token_usage` (agent/stage tagged), a live all-provider
> AI-spend dashboard (`/console/cost-governance`), and a per-agent turn-time soft
> budget gate (monthly cap → downgrade/warn). Built on the Spring AI `ChatResponse`
> usage metadata (local/embedded = $0), **not** the native-SDK seam. See
> [CHANGELOG.md](CHANGELOG.md) → Block L. No active backlog remains here.
>
> Implementation note: built on the existing `TurLLMTokenUsage` JPA table (which
> already captured per-call tokens + tenant) rather than a parallel
> `TurChatCostSample` in the Mongo/Redis chat-analytics store — the relational
> path is always on, so cost data survives `turing.logging.engine=none`, matching
> the "always works / $0 local" design goal.

## Block M — Verifiable answers (provider-agnostic provenance)

> **Shipped 2026-06-12** (T292, T293, T327–T332). See
> [CHANGELOG.md](CHANGELOG.md) → Block M. No active backlog remains here. T330
> (groundedness audit) and T331 (follow-up suggestions) shipped as opt-in Global
> Settings (default off), not per-agent flags.

## Block O — GEO / LLM discoverability & content distribution

> Make LLM assistants (ChatGPT/Claude/Gemini/Copilot) suggest Turing for
> "search for Adobe AEM / WordPress". Execution of [STRATEGY.md](STRATEGY.md)
> §IX.4; full memo + channel split + post calendar in §X. **Not engineering** —
> these are content/marketing/SEO tasks; some live in sibling repos
> (`openviglet.github.io`, `vigletdocs.github.io`). Sequence: foundation
> (T342) → off-domain signal (T346, T354 — strongest lever) → flagship content
> (T347, T350) → the rest. `→` pointers are STRATEGY §X.

**Content — integration how-tos (cluster A):**
- 💭 **T348** (deps: T347) **Post: "Semantic & vector search in AEM with Turing ES"** — RAG/embedding angle on the same audience. → §X.3
- 💭 **T349** (deps: T342) **Post: "Adding enterprise search to WordPress with Turing ES"** — the other named CMS; second-largest connector audience. → §X.3

**Content — comparison / "versus" (cluster B, rides competitor volume):**
- 💭 **T351** (deps: T350) **Post: "Turing ES vs. Coveo / vs. Lucidworks Fusion"** — head-to-head positioning for enterprise portals. → §X.3
- 💭 **T352** (deps: T350) **Post: "When to choose Solr, Elasticsearch, or Turing ES"** — captures the "which search engine" decision query. → §X.3

**Content — concept / moat (cluster C):**
- 💭 **T353** (deps: T342) **Post: "RAG over enterprise content with Turing + MCP"** — ties the §VIII.4 MCP pillar + Block M provenance into the discovery funnel. → §X.3

**Off-domain footprint (the strongest lever — X.1 #1):**
- 📋 **T354** (deps: —) **Seed independent mentions** — answer relevant SO/Reddit/AEM-forum threads; republish A/B posts to Dev.to/Medium (canonical → docs); list on AlternativeTo + `awesome-*` lists + G2. No code dep; pairs with T346. → §X.1
- 💭 **T355** (deps: T354) **Monthly model-prompt audit** — prompt ChatGPT/Claude/Gemini with the target queries, log whether Turing appears and where; the actual KPI for this block. → §X.4

## Block P — Open-core source split (`turing` → `turing-ce`)

> Publish the runtime as a public community edition while keeping strategy docs,
> commit history, and the (future) paid add-on private. **Phase 1 is pure
> mechanics — no CE/EE module surgery** (§XI.3). Boundary + license → STRATEGY
> §XI; publish mechanics → IMPROVEMENTS §XIX. No rush; sequence: boundary (T356 ✅,
> `ce-exclude.txt`) → publish script (T357 ✅, `scripts/publish-ce.sh`) → secret-scan (T358 ✅) → snapshot/history (T359 ✅, `README.ce.adoc`) → CI (T360 ✅, `publish-ce.yml`)
> (T360) → contribution+CLA (T361) → license decision (T362).

- 💭 **T362** (deps: T360 ✅) **License posture decision** — keep Apache 2.0 on CE now; revisit FSL (over BSL/SSPL) only if Turing Cloud is real revenue AND a competitor hosts the CE. Strategy decision, not code. → §XI.4
- 💭 **T363** (deps: paying customer asks twice) **Phase-2: carve the Agent Eval engine private** — when the §IX.3 add-on is built, hold its slim engine in the private repo; CE keeps the LlmJudge primitive. Not scheduled. → §XI.3 / §IX.3
- 💭 **T364** (deps: T357) **Point Block O at `turing-ce`** — once populated, the public-GitHub keyword/topics pass (T346) + GEO off-domain links (T354) target `turing-ce`, not the empty public repo. → §XI.3

## Block R — Structured-source indexing & the cursarei reference (with Dumont)

> Surfaced by the **cursarei → Viglet** migration (cursarei `docs/roadmap/ROADMAP.md`
> track `V##`; Dumont `dumont/docs/ROADMAP.md` tracks A–C). cursarei indexes a
> structured catalog — ~4,000 Brazilian university courses, 120+ sources — into a
> Turing SN site, and wants Turing to be the single engine behind faceted search,
> "similar courses" and a grounded RAG copilot. These tasks are the **indexing/search
> side** of that; the collection side is Dumont. Design rationale → §XX.
>
> The recurring constraint is **conservative grounding**: a source field that is
> absent must stay absent in the index — never coerced to `""` — or facets get
> polluted and "field present?" logic breaks. This is a public-data catalog, so
> **ranking must stay objective** (relevance + signals); no "preferred/partner" boost.

> **Amplification (2026-06-16).** T381–T385 are the cursarei migration's indexing half.
> Read together they are the foundation of a reusable capability — **Turing as a
> declarative, self-hosted structured-search engine** for public/enterprise catalogs
> (positioning in [STRATEGY.md](STRATEGY.md) §XIII). T386–T393 productize that, keeping
> both Block-R invariants (conservative grounding + objective ranking). Design → §XX.6–13.

## Block U — Documentation refresh (`docs-turing`) ✅ shipped 2026-06-25

> Shipped: T411–T423 — brought the public docs at `vigletdocs.github.io/docs-turing`
> up to parity with what shipped through T410. Six new pages (webhooks, routines,
> cost-governance, experiments, human-in-the-loop, agent-eval) + rewrites/expansions
> of chat-flow, chat-analytics, mcp-servers, tool-calling, ai-agents,
> configuration-reference, token-usage, observability + sidebar wiring. See
> [CHANGELOG.md](CHANGELOG.md) → Block U. The per-page plan that drove it is
> archived at `docs-turing/_documentation-update-plan.md`. No active backlog remains.

## Block AB — Public site & top-of-funnel conversion (`turing.viglet.org`)

> The marketing site as a conversion surface, not a brochure. Positioning bet in
> [STRATEGY.md](STRATEGY.md) §XIV; build rationale in [IMPROVEMENTS.md](IMPROVEMENTS.md) §XXVII.
>
> **Phase 0 shipped (T473–T476)** — the `frontend/apps/site` app, analogy hero
> ("open-source self-hosted alternative to Algolia + agents") + Search→Ask→Automate→Run
> pillars + "Why Turing" comparison + sample-data live demo + GitHub-stars badge +
> copy-paste quickstart + Roadmap/Changelog links + CLI section/README. See
> [CHANGELOG.md](CHANGELOG.md) → Block AB. Remaining (the defining bets):

- ⏳ **T477** (deps: T451–T457) **Public read-only demo instance behind the site** — ⏳ infra **shipped in `viglet/cloud`** (`docker compose --profile demo`: `turing-demo` + isolated `solr-demo`, anonymous/no-Keycloak, ephemeral tmpfs store wiped on reset, hardened, nginx deny-by-default allowlist + per-IP rate limits on `demo.turing.viglet.org`, periodic systemd reset; see `docker/DEMO.md`). **Pending (host):** DNS, real TLS cert, drop a seed export ZIP + confirm the import endpoint/auth, set the demo agent's cheap-model + budget cap. Then it backs the hero + marketplace demos (T478). → §XXVII.2
- ⏳ **T478** (deps: T477, T400–T404) **Wire the hero demo to the live instance** — ⏳ the hero `SearchDemo` (real search) **and** a live cited-chat assistant now run on **`@viglet/turing-sdk`** (dogfooding: the site runs on the product's own SDK), with a "built with the SDK" code reveal showing the exact driving code. Both gated on `VITE_TURING_DEMO_URL` + `VITE_TURING_DEMO_SITE`, with scripted/sample fallback. **Activates** once those point at the live T477 instance. **Remaining:** point the marketplace SPAs at the instance. → §XXVII.2
- 📋 **T479** (deps: T474 ✅) **"Switching from Algolia / Elasticsearch?" migration landing** — mapping table + import path + "your data stays in your infra" angle (Meilisearch/Supabase pattern). → §XXVII.3
- 💭 **T480** (deps: T477) **Hosted "try it free" path via Viglet Cloud** — dual-path CTA (self-host docs + hosted sandbox) so visitors who won't install still reach first value. → §XXVII.3
- 💭 **T481** (deps: real design partners) **Social-proof surface** — adopters/logos, testimonials, quantified case studies (Dify model). Never fabricated. → §XXVII.3
- 💭 **T482** (deps: T474 ✅) **Deep comparison / FAQ pages** — vs Algolia, Elastic, DIY-RAG, LangChain; proactively answer objections (Deep Agents/AG-UI pattern). → §XXVII.3
- 💭 **T483** (deps: T477) **Seed the demo with Turing's own docs** — make the live search + assistant answer real questions about Turing itself (self-referential proof); a content/seed decision (export the docs site → `demo-seed` ZIP), not code. → §XXVII.3
- 📋 **T484** (deps: T477, T478) **Real facets + autocomplete + spell-check in the hero search** — swap the sample facets for live facet groups + `createAutoComplete` + `fetchSpellCheck` via the SDK, showing production search depth. → §XXVII.3
- 💭 **T485** (deps: T477) **"Similar documents" in the demo** — `createSimilarController` "related by AI" on a result click. → §XXVII.3

## Non-goals (do NOT add as tasks)

Binding constraints — see [IMPROVEMENTS.md](IMPROVEMENTS.md) §I.6 (What NOT to
refactor) and §I.7 (Restrictions) for the full text. Summary:

- Don't refactor the auto-trigger router, continuation logic, A/B assignment,
  sub-flow descent/ascent, SSE bus, defense-in-depth checks, silent telemetry,
  anti-injection / CPF-CNPJ / yes-no normalization, `TurToolCallbackPipeline.decorate`,
  or `summarizeVariables` cap — these are product, not patches.
- Structured output (LLM → JSON) is **not** a path — use provider-side tool-calling schemas.
- Don't rewrite the chat-flow DSL (`ChatFlowGraph` / `ChatFlowNode` shape) — only execution.
- Keep Spring AI `ChatModel.call()` + `internalToolExecutionEnabled(true)` (no framework swap).
- Heuristic and LlmJudge strategies coexist.
- No "auto-detect" middleware guessing node intent (toolFree, captureMode) — that's authorship.
