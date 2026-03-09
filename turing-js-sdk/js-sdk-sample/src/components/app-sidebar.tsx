import {
  IconCode,
  IconCpu2,
  IconDatabase,
  IconHelp,
  IconInnerShadowTop,
  IconPlugConnectedX,
  IconReceiptRupee,
  IconSearch,
  IconSettings,
  IconZoomCode
} from "@tabler/icons-react"
import * as React from "react"

import { ROUTES } from "@/app/routes.const"
import { NavMain } from "@/components/nav-main"
import { NavSecondary } from "@/components/nav-secondary"
import { NavUser } from "@/components/nav-user"
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
} from "@/components/ui/sidebar"
import { NavLink } from "react-router-dom"

const data = {
  navMain: [
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
      title: "Logging",
      url: "/admin/logging/instance",
      icon: IconReceiptRupee,
    },
    {
      title: "API Token",
      url: "/admin/token/instance",
      icon: IconCode,
    },
  ],
  navSecondary: [
    {
      title: "Settings",
      url: "#",
      icon: IconSettings,
    },
    {
      title: "Get Help",
      url: "#",
      icon: IconHelp,
    },
    {
      title: "Search",
      url: "#",
      icon: IconSearch,
    },
  ],
}

export function AppSidebar({ ...props }: React.ComponentProps<typeof Sidebar>) {

  return (
    <Sidebar  {...props}>
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton
              asChild
              className="data-[slot=sidebar-menu-button]:p-1.5!">
              <NavLink to={ROUTES.CONSOLE}>
                <IconInnerShadowTop className="size-5!" />
                <span className="text-base font-semibold">Turing ES</span>
              </NavLink>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>
      <SidebarContent>
        <NavMain items={data.navMain} />
        <NavSecondary items={data.navSecondary} className="mt-auto" />
      </SidebarContent>
      <SidebarFooter>
        <NavUser user={data.user} />
      </SidebarFooter>
    </Sidebar>
  )
}
