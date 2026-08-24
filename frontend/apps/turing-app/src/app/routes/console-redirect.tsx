import { Navigate, useLocation } from "react-router-dom";
import { ROUTES } from "../routes.const";
import { consoleSuffixToBento } from "./console-suffix-to-bento";

/**
 * T1000 — this redirect is now the whole of the console.
 *
 * Until this change, 33 components took a `chrome?: "console" | "bento"` prop
 * and rendered one of two shells: a `StickyPageHeader` with a save/cancel pair,
 * or a `BentoFormHero`. Measured before deleting it: 46 call sites passed
 * `chrome="bento"` and not one passed `chrome="console"`. The console arm was
 * reachable only by omitting the prop, which no caller did — it had been
 * unrendered since T569/T570 took the console tree out of the router.
 *
 * So it was not a seam. A seam is a choice something makes; this was a second
 * implementation of every form in the product, kept compiling and read by
 * everyone who opened those files. What is left is this file: old bookmarks
 * still answer, at the cost of one lazy route.
 *
 * T1026 — the same branch survived in thirteen more files under a different
 * name: `header !== undefined` and `bentoHero !== undefined`. Fourteen mounts,
 * fourteen headers, two of them `header={null}` — which the condition read as
 * bento, because `null !== undefined`. That arm held the app's last fifteen
 * `<StickyPageHeader>`, and `components/sticky-page-header.tsx` went with it.
 * The component itself is still the design system's, where Shio reaches it.
 *
 * The lesson worth keeping is that both were found by counting call sites, not
 * by reading the components. A branch that no caller takes reads exactly like
 * one that every caller takes.
 */
export default function ConsoleToBentoRedirect() {
  const { pathname, search, hash } = useLocation();
  const base = ROUTES.CONSOLE;
  const rawSuffix = pathname.startsWith(base) ? pathname.slice(base.length) : pathname;
  const suffix = rawSuffix.replace(/^\/+/, "").replace(/\/+$/, "");
  return <Navigate to={`${consoleSuffixToBento(suffix)}${search}${hash}`} replace />;
}
