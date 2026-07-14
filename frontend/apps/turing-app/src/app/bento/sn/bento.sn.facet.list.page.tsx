import { ROUTES } from "@/app/routes.const";
import { BentoHero } from "@/components/bento";
import { LoadProvider } from "@/components/loading-provider";
import { SNSiteFacetDraggableList } from "@/components/sn/facet/sn.site.facet.draggable.list";
import { GradientButtonLink } from "@viglet/viglet-design-system/router";
import type { TurSNSiteFacetOrdering } from "@/models/sn/sn-site-facet-ordering.model";
import { TurSNFacetedFieldService } from "@/services/sn/sn.faceted.field.service";
import { IconFilter, IconPlus } from "@tabler/icons-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

const service = new TurSNFacetedFieldService();

/** Bento SN facet list — T576. Reuses the facet draggable list with the bento
 *  base route so row edit links stay inside the shell. */
export default function BentoSNFacetListPage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const [tableData, setTableData] = React.useState<TurSNSiteFacetOrdering[]>();
  const [error, setError] = React.useState<string | null>(null);
  const instanceRoute = `${ROUTES.BENTO_SN_INSTANCE}/${id}`;
  const facetRoute = `${instanceRoute}/facet`;

  React.useEffect(() => {
    service.query(id).then(setTableData).catch(() => setError(t("common.connectionError", { resource: t("sn.facets.title") })));
  }, [id, t]);

  return (
    <LoadProvider checkIsNotUndefined={tableData} error={error} tryAgainUrl={facetRoute}>
      <BentoHero
        backTo={instanceRoute}
        backLabel={t("sn.title")}
        leading={
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-emerald-500 to-teal-600 text-white shadow-md">
            <IconFilter size={24} />
          </span>
        }
        title={t("sn.facets.feature")}
        subtitle={t("sn.facets.description")}
        trailing={
          <GradientButtonLink to={`${facetRoute}/custom/new`} size="sm">
            <IconPlus className="size-4" />
            {t("sn.facets.newCustomFacet")}
          </GradientButtonLink>
        }
      />
      {tableData && (
        <SNSiteFacetDraggableList
          siteId={id}
          tableData={tableData}
          setTableData={setTableData as React.Dispatch<React.SetStateAction<TurSNSiteFacetOrdering[]>>}
          baseRoute={ROUTES.BENTO_SN_INSTANCE}
        />
      )}
    </LoadProvider>
  );
}
