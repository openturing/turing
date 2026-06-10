---
"@viglet/turing-react-ui": minor
---

a11y + SSR hardening pass (T304): the `TuringHtmlSandbox` fullscreen portal is now a labelled modal dialog (`role="dialog"` + `aria-modal` + `aria-label`) with a focus trap (Tab/Shift+Tab cycle inside), initial focus into the dialog, and focus restoration to the trigger on close. The code/preview toggle gained an accessible name for icon-only use. `TuringD2Diagram` exposes its loading state as an `aria-live` status, the rendered SVG as a labelled `role="img"` (new `labels.diagram`), and the source fallback caption as a `role="alert"`. `TuringCopyButton` announces its "Copied" state via `aria-live`. SSR/RSC-safety audited across all components — client-only `document`/`navigator`/portal access stays guarded and effect-scoped.
