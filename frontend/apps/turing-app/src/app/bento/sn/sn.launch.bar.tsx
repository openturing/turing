import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from "@/components/ui/dropdown-menu";
import {
  buildSnSearchLaunchers,
  type SnLauncher,
} from "@/components/sn/sn-search-launchers";
import type { TurSNSite } from "@/models/sn/sn-site.model.ts";
import type { Icon as TablerIcon } from "@tabler/icons-react";
import { IconAtom, IconChevronDown, IconExternalLink, IconSearch } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

const TILE = "bento-tile bento-tile-clickable bento-glass inline-flex items-center gap-2.5 rounded-2xl px-4 py-2.5 text-sm font-medium";

function LauncherTile({
  launcher,
  icon: Icon,
  gradient,
}: Readonly<{ launcher: SnLauncher; icon: TablerIcon; gradient: string }>) {
  const chip = (
    <span className={`grid h-8 w-8 shrink-0 place-items-center rounded-xl bg-linear-to-br ${gradient} text-white shadow-sm`}>
      <Icon size={16} />
    </span>
  );

  // Single locale → a plain external link. Multi-locale → a dropdown whose
  // first item is the default (all-locales) URL, then one item per locale.
  if (launcher.items.length === 0) {
    return (
      <a href={launcher.url} target="_blank" rel="noopener noreferrer" className={TILE}>
        {chip}
        {launcher.label}
        <IconExternalLink size={14} className="text-muted-foreground" />
      </a>
    );
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger className={TILE}>
        {chip}
        {launcher.label}
        <IconChevronDown size={14} className="text-muted-foreground" />
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start">
        <DropdownMenuItem asChild>
          <a href={launcher.url} target="_blank" rel="noopener noreferrer">
            <IconExternalLink className="mr-2 size-4" />
            {launcher.label}
          </a>
        </DropdownMenuItem>
        {launcher.items.map((item) => (
          <DropdownMenuItem key={item.url} asChild>
            <a href={item.url} target="_blank" rel="noopener noreferrer">
              {item.label}
            </a>
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

/**
 * Bento SN "launch" bar — the visitor-facing search launchers the console grid
 * cards carry (Open Search + ANN Search), surfaced on the bento detail page
 * where "Configure" is redundant (the page itself is the config). External
 * links open in a new tab; multi-locale sites get a per-locale dropdown.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export function SnLaunchBar({ site }: Readonly<{ site: TurSNSite }>) {
  const { t } = useTranslation();
  const { search, ann } = buildSnSearchLaunchers(
    {
      name: site.name,
      genAiEnabled: Boolean(site.turSNSiteGenAi?.turAIAgent),
      locales: site.turSNSiteLocales ?? [],
    },
    (key) => t(key),
  );

  return (
    <section>
      <h2 className="mb-3 flex items-center gap-2 text-sm font-medium text-muted-foreground">
        <IconSearch size={16} className="text-emerald-500" />
        {t("sn.launch.title", { defaultValue: "Live search" })}
      </h2>
      <div className="flex flex-wrap gap-3">
        <LauncherTile launcher={search} icon={IconSearch} gradient="from-emerald-500 to-teal-600" />
        {ann && <LauncherTile launcher={ann} icon={IconAtom} gradient="from-indigo-600 to-fuchsia-600" />}
      </div>
    </section>
  );
}
