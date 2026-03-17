import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SNSiteFacetedFieldForm } from "@/components/sn/facet/sn.site.faceted.field.form";
import { SubPageHeader } from "@/components/sub.page.header";
import type { TurSNSiteField } from "@/models/sn/sn-site-field.model";
import { TurSNFieldService } from "@/services/sn/sn.field.service";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import { IconFilter } from "@tabler/icons-react";
import { useEffect, useRef, useState } from "react";
import { useParams } from "react-router-dom";

const turSNFieldService = new TurSNFieldService();

export default function SNSiteFacetedFieldPage() {
  const { id, facetedFieldId } = useParams() as { id: string, facetedFieldId: string };
  const [snField, setSnField] = useState<TurSNSiteField>();
  const [isNew, setIsNew] = useState<boolean>(true);
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { pushItem, popItem } = useBreadcrumb();
  const breadcrumbAdded = useRef(false);

  useEffect(() => {
    if (facetedFieldId === "new") {
      turSNFieldService.query(id).then(() => setSnField({} as TurSNSiteField)).catch(() => setError("Connection error or timeout while fetching faceted fields."));
      if (!breadcrumbAdded.current) {
        pushItem({ label: "Facets", href: `${ROUTES.SN_INSTANCE}/${id}/facet` });
        pushItem({ label: "New" });
        breadcrumbAdded.current = true;
      }
    } else {
      turSNFieldService.get(id, facetedFieldId).then((field) => {
        setSnField(field);
        if (!breadcrumbAdded.current) {
          pushItem({ label: "Facets", href: `${ROUTES.SN_INSTANCE}/${id}/facet` });
          pushItem({ label: field.name || facetedFieldId });
          breadcrumbAdded.current = true;
        }
      }).catch(() => setError("Connection error or timeout while fetching faceted field details."));
      setIsNew(false);
    }
  }, [id, facetedFieldId]);

  useEffect(() => {
    return () => {
      if (breadcrumbAdded.current) {
        popItem();
        popItem();
        breadcrumbAdded.current = false;
      }
    };
  }, []);
  return (
    <LoadProvider checkIsNotUndefined={snField} error={error} tryAgainUrl={`${ROUTES.SN_INSTANCE}/${id}/field/${facetedFieldId}`}>
      {snField && (<> <SubPageHeader
        icon={IconFilter}
        name={snField.name}
        feature="Faceted Field"
        description={snField.description}
        open={open}
        setOpen={setOpen} />
        <SNSiteFacetedFieldForm snSiteId={id} snField={snField} isNew={isNew} />
      </>)}
    </LoadProvider>
  )
}
