import { useKnowledgeBases } from "@/api/queries/thesaurus.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoEntityTile, BentoListPage } from "@/components/bento";
import { IconSitemap } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

/**
 * Block AQ (§XL) / T676 — the Thesaurus library: a bento mosaic of Knowledge
 * Bases (controlled-vocabulary libraries). User-facing name is "Thesaurus".
 */
export default function BentoThesaurusListPage() {
  const { t } = useTranslation();
  const { data: kbs, isError } = useKnowledgeBases();
  const error = isError ? t("common.connectionError", { resource: t("thesaurus.title") }) : null;

  return (
    <BentoListPage
      items={kbs}
      error={error}
      tryAgainUrl={ROUTES.BENTO_THESAURUS}
      backTo={ROUTES.BENTO_AREA_ENTERPRISE_SEARCH}
      backLabel={t("home.sections.enterpriseSearch.label")}
      heroIcon={IconSitemap}
      tone="violet"
      title={t("thesaurus.title")}
      subtitle={t("thesaurus.description")}
      newRoute={`${ROUTES.BENTO_THESAURUS}/new`}
      newLabel={t("thesaurus.newKnowledgeBase")}
      itemKey={(kb) => kb.id}
      emptyTitle={t("thesaurus.blankTitle")}
      emptyDescription={t("thesaurus.blankDescription")}
      listId="thesaurus"
      renderTile={(kb, emphasis) => (
        <BentoEntityTile
          to={`${ROUTES.BENTO_THESAURUS}/${kb.id}`}
          emphasis={emphasis}
          defaultIcon={IconSitemap}
          tone="violet"
          title={kb.name}
          description={kb.description ?? ""}
        />
      )}
    />
  );
}
