import type {TurSNSiteMergeField} from "./sn-site-merge-field.model.ts";
import type {TurSNSite} from "./sn-site.model.ts";

export type TurSNSiteMerge = {
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
