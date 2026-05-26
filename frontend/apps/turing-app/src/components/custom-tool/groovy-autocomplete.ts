/**
 * CodeMirror autocomplete extension for the Custom Tool Groovy editor (T40).
 *
 * Given a {@link CustomToolEditorDescriptor} fetched from
 * `GET /api/custom-tool/descriptor`, returns a CodeMirror v6 `Extension`
 * that suggests:
 *   - top-level binding names (`http`, `slots`, `turingSearch`, `code`, `args`),
 *     and
 *   - methods on a helper when the user types `<helper>.` (e.g. `http.` →
 *     `getJson` / `postJson` / `bearer` / `basic`).
 *
 * The Groovy `StreamLanguage` shipped by CodeMirror's legacy modes does NOT
 * provide a CompletionSource, so we wire one up explicitly via
 * `autocompletion({ override: [...] })`. Plays nicely with `basicSetup`
 * (which already enables the autocomplete extension package — the override
 * here is additive).
 *
 * @since 2026.3.1
 */

import { autocompletion, type Completion, type CompletionContext, type CompletionResult } from "@codemirror/autocomplete";
import type { Extension } from "@codemirror/state";
import type { CustomToolEditorDescriptor, CustomToolHelperDescriptor, CustomToolMethodDescriptor } from "@/models/customtool/custom-tool-descriptor.model.ts";

/** Matches a token like `http.get` so we can split on the dot to drive helper-method suggestions. */
const HELPER_MEMBER_PATTERN = /([A-Za-z_$][\w$]*)\.([A-Za-z_$][\w$]*)?$/;
/** Matches a bare identifier token (top-level binding suggestion). */
const IDENT_PATTERN = /[A-Za-z_$][\w$]*$/;

function methodCompletion(helperName: string, method: CustomToolMethodDescriptor): Completion {
  return {
    label: method.name,
    type: "method",
    detail: method.signature.replace(/^[^(]*(?=\()/, ""),
    info: `${helperName}.${method.signature}\n\n${method.description}`,
    boost: 1,
  };
}

function helperCompletion(helper: CustomToolHelperDescriptor): Completion {
  return {
    label: helper.name,
    type: "variable",
    detail: helper.className,
    info: `${helper.name}: ${helper.className}\n\n${helper.description}`,
    boost: 2,
  };
}

function globalCompletion(name: string, type: string, description: string): Completion {
  return {
    label: name,
    type: "variable",
    detail: type,
    info: `${name}: ${type}\n\n${description}`,
    boost: 2,
  };
}

/**
 * Build the CodeMirror completion source. Returns `null` when there's nothing
 * to suggest at the cursor (the source then yields to other CodeMirror
 * completion sources, like word-based suggestions).
 */
export function createGroovyCompletionSource(
  descriptor: CustomToolEditorDescriptor
): (context: CompletionContext) => CompletionResult | null {
  const helperByName = new Map(descriptor.helpers.map((h) => [h.name, h]));
  const topLevel: Completion[] = [
    ...descriptor.helpers.map(helperCompletion),
    ...descriptor.globals.map((g) => globalCompletion(g.name, g.type, g.description)),
  ];

  return (context: CompletionContext): CompletionResult | null => {
    // 1) Member access: `http.|` or `http.ge|`
    const memberMatch = context.matchBefore(HELPER_MEMBER_PATTERN);
    if (memberMatch && memberMatch.text.includes(".")) {
      const dotIndex = memberMatch.text.indexOf(".");
      const helperName = memberMatch.text.slice(0, dotIndex);
      const partial = memberMatch.text.slice(dotIndex + 1);
      const helper = helperByName.get(helperName);
      if (!helper) {
        return null;
      }
      return {
        from: memberMatch.from + dotIndex + 1,
        to: memberMatch.from + dotIndex + 1 + partial.length,
        options: helper.methods.map((m) => methodCompletion(helper.name, m)),
        validFor: /^[\w$]*$/,
      };
    }

    // 2) Top-level identifier suggestion. Trigger only when there's at least
    //    one character typed OR the user explicitly hit Ctrl+Space — avoids a
    //    noisy popup on every keystroke at column 0.
    const identMatch = context.matchBefore(IDENT_PATTERN);
    if (!identMatch && !context.explicit) {
      return null;
    }
    if (identMatch && identMatch.from === identMatch.to && !context.explicit) {
      return null;
    }
    return {
      from: identMatch ? identMatch.from : context.pos,
      options: topLevel,
      validFor: /^[\w$]*$/,
    };
  };
}

/**
 * Convenience: wraps the source in CodeMirror's `autocompletion({...})`
 * factory so callers get a ready-to-attach `Extension`.
 */
export function groovyAutocomplete(descriptor: CustomToolEditorDescriptor): Extension {
  return autocompletion({
    override: [createGroovyCompletionSource(descriptor)],
  });
}
