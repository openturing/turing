export type TurSNSiteCustomSortOrder = "ASC" | "DESC";

/** T1104 — where a level puts documents with no value in its field. */
export type TurSNSiteCustomSortMissing = "FIRST" | "LAST";

export interface TurSNSiteCustomSortItem {
  id?: string;
  fieldName: string;
  sortOrder: TurSNSiteCustomSortOrder;
  position: number;
  /** Absent or null keeps the search engine's own placement. */
  missing?: TurSNSiteCustomSortMissing | null;
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
