import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SNSiteFacetedFieldForm } from "@/components/sn/facet/sn.site.faceted.field.form";
import type { TurSNSiteField } from "@/models/sn/sn-site-field.model";
import { TurSNFieldService } from "@/services/sn/sn.field.service";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

const service = new TurSNFieldService();

/** Bento SN faceted-field detail — T576. */
export default function BentoSNFacetedFieldPage() {
  const { id, facetedFieldId } = useParams() as { id: string; facetedFieldId: string };
  const { t } = useTranslation();
  const [snField, setSnField] = useState<TurSNSiteField>();
  const [isNew, setIsNew] = useState(true);
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const facetRoute = `${ROUTES.BENTO_SN_INSTANCE}/${id}/facet`;

  useEffect(() => {
    if (facetedFieldId === "new") {
      service.query(id).then(() => setSnField({} as TurSNSiteField))
        .catch(() => setError(t("common.connectionError", { resource: t("sn.facets.title") })));
      setIsNew(true);
    } else {
      service.get(id, facetedFieldId).then((f) => { setSnField(f); setIsNew(false); })
        .catch(() => setError(t("common.connectionError", { resource: t("sn.facets.title") })));
    }
  }, [id, facetedFieldId, t]);

  return (
    <LoadProvider checkIsNotUndefined={snField} error={error} tryAgainUrl={`${facetRoute}/field/${facetedFieldId}`}>
      {snField && (
        <SNSiteFacetedFieldForm
          snSiteId={id}
          snField={snField}
          isNew={isNew}
          open={open}
          setOpen={setOpen}
          baseRoute={ROUTES.BENTO_SN_INSTANCE}
          chrome="bento"
        />
      )}
    </LoadProvider>
  );
}
