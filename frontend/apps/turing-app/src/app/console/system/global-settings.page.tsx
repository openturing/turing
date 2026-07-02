import { Form } from "@/components/ui/form";
import { StickyPageHeader } from "@/components/sticky-page-header";
import { GradientButton } from "@/components/ui/gradient-button";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconDeviceFloppy, IconSettings } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { EmailSettingsSection } from "./sections/email-settings.section";
import { GeneralSettingsSection } from "./sections/general-settings.section";
import { useGlobalSettingsForm } from "./use-global-settings-form";

/**
 * "Settings" admin page — platform-wide formatting preferences and the
 * transactional email provider. Generative-AI configuration (Python, URL
 * signing secret, PII retention, LLM, RAG, reranker) lives on the sibling
 * "Generative AI" page; both edit the same TurGlobalSettings record.
 */
export default function GlobalSettingsPage() {
    const { t } = useTranslation();
    useSubPageBreadcrumb(t("globalSettings.breadcrumb"));

    const state = useGlobalSettingsForm();
    const { form, isLoading, isSaving, onSubmit } = state;

    return (
        <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 px-4 lg:px-6 pb-8">
                <StickyPageHeader>
                    <StickyPageHeader.Title
                        icon={IconSettings}
                        feature={t("globalSettings.breadcrumb")}
                        description={t("globalSettings.description")}
                    />
                    <StickyPageHeader.Actions>
                        <GradientButton type="submit" size="sm" loading={isSaving} disabled={isLoading}>
                            <IconDeviceFloppy className="size-4" />
                            {t("forms.formActions.saveChanges")}
                        </GradientButton>
                    </StickyPageHeader.Actions>
                </StickyPageHeader>

                <GeneralSettingsSection state={state} />
                <EmailSettingsSection state={state} />
            </form>
        </Form>
    );
}
