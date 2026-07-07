/**
 * T132 / §X.2 — one row of the per-LLM-instance native capability matrix, as
 * returned by `GET /api/llm/{instanceId}/capability`. This is the admin gate
 * that actually enables a provider-native capability (e.g. OpenAI
 * `image_generation`) on an instance; an agent's capability picker can only
 * select a capability that is enabled here.
 */
export interface TurLLMInstanceCapability {
  /** A `TurNativeCapability` key, e.g. "openai-image-generation". */
  key: string;
  /** The vendor plugin type the capability belongs to, e.g. "openai" / "anthropic". */
  pluginType: string;
  /** Whether the capability is enabled on this instance. */
  enabled: boolean;
  /** Per-capability configuration JSON (vector-store ids, MCP server, …); may be null. */
  configJson: string | null;
}
