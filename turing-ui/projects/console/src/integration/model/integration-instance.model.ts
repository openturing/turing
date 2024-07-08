import {TurIntegrationVendor} from "./integration-vendor.model";


export interface TurIntegrationInstance {
  id: string;
  title: string;
  description: string;
  endpoint: string;
  vendor: string;
  enabled: number;
}
