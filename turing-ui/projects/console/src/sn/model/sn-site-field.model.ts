import {TurSNSiteFieldFacet} from "./sn-site-field-facet.model";
import {TurSNSiteFacetRangeEnum} from "./sn-site-facet-range.enum";
import {TurSNSiteFacetFieldEnum} from "./sn-site-facet.field.enum";
import {TurSNSiteFacetFieldSortEnum} from "./sn-site-facet--field-sort.enum";

export interface TurSNSiteField {
  id: string;
  name: string;
  description: string;
  defaultValue: string;
  enabled: number;
  externalId: string;
  facet: number;
  facetName: string;
  facetRange: TurSNSiteFacetRangeEnum;
  facetLocales: TurSNSiteFieldFacet[]
  facetType: TurSNSiteFacetFieldEnum;
  facetItemType: TurSNSiteFacetFieldEnum;
  facetSort: TurSNSiteFacetFieldSortEnum;
  facetPosition: number;
  secondaryFacet: boolean;
  showAllFacetItems: boolean;
  mlt: number;
  multiValued: number;
  required: number;
  snType: string;
  type: string;
  hl: number;
}
