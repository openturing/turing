import { SectionCard } from "@/components/ui/section-card";
import {
    FormControl,
    FormDescription,
    FormField,
    FormItem,
    FormLabel,
    FormMessage,
} from "@/components/ui/form";
import { FormItemTwoColumns } from "@/components/ui/form-item-two-columns";
import { GradientButton } from "@/components/ui/gradient-button";
import { Input } from "@/components/ui/input";
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select";
import { IconLoader2, IconMail, IconMailForward } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import type { GlobalSettingsFormState } from "../use-global-settings-form";

/**
 * Transactional email provider configuration + test-email action. Part of the
 * "Settings" admin page.
 *
 * @since 2026.3.4
 */
export function EmailSettingsSection({ state }: { state: GlobalSettingsFormState }) {
    const { t } = useTranslation();
    const { form, isLoading, isSendingTest, onSendTestEmail } = state;

    return (
        <SectionCard variant="amber">
            <SectionCard.Header icon={IconMail} title={t("globalSettings.emailSettings")} description={t("globalSettings.emailSettingsDesc")} />
            <SectionCard.Content>
                <FormField
                    control={form.control}
                    name="emailProvider"
                    render={({ field }) => (
                        <FormItemTwoColumns>
                            <FormItemTwoColumns.Left>
                                <FormItemTwoColumns.Label>{t("globalSettings.emailProvider")}</FormItemTwoColumns.Label>
                                <FormItemTwoColumns.Description>
                                    {t("globalSettings.emailProviderDesc")}
                                </FormItemTwoColumns.Description>
                            </FormItemTwoColumns.Left>
                            <FormItemTwoColumns.Right>
                                <FormControl>
                                    <Select onValueChange={field.onChange} value={field.value ?? "BREVO"} disabled={isLoading}>
                                        <SelectTrigger className="w-full max-w-xs">
                                            <SelectValue placeholder={t("globalSettings.choose")} />
                                        </SelectTrigger>
                                        <SelectContent>
                                            <SelectItem value="BREVO">{t("globalSettings.brevo")}</SelectItem>
                                        </SelectContent>
                                    </Select>
                                </FormControl>
                            </FormItemTwoColumns.Right>
                        </FormItemTwoColumns>
                    )}
                />
                <FormField
                    control={form.control}
                    name="emailApiKey"
                    render={({ field }) => (
                        <FormItem>
                            <FormLabel>{t("globalSettings.apiKey")}</FormLabel>
                            <FormDescription>
                                {t("globalSettings.apiKeyDesc")}
                            </FormDescription>
                            <FormControl>
                                <Input
                                    type="password"
                                    className="max-w-md font-mono text-sm"
                                    placeholder="xkeysib-..."
                                    disabled={isLoading}
                                    {...field}
                                    value={field.value ?? ""}
                                />
                            </FormControl>
                            <FormMessage />
                        </FormItem>
                    )}
                />
                <FormField
                    control={form.control}
                    name="senderEmail"
                    render={({ field }) => (
                        <FormItem>
                            <FormLabel>{t("globalSettings.senderEmail")}</FormLabel>
                            <FormDescription>
                                {t("globalSettings.senderEmailDesc")}
                            </FormDescription>
                            <FormControl>
                                <Input
                                    type="email"
                                    className="max-w-md text-sm"
                                    placeholder="noreply@example.com"
                                    disabled={isLoading}
                                    {...field}
                                    value={field.value ?? ""}
                                />
                            </FormControl>
                            <FormMessage />
                        </FormItem>
                    )}
                />
                <FormField
                    control={form.control}
                    name="senderName"
                    render={({ field }) => (
                        <FormItem>
                            <FormLabel>{t("globalSettings.senderName")}</FormLabel>
                            <FormDescription>
                                {t("globalSettings.senderNameDesc")}
                            </FormDescription>
                            <FormControl>
                                <Input
                                    className="max-w-md text-sm"
                                    placeholder="Viglet Turing"
                                    disabled={isLoading}
                                    {...field}
                                    value={field.value ?? ""}
                                />
                            </FormControl>
                            <FormMessage />
                        </FormItem>
                    )}
                />
                <FormField
                    control={form.control}
                    name="recipientEmail"
                    render={({ field }) => (
                        <FormItem>
                            <FormLabel>{t("globalSettings.recipientEmail")}</FormLabel>
                            <FormDescription>
                                {t("globalSettings.recipientEmailDesc")}
                            </FormDescription>
                            <FormControl>
                                <Input
                                    type="email"
                                    className="max-w-md text-sm"
                                    placeholder="admin@example.com"
                                    disabled={isLoading}
                                    {...field}
                                    value={field.value ?? ""}
                                />
                            </FormControl>
                            <FormMessage />
                        </FormItem>
                    )}
                />
                <div className="flex items-center gap-3 pt-4 border-t">
                    <GradientButton
                        type="button"
                        variant="outline"
                        disabled={isLoading || isSendingTest}
                        onClick={onSendTestEmail}
                    >
                        {isSendingTest ? (
                            <IconLoader2 className="size-4 animate-spin" />
                        ) : (
                            <IconMailForward className="size-4" />
                        )}
                        {isSendingTest ? t("globalSettings.sending") : t("globalSettings.sendTestEmail")}
                    </GradientButton>
                    <span className="text-xs text-muted-foreground">
                        {t("globalSettings.testEmailHint")}
                    </span>
                </div>
            </SectionCard.Content>
        </SectionCard>
    );
}
