import { GradientButton } from "@/components/ui/gradient-button";
import type { TurChatResponse } from "@viglet/turing-react-sdk";
import { IconSearch } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import type { SearchSpellCheckView } from "../lib/search-view";

interface SearchEmptyProps {
  query: string;
  /** T865 — the projected spell-check, `null` when there is no correction to offer. */
  spellCheck: SearchSpellCheckView | null;
  chat: TurChatResponse | null;
  onShowAll: () => void;
}

export function SearchEmpty({ query, spellCheck, chat, onShowAll }: Readonly<SearchEmptyProps>) {
  const { t } = useTranslation();

  return (
    <div className="flex flex-col items-center text-center py-20 max-w-md mx-auto gap-5">
      <div className="rounded-2xl bg-muted/50 p-5">
        <IconSearch className="size-10 text-muted-foreground" />
      </div>
      <div className="space-y-2">
        <h3 className="text-xl font-semibold">
          {t("search.noResults", { query })}
        </h3>
        {spellCheck && (
          <p className="text-sm text-muted-foreground">
            {t("search.didYouMean")}{" "}
            <button
              type="button"
              onClick={spellCheck.applyCorrection}
              className="font-semibold text-blue-600 dark:text-blue-400 hover:underline cursor-pointer"
            >
              {spellCheck.correctedTextValue}
            </button>
            ?
          </p>
        )}
        {!spellCheck && !chat?.text && (
          <p className="text-sm text-muted-foreground">
            {t("search.tryDifferent")}
          </p>
        )}
      </div>
      {!spellCheck && !chat?.text && (
        <GradientButton size="lg" onClick={onShowAll}>
          {t("search.browseAll")}
        </GradientButton>
      )}
    </div>
  );
}
