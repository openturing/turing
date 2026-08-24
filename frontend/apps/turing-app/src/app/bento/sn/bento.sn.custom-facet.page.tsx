import { useSnCustomFacet, useDeleteSnCustomFacet } from "@/api/queries/sn-custom-facet.queries";
import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SNSiteCustomFacetForm } from "@/components/sn/facet/sn.site.custom.facet.form";
import type { TurSNSiteCustomFacet } from "@/models/sn/sn-site-custom-facet.model";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";

const NEW_RECORD: TurSNSiteCustomFacet = {
  name: "",
  defaultLabel: "",
  label: {},
  facetType: "DEFAULT",
  facetItemType: "DEFAULT",
  items: [],
  fieldExtId: "",
} as TurSNSiteCustomFacet;

/** Bento SN custom-facet detail — T576. */
export default function BentoSNCustomFacetPage() {
  const { id, customFacetId } = useParams() as { id: string; customFacetId: string };
  const { t } = useTranslation();
  const navigate = useNavigate();
  // T1017 — derived, not stored. `isNew` was state that an effect wrote to
  // `customFacetId === "new"`, which is the same value the router already has: holding it
  // meant a route change from an existing record to "new" rendered one pass
  // with the previous answer before the effect corrected it.
  const isNew = customFacetId === "new";
  const [open, setOpen] = useState(false);
  const listRoute = `${ROUTES.BENTO_SN_INSTANCE}/${id}/facet`;

  // T1018 — the record comes from the query cache instead of a private copy
  // filled by an effect. The hook is disabled on the `new` route, where the seed
  // above is the whole of what there is to show.
  const { data: fetched, isError } = useSnCustomFacet(id, customFacetId);
  const value = isNew ? NEW_RECORD : fetched;
  const error = isError ? t("common.connectionError", { resource: t("sn.facets.title") }) : null;
  const deleteMutation = useDeleteSnCustomFacet(id);

  async function onDelete() {
    try {
      if (value?.id && await deleteMutation.mutateAsync(value.id)) {
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
