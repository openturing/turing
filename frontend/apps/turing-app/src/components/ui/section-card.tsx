/* eslint-disable react-refresh/only-export-components --
 * T1024 — a re-export shim: the component, its chrome provider and the hook that
 * reads it come from one package entry point because that is how the design
 * system ships them. Nothing is declared here to split.
 */
// T994 — shim. The chrome adapter lives in the design system as
// `AdaptiveSectionCard`; this module keeps its path and its local name so the
// nineteen files composing `SectionCard`, `SectionCardChromeProvider` and
// `useSectionChrome` need no edit.
//
// It never forked the design system's SectionCard — it wrapped it, so a provider
// at the top of a page decides whether the sections inside render as console
// cards or as frosted bento sections and a form with real field logic is written
// once. That is the most reusable thing this migration produced, and it sat in
// one product's ui folder. Shio needs it more than Turing does: its post editor,
// post-type editor and site editor are its three largest forms, and a parallel
// bento route would otherwise fork all three.
//
// Renamed on the way out, because the package already exports a `SectionCard` —
// the console card this switches *to*. `AdaptiveSectionCard` is aliased back to
// the local name here rather than renamed at nineteen call sites: the adapter a
// page composes is the same component either way, and a rename would have made a
// module move read as a change in what the page renders. `.Header`,
// `.StaticHeader` and `.Content` are attached to it there exactly as they were
// here.
export {
  AdaptiveSectionCard as SectionCard,
  SectionCardChromeProvider,
  useSectionChrome,
  type AdaptiveSectionCardProps as SectionCardProps,
  type SectionChrome,
} from "@viglet/viglet-design-system/bento";
