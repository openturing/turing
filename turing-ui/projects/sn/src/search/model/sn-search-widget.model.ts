import { TurSNSearchFacet } from "./sn-search-facet.model";
import { TurSNSearchLocale } from "./sn-search-locale.model";
import { TurSNSearchSpellCheck } from "./sn-search-spell-check.model";

export interface TurSNSearchWidget {
  facet: TurSNSearchFacet[];
  facetToRemove: TurSNSearchFacet;
  similar: string;
  spellCheck: TurSNSearchSpellCheck;
  locales: TurSNSearchLocale[];
}
