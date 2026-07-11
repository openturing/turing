# @viglet/turing-cli

> `turing` — the [Viglet Turing ES](https://turing.viglet.org) developer CLI.

Scaffold an agent project, run a full local stack, deploy agents/flows/tools/skills
to any environment, tail live chat events, and run YAML eval suites in CI.

Zero runtime dependencies (ships as compiled ESM, `tsc`-only). Requires **Node ≥ 26**.

## Install

```bash
npm i -g @viglet/turing-cli
# or run without installing
npx @viglet/turing-cli init my-copilot
```

This exposes the `turing` binary.

## Quick start

```bash
turing init my-copilot      # scaffold an agent project
cd my-copilot
turing dev                  # boot a local Turing stack (Docker Compose)
turing deploy --env=local   # push agent + flows + tools + skills
turing eval                 # run the YAML eval suites in evals/
```

## Project layout

`turing init <name>` scaffolds:

| Path                  | Purpose                                                              |
| --------------------- | ------------------------------------------------------------------- |
| `agent.json`          | The AI Agent definition (system prompt, flags, native tools).       |
| `flows/`              | `*.chat-flow.json` chat flows (author with `@viglet/turing-flow-dsl`). |
| `tools/`              | `*.groovy` custom-tool scripts.                                     |
| `skills/`             | Anthropic-compatible skill folders (one per subdirectory).          |
| `evals/`              | `*.eval.yaml` regression suites.                                    |
| `turing.config.json`  | Instance URL(s) per environment + the deployed agent id.            |
| `docker-compose.dev.yml` | Local stack used by `turing dev`.                               |

## Commands

### `turing init <name> [--dir <path>] [--force]`

Scaffold a new agent project directory. `--force` overwrites an existing folder.

### `turing dev [--file <compose>] [--detach] [--watch]`

Boot a local Turing stack via Docker Compose. `--detach` runs in the background;
`--watch` redeploys the project on file changes.

### `turing deploy [--env <name>] [connection flags]`

Push the project — agent, flows, tools and skills — to the environment named in
`turing.config.json` (e.g. `--env=local`, `--env=staging`).

### `turing eval [path] [--watch] [connection flags]`

Discover, run and report `*.eval.yaml` suites (defaults to `./evals/`). Exits
non-zero when any fixture fails, so it drops straight into CI.

### `turing eval record <conversationId> [--out <file>] [connection flags]`

Capture a real conversation from a running instance into an eval suite, e.g.
`turing eval record abc123 --out evals/from-prod.eval.yaml`.

### `turing logs --conversation <id> [--slots] [connection flags]`

Tail live chat events (SSE) for a conversation. `--slots` also streams slot updates.

### `turing migrate elasticsearch|algolia … [connection flags]`

Import a source search index into a Turing SN site — the automated "step 2" of
switching from Elasticsearch or Algolia. Turing reads the source schema, derives a
field manifest, provisions the SN site, and imports every record. Add `--dry-run`
to preview the derived schema without provisioning or importing anything.

```bash
# Elasticsearch — URL + credentials + index only, no vendor SDK
turing migrate elasticsearch \
  --source-url https://es.example.com:9200 \
  --source-user elastic --source-password secret \
  --index products --site Products --se-instance <seInstanceId> --dry-run

# Algolia — schema inferred from a record sample + index settings
turing migrate algolia \
  --app-id APPID --api-key <read-key> \
  --index catalog --site Catalog --se-instance <seInstanceId> --use-llm
```

| Flag | Engine | Purpose |
| ---- | ------ | ------- |
| `--index` | both | Source index name (required). |
| `--site` | both | Target SN site name (required). |
| `--se-instance` | both | Search-engine instance id (required to create a new site). |
| `--source-url` | ES | Source cluster URL (required). |
| `--source-user` / `--source-password` / `--source-api-key` | ES | Source auth. |
| `--app-id` / `--api-key` | Algolia | Application id + a read-capable API key (required). |
| `--use-llm` | Algolia | Refine the inferred schema with a configured LLM. |
| `--overrides-file <path>` | both | JSON array of field-mapping overrides (rename / retype / drop / default). |
| `--locale` `--batch-size` `--max-documents` `--dry-run` | both | Tuning + preview. |

A source schema is rarely 1:1 with what you want in Turing. `--overrides-file`
points at a JSON array that reshapes fields on the way in:

```json
[
  { "field": "cost", "rename": "price", "type": "CURRENCY" },
  { "field": "internal_notes", "drop": true },
  { "field": "source", "type": "STRING", "defaultValue": "algolia-import" }
]
```

Each override targets one source `field` and may `rename` it, force its `type`,
`drop` it, or set a `defaultValue` for records that omit it (a `type` with no
matching source field materialises a constant field). Overrides apply to both the
derived manifest and every document, so schema and data stay consistent.

`--se-instance` is only required when the target site doesn't exist yet. Synonyms
found in an Algolia index are reported for you to apply in the target engine
(native synonym management is on the roadmap).

#### `turing migrate compare` — measure relevance parity before cutover

Run the same query set against the **source engine** and the **migrated Turing
site**, and diff the top-N result sets — so you can prove parity before flipping
traffic. Read-only on both sides.

```bash
turing migrate compare \
  --engine elasticsearch \
  --source-url https://es.example.com:9200 --source-user elastic --source-password "$ES_PASSWORD" \
  --index products --site Products \
  --queries-file ./queries.txt --rows 10
```

`--queries-file` is a text file with one query per line (blank lines and `#`
comments ignored). The report shows, per query and in aggregate, the set overlap
(Jaccard), how much of the source's top-N Turing reproduced (source recall), and
how often the #1 result matches. Source flags are the same as `turing migrate`.

## Eval suites

Eval suites are `*.eval.yaml` files: a list of `fixtures`, each a sequence of
`user` turns and `assert.*` checks run against a live agent.

```yaml
# evals/smoke.eval.yaml — run with: turing eval
fixtures:
  - id: greeting
    steps:
      - user: "Hello, can you help me?"
      - assert.assistant.matches: ".+"
      - assert.persona.forbidden: ["I cannot help", "I'm just an AI"]
```

Available assertions include `assistant.matches` / `assistant.not_matches`,
`assistant.contains` / `assistant.not_contains`, `slot`, `tool_called` /
`tool_not_called`, `persona.required` / `persona.forbidden`, and `node`.

## Configuration & authentication

`turing.config.json` holds non-secret settings — the project name, the deployed
`agentId`, and a URL per environment:

```json
{
  "name": "my-copilot",
  "agentId": "",
  "envs": {
    "local": { "url": "http://localhost:2700" },
    "staging": { "url": "https://staging.turing.example.com" }
  }
}
```

Credentials are **never** written to `turing.config.json`. Provide them via the
environment or flags:

| Variable / flag             | Use                                              |
| --------------------------- | ------------------------------------------------ |
| `TURING_URL`                | Override the target instance URL.                |
| `TURING_TOKEN`              | Dev token (from the admin console) for unattended / CI use. |
| `TURING_USERNAME` / `TURING_PASSWORD` | Basic auth for interactive use.        |
| `TURING_ENV`                | Default `--env` when omitted.                    |
| `--url`, `--token`, `--username`, `--password` | Per-command overrides.        |

## License

Apache-2.0 — © Viglet. Part of the [Viglet Turing ES](https://github.com/openviglet/turing) project.
