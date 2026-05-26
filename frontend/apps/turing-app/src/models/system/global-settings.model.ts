export type TurGlobalDecimalSeparator = "DOT" | "COMMA";

export type TurEmailProviderType = "BREVO";

export interface TurGlobalSettings {
  decimalSeparator: TurGlobalDecimalSeparator;
  pythonExecutable?: string;
  /**
   * Optional requirements.txt-style declaration of Python packages the
   * Code Interpreter sandbox auto-installs before every Custom Tool that
   * calls `code.executePython(...)`. One package per line, optionally
   * version-pinned (`reportlab==4.0.7`). Blank disables auto-install.
   */
  pythonRequirements?: string;
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
  defaultAiAgentId?: string;
  /**
   * T61 — retention TTL in hours for `pii_*` prefixed slots. The hourly
   * cleanup job wipes PII slot values older than this. `0` disables
   * retention enforcement (slots stay encrypted at rest and redacted in
   * logs). Clamped server-side to [0, 8760] (one year).
   *
   * @since 2026.3.1
   */
  piiSlotTtlHours?: number;
}
