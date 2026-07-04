import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { Link, useLocation } from "react-router-dom";
import { BENTO_NAV_SECTIONS, useVisibleBentoNav, type BentoNavItem } from "./bento-nav.config";

/** A migrated route is active when it (or a child of it) is the current path. */
function isActive(pathname: string, route: string): boolean {
  return pathname === route || pathname.startsWith(`${route}/`);
}

/**
 * The persistent Bento navigation rail (T549) — a slim, fixed, frosted icon
 * strip on the left (desktop only) listing the migrated bento surfaces,
 * grouped by section. Each icon carries a tooltip label and `aria-current`
 * when active. Not-yet-migrated areas are intentionally absent here; they're
 * still reachable via the `⌘K` command palette until they migrate (at which
 * point filling in their `bentoRoute` graduates them onto this rail).
 *
 * Hidden below `md` — on mobile the header's command button + global `⌘K`
 * provide navigation without the rail eating horizontal space.
 */
export function BentoNavRail() {
  const { t } = useTranslation();
  const { pathname } = useLocation();
  const items = useVisibleBentoNav();

  // Only migrated surfaces appear on the rail, grouped in section order.
  const grouped = useMemo(() => {
    const migrated = items.filter((i) => i.bentoRoute);
    return BENTO_NAV_SECTIONS.map((section) => ({
      section,
      items: migrated.filter((i) => i.section === section.id),
    })).filter((g) => g.items.length > 0);
  }, [items]);

  return (
    <TooltipProvider delayDuration={200}>
      <nav
        aria-label={t("bento.nav.label", { defaultValue: "Primary" })}
        className="fixed inset-y-0 left-0 z-40 hidden w-16 flex-col items-center gap-1 border-r border-border/40 bg-background/55 pt-20 pb-4 backdrop-blur-xl backdrop-saturate-150 md:flex"
      >
        {grouped.map((group, groupIdx) => (
          <div key={group.section.id} className="flex flex-col items-center gap-1">
            {groupIdx > 0 && <span aria-hidden className="my-1 h-px w-6 bg-border/60" />}
            {group.items.map((item) => (
              <RailLink key={item.id} item={item} active={isActive(pathname, item.bentoRoute!)} label={t(item.titleKey)} />
            ))}
          </div>
        ))}
      </nav>
    </TooltipProvider>
  );
}

function RailLink({ item, active, label }: Readonly<{ item: BentoNavItem; active: boolean; label: string }>) {
  const Icon = item.icon;
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <Link
          to={item.bentoRoute!}
          aria-label={label}
          aria-current={active ? "page" : undefined}
          className={`bento-tile-clickable grid h-11 w-11 place-items-center rounded-2xl border transition-colors duration-200 ${
            active
              ? "border-indigo-500/40 bg-linear-to-br from-blue-600/15 to-indigo-600/15 text-indigo-600 dark:text-indigo-400"
              : "border-transparent text-muted-foreground hover:border-border/60 hover:bg-card/60 hover:text-foreground"
          }`}
        >
          <Icon size={20} />
        </Link>
      </TooltipTrigger>
      <TooltipContent side="right">{label}</TooltipContent>
    </Tooltip>
  );
}
