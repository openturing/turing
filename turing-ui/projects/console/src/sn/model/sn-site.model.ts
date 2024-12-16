import { TurNLPVendor } from "../../nlp/model/nlp-vendor.model";
import { TurSEInstance } from "../../se/model/se-instance.model";
import { TurSNSiteLocale } from "./sn-site-locale.model";

export interface TurSNSite {
  id: string;
  name: string;
  description: string;
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
  turNLPVendor: TurNLPVendor;
  turSNSiteLocales: TurSNSiteLocale[];
  rowsPerPage: number;
  spellCheck: number;
  spellCheckFixes: number;
  spotlightWithResults: number;
}
