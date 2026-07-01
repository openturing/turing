import { SectionCard } from "@/components/ui/section-card";
import { FormItemTwoColumns } from "@/components/ui/form-item-two-columns";
import { GradientButton } from "@/components/ui/gradient-button";
import { IconAlertTriangle, IconCheck, IconKey, IconRefresh } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import type { GlobalSettingsFormState } from "../use-global-settings-form";

/**
 * URL Signing Secret — Code Interpreter file API HMAC. Status-only (no value on
 * the wire); regenerate button mints a fresh secret server-side. Rotating
 * invalidates every previously emitted signed download URL — that's the
 * intended behavior of an operator-initiated rotation. Part of the
 * "Generative AI" admin page.
 *
 * @since 2026.3.4
 */
export function UrlSigningSecretSection({ state }: { state: GlobalSettingsFormState }) {
    const { t } = useTranslation();
    const { isLoading, secretConfigured, secretPreview, isRotatingSecret, onRotateUrlSigningSecret } = state;

    return (
        <SectionCard variant="rose">
            <SectionCard.Header
                icon={IconKey}
                title={t("globalSettings.urlSigningSecretTitle")}
                description={t("globalSettings.urlSigningSecretDesc")}
            />
            <SectionCard.Content>
                <FormItemTwoColumns>
                    <FormItemTwoColumns.Left>
                        <FormItemTwoColumns.Label>
                            {t("globalSettings.urlSigningSecretStatusLabel")}
                        </FormItemTwoColumns.Label>
                        <FormItemTwoColumns.Description>
                            {secretConfigured === null
                                ? t("globalSettings.urlSigningSecretStatusUnknown")
                                : secretConfigured
                                    ? t("globalSettings.urlSigningSecretStatusConfigured")
                                    : t("globalSettings.urlSigningSecretStatusBlank")}
                            {secretPreview && (
                                <span className="block mt-1 font-mono text-xs text-muted-foreground">
                                    {t("globalSettings.urlSigningSecretPreview", { preview: secretPreview })}
                                </span>
                            )}
                        </FormItemTwoColumns.Description>
                    </FormItemTwoColumns.Left>
                    <FormItemTwoColumns.Right>
                        <div className="flex items-center gap-2">
                            {secretConfigured && !isRotatingSecret && (
                                <span className="inline-flex items-center gap-1 text-xs text-emerald-600 dark:text-emerald-400">
                                    <IconCheck className="size-4" />
                                    {t("globalSettings.urlSigningSecretSetBadge")}
                                </span>
                            )}
                            {!secretConfigured && secretConfigured !== null && (
                                <span className="inline-flex items-center gap-1 text-xs text-amber-600 dark:text-amber-400">
                                    <IconAlertTriangle className="size-4" />
                                    {t("globalSettings.urlSigningSecretBlankBadge")}
                                </span>
                            )}
                            <GradientButton
                                type="button"
                                variant="outline"
                                size="sm"
                                onClick={onRotateUrlSigningSecret}
                                loading={isRotatingSecret}
                                disabled={isLoading || isRotatingSecret}
                            >
                                <IconRefresh className="size-4" />
                                {t("globalSettings.urlSigningSecretRotateAction")}
                            </GradientButton>
                        </div>
                    </FormItemTwoColumns.Right>
                </FormItemTwoColumns>
            </SectionCard.Content>
        </SectionCard>
    );
}
