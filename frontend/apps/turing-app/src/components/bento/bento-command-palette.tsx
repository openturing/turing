import { Dialog, DialogContent, DialogDescription, DialogTitle } from "@/components/ui/dialog";
import { IconArrowRight, IconExternalLink, IconSearch } from "@tabler/icons-react";
import { useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { bentoNavTarget, useVisibleBentoNav, type BentoNavItem } from "./bento-nav.config";

export interface BentoCommandPaletteProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * The Bento `⌘K` command palette (T549) — a keyboard-first launcher over
 * every navigable area. Built on the Radix `Dialog` primitive (zero extra
 * deps): a filter box + a flat, arrow-key-navigable result list. Migrated
 * surfaces navigate inside the bento shell; not-yet-migrated areas link out
 * to the console (marked with an external-link glyph) so the palette reaches
 * everything during the migration.
 *
 * Global open/close (⌘K / Ctrl+K) is owned by the parent shell; this
 * component only renders and drives selection while `open`.
 */
export function BentoCommandPalette({ open, onOpenChange }: Readonly<BentoCommandPaletteProps>) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const items = useVisibleBentoNav();
  const [query, setQuery] = useState("");
  const [activeIndex, setActiveIndex] = useState(0);
  const listRef = useRef<HTMLDivElement>(null);

  const results = useMemo(() => {
    const q = query.trim().toLowerCase();
    const labelled = items.map((item) => ({ item, label: t(item.titleKey) }));
    if (!q) return labelled;
    return labelled.filter(({ label }) => label.toLowerCase().includes(q));
  }, [items, query, t]);

  // Reset transient state whenever the palette (re)opens.
  useEffect(() => {
    if (open) {
      setQuery("");
      setActiveIndex(0);
    }
  }, [open]);

  // Keep the active row within bounds as the filtered list shrinks/grows.
  useEffect(() => {
    setActiveIndex((prev) => (results.length === 0 ? 0 : Math.min(prev, results.length - 1)));
  }, [results.length]);

  // Scroll the active row into view on keyboard navigation.
  useEffect(() => {
    const node = listRef.current?.querySelector<HTMLElement>(`[data-index="${activeIndex}"]`);
    node?.scrollIntoView({ block: "nearest" });
  }, [activeIndex]);

  function go(item: BentoNavItem) {
    onOpenChange(false);
    navigate(bentoNavTarget(item));
  }

  function onKeyDown(event: React.KeyboardEvent) {
    if (event.key === "ArrowDown") {
      event.preventDefault();
      setActiveIndex((prev) => (results.length === 0 ? 0 : (prev + 1) % results.length));
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      setActiveIndex((prev) => (results.length === 0 ? 0 : (prev - 1 + results.length) % results.length));
    } else if (event.key === "Enter") {
      event.preventDefault();
      const hit = results[activeIndex];
      if (hit) go(hit.item);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        showCloseButton={false}
        className="top-[15%] max-w-xl translate-y-0 gap-0 overflow-hidden p-0"
        onKeyDown={onKeyDown}
      >
        <DialogTitle className="sr-only">{t("bento.palette.title", { defaultValue: "Command palette" })}</DialogTitle>
        <DialogDescription className="sr-only">
          {t("bento.palette.description", { defaultValue: "Search and jump to any area" })}
        </DialogDescription>

        <div className="flex items-center gap-3 border-b border-border/60 px-4">
          <IconSearch size={18} className="shrink-0 text-muted-foreground" />
          {/* eslint-disable-next-line jsx-a11y/no-autofocus -- palette is a keyboard-first launcher; focusing the input on open is the expected behavior */}
          <input
            autoFocus
            type="text"
            role="combobox"
            aria-expanded="true"
            aria-controls="bento-palette-list"
            aria-activedescendant={results[activeIndex] ? `bento-palette-opt-${activeIndex}` : undefined}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={t("bento.palette.placeholder", { defaultValue: "Search areas…" })}
            className="h-12 w-full bg-transparent text-sm outline-none placeholder:text-muted-foreground"
          />
        </div>

        <div
          ref={listRef}
          id="bento-palette-list"
          role="listbox"
          aria-label={t("bento.palette.title", "Command palette")}
          className="max-h-80 overflow-y-auto p-2"
        >
          {results.length === 0 && (
            <p className="px-3 py-6 text-center text-sm text-muted-foreground">
              {t("bento.palette.noResults", { defaultValue: "No matches" })}
            </p>
          )}
          {results.map(({ item, label }, index) => {
            const external = !item.bentoRoute;
            const active = index === activeIndex;
            const Icon = item.icon;
            return (
              <button
                key={item.id}
                type="button"
                id={`bento-palette-opt-${index}`}
                data-index={index}
                role="option"
                aria-selected={active ? "true" : "false"}
                onMouseEnter={() => setActiveIndex(index)}
                onClick={() => go(item)}
                className={`flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left text-sm transition-colors ${
                  active ? "bg-accent text-accent-foreground" : "text-foreground"
                }`}
              >
                <Icon size={18} className="shrink-0 text-muted-foreground" />
                <span className="flex-1 truncate">{label}</span>
                {external ? (
                  <>
                    <IconExternalLink aria-hidden size={15} className="shrink-0 text-muted-foreground/70" />
                    <span className="sr-only">{t("bento.palette.opensConsole", { defaultValue: "Opens in console" })}</span>
                  </>
                ) : (
                  active && <IconArrowRight aria-hidden size={15} className="shrink-0 text-muted-foreground" />
                )}
              </button>
            );
          })}
        </div>
      </DialogContent>
    </Dialog>
  );
}
