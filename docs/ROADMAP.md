# Turing — Roadmap (active backlog)

> **Written by [roadkeep](../roadkeep.toml), never by hand.** Open work only, one line per
> task; the `→ §` pointer is that task's design in [IMPROVEMENTS.md](IMPROVEMENTS.md).
> Shipped work lives in [CHANGELOG.md](CHANGELOG.md) and positioning in
> [STRATEGY.md](STRATEGY.md); the markers and limits are `roadkeep.toml`'s. Blocks are
> reused, not minted — `roadkeep brief` picks the next task and states why.

## Block J.10 — Viglet AI: the managed LLM onboarding credit (§XCIV)

## Block BX — The bento layer leaves the product it grew in (§XCVI)

## Block BV — The quality gate stops at the package boundary

## Block R.1 — Event-driven ingestion from enterprise CMS platforms (AEM as the reference)

- 📋 **T1045** (deps: —) **A misnamed Content AI source returns zero results with nothing saying the name is wrong** — Adobe answers an unknown source with totalResults 0 and indexExists answers from configuration, so a typo and an empty index look identical. → §XX.22
- 📋 **T1046** (deps: —) **A DSL query on an engine with no executor is silently answered by the Solr one** — TurDslSearchService falls back to Solr, which builds a Solr client from the site's endpoint URL and swallows the failure, so the API answers as if nothing matched. → §XX.23
- 📋 **T1047** (deps: T1046) **The vectorless copilot is unavailable on a federated site, the one answer surface it should fit** — The copilot runs through the DSL layer, which has no Content AI executor, so a customer gets grounded answers only by authoring a Groovy tool. → §XX.24

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
