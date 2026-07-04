import { useSnSites } from "@/api/queries/sn-site.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoEntityTile, BentoListPage } from "@/components/bento";
import { IconCompass } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

/**
 * Bento Semantic Navigation list — T557. The heaviest console area, re-skinned
 * onto the shared {@link BentoListPage}. Each tile links to the bento instance
 * detail; a per-site search URL is not surfaced here (the console keeps that in
 * the grid dropdown) — the bento detail hero owns identity + config.
 */
export default function BentoSNInstanceListPage() {
  const { t } = useTranslation();
  const { data: snSites, isError } = useSnSites();
  const error = isError ? t("common.connectionError", { resource: t("sn.title") }) : null;

  return (
    <BentoListPage
      items={snSites}
      error={error}
      tryAgainUrl={ROUTES.BENTO_SN_INSTANCE}
      eyebrow={t("home.sections.enterpriseSearch.label")}
      heroIcon={IconCompass}
      tone="emerald"
      title={t("sn.title")}
      subtitle={t("sn.blankDescription")}
      newRoute={`${ROUTES.BENTO_SN_INSTANCE}/new`}
      newLabel={t("sn.newInstance")}
      newSubtitle={t("sn.newSite")}
      itemKey={(site) => site.id}
      emptyTitle={t("sn.blankTitle")}
      emptyDescription={t("sn.blankDescription")}
      listId="semanticNavigation"
      renderTile={(site, emphasis) => (
        <BentoEntityTile
          to={`${ROUTES.BENTO_SN_INSTANCE}/${site.id}`}
          emphasis={emphasis}
          defaultIcon={IconCompass}
          icon={site.icon}
          tone="emerald"
          title={site.name}
          description={site.description}
          meta={
            site.genAiEnabled ? (
              <span className="rounded-full border border-border/60 bg-card/40 px-2 py-0.5 backdrop-blur">
                {t("sn.genai.title")}
              </span>
            ) : undefined
          }
        />
      )}
    />
  );
}
