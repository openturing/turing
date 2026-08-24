import * as React from "react";

import { useSnFieldStatus, useSnFields } from "@/api/queries/sn-field.queries";
import { queryKeys } from "@/api/queries/keys";
import { useQueryClient } from "@tanstack/react-query";
import { ROUTES } from "@/app/routes.const";
import { BentoHero, bentoChipClass } from "@/components/bento";
import { LoadProvider } from "@/components/loading-provider";
import { SNSiteFieldGridList } from "@/components/sn/fields/field.grid.list";
import { SNFieldWizardDialog } from "@/components/sn/fields/sn.field.wizard.dialog";
import { GradientButton } from "@/components/ui/gradient-button";
import { GradientButtonLink } from "@viglet/viglet-design-system/router";
import type { TurSNSiteField } from "@/models/sn/sn-site-field.model.ts";
import { IconAlignBoxCenterStretch, IconPlus, IconSparkles } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

/**
 * Bento SN fields sub-CRUD — T558. Reuses the console `SNSiteFieldGridList`
 * (its row edit link is relative, so it resolves to the Bento field editor) +
 * `SNFieldWizardDialog` verbatim, wrapped in a `BentoHero`. The "New field"
 * action points at the Bento field editor route.
 */
export default function BentoSNFieldListPage() {
  const { id } = useParams() as { id: string };
  const [wizardOpen, setWizardOpen] = React.useState(false);
  const { t } = useTranslation();
  const instanceRoute = `${ROUTES.BENTO_SN_INSTANCE}/${id}`;

  // T1018 — the fields and the engine's status for them are two cache entries,
  // and the page renders neither until both arrive. `refreshFields` used to
  // re-run both calls by hand after the wizard closed; invalidating the family
  // does the same and also refreshes whatever else is reading them.
  const queryClient = useQueryClient();
  const { data, isError: fieldsError } = useSnFields(id);
  const { data: statusFields, isError: statusError } = useSnFieldStatus(id);
  const error = fieldsError || statusError
    ? t("common.connectionError", { resource: t("sn.fields.title") })
    : null;

  const refreshFields = React.useCallback(
    () => queryClient.invalidateQueries({ queryKey: queryKeys.snFields.all() }),
    [queryClient],
  );

  const refreshStatus = React.useCallback(
    () => queryClient.invalidateQueries({ queryKey: queryKeys.snFields.status(id) }),
    [queryClient, id],
  );

  /**
   * The grid toggles a flag optimistically and rolls back if the save fails, so
   * it needs a setter over the list. T1018 — that list is a cache entry now, and
   * `setQueryData` takes the same `(prev) => next` updater, so the optimistic
   * write and its rollback land on the cache instead of on local state and the
   * grid's own contract is unchanged.
   */
  const setSnField = React.useCallback(
    (action: React.SetStateAction<TurSNSiteField[]>) => {
      queryClient.setQueryData<TurSNSiteField[]>(queryKeys.snFields.list(id), (prev) =>
        typeof action === "function" ? action(prev ?? []) : action,
      );
    },
    [queryClient, id],
  );

  return (
    <LoadProvider checkIsNotUndefined={data && statusFields} error={error} tryAgainUrl={`${instanceRoute}/field`}>
      {data && statusFields && (
        <>
          <BentoHero
            backTo={instanceRoute}
            backLabel={t("sn.title")}
            leading={
              <span className={`grid h-12 w-12 place-items-center rounded-2xl ${bentoChipClass("emerald")} text-white shadow-md`}>
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
            setSnField={setSnField}
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
