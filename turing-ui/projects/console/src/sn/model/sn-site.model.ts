import { TurSEInstance } from "../../se/model/se-instance.model";
import { TurSNSiteLocale } from "./sn-site-locale.model";
import { TurSNSiteFacetEnum} from "./sn-site-facet.enum";
import {TurSNSiteFacetSortEnum} from "./sn-site-facet-sort.enum";
import {TurSNSiteGenAi} from "./sn-site-genai.model";

export interface TurSNSite {
  id: string;
  name: string;
  description: string;
  exactMatchField: string;
  defaultField: string;
  defaultTitleField: string;
  defaultDescriptionField: string;
  defaultTextField: string;
  defaultDateField: string;
  defaultImageField: string;
  defaultURLField: string;
  facet: number;
  itemsPerFacet: number;
  hl: number;
  hlPre: string;
  hlPost: string;
  mlt: number;
  thesaurus: number;
  turSEInstance: TurSEInstance;
  turSNSiteLocales: TurSNSiteLocale[];
  rowsPerPage: number;
  spellCheck: number;
  spellCheckFixes: number;
  spotlightWithResults: number;
  facetType: TurSNSiteFacetEnum;
  facetItemType: TurSNSiteFacetEnum;
  facetSort: TurSNSiteFacetSortEnum;
  wildcardNoResults: number;
  wildcardAlways: number;
  exactMatch: number;
  turSNSiteGenAi: TurSNSiteGenAi;
}
