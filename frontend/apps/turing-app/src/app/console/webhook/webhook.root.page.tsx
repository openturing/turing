import { ROUTES } from "@/app/routes.const";
import { Page } from "@/components/page";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconWebhook } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

/**
 * @since 2026.3.1
 */
export default function WebhookRootPage() {
  const { t } = useTranslation();
  useSubPageBreadcrumb({ label: t("webhook.title"), href: `${ROUTES.CHAT_WEBHOOK_INSTANCE}` });
  return (
    <Page turIcon={IconWebhook} title={t("webhook.title")} urlBase={ROUTES.CHAT_WEBHOOK_INSTANCE} />
  );
}
