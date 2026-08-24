import { useSnSearchRule, useDeleteSnSearchRule } from "@/api/queries/sn-search-rule.queries";
import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SNSiteSearchRuleForm } from "@/components/sn/search-rule/sn.site.search.rule.form";
import type { TurSNSiteSearchRule } from "@/models/sn/sn-site-search-rule.model";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";

const NEW_RECORD: TurSNSiteSearchRule = { name: "", position: 0, enabled: true, conditions: [], actions: [] };

/** Bento SN search-rule detail — T576. */
export default function BentoSNSearchRulePage() {
  const { id, searchRuleId } = useParams() as { id: string; searchRuleId: string };
  const { t } = useTranslation();
  const navigate = useNavigate();
  // T1017 — derived, not stored. `isNew` was state that an effect wrote to
  // `searchRuleId === "new"`, which is the same value the router already has: holding it
  // meant a route change from an existing record to "new" rendered one pass
  // with the previous answer before the effect corrected it.
  const isNew = searchRuleId === "new";
  const [open, setOpen] = useState(false);
  const listRoute = `${ROUTES.BENTO_SN_INSTANCE}/${id}/search-rule`;

  // T1018 — the record comes from the query cache instead of a private copy
  // filled by an effect. The hook is disabled on the `new` route, where the seed
  // above is the whole of what there is to show.
  const { data: fetched, isError } = useSnSearchRule(id, searchRuleId);
  const value = isNew ? NEW_RECORD : fetched;
  const error = isError ? t("common.connectionError", { resource: t("sn.searchRule.title") }) : null;
  const deleteMutation = useDeleteSnSearchRule(id);

  async function onDelete() {
    if (!value?.id) return;
    try {
      if (await deleteMutation.mutateAsync(value.id)) {
        toast.success(t("sn.searchRule.deleted"));
        navigate(listRoute);
      } else {
        toast.error(t("sn.searchRule.notDeleted"));
      }
    } catch (err) {
      console.error("Delete error", err);
      toast.error(t("sn.searchRule.notDeleted"));
    }
    setOpen(false);
  }

  return (
    <LoadProvider checkIsNotUndefined={value} error={error} tryAgainUrl={`${listRoute}/${searchRuleId}`}>
      {value && (
        <SNSiteSearchRuleForm
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
