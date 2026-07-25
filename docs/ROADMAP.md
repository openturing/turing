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

## Block BH — SN result & facet rendering robustness for feed/crawl-indexed sites (§LVI)

> Same dogfood, next surface: with `openviglet/model-catalog` indexing as a vectorless
> feed (Block BF, T794) the SN search shows the **correct result count** but every
> result's **title/description renders blank** and every **facet renders with no group
> label**. The data is indexed and searchable — it just displays empty, silently. Root
> cause: the search response does a pure **name-lookup with no fallback**
> (`queryContext.defaultFields` → `doc.fields[name]`), the structured-feed documents
> carry the feed's own field names (`label`/`vendor`/`kind`, **no** `title`/`abstract`),
> and the T796 auto-provisioner never wires the site's `defaultXField` mapping or facet
> labels to those fields. The immediate unblock is deployment configuration; this block
> closes the *silent* robustness gap so a feed/crawl-indexed site renders readably out
> of the box. Design in [IMPROVEMENTS.md](IMPROVEMENTS.md) §LVI.

- 📋 **T808** (deps: —) **Default-field resolution fallback in the search query context** — `TurSNQueryContextBuilder.defaultFields` reads the site's `defaultTitleField`/`defaultDescriptionField`/`defaultTextField`/`defaultURLField`/`defaultImageField`/`defaultDateField` verbatim; when a column is blank (sites created via `TurSNSiteImport.createSNSiteFromExchange` or the T796 auto-provisioner, neither of which runs `TurSNTemplate.defaultSNUI`) the client receives a null field name and renders `""`. Fall back to the canonical `TurSNFieldName` constants (`title`/`abstract`/`text`/`url`/`image`/`publication_date`) when a column is blank, so a site missing its default-field mapping still names the conventional fields. Safety net only — a feed field *name mismatch* (`label` ≠ `title`) is fixed by T810. → §LVI.1
- 📋 **T809** (deps: —) **Facet group-label fallback to the field name** — `TurSNFacetRenderer.searchFacetBean` (and `facetLocalesFor` in `TurSNSearchProcess`) sets the group label from the locale-matched `TurSNSiteFieldExtFacet` row else `fieldExtDto.getFacetName()`; for auto-provisioned crawl/feed facet fields both are null (`TurSNFieldProvisioner.saveSiteFieldExt` sets `facetName=null`, `saveFacetLocales` creates zero rows), so the header renders blank. Add a last-resort fallback to a humanized field name when both are null (facet *value* labels already fall back to the raw value). Locale labels still win when configured. → §LVI.2
- 📋 **T810** (deps: T796, T808) **Auto-provisioning wires default fields + facet labels from the feed manifest** — extend `TurStructuredFeedProvisioningOnStartup` (T796) so a structured-feed source / `VigletFieldManifest` can declare the title/description/text/url/image/date field mapping + per-field facet labels, and apply them at provision time (set `TurSNSite.defaultXField` + each faceted field's `TurSNSiteFieldExt.facetName`/locale rows). The zero-config dogfood fix: the model-catalog site provisions with `defaultTitleField = label` (+ a chosen description field) and readable facet labels, rendering correctly on first boot. The concrete mapping lives in the deployment manifest/env (viglet/cloud) — Turing only gains the ability to honor it. → §LVI.3

## Block BI — Deterministic (LLM-independent) query planning for the copilot (§LVII)

> Dogfooding the model-catalog copilot (Block BF, T794) exposed a conceptual
> fragility, not an indexing/search bug: **NL→DSL query planning is 100% LLM**, and
> T802(b)'s "superlative→sort" rule was shipped as *prompt text only*. On a cheap
> default model that fails — a superlative/interrogative phrasing ("which chat model
> scores **highest** on the intelligence index?", "which embedding has the **lowest**
> price?") makes the model return an **empty query**, dropping even the valid
> `kind=CHAT`/`kind=EMBEDDING` facet, so retrieval returns `totalHits:0`. The engine
> side is sound — `TurDslLuceneExecutor.buildSort` already sorts numeric fields
> correctly with a relevance fallback; nothing about creation/indexing/search needs
> rework. The fix is to stop trusting the LLM for the ranking dimension: handle it
> **procedurally** and keep the prompt **generic**. Design in
> [IMPROVEMENTS.md](IMPROVEMENTS.md) §LVII.

- 📋 **T813** (deps: T811) **Model-catalog NL→facet eval sample + local regression harness** — add `model-catalog.nl-facet-eval.json` (schema + the live-failing ranking cases with golden sort/filter expectations) so the fix is reproducible locally against a **real embedded Lucene index + real LLM** via `TurNLFacetEvalIT` (`mvn verify -Pnl-facet-eval`, gated by `OPENAI_API_KEY`), plus always-on deterministic planner/scorer unit cases in CI. Turns the live bug into a repeatable local simulation with sample content. → §LVII.3

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
