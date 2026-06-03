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

### VIII.4 The MCP-first transition

**Tese**: Model Context Protocol is becoming the de facto standard for
agent tools (Anthropic, Cursor, Claude Desktop, Glama, OpenTools).
**Custom Tools Groovy is Turing-only lock-in**. Move Custom Tools to be
**MCP servers by default**, with Groovy demoted to legacy mode.

- **Ganho 1**: Custom Tools written for Turing become portable to any
  MCP client.
- **Ganho 2**: the MCP marketplace ecosystem becomes available *to*
  Turing customers without writing new code (third-party tools listed
  in the admin gallery).
- **Trade-off**: existing Groovy customers need a migration path.
- **Sequencing**: ship a one-shot Groovy → MCP exporter, then make MCP
  the default for new tools while keeping Groovy on the docs as
  "advanced" for power users.

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

**Convicted choices, not exhaustive list**:

1. ⭐ **T80 — Container sandbox for Code Interpreter** (~2 weeks). The
   one feature that gates multi-tenant SaaS and the Turing Sandbox SKU.
   No clean shortcut exists.
2. **T60 + T61 — Slot audit log + PII prefix** (~2 weeks). Unlocks the
   compliance pitch for regulated verticals. Required before any bank /
   hospital conversation.
3. **T94 — Chat-flow lint** (~1 week). Drives perceived editor
   maturity; first thing CTOs see in a demo.
4. **T70 — Multi-armed bandit for A/B** (~1 week). Replaces fixed-split
   pain we already feel internally.
5. **T46 OR TK4 — `functionCall` decision** (~1-3 days for decision +
   1-2 weeks if implemented). Vapor cleanup. Either ship it or kill it.

**Defer**: T80 + T81 (resource limits) + T82 (pre-warmed pool) shipped.
Most remaining Block D items wait on a customer to ask for them
twice before scheduling.

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
