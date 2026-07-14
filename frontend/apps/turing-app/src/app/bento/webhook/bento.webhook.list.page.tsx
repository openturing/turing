import { useChatWebhooks } from "@/api/queries/webhook.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoEntityTile, BentoListPage } from "@/components/bento";
import { IconWebhook } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

export default function BentoWebhookListPage() {
  const { t } = useTranslation();
  const { data: webhooks, isError } = useChatWebhooks();
  const error = isError ? t("common.connectionError", { resource: t("webhook.title") }) : null;

  return (
    <BentoListPage
      items={webhooks}
      error={error}
      tryAgainUrl={ROUTES.BENTO_CHAT_WEBHOOK_INSTANCE}
      backTo={ROUTES.BENTO_AREA_GENERATIVE_AI}
      backLabel={t("home.sections.generativeAi.label")}
      heroIcon={IconWebhook}
      tone="rose"
      title={t("webhook.title")}
      subtitle={t("webhook.blankDescription")}
      newRoute={`${ROUTES.BENTO_CHAT_WEBHOOK_INSTANCE}/new`}
      newLabel={t("webhook.newWebhook")}
      itemKey={(webhook) => webhook.id ?? webhook.name}
      emptyTitle={t("webhook.blankTitle")}
      emptyDescription={t("webhook.blankDescription")}
      listId="chatWebhook"
      renderTile={(webhook, emphasis) => (
        <BentoEntityTile
          to={`${ROUTES.BENTO_CHAT_WEBHOOK_INSTANCE}/${webhook.id}`}
          emphasis={emphasis}
          defaultIcon={IconWebhook}
          tone="rose"
          title={webhook.name}
          description={webhook.description}
          hasStatus
          enabled={webhook.enabled === false ? 0 : 1}
          meta={
            <span className="rounded-full border border-border/60 bg-card/40 px-2 py-0.5 font-mono backdrop-blur">
              {webhook.httpMethod ?? "POST"}
            </span>
          }
        />
      )}
    />
  );
}
