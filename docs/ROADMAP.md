# Turing — Roadmap (active backlog)

> **Written by [roadkeep](../roadkeep.toml), never by hand.** Open work only, one line per
> task; the `→ §` pointer is that task's design in [IMPROVEMENTS.md](IMPROVEMENTS.md).
> Shipped work lives in [CHANGELOG.md](CHANGELOG.md) and positioning in
> [STRATEGY.md](STRATEGY.md); the markers and limits are `roadkeep.toml`'s. Blocks are
> reused, not minted — `roadkeep brief` picks the next task and states why.

## Block BJ — Configurable, per-language NL ranking lexicon (Lucene analysis) (§LVIII)

- 💭 **T814** (deps: T811 ✅) **Externalize the ranking lexicon** — move the four hardcoded word sets out of `TurNLRankingPlanner` into a resource-backed `TurRankingLexicon` — English parity, but data-driven and the foundation for the block. → §LVIII.1
- 💭 **T815** (deps: T814) **Lucene analysis backbone** — replace the hand-rolled tokenize/expand/stopword logic with a per-locale Lucene `Analyzer` over both the query and each field, so inflected forms collapse without enumerating them. → §LVIII.2
- 💭 **T816** (deps: T814, T815) **Per-language lexicons + locale resolution** — resolve lexicon and analyzer by request/site locale with an `en` fallback, ship `pt`/`es` bundles, and add a lexicon-dir property so a deployment tunes languages without a rebuild. → §LVIII.3
- 💭 **T817** (deps: T816) **Per-site / admin-editable ranking lexicon overrides** — let a site or global settings extend the direction/synonym lexicon with domain terms, edited in admin and merged over the resource defaults. → §LVIII.4

## Block BN — Content-fit tuning loop: model-aware, enforceable, and it tells you (§LXI)

- 💭 **T831** (deps: T704 ✅) **Apply a persona adjustment (human-confirmed)** — for PERSONA items only, an opt-in Apply action renders the suggestion as a field-level diff over the audience facet and writes it only after the operator confirms each field. → §LXI.7

## Block J follow-ups — Cloud SaaS hardening (T334–T373)

- 📋 **T373** (deps: —) **Ship Cloud default config as a GLOBAL seed ZIP** — guarantee the export keeps `tenantId = null` for the 6 BYO-infra types and bake the seed ZIP into the image, so one seeded default is visible to every auto-provisioned tenant. → §XIV.8.7

## Block BU — The search cache is off by a thousand and never evicted on write (§LXVIII)

- 💭 **T897** (deps: T870 ✅) **Per-site eviction has to reach outside the Spring `Cache` contract** — Enumerating a site's keys needs `getNativeCache()` to be a `Map`, so a provider that is not one silently degrades to clearing every site — a generation counter in the key would not. → §LXVIII.6
- 💭 **T905** (deps: T903 ✅) **The snapshot and field-config caches have no backstop sweep** — Their correctness rests entirely on eviction being right — the assumption that was wrong three times in this block — and a wrong entry survives until the next SN write. → §LXVIII.12

## Block BV — The quality gate stops at the package boundary (§LXIX)

- 📋 **T873** (deps: T861 ✅) **Two published packages are in no validation matrix** — both are public npm packages with real `node --test` suites listed in no validation matrix, so a break is caught at release time by whoever is shipping. → §LXIX.1
- 📋 **T874** (deps: T861 ✅) **The admin app's 171 tests run in no workflow** — `turing-app`'s vitest suite is the largest in the repo and executes in no CI job at all, so the suite T868 is about to invest in is one nobody runs. → §LXIX.2
- 📋 **T875** (deps: —) **`publish-js-sdk.yml` builds the wrong package** — the step named for react-sdk runs a filter on the vanilla SDK, so react-sdk is built only as a side effect of its own `prepublishOnly`. → §LXIX.3
- 📋 **T876** (deps: T875) **The vanilla SDK has no publish workflow** — `@viglet/turing-sdk` is released by hand while react-ui, cli and flow-dsl each have a workflow, and the react-sdk release patch-bumps every workspace so unpublished packages drift from npm. → §LXIX.4
- 📋 **T877** (deps: T861 ✅) **The most-consumed package ships zero tests** — `@viglet/turing-react-sdk` has no `test` script and no test files, so T861's `--if-present` correctly-but-unhelpfully skips the most-consumed package. → §LXIX.5
- 💭 **T878** (deps: T863 ✅) **A total-only baseline cannot localize a regression** — react-sdk is `strict: false`, so its baseline is one number that can say the package grew 4 kB but never which file — baseline per directory instead. → §LXIX.6
- 📋 **T892** (deps: —) **A Java enum and its hand-written TypeScript mirror drift with nothing watching** — `MISSING_DEFAULT_LLM` was in the backend enum and absent from the SDK union for eight months, narrowing the reason to `undefined` in every consumer. → §LXIX.7
- 📋 **T902** (deps: —) **Two governed prose files declare the same 13 section anchors** — STRATEGY.md and IMPROVEMENTS.md number sections from one outline, so a pointer to any of the 13 resolves to two sections and every verb that reads one refuses. → §LXIX.8

## Block BW — What the link-free response and its consumers still lack (§LXX)

- 📋 **T879** (deps: T865 ✅) **A site's icon is silently dropped POST-native** — `TurSNStructuredSearchBean` has no field for the icon, so every site that configured one renders the generic Turing logo and T865 had to hardcode `siteIcon: null`. → §LXX.1
- 📋 **T880** (deps: T709 ✅) **`similar` and `spotlights` are written by the mapper and invisible to both SDKs** — `toStructured` sets both, yet `TurStructuredSearchResponse` declares no `similar` and types `spotlights` as `unknown[]`, so a typed consumer cannot reach either. → §LXX.2
- 📋 **T881** (deps: T865 ✅) **The two-shape projection belongs in the SDK, not in one app** — `search/lib/search-view.ts` answers how to read a response without caring which shape it is, and is reachable only from `turing-app` while both SDKs have the identical problem. → §LXX.3
- 📋 **T882** (deps: T867 ✅) **`TuringResultList`'s chip is the T867 bug, still shipping** — the headless package narrows `metadata` on `"href" in chip` but renders only `text`, so a viglet.com consumer gets exactly the dead badge T867 removed from the admin app. → §LXX.4
- 📋 **T883** (deps: T710 ✅) **The react-sdk README still teaches the shape that broke** — it shows `data.widget.facet.map(…)` as the way to render facets, which is `null` in the default mode, so anyone following it writes T865 again on their first page. → §LXX.5
- 📋 **T884** (deps: T713 ✅) **`toggle_facet` cannot reach a secondary facet** — `findStructuredFacetItem` searches `structured.facets` only, so a secondary facet answers "Facet not found" and falls through to a legacy lookup that is primary-only too. → §LXX.6
- 💭 **T885** (deps: T868 ✅) **Nothing stops the fourth consumer** — T865–T867 were one mistake three times and T868 covers the page rather than the class, so the systemic form is a guard that nothing outside the projection reads `store.data`. → §LXX.7

## Block AN — Security hardening (post-audit, §XXXVII)

- 📋 **T893** (deps: —) **The public search page is not in the Keycloak branch's anonymous list** — `/sn/**` is permitted only under local auth, so a Keycloak deployment sends a visitor to SSO login for a page whose every API is already public. → §XXXVII.17
- 📋 **T894** (deps: —) **The permitAll list is written twice and nothing holds the copies equal** — forty matchers are spelled once per auth branch by hand, which is what produced both the missing answer endpoint and the missing search page. → §XXXVII.18

## Block AC — JPA caching strategy: decouple cache from entities

- 📋 **T898** (deps: —) **The search snapshot caches six kinds of JPA entity, which T486 forbids** — T486's lint only walks repositories, so a service-level `@Cacheable` holding entities passes — and this one already paid for it once with a workaround for the exact failure. → §LXXI.1

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
