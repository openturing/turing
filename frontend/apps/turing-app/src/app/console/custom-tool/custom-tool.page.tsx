import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { CustomToolForm } from "@/components/custom-tool/custom-tool.form";
import type { BreadcrumbItem } from "@/contexts/breadcrumb.context";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import type { TurCustomTool } from "@/models/customtool/custom-tool.model.ts";
import { TurCustomToolService } from "@/services/customtool/custom-tool.service";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

/**
 * @since 2026.2.5
 */
const turCustomToolService = new TurCustomToolService();

export default function CustomToolPage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const [tool, setTool] = useState<TurCustomTool>();
  const [isNew, setIsNew] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [breadcrumb, setBreadcrumb] = useState<BreadcrumbItem[] | undefined>(
    id === "new" ? [{ label: t("customTool.newCustomTool") }] : undefined
  );
  useSubPageBreadcrumb(breadcrumb);

  useEffect(() => {
    if (id === "new") {
      setTool({} as TurCustomTool);
      setIsNew(true);
    } else {
      turCustomToolService.get(id).then((t2) => {
        setTool(t2);
        setBreadcrumb([{ label: t2.title, href: `${ROUTES.CUSTOM_TOOL_INSTANCE}/${t2.id}` }]);
      }).catch(() => setError(t("common.connectionError", { resource: t("customTool.title") })));
      setIsNew(false);
    }
  }, [id]);

  return (
    <LoadProvider checkIsNotUndefined={tool} error={error} tryAgainUrl={`${ROUTES.CUSTOM_TOOL_INSTANCE}/${id}`}>
      {tool && <CustomToolForm value={tool} isNew={isNew} />}
    </LoadProvider>
  );
}
