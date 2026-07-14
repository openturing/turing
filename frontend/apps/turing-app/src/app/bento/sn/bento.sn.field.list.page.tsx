import * as React from "react";

import { ROUTES } from "@/app/routes.const";
import { BentoHero } from "@/components/bento";
import { LoadProvider } from "@/components/loading-provider";
import { SNSiteFieldGridList } from "@/components/sn/fields/field.grid.list";
import { SNFieldWizardDialog } from "@/components/sn/fields/sn.field.wizard.dialog";
import { GradientButton } from "@/components/ui/gradient-button";
import { GradientButtonLink } from "@viglet/viglet-design-system/router";
import type { TurSNStatusFields } from "@/models/sn/sn-field-status.model";
import type { TurSNSiteField } from "@/models/sn/sn-site-field.model.ts";
import { TurSNFieldService } from "@/services/sn/sn.field.service";
import { IconAlignBoxCenterStretch, IconPlus, IconSparkles } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

const turSNFieldService = new TurSNFieldService();

/**
 * Bento SN fields sub-CRUD — T558. Reuses the console `SNSiteFieldGridList`
 * (its row edit link is relative, so it resolves to the Bento field editor) +
 * `SNFieldWizardDialog` verbatim, wrapped in a `BentoHero`. The "New field"
 * action points at the Bento field editor route.
 */
export default function BentoSNFieldListPage() {
  const { id } = useParams() as { id: string };
  const [data, setSnField] = React.useState<TurSNSiteField[]>();
  const [statusFields, setStatusFields] = React.useState<TurSNStatusFields>();
  const [error, setError] = React.useState<string | null>(null);
  const [wizardOpen, setWizardOpen] = React.useState(false);
  const { t } = useTranslation();
  const instanceRoute = `${ROUTES.BENTO_SN_INSTANCE}/${id}`;

  const refreshStatus = React.useCallback(async () => {
    try {
      setStatusFields(await turSNFieldService.getStatusFields(id));
    } catch {
      setError(t("common.connectionError", { resource: t("sn.fields.title") }));
    }
  }, [id, t]);

  const refreshFields = React.useCallback(async () => {
    try {
      setSnField(await turSNFieldService.query(id));
      await refreshStatus();
    } catch {
      setError(t("common.connectionError", { resource: t("sn.fields.title") }));
    }
  }, [id, refreshStatus, t]);

  React.useEffect(() => {
    turSNFieldService.query(id).then(setSnField).catch(() => setError(t("common.connectionError", { resource: t("sn.fields.title") })));
    void refreshStatus();
  }, [id, refreshStatus, t]);

  return (
    <LoadProvider checkIsNotUndefined={data && statusFields} error={error} tryAgainUrl={`${instanceRoute}/field`}>
      {data && statusFields && (
        <>
          <BentoHero
            backTo={instanceRoute}
            backLabel={t("sn.title")}
            leading={
              <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-emerald-500 to-teal-600 text-white shadow-md">
                <IconAlignBoxCenterStretch size={24} />
              </span>
            }
            title={t("sn.fields.feature")}
            subtitle={t("sn.fields.description")}
            trailing={
              <>
                <GradientButtonLink to={`${instanceRoute}/field/new`} size="sm">
                  <IconPlus className="size-4" />
                  {t("sn.fields.newField")}
                </GradientButtonLink>
                <GradientButton type="button" variant="outline" size="sm" onClick={() => setWizardOpen(true)}>
                  <IconSparkles className="size-4" />
                  {t("sn.fields.wizard.action")}
                </GradientButton>
              </>
            }
          />
          <SNSiteFieldGridList
            id={id}
            statusFields={statusFields}
            data={data}
            setSnField={setSnField as React.Dispatch<React.SetStateAction<TurSNSiteField[]>>}
            onRefreshStatus={refreshStatus}
          />
          <SNFieldWizardDialog
            siteId={id}
            open={wizardOpen}
            onOpenChange={setWizardOpen}
            existingFieldNames={data.map((field) => field.name)}
            onApplied={() => void refreshFields()}
          />
        </>
      )}
    </LoadProvider>
  );
}
