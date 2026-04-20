import type { TurSNSiteFacetFieldSortTypes } from "./sn-site-facet-field-sort.type.ts";
import type { TurSNSiteFacetRangeTypes } from "./sn-site-facet-range.type.ts";
import type { TurSNSiteFacetFieldTypes } from "./sn-site-facet.field.type.ts";
import type { TurSNSiteFieldFacet } from "./sn-site-field-facet.model.ts";

export type TurSNSiteField = {
  id: string;
  name: string;
  description: string;
  defaultValue: string;
  enabled: number;
  externalId: string;
  facet: number;
  facetName: string;
  facetRange: TurSNSiteFacetRangeTypes;
  facetLocales: TurSNSiteFieldFacet[];
  facetType: TurSNSiteFacetFieldTypes;
  facetItemType: TurSNSiteFacetFieldTypes;
  facetSort: TurSNSiteFacetFieldSortTypes;
  facetPosition: number;
  secondaryFacet: boolean;
  showAllFacetItems: boolean;
  mlt: number;
  multiValued: number;
  required: number;
  snType: string;
  type: string;
  hl: number;
};
