export type TurGlobalDecimalSeparator = "DOT" | "COMMA";

export type TurEmailProviderType = "BREVO";

export interface TurGlobalSettings {
  decimalSeparator: TurGlobalDecimalSeparator;
  pythonExecutable?: string;
  defaultLlmId?: string;
  llmCacheEnabled?: boolean;
  llmCacheTtlMs?: number;
  llmCacheRegenerate?: boolean;
  emailProvider?: TurEmailProviderType;
  emailApiKey?: string;
  senderEmail?: string;
  senderName?: string;
  recipientEmail?: string;
  ragEnabled?: boolean;
  defaultEmbeddingModelId?: string;
  defaultEmbeddingStoreId?: string;
}
