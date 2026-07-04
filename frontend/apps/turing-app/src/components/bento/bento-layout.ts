/**
 * T575 / §XXXI.11 — the shared layout vocabulary for customizable Bento lists:
 * the {@link BentoEmphasis} sizes, the persisted layout shape (mirrors the T574
 * backend `TurBentoLayoutResponse`), the emphasis→grid-span map, and the pure
 * resolver that turns persisted entries (or the built-in default) into an
 * ordered, sized list. Keeping the resolver pure makes it unit-testable without
 * a DOM or the network.
 */

/** How prominent one tile is, decoupled from its position (T574 mirror). */
export type BentoEmphasis = "SMALL" | "MEDIUM" | "LARGE";

/** Which cascade layer produced the resolved layout. */
export type BentoLayoutSource = "USER" | "GLOBAL" | "DEFAULT";

/** One item's persisted placement. */
export interface BentoLayoutEntry {
  itemId: string;
  emphasis: BentoEmphasis;
  displayOrder: number;
}

/** The resolved layout the API returns for a list surface. */
export interface BentoLayoutResponse {
  listId: string;
  source: BentoLayoutSource;
  canEditGlobal: boolean;
  entries: BentoLayoutEntry[];
}

/**
 * Grid span per emphasis. `LARGE` = the 2×2 hero (today's featured), `MEDIUM` =
 * the 2×1 wide tile (today's non-featured default), `SMALL` = a compact 1×1 that
 * a `grid-flow-dense` pass uses to fill holes. Matches {@link BentoEntityTile}.
 */
export const BENTO_EMPHASIS_SPAN: Record<BentoEmphasis, string> = {
  LARGE: "col-span-2 row-span-2 md:col-span-2 md:row-span-2 lg:col-span-2 lg:row-span-2",
  MEDIUM: "col-span-2 row-span-1 md:col-span-2 md:row-span-1 lg:col-span-2 lg:row-span-1",
  SMALL: "col-span-1 row-span-1",
};

/** Next size when cycling the per-tile emphasis affordance. */
export const BENTO_EMPHASIS_NEXT: Record<BentoEmphasis, BentoEmphasis> = {
  SMALL: "MEDIUM",
  MEDIUM: "LARGE",
  LARGE: "SMALL",
};

/** An item paired with its resolved key + emphasis, in resolved order. */
export interface ResolvedBentoItem<T> {
  item: T;
  key: string;
  emphasis: BentoEmphasis;
}

/**
 * Resolve items into ordered, sized tiles. With no persisted entries this is the
 * built-in default — first item `LARGE`, the rest `MEDIUM` (today's
 * `featured=idx0`). With entries, order by `displayOrder` (items absent from the
 * layout — e.g. newly created since the layout was saved — keep their natural
 * order at the end) and size by the persisted emphasis (absent → `MEDIUM`).
 */
export function resolveBentoLayout<T>(
  items: T[],
  itemKey: (item: T) => string,
  entries: BentoLayoutEntry[] | undefined,
): ResolvedBentoItem<T>[] {
  if (!entries || entries.length === 0) {
    return items.map((item, idx) => ({
      item,
      key: itemKey(item),
      emphasis: idx === 0 ? "LARGE" : "MEDIUM",
    }));
  }

  const orderMap = new Map<string, number>();
  const emphasisMap = new Map<string, BentoEmphasis>();
  for (const entry of entries) {
    orderMap.set(entry.itemId, entry.displayOrder);
    emphasisMap.set(entry.itemId, entry.emphasis);
  }

  return items
    .map((item, idx) => ({ item, key: itemKey(item), idx }))
    .sort((a, b) => {
      const oa = orderMap.has(a.key) ? (orderMap.get(a.key) as number) : Number.MAX_SAFE_INTEGER;
      const ob = orderMap.has(b.key) ? (orderMap.get(b.key) as number) : Number.MAX_SAFE_INTEGER;
      return oa !== ob ? oa - ob : a.idx - b.idx;
    })
    .map(({ item, key }) => ({ item, key, emphasis: emphasisMap.get(key) ?? "MEDIUM" }));
}

/** Serialize a resolved list back into persistable entries (order = array index). */
export function toBentoLayoutEntries<T>(resolved: ResolvedBentoItem<T>[]): BentoLayoutEntry[] {
  return resolved.map((r, idx) => ({ itemId: r.key, emphasis: r.emphasis, displayOrder: idx }));
}
