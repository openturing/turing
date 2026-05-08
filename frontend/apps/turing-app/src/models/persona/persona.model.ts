import type { TurMcpServer } from "../mcp/mcp-server.model.ts";
import type { TurStoreInstance } from "../store/store-instance.model.ts";

export type TurPersonaTone = "FORMAL" | "CASUAL" | "TECHNICAL" | "EXECUTIVE";
export type TurPersonaLanguageStyle =
  | "NEUTRAL"
  | "DIRECT"
  | "NARRATIVE"
  | "PERSUASIVE"
  | "INSTRUCTIONAL";

/**
 * Reusable voice profile that can be attached to a TurAIAgent. Mirrors
 * the backend `TurPersona` JPA entity. `mandatoryTerms` and
 * `forbiddenTerms` are pipe-separated strings (`term A|term, with comma`)
 * — the form parses/joins on save.
 */
export interface TurPersona {
  id: string;
  name: string;
  description?: string | null;
  systemInstruction?: string | null;
  tone?: TurPersonaTone | null;
  verbosity: number;
  languageStyle?: TurPersonaLanguageStyle | null;
  mandatoryTerms?: string | null;
  forbiddenTerms?: string | null;
  enabled: number;
  fewShotStore?: TurStoreInstance | null;
  brandContextMcpServer?: TurMcpServer | null;
}

/**
 * LLM-shape used by the AI Authoring chat. Strips the id and the heavy
 * relations (`fewShotStore`, `brandContextMcpServer`) — the operator wires
 * those after save. Mirrors the backend `PersonaGeneration` record.
 *
 * @since 2026.2.7
 */
export interface PersonaGeneration {
  name: string;
  description?: string | null;
  systemInstruction?: string | null;
  tone?: TurPersonaTone | null;
  verbosity: number;
  languageStyle?: TurPersonaLanguageStyle | null;
  mandatoryTerms?: string | null;
  forbiddenTerms?: string | null;
  enabled: number;
}
