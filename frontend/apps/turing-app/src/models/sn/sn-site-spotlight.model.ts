import type {TurSNSiteSpotlightDocument} from "./sn-site-spotlight-document.model.ts";
import type {TurSNSiteSpotlightTerm} from "./sn-site-spotlight-term.model.ts";
import type {TurSNSite} from "./sn-site.model.ts";

export type TurSNSiteSpotlight = {
  id: string;
  name: string;
  description: string;
  language: string;
  modificationDate: Date;
  turSNSiteSpotlightTerms: TurSNSiteSpotlightTerm[];
  turSNSiteSpotlightDocuments: TurSNSiteSpotlightDocument[];
  turSNSite: TurSNSite;
}
