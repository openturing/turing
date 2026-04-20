import { GradientButton } from "@/components/ui/gradient-button";
import type { TurChatResponse, TurSpellCheck } from "@viglet/turing-react-sdk";
import { IconSearch } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

interface SearchEmptyProps {
  query: string;
  spellCheck: TurSpellCheck;
  chat: TurChatResponse | null;
  onNavigate: (href: string) => void;
  onShowAll: () => void;
}

export function SearchEmpty({ query, spellCheck, chat, onNavigate, onShowAll }: Readonly<SearchEmptyProps>) {
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
        {spellCheck.correctedText ? (
          <p className="text-sm text-muted-foreground">
            {t("search.didYouMean")}{" "}
            <button
              type="button"
              onClick={() => onNavigate(spellCheck.corrected.link)}
              className="font-semibold text-blue-600 dark:text-blue-400 hover:underline cursor-pointer"
            >
              {spellCheck.corrected.text}
            </button>
            ?
          </p>
        ) : !chat?.text ? (
          <p className="text-sm text-muted-foreground">
            {t("search.tryDifferent")}
          </p>
        ) : null}
      </div>
      {!spellCheck.correctedText && !chat?.text && (
        <GradientButton size="lg" onClick={onShowAll}>
          {t("search.browseAll")}
        </GradientButton>
      )}
    </div>
  );
}
