import { TurSEVendor } from "./se-vendor.model";

export interface TurSEInstance {
  id: string;
  title: string;
  description: string;
  url: string;
  turSEVendor: TurSEVendor;
  language: string;
  enabled: number;
}
