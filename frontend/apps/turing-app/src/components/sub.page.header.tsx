// T1000 — shim. SubPageHeader lives in the design system; this module keeps its
// path so the 16 pages composing it, and their `SubPageHeader.Action` children,
// need no edit.
//
// Three things made this checkable rather than hopeful: `components/ui/sidebar`
// here is already a re-export of the package's, so the package's header reads
// the same sidebar context; its `useSidebarOptional` is strictly more forgiving
// than the `useSidebar` that stood here, degrading to a desktop layout instead
// of throwing where no provider is mounted; and its `markerName(child)` reads
// the same `displayName` the local copy compared, with `.Action` arriving from
// the same module as the header, so the marker cannot drift out of step.
export { SubPageHeader } from "@viglet/viglet-design-system/router";
