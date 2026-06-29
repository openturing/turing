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
  detail page as the working anchor; the full search surface lands in T452.

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
