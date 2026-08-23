// T1002 — shim. BentoHeroIconPicker now lives in the design system; this module
// keeps its path and its name so the pages that compose it need no edit.
//
// The package's copy draws its chip through `bentoChipClass(tone)` rather than
// indexing the local gradient map, which is the tone-as-token change VDS9 made.
// The gradient map itself stays declared here for the two heroes still reading
// it directly — that swap is T996's, and it is the one that has to be proved
// invisible.
export {
  BentoHeroIconPicker,
  type BentoHeroIconPickerProps,
} from "@viglet/viglet-design-system/bento";
