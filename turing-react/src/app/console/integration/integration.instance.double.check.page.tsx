import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from "@/components/ui/accordion";
import { Badge } from "@/components/ui/badge";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import type { TurIntegrationAemSource } from "@/models/integration/integration-aem-source.model";
import type { TurIntegrationDoubleCheck } from "@/models/integration/integration-double-check.model";
import type { TurIntegrationIndexing } from "@/models/integration/integration-indexing.model";
import type { TurIntegrationMonitoring } from "@/models/integration/integration-monitoring.model";
import { TurIntegrationAemSourceService } from "@/services/integration/integration-aem-source.service";
import { TurIntegrationDoubleCheckService } from "@/services/integration/integration-double-check.service";
import { IconListCheck } from "@tabler/icons-react";
import { useEffect, useMemo, useState } from "react";
import { useParams } from "react-router-dom";

const NO_SOURCE = "";

export default function IntegrationInstanceDoubleCheckPage() {
  const { id } = useParams<{ id: string }>();

  const [sources, setSources] = useState<TurIntegrationAemSource[]>([]);
  const [selectedSource, setSelectedSource] = useState<string>(NO_SOURCE);
  const [doubleCheck, setDoubleCheck] = useState<TurIntegrationDoubleCheck | null>(null);
  const [unprocessed, setUnprocessed] = useState<TurIntegrationMonitoring | null>(null);
  const [loading, setLoading] = useState(false);
  const [loadingUnprocessed, setLoadingUnprocessed] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const { pushItem, popItem } = useBreadcrumb();

  const aemSourceService = useMemo(() => {
    return id ? new TurIntegrationAemSourceService(id) : null;
  }, [id]);

  const doubleCheckService = useMemo(() => {
    return id ? new TurIntegrationDoubleCheckService(id) : null;
  }, [id]);

  useEffect(() => {
    if (!aemSourceService || !id) return;
    let added = false;
    aemSourceService
      .query()
      .then((data) => {
        setSources(data);
        if (data.length > 0) {
          setSelectedSource(data[0].name);
        }
        pushItem({ label: "Double Check", href: `${ROUTES.INTEGRATION_INSTANCE}/${id}/double-check` });
        added = true;
      })
      .catch(() => setError("Failed to load sources."));
    return () => {
      if (added) popItem();
    };
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
      .catch(() => setError("Failed to load validation data."))
      .finally(() => setLoading(false));

    doubleCheckService
      .getUnprocessed(selectedSource)
      .then((data) => setUnprocessed(data))
      .catch(() => {})
      .finally(() => setLoadingUnprocessed(false));
  }, [selectedSource, id]);

  if (!id) {
    return <div>Invalid integration instance ID</div>;
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
        name="Double Check"
        feature="Double Check"
        description="Compare content between the connector and the search engine index."
      />
      <div className="px-6 py-4 space-y-4">
        {sources.length > 0 && (
          <div className="flex items-center gap-3">
            <span className="text-sm text-muted-foreground">Source:</span>
            <Select value={selectedSource} onValueChange={setSelectedSource}>
              <SelectTrigger className="w-64 h-8 text-sm">
                <SelectValue placeholder="Select a source" />
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
          <div className="text-sm text-muted-foreground italic py-4">Loading validation data…</div>
        )}

        {!loading && !loadingUnprocessed && (doubleCheck || unprocessed) && (
          <Tabs defaultValue="unprocessed">
            <TabsList className="bg-transparent border-none">
              <TabsTrigger value="unprocessed" className="gap-2">
                Unprocessed
                <Badge variant="secondary">{totalUnprocessed}</Badge>
              </TabsTrigger>
              <TabsTrigger value="missing" className="gap-2">
                Missing
                <Badge variant="secondary">{totalMissing}</Badge>
              </TabsTrigger>
              <TabsTrigger value="extra" className="gap-2">
                Extra
                <Badge variant="secondary">{totalExtra}</Badge>
              </TabsTrigger>
            </TabsList>

            <TabsContent value="unprocessed">
              <p className="text-xs text-muted-foreground mb-3">
                Content accessible in the source (HTTP 200) but <strong>not yet processed</strong> by the connector.
              </p>
              <UnprocessedList items={unprocessed?.indexing ?? []} />
            </TabsContent>

            <TabsContent value="missing">
              <p className="text-xs text-muted-foreground mb-3">
                Content present in the connector but <strong>not indexed</strong> in the search engine.
              </p>
              {doubleCheck && <DoubleCheckCoreList coreMap={doubleCheck.missing} emptyMessage="No missing content found." />}
            </TabsContent>

            <TabsContent value="extra">
              <p className="text-xs text-muted-foreground mb-3">
                Content present in the search engine index but <strong>not found</strong> in the connector.
              </p>
              {doubleCheck && <DoubleCheckCoreList coreMap={doubleCheck.extra} emptyMessage="No extra content found." />}
            </TabsContent>
          </Tabs>
        )}
      </div>
    </LoadProvider>
  );
}

function UnprocessedList({ items }: { items: TurIntegrationIndexing[] }) {
  if (items.length === 0) {
    return (
      <div className="text-sm text-muted-foreground italic py-4">No unprocessed content found.</div>
    );
  }

  return (
    <div className="border rounded-md overflow-hidden">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b bg-muted/50">
            <th className="text-left px-3 py-2 font-medium">Object ID</th>
            <th className="text-left px-3 py-2 font-medium">Status</th>
            <th className="text-left px-3 py-2 font-medium">Created</th>
          </tr>
        </thead>
        <tbody>
          {items.map((item) => (
            <tr key={item.id} className="border-b last:border-b-0 hover:bg-muted/30">
              <td className="px-3 py-2 font-mono text-xs truncate max-w-md" title={item.objectId}>
                {item.objectId}
              </td>
              <td className="px-3 py-2">
                <Badge variant="outline" className="text-xs">{item.status}</Badge>
              </td>
              <td className="px-3 py-2 text-xs text-muted-foreground">
                {item.created ? new Date(item.created).toLocaleString() : "—"}
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
}: {
  coreMap: Record<string, string[]>;
  emptyMessage: string;
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
                  className="text-xs font-mono text-muted-foreground px-2 py-1 rounded hover:bg-muted truncate"
                  title={path}
                >
                  {path}
                </li>
              ))}
            </ul>
          </AccordionContent>
        </AccordionItem>
      ))}
    </Accordion>
  );
}
