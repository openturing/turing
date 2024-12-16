import { TurSEVendor } from "./se-vendor.model";

export interface TurSEInstance {
  id: string;
  title: string;
  description: string;
  host: string;
  port: number;
  turSEVendor: TurSEVendor;
  language: string;
  enabled: number;
}
