import { SectionCard } from "@/components/ui/section-card";
import {
    FormControl,
    FormDescription,
    FormField,
    FormItem,
    FormLabel,
    FormMessage,
} from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { IconAlertTriangle } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import type { GlobalSettingsFormState } from "../use-global-settings-form";

/**
 * T61 — PII slot retention TTL. Hourly cleanup removes `pii_*` slot values
 * older than the configured horizon. Encryption-at-rest + log redaction always
 * apply regardless of the TTL value. Part of the "Generative AI" admin page.
 *
 * @since 2026.3.4
 */
export function PiiSlotSection({ state }: { state: GlobalSettingsFormState }) {
    const { t } = useTranslation();
    const { form, isLoading } = state;

    return (
        <SectionCard variant="amber">
            <SectionCard.Header
                icon={IconAlertTriangle}
                title={t("globalSettings.piiSlotTitle")}
                description={t("globalSettings.piiSlotDesc")}
            />
            <SectionCard.Content>
                <FormField
                    control={form.control}
                    name="piiSlotTtlHours"
                    rules={{
                        min: { value: 0, message: t("globalSettings.piiSlotTtlOutOfRange") },
                        max: { value: 8760, message: t("globalSettings.piiSlotTtlOutOfRange") },
                    }}
                    render={({ field }) => (
                        <FormItem>
                            <FormLabel>{t("globalSettings.piiSlotTtlLabel")}</FormLabel>
                            <FormDescription>
                                {t("globalSettings.piiSlotTtlDesc")}
                            </FormDescription>
                            <FormControl>
                                <Input
                                    type="number"
                                    min={0}
                                    max={8760}
                                    step={1}
                                    disabled={isLoading}
                                    {...field}
                                    value={field.value ?? 0}
                                    onChange={e => field.onChange(Number(e.target.value))}
                                />
                            </FormControl>
                            <FormMessage />
                        </FormItem>
                    )}
                />
            </SectionCard.Content>
        </SectionCard>
    );
}
