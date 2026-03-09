import React, { type Dispatch, type SetStateAction } from "react";
import { Outlet } from "react-router-dom";
import { InternalSidebar } from "./internal.sidebar";
import { Card } from "./ui/card";
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
  open: boolean;
  setOpen: Dispatch<SetStateAction<boolean>>
  onDelete: () => void;
  onExport?: () => void;
}

export const SubPage: React.FC<Props> = (props) => {
  return (
    <div className="flex w-full items-center justify-center px-8 py-4">
      <Card className="w-full bg-sidebar py-1 overflow-hidden">
        <SidebarProvider
          defaultOpen={true}
          style={{
            "--sidebar-width": "calc(var(--spacing) * 72)",
            "--header-height": "calc(var(--spacing) * 12)",
          } as React.CSSProperties}
        >
          <InternalSidebar {...props} />
          <SidebarInset className="mr-1 rounded-xl border ">
            <main className="flex flex-1 flex-col overflow-hidden pt-4">
              <Outlet />
            </main>
          </SidebarInset>
        </SidebarProvider>
      </Card>
    </div>
  );
};
