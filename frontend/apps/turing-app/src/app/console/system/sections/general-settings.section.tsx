import { SectionCard } from "@/components/ui/section-card";
import { FormControl, FormField } from "@/components/ui/form";
import { FormItemTwoColumns } from "@/components/ui/form-item-two-columns";
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select";
import { IconSettings } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import type { GlobalSettingsFormState } from "../use-global-settings-form";

/**
 * Formatting preferences (decimal / currency separator). Part of the
 * "Settings" admin page.
 *
 * @since 2026.3.4
 */
export function GeneralSettingsSection({ state }: { state: GlobalSettingsFormState }) {
    const { t } = useTranslation();
    const { form, isLoading } = state;

    const decimalSeparator = form.watch("decimalSeparator") ?? "DOT";
    const decimalExample = decimalSeparator === "COMMA" ? "1.500,75" : "1,500.75";
    const currencyExample = decimalSeparator === "COMMA" ? "150,75,BRL" : "150.75,BRL";

    return (
        <SectionCard variant="amber">
            <SectionCard.Header icon={IconSettings} title={t("globalSettings.sectionTitle")} description={t("globalSettings.sectionDescription")} />
            <SectionCard.Content>
                <FormField
                    control={form.control}
                    name="decimalSeparator"
                    render={({ field }) => (
                        <FormItemTwoColumns>
                            <FormItemTwoColumns.Left>
                                <FormItemTwoColumns.Label>{t("globalSettings.decimalSeparator")}</FormItemTwoColumns.Label>
                                <FormItemTwoColumns.Description>
                                    {t("globalSettings.decimalSeparatorDesc")}
                                </FormItemTwoColumns.Description>
                            </FormItemTwoColumns.Left>
                            <FormItemTwoColumns.Right>
                                <FormControl>
                                    <Select onValueChange={field.onChange} value={field.value} disabled={isLoading}>
                                        <SelectTrigger className="w-full max-w-xs">
                                            <SelectValue placeholder={t("globalSettings.choose")} />
                                        </SelectTrigger>
                                        <SelectContent>
                                            <SelectItem value="DOT">{t("globalSettings.dot")}</SelectItem>
                                            <SelectItem value="COMMA">{t("globalSettings.comma")}</SelectItem>
                                        </SelectContent>
                                    </Select>
                                </FormControl>
                            </FormItemTwoColumns.Right>
                        </FormItemTwoColumns>
                    )}
                />
                <div className="flex gap-4">
                    <div className="flex-1 rounded-lg bg-muted/40 px-4 py-3">
                        <span className="text-xs text-muted-foreground">{t("globalSettings.decimalPreview")}</span>
                        <p className="text-sm font-mono font-semibold mt-0.5">{decimalExample}</p>
                    </div>
                    <div className="flex-1 rounded-lg bg-muted/40 px-4 py-3">
                        <span className="text-xs text-muted-foreground">{t("globalSettings.currencyFormat")}</span>
                        <p className="text-sm font-mono font-semibold mt-0.5">{currencyExample}</p>
                    </div>
                </div>
            </SectionCard.Content>
        </SectionCard>
    );
}
