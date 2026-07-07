# Turing SDK in Adobe Edge Delivery Services (EDS)

This folder shows how to consume **`@viglet/turing-sdk`** from a vanilla
[Adobe Edge Delivery Services](https://www.aem.live/) project — no React, no
bundler required.

## How EDS loads the SDK

EDS serves your repo's JS as native ES modules straight to the browser. There
is no `npm install` step at runtime, so you get the SDK into your project one of
two ways:

### Option A — Vendor the self-contained ESM file (recommended)

1. Build the SDK: `pnpm --filter @viglet/turing-sdk build`.
2. Copy `frontend/packages/sdk/dist/turing-sdk.js` into your EDS project's
   `scripts/` folder (so it ends up at `/scripts/turing-sdk.js`).
3. Import it from a block with a **relative** path:

   ```js
   import { createTuringClient, createSearchController } from "../../scripts/turing-sdk.js";
   ```

The file is zero-dependency, so nothing else needs to be vendored.

### Option B — Import from a CDN

```js
import { createTuringClient } from "https://cdn.jsdelivr.net/npm/@viglet/turing-sdk/+esm";
```

CDN imports add a third-party runtime dependency on the visitor's first paint —
prefer Option A for production EDS sites where you control the asset.

## The example blocks

| Block | File | What it shows |
|-------|------|---------------|
| `turing-search` | [`blocks/turing-search/`](blocks/turing-search/) | `createSearchController` + `createAutoComplete`, repainting the DOM via `subscribe`, facet/pagination `navigate`. |
| `turing-chat` | [`blocks/turing-chat/`](blocks/turing-chat/) | `createChatController` with token-by-token streaming (`onToken`), suggestion chips, `sessionStorage` persistence. |
| `turing-analytics` | [`blocks/turing-analytics/`](blocks/turing-analytics/) | `createTuringAnalytics` + `googleAnalyticsSink` (Block Z): one bus shared by search + chat emits canonical events to GA4/GTM, **auto-detecting** the host `window.gtag` / `dataLayer` (zero config). Stitches the `search → chat → lead` funnel via `TUR_SESSION`. |

Copy a block folder into your EDS project's `blocks/` directory. Authors then
add a block by name; the first authored row carries the Turing **API base URL**
and **SN site name**:

```
| turing-search                                   |
| https://turing.example.com/api | my-site        |
```

## CORS & CSRF notes

- The SDK sends `credentials: "include"` so the visitor's session/CSRF cookies
  travel cross-origin. The Turing backend must allow your EDS origin in CORS
  (`Access-Control-Allow-Origin` + `Access-Control-Allow-Credentials: true`).
- For chat POSTs against a CSRF-protected Turing, the client primes the token
  via `GET {baseURL}/csrf` automatically (the `XSRF-TOKEN` cookie is HttpOnly).
- Public/anonymous search sites with CSRF disabled work tokenless out of the box.
