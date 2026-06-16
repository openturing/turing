/**
 * An admin-declared outbound webhook (T62). POSTs the conversation
 * transcript + slot snapshot to a CRM endpoint. Mirrors the
 * {@code TurChatWebhookDto} record on the backend.
 *
 * <p>{@link authHeader} is write-only — the API never echoes it back; reads
 * surface only {@link hasAuthHeader}. Leave it blank on edit to keep the
 * stored credential untouched.
 *
 * @since 2026.3.1
 */
export type TurChatWebhookMethod = "POST" | "PUT" | "PATCH" | "GET" | "DELETE";

export interface TurChatWebhook {
  id?: string;
  name: string;
  description?: string;
  /** Absolute http(s) URL the payload is sent to. */
  targetUrl: string;
  /** HTTP method: POST (default), PUT, PATCH, GET, DELETE. GET/DELETE send no body. */
  httpMethod?: TurChatWebhookMethod;
  /** Custom request headers as a JSON object string, e.g. {"X-Api-Key":"abc"}. */
  headersJson?: string;
  /**
   * Optional JSON body template with {@code {{slot}}} placeholders so the POST
   * matches the CRM's own schema. Blank = default envelope.
   */
  payloadTemplate?: string;
  /**
   * Slot name that auto-fires the webhook, the wildcard {@code *} for any
   * slot write, or blank to make it handoff-only (fired explicitly via the
   * handoff endpoint, never on slot writes).
   */
  slotTrigger?: string;
  /** Comma-separated slot whitelist for the payload. Blank = all slots. */
  includeSlots?: string;
  /** Write-only Authorization header value (e.g. "Bearer xyz"). */
  authHeader?: string;
  /** Read-only: whether a credential is stored. Never carries the value. */
  hasAuthHeader?: boolean;
  /**
   * Write-only HMAC-SHA256 signing key (T378). When set, each dispatch adds a
   * `<signatureHeader>: sha256=<hex>` header over the body. Leave blank on edit
   * to keep the stored key untouched.
   */
  signingSecret?: string;
  /** Read-only: whether a signing key is stored. Never carries the value. */
  hasSigningSecret?: boolean;
  /** Header name the HMAC signature is sent under. Blank = X-Turing-Signature. */
  signatureHeader?: string;
  enabled?: boolean;
}
