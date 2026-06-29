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

## Block W — Agent↔UI transparency & client tools (AG-UI parity)

> Closed the two AG-UI parity gaps for Turing's own agent runtime (live tool-call
> SSE + frontend-defined client tools). All 📋 tasks shipped — see
> [CHANGELOG.md](CHANGELOG.md) Block W. Only the optional protocol adapter remains.

- 💭 **T441** (deps: T436, T438) **Canonical AG-UI protocol adapter** — optional endpoint that re-encodes Turing's stream into the canonical AG-UI event schema (`RUN_STARTED`, `TEXT_MESSAGE_*`, `TOOL_CALL_*`, `STATE_SNAPSHOT`/`STATE_DELTA`, …) so CopilotKit's off-the-shelf React/Angular clients connect with zero Turing-SDK code. Distribution play, not core parity — promote from 💭 only if a named integration asks. → §XXII.6

## Block Y — Reference showcase app (stress every feature)

> **Why now:** one installable reference app that exercises the **maximum surface
> of the platform** — a living demo, an SDK-vs-real-backend integration test, and
> the canonical "this is what Turing can do" artifact for sales/docs. Domain: a
> commerce-catalog copilot ("Atlas Store") — the most feature-dense domain, where
> a typed catalog + compare + add-to-cart + "why #1?" + returns flow naturally
> pull in nearly every primitive. Lives at `frontend/apps/showcase`, seeds its
> own data. **Coverage matrix + per-task feature lists → §XXIV.**

> **Block complete (2026-06-29).** All seven tasks shipped (T451–T457) — see
> [CHANGELOG.md](CHANGELOG.md) → Block Y. The `frontend/apps/showcase` "Atlas
> Store" app exercises the maximum platform surface end to end (typed catalog,
> faceted + hybrid search, RAG chat, agent power, creative bets,
> voice/memory/ops, MCP) with an in-app guided tour, the `TurShowcaseSmokeIT`
> e2e test (which uncovered + fixed a T448 circular-dependency context-startup
> bug), a `docs-turing` page, and the §XXIV.1 coverage audit (`COVERAGE.md`). No
> active backlog remains.

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

> **Block AA is fully shipped** (T464–T472) — see [CHANGELOG.md](CHANGELOG.md). No
> active backlog remains.

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

## Block J follow-ups — Cloud SaaS hardening (T334–T373)

> Surfaced wiring Block J (multi-tenancy, shipped — see [CHANGELOG.md](CHANGELOG.md)
> → Block J and [MULTI_TENANCY.md](MULTI_TENANCY.md)) into Viglet Cloud (one tenant
> per signed-up user on shared turing/solr/postgres infra). These are the gaps that
> remain for a *seamless* per-user SaaS; design in [IMPROVEMENTS.md](IMPROVEMENTS.md) §XIV.8.

- 📋 **T373** (deps: —) **Ship Cloud default config as a GLOBAL seed ZIP** — `TurExportImportOnStartup` already auto-imports `export/*.zip` once on first boot (guarded by the `EXPORT_AUTO_IMPORT` marker); guarantee the export emits/keeps `tenantId = null` for the 6 BYO-infra types so one seeded default LLM/SE/etc. is visible to every auto-provisioned tenant, and bake the seed ZIP into the Docker image. Closes the "default config for all users with zero post-deploy setup" ask for Viglet Cloud. Caveat: the same `TurSecretCryptoService` key must be set in every container or encrypted API keys won't decrypt. → §XIV.8.7

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
> §XI; publish mechanics → IMPROVEMENTS §XIX. Mechanics shipped (T356–T361); what
> remains are strategy decisions.

- 💭 **T362** (deps: T360 ✅) **License posture decision** — keep Apache 2.0 on CE now; revisit FSL (over BSL/SSPL) only if Turing Cloud is real revenue AND a competitor hosts the CE. Strategy decision, not code. → §XI.4
- 💭 **T363** (deps: paying customer asks twice) **Phase-2: carve the Agent Eval engine private** — when the §IX.3 add-on is built, hold its slim engine in the private repo; CE keeps the LlmJudge primitive. Not scheduled. → §XI.3 / §IX.3
- 💭 **T364** (deps: T357) **Point Block O at `turing-ce`** — once populated, the public-GitHub keyword/topics pass (T346) + GEO off-domain links (T354) target `turing-ce`, not the empty public repo. → §XI.3

## Block AB — Public site & top-of-funnel conversion (`turing.viglet.org`)

> The marketing site as a conversion surface, not a brochure. Positioning bet in
> [STRATEGY.md](STRATEGY.md) §XIV; build rationale in [IMPROVEMENTS.md](IMPROVEMENTS.md) §XXVII.
>
> **Phase 0 shipped (T473–T476)** — the `frontend/apps/site` app, analogy hero
> + pillars + comparison + sample-data live demo + quickstart + CLI section. See
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

## Block AC — JPA caching strategy: decouple cache from entities

> **Trigger (2026-06-29):** turning `hibernate.enable_lazy_load_no_trans` **off**
> (to kill the `@TenantId` `HHH90030002` WARN spam) exposed a latent fault: the
> hot paths read **`@Cacheable`-cached JPA entities** out of session, and a
> cached entity's `@ManyToOne(LAZY)` proxy then throws `LazyInitializationException`.
> The chat-flow engine hit this (`TurChatFlow.getDefinitionJson()` after
> `save()`); `TurSNSite` already carries a `findByIdNoCache` band-aid for the same
> class of bug. The durable fix is **don't cache JPA entities** — cache immutable
> read-models. Two-phase transition so each step lands cleanly. → §XXVIII

- 📋 **T487** (deps: T486 ✅) **Phase 2: one dedicated cached read-model layer for the hot path (semantic navigation + chat)** — a cache service over **immutable DTOs / parsed read-models** (parsed `ChatFlowGraph`, SN site + field config), explicit keys, never lazy entities. Admin area stays uncached. → §XXVIII.2
- 📋 **T488** (deps: T486 ✅) **Admin/CRUD = uncached + transactional graph loads** — load-mutate-map paths use `@Transactional` + `@Query ... JOIN FETCH` (retire `findByIdNoCache`; **not** `@EntityGraph` — it triggers log warnings, and `JOIN FETCH` is the project convention); document + lint the policy "cache = hot path only, DTOs not entities". → §XXVIII.3

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
