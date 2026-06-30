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

## F.14 — Bold creative bets (the long shots)

- 💭 **T192** (deps: T138, T144, T146) **Inter-tenant MCP federation** — opt-in directory at `directory.turing.io` where tenants can publish a redacted MCP server exposing **only public knowledge** (their docs, their public catalog). → §X.15.f

## Block F.16 — Gemini native API exploitation + cross-vendor parity

> Block F wired ~30 native features for OpenAI and Anthropic through the
> `TurNativeProviderClient` seam (T130) + capability matrix (T132); **Gemini got
> none** — `TurGeminiLlmProvider` is chat-only and `throw`s on embeddings. This
> block gives Gemini the third native branch and the same capability surface,
> then exploits primitives the other two lack (task-type embeddings, explicit
> reusable context caching, 1–2 M-token full-context answering, native video).
> It also closes three cross-vendor gaps the F-blocks left open (Anthropic
> `cache_control`, OpenAI background mode, Anthropic extended thinking). Design →
> [IMPROVEMENTS.md](IMPROVEMENTS.md) §X.19 / §X.20 / §X.13.

**Foundation:** ✅ T489 shipped — see [CHANGELOG.md](CHANGELOG.md) Block F.16.

**Server-side built-in tools (parity with F.2/F.3):**
- ✅ T490 shipped — see [CHANGELOG.md](CHANGELOG.md) Block F.16.
- ✅ T491 shipped — see [CHANGELOG.md](CHANGELOG.md) Block F.16.
- ✅ T492 shipped — see [CHANGELOG.md](CHANGELOG.md) Block F.16.
- ✅ T493 shipped — see [CHANGELOG.md](CHANGELOG.md) Block F.16.
- ✅ T494 shipped — see [CHANGELOG.md](CHANGELOG.md) Block F.16.

**Distinctive Gemini cost/quality primitives:**
- ✅ T495 shipped — see [CHANGELOG.md](CHANGELOG.md) Block F.16.
- ⏳ **T496** (deps: T156) **Gemini Batch provider** — **chat batch shipped** (T157/T159 run on Gemini; see [CHANGELOG.md](CHANGELOG.md) Block F.16). Remaining: the **embeddings** batch leg (`TurBatchEmbeddingProvider` via `createEmbeddings`) so T158 re-embedding also runs on Gemini instead of synchronous; and live-API confirmation of the batch JSONL wire format. → §X.19
- ✅ T497 shipped — see [CHANGELOG.md](CHANGELOG.md) Block F.16.
- ✅ T498 shipped — see [CHANGELOG.md](CHANGELOG.md) Block F.16.

**Innovation bets (Gemini-shape-only):**
- ⏳ **T501** (deps: T489, T64, T497) **Native video understanding** — engine + VIDEO slot modality + chat-slot consumer **shipped** (Block F.16); the remaining leg is wiring the same engine into index-time SN-doc enrichment (write the transcript/scene description to the indexed media doc). → §X.19

**Cross-vendor gaps the F-blocks left open:**
- 📋 **T504** (deps: T178) **Anthropic extended / interleaved thinking** — `thinking` (`budget_tokens`) + interleaved-thinking beta in the F.3 tool loops; thought summaries feed the same T178 panel, making "reasoning budget" one cross-vendor contract. → §X.13

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

## Block AD — Provider coverage + cross-vendor capability exploitation

> Turing wires 6 chat provider types (`openai`, `anthropic`, `gemini`,
> `gemini-openai`, `ollama`, `embedded`) + Cohere **as a reranker only**. This
> block does two things: (1) closes the provider gaps that matter for
> enterprise search/RAG, and (2) — in the spirit of Block F.16 — does **not**
> stop at parity: it exploits the *distinctive* primitive each new vendor
> brings and builds **net-new Turing bridges** off them (multimodal &
> contextualized retrieval, OCR extraction, grounding guardrails, per-stage
> model "lanes", managed-RAG backends, native cross-vendor citations). All plug
> into the existing `TurGenAiLlmProvider` / `TurGenAiLlmProviderFactory` seam
> (rerank via Block N `TurRagReranker`); empty/legacy config = unchanged paths.
> Design → [IMPROVEMENTS.md](IMPROVEMENTS.md) §XXVIII.

**Foundation — the provider seams (ROI-ordered):**
- 📋 **T505** (deps: §XXVIII) **Generic OpenAI-compatible provider** (`openai-compatible`) — generalize the `gemini-openai` pattern into a first-class provider with a configurable `baseUrl`, unlocking **DeepSeek, xAI Grok, Groq, Cerebras, OpenRouter, Together, Fireworks, and local vLLM** with one seam (chat + embeddings where exposed). Highest ROI: 1 provider → ~8 vendors. → §XXVIII.1
- 📋 **T506** (deps: §XXVIII) **AWS Bedrock provider** (`bedrock`) — `TurBedrockLlmProvider` via Spring AI Bedrock Converse: Claude/Llama/Mistral/Nova on one gateway, IAM/region credentials (not an API key), Titan/Cohere embeddings. The enterprise compliance/data-residency path AWS-standardized customers require. → §XXVIII.2
- 📋 **T507** (deps: §XXVIII, T337–T340) **Voyage AI embeddings + rerank** — `voyage` embedding provider (`voyage-3` family, asymmetric `input_type` doc/query) + a `VOYAGE` `TurRagReranker` strategy (`rerank-2.5`). The specialist retrieval vendor Anthropic itself recommends; directly lifts RAG quality. → §XXVIII.3
- 📋 **T508** (deps: §XXVIII, T337–T340) **Cohere full provider** (`cohere`) — promote Cohere beyond the existing reranker: `Command` chat (built-in citation mode) + `Embed v4` multilingual/multimodal embeddings, reusing the wired Cohere credentials. → §XXVIII.4
- 📋 **T509** (deps: §XXVIII, T337–T340) **Mistral AI provider** (`mistral`) — Spring-AI-native chat (Mistral Large/Small/Codestral) + Mistral embeddings + a `MISTRAL` reranker strategy. European/GDPR-friendly origin (EU customers). → §XXVIII.5
- 📋 **T510** (deps: §XXVIII, T489) **Google Vertex AI provider** (`vertex-ai`) — enterprise Gemini via Vertex (IAM, VPC-SC, CMEK, regional endpoints, provisioned throughput) + Claude/Llama-on-Vertex via Model Garden, distinct from the public `generativelanguage.googleapis.com` path that can't serve GCP-regulated tenants. → §XXVIII.6

**Exploit distinctive primitives (net-new retrieval capabilities):**
- 💭 **T511** (deps: T507, T508, T64) **Multimodal embeddings retrieval mode** — index PDF page-images, diagrams and screenshots into the vector store via `voyage-multimodal-3` / Cohere `Embed v4` / Bedrock Titan multimodal, so text queries match *visual* documents. New "search by text → match an image" surface; bridges T64 multimodal slots + the Lucene vector store + the indexer write path. → §XXVIII.7
- 💭 **T512** (deps: T507) **Contextualized chunk embeddings** (`voyage-context-3`) — embed each chunk *with* its surrounding document context, fixing the classic chunk-context-loss RAG failure. Per-site opt-in retrieval-quality lift, transparent to query time. → §XXVIII.8
- 💭 **T513** (deps: T507, T508, T386) **Domain-specialized embedding model per SN site** — let a site pick `voyage-finance`/`voyage-law`/`voyage-code` (or a Cohere domain model) in its manifest/field config; index + query route through the site's chosen embedder. → §XXVIII.9
- 💭 **T514** (deps: T507) **Quantized / Matryoshka embeddings for the Lucene vector store** — int8/binary vectors + `outputDimensionality` truncation to shrink the embedded vector store (cheaper, faster KNN) with a measured recall trade-off, opt-in per store. → §XXVIII.10
- 💭 **T515** (deps: T509, T175) **Mistral OCR / Document-AI extraction bridge** — augment Tika on the indexer/connector write path for scanned PDFs and images via `mistral-ocr`; its structured (bbox) annotations also feed T387 manifest derivation and field extraction. → §XXVIII.11

**Cross-vendor bridges (the bold bets):**
- 💭 **T516** (deps: T506, T155, T182) **Pluggable answer-grounding guardrail** (`TurAnswerGuardrail` seam) — validate that a streamed RAG answer is actually grounded in the retrieved context before/while it streams; first adapter = Bedrock Guardrails contextual-grounding + PII redaction, with Mistral/OpenAI moderation adapters. Ties into T155 citation-drift + T182 moderation. → §XXVIII.12
- 💭 **T517** (deps: T505, T101-router) **Cross-provider per-stage model "lanes"** — declaratively bind pipeline stages to providers: a **fast lane** (Groq/Cerebras) for autocomplete/spell/query-rewrite, a **reasoning lane** (DeepSeek R1 / o-series) for rerank/judge/router, a **cheap lane** for background work — over the existing router, no code per stage. → §XXVIII.13
- 💭 **T518** (deps: T505, T183) **Cost-aware cross-provider fallback / meta-provider** (OpenRouter-style) — automatic failover + cheapest-capable routing across configured instances, with per-tenant cost attribution (Bedrock application-inference-profile tags); ties to F.13 cost governance. → §XXVIII.14
- 💭 **T519** (deps: T508, T506, T152–T155) **Native-citation RAG across vendors** — providers that return grounded citations natively (Cohere `Command` citation mode, Bedrock KB citations) decode onto the T152–T155 `TurChatCitation`/SSE contract — parity with the Gemini/Anthropic/OpenAI native grounding already wired. → §XXVIII.15
- 💭 **T520** (deps: T506, T510) **Managed-RAG retrieval backend seam** (`TurRetrievalBackend`) — optional alternative to the built-in index: Bedrock Knowledge Bases / Vertex AI Search / Vertex RAG Engine as a pluggable retriever for tenants already standardized on a cloud RAG stack. Larger architectural bet. → §XXVIII.16
- 💭 **T521** (deps: T510, T506, T337–T340) **Managed reranker strategies** — Vertex AI Ranking API + Bedrock rerank as additional Block N `TurRagReranker` strategies for cloud-native deployments (no self-hosted cross-encoder). → §XXVIII.17
- 💭 **T522** (deps: T505, T180) **Multi-provider "second opinion" answer cross-check** — for high-stakes answers, a cheap *different-vendor* model critiques the primary answer (cross-vendor LlmJudge), surfaced as a confidence signal next to the answer; composes the existing LlmJudge + T180 logprobs. → §XXVIII.18

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
