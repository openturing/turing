import { useFeatures } from "@/api/queries/features.queries";
import { useSeInstances } from "@/api/queries/se-instance.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoEntityTile, BentoListPage } from "@/components/bento";
import { GlobalBadge } from "@/components/infra-global-notice";
import { IconZoomCode } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

export default function BentoSEInstanceListPage() {
  const { t } = useTranslation();
  const { data: seInstances, isError } = useSeInstances();
  const { data: features } = useFeatures();
  const error = isError ? t("common.connectionError", { resource: t("se.title") }) : null;

  // The Solr-property catalog lock (turing.solr.endpoint) makes the whole
  // catalog read-only — hide "New" then, mirroring the console list.
  const catalogReadOnly = features?.seInstanceReadOnly ?? false;
  const tenancyEnabled = features?.tenancyEnabled === true;

  return (
    <BentoListPage
      items={seInstances}
      error={error}
      tryAgainUrl={ROUTES.BENTO_SE_INSTANCE}
      backTo={ROUTES.BENTO_AREA_ENTERPRISE_SEARCH}
      backLabel={t("home.sections.enterpriseSearch.label")}
      heroIcon={IconZoomCode}
      tone="emerald"
      title={t("se.title")}
      subtitle={t("se.blankDescription")}
      newRoute={`${ROUTES.BENTO_SE_INSTANCE}/new`}
      newLabel={t("se.newSearchEngine")}
      newSubtitle="Solr · Elasticsearch · Lucene"
      hideNew={catalogReadOnly}
      itemKey={(se) => se.id}
      emptyTitle={t("se.blankTitle")}
      emptyDescription={t("se.blankDescription")}
      listId="searchEngine"
      renderTile={(se, emphasis) => (
        <BentoEntityTile
          to={`${ROUTES.BENTO_SE_INSTANCE}/${se.id}`}
          emphasis={emphasis}
          defaultIcon={IconZoomCode}
          icon={se.icon}
          tone="emerald"
          title={se.title}
          description={se.description}
          meta={
            <>
              <span className="rounded-full border border-border/60 bg-card/40 px-2 py-0.5 backdrop-blur">
                {se.turSEVendor?.title ?? "—"}
              </span>
              {tenancyEnabled && se.tenantId == null && <GlobalBadge />}
            </>
          }
        />
      )}
    />
  );
}
