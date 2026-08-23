// T1002 — shim. The layout vocabulary now lives in the design system; this
// module keeps its path and its nine names so the mosaic, the queries and the
// service need no edit.
//
// It was in neither the leaf list T989 worked from nor the compound list T990
// did, which is the only reason it stayed this long: nothing about it is this
// product's. The emphasis table and the resolve/serialise pair are pure
// functions over a layout response — where that response is *stored* is ours,
// and that half stays in `api/queries/bento-layout.queries.ts`.
export {
  BENTO_EMPHASIS_NEXT,
  BENTO_EMPHASIS_SPAN,
  resolveBentoLayout,
  toBentoLayoutEntries,
  type BentoEmphasis,
  type BentoLayoutEntry,
  type BentoLayoutResponse,
  type BentoLayoutSource,
  type ResolvedBentoItem,
} from "@viglet/viglet-design-system/bento";
