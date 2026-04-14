import type { ComponentType } from "react";

export type TurGridItemDropdownItem = {
  iconifyIcon?: string;
  label: string;
  url: string;
  external?: boolean;
};

export type TurGridItemAction = {
  icon: ComponentType<{ className?: string }>;
  label: string;
  url: string;
  external?: boolean;
  dropdownItems?: TurGridItemDropdownItem[];
};

export type TurGridItem = {
  id: string;
  name: string;
  description: string;
  url: string;
  external?: boolean;
  icon?: string | null;
  actions?: TurGridItemAction[];
};
