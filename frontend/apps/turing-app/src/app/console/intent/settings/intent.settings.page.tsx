import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { IntentSettingsForm } from "@/components/intent/intent.settings.form";
import type { TurIntent } from "@/models/intent/intent.model";
import { TurIntentService } from "@/services/intent/intent.service";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

const turIntentService = new TurIntentService();

export default function IntentSettingsPage() {
  const { id: agentId, intentId } = useParams() as { id: string; intentId: string };
  const { t } = useTranslation();
  const [intent, setIntent] = useState<TurIntent>();
  const [isNew, setIsNew] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!agentId) return;
    if (intentId === "new") {
      setIntent({ actions: [] } as unknown as TurIntent);
    } else {
      turIntentService.get(agentId, intentId)
        .then(setIntent)
        .catch(() => setError(t("common.connectionError", { resource: t("intent.title").toLowerCase() })));
      setIsNew(false);
    }
  }, [agentId, intentId]);

  return (
    <LoadProvider
      checkIsNotUndefined={intent}
      error={error}
      tryAgainUrl={`${ROUTES.AI_AGENT_INSTANCE}/${agentId}/intent/${intentId}/settings`}
    >
      {intent && <IntentSettingsForm value={intent} isNew={isNew} agentId={agentId} />}
    </LoadProvider>
  );
}
