# Turing — Roadmap (active backlog)

> **Written by [roadkeep](../roadkeep.toml), never by hand.** Open work only, one line per
> task; the `→ §` pointer is that task's design in [IMPROVEMENTS.md](IMPROVEMENTS.md).
> Shipped work lives in [CHANGELOG.md](CHANGELOG.md) and positioning in
> [STRATEGY.md](STRATEGY.md); the markers and limits are `roadkeep.toml`'s. Blocks are
> reused, not minted — `roadkeep brief` picks the next task and states why.

## Block J.10 — Viglet AI: the managed LLM onboarding credit (§XCIV)

## Block BX — The bento layer leaves the product it grew in (§XCVI)

- 📋 **T996** (deps: viglet-design-system VDS23) **tile tones and the brand hue are named as Tailwind classes inside components this product no longer owns** — The token swap must be provably invisible here, or one look across products arrives as a regression in this one. → §XCVI.9
- 📋 **T999** (deps: viglet-design-system VDS20) **CONVENTIONS.md is the layer's authoring contract and it lives in the product that no longer owns the layer** — Two copies of a contract drift exactly as two copies of a component do. → §XCVI.12
- ⏳ **T1000** (deps: T1008 ⏳) **40 console pages still render beside the 118 bento ones, so two chromes and two component sets stay alive** — The package cannot deprecate its console entry point while this product still declares four of its components. → §XCVI.13
- 📋 **T1001** (deps: viglet-design-system VDS25) **nothing here proves a page renders the same after its components moved out of the repository** — The whole block is a claim about appearance, and appearance is the one thing this suite never checks. → §XCVI.14
- ⏳ **T1008** (deps: viglet-design-system SubPage props) **four console-chrome components stay declared here because each package copy dropped something this product renders** — The package's SubPage drops outletContext, which shares one form instance across the persona editor's section sub-pages. → §XCVI.18

## Block AR — Live-demo search & chat quality (Lucene parity gaps, §XLI)

- ⏳ **T1004** (deps: —) **site chat emits the whole answer in one SSE content event, so no consumer can render it progressively** — The measurement that opened this has not been repeated against the demo. → §XCVII

## Done when — T996

- **The token swap is invisible in this product** The render digest over a fixed set of
  surfaces is identical before and after, except where the brand accent token is
  deliberately different.

## Done when — T999

- **The authoring contract is a pointer, not a copy** CONVENTIONS.md here names the
  package's contract and keeps only the rules genuinely about this application's routes,
  privileges and entities.

## Done when — T1000

- **One chrome renders and the console routes are gone** The forty console pages are
  migrated or retired, the redirect and the console shell are removed, and nothing
  imports a console-era export.

## Done when — T1001

- **A page is proved unchanged by the move, not assumed** This product's token set feeds
  the cross-product digest, it runs over a hero, a form section, an entity tile, a
  mosaic and a scrolled save bar, and a difference fails the build.

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
