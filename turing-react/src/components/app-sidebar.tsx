import {
  IconBrandGraphql,
  IconBulb,
  IconChartBar,
  IconCompass,
  IconCpu2,
  IconCube,
  IconDatabase,
  IconFileImport,
  IconFolder,
  IconMessageChatbot,
  IconPlugConnectedX,
  IconReceiptRupee,
  IconRobot,
  IconServer2,
  IconShieldCog,
  IconZoomCode
} from "@tabler/icons-react"
import * as React from "react"

import { ROUTES } from "@/app/routes.const"
import { NavMain } from "@/components/nav-main"
import { NavUser } from "@/components/nav-user"
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

const LLM_ONLY_TITLES = new Set(["Chat", "Token Usage"])

/** Maps sidebar item titles to the privilege required to view them. */
const ITEM_PRIVILEGE: Record<string, string> = {
  "Language Model": "LLM_VIEW",
  "Embedding Model": "EMBEDDING_VIEW",
  "Embedding Store": "STORE_VIEW",
  "AI Agent": "AI_AGENT_VIEW",
  "Intent": "INTENT_VIEW",
  "Search Engine": "SE_VIEW",
  "Semantic Navigation": "SN_VIEW",
}

const generativeAiItems = [
  { title: "Language Model", url: "/admin/llm/instance", icon: IconCpu2 },
  { title: "Embedding Model", url: ROUTES.EMBEDDING_MODEL_INSTANCE, icon: IconCube },
  { title: "Embedding Store", url: "/admin/store/instance", icon: IconDatabase },
  { title: "MCP Server", url: "/admin/mcp/instance", icon: IconServer2 },
  { title: "AI Agent", url: "/admin/ai-agent/instance", icon: IconRobot },
  { title: "Intent", url: ROUTES.INTENT_INSTANCE, icon: IconBulb },
  { title: "Token Usage", url: ROUTES.TOKEN_USAGE, icon: IconChartBar },
  { title: "Chat", url: ROUTES.CHAT_ROOT, icon: IconMessageChatbot },
]

const searchItems = [
  { title: "Search Engine", url: "/admin/se/instance", icon: IconZoomCode },
  { title: "Semantic Navigation", url: "/admin/sn/instance", icon: IconCompass },
  { title: "Integration", url: "/admin/integration/instance", icon: IconPlugConnectedX },
  { title: "GraphQL Explorer", url: ROUTES.GRAPHQL_ROOT, icon: IconBrandGraphql },
]

const management = [
  { title: "Assets", url: ROUTES.ASSET_ROOT, icon: IconFolder },
  { title: "Import", url: "/admin/exchange/import", icon: IconFileImport },
  { title: "Logging", url: "/admin/logging/instance", icon: IconReceiptRupee },
  { title: "Administration", url: ROUTES.ADMIN_ROOT, icon: IconShieldCog },
]

const turLLMInstanceService = new TurLLMInstanceService();
const turFeaturesService = new TurFeaturesService();

export function AppSidebar({ ...props }: React.ComponentProps<typeof Sidebar>) {
  const { user } = useCurrentUser();
  const [hasEnabledLlm, setHasEnabledLlm] = React.useState(false);
  const [minioEnabled, setMinioEnabled] = React.useState(false);
  const { toggleSidebar, state } = useSidebar();
  const isCollapsed = state === "collapsed";

  React.useEffect(() => {
    turLLMInstanceService.query().then((instances) => {
      setHasEnabledLlm(instances.some((i) => i.enabled === 1));
    });
    turFeaturesService.getFeatures().then((features) => {
      setMinioEnabled(features.minioEnabled);
    }).catch(() => setMinioEnabled(false));
  }, [])

  const isAdmin = !!user?.admin
  const userPrivileges = React.useMemo(() => new Set(user?.privileges ?? []), [user?.privileges])

  const hasPrivilege = React.useCallback(
    (title: string) => {
      if (isAdmin) return true
      const required = ITEM_PRIVILEGE[title]
      return !required || userPrivileges.has(required)
    },
    [isAdmin, userPrivileges]
  )

  const filterItems = React.useCallback(
    (items: typeof generativeAiItems) =>
      items.filter((item) => {
        if (LLM_ONLY_TITLES.has(item.title) && !hasEnabledLlm) return false
        return hasPrivilege(item.title)
      }),
    [hasEnabledLlm, hasPrivilege]
  )

  const filteredManagement = React.useMemo(
    () => management.filter((item) => {
      if (item.title === "Assets" && !minioEnabled) return false
      if (item.title === "Administration" && !isAdmin) return false
      return true
    }),
    [minioEnabled, isAdmin]
  )

  const navGroups = React.useMemo(
    () => [
      { label: "Generative AI", items: filterItems(generativeAiItems) },
      { label: "Enterprise Search", items: filterItems(searchItems) },
      { label: "Management", items: filteredManagement },
    ].filter((g) => g.items.length > 0),
    [filterItems, filteredManagement]
  )
  return (
    <Sidebar collapsible="icon" {...props}>
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton
              onClick={toggleSidebar}
              className="data-[slot=sidebar-menu-button]:p-1.5!">

              <TurLogo className="size-6!" />

              {!isCollapsed && (
                <div className="grid flex-1 text-left leading-tight">
                  <span className="text-sm font-bold tracking-tight">Turing ES</span>
                  <span className="text-[10px] text-muted-foreground">Enterprise Search</span>
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
        <NavUser user={user} />
      </SidebarFooter>
    </Sidebar>
  )
}
