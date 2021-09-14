import { TurSNSearchFacetItem } from "./sn-search-facet-item.model";
import { TurSNSearchLabel } from "./sn-search-label.model";

export interface TurSNSearchFacet {
  facets: TurSNSearchFacetItem[];
  label: TurSNSearchLabel;
}
