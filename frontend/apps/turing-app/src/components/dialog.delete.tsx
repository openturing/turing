// T1008 — shim. DialogDelete lives in the design system; this module keeps its
// path so the twenty call sites need no edit.
//
// The package's copy is the same dialog with one thing lifted out: where the
// product built each blocked-by link from `ROUTES.SN_INSTANCE` and a site/locale
// pair, it now takes an `href` per item. That is the right seam — which entity
// blocks a delete, and where it lives, is this product's knowledge — and it
// costs two call sites, both in the search-engine cores page, which are the only
// two that pass `blockedBy` at all. Its `trigger` also accepts `null` now, for a
// dialog opened from a dropdown item rather than its own button.
export {
  DialogDelete,
  type VigBlockedByItem,
} from "@viglet/viglet-design-system/router";
