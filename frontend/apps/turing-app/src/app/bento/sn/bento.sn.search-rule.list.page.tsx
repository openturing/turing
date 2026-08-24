import { useSnSearchRules } from "@/api/queries/sn-search-rule.queries";
import { IconGavel } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";
import { BentoSnSectionList } from "./bento.sn.section-list";

/** Bento SN search-rule list — T576. */
export default function BentoSNSearchRuleListPage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  // T1018 — read through the hook, so deleting one from the detail page
  // invalidates this list instead of leaving the removed row on screen.
  const { data: items, isError } = useSnSearchRules(id);
  const error = isError ? t("common.connectionError", { resource: t("sn.searchRule.title") }) : null;

  return (
    <BentoSnSectionList
      siteId={id}
      section="search-rule"
      icon={IconGavel}
      title={t("sn.searchRule.title")}
      subtitle={t("sn.searchRule.description")}
      items={items}
      error={error}
      newLabel={t("sn.searchRule.newSearchRule")}
      emptyTitle={t("sn.searchRule.blankTitle")}
      emptyDescription={t("sn.searchRule.blankDescription")}
      itemKey={(item) => item.id ?? ""}
      itemTitle={(item) => item.name}
      itemDescription={(item) => item.description}
    />
  );
}
