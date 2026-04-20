import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SNSiteCustomFacetForm } from "@/components/sn/facet/sn.site.custom.facet.form";
import type { TurSNSiteCustomFacet } from "@/models/sn/sn-site-custom-facet.model";
import { TurSNSiteCustomFacetService } from "@/services/sn/sn.site.custom.facet.service";
import { type BreadcrumbItem } from "@/contexts/breadcrumb.context";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";

const turSNSiteCustomFacetService = new TurSNSiteCustomFacetService();

export default function SNSiteCustomFacetPage() {
  const navigate = useNavigate();
  const { id, customFacetId } = useParams() as { id: string, customFacetId: string };
  const [customFacet, setCustomFacet] = useState<TurSNSiteCustomFacet>();
  const [isNew, setIsNew] = useState<boolean>(true);
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [breadcrumb, setBreadcrumb] = useState<BreadcrumbItem[]>();
  const { t } = useTranslation();

  useEffect(() => {
    if (customFacetId === "new") {
      setCustomFacet({
        name: "",
        defaultLabel: "",
        label: {},
        facetType: "DEFAULT",
        facetItemType: "DEFAULT",
        items: [],
        fieldExtId: "",
      } as TurSNSiteCustomFacet);
      setBreadcrumb([{ label: t("sn.facets.title"), href: `${ROUTES.SN_INSTANCE}/${id}/facet` }, { label: t("common.new") }]);
    } else {
      turSNSiteCustomFacetService.get(id, customFacetId).then((facet) => {
        setCustomFacet(facet);
        setBreadcrumb([{ label: t("sn.facets.title"), href: `${ROUTES.SN_INSTANCE}/${id}/facet` }, { label: facet.name || customFacetId }]);
      }).catch(() => setError("Connection error or timeout while fetching custom facet details."));
      setIsNew(false);
    }
  }, [id, customFacetId]);

  useSubPageBreadcrumb(breadcrumb);

  async function onDelete() {
    try {
      if (customFacet?.id && await turSNSiteCustomFacetService.delete(id, customFacet.id)) {
        toast.success(t("sn.facets.deleted", { name: customFacet.name }));
        navigate(`${ROUTES.SN_INSTANCE}/${id}/facet`);
      }
      else {
        toast.error(t("sn.facets.notDeleted", { name: customFacet?.name }));
      }

    } catch (error) {
      console.error(t("sn.facets.deletionError"), error);
      const errorMessage = error instanceof Error ? error.message : "Unknown error occurred";
      toast.error(t("sn.facets.failedDelete", { message: errorMessage }));
    }
    setOpen(false);
  }

  return (
    <LoadProvider checkIsNotUndefined={customFacet} error={error} tryAgainUrl={`${ROUTES.SN_INSTANCE}/${id}/facet/custom/${customFacetId}`}>
      {customFacet && <SNSiteCustomFacetForm snSiteId={id} value={customFacet} isNew={isNew} onDelete={isNew ? undefined : onDelete} open={open} setOpen={setOpen} />}
    </LoadProvider>
  )
}
