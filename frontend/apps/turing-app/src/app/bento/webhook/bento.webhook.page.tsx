import { useChatWebhook, useDeleteChatWebhook, useUpdateChatWebhook } from "@/api/queries/webhook.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoEntityShell } from "@/components/bento";
import { LoadProvider } from "@/components/loading-provider";
import type { TurChatWebhook } from "@/models/genai/webhook.model";
import { IconWebhook } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";
import { BENTO_WEBHOOK_FORM_ID, BentoWebhookForm } from "./bento.webhook.form";

/**
 * Chat Webhook uses `name` + boolean `enabled` and has no icon, so the shared
 * shell's identity is aliased (title↔name, number↔bool) and `hideIcon` is set.
 * See T555.
 */
type WebhookView = Omit<TurChatWebhook, "enabled"> & { title: string; enabled: number };

const toView = (w: TurChatWebhook): WebhookView => ({ ...w, title: w.name ?? "", enabled: w.enabled === false ? 0 : 1 });

const fromView = (view: WebhookView): TurChatWebhook => {
  const { title, ...rest } = view;
  return { ...rest, name: title ?? rest.name, enabled: view.enabled === 1 };
};

export default function BentoWebhookPage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const isNew = id === "new";

  const { data: webhook, isError } = useChatWebhook(isNew ? undefined : id);
  const error = isError ? t("common.connectionError", { resource: t("webhook.title") }) : null;

  const updateMutation = useUpdateChatWebhook();
  const deleteMutation = useDeleteChatWebhook();

  function shell(entity: TurChatWebhook, headlineFallback: string) {
    return (
      <BentoEntityShell<WebhookView>
        entity={toView(entity)}
        isNew={isNew}
        headlineFallback={headlineFallback}
        eyebrow={t("webhook.title")}
        listRoute={ROUTES.BENTO_CHAT_WEBHOOK_INSTANCE}
        icon={IconWebhook}
        tone="rose"
        formId={BENTO_WEBHOOK_FORM_ID}
        feature={t("webhook.title")}
        hasStatus
        hideIcon
        onUpdate={isNew ? undefined : (next) => updateMutation.mutateAsync(fromView(next))}
        onDelete={isNew ? undefined : () => deleteMutation.mutateAsync(entity)}
      >
        {({ staged, onStateChange }) => (
          <BentoWebhookForm value={entity} isNew={isNew} staged={staged} onStateChange={onStateChange} />
        )}
      </BentoEntityShell>
    );
  }

  if (isNew) {
    return shell({ httpMethod: "POST" } as TurChatWebhook, t("webhook.newWebhook"));
  }

  return (
    <LoadProvider checkIsNotUndefined={webhook} error={error} tryAgainUrl={`${ROUTES.BENTO_CHAT_WEBHOOK_INSTANCE}/${id}`}>
      {webhook && shell(webhook, webhook.name)}
    </LoadProvider>
  );
}
