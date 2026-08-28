# Turing — Roadmap (active backlog)

> **Written by [roadkeep](../roadkeep.toml), never by hand.** Open work only, one line per
> task; the `→ §` pointer is that task's design in [IMPROVEMENTS.md](IMPROVEMENTS.md).
> Shipped work lives in [CHANGELOG.md](CHANGELOG.md) and positioning in
> [STRATEGY.md](STRATEGY.md); the markers and limits are `roadkeep.toml`'s. Blocks are
> reused, not minted — `roadkeep brief` picks the next task and states why.

## Block J.10 — Viglet AI: the managed LLM onboarding credit (§XCIV)

## Block BX — The bento layer leaves the product it grew in (§XCVI)

## Block R.1 — Event-driven ingestion from enterprise CMS platforms (AEM as the reference)

- 📋 **T1051** (deps: —) **Nothing bounds the total buckets a Content AI search asks for, which Adobe fails the whole request over** — The 10,000 cap is prose in the aggregates description, not a schema keyword, so T1048's validator cannot reach it and T1049 left it: six aggs at the clamped 2000 is 12,000. → §XX.28
- 📋 **T1052** (deps: —) **The content-source listing the source catalog is proven against is a page nobody checked against Adobe** — T1048 and T1050 made the search stub a contract both ways; GET /content-sources kept its hand-written fixture, and the bundle declares ContentSourceListPage for it. → §XX.29

## Block BV — The quality gate stops at the package boundary

## Non-goals (do NOT add as tasks)

Binding constraints — see [IMPROVEMENTS.md](IMPROVEMENTS.md) §I.6 (What NOT to
refactor) and §I.7 (Restrictions) for the full text. Summary:

- Don't refactor the auto-trigger router, continuation logic, A/B assignment,
  sub-flow descent/ascent, SSE bus, defense-in-depth checks, silent telemetry,
  anti-injection / CPF-CNPJ / yes-no normalization, `TurToolCallbackPipeline.decorate`,
  or `summarizeVariables` cap — these are product, not patches.
- Structured output (LLM → JSON) is **not** a path — use provider-side tool-calling schemas.
- Don't rewrite the chat-flow DSL (`ChatFlowGraph` / `ChatFlowNode` shape) — only execution.
- Keep Spring AI `ChatModel.call()` + `internalToolExecutionEnabled(true)` (no framework swap).
- Heuristic and LlmJudge strategies coexist.
- No "auto-detect" middleware guessing node intent (toolFree, captureMode) — that's authorship.
- No in-process ONNX/DJL Whisper transcription runtime (the retired `LOCAL_ONNX` backend, ex-T694). The supported fully-local path is `OPENAI_COMPATIBLE` pointed at a self-hosted server (e.g. faster-whisper-server) — the platform won't bundle a fragile in-process Whisper dependency (T193 Jlama discipline).
