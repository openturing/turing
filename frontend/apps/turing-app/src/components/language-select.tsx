// T1000 — shim. LanguageSelect lives in the design system; this module keeps its
// path so the four forms composing it need no edit.
//
// `TurLocale` and `VigLocale` are the same three fields — `initials`, `en`, `pt`
// — so the locale arrays these callers already pass satisfy the package's props
// exactly. The package's copy also labels the trigger with its placeholder: a
// placeholder disappears the moment a locale is chosen, so without it a screen
// reader announced this as an unlabelled combobox.
export { LanguageSelect } from "@viglet/viglet-design-system";
