# Public Model Catalog API — moved

> **This contract moved 2026-07-21 to the dedicated
> [`openviglet/model-catalog`](https://github.com/openviglet/model-catalog) repo.**

The public model catalog API (Block BC) and its regeneration pipeline (Block BD)
were extracted from Turing into their own repository, which owns the canonical
catalog and publishes the endpoint.

- **Endpoint:** `https://openviglet.github.io/model-catalog/catalog.json`
  (pinned: `catalog-v1.json`; schema: `catalog.schema.json`)
- **Contract reference:** [`docs/references/api.md`](https://github.com/openviglet/model-catalog/blob/main/docs/references/api.md) in that repo
- **Regeneration pipeline:** [`docs/references/pipeline.md`](https://github.com/openviglet/model-catalog/blob/main/docs/references/pipeline.md)

**How Turing consumes it:** `TurLlmModelCatalog` fetches `turing.model-catalog.url`
(default the published rolling endpoint) on a TTL as the model-picker fallback
catalog. It is **remote-only** — there is no bundled copy — so the picker falls back
to live vendor listing until the first successful fetch.
