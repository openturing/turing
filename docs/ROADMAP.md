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

- ⏳ **T477** (deps: T451–T457) **Public read-only demo instance behind the site** — ⏳ infra **shipped in `viglet/cloud`** (`docker compose --profile demo`: `turing-demo` + isolated `solr-demo`, anonymous/no-Keycloak, ephemeral tmpfs store wiped on reset, hardened, nginx deny-by-default allowlist + per-IP rate limits on `demo.turing.viglet.org`, periodic systemd reset; see `docker/DEMO.md`). **Seeding + LLM now automatic (T620):** `turing-demo` mounts `docker/demo-seed/` at `/app/export` so Turing auto-imports the seed ZIP on boot, and `OPENAI_API_KEY` self-provisions the default LLM instance. **Pending (host):** DNS, real TLS cert, drop the seed export ZIP into `docker/demo-seed/` once, set the demo agent's budget cap. Then it backs the hero + marketplace demos (T478). → §XXVII.2
- ⏳ **T478** (deps: T477, T400–T404) **Wire the hero demo to the live instance** — ⏳ the hero `SearchDemo` (real search) **and** a live cited-chat assistant now run on **`@viglet/turing-sdk`** (dogfooding: the site runs on the product's own SDK), with a "built with the SDK" code reveal showing the exact driving code. Gated on `VITE_TURING_DEMO_URL`; the SN site is auto-discovered via `/api/sn/names` (`VITE_TURING_DEMO_SITE` now optional — T620), with scripted/sample fallback. **Activates** once the URL points at the live T477 instance. **Remaining:** point the marketplace SPAs at the instance. → §XXVII.2
- 📋 **T479** (deps: T474 ✅) **"Switching from Algolia / Elasticsearch?" migration landing** — mapping table + import path + "your data stays in your infra" angle (Meilisearch/Supabase pattern). → §XXVII.3
- 💭 **T480** (deps: T477) **Hosted "try it free" path via Viglet Cloud** — dual-path CTA (self-host docs + hosted sandbox) so visitors who won't install still reach first value. → §XXVII.3
- 💭 **T481** (deps: real design partners) **Social-proof surface** — adopters/logos, testimonials, quantified case studies (Dify model). Never fabricated. → §XXVII.3
- 💭 **T482** (deps: T474 ✅) **Deep comparison / FAQ pages** — vs Algolia, Elastic, DIY-RAG, LangChain; proactively answer objections (Deep Agents/AG-UI pattern). → §XXVII.3
- 💭 **T483** (deps: T477) **Seed the demo with Turing's own docs** — make the live search + assistant answer real questions about Turing itself (self-referential proof); a content/seed decision (export the docs site → `demo-seed` ZIP), not code. → §XXVII.3
- 📋 **T484** (deps: T477, T478) **Real facets + autocomplete + spell-check in the hero search** — swap the sample facets for live facet groups + `createAutoComplete` + `fetchSpellCheck` via the SDK, showing production search depth. → §XXVII.3
- 💭 **T485** (deps: T477) **"Similar documents" in the demo** — `createSimilarController` "related by AI" on a result click. → §XXVII.3

## Block AG — Bento UI: complete the interface migration (100%)

> A Bento (iOS-flavored frosted-glass mosaic) redesign of the admin. Phases 0–4
> shipped (T547–T568, T573, T576) — every console CRUD/functional/admin surface
> now exists in `/bento`; see [CHANGELOG.md](CHANGELOG.md) → Block AG. Design +
> full screen inventory → [IMPROVEMENTS.md](IMPROVEMENTS.md) §XXXI. Remaining is
> the cutover & retirement phase:

**Phase 5 — cutover & retirement:** Block AG's committed scope (T547–T571), the
T572 delight-polish follow-up, and the T619 persona-parity follow-up are all
**complete** — cutover done, console retired, full a11y/responsive/perf/e2e pass
passed, onboarding polish shipped, and the two console-only persona tools
(from-audio authoring + persona-suggest) re-added to bento. Block AG is closed.

## Block AM — HuggingFace embedding provider + provider-aware model discovery

> Today an embedding model is created two ways: pick an **LLM instance** (remote
> embeddings API, model typed free-text into `modelReference`) or the
> **`TRANSFORMERS_LOCAL`** sentinel (in-process ONNX via Spring AI
> `TransformersEmbeddingModel`, with `.onnx` + `tokenizer.json` **paths typed by
> hand**). The local path already downloads from `huggingface.co` URLs — the
> default startup model is literally `.../sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx`.
> This block adds a third provider, **huggingface.co**, that does the same job as
> the local transformers path but lets you **pick the model from a list** instead
> of typing URLs, and unifies the frontend model field into one **provider-aware
> picker** (list-from-LLM · list-from-HuggingFace · manual local path). Design in
> [IMPROVEMENTS.md](IMPROVEMENTS.md) §XXXVI. Sequence: backend provider (T623) →
> the two discovery seams (T624 HF, T625 LLM-embeddings) → unified frontend picker
> (T626) → the exploit tail (T627–T629).

- ✅ **T623** (deps: —) **HuggingFace embedding provider (backend)** — SHIPPED. `HUGGINGFACE` sentinel + `TurHuggingFaceEmbeddingModelFactory` reading the repo id from `modelReference` (no new columns), a shared `TurTransformersEmbeddingModelBuilder` both local + HF factories reuse, and `TurHuggingFaceRepoResolver` (tree-probed ONNX path + fail-open layout fallback + `hasOnnxAndTokenizer` for T624). Wired into `TurRagContextBuilder`. → CHANGELOG Block AM.
- ✅ **T624** (deps: T623) **HuggingFace model discovery service** — SHIPPED. `TurHuggingFaceModelDiscoveryService` queries HF `/api/models` (sentence-transformers filter, downloads-ranked), ONNX-probes each candidate via `TurHuggingFaceRepoResolver.hasOnnxAndTokenizer` so only loadable models are offered, and falls back to the bundled `TurHuggingFaceModelCatalog` with a `Source {LIVE, CATALOG, NONE}` result (mirrors T577). Endpoint `GET /api/embedding-model/hf-models?query=`. → CHANGELOG Block AM.
- ✅ **T625** (deps: —) **LLM embedding-model discovery** — SHIPPED. `TurLlmModelDiscoveryService.listEmbeddingModels(...)` filters the T577 live+catalog listing to embedding-capable models (`isEmbeddingModel` heuristic: `embed` in id/label or a non-rerank Voyage id; rerank/chat excluded), each stage filtered so a live list with no embeddings falls back to the catalog. Exposed at `POST /api/embedding-model/llm-models`. → CHANGELOG Block AM.
- ✅ **T626** (deps: T624, T625) **Provider-aware model picker (frontend)** — SHIPPED. `bento.embedding.form.tsx` reworked into three provider modes: **LLM instance** (embedding `ModelCombobox` via T625 `useLlmEmbeddingModels`), **HuggingFace.co** (new `HuggingFaceModelCombobox` with server-side search + live/catalog badges + downloads/dims + ONNX-verified check, via `useHuggingFaceModels`), **Local transformers (manual)** (the existing `.onnx`/`.json` inputs). en/pt i18n; frontend `tsc -b --noEmit` green. **Completes the committed Block AM scope (T623–T626).** → CHANGELOG Block AM.
- 💭 **T627** (deps: T623) **Dimension detection + reindex-awareness on select** — when an HF/LLM model is chosen, probe its embedding **dimension** (HF `config.json` `hidden_size`, or a one-shot embed) and warn in the UI when it differs from the current default store's dimension (a change means a reindex + a matching vector store). Composes with T386 (manifest reindex) / T388 (field coverage). → §XXXVI.5
- 💭 **T628** (deps: T623) **ONNX variant / quantization picker** — HF repos often ship several ONNX artifacts (`model.onnx`, `model_quantized.onnx`, `model_O4.onnx`); let the picker choose a variant (size/latency vs accuracy) and store the chosen artifact path. Ties into T514 (quantized/Matryoshka embeddings for the Lucene store). → §XXXVI.6
- 💭 **T629** (deps: T623) **Migrate default-RAG startup provisioning to `HUGGINGFACE`** — have `TurDefaultRagInfraOnStartup` (T622) provision the default embedding model as a `HUGGINGFACE` model storing the repo id `sentence-transformers/all-MiniLM-L6-v2` instead of the hardcoded `resolve/main/…` URLs, so the seeded default and a user-picked HF model are the same shape. Idempotent; keep the raw-URL local path as a fallback. → §XXXVI.7

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
