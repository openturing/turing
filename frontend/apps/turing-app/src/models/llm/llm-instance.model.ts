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
  /**
   * T431 — allow sending raw document binaries (PDF/DOCX) to this provider's
   * native file input for document-to-slot extraction (T103) instead of Tika
   * text extraction. Only enable for providers that support document input
   * (Anthropic / Gemini / OpenAI).
   */
  fileUploadEnabled?: boolean;
  apiKey?: string;
  providerOptionsJson?: string;
  icon?: string | null;
  /** T372 — null for the shared GLOBAL BYO-infra pool (read-only when tenancy is on). */
  tenantId?: string | null;
}
