import { ROUTES } from "@/app/routes.const";
import { Navigate } from "react-router-dom";
import { useAiAnalyticsNav } from "./use-ai-analytics-nav";

/**
 * Index route for "AI Analytics & Costs": redirects to the first visible
 * sub-page once the LLM probe resolves, so an LLM-enabled user lands on token
 * usage rather than the always-visible chat analytics.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
export default function AiAnalyticsIndexRedirect() {
  const { items, loaded } = useAiAnalyticsNav();
  if (!loaded || items.length === 0) return null;
  return <Navigate to={`${ROUTES.AI_ANALYTICS_ROOT}${items[0].url}`} replace />;
}
