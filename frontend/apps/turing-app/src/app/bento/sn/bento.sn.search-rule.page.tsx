import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SNSiteSearchRuleForm } from "@/components/sn/search-rule/sn.site.search.rule.form";
import type { TurSNSiteSearchRule } from "@/models/sn/sn-site-search-rule.model";
import { TurSNSiteSearchRuleService } from "@/services/sn/sn.site.search.rule.service";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";

const service = new TurSNSiteSearchRuleService();

/** Bento SN search-rule detail — T576. */
export default function BentoSNSearchRulePage() {
  const { id, searchRuleId } = useParams() as { id: string; searchRuleId: string };
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [value, setValue] = useState<TurSNSiteSearchRule>();
  const [isNew, setIsNew] = useState(true);
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const listRoute = `${ROUTES.BENTO_SN_INSTANCE}/${id}/search-rule`;

  useEffect(() => {
    if (searchRuleId === "new") {
      setValue({ name: "", position: 0, enabled: true, conditions: [], actions: [] });
      setIsNew(true);
    } else {
      service.get(id, searchRuleId).then((s) => { setValue(s); setIsNew(false); })
        .catch(() => setError(t("common.connectionError", { resource: t("sn.searchRule.title") })));
    }
  }, [id, searchRuleId, t]);

  async function onDelete() {
    if (!value?.id) return;
    try {
      if (await service.delete(id, value.id)) {
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
          chrome="bento"
        />
      )}
    </LoadProvider>
  );
}
