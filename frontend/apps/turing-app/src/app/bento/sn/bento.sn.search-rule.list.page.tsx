import type { TurSNSiteSearchRule } from "@/models/sn/sn-site-search-rule.model";
import { TurSNSiteSearchRuleService } from "@/services/sn/sn.site.search.rule.service";
import { IconGavel } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";
import { BentoSnSectionList } from "./bento.sn.section-list";

const service = new TurSNSiteSearchRuleService();

/** Bento SN search-rule list — T576. */
export default function BentoSNSearchRuleListPage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const [items, setItems] = useState<TurSNSiteSearchRule[]>();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    service.query(id).then(setItems).catch(() => setError(t("common.connectionError", { resource: t("sn.searchRule.title") })));
  }, [id, t]);

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
