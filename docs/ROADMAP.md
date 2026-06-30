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

## Block W — Agent↔UI transparency & client tools (AG-UI parity)

> Closed the two AG-UI parity gaps for Turing's own agent runtime (live tool-call
> SSE + frontend-defined client tools). All 📋 tasks shipped — see
> [CHANGELOG.md](CHANGELOG.md) Block W. Only the optional protocol adapter remains.

- 💭 **T441** (deps: T436, T438) **Canonical AG-UI protocol adapter** — optional endpoint that re-encodes Turing's stream into the canonical AG-UI event schema (`RUN_STARTED`, `TEXT_MESSAGE_*`, `TOOL_CALL_*`, `STATE_SNAPSHOT`/`STATE_DELTA`, …) so CopilotKit's off-the-shelf React/Angular clients connect with zero Turing-SDK code. Distribution play, not core parity — promote from 💭 only if a named integration asks. → §XXII.6

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

**Foundation — the provider seams (ROI-ordered):** ✅ shipped (T505–T510) —
see [CHANGELOG.md](CHANGELOG.md) Block AD. (T509's `MISTRAL` reranker was
dropped: Mistral has no managed rerank API; use the `LLM` strategy on a Mistral
instance.)

**Exploit distinctive primitives (net-new retrieval capabilities):** ✅ shipped
(T511–T515) — see [CHANGELOG.md](CHANGELOG.md) Block AD.

**Cross-vendor bridges (the bold bets):** ✅ shipped (T516–T522) — see
[CHANGELOG.md](CHANGELOG.md) Block AD. (T519's Bedrock-KB-citation half landed
with T520, where the `bedrockagentruntime` dependency lives.) **Block AD is now
fully shipped.**

## Block AE — Documentation parity & didactic refresh (post-T410)

> **Second documentation round.** Block U (T411–T423, shipped) brought
> `docs-turing` to parity with **T410**; the product then shipped **T424 → T523**
> (Blocks W, Y, Z, AA, AD, F.15, F.16 + the Block N reranker, Block R
> manifest/structured-indexing, Block M verifiable answers). A CHANGELOG-vs-docs
> confrontation found whole subsystems with **no page at all** — and `llm-instances.md`
> still says "six vendor types" when Block AD made it twelve. This block does two
> things: **(1) parity** — give every shipped capability a sidebar home; **(2) didactics**
> — a zero→first-agent learning-path spine + a per-pillar review so every page (new
> *and* old) explains the feature *from the beginning*. Full per-page plan + house-style
> checklist in [IMPROVEMENTS.md](IMPROVEMENTS.md) §XXIX. Work lands in the
> `vigletdocs.github.io/docs-turing` repo. Rule: verify every class/endpoint/config-key/vendor-enum
> name against source before publishing; never document 💭 ideas as shipped.

**Phase 1 — provider & retrieval parity (highest drift first):**
- ✅ **T524** shipped — see CHANGELOG. (Real set is **11** vendors, not 12: `AZURE_OPENAI` was removed in v2026.2.5.0.)
- 📋 **T525** (deps: T524) **New `capabilities.md`** — the F.15 capability registry/taxonomy (`kind`/`function`/`provider`/`ownsTurn`) + the **two-level gate** (instance matrix `llm_instance_capability` ∧ per-agent picker), REQUEST_OPTION knobs, and the union tool loop (native server tools + non-conflicting Turing/MCP/custom in one loop). Didactic: what a capability is, why two gates. → §XXIX.3
- 📋 **T528** (deps: T524) **Expand `embedding-models.md` + `embedding-stores.md`** — Voyage + Cohere Embed v4, multimodal embeddings (text→image, T511), contextualized chunks (T512), per-SN-site domain model (T513), quantized/Matryoshka for the Lucene store (T514), Mistral-OCR extraction bridge (T515), Gemini asymmetric task-type embeddings (T495). → §XXIX.3
- 📋 **T529** (deps: T524, T528) **Expand `rag.md`** — managed-RAG backends (Bedrock KB / Vertex AI Search, T520), native-citation RAG across vendors + Block M provenance/citation-drift (T519/T155), answer-grounding guardrail (T516), per-stage model "lanes" (T517), cost-aware fallback meta-provider (T518), multi-provider second-opinion cross-check (T522). → §XXIX.3
- 📋 **T530** (deps: T529) **New `reranking.md`** — Block N pluggable reranker (LLM / CROSS_ENCODER / COHERE, T337–T341, fail-open) + managed strategies (Vertex AI Ranking / Bedrock Rerank, T521); fold in `docs/RAG_HYBRID_CONFIG.md`. → §XXIX.3

**Phase 2 — net-new pages & native exploitation:**
- 📋 **T531** (deps: —) **New `manifest.md` (+ `search-engine.md` cross-link)** — field-manifest provisioning (T382), schema-as-code + planner/migration (T386), LLM-assisted derivation + wizard (T387/T408), hybrid RRF ranking (T383), field coverage / content-gap observability (T388). → §XXIX.3
- 📋 **T532** (deps: —) **New `client-tools.md` (AG-UI)** — live `tool_call` SSE events (T436), headless `TuringToolActivity` (T437), the client-tool park/resume protocol (T438, like `humanApproval`), `registerClientTool`/`clientTools` (T439), generative UI via client tools (T440); cross-link `react-sdk.md` / `developer-guide.md` / `ai-agents.md`. → §XXIX.3
- 📋 **T526** (deps: T524) **Expand `genai-llm.md` + `tool-calling.md` for Gemini native primitives (F.16)** — native `gemini` vs `gemini-openai`; Google Search grounding, URL Context, code execution, native image gen, Computer Use, thinking budget/thought summaries, explicit context caching, Batch, Files, full-context retrieval-free answering, native video understanding (T489–T501). → §XXIX.3
- 📋 **T527** (deps: T526) **Expand `tool-calling.md` + `ai-agents.md` for cross-vendor native parity** — OpenAI Responses `background` mode (T503), Anthropic `cache_control` + extended/interleaved thinking (T502/T504), the reusable `TurContextCacheProvider` seam (T499). → §XXIX.3
- 📋 **T533** (deps: —) **Expand `personas.md` for audience-fit (Block AA)** — audience/reader personas (`personaKind` SPEAKER/AUDIENCE/BOTH, T464), notebook source set (T465), multilingual readability/complexity scorer (T466), content-fit evaluator + red/amber/green report (T467/T468), persona-from-audio authoring (T470), the `TurTranscriptionProvider` seam (T469). → §XXIX.3
- 📋 **T534** (deps: T532) **Expand `showcase.md` into the canonical end-to-end walkthrough** — the Block Y "Atlas Store" maximum-surface tour (T451–T457) as the didactic "see it all wired together" page; mark Block X creative bets it demos as previews, not shipped features. → §XXIX.3

**Phase 3 — didactic "from-the-beginning" layer (new + old pages):**
- 📋 **T535** (deps: —) **Learning-path spine** — rewrite `getting-started/intro` + `getting-started/core-concepts` + `index.mdx` into one guided narrative (what Turing is → first search → first RAG answer → first agent → first automation), each step linking its deep page. → §XXIX.4
- 📋 **T536** (deps: T535) **Didactic pass — "Search it" + "Ask it" pillars** — apply the §XXIX.2 checklist (concept-first opener, from-zero example, Related Pages) to every page in both categories, old pages included. → §XXIX.4
- 📋 **T537** (deps: T535) **Didactic pass — "Automate it" + "Run it" + Developers + Security pillars** — same checklist across the remaining categories. → §XXIX.4

**Phase 4 — coverage gate (nothing lost):**
- 📋 **T538** (deps: T524–T537) **Coverage audit + sidebar wiring** — author `docs-turing/COVERAGE.md` (every CHANGELOG block/feature → doc page + anchor; orphan = red row), refresh/retire `_documentation-update-plan.md` to Block AE scope, wire all new pages into `sidebars-turing.ts`, and sweep older blocks (M / Q-internal / R / S) for orphans. Closes Block AE. → §XXIX.5

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
