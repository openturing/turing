# Sub-Product Brief — Turing MCP Connector

> **One line:** make the AI client the customer *already pays for* (Claude
> Desktop, Cursor, ChatGPT, internal agents) search and act on their enterprise
> content through Turing — over the **Model Context Protocol**, in ~30 seconds.
>
> Status: **partially viable now** (self-hosted shape), **managed cloud
> deferred** (see §6). Ties to STRATEGY.md §VIII.4, IMPROVEMENTS.md §XIII,
> ROADMAP Block I.

---

## 1. The idea (distribution inversion)

Instead of asking the customer to adopt Turing's chat UI, Turing becomes the
**grounding layer inside the agent they already run**. Their Claude/Cursor/ChatGPT
connects to a Turing MCP endpoint and gains tools like `search_site`,
`get_site_fields`, `list_sites` — answering with citations over enterprise content.
Turing stops being "another UI to adopt" and becomes plumbing under everything else.

MCP has two directions; this is one protocol investment serving both:

- **Turing as MCP _server_** (the sub-product) — expose `https://<host>/mcp`.
- **Turing as MCP _client_** (already partly built) — register external MCP
  servers as tools (`TurMcpServer`, `TurMcpServerAPI`), making the third-party
  MCP marketplace available *inside* Turing.

## 2. Two shapes — and which fits the constraint

| Shape | Infra burden (solo) | Verdict |
|---|---|---|
| **Self-hosted MCP server** — a binary/package the customer runs pointing at their Turing/content; their AI client connects | ✅ **zero — runs on customer infra** | **viable now** |
| **Managed MCP cloud** — you host `mcp.turing.io`, customers point clients at it | ❌ hosting + multi-tenant auth + uptime | **deferred** |

The self-hosted shape is the solo-viable slice: same property as
[Agent Eval](agent-eval.md) — the compute is the customer's, you ship and license
software, not a service.

## 3. What's already built vs. new

**Reuse (exists):**
- `TurMcpServer`, `TurMcpServerConnectionType`, `TurMcpServerType`,
  `TurMcpServerAPI` — the MCP **client** side (Turing consuming external MCP servers).
- `TurMcpInstructionsProvider` — instruction/prompt wiring for MCP tools.
- The Semantic Navigation tools (`list_sites`, `get_site_fields`, `search_site`)
  already exist as `@Tool` methods — the same tools become MCP-exposed resources.

**Build new (Block I — mostly transport, low logic cost):**
- The **server** direction: a `/mcp` endpoint (Streamable HTTP / SSE transport).
- Auth (API-key/OAuth) and per-connection scoping.
- A one-shot **Groovy → MCP exporter** so existing Custom Tools become portable
  MCP servers (§VIII.4) — turns Turing-only lock-in into portable assets.

## 4. Monetization

Open-core, offline-licensed (no server to run):

| Layer | What | Price |
|---|---|---|
| **Open-source** | basic self-hosted `/mcp` with the core search tools | free (distribution) |
| **Pro (offline license)** | enterprise auth, citations/provenance, per-tool scoping, audit | per-deployment / per-seat |
| **Managed cloud** | hosted `mcp.turing.io` | usage-based — **deferred** |

## 5. Why it's attractive medium-term

The build cost is low (mostly transport — the tools and the client side already
exist), and the distribution leverage is the highest of any candidate: you ride
the adoption curve of Claude/Cursor/ChatGPT instead of asking anyone to adopt a
UI. It pairs naturally with the existing consultancy base — *"connect your Claude
Desktop to the content we already indexed for you."*

## 6. Why the managed cloud is deferred

Hosting `mcp.turing.io` reintroduces exactly what the solo/no-infra constraint
rules out: uptime, multi-tenant auth, abuse handling. **Ship the self-hosted
open-core + Pro license first;** consider managed only with a partner for ops or
a customer who explicitly wants hosted. The self-hosted slice captures most of
the value with none of the operational tail.
