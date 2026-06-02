import { useChatWebhooks } from "@/api/queries/webhook.queries";
import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import { IconWebhook } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

/**
 * T62 admin — list of configured webhooks. Empty state surfaces a CTA;
 * otherwise renders the grid of webhook cards with click-through to the
 * editor.
 *
 * @since 2026.3.1
 */
export default function WebhookListPage() {
  const { t } = useTranslation();
  const { data: webhooks, isError } = useChatWebhooks();
  const error = isError ? t("common.connectionError", { resource: t("webhook.title") }) : null;

  const gridItemList = useGridAdapter(webhooks, {
    name: "name",
    description: (item) =>
      item.description ??
      (item.slotTrigger
        ? t("webhook.gridSlotTrigger", { slot: item.slotTrigger })
        : t("webhook.gridHandoffOnly")),
    url: (item) => `${ROUTES.CHAT_WEBHOOK_INSTANCE}/${item.id}`,
  });

  return (
    <LoadProvider
      checkIsNotUndefined={webhooks}
      error={error}
      tryAgainUrl={ROUTES.CHAT_WEBHOOK_INSTANCE}
    >
      {gridItemList.length > 0 ? (
        <GridList gridItemList={gridItemList}>
          <GridList.NewButton
            to={`${ROUTES.CHAT_WEBHOOK_INSTANCE}/new`}
            label={t("webhook.title")}
          />
        </GridList>
      ) : (
        <BlankSlate
          icon={IconWebhook}
          title={t("webhook.blankTitle")}
          description={t("webhook.blankDescription")}
          buttonText={t("webhook.newInstance")}
          urlNew={`${ROUTES.CHAT_WEBHOOK_INSTANCE}/new`}
        />
      )}
    </LoadProvider>
  );
}
