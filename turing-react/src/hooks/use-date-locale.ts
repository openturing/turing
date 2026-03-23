import type { Locale } from "date-fns";
import { enUS, ptBR } from "date-fns/locale";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";

const LOCALE_MAP: Record<string, Locale> = {
    en: enUS,
    pt: ptBR,
};

/**
 * Returns the date-fns Locale matching the current i18n language.
 *
 * @since 2026.1.14
 */
export function useDateLocale(): Locale {
    const { i18n } = useTranslation();
    return useMemo(
        () => LOCALE_MAP[i18n.language?.substring(0, 2)] ?? enUS,
        [i18n.language],
    );
}
