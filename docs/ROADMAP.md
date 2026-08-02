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

## Block BQ — The answer engine as a first-class search lens (`/answer`) (§LXIV)

> Block BO built the answer engine and gave it exactly **one** surface: an
> expand/collapse panel inside the *visitor* search page (`_aimode`, T835). The
> console's "Live search" bar still offers two lenses — Open Search (`/sn/{name}`)
> and ANN (`/ann/{name}`) — so the engine an operator most needs to inspect is the
> one with no operator surface. Three consequences: (1) the ANN tile's gate is
> strictly *narrower* than the answer endpoint's, so a `VECTORLESS_STRUCTURED` site
> that answers perfectly well shows **no** AI lens at all in the console; (2) the
> visitor panel deliberately foregrounds prose, while the things you need when an
> answer is wrong — T839 provenance, T843 trace, per-citation `rankingExplanation`
> — are collapsed or absent; (3) there is no way to run one query across keyword,
> vector and answer to localize a bad result to **recall vs rerank vs synthesis**.
> `/answer` becomes the third peer route, operator-flavoured the way `/ann` already
> is. **No new backend** — Block BO shipped the whole engine. Design in
> [IMPROVEMENTS.md](IMPROVEMENTS.md) §LXIV.

- 📋 **T852** (deps: T833, T837) **`/answer/{siteName}` — the third search route** — new peer route alongside `/sn` and `/ann` (`ROUTES.ANSWER_SEARCH` + a lazy page in `App.tsx`), answer-first: an ask box over `TuringAnswer` from `@viglet/turing-react-sdk` driving the shipped `GET /api/sn/{site}/answer` (streamed prose, `[n]` anchors, source cards, follow-up chips). Query, locale and `fq` filters live in the URL so an answer is linkable and reproducible. No backend change. → §LXIV.1
- 📋 **T853** (deps: T852) **Third launcher tile in the console, with its own gate** — `buildSnSearchLaunchers` gains an `answer` launcher next to `search`/`ann`, gated by the shipped `GET /sn/{site}/chat/enabled` readiness probe (which already handles the vectorless path) instead of the frontend-derived `annEnabled`, so a `VECTORLESS_STRUCTURED` site finally gets the AI lens ANN can never give it — and the probe's `reason` renders a precise "why not" tooltip when it's off. Per-locale dropdown like the other two tiles. → §LXIV.2
- 📋 **T854** (deps: T852) **The operator page shows its work by default** — what separates `/answer` from the visitor panel: T839 provenance expanded (KB mode, rerank strategy, per-source `rankingExplanation`), the T843 research trace, rank + score on each source card, and the raw `JsonSheet` view `/ann` already ships. Makes "this answer is wrong" debuggable in the console instead of in the logs. → §LXIV.3
- 💭 **T855** (deps: T852, T853) **Cross-lens compare — same query, three lenses** — a switcher that carries `q`, locale and filters between `/sn`, `/ann` and `/answer` (and, promoted further, a side-by-side view) so a bad answer is attributed to recall, rerank or synthesis in one pass. Promote only if operators actually triage this way. → §LXIV.4

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

> **Phases 1–3 shipped (T707–T711)**, plus **T712** — the anonymous
> `POST /api/sn/{site}/search/query` JSON contract, the opt-in link-free
> `STRUCTURED` response, both JS SDKs driving them, the parity guard +
> anonymous-demo confirmation, and the `/ac` + `/list` POST variants that make
> the whole public read surface POST-capable. See
> [CHANGELOG.md](CHANGELOG.md) → Block AV. Remaining (all 💭 — promote when a
> consumer asks):

- 💭 **T713** (deps: T709, T710, T712) **Deprecate embedded href/link fields behind the format flag** — once both SDKs are POST-native, mark `link`/`href`/`cleanUpFacets`/spell-check `link` legacy (kept for no-JS/HTML/SEO/deep-linking) and drop the query-string round-trip helpers from the SDKs. → §XLV
- 💭 **T714** (deps: T709, T710) **Docs + showcase + EDS POST-native** — update `rest-api.md`/`javascript-sdk.md`, the Atlas Store showcase and the EDS example blocks to the POST-native flow. → §XLV

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
