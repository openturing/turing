import {
  IconBrandGraphql,
  IconCoin,
  IconCompass,
  IconCpu2,
  IconDatabase,
  IconFileImport,
  IconMessageChatbot,
  IconRocket,
  IconZoomCode,
} from "@tabler/icons-react";
import type { Icon } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { NavLink } from "react-router-dom";
import { ROUTES } from "@/app/routes.const";
import type { WidgetId } from "../use-dashboard";
import { WidgetWrapper } from "./widget-wrapper";

interface QuickLink {
  titleKey: string;
  url: string;
  icon: Icon;
  gradient: string;
}

const QUICK_LINKS: QuickLink[] = [
  { titleKey: "home.features.chat.title", url: ROUTES.CHAT_ROOT, icon: IconMessageChatbot, gradient: "from-blue-600 to-indigo-600" },
  { titleKey: "home.features.semanticNavigation.title", url: ROUTES.SN_ROOT, icon: IconCompass, gradient: "from-emerald-600 to-teal-600" },
  { titleKey: "home.features.languageModel.title", url: ROUTES.LLM_ROOT, icon: IconCpu2, gradient: "from-blue-600 to-indigo-600" },
  { titleKey: "home.features.costGovernance.title", url: ROUTES.COST_GOVERNANCE, icon: IconCoin, gradient: "from-amber-500 to-orange-500" },
  { titleKey: "home.features.searchEngine.title", url: ROUTES.SE_ROOT, icon: IconZoomCode, gradient: "from-emerald-600 to-teal-600" },
  { titleKey: "home.features.embeddingStore.title", url: ROUTES.STORE_ROOT, icon: IconDatabase, gradient: "from-purple-600 to-pink-600" },
  { titleKey: "home.features.import.title", url: ROUTES.EXCHANGE_IMPORT, icon: IconFileImport, gradient: "from-slate-600 to-slate-700" },
  { titleKey: "home.features.graphqlExplorer.title", url: ROUTES.GRAPHQL_ROOT, icon: IconBrandGraphql, gradient: "from-pink-600 to-rose-600" },
];

interface QuickLinksWidgetProps {
  onHide: (id: WidgetId) => void;
  colSpan?: 1 | 2 | 3;
}

export function QuickLinksWidget({ onHide, colSpan }: QuickLinksWidgetProps) {
  const { t } = useTranslation();

  return (
    <WidgetWrapper
      id="quickLinks"
      titleKey="dashboard.widgets.quickLinks.title"
      icon={IconRocket}
      gradient="from-amber-500 to-orange-500"
      colSpan={colSpan}
      onHide={onHide}
    >
      <div className="grid grid-cols-2 gap-2">
        {QUICK_LINKS.map((link) => {
          const LinkIcon = link.icon;
          return (
            <NavLink
              key={link.url}
              to={link.url}
              className="flex flex-col items-center gap-1.5 rounded-lg border border-border/50 p-3 hover:bg-secondary/40 transition-colors text-center group"
            >
              <div className={`inline-flex rounded-md bg-gradient-to-br ${link.gradient} p-1.5`}>
                <LinkIcon className="size-4 text-white" />
              </div>
              <span className="text-[10px] font-medium text-muted-foreground group-hover:text-foreground transition-colors leading-tight">
                {t(link.titleKey)}
              </span>
            </NavLink>
          );
        })}
      </div>
    </WidgetWrapper>
  );
}
