import { useSnCustomSorts } from "@/api/queries/sn-custom-sort.queries";
import { IconArrowsSort } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";
import { BentoSnSectionList } from "./bento.sn.section-list";

/** Bento SN custom-sort list — T576. */
export default function BentoSNCustomSortListPage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  // T1018 — read through the hook, so deleting one from the detail page
  // invalidates this list instead of leaving the removed row on screen.
  const { data: items, isError } = useSnCustomSorts(id);
  const error = isError ? t("common.connectionError", { resource: t("sn.customSort.title") }) : null;

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
