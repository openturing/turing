# Turing — Roadmap (active backlog)

> **Written by [roadkeep](../roadkeep.toml), never by hand.** Open work only, one line per
> task; the `→ §` pointer is that task's design in [IMPROVEMENTS.md](IMPROVEMENTS.md).
> Shipped work lives in [CHANGELOG.md](CHANGELOG.md) and positioning in
> [STRATEGY.md](STRATEGY.md); the markers and limits are `roadkeep.toml`'s. Blocks are
> reused, not minted — `roadkeep brief` picks the next task and states why.

## Block J.10 — Viglet AI: the managed LLM onboarding credit (§XCIV)

## Block BX — The bento layer leaves the product it grew in (§XCVI)

- ⏳ **T1000** (deps: T1008 ⏳) **40 console pages still render beside the 118 bento ones, so two chromes and two component sets stay alive** — The package cannot deprecate its console entry point while this product still declares four of its components. → §XCVI.13
- ⏳ **T1008** (deps: viglet-design-system VDS76) **four console-chrome components stay declared here because each package copy dropped something this product renders** — The package's SubPage drops outletContext, which shares one form instance across the persona editor's section sub-pages. → §XCVI.18
- ⏳ **T1013** (deps: —) **fifteen bento pages write a hero gradient inline, using eight colour pairs no tone offers** — Eight pairs remain, each a combination no tone offers rather than a shade of drift, so closing one changes a hue. → §XCVI.20

## Block AR — Live-demo search & chat quality (Lucene parity gaps, §XLI)

- ⏳ **T1004** (deps: —) **site chat emits the whole answer in one SSE content event, so no consumer can render it progressively** — The mode that streams it exists; the demo runs a build from before it, so the last acceptance bullet waits on a deploy. → §XCVII

## Block BV — The quality gate stops at the package boundary

- ⏳ **T1018** (deps: —) **31 files still fetch with useState and useEffect, and most need a query hook that does not exist** — Thirty-one files still hold a data-loading-error triple of their own, and most need a hook that does not exist yet. → §CIII.4

## Done when — T1000

- **One chrome renders and the console routes are gone** The forty console pages are
  migrated or retired, the redirect and the console shell are removed, and nothing
  imports a console-era export.

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
