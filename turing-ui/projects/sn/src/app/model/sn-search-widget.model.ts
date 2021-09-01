import { TurSNSearchFacet } from "./sn-search-facet.model";

export interface TurSNSearchWidget {
  facet: TurSNSearchFacet[];
  factToRemove: string;
  similar: string;
}
