import { useFeatures } from "@/api/queries/features.queries";
import { useStoreInstances } from "@/api/queries/store-instance.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoEntityTile, BentoListPage } from "@/components/bento";
import { GlobalBadge } from "@/components/infra-global-notice";
import { IconDatabase } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

export default function BentoStoreInstanceListPage() {
  const { t } = useTranslation();
  const { data: stores, isError } = useStoreInstances();
  const { data: features } = useFeatures();
  const error = isError ? t("common.connectionError", { resource: t("store.title") }) : null;
  const tenancyEnabled = features?.tenancyEnabled === true;

  return (
    <BentoListPage
      items={stores}
      error={error}
      tryAgainUrl={ROUTES.BENTO_STORE_INSTANCE}
      backTo={ROUTES.BENTO_AREA_GENERATIVE_AI}
      backLabel={t("home.sections.generativeAi.label")}
      heroIcon={IconDatabase}
      tone="violet"
      title={t("store.title")}
      subtitle={t("store.blankDescription")}
      newRoute={`${ROUTES.BENTO_STORE_INSTANCE}/new`}
      newLabel={t("store.newInstance")}
      newSubtitle="Chroma · Milvus · PGVector"
      itemKey={(store) => store.id}
      emptyTitle={t("store.blankTitle")}
      emptyDescription={t("store.blankDescription")}
      listId="embeddingStore"
      renderTile={(store, emphasis) => (
        <BentoEntityTile
          to={`${ROUTES.BENTO_STORE_INSTANCE}/${store.id}`}
          emphasis={emphasis}
          defaultIcon={IconDatabase}
          icon={store.icon}
          tone="violet"
          title={store.title}
          description={store.description}
          hasStatus
          enabled={store.enabled}
          meta={
            <>
              <span className="rounded-full border border-border/60 bg-card/40 px-2 py-0.5 backdrop-blur">
                {store.turStoreVendor?.title ?? "—"}
              </span>
              {tenancyEnabled && store.tenantId == null && <GlobalBadge />}
            </>
          }
        />
      )}
    />
  );
}
