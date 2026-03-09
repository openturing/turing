import type { TurSECoreSiteUsage } from "./se-core-site-usage.model";

export interface TurSECoreInfo {
  name: string;
  numDocs: number;
  usedBySites: TurSECoreSiteUsage[];
}
