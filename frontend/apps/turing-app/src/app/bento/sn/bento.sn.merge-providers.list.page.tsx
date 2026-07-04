import { ROUTES } from "@/app/routes.const";
import type { TurSNSiteMerge } from "@/models/sn/sn-site-merge.model";
import { TurSNSiteMergeService } from "@/services/sn/sn.site.merge.service";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
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

  const gridItemList = useGridAdapter(items, {
    name: (item) => `${item.providerFrom} → ${item.providerTo}`,
    description: "description",
    url: (item) => `${ROUTES.BENTO_SN_INSTANCE}/${id}/merge-providers/${item.id}`,
  });

  return (
    <BentoSnSectionList
      siteId={id}
      section="merge-providers"
      icon={IconGitMerge}
      title={t("sn.mergeProviders.title")}
      subtitle={t("sn.mergeProviders.description")}
      items={items}
      gridItemList={gridItemList}
      error={error}
      newLabel={t("sn.mergeProviders.newMergeProvider")}
      blankTitle={t("sn.mergeProviders.blankTitle")}
      blankDescription={t("sn.mergeProviders.blankDescription")}
      blankButton={t("sn.mergeProviders.newMergeProviderBlank")}
    />
  );
}
