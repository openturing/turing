import type { TurSNSiteMerge } from "@/models/sn/sn-site-merge.model";
import { TurSNSiteMergeService } from "@/services/sn/sn.site.merge.service";
import { IconGitMerge } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";
import { BentoSnSectionList } from "./bento.sn.section-list";

const service = new TurSNSiteMergeService();

/** Bento SN merge-providers list — T576. */
export default function BentoSNMergeProvidersListPage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const [items, setItems] = useState<TurSNSiteMerge[]>();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    service.query(id).then(setItems).catch(() => setError(t("common.connectionError", { resource: t("sn.mergeProviders.title") })));
  }, [id, t]);

  return (
    <BentoSnSectionList
      siteId={id}
      section="merge-providers"
      icon={IconGitMerge}
      title={t("sn.mergeProviders.title")}
      subtitle={t("sn.mergeProviders.description")}
      items={items}
      error={error}
      newLabel={t("sn.mergeProviders.newMergeProvider")}
      emptyTitle={t("sn.mergeProviders.blankTitle")}
      emptyDescription={t("sn.mergeProviders.blankDescription")}
      itemKey={(item) => item.id ?? ""}
      itemTitle={(item) => `${item.providerFrom} → ${item.providerTo}`}
      itemDescription={(item) => item.description}
    />
  );
}
