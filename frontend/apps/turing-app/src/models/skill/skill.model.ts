/**
 * T116/T117 — client types for the Skills marketplace (`/api/skill`).
 *
 * A skill is a typed, versioned, signed bundle of
 * `(personaFragment + flowSnippet + tools + workspaceDefaults + dependencies)`.
 * The backend exchange shape is `TurSkillManifest`; an installed row is
 * summarized by `TurSkill`.
 *
 * @since 2026.3.1
 */

/** Outcome of comparing a manifest's declared signature to the recomputed digest. */
export type TurSkillSignatureStatus = "UNSIGNED" | "VALID" | "INVALID";

/**
 * Persona contribution a skill grafts onto the agent system prompt. Tone
 * vocabulary (mandatory/forbidden terms) is NOT modeled here — it lives solely
 * on the Persona, so the skill carries only the instruction snippet.
 */
export interface TurSkillPersonaFragment {
  instruction?: string;
}

/** Compact view of an installed skill — one marketplace card. */
export interface TurSkill {
  id: string;
  name: string;
  version: string;
  author: string | null;
  description: string | null;
  signature: string | null;
  builtIn: boolean;
  enabled: boolean;
  tools: string[] | null;
}

/** Full manifest — catalog entries and `GET /api/skill/{id}`. */
export interface TurSkillManifest {
  name: string;
  version: string;
  author?: string | null;
  signature?: string | null;
  description?: string | null;
  personaFragment?: TurSkillPersonaFragment | null;
  flowSnippet?: unknown;
  tools?: string[] | null;
  workspaceDefaults?: Record<string, string> | null;
  dependencies?: string[] | null;
}

export interface TurSkillValidateResponse {
  valid: boolean;
  errors: string[];
  signatureStatus: TurSkillSignatureStatus;
  computedSignature: string | null;
}

export interface TurSkillInstallResponse {
  success: boolean;
  id: string | null;
  name: string | null;
  version: string | null;
  error: string | null;
}
