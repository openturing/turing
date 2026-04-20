import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SNSiteFacetedFieldForm } from "@/components/sn/facet/sn.site.faceted.field.form";
import type { TurSNSiteField } from "@/models/sn/sn-site-field.model";
import { TurSNFieldService } from "@/services/sn/sn.field.service";
import { type BreadcrumbItem } from "@/contexts/breadcrumb.context";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

const turSNFieldService = new TurSNFieldService();

export default function SNSiteFacetedFieldPage() {
  const { id, facetedFieldId } = useParams() as { id: string, facetedFieldId: string };
  const [snField, setSnField] = useState<TurSNSiteField>();
  const [isNew, setIsNew] = useState<boolean>(true);
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [breadcrumb, setBreadcrumb] = useState<BreadcrumbItem[]>();
  const { t } = useTranslation();

  useEffect(() => {
    if (facetedFieldId === "new") {
      turSNFieldService.query(id).then(() => setSnField({} as TurSNSiteField)).catch(() => setError("Connection error or timeout while fetching faceted fields."));
      setBreadcrumb([{ label: t("sn.facets.title"), href: `${ROUTES.SN_INSTANCE}/${id}/facet` }, { label: t("common.new") }]);
    } else {
      turSNFieldService.get(id, facetedFieldId).then((field) => {
        setSnField(field);
        setBreadcrumb([{ label: t("sn.facets.title"), href: `${ROUTES.SN_INSTANCE}/${id}/facet` }, { label: field.name || facetedFieldId }]);
      }).catch(() => setError("Connection error or timeout while fetching faceted field details."));
      setIsNew(false);
    }
  }, [id, facetedFieldId]);

  useSubPageBreadcrumb(breadcrumb);
  return (
    <LoadProvider checkIsNotUndefined={snField} error={error} tryAgainUrl={`${ROUTES.SN_INSTANCE}/${id}/field/${facetedFieldId}`}>
      {snField && <SNSiteFacetedFieldForm snSiteId={id} snField={snField} isNew={isNew} open={open} setOpen={setOpen} />}
    </LoadProvider>
  )
}
