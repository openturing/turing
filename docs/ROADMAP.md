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

## Block AN — Security hardening (post-audit, 2026-07-10)

> From a full-surface security review assuming a client exercises 100% of the
> product (all anonymous SN chat/search, admin config, connectors, MCP, storage,
> imports, custom tools). Findings + CONFIRMED/POTENTIAL status and evidence in
> [IMPROVEMENTS.md](IMPROVEMENTS.md) §XXXVII. Each task is *analyse-then-fix*:
> confirm the reachability caveat noted, then remediate. Ordered by severity;
> the three 🔴 items are live-secret / anonymous-data / anonymous-cost exposures
> that warrant immediate attention regardless of task sequencing.

**🔴 Critical:**
- 📋 **T639** (deps: —) 🔴 **Externalise & rotate the AI crypto master key** — `turing.ai.crypto.key: sample-key-for-crypto` is committed in `application.yaml:9` with `spring.profiles.active: production` (`:292`); the prod fail-fast in `TurCryptoKeyMaterial` only fires on a *blank* key, so this repo-committed key currently encrypts every stored provider API key / store credential. Require the key via env only, reject known sample/dev values in prod, re-encrypt existing secrets, document rotation. → §XXXVII.1
- 📋 **T640** (deps: —) 🔴 **Authenticate `/api/system/chat-analytics/**`** — `permitAll` in both auth branches (`TurSecurityConfigProduction.java:191,253`) exposes session enumeration + full chat transcripts (`.../sessions/{id}/transcript`) anonymously — end-user content / PII with no auth. Require auth + role; a code comment is not a control. → §XXXVII.2
- 📋 **T641** (deps: —) 🔴 **App-layer abuse controls on the anonymous chat path** — no rate-limit / per-IP / per-session cap anywhere in the app, and the shipped `containers/nginx/conf.d/app.conf` has no `limit_req`/`limit_conn`; the only budget gate (`TurChatCostBudgetGate`) is per-agent, monthly, opt-in and *soft* (never aborts). Add per-IP/session throttling + a hard aborting cost/turn kill-switch + nginx limits. → §XXXVII.3

**🟠 High:**
- 📋 **T642** (deps: —) **Fix the `permissions=false` blanket-admin grant** — default `turing.permissions: false` (`application.yaml:11`) makes `TurCustomUserDetailsService`/`TurAuthTokenHeaderFilter` grant every authenticated principal `ROLE_ADMIN` + all privileges, nullifying all 240 method-level checks. Stop blanket-granting when permissions are off (or flip the default) so authn ≠ super-admin. → §XXXVII.4
- 📋 **T643** (deps: —) **Central egress guard for all outbound fetches (SSRF)** — one hardened validator (extend `TurFileUtils.isSafe`: add IPv6 ULA `fc00::/7`, pin the resolved IP to defeat DNS-rebinding) routed through the connector test-connection oracle (`TurConnectorSelfInstallService`, authenticated, response-observable), LLM/rerank/webhook base URLs, and the federation proxy (re-validate every redirect hop). → §XXXVII.5
- 📋 **T644** (deps: —) **Kill the SpEL RCE sink in chat-flow conditions** — `ChatFlowOps.trySpel` (`:1738`) evaluates `conditionExpression` with a `StandardEvaluationContext` (full `T(...)`/method calls → RCE) reachable via flow-bundle import + LLM authoring; swap to `SimpleEvaluationContext.forPropertyAccessors(...)`. → §XXXVII.6
- 📋 **T645** (deps: —) **Storage path-traversal / Zip-Slip containment** — `TurTenantScopedStorageService.scopeKey` only rejects `..` when a tenant prefix is active, so the default single-tenant path is unguarded; `TurAssetAPI` fetches/deletes a user-supplied `objectName` with no authz; page/skill ZIP import concatenates entry names without `..` stripping. Add unconditional key containment + authz + entry-name sanitisation; verify the filesystem backend resolves within base. → §XXXVII.7
- 📋 **T646** (deps: —) **Stop leaking credential material over the API** — `TurUser.password` has no `@JsonIgnore` so the admin user-list returns BCrypt hashes; `TurDevTokenAPI` has zero authz, returns cleartext tokens, and tokens have no expiry/revocation and are accepted as a `?apiKey=` query param. Add `@JsonIgnore`/DTO scrub, authz, expiry+revocation, drop query-param tokens. → §XXXVII.8
- 📋 **T647** (deps: —) **RAG / tool-call trust boundary for anonymous chat** — retrieved chunks are concatenated verbatim into the *system* prompt (`TurSNGenAi.buildSystemPrompt:660`) with no untrusted-data framing (the roadmap's "anti-injection" is unrelated CPF/CNPJ parsing), and there is no authorization boundary between an anonymous visitor and LLM-invoked native/MCP-client/custom tools. Frame retrieved content as untrusted + gate tool invocation by caller trust. → §XXXVII.9
- 📋 **T648** (deps: —) **Input / DoS caps on anonymous endpoints** — `spring.servlet.multipart.max-*-size: 1024MB` (`application.yaml:360`) lets anonymous slot-upload feed ~1 GB into Tika + a vision call; chat message count/length is unbounded (token budget is opt-in/off); ZIP import buffers entries via `readAllBytes()` with no size/count cap (zip-bomb OOM). Add pre-parse size/type gates, default-on hard message/token caps, and zip entry limits. → §XXXVII.10

**🟡 Medium / Low:**
- 📋 **T649** (deps: —) **Actuator hardening** — `management.endpoints.web.exposure.include: "*"` (`application.yaml:464`) exposes `/actuator/env` + `/heapdump` (leaking the crypto key, DB creds, provider keys) to any authenticated principal (= admin by default, cf. T642). Restrict to `health,info,prometheus` and role-gate the rest. → §XXXVII.11
- 📋 **T650** (deps: —) **MCP hardening** — inbound `/mcp` fails open (`require-auth` defaults false, relies on a loopback filter); the agent-as-MCP-*client* path (`TurMcpToolCallbackService.createTransport:139`) takes a verbatim URL (SSRF) and a stdio `command` (arbitrary local process) with no allowlist, and `TurMcpToolScopePolicy` guards only the inbound server surface. Fail closed by default + validate client URL/command + extend the scope policy to client tools. → §XXXVII.12
- 📋 **T651** (deps: —) **Fix IDOR in `TurUserMemoryAPI`** — list/remember/clear key on a client-supplied `userId` with no binding to the caller, and `delete(id)` has no ownership/tenant/agent check. Scope every op to the resolved caller/session. → §XXXVII.13
- 💭 **T652** (deps: —) **Config & logging hygiene** — remove committed MinIO creds (`application.yaml:116` `admin`/`minha_senha_forte`, purge from history); keep the H2 console off and drop `web.ignoring("/h2/**")` + `web-allow-others: true`; redact the secret-preview + human-approval capability-token INFO logs; constrain the anonymous self-registration / signup arbitrary-username paths. → §XXXVII.14
- 📋 **T654** (deps: —) **Triage & patch dependency CVEs (Dependabot)** — GitHub Dependabot reports 13 open advisories on the default branch (7 high · 3 moderate · 3 low) across the backend + frontend dependency trees. Triage each against actual usage (reachable vs transitive-only), bump/override the affected coordinates, and wire a CI gate (`mvn` + `pnpm audit` / Dependabot auto-merge policy) so new highs fail the build. Separate from the code-level findings above but part of the same full-product exposure. → §XXXVII.16
- 💭 **T653** (deps: —) **Frontend XSS surface** — confirm & enforce that the search-engine highlighter HTML-escapes field content before it reaches the `dangerouslySetInnerHTML` result-card (rendered inside the authenticated admin console); add a client sanitiser allowing only `<mark>`-style markup; sanitise/sandbox the Gemini `renderedContent` fragment injected raw into the console; optionally post-sanitise the LLM-driven D2 SVG. → §XXXVII.15

## Block AQ — Knowledge Base: microthesauri & index-time term expansion

> A tenant-scoped **library of hierarchical microthesauri** (controlled
> vocabularies, per language + domain) inspired by OpenText Semantic Navigation.
> At **index time** every document is expanded with the root→term hierarchical
> path of each term it mentions, written to a dedicated Solr field — recall +
> concept-facet browse with no query-time cost. Complements Block AP synonyms and
> embedding search, does not replace them. Architecture fixed in
> [ADR 0003](adr/0003-knowledge-base-microthesaurus.md); design in §XL. The
> reconstructed nstein TME / OpenText SN authority-file format (`BT/NT/RT/U/UF`
> relations + `variations`) is the import + AI-generation contract.

- 📋 **T670** (deps: T668) **Per-site selection + field config** — `TurSNSiteMicrothesaurus` join (site ↔ selected microthesaurus, `enabled`) + per-site index config (field name / boost / include-synonyms) + selection API; wires `TurSNFieldProvisioner`. Opt-in: no selection = legacy indexing. → §XL.3
- 📋 **T671** (deps: T668, T670) **Recognition dictionary + matcher** — compile the selected microthesauri (∩ locale) into a cached, normalised (case/accent per variation) Aho-Corasick automaton resolving surface forms (label + `USE`/`USED_FOR`) → canonical term; evicted on KB/term/selection change. → §XL.4
- 📋 **T672** (deps: T671) **Index-time expansion enricher** — `TurSNMicrothesaurusIndexer.enrich(site, locale, attributes)` wired into `TurSNProcessQueue.index` after the T472/T501 enrichers; provisions multi-valued TEXT `microthesaurus_terms` (+ `_str` concept facet), top-down ancestor expansion, low `qf` boost; defensive (never blocks indexing) + index-time IT. → §XL.5
- 📋 **T673** (deps: T668) **XML importer + reconstructed contract** — author the nstein TME / OpenText SN authority-file XSD/DTD as the formal contract (shallow clone — the old importer is unrecoverable), then import `authorityFile` → domain (`BT/NT` → `parentTermId`, `RT/U/UF` → relations, `variations` → surface forms; reciprocal-pair + self-relation dedupe). → §XL.6
- 📋 **T674** (deps: T668, T673) **Pre-populated EDUCATION knowledge base** — AI-generated microthesaurus hierarchies for the priority **education** domain (pt/en/it) shipped as an importable seed bundle (the T373/T655 seed-ZIP precedent, not baked into startup) + a library browse area. → §XL.7
- 💭 **T675** (deps: T673, T674) **LLM-assisted hierarchy generation** — generate new microthesaurus trees from a domain+language prompt, emitting the T673 authority-file format; reuses the AI-authoring strict-schema + LlmJudge grounding path (T426/T385). → §XL.8
- 📋 **T676** (deps: T669, T548) **Bento admin editor** — browse/create/edit KB → microthesaurus → term trees + relations on a `/bento/kb` surface (BentoHero + BentoEntityShell + tree editor) for in-project curation. → §XL.9
- 💭 **T677** (deps: T672) **Hierarchical concept facet** — second `microthesaurus_path` field (path tokens `0/Doença`, `1/Doença/Doença respiratória`, …) for Solr drill-down navigation, surfacing the OpenText-style Semantic Navigation browse. → §XL.10
- 💭 **T678** (deps: T672) **Query-time related-term suggestions** — a did-you-mean-style "related concepts" surface built on the microthesaurus `RELATED` (RT) links, reusing the spell-check/suggestion UI plumbing. → §XL.11

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
