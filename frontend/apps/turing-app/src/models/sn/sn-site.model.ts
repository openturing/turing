import type { TurSEInstance } from "../se/se-instance.model.ts";
import type { TurSNSiteFacetFieldSortTypes } from "./sn-site-facet-field-sort.type.ts";
import type { TurSNSiteFacetTypes } from "./sn-site-facet.type.ts";
import type { TurSNSiteGenAi } from "./sn-site-genai.model.ts";
import type { TurSNSiteLocale } from "./sn-site-locale.model.ts";

export type TurSNSite = {
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
  facetType: TurSNSiteFacetTypes;
  facetItemType: TurSNSiteFacetTypes;
  facetSort: TurSNSiteFacetFieldSortTypes;
  wildcardNoResults: number;
  wildcardAlways: number;
  exactMatch: number;
  turSNSiteGenAi: TurSNSiteGenAi;
  icon?: string | null;
  searchTemplate?: string | null;
  /**
   * T233 / §VII.6.h — whether the visitor-facing API (search / autocomplete /
   * query / click / chat / spell-check) is open or requires a Turing-registered
   * API key (Dev Token). Defaults to {@code "PUBLIC"} server-side.
   */
  apiAuthMode?: TurSNSiteApiAuthMode;
};

/** T233 / §VII.6.h — public vs API-key access for the visitor-facing API. */
export type TurSNSiteApiAuthMode = "PUBLIC" | "API_KEY";
