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

## 7. Developer experience

The whole pitch is *"30 seconds to connect."* The customer runs the self-hosted
server (one container/jar) and adds it to the AI client they already use. For
Claude Desktop that is a few lines of config:

```json
// claude_desktop_config.json
{
  "mcpServers": {
    "turing": {
      "type": "http",
      "url": "https://intranet.acme.com/mcp",
      "headers": { "Authorization": "Bearer ${TURING_MCP_KEY}" }
    }
  }
}
```

From then on the user just asks, and the model calls Turing's tools with citations:

```
User:   What's our 2025 parental-leave policy?
Claude: (calls turing.search_site → grounds on the indexed HR space)
        Parental leave is 180 days for the primary caregiver…
        ► Source: HR Handbook §4.2  (intranet.acme.com/hr/handbook#4.2)
```

The tools exposed are the Semantic Navigation set that already exists as `@Tool`
methods — `list_sites`, `get_site_fields`, `search_site` — now reachable over MCP
instead of only the in-app chat.

## 8. MVP shape (self-hosted, realistic for a solo dev)

1. **`/mcp` endpoint** — Streamable HTTP transport over the existing Semantic
   Navigation tools. This is the bulk of the work and it's mostly transport
   plumbing; the tool logic already exists.
2. **API-key auth + per-connection scoping** — which sites/tools a given key may
   reach. Reuse the existing encrypted-key infrastructure (`TurSecretCryptoService`).
3. **Packaging** — a runnable jar / single container the customer drops next to
   their Turing instance; a 10-line README and the `claude_desktop_config.json`
   snippet above.
4. **Citations/provenance in tool output** — the Pro differentiator: every result
   carries a source URL so the host model can attribute (pairs with Block M).
5. **Pro gate** — enterprise auth + citations + audit behind the same offline
   Ed25519 license used by [Agent Eval](agent-eval.md) §5. No license server.
6. **(Later)** the **Groovy → MCP exporter** (§VIII.4) so existing Custom Tools
   become portable MCP servers — and, separately, the managed cloud once §6's
   conditions are met.

Nothing here needs a server of yours: the customer hosts the `/mcp` endpoint next
to the Turing they already run, and connects the AI client they already pay for.
