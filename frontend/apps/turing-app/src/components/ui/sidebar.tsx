/* eslint-disable react-refresh/only-export-components --
 * T1024 — this module is a re-export shim, and what it re-exports is the design
 * system's decision. The package ships a component beside its variants (or a
 * provider beside its hook) under one entry point; naming them here in two files
 * would not change that, and Fast Refresh has no state to preserve in a module
 * that declares nothing. The split, if it is ever wanted, is the package's.
 */
export { Sidebar, SidebarContent, SidebarFooter, SidebarGroup, SidebarGroupAction, SidebarGroupContent, SidebarGroupLabel, SidebarHeader, SidebarInput, SidebarInset, SidebarMenu, SidebarMenuAction, SidebarMenuBadge, SidebarMenuButton, SidebarMenuItem, SidebarMenuSkeleton, SidebarMenuSub, SidebarMenuSubButton, SidebarMenuSubItem, SidebarProvider, SidebarRail, SidebarSeparator, SidebarTrigger, useSidebar } from "@viglet/viglet-design-system";
