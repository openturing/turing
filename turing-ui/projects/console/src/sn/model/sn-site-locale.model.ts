import { TurNLPInstance } from "../../nlp/model/nlp-instance.model";

export interface TurSNSiteLocale {
  id: string;
  language: string;
  core: string;
  turNLPInstance: TurNLPInstance;
}
