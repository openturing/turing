import { ROUTES } from "@/app/routes.const";
import {
  BENTO_NAV_ITEMS,
  BENTO_NAV_SECTIONS,
  BentoBackToTop,
  BentoCommandPalette,
  BentoFirstRunTour,
  BentoNavRail,
  BentoShortcutsDialog,
  BentoUserMenu,
  bentoSectionAreaRoute,
  hasSeenBentoTour,
} from "@/components/bento";
import { TurLogo } from "@/components/logo/tur-logo";
import { ModeToggle } from "@/components/mode-toggle";
import { UserProvider } from "@/contexts/user.context";
import { LanguageSwitcher } from "@viglet/viglet-design-system";
import { IconArrowLeft, IconSearch } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, Outlet, useLocation } from "react-router-dom";

/**
 * The command-palette shortcut hint, honest per-platform: macOS uses ⌘, every
 * other platform uses Ctrl (the handler already accepts both `metaKey` and
 * `ctrlKey`). Showing a bare `⌘K` on Windows/Linux misleads users into
 * pressing the wrong key.
 */
const IS_MAC =
  typeof navigator !== "undefined" &&
  /Mac|iPhone|iPad|iPod/.test(navigator.platform || navigator.userAgent || "");
const SHORTCUT_HINT = IS_MAC ? "⌘K" : "Ctrl K";

/**
 * Resolve the parent route for the current bento path (config-driven, T573).
 *
 * iOS-style "up" navigation: the back button is anchored to the route
 * hierarchy, not to the browser history. That way a deep link or a
 * redirect-into-bento still goes somewhere predictable when the user
 * presses back, instead of bouncing them outside the bento section.
 *
 * The hierarchy is home → area hub → leaf list → leaf detail:
 *  - a leaf **detail** (under a migrated `bentoRoute`) goes up to its list,
 *  - a leaf **list** (exactly a `bentoRoute`) goes up to its section hub,
 *  - an **area hub** goes up to home,
 *  - home / `/bento` have no parent (returns `null`, hiding the button).
 */
function resolveBentoParent(pathname: string): string | null {
  const path = pathname.replace(/\/$/, "");

  // Top of the bento tree — no parent.
  if (path === ROUTES.BENTO_HOME || path === ROUTES.BENTO_ROOT) return null;

  // Area hubs sit directly under home.
  if (BENTO_NAV_SECTIONS.some((s) => s.areaRoute === path)) {
    return ROUTES.BENTO_HOME;
  }

  // Migrated leaf surfaces (skip `home`, which has bentoRoute === BENTO_HOME).
  for (const item of BENTO_NAV_ITEMS) {
    if (!item.bentoRoute || item.bentoRoute === ROUTES.BENTO_HOME) continue;
    if (path === item.bentoRoute) {
      return bentoSectionAreaRoute(item) ?? ROUTES.BENTO_HOME;
    }
    if (path.startsWith(`${item.bentoRoute}/`)) {
      return item.bentoRoute;
    }
  }

  return null;
}

export default function BentoRootPage() {
  const { pathname } = useLocation();
  const { t } = useTranslation();
  const parentPath = resolveBentoParent(pathname);
  /*
   * "Immersive" app-like surfaces (the chat, the GraphQL explorer) fill the
   * shell edge-to-edge instead of being centered in the `max-w-7xl` reading
   * column — they own a viewport-height layout with their own internal scroll,
   * so the extra width and height are used by the tool (conversation / GraphiQL
   * iframe) rather than left as empty margins.
   */
  const isImmersive =
    pathname === ROUTES.BENTO_CHAT ||
    pathname.startsWith(`${ROUTES.BENTO_CHAT}/`) ||
    pathname === ROUTES.BENTO_GRAPHQL;
  const [paletteOpen, setPaletteOpen] = useState(false);
  const [shortcutsOpen, setShortcutsOpen] = useState(false);
  const [tourOpen, setTourOpen] = useState(false);

  /*
   * Global shell shortcuts, registered once at the root so they work from any
   * bento screen (T549 palette + T572 shortcut guide). Both ignore the key
   * while the user is typing in an input/textarea/contenteditable so they
   * never hijack a keystroke meant for a field.
   *
   *   ⌘K / Ctrl+K → toggle the command palette
   *   ?           → open the keyboard-shortcut guide
   */
  useEffect(() => {
    function isTyping(target: EventTarget | null): boolean {
      const el = target as HTMLElement | null;
      const tag = el?.tagName;
      return tag === "INPUT" || tag === "TEXTAREA" || Boolean(el?.isContentEditable);
    }
    function onKeyDown(event: KeyboardEvent) {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "k") {
        if (isTyping(event.target)) return;
        event.preventDefault();
        setPaletteOpen((prev) => !prev);
      } else if (event.key === "?" && !event.metaKey && !event.ctrlKey && !event.altKey) {
        if (isTyping(event.target)) return;
        event.preventDefault();
        setShortcutsOpen((prev) => !prev);
      }
    }
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, []);

  /*
   * First-run tour (T572): shown once, only when landing on the bento home, and
   * only if the user hasn't seen (or skipped) it. The tour itself persists the
   * "seen" flag on dismiss; here we just decide whether to raise it. Deferred a
   * tick so it doesn't fight the shell's own entrance animation.
   */
  useEffect(() => {
    if (pathname !== ROUTES.BENTO_HOME && pathname !== ROUTES.BENTO_ROOT) return;
    if (hasSeenBentoTour()) return;
    const id = window.setTimeout(() => setTourOpen(true), 400);
    return () => window.clearTimeout(id);
  }, [pathname]);

  return (
    <UserProvider>
      {/*
       * No `overflow-hidden` on this wrapper — that turns it into a
       * scroll container which prevents `position: sticky` on the
       * header from latching onto the document scroll. The ambient
       * blobs are clipped inside their own `fixed`-positioned layer
       * (see AmbientBackground) instead.
       *
       * `md:pl-16` reserves the left gutter for the fixed BentoNavRail so
       * the header + main clear it on desktop.
       */}
      <div
        className={`relative w-full bg-background text-foreground md:pl-16 ${
          isImmersive ? "flex h-svh flex-col overflow-hidden" : "min-h-svh"
        }`}
      >
        {/*
         * Skip-to-content link (a11y / T571): the first focusable element so a
         * keyboard user can jump past the nav rail + header straight to the
         * page body. Visually hidden until focused.
         */}
        <a
          href="#bento-main-content"
          className="sr-only rounded-full focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:border focus:border-border/60 focus:bg-card focus:px-4 focus:py-2 focus:text-sm focus:shadow-lg"
        >
          {t("bento.skipToContent", { defaultValue: "Skip to content" })}
        </a>
        <AmbientBackground />

        <BentoNavRail />

        <header className="bento-shell-header sticky top-0 z-30 flex shrink-0 items-center justify-between gap-3 border-b border-border/40 bg-background/55 px-4 py-3 backdrop-blur-xl backdrop-saturate-150 md:px-8">
          <div className="flex items-center gap-3">
            {parentPath && (
              <Link
                to={parentPath}
                aria-label="Back"
                className="bento-tile bento-tile-clickable inline-flex h-9 w-9 items-center justify-center rounded-full border border-border/60 bg-card/60 backdrop-blur"
              >
                <IconArrowLeft size={18} />
              </Link>
            )}
            <Link to={ROUTES.BENTO_HOME} className="flex items-center gap-2">
              <TurLogo className="size-8" />
              <span className="text-sm font-medium tracking-tight md:text-base">Turing ES</span>
            </Link>
          </div>
          <div className="flex items-center gap-2">
            {/*
             * Command-palette trigger. On desktop it doubles as a hint that
             * ⌘K exists; on mobile (where the rail is hidden) it's the main
             * way to reach deep areas.
             */}
            <button
              type="button"
              onClick={() => setPaletteOpen(true)}
              aria-label={t("bento.palette.open", { defaultValue: "Open command palette" })}
              className="bento-tile bento-tile-clickable inline-flex items-center gap-2 rounded-full border border-border/60 bg-card/60 py-1.5 pl-3 pr-2 text-sm text-muted-foreground backdrop-blur hover:text-foreground"
            >
              <IconSearch size={16} />
              <span className="hidden sm:inline">{t("bento.palette.search", { defaultValue: "Search" })}</span>
              <kbd className="hidden rounded border border-border/60 bg-muted px-1.5 py-0.5 text-[10px] font-medium tracking-wide sm:inline">
                {SHORTCUT_HINT}
              </kbd>
            </button>
            <LanguageSwitcher />
            <ModeToggle />
            <BentoUserMenu
              onOpenShortcuts={() => setShortcutsOpen(true)}
              onReplayTour={() => setTourOpen(true)}
            />
          </div>
        </header>

        <main
          id="bento-main-content"
          tabIndex={-1}
          aria-label={t("bento.mainContent", { defaultValue: "Main content" })}
          className={
            isImmersive
              ? "relative z-10 flex min-h-0 w-full flex-1 flex-col px-4 pt-6 focus:outline-none md:px-8 md:pt-8"
              : "relative z-10 mx-auto w-full max-w-7xl px-4 py-6 focus:outline-none md:px-8 md:py-10"
          }
        >
          <Outlet />
        </main>

        <BentoBackToTop />
        <BentoCommandPalette open={paletteOpen} onOpenChange={setPaletteOpen} />
        <BentoShortcutsDialog open={shortcutsOpen} onOpenChange={setShortcutsOpen} isMac={IS_MAC} />
        <BentoFirstRunTour open={tourOpen} onOpenChange={setTourOpen} />
      </div>
    </UserProvider>
  );
}

/**
 * Soft, slowly-drifting gradient blobs. Pure CSS — no JS, no canvas.
 * Provides depth behind the frosted bento tiles.
 *
 * Positioned `fixed` so the ambience stays anchored to the viewport
 * (Apple-style — wallpapers don't scroll with content) and the blobs
 * are clipped naturally to viewport bounds without forcing
 * `overflow-hidden` on the page wrapper.
 */
function AmbientBackground() {
  return (
    <div aria-hidden className="pointer-events-none fixed inset-0 z-0 overflow-hidden">
      <div className="bento-blob absolute -left-32 -top-32 h-96 w-96 rounded-full bg-linear-to-br from-blue-500/30 to-indigo-500/20 blur-3xl" />
      <div className="bento-blob-2 absolute -right-24 top-1/3 h-112 w-md rounded-full bg-linear-to-tr from-indigo-500/25 to-fuchsia-500/15 blur-3xl" />
      <div className="bento-blob-3 absolute -bottom-32 left-1/3 h-96 w-96 rounded-full bg-linear-to-tr from-emerald-400/15 to-teal-500/10 blur-3xl" />
    </div>
  );
}
