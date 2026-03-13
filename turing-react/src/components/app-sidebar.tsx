import {
  IconBrandGraphql,
  IconChartBar,
  IconCode,
  IconCpu2,
  IconDatabase,
  IconFileImport,
  IconMessageChatbot,
  IconPlugConnectedX,
  IconReceiptRupee,
  IconSearch,
  IconSettings,
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
import { TurAuthorizationService } from "@/services/auth/authorization.service"
import { TurLLMInstanceService } from "@/services/llm/llm.service"
import { TurLogo } from "./logo/tur-logo"

const LLM_ONLY_ITEMS = new Set(["Chat", "Token Usage"])
const KEYCLOAK_HIDDEN_ITEMS = new Set(["Administration"])

const allNavItems = [
  {
    title: "Search Engine",
    url: "/admin/se/instance",
    icon: IconZoomCode,
  },
  {
    title: "Language Model",
    url: "/admin/llm/instance",
    icon: IconCpu2,
  },
  {
    title: "Embedding Store",
    url: "/admin/store/instance",
    icon: IconDatabase,
  },
  {
    title: "Chat",
    url: ROUTES.CHAT_ROOT,
    icon: IconMessageChatbot,
  },
  {
    title: "Semantic Navigation",
    url: "/admin/sn/instance",
    icon: IconSearch,
  },
  {
    title: "Integration",
    url: "/admin/integration/instance",
    icon: IconPlugConnectedX,
  },
  {
    title: "GraphQL Explorer",
    url: ROUTES.GRAPHQL_ROOT,
    icon: IconBrandGraphql,
  },
  {
    title: "Logging",
    url: "/admin/logging/instance",
    icon: IconReceiptRupee,
  },
  {
    title: "Import",
    url: "/admin/exchange/import",
    icon: IconFileImport,
  },
  {
    title: "Global Settings",
    url: ROUTES.GLOBAL_SETTINGS,
    icon: IconSettings,
  },
  {
    title: "Token Usage",
    url: ROUTES.TOKEN_USAGE,
    icon: IconChartBar,
  },
  {
    title: "API Token",
    url: "/admin/token/instance",
    icon: IconCode,
  },
  {
    title: "Administration",
    url: ROUTES.ADMIN_ROOT,
    icon: IconShieldCog,
  },
]

const turLLMInstanceService = new TurLLMInstanceService();
const turAuthorizationService = new TurAuthorizationService();

export function AppSidebar({ ...props }: React.ComponentProps<typeof Sidebar>) {
  const { user } = useCurrentUser();
  const [hasEnabledLlm, setHasEnabledLlm] = React.useState(false);
  const [isKeycloak, setIsKeycloak] = React.useState(true);
  const { toggleSidebar, state } = useSidebar();
  const isCollapsed = state === "collapsed";

  React.useEffect(() => {
    turLLMInstanceService.query().then((instances) => {
      setHasEnabledLlm(instances.some((i) => i.enabled === 1));
    });
    turAuthorizationService.discovery().then((d) => {
      setIsKeycloak(d.keycloak);
    });
  }, [])

  const navMain = React.useMemo(
    () => allNavItems.filter((item) => {
      if (!hasEnabledLlm && LLM_ONLY_ITEMS.has(item.title)) return false;
      if (isKeycloak && KEYCLOAK_HIDDEN_ITEMS.has(item.title)) return false;
      return true;
    }),
    [hasEnabledLlm, isKeycloak]
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
        <NavMain items={navMain} />
      </SidebarContent>
      <SidebarFooter>
        <NavUser user={user} />
      </SidebarFooter>
    </Sidebar>
  )
}
