import { BadgeLocale } from "@/components/badge-locale";
import { TurLogo } from "@/components/logo/tur-logo";
import { ModeToggle } from "@/components/mode-toggle";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import type { SearchStoreValue, TurLocaleItem } from "@viglet/turing-react-sdk";
import { Icon } from "@iconify/react";
import { IconSearch } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

interface SearchHeaderProps {
  siteName: string;
  siteIcon?: string | null;
  store: SearchStoreValue;
  locale: string;
  locales: TurLocaleItem[];
  suggestions: string[];
  onInputChange: (value: string) => void;
  onSuggestionPick: (term: string) => void;
  onSuggestionsClose: () => void;
}

export function SearchHeader({
  siteName,
  siteIcon,
  store,
  locale,
  locales,
  suggestions,
  onInputChange,
  onSuggestionPick,
  onSuggestionsClose,
}: Readonly<SearchHeaderProps>) {
  const { t } = useTranslation();

  return (
    <header className="sticky top-0 z-40 border-b border-border/50 bg-background/80 backdrop-blur-xl">
      <div className="container mx-auto px-4 lg:px-8">
        <div className="flex items-center gap-4 h-16">
          {/* Brand */}
          <button
            type="button"
            onClick={store.showAll}
            className="flex items-center gap-2.5 shrink-0 cursor-pointer group"
          >
            {siteIcon ? (
              <Icon icon={siteIcon} className="size-7 text-blue-600 dark:text-blue-400 transition-transform group-hover:scale-110" />
            ) : (
              <TurLogo size={28} className="transition-transform group-hover:scale-110" />
            )}
            <span className="text-base font-bold tracking-tight bg-gradient-to-r from-blue-600 to-indigo-600 bg-clip-text text-transparent dark:from-blue-400 dark:to-indigo-400">
              {siteName}
            </span>
          </button>

          {/* Search Bar */}
          <div className="flex-1 relative max-w-2xl">
            <div className="relative group">
              <div className="absolute -inset-0.5 bg-gradient-to-r from-blue-600/20 to-indigo-600/20 rounded-lg opacity-0 group-focus-within:opacity-100 transition-opacity blur-sm" />
              <div className="relative flex items-center">
                <IconSearch className="absolute left-3 size-4 text-muted-foreground pointer-events-none" />
                <input
                  value={store.inputValue}
                  onChange={(e) => onInputChange(e.target.value)}
                  onKeyDown={(e) => e.key === "Enter" && store.submitSearch()}
                  onBlur={() => globalThis.setTimeout(() => onSuggestionsClose(), 150)}
                  type="search"
                  className="w-full h-9 pl-9 pr-4 rounded-lg border border-border bg-muted/50 text-sm placeholder:text-muted-foreground/60 focus:outline-none focus:ring-2 focus:ring-blue-500/30 focus:border-blue-500/50 transition-all"
                  placeholder={t("search.placeholder")}
                  autoComplete="off"
                />
              </div>
            </div>

            {/* Autocomplete Dropdown */}
            {suggestions.length > 0 && (
              <div className="absolute z-50 mt-1.5 w-full rounded-lg border border-border bg-popover shadow-xl shadow-black/10 overflow-hidden">
                {suggestions.map((term) => (
                  <button
                    key={term}
                    type="button"
                    className="w-full px-4 py-2.5 text-left text-sm hover:bg-accent transition-colors flex items-center gap-2 cursor-pointer"
                    onMouseDown={(e) => e.preventDefault()}
                    onClick={() => onSuggestionPick(term)}
                  >
                    <IconSearch className="size-3.5 text-muted-foreground shrink-0" />
                    {term}
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* Theme Toggle */}
          <ModeToggle />

          {/* Locale Selector */}
          {locales.length > 1 && (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="ghost" size="sm" className="gap-2 h-9 px-2">
                  <BadgeLocale locale={locale} />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                {locales.map((l) => (
                  <DropdownMenuItem key={l.locale} onClick={() => store.setLocale(l.locale)} className="gap-2">
                    <BadgeLocale locale={l.locale} />
                  </DropdownMenuItem>
                ))}
              </DropdownMenuContent>
            </DropdownMenu>
          )}
        </div>
      </div>
    </header>
  );
}
