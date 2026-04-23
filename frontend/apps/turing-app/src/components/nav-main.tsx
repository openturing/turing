import { type Icon } from "@tabler/icons-react"

import {
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from "@/components/ui/sidebar"
import { NavLink, useLocation } from "react-router-dom"

interface NavItem {
  readonly title: string
  readonly url: string
  readonly icon?: Icon
}

interface NavGroup {
  readonly label?: string
  readonly items: readonly NavItem[]
}

export function NavMain({
  groups,
}: {
  readonly groups: readonly NavGroup[]
}) {
  const location = useLocation();
  const pathname = location.pathname;
  const { isMobile, setOpenMobile } = useSidebar();
  return (
    <>
      {groups.map((group) => (
        <SidebarGroup key={group.label ?? "_ungrouped"}>
          {group.label && <SidebarGroupLabel>{group.label}</SidebarGroupLabel>}
          <SidebarGroupContent className="flex flex-col gap-2">
            <SidebarMenu>
              {group.items.map((item) => {
                const isActive = pathname === item.url || pathname.startsWith(item.url + "/");
                return (
                  <SidebarMenuItem key={item.title}>
                    <SidebarMenuButton tooltip={item.title} isActive={isActive} asChild>
                      <NavLink to={item.url} onClick={() => isMobile && setOpenMobile(false)}>
                        {item.icon && <item.icon className="size-6!" />}
                        <span>{item.title}</span>
                      </NavLink>
                    </SidebarMenuButton>
                  </SidebarMenuItem>
                );
              })}
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
      ))}
    </>
  )
}
