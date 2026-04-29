import React, { type Dispatch, type SetStateAction } from "react";
import { Outlet } from "react-router-dom";
import { InternalSidebar } from "./internal.sidebar";
import { SidebarInset, SidebarProvider } from "./ui/sidebar";

interface NavMainItem {
  title: string;
  /** Navigable URL fragment. When omitted *and* `children` is set, the item
   *  is rendered as a SidebarGroupLabel heading the children — useful for
   *  grouping related sub-pages without introducing a clickable parent. */
  url?: string;
  icon?: React.ElementType;
  children?: NavMainItem[];
}

interface NavCountItem {
  title: string;
  icon?: React.ElementType;
  count?: number;
}

interface DataType {
  navMain: NavMainItem[];
  counts?: NavCountItem[];
}

interface Props {
  icon: React.ElementType
  feature: string;
  name: string;
  urlBase?: string;
  isNew?: boolean;
  data?: DataType;
  open?: boolean;
  setOpen?: Dispatch<SetStateAction<boolean>>
  onDelete?: () => void;
  onExport?: () => void;
}

export const SubPage: React.FC<Props> = (props) => {
  return (
    <div className="w-full px-0 py-0 min-h-[calc(100svh-10.5rem)]">
      <div className="flex min-h-full w-full md:rounded-xl md:border md:bg-sidebar md:shadow-sm">
        <SidebarProvider
          defaultOpen={true}
          className="min-h-0!"
          style={{
            "--sidebar-width": "calc(var(--spacing) * 72)",
            "--header-height": "calc(var(--spacing) * 12)",
            minHeight: 0,
          } as React.CSSProperties}
        >
          <InternalSidebar {...props} />
          <SidebarInset className="md:mx-1.5 md:rounded-xl md:border bg-background md:shadow-sm">
            <main className="flex flex-1 flex-col pt-1 md:pt-2 max-md:[&_.px-6]:px-2">
              <Outlet />
            </main>
          </SidebarInset>
        </SidebarProvider>
      </div>
    </div>
  );
};
