import { BentoSaveBar, type BentoSaveBarProps } from "./bento-save-bar";

/**
 * A {@link BentoSaveBar} wrapped in the standard sticky positioning for Bento
 * form pages that are **not** built on {@link BentoEntityShell} (which owns its
 * own scroll-linked fixed save bar).
 *
 * It pins the bar at `top-20` — directly below the `sticky top-0 z-30` shell
 * header in `bento.root.page`. This is the crucial bit: a `top-0` bar sticks
 * to the very top of the viewport and slides *behind* that header (the header
 * has a higher z-index and an opaque frosted background), so on a long form the
 * Save/Cancel controls become invisible and un-clickable the moment the user
 * scrolls. Pinning at `top-20` clears the header, so the bar stays visible and
 * reachable for the entire scroll of the form.
 *
 * All {@link BentoSaveBarProps} pass straight through, and the built-in Save
 * button remains `type="submit"` — so this must be rendered inside the `<form>`
 * it submits (same as before).
 */
export function BentoStickySaveBar(props: Readonly<BentoSaveBarProps>) {
  return (
    <div className="sticky top-20 z-20 mb-1">
      <BentoSaveBar {...props} />
    </div>
  );
}
