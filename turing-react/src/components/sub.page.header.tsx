import React from "react";
import { NavLink } from "react-router-dom";
import { DialogDelete } from "./dialog.delete";
import { GradientButton } from "./ui/gradient-button";
import { Separator } from "./ui/separator";
import { SidebarTrigger } from "./ui/sidebar";

interface Props {
  icon: React.ElementType
  feature: string;
  name: string;
  description: string;
  urlNew?: string;
  urlBase?: string;
  onDelete?: () => void;
  open?: boolean;
  setOpen?: React.Dispatch<React.SetStateAction<boolean>>;
}
export const SubPageHeader: React.FC<Props> = ({ icon: Icon, feature, name, description, urlNew, urlBase, onDelete, open, setOpen }) => {
  return (
    <header className="mb-5">
      <div className="flex w-full items-start gap-3 px-4 lg:px-6">
        <div className="flex items-center gap-1 pt-1">
          <SidebarTrigger className="-ml-1" />
          <Separator
            orientation="vertical"
            className="mx-2 data-[orientation=vertical]:h-4"
          />
        </div>
        <div className="min-w-0 flex-1 pt-1">
          {urlBase ? (
            <NavLink
              to={urlBase}
              className="flex flex-wrap items-center gap-x-2 gap-y-1"
            >
              <span className="flex items-center gap-2">
                {Icon && <Icon className="size-6!" />}
                <h1 className="text-base font-semibold leading-none text-foreground">{feature}</h1>
              </span>
              <span className="translate-y-0.5 text-sm text-muted-foreground leading-relaxed">
                {description}
              </span>
            </NavLink>
          ) : (
            <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
              <span className="flex items-center gap-2">
                {Icon && <Icon className="size-6!" />}
                <h1 className="text-base font-semibold leading-none text-foreground">{feature}</h1>
                <span className="text-muted-foreground leading-none"> {description}
                </span>
              </span>
            </div>
          )}
        </div>
        <div className="flex items-center gap-2">
          {urlNew !== undefined && (
            <GradientButton asChild size={"sm"}>
              <NavLink to={urlNew} className="flex items-center gap-2">
                {Icon && <Icon className="size-6!" />} New {feature}
              </NavLink>
            </GradientButton>
          )}
          {open !== undefined && onDelete !== undefined && setOpen !== undefined && (
            <DialogDelete feature={feature} name={name} onDelete={onDelete} open={open} setOpen={setOpen} />
          )}
        </div>
      </div>
      <Separator className="mt-3" />
    </header>
  )
}
