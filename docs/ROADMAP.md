# Turing — Roadmap (active backlog)

> **Written by [roadkeep](../roadkeep.toml), never by hand.** Open work only, one line per
> task; the `→ §` pointer is that task's design in [IMPROVEMENTS.md](IMPROVEMENTS.md).
> Shipped work lives in [CHANGELOG.md](CHANGELOG.md) and positioning in
> [STRATEGY.md](STRATEGY.md); the markers and limits are `roadkeep.toml`'s. Blocks are
> reused, not minted — `roadkeep brief` picks the next task and states why.

## Block CA — Search response fields arrive as they were indexed

- 📋 **T1074** (deps: —) **Search returns a document's text cut short with no marker, inside a UTF-8 character, so accented text arrives corrupted** — Any page that shows a result's text prints a replacement character and loses the rest of the description. → §CXI.1
- 📋 **T1075** (deps: —) **On Lucene a filter query is one exact term, so a date or number range matches nothing, and no relative date facet exists** — No recency or salary-from filter can be built on a Lucene site, and the empty result never says the filter was misread. → §CXI.2
- 📋 **T1076** (deps: —) **Autocomplete on Lucene suggests words from every stored field, so slugs, URLs and ids come back as suggestions** — A visitor is offered terms nobody types, which crowd out the real completions on a small phone list. → §CXI.3
- 📋 **T1077** (deps: —) **On Lucene a search request's fqOr and fqItemOperator are echoed and ignored, and no endpoint returns a document by id** — A detail page cannot fetch a job by one of several candidate ids in one call, and a silent no-op reads as success. → §CXI.4
- 📋 **T1078** (deps: —) **Similar documents on a Lucene site answer 500: getSimilarDocuments opens the index with a null path** — No related-items rail can be built on Lucene, and the error gives the caller no reason. → §CXI.5
- 📋 **T1079** (deps: —) **The JS SDK's API calls take no timeout or AbortSignal, so a stalled Turing hangs every server-rendered page** — A caller cannot bound a search, and an outage turns into requests that never finish instead of an error page. → §CXI.6

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
