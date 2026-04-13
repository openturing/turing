import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SNSiteFieldForm } from "@/components/sn/fields/sn.site.field.form";
import type { TurSNSiteField } from "@/models/sn/sn-site-field.model";
import { TurSNFieldService } from "@/services/sn/sn.field.service";
import { type BreadcrumbItem } from "@/contexts/breadcrumb.context";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";

const turSNFieldService = new TurSNFieldService();

export default function SNSiteFieldPage() {
  const navigate = useNavigate();
  const { id, fieldId } = useParams() as { id: string, fieldId: string };
  const [snField, setSnField] = useState<TurSNSiteField>();
  const [isNew, setIsNew] = useState<boolean>(true);
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [breadcrumb, setBreadcrumb] = useState<BreadcrumbItem[]>();
  const { t } = useTranslation();

  useEffect(() => {
    if (fieldId === "new") {
      turSNFieldService.query(id).then(() => setSnField({} as TurSNSiteField)).catch(() => setError("Connection error or timeout while fetching fields."));
      setBreadcrumb([{ label: t("sn.fields.title"), href: `${ROUTES.SN_INSTANCE}/${id}/field` }, { label: t("common.new") }]);
    } else {
      turSNFieldService.get(id, fieldId).then((field) => {
        setSnField(field);
        setBreadcrumb([{ label: t("sn.fields.title"), href: `${ROUTES.SN_INSTANCE}/${id}/field` }, { label: field.name || fieldId }]);
      }).catch(() => setError("Connection error or timeout while fetching field details."));
      setIsNew(false);
    }
  }, [id, fieldId]);

  useSubPageBreadcrumb(breadcrumb);
  async function onDelete() {
    if (!snField) return;
    try {
      if (await turSNFieldService.delete(id, snField)) {
        toast.success(t("sn.fields.deleted"));
        navigate(`${ROUTES.SN_INSTANCE}/${id}/field`);
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
    <LoadProvider checkIsNotUndefined={snField} error={error} tryAgainUrl={`${ROUTES.SN_INSTANCE}/${id}/field`}>
      {snField && <SNSiteFieldForm snSiteId={id} snField={snField} isNew={isNew} onDelete={isNew ? undefined : onDelete} open={open} setOpen={setOpen} />}
    </LoadProvider>
  )
}
