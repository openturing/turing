import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SNSiteFacetDraggableList } from "@/components/sn/facet/sn.site.facet.draggable.list";
import { SubPageHeader } from "@/components/sub.page.header";
import type { TurSNSiteFacetOrdering } from "@/models/sn/sn-site-facet-ordering.model";
import { TurSNFacetedFieldService } from "@/services/sn/sn.faceted.field.service";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconFilter } from "@tabler/icons-react";
import React, { useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

const turSNFacetedFieldService = new TurSNFacetedFieldService();
export default function SNSiteFacetListPage() {
  const { id: siteId } = useParams() as { id: string };
  const [error, setError] = useState<string | null>(null);
  const [tableData, setTableData] = React.useState<TurSNSiteFacetOrdering[]>();
  const { t } = useTranslation();
  useSubPageBreadcrumb(t("sn.facets.title"));

  React.useEffect(() => {
    turSNFacetedFieldService.query(siteId).then(setTableData).catch(() => setError("Connection error or timeout while fetching faceted fields."));
  }, [siteId]);
  return (
    <LoadProvider checkIsNotUndefined={tableData} error={error} tryAgainUrl={`${ROUTES.SN_INSTANCE}/${siteId}/facet`}>
      <SubPageHeader
        icon={IconFilter}
        name={t("sn.facets.feature")}
        feature={t("sn.facets.feature")}
        description={t("sn.facets.description")}>
        <SubPageHeader.Action label={t("sn.facets.newCustomFacet")} href={`${ROUTES.SN_INSTANCE}/${siteId}/facet/custom/new`} />
      </SubPageHeader>
      {tableData && <SNSiteFacetDraggableList
        siteId={siteId}
        tableData={tableData}
        setTableData={setTableData as React.Dispatch<React.SetStateAction<TurSNSiteFacetOrdering[]>>}
      />}
    </LoadProvider>
  )
}
