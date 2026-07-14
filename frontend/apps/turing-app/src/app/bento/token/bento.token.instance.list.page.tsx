import { useTokenInstances } from "@/api/queries/token-instance.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoEntityTile, BentoListPage } from "@/components/bento";
import { IconCode } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

export default function BentoTokenInstanceListPage() {
  const { t } = useTranslation();
  const { data: tokens, isError } = useTokenInstances();
  const error = isError ? t("common.connectionError", { resource: t("apiToken.title") }) : null;

  return (
    <BentoListPage
      items={tokens}
      error={error}
      tryAgainUrl={ROUTES.BENTO_TOKEN_INSTANCE}
      backTo={ROUTES.BENTO_AREA_MANAGEMENT}
      backLabel={t("home.sections.management.label")}
      heroIcon={IconCode}
      tone="slate"
      title={t("apiToken.title")}
      subtitle={t("apiToken.description")}
      newRoute={`${ROUTES.BENTO_TOKEN_INSTANCE}/new`}
      newLabel={t("apiToken.newInstance")}
      itemKey={(token) => token.id}
      emptyTitle={t("apiToken.blankTitle")}
      emptyDescription={t("apiToken.blankDescription")}
      listId="apiToken"
      renderTile={(token, emphasis) => (
        <BentoEntityTile
          to={`${ROUTES.BENTO_TOKEN_INSTANCE}/${token.id}`}
          emphasis={emphasis}
          defaultIcon={IconCode}
          tone="slate"
          title={token.title}
          description={token.description}
        />
      )}
    />
  );
}
