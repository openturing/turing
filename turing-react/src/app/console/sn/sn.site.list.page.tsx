import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { DropdownMenuItem } from "@/components/ui/dropdown-menu";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import type { TurSNSite } from "@/models/sn/sn-site.model.ts";
import { TurSNSiteService } from "@/services/sn/sn.service";
import { IconDownload, IconSearch } from "@tabler/icons-react";
import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { toast } from "@openviglet/viglet-design-system";

const turSNSiteService = new TurSNSiteService();

export default function SNSiteListPage() {
  const { t } = useTranslation();
  const [snInstances, setSnInstances] = useState<TurSNSite[]>();
  const [error, setError] = useState<string | null>(null);
  const [exporting, setExporting] = useState(false);

  useEffect(() => {
    turSNSiteService.query().then(setSnInstances).catch(() => setError(t("common.connectionError", { resource: "instances" })));
  }, []);

  const gridItemList = useGridAdapter(snInstances, {
    name: "name",
    description: "description",
    url: (item) => `${ROUTES.SN_INSTANCE}/${item.id}`,
    icon: "icon",
  });

  const handleExportAll = useCallback(async () => {
    setExporting(true);
    try {
      const blob = await turSNSiteService.exportAll();
      if (blob) {
        const url = globalThis.URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        a.download = `sn-sites-all-${new Date().toISOString()}.zip`;
        document.body.appendChild(a);
        a.click();
        globalThis.URL.revokeObjectURL(url);
        a.remove();
        toast.success(t("sn.exportSuccess"));
      }
    } catch {
      toast.error(t("sn.exportFailed"));
    } finally {
      setExporting(false);
    }
  }, []);

  return (
    <LoadProvider checkIsNotUndefined={snInstances} error={error} tryAgainUrl={`${ROUTES.SN_INSTANCE}`}>
      {gridItemList.length > 0 ? (
        <GridList gridItemList={gridItemList ?? []}>
          <GridList.NewButton to={`${ROUTES.SN_INSTANCE}/new`} label={t("sn.title")} />
          <GridList.Action>
            <DropdownMenuItem onClick={handleExportAll} disabled={exporting}>
              <IconDownload className="size-4 mr-2" />
              {exporting ? t("sn.exporting") : t("sn.exportAll")}
            </DropdownMenuItem>
          </GridList.Action>
        </GridList>
      ) : (
        <BlankSlate
          icon={IconSearch}
          title={t("sn.blankTitle")}
          description={t("sn.blankDescription")}
          buttonText={t("sn.newInstance")}
          urlNew={`${ROUTES.SN_INSTANCE}/new`} />
      )}
    </LoadProvider>
  );
}
