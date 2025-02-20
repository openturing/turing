import { TurStoreVendor } from "./store-vendor.model";

export interface TurStoreInstance {
  id: string;
  title: string;
  description: string;
  url: string;
  turStoreVendor: TurStoreVendor;
  language: string;
  enabled: number;
}
