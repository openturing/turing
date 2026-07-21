# Model-catalog regeneration pipeline — maintainer reference (Block BD)

> **What it is.** A **local, zero-dependency Node** pipeline that regenerates the
> canonical `turing-app/src/main/resources/llm/models-catalog.json` (the single
> source of truth behind the model picker fallback *and* the public catalog API,
> Block BC) from **multiple trustworthy sources per vendor**, so every model
> carries provenance-tracked detail instead of a hand guess.
>
> It is a **maintenance tool, not a runtime dependency** — the product still reads
> the bundled/published catalog. Lives in `frontend/apps/site/scripts/catalog/`,
> mirroring `emit-model-catalog.mjs`. Design rationale → [IMPROVEMENTS.md](../IMPROVEMENTS.md) §LII.

## The one rule: propose, never auto-overwrite

The public catalog is an authority asset; a bad upstream fetch must not silently
poison it. So the pipeline follows the same never-auto-apply discipline as
LLM-derived data elsewhere (T387 manifest derivation, T667/T675 mining):

- A plain run is **read-only** — it fetches, merges, validates, and writes a
  *proposed* envelope + a diff report into `scripts/catalog/out/`, then prints the
  report. **The canonical file is never touched.**
- Only `--apply` writes `models-catalog.json` (bumping `lastUpdated`).
- Even the (deferred) CI workflow (T771) would open a **PR** — never auto-merge.

## Commands

```bash
# from repo root (aliased in frontend/apps/site/package.json)
pnpm --filter turing-site catalog:regen              # dry-run: fetch → propose → report
pnpm --filter turing-site catalog:regen -- --offline # replay cached snapshots, no network
pnpm --filter turing-site catalog:regen -- --apply   # write the canonical file
pnpm --filter turing-site catalog:regen -- --only=gemini-api,litellm   # subset of sources
pnpm --filter turing-site catalog:regen -- --date=2026-07-21 --offline # deterministic

# after --apply, republish the public artifact (Block BC):
pnpm --filter turing-site emit:models
```

| Flag | Effect |
|---|---|
| _(none)_ | Dry-run. Fetch live, write `out/proposed-catalog.json` + `out/diff-report.txt`, print report. Canonical untouched. |
| `--apply` | Write the canonical `models-catalog.json`. The **only** path that overwrites it. |
| `--offline` | Skip the network; replay `scripts/catalog/sources/<source>.json` snapshots. Reproduces a run without re-hitting APIs. |
| `--only=a,b` | Restrict to the named source ids (see table below). |
| `--date=YYYY-MM-DD` | Stamp `lastUpdated`/`lastVerified` deterministically (tests). |
| `-v` / `--verbose` | Extra logging. |

## Trusted source per vendor

| Source id | Vendor(s) | Endpoint | Env key | Notes |
|---|---|---|---|---|
| `openai-api` | openai | `GET /v1/models` | `OPENAI_API_KEY` | Ids only → kind is heuristic; enriched by LiteLLM. |
| `anthropic-api` | anthropic | `GET /v1/models` | `ANTHROPIC_API_KEY` | id + display_name + created_at; all CHAT. |
| `gemini-api` | gemini | `models.list` | `GEMINI_API_KEY` | **Richest**: `supportedGenerationMethods` → kind, `inputTokenLimit`/`outputTokenLimit`. |
| `cohere-api` | cohere | `GET /v1/models` | `COHERE_API_KEY` | `endpoints[]` → kind, `context_length`. |
| `mistral-api` | mistral | `GET /v1/models` | `MISTRAL_API_KEY` | `capabilities{}` → tools/vision, `max_context_length`. |
| `litellm` | all | LiteLLM `model_prices_and_context_window.json` | _(none — public)_ | Vendor-agnostic enrichment. **Non-pricing fields only** (STRATEGY §X.5). |
| `overrides` | any | `scripts/catalog/overrides.json` | _(local)_ | Human-curated pins. |

**A missing API key skips that source with a warning — the run never hard-fails
because one credential is absent.** Every live fetch is cached as a raw snapshot
under `scripts/catalog/sources/<source>.json` (with fetch timestamp + provenance),
so a merge/normalize change can be re-run offline without re-hitting the network.

## Precedence & the anchoring rule

Merge folds all drafts per `(vendor, id)`. **Per-field**, the highest-precedence
source that supplies a value wins; provenance is recorded per field and
disagreements are flagged as conflicts in the report.

```
pinned override (100) > live vendor API (50) > override (30) > LiteLLM (10)
```

- **Scalar fields** (`kind`, `contextWindow`, `maxOutputTokens`, `label`, …) →
  highest-precedence wins; conflicts reported.
- **Set fields** (`capabilities`, `aliases`) → union of all sources.
- **`modalities`** → union of `input`/`output` arrays.

**Anchoring rule.** An id is admitted only if a **live vendor API**, an
**override**, or the **committed catalog** already vouches for it. LiteLLM
*enriches* existing/vendor-confirmed ids but can never introduce a brand-new id on
its own — so a noisy registry entry cannot poison the reference. Skipped
LiteLLM-only ids are listed in the diff report.

`overrides.json` is a claim you stand behind: set `"__pin": true` on an entry to
force its fields above **everything** (use only for personally-verified facts);
otherwise a pin sits above LiteLLM and below the live vendor API.

## `overrides.json` shape

```jsonc
{
  "openai": [
    { "id": "text-embedding-3-large", "embeddingDimensions": 3072, "__pin": true }
  ]
}
```

## Provenance written into the catalog

Each regenerated entry gains two additive fields (schema v1, T764):
`sources` (the source ids that contributed) and `lastVerified` (the run date).
Richer optional fields the sources populate: `maxOutputTokens`, `modalities`,
`knowledgeCutoff`, `releaseDate`, `aliases`, `status`. Consumers ignore unknown
keys, so the envelope stays `version: 1`.

## Review workflow

1. `pnpm --filter turing-site catalog:regen` (set whatever API keys you have).
2. Read `out/diff-report.txt` — check ADD/REMOVE/CHANGE and any `⚠ conflict`.
3. Wrong upstream value? Pin the correct one in `overrides.json` and re-run.
4. Satisfied → `catalog:regen -- --apply`, then `emit:models` to republish.
5. Commit `models-catalog.json` (+ any `overrides.json` change). `sources/` and
   `out/` are gitignored.

## Deferred (T771, 💭)

Self-hosted/aggregator sources (Ollama `list`, Bedrock `ListFoundationModels`,
HuggingFace for local ONNX) behind the same `SourceAdapter` contract, plus an
opt-in manual-dispatch GitHub Actions workflow that runs the pipeline and **opens
a PR** with the regenerated catalog. The human review gate is never removed.

## Non-goals

No pricing (STRATEGY §X.5 — enrichment is non-pricing only). No runtime
dependency. No auto-overwrite / no CI auto-merge. No envelope-shape break
(`version` stays 1, additive only).
