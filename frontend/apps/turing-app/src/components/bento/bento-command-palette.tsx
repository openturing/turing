// T993 — shim. BentoCommandPalette now lives in the design system; this module
// keeps its path and its name so the shell needs no edit beyond the props.
//
// It was only ever coupled to this product through the nav config, which T992
// split into a package schema and a product array. The palette now takes that
// array as `items` — the same one the rail is given, so `⌘K` and the rail can
// never disagree about which surfaces exist.
export {
  BentoCommandPalette,
  type BentoCommandPaletteProps,
} from "@viglet/viglet-design-system/bento";
