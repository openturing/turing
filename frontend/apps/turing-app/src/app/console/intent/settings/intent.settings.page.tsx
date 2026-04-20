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
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const [intent, setIntent] = useState<TurIntent>();
  const [isNew, setIsNew] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (id === "new") {
      setIntent({ actions: [] } as unknown as TurIntent);
    } else {
      turIntentService.get(id)
        .then(setIntent)
        .catch(() => setError(t("common.connectionError", { resource: t("intent.title").toLowerCase() })));
      setIsNew(false);
    }
  }, [id]);

  return (
    <LoadProvider checkIsNotUndefined={intent} error={error} tryAgainUrl={`${ROUTES.INTENT_INSTANCE}/${id}/settings`}>
      {intent && <IntentSettingsForm value={intent} isNew={isNew} />}
    </LoadProvider>
  );
}
