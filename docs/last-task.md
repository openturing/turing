# Last task number — `T426`

> **Single source of truth for the next free task number.** The next new task is
> `T427`; after assigning it, bump the number above and the log line below.
>
> **Note (2026-06-16):** `T380` is an intentional **free gap** — Block R was
> created as `T381–T385` without bumping this counter (the exact hazard this file
> guards against), so the counter was reconciled to the real max when Block R's
> amplification (`T386–T393`) landed. Don't reuse `T380` unless you deliberately
> want to fill the gap.

## Why this file exists

T-numbers are **non-contiguous across blocks** — a block's header range (e.g.
"Block J follow-ups (T334–T373)") is *not* a reliable guide to what's free,
because a later block can consume numbers inside an earlier block's band. Real
example that caused a collision (2026-06-16): Block N (reranker) had already
shipped as **T337–T341**, sitting inside the Block J band, so "the next Block J
number" looked free but wasn't. Grepping three files (`ROADMAP.md`,
`CHANGELOG.md`, `IMPROVEMENTS.md`) is error-prone; this counter is authoritative.

## Rule

1. **Before creating a task**, read the number above and use `T<n+1>`.
2. **After creating it**, increment the number above and append a log line below.
3. Never infer the next number from a block header range or a `git log` scan.

## Log (most recent first)

- **T426** — AI Authoring on provider tool-calling/strict schema (F.10): retire `BeanOutputConverter`-over-free-text in `TurAiAuthoringService` (it 500s on prose-around-JSON / empty turns from gpt-4o-mini in the Intent authoring chat) in favor of a provider tool / strict `json_schema` so the response is validated JSON by construction. Band-aid recovery (brace-scan extract + graceful fallback) shipped first. deps: T173, T174. → IMPROVEMENTS §X.11 — 2026-06-26
- **T425** — `mcp` binding for Custom Tools (E.9): Groovy scripts can call an attached MCP server's tools via `mcp.call("dspace_search_items", [...])`, reusing the agent's MCP integration instead of a parallel direct-REST path. Motivated by the Aula-Relâmpago tool hitting the Insper Akamai WAF with the JDK `Java/<v>` UA (403) while the MCP server's `python-httpx` UA is allowlisted. → IMPROVEMENTS §XXI — 2026-06-25
- **T424** — Trigger/namespace-aware tool prioritization (E.9): boost an attached MCP server's tools when its `llmInstructions` triggers match the message + reserve a pre-filter quota so always-keep native tools can't starve attached MCP/custom tools to zero. Surfaced by a real incident where `dspace_*` was dropped (24 natives ≥ top-K) and gpt-4o-mini looped on `get_current_time`. → IMPROVEMENTS §XXI — 2026-06-25
- **T411–T423** — Block U: documentation refresh for `docs-turing` (webhooks, routines, cost-governance new pages; chat-flow/chat-analytics rewrites; experiments + human-in-the-loop split-outs; agent-eval/mcp-server/tool-calling/ai-agents/config-reference/token-usage updates). Plan: `docs-turing/_documentation-update-plan.md` — 2026-06-25
- **T410** — Chaotic chatflow HITL coverage: `humanApproval` node type in `flow-dsl` + dedicated approval sub-flow in the chaotic fixture + park/decision-resume IT (also fixed a leaking test cache-manager that broke every full-context test) — 2026-06-18
- **T409** — Lucene DSL executor: type-aware numeric/currency range + sort (Block R) — 2026-06-17
- **T407** — End-to-end NL→facet eval integration test (Block R) — 2026-06-16
- **T406** — SDK parity guard (CI contract test) (Block S) — 2026-06-16
- **T405** — SDK platform discovery helpers (Block S) — 2026-06-16
- **T404** — SDK semantic-chat client (Block S) — 2026-06-16
- **T403** — SDK DSL search client (Block S) — 2026-06-16
- **T402** — SDK spell-check client (Block S) — 2026-06-16
- **T401** — SDK multimodal slot-upload + flow resume (Block S) — 2026-06-16
- **T400** — SDK public "similar documents" client (Block S) — 2026-06-16
- **T399** — Cross-product tenancy conformance test (Block Q / parity) — 2026-06-16
- **T398** — Dumont: audit + adopt the shared tenancy (Block Q / parity) — 2026-06-16
- **T397** — Shio: adopt the full shared tenancy (Block Q / parity) — 2026-06-16
- **T396** — Turing: align to the expanded tenancy kernel (Block Q / parity) — 2026-06-16
- **T408** — Admin wizard: generate SN fields from sample documents (frontend for T387) — 2026-06-17
- **T395** — Expand `viglet-core-tenancy` to the full shared feature set (Block Q / parity) — 2026-06-16
- **T394** — Audit + design the canonical shared tenancy surface (Block Q / parity) — 2026-06-16
- **T393** — Lift manifest + grounding contract into `viglet-core` (Block R) — 2026-06-16
- **T392** — Grounded conversational catalog copilot (Block R) — 2026-06-16 (shipped 2026-06-17)
- **T391** — Structured-source onboarding wizard (Block R) — 2026-06-16
- **T390** — Multi-source duplicate clustering (Block R) — 2026-06-16 (shipped 2026-06-17)
- **T389** — Pluggable ranking pipeline + ranking explainability (Block R) — 2026-06-16 (shipped 2026-06-17)
- **T388** — Per-field coverage / completeness observability (Block R) — 2026-06-16 (shipped 2026-06-17)
- **T387** — LLM-assisted manifest derivation from sample documents (Block R) — 2026-06-16 (shipped 2026-06-17)
- **T386** — Manifest as versioned schema-as-code (Block R) — 2026-06-16
- _(T380 left as a free gap — see note above; T381–T385 are Block R, created earlier without a log entry)_
- **T379** — `viglet-core` HTTP/RestClient builder (Block Q) — 2026-06-16
- **T378** — `viglet-core` outbound-webhook + HMAC signing (Block Q) — 2026-06-16
- **T377** — `viglet-core` messaging + persistent-logging module (Block Q) — 2026-06-16
- **T376** — `viglet-core` config-var / global-settings service (Block Q) — 2026-06-16
- **T375** — `viglet-core` commons module (Block Q) — 2026-06-16
- **T374** — `viglet-core` content-extraction module (Block Q) — 2026-06-16
- **T373** — Ship Cloud default config as a GLOBAL seed ZIP (Block J follow-ups) — 2026-06-16
- **T372** — Platform-global BYO-infra is read-only in the CRUD (Block J follow-ups) — 2026-06-16
- **T371** — `viglet-core` Phase 2: promote to a public shared library (Block Q) — 2026-06-16
- **T370** — `viglet-core` tenancy module (Block Q) — 2026-06-16
- **T369** — `viglet-core` security module (Block Q) — 2026-06-16
- **T368** — `viglet-core` crypto + storage modules (Block Q) — 2026-06-16
- **T367** — `viglet-core` Turing submodule skeleton (Block Q) — 2026-06-16
