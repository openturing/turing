/**
 * T785 — model advisor: constraint query + ranked recommendation.
 * Mirrors `TurLLMModelAdvisorService.AdvisorQuery` / `.Recommendation`.
 */

/** Constraint set sent to the advisor; every field is optional. */
export interface TurLlmAdvisorQuery {
  requiredCapabilities?: string[];
  requiredInputModalities?: string[];
  minContextWindow?: number | null;
  maxInputPricePer1M?: number | null;
  minIntelligenceIndex?: number | null;
  minTier?: string | null;
  kind?: string | null;
  limit?: number | null;
}

/** A ranked model recommendation. */
export interface TurLlmModelRecommendation {
  vendorId: string;
  modelId: string;
  label: string;
  tier?: string | null;
  contextWindow?: number | null;
  inputPricePer1M?: number | null;
  intelligenceIndex?: number | null;
  capabilities: string[];
}
