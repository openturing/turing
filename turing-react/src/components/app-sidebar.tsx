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
import type { TurUser } from "@/models/auth/user"
import { TurUserService } from "@/services/auth/user.service"
import { TurLLMInstanceService } from "@/services/llm/llm.service"
import { TurLogo } from "./logo/tur-logo"

const LLM_ONLY_ITEMS = new Set(["Chat", "Token Usage"])

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
]

const turUserService = new TurUserService();
const turLLMInstanceService = new TurLLMInstanceService();

export function AppSidebar({ ...props }: React.ComponentProps<typeof Sidebar>) {
  const [user, setUser] = React.useState<TurUser>({} as TurUser);
  const [hasEnabledLlm, setHasEnabledLlm] = React.useState(false);
  const { toggleSidebar } = useSidebar();
  React.useEffect(() => {
    turUserService.get().then(setUser);
    turLLMInstanceService.query().then((instances) => {
      setHasEnabledLlm(instances.some((i) => i.enabled === 1));
    });
  }, [])

  const navMain = React.useMemo(
    () => hasEnabledLlm ? allNavItems : allNavItems.filter((item) => !LLM_ONLY_ITEMS.has(item.title)),
    [hasEnabledLlm]
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
              <span className="text-base font-semibold">Turing ES</span>
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
