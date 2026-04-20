import { TurLogo } from "@/components/logo/tur-logo";
import { GradientButton } from "@/components/ui/gradient-button";
import { IconLoader2, IconSearchOff } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

export function SearchLoading() {
  const { t } = useTranslation();

  return (
    <div className="min-h-screen bg-background flex items-center justify-center">
      <div className="flex flex-col items-center gap-4">
        <div className="relative">
          <div className="absolute inset-0 rounded-full bg-gradient-to-r from-blue-500/20 to-indigo-500/20 blur-xl animate-pulse" />
          <div className="relative bg-gradient-to-br from-blue-600 to-indigo-600 rounded-full p-4">
            <IconLoader2 className="size-8 text-white animate-spin" />
          </div>
        </div>
        <p className="text-sm text-muted-foreground animate-pulse">{t("search.loading")}</p>
      </div>
    </div>
  );
}

interface SearchErrorProps {
  siteName: string;
  error: string | null;
  onShowAll: () => void;
}

export function SearchError({ siteName, error, onShowAll }: Readonly<SearchErrorProps>) {
  const { t } = useTranslation();

  return (
    <div className="min-h-screen bg-background text-foreground">
      <header className="border-b border-border/50 bg-background/80 backdrop-blur-xl">
        <div className="container mx-auto px-4 lg:px-8 h-16 flex items-center gap-2.5">
          <TurLogo size={28} />
          <span className="text-base font-bold tracking-tight bg-gradient-to-r from-blue-600 to-indigo-600 bg-clip-text text-transparent dark:from-blue-400 dark:to-indigo-400">
            {siteName}
          </span>
        </div>
      </header>
      <main className="container mx-auto px-4 lg:px-8 py-24">
        <div className="flex flex-col items-center text-center max-w-md mx-auto gap-5">
          <div className="rounded-2xl bg-muted/50 p-5">
            <IconSearchOff className="size-10 text-muted-foreground" />
          </div>
          <div className="space-y-2">
            <h3 className="text-xl font-semibold">{t("search.noContent")}</h3>
            <p className="text-sm text-muted-foreground leading-relaxed">
              {error ?? t("search.errorMessage")}
            </p>
          </div>
          <GradientButton size="lg" onClick={onShowAll}>
            {t("search.showAll")}
          </GradientButton>
        </div>
      </main>
    </div>
  );
}
