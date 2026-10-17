# Turing — Roadmap (active backlog)

> **Written by [roadkeep](../roadkeep.toml), never by hand.** Open work only, one line per
> task; the `→ §` pointer is that task's design in [IMPROVEMENTS.md](IMPROVEMENTS.md).
> Shipped work lives in [CHANGELOG.md](CHANGELOG.md) and positioning in
> [STRATEGY.md](STRATEGY.md); the markers and limits are `roadkeep.toml`'s. Blocks are
> reused, not minted — `roadkeep brief` picks the next task and states why.

## Block CB — LLM gateway parity with the OpenAI wire

- 📋 **T1092** (deps: —) **The gateway answers 502 api_error when the provider says 429 out of credits, so clients cannot tell quota from outage** — OpenAI clients back off, alert or retry by status and error type, and a quota hit read as an outage gets the wrong response. → §CXIII.2
- 📋 **T1099** (deps: —) **The gateway ignores temperature, top_p, max_tokens and stop sent by the client, so the instance defaults always win** — A client pinning temperature 0 or a token cap gets a 200 and different sampling than it asked for. → §CXIII.3

## Block CD — turing.viglet.org relevance pass (2026-10)

- 📋 **T1093** (deps: —) **The AEM page ranks 36th to 44th for the AEM queries, and almost nothing links to it** — Google already picked /aem-search/ as the Viglet answer for AEM; inbound links and the searched words in its title are what it lacks. → §CXV.1
- 📋 **T1094** (deps: —) **The security page wins data-residency questions at position 1, yet its title never says so** — Self-hosting is where Turing wins outright, and these are the questions an assistant asks for that buyer. → §CXV.2
- 📋 **T1095** (deps: —) **Compare, migrate, persona and the four features pages had no impression in 90 days** — Thin pages that repeat the home, and deep ones nothing links to, never enter the results. → §CXV.3
- 📋 **T1096** (deps: —) **The compare page never names Lucidworks, though Coveo vs Lucidworks is the comparison people search** — The demand is already proven by the docs.viglet.org post, and the product comparison page misses it. → §CXV.4
- 💭 **T1097** (deps: viglet.org W42) **The site loads Google Analytics without consent, while viglet.org asks first** — Three Viglet sites should treat one visitor under one privacy rule, decided once. → §CXV.5
- 💭 **T1098** (deps: —) **The sitemap lists llms.txt and the Storybook beside the product pages** — A sitemap should offer the search engines the pages meant to rank, not every file the site serves. → §CXV.6

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
