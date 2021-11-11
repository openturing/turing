import { TurNLPInstance } from "../../nlp/model/nlp-instance.model";
import { TurSEInstance } from "../../se/model/se-instance.model";

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
  core: string;
  thesaurus: number;
  turSEInstance: TurSEInstance;
  turNLPInstance: TurNLPInstance;
  language: string;
  rowsPerPage: number;
  spellCheck: number;
  spellCheckFixes: number;
}
