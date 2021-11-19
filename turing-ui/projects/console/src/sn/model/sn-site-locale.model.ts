import { TurNLPInstance } from "../../nlp/model/nlp-instance.model";
import { TurSNSite } from "./sn-site.model";

export interface TurSNSiteLocale {
  id: string;
  language: string;
  core: string;
  turNLPInstance: TurNLPInstance;
  turSNSite: TurSNSite;
}
