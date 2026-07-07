# Atlas Store — coverage audit (Block Y / §XXIV.1)

The T457 coverage audit: reconciles the showcase against the §XXIV.1 "what the
sample must stress" matrix. ✅ = exercised in the app; 🔌 = wired client-side but
lights up only against a backend with the matching feature enabled; 📄 =
documented/asset (not a UI surface). Gaps are listed at the end.

| Area | Feature | Status | Where |
|---|---|---|---|
| **Indexing/schema** | Manifest / typed fields (CURRENCY/FLOAT/INT/BOOL/DATE + multi-valued) | ✅ | `export/export.json`, `gen-catalog.mjs` |
| | Field provisioning (T382) / manifest (T386) | ✅ | import ZIP → SN site |
| | LLM-assisted derivation (T387) | 📄 | optional at import time (not seeded) |
| | Per-field coverage (T388) | 📄 | admin surface — `/ops` tour |
| | Import/export ZIP | ✅ | `zip-dist.mjs` → `atlas-store.zip` |
| **Search** | Faceted search | ✅ | storefront `FacetSidebar` |
| | URL-synced search | ✅ | `TuringProvider` `urlSync` |
| | Autocomplete | ✅ | `TuringSearchField.Dropdown` |
| | Spell-check | ✅ | `SpellSuggestion` (T452) |
| | Tabs (T247) | ✅ | category tabs (`useTuringTabs`) |
| | Sort options | ✅ | `SortSelect` (5 custom sorts) |
| | Similar / MLT (T400) | ✅ | product page `SimilarRail` |
| | Click tracking / CTR | ✅ | `useTuringClickTracking` |
| | Hybrid RRF ranking (T383) | 🔌 | `snRankingMode` server toggle (README) |
| | Pluggable reranker (Block N) | 🔌 | `HYBRID_RRF_RERANK` server toggle |
| **RAG chat** | Token streaming SSE | 🔌 | `useTuringChat` (needs agent) |
| | RAG sources/citations (T152/T292) | 🔌 | `TuringSourceChips` |
| | Option chips | 🔌 | message `options` |
| | Native forms (T107) | 🔌 | `NativeForm` + `submitForm` |
| | Slots read/subscribe/delta (T63) | 🔌 | `SlotsStrip` (`useTuringSlots`) |
| | Multimodal slot upload + vision (T401) | 🔌 | paperclip (`useTuringSlotUpload`) |
| | Workspace panel (T113) | 🔌 | `TuringWorkspacePanelView` |
| | Rich content md/html/d2 (T295–T298) | ✅ | `TuringRichContent` + `D2Block` |
| **Agent power** | Skills in chat (T323/T324/T325) | ✅/📄 | `skills/returns-rma/` folder |
| | Code interpreter (T80/T82/T83) | 🔌 | via `get_cart` ("total my cart") |
| | Live tool-call activity (T436) | 🔌 | `TuringToolActivity` |
| | Client tools (T438/T439) | ✅ | cart tools (`cart-tools.ts`) |
| | Answer-as-app generative UI (T440/T442) | 🔌 | `useAnswerAsApp` |
| **Creative bets** | Co-browse search (T443) | ✅ | `useCoBrowseSearch` |
| | Glass-box ranking (T444) | 🔌 | "Why #1?" → `explain_ranking` |
| | Ambient copilot (T445) | 🔌 | `ProactiveToast` |
| | Cross-conversation memory (T446) | 🔌 | memory panel (`useTuringUserMemory`) |
| | Agent→agent handoff (T448) | 🔌 | visible via tool activity |
| | Skills-ship-UI (T449) | 🔌 | skill UI tools (registry) |
| | Embeddable action widget (T450) | ✅ | `/embed-demo` (`createHostActions`) |
| **Voice/multimodal** | Voice agent (T147) | ✅ | chat mic (`useTuringVoice`) |
| | Search by image | ✅ | hero → multimodal upload |
| **Ops/admin** | Sentiment trajectory (T87) | 📄 | `/ops` tour |
| | Tool latency p95 (T88) | 📄 | `/ops` tour |
| | SSE channel debug (T90) | 📄 | `/ops` tour |
| | Agent eval + golden sets (Block K) | 📄 | `/ops` tour |
| | Self-tuning (T447) | 📄 | `/ops` tour |
| | Cost/token governance (Block L) | 📄 | `/ops` tour |
| | Token-budget COMPACT (T123) | 📄 | server-side |
| **Platform** | Multi-tenancy (T333) | 🔌 | per-deploy (tenancy flag) |
| | MCP server exposure (Block I) | 📄 | `mcp/claude-desktop.json` |
| | Webhooks (T62) | 🔌 | per-agent server config |
| | Routines/cron | 📄 | admin surface |
| **SDK surfaces** | Vanilla `@viglet/turing-sdk` | ✅ | `/embed-demo` host actions |
| | React SDK hooks | ✅ | throughout |
| | Headless `@viglet/turing-react-ui` | ✅ | brand skin + chat components |
| | CLI `turing dev`/`eval` (E.7) | 📄 | README (clone-to-running) |

## e2e smoke test

`turing-app/src/test/java/com/viglet/turing/showcase/TurShowcaseSmokeIT.java`
boots the backend on a random port, imports the showcase ZIP into an embedded
Lucene index, and asserts the REST contract: 200-product wildcard count, typed
default fields, category/brand facets, keyword search, sort options and
`chat/enabled → NO_GENAI`. It uncovered (and the fix shipped) a context-startup
circular dependency in the T448 handoff service.

## Known gaps / follow-ups

- **No frontend test runner** in the showcase app yet (the backend smoke IT
  covers the contract). A vitest smoke for the storefront render is a follow-up.
- **🔌 features need a configured backend** (agent + LLM + embeddings) to light
  up live; the offline Lucene seed is search-only by design and the UI shows
  honest "configure GenAI" notices.
- **Self-referential demo** (T483) — seeding the demo with Turing's own docs is
  tracked in Block AB, not here.
