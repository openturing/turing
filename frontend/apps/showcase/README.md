# Atlas Store — Viglet Turing ES reference showcase

**Atlas Store** is the canonical reference application for [Viglet Turing
ES](https://github.com/openviglet/turing). It is a commerce-catalog copilot
("the kitchen sink") whose single job is to **exercise the maximum surface of
the platform** — a living demo, an SDK-vs-real-backend integration test, and the
"this is what Turing can do" artifact for sales and docs.

It lives in the monorepo at `frontend/apps/showcase` and **seeds its own data**:
a ~200-product typed catalog (price as `CURRENCY`, rating as `FLOAT`, reviews as
`INT`, on-sale as `BOOL`, release date as `DATE`, multi-valued
color/material/tags) that stresses the manifest, hybrid ranking and facets.

> **Block Y roadmap.** This app is built across tasks **T451–T457** (see
> `docs/IMPROVEMENTS.md` §XXIV). T451 is the scaffold + seed; later tasks light
> up the search surface, RAG chat, agent power, creative bets, voice/ops and the
> guided tour + e2e smoke test.

## What's here (T451 — scaffold + seed)

- A Turborepo app (`turing-showcase`) wired to **`@viglet/turing-react-sdk`**
  (search/chat hooks) and skinned with **`@viglet/turing-react-ui`** token
  theming (`src/lib/brand.ts`, roadmap T305).
- A deterministic catalog generator (`gen-catalog.mjs`) → `export/atlas-store_content.json`.
- A hand-authored SN site + typed fields + facets + custom sorts + a spotlight
  (`export/export.json`).
- The **import ZIP** path (`gen-manifest.mjs` + `zip-dist.mjs`) so the app +
  data can be imported into a running Turing as a marketplace bundle, and the
  `turing init` / `turing dev` clone-to-running flow works against it.
- A storefront (faceted search + product grid + click tracking) and a product
  detail page as the working anchor.

## Search surface (T452)

The storefront lights up the full SDK search half end to end:

- **Faceted + URL-synced** search (`TuringProvider` `urlSync` + `useTuringFacets`).
- **Autocomplete** suggestions dropdown (`TuringSearchField.Dropdown`).
- **Spell-check** "did you mean?" banner (`useTuringSpellCheck`).
- **Category tabs** (`useTuringTabs`, attribute `category`) — the sidebar hides
  the category facet so the two don't conflict (T247).
- **Sort** dropdown wired to the site's custom sorts (`useTuringSortOptions` →
  `store.setSort`): price ↑/↓, top-rated, most-reviewed, newest.
- **Similar / "you may also like"** rail on the product page
  (`useTuringSimilar`, T400 — vector / more-like-this).
- **Click tracking / CTR** (`useTuringClickTracking`) on every result open.

## Chat surface (T453)

A floating **"Ask Atlas"** copilot (slide-over panel) exercises the RAG chat
half end to end via `useTuringChat`:

- **Token streaming** with a thinking indicator.
- **RAG sources / citations** rendered as `TuringSourceChips` (T152/T292).
- **Option chips** for the next turn (chat-flow `options` event).
- **Native multi-field forms** (T107) — `activeForm` → `submitForm`, with
  per-field types (text/email/tel/number/date/textarea/select).
- **Slots** (T63) — a "Collected" strip shows the structured data captured so
  far (`useTuringSlots`).
- **Multimodal upload** (T401) — a paperclip attaches an image to a slot with
  vision extraction (`useTuringSlotUpload`).
- **Workspace panel** (T113) — artifacts the agent produced
  (`useTuringWorkspace` → `TuringWorkspacePanelView`).
- **Rich content** (T295–T298) — `TuringRichContent` dispatches markdown /
  sandboxed `html` / `d2` diagrams; the `@terrastruct/d2` engine is **lazy /
  code-split** (only fetched when an answer contains a diagram).

Like hybrid ranking, the copilot needs an AI agent + LLM on the site: the
offline Lucene seed has search only, so the panel shows an honest "chat lights
up when GenAI is configured" notice. Point the app at a backend with GenAI
enabled to chat over the catalog.

## Agent power (T454)

The copilot exercises the Block W + skills agent surface:

- **Client tools** (T438/T439) — `add_to_cart` / `get_cart` / `clear_cart`
  (`src/lib/cart-tools.ts`) run against a real cart context
  (`src/contexts/cart.tsx`), so the agent acts on the shopper's live basket. The
  cart drawer (header) and the product page "Add to cart" share the same state.
- **Code interpreter** (T80) — with `get_cart` exposed, the agent can run
  Python to "compute my cart total" or "chart the price history" over real data.
- **Live tool-call activity** (T436) — `TuringToolActivity` renders each
  `tool_call` SSE event inline in the assistant bubble (`message.toolCalls`).
- **Answer-as-an-app** (T442) — `useAnswerAsApp` + `TuringGenerativeContent`
  render the built-in `comparison_table` / `spec_card` / `configurator` widgets
  the agent emits, parked/resumed over the client-tool protocol.
- **Bundled returns/RMA skill** — `skills/returns-rma/` is a self-contained
  Anthropic skill folder (`SKILL.md` + `references/return-policy.md` +
  `scripts/rma.py`). Copy it into the backend's `turing.skill.skillsPath` (or
  import it) so the agent can authorize returns and generate RMA ids in chat.

As with search and chat, the agent-side features light up only when the site has
an AI agent with the relevant capabilities enabled (`clientToolsEnabled`,
`answerAsAppEnabled`, `toolCallEventsEnabled`, `skillsEnabled`, code interpreter);
the client wiring is always present.

## Creative bets (T455)

The thin compositions that only pay off because Turing owns search **and** agents:

- **Co-browse the search UI** (T443) — `useCoBrowseSearch(store)` folds
  `set_search_query`/`toggle_facet`/`clear_facets`/`set_sort`/`set_page` into the
  chat's client tools, so the agent drives the real storefront the shopper is
  looking at (the chat is a remote control, not a parallel box).
- **Glass-box "Why #1?"** (T444) — a button on the results asks the agent to
  explain the top result's ranking (via the `explain_ranking` tool) using the
  real BM25 / vector / RRF / rerank signals.
- **Ambient proactive copilot** (T445) — `useTuringProactiveCopilot` surfaces a
  bottom-left toast when an interaction signal crosses the agent's threshold;
  accepting hands the prompt to the chat.
- **Agent→agent handoff** (T448) — when the agent delegates (e.g. sales →
  returns specialist), the `delegate_to_agent` call shows up for free in the
  inline **tool-call activity** (T436) already wired in the chat.
- **Embeddable action widget** (T450) — [`/embed-demo`](src/pages/embed-demo.tsx)
  is a **mock third-party site** that embeds the Atlas agent via the zero-dep
  vanilla `@viglet/turing-sdk` `createHostActions({ onAddToCart })`, so the agent
  can add to *that site's* cart — Turing as an action layer over any website.

The glass-box / proactive / co-browse / handoff behaviors are gated by the
matching per-agent flags server-side; the client wiring is always present.

## Voice, memory & ops (T456)

- **Voice agent** (T147) — a mic button in the chat composer (`useTuringVoice`,
  Web Speech STT) dictates the question and auto-sends on a final utterance.
- **Search by image** — a hero affordance hands off to the copilot's multimodal
  upload (vision → "find similar products").
- **Cross-conversation memory** (T446) — a "brain" toggle in the chat header
  opens a panel of what Atlas remembers about the visitor (`useTuringUserMemory`,
  keyed on a stable localStorage visitor id); `remember_fact`/`recall_user_memory`
  client tools are merged into the chat, and a "Forget me" button is GDPR delete.
- **Ops tour** — [`/ops`](src/pages/ops.tsx) maps the operational surfaces:
  chat analytics (sentiment trajectory T87, tool-latency p95 T88, SSE channels
  T90), agent eval + golden sets (Block K), self-tuning suggestions (T447), and
  cost governance (Block L). These are admin-console endpoints, so the tour
  documents each with its endpoint rather than faking dashboards.
- **MCP exposure** (Block I) — [`mcp/claude-desktop.json`](mcp/claude-desktop.json)
  bridges Claude Desktop to Turing's MCP server (`/mcp`, Streamable HTTP) via
  `mcp-remote`, so you can drive the Atlas catalog (`search_site`, …) from Claude
  Desktop.

### Hybrid ranking + reranker (server toggle)

`HYBRID_RRF` (T383) and the pluggable reranker (Block N) are **site-level
backend configuration**, not a client switch — they need an embedding LLM and an
AI agent attached to the site's GenAI config. They are off in the offline Lucene
seed (which has no embeddings). To activate against a real backend, set
`snRankingMode` on the imported site's GenAI config to `HYBRID_RRF` (or
`HYBRID_RRF_RERANK` to add the Block N reranker) once an embedding instance is
configured. The search UI above is rank-mode agnostic — it renders whatever
ordering the backend returns.

## Develop

```bash
# From the monorepo root
pnpm install
pnpm dev:showcase            # vite dev server (proxy VITE_API_URL → a running Turing)

# Or from this directory
npm run gen-catalog          # (re)generate the 200-product catalog
npm run dev
```

`.env` controls the target backend and site:

```
VITE_API_URL=/api
VITE_SN_SITE=atlas-store
VITE_LOCALE=en_US
```

## Package as an import bundle

```bash
pnpm build:showcase          # gen-catalog + gen-manifest + tsc + vite build
npm run zip                  # → atlas-store.zip (export.json + content + compiled app/)
```

Import `atlas-store.zip` from the Turing admin (Marketplace / Pages import) to
provision the SN site, index the catalog, and serve the SPA as a search
template.

## Catalog schema

| Field | Type | Notes |
|---|---|---|
| `title`, `description`, `text` | `TEXT` | highlighted, more-like-this |
| `brand`, `category`, `subcategory`, `availability` | `STRING` | facets |
| `color`, `material`, `tags` | `STRING` (multi-valued) | facets |
| `price`, `list_price` | `CURRENCY` | `amount,ISO4217` payload |
| `price_amount`, `list_price_amount`, `weight_kg` | `FLOAT` | numeric sort/range |
| `rating` | `FLOAT` | star rating |
| `review_count` | `INT` | |
| `on_sale` | `BOOL` | facet |
| `release_date` | `DATE` | year facet |

Built with **Viglet Turing ES**.
