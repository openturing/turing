# Turing — Roadmap (active backlog)

> **Written by [roadkeep](../roadkeep.toml), never by hand.** Open work only, one line per
> task; the `→ §` pointer is that task's design in [IMPROVEMENTS.md](IMPROVEMENTS.md).
> Shipped work lives in [CHANGELOG.md](CHANGELOG.md) and positioning in
> [STRATEGY.md](STRATEGY.md); the markers and limits are `roadkeep.toml`'s. Blocks are
> reused, not minted — `roadkeep brief` picks the next task and states why.

## Block CA — Search response fields arrive as they were indexed

- 📋 **T1085** (deps: —) **No SN endpoint returns the filters it extracts from a natural-language request as data a search can use** — A site cannot turn a visitor's description into its own ranked, faceted results page. → §CXI.10
- 📋 **T1086** (deps: —) **The JS SDK's chat conversation cannot send filters, though the endpoint takes them** — An assistant cannot be scoped to the visitor's current search without bypassing the SDK. → §CXI.11
- 📋 **T1087** (deps: —) **On a vectorless site chat/enabled says ready while chat/conversation answers that AI is not enabled** — A site turns its assistant on from the readiness check and then shows visitors an error. → §CXI.12
- 📋 **T1088** (deps: —) **A fresh install with the seed store skips the RAG bootstrap, so no embedding model exists and chat stays off** — The out-of-the-box chat the startup promises never happens on the image the seed ships with. → §CXI.13
- 📋 **T1089** (deps: —) **With RAG on, an import whose document has a null field is refused whole: metadata cannot have null values** — Turning on chat for a site stops its connector's imports, search included. → §CXI.14

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
