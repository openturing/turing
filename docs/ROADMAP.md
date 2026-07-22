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

## Block BC/BD — Model catalog API (moved to a dedicated repo)

> **Migrated 2026-07-21 to [`openviglet/model-catalog`](https://github.com/openviglet/model-catalog).**
> The public model catalog API (Block BC) and its multi-source regeneration
> pipeline (Block BD) now live in their own repo, which owns the canonical
> `model-catalog.json`, publishes the CORS-open JSON endpoint via GitHub Pages
> (`https://openviglet.github.io/model-catalog/catalog.json`), and carries its own
> roadmap/backlog (extended sources, custom domain). Turing consumes the published
> endpoint remotely (`turing.model-catalog.url`, remote-only — no bundled copy).
> The shipped Turing history stays in [CHANGELOG.md](CHANGELOG.md) → Blocks BC/BD;
> new catalog work happens in the dedicated repo, not here.

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

- ⏳ **T477** (deps: T451–T457) **Public read-only demo instance behind the site** — ⏳ infra **shipped in `viglet/cloud`** (`docker compose --profile demo`: `turing-demo` + isolated `solr-demo`, anonymous/no-Keycloak, ephemeral tmpfs store wiped on reset, hardened, nginx deny-by-default allowlist + per-IP rate limits on `turing-demo.viglet.org`, periodic systemd reset; see `docker/DEMO.md`). **Seeding + LLM now automatic (T620):** `turing-demo` mounts `docker/demo-seed/` at `/app/export` so Turing auto-imports the seed ZIPs on boot, and `OPENAI_API_KEY` self-provisions the default LLM instance. **Seed sources committed:** `demo-seed-src/mythical-creatures` + `demo-seed-src/turing-docs` (T483). **Live as of 2026-07-10** — deployed at `https://turing-demo.viglet.org` (DNS + TLS resolved; `/api/sn/names` serves the seeded `mythical-creatures` + `turing-docs`). **Pending (host):** set the demo agent's budget cap. It backs the hero + marketplace demos (T478 ✅). → §XXVII.2
- 💭 **T480** (deps: T477) **Hosted "try it free" path via Viglet Cloud** — dual-path CTA (self-host docs + hosted sandbox) so visitors who won't install still reach first value. → §XXVII.3
- 💭 **T481** (deps: real design partners) **Social-proof surface** — adopters/logos, testimonials, quantified case studies (Dify model). Never fabricated. → §XXVII.3

## Block AT — Persona Match: N×N persona↔content fit projects (§XLIII)

> Reframes content-fit from the per-persona validation notebook (Block AA,
> `/bento/persona/validate/:id` — **1 persona × N contents**) into a reusable,
> scheduled **analysis project** holding **N contents × N personas** with a full
> fit matrix. Moves the surface up to `/bento/persona/`, beside Persona Dialogue.
> The motivating ask: create groups/projects of contents, pick many personas, and
> get a bidirectional report — *per content:* which persona each content best
> serves; *per persona:* which content each persona best serves — re-run on a
> schedule so personas and content stay tuned to each other. Reuses the Block AA
> evaluator (`TurPersonaContentFitEvaluator`, deterministic readability ⊕ grounded
> LLM judge) unchanged; the new work is the aggregate, the matrix, the scheduler,
> and the studio UI. Design in [IMPROVEMENTS.md](IMPROVEMENTS.md) §XLIII.

> **T696–T703 shipped** (see CHANGELOG Block AT) — domain model + project-scoped
> extraction + N×N runner (SSE) + CRUD/report API + scheduled re-analysis + the
> bento studio (list/workspace/heatmap/reports) + validate-route retirement. Only
> the trend/history bet remains:

- 💭 **T704** (deps: T698, T700) **Fit trend/history + adjustment suggestions** — persist per-cell score across scheduled runs; surface drift ("this content dropped below fit for persona X since last run") and LLM-suggested persona/content adjustments, closing the bidirectional tuning loop. → §XLIII

## Block AZ — Governed LLM Gateway (OpenAI-compatible governed egress) (§XLIX)

> Turn Turing's internal LLM layer inside-out: expose an **OpenAI-compatible
> inbound API** (`/v1/chat/completions`, `/v1/embeddings`, `/v1/models`) so any
> existing app points its `base_url` at Turing with **zero code change** — the
> LiteLLM baseline. But the endpoint is a *thin façade* over the whole stack, so
> the same call transparently gains RAG, guardrails, semantic cache, tools,
> budget-aware routing and audit. The reuse is the moat: ~85–95% of the
> governance half already exists (`TurGenAiLlmProviderFactory`, `TurLLMTokenUsage`
> + cost aggregations, `TurChatCostBudgetGate`, `TurSecretCryptoService`,
> `@TenantId`, `TurLlmCacheService`, `TurModerationService`), and the
> differentiators reuse `TurAgentChatExecutor`, `TurSNGenAi`, the F.15 capability
> matrix and the Block AJ eval/distillation path. **Strategic stance:** not a
> commodity-proxy play (that market is crowded and infra-shaped); this is a
> *governed enterprise gateway* for orgs already running Turing. All opt-in behind
> `turing.gateway.enabled` (default off); nothing changes for non-gateway paths.
> Design in [IMPROVEMENTS.md](IMPROVEMENTS.md) §XLIX.

**Phase 1 — inbound seam + passthrough parity:**
- ✅ **T740** shipped — see CHANGELOG.
- ✅ **T741** shipped — see CHANGELOG.

**Phase 2 — reuse the governance half:**
- ✅ **T742** shipped — see CHANGELOG.
- ✅ **T743** shipped — see CHANGELOG.

**Phase 3 — the intelligence differentiators (the moat LiteLLM can't copy):**
- ✅ **T744** shipped — see CHANGELOG.
- ✅ **T745** shipped — see CHANGELOG.
- ✅ **T746** shipped — see CHANGELOG. **Block AZ fully shipped (T740–T748).**

**Phase 4 — surfacing + admin:**
- ✅ **T747** shipped — see CHANGELOG.
- ✅ **T748** shipped — see CHANGELOG. **Block AZ committed scope complete (T740–T744, T747, T748); only 💭 T745/T746 remain.**

## Block AV — POST-native search & link-free response contract (§XLV)

> The search response hands the client **pre-serialized GET query-string links**
> (facet `link`, pagination `href`, `cleanUpFacets`, spell-check `link`) and the
> JS SDKs parse them back (`parseHrefToParams`) and re-serialize them
> (`buildQueryString`) — a four-hop query-string round-trip that just produced the
> `&`-in-facet-value bug (`section:RAG & Chat` truncated to `section:RAG `). The
> encoder was fixed, but the coupling keeps the whole bug class (`& = + # %`, space
> in a value) alive; a **JSON request body has no query-string grammar**, so the
> class becomes impossible. A POST mapping already exists but is anonymous-blocked
> (401), takes the impersonation-oriented `TurSNSitePostParamsBean`, and returns
> the same href-laden bean — so POST today changes transport, not contract. Key
> insight: facet items already carry `filterQuery` + `selected` + `count`, so the
> "next request" is reconstructable **without parsing any URL**. This block makes
> search POST-native with a clean anonymous read-only JSON request and an opt-in
> **structured, link-free** response, then moves both JS SDKs to 100% POST driving
> toggles from structured state. **Additive:** GET + server-rendered links stay for
> deep-linking / SEO / no-JS. Design in [IMPROVEMENTS.md](IMPROVEMENTS.md) §XLV.

- 📋 **T707** (deps: —) **Public POST read-only search contract** — new anonymous-capable `TurSNSearchRequest` JSON bean (`q`/`fq[]`/`fqAnd`/`fqOr`/operators/`page`/`rows`/`sort`/`locale`/`group`/`fieldList`/`targetingRules`), decoupled from the impersonation-only `TurSNSitePostParamsBean` (the authenticated metrics/impersonation POST stays); GET untouched. → §XLV
- 📋 **T708** (deps: T707) **Structured link-free response + format negotiation** — formalize facet items as `field`+`value`+`selected`(+`count`) and add structured pagination (page/type), sort, cleanup and locale-switch **actions** so the client rebuilds the next request without parsing an href; selectable via a response-format flag (`Accept`/`responseFormat`), default = legacy `LINKS` so the GET response stays byte-identical. → §XLV
- 📋 **T709** (deps: T707, T708) **Vanilla `@viglet/turing-sdk` → POST-native** — search/autocomplete/similar controllers POST the JSON request and toggle facets/pagination/sort/cleanup from structured state; retire `parseHrefToParams`/`buildQueryString` from the hot path; prime CSRF via `/csrf`. → §XLV
- 📋 **T710** (deps: T708, T709) **React `@viglet/turing-react-sdk` + react-ui → POST-native** — same request/toggle model; react-ui facet/pagination components navigate via structured actions instead of `item.link`. → §XLV
- 📋 **T711** (deps: T709, T710) **Parity guard + demo wiring** — extend the T406 SDK contract test to the POST/structured paths; confirm the anonymous demo's nginx allowlist + CSRF priming cover POST search (the public demo is anonymous). → §XLV
- 💭 **T712** (deps: T708) **Autocomplete + select-list POST variants** — `/ac` and `/list` accept the same structured `TurSNSearchRequest` so the whole read surface is POST-capable. → §XLV
- 💭 **T713** (deps: T709, T710, T712) **Deprecate embedded href/link fields behind the format flag** — once both SDKs are POST-native, mark `link`/`href`/`cleanUpFacets`/spell-check `link` legacy (kept for no-JS/HTML/SEO/deep-linking) and drop the query-string round-trip helpers from the SDKs. → §XLV
- 💭 **T714** (deps: T709, T710) **Docs + showcase + EDS POST-native** — update `rest-api.md`/`javascript-sdk.md`, the Atlas Store showcase and the EDS example blocks to the POST-native flow. → §XLV

## Block AX — Bento header consistency: the `BentoFormHero` morph everywhere (§XLVII)

> Every bento **detail form** should present the same header the LLM-instance page
> settled on: Save/Cancel live in the hero and fade away on scroll while a fixed
> frosted save bar fades in (the `--bento-fade` morph), and the breadcrumb eyebrow
> leads with a `←` back-arrow. Identity-CRUD pages already get this free from
> `BentoEntityShell`; own-hero form pages (persona editor, AI-agent sub-sections,
> SN forms) historically hand-wired it — or, in a few cases, shipped a static
> non-morphing save row. This block makes the own-hero pattern a **single drop-in**
> (`BentoFormHero`) and applies it. Design in [IMPROVEMENTS.md](IMPROVEMENTS.md) §XLVII.

> **T735–T737 shipped** (see CHANGELOG Block AX) — the `BentoFormHero` drop-in +
> `backTo` breadcrumb, the 3 morph gaps closed, and the `←` back-arrow applied
> across every own-hero surface. Only the speculative collapse remains:

- 💭 **T738** (deps: T735) **Collapse the working manual-two-piece forms onto `BentoFormHero`** — the ~14 forms that already morph correctly by hand (11 SN forms + `ai-agent.settings.form` + `bento.global-settings` + `sn.site.custom.facet.item`) could drop their `bento-fade-out BentoSaveBar` + `BentoScrollSaveBar` boilerplate for the single component, so there is one canonical own-hero pattern. **Assessment (2026-07-13, deferred):** *not* the pure no-op it looks like. These forms render the hero as a **separate row** (`{header}` / shared `BentoAgentSubHero`) with the save bar **below** it in the body; `BentoFormHero` puts Save **inside** the hero `trailing`, so collapsing them would (1) **visibly** relocate Save into the hero — contradicting the "no visible change" premise — and (2) require the page↔form hero contract to move into each form. Worse, several (e.g. `sn.site.custom.facet.item`) save **imperatively** via `actions=` onClick, but `BentoFormHero` hardcodes `type="submit"` Save/Cancel — so it can't be dropped in without first extending its API to accept custom `actions`. Prereq if revived: add an `actions?` escape hatch to `BentoFormHero` and accept the layout change (or keep the two-piece as the sanctioned pattern for page-supplied heroes). → §XLVII

## Block AY — Pluggable URL content fetcher with headless-browser fallback (§XLVIII)

> **T739 shipped** (see CHANGELOG Block AY) — the `TurUrlFetcher` seam
> (`SIMPLE`/`HEADLESS`/`AUTO`) over a `browserless/chromium` sidecar, with the
> Global Settings "Content Ingestion" section and a `docker-compose.yaml`
> service. JS-rendered SPAs now extract text via the headless fallback. Block AY
> is fully shipped.

## Block BE — Catalog-driven model intelligence (§LIII)

> The `io.github.openviglet:model-catalog-client` (1.0.5+) already fetches a
> **rich** `ModelEntry` per model — `pricing`, `contextWindow`, `maxOutputTokens`,
> `embeddingDimensions`, `capabilities`, `modalities`, `benchmarks`, `performance`,
> `knowledgeCutoff`, `releaseDate`, `deprecated`/`status`, plus a `Classifier`
> tier (Frontier/High/Mid/Light) and `providers()`/`plans()`/`changes()`
> registries — but `TurLlmModelCatalog.toCatalogMap` collapses every entry to
> `id/label/kind` and Turing re-enters everything by hand (`TurLLMPrice`,
> `TurLLMInstance.contextWindow`, the native-capability matrix). **Widen the funnel
> once (T776), then light up cost, selection UX, correctness, optimization and
> freshness on top.** Full design in [IMPROVEMENTS.md](IMPROVEMENTS.md) §LIII.
> Feasible against the current client (1.0.5) — no library changes required (one optional
> refinement is noted in §LIII.4).

**Phase 1 — Widen the funnel + auto-pricing (headline):**
- ✅ **T776** SHIPPED — Carry rich `ModelEntry` metadata through the catalog boundary (`TurLlmModelMetadata` on `TurLlmModelOption`). Foundational; see CHANGELOG. → §LIII.1
- ✅ **T777** SHIPPED — Auto-seed & refresh `TurLLMPrice` from catalog pricing (`TurLLMPriceCatalogReconciler`; `CATALOG`/`MANUAL` source, manual always wins). Closes the "$0 unpriced model" gap; see CHANGELOG. → §LIII.1
- ✅ **T778** SHIPPED — Price provenance & staleness in the cost UI (Source/Indicative/Verified badges, verify-at-vendor links via `provider-links`, used-but-unpriced / indicative warnings). Picker price surfacing folded into T779. See CHANGELOG. → §LIII.1

**Phase 2 — Enriched selection & correctness:**
- ✅ **T779** SHIPPED — Enriched model picker: shared `model-meta.tsx` renders a compact price/context/max-out/intelligence/TTFT/cutoff + capability chips line and a tier badge on every `model-combobox` / `llm-model-multi-select` row, plus a sort control (relevance/cheapest/largest-context/most-intelligent). See CHANGELOG. → §LIII.2
- ✅ **T780** SHIPPED — Auto-fill instance metadata from catalog: selecting a model pre-fills `contextWindow` / max output tokens / `embeddingDimensions` (fill-when-empty, operator override kept) + `CatalogLimitHint` warns when a hand-entered value exceeds the catalog max. New Context-window field; update API now persists `contextWindow` (+ guarded `embeddingDimensions`). See CHANGELOG. → §LIII.2
- ✅ **T781** SHIPPED — Capability/modality-aware feature gating: the instance form warns (amber) when `toolsEnabled`/`fileUploadEnabled` is on but the selected model's catalog `capabilities`/`modalities` don't support it (`catalogSupportsTools`/`catalogSupportsFileInput`, silent when unknown). Native-capability-matrix seeding intentionally not folded in (F.15 matrix = provider-native tools, a distinct concept from catalog capability flags). See CHANGELOG. → §LIII.2
- ✅ **T782** SHIPPED — Model lifecycle / deprecation warnings: `TurLLMModelLifecycleService` flags instances on `deprecated`/`DEPRECATED`/`RETIRED` models + suggests a GA replacement (same vendor+kind, covers capabilities, ≤ price, best intelligence); `@Scheduled` warn-log scan + `GET /api/v2/llm/model-lifecycle/deprecations`; admin banner on the instance list + `ModelDeprecatedBadge` in both pickers. See CHANGELOG. → §LIII.2

**Phase 3 — Intelligence, budgeting & optimization:**
- ✅ **T783** SHIPPED — Pre-flight batch cost estimate: `TurLLMBatchCostEstimator` prices a batch (itemCount × per-item tokens × catalog rate) → projected total + `overBudget` flag; `POST /api/v2/llm/cost/preflight-batch`. Backend primitive the eval/research/embed runners call to gate an expensive run; per-runner UI confirm is follow-on. See CHANGELOG. → §LIII.3
- ✅ **T784** SHIPPED — Cost/quality/latency gateway routing: `TurGatewayRouterService` gains `cost`/`quality`/`ttft` strategies (`turing-router:cost/id1,id2`) that order the declared deployments by catalog input price / intelligence index / TTFT (missing-metric sinks last), alongside the T745 rr/latency/failover. See CHANGELOG. → §LIII.3
- ✅ **T785** SHIPPED — Model advisor: `TurLLMModelAdvisorService` filters catalog models by required capabilities/modalities + min context + budget ceiling + min tier/intelligence and ranks survivors (intelligence desc → cheaper → larger context); `POST /api/v2/llm/model-advisor/recommend`. Backend recommender core; the advisor wizard UI + one-click-create (reusing the instance-create API) is follow-on. See CHANGELOG. → §LIII.3
- 💭 **T786** (deps: T777, T784) **Right-sizing / savings suggestions** — analyze real per-agent usage (capabilities actually exercised, prompt sizes) and suggest a cheaper or faster catalog model that still meets the observed needs, with projected monthly savings. → §LIII.3

**Phase 4 — Freshness & trust:**
- 💭 **T787** (deps: T776) **Surface knowledge cutoff** — show `knowledgeCutoff`/`releaseDate` in the agent/chat UI, with an optional per-agent toggle to inject "your knowledge cutoff is <date>" into the system prompt so answers self-disclose staleness. → §LIII.4
- 💭 **T788** (deps: T776) **Catalog change-feed notifications** — consume the client `changes()` feed to notify admins when a model they use is deprecated, repriced beyond a threshold, or superseded; dashboard widget + optional email. → §LIII.4
- 💭 **T789** (deps: T778) **Consumer-plan & provider-pricing reference** — use `providers()` for the authoritative "verify" links behind T778 and optionally surface a `plans()`-backed consumer-plan reference (Claude Pro / ChatGPT Plus…) in admin help. → §LIII.4

## Block BF — Vectorless (Structured-Data) RAG: name it, feed it, integrate it (§LIV)

> Turing already ships a fully **vectorless**, grounded, cited "talk to a structured
> catalog" engine — the T392 `TurCatalogCopilotService` (§XX.12): NL→filter over a
> **declared** field schema (`TurLlmNLFacetParser`, T385) → structured DSL search
> (`TurDslSearchService`, Solr/ES/Lucene, **no embeddings**) → objective re-rank
> (`TurSNHybridRankingService`) → LLM answers strictly from cited rows (`[n]` →
> `TurCatalogCitation`), needing only a default LLM (no embedding model / no vector
> store). But the word "vectorless" appears nowhere, the capability isn't
> discoverable, it can't **pull** a remote JSON feed, and it has no frontend
> widget/SDK hook. This block makes vectorless RAG an explicit, integration-friendly
> capability and closes the two real gaps — dogfooded with the public
> `openviglet/model-catalog` on `turing-demo.viglet.org`. Design in
> [IMPROVEMENTS.md](IMPROVEMENTS.md) §LIV.

- 📋 **T790** (deps: —) **Name & surface "Vectorless (Structured-Data) RAG"** — promote the T392 copilot retrieval path to a first-class, explicitly-named knowledge-base *mode*: a Vector / Vectorless-Structured / Hybrid selector on the SN-site AI form, a `ChatDisabledReason` readiness path that recognizes vectorless needs only a default LLM (not `MISSING_EMBEDDING`/`MISSING_STORE`), and an end-user docs page. No new retrieval code — discoverability + config surface over an engine that already exists. → §LIV.1
- 📋 **T791** (deps: —) **Scheduled JSON-feed source (pull ingester)** — a `TurStructuredFeedIngestService` that GETs a remote JSON feed (array or catalog envelope; natively consumes `catalog.ndjson`) on a TTL via the T739 `TurUrlFetcher`, declares/refreshes the SN-site field schema (`TurSNSiteFieldExt`) from a field manifest (the model catalog's `query-manifest.json`), maps records → `TurSNJobItems`, and POSTs to the existing `/api/sn/import` pipeline (de-indexing vanished ids). Fills the one genuine ingestion gap (today content must be pushed by an external connector). → §LIV.2
- 📋 **T792** (deps: T790) **Copilot React widget + react-sdk hook** — a `use-turing-copilot` react-sdk hook + a thin, embeddable Q&A component bound to `POST /api/sn/{siteName}/copilot`, rendering the answer + `TurCatalogCitation[]` as linked sources (mirrors `ai-mode-chat.tsx` for the vectorless/copilot path, multi-turn like `TurCatalogCopilotResult`). Closes the frontend gap so the admin app and external sites can embed vectorless Q&A with citations. → §LIV.3
- 📋 **T793** (deps: T790) **Stuff-all mode for small datasets** — a per-source copilot option that, below a token threshold, skips DSL retrieval and grounds the LLM on the whole context bundle (e.g. the model catalog's `context.txt`, ≤ ~40k tokens) — the extreme-vectorless path for tiny catalogs, still cited + fail-open; retrieval stays the default above the threshold. → §LIV.4
- 📋 **T794** (deps: T790, T791, T792) **Dogfood: model-catalog as the first vectorless KB on `turing-demo.viglet.org`** — provision an SN site + field schema from the model catalog's `query-manifest.json`, wire the T791 feed to the published `openviglet.github.io/model-catalog` endpoint, enable Vectorless RAG, expose the copilot; the live demo answers "cheapest embedding model with ≥1M context?" with cited model links. The reference integration for the model-catalog "Ask the catalog" widget (that repo's Block M). → §LIV.5
- 💭 **T795** (deps: T790) **Vectorless RAG over the OpenAI-compatible gateway** — extend the Block AZ gateway (§XLIX) so a `turing-copilot:<site>` model route (or `x-turing-copilot-site` header) invokes the vectorless copilot path, letting any OpenAI-style client get grounded, cited structured answers with zero Turing-SDK code — the cleanest "separate backend" integration for an external static site; a natural extension of the T744 `turing-sn:<site>` / RAG-as-a-header routing. → §LIV.6

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
- No in-process ONNX/DJL Whisper transcription runtime (the retired `LOCAL_ONNX` backend, ex-T694). The supported fully-local path is `OPENAI_COMPATIBLE` pointed at a self-hosted server (e.g. faster-whisper-server) — the platform won't bundle a fragile in-process Whisper dependency (T193 Jlama discipline).
