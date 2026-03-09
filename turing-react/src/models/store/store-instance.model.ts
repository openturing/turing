import type { TurStoreVendor } from "./store-vendor.model.ts";

export interface TurStoreInstance {
  id: string;
  title: string;
  description: string;
  url: string;
  turStoreVendor: TurStoreVendor;
  enabled: number;
  collectionName?: string;
  credential?: string;
  providerOptionsJson?: string;
}
