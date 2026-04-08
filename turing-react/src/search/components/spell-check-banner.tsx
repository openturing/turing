import type { TurSpellCheck } from "@viglet/turing-react-sdk";
import { IconSparkles } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

interface SpellCheckBannerProps {
  spellCheck: TurSpellCheck;
  onNavigate: (href: string) => void;
}

export function SpellCheckBanner({ spellCheck, onNavigate }: Readonly<SpellCheckBannerProps>) {
  const { t } = useTranslation();

  if (!spellCheck.correctedText) return null;

  if (spellCheck.usingCorrectedText) {
    return (
      <div className="mb-5 flex items-start gap-2.5 rounded-lg border border-amber-500/20 bg-amber-500/5 px-4 py-3">
        <IconSparkles className="size-4 text-amber-500 mt-0.5 shrink-0" />
        <div className="text-sm">
          <span className="text-muted-foreground">{t("search.showingResultsFor")} </span>
          <button
            type="button"
            onClick={() => onNavigate(spellCheck.corrected.link)}
            className="font-semibold text-foreground italic hover:text-blue-600 dark:hover:text-blue-400 underline underline-offset-2 decoration-blue-500/30 cursor-pointer"
          >
            {spellCheck.corrected.text}
          </button>
          <span className="text-muted-foreground mx-1.5">&middot;</span>
          <span className="text-muted-foreground">{t("search.searchInsteadFor")} </span>
          <button
            type="button"
            onClick={() => onNavigate(spellCheck.original.link)}
            className="font-medium text-blue-600 dark:text-blue-400 hover:underline cursor-pointer"
          >
            {spellCheck.original.text}
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
          onClick={() => onNavigate(spellCheck.corrected.link)}
          className="font-semibold text-foreground italic hover:text-blue-600 dark:hover:text-blue-400 underline underline-offset-2 decoration-blue-500/30 cursor-pointer"
        >
          {spellCheck.corrected.text}
        </button>
        <span className="text-muted-foreground">?</span>
      </p>
    </div>
  );
}
