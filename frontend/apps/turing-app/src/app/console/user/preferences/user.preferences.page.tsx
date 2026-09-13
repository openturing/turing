import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { toast } from "@viglet/viglet-design-system";

const LANGUAGES = [
    { code: "en", labelKey: "account.preferences.english" },
    { code: "pt", labelKey: "account.preferences.portuguese" },
] as const;

/**
 * Preferences sub-page: language and display settings.
 *
 * @since 2026.1.14
 */
/**
 * @param header Header for this surface — the Bento account surface (T568)
 *   passes `null`, its tab-bar already labelling the section.
 */
export default function UserPreferencesPage({ header }: Readonly<{ header?: ReactNode }> = {}) {
    const { t, i18n } = useTranslation();

    useSubPageBreadcrumb(t("account.preferences.title"));

    function handleLanguageChange(lang: string) {
        i18n.changeLanguage(lang);
        toast.success(t("account.preferences.languageSaved"));
    }

    return (
        <>
            {header}
            <div className="bento-tile bento-glass rounded-3xl p-6 md:p-8">
                <div className="space-y-2">
                    <Label htmlFor="language">{t("account.preferences.language")}</Label>
                    <p className="text-sm text-muted-foreground mb-2">
                        {t("account.preferences.languageDesc")}
                    </p>
                    <Select value={i18n.language?.substring(0, 2)} onValueChange={handleLanguageChange}>
                        <SelectTrigger id="language" className="w-64">
                            <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                            {LANGUAGES.map((lang) => (
                                <SelectItem key={lang.code} value={lang.code}>
                                    {t(lang.labelKey)}
                                </SelectItem>
                            ))}
                        </SelectContent>
                    </Select>
                </div>
            </div>
        </>
    );
}
