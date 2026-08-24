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
 */
export default function ConsoleToBentoRedirect() {
  const { pathname, search, hash } = useLocation();
  const base = ROUTES.CONSOLE;
  const rawSuffix = pathname.startsWith(base) ? pathname.slice(base.length) : pathname;
  const suffix = rawSuffix.replace(/^\/+/, "").replace(/\/+$/, "");
  return <Navigate to={`${consoleSuffixToBento(suffix)}${search}${hash}`} replace />;
}
