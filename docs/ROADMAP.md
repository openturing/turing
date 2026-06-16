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

## D.8 — Document extraction & document AI

- 💭 **T99** (deps: T58) OCR de CV (image) — phone-photo of CV bypasses Tika text extraction; integrate Tesseract (already a Tika parser dep) or Vision API. → §VII.12.a
- 💭 **T100** (deps: T58) Per-agent extraction schema — agent declares `documentSchema` in admin (slot names + validation patterns) instead of caller passing slot names per request. → §VII.12.b
- 💭 **T101** (deps: T58) Multi-doc context — upload CV + JD together → LLM identifies skill gap → writes `skill_gap` slot. → §VII.12.c
- 💭 **T102** (deps: T58) Extraction confidence per field — LLM returns `{value, confidence: 0-1}`; UI shows warning when `confidence < 0.7`. → §VII.12.d
- 💭 **T103** (deps: T58) Anthropic Files API / OpenAI Files — pass binary PDF/DOCX directly to the LLM (preserves tables/layout) instead of Tika→text. → §VII.12.e

## D.11 — Tracked follow-ups (promoted from inline notes, 2026-06-03)

- 💭 **T237** (deps: T85) **Full path-aware funnel** — T85 shipped the funnel V1 over the existing session events (the API already exposes `abandonedCount`), but a true per-node drop-off funnel needs a per-turn node-visit log. → §VII.10.b
- 💭 **T238** (deps: T86) **Conversation replay scrubber** — T86 shipped a chronological replay (render + scroll). → §VII.10.c
- 💭 **T239** (deps: T80) **Code Interpreter sandbox hardening (seccomp + gVisor)** — T80 ships Docker mode with `--cap-drop ALL --security-opt no-new-privileges` + read-only FS. → §VII.2.h / §VIII
- 💭 **T240** (deps: T110) **Sub-agent session-detail surfaces** — T110 captures `parentConversationId` + the scorecard dimension; the presentation glue is deferred. → §IX.2.c
- 💭 **T241** (deps: T70) **Per-experiment success-metric override** — T70's Thompson-sampling bandit defines "success" via `TurExperimentSignificanceService.collectVariantStats` (conversion). → §VII.8.a
- 📋 **T242** (deps: —) **CI nightly pipeline for `-Pllm-it`** — the `llm-it` Maven profile (real-LLM integration tests, gpt-4o-mini) runs locally; wire a nightly CI job that injects the `OPENAI_API_KEY` secret and runs it, so LLM-path regressions surface without… → §VI.6
- 📋 **T243** (deps: T224) **Extract a shared `useStreamingChatCore` hook** — `useTuringChat` and `useTuringLlmChat` duplicate the streaming/bubble/abort state machine. → §XII.1
- 📋 **T244** (deps: —) **Block A "cascade ascent" IT body** — the last deferred structural test body from the Block A harness audit: a multi-level sub-flow ascent case. → §0 Block A
- 💭 **T294** (deps: —) **Outbound MCP client → Streamable HTTP transport (per-server opt-in)** — MCP SDK 2.0.0-RC1 deprecated `HttpClientSseClientTransport` (legacy two-endpoint HTTP+SSE) in favour of `HttpClientStreamableHttpTransport` (single `/mcp` endpoint). The new transport is a different wire protocol with no fallback to the legacy SSE handshake, so it can't silently replace the SSE one for SSE-only servers. The deprecation is plain `@Deprecated` (not `forRemoval`), so this is non-urgent; `TurMcpToolCallbackService.createTransport(...)` currently keeps SSE behind `@SuppressWarnings`. Add a per-server transport-type field on `TurMcpServer` (default = legacy SSE) so each HTTP server picks SSE or Streamable HTTP — entity + Liquibase column + admin UI — and drop the suppression once migrated.

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

- 📋 **T119** (deps: —) **`humanApproval` node type** — pauses the flow, sends notification to a configured channel (Slack/Email/webhook), persists a pending-approval record, returns a resume URL. → §IX.5.a
- 💭 **T120** (deps: T119) **Spectator + Co-pilot mode** — operator opens `/conversation/{id}?spectate=true`, sees live chat + slots + workspace, can take the wheel by toggling `manual=true` (LLM step is skipped on that turn; operator's text becomes the assistant mes… → §IX.5.b

## E.6 — Context engineering enforcement

- ⏳ **T123** (deps: T31) **Token-budget enforcement per turn (V1: WARN/ERROR; COMPACT degrades to WARN until T115 ships)**. → §IX.7.a
- 💭 **T124** (deps: T32) **Workspace + memory + tool-offload metrics** — extend `TurChatPipelineObservation` with `turing.chat.workspace.{puts,gets,bytes_stored}` + `turing.chat.memory.compression_ratio` + `turing.chat.tools.offloaded_bytes`. → §IX.7.b

## E.7 — Developer experience (CLI + eval)

- 📋 **T125** (deps: —) **`turing` CLI** — `init` (scaffold new agent JSON), `dev` (spin up full local stack with hot reload), `deploy` (push agent + flows + tools + skills to remote instance), `logs` (tail chat events from remote). → §IX.8.a
- 📋 **T126** (deps: T125) **`turing eval` — YAML-defined regression suite** — agent authors write `evals/onboarding-flow.eval.yaml` with N test conversations + per-turn assertions (slot values, persona invariants, tool-call expected/forbidden, response regex). → §IX.8.b
- 💭 **T127** (deps: T126) **`turing eval` replay-as-test** — pull a production conversationId from the scorecard, freeze it as an eval fixture with all slot/turn snapshots. → §IX.8.c

## E.8 — Observability interop

- 💭 **T128** (deps: T32) **LangSmith-compatible export** — `TurChatPipelineObservation` events can be optionally mirrored to a LangSmith project via the public LangSmith API. → §IX.9.a
- 💭 **T129** (deps: T32) **OpenTelemetry trace export** — wrap `STAGE_CHAT_*` timers as OTel spans; emit to any OTLP-compatible backend (Honeycomb, Datadog, Jaeger). → §IX.9.b

## F.1 — Foundation (the seam itself)

- ⏳ **T131** (deps: T130 ✅) **`TurNativeChatExecutor` variant** — sibling executor shipped & wired into the agent chat path (opt-in, plain turns only); **remaining**: fuse the post-T33 collaborators (RAG / persona / chat-flow assembly) onto the native path — today it uses the agent base prompt + raw history. → §X.2

## F.2 — OpenAI Responses API + built-in tools

- 💭 **T136** (deps: T131) **OpenAI `computer_use` built-in tool** — agentic browser. Capability key advertised in the matrix but not wired (needs the screenshot/action loop). → §X.3.d

## F.3 — Anthropic server-side tools

- 📋 **T139** (deps: T131) **Anthropic `web_search_20250305` server tool** — analog to T133 but on Claude models. → §X.4.a
- 📋 **T140** (deps: T131) **Anthropic `web_fetch` server tool** — Claude pulls a URL or PDF directly (no Turing crawler needed). → §X.4.b
- 📋 **T141** (deps: T131) **Anthropic `code_execution` server tool** — Python sandbox, **free** when paired with web_search/web_fetch. → §X.4.c
- 💭 **T142** (deps: T131) **Anthropic `bash_20250124` + `text_editor_20250728` tools** — Claude edits markdown files persisted in `TurAgentWorkspace` (T111). → §X.4.d
- 💭 **T143** (deps: T131) **Anthropic `computer_use_20251124` tool with built-in prompt-injection classifier** — same use cases as T136 but on Claude with the server-side screenshot-level prompt-injection classifier that ships with the tool. → §X.4.e

## F.4 — MCP Connector (both vendors)

- 📋 **T144** (deps: T131) **Anthropic MCP Connector** (`mcp-client-2025-11-20` beta) — `messages.create(mcp_servers=[…])` lets Claude call any remote MCP server with no client code. → §X.5.a
- 💭 **T145** (deps: T138, T144) **Cross-vendor MCP federation layer** — a thin `TurMcpFederationDirectory` keeps a single per-agent allow-list of MCP server URLs; the `TurNativeChatExecutor` passes the matching subset to whichever vendor handles the turn (OpenAI via T138… → §X.5.b
- 💭 **T146** (deps: T144) **Turing-as-MCP-Server for Claude Desktop / Cursor / OpenAI Responses** — promote the existing Turing MCP server from "internal feature" to a **public consumption surface**: API-key-gated, OAuth optional, hosted at `https://<turing-host>/mc… → §X.5.c

## F.5 — Voice agents (Realtime API)

- 📋 **T147** (deps: T131) **`TurRealtimeVoiceProvider`** — WebSocket transport against `gpt-realtime` / `gpt-realtime-mini` / `gpt-realtime-1.5`. → §X.6.a
- 📋 **T148** (deps: T147) **`/chat/voice` endpoint + React hook `useTuringVoice`** — already shipped as a stub in T104; this expands it to full duplex. → §X.6.b
- 💭 **T149** (deps: T147) **Voice-first kiosk mode** — `/console/voice-kiosk?agent=X` — full-screen voice UI for trade-show booths, lobby kiosks, accessibility deployments. → §X.6.c
- 💭 **T150** (deps: T147) **`gpt-realtime-translate` simultaneous interpretation** — visitor speaks Portuguese, agent answers Portuguese AND the operator sees a live English translation in spectator mode (T120). → §X.6.d
- 💭 **T151** (deps: T147) **Voice authoring of chat-flows** — admin says "add an `aiQuestion` node asking for the lead's cargo, then route by role" → Realtime parses → emits chat-flow JSON patch → applies via the editor's existing event bus. → §X.6.e

## F.6 — Citations & verifiable RAG

- 📋 **T152** (deps: T131) **Anthropic Citations (`citations: enabled`)** — every passage in `TurSNDocument` payload added to the prompt as a `document` content block; Claude responses come back with per-sentence citation arrays pointing to `(document_id, char_start,… → §X.7.a
- 📋 **T153** (deps: T152) **Anthropic Search Results content type** — Turing's vector / hybrid retrieval (T19/T24/T24b) wraps its hits in the new `search_result` content block (encyclopedia-quality citation format) instead of the older `document` block. → §X.7.b
- 📋 **T154** (deps: T152) **Citation-aware chat UI** — React message renderer underlines the exact span Claude derived each claim from, with a hover popover showing the source chunk + deep link `/sn/{site}/document/{id}#chunk={index}`. → §X.7.c
- 💭 **T155** (deps: T154) **Citation drift detection** — async job re-resolves recent citations; flags messages where the cited chunk has been re-indexed since the answer (`citationStale=true`). → §X.7.d

## F.7 — Batch + token economics

- 📋 **T156** (deps: T131) **`TurBatchInferenceService`** — unified abstraction over OpenAI Batch API (`client.batches()`) and Anthropic Message Batches (`POST /v1/messages/batches`). → §X.8.a
- 📋 **T157** (deps: T156) **Nightly batch summarization** — the per-site AI Insights cron + per-SN-document summary now run through `TurBatchInferenceService` at the **50% Batch discount** instead of synchronous Spring AI calls. → §X.8.b
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
- 💭 **T186** (deps: T132) **Capability matrix UI heatmap** — admin dashboard heatmap of (LLM instance × capability) showing which natives are wired and which are pending. → §X.14.f

## F.14 — Bold creative bets (the long shots)

- 💭 **T187** (deps: T133, T139, T141) **"Live answers" hybrid mode** — single chat answer fuses (a) SN index hits, (b) web_search citations, (c) live data via web_fetch, (d) computed analytics via code_execution — composed in one Responses turn. → §X.15.a
- 💭 **T188** (deps: T146, T138, T144) **Turing as the "headless" enterprise-search MCP backbone** — abandon the chat UI as the only consumption surface. → §X.15.b
- 💭 **T189** (deps: T147, T150, T55) **Real-time multilingual call center augmentation** — operator's headset audio goes through `gpt-realtime-translate` (T150), agent's screen shows live cited answers (T154) sourced from the customer's index, escalation paths via T55 handoff.… → §X.15.c
- 💭 **T190** (deps: T136, T142, T78) **"Self-installing" enterprise connectors via computer_use** — customer says "connect my company's Notion" → Turing's computer-use agent navigates to Notion → guides them through API token generation → tests the connection → enables the con… → §X.15.d
- 💭 **T191** (deps: T169, T126) **Agent that improves itself overnight** — combine T169 distillation + T126 eval suite + T127 production-replay-as-test: every night, the agent's prod traffic feeds a distillation candidate, the candidate runs against the eval suite, and if… → §X.15.e
- 💭 **T192** (deps: T138, T144, T146) **Inter-tenant MCP federation** — opt-in directory at `directory.turing.io` where tenants can publish a redacted MCP server exposing **only public knowledge** (their docs, their public catalog). → §X.15.f

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

## Block L — Cost & token governance (provider-agnostic)

> Productizes the X.17 promise ("the bill drops 50%") with a screen that proves it.
> Provider-agnostic + real-time, so it works on the Spring AI path, Ollama, and
> embedded ($0) — **not** coupled to the native-SDK seam. The vendor Usage-API
> import (F.13 T183–T185) becomes the *enrichment* layer, not the foundation.

- 📋 **T289** (deps: —) **`TurChatCostSample` per-turn capture** — read prompt/completion tokens from Spring AI `ChatResponse` usage metadata × a per-model price table; one sample per turn tagged `(tenant, agent, llm, stage)`. Rides the T32 observability plumbing; works for every provider incl. local ($0). → §XVI.1
- 📋 **T290** (deps: T289) **Live AI-spend dashboard** — per agent / tenant / LLM / stage time-series + budget thresholds. Complements (does not duplicate) F.13 T184's 30-day vendor-Usage import. → §XVI.2
- 📋 **T291** (deps: T289) **Turn-time soft budget gate** — per-agent `monthlyBudgetUsd` / `perTurnSoftCapUsd`; on projected breach, downgrade to a cheaper model or warn. Generalizes F.13 T185 beyond the native path. → §XVI.3

## Block M — Verifiable answers (provider-agnostic provenance)

> **Shipped 2026-06-12** (T292, T293, T327–T332). See
> [CHANGELOG.md](CHANGELOG.md) → Block M. No active backlog remains here. T330
> (groundedness audit) and T331 (follow-up suggestions) shipped as opt-in Global
> Settings (default off), not per-agent flags.

## Block N — Pluggable reranker (cross-encoder / managed)

> **T337–T340 shipped 2026-06-12** (selectable reranker strategy + self-hosted
> cross-encoder + managed Cohere + Settings UI). See
> [CHANGELOG.md](CHANGELOG.md) → Block N. Only the optional cache remains.

- 💭 **T341** (deps: T338) **Rerank-score cache** — optional `@Cacheable("turRagRerankScore")` keyed by `(strategy, model, query, docHash)` with paired `@CacheEvict`. Precision is the win, not latency — ship only if measured. → §XVIII.1

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

## Block Q — `viglet-core` shared backend platform (cross-product)

> The bet to stop solving the same backend problems three times across Turing,
> Shio and Dumont (all on Spring Boot 4.0.6). **Incubate as a Turing submodule,
> promote to a public shared lib when stable** — the same "don't fragment
> prematurely" logic as Block P. The bet + scope + phasing → STRATEGY §XII.
> Cross-product
> initiative — Phase-1 tasks are genuine Turing module work; Phase-2 adoption in
> Shio/Dumont is gated, not scheduled.

- ⏳ **T371** (deps: T368, T369, T370) **Phase 2 — promote to a public shared library** — standalone repo [`openviglet/viglet-core`](https://github.com/openviglet/viglet-core) (self-contained parent, BOM, published to GitHub Packages) is **live**, and Turing consumes it as an external dependency (see CHANGELOG). **Shio adoption started**: `viglet-core-webhook` adopted (signer + dispatcher delegate) and `ShTenantContext` now runs on the shared `VigletTenantContext` kernel. **Remaining**: finish Shio + Dumont adoption (tenancy parity tracked separately as T394–T399). → STRATEGY §XII.3/§XII.4

> A cross-product dependency-and-utility audit (2026-06-16) of the three
> SB-4.0.6 codebases surfaced concrete duplication that the original scope
> missed; the lifts it produced (T375–T379) have all shipped — see CHANGELOG. → STRATEGY §XII.2

> **Multi-tenancy parity (T394–T399) — shipped.** Tenancy is now 100% identical
> across Turing / Shio / Dumont on the one shared `viglet-core-tenancy`
> implementation, locked by the cross-product conformance test (see CHANGELOG).

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

- 📋 **T381** (deps: —) **Conservative-grounding indexing contract** — guarantee that an
      indexed document with an *absent* attribute is not materialized as an empty value
      in Solr/ES (so facets and `exists`-style filters are correct). Document the
      null-vs-absent contract for `TurSNJobItem.attributes` and cover it with a test.
      Pairs with Dumont **D03**. → §XX.1
- 📋 **T382** (deps: T381) **Field-manifest site provisioning** — one idempotent call
      that creates/updates an SN site **and** its field schema from a single manifest
      (per field: `TurSEFieldType`, multi-valued, facet, mandatory, locale/analyzer),
      so "index a new content type" is a declarative op, not hand-clicking the console.
      Builds on `TurSNJobAttributeSpec`. Makes onboarding a structured source (cursarei,
      and any Dumont structured connector) a one-shot. **Foundation for schema-as-code
      (T386) + LLM-assisted derivation (T387).** → §XX.2
- 💭 **T383** (deps: —) **RRF hybrid ranking for public SN search** — the RAG
      knowledge-base tool already fuses BM25 + vector via `TurRagRrf` (T24); bring the
      same **single-query hybrid (RRF) ranking** to the public **Semantic Navigation
      search** results (faceted catalog search), so a catalog query gets lexical +
      semantic fusion without a separate ANN call. Lets cursarei retire its Fuse.js +
      Orama split (cursarei `V02/V03`). Generalized into a pluggable ranking pipeline +
      explainability by **T389**. → §XX.3
- 💭 **T384** (deps: —) **Public "similar documents" endpoint** — expose more-like-this /
      vector-neighbor retrieval as a first-class SN endpoint so a catalog can render
      "similar courses" from the index instead of a pre-baked neighbors file (replaces
      cursarei's `courseNeighbors.json`). Also the basis for cross-source dedup (**T390**).
      → §XX.4
- 💭 **T385** (deps: T382) **NL→facet query parsing eval pack for faceted catalogs** —
      harden and regression-test the LLM tool path that turns prose ("online graduate
      programs under 20k") into facet filters + ranges over a declared field schema,
      with a catalog eval fixture. Lets cursarei retire its hand-written `nlFilters`
      (cursarei `V03`). Eval queries can be auto-generated from the T382 manifest; feeds
      the catalog copilot (**T392**). → §XX.5

> **Amplification (2026-06-16).** T381–T385 are the cursarei migration's indexing half.
> Read together they are the foundation of a reusable capability — **Turing as a
> declarative, self-hosted structured-search engine** for public/enterprise catalogs
> (positioning in [STRATEGY.md](STRATEGY.md) §XIII). T386–T393 productize that, keeping
> both Block-R invariants (conservative grounding + objective ranking). Design → §XX.6–13.

- 💭 **T386** (deps: T382) **Manifest as versioned schema-as-code (diff + additive migration)** —
      promote the T382 manifest to a file checked into the source repo and make provisioning
      compute a *diff* against the live site: additive changes converge silently, type/analyzer
      changes require an explicit migration step. "Liquibase for the SN field schema" — the team
      already owns that mental model. → §XX.6
- 💭 **T387** (deps: T382) **LLM-assisted manifest derivation from sample documents** — given a
      sample of a source's documents, propose a draft manifest (per-field `TurSEFieldType`,
      facet/multi-valued/mandatory, locale) for human review, so onboarding a new structured
      source starts from a generated schema instead of hand-declaring every field. → §XX.7
- 💭 **T388** (deps: T381) **Per-field coverage / completeness observability** — turn the
      absent-vs-empty contract into a data-quality signal: report % populated per field across a
      site's documents (which fields a source under-fills), surfaced in admin + via API. Makes
      grounding *visible* and pinpoints which Dumont crawler under-delivers. → §XX.8
- 💭 **T389** (deps: T383) **Pluggable ranking pipeline + ranking explainability** — generalize
      T383 from "RRF on/off" into a configurable `retrieve → fuse → rerank` pipeline that reuses
      the Block N `TurRagReranker` strategy seam (LLM | CROSS_ENCODER | COHERE), plus a "why this
      result ranked here" breakdown. Objective signals only — no partner boost. → §XX.9
- 💭 **T390** (deps: T384) **Multi-source duplicate clustering** — use the T384 vector-neighbor
      retrieval to cluster the same real-world entity arriving from many sources (a course listed
      by 5 portals collapses to one result, not five). The concrete multi-source-aggregator
      data-quality win; content-based only. → §XX.10
- 💭 **T391** (deps: T382, T387, T385) **Structured-source onboarding wizard** — the capstone flow
      that chains the block: sample data → derived manifest (T387) → provision (T382) → run the
      NL→facet eval pack (T385) → live. Turns "index a new catalog" into a minutes-long guided op
      and productizes the whole block. → §XX.11
- 💭 **T392** (deps: T383, T385) **Grounded conversational catalog copilot** — compose the NL→facet
      path (T385) with hybrid SN search (T383) and the existing RAG/agent stack into a "talk to the
      catalog" assistant that answers from the index with citations and objective ranking. The
      GenAI value layer on top of the structured-search foundation. → §XX.12
- 💭 **T393** (deps: T381, T382) **Lift the manifest + grounding contract into `viglet-core`** —
      once T381/T382/T386 prove out in Turing, the conservative-grounding contract and the
      field-manifest provisioning are product-neutral; candidate for `viglet-core` (Block Q) so
      Shio and Dumont index structured sources the same way. Gated on Phase-2 promotion
      ([STRATEGY.md](STRATEGY.md) §XII.3). → §XX.13

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
