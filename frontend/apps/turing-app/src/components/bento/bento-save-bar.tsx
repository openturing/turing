// T990 -- shim. BentoSaveBar now lives in the design system; this module keeps
// its path and its name so the pages importing it need no edit. The badge and
// its props were internal to the implementation and left with it.
export { BentoSaveBar, type BentoSaveBarProps } from "@viglet/viglet-design-system/bento";
