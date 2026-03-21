import { IconDotsVertical, IconPlus } from "@tabler/icons-react";
import React from "react";
import { NavLink, useNavigate } from "react-router-dom";
import { DialogDelete } from "./dialog.delete";
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from "./ui/dropdown-menu";
import { GradientButton } from "./ui/gradient-button";
import { Separator } from "./ui/separator";
import { SidebarTrigger, useSidebar } from "./ui/sidebar";

/* ── Composite sub-components (markers) ── */

interface ActionProps {
  label: string;
  icon?: React.ElementType;
  href?: string;
  onClick?: () => void;
  disabled?: boolean;
}

const SubPageHeaderAction: React.FC<ActionProps> = () => null;
SubPageHeaderAction.displayName = "SubPageHeaderAction";

/* ── Main component ── */

interface Props {
  icon: React.ElementType
  feature: string;
  name: string;
  description: string;
  urlBase?: string;
  onDelete?: () => void;
  open?: boolean;
  setOpen?: React.Dispatch<React.SetStateAction<boolean>>;
  children?: React.ReactNode;
}

const SubPageHeaderComponent: React.FC<Props> = ({ icon: Icon, feature, name, description, urlBase, onDelete, open, setOpen, children }) => {
  const { isMobile, toggleSidebar } = useSidebar();
  const navigate = useNavigate();

  // Extract actions from marker children
  const actions: ActionProps[] = [];
  React.Children.forEach(children, (child) => {
    if (React.isValidElement(child) && (child.type as any)?.displayName === "SubPageHeaderAction") {
      actions.push(child.props as ActionProps);
    }
  });

  const iconElement = Icon && (
    <div
      className={`flex h-9 w-9 items-center justify-center rounded-lg bg-linear-to-br from-blue-500/10 to-indigo-500/10 dark:from-blue-500/20 dark:to-indigo-500/20 ring-1 ring-blue-500/10 dark:ring-blue-400/10 ${isMobile ? "cursor-pointer active:scale-95 transition-transform" : ""}`}
      onClick={isMobile ? toggleSidebar : undefined}
      onKeyDown={isMobile ? (e) => { if (e.key === "Enter" || e.key === " ") toggleSidebar() } : undefined}
      tabIndex={isMobile ? 0 : undefined}
      title={isMobile ? "Open navigation" : undefined}
    >
      <Icon className="size-5! text-blue-600 dark:text-blue-400" />
    </div>
  );

  const headerContent = (
    <div className="flex items-center gap-3">
      {iconElement}
      <div className="min-w-0">
        <h1 className="text-base font-semibold leading-tight text-foreground">{feature}</h1>
        <p className="hidden md:block text-xs text-muted-foreground leading-relaxed mt-0.5">{description}</p>
      </div>
    </div>
  );

  return (
    <header className="mb-5">
      <div className="flex w-full items-center gap-3 px-4 lg:px-6">
        <div className="hidden md:flex items-center gap-1">
          <SidebarTrigger className="-ml-1" />
          <Separator
            orientation="vertical"
            className="mx-2 data-[orientation=vertical]:h-4"
          />
        </div>
        <div className="min-w-0 flex-1">
          {urlBase && !isMobile ? (
            <NavLink to={urlBase} className="group inline-flex">
              {headerContent}
            </NavLink>
          ) : (
            headerContent
          )}
        </div>
        <div className="flex items-center gap-2">
          {actions.length > 0 && (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <GradientButton variant="outline" size="sm" className="gap-1.5">
                  <IconDotsVertical className="size-4" />
                  <span className="hidden sm:inline">Actions</span>
                </GradientButton>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                {actions.map((action) => {
                  const ActionIcon = action.icon ?? IconPlus;
                  return (
                    <DropdownMenuItem
                      key={action.label}
                      disabled={action.disabled}
                      onClick={() => {
                        if (action.href) navigate(action.href);
                        if (action.onClick) action.onClick();
                      }}
                    >
                      <ActionIcon className="size-4 mr-2" />
                      {action.label}
                    </DropdownMenuItem>
                  );
                })}
              </DropdownMenuContent>
            </DropdownMenu>
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

export const SubPageHeader = Object.assign(SubPageHeaderComponent, {
  Action: SubPageHeaderAction,
});
