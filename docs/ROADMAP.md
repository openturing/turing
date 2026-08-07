# Turing — Roadmap (active backlog)

> **Written by [roadkeep](../roadkeep.toml), never by hand.** Open work only, one line per
> task; the `→ §` pointer is that task's design in [IMPROVEMENTS.md](IMPROVEMENTS.md).
> Shipped work lives in [CHANGELOG.md](CHANGELOG.md) and positioning in
> [STRATEGY.md](STRATEGY.md); the markers and limits are `roadkeep.toml`'s. Blocks are
> reused, not minted — `roadkeep brief` picks the next task and states why.

## Block BJ — Configurable, per-language NL ranking lexicon (Lucene analysis) (§LVIII)

## Block BN — Content-fit tuning loop: model-aware, enforceable, and it tells you (§LXI)

- 💭 **T831** (deps: T704 ✅) **Apply a persona adjustment (human-confirmed)** — for PERSONA items only, an opt-in Apply action renders the suggestion as a field-level diff over the audience facet and writes it only after the operator confirms each field. → §LXI.7

## Block J follow-ups — Cloud SaaS hardening (T334–T373)

- 📋 **T373** (deps: —) **Ship Cloud default config as a GLOBAL seed ZIP** — guarantee the export keeps `tenantId = null` for the 6 BYO-infra types and bake the seed ZIP into the image, so one seeded default is visible to every auto-provisioned tenant. → §XIV.8.7

## Block BV — The quality gate stops at the package boundary (§LXIX)

- ⏳ **T902** (deps: roadkeep RK377) **Two governed prose files declare the same 13 section anchors** — both halves of the fix exist upstream but not in the 0.1.285 plugin this repo runs, whose config parser would reject the key the fix declares. → §LXIX.8

## Block AN — Security hardening (post-audit, §XXXVII)

- 📋 **T893** (deps: —) **The public search page is not in the Keycloak branch's anonymous list** — `/sn/**` is permitted only under local auth, so a Keycloak deployment sends a visitor to SSO login for a page whose every API is already public. → §XXXVII.17
- 📋 **T894** (deps: —) **The permitAll list is written twice and nothing holds the copies equal** — forty matchers are spelled once per auth branch by hand, which is what produced both the missing answer endpoint and the missing search page. → §XXXVII.18

## Block AC — JPA caching strategy: decouple cache from entities

- 📋 **T898** (deps: —) **The search snapshot caches six kinds of JPA entity, which T486 forbids** — T486's lint only walks repositories, so a service-level `@Cacheable` holding entities passes — and this one already paid for it once with a workaround for the exact failure. → §LXXI.1

## Block AV — POST-native search & link-free response contract (§XLV)

- 📋 **T936** (deps: T884 ✅) **`useTuringFacets` renders the primary rail only** — It reads `structured.facets` and `widget.facet` and neither `secondaryFacets` nor `widget.secondaryFacet`, so a site's secondary facets are invisible to every SDK consumer. → §XLV.6
- 📋 **T937** (deps: T880 ✅) **The legacy `similar` is as unreadable as the structured one was** — `TurWidget.similar` is typed `string` while the server sends a list of similar documents, so the legacy transport carries a value no typed consumer can iterate. → §XLV.7
- 📋 **T938** (deps: T880 ✅) **The no-links guard passes because its fixture is incomplete** — `testStructuredResponseCarriesNoLinks` asserts the JSON holds no `"link"`, but a spotlight document carries one, so the block's central claim holds only while no spotlight is in the response. → §XLV.8
- 📋 **T939** (deps: T885 ✅) **Nothing stops the README teaching the broken shape again** — T885 guards app source, so a documentation fence may still show `data.widget.facet.map` — which is how T883's bug survived three releases in the first place. → §XLV.9

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
