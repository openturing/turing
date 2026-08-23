// T992 — shim. BentoNavRail now lives in the design system; this module keeps
// its path and its name so the shell needs no edit beyond the props.
//
// The rail arrives generic: it renders the groups it is handed and links a Home
// button wherever it is told. Both are this product's to supply — which
// surfaces a reader may see is a privilege question, and where home is is a
// route question — so the shell passes them at the call site, next to the
// `ROUTES` it already imports.
export { BentoNavRail, type BentoNavRailProps } from "@viglet/viglet-design-system/bento";
