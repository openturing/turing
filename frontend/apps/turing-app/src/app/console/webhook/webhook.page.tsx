import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import type { BreadcrumbItem } from "@/contexts/breadcrumb.context";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import type { TurChatWebhook } from "@/models/genai/webhook.model";
import { TurChatWebhookService } from "@/services/genai/webhook.service";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

import { WebhookForm } from "@/components/webhook/webhook.form";

/**
 * @since 2026.3.1
 */
const turChatWebhookService = new TurChatWebhookService();

export default function WebhookPage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const [webhook, setWebhook] = useState<TurChatWebhook>();
  const [isNew, setIsNew] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [breadcrumb, setBreadcrumb] = useState<BreadcrumbItem[] | undefined>(
    id === "new" ? [{ label: t("webhook.newWebhook") }] : undefined,
  );
  useSubPageBreadcrumb(breadcrumb);

  useEffect(() => {
    if (id === "new") {
      setWebhook({
        name: "",
        targetUrl: "",
        enabled: true,
      } as TurChatWebhook);
      setIsNew(true);
    } else {
      turChatWebhookService
        .get(id)
        .then((w) => {
          setWebhook(w);
          setBreadcrumb([{ label: w.name, href: `${ROUTES.CHAT_WEBHOOK_INSTANCE}/${w.id}` }]);
        })
        .catch(() => setError(t("common.connectionError", { resource: t("webhook.title") })));
      setIsNew(false);
    }
  }, [id, t]);

  return (
    <LoadProvider
      checkIsNotUndefined={webhook}
      error={error}
      tryAgainUrl={`${ROUTES.CHAT_WEBHOOK_INSTANCE}/${id}`}
    >
      {webhook && <WebhookForm value={webhook} isNew={isNew} />}
    </LoadProvider>
  );
}
