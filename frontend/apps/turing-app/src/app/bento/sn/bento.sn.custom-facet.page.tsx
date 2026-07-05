import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SNSiteCustomFacetForm } from "@/components/sn/facet/sn.site.custom.facet.form";
import type { TurSNSiteCustomFacet } from "@/models/sn/sn-site-custom-facet.model";
import { TurSNSiteCustomFacetService } from "@/services/sn/sn.site.custom.facet.service";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";

const service = new TurSNSiteCustomFacetService();

/** Bento SN custom-facet detail — T576. */
export default function BentoSNCustomFacetPage() {
  const { id, customFacetId } = useParams() as { id: string; customFacetId: string };
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [value, setValue] = useState<TurSNSiteCustomFacet>();
  const [isNew, setIsNew] = useState(true);
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const listRoute = `${ROUTES.BENTO_SN_INSTANCE}/${id}/facet`;

  useEffect(() => {
    if (customFacetId === "new") {
      setValue({
        name: "",
        defaultLabel: "",
        label: {},
        facetType: "DEFAULT",
        facetItemType: "DEFAULT",
        items: [],
        fieldExtId: "",
      } as TurSNSiteCustomFacet);
      setIsNew(true);
    } else {
      service.get(id, customFacetId).then((f) => { setValue(f); setIsNew(false); })
        .catch(() => setError(t("common.connectionError", { resource: t("sn.facets.title") })));
    }
  }, [id, customFacetId, t]);

  async function onDelete() {
    try {
      if (value?.id && await service.delete(id, value.id)) {
        toast.success(t("sn.facets.deleted", { name: value.name }));
        navigate(listRoute);
      } else {
        toast.error(t("sn.facets.notDeleted", { name: value?.name }));
      }
    } catch (err) {
      console.error("Delete error", err);
      const message = err instanceof Error ? err.message : "Unknown error occurred";
      toast.error(t("sn.facets.failedDelete", { message }));
    }
    setOpen(false);
  }

  return (
    <LoadProvider checkIsNotUndefined={value} error={error} tryAgainUrl={`${listRoute}/custom/${customFacetId}`}>
      {value && (
        <SNSiteCustomFacetForm
          snSiteId={id}
          value={value}
          isNew={isNew}
          onDelete={isNew ? undefined : onDelete}
          open={open}
          setOpen={setOpen}
          baseRoute={ROUTES.BENTO_SN_INSTANCE}
          chrome="bento"
        />
      )}
    </LoadProvider>
  );
}
