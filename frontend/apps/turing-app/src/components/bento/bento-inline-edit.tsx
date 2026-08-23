// T1002 — shim. BentoInlineEdit now lives in the design system; this module
// keeps its path and its name so the three pages that compose it need no edit.
//
// The package's copy is not byte-identical to what stood here, and the
// differences are both fixes it made after the move: a rejected `onCommit` used
// to escape as an unhandled rejection, because `commit` is wired to `onBlur` and
// nothing awaits what that returns, and the external-value reset ran in an
// effect that painted the stale draft once before correcting it.
export { BentoInlineEdit, type BentoInlineEditProps } from "@viglet/viglet-design-system/bento";
