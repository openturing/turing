import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { DropdownMenuItem } from "@/components/ui/dropdown-menu";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import type { TurSNSite } from "@/models/sn/sn-site.model.ts";
import type { TurGridItemAction, TurGridItemDropdownItem } from "@/models/ui/grid-item";
import { TurSNSiteService } from "@/services/sn/sn.service";
import { TurFeaturesService } from "@/services/system/features.service";
import { IconDownload, IconSearch, IconSettings } from "@tabler/icons-react";
import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { toast } from "@viglet/viglet-design-system";

const turSNSiteService = new TurSNSiteService();
const turFeaturesService = new TurFeaturesService();

export default function SNSiteListPage() {
  const { t } = useTranslation();
  const [snInstances, setSnInstances] = useState<TurSNSite[]>();
  const [error, setError] = useState<string | null>(null);
  const [exporting, setExporting] = useState(false);
  const [storageEnabled, setStorageEnabled] = useState(false);

  useEffect(() => {
    turSNSiteService.query().then(setSnInstances).catch(() => setError(t("common.connectionError", { resource: "instances" })));
    turFeaturesService.getFeatures().then((features) => {
      setStorageEnabled(features.storageEnabled);
    }).catch(() => setStorageEnabled(false));
  }, []);

  const buildActions = useCallback((site: TurSNSite): TurGridItemAction[] => {
    const searchUrl = `/sn/${site.name}`;
    const defaultUrl = `/sn/${site.name}?_embedded=true`;
    const hasTemplate = storageEnabled && !!site.searchTemplate?.trim();

    const searchAction: TurGridItemAction = hasTemplate
      ? {
          icon: IconSearch,
          label: t("forms.localeTable.openSearch"),
          url: searchUrl,
          external: true,
          dropdownItems: [
            {
              iconifyIcon: "mdi:card-search",
              label: `${t("forms.localeTable.openSearch")} (${site.searchTemplate})`,
              url: searchUrl,
              external: true,
            },
            {
              iconifyIcon: "mdi:card-search-outline",
              label: t("forms.localeTable.searchDefault"),
              url: defaultUrl,
              external: true,
            },
          ] as TurGridItemDropdownItem[],
        }
      : {
          icon: IconSearch,
          label: t("forms.localeTable.openSearch"),
          url: searchUrl,
          external: true,
        };

    const configureAction: TurGridItemAction = {
      icon: IconSettings,
      label: t("forms.common.configure"),
      url: `${ROUTES.SN_INSTANCE}/${site.id}`,
    };

    return [searchAction, configureAction];
  }, [storageEnabled, t]);

  const gridItemList = useGridAdapter(snInstances, {
    name: "name",
    description: "description",
    url: (item) => `${ROUTES.SN_INSTANCE}/${item.id}`,
    icon: "icon",
    actions: buildActions,
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
