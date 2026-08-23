// T1000 — shim. The hook lives in the design system; this module keeps its path
// so the 23 pages calling it need no edit.
//
// Safe to take because `contexts/breadcrumb.context.tsx` here is already a
// re-export of the package's provider, so the package's hook writes to the very
// context this product's header reads. It also carries two fixes made after the
// copy diverged: a sub-page restating its parent's level used to return the
// breadcrumb untouched and lose every level behind the repeat — the hook's own
// documented two-level example — and the ref recording which levels it inserted
// was written outside the updater, so it named levels the dedupe had declined to
// insert and the cleanup then removed nothing.
export { useSubPageBreadcrumb } from "@viglet/viglet-design-system";
