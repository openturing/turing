import { TurSNSiteSpotlightDocument } from "./sn-site-spotlight-document.model";
import { TurSNSiteSpotlightTerm } from "./sn-site-spotlight-term.model";

export interface TurSNSiteSpotlight {
  id: string;
  name: string;
  description: string;
  language: string;
  date: Date;
  turSNSiteSpotlightTerms: TurSNSiteSpotlightTerm[];
  turSNSiteSpotlightDocuments: TurSNSiteSpotlightDocument[];
}
