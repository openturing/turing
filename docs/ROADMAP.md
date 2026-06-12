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

- 📋 **T130** (deps: —) **`TurNativeProviderClient` seam** — interface exposing per-`TurLLMInstance` access to the official OpenAI (`com.openai:openai-java`) and Anthropic (`com.anthropic:anthropic-java`) SDK clients. → §X.2
- 📋 **T131** (deps: T130) **`TurNativeChatExecutor` variant** — sibling to `TurAgentChatExecutor` (post-T33 decomposition). → §X.2
- 📋 **T132** (deps: T131) **Per-LLM-instance feature flags table** — `tur_llm_instance_capability` (instanceId, capabilityKey, enabled, configJson). → §X.2

## F.2 — OpenAI Responses API + built-in tools

- 📋 **T133** (deps: T131) **OpenAI `web_search` built-in tool** — wires the `web_search_preview` tool into the Responses path. → §X.3.a
- 📋 **T134** (deps: T131) **OpenAI `file_search` built-in tool** — provisions OpenAI-hosted vector stores per agent/tenant. → §X.3.b
- 📋 **T135** (deps: T131, T80) **OpenAI `code_interpreter` built-in tool** — alternative path to T80's in-process Code Interpreter for installs that prefer OpenAI's managed Container sandbox. → §X.3.c
- 💭 **T136** (deps: T131) **OpenAI `computer_use` built-in tool** — agentic browser. → §X.3.d
- 📋 **T137** (deps: T131) **OpenAI `image_generation` as inline tool** — `gpt-image-1`/`gpt-image-1.5` invoked inside the Responses turn. → §X.3.e
- 💭 **T138** (deps: T131) **OpenAI `mcp` tool (remote MCP connector)** — Responses API gains a first-class `mcp` tool that points at a remote MCP server URL (Streamable HTTP / SSE). → §X.3.f

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

- 📋 **T247** (deps: T246 ✅) **Read search surface as MCP tools** — `list_sites`, `get_site_fields`, `search_site`, `get_document`, `similar_documents`, `facet_search`, `autocomplete`. → §XIII.3
- 📋 **T248** (deps: T247) **`rag_answer` — one-call cited RAG** — hybrid search + grounded answer + citations in a single MCP tool call, so a client gets enterprise-truth-grounded prose without orchestrating 3 calls. → §XIII.3
- 📋 **T249** (deps: T246) **`invoke_agent` + `list_agents`** — run a configured AI Agent / chat-flow headlessly over MCP (returns final answer + captured slots), reusing `TurAgentChatExecutor`. → §XIII.3
- 📋 **T250** (deps: T247) **MCP Resources** — expose indexed documents + site schemas as browsable MCP resources (`turing://site/{id}/doc/{docId}`), so clients can read/attach content directly, not only search it. → §XIII.3
- 📋 **T251** (deps: T245) **MCP Prompts** — publish personas + system-prompt fragments as reusable MCP prompts, so a client can adopt the customer's brand voice with one prompt pick. → §XIII.3
- 📋 **T252** (deps: T247) **Analytics tools** — `top_failed_searches`, `search_metrics`, `content_gaps`. → §XIII.4
- 💭 **T253** (deps: T246) **Gated write / ingestion tools** — `index_document`, `deindex_document`, `reindex_site`. → §XIII.3
- 💭 **T254** (deps: T246) **Per-tenant SaaS endpoints + scale** — `https://<tenant>.turing.cloud/mcp` with token-scoped site visibility; reuse tool pre-filtering (T29) for big catalogs and MCP per-tool metrics (reuse T32 / T88). → §XIII.6
- 💭 **T255** (deps: T245) **One-click client config (DX)** — admin generates ready-to-paste `mcp.json` snippets for Claude Desktop / Claude Code / Cursor (endpoint + auth). → §XIII.7
- 💭 **T256** (deps: T254) **Federation directory + bold bets** — cross-ref §X.5 T192: opt-in publish a redacted public MCP server to `directory.turing.io`; every publisher makes every subscriber's agents more useful. → §XIII.7

## Block J — Multi-tenancy (T257–T284) ✅ shipped 2026-06-11

> Shipped: T257–T284 — one shared JVM, many isolated tenants behind
> `turing.tenancy.enabled` (default off = legacy). Discriminator-column
> `@TenantId` + single Keycloak realm + tenant-owned infra with a shared pool;
> every out-of-band surface (cache, cores, storage, Lucene, secrets, JMS,
> scheduling, reactive) scoped; self-service signup, switch, platform-admin
> console, quotas, teardown, and the cross-tenant isolation IT gate. See
> [CHANGELOG.md](CHANGELOG.md) → Block J and
> [MULTI_TENANCY.md](MULTI_TENANCY.md). No active backlog remains here.

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

> Makes the RAG provenance you already retrieve *visible* to the end user — the
> trust unlock for Education/Finance vs a source-less ChatGPT. Works for any LLM
> over existing T24 hybrid-RAG hits; Anthropic per-span Citations (F.6 T152–T154)
> enrich it when the agent runs on Claude.

- 📋 **T292** (deps: T24) **RAG attribution metadata end-to-end** — every `search_knowledge_base` hit carries `(sourceId, title, url, chunkIndex, score)` through the SSE stream as a structured `sources[]` block — no vendor citation API required. → §XVII.1
- 📋 **T293** (deps: T292) **Source-chip UI + "why did you say this?" trace** — chat renderer (react-sdk + admin) shows inline source chips with confidence and a one-click expansion to the retrieved chunks. Cross-refs F.6 T154 for Claude per-span underlining. → §XVII.2

> **AddSearch-parity sub-block (T327–T332).** T292/T293 cover the
> `search_knowledge_base` tool + MCP path. The **public semantic-navigation site
> chat** (`TurSNGenAi` → `/api/sn/{site}/chat/conversation` → `ai-mode-chat.tsx`)
> is a *separate* path that stuffs chunks straight into the system prompt and
> loses provenance — these tasks bring the same grounding maturity there, to match
> AddSearch's "AI Answers + AI Conversations over site content". → §XVII.3

- 📋 **T327** (deps: T292) **Provenance in the public SN chat** — `TurSNGenAi.retrieveAndBuildSystemPrompt` preserves `(sourceId, title, url, chunkIndex, score)` per chunk and emits the `sources[]` SSE event (same contract as T292) on `/api/sn/{site}/chat/conversation`; today the chunks are joined into a raw string and provenance is lost. → §XVII.3
- 📋 **T328** (deps: —) **Relevance gate + optional reranker for SN RAG** — replace the `similarityThreshold=0.0` used on facet-filtered queries with a real cutoff and an optional top-N→top-k rerank before stuffing, so weak matches stop polluting the context (the main hallucination driver). → §XVII.4
- 📋 **T329** (deps: T328) **Deterministic "not in this site" refusal** — when post-gate retrieval is empty, short-circuit to the canonical refusal without an LLM call instead of relying on the model obeying the prompt instruction. → §XVII.5
- 💭 **T330** (deps: T327) **Optional groundedness check for SN answers** — reuse the `LlmJudge` infra as a post-generation faithfulness audit (is the answer supported by the retrieved chunks?); on fail, regenerate with a stricter prompt or flag low confidence. Opt-in per agent (default off) to keep latency/cost predictable. → §XVII.6
- 💭 **T331** (deps: T327) **Suggested follow-up questions in AI mode** — after each answer, generate 2–3 follow-up suggestions grounded in the retrieved context, rendered as clickable chips (parity with AddSearch "AI Conversations"). → §XVII.7
- 📋 **T332** (deps: T327, T293) **Source chips on the public `ai-mode-chat.tsx`** — extend the T293 source-chip renderer (today admin + react-sdk) to the public SN AI-mode chat, with a confidence cue and one-click expansion to the cited chunks. → §XVII.8

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
