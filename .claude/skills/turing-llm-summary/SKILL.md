---
name: turing-llm-summary
description: The reusable Turing LLM Summary Service (cached AI insights) — TurLlmSummaryService.generate(cacheKey, data, systemPrompt, regenerate) returning SummaryResult, its use of TurLlmCacheService and the default LLM, the two current callers (SN site insights, System Info insights), and the frontend regenerate pattern (canRegenerate gated by llmCacheRegenerate). Use whenever adding a new cached LLM-summary/insights feature or working with TurLlmSummaryService, SN insights, or System Info insights.
---

# LLM Summary Service (Cached AI Insights)

Reusable service for generating cached LLM summaries with regenerate support:

**Backend** (`system/TurLlmSummaryService`):

- `generate(cacheKey, data, systemPrompt, regenerate)` → `SummaryResult(success, error, content, canRegenerate)`
- Uses `TurLlmCacheService` for in-memory caching (respects Global Settings TTL)
- Uses default LLM from `TurGlobalSettingsService.getDefaultLlmId()`
- `isAvailable()` — checks if default LLM is configured and enabled

**Used by**:

- `TurSNSiteSummaryAPI` — `GET /api/sn/{id}/summary?regenerate=false` for SN site insights
- `TurSystemInfoAPI` — `GET /api/system/info/insights?regenerate=false` for system info AI insights
- Both follow same pattern: collect data → call `llmSummaryService.generate()` → return `SummaryResult`

**Frontend pattern** (same for both SN insights and System Info insights):

```typescript
const response = await axios.get<InsightsResponse>(url, {
  params: regenerate ? { regenerate: true } : {},
});
// InsightsResponse: { success, error, content, canRegenerate }
```

- Show regenerate button when `canRegenerate=true` (controlled by `llmCacheRegenerate` in Global Settings)
- System Info uses a resizable side panel; SN insights uses a full page
