import { TurLLMVendor } from "./llm-vendor.model";

export interface TurLLMInstance {
  id: string;
  title: string;
  description: string;
  url: string;
  turLLMVendor: TurLLMVendor;
  language: string;
  enabled: number;
}
