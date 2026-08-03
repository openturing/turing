import { IconSparkles } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import type { SearchSpellCheckView } from "../lib/search-view";

interface SpellCheckBannerProps {
  /**
   * T865 — the projected spell-check, `null` when the query needed no correction.
   * It carries its own actions, so this component no longer knows whether applying
   * a correction means navigating an href (legacy) or re-running the request with
   * new text (POST-native).
   */
  spellCheck: SearchSpellCheckView | null;
}

export function SpellCheckBanner({ spellCheck }: Readonly<SpellCheckBannerProps>) {
  const { t } = useTranslation();

  if (!spellCheck) return null;

  if (spellCheck.usingCorrectedText) {
    return (
      <div className="mb-5 flex items-start gap-2.5 rounded-lg border border-amber-500/20 bg-amber-500/5 px-4 py-3">
        <IconSparkles className="size-4 text-amber-500 mt-0.5 shrink-0" />
        <div className="text-sm">
          <span className="text-muted-foreground">{t("search.showingResultsFor")} </span>
          <button
            type="button"
            onClick={spellCheck.applyCorrection}
            className="font-semibold text-foreground italic hover:text-blue-600 dark:hover:text-blue-400 underline underline-offset-2 decoration-blue-500/30 cursor-pointer"
          >
            {spellCheck.correctedTextValue}
          </button>
          <span className="text-muted-foreground mx-1.5">&middot;</span>
          <span className="text-muted-foreground">{t("search.searchInsteadFor")} </span>
          <button
            type="button"
            onClick={spellCheck.applyOriginal}
            className="font-medium text-blue-600 dark:text-blue-400 hover:underline cursor-pointer"
          >
            {spellCheck.originalText}
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="mb-5 flex items-center gap-2.5 rounded-lg border border-amber-500/20 bg-amber-500/5 px-4 py-3">
      <IconSparkles className="size-4 text-amber-500 shrink-0" />
      <p className="text-sm">
        <span className="text-muted-foreground">{t("search.didYouMean")} </span>
        <button
          type="button"
          onClick={spellCheck.applyCorrection}
          className="font-semibold text-foreground italic hover:text-blue-600 dark:hover:text-blue-400 underline underline-offset-2 decoration-blue-500/30 cursor-pointer"
        >
          {spellCheck.correctedTextValue}
        </button>
        <span className="text-muted-foreground">?</span>
      </p>
    </div>
  );
}
