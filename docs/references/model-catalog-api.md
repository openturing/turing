# Turing Public Model Catalog API — contract reference

> **Status: PLANNED (Block BC — not yet live).** This documents the *designed*
> contract for the public curated model catalog so it can be reviewed and cited
> while Block BC is built. The endpoint below **does not resolve yet**; do not
> point production code at it until Block BC ships (track ROADMAP Block BC /
> IMPROVEMENTS §LI). The authoritative source while unshipped is the bundled
> `turing-app/src/main/resources/llm/models-catalog.json` (Block BB).

## What it is

A **vendor-neutral, kind-aware catalog of LLM/embedding/rerank/media models** —
which model ids exist per vendor and, for each, what *kind* it is (chat,
embedding, rerank, image, transcription, speech, video, moderation), plus context
window, embedding dimensions and capability hints. Published as an open,
CORS-enabled, versioned JSON artifact so any tool can consume it as a market
reference (the role LiteLLM's `model_prices_and_context_window.json` plays for
pricing, but identity/kind-first and browsable).

It is **free, unauthenticated, and read-only.** First cut is *identity + kind +
capability* — **not pricing** (see STRATEGY §X.5).

## Endpoints (planned)

| URL | Meaning |
|---|---|
| `https://turing.viglet.org/models/catalog.json` | Rolling latest — the current schema version. |
| `https://turing.viglet.org/models/catalog-v1.json` | Pinned to schema **v1** — safe for external consumers to lock. |
| `https://turing.viglet.org/models/catalog.schema.json` | The JSON Schema (Draft 2020-12) describing the envelope + entry. |
| `https://turing.viglet.org/models` | Human-browsable reference page (T761). |

Served with `Access-Control-Allow-Origin: *` and a cache header (e.g.
`Cache-Control: public, max-age=3600`); consumers should honour ETag/`max-age`.
Breaking schema changes bump the pinned path (`catalog-v2.json`); `catalog.json`
always tracks the newest.

## Envelope

```jsonc
{
  "schema": "https://turing.viglet.org/models/catalog.schema.json",
  "version": 1,                 // schema major version (integer)
  "lastUpdated": "2026-07-20",  // ISO-8601 date the catalog was regenerated
  "source": "https://turing.viglet.org",
  "vendors": {
    "openai":    [ /* ModelEntry, ... */ ],
    "anthropic": [ /* ModelEntry, ... */ ],
    "gemini":    [ /* ... */ ]
    // keyed by provider plugin type (lower-case), mirroring models-catalog.json
  }
}
```

## `ModelEntry`

| Field | Type | Required | Notes |
|---|---|---|---|
| `id` | string | ✅ | The exact id sent to the vendor (`text-embedding-3-large`). |
| `label` | string | ✅ | Human-friendly display name. |
| `kind` | enum | ✅ | `CHAT` · `EMBEDDING` · `RERANK` · `IMAGE` · `TRANSCRIPTION` · `SPEECH` · `VIDEO` · `MODERATION` · `UNKNOWN` (Block BB `TurLlmModelKind`). |
| `vendor` | string | ✅ | Provider plugin type (echoes the map key). |
| `contextWindow` | integer | — | Max context tokens, when known. |
| `embeddingDimensions` | integer | — | Output vector size — only for `kind = EMBEDDING`. |
| `capabilities` | string[] | — | Hints such as `vision`, `tools`, `json_mode`, `streaming`. |
| `deprecated` | boolean | — | `true` when the vendor has retired/superseded the id. |

```jsonc
// example entry
{
  "id": "text-embedding-3-large",
  "label": "text-embedding-3-large",
  "kind": "EMBEDDING",
  "vendor": "openai",
  "embeddingDimensions": 3072,
  "capabilities": ["matryoshka"]
}
```

## Usage examples (once live)

```bash
# every embedding model across all vendors
curl -s https://turing.viglet.org/models/catalog-v1.json \
  | jq '.vendors | to_entries[].value[] | select(.kind=="EMBEDDING") | .id'
```

```js
// browser / Node — kind lookup for an arbitrary id
const { vendors } = await (await fetch(
  "https://turing.viglet.org/models/catalog-v1.json",
)).json()
const all = Object.values(vendors).flat()
const kindOf = (id) => all.find((m) => m.id === id)?.kind ?? "UNKNOWN"
```

## How Turing itself consumes it (T762)

`TurLlmModelCatalog` will fetch `turing.models-catalog.url` (default the rolling
endpoint) as the **primary** fallback catalog, cached with ETag/TTL, and fall
back to the bundled `models-catalog.json` when the endpoint is unreachable
(offline / air-gapped installs). The live per-vendor `/v1/models` listing path
and the Block BB metadata-first classification are unchanged — the public catalog
only replaces where the *static fallback* comes from.

## Relationship to other artifacts

- **Source of truth:** the canonical enriched catalog defined in T759; this doc is
  its published contract.
- **Kinds:** the `kind` enum is the backend `TurLlmModelKind` (Block BB / §L.1).
- **Positioning:** STRATEGY §X.5 (why this is a discoverability asset).
