"use client"

import { LanguageSelect } from "@/components/language-select"
import { IconCirclePlus, IconTrash } from "@tabler/icons-react";
import { Button } from "@/components/ui/button"
import { FormDescription, FormItem, FormLabel } from "@/components/ui/form"
import { Input } from "@/components/ui/input"
import type { TurLocale } from "@/models/locale/locale.model"
import { useTranslation } from "react-i18next"

export type SNSiteLabelTranslationEntry = {
    locale: string;
    label: string;
};

type SNSiteLabelTranslationsProps = {
    entries: SNSiteLabelTranslationEntry[];
    locales: TurLocale[];
    onAdd: () => void;
    onUpdate: (index: number, key: "locale" | "label", value: string) => void;
    onRemove: (index: number) => void;
};

export const SNSiteLabelTranslations: React.FC<SNSiteLabelTranslationsProps> = ({
    entries,
    locales,
    onAdd,
    onUpdate,
    onRemove,
}) => {
    const { t } = useTranslation();
    return (
        <FormItem>
            <div className="flex items-center justify-between mt-6 mb-1">
                <div className="grid gap-2">
                    <FormLabel>{t("forms.snLabelTranslations.addTranslations")}</FormLabel>
                    <FormDescription>{t("forms.snLabelTranslations.addTranslationsDesc")}</FormDescription>
                </div>
                <Button type="button" variant="outline" onClick={onAdd}>
                    <IconCirclePlus className="h-4 w-4 mr-2" />
                    {t("forms.snLabelTranslations.addLabel")}
                </Button>
            </div>
            <div className="space-y-3">
                {entries.map((entry, index) => (
                    <div key={`${entry.locale}-${index}`} className="flex items-center gap-2">
                        <LanguageSelect className="w-1/3"
                            value={entry.locale}
                            onValueChange={(value) => onUpdate(index, "locale", value)}
                            locales={locales}
                            extraLocaleValues={entries.map((item) => item.locale)}
                        />
                        <Input
                            placeholder={t("forms.snLabelTranslations.enterLabel")}
                            value={entry.label}
                            onChange={(event) => onUpdate(index, "label", event.target.value)}
                            className="flex-1"
                        />
                        <Button type="button" variant="ghost" size="icon" onClick={() => onRemove(index)}>
                            <IconTrash className="h-4 w-4 text-red-500" />
                        </Button>
                    </div>
                ))}
            </div>
        </FormItem>
    );
};
