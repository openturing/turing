import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { IntentSettingsForm } from "@/components/intent/intent.settings.form";
import type { TurIntent } from "@/models/intent/intent.model";
import { TurIntentService } from "@/services/intent/intent.service";
import { IconSettings } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";

const turIntentService = new TurIntentService();

export default function IntentSettingsPage() {
  const { id } = useParams() as { id: string };
  const [intent, setIntent] = useState<TurIntent>();
  const [isNew, setIsNew] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (id === "new") {
      setIntent({ actions: [] } as unknown as TurIntent);
    } else {
      turIntentService.get(id)
        .then(setIntent)
        .catch(() => setError("Connection error or timeout while fetching intent."));
      setIsNew(false);
    }
  }, [id]);

  return (
    <LoadProvider checkIsNotUndefined={intent} error={error} tryAgainUrl={`${ROUTES.INTENT_INSTANCE}/${id}/settings`}>
      <SubPageHeader icon={IconSettings} name="Settings" feature="Settings"
        description="General information, actions (prompts), and status." />
      {intent && <IntentSettingsForm value={intent} isNew={isNew} />}
    </LoadProvider>
  );
}
