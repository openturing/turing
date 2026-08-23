// T993 — shim. BentoShortcutsDialog now lives in the design system; this module
// keeps its path and its name so the shell needs no edit. It reads nothing of
// this product: the shortcuts it lists are the shell's own bindings, and the one
// thing that varies is whether to draw ⌘ or Ctrl, which is already a prop.
export {
  BentoShortcutsDialog,
  type BentoShortcutsDialogProps,
} from "@viglet/viglet-design-system/bento";
