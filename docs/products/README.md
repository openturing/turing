# Turing Sub-Products

> A "sub-product" is a piece of Turing that can be **carved out, distributed,
> and monetized on its own** — with a different buyer, a lighter footprint, and
> ideally a path to recurring revenue. This folder holds the design + go-to-market
> brief for each. It complements (does **not** duplicate) the platform docs:
> strategy lives in [`../STRATEGY.md`](../STRATEGY.md), tasks in
> [`../ROADMAP.md`](../ROADMAP.md), design rationale in [`../IMPROVEMENTS.md`](../IMPROVEMENTS.md).

---

## The lens: why sub-products at all

Two constraints shape every pick here. They come from the actual situation, not
from a textbook:

1. **Solo + no infra + no capital.** Anything that requires operating servers,
   multi-tenant SaaS, or a sales team is the *wrong* short/medium-term bet.
   Favour things that run on **the customer's** machine/CI, and that monetize
   **without a server** (e.g. offline-signed license keys).
2. **"Install and forget" is the revenue disease.** The platform installs,
   works, and then never needs the customer again → no engagement, no recurring
   revenue, consultancy that ends at delivery. The cure is a sub-product that
   creates a **recurring reason to come back** — something consumed continuously
   or something that *rots and must be re-checked*.

A good sub-product scores well on **both** axes. That is the filter.

---

## The matrix

| Candidate | Recurring need? | Infra burden (solo) | Sells to existing base? | Verdict |
|---|---|---|---|---|
| **[Agent Eval](agent-eval.md)** — regression/continuous-assurance for AI agents | ✅ high — agents rot every model update | ✅ near-zero — runs on customer CI; judge over HTTP | ✅ yes — reactivates dormant Turing installs | **★ primary pick** |
| **[Turing Sandbox](turing-sandbox.md)** — standalone multi-tenant Code Interpreter (§VIII.1) | ✅ usage-based | ❌ heavy — multi-tenant sandbox hosting | ⚠️ partial | Defer — best upside, worst infra fit (self-hosted add-on is the viable slice) |
| **[MCP Connector](mcp-connector.md)** — Turing-as-MCP (§VIII.4) | ✅ | ⚠️ self-hosted = zero; managed = hosting | ✅ | Self-hosted viable now; managed cloud deferred |
| **[Vertical Packs](vertical-packs.md)** — personas/flows/tools for Education/Finance | ❌ install-and-forget alone | ✅ zero (digital download) | ✅ | Add-on + content feedstock for Agent Eval, not a standalone engine |

Only **Agent Eval** wins on recurrence *and* zero-infra *and* sell-to-existing-base
simultaneously. That is why it is the lead sub-product.

---

## Where the monetization scaffolding already lives

- **Storefront** (shipped): [`../../SUPPORT.md`](../../SUPPORT.md) (support tiers,
  services), [`../../COMMERCIAL-LICENSE.md`](../../COMMERCIAL-LICENSE.md) (dual
  licensing — clean because copyright is single-owner), `.github/FUNDING.yml`
  (GitHub Sponsors, live).
- **Short-term playbook** (the "almost no users → distribution before pricing"
  reality): [`../STRATEGY.md`](../STRATEGY.md) §IX.
- **Sub-product briefs**: this folder.

The honest sequencing across all of the above: **services to the existing
network pay first (weeks); the storefront converts inbound (passive); Agent Eval
is the medium-term engine that turns one-off consultancy into a subscription.**
