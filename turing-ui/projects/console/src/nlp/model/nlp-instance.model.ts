import { TurNLPVendor } from "./nlp-vendor.model";

export interface TurNLPInstance {
  id: string;
  title: string;
  description: string;
  host: string;
  port: number;
  language: string;
  enabled: number;
  turNLPVendor: TurNLPVendor;
}
