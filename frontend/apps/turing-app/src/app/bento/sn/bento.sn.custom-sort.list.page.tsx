import type { TurSNSiteCustomSort } from "@/models/sn/sn-site-custom-sort.model";
import { TurSNSiteCustomSortService } from "@/services/sn/sn.site.custom.sort.service";
import { IconArrowsSort } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";
import { BentoSnSectionList } from "./bento.sn.section-list";

const service = new TurSNSiteCustomSortService();

/** Bento SN custom-sort list — T576. */
export default function BentoSNCustomSortListPage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const [items, setItems] = useState<TurSNSiteCustomSort[]>();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    service.query(id).then(setItems).catch(() => setError(t("common.connectionError", { resource: t("sn.customSort.title") })));
  }, [id, t]);

  return (
    <BentoSnSectionList
      siteId={id}
      section="custom-sort"
      icon={IconArrowsSort}
      title={t("sn.customSort.title")}
      subtitle={t("sn.customSort.description")}
      items={items}
      error={error}
      newLabel={t("sn.customSort.newCustomSort")}
      emptyTitle={t("sn.customSort.blankTitle")}
      emptyDescription={t("sn.customSort.blankDescription")}
      itemKey={(item) => item.id ?? ""}
      itemTitle={(item) => item.name}
      itemDescription={(item) => item.description}
    />
  );
}
