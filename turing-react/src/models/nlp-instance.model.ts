import { TurNLPVendor } from "./nlp-vendor.model";

export interface TurNLPInstance {
  id: string;
  title: string;
  description: string;
  endpointURL: string;
  key: string;
  language: string;
  enabled: number;
}
