import { IconDownload } from "@tabler/icons-react";
import React, { type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { NavLink, useLocation } from "react-router-dom";
import { DialogDelete } from "./dialog.delete";
import { GradientButton } from "./ui/gradient-button";
import {
  Sidebar,
  SidebarContent,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuBadge,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from "./ui/sidebar";

interface NavMainItem {
  title: string;
  /** Navigable URL fragment. When omitted *and* `children` is set, the item
   *  is rendered as a `SidebarGroupLabel` heading the children — useful for
   *  grouping related sub-pages without introducing a clickable parent. */
  url?: string;
  icon?: React.ElementType;
  children?: NavMainItem[];
}

interface InternalSidebarProps {
  icon: React.ElementType;
  feature: string;
  name: string;
  urlBase?: string;
  isNew?: boolean;
  data?: {
    counts?: any[];
    navMain: NavMainItem[];
  };
  onDelete?: () => void;
  onExport?: () => void;
  open?: boolean;
  setOpen?: React.Dispatch<React.SetStateAction<boolean>>;
}

const formatCount = (value?: number) => (value ?? 0).toLocaleString();

// Items without a url + with children are group labels rather than clickable
// nav targets; everything else is a regular link.
const isGroupLabel = (item: NavMainItem): boolean =>
  (!item.url || item.url === "") && Array.isArray(item.children) && item.children.length > 0;

// Parent items with `children` should also light up when any descendant
// matches the current path, so the user always knows which section they
// are in even when the active page is a nested child.
const isItemActive = (
  item: NavMainItem,
  urlBase: string | undefined,
  pathname: string,
): boolean => {
  if (item.url && pathname.startsWith((urlBase ?? "") + item.url)) {
    return true;
  }
  return item.children?.some((c) => isItemActive(c, urlBase, pathname)) ?? false;
};

const renderNavItems = (
  items: NavMainItem[],
  urlBase: string | undefined,
  pathname: string,
  isCollapsed: boolean,
  onNavigate?: () => void
): ReactNode =>
  items.map((item) => (
    <SidebarMenuItem key={item.title}>
      <SidebarMenuButton
        tooltip={item.title}
        isActive={isItemActive(item, urlBase, pathname)}
        asChild
      >
        <NavLink to={(urlBase ?? "") + (item.url ?? "")} onClick={onNavigate}>
          {item.icon && <item.icon className="size-5!" />}
          <span>{item.title}</span>
        </NavLink>
      </SidebarMenuButton>
      {!isCollapsed && item.children && item.children.length > 0 && (
        <SidebarMenu className="ml-4">
          {renderNavItems(item.children, urlBase, pathname, isCollapsed, onNavigate)}
        </SidebarMenu>
      )}
    </SidebarMenuItem>
  ));

export const InternalSidebar: React.FC<InternalSidebarProps> = ({
  icon: Icon,
  feature,
  name,
  urlBase,
  isNew,
  data,
  onDelete,
  onExport,
  open,
  setOpen,
}) => {
  const { t } = useTranslation();
  const location = useLocation();
  const pathname = location.pathname;
  const { state, isMobile, setOpenMobile } = useSidebar();
  const isCollapsed = state === "collapsed";
  const onNavigate = isMobile ? () => setOpenMobile(false) : undefined;

  return (
    <Sidebar collapsible="icon" variant="inset" position="absolute">
      <SidebarHeader className="pb-3">
        <SidebarMenu>
          <SidebarMenuItem className="flex items-center">
            <SidebarMenuButton asChild className="data-[slot=sidebar-menu-button]:p-1.5!">
              <NavLink to={urlBase ?? "#"} className="flex items-center gap-2.5">
                <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-linear-to-br from-blue-500/10 to-indigo-500/10 dark:from-blue-500/20 dark:to-indigo-500/20 ring-1 ring-blue-500/10 dark:ring-blue-400/10">
                  <Icon className="size-5! text-blue-600 dark:text-blue-400" />
                </div>
                {!isCollapsed && (
                  <span className="text-sm font-semibold whitespace-nowrap truncate">
                    {isNew ? t("sidebar.new", { feature }) : name}
                  </span>
                )}
              </NavLink>
            </SidebarMenuButton>
            {!isCollapsed && (
              <div className="flex items-center gap-1 mr-auto">
                {!isNew && onExport && (
                  <GradientButton
                    variant="ghost"
                    size="icon-sm"
                    onClick={onExport}
                  >
                    <IconDownload className="size-5!" />
                  </GradientButton>
                )}
                {!isNew && onDelete && setOpen && (
                  <DialogDelete
                    feature={feature}
                    name={name}
                    onDelete={onDelete}
                    open={open ?? false}
                    setOpen={setOpen}
                  />
                )}
              </div>
            )}
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>

      <SidebarContent>
        {data?.counts && data.counts.length > 0 && (
          <SidebarGroup>
            <SidebarGroupLabel>{t("sidebar.indexing")}</SidebarGroupLabel>
            <SidebarGroupContent className="flex flex-col gap-2 pt-4">
              <SidebarMenu>
                {data.counts.map((item: any) => (
                  <SidebarMenuItem key={item.title}>
                    <SidebarMenuButton
                      tooltip={item.title + ": " + formatCount(item.count)}
                      variant="outline"
                    >
                      {item.icon && <item.icon className="size-5!" />}
                      <span>{item.title}</span>
                    </SidebarMenuButton>
                    {!isCollapsed && (
                      <SidebarMenuBadge>
                        {formatCount(item.count)}
                      </SidebarMenuBadge>
                    )}
                  </SidebarMenuItem>
                ))}
              </SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>
        )}
        {(() => {
          const allItems = data?.navMain ?? [];
          const visibleItems = isNew
            ? allItems.filter((item) => item.url === "/detail")
            : allItems;
          // Items with a url stay in the main feature group; items without
          // one (group labels) become their own SidebarGroup below.
          const flatItems = visibleItems.filter((item) => !isGroupLabel(item));
          const groupItems = visibleItems.filter(isGroupLabel);
          return (
            <>
              <SidebarGroup>
                <SidebarGroupLabel>{feature}</SidebarGroupLabel>
                <SidebarGroupContent className="flex flex-col gap-2">
                  <SidebarMenu>
                    {renderNavItems(flatItems, urlBase, pathname, isCollapsed, onNavigate)}
                  </SidebarMenu>
                </SidebarGroupContent>
              </SidebarGroup>
              {groupItems.map((group) => (
                <SidebarGroup key={group.title}>
                  <SidebarGroupLabel>{group.title}</SidebarGroupLabel>
                  <SidebarGroupContent className="flex flex-col gap-2">
                    <SidebarMenu>
                      {renderNavItems(
                        group.children ?? [],
                        urlBase,
                        pathname,
                        isCollapsed,
                        onNavigate,
                      )}
                    </SidebarMenu>
                  </SidebarGroupContent>
                </SidebarGroup>
              ))}
            </>
          );
        })()}
      </SidebarContent>
    </Sidebar>
  );
};
