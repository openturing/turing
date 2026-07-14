import { useAiAgent } from "@/api/queries/ai-agent.queries";
import { ROUTES } from "@/app/routes.const";
import { IntentSettingsForm } from "@/components/intent/intent.settings.form";
import { LoadProvider } from "@/components/loading-provider";
import type { TurIntent } from "@/models/intent/intent.model";
import { TurIntentService } from "@/services/intent/intent.service";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

const turIntentService = new TurIntentService();

/**
 * Bento intent detail — the console IntentSettingsForm rendered with bento
 * chrome (frosted sections + save bar) under the shared agent sub-hero. Save,
 * cancel and delete all resolve inside /bento via `baseRoute`.
 */
export default function BentoAIAgentIntentPage() {
  const { id: agentId, intentId } = useParams() as { id: string; intentId: string };
  const { t } = useTranslation();
  const isNew = intentId === "new";
  const [intent, setIntent] = useState<TurIntent | undefined>(isNew ? ({ actions: [] } as unknown as TurIntent) : undefined);
  const [error, setError] = useState<string | null>(null);
  const { data: agent } = useAiAgent(agentId);

  useEffect(() => {
    if (!agentId || isNew) return;
    turIntentService
      .get(agentId, intentId)
      .then(setIntent)
      .catch(() => setError(t("common.connectionError", { resource: t("intent.title").toLowerCase() })));
  }, [agentId, intentId, isNew, t]);

  return (
    <LoadProvider
      checkIsNotUndefined={intent}
      error={error}
      tryAgainUrl={`${ROUTES.BENTO_AI_AGENT_INSTANCE}/${agentId}/intent/${intentId}`}
    >
      {intent && (
        <IntentSettingsForm
          value={intent}
          isNew={isNew}
          agentId={agentId}
          agentTitle={agent?.title ?? t("aiAgent.title")}
          chrome="bento"
          baseRoute={ROUTES.BENTO_AI_AGENT_INSTANCE}
        />
      )}
    </LoadProvider>
  );
}
