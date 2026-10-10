# Turing — Roadmap (active backlog)

> **Written by [roadkeep](../roadkeep.toml), never by hand.** Open work only, one line per
> task; the `→ §` pointer is that task's design in [IMPROVEMENTS.md](IMPROVEMENTS.md).
> Shipped work lives in [CHANGELOG.md](CHANGELOG.md) and positioning in
> [STRATEGY.md](STRATEGY.md); the markers and limits are `roadkeep.toml`'s. Blocks are
> reused, not minted — `roadkeep brief` picks the next task and states why.

## Block BZ — Indexing API trust for connectors: key auth, scripted login, request validation

- 📋 **T1063** (deps: —) **Basic auth with a local admin password answers 401 though httpBasic is enabled, so scripts cannot call admin APIs** — Scripts and CI must mimic the console's cookie and XSRF login to provision a site, which nothing documents. → §CX.3
- 📋 **T1064** (deps: —) **Sites on the embedded Lucene engine silently ignore ranking expressions, recency boost included** — The API and console accept the expressions, so a Lucene site ranks without them and nobody is told. → §CX.4
- 📋 **T1065** (deps: —) **No per-field search weight and no field-exists ranking condition, so title cannot outweigh body text** — Relevance for structured catalogs depends on which field matched, and connectors cannot declare it. → §CX.5
- 📋 **T1066** (deps: —) **Multi-word synonym terms such as home office never match on Lucene sites, though single-word ones do** — Job and product vocabularies are full of two-word terms, and the API accepts them without saying they do nothing. → §CX.6
- 📋 **T1067** (deps: —) **Spell-check answers an empty correction for a correct word that is missing from the index** — A did-you-mean link to an empty query, or a fix that blanks the query, is worse than no suggestion. → §CX.7

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
