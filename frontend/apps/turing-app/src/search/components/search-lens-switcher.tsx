import { IconAtom, IconSearch, IconSparkles } from "@tabler/icons-react";
import type { Icon as TablerIcon } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { lensHref, type LensQuery, type SearchLens } from "../lib/search-lens";

/**
 * T855 / §LXIV.4 (Block BQ) — the cross-lens switcher: one question, replayed as
 * keyword (`/sn`), vector (`/ann`) and answer (`/answer`), so a wrong answer is
 * attributed to **recall**, **rerank** or **synthesis** in one pass instead of
 * three hand-edited URLs.
 *
 * <p>The two operator lenses carry it unconditionally. The public `/sn` page shows
 * it only behind the T891 `_lens=1` opt-in, so a visitor's page is unchanged —
 * and the keyword link below carries that param, which is what keeps the round
 * trip open once an operator is mid-comparison.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */

const LENSES: ReadonlyArray<{ lens: SearchLens; icon: TablerIcon; labelKey: string }> = [
  { lens: "keyword", icon: IconSearch, labelKey: "answer.lens.keyword" },
  { lens: "vector", icon: IconAtom, labelKey: "answer.lens.vector" },
  { lens: "answer", icon: IconSparkles, labelKey: "answer.lens.answer" },
];

export interface SearchLensSwitcherProps {
  /** The lens the operator is looking at — rendered inert and highlighted. */
  current: SearchLens;
  siteName: string;
  /** The question to carry across. */
  query: LensQuery;
}

export function SearchLensSwitcher({
  current,
  siteName,
  query,
}: Readonly<SearchLensSwitcherProps>) {
  const { t } = useTranslation();

  return (
    <nav aria-label={t("answer.lens.title")} className="flex items-center gap-1">
      {LENSES.map(({ lens, icon: Icon, labelKey }) => {
        const active = lens === current;
        const label = t(labelKey);
        const className =
          "inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs transition-colors " +
          (active
            ? "border-blue-500/40 bg-blue-500/10 text-blue-700 dark:text-blue-300"
            : "border-border/60 text-muted-foreground hover:border-blue-500/30 hover:bg-blue-500/5");

        if (active) {
          return (
            <span key={lens} className={className} aria-current="page">
              <Icon className="size-3.5" />
              {label}
            </span>
          );
        }
        return (
          <a
            key={lens}
            href={lensHref(lens, siteName, query, { switcher: true })}
            className={className}
          >
            <Icon className="size-3.5" />
            {label}
          </a>
        );
      })}
    </nav>
  );
}
