import { TurSNSiteSpotlightDocument } from "./sn-site-spotlight-document.model";
import { TurSNSiteSpotlightTerm } from "./sn-site-spotlight-term.model";
import {TurSNSite} from "./sn-site.model";

export interface TurSNSiteSpotlight {
  id: string;
  name: string;
  description: string;
  language: string;
  modificationDate: Date;
  turSNSiteSpotlightTerms: TurSNSiteSpotlightTerm[];
  turSNSiteSpotlightDocuments: TurSNSiteSpotlightDocument[];
  turSNSite: TurSNSite;
}
