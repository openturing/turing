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

- 📋 **T543** (deps: T541) **Prune the speculative scaffolding** — remove ports+adapters+mappers+records generated "scaffolding only" for pure admin-CRUD aggregates that never touch a hot path, a cache, or a LazyInit risk. Kills the two-sources-of-truth ceremony a 100% migration would have made permanent. (Dead scaffolding with zero consumers is removed opportunistically ahead of this task; T543 is the systematic pass against the T541 boundary.) → §XXX.4
- 📋 **T544** (deps: T541) **Close the write-path question** — confirm ports stay read-only; admin/CRUD writes stay on the JPA repo + `@Transactional` + `LEFT JOIN FETCH` (per T488). Rewrite the "future slices will introduce save / delete" Javadoc on the surviving ports to state the final decision, so the layer stops reading as "in progress forever". → §XXX.5
- 📋 **T545** (deps: T541, T542, T543) **Enforcement convention test** (`TurDomainLayerConventionsTest`) — following the `TurCacheAnnotationConventionsTest` pattern: assert `*Domain` records carry no JPA/Hibernate/Jackson annotations; the designated hot-path packages depend on ports, not JPA repositories; every surviving port has exactly one adapter + mapper. Makes the boundary self-policing so it can't silently drift back toward 100%. → §XXX.6
- 💭 **T546** (deps: T541) **Collapse port+adapter → mapper-only where the interface is pure ceremony** — for aggregates that will only ever have a single JPA adapter, evaluate dropping the `*RepositoryPort` interface and returning records straight from a mapper-backed reader. Keeps ~90% of the benefit (immutability/caching) without the interface boilerplate the port adds when there's no second implementation and no mocking need. → §XXX.7

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
