import { ROUTES } from "@/app/routes.const";
import { IntegrationIndexingManagerForm } from "@/components/integration/integration.indexing.manager.form";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { HoverCard, HoverCardContent, HoverCardTrigger } from "@/components/ui/hover-card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import type { TurIntegrationAemSource } from "@/models/integration/integration-aem-source.model";
import { TurIntegrationAemSourceService } from "@/services/integration/integration-aem-source.service";
import {
  IconAdjustmentsSearch,
  IconCloudDownload,
  IconCloudUpload,
  IconInfoCircle,
  IconPlus,
  IconTrash
} from "@tabler/icons-react";
import { useEffect, useMemo, useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";

// Type alias for tab values
type IndexingTabMode = "INDEXING" | "DEINDEXING" | "PUBLISHING" | "UNPUBLISHING";

type IndexingTabItem = {
  value: IndexingTabMode;
  title: string;
  icon: React.ComponentType<{ className?: string }>;
  color: string;
  activeBorder: string;
  description: string;
  detailedDescription: string;
  mode: IndexingTabMode;
};

export default function IntegrationInstanceIndexAdminPage() {
  const { id } = useParams() as { id: string };
  const [error, setError] = useState<string | null>(null);
  const turIntegrationAemSourceService = useMemo(() => new TurIntegrationAemSourceService(id), [id]);
  const [sources, setSources] = useState<TurIntegrationAemSource[]>();
  const { pushItem, popItem } = useBreadcrumb();
  const navigate = useNavigate();
  const location = useLocation();

  // Tab path mapping
  const tabPathMap: Record<IndexingTabMode, string> = {
    INDEXING: "indexing",
    DEINDEXING: "deindexing",
    PUBLISHING: "publishing",
    UNPUBLISHING: "unpublishing",
  };

  // Reverse mapping
  const pathTabMap: Record<string, IndexingTabMode> = Object.entries(tabPathMap).reduce(
    (acc, [tab, path]) => {
      acc[path] = tab as IndexingTabMode;
      return acc;
    },
    {} as Record<string, IndexingTabMode>
  );

  // Extract tab from URL
  const pathSegment = location.pathname.split("/").pop();
  const initialTab: IndexingTabMode = pathTabMap[pathSegment ?? ""] || "INDEXING";

  const [selectedTab, setSelectedTab] = useState<IndexingTabMode>(initialTab);

  useEffect(() => {
    const path = location.pathname.split("/").pop();
    const tab = pathTabMap[path ?? ""];
    if (tab && tab !== selectedTab) setSelectedTab(tab);
    else if (!tab && selectedTab !== "INDEXING") setSelectedTab("INDEXING");
  }, [location.pathname, selectedTab]);

  const handleTabChange = (value: string) => {
    const mode = value as IndexingTabMode;
    setSelectedTab(mode);
    navigate(
      `/admin/integration/instance/${id}/indexing-manager/${tabPathMap[mode]}`
    );
  };

  useEffect(() => {
    let added = false;
    turIntegrationAemSourceService
      .query()
      .then((sources) => {
        setSources(sources);
        pushItem({ label: "Indexing Manager" });
        added = true;
      })
      .catch(() => setError("Failed to load integration details"));
    return () => {
      if (added) popItem();
    };
  }, [turIntegrationAemSourceService, pushItem, popItem]);

  const items: IndexingTabItem[] = [
    {
      value: "INDEXING",
      title: "Indexing",
      icon: IconPlus,
      mode: "INDEXING",
      color: "text-green-500",
      activeBorder: "data-[state=active]:border-green-500",
      description:
        "Reindexes the selected content. If the item is already published, it will be automatically republished in the chosen environments.",
      detailedDescription:
        "Use this function to ensure the content is up-to-date in the Turing search index. The process supports selection by ID or URL and can be executed in different environments (Author or Publish). After reindexing, the content will be republished if it is already available in the Publish environment. Ideal for fixing inconsistencies or quickly updating indexed content."
    },
    {
      value: "DEINDEXING",
      title: "Deindexing",
      icon: IconTrash,
      mode: "DEINDEXING",
      color: "text-red-500",
      activeBorder: "data-[state=active]:border-red-500",
      description:
        "Removes the content from the search index, both in the Author and Publish environments.",
      detailedDescription:
        "Deindexing removes the content from the Turing index, making it invisible to searches in all selected environments. Use this option to remove obsolete, sensitive, or no longer relevant content from search results. The process is safe and does not affect the original content in AEM."
    },
    {
      value: "PUBLISHING",
      title: "Publishing",
      icon: IconCloudUpload,
      mode: "PUBLISHING",
      color: "text-blue-500",
      activeBorder: "data-[state=active]:border-blue-500",
      description: "Forces the publication of the content in the target environment, regardless of its current status.",
      detailedDescription:
        "This action immediately publishes the selected content in the Publish environment, even if it is not yet published in Author. Useful to ensure that updates or new content are available to end users without relying on the standard publication flow."
    },
    {
      value: "UNPUBLISHING",
      title: "Unpublishing",
      icon: IconCloudDownload,
      mode: "UNPUBLISHING",
      color: "text-orange-500",
      activeBorder: "data-[state=active]:border-orange-500",
      description: "Forces the unpublishing of the content, removing it from the air in the Publish environment.",
      detailedDescription:
        "Use this function to quickly remove content from the Publish environment, making it inaccessible to the public but keeping it available in Author for future edits. Unpublishing is immediate and independent of the previous content status."
    }
  ];

  return (
    <LoadProvider
      checkIsNotUndefined={sources}
      error={error}
      tryAgainUrl={`${ROUTES.INTEGRATION_INSTANCE}/${id}`}
    >
      <SubPageHeader
        icon={IconAdjustmentsSearch}
        feature="Indexing Manager"
        name="Indexing Manager"
        description="Directly manage and override content indexing states to ensure search accuracy."
      />
      <div className="w-full mx-auto mt-6 px-6 pb-6">
        <Tabs
          value={selectedTab}
          onValueChange={handleTabChange}
          className="w-full"
        >
          <TabsList className="w-full h-auto bg-transparent p-0 gap-4 flex-wrap justify-start">
            {items.map((item) => (
              <TabsTrigger
                key={item.value}
                value={item.value}
                className={`flex-1 min-w-45 max-w-55 flex flex-col items-center gap-2 py-5 px-2 h-auto border-2 border-transparent bg-card shadow-sm hover:bg-accent hover:text-accent-foreground data-[state=active]:shadow-md transition-all ${item.activeBorder}`}
              >
                <item.icon className={`w-8 h-8 ${item.color}`} />
                <span className="font-semibold text-base">{item.title}</span>
              </TabsTrigger>
            ))}
          </TabsList>

          <div className="mt-8 border rounded-xl p-8 bg-card shadow-md">
            {items.map((item) => (
              <TabsContent key={item.value} value={item.value} className="mt-0 focus-visible:ring-0">
                <div className="mb-8 pb-4 border-b flex flex-col md:flex-row md:items-center  gap-4">
                  <div className="flex items-center gap-3">
                    <item.icon className={`w-7 h-7 ${item.color}`} />
                    <span className="text-xl font-semibold">{item.title} Operation</span>
                    <HoverCard>
                      <HoverCardTrigger asChild>
                        <IconInfoCircle className="w-5 h-5 text-muted-foreground cursor-help" />
                      </HoverCardTrigger>
                      <HoverCardContent className="w-80">
                        <div className="space-y-2">
                          <h4 className="font-medium leading-none">About {item.title}</h4>
                          <p className="text-sm text-muted-foreground">
                            {item.detailedDescription}
                          </p>
                        </div>
                      </HoverCardContent>
                    </HoverCard>
                  </div>
                  <div className="rounded-md bg-blue-50 p-3 text-sm text-blue-700 dark:bg-blue-900/20 dark:text-blue-400 w-full md:w-auto text-left">
                    {item.description}
                  </div>
                </div>
                <div className="mb-6">
                  <p className="text-muted-foreground text-sm">
                    Configure the parameters for the <span className="font-medium">{item.title.toLowerCase()}</span> operation below.
                  </p>
                </div>
                <div className="rounded-lg p-6 shadow-inner">
                  <IntegrationIndexingManagerForm integrationId={id} mode={item.mode} sources={sources} />
                </div>
              </TabsContent>
            ))}
          </div>
        </Tabs>
      </div>
    </LoadProvider>
  );
}
