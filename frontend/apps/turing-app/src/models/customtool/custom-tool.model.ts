/**
 * User-defined Spring AI tool callable backed by a Groovy script.
 *
 * @since 2026.2.5
 */

export type CustomToolPrimitive = "string" | "integer" | "number" | "boolean" | "html" | "markdown" | "json";

export interface CustomToolParameter {
  name: string;
  type: CustomToolPrimitive;
}

export interface TurCustomTool {
  id: string;
  title: string;
  description?: string | null;
  /** User-authored brief that drives the SmartDescription "Help me write" modal. */
  descriptionMetaPrompt?: string | null;
  icon?: string | null;
  llmDescription: string;
  /** User-authored brief that drives the SmartDescription "Help me write" modal for llmDescription. */
  llmDescriptionMetaPrompt?: string | null;
  groovyScript: string;
  /** User-authored brief that drives the "Vibe coding" sheet for the Groovy editor. */
  groovyMetaPrompt?: string | null;
  /** JSON-serialized {@link CustomToolParameter} array. */
  parametersJson?: string | null;
  returnType: CustomToolPrimitive;
  enabled: number;
}
