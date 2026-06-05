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
