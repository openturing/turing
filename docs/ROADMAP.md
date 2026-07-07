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

## Block AE — Documentation parity & didactic refresh (post-T410) — ✅ COMPLETE

> **Shipped (2026-06-30) — T524–T538.** Second documentation round: brought
> `docs-turing` to parity with everything shipped T424→T523 and added the
> zero→first-agent didactic layer. All task detail is in
> [CHANGELOG.md](CHANGELOG.md) → Block AE; the living coverage gate is
> `docs-turing/COVERAGE.md`. §XXIX design rationale removed on ship, per the
> founding rule.

## Block AF — Domain layer: bounded completion, stop-criteria & enforcement

> A hexagonal (ports & adapters) migration was started at 2026.2.6: ~50
> annotation-free `*Domain` records + 39 `*RepositoryPort` interfaces + 39
> MapStruct adapters that delegate to the existing JPA repositories. It is a
> **read-only pilot** (~18 app files consume ports; ~333 still use JPA repos
> directly; write paths untouched) with **no ADR**. **Decision: do NOT take it
> to 100%.** The hexagonal "swap the persistence" payoff is near-zero for this
> product; the real, bankable value is the *immutable, lazy-proxy-free,
> cacheable read model* — which pays off only on the hot/cached/cross-aggregate
> read paths we've already been burned on (the EAGER 200ms→5s incident; the
> "never cache a JPA entity" rule, Block AC / T486–T488). This block finishes
> that valuable slice, prunes the ceremony a 100% migration would have created,
> and makes the boundary self-enforcing. Design → [IMPROVEMENTS.md](IMPROVEMENTS.md) §XXX.

> **Shipped (2026-07-02) — T540–T546. Block AF complete.** The bounded domain
> layer landed: benchmark (T540) → ADR + stop-criteria (T541) → finished the
> keep slice (T542) → pruned scaffolding (T543) → read-only-forever Javadoc
> (T544) → enforcement lint (T545) → collapsed the one pure-ceremony port to a
> mapper-backed reader (T546). End state: **7 ports + 8 records + 1 reader**,
> self-policed by `TurDomainLayerConventionsTest`. Detail in
> [CHANGELOG.md](CHANGELOG.md) → Block AF; §XXX design rationale removed on ship
> per the founding rule.

## Block AG — Bento UI: complete the interface migration (100%)

> A Bento (iOS-flavored frosted-glass mosaic) redesign of the admin was started
> and lives **in parallel** at `/bento` under a "Preview" badge — only 3 surfaces
> exist so far: **home**, **AI Agent**, **LLM instance** (list + detail). The
> detail pattern is settled and reusable: a `BentoHero` with inline-edit identity
> fields (title/description/status/icon), a scroll-linked hero→sticky-`BentoSaveBar`
> morph, a `⋮` `BentoActionsMenu` for destructive actions, and `BentoFormSection`
> wrappers that **reuse the existing react-hook-form fields unchanged** — so each
> remaining screen is mostly a re-skin, not a rewrite. This block takes it to
> **100%**: migrate every console CRUD/functional surface, make `/bento` the
> default interface, and retire the legacy console. Goal per the ask: fluid,
> concise, friendly. Design + full screen inventory → [IMPROVEMENTS.md](IMPROVEMENTS.md) §XXXI.

**Phase 0 — foundation (gate the rest):**
- ✅ **T547** — Migration architecture & cutover strategy (ADR `0002`): Bento-as-primary, route-swap + `<Navigate>` redirects (no runtime flag), closed surface inventory. → CHANGELOG Block AG.
- ✅ **T548** — Shared Bento CRUD scaffold (`BentoEntityShell` + `BentoListPage` + `BentoEntityTile`); LLM + AI-Agent surfaces re-skinned onto it as proof. → CHANGELOG Block AG.
- ✅ **T549** — Global Bento nav (`BentoNavRail`) + zero-dep `⌘K` `BentoCommandPalette`, both from one `bento-nav.config.ts` registry mirroring the console taxonomy. → CHANGELOG Block AG.
- ✅ **T550** — Bento conventions codified (`components/bento/CONVENTIONS.md`: i18n object-form defaults, a11y baseline + reduced-motion, responsive) + vitest coverage for the shared components. **Phase 0 complete.** → CHANGELOG Block AG.
- ✅ **T573** — Nav rail restructured to **hub-and-spoke**: rail lists Home + 3 section hubs (not the ~22 leaves) so it can't overflow the viewport as surfaces migrate; each hub is a shared `BentoAreaPage` mosaic; taxonomy unified into one `bento-nav.config.ts` (rail + hubs + home + `⌘K` all read it), retiring the home page's divergent `SECTIONS` array. → CHANGELOG Block AG.

**Phase 1 — BYO-infra & simple CRUD (reuse T548 scaffold, LLM pattern applies directly):**
- ✅ **T551** — Search Engine (SE) → bento (list + instance detail + form). BYO-infra read-only mode (T372 GLOBAL + Solr catalog lock) threaded through the shared shell. → CHANGELOG Block AG.
- ✅ **T552** — Embedding Store + Embedding Model → bento (two BYO-infra CRUDs; Embedding Model aliases `modelName`↔`title` at the page boundary). → CHANGELOG Block AG.
- ✅ **T553** — API Token + Integration → bento (Token drove a `hideIcon` shell option; new `apiToken` nav item). → CHANGELOG Block AG.
- ✅ **T554** — MCP Server + Custom Tool → bento (Custom Tool keeps its Groovy editor + params + Vibe-Coding inside the form body). → CHANGELOG Block AG.
- ✅ **T555** — Routine + Chat Webhook → bento (both alias `name`↔`title` + boolean↔numeric `enabled`, `hideIcon`). → CHANGELOG Block AG.
- ✅ **T556** — Persona + Git → bento (Persona reuses its multi-section editor via a `baseRoute` prop; Git re-skinned as a Bento repo/pipeline tool). **Phase 1 complete.** → CHANGELOG Block AG.

**Phase 2 — complex CRUD & editors:**
- ✅ **T557** — Semantic Navigation instance → bento (`app/bento/sn/`): list + hero-driven detail + config form (SE binding + search template); identity aliases title↔name (no status pill); deeper SN sub-sections reached from a config-sections panel until their own migration. **Phase 2 opens.** → CHANGELOG Block AG.
- ✅ **T558** — SN fields sub-CRUD + field-coverage → bento sub-routes (`sn/instance/:id/field[/:fieldId]`, `/field-coverage`): reuses `SNSiteFieldGridList` + `SNSiteFieldForm` (new `baseRoute` prop) + a shared `SNFieldCoveragePanel`. Manifest plan/diff lives in the onboarding/field wizards (→ T559). → CHANGELOG Block AG.
- ✅ **T559** — SN onboarding wizard → bento (`/bento/sn/onboarding`): the multi-step stepper reused via a new `baseRoute` prop so completion lands inside the shell; a "Guided onboarding" pill on the bento SN list surfaces it. → CHANGELOG Block AG.
- ✅ **T560** — Skill folder editor (mini VS Code) → bento shell (`app/bento/skill/`): bento list + the T318 editor reused via new `baseRoute` + `containerClassName` props (shell-aware height + frosted frame). → CHANGELOG Block AG.
- ✅ **T561** — Asset browser/manager → bento shell (`app/bento/asset/`): extracted a shared `AssetBrowser` (route-shell-agnostic, prefix-based folder nav) rendered under a `BentoHero`. → CHANGELOG Block AG.
- ✅ **T562** — Page CRUD → bento (`app/bento/page/`): ZIP-deployed-SPA grid (upload/open/delete) re-skinned onto a `BentoHero` + frosted tiles, reusing `TurPageService`. **Phase 2 complete.** → CHANGELOG Block AG.
- ✅ **T576** — SN instance sub-sections → bento (`app/bento/sn/`): the deep sections T557 deferred — locale/behavior/facet(+custom-facet/item/faceted-field)/custom-sort/search-rule/ai/result-ranking/merge-providers/spotlight/top-terms/insights. Reuses the console forms/lists verbatim via a new `baseRoute` prop + a shared `BentoSnSectionList` scaffold; the SN config-sections panel now routes every section into `/bento`. Completes the SN instance migration. → CHANGELOG Block AG.

**Phase 3 — functional & dashboard surfaces:**
- ✅ **T563** — Chat workspace + Conversation detail → bento (`app/bento/{chat,conversation}/`): reused verbatim inside the shell; `ChatPage` gained an `emptyStateHeader` prop so blank slates use a `BentoHero` (no console `SidebarTrigger`). **Phase 3 opens.** → CHANGELOG Block AG.
- ✅ **T564** — AI Analytics suite → bento (`app/bento/ai-analytics/`): `BentoHero` + pill tab-bar over `<Outlet />`, mounting the four reused console content components (cost-governance/chat-analytics/parked-conversations/capability-matrix); shares `useAiAnalyticsNav` visibility with the console. → CHANGELOG Block AG.
- ✅ **T565** — GraphQL explorer + Dashboard + Marketplace → bento (`app/bento/{graphql,dashboard,marketplace}/`); GraphiQL/Marketplace reused under a `BentoHero`, Dashboard re-skinned (customize in hero + reused widget grid). Voice Kiosk intentionally stays a standalone chrome-free route (booth/lobby, §X.6.c). → CHANGELOG Block AG.
- ✅ **T566** — Global Settings + Import/Exchange + Logging + System Info → bento (`app/bento/{settings,import,logging,system-info}/`): read-mostly surfaces hosted in the shell; `SystemInfoPage` gained a `header` prop; two admin-gated management nav items added. **Phase 3 complete.** → CHANGELOG Block AG.

**Phase 4 — admin & tenancy:**
- ✅ **T567** — Admin settings → bento (`app/bento/admin/`): `BentoHero` + pill tab-bar over users/groups/roles list `Outlet`s (settings/tokens tabs link to their migrated surfaces); reuses the Keycloak-vs-JPA dependent-query hooks + the admin forms/Keycloak views verbatim via a new `listRoute` prop. `administration` was the last console-only nav item → nav-config migration complete. **Phase 4 opens.** → CHANGELOG Block AG.
- ✅ **T568** — Tenant settings + Tenant admin + User account → bento (`app/bento/{user,tenant}/`): account is a hero + Profile/Preferences tabs over the reused console pages (new `header` prop drops the sidebar-coupled `SubPageHeader`); the two tenant surfaces are reused verbatim under a `BentoHero`; `BentoUserMenu` gains tenancy-gated Organizations + Tenant admin entries. **Phase 4 complete** — every surface now exists in bento. → CHANGELOG Block AG.

**Phase 5 — cutover & retirement:**
- 📋 **T569** (deps: T551–T568) **Make `/bento` the default interface** — route cutover, console → bento redirects, drop the "Preview" badge. → §XXXI.10
- 📋 **T570** (deps: T569) **Retire the legacy console** — delete migrated `console/*` pages + dead routes/components; no orphaned imports. → §XXXI.10
- 📋 **T571** (deps: T569) **Full a11y + responsive + performance + e2e pass** across the whole bento surface (keyboard, reduced-motion, mobile, bundle/lazy-load, smoke IT). → §XXXI.10
- 💭 **T572** (deps: T571) **Onboarding & delight polish** — consistent empty-states, keyboard-shortcut guide, motion-reduce audit, first-run tour. → §XXXI.10

## Block AI — Persona as a first-class actionable surface (persona chat, launchpad, bento dashboard)

> Today a `TurPersona` is a passive voice profile that only reaches a conversation
> by being attached to a `TurAIAgent`; the bento persona detail (T556) reuses the
> multi-section pill-tab **form** verbatim, so it reads as an editor, not a
> product surface. This block makes the persona **actionable and testable in its
> own right**: talk directly to a persona from a launchpad (MVP), redesign its
> detail as an AI-agent-style bento **dashboard** (big frosted cards → each
> detail), and make chat **URL-deterministic** so both agent and persona chats are
> shareable/back-forward-safe. The launchpad is designed to grow: content-fit
> validation and persona↔persona dialogue are staged as future actions.
> Design + rationale → [IMPROVEMENTS.md](IMPROVEMENTS.md) §XXXII.

**Phase 1 — persona chat MVP (talk to a persona): ✅ shipped (T578–T581) — see CHANGELOG.**

**Phase 2 — persona bento dashboard + launchpad: ✅ shipped (T582–T583) — see CHANGELOG.**
- ✅ **T582** (deps: —) **Frontend: persona detail → bento dashboard** — replaced the reused pill-tab form landing (T556) with an AI-agent-style **`BentoHero` + big `BentoTile` mosaic** (system-instruction hero tile, style, general, guidelines, audience, notebook, fit-report), each card linking into its section. Section sub-pages moved under a pathless editor route and their bodies swapped `SectionCard` → `BentoFormSection` (via `SectionCardChromeProvider chrome="bento"`), preserving the single-`useForm` save bar. → §XXXII.5
- ✅ **T583** (deps: T581, T582) **Frontend: Persona Launchpad** — an extensible launch bar on the persona dashboard mirroring `AiAgentLaunchBar`, with **Open chat** live (navigates to `/bento/chat/persona/:id`) and disabled/"soon" affordances that communicate the roadmap (validate content, persona dialogue). → §XXXII.6

**Phase 3 — future launch actions: ✅ shipped (T584–T585) — see CHANGELOG.**
- ✅ **T584** — **Content-fit validation from the launchpad** — the **Validate content** launch action opens a first-class, shareable surface `/bento/persona/validate/:personaId` composing Block AA's notebook (T465) + grounded content-fit evaluator (T467) + red/amber/green report (T468).
- ✅ **T585** — **Automatic persona↔persona conversation** — a **global** surface (`/bento/persona/dialogue`) that runs a bounded, turn-by-turn dialogue between two personas on a topic; each speaker must end with a question so the exchange self-sustains; turn budget default 10.

## Block AJ — Evaluation platform: pluggable graders (model/code/human) + datasets

> Today "eval" is two hard-wired islands: the **Agent-CI eval** (Block K —
> `TurAgentEvalRunnerService` scores every case on exactly four baked-in
> dimensions: slots/outcome/node = deterministic, rubric = one LLM judge) and the
> **NL→facet eval** (Block R — a pure deterministic scorer). Neither lets you
> *choose* how a case is graded, there is no **human-in-the-loop** grader at all,
> and a "dataset" is just golden cases typed inline into one agent — you cannot
> upload one, reuse it across agents, or version it. The industry frames graders
> as three kinds — **model** (LLM-as-judge), **code** (deterministic/programmatic)
> and **human** (review). This block turns Turing's eval into a real platform: a
> **grader SPI** (the three kinds as pluggable, composable extensions), a
> **first-class dataset** you can provide/import/version/share, reusable **grader
> stacks**, an **Eval Studio** surface, and consolidation of the two islands onto
> one model — all opt-in so empty/legacy config runs the exact current path (ITs
> stay green). Design + rationale → [IMPROVEMENTS.md](IMPROVEMENTS.md) §XXXIII.

**Phase 0 — grader SPI (refactor, zero behavior change):**
- ✅ **T586** (deps: —) **Grader SPI + registry** — `TurEvalGrader` interface + `TurEvalGraderKind {MODEL, CODE, HUMAN}` + `TurEvalGradingContext` + `TurEvalGraderResult` (score/passed/verdict/rationale/`deferred`) + `TurEvalGraderRegistry`. `TurAgentEvalRunnerService.scoreCase` now iterates the resolved grader stack; the four dimensions became built-in graders (SlotMatch/Outcome/Node = CODE, Rubric = MODEL). Empty config ⇒ legacy default stack, byte-identical. → CHANGELOG
- ✅ **T587** (deps: T586) **Grader config persistence** — `TurEvalGraderConfig` (kind, graderId, name, `configJson`, weight, threshold, `blocking`, enabled, sortOrder, per-case `caseId` override) as an EAGER cascade child of `TurAgentEvalSet` (Liquibase v2026.3.1.87); `TurEvalGraderRegistry.resolveStack(set, case)` resolves the stack from enabled rows (set-level + per-case override), empty ⇒ legacy default stack; travels inline with the agent via `TurAIAgentExchange.evalSets`. → CHANGELOG

**Phase 1 — CODE graders (deterministic, CI-safe, no LLM):**
- ✅ **T588** (deps: T587) **Built-in CODE grader library** — eight config-driven `TurEvalGrader`s under `grader/builtin/`: `exact` / `contains` / `regex` / `json-path` (jayway) on the final answer, `slot-tolerance` (numeric tolerance / ignoreCase), `numeric-range`, and `tool-called` + `latency-budget` composing the T427 tool-call trace. SPI evolved to `grade(ctx, config)` (config-driven via `TurEvalGraderConfigView`); opt-in (never in the default stack). → §XXXIII.3
- ✅ **T589** (deps: T588) **Custom Groovy CODE grader** — `grader "groovy"` runs an admin-authored `script` (from `configJson`) via `TurGroovyGraderEngine`, reusing the Custom Tool sandbox model (same `GroovyShell` + `sanitizeGroovySource` + compiled-script cache) with a vetted grading-context binding (`answer`/`answers`/`turns`/`slots`/`outcome`/`node`/`conversationId`/`caseName`). Returns Map `[score,pass,rationale]` / Boolean / Number; opt-in, errors-as-fail. → CHANGELOG

**Phase 2 — MODEL graders (LLM-as-judge, extensible):**
- ✅ **T590** (deps: T587) **Model-grader strategies** — `grader "model-judge"` (`TurModelJudgeGrader`) generalizes the rubric judge into `LABEL` / `SCORE` (threshold-gated) / `CRITERIA` (checklist) / `PAIRWISE` (vs a reference) strategies via `TurEvalModelJudge`, each with a per-grader `model` (LLM instance id) + `prompt` override. Uses the robust bilingual `TurEvalJudgePrompt` JSON verdict (the LlmJudge path — not a free-text-JSON structured-output guardrail, per the gpt-4o-mini pitfall). Sync path; folding the Batch (T159) / OpenAI-Evals (T168) offload tiers under the grader remains 💭 (rubric offload unchanged). → §XXXIII.5
- 📋 **T591** (deps: T590, T595) **Reference-answer grading** — dataset rows carry a golden reference answer; a MODEL grader scores answer-vs-reference faithfulness/correctness and a CODE grader scores embedding cosine similarity (reuses the configured embedding provider). Bridges eval to the Block M grounding contract. → §XXXIII.6

**Phase 3 — HUMAN graders (human-in-the-loop review):**
- 📋 **T592** (deps: T587) **Human-review grader** — a grader that **defers**: emits `PENDING_REVIEW` and persists a `TurEvalReviewTask` per case-result; the report gains a `PENDING_REVIEW` status; gate policy per stack = *warn* or *block-until-reviewed*. This is the missing third grader kind. → §XXXIII.7
- 📋 **T593** (deps: T592) **Human review inbox (API + bento)** — reviewers see the replayed transcript + expected-vs-actual + the per-grader breakdown and submit `{pass, score, notes}`; the verdict merges back into the report and can be **promoted into a golden dataset row** in one click. Role-gated (`AI_AGENT_*`). → §XXXIII.8
- 💭 **T594** (deps: T593) **Calibration & inter-annotator agreement** — optional N reviewers per item + an agreement metric, plus a sample-based human audit of MODEL-graded runs to align the LLM judge to human labels (feeds judge-prompt tuning). Makes the model grader *trustworthy*, not just cheap. → §XXXIII.9

**Phase 4 — datasets (provide, import, version, test):**
- 📋 **T595** (deps: T586) **`TurEvalDataset` + `TurEvalDatasetRow`** — a reusable, versioned, **agent-decoupled** collection of rows (input turns + expected slots/outcome/node/reference answer + tags/metadata); an eval set now binds `dataset + grader stack`. Migrate the existing inline cases into a per-agent default dataset (back-compat view). → §XXXIII.10
- 📋 **T596** (deps: T595) **Dataset import/export** — upload CSV / JSON / JSONL / OpenAI-Evals JSONL; import from anonymized production transcripts (T61) and from Chat-Analytics failures (reuse the T447 miner); a column-mapping step; export as JSONL. `POST /api/eval/dataset/import`. This is the "provide a dataset to be tested" ask made concrete. → §XXXIII.11
- 💭 **T597** (deps: T595) **LLM-assisted dataset generation & augmentation** — synthesize candidate rows from the flow / system-prompt / T386 manifest and paraphrase seed turns; **never auto-trusted** → opens the review form (reuses the T387 derivation + grounding pattern). → §XXXIII.12
- 📋 **T598** (deps: T595) **Dataset versioning + drift** — immutable snapshots; a run pins a dataset version; a diff shows added/removed/changed rows across versions; every report references the version it ran against (regression comparisons become honest). → §XXXIII.13

**Phase 5 — UX, consolidation & scale:**
- 📋 **T599** (deps: T587, T595) **Eval Studio (bento page)** — `/bento/eval`: datasets, grader stacks, run history, per-case drill-down (transcript + per-grader breakdown), the human-review inbox, and a baseline/regression + score timeline. The cramped flow-editor gate panel shrinks to a compact status badge that links here (follows the bento-ui skill). → §XXXIII.14
- 📋 **T600** (deps: T587) **Grader stacks as reusable templates** — a named stack (e.g. "Lead-capture QA") shareable across agents; **weighted** aggregate + per-grader pass thresholds + a blocking policy on the stack (replacing the hardcoded all-must-pass rule). → §XXXIII.15
- 📋 **T601** (deps: T586, T595) **Unify NL→facet eval (Block R) onto the grader/dataset model** — the `sn.dsl.eval` scorer becomes a CODE grader and the facet pack becomes a dataset, so chat-flow and SN evals share one Studio, one gate, and one dataset toolchain. → §XXXIII.16
- 💭 **T602** (deps: T595, T600) **Public eval API + SDK/CLI** — `turing eval` (T125–T127) runs a named `dataset × grader stack` remotely; SDK `runEval`; a reusable CI gate over any dataset (extends T428/T430). → §XXXIII.17
- 💭 **T603** (deps: T590, T588) **Continuous / online eval** — sample live production traffic, grade in the background with CODE+MODEL graders, and alert on quality **drift** (composes T87 sentiment + T155 citation-drift + T447 miner). "Eval in prod", not just pre-publish. → §XXXIII.18

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
