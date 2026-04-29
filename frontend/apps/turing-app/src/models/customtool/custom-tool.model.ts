/**
 * User-defined Spring AI tool callable backed by a Groovy script.
 *
 * @since 2026.2.5
 */

export type CustomToolPrimitive = "string" | "integer" | "number" | "boolean";

export interface CustomToolParameter {
  name: string;
  type: CustomToolPrimitive;
}

export interface TurCustomTool {
  id: string;
  title: string;
  description?: string | null;
  icon?: string | null;
  llmDescription: string;
  groovyScript: string;
  /** JSON-serialized {@link CustomToolParameter} array. */
  parametersJson?: string | null;
  returnType: CustomToolPrimitive;
  enabled: number;
}
