import { TurSNSiteMergeField } from "./sn-site-merge-field.model";
import { TurSNSite } from "./sn-site.model";

export interface TurSNSiteMerge {
  id: string;
  turSNSite: TurSNSite;
  locale: string;
  providerFrom: string;
  providerTo: string;
  relationFrom: string;
  relationTo: string;
  overwrittenFields: TurSNSiteMergeField[];
  description: string;
}
