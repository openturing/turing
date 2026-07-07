import { Form } from "@/components/ui/form";
import { StickyPageHeader } from "@/components/sticky-page-header";
import { GradientButton } from "@/components/ui/gradient-button";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { IconDeviceFloppy, IconSparkles } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { LlmSettingsSection } from "./sections/llm-settings.section";
import { PiiSlotSection } from "./sections/pii-slot.section";
import { PythonSettingsSection } from "./sections/python-settings.section";
import { RagRerankSection } from "./sections/rag-rerank.section";
import { RagSettingsSection } from "./sections/rag-settings.section";
import { UrlSigningSecretSection } from "./sections/url-signing-secret.section";
import { useGlobalSettingsForm } from "./use-global-settings-form";

/**
 * "Generative AI" admin page — all AI-adjacent configuration split out of the
 * "Settings" page: Python / Code Interpreter runtime, the URL-signing secret,
 * PII slot retention, LLM defaults & cache, RAG, and the RAG reranker. Shares
 * the TurGlobalSettings record with the "Settings" page via
 * {@link useGlobalSettingsForm}.
 */
export default function GenAiSettingsPage() {
    const { t } = useTranslation();
    useSubPageBreadcrumb(t("globalSettings.genAiBreadcrumb"));

    const state = useGlobalSettingsForm();
    const { form, isLoading, isSaving, onSubmit } = state;

    return (
        <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 px-4 lg:px-6 pb-8">
                <StickyPageHeader>
                    <StickyPageHeader.Title
                        icon={IconSparkles}
                        feature={t("globalSettings.genAiTitle")}
                        description={t("globalSettings.genAiDescription")}
                    />
                    <StickyPageHeader.Actions>
                        <GradientButton type="submit" size="sm" loading={isSaving} disabled={isLoading}>
                            <IconDeviceFloppy className="size-4" />
                            {t("forms.formActions.saveChanges")}
                        </GradientButton>
                    </StickyPageHeader.Actions>
                </StickyPageHeader>

                <PythonSettingsSection state={state} />
                <UrlSigningSecretSection state={state} />
                <PiiSlotSection state={state} />
                <LlmSettingsSection state={state} />
                <RagSettingsSection state={state} />
                <RagRerankSection state={state} />
            </form>
        </Form>
    );
}
