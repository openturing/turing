// T1009 — shim. IconPickerDialog lives in the design system; this module keeps
// its path and its name so `icon-picker.tsx` needs no edit beyond one prop.
//
// T1008 could not move it: the local copy carried an LLM pass that turned the
// entity's title and description into icon search keywords, and the package's
// copy had no seam for it, so taking that copy would have made the picker
// quietly stop suggesting on a live page — the agent intent surface under
// /bento/ai-agent renders it.
//
// The package does have the seam: `suggestKeywords`, a function from the
// entity's context to a list of keywords, which it then searches Iconify for and
// groups. Everything after the keywords was already generic; only their origin
// was ours. `useIconKeywordSuggester` in ./icon-keyword-suggester supplies it,
// and returns undefined where no default LLM is configured, which is how the
// package knows to hide the button.
export { IconPickerDialog, type IconPickerDialogProps } from "@viglet/viglet-design-system";
