import { useEffect, type RefObject } from "react";

/**
 * Drives the shared Bento "hero → sticky save bar" scroll morph.
 *
 * Each scroll frame it computes a 0..1 progress from the sentinel's viewport
 * position and writes it as `--bento-fade` on `<html>`, toggling
 * `bento-fade-half` past the midpoint. CSS inheritance then distributes it:
 * hero-anchored controls (`bento-fade-out`) fade away and the fixed save bar
 * (`bento-fade-in`) fades in, with zero React re-renders during scroll.
 *
 *   - sentinel at 160px (STICKY_OFFSET + REVEAL_RANGE) → progress 0
 *   - sentinel at  80px (STICKY_OFFSET)               → progress 1
 *
 * Extracted verbatim from {@link BentoEntityShell} so standalone form pages
 * (which render their own hero instead of using the shell) can reuse the exact
 * same mechanic via {@link BentoScrollSaveBar}. Pass a ref to a 1px sentinel
 * rendered immediately after the hero.
 */
export function useBentoScrollFade(sentinelRef: RefObject<HTMLElement | null>) {
  useEffect(() => {
    const node = sentinelRef.current;
    if (!node) return;

    const STICKY_OFFSET = 80;
    const REVEAL_RANGE = 80;
    const startTop = STICKY_OFFSET + REVEAL_RANGE;
    const endTop = STICKY_OFFSET;
    const root = document.documentElement;

    let raf = 0;
    const update = () => {
      raf = 0;
      const top = node.getBoundingClientRect().top;
      const progress = Math.max(0, Math.min(1, (startTop - top) / (startTop - endTop)));
      root.style.setProperty("--bento-fade", String(progress));
      root.classList.toggle("bento-fade-half", progress > 0.5);
    };

    const onScroll = () => {
      if (!raf) raf = requestAnimationFrame(update);
    };

    update();
    window.addEventListener("scroll", onScroll, { passive: true });
    window.addEventListener("resize", onScroll);
    return () => {
      if (raf) cancelAnimationFrame(raf);
      window.removeEventListener("scroll", onScroll);
      window.removeEventListener("resize", onScroll);
      // Reset on unmount so the variable doesn't leak into other pages.
      root.style.removeProperty("--bento-fade");
      root.classList.remove("bento-fade-half");
    };
  }, [sentinelRef]);
}
