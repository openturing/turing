import type { TurLLMVendor } from "./llm-vendor.model.ts";

export interface TurLLMInstance {
  id: string;
  title: string;
  description: string;
  url: string;
  turLLMVendor: TurLLMVendor;
  language: string;
  enabled: number;
  modelName: string;
  temperature: number;
  topK: number;
  topP: number;
  repeatPenalty: number;
  seed: number;
  numPredict: number;
  stop: string;
  responseFormat: string;
  supportedCapabilities: string;
  timeout: string;
  maxRetries: number;
  contextWindow: number;
  toolsEnabled: boolean;
  apiKey?: string;
  providerOptionsJson?: string;
  icon?: string | null;
}
