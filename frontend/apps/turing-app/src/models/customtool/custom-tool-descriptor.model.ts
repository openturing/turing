/**
 * Editor descriptor returned by `GET /api/custom-tool/descriptor`. Drives the
 * Custom Tool Groovy editor's auto-complete (T40) — lists every binding the
 * runtime injects into a script and the methods callable on each helper.
 *
 * The shape is deliberately editor-agnostic so a future Monaco / LSP path can
 * consume the same payload without backend churn.
 *
 * @since 2026.3.1
 */

export interface CustomToolMethodDescriptor {
  /** Method name as called in Groovy (e.g. `getJson`). */
  name: string;
  /** Hand-written signature with named-arg / default-value conventions. */
  signature: string;
  /** One-line summary shown in the autocomplete popover. */
  description: string;
}

export interface CustomToolHelperDescriptor {
  /** Binding name in the Groovy script (e.g. `http`). */
  name: string;
  /** Underlying Java class — surfaced as a hint, not used for routing. */
  className: string;
  /** Description shown when the binding name itself is hovered. */
  description: string;
  methods: CustomToolMethodDescriptor[];
}

export interface CustomToolGlobalDescriptor {
  /** Non-helper binding name (e.g. `args`). */
  name: string;
  /** Type as authors should read it (e.g. `Map<String, Object>`). */
  type: string;
  description: string;
}

export interface CustomToolEditorDescriptor {
  helpers: CustomToolHelperDescriptor[];
  globals: CustomToolGlobalDescriptor[];
}
