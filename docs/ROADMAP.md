# Turing — Roadmap (active backlog)

> **Written by [roadkeep](../roadkeep.toml), never by hand.** Open work only, one line per
> task; the `→ §` pointer is that task's design in [IMPROVEMENTS.md](IMPROVEMENTS.md).
> Shipped work lives in [CHANGELOG.md](CHANGELOG.md) and positioning in
> [STRATEGY.md](STRATEGY.md); the markers and limits are `roadkeep.toml`'s. Blocks are
> reused, not minted — `roadkeep brief` picks the next task and states why.

## Block J follow-ups — Cloud SaaS hardening (§XIV.8)

- 📋 **T949** (deps: —) **A corrupt seed ZIP permanently disables seeding for that database** — The runner catches each ZIP's failure and writes the one-shot EXPORT_AUTO_IMPORT marker regardless, so a boot that imported nothing looks exactly like one that imported everything. → §XIV.8.8
- 📋 **T950** (deps: —) **An import that created no site still reports one, named unknown** — importSNSiteFromExportFile returns the literal unknown for a site-less bundle, and the caller reads any non-null name as proof a site landed. → §XIV.8.9

## Block BV — The quality gate stops at the package boundary (§LXIX)

- ⏳ **T902** (deps: roadkeep RK377) **Two governed prose files declare the same 13 section anchors** — both halves of the fix exist upstream but not in the 0.1.285 plugin this repo runs, whose config parser would reject the key the fix declares. → §LXIX.8
- 📋 **T944** (deps: —) **A README fence hands a reader a prop the component does not have** — The T939 guard reads the fences for one rule and nothing compiles them, so `TuringResultList`s documented `renderItem` outlived the rename to `itemComponent`. → §LXIX.29

## Block AN — Security hardening (post-audit, §XXXVII)

- 📋 **T951** (deps: —) **The SPA file server decodes a path and concatenates it, checking nothing** — Encoded traversal clears the firewall because the encoded-slash allowance is on, so only a containment check in another repository stands between an anonymous request and an arbitrary storage key. → §XXXVII.19

## Block AC — JPA caching strategy: decouple cache from entities

- ⏳ **T898** (deps: —) **Two of the snapshot's six cached entity kinds resist projection** — Four converted cleanly; TurSNSiteFieldExt reaches finders used in 17 places across 15 files and TurSNSite is a JPA FK on a write, so the rest is a facet-contract change rather than a fifth projection. → §LXXI.1

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
