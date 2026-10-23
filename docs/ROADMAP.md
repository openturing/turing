# Turing — Roadmap (active backlog)

> **Written by [roadkeep](../roadkeep.toml), never by hand.** Open work only, one line per
> task; the `→ §` pointer is that task's design in [IMPROVEMENTS.md](IMPROVEMENTS.md).
> Shipped work lives in [CHANGELOG.md](CHANGELOG.md) and positioning in
> [STRATEGY.md](STRATEGY.md); the markers and limits are `roadkeep.toml`'s. Blocks are
> reused, not minted — `roadkeep brief` picks the next task and states why.

## Block CD — turing.viglet.org relevance pass (2026-10)

## Block CE — Semantic retrieval for SN sites

- ⏳ **T1103** (deps: —) **SN search matches only words, so a query describing the work misses jobs titled by role** — Recall still skips filtered requests and later pages, facets stay lexical, and the blend weight and embedded fields are not per site. → §CXVI

## Block CF — What the SN site manifest cannot declare

- 📋 **T1104** (deps: —) **The SN site manifest cannot declare a custom sort, so a sort a connector needs is a console step per site** — Every environment needs a hand step, and a site rebuilt from the manifest silently loses the sort. → §CXVII

## Done when — T1103

- **A filtered query recalls only documents inside its filter** A test runs `trabalho
  com crianças` with an `fq` that holds `auxiliar de creche` and gets it back, and runs
  it with an `fq` that excludes it and does not.
- **Blend weight and embedded fields are set per site** The site config names the fields
  embedded at index time and a vector-versus-lexical weight, and a test shows each one
  changing the order or the embedded text.

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
