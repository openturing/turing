---
"@viglet/turing-cli": minor
---

New `@viglet/turing-cli` package — the `turing` developer CLI (E.7 / T125–T127). Zero runtime dependencies (Node 22+ built-ins), `tsc`-only build, mirroring `@viglet/turing-flow-dsl` conventions. Commands:

- `turing init <name>` — scaffold an agent project (`agent.json`, `flows/`, `tools/`, `skills/`, `evals/`, `turing.config.json`, a `docker-compose.dev.yml`).
- `turing dev` — boot a local Turing stack via Docker Compose; `--watch` redeploys on file change.
- `turing deploy` — push agent + chat-flows (atomic `import-bundle`) + custom tools + skills (zipped via a built-in store-method ZIP writer) to an instance; idempotent (creates then updates, persisting the agent id back to `turing.config.json`).
- `turing logs --conversation <id>` — tail live chat events over the spectator SSE stream (`--slots` adds the slot stream).
- `turing eval [path]` — run YAML regression suites against the chat API with `assistant.matches/contains`, `slot.*`, `tool_called`, `persona.forbidden/required`, and `node` assertions; `--watch` re-runs on change.
- `turing eval record <conversationId>` — freeze a production conversation as an `*.eval.yaml` fixture (T127).

Auth resolves from `turing.config.json` → env (`TURING_TOKEN` dev-token preferred for CI, or `TURING_USERNAME`/`TURING_PASSWORD`) → flags.
