import { ROUTES } from "@/app/routes.const";
import { BentoBackToTop, BentoCommandPalette, BentoNavRail, BentoUserMenu } from "@/components/bento";
import { TurLogo } from "@/components/logo/tur-logo";
import { ModeToggle } from "@/components/mode-toggle";
import { UserProvider } from "@/contexts/user.context";
import { IconArrowLeft, IconSearch } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, Outlet, useLocation } from "react-router-dom";

/**
 * Resolve the parent route for the current bento path.
 *
 * iOS-style "up" navigation: the back button is anchored to the route
 * hierarchy, not to the browser history. That way a deep link or a
 * redirect-into-bento still goes somewhere predictable when the user
 * presses back, instead of bouncing them outside the bento section.
 *
 * Returns `null` when there's no in-app parent (we're already at bento
 * home) — the caller hides the back button in that case.
 */
function resolveBentoParent(pathname: string): string | null {
  if (pathname.startsWith(`${ROUTES.BENTO_AI_AGENT_INSTANCE}/`)) {
    return ROUTES.BENTO_AI_AGENT_INSTANCE;
  }
  if (pathname === ROUTES.BENTO_AI_AGENT_INSTANCE
      || pathname === `${ROUTES.BENTO_AI_AGENT_INSTANCE}/`) {
    return ROUTES.BENTO_HOME;
  }
  if (pathname.startsWith(`${ROUTES.BENTO_LLM_INSTANCE}/`)) {
    return ROUTES.BENTO_LLM_INSTANCE;
  }
  if (pathname === ROUTES.BENTO_LLM_INSTANCE
      || pathname === `${ROUTES.BENTO_LLM_INSTANCE}/`) {
    return ROUTES.BENTO_HOME;
  }
  // /bento/home and /bento — top of the bento tree, no parent.
  return null;
}

export default function BentoRootPage() {
  const { pathname } = useLocation();
  const { t } = useTranslation();
  const parentPath = resolveBentoParent(pathname);
  const [paletteOpen, setPaletteOpen] = useState(false);

  /*
   * Global ⌘K / Ctrl+K opens the command palette (T549). Registered once
   * at the shell root so it works from any bento screen. Ignores the combo
   * while typing in an input/textarea/contenteditable so it never hijacks
   * a keystroke the user meant for a field.
   */
  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "k") {
        const target = event.target as HTMLElement | null;
        const tag = target?.tagName;
        if (tag === "INPUT" || tag === "TEXTAREA" || target?.isContentEditable) return;
        event.preventDefault();
        setPaletteOpen((prev) => !prev);
      }
    }
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, []);

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
      <div className="relative min-h-svh w-full bg-background text-foreground md:pl-16">
        <AmbientBackground />

        <BentoNavRail />

        <header className="bento-shell-header sticky top-0 z-30 flex items-center justify-between gap-3 border-b border-border/40 bg-background/55 px-4 py-3 backdrop-blur-xl backdrop-saturate-150 md:px-8">
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
              <span className="rounded-full bg-muted px-2 py-0.5 text-[10px] uppercase tracking-wider text-muted-foreground">
                Preview
              </span>
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
              aria-label={t("bento.palette.open", "Open command palette")}
              className="bento-tile bento-tile-clickable inline-flex items-center gap-2 rounded-full border border-border/60 bg-card/60 py-1.5 pl-3 pr-2 text-sm text-muted-foreground backdrop-blur hover:text-foreground"
            >
              <IconSearch size={16} />
              <span className="hidden sm:inline">{t("bento.palette.search", "Search")}</span>
              <kbd className="hidden rounded border border-border/60 bg-muted px-1.5 py-0.5 text-[10px] font-medium tracking-wide sm:inline">
                ⌘K
              </kbd>
            </button>
            <ModeToggle />
            <BentoUserMenu />
          </div>
        </header>

        <main className="relative z-10 mx-auto w-full max-w-7xl px-4 py-6 md:px-8 md:py-10">
          <Outlet />
        </main>

        <BentoBackToTop />
        <BentoCommandPalette open={paletteOpen} onOpenChange={setPaletteOpen} />
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
