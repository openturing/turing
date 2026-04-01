export type TurSNSiteCustomSortOrder = "ASC" | "DESC";

export interface TurSNSiteCustomSortItem {
  id?: string;
  fieldName: string;
  sortOrder: TurSNSiteCustomSortOrder;
  position: number;
}

export interface TurSNSiteCustomSort {
  id?: string;
  name: string;
  description?: string;
  items: TurSNSiteCustomSortItem[];
}

export interface TurSNSiteCustomSortFieldOption {
  id: string;
  name: string;
  type?: string;
}
