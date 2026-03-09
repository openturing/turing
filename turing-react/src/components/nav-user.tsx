import {
  IconDotsVertical,
  IconLogout,
  IconUserCircle
} from "@tabler/icons-react"

import { ROUTES } from "@/app/routes.const"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import {
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from "@/components/ui/sidebar"
import type { TurUser } from "@/models/auth/user"
import React from "react"
import { NavLink } from "react-router-dom"
import { GradientAvatar, GradientAvatarFallback, GradientAvatarImage } from "./ui/gradient-avatar"

export function NavUser({
  user
}: Readonly<{
  user: TurUser
}>) {
  const { isMobile } = useSidebar()
  const handleClick = () => {
    localStorage.removeItem('restInfo');
    localStorage.removeItem('user');
  };
  const initials = React.useMemo(() => {
    const first = user.firstName || '';
    const last = user.lastName || '';

    if (!first && !last) return ' ';

    const fullName = `${first} ${last}`.trim();
    const nameParts = fullName.split(' ').filter(Boolean);

    if (nameParts.length === 1) {
      return nameParts[0].charAt(0).toUpperCase();
    }

    return (
      nameParts[0].charAt(0).toUpperCase() +
      (nameParts.at(-1)?.charAt(0).toUpperCase() ?? '')
    );
  }, [user]);

  const avatarSrc = user?.avatarUrl || '';

  return (
    <SidebarMenu>
      <SidebarMenuItem>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <SidebarMenuButton
              size="lg"
              className="data-[state=open]:bg-sidebar-accent data-[state=open]:text-sidebar-accent-foreground"
            >
              <GradientAvatar className="h-8 w-8 rounded-lg">
                {avatarSrc && <GradientAvatarImage src={avatarSrc} alt={user.username} />}
                <GradientAvatarFallback className="rounded-lg">{initials}</GradientAvatarFallback>
              </GradientAvatar>
              <div className="grid flex-1 text-left text-sm leading-tight">
                <span className="truncate font-medium">{user.firstName} {user.lastName}</span>
                <span className="text-muted-foreground truncate text-xs">
                  {user.email}
                </span>
              </div>
              <IconDotsVertical className="ml-auto size-4" />
            </SidebarMenuButton>
          </DropdownMenuTrigger>
          <DropdownMenuContent
            className="w-(--radix-dropdown-menu-trigger-width) min-w-56 rounded-lg"
            side={isMobile ? "bottom" : "right"}
            align="end"
            sideOffset={4}
          >
            <DropdownMenuLabel className="p-0 font-normal">
              <div className="flex items-center gap-2 px-1 py-1.5 text-left text-sm">
                <GradientAvatar className="h-8 w-8 rounded-lg">
                  {avatarSrc && <GradientAvatarImage src={avatarSrc} alt={user.username} />}
                  <GradientAvatarFallback className="rounded-lg">{initials}</GradientAvatarFallback>
                </GradientAvatar>
                <div className="grid flex-1 text-left text-sm leading-tight">
                  <span className="truncate font-medium">{user.firstName} {user.lastName}</span>
                  <span className="text-muted-foreground truncate text-xs">
                    {user.email}
                  </span>
                </div>
              </div>
            </DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuGroup>
              <NavLink to={ROUTES.USER_ACCOUNT} className="w-full">
                <DropdownMenuItem>
                  <IconUserCircle />
                  Account
                </DropdownMenuItem>
              </NavLink>
            </DropdownMenuGroup>
            <DropdownMenuSeparator />
            <DropdownMenuItem>
              <NavLink to={ROUTES.LOGOUT}
                onClick={handleClick} className="flex items-center gap-1 w-full">
                <IconLogout />
                <span>Log out</span></NavLink>
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </SidebarMenuItem>
    </SidebarMenu>
  )
}
