import React from "react";
import { NavLink } from "react-router-dom";
import { DialogDelete } from "./dialog.delete";
import { Separator } from "./ui/separator";
import { SidebarTrigger } from "./ui/sidebar";

interface Props {
  icon: React.ElementType
  feature: string;
  name: string;
  description: string;
  urlBase?: string;
  onDelete?: () => void;
  open?: boolean;
  setOpen?: React.Dispatch<React.SetStateAction<boolean>>;
}
export const SubPageHeader: React.FC<Props> = ({ icon: Icon, feature, name, description, urlBase, onDelete, open, setOpen }) => {
  const headerContent = (
    <div className="flex items-center gap-3">
      {Icon && (
        <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-linear-to-br from-blue-500/10 to-indigo-500/10 dark:from-blue-500/20 dark:to-indigo-500/20 ring-1 ring-blue-500/10 dark:ring-blue-400/10">
          <Icon className="size-5! text-blue-600 dark:text-blue-400" />
        </div>
      )}
      <div className="min-w-0">
        <h1 className="text-base font-semibold leading-tight text-foreground">{feature}</h1>
        <p className="text-xs text-muted-foreground leading-relaxed mt-0.5">{description}</p>
      </div>
    </div>
  );

  return (
    <header className="mb-5">
      <div className="flex w-full items-center gap-3 px-4 lg:px-6">
        <div className="flex items-center gap-1">
          <SidebarTrigger className="-ml-1" />
          <Separator
            orientation="vertical"
            className="mx-2 data-[orientation=vertical]:h-4"
          />
        </div>
        <div className="min-w-0 flex-1">
          {urlBase ? (
            <NavLink to={urlBase} className="group inline-flex">
              {headerContent}
            </NavLink>
          ) : (
            headerContent
          )}
        </div>
        <div className="flex items-center gap-2">
          {open !== undefined && onDelete !== undefined && setOpen !== undefined && (
            <DialogDelete feature={feature} name={name} onDelete={onDelete} open={open} setOpen={setOpen} />
          )}
        </div>
      </div>
      <Separator className="mt-3" />
    </header>
  )
}
