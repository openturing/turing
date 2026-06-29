# @viglet/turing-sdk

Vanilla JavaScript SDK for **Viglet Turing ES** — a framework-agnostic,
**zero-dependency** client for the Turing Semantic Navigation API, plus
observable controllers for the common front-end flows. No React, no axios.

Built for **Adobe Edge Delivery Services (EDS)** blocks, plain `<script>`, Astro,
WordPress, or any bundler. (For React apps, use
[`@viglet/turing-react-sdk`](../react-sdk) instead — same backend, hook-based API.)

## Installation

```bash
npm install @viglet/turing-sdk
```

Or vendor the self-contained ESM bundle (`dist/turing-sdk.js`) directly into an
EDS project — see [`examples/eds`](examples/eds/README.md).

## Quick start

```js
import { createTuringClient, createSearchController } from "@viglet/turing-sdk";

// 1. Configure the client (the explicit replacement for axios.defaults).
const client = createTuringClient({ baseURL: "http://localhost:2700/api" });

// 2. Drive search through an observable controller.
const search = createSearchController(client, { site: "my-site", locale: "en_US" });
search.subscribe((state) => {
  for (const doc of state.documents) {
    console.log(doc.title, doc.url);
  }
});
await search.searchQuery("artificial intelligence");
```

### Streaming chat

```js
import { createTuringClient, createChatController } from "@viglet/turing-sdk";

const client = createTuringClient({ baseURL: "https://turing.example.com/api" });
const chat = createChatController(client, { site: "my-site", persist: true });

chat.subscribe((s) => renderBubbles(s.messages)); // re-renders on every token
await chat.send("How do I reset my password?");
```

## Architecture

| Layer | Exports | Notes |
|-------|---------|-------|
| **Client** | `createTuringClient` | Owns `baseURL`, `credentials: "include"`, and CSRF priming via `GET /csrf`. |
| **API** | `fetchSearch`, `postChatConversation`, `postAgentChat`, `postLlmChat`, `fetchIntents`, slot/handoff/flow functions, `parseHrefToParams`, … | Pure functions `(client, …args)`. `createTuringApi(client)` returns a client-bound object. |
| **Controllers** | `createSearchController`, `createChatController`, `createAutoComplete`, `createSlotsController` | Observable (`getState` / `subscribe`); the vanilla equivalents of the React hooks. |
| **Session / Resolve / SSE** | `getOrCreateTurSession`, `resolveDocuments`, `subscribeSlotsSse`, … | Browser helpers ported verbatim from the React SDK core. |

### Observable controllers

Each controller wraps a tiny pub/sub `Store`:

```js
const ctrl = createSearchController(client, { site });
const unsubscribe = ctrl.subscribe((state) => paint(state));
const snapshot = ctrl.getState();
unsubscribe(); // clean up when your component/block tears down
```

`createChatController` and `createSlotsController` additionally expose a
`destroy()` to abort in-flight requests, timers, and SSE subscriptions.

## Analytics & the Google Analytics bridge (Block Z)

Measure conversion, abandonment and "which persona A/B variation was preferred"
**in the browser**, where it lives next to your acquisition source and audiences.
The SDK ships a zero-dependency canonical event bus + **pluggable sinks** — GA4
is the first adapter, not a hard dependency, so the SDK stays EDS-friendly.

```js
import {
  createTuringClient, createTuringAnalytics, googleAnalyticsSink,
  createSearchController, createChatController,
} from "@viglet/turing-sdk";

const analytics = createTuringAnalytics({
  sinks: [googleAnalyticsSink()], // auto-detects window.gtag / dataLayer
  context: { site: "my-site" },
});

const client = createTuringClient({ baseURL: "/api" });
createSearchController(client, { site: "my-site" }, undefined, { analytics });
createChatController(client, { site: "my-site", analytics, goalSlots: ["email"] });
```

**Canonical events** (vendor-neutral; sinks map them verbatim):

| Event | Fired when |
|-------|-----------|
| `turing_chat_start` / `turing_chat_message_sent` | conversation begins / each user turn |
| `turing_chat_step` | a flow node is reached (client mirror of the server funnel) |
| `turing_chat_lead_captured` | a goal slot is written or a native form (T107) is submitted |
| `turing_chat_abandoned` | a started, unconverted conversation ends (tab hide / unload / idle) — the signal the server can't see |
| `turing_ab_variant_assigned` | the A/B arm resolves; `experiment_key` / `variant_label` / `persona_id` then ride on **every** event |
| `turing_search` / `_no_results` / `_result_click` / `_refined` | search lifecycle (`_no_results` is a content-gap signal) |

**Sinks**: `googleAnalyticsSink({ measurementId?, transport? })` (GA4 `gtag` +
GTM `dataLayer`, auto-detected), `onEventSink(fn)` (Segment/Matomo/Plausible),
`debugSink()` (console). The bus is **no-op when no `analytics` option is
passed**, so existing embeds are unchanged. Cross-surface `search → chat → lead`
stitching is automatic via the shared `TUR_SESSION` cookie. In React, use
`useTuringAnalytics()` from `@viglet/turing-react-sdk`.

## CSRF & cross-origin

The Turing backend uses Spring Security's `CookieCsrfTokenRepository`, which
writes the `XSRF-TOKEN` cookie **HttpOnly** and surfaces the token via the
`X-XSRF-TOKEN` response header. JS can't read the cookie, so the client primes
the token with a best-effort `GET {baseURL}/csrf` before mutating requests.
Anonymous/public deployments with CSRF disabled work tokenless.

When the SDK and Turing are on different origins, enable CORS with credentials
on the backend (`Access-Control-Allow-Credentials: true` + an explicit origin).

## Relationship to the React SDK

This package was extracted from the framework-agnostic `core/` layer of
`@viglet/turing-react-sdk` (same API endpoints, same `Tur*` types). The React
SDK currently keeps its own copy of that core; a future change may have it
depend on `@viglet/turing-sdk` to remove the duplication.

## License

Apache-2.0 — © Viglet.
