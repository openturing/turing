import { useMemo } from "react";
import type { ResolvedDocument } from "../core/types";

/**
 * Helper interface returned by useTuringDocument.
 * Provides typed, normalized field access for document custom fields.
 */
export interface TuringDocumentHelper {
  /** Get a field value as a string. Returns first element if array, fallback if missing. */
  getString: (field: string, fallback?: string) => string;

  /** Get a field value always as a string array. Normalizes single values to [value]. */
  getArray: (field: string) => string[];

  /** Get a field value as a number. Returns fallback if missing or NaN. */
  getNumber: (field: string, fallback?: number) => number;

  /** Get a field value as a boolean. */
  getBoolean: (field: string) => boolean;

  /** Get a field value as a Date. Returns null if missing or invalid. */
  getDate: (field: string) => Date | null;

  /** Get a field value as-is (no normalization). */
  getRaw: <T = unknown>(field: string) => T | undefined;

  /** The resolved document (title, url, description, etc.) */
  doc: ResolvedDocument;

  /** Direct access to raw fields */
  fields: Record<string, unknown>;
}

/**
 * Provides typed, normalized access to document custom fields.
 * Eliminates repetitive `Array.isArray()` checks and type coercion.
 *
 * @example
 * ```tsx
 * function CourseCard({ doc }: { doc: ResolvedDocument }) {
 *   const d = useTuringDocument(doc);
 *
 *   const modalities = d.getArray("formato-de-aula");   // always string[]
 *   const theme = d.getString("temas");                  // always string
 *   const date = d.getDate("data-inicio");               // Date | null
 *   const price = d.getNumber("preco", 0);               // number
 *
 *   return (
 *     <article>
 *       <h3>{doc.title}</h3>
 *       {modalities.map(m => <span key={m} className="tag">{m}</span>)}
 *     </article>
 *   );
 * }
 * ```
 *
 * @since 2026.3.0
 */
export function useTuringDocument(doc: ResolvedDocument): TuringDocumentHelper {
  return useMemo(() => {
    const fields = doc.raw.fields as Record<string, unknown>;

    const getString = (field: string, fallback = ""): string => {
      const val = fields[field];
      if (val == null) return fallback;
      if (Array.isArray(val)) return val.length > 0 ? String(val[0]) : fallback;
      return String(val);
    };

    const getArray = (field: string): string[] => {
      const val = fields[field];
      if (val == null) return [];
      if (Array.isArray(val)) return val.map(String);
      return [String(val)];
    };

    const getNumber = (field: string, fallback = 0): number => {
      const val = fields[field];
      if (val == null) return fallback;
      const num = Number(Array.isArray(val) ? val[0] : val);
      return Number.isNaN(num) ? fallback : num;
    };

    const getBoolean = (field: string): boolean => {
      const val = fields[field];
      if (val == null) return false;
      const v = Array.isArray(val) ? val[0] : val;
      return v === true || v === "true" || v === "1";
    };

    const getDate = (field: string): Date | null => {
      const val = fields[field];
      if (val == null) return null;
      const raw = Array.isArray(val) ? val[0] : val;
      const d = new Date(raw as string | number);
      return Number.isNaN(d.getTime()) ? null : d;
    };

    const getRaw = <T = unknown>(field: string): T | undefined => {
      return fields[field] as T | undefined;
    };

    return { getString, getArray, getNumber, getBoolean, getDate, getRaw, doc, fields };
  }, [doc]);
}
