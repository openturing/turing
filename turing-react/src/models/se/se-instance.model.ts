import type { TurSEVendor } from "./se-vendor.model.ts";

export interface TurSEInstance {
  id: string;
  title: string;
  description: string;
  endpointUrl: string;
  turSEVendor: TurSEVendor;
  language: string;
  enabled: number;
  icon?: string | null;
}
