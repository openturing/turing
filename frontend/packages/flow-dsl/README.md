# @viglet/turing-flow-dsl

T98 / §VII.11.h — typed TypeScript DSL for Viglet Turing ES chat flows.

Author chat flows as TypeScript source with full IDE inference, then transpile
to the JSON wire shape the chat-flow editor's "Import JSON" button and the
backend `POST /api/ai-agent/{agentId}/chat-flow/import` endpoint already accept.

The goal is the same as any DSL: catch typos like `triggerMode: "ONCEE"` at
`tsc` time instead of letting them through to a server-side 400.

```ts
// flows/lead-capture.flow.ts
import { defineFlow } from "@viglet/turing-flow-dsl";

export default defineFlow({
  name: "Lead capture",
  triggerMode: "ONCE",              // ✅ valid (literal union)
  // triggerMode: "ONCEE",          // ❌ tsc: Type '"ONCEE"' is not assignable…
  guardrailMethod: "LLM_JUDGE",
  nodes: [
    { id: "start", type: "start" },
    {
      id: "ask-name",
      type: "aiQuestion",
      aiInstruction: "Ask the visitor's name.",
      outputVariable: "name",
    },
    { id: "end", type: "end" },
  ],
  edges: [
    { source: "start", target: "ask-name" },
    { source: "ask-name", target: "end" },
  ],
});
```

## Install

```bash
pnpm add -D @viglet/turing-flow-dsl
```

## Two ways to transpile

### CLI (one-shot, dep-free)

```bash
# compile your .ts sources to .mjs first
tsc flows/*.flow.ts --outDir build/

# then run the CLI
turing-flow-dsl build build/ --out flows-out/
```

The CLI accepts three input shapes in the same directory:

| Extension          | What it is                              | Behaviour                       |
|--------------------|-----------------------------------------|---------------------------------|
| `*.flow.mjs/.js`   | Compiled DSL module (default export)    | dynamic-imported, transpiled    |
| `*.flow.json`      | Raw export from the editor              | parsed, normalised, re-emitted  |

Output: `<basename>.chat-flow.json` per input, written in the editor's wire
shape so it can be dropped on the "Import JSON" button or fed to the import
endpoint via `curl`/`fetch`.

The raw-JSON path is the "back-compat reading raw JSON" requirement from
T98 — a team can mix hand-authored JSON files (still produced by the
editor) and typed DSL sources in the same `flows/` directory.

### Programmatic (your own build script)

```ts
import { transpileFlow } from "@viglet/turing-flow-dsl";
import { writeFileSync } from "node:fs";
import leadCapture from "./flows/lead-capture.flow.js";

writeFileSync(
  "out/lead-capture.chat-flow.json",
  JSON.stringify(transpileFlow(leadCapture), null, 2),
);
```

## What the transpiler does

- Fills `data.label` defaults per node type (`START`, `AI QUESTION`, …).
- Lays nodes out vertically (`x=0`, `y=index*120`) so the editor opens to
  a readable canvas before running its own auto-layout.
- Synthesizes edge ids (`e1`, `e2`, …) when omitted.
- Emits the editor-compatible `{ graph: { nodes, edges }, name, … }` shape.
- Coerces blank optional fields (`description`, `triggerDescription`) to
  `null` so the output matches `buildImportPayloadFromExport`.

## What the transpiler validates

- Exactly one `start` node, no duplicate node ids.
- Every edge endpoint refers to a declared node.
- Every `condition` node has exactly one outgoing edge with
  `sourceHandle="yes"` and one with `sourceHandle="no"`.
- Every outgoing edge of a `switch` node has a `sourceHandle` matching one
  of the node's `switchOptions[].id` — plus an optional single
  blank-`sourceHandle` **wildcard** edge for the no-match fallback (the
  engine resolves it via `pickConditionEdge`).
- Every non-`start` node has at least one incoming edge.

## Bundles (sub-flows)

A flow that descends into other flows via `subFlow` / `subFlowSwitch` ships
as a **bundle** — a JSON array the backend's
`POST /chat-flow/import-bundle` endpoint imports atomically, re-assigning
UUIDs and rewriting the transient `subFlowId` / `personaId` references.

Author the bundle with `defineBundle` (give every flow a stable `id`) and
emit it with `transpileBundle`:

```ts
import { defineBundle, defineFlow, transpileBundle } from "@viglet/turing-flow-dsl";

export const flows = defineBundle([
  defineFlow({ id: "main", name: "Main", nodes: [
    { id: "start", type: "start" },
    { id: "go", type: "subFlow", subFlowId: "child" },   // cross-flow ref
    { id: "end", type: "end" },
  ], edges: [
    { source: "start", target: "go" },
    { source: "go", target: "end" },
  ]}),
  defineFlow({ id: "child", name: "Child", nodes: [
    { id: "start", type: "start" }, { id: "end", type: "end" },
  ], edges: [{ source: "start", target: "end" }]}),
]);

const bundle = transpileBundle(flows);   // → import-bundle JSON array
```

`transpileBundle` runs every flow through `transpileFlow` **and** validates
that each `subFlowId` (on `subFlow` nodes and `subFlowSwitch`/`switch`
options) resolves to a flow `id` present in the bundle — a dangling
reference throws `FlowSpecError` at build time instead of silently failing
the descent at runtime. It also fills each `subFlowName` from the referenced
flow's `name`.

## Node & type reference notes

- **`suspend`** node parks the cursor until `POST /chat/resume` advances it;
  it carries only a `label`.
- **`functionCall`** / **`scheduleAgent`** accept `continueOnFailure: true`
  to route a `sourceHandle: "failure"` edge on error; `functionCall` also
  writes its result into `outputVariable`.
- **`SlotType`** is `STRING | INTEGER | BOOLEAN | FLOAT | TEXT` (matching the
  backend `TurAIAgentSlotType`). Email/phone/URL/number/date are
  `ValidationRule`s on the capturing node, **not** slot types.
- **Personas** declare the full backend voice shape (`id`, `systemInstruction`,
  `tone`, `verbosity`, `languageStyle`, `mandatoryTerms`, `forbiddenTerms`,
  `enabled`); a `persona` node's `personaId` points at a persona's `id`.

Anything beyond that (unused `outputVariable`, too-long `aiInstruction`,
trigger-description conflicts across flows) is the runtime linter's
responsibility — see T94 (`TurChatFlowLinterService`) and T91
(`TurTriggerConflictService`).

Throws `FlowSpecError` with a precise message pointing at the offending
node or edge id.

## Deploy

`turing-flow-dsl deploy <dir>` uploads every `*.chat-flow.json` in `<dir>` to a
running Turing instance. The connection details live in `package.json` under a
`"turing"` block so they're code-reviewable; only the password is interactive.

```json
{
  "scripts": {
    "build:flows": "tsc && turing-flow-dsl build build/flows --out dist",
    "deploy": "pnpm build:flows && turing-flow-dsl deploy dist"
  },
  "turing": {
    "url": "https://turing.empresa.com",
    "agentId": "abc-123-uuid",
    "username": "alexandre.oliveira",
    "mode": "bundle"
  }
}
```

```text
$ pnpm deploy
✓ build/flows/lead-capture.flow.mjs → dist/lead-capture.chat-flow.json
2 flow(s) transpiled, 0 failed.

Password for alexandre.oliveira at https://turing.empresa.com: ********
Deploying to https://turing.empresa.com (agent abc-123-uuid, user alexandre.oliveira, mode bundle)
✓ created  lead-capture.chat-flow.json  →  Lead capture (9f3b…)
↻ updated  in-company.chat-flow.json    →  In-company quote (a1c4…)
2 flow(s) deployed.
```

### Config precedence (last wins)

| Source                | URL                    | Agent       | Username      | Password           |
|-----------------------|------------------------|-------------|---------------|--------------------|
| `package.json` "turing" | `url`                | `agentId`   | `username`    | _(never)_          |
| Env var               | `TURING_URL`           | `TURING_AGENT_ID` | `TURING_USERNAME` | `TURING_PASSWORD` |
| CLI flag              | `--url`                | `--agent`   | `--username`  | _(never)_          |
| Interactive prompt    | —                      | —           | —             | ✓ (TTY required)   |

The CLI **never** reads a password from a flag (it would land in shell history)
and **never** from `package.json` (it would land in git). Only env var or
prompt — pick env in CI, prompt in dev.

### Modes

- `--mode bundle` (default) — every `.chat-flow.json` goes in a single `POST
  /chat-flow/import-bundle` round-trip. The backend auto-wires cross-flow
  `subFlowId` references in that path, so use bundle whenever flows reference
  each other.
- `--mode single` — one `POST /chat-flow/import` per file. Combined with the
  default (idempotent) lookup, this is the create-or-update path: the CLI
  fetches the agent's flow list, matches by `name`, then `PUT`s existing
  flows or `POST`s new ones.

### `--create-only`

Skips the `GET /chat-flow` lookup and always creates fresh flows. Safer when
flow names might collide with unrelated, hand-authored flows on the same
agent. Default is **false** (idempotent updates by name).

### Auth & CSRF

The deploy CLI authenticates with HTTP Basic on `/api/csrf` to pick up the
`XSRF-TOKEN` cookie + token, then re-uses both on every subsequent
state-changing request. No login session is created on the server beyond
what CSRF needs. The first non-2xx response aborts the run (fail-fast for
CI).

### CI usage

```yaml
# .github/workflows/deploy-flows.yml
- run: pnpm install
- run: pnpm build:flows
- run: pnpm exec turing-flow-dsl deploy dist
  env:
    TURING_URL: ${{ vars.TURING_URL }}
    TURING_AGENT_ID: ${{ vars.TURING_AGENT_ID }}
    TURING_USERNAME: ${{ vars.TURING_USERNAME }}
    TURING_PASSWORD: ${{ secrets.TURING_PASSWORD }}
```

If `TURING_PASSWORD` is unset in CI (no TTY), the prompt fails loudly with
`stdin is not a TTY` rather than hanging — set the secret.

## Versioning

The package version tracks the parent project (`pom.xml` in
`viglet/turing`). When the backend adds a new enum value (a guardrail
strategy, a node type), bump the DSL minor and extend the matching
literal union in `src/types.ts`. The transpiler itself is purely
structural — it doesn't need to know about the new value, but authors
lose compile-time safety until the union grows.
