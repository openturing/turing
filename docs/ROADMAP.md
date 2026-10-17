# Turing — Roadmap (active backlog)

> **Written by [roadkeep](../roadkeep.toml), never by hand.** Open work only, one line per
> task; the `→ §` pointer is that task's design in [IMPROVEMENTS.md](IMPROVEMENTS.md).
> Shipped work lives in [CHANGELOG.md](CHANGELOG.md) and positioning in
> [STRATEGY.md](STRATEGY.md); the markers and limits are `roadkeep.toml`'s. Blocks are
> reused, not minted — `roadkeep brief` picks the next task and states why.

## Block CB — LLM gateway parity with the OpenAI wire

- 📋 **T1099** (deps: —) **The gateway ignores temperature, top_p, max_tokens and stop sent by the client, so the instance defaults always win** — A client pinning temperature 0 or a token cap gets a 200 and different sampling than it asked for. → §CXIII.3
- 📋 **T1100** (deps: —) **A streamed gateway call hit by a provider 429 answers 200 with the error as assistant text, so clients never back off** — Streaming OpenAI clients get a normal-looking answer whose content is an error message instead of a RateLimitError. → §CXIII.4

## Block CD — turing.viglet.org relevance pass (2026-10)

- 💭 **T1097** (deps: viglet.org W42) **The site loads Google Analytics without consent, while viglet.org asks first** — Three Viglet sites should treat one visitor under one privacy rule, decided once. → §CXV.5
- 📋 **T1101** (deps: —) **The marketing site still lists Azure OpenAI as a supported LLM provider, which Turing removed** — A buyer who picks Turing for Azure finds no such provider in the console, and the claim undercuts every other one on the page. → §CXV.7

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
