import { Separator } from "@/components/ui/separator";
import { SidebarTrigger } from "@/components/ui/sidebar";

import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import React from "react";
import { NavLink } from "react-router-dom";
import { ModeToggle } from "./mode-toggle";
import { Breadcrumb, BreadcrumbItem, BreadcrumbLink, BreadcrumbList, BreadcrumbPage, BreadcrumbSeparator } from "./ui/breadcrumb";
import { GradientButton } from "./ui/gradient-button";
interface MyComponentProps {
  turIcon?: React.ElementType;
  title: string;
  urlBase?: string;
  urlNew?: string;
}


export const PageHeader: React.FC<MyComponentProps> = ({ turIcon: TurIcon, title, urlBase, urlNew }) => {
  const { items } = useBreadcrumb();
  return (
    <header className="flex h-(--header-height) shrink-0 items-center gap-2 border-b transition-[width,height] ease-linear group-has-data-[collapsible=icon]/sidebar-wrapper:h-(--header-height)">
      <div className="flex w-full items-center gap-1 px-4 lg:gap-2 lg:px-6">
        <SidebarTrigger className="-ml-1" />
        <Separator
          orientation="vertical"
          className="mx-2 data-[orientation=vertical]:h-4"
        />
        {urlBase ? (
          <Breadcrumb>
            <BreadcrumbList>
              {items.map((item, index) => (
                <React.Fragment key={item.label}>
                  {index > 0 && <BreadcrumbSeparator />}
                  <BreadcrumbItem>
                    {item.href ? (
                      <BreadcrumbLink asChild>
                        <NavLink to={item.href}>{item.label}</NavLink>
                      </BreadcrumbLink>
                    ) : (
                      <BreadcrumbPage>{item.label}</BreadcrumbPage>
                    )}
                  </BreadcrumbItem>
                </React.Fragment>
              ))}
            </BreadcrumbList>
          </Breadcrumb>
        ) : (
          <>
            {TurIcon && <TurIcon />}
            <h1 className="text-base font-medium"> {title}</h1>
          </>
        )}
        <div className="ml-auto flex items-center gap-2">
          {urlNew !== undefined && (
            <GradientButton asChild size="sm">
              <NavLink to={urlNew} className="flex items-center gap-2">{TurIcon && <TurIcon />} New {title}</NavLink>
            </GradientButton>
          )}
          <ModeToggle></ModeToggle>
        </div>
      </div>
    </header >
  )
}