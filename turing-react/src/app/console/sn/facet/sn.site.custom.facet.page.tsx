import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SNSiteCustomFacetForm } from "@/components/sn/facet/sn.site.custom.facet.form";
import { SubPageHeader } from "@/components/sub.page.header";
import type { TurSNSiteCustomFacet } from "@/models/sn/sn-site-custom-facet.model";
import { TurSNSiteCustomFacetService } from "@/services/sn/sn.site.custom.facet.service";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import { IconFilter } from "@tabler/icons-react";
import { useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "sonner";

const turSNSiteCustomFacetService = new TurSNSiteCustomFacetService();

export default function SNSiteCustomFacetPage() {
  const navigate = useNavigate();
  const { id, customFacetId } = useParams() as { id: string, customFacetId: string };
  const [customFacet, setCustomFacet] = useState<TurSNSiteCustomFacet>();
  const [isNew, setIsNew] = useState<boolean>(true);
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { pushItem, popItem } = useBreadcrumb();
  const breadcrumbAdded = useRef(false);

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
      if (!breadcrumbAdded.current) {
        pushItem({ label: "Facets", href: `${ROUTES.SN_INSTANCE}/${id}/facet` });
        pushItem({ label: "New" });
        breadcrumbAdded.current = true;
      }
    } else {
      turSNSiteCustomFacetService.get(id, customFacetId).then((facet) => {
        setCustomFacet(facet);
        if (!breadcrumbAdded.current) {
          pushItem({ label: "Facets", href: `${ROUTES.SN_INSTANCE}/${id}/facet` });
          pushItem({ label: facet.name || customFacetId });
          breadcrumbAdded.current = true;
        }
      }).catch(() => setError("Connection error or timeout while fetching custom facet details."));
      setIsNew(false);
    }
  }, [id, customFacetId]);

  useEffect(() => {
    return () => {
      if (breadcrumbAdded.current) {
        popItem();
        popItem();
        breadcrumbAdded.current = false;
      }
    };
  }, []);

  async function onDelete() {
    try {
      if (customFacet?.id && await turSNSiteCustomFacetService.delete(id, customFacet.id)) {
        toast.success(`The ${customFacet.name} Custom Facet was deleted`);
        navigate(`${ROUTES.SN_INSTANCE}/${id}/facet`);
      }
      else {
        toast.error(`The ${customFacet?.name} Custom Facet was not deleted`);
      }

    } catch (error) {
      console.error("Form deletion error", error);
      const errorMessage = error instanceof Error ? error.message : "Unknown error occurred";
      toast.error(`Failed to delete: ${errorMessage}`);
    }
    setOpen(false);
  }

  return (
    <LoadProvider checkIsNotUndefined={customFacet} error={error} tryAgainUrl={`${ROUTES.SN_INSTANCE}/${id}/facet/custom/${customFacetId}`}>
      {customFacet && (<> <SubPageHeader
        icon={IconFilter}
        name={customFacet.name}
        feature="Custom Field"
        description={customFacet.defaultLabel ?? ""}
        onDelete={isNew ? undefined : onDelete}
        open={open}
        setOpen={setOpen} />
        <SNSiteCustomFacetForm snSiteId={id} value={customFacet} isNew={isNew} /></>)}
    </LoadProvider>
  )
}
