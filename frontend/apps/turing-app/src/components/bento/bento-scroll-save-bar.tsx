import { useRef } from "react";
import { BentoSaveBar, type BentoSaveBarProps } from "./bento-save-bar";
import { useBentoScrollFade } from "./bento-scroll-fade";

/**
 * The scroll-linked save bar for Bento form pages that render their own hero
 * (i.e. not built on {@link BentoEntityShell}). It reproduces the shell's
 * "hero → sticky bar" morph so every form behaves identically:
 *
 *   - At the top of the page the Save/Cancel controls live in the hero
 *     (wrap them in a `bento-fade-out` container so they fade as you scroll).
 *   - As the hero scrolls away this fixed bar fades in at `top-20`, below the
 *     shell header — so the controls appear to move from the header into a
 *     sticky bar, and nothing is shown until the title actually scrolls off.
 *
 * Self-contained: it renders its own 1px sentinel driven by
 * {@link useBentoScrollFade} (the exact mechanic extracted from
 * {@link BentoEntityShell}), the layout spacer, and a fixed `bento-fade-in`
 * {@link BentoSaveBar}. Render it immediately after the hero, inside the
 * `<form>` it submits (the default Save button is `type="submit"`). Keep the
 * destructive action in the hero copy only — never pass a delete dialog here,
 * since a controlled dialog rendered twice would open two modals at once.
 */
export function BentoScrollSaveBar(props: Readonly<BentoSaveBarProps>) {
  const sentinelRef = useRef<HTMLDivElement>(null);
  useBentoScrollFade(sentinelRef);
  return (
    <>
      <div ref={sentinelRef} aria-hidden className="h-px" />
      <div aria-hidden className="bento-save-bar-spacer" />
      <div className="bento-fade-in fixed inset-x-0 top-20 z-20 mx-auto w-full max-w-7xl px-4 md:px-8">
        <BentoSaveBar {...props} />
      </div>
    </>
  );
}
