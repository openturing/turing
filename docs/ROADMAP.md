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

- 💭 **T441** (deps: T436 ✅, T438 ✅) **Canonical AG-UI protocol adapter** — an optional endpoint re-encoding Turing's stream into the canonical AG-UI event schema, so CopilotKit's off-the-shelf clients connect with no Turing-SDK code. → §XXII.6

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

- 💭 **T814** (deps: T811 ✅) **Externalize the ranking lexicon** — move the four hardcoded word sets out of `TurNLRankingPlanner` into a resource-backed `TurRankingLexicon` — English parity, but data-driven and the foundation for the block. → §LVIII.1
- 💭 **T815** (deps: T814) **Lucene analysis backbone** — replace the hand-rolled tokenize/expand/stopword logic with a per-locale Lucene `Analyzer` over both the query and each field, so inflected forms collapse without enumerating them. → §LVIII.2
- 💭 **T816** (deps: T814, T815) **Per-language lexicons + locale resolution** — resolve lexicon and analyzer by request/site locale with an `en` fallback, ship `pt`/`es` bundles, and add a lexicon-dir property so a deployment tunes languages without a rebuild. → §LVIII.3
- 💭 **T817** (deps: T816) **Per-site / admin-editable ranking lexicon overrides** — let a site or global settings extend the direction/synonym lexicon with domain terms, edited in admin and merged over the resource defaults. → §LVIII.4

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

- 💭 **T831** (deps: T704 ✅) **Apply a persona adjustment (human-confirmed)** — for PERSONA items only, an opt-in Apply action renders the suggestion as a field-level diff over the audience facet and writes it only after the operator confirms each field. → §LXI.7

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

- 📋 **T852** (deps: T833 ✅, T837 ✅) **`/answer/{siteName}` — the third search route** — a peer route beside `/sn` and `/ann`, answer-first over `TuringAnswer` driving the shipped answer endpoint, with query, locale and filters in the URL so an answer is linkable. → §LXIV.1
- 📋 **T853** (deps: T852) **Third launcher tile in the console, with its own gate** — `buildSnSearchLaunchers` gains an `answer` tile gated by the readiness probe rather than the frontend-derived `annEnabled`, so a `VECTORLESS_STRUCTURED` site finally gets the AI lens. → §LXIV.2
- 📋 **T854** (deps: T852) **The operator page shows its work by default** — turn the reader defaults inside out on this page — provenance expanded, the research trace, rank and score per source, the raw `JsonSheet` — so a wrong answer is debuggable in the console. → §LXIV.3
- 💭 **T855** (deps: T852, T853) **Cross-lens compare — same query, three lenses** — a switcher carrying `q`, locale and filters between `/sn`, `/ann` and `/answer`, so a bad answer is attributed to recall, rerank or synthesis in one pass. → §LXIV.4

## Block J follow-ups — Cloud SaaS hardening (T334–T373)

> Surfaced wiring Block J (multi-tenancy, shipped — see [CHANGELOG.md](CHANGELOG.md)
> → Block J and [MULTI_TENANCY.md](MULTI_TENANCY.md)) into Viglet Cloud (one tenant
> per signed-up user on shared turing/solr/postgres infra). These are the gaps that
> remain for a *seamless* per-user SaaS; design in [IMPROVEMENTS.md](IMPROVEMENTS.md) §XIV.8.

- 📋 **T373** (deps: —) **Ship Cloud default config as a GLOBAL seed ZIP** — guarantee the export keeps `tenantId = null` for the 6 BYO-infra types and bake the seed ZIP into the image, so one seeded default is visible to every auto-provisioned tenant. → §XIV.8.7

## Block O — GEO / LLM discoverability & content distribution

> Make LLM assistants (ChatGPT/Claude/Gemini/Copilot) suggest Turing for
> "search for Adobe AEM / WordPress". Execution of [STRATEGY.md](STRATEGY.md)
> §IX.4; full memo + channel split + post calendar in §X. **Not engineering** —
> these are content/marketing/SEO tasks; some live in sibling repos
> (`openviglet.github.io`, `vigletdocs.github.io`). Sequence: foundation
> (T342) → off-domain signal (T346, T354 — strongest lever) → flagship content
> (T347, T350) → the rest. `→` pointers are STRATEGY §X.

**Content — integration how-tos (cluster A):**
- 💭 **T348** (deps: T347 ✅) **Post: "Semantic & vector search in AEM with Turing ES"** — RAG/embedding angle on the same audience. → §X.3
- 💭 **T349** (deps: T342 ✅) **Post: "Adding enterprise search to WordPress with Turing ES"** — the other named CMS; second-largest connector audience. → §X.3

**Content — comparison / "versus" (cluster B, rides competitor volume):**
- 💭 **T351** (deps: T350 ✅) **Post: "Turing ES vs. Coveo / vs. Lucidworks Fusion"** — head-to-head positioning for enterprise portals. → §X.3
- 💭 **T352** (deps: T350 ✅) **Post: "When to choose Solr, Elasticsearch, or Turing ES"** — captures the "which search engine" decision query. → §X.3

**Content — concept / moat (cluster C):**
- 💭 **T353** (deps: T342 ✅) **Post: "RAG over enterprise content with Turing + MCP"** — ties the §VIII.4 MCP pillar + Block M provenance into the discovery funnel. → §X.3

**Off-domain footprint (the strongest lever — X.1 #1):**
- 📋 **T354** (deps: —) **Seed independent mentions** — answer relevant SO/Reddit/AEM-forum threads, republish the posts to Dev.to/Medium behind a canonical link, and list on AlternativeTo, `awesome-*` and G2. → §X.1
- 💭 **T355** (deps: T354) **Monthly model-prompt audit** — prompt ChatGPT/Claude/Gemini with the target queries, log whether Turing appears and where; the actual KPI for this block. → §X.4

## Block AB — Public site & top-of-funnel conversion (`turing.viglet.org`)

> The marketing site as a conversion surface, not a brochure. Positioning bet in
> [STRATEGY.md](STRATEGY.md) §XIV; build rationale in [IMPROVEMENTS.md](IMPROVEMENTS.md) §XXVII.
>
> **Phase 0 shipped (T473–T476)** — the `frontend/apps/site` app, analogy hero
> + pillars + comparison + sample-data live demo + quickstart + CLI section. See
> [CHANGELOG.md](CHANGELOG.md) → Block AB. Remaining (the defining bets):

- ⏳ **T477** (deps: T451–T457 ✅) **Public read-only demo instance behind the site** — infra, seeding and the LLM are live at `turing-demo.viglet.org`, so what is left is the host setting the demo agent's budget cap. → §XXVII.2
- 💭 **T480** (deps: T477) **Hosted "try it free" path via Viglet Cloud** — dual-path CTA (self-host docs + hosted sandbox) so visitors who won't install still reach first value. → §XXVII.3
- 💭 **T481** (deps: real design partners) **Social-proof surface** — adopters, logos, testimonials and quantified case studies on the Dify model, never fabricated. → §XXVII.3

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

> **✅ Block AV fully shipped (T707–T714).** The anonymous
> `POST /api/sn/{site}/search/query` JSON contract, the opt-in link-free
> `STRUCTURED` response, both JS SDKs driving them, the parity guard +
> anonymous-demo confirmation, the `/ac` + `/list` POST variants, the
> docs/showcase/EDS migration, and the href round-trip put on notice. See
> [CHANGELOG.md](CHANGELOG.md) → Block AV. **The removal of
> `parseHrefToParams` / `navigate(href)` / `onNavigate` is deliberately left to
> the next major** — T713 deprecates, it does not delete.

## Block BR — Block AV follow-through: the gaps the POST-native migration left (§LXV)

> Block AV shipped the POST-native, link-free search contract (T707–T714). Building
> it surfaced five things that are **not** part of that contract but were exposed
> by it: (1) §XLV.5 settled the negotiation mechanism on a *request field*
> precisely because "it works over both GET and POST" — but only the POST body
> reads it, so the documented A/B story doesn't exist; (2) a POST-native client
> needs **two** round-trips to paint a sort dropdown, because the structured
> response deliberately points at `/search/sort-options` instead of duplicating it;
> (3) `TurSNUtils.getCacheKey(site, request)` keys on `request.getQueryString()`,
> so **every** POST endpoint has to remember to supply its own key — T707 and T712
> each hit this separately, which makes it a footgun in the primitive, not two
> isolated mistakes; (4) the whole Block AV backend has **no end-to-end coverage** —
> `TurSdkContractIT` POSTs only to chat, and a mocked unit suite is exactly what
> failed to catch the cache-key collision; (5) `EnrichedFacetItem.link` is typed
> `string` but set to `""` in structured mode, so the type lies to consumers.
> Design in [IMPROVEMENTS.md](IMPROVEMENTS.md) §LXV.

> **✅ Block BR fully shipped (T856–T860 + T864).** `responseFormat` and
> `includeSortOptions` work on both transports, the cache key is derived from the
> search context, the POST/structured surface has end-to-end coverage — which
> found the last link leak on its first run — and both `EnrichedFacetItem.link`
> and the per-document facet chips now say what is true. See
> [CHANGELOG.md](CHANGELOG.md) → Block BR.

## Block BS — The JS SDK release gate is not a gate (§LXVI)

> Three findings from the Block AV work, none of them about search: the vanilla
> SDK's `test` script **cannot run** (`"test": "vitest run"` with no `vitest`
> devDependency — `pnpm --filter @viglet/turing-sdk test` fails with "vitest not
> found"); its suite is **red at rest** (7 failures in `analytics-abandonment` +
> `analytics-conversion`, unrelated to search and present before Block AV); and its
> gzip budget was **already exceeded before Block AV** (16.22 kB against a 15.63 kB
> ceiling), which means `prepublishOnly` would have blocked a publish over a stale
> number rather than real bloat. Individually small; together they mean the package
> most external consumers depend on has no working quality gate. Design in
> [IMPROVEMENTS.md](IMPROVEMENTS.md) §LXVI.

> **✅ Block BS fully shipped (T861–T863).** The vanilla SDK declares `vitest`
> and has its own `vitest.config.ts`, `validate-js.yml` runs every workspace's
> tests (it ran none before) and now triggers on PRs, the 7 red tests were a
> missing jsdom rather than timer drift and the suite is green with the SSR path
> newly covered, and the size budget guards a committed baseline's delta instead
> of an absolute ceiling — which found react-ui *and* react-sdk already over
> theirs. See [CHANGELOG.md](CHANGELOG.md) → Block BS.

## Block BT — The POST-native default left its consumers behind (§LXVII)

> T710 made `postNative: true` the **default** for the React SDK's search
> provider, and POST-native the provider sets `data = null` and fills
> `structured` instead. Three consumers in `turing-app` were never migrated off
> `store.data`, and the worst of them gates a whole page: `SearchContent`
> returns `<SearchError>` when `data` is falsy, so `/sn/{siteName}` — the T835
> answer-first search page — renders its error state on **every** search since
> T710 shipped. The frontend tests do not catch it because they mock the store
> with `data` populated: a fixture that can no longer occur in production, the
> same blind spot that let T864's link leak through T708's empty-bean assertion.
> Found while shipping T860/T864. Design in
> [IMPROVEMENTS.md](IMPROVEMENTS.md) §LXVII.

> **✅ Block BT fully shipped (T865–T869).** The page reads one projection
> (`search/lib/search-view.ts`) instead of three components each reaching into
> `store.data`, so `/sn/{siteName}` renders results rather than `SearchError`, the
> applied-filters row and facet checkboxes are back, and a result's facet chip
> filters the search again through the same `toggleFacet` the rail uses. A
> document's chip now carries `selected` + `action` inherited from the matching
> facet item, and the new suite mounts the real provider against a real
> `STRUCTURED` payload — the mocked-store fixture that hid all three bugs cannot
> hide the next one. See [CHANGELOG.md](CHANGELOG.md) → Block BT.

## Block BU — The search cache is off by a thousand and never evicted on write (§LXVIII)

> Read while deriving the T858 cache key. Two defects and one cohesion problem in
> `TurSNSiteSearchCachedAPI`, all of them latent because the cache is off by
> default (`turing.search.cache.enabled: false`) — which is also why nobody has
> hit them. Design in [IMPROVEMENTS.md](IMPROVEMENTS.md) §LXVIII.

- 📋 **T870** (deps: T858 ✅) **Indexing does not evict the search cache** — the only eviction is a scheduled `allEntries` sweep, so a reindexed document keeps serving its previous result for up to 24h — evict per site at index, deindex and commit. → §LXVIII.1
- 📋 **T871** (deps: —) **`turing.search.cache.ttl.seconds` is milliseconds** — it feeds `@Scheduled(fixedRateString = …)`, whose unit is ms, so an operator setting `3600` for hourly gets a sweep every 3.6 seconds — accept a `Duration` so the unit is stated. → §LXVIII.2
- 💭 **T872** (deps: —) **`TurSNSiteSearchCachedAPI` is two classes wearing one name** — the `@Cacheable` search seam and the MongoDB logging config share a bean, so the cache primitive carries seven Mongo constructor parameters it never uses. → §LXVIII.3

## Block BV — The quality gate stops at the package boundary (§LXIX)

> Found while shipping Block BS. Every gate in `frontend/` is written **per
> package** — `validate-js.yml`'s matrix is a hand-maintained list, each
> `prepublishOnly` is its own, and the four publish workflows were copied from
> each other and then diverged — so a package added later inherits nothing and
> nothing enumerates the workspace to check. Applying T861's two steps (declare
> the runner, then confirm CI runs it) to the rest of the workspace turned up the
> same shape five more times, including one live bug. Design in
> [IMPROVEMENTS.md](IMPROVEMENTS.md) §LXIX.

- 📋 **T873** (deps: T861 ✅) **Two published packages are in no validation matrix** — both are public npm packages with real `node --test` suites listed in no validation matrix, so a break is caught at release time by whoever is shipping. → §LXIX.1
- 📋 **T874** (deps: T861 ✅) **The admin app's 171 tests run in no workflow** — `turing-app`'s vitest suite is the largest in the repo and executes in no CI job at all, so the suite T868 is about to invest in is one nobody runs. → §LXIX.2
- 📋 **T875** (deps: —) **`publish-js-sdk.yml` builds the wrong package** — the step named for react-sdk runs a filter on the vanilla SDK, so react-sdk is built only as a side effect of its own `prepublishOnly`. → §LXIX.3
- 📋 **T876** (deps: T875) **The vanilla SDK has no publish workflow** — `@viglet/turing-sdk` is released by hand while react-ui, cli and flow-dsl each have a workflow, and the react-sdk release patch-bumps every workspace so unpublished packages drift from npm. → §LXIX.4
- 📋 **T877** (deps: T861 ✅) **The most-consumed package ships zero tests** — `@viglet/turing-react-sdk` has no `test` script and no test files, so T861's `--if-present` correctly-but-unhelpfully skips the most-consumed package. → §LXIX.5
- 💭 **T878** (deps: T863 ✅) **A total-only baseline cannot localize a regression** — react-sdk is `strict: false`, so its baseline is one number that can say the package grew 4 kB but never which file — baseline per directory instead. → §LXIX.6

## Block BW — What the link-free response and its consumers still lack (§LXX)

> Found while shipping Block BT. That block fixed the three consumers reading
> `store.data`; it did not fix the **shape** they were reading it for, nor the
> **reach** of the fix. Two fields the legacy `widget` carried have no structured
> equivalent (one of them a silent user-visible regression), two the mapper sets
> are undeclared in both SDKs, the projection T865 wrote to end the mistake lives
> in a single app while the vanilla SDK and react-ui have the same problem, and
> the react-sdk README still documents the shape that broke. Design in
> [IMPROVEMENTS.md](IMPROVEMENTS.md) §LXX.

- 📋 **T879** (deps: T865 ✅) **A site's icon is silently dropped POST-native** — `TurSNStructuredSearchBean` has no field for the icon, so every site that configured one renders the generic Turing logo and T865 had to hardcode `siteIcon: null`. → §LXX.1
- 📋 **T880** (deps: T709 ✅) **`similar` and `spotlights` are written by the mapper and invisible to both SDKs** — `toStructured` sets both, yet `TurStructuredSearchResponse` declares no `similar` and types `spotlights` as `unknown[]`, so a typed consumer cannot reach either. → §LXX.2
- 📋 **T881** (deps: T865 ✅) **The two-shape projection belongs in the SDK, not in one app** — `search/lib/search-view.ts` answers how to read a response without caring which shape it is, and is reachable only from `turing-app` while both SDKs have the identical problem. → §LXX.3
- 📋 **T882** (deps: T867 ✅) **`TuringResultList`'s chip is the T867 bug, still shipping** — the headless package narrows `metadata` on `"href" in chip` but renders only `text`, so a viglet.com consumer gets exactly the dead badge T867 removed from the admin app. → §LXX.4
- 📋 **T883** (deps: T710 ✅) **The react-sdk README still teaches the shape that broke** — it shows `data.widget.facet.map(…)` as the way to render facets, which is `null` in the default mode, so anyone following it writes T865 again on their first page. → §LXX.5
- 📋 **T884** (deps: T713 ✅) **`toggle_facet` cannot reach a secondary facet** — `findStructuredFacetItem` searches `structured.facets` only, so a secondary facet answers "Facet not found" and falls through to a legacy lookup that is primary-only too. → §LXX.6
- 💭 **T885** (deps: T868 ✅) **Nothing stops the fourth consumer** — T865–T867 were one mistake three times and T868 covers the page rather than the class, so the systemic form is a guard that nothing outside the projection reads `store.data`. → §LXX.7

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
