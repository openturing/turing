import { ROUTES } from "@/app/routes.const";
import { BentoHero } from "@/components/bento";
import { LoadProvider } from "@/components/loading-provider";
import { SNSiteFieldForm } from "@/components/sn/fields/sn.site.field.form";
import type { TurSNSiteField } from "@/models/sn/sn-site-field.model";
import { TurSNFieldService } from "@/services/sn/sn.field.service";
import { IconAlignBoxCenterStretch } from "@tabler/icons-react";
import { toast } from "@viglet/viglet-design-system";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useNavigate, useParams } from "react-router-dom";

const turSNFieldService = new TurSNFieldService();

/**
 * Bento SN field editor — T558. Reuses the console `SNSiteFieldForm` verbatim,
 * passing `baseRoute={ROUTES.BENTO_SN_INSTANCE}` so its save/cancel navigate
 * back to the Bento field list instead of the console.
 */
export default function BentoSNFieldPage() {
  const navigate = useNavigate();
  const { id, fieldId } = useParams() as { id: string; fieldId: string };
  const [snField, setSnField] = useState<TurSNSiteField>();
  const [isNew, setIsNew] = useState<boolean>(true);
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { t } = useTranslation();
  const fieldListRoute = `${ROUTES.BENTO_SN_INSTANCE}/${id}/field`;

  useEffect(() => {
    if (fieldId === "new") {
      turSNFieldService.query(id).then(() => setSnField({} as TurSNSiteField)).catch(() => setError(t("common.connectionError", { resource: t("sn.fields.title") })));
    } else {
      turSNFieldService.get(id, fieldId).then(setSnField).catch(() => setError(t("common.connectionError", { resource: t("sn.fields.title") })));
      setIsNew(false);
    }
  }, [id, fieldId, t]);

  async function onDelete() {
    if (!snField) return;
    try {
      if (await turSNFieldService.delete(id, snField)) {
        toast.success(t("sn.fields.deleted"));
        navigate(fieldListRoute);
      } else {
        toast.error(t("sn.fields.notDeleted"));
      }
    } catch (error) {
      console.error("Delete error", error);
      toast.error(t("sn.fields.notDeleted"));
    }
    setOpen(false);
  }

  return (
    <LoadProvider checkIsNotUndefined={snField} error={error} tryAgainUrl={fieldListRoute}>
      {snField && (
        <>
          <BentoHero
            eyebrow={<Link to={fieldListRoute} className="hover:text-foreground">{t("sn.fields.title")}</Link>}
            leading={
              <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-emerald-500 to-teal-600 text-white shadow-md">
                <IconAlignBoxCenterStretch size={24} />
              </span>
            }
            title={isNew ? t("common.new") : snField.name || fieldId}
            subtitle={t("sn.fields.description")}
          />
          <SNSiteFieldForm
            snSiteId={id}
            snField={snField}
            isNew={isNew}
            baseRoute={ROUTES.BENTO_SN_INSTANCE}
            onDelete={isNew ? undefined : onDelete}
            open={open}
            setOpen={setOpen}
          />
        </>
      )}
    </LoadProvider>
  );
}
