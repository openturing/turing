import { BadgeLocale } from "@/components/badge-locale";
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
} from "@/components/ui/select";
import type { TurLocale } from "@/models/locale/locale.model";
import { useTranslation } from "react-i18next";

interface LanguageSelectProps {
    value?: string;
    onValueChange: (value: string) => void;
    locales: TurLocale[];
    extraLocaleValues?: string[];
    placeholder?: string;
    className?: string;
}

export function LanguageSelect({
    value,
    onValueChange,
    locales,
    extraLocaleValues = [],
    placeholder,
    className,
}: Readonly<LanguageSelectProps>) {
    const { t } = useTranslation();
    const resolvedPlaceholder = placeholder ?? t("forms.common.chooseLanguage");
    const localeMap = new Map<string, { name: string; label: string }>();

    locales.forEach((locale) => {
        localeMap.set(locale.initials, {
            name: locale.en,
            label: `${locale.en}`,
        });
    });

    extraLocaleValues.forEach((localeValue) => {
        if (localeValue && !localeMap.has(localeValue)) {
            localeMap.set(localeValue, {
                name: localeValue,
                label: localeValue,
            });
        }
    });

    const options = Array.from(localeMap.entries()).map(([localeValue, localeData]) => ({
        value: localeValue,
        name: localeData.name,
        label: localeData.label,
    }));
    const selectedOption = options.find((option) => option.value === value);

    return (
        <Select value={value} onValueChange={onValueChange}>
            <SelectTrigger className={className}>
                {selectedOption ? (
                    <div className="flex items-center gap-2 truncate">
                        <BadgeLocale locale={selectedOption.value} />
                        <span className="truncate">{selectedOption.label}</span>
                    </div>
                ) : (
                    <span className="text-muted-foreground">{resolvedPlaceholder}</span>
                )}
            </SelectTrigger>
            <SelectContent>
                {options.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                        <div className="flex items-center gap-2">
                            <BadgeLocale locale={option.value} />
                            <span>{option.label}</span>
                        </div>
                    </SelectItem>
                ))}
            </SelectContent>
        </Select>
    );
}
