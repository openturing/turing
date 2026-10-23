# Turing — Roadmap (active backlog)

> **Written by [roadkeep](../roadkeep.toml), never by hand.** Open work only, one line per
> task; the `→ §` pointer is that task's design in [IMPROVEMENTS.md](IMPROVEMENTS.md).
> Shipped work lives in [CHANGELOG.md](CHANGELOG.md) and positioning in
> [STRATEGY.md](STRATEGY.md); the markers and limits are `roadkeep.toml`'s. Blocks are
> reused, not minted — `roadkeep brief` picks the next task and states why.

## Block CD — turing.viglet.org relevance pass (2026-10)

## Block CG — Search-engine plugin parity (Solr, Lucene, Elasticsearch)

- 🛠 **T1107** (deps: —) **No test runs one SN request against live Solr, Lucene and Elasticsearch, so parity is checked on request shape only** — Solr is not in the conformance table: it needs Turing's own Solr configset and a TurSolrQueryBuilder wired outside Spring. → §CXX
- 📋 **T1108** (deps: —) **On Docker 29 every container IT is silently skipped, because Testcontainers' default API version is refused** — The engine ITs that prove search parity report green without ever starting Solr or Elasticsearch. → §CXXI
- 📋 **T1110** (deps: —) **Solr 10 refuses the core-creation request Turing sends, so a new SN site locale gets no core** — The v2 create body Turing posts is the one Solr 10 rejects, and the failure is only logged. → §CXXIII

## Done when — T1107

- **Solr answers every row of the engine conformance table** TurSNEngineConformanceIT
  runs each row against a Solr core built from Turing's own configset and asserts the
  same ids as Lucene and Elasticsearch.

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
