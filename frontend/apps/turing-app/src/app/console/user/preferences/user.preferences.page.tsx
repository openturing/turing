import { SubPageHeader } from "@/components/sub.page.header";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconWorld } from "@tabler/icons-react";
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
 * @param header Optional header override. Console renders the default
 *   {@link SubPageHeader}; the Bento account surface (T568) passes `null`.
 */
export default function UserPreferencesPage({ header }: Readonly<{ header?: ReactNode }> = {}) {
    const { t, i18n } = useTranslation();

    // Bento surfaces pass an explicit `header` (a BentoHero or `null`); the
    // console leaves it undefined. In bento the body is a frosted card instead
    // of a bare centered column.
    const isBento = header !== undefined;

    useSubPageBreadcrumb(t("account.preferences.title"));

    function handleLanguageChange(lang: string) {
        i18n.changeLanguage(lang);
        toast.success(t("account.preferences.languageSaved"));
    }

    return (
        <>
            {header !== undefined ? header : (
                <SubPageHeader
                    icon={IconWorld}
                    name={t("account.preferences.title")}
                    feature={t("account.preferences.title")}
                    description={t("account.preferences.description")}
                />
            )}
            <div className={isBento ? "bento-tile bento-glass rounded-3xl p-6 md:p-8" : "max-w-2xl mx-auto py-8 px-6"}>
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
