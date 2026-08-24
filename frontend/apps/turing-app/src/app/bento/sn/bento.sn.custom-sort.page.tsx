import { useSnCustomSort, useDeleteSnCustomSort } from "@/api/queries/sn-custom-sort.queries";
import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SNSiteCustomSortForm } from "@/components/sn/custom-sort/sn.site.custom.sort.form";
import type { TurSNSiteCustomSort } from "@/models/sn/sn-site-custom-sort.model";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";

const NEW_RECORD: TurSNSiteCustomSort = { name: "", items: [] };

/** Bento SN custom-sort detail — T576. Reuses `SNSiteCustomSortForm` with the
 *  bento base route so save/cancel/delete stay inside the shell. */
export default function BentoSNCustomSortPage() {
  const { id, customSortId } = useParams() as { id: string; customSortId: string };
  const { t } = useTranslation();
  const navigate = useNavigate();
  // T1017 — derived, not stored. `isNew` was state that an effect wrote to
  // `customSortId === "new"`, which is the same value the router already has: holding it
  // meant a route change from an existing record to "new" rendered one pass
  // with the previous answer before the effect corrected it.
  const isNew = customSortId === "new";
  const [open, setOpen] = useState(false);
  const listRoute = `${ROUTES.BENTO_SN_INSTANCE}/${id}/custom-sort`;

  // T1018 — the record comes from the query cache instead of a private copy
  // filled by an effect. The hook is disabled on the `new` route, where the seed
  // above is the whole of what there is to show.
  const { data: fetched, isError } = useSnCustomSort(id, customSortId);
  const value = isNew ? NEW_RECORD : fetched;
  const error = isError ? t("common.connectionError", { resource: t("sn.customSort.title") }) : null;
  const deleteMutation = useDeleteSnCustomSort(id);

  async function onDelete() {
    if (!value?.id) return;
    try {
      if (await deleteMutation.mutateAsync(value.id)) {
        toast.success(t("sn.customSort.deleted"));
        navigate(listRoute);
      } else {
        toast.error(t("sn.customSort.notDeleted"));
      }
    } catch (err) {
      console.error("Delete error", err);
      toast.error(t("sn.customSort.notDeleted"));
    }
    setOpen(false);
  }

  return (
    <LoadProvider checkIsNotUndefined={value} error={error} tryAgainUrl={`${listRoute}/${customSortId}`}>
      {value && (
        <SNSiteCustomSortForm
          snSiteId={id}
          value={value}
          isNew={isNew}
          onDelete={isNew ? undefined : onDelete}
          open={open}
          setOpen={setOpen}
          baseRoute={ROUTES.BENTO_SN_INSTANCE}
        />
      )}
    </LoadProvider>
  );
}
