// T990 -- adapter, not a copy.
//
// viglet-ds-allow-duplicate BentoListPage
// viglet-ds-allow-duplicate BentoListPageProps
// VDS74 -- the gate used to skip this whole file for containing a re-export,
// which is the hole it closed. Both names are re-declared on purpose and the
// note below says why; the markers are what makes that deliberate rather than
// merely unreported.
//
// The other compounds in this block became one-line shims. This one cannot,
// and the reason is the whole point of moving it: the package's BentoListPage
// does not fetch the layout. Where a layout is stored is the product's
// business -- its API, its cache, its mutation library -- so the component
// takes a `layout` object and the product fills it in.
//
// The copy that stood here called useBentoLayout and the three mutations
// directly. Shimming it raw would have type-checked and silently dropped the
// Customize affordance on all 16 surfaces that pass a listId, because the
// package hides it when nothing was passed to write to. So this module keeps
// its path and its name and wires this product's hooks to the shared
// component. The 118 pages still import BentoListPage from here and see the
// same props they always did.
import {
  useBentoLayout,
  useResetBentoLayout,
  useSaveBentoLayout,
  useSaveBentoLayoutGlobal,
} from "@/api/queries/bento-layout.queries";
import {
  BentoListPage as SharedBentoListPage,
  type BentoLayoutEntry,
  type BentoListPageProps as SharedBentoListPageProps,
} from "@viglet/viglet-design-system/bento";

export {
  BentoEntityTile,
  type BentoEntityTileProps,
  BentoTileGrid,
  type BentoTileGridProps,
} from "@viglet/viglet-design-system/bento";

/** The shared props minus `layout`, which this module supplies. */
export type BentoListPageProps<T> = Omit<SharedBentoListPageProps<T>, "layout">;

export function BentoListPage<T>(props: Readonly<BentoListPageProps<T>>) {
  const { listId } = props;

  const { data } = useBentoLayout(listId);
  const saveUser = useSaveBentoLayout();
  const saveGlobal = useSaveBentoLayoutGlobal();
  const reset = useResetBentoLayout();

  // No listId means no surface to persist against, and the package reads a
  // missing `layout` as exactly that: render the default order, no affordance.
  if (!listId) return <SharedBentoListPage<T> {...props} />;

  return (
    <SharedBentoListPage<T>
      {...props}
      layout={{
        data,
        onSave: (entries: BentoLayoutEntry[]) => saveUser.mutateAsync({ listId, entries }),
        // Only an admin may write everyone's default; the package hides the
        // control when the callback is absent, which is the same rule the
        // local copy expressed as `canEditGlobal && (...)`.
        onSaveGlobal: data?.canEditGlobal
          ? (entries: BentoLayoutEntry[]) => saveGlobal.mutateAsync({ listId, entries })
          : undefined,
        onReset: () => reset.mutateAsync(listId),
        saving: saveUser.isPending || saveGlobal.isPending || reset.isPending,
      }}
    />
  );
}
