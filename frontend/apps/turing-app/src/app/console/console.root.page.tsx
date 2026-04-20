import { AppFooter } from "@/components/app-footer";
import { AppSidebar } from "@/components/app-sidebar";
import { ROUTES } from "@/app/routes.const";
import {
  SidebarInset,
  SidebarProvider,
} from "@/components/ui/sidebar";
import { UserProvider } from "@/contexts/user.context";
import React from "react";
import { Outlet, useLocation } from "react-router-dom";

const HIDE_FOOTER_ROUTES = [ROUTES.CHAT_ROOT];

export default function ConsoleRootPage() {
  const { pathname } = useLocation();
  const showFooter = !HIDE_FOOTER_ROUTES.some((r) => pathname.startsWith(r));

  return (
    <UserProvider>
    <SidebarProvider defaultOpen={false}
      style={
        {
          "--sidebar-width": "calc(var(--spacing) * 72)",
          "--header-height": "calc(var(--spacing) * 12)",
        } as React.CSSProperties
      }
      className="min-h-svh"
    >
      <AppSidebar variant="inset" />
      <SidebarInset>
        <Outlet />
        {showFooter && <AppFooter />}
      </SidebarInset>
    </SidebarProvider>
    </UserProvider>
  )
}
