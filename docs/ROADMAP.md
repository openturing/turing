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

## Block AW — Synthetic User Research: persona studies, interviews & continuous insight (§XLVI)

> Matures the persona surface from *acting on one persona at a time* (chat, Match,
> Dialogue) into a **research method**: run structured interviews with a cohort of
> synthetic users, synthesize an insights report with saturation scoring, and feed
> the findings back into the product. Benchmarked against **synth.users** (Synthetic
> Users): audience definition → dynamic/custom/concept-test interviews → multi-agent
> OCEAN-profiled participants → RAG-grounded answers → thematic insights report +
> saturation scoring → continuous validation + a multi-study planner (PRISMA).
>
> **The value is the interconnection, not a clone.** Turing already owns every half
> that synth.users has to fake: a persona model (`TurPersona`, SPEAKER/AUDIENCE/BOTH
> since Block AA), a grounded RAG/SN retrieval stack, a real agent runtime under
> test, an evaluation platform (Block AJ), an analytics stack (sentiment trajectory
> T87), a scheduler + project pattern (Persona Match / Dialogue), an LLM-lane router
> (T517), cached LLM synthesis (`TurLlmSummaryService`), and persona synthesis from
> audio (Block AS). This block is thin composition over those primitives — a study
> aggregate + an interview engine + a synthesizer + the bridges that make the panel
> pay for itself (interview your **own** deployed agent; promote transcripts into
> eval datasets). Reuses the Persona Match aggregate/scheduler/studio shape (Block
> AT) and the persona-chat executor (T578) unchanged where possible. All opt-in;
> default persona/agent behavior is untouched. Design in
> [IMPROVEMENTS.md](IMPROVEMENTS.md) §XLVI.

> **Frontend & SDK scope.** Per the "explicit UI per task" decision, every task
> below carries its own **UI:** clause (the visible surface it must ship), with
> `/bento/persona/research` (T730) as the studio hub. **SDK stance:** the embed JS
> SDKs (`@viglet/turing-sdk` / `@viglet/turing-react-sdk`) are customer-facing
> (search/chat) — synthetic research is an **admin/research tool**, so most tasks
> are *not* SDK work. The only embed-SDK touch is OCEAN metadata on the existing
> persona surface (T717); the programmatic/developer surface for running studies is
> a **CLI** concern (T734), consistent with `turing eval`.

**Phase 1 — persona depth — ✅ shipped (T717 OCEAN facet, T718 RAG grounding; see CHANGELOG Block AW).**

**Phase 2 — the study model — ✅ shipped (T719 study aggregate + CRUD/API, T720 interview engine, T721 N-persona runner + SSE; see CHANGELOG Block AW).**

**Phase 3 — synthesis & sufficiency — ✅ shipped (T722 insights report, T723 saturation scoring, T724 theme/affinity graph; see CHANGELOG Block AW).**
- ✅ **T722** (deps: T721) **Insights report — themes + verbatim quotes + recommendations** — synthesize transcripts through `TurLlmSummaryService` (cached, regenerable) into an executive summary, ranked themes with **verbatim participant quotes** (traceable to the interview + persona), and recommendations; bidirectional lenses (*by theme* / *by persona*). **UI:** none of its own — the report renderer + lens toggle + regenerate button ship in T730. → §XLVI.3
- ✅ **T723** (deps: T721) **Saturation scoring (deterministic, CI-free)** — a pure novelty scorer (embedding/keyword theme-overlap, T385/T466 pattern — no LLM in CI) that flags when marginal personas stop adding new themes → "sample adequate at N", the honest antidote to fabricated-confidence synthetic research. **UI:** the saturation gauge/curve is part of the studio (T730). → §XLVI.3
- ✅ **T724** (deps: T722) **Theme / affinity knowledge graph** — a concept↔problem↔persona graph over the synthesized themes (which cohorts cluster on which pain point). Pure, LLM-free re-projection of the T722 report into a persona↔theme bipartite graph (no new inference). **UI:** interactive graph view in the studio, rendered with the existing viz stack. → §XLVI.3

**Phase 4 — interconnection (the value multiplier over synth.users):**
- ✅ **T725** (deps: T721, T722) **Studies → Block AJ eval datasets/graders bridge** — promote interview transcripts + synthesized findings into a `TurEvalDataset` (T595) and expose the persona panel as a reusable **agent-QA harness**: a study becomes a repeatable eval that regression-tests an agent against a synthetic audience. **UI:** a "promote to dataset / run as eval" action in the studio that deep-links into the existing Eval Studio (`/bento/eval`) — no new eval UI. → §XLVI.4
- ✅ **T726** (deps: T720) **Interview your *own* deployed agent** — a study target can be a live `TurAIAgent` (its real flow/tools/RAG) instead of a bare LLM, so synthetic users UX-test / red-team the actual shipped agent. Uniquely Turing's, because it owns both the research panel *and* the agent under test. **UI:** a target selector (Default LLM ↔ pick an agent) in the study config within the studio. → §XLVI.4
- ✅ **T727** (deps: T719) **Concept-testing bridge onto Persona Match** — a `CONCEPT_TEST` study over a piece of content is content-fit's sibling; route it through `TurPersonaContentFitEvaluator` (Block AT) so concept feedback and fit scoring share one evaluator. **UI:** reuses the Match report surface (no new UI). → §XLVI.4
- ✅ **T728** (deps: T721, T722) **Multi-model per-stage lanes ("Big Shuffle")** — let a study assign different LLM instances per stage (fast model to interview, a stronger model to synthesize) reusing the T517 model-lane seam, so no single model's bias colors the whole study. Saturation is LLM-free, so it has no lane. **UI:** per-stage LLM-instance pickers in the study config (studio), reusing the existing LLM-instance combobox. → §XLVI.4
- ✅ **T729** (deps: T721, T722) **Continuous Insight — scheduled re-runs + insight drift** — re-run a study on a MANUAL/DAILY/WEEKLY cadence (reuse the Persona Match scheduler + ShedLock) and surface how themes/saturation move over time (deterministic snapshots; T87 sentiment trajectory a future extension), turning one-shot research into continuous validation. **UI:** schedule control + a drift/trend chart in the studio, reusing the Persona Match schedule UX + recharts. → §XLVI.4

**Phase 5 — authoring, the studio & the developer surface:**
- ✅ **T730** (deps: T721, T722) **Bento Research Studio (the frontend hub)** — `/bento/persona/research` project mosaic + `:studyId` workspace: audience picker (shared `PersonaSelectGrid`), protocol editor, live SSE interview feed, report lenses, saturation gauge, PDF export — the Persona Match / Dialogue studio pattern. Renders the surfaces that Phases 2–4 deliberately left headless. → §XLVI.5
- ✅ **T731** (deps: T717) **Audience cohort synthesis** — generate a *diverse* persona cohort from a one-paragraph audience brief (demographics + psychographics → N personas with spread OCEAN facets), reusing the AI-authoring strict-schema path (T426) + persona-from-audio drafting (T470); never auto-saved. **UI:** brief input + a review grid of the drafted personas (edit/keep/discard) opening the existing persona review form. → §XLVI.5
- ✅ **T732** (deps: T730, T731) **Research Assistant (guided authoring)** — a conversational guide that helps define the audience + goal + protocol up front and offers to synthesize on completion (synth.users' Research Assistant), built on AI Authoring; skippable. **UI:** a chat-style setup drawer/step in the studio create flow. → §XLVI.5
- ✅ **T733** (deps: T730) **Multi-study planner + cross-study rollup (PRISMA)** — one surface to design/run several studies across different audiences and roll their insights up into a program-level view. **UI:** a planner board + a program rollup page under `/bento/persona/research`. → §XLVI.5
- ✅ **T734** (deps: T721, T722, T725) **Developer surface — `turing research` CLI (+ optional client)** — a `@viglet/turing-cli` verb (mirroring `turing eval`) to define/run a study and fetch its insights from code/CI, plus an optional thin programmatic client; the customer-facing **embed** SDKs stay unchanged beyond the T717 OCEAN metadata. → §XLVI.5

## Block AX — Bento header consistency: the `BentoFormHero` morph everywhere (§XLVII)

> Every bento **detail form** should present the same header the LLM-instance page
> settled on: Save/Cancel live in the hero and fade away on scroll while a fixed
> frosted save bar fades in (the `--bento-fade` morph), and the breadcrumb eyebrow
> leads with a `←` back-arrow. Identity-CRUD pages already get this free from
> `BentoEntityShell`; own-hero form pages (persona editor, AI-agent sub-sections,
> SN forms) historically hand-wired it — or, in a few cases, shipped a static
> non-morphing save row. This block makes the own-hero pattern a **single drop-in**
> (`BentoFormHero`) and applies it. A read-only audit
> (`scratchpad/bento-header-audit.md`) found **zero** broken `StickySaveBar`-in-bento
> cases, 3 real morph gaps, and ~16 heroes missing the arrow. All cosmetic/structural,
> no behavior change. Design in [IMPROVEMENTS.md](IMPROVEMENTS.md) §XLVII.

- ✅ **T735** **`BentoFormHero` single-component morph + `backTo` breadcrumb** — a drop-in that wraps `BentoHero` (fade-out Save/Cancel in `trailing`) + `BentoScrollSaveBar`, plus `backTo`/`backLabel` props that render the `←` breadcrumb like `BentoEntityShell`. Adopted in the persona editor (retires its non-morphing console `StickySaveBar` on the bento branch); `turing-bento-ui` skill documents it. → CHANGELOG Block AX / §XLVII
- ✅ **T736** (deps: T735) **Close the 3 morph gaps** — routed the bento forms that showed a static (non-fading) Save row through the morph: `bento.sn.synonym.page.tsx` (lone floating bar → `BentoFormHero`), `bento.se.core.new` (reused `se.instance.core.new` swapped its `header` prop for a `chrome` flag rendering `BentoFormHero` in-form), and `ai-agent.system-prompt.form.tsx` (static `justify-end` → the two-piece `BentoSaveBar` + `BentoScrollSaveBar` its sibling `ai-agent.settings.form` uses, since the shared `BentoAgentSubHero` owns the hero). Cosmetic only; console paths unchanged. → CHANGELOG Block AX / §XLVII
- ✅ **T737** (deps: T735) **Back-arrow breadcrumb across own-hero bento forms** — added the `←` affordance systematically to every back-link eyebrow the §XLVII audit flagged (~16) + `bento.persona.suggest`: one edit to the shared `bento.ai-agent.sub-hero.tsx` (all agent sub-pages) and one to `bento.sn.section-list.tsx` (all 5 SN section lists), plus SN custom-facet.item/field.coverage/facet.list/field.list/insights/locale.list/synonym.list/top-terms/thesaurus, admin user/group/role, se cores/system-info, store collection-import/system-info. (`bento.se.core.new` + the synonym editor got theirs from T736 `BentoFormHero.backTo`.) Additive/cosmetic. → CHANGELOG Block AX / §XLVII
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
