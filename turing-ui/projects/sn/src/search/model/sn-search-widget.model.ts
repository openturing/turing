import { TurSNSearchFacet } from "./sn-search-facet.model";
import { TurSNSearchSpellCheck } from "./sn-search-spell-check.model";

export interface TurSNSearchWidget {
  facet: TurSNSearchFacet[];
  facetToRemove: TurSNSearchFacet;
  similar: string;
  spellCheck: TurSNSearchSpellCheck;
}
