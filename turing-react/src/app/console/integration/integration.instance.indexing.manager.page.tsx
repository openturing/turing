import { ROUTES } from "@/app/routes.const";
import { IntegrationIndexingManagerForm } from "@/components/integration/integration.indexing.manager.form";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { Card } from "@/components/ui/card";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import type { TurIntegrationAemSource } from "@/models/integration/integration-aem-source.model";
import { TurIntegrationAemSourceService } from "@/services/integration/integration-aem-source.service";
import {
  IconAdjustmentsSearch,
  IconCloudDownload,
  IconCloudUpload,
  IconPlus,
  IconTrash,
} from "@tabler/icons-react";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useLocation, useNavigate, useParams } from "react-router-dom";

type IndexingTabMode = "INDEXING" | "DEINDEXING" | "PUBLISHING" | "UNPUBLISHING";

type IndexingTabItem = {
  value: IndexingTabMode;
  title: string;
  icon: React.ComponentType<{ className?: string }>;
  color: string;
  bgLight: string;
  bgActive: string;
  borderActive: string;
  description: string;
  hint: string;
};

function buildItems(t: (key: string) => string): IndexingTabItem[] {
  return [
    {
      value: "INDEXING",
      title: t("integration.indexingManager.indexing"),
      icon: IconPlus,
      color: "text-emerald-600 dark:text-emerald-400",
      bgLight: "bg-emerald-50 dark:bg-emerald-950/30",
      bgActive: "bg-emerald-500",
      borderActive: "ring-emerald-500/30",
      description: t("integration.indexingManager.indexingDesc"),
      hint: t("integration.indexingManager.indexingHint"),
    },
    {
      value: "DEINDEXING",
      title: t("integration.indexingManager.deindexing"),
      icon: IconTrash,
      color: "text-red-600 dark:text-red-400",
      bgLight: "bg-red-50 dark:bg-red-950/30",
      bgActive: "bg-red-500",
      borderActive: "ring-red-500/30",
      description: t("integration.indexingManager.deindexingDesc"),
      hint: t("integration.indexingManager.deindexingHint"),
    },
    {
      value: "PUBLISHING",
      title: t("integration.indexingManager.publishing"),
      icon: IconCloudUpload,
      color: "text-blue-600 dark:text-blue-400",
      bgLight: "bg-blue-50 dark:bg-blue-950/30",
      bgActive: "bg-blue-500",
      borderActive: "ring-blue-500/30",
      description: t("integration.indexingManager.publishingDesc"),
      hint: t("integration.indexingManager.publishingHint"),
    },
    {
      value: "UNPUBLISHING",
      title: t("integration.indexingManager.unpublishing"),
      icon: IconCloudDownload,
      color: "text-amber-600 dark:text-amber-400",
      bgLight: "bg-amber-50 dark:bg-amber-950/30",
      bgActive: "bg-amber-500",
      borderActive: "ring-amber-500/30",
      description: t("integration.indexingManager.unpublishingDesc"),
      hint: t("integration.indexingManager.unpublishingHint"),
    },
  ];
}

export default function IntegrationInstanceIndexAdminPage() {
  const { t } = useTranslation();
  const { id } = useParams() as { id: string };
  const [error, setError] = useState<string | null>(null);
  const turIntegrationAemSourceService = useMemo(() => new TurIntegrationAemSourceService(id), [id]);
  const [sources, setSources] = useState<TurIntegrationAemSource[]>();
  const { pushItem, popItem } = useBreadcrumb();
  const navigate = useNavigate();
  const location = useLocation();
  const items = useMemo(() => buildItems(t), [t]);

  const tabPathMap: Record<IndexingTabMode, string> = {
    INDEXING: "indexing",
    DEINDEXING: "deindexing",
    PUBLISHING: "publishing",
    UNPUBLISHING: "unpublishing",
  };

  const pathTabMap: Record<string, IndexingTabMode> = Object.entries(tabPathMap).reduce(
    (acc, [tab, path]) => {
      acc[path] = tab as IndexingTabMode;
      return acc;
    },
    {} as Record<string, IndexingTabMode>,
  );

  const pathSegment = location.pathname.split("/").pop();
  const initialTab: IndexingTabMode = pathTabMap[pathSegment ?? ""] || "INDEXING";
  const [selectedTab, setSelectedTab] = useState<IndexingTabMode>(initialTab);

  useEffect(() => {
    const path = location.pathname.split("/").pop();
    const tab = pathTabMap[path ?? ""];
    if (tab && tab !== selectedTab) setSelectedTab(tab);
    else if (!tab && selectedTab !== "INDEXING") setSelectedTab("INDEXING");
  }, [location.pathname, selectedTab]);

  const handleTabChange = (mode: IndexingTabMode) => {
    setSelectedTab(mode);
    navigate(
      `/admin/integration/instance/${id}/indexing-manager/${tabPathMap[mode]}`,
    );
  };

  useEffect(() => {
    let added = false;
    turIntegrationAemSourceService
      .query()
      .then((sources) => {
        setSources(sources);
        pushItem({ label: t("integration.indexingManager.title") });
        added = true;
      })
      .catch(() => setError("Failed to load integration details"));
    return () => {
      if (added) popItem();
    };
  }, [turIntegrationAemSourceService, pushItem, popItem]);

  const activeItem = items.find((i) => i.value === selectedTab) ?? items[0];

  return (
    <LoadProvider
      checkIsNotUndefined={sources}
      error={error}
      tryAgainUrl={`${ROUTES.INTEGRATION_INSTANCE}/${id}`}
    >
      <SubPageHeader
        icon={IconAdjustmentsSearch}
        feature={t("integration.indexingManager.title")}
        name={t("integration.indexingManager.title")}
        description={t("integration.indexingManager.description")}
      />
      <div className="w-full mx-auto mt-4 px-6 pb-6 space-y-6">
        {/* Operation Selector Cards */}
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
          {items.map((item) => {
            const isActive = selectedTab === item.value;
            return (
              <button
                key={item.value}
                type="button"
                onClick={() => handleTabChange(item.value)}
                className={[
                  "relative text-left rounded-xl p-4 transition-all duration-200 cursor-pointer border",
                  isActive
                    ? `ring-2 ${item.borderActive} border-transparent shadow-md`
                    : "border-border/60 hover:border-border hover:shadow-sm",
                ].join(" ")}
              >
                {/* Active indicator bar */}
                <div
                  className={[
                    "absolute top-0 left-4 right-4 h-0.5 rounded-b transition-all duration-200",
                    isActive ? item.bgActive : "bg-transparent",
                  ].join(" ")}
                />
                <div className="flex items-center gap-3 mb-2">
                  <div className={`rounded-lg p-2 ${item.bgLight}`}>
                    <item.icon className={`w-5 h-5 ${item.color}`} />
                  </div>
                  <span className={`font-semibold text-sm ${isActive ? "text-foreground" : "text-muted-foreground"}`}>
                    {item.title}
                  </span>
                </div>
                <p className="text-xs text-muted-foreground leading-relaxed line-clamp-2">
                  {item.description}
                </p>
              </button>
            );
          })}
        </div>

        {/* Active Operation Content */}
        <Card className="overflow-hidden p-0">
          {/* Header with colored accent */}
          <div className={`${activeItem.bgLight} px-6 py-4 border-b`}>
            <div className="flex items-center gap-3">
              <div className={`rounded-lg p-2 bg-white dark:bg-background shadow-sm`}>
                <activeItem.icon className={`w-5 h-5 ${activeItem.color}`} />
              </div>
              <div className="min-w-0">
                <h2 className="font-semibold text-base">{activeItem.title}</h2>
                <p className="text-xs text-muted-foreground mt-0.5">{activeItem.hint}</p>
              </div>
            </div>
          </div>

          {/* Form */}
          <div className="p-6">
            <IntegrationIndexingManagerForm
              key={activeItem.value}
              integrationId={id}
              mode={activeItem.value}
              sources={sources}
            />
          </div>
        </Card>
      </div>
    </LoadProvider>
  );
}
