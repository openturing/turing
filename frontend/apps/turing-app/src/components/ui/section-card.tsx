// T994 — shim. The section adapter lives in the design system as
// `AdaptiveSectionCard`; this module keeps its path and its local name so the
// nineteen files composing `SectionCard` need no edit.
//
// It never forked the design system's SectionCard — it wrapped it, so a form
// with real field logic is written once and renders as frosted bento sections.
// That is the most reusable thing this migration produced, and it sat in one
// product's ui folder. Shio needs it more than Turing does: its post editor,
// post-type editor and site editor are its three largest forms, and a parallel
// bento route would otherwise fork all three.
//
// Renamed on the way out, because the package already exports a `SectionCard` —
// the console card this used to switch *to*. `AdaptiveSectionCard` is aliased
// back to the local name here rather than renamed at nineteen call sites: the
// adapter a page composes is the same component either way, and a rename would
// have made a module move read as a change in what the page renders. `.Header`,
// `.StaticHeader` and `.Content` are attached to it there exactly as they were
// here.
//
// VDS146 removed `SectionCardChromeProvider`, `useSectionChrome` and the
// `SectionChrome` type from the package: with every consumer cut over there was
// one chrome left and nothing to pick between. They were re-exported here and
// are gone from this list for that reason.
export {
  AdaptiveSectionCard as SectionCard,
  type AdaptiveSectionCardProps as SectionCardProps,
} from "@viglet/viglet-design-system/bento";
