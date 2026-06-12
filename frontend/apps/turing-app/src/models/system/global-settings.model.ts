export type TurGlobalDecimalSeparator = "DOT" | "COMMA";

export type TurEmailProviderType = "BREVO";

/**
 * T80 — where the Code Interpreter runs visitor-/agent-generated Python.
 * `NATIVE` = host subprocess (legacy default, no runtime isolation);
 * `DOCKER` = each execution runs in a throwaway hardened container.
 */
export type TurCodeInterpreterExecutionMode = "NATIVE" | "DOCKER";

/**
 * T337 — SN RAG reranker backend. `LLM` borrows a chat model to rank snippet
 * numbers (legacy default); `CROSS_ENCODER` calls a self-hosted `/rerank`
 * endpoint; `COHERE` uses the managed Cohere Rerank API.
 */
export type TurRagRerankStrategy = "LLM" | "CROSS_ENCODER" | "COHERE";

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
  /**
   * T80 — Code Interpreter execution mode. `NATIVE` runs Python as a host
   * subprocess (default); `DOCKER` runs each execution inside a throwaway
   * hardened container for runtime isolation.
   */
  codeInterpreterExecutionMode?: TurCodeInterpreterExecutionMode;
  /**
   * T80 — Docker image used when `codeInterpreterExecutionMode` is
   * `DOCKER`. Should bundle Python + the agents' packages. Blank falls
   * back to the server default (`python:3.12-slim`).
   */
  codeInterpreterDockerImage?: string;
  /**
   * T321 — Docker image for the Anthropic-compatible skill sandbox session
   * (a `bash`-driven container with the skill folder mounted). Should bundle
   * python + node + bash. Blank falls back to the server default
   * (`python:3.12-slim`). Used only in `DOCKER` mode.
   */
  codeInterpreterSkillImage?: string;
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
  /** T328 — whether the SN RAG reranker runs (default off). */
  ragSnRerankEnabled?: boolean;
  /** T328 — chunks kept after the reranker narrows the candidate pool. */
  ragSnRerankTopN?: number;
  /** T337 — reranker backend: `LLM` (default) | `CROSS_ENCODER` | `COHERE`. */
  ragSnRerankStrategy?: TurRagRerankStrategy;
  /** T338 — self-hosted cross-encoder `/rerank` endpoint URL. */
  ragSnRerankEndpoint?: string;
  /** T338 — rerank model name (cross-encoder / Cohere); blank = backend default. */
  ragSnRerankModel?: string;
  /**
   * T339 — Cohere API key. Write-only: sent on update, never returned on read
   * (always blank in GET). A blank value on save leaves the stored key intact.
   */
  ragSnRerankApiKey?: string;
  /** T339 — read-only status: whether a Cohere API key is configured. */
  ragSnRerankApiKeySet?: boolean;
}
