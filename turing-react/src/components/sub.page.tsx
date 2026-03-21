import React, { type Dispatch, type SetStateAction } from "react";
import { Outlet } from "react-router-dom";
import { InternalSidebar } from "./internal.sidebar";
import { SidebarInset, SidebarProvider } from "./ui/sidebar";

interface NavMainItem {
  title: string;
  url: string;
  icon?: React.ElementType;
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
    <div className="w-full px-1 md:px-6 lg:px-8 py-1 md:py-4 min-h-[calc(100svh-10.5rem)]">
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
          <SidebarInset className="md:mr-1.5 md:my-1.5 md:rounded-xl md:border bg-background md:shadow-sm">
            <main className="flex flex-1 flex-col pt-2 md:pt-4 max-md:[&_.px-6]:px-2">
              <Outlet />
            </main>
          </SidebarInset>
        </SidebarProvider>
      </div>
    </div>
  );
};
