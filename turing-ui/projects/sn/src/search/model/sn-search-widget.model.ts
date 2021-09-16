import { TurSNSearchFacet } from "./sn-search-facet.model";

export interface TurSNSearchWidget {
  facet: TurSNSearchFacet[];
  facetToRemove: TurSNSearchFacet;
  similar: string;
}
