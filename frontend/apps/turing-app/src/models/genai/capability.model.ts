/**
 * T432 / §X.18 — one row of the unified capability registry, as returned by
 * `GET /api/capability/registry`. Both the agent Tools & Capabilities picker
 * (T434) and the REQUEST_OPTION settings (T435) render from this.
 */
export type TurCapabilityKind = "TOOL" | "REQUEST_OPTION" | "PLATFORM";

export type TurCapabilityProvider = "TURING" | "OPENAI" | "ANTHROPIC" | "ANY";

export interface TurCapabilityDescriptor {
  /** Unique id: a `TurNativeCapability` key (e.g. "openai-web-search") or a Turing tool group id (e.g. "finance"). */
  key: string;
  /** Human-readable fallback label (the UI localizes by `key` when it can). */
  label: string;
  description: string;
  /** Decides the UI surface: TOOL → picker (T434), REQUEST_OPTION → settings (T435), PLATFORM → own screens. */
  kind: TurCapabilityKind;
  /** Abstract function; descriptors sharing a function are mutually-exclusive alternatives (radio). */
  function: string;
  /** Who executes it. */
  provider: TurCapabilityProvider;
  /** Coarse category for visual layout ("Web", "Code", …). */
  category: string;
  /** When true the capability takes the whole turn alone (computer_use). */
  ownsTurn: boolean;
  /** For Turing tool groups, the `@Tool` names in the group; empty for provider-native rows. */
  toolNames: string[];
}
