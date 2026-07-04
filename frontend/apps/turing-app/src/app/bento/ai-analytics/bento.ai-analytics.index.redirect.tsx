import { ROUTES } from "@/app/routes.const";
import { useAiAnalyticsNav } from "@/app/console/ai-analytics/use-ai-analytics-nav";
import { Navigate } from "react-router-dom";

/**
 * Index route for the Bento AI-Analytics suite — T564. Redirects to the first
 * visible sub-page once the LLM probe resolves (mirrors the console redirect),
 * so an LLM-enabled user lands on cost governance rather than the always-visible
 * chat analytics.
 */
export default function BentoAiAnalyticsIndexRedirect() {
  const { items, loaded } = useAiAnalyticsNav();
  if (!loaded || items.length === 0) return null;
  return <Navigate to={`${ROUTES.BENTO_AI_ANALYTICS}${items[0].url}`} replace />;
}
