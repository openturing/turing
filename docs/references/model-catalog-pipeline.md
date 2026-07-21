# Model-catalog regeneration pipeline — moved

> **This pipeline moved 2026-07-21 to the dedicated
> [`openviglet/model-catalog`](https://github.com/openviglet/model-catalog) repo.**

The multi-source, propose-and-review regeneration pipeline (Block BD) that
regenerates the canonical `model-catalog.json` from live vendor APIs + the LiteLLM
registry now lives in that repository.

- **Maintainer workflow:** [`docs/references/pipeline.md`](https://github.com/openviglet/model-catalog/blob/main/docs/references/pipeline.md)
- **Run it:** `npm run regen` (dry-run) / `npm run regen -- --apply` (write) in that repo
- **CI:** a manual-dispatch workflow regenerates and opens a review PR (never auto-merges)

Turing no longer contains the pipeline or a bundled catalog copy; it consumes the
published endpoint remotely (`turing.model-catalog.url`).
