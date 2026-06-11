import {
  IconBraces,
  IconBrandGraphql,
  IconBuildingStore,
  IconChartBar,
  IconClock,
  IconCompass,
  IconCpu2,
  IconCube,
  IconDatabase,
  IconFileImport,
  IconFolder,
  IconGitBranch,
  IconGlobe,
  IconHome,
  IconLayoutDashboard,
  IconMessageChatbot,
  IconMessageCircle2,
  IconPlayerPause,
  IconPlugConnectedX,
  IconReceiptRupee,
  IconRobot,
  IconServer2,
  IconShieldCog,
  IconSparkles,
  IconUserCircle,
  IconWebhook,
  IconZoomCode
} from "@tabler/icons-react"
import type { Icon } from "@tabler/icons-react"
import * as React from "react"
import { useTranslation } from "react-i18next"

import { ROUTES } from "@/app/routes.const"
import { NavMain } from "@/components/nav-main"
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from "@/components/ui/sidebar"
import { useCurrentUser } from "@/contexts/user.context"
import { TurFeaturesService } from "@/services/system/features.service"
import { TurLLMInstanceService } from "@/services/llm/llm.service"
import { TurLogo } from "./logo/tur-logo"
import { ModeToggleSidebar } from "./mode-toggle"

interface SidebarNavItem {
  key: string
  titleKey: string
  url: string
  icon: Icon
}

const LLM_ONLY_KEYS = new Set(["chat", "tokenUsage"])

/** Maps sidebar item keys to the privilege required to view them. */
const ITEM_PRIVILEGE: Record<string, string> = {
  languageModel: "LLM_VIEW",
  embeddingModel: "EMBEDDING_VIEW",
  embeddingStore: "STORE_VIEW",
  aiAgent: "AI_AGENT_VIEW",
  persona: "AI_AGENT_VIEW",
  parkedConversations: "AI_AGENT_VIEW",
  searchEngine: "SE_VIEW",
  semanticNavigation: "SN_VIEW",
}

const generativeAiItems: SidebarNavItem[] = [
  { key: "languageModel", titleKey: "home.features.languageModel.title", url: "/admin/llm/instance", icon: IconCpu2 },
  { key: "embeddingModel", titleKey: "home.features.embeddingModel.title", url: ROUTES.EMBEDDING_MODEL_INSTANCE, icon: IconCube },
  { key: "embeddingStore", titleKey: "home.features.embeddingStore.title", url: "/admin/store/instance", icon: IconDatabase },
  { key: "mcpServer", titleKey: "home.features.mcpServer.title", url: "/admin/mcp/instance", icon: IconServer2 },
  { key: "customTool", titleKey: "home.features.customTool.title", url: ROUTES.CUSTOM_TOOL_INSTANCE, icon: IconBraces },
  { key: "routine", titleKey: "home.features.routine.title", url: ROUTES.ROUTINE_INSTANCE, icon: IconClock },
  { key: "chatWebhook", titleKey: "home.features.chatWebhook.title", url: ROUTES.CHAT_WEBHOOK_INSTANCE, icon: IconWebhook },
  { key: "persona", titleKey: "home.features.persona.title", url: ROUTES.PERSONA_INSTANCE, icon: IconUserCircle },
  { key: "aiAgent", titleKey: "home.features.aiAgent.title", url: "/admin/ai-agent/instance", icon: IconRobot },
  { key: "tokenUsage", titleKey: "home.features.tokenUsage.title", url: ROUTES.TOKEN_USAGE, icon: IconChartBar },
  { key: "chatAnalytics", titleKey: "home.features.chatAnalytics.title", url: ROUTES.CHAT_ANALYTICS, icon: IconMessageCircle2 },
  { key: "parkedConversations", titleKey: "home.features.parkedConversations.title", url: ROUTES.PARKED_CONVERSATIONS, icon: IconPlayerPause },
  { key: "chat", titleKey: "home.features.chat.title", url: ROUTES.CHAT_ROOT, icon: IconMessageChatbot },
]

const searchItems: SidebarNavItem[] = [
  { key: "searchEngine", titleKey: "home.features.searchEngine.title", url: "/admin/se/instance", icon: IconZoomCode },
  { key: "semanticNavigation", titleKey: "home.features.semanticNavigation.title", url: "/admin/sn/instance", icon: IconCompass },
  { key: "integration", titleKey: "home.features.integration.title", url: "/admin/integration/instance", icon: IconPlugConnectedX },
  { key: "pages", titleKey: "home.features.pages.title", url: ROUTES.PAGE_ROOT, icon: IconGlobe },
  { key: "graphqlExplorer", titleKey: "home.features.graphqlExplorer.title", url: ROUTES.GRAPHQL_ROOT, icon: IconBrandGraphql },
]

const managementItems: SidebarNavItem[] = [
  { key: "assets", titleKey: "home.features.assets.title", url: ROUTES.ASSET_ROOT, icon: IconFolder },
  { key: "skills", titleKey: "home.features.skills.title", url: ROUTES.SKILL_ROOT, icon: IconSparkles },
  { key: "git", titleKey: "home.features.git.title", url: ROUTES.GIT_ROOT, icon: IconGitBranch },
  { key: "marketplace", titleKey: "home.features.marketplace.title", url: ROUTES.MARKETPLACE_ROOT, icon: IconBuildingStore },
  { key: "import", titleKey: "home.features.import.title", url: "/admin/exchange/import", icon: IconFileImport },
  { key: "logging", titleKey: "home.features.logging.title", url: "/admin/logging/instance", icon: IconReceiptRupee },
  { key: "administration", titleKey: "home.features.administration.title", url: ROUTES.ADMIN_ROOT, icon: IconShieldCog },
]

const turLLMInstanceService = new TurLLMInstanceService();
const turFeaturesService = new TurFeaturesService();

export function AppSidebar({ ...props }: React.ComponentProps<typeof Sidebar>) {
  const { t } = useTranslation();
  const { user } = useCurrentUser();
  const [hasEnabledLlm, setHasEnabledLlm] = React.useState(false);
  const [storageEnabled, setStorageEnabled] = React.useState(false);
  const [gitServerEnabled, setGitServerEnabled] = React.useState(false);
  const [marketplaceEnabled, setMarketplaceEnabled] = React.useState(false);
  const [skillsEnabled, setSkillsEnabled] = React.useState(false);
  const { toggleSidebar, state, isMobile } = useSidebar();
  const isCollapsed = state === "collapsed";

  React.useEffect(() => {
    turLLMInstanceService.query().then((instances) => {
      setHasEnabledLlm(instances.some((i) => i.enabled === 1));
    });
    turFeaturesService.getFeatures().then((features) => {
      setStorageEnabled(features.storageEnabled);
      setGitServerEnabled(features.gitServerEnabled ?? false);
      setMarketplaceEnabled(features.marketplaceEnabled ?? false);
      setSkillsEnabled(features.skillsEnabled ?? false);
    }).catch(() => { setStorageEnabled(false); setGitServerEnabled(false); setMarketplaceEnabled(false); setSkillsEnabled(false); });
  }, [])

  const isAdmin = !!user?.admin
  const userPrivileges = React.useMemo(() => new Set(user?.privileges ?? []), [user?.privileges])

  const hasPrivilege = React.useCallback(
    (key: string) => {
      if (isAdmin) return true
      const required = ITEM_PRIVILEGE[key]
      return !required || userPrivileges.has(required)
    },
    [isAdmin, userPrivileges]
  )

  const filterItems = React.useCallback(
    (items: SidebarNavItem[]) =>
      items.filter((item) => {
        if (LLM_ONLY_KEYS.has(item.key) && !hasEnabledLlm) return false
        if (item.key === "pages" && !storageEnabled) return false
        return hasPrivilege(item.key)
      }),
    [hasEnabledLlm, storageEnabled, hasPrivilege]
  )

  const filteredManagement = React.useMemo(
    () => managementItems.filter((item) => {
      if (item.key === "assets" && !storageEnabled) return false
      if (item.key === "skills" && !skillsEnabled) return false
      if (item.key === "git" && !gitServerEnabled) return false
      if (item.key === "marketplace" && !marketplaceEnabled) return false
      if (item.key === "administration" && !isAdmin) return false
      return true
    }),
    [storageEnabled, skillsEnabled, gitServerEnabled, marketplaceEnabled, isAdmin]
  )

  const toNavItems = React.useCallback(
    (items: SidebarNavItem[]) => items.map(({ titleKey, url, icon }) => ({ title: t(titleKey), url, icon })),
    [t]
  )

  const navGroups = React.useMemo(
    () => [
      { items: [{ title: t("home.title"), url: ROUTES.HOME, icon: IconHome }, { title: t("dashboard.title"), url: ROUTES.DASHBOARD, icon: IconLayoutDashboard }] },
      { label: t("home.sections.generativeAi.label"), items: toNavItems(filterItems(generativeAiItems)) },
      { label: t("home.sections.enterpriseSearch.label"), items: toNavItems(filterItems(searchItems)) },
      { label: t("home.sections.management.label"), items: toNavItems(filteredManagement) },
    ].filter((g) => g.items.length > 0),
    [t, filterItems, filteredManagement, toNavItems]
  )
  return (
    <Sidebar collapsible="icon" side={isMobile ? "right" : "left"} {...props}>
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton
              onClick={toggleSidebar}
              className="data-[slot=sidebar-menu-button]:p-1.5!">

              <TurLogo className="size-6!" />

              {!isCollapsed && (
                <div className="grid flex-1 text-left leading-tight">
                  <span className="text-sm font-bold tracking-tight">{t("sidebar.brandName")}</span>
                  <span className="text-[10px] text-muted-foreground">{t("sidebar.brandTagline")}</span>
                </div>
              )}
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>
      <SidebarContent>
        <NavMain groups={navGroups} />
      </SidebarContent>
      <SidebarFooter>
        <ModeToggleSidebar />
      </SidebarFooter>
    </Sidebar>
  )
}
