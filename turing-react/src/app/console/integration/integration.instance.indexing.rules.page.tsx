import { ROUTES } from "@/app/routes.const";
import { IntegrationIndexingRulesForm } from "@/components/integration/integration.indexing.rules.form";
import { SubPageHeader } from "@/components/sub.page.header";
import type { TurIntegrationIndexingRule } from "@/models/integration/integration-indexing-rule.model";
import { TurIntegrationIndexingRuleService } from "@/services/integration/integration-indexing-rule.service";
import { IconTools } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "sonner";

export default function IntegrationInstanceIndexingRulePage() {
  const navigate = useNavigate();
  const { id, ruleId } = useParams() as { id: string, ruleId: string };
  const [integrationIndexingRules, setIntegrationIndexingRules] = useState<TurIntegrationIndexingRule>({} as TurIntegrationIndexingRule);
  const turIntegrationIndexingRuleService = new TurIntegrationIndexingRuleService(id || "");
  const [isNew, setIsNew] = useState<boolean>(true);
  const [open, setOpen] = useState(false);
  const { t } = useTranslation();
  useEffect(() => {
    if (ruleId !== "new") {
      turIntegrationIndexingRuleService.get(ruleId).then(setIntegrationIndexingRules);
      setIsNew(false);
    }
  }, [id, ruleId, turIntegrationIndexingRuleService]);
  async function onDelete() {
    try {
      if (await turIntegrationIndexingRuleService.delete(integrationIndexingRules)) {
        toast.success(t("integration.indexingRules.deleted", { name: integrationIndexingRules.name }));
        navigate(`${ROUTES.INTEGRATION_INSTANCE}/${id}/indexing-rule`);
      } else {
        toast.error(t("integration.indexingRules.notDeleted", { name: integrationIndexingRules.name }));
      }

    } catch (error) {
      console.error("Form submission error", error);
      toast.error(t("integration.indexingRules.notDeleted", { name: integrationIndexingRules.name }));
    }
    setOpen(false);
  }
  return (
    <>
      <SubPageHeader icon={IconTools} feature={t("integration.indexingRules.title")} name={integrationIndexingRules.name}
        description={t("integration.indexingRules.description")}
        onDelete={onDelete}
        open={open}
        setOpen={setOpen}
      />
      <IntegrationIndexingRulesForm value={integrationIndexingRules} integrationId={id} isNew={isNew} />
    </>
  )
}
