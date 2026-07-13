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
 * Block AA / §XXVI.1 — what a persona is usable for. `SPEAKER` (default) is
 * the legacy voice contract; `AUDIENCE` models a reader for content-fit
 * evaluation; `BOTH` is a peer persona used as voice and audience.
 */
export type TurPersonaKind = "SPEAKER" | "AUDIENCE" | "BOTH";

/**
 * T718 / §XLVI.1 — where a persona's answers are grounded. `NONE` (default) =
 * the model's priors; `SN_SITE` = a Semantic Navigation site's indexed corpus;
 * `NOTEBOOK` = the persona's own Block AA notebook source set.
 */
export type TurPersonaGroundingSource = "NONE" | "SN_SITE" | "NOTEBOOK";

export type TurPersonaReadingLevel =
  | "ELEMENTARY"
  | "MIDDLE"
  | "SECONDARY"
  | "UNDERGRADUATE"
  | "GRADUATE";

export type TurPersonaDomainExpertise =
  | "NOVICE"
  | "BEGINNER"
  | "INTERMEDIATE"
  | "ADVANCED"
  | "EXPERT";

/**
 * The audience facet of a persona — the reader proxy read only by the
 * content-fit evaluation path. Mirrors the backend `TurPersonaAudience`
 * embeddable; populated only when `personaKind` is AUDIENCE or BOTH.
 */
export interface TurPersonaAudience {
  readingLevel?: TurPersonaReadingLevel | null;
  domainExpertise?: TurPersonaDomainExpertise | null;
  vocabularyCeiling?: string | null;
  comprehensionNotes?: string | null;
  accessibilityNotes?: string | null;
  primaryLanguage?: string | null;
}

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
  /**
   * T605 — opt-in style→model calibration. When 1, verbosity → maxTokens and
   * tone → temperature calibrate the model's sampling params per turn. 0 (default)
   * keeps the LLM instance's own params.
   */
  calibrateModelParams?: number;
  /**
   * T717 / §XLVI.1 — opt-in Big Five (OCEAN) personality traits, 0–100 each.
   * `null`/omitted = unset (renders nothing, byte-for-byte legacy). When set they
   * render as behavioral guidance in the persona prompt and — when
   * `calibrateModelParams` is on — nudge the calibrated sampling temperature.
   */
  openness?: number | null;
  conscientiousness?: number | null;
  extraversion?: number | null;
  agreeableness?: number | null;
  neuroticism?: number | null;
  /**
   * T718 / §XLVI.1 — opt-in knowledge binding. `NONE` (default) keeps legacy
   * behavior; `SN_SITE` grounds on `groundingSnSite`; `NOTEBOOK` grounds on the
   * persona's own notebook source set.
   */
  groundingSource?: TurPersonaGroundingSource;
  /** The bound SN site name — used only when `groundingSource` is `SN_SITE`. */
  groundingSnSite?: string | null;
  fewShotStore?: TurStoreInstance | null;
  brandContextMcpServer?: TurMcpServer | null;
  /** Block AA — defaults to SPEAKER for legacy voice personas. */
  personaKind?: TurPersonaKind | null;
  /** Block AA — present only for AUDIENCE / BOTH personas. */
  audience?: TurPersonaAudience | null;
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
