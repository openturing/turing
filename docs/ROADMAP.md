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

## Block BJ — Configurable, per-language NL ranking lexicon (Lucene analysis) (§LVIII)

> ⏸ **ON HOLD — Block BK has shipped.** `LLM_ASSISTED`/`HYBRID` (Block BK, §LIX)
> now handle other languages natively, so a per-language *deterministic* lexicon is
> no longer needed for i18n. Keep this block as the design for the deterministic
> multilingual path; only promote its tasks from 💭 if a deployment wants
> deterministic multilingual ranking *in addition to* the LLM path — i.e. it needs
> non-English ranking on the free, CI-testable `DETERMINISTIC` fast-path (which is
> also what `HYBRID` runs first, so a non-English lexicon would reduce how often
> `HYBRID` has to escalate).
>
> The T811 ranking planner works, but its knowledge — the ascending/descending
> superlative words, the adjective→concept synonyms, the stopwords — is a **hardcoded
> English word list in `TurNLRankingPlanner`**. That doesn't scale: it can't be tuned
> per deployment/domain and it only understands English (a `pt` user asking "modelo
> mais barato" or "ordenar por preço" gets no sort). Make the lexicon **data-driven
> and per-language**, and replace the hand-rolled tokenizer/stemmer with **Lucene's
> analysis chain** (already a dependency: per-language stemmers, stopword sets,
> `SynonymGraphFilter`) so adding a language or a domain vocabulary is a **resource,
> not code**, and inflected forms collapse without enumerating every variant. Stays
> deterministic + fail-open (never reintroduces the LLM). Design in
> [IMPROVEMENTS.md](IMPROVEMENTS.md) §LVIII.

- 💭 **T814** (deps: T811) **Externalize the ranking lexicon** — move `ASC_WORDS`/`DESC_WORDS`/`SYNONYMS`/`STOPWORDS` out of `TurNLRankingPlanner` into a resource-backed `TurRankingLexicon` (default `en` bundle under `resources/`) loaded by a `TurRankingLexiconProvider`; the planner consumes the lexicon with no hardcoded sets. English parity — no behaviour change — but now data-driven. Foundation for the rest of the block. → §LVIII.1
- 💭 **T815** (deps: T814) **Lucene analysis backbone** — replace the hand-rolled `tokenize`/`expand`/stopword logic with a per-locale Lucene `Analyzer` (StandardTokenizer + LowerCaseFilter + per-language `StopFilter` + Snowball stemmer + `SynonymGraphFilter` built from the T814 synonym map), applied to BOTH the query and each declared field's name + description so inflected/variant forms collapse (cheapest/cheaper/cheap; "mais barato"/"barato") without enumerating them. Reuses `org.apache.lucene.analysis.*` (already on the classpath); no new dependency. → §LVIII.2
- 💭 **T816** (deps: T814, T815) **Per-language lexicons + locale resolution** — resolve the lexicon + analyzer by the copilot's request/site locale (registry locale→bundle, fallback `en`); ship `pt` (and `es`) bundles ("mais barato"/"menor"→asc, "mais inteligente"/"maior"→desc, "ordenar por"/"ordenado por" triggers); add `turing.genai.copilot.ranking.lexicon-dir` so a deployment can point at an external lexicon dir and add/tune languages without a rebuild. → §LVIII.3
- 💭 **T817** (deps: T816) **Per-site / admin-editable ranking lexicon overrides** — let an SN site (or global settings) extend the direction/synonym lexicon with domain-specific terms, editable in admin and persisted, merged over the resource defaults. Promote from 💭 only if a deployment needs domain-specific ranking vocabulary beyond the shipped bundles. → §LVIII.4

## Block BN — Content-fit tuning loop: model-aware, enforceable, and it tells you (§LXI)

> Block AT closed with T704 — Persona Match now remembers how every
> `(content, persona)` pair's fit moved across runs, surfaces drift, and suggests
> adjustments on both sides. Building it surfaced gaps that stop the loop from
> actually *closing* in an operator's day: (1) the per-project **Model (LLM)** picker
> drives only the T704 suggestions — `TurPersonaContentFitEvaluator` still resolves
> the global default LLM, so the score the whole feature rests on ignores the setting;
> (2) the "good fit" bar is a **hardcoded 75** in both the drift service and the
> heatmap bands, so a project cannot set its own standard; (3) drift is **only visible
> if a human opens the studio** — a DAILY project that regresses at 03:30 tells nobody;
> (4) the loop ends at advice — nothing enforces fit in CI, and nothing can apply a
> suggestion; and (5) both verdicts are still parsed from **prompt-embedded JSON**,
> which the Non-goals say is not the path. Design in
> [IMPROVEMENTS.md](IMPROVEMENTS.md) §LXI.
>
> **All five gaps are fixed** — T825–T830 shipped, see
> [CHANGELOG.md](CHANGELOG.md) Block BN. The loop is model-aware, calibrated,
> noticed, enforceable, provider-schema-bound and reportable per target. Only the
> 💭 apply action (T831) remains — promote it if an operator asks for it.

- 💭 **T831** (deps: T704) **Apply a persona adjustment (human-confirmed)** — T704 is advisory by design, so every accepted suggestion is still retyped by hand into the persona form. For **PERSONA** items only, add an opt-in *Apply* action that renders the suggestion as a field-level diff over the audience facet (`readingLevel` / `domainExpertise` / `vocabularyCeiling` / `comprehensionNotes`) and writes it only after the operator confirms each field, stamping what was applied so the next run's drift is attributable. CONTENT items stay advisory forever — Turing does not own the CMS the content lives in. → §LXI.7

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

## Block BO — Answer-first search: the steerable, auditable answer engine (§LXII)

> A product review asked whether Turing could return search results "like Perplexity".
> The audit found Turing already owns nearly every **engine** piece — hybrid RRF +
> 8 rerank strategies, `TurCatalogCopilotResult` (`answer` + footnote-numbered
> `citations` + `totalHits`, with the answer's `[n]` markers rewritten to match, T800),
> `TurRagSource` `sources[]` SSE, per-sentence `TurChatCitation` answer spans, citation
> drift detection, and `TurLiveAnswersService` fusing KB + `web_search` + `web_fetch`
> — and none of the **surface**. Four gaps: (1) AI Mode is *exclusive*
> (`search-content.tsx` → `showAiMode ? <AiModeChat/> : <SearchResultsPanel/>`), so
> turning AI on costs the user results, facets and pagination; (2) three incompatible
> citation contracts, with the T800 footnote renumbering wired **only** to the catalog
> path so the main RAG answer ships unnumbered chips and inert `[n]` prose; (3) no
> single streaming answer endpoint (`/copilot` is POST→JSON); (4) no follow-ups, no
> research trace, one retrieval pass.
>
> **The bet is not a Perplexity clone.** Perplexity's moat is web-index breadth —
> unwinnable and uninteresting. Turing's answer sits on a real faceted index with an
> auditable ranking pipeline, which buys three things Perplexity structurally cannot
> offer: an answer you can **steer** (T838), **audit** (T839), and one that knows it
> has **decayed** (T840). Phases 1–2 are table stakes; 3–4 are the differentiation.
> Positioning in [STRATEGY.md](STRATEGY.md) §XV; design in
> [IMPROVEMENTS.md](IMPROVEMENTS.md) §LXII.

> **Phase 1 (contract & transport) shipped** — T832–T834, see
> [CHANGELOG.md](CHANGELOG.md) Block BO.

**Phase 2 — the fused frontend:**
- 📋 **T835** (deps: T833) **Answer-first fused search page** — kill the exclusive toggle: the answer panel renders **above** `SearchResultsPanel` with the facet sidebar, pagination and result cards mounted throughout; `_aimode` becomes an expand/collapse preference, not a mode switch. Highest-leverage change in the block. → §LXII.4
- 📋 **T836** (deps: T832, T835) **Inline `[n]` citation anchors** — rehype plugin for `TuringMarkdown` turning each `[n]` into a clickable superscript that scroll-links + highlights the matching source card, with a cited-passage popover; on the native path the T154 `answerStart`/`answerEnd` offsets underline the exact grounded span. → §LXII.5
- 📋 **T837** (deps: T833, T835) **`useTuringAnswer` + `TuringAnswer` in the react-sdk** — headless hook + design-agnostic component on the `TuringCopilot`/`TuringSourceChips` skinning contract, so the admin search page, the showcase and the public-site hero share one implementation instead of three forks; observable answer controller ported to the vanilla `@viglet/turing-sdk`. → §LXII.6

**Phase 3 — the differentiators (the repositioning):**
- 📋 **T838** (deps: T835) **Steerable answers — facets as answer controls** — synthesize the answer **within the active filter set** and say so with removable constraint chips ("answering within: Vendor = Anthropic · Context ≥ 200k"); turning a facet re-runs the answer against the narrowed candidates. Mechanically small, but it is the block's flagship claim — a Perplexity answer has no filter state to turn — and it converts the classic answer-engine failure (confident answer over the wrong subset) into a visible control. → §LXII.7
- 📋 **T839** (deps: T835) **"Why this answer" — auditable provenance panel** — one expandable panel surfacing the T389 `rankingExplanation` already attached to hybrid results (lexical / semantic / fused rank, RRF score) plus the rerank strategy, KB mode, candidate count and dropped chunks; promotes the per-chip "why did you say this?" to an answer-level audit trail. The feature a regulated buyer asks for first. → §LXII.8
- 💭 **T840** (deps: T835) **Answer freshness & drift badge** — wire the shipped `TurCitationDriftService` + detection job to the answer surface: a badge when a cited source changed after the answer was produced, plus one-click re-answer. Turing already computes the signal and only reports it internally; no answer engine on the market models an answer as something that decays. → §LXII.9
- 💭 **T841** (deps: T835, T837) **Structured answers — answer as an artifact, not prose** — for comparative/enumerable questions over a faceted or `VECTORLESS_STRUCTURED` catalog, render a comparison table / spec grid from the declared field manifest with per-cell citations and prose reduced to a lead-in, reusing the shipped answer-as-app + generative-UI path (`TurAnswerAsAppClientTools`, `useGenerativeUI`) rather than adding a renderer. → §LXII.10

**Phase 4 — depth & distribution:**
- 💭 **T842** (deps: T833) **Multi-query fan-out** — decompose one question into parallel sub-queries behind the existing `TurCopilotQueryPlanner` seam, bounded by `turing.genai.copilot.planning.max-passes`, unioning recall through `TurRagRrf` before rerank. Perplexity's multi-hop recall advantage is mostly fan-out, not model quality. Fail-open to single-pass; off by default for `DETERMINISTIC` sites. → §LXII.11
- 💭 **T843** (deps: T833, T835) **Live research trace** — frame the `tool_call` SSE events the runtime already emits as a collapsible "searched *X* · read *N* sources · reranked with Cohere · answered" timeline via a `trace` event. Pure presentation over shipped plumbing; the cheapest way to make a multi-second answer feel deliberate rather than slow. → §LXII.12
- 💭 **T844** (deps: T835, T838) **Answer permalinks & shareable answer cards** — a stable URL for an answer (query + filter state + source snapshot + timestamp) that re-renders it read-only, with an OG card via the shipped `TurChatShareOgService`; makes every good answer a linkable artifact and, on public deployments, indexable content. Pairs with Block O (GEO). → §LXII.13
- 💭 **T845** (deps: T838, T843) **Answer-level analytics & the self-tuning loop** — emit citation-clicked / facet-refined-after-reading / follow-up-taken / re-answer signals into the existing chat analytics, keyed for the `selftuning` miner+drafter. T838's steerability produces an unusually clean relevance signal: narrowing a facet *after* reading an answer is explicit feedback that retrieval missed. → §LXII.14

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
