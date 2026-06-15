# Turing — Strategy & Positioning

> Business/positioning decisions that are **not** engineering tasks: pricing,
> market focus, deprecations, sequencing of bets. Extracted from the roadmap so
> the backlog stays executable and strategy stays debatable on its own terms.
> Engineering work lives in [ROADMAP.md](ROADMAP.md) / [IMPROVEMENTS.md](IMPROVEMENTS.md).

---

## VIII. Strategic moves — repositioning beyond features

> The Education customer engagement proved the product. This section is
> about **positioning** — what to start, what to deprecate, where the
> moat deepens. Not a roadmap; a strategy memo. Items here are scoped at
> *months*, not *PRs*. Tactical entries live in §0 Block D and §IV.

### VIII.1 The pricing-tiers question — what to charge for

Today everything is bundled. Three observable buyer profiles:

| Profile | Buys | Pricing pressure |
|---|---|---|
| **Self-host enterprise** (education, banking) | Full source + on-prem deploy + support contract | Custom enterprise license — high willingness-to-pay, low volume |
| **Mid-market SaaS** (would-be enterprise customers if smaller) | Hosted Turing without operating Spring Boot + Solr + JPA + Code Interpreter | Usage-based $0.01-0.05/conv; doesn't exist today, full mid-market lost to OpenAI Assistants |
| **Code Interpreter only** (devs who want Python sandbox + URL signing) | Standalone Modal / E2B competitor | Pure usage-based; the *sandbox hardening* (T76–T80) is differentiator |

**The strategic bet**: **separate Turing into 3 SKUs** with independent
pricing and marketing:

1. **Turing Search** — TurSN heritage (Solr + facets + ANN). Open
   source + paid support. Mature, slow-growth, paying for itself.
2. **Turing Agents** — chat-flow engine + Custom Tools + LLM-judge +
   analytics + admin. Open source (self-host) + cloud usage-based.
   Where the engagement work concentrates.
3. **Turing Sandbox** — Code Interpreter multi-tenant standalone, after
   T80 lands. Direct Modal / E2B competitor. Pure SaaS usage-based.

Each has a different buyer, different pricing, different lifecycle.
Bundling them dilutes the message — "is Turing a search engine or an
agent platform or a code sandbox?" today gets a yes on all three and a
buying decision on none.

### VIII.2 The Cloud bet — paralleling open source

**Tese**: SMB and mid-market will not operate Spring Boot + Solr +
Code Interpreter + Liquibase + multi-LLM config. Today 100% of that
segment goes to OpenAI Assistants / Copilot Studio. Self-host stays
free + open; cloud charges per use. Supabase / Posthog / Sentry pattern.

- **MVP shape**: signup → wizard creates agent → picks flow template
  ("Educational Lead Capture" / "B2B Pricing Quote" / "Knowledge Q&A")
  → WhatsApp integration → live in 10 min.
- **Stack**: managed containers (Fly.io / Cloud Run / Modal) — do not
  reinvent K8s.
- **Pricing**: $0.01/conv with 1k free; Code Interpreter $0.05/min
  additional; premium support in tiers.
- **Risks**: distracts from OSS; operations cost real money; competes
  with potential integrator partners.
- **Why it matters**: SaaS valuation multiple is 8-15× revenue vs open
  source 0-3×. Even if Cloud stays a small slice, it professionalizes
  the product (because customers complain) and unlocks the multi-tenant
  pitch that T80 will support.

### VIII.3 Verticalize in 2-3 markets

**Tese**: today Turing is "agent platform for anything." Wins no
category. Sierra ($4.5B) is vertical customer-service; Decagon idem;
Hippocratic AI is vertical healthcare. **Horizontal-positioned losers**:
LangChain (commodity), Botpress (struggling), Copilot Studio
(Microsoft-only). Verticalize on **Education + Financial Services**
where the engagement opened doors:

- Pre-built personas tone-calibrated per vertical
- Pre-built Custom Tools (DSpace adapter for academia, Bloomberg / Quandl
  for finance, FHIR for healthcare if added)
- Pre-built flow templates (Lead Capture EE, Mortgage Pre-Qualification,
  Patient Intake)
- Compliance defaults (LGPD aggressive, PII redaction by default,
  SOC2 audit trail)
- Public case studies per vertical (Education customer as anchor, plus
  1 bank + 1 hospital network)

Vertical is where 2026 venture dollars sit. Horizontal is where they
left.

### VIII.4 The MCP pillar — Turing speaks MCP both ways

**Tese**: Model Context Protocol is the de facto standard for agent
tools (Anthropic, Cursor, Claude Desktop, Glama, OpenTools). Turing's
MCP story is **one pillar with two directions** — previously split
across two sections, now unified because the strategic move is the same
bet on the same protocol:

1. **Turing as MCP _client_** (tool portability) — **Custom Tools Groovy
   is Turing-only lock-in.** Make Custom Tools **MCP servers by default**,
   Groovy demoted to legacy/"advanced" mode. Ganho: tools written for
   Turing become portable to any MCP client, AND the third-party MCP
   marketplace becomes available *inside* Turing's admin gallery with no
   new code. Sequencing: ship a one-shot **Groovy → MCP exporter**, then
   default new tools to MCP.
2. **Turing as MCP _server_** (distribution inversion) — expose
   `https://<host>/mcp` so the AI client the customer *already pays for*
   (Claude Desktop, Cursor, ChatGPT, internal agents) connects to Turing
   in 30 seconds and searches/answers/acts on enterprise content with
   citations. Turing stops being "a chat UI you must adopt" and becomes
   the grounding layer inside whatever agent the customer already runs.
   Full plan in [IMPROVEMENTS.md](IMPROVEMENTS.md) §XIII; tasks in
   [ROADMAP.md](ROADMAP.md) Block I.

**Why one pillar, not two**: the same protocol investment (transport,
auth, tool/resource/prompt schema) serves both. Sell it as one line —
"Turing speaks MCP in both directions: your tools go out, the world's
agents come in" — instead of two half-stories. **Trade-off**: existing
Groovy customers need a migration path (the exporter covers it).

### VIII.5 The Spring AI question — fork or live with the lag

**Tese**: Spring AI is the abstraction layer between Turing and
underlying LLMs. **Every new feature ships in Spring AI weeks or months
after the upstream SDKs.** Spring AI 2.0.0-M6's broken streaming (T17,
T18) was a concrete example.

**Two options**:
- (A) **Keep Spring AI**, ship workarounds (T18: hand-rolled WebClient
  for OpenAI streaming) as needed. Maintenance keeps growing.
- (B) **Hybrid: direct SDKs for fast-moving features, Spring AI for
  long-tail providers**. `turing-llm` thin abstraction layer routes by
  provider type. Higher initial cost (~4-6 weeks senior dev); buys
  feature parity at SDK release day.

Recommend (B) before customer commitments require day-1 feature parity
(vision, code-execution tools, prompt caching v2, etc.). The engagement
already paid the price for this on T18.

### VIII.6 What to deprecate (loud, with deadlines)

Cutting saves ciclo. Hesitation costs forever-maintenance.

| Feature | Why deprecate | Sunset window |
|---|---|---|
| `HeuristicGuardrailStrategy` as default for new flows | Regex-only; LLM_JUDGE + tool-calling do better. Third option pollutes the UI (TK1). | 6 months — flag-flip default; remove enum entry 12 months |
| Slot types `INTEGER` / `BOOLEAN` (TK2) | `useTuringSlot<number>` does coercion correctly; schema types only generate bugs. | 12 months — silently migrate to `string`, remove enum 18 months |
| `functionCall` node type (TK4) | Vapor — engine doesn't execute it. Either ship T46 or kill the type. | Decision in 1 sprint |
| Brevo integration (TK5) | Modern alternatives (Resend, Postmark) have better deliverability + simpler API. | Replace within 6 months |
| Custom Solr admin UI (TK6) | TurSN heritage; Solr has native admin. | Audit usage, kill if zero customers depend |
| ~~`forbidsToolCalls(String)` substring overload (TK3)~~ | ✅ Shipped in T16 — only the node-aware overload survives, reading `ChatFlowNode.toolsEnabled` declaratively. | Done |

### VIII.7 Sequencing — what to start this quarter

**Convicted choices, not exhaustive list** (revised 2026-06-03):

1. ✅ **Block K — Agent CI (T285–T288)** — **shipped 2026-06-05.**
   Turned capabilities already shipped (LLM-judge + analytics + transcripts
   + T94 lint) into the trust unlock for regulated verticals: a
   non-engineer changes a prompt and a golden-set eval gate proves no
   regression before publish. The next anchor is now the MCP pillar (#2).
2. ⭐ **MCP pillar (Block I + the §VIII.4 Groovy→MCP exporter)** — highest
   *distribution* leverage at lowest build cost (mostly transport — see
   §XIII.2). Ride the adoption curve of Claude/Cursor/ChatGPT instead of
   asking customers to adopt our UI.
3. **Block M — Verifiable answers (T292–T293)** — provider-agnostic
   provenance UI over existing RAG hits. Cheap, visible, and the
   Education/Finance trust differentiator vs source-less ChatGPT.
4. **Block L — Cost governance (T289–T291)** — the screen that proves
   "the bill drops 50%." Provider-agnostic, pairs with multi-tenancy (J).
5. **T60 + T61 — Slot audit log + PII prefix** — compliance pre-req for
   any bank/hospital conversation; also seeds Agent CI's anonymized
   golden sets.

**Downgraded (courage to deprecate, applied to our own roadmap):**
- **Jlama (former Block G, T193–T218) → a 2–3 day spike (T193-spike).**
  Upstream is stagnant (last release 2025-01-01); **Ollama-as-sidecar**
  already delivers air-gapped + tool-calling with far less risk. Do not
  commit an 8-week pure-Java inference build before a spike beats that
  default for a *named* customer requirement.

**Defer**: T80–T82 (Code Interpreter sandbox) shipped. Most remaining
Block D/F items wait on a customer to ask for them twice before
scheduling. Note F.6/F.13 (vendor citations & Usage-API cost) are now
the *enrichment* layer beneath Blocks M and L, not standalone priorities.

### VIII.8 The pergunta hard

**Today's positioning** (~35 words; buyer zones out at the first comma):
> "Open source platform for search + AI agents with chat-flows and
> Custom Tools running on Spring Boot, supporting A/B testing and
> multi-tenant Code Interpreter."

**12 months from now, if VII.2.h + VIII.1 + VIII.3 ship** (~28 words;
buyer knows in 5s if it's for them):
> "Turing is the open-source conversational AI platform for Education
> and Financial Services — self-host or cloud, end-to-end auditable,
> with the best LLM-as-judge in the market."

Difference is not features. It is **focus, clarity, and the courage to
deprecate what does not pay**.

---

## IX. Short-term monetization playbook (the "almost no users" reality)

> Added 2026-06-05. Honest sequencing for turning 10 years of work into
> revenue **now**, given the current state: copyright fully owned by a
> single holder, but **almost no installed user base**. The constraint
> this section answers: *fastest money, least effort.*

**The hard truth**: with near-zero users, **pricing is not the bottleneck —
distribution is.** A pricing page on a project nobody runs converts nothing.
So the storefront (below) is necessary but **not sufficient**; the money in
the next 90 days comes from *outbound services sold through the founder's
network*, not inbound SaaS.

### IX.1 The storefront (shipped — the cheap, durable part)

Published so any inbound buyer self-qualifies without a sales call:

- [`SUPPORT.md`](../SUPPORT.md) — commercial support tiers (Business/Enterprise),
  installation, custom dev, training. One page, four revenue doors.
- [`COMMERCIAL-LICENSE.md`](../COMMERCIAL-LICENSE.md) — dual licensing. Clean to
  offer because copyright is single-owner (no CLA to clear).
- `.github/FUNDING.yml` — GitHub Sponsors (`alegauss`), already live.

Dumont and Shio carry lean `SUPPORT.md` variants pointing to the same channel;
both are sold **bundled with Turing**, not standalone.

### IX.2 Sequencing by effort vs. time-to-cash

1. **Services first (week 1, zero new code).** Sell installation, support
   contracts, and custom connectors to the founder's existing network and the
   "Education" anchor account (§VIII proved the product there). This is the only
   path that pays in <90 days with no user base.
2. **Dual license (week 1–2, zero new code).** Offer the commercial license to
   any org that wants warranty/indemnity/closed-embedding. One email thread,
   one signed agreement — high margin, no engineering.
3. **Sponsorship (live).** Passive, low yield until users exist; keep it on.
4. **Open-core add-on (quarter, real build).** Deferred — see IX.3. Do **not**
   build before §IX.2 produces a *paying* customer who asks for it twice
   (the binding non-goal from CLAUDE.md §I).

### IX.3 The open-core pick — when it's time

The lead sub-product is **Agent Eval** — provider-agnostic regression / continuous-
assurance for AI agents, with the LLM-as-judge as the paid differentiator. Full
design + architecture in [`products/agent-eval.md`](products/agent-eval.md);
the selection rationale (the constraints matrix) in
[`products/README.md`](products/README.md). It wins because it is the only
candidate that is *recurring* (agents rot every model release — the cure for
"install and forget"), *zero-infra* (runs on the customer's CI; judge over HTTP
on the customer's own Turing, like Dumont calls `TurSummaryAPI`), and *sellable
to the existing base* (reactivates dormant consultancy installs). Reuses
`LlmJudgeGuardrailStrategy` + `TurLlmSummaryService`; engine is new, slim code.
Ties to T125–T127 and Block K (T285–T288).

**Turing Sandbox** (multi-tenant Code Interpreter, T80 shipped; hardening T239)
remains the higher-*upside* open-core wedge — usage-priceable, direct comp
(Modal/E2B) — but its multi-tenant hosting is the **worst** fit for the
solo/no-infra constraint. Defer behind Agent Eval; gate on a named customer
requirement, not on hope.

### IX.4 Non-negotiable: distribution before pricing pages pay

The single highest-leverage *short-term* move is not in this repo — it's
**visibility**: one public case study from the Education account, a Show HN /
Spring community post, and direct outreach. Without that, §IX.1's storefront
sits in the dark. Treat the storefront as "done and waiting", and spend the
actual short-term effort on getting the first ten serious evaluators in front
of it.

---

## X. Discoverability & GEO (Generative Engine Optimization)

> Added 2026-06-14. The concrete execution of §IX.4 ("distribution before
> pricing pages pay") for the channel that now matters as much as Google:
> **LLM assistants** (ChatGPT, Claude, Gemini, Copilot) answering "what's the
> best search for Adobe AEM / WordPress?". Tasks in [ROADMAP.md](ROADMAP.md)
> Block O (T342–T354). Some tasks live in sibling repos
> (`openviglet.github.io`, `vigletdocs.github.io`), tracked here because §IX.4
> already operates cross-repo.

### X.1 The thesis — why an LLM doesn't suggest Turing today

An LLM recommends by **semantic association built from the open web**, not from
a curated vendor list. When a user asks "search for AEM", the models surface
whatever has the densest digital correlation with those terms — today Coveo,
Algolia, Lucidworks, Solr. Turing is absent not because it's worse, but because
the **corpus the models trained on barely connects "Viglet Turing" to "AEM
search"**. The fix is to manufacture that association across the web — and to do
it where models actually ingest text.

**The uncomfortable ranking of signals** (strongest → weakest):

1. **Independent / third-party mentions** — GitHub README + topics, Stack
   Overflow / Reddit answers, `awesome-*` lists, comparison directories
   (AlternativeTo, G2). Models weight corroboration from sources you don't own.
2. **Technical documentation** — static HTML, clear H1s matching the query,
   how-to intent. Doc sites are heavily ingested and cited.
3. **Your own blog/marketing site** — necessary for narrative control, but the
   **weakest signal alone**. A post nobody links to barely moves the needle.

The mistake is to spend 90% of the effort on #3. The plan below front-loads #1
and #2.

### X.2 Channel split — decided

| Channel | Role | Stack reality |
|---|---|---|
| **docs.viglet.org** | **Home of the technical blog + integration guides.** How-to intent → doc-site authority. | Docusaurus 3 — **native blog plugin** (RSS, tags, authors, static HTML) for ~zero cost. |
| **viglet.org** | Positioning / product / comparison landing pages; cross-links *into* the docs guides. | Custom React+Vite SPA — building a blog here is days of work; **don't**. Already emits `llms.txt` via `vite-plugin-llmtxt.ts` — enrich it. |
| **Off-domain** | The strongest lever (X.1 #1): GitHub, SO/Reddit, Dev.to/Medium (canonical → docs), directories. | Where independent corroboration is born. |

**Decision: the blog lives on `docs.viglet.org` (Docusaurus).** Rationale —
how-to pieces match technical search intent, doc domains are ingested/cited more
heavily, and the blog plugin gives RSS + static HTML for free. `viglet.org` stays
the brand/positioning surface and links down into the guides; mixing editorial
posts into a custom SPA would cost days and dilute the docs' reference clarity.

### X.3 Content calendar — post themes that build the association

Three intent clusters. Each post must **name the problem in the title** (that's
the phrase users type at the LLM) and cross-link siblings.

**A. Integration how-tos (highest intent — these are the queries):**
- "Integrating Viglet Turing ES with Adobe AEM" *(flagship)*
- "Semantic & vector search in Adobe AEM with Turing ES"
- "Adding enterprise search to WordPress with Turing ES"
- "Indexing a CMS into Turing: AEM, WordPress, and custom connectors"

**B. Comparison / "versus" (rides competitor search volume):**
- "Open-source alternatives to Algolia for Adobe AEM"
- "Viglet Turing ES vs. Coveo for enterprise portals"
- "Turing ES vs. Lucidworks Fusion — open-source enterprise search"
- "When to choose Solr, Elasticsearch, or Turing ES"

**C. Concept / thought-leadership (RAG & agents — ties to the moat):**
- "RAG over enterprise content with Turing + MCP" *(ties §VIII.4 MCP pillar)*
- "Grounded, citeable answers from enterprise search" *(ties Block M)*
- "Building a conversational search agent on your own infrastructure"

Cadence: ship A1 (flagship) + B1 first (they convert the existing query volume),
then one post / 2 weeks. Republish A/B posts to Dev.to/Medium with `canonical`
pointing back to docs (reach without SEO duplication).

### X.4 Measurement — how we'll know it's working

Cheap, founder-runnable signals (no analytics build required):
- **Prompt the models monthly** with the target queries ("best search for AEM",
  "open-source Algolia alternative") across ChatGPT/Claude/Gemini; log whether
  Turing appears and in what position. This is the actual KPI.
- GitHub stars / `awesome-*` inclusions; referral traffic to docs; inbound that
  cites "found you via ChatGPT/Google".

Note the **lag**: model training cutoffs mean own-domain content can take months
to surface in answers; off-domain mentions (X.1 #1) and live-retrieval surfaces
(the models that browse) reflect it sooner — another reason to front-load #1.

---

## XI. Open-core source split — `turing` (private) → `turing-ce` (public)

> Added 2026-06-15. The boundary + license posture for separating the public
> community edition from confidential strategy. Implements the open-core model
> §VIII/§IX assume but never specced mechanically. Tasks in
> [ROADMAP.md](ROADMAP.md) Block P; publish mechanics design in
> [IMPROVEMENTS.md](IMPROVEMENTS.md) §XIX.

### XI.1 The boundary principle — hide *thinking* and *revenue*, not *cleverness*

The instinct to hide "clever" code (the LLM judge, the chat-flow engine, the
skill sandbox) protects almost nothing: those are strong *implementations of
known patterns*, reproducible by any competent engineer in days — or by asking
an LLM. Hiding them only costs adoption, and adoption is the actual bottleneck
(§IX.4). Decide what stays private by two tests, **never** by "is it hard to
copy":

1. **Does it expose our thinking?** — `STRATEGY.md`, `IMPROVEMENTS.md`,
   `ROADMAP.md`, `docs/products/**`. ~2.300 lines of design rationale is a
   competitor's playbook and is *not* reproducible. This is the real moat —
   far more than any one feature's source.
2. **Does a paying customer pay for it?** — the productized commercial add-on
   (Agent Eval engine, §IX.3) and managed hosting (Turing Sandbox / Cloud,
   §VIII.1–2). Held back by *business model*, not by secrecy.

Everything else — the entire runtime, the LLM-judge primitive, the SDKs, the
connectors — is **public** in `turing-ce`. The judge primitive being public is
*desired*: it is the community-edition funnel that the paid Agent Eval engine
sells against.

### XI.2 What stays private

- The three thinking docs + `docs/products/**` (and this file).
- **Commit history** — T-numbers and commit titles ("feat: T257 multi-tenancy
  enterprise…") leak the roadmap as loudly as ROADMAP.md. The public repo is a
  **squashed snapshot per release**, never a full-history mirror.
- The one monetized add-on engine, *when built* (§IX.3) — deferred behind the
  binding "paying customer asks twice" non-goal; **not** a Phase-1 carve-out.

### XI.3 Phasing — don't fragment the code prematurely

With near-zero users (§IX), splitting the platform into CE/EE Maven modules now
is premature surgery. Two phases:

- **Phase 1 (now):** publish *everything* except docs + history. Pure mechanics
  — a one-way denylist publish (Block P). Maximizes the funnel and feeds Block O
  (the public GitHub repo GEO needs — T346/T354 should target `turing-ce`).
- **Phase 2 (gated, not scheduled):** carve the Agent Eval engine into the
  private repo as the open-core wedge — only after a paying customer asks twice.
  The platform stays public; only the slim new engine is held back.

### XI.4 License posture — keep Apache 2.0; relicense is a *held option*, not a move

**Do not change the license now.** Reasoning:

- Relicensing is a one-way **trust event** — it spooks contributors and cannot
  be walked back in perception. You spend that cost once; spend it on a real
  threat.
- The threat a restrictive license defends against — a hyperscaler hosting
  Turing against your own Cloud — **does not exist at near-zero users**.
  Permissive Apache 2.0 on the CE maximizes the funnel, which §IX.4 names as
  *the* bottleneck.
- Protection today already comes from **dual licensing**
  ([COMMERCIAL-LICENSE.md](../COMMERCIAL-LICENSE.md)) **+ what you don't
  publish** (§XI.2), not from license terms.

**But preserve the *option*.** Single-owner copyright means Viglet can
relicense unilaterally later. To keep that right as external contributions
arrive on the public repo, adopt a **DCO sign-off (or lightweight CLA) before
accepting the first external PR** (T361) — otherwise contributed code dilutes
the right to relicense. The conditional future move, **if** Turing Cloud
(§VIII.2) becomes real revenue **and** a competitor hosts the CE against you:
evaluate the **FSL (Functional Source License)** — converts to Apache 2.0 after
2 years, blocks only competing/hosted use — applied **only** to the affected
parts, in preference to BSL/SSPL. Decision deferred to T362; not actioned until
both conditions hold.
