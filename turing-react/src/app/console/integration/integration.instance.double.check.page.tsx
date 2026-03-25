import { ROUTES } from "@/app/routes.const";
import { BadgeAemEnv } from "@/components/badge-aem-env";
import { BadgeIndexingStatus } from "@/components/badge-indexing-status";
import { BadgeLocale } from "@/components/badge-locale";
import { BadgeSites } from "@/components/badge-sites";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from "@/components/ui/accordion";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { GradientButton } from "@/components/ui/gradient-button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import type { BreadcrumbItem } from "@/contexts/breadcrumb.context";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import type { TurIntegrationAemSource } from "@/models/integration/integration-aem-source.model";
import type { TurIntegrationDoubleCheck } from "@/models/integration/integration-double-check.model";
import type { TurIntegrationIndexing } from "@/models/integration/integration-indexing.model";
import type { TurIntegrationMonitoring } from "@/models/integration/integration-monitoring.model";
import { TurIntegrationAemSourceService } from "@/services/integration/integration-aem-source.service";
import { TurIntegrationDoubleCheckService } from "@/services/integration/integration-double-check.service";
import { IconListCheck } from "@tabler/icons-react";
import { Play } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";
import { toast } from "sonner";

const NO_SOURCE = "";

export default function IntegrationInstanceDoubleCheckPage() {
  const { t } = useTranslation();
  const { id } = useParams<{ id: string }>();

  const [sources, setSources] = useState<TurIntegrationAemSource[]>([]);
  const [selectedSource, setSelectedSource] = useState<string>(NO_SOURCE);
  const [doubleCheck, setDoubleCheck] = useState<TurIntegrationDoubleCheck | null>(null);
  const [unprocessed, setUnprocessed] = useState<TurIntegrationMonitoring | null>(null);
  const [loading, setLoading] = useState(false);
  const [loadingUnprocessed, setLoadingUnprocessed] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [breadcrumb, setBreadcrumb] = useState<BreadcrumbItem[]>();
  useSubPageBreadcrumb(breadcrumb);

  const aemSourceService = useMemo(() => {
    return id ? new TurIntegrationAemSourceService(id) : null;
  }, [id]);

  const doubleCheckService = useMemo(() => {
    return id ? new TurIntegrationDoubleCheckService(id) : null;
  }, [id]);

  useEffect(() => {
    if (!aemSourceService || !id) return;
    aemSourceService
      .query()
      .then((data) => {
        setSources(data);
        if (data.length > 0) {
          setSelectedSource(data[0].name);
        }
        setBreadcrumb([{ label: t("integration.doubleCheck.title"), href: `${ROUTES.INTEGRATION_INSTANCE}/${id}/double-check` }]);
      })
      .catch(() => setError(t("integration.doubleCheck.loadSourcesFailed")));
  }, [id]);

  useEffect(() => {
    if (!doubleCheckService || !selectedSource) return;
    setLoading(true);
    setLoadingUnprocessed(true);
    setDoubleCheck(null);
    setUnprocessed(null);
    setError(null);

    doubleCheckService
      .get(selectedSource)
      .then((data) => setDoubleCheck(data))
      .catch(() => setError(t("integration.doubleCheck.loadValidationFailed")))
      .finally(() => setLoading(false));

    doubleCheckService
      .getUnprocessed(selectedSource)
      .then((data) => setUnprocessed(data))
      .catch(() => { })
      .finally(() => setLoadingUnprocessed(false));
  }, [selectedSource, id]);

  const handleIndex = useCallback(async (contentId: string) => {
    if (!doubleCheckService || !selectedSource) return;
    try {
      await doubleCheckService.indexById(selectedSource, contentId);
      toast.success(t("integration.doubleCheck.indexSent"));
    } catch {
      toast.error(t("integration.doubleCheck.indexFailed"));
    }
  }, [doubleCheckService, selectedSource, t]);

  const handleIndexAll = useCallback(async (tab: string) => {
    if (!doubleCheckService || !selectedSource) return;
    try {
      await doubleCheckService.indexAllByTab(selectedSource, tab);
      toast.success(t("integration.doubleCheck.indexAllSent"));
    } catch {
      toast.error(t("integration.doubleCheck.indexAllFailed"));
    }
  }, [doubleCheckService, selectedSource, t]);

  if (!id) {
    return <div>{t("integration.indexingStats.invalidId")}</div>;
  }

  const totalUnprocessed = unprocessed?.indexing?.length ?? 0;

  const totalMissing = doubleCheck
    ? Object.values(doubleCheck.missing).reduce((acc, paths) => acc + paths.length, 0)
    : 0;

  const totalExtra = doubleCheck
    ? Object.values(doubleCheck.extra).reduce((acc, paths) => acc + paths.length, 0)
    : 0;

  return (
    <LoadProvider checkIsNotUndefined={sources} error={error} tryAgainUrl={`${ROUTES.INTEGRATION_INSTANCE}/${id}/double-check`}>
      <SubPageHeader
        icon={IconListCheck}
        name={t("integration.doubleCheck.title")}
        feature={t("integration.doubleCheck.title")}
        description={t("integration.doubleCheck.description")}
      />
      <div className="px-6 py-4 space-y-4">
        {sources.length > 0 && (
          <div className="flex items-center gap-3">
            <span className="text-sm text-muted-foreground">{t("integration.doubleCheck.sourceLabel")}</span>
            <Select value={selectedSource} onValueChange={setSelectedSource}>
              <SelectTrigger className="w-64 h-8 text-sm">
                <SelectValue placeholder={t("integration.doubleCheck.selectSource")} />
              </SelectTrigger>
              <SelectContent>
                {sources.map((source) => (
                  <SelectItem key={source.id} value={source.name} className="text-sm">
                    {source.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        )}

        {loading && (
          <div className="text-sm text-muted-foreground italic py-4">{t("integration.doubleCheck.loading")}</div>
        )}

        {!loading && !loadingUnprocessed && (doubleCheck || unprocessed) && (
          <Tabs defaultValue="unprocessed">
            <TabsList className="bg-transparent border-none">
              <TabsTrigger value="unprocessed" className="gap-2">
                {t("integration.doubleCheck.unprocessed")}
                <Badge variant="secondary">{totalUnprocessed}</Badge>
              </TabsTrigger>
              <TabsTrigger value="missing" className="gap-2">
                {t("integration.doubleCheck.missing")}
                <Badge variant="secondary">{totalMissing}</Badge>
              </TabsTrigger>
              <TabsTrigger value="extra" className="gap-2">
                {t("integration.doubleCheck.extra")}
                <Badge variant="secondary">{totalExtra}</Badge>
              </TabsTrigger>
            </TabsList>

            <TabsContent value="unprocessed">
              <div className="flex items-center justify-between mb-3">
                <p className="text-xs text-muted-foreground">
                  {t("integration.doubleCheck.unprocessedDesc")}
                </p>
                {totalUnprocessed > 0 && (
                  <GradientButton
                    size="sm"
                    variant="outline"
                    className="h-7 text-xs gap-1"
                    onClick={() => handleIndexAll("unprocessed")}
                  >
                    <Play className="h-3 w-3" />
                    {t("integration.doubleCheck.indexAll")}
                  </GradientButton>
                )}
              </div>
              <UnprocessedList items={unprocessed?.indexing ?? []} t={t} onIndex={handleIndex} />
            </TabsContent>

            <TabsContent value="missing">
              <div className="flex items-center justify-between mb-3">
                <p className="text-xs text-muted-foreground">
                  {t("integration.doubleCheck.missingDesc")}
                </p>
                {totalMissing > 0 && (
                  <GradientButton
                    size="sm"
                    variant="outline"
                    className="h-7 text-xs gap-1"
                    onClick={() => handleIndexAll("missing")}
                  >
                    <Play className="h-3 w-3" />
                    {t("integration.doubleCheck.indexAll")}
                  </GradientButton>
                )}
              </div>
              {doubleCheck && <DoubleCheckCoreList coreMap={doubleCheck.missing} emptyMessage={t("integration.doubleCheck.noMissing")} t={t} onIndex={handleIndex} />}
            </TabsContent>

            <TabsContent value="extra">
              <div className="flex items-center justify-between mb-3">
                <p className="text-xs text-muted-foreground">
                  {t("integration.doubleCheck.extraDesc")}
                </p>
                {totalExtra > 0 && (
                  <GradientButton
                    size="sm"
                    variant="outline"
                    className="h-7 text-xs gap-1"
                    onClick={() => handleIndexAll("extra")}
                  >
                    <Play className="h-3 w-3" />
                    {t("integration.doubleCheck.indexAll")}
                  </GradientButton>
                )}
              </div>
              {doubleCheck && <DoubleCheckCoreList coreMap={doubleCheck.extra} emptyMessage={t("integration.doubleCheck.noExtra")} t={t} onIndex={handleIndex} />}
            </TabsContent>
          </Tabs>
        )}
      </div>
    </LoadProvider>
  );
}

function UnprocessedList({ items, t, onIndex }: { items: TurIntegrationIndexing[]; t: (key: string) => string; onIndex: (contentId: string) => void }) {
  if (items.length === 0) {
    return (
      <div className="text-sm text-muted-foreground italic py-4">{t("integration.doubleCheck.noUnprocessed")}</div>
    );
  }

  return (
    <div className="border rounded-md overflow-hidden">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b bg-muted/50">
            <th className="text-left px-3 py-2 font-medium">{t("integration.doubleCheck.objectIdCol")}</th>
            <th className="text-left px-3 py-2 font-medium">{t("integration.doubleCheck.localeCol")}</th>
            <th className="text-left px-3 py-2 font-medium">{t("integration.doubleCheck.sitesCol")}</th>
            <th className="text-left px-3 py-2 font-medium">{t("integration.doubleCheck.environmentCol")}</th>
            <th className="text-left px-3 py-2 font-medium">{t("integration.doubleCheck.statusCol")}</th>
            <th className="text-left px-3 py-2 font-medium">{t("integration.doubleCheck.createdCol")}</th>
            <th className="text-left px-3 py-2 font-medium">{t("integration.doubleCheck.actionsCol")}</th>
          </tr>
        </thead>
        <tbody>
          {items.map((item) => (
            <tr key={item.id} className="border-b last:border-b-0 hover:bg-muted/30">
              <td className="px-3 py-2 font-mono text-xs truncate max-w-md" title={item.objectId}>
                {item.objectId}
              </td>
              <td className="px-3 py-2 text-xs">
                <BadgeLocale locale={item.locale} />
              </td>
              <td className="px-3 py-2 text-xs">
                <BadgeSites sites={item.sites} />
              </td>
              <td className="px-3 py-2 text-xs">
                <BadgeAemEnv environment={item.environment} />
              </td>
              <td className="px-3 py-2">
                <BadgeIndexingStatus status={item.status} />
              </td>
              <td className="px-3 py-2 text-xs text-muted-foreground">
                {item.created ? new Date(item.created).toLocaleString() : "—"}
              </td>
              <td className="px-3 py-2">
                <Button
                  variant="outline"
                  size="sm"
                  className="h-7 text-xs gap-1"
                  onClick={() => onIndex(item.objectId)}
                >
                  <Play className="h-3 w-3" />
                  {t("integration.doubleCheck.index")}
                </Button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function DoubleCheckCoreList({
  coreMap,
  emptyMessage,
  t,
  onIndex,
}: {
  coreMap: Record<string, string[]>;
  emptyMessage: string;
  t: (key: string) => string;
  onIndex: (contentId: string) => void;
}) {
  const entries = Object.entries(coreMap).filter(([, paths]) => paths.length > 0);

  if (entries.length === 0) {
    return (
      <div className="text-sm text-muted-foreground italic py-4">{emptyMessage}</div>
    );
  }

  return (
    <Accordion type="multiple" defaultValue={entries.map(([core]) => core)}>
      {entries.map(([core, paths]) => (
        <AccordionItem key={core} value={core}>
          <AccordionTrigger className="text-sm font-medium gap-2">
            <span className="font-mono">{core}</span>
            <Badge variant="outline" className="ml-2 font-mono text-xs">
              {paths.length}
            </Badge>
          </AccordionTrigger>
          <AccordionContent>
            <ul className="space-y-1 pt-1 pb-2">
              {paths.map((path) => (
                <li
                  key={path}
                  className="flex items-center justify-between text-xs font-mono text-muted-foreground px-2 py-1 rounded hover:bg-muted"
                >
                  <span className="truncate" title={path}>{path}</span>
                  <Button
                    variant="outline"
                    size="sm"
                    className="h-6 text-xs gap-1 ml-2 shrink-0"
                    onClick={() => onIndex(path)}
                  >
                    <Play className="h-3 w-3" />
                    {t("integration.doubleCheck.index")}
                  </Button>
                </li>
              ))}
            </ul>
          </AccordionContent>
        </AccordionItem>
      ))}
    </Accordion>
  );
}
